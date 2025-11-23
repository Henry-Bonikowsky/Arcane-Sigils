package com.zenax.armorsets.gui;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages GUI interfaces for socketing sigils.
 */
public class GUIManager implements Listener {

    private final ArmorSetsPlugin plugin;
    private final Map<UUID, GUISession> activeSessions = new HashMap<>();
    private final Set<UUID> transitioning = new HashSet<>(); // Players transitioning between GUIs
    private final NamespacedKey GUI_TYPE_KEY;

    public GUIManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.GUI_TYPE_KEY = new NamespacedKey(plugin, "gui_type");
    }

    /**
     * Open the socket GUI for an armor piece.
     */
    public void openSocketGUI(Player player, ItemStack armor, int armorSlot) {
        String title = plugin.getConfigManager().getMainConfig().getString("gui.socket-title", "&8Socket Sigil");
        Inventory gui = Bukkit.createInventory(null, 27, TextUtil.parseComponent(title));

        // Slot 13: Armor piece display
        gui.setItem(13, armor.clone());

        // Slot 11: Current sigil or empty socket
        Sigil current = plugin.getSocketManager().getSocketedSigil(armor);
        if (current != null) {
            ItemStack sigilDisplay = createSigilDisplay(current, true);
            gui.setItem(11, sigilDisplay);
        } else {
            gui.setItem(11, createEmptySocketItem());
        }

        // Slot 15: Socket/Unsocket action button
        if (current != null) {
            gui.setItem(15, createUnsocketButton());
        } else {
            gui.setItem(15, createSocketButton());
        }

        // Fill borders
        ItemStack border = createBorderItem();
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, border);
            }
        }

        // Store session and open
        GUISession session = new GUISession(GUIType.SOCKET, armor, armorSlot);
        openGUI(player, gui, session);
    }

    /**
     * Open the unsocket GUI to select which sigil to remove.
     */
    public void openUnsocketGUI(Player player, ItemStack armor, int armorSlot) {
        List<Sigil> sigils = plugin.getSocketManager().getSocketedSigils(armor);

        if (sigils.isEmpty()) {
            player.sendMessage(TextUtil.colorize("&cNo sigils socketed on this armor!"));
            return;
        }

        // Calculate inventory size based on sigil count (min 27, 9 per row)
        int rows = Math.max(3, (int) Math.ceil((sigils.size() + 9) / 9.0));
        int size = Math.min(rows * 9, 54);

        Inventory gui = Bukkit.createInventory(null, size, TextUtil.parseComponent("&8Remove Sigil"));

        // Top row border
        ItemStack border = createBorderItem();
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, border);
        }

        // Display armor in center of top row
        gui.setItem(4, armor.clone());

        // Add sigils starting from slot 9
        int slot = 9;
        for (Sigil sigil : sigils) {
            if (slot >= size - 1) break;

            ItemStack sigilItem = createUnsocketSigilItem(sigil);
            gui.setItem(slot, sigilItem);
            slot++;
        }

        // Close button at last slot
        ItemStack close = createGuiItem(Material.BARRIER, "&cClose", "&7Close menu");
        gui.setItem(size - 1, close);

        GUISession session = new GUISession(GUIType.UNSOCKET, armor, armorSlot);
        openGUI(player, gui, session);
    }

    private ItemStack createUnsocketSigilItem(Sigil sigil) {
        Material material = sigil.isExclusive() ? Material.BARRIER : Material.NETHER_STAR;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String rarityColor = getRarityColor(sigil.getRarity());
        String roman = toRomanNumeral(sigil.getTier());
        String baseName = sigil.getName().replaceAll("\\s*&8\\[T\\d+\\]", "").trim();

        meta.displayName(TextUtil.parseComponent(rarityColor + baseName + " &b" + roman));

        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.parseComponent("&8Rarity: " + rarityColor + sigil.getRarity()));
        lore.add(TextUtil.parseComponent("&8Slot: &f" + TextUtil.toProperCase(sigil.getSlot())));
        lore.add(Component.empty());

        for (String desc : sigil.getDescription()) {
            lore.add(TextUtil.parseComponent("&7" + desc));
        }

        lore.add(Component.empty());

        if (sigil.isExclusive()) {
            lore.add(TextUtil.parseComponent("&c&l✖ EXCLUSIVE - Cannot be removed"));
            if (sigil.getCrate() != null) {
                lore.add(TextUtil.parseComponent("&6☆ " + sigil.getCrate() + " Exclusive"));
            }
        } else {
            lore.add(TextUtil.parseComponent("&a&lClick to remove"));
            lore.add(TextUtil.parseComponent("&7Sigil will be returned as a shard"));
        }

        // Store sigil ID in PDC for identification
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "unsocket_sigil_id"),
            PersistentDataType.STRING,
            sigil.getId()
        );

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void handleUnsocketGUIClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        int lastSlot = inv.getSize() - 1;

        // Close button
        if (slot == lastSlot) {
            player.closeInventory();
            return;
        }

        // Ignore top row (border)
        if (slot < 9) return;

        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;

        // Check if it's an exclusive sigil (barrier = can't remove)
        if (clicked.getType() == Material.BARRIER) {
            player.sendMessage(TextUtil.colorize("&c&lExclusive sigils cannot be removed!"));
            playSound(player, "error");
            return;
        }

        // Get sigil ID from PDC
        String sigilId = clicked.getItemMeta().getPersistentDataContainer().get(
            new NamespacedKey(plugin, "unsocket_sigil_id"),
            PersistentDataType.STRING
        );

        if (sigilId == null) return;

        // Unsocket the sigil
        Sigil removed = plugin.getSocketManager().unsocketSigilById(player, session.armor, sigilId);
        if (removed != null) {
            // Give sigil shard back to player
            ItemStack shard = plugin.getSigilManager().createSigilItem(removed);
            player.getInventory().addItem(shard);

            // Update armor in player inventory
            updateArmorInInventory(player, session.armorSlot, session.armor);

            player.sendMessage(TextUtil.colorize("&aUnsocketed &f" + removed.getName() + "&a! Shard returned."));
            playSound(player, "unsocket");

            // Refresh GUI or close if no more sigils
            List<Sigil> remaining = plugin.getSocketManager().getSocketedSigils(session.armor);
            if (remaining.isEmpty()) {
                player.closeInventory();
            } else {
                openUnsocketGUI(player, session.armor, session.armorSlot);
            }
        } else {
            player.sendMessage(TextUtil.colorize("&cFailed to unsocket sigil!"));
            playSound(player, "error");
        }
    }

    private String getRarityColor(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> "&7";
            case "UNCOMMON" -> "&a";
            case "RARE" -> "&9";
            case "EPIC" -> "&5";
            case "LEGENDARY" -> "&6";
            case "MYTHIC" -> "&d";
            default -> "&7";
        };
    }

    private String toRomanNumeral(int num) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (num >= 1 && num <= 10) return numerals[num - 1];
        return String.valueOf(num);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GUISession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();

        switch (session.type) {
            case SOCKET -> handleSocketGUIClick(player, session, slot, event);
            case UNSOCKET -> handleUnsocketGUIClick(player, session, slot, event);
            case BUILD_MAIN_MENU -> handleBuildMainMenuClick(player, session, slot, event);
            case SET_BROWSER -> handleSetBrowserClick(player, session, slot, event);
            case FUNCTION_BROWSER -> handleFunctionBrowserClick(player, session, slot, event);
            case SLOT_SELECTOR -> handleSlotSelectorClick(player, session, slot, event);
            case TRIGGER_SELECTOR -> handleTriggerSelectorClick(player, session, slot, event);
            case EFFECT_SELECTOR -> handleEffectSelectorClick(player, session, slot, event);
            case TRIGGER_CONFIG -> handleTriggerConfigClick(player, session, slot, event);
            case CONFIRMATION -> handleConfirmationClick(player, session, slot, event);
            case SET_EDITOR -> handleSetEditorClick(player, session, slot, event);
            case FUNCTION_EDITOR -> handleFunctionEditorClick(player, session, slot, event);
            case SET_EFFECTS_VIEWER -> handleSetEffectsViewerClick(player, session, slot, event);
            case SET_SYNERGIES_VIEWER -> handleSetSynergiesViewerClick(player, session, slot, event);
            case TRIGGER_REMOVER -> handleTriggerRemoverClick(player, session, slot, event);
        }
    }

    private void handleSocketGUIClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        // Slot 15: Action button
        if (slot == 15) {
            ItemStack armor = session.armor;
            Sigil current = plugin.getSocketManager().getSocketedSigil(armor);

            if (current != null) {
                // Unsocket
                Sigil removed = plugin.getSocketManager().unsocketSigil(player, armor);
                if (removed != null) {
                    // Give sigil item to player
                    ItemStack sigilItem = plugin.getSigilManager().createSigilItem(removed);
                    player.getInventory().addItem(sigilItem);

                    // Update original armor in player inventory
                    updateArmorInInventory(player, session.armorSlot, armor);

                    player.sendMessage(TextUtil.colorize(
                            plugin.getConfigManager().getMessage("sigil-unsocketed")
                                    .replace("%sigil_name%", removed.getName())
                    ));
                    playSound(player, "unsocket");
                }
            }

            player.closeInventory();
            return;
        }

        // Allow placing sigil items from cursor
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir() && slot == 11) {
            Sigil sigil = plugin.getSigilManager().getSigilFromItem(cursor);
            if (sigil != null) {
                // Try to socket
                var result = plugin.getSocketManager().socketSigil(player, session.armor, sigil);
                if (result == com.zenax.armorsets.core.SocketManager.SocketResult.SUCCESS) {
                    cursor.setAmount(cursor.getAmount() - 1);
                    event.setCursor(cursor.getAmount() <= 0 ? null : cursor);

                    updateArmorInInventory(player, session.armorSlot, session.armor);
                    playSound(player, "socket");
                    player.closeInventory();
                } else {
                    playSound(player, "error");
                }
            }
        }
    }

    private void updateArmorInInventory(Player player, int slot, ItemStack armor) {
        switch (slot) {
            case 39 -> player.getInventory().setHelmet(armor);
            case 38 -> player.getInventory().setChestplate(armor);
            case 37 -> player.getInventory().setLeggings(armor);
            case 36 -> player.getInventory().setBoots(armor);
            default -> {
                // Handle hotbar/held item slots (0-8)
                if (slot >= 0 && slot <= 8) {
                    player.getInventory().setItem(slot, armor);
                } else {
                    // Fallback: set main hand
                    player.getInventory().setItemInMainHand(armor);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            UUID uuid = player.getUniqueId();
            // Don't remove session if player is transitioning between our GUIs
            if (transitioning.contains(uuid)) {
                transitioning.remove(uuid);
                return;
            }
            activeSessions.remove(uuid);
            playSound(player, "close");
        }
    }

    public void closeAll() {
        for (UUID uuid : new HashSet<>(activeSessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory();
            }
        }
        activeSessions.clear();
    }

    private ItemStack createSigilDisplay(Sigil sigil, boolean installed) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(TextUtil.parseComponent("&d" + sigil.getName()));

        List<Component> lore = new ArrayList<>();
        for (String desc : sigil.getDescription()) {
            lore.add(TextUtil.parseComponent("&7" + TextUtil.toProperCase(desc)));
        }

        if (!sigil.getEffects().isEmpty()) {
            lore.add(Component.empty());
            lore.add(TextUtil.parseComponent("&b&lWhen Empowered:"));
            for (String triggerKey : sigil.getEffects().keySet()) {
                String triggerName = TextUtil.toProperCase(triggerKey.replace("_", " "));
                String description = TextUtil.getTriggerDescription(triggerKey);
                lore.add(TextUtil.parseComponent("&b• &3" + triggerName));
                lore.add(TextUtil.parseComponent("&7  " + TextUtil.toProperCase(description)));
                var triggerConfig = sigil.getEffects().get(triggerKey);
                for (String effect : triggerConfig.getEffects()) {
                    String effectDesc = TextUtil.getEffectDescription(effect);
                    lore.add(TextUtil.parseComponent("&8    →&7 " + TextUtil.toProperCase(effectDesc)));
                }
            }
        }

        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("&8Tier: &f" + sigil.getTier()));
        lore.add(TextUtil.parseComponent("&8Slot: &f" + TextUtil.toProperCase(sigil.getSlot())));
        if (installed) {
            lore.add(Component.empty());
            lore.add(TextUtil.parseComponent("&aCurrently Installed"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmptySocketItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&8Empty Socket"));
        meta.lore(List.of(TextUtil.parseComponent("&7Drop a sigil shard here to socket")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSocketButton() {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&aSocket Sigil"));
        meta.lore(List.of(TextUtil.parseComponent("&7Drop a sigil shard on the socket")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createUnsocketButton() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&cUnsocket Sigil"));
        meta.lore(List.of(TextUtil.parseComponent("&7Click to remove the current sigil")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBorderItem() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuiItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        meta.displayName(TextUtil.parseComponent(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> loreList = new ArrayList<>();
            loreList.add(TextUtil.parseComponent(lore));
            meta.lore(loreList);
        }
        
        item.setItemMeta(meta);
        return item;
    }

    private void playSound(Player player, String soundType) {
        String soundName = plugin.getConfigManager().getMainConfig()
                .getString("gui.sounds." + soundType, "BLOCK_NOTE_BLOCK_PLING");
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }

    /**
     * Opens an inventory for a player while preserving the session during GUI transitions.
     */
    private void openGUI(Player player, Inventory inv, GUISession session) {
        UUID uuid = player.getUniqueId();
        // Mark as transitioning so onInventoryClose doesn't clear the session
        if (activeSessions.containsKey(uuid)) {
            transitioning.add(uuid);
        }
        activeSessions.put(uuid, session);
        player.openInventory(inv);
        playSound(player, "open");
    }

    private enum GUIType {
        SOCKET,
        UNSOCKET,
        FUNCTION_LIST,
        BUILD_MAIN_MENU,
        SET_BROWSER,
        FUNCTION_BROWSER,
        SLOT_SELECTOR,
        TRIGGER_SELECTOR,
        EFFECT_SELECTOR,
        TRIGGER_CONFIG,
        EFFECT_CONFIG,
        CONFIRMATION,
        SET_EDITOR,
        FUNCTION_EDITOR,
        SET_EFFECTS_VIEWER,
        SET_SYNERGIES_VIEWER,
        TRIGGER_REMOVER
    }

    private static class GUISession {
        final GUIType type;
        final ItemStack armor;
        final int armorSlot;
        final Map<String, Object> data;

        GUISession(GUIType type, ItemStack armor, int armorSlot) {
            this.type = type;
            this.armor = armor;
            this.armorSlot = armorSlot;
            this.data = new HashMap<>();
        }

        GUISession(GUIType type) {
            this.type = type;
            this.armor = null;
            this.armorSlot = -1;
            this.data = new HashMap<>();
        }

        void put(String key, Object value) {
            data.put(key, value);
        }

        Object get(String key) {
            return data.get(key);
        }
    }

    public void openBuildMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("&8Build Menu"));

        ItemStack createSet = createGuiItem(Material.DIAMOND_CHESTPLATE, "&b&lCreate Set", "&7Build new armor set");
        ItemStack createSigil = createGuiItem(Material.ECHO_SHARD, "&5&lCreate Sigil", "&7Build new sigil");
        ItemStack editSet = createGuiItem(Material.COMPARATOR, "&6&lEdit Set", "&7Modify existing set");
        ItemStack editSigil = createGuiItem(Material.REDSTONE, "&c&lEdit Sigil", "&7Modify existing sigil");
        ItemStack back = createGuiItem(Material.BARRIER, "&cClose", "&7Close menu");

        inv.setItem(10, createSet);
        inv.setItem(12, createSigil);
        inv.setItem(14, editSet);
        inv.setItem(16, editSigil);
        inv.setItem(26, back);

        GUISession session = new GUISession(GUIType.BUILD_MAIN_MENU);
        openGUI(player, inv, session);
    }

    /**
     * Opens armor slot selector for sets before adding triggers.
     */
    public void openSlotSelector(Player player, String setId) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("&8Select Armor Slot"));

        // Helmet
        ItemStack helmet = createGuiItem(Material.DIAMOND_HELMET, "&bHelmet", "&7Add trigger to helmet");
        inv.setItem(10, helmet);

        // Chestplate
        ItemStack chestplate = createGuiItem(Material.DIAMOND_CHESTPLATE, "&bChestplate", "&7Add trigger to chestplate");
        inv.setItem(12, chestplate);

        // Leggings
        ItemStack leggings = createGuiItem(Material.DIAMOND_LEGGINGS, "&bLeggings", "&7Add trigger to leggings");
        inv.setItem(14, leggings);

        // Boots
        ItemStack boots = createGuiItem(Material.DIAMOND_BOOTS, "&bBoots", "&7Add trigger to boots");
        inv.setItem(16, boots);

        // Back button
        ItemStack back = createGuiItem(Material.BARRIER, "&cBack", "");
        inv.setItem(22, back);

        GUISession session = new GUISession(GUIType.SLOT_SELECTOR);
        session.put("buildType", "set");
        session.put("buildId", setId);
        openGUI(player, inv, session);
    }

    public void openTriggerSelector(Player player, String buildType, String buildId) {
        openTriggerSelectorWithSlot(player, buildType, buildId, null);
    }

    public void openTriggerSelectorWithSlot(Player player, String buildType, String buildId, String armorSlot) {
        String title = armorSlot != null
            ? "&8Select Trigger: " + TextUtil.toProperCase(armorSlot)
            : "&8Select Trigger: " + buildId;
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent(title));

        String[] triggers = {"ATTACK", "DEFENSE", "KILL_MOB", "KILL_PLAYER", "SHIFT", "FALL_DAMAGE",
                "EFFECT_STATIC", "BOW_HIT", "BOW_SHOOT", "BLOCK_BREAK", "BLOCK_PLACE", "INTERACT", "TRIDENT_THROW"};

        for (int i = 0; i < triggers.length; i++) {
            String trigger = triggers[i];
            String desc = TextUtil.getTriggerDescription(trigger);
            ItemStack item = createGuiItem(Material.OAK_BUTTON, "&f" + trigger, "&8" + desc);
            inv.setItem(i, item);
        }

        ItemStack back = createGuiItem(Material.BARRIER, "&cBack", "");
        inv.setItem(44, back);

        GUISession session = new GUISession(GUIType.TRIGGER_SELECTOR);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        if (armorSlot != null) {
            session.put("armorSlot", armorSlot);
        }
        openGUI(player, inv, session);
    }

    public void openEffectSelector(Player player, String buildType, String buildId, String trigger) {
        openEffectSelectorWithSlot(player, buildType, buildId, trigger, null);
    }

    public void openEffectSelectorWithSlot(Player player, String buildType, String buildId, String trigger, String armorSlot) {
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent("&8Select Effects"));

        String[] effects = {"HEAL", "DAMAGE", "POTION", "PARTICLE", "SOUND", "DISINTEGRATE", "AEGIS",
                "INCREASE_DAMAGE", "MESSAGE", "CANCEL_EVENT", "TELEPORT_RANDOM", "SMOKEBOMB", "REPLENISH"};

        for (int i = 0; i < effects.length; i++) {
            String effect = effects[i];
            String desc = TextUtil.getEffectDescription(effect);
            ItemStack item = createGuiItem(Material.REDSTONE, "&f" + effect, "&8" + desc);
            inv.setItem(i, item);
        }

        ItemStack back = createGuiItem(Material.BARRIER, "&cBack", "");
        inv.setItem(44, back);

        GUISession session = new GUISession(GUIType.EFFECT_SELECTOR);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        session.put("trigger", trigger);
        if (armorSlot != null) {
            session.put("armorSlot", armorSlot);
        }
        openGUI(player, inv, session);
    }

    /**
     * Opens trigger configuration GUI to set chance and cooldown.
     */
    public void openTriggerConfig(Player player, String buildType, String buildId, String trigger, String effect, String armorSlot, double chance, double cooldown) {
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent("&8Configure Trigger"));

        // Display current settings
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(TextUtil.parseComponent("&e" + trigger));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(TextUtil.parseComponent("&7Effect: &f" + effect));
        infoLore.add(Component.empty());
        infoLore.add(TextUtil.parseComponent("&7Chance: &f" + (int) chance + "%"));
        infoLore.add(TextUtil.parseComponent("&7Cooldown: &f" + cooldown + "s"));
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // Chance controls (row 2)
        inv.setItem(19, createGuiItem(Material.RED_STAINED_GLASS_PANE, "&c-10%", "&7Decrease chance"));
        inv.setItem(20, createGuiItem(Material.ORANGE_STAINED_GLASS_PANE, "&6-1%", "&7Decrease chance"));

        ItemStack chanceDisplay = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta chanceMeta = chanceDisplay.getItemMeta();
        chanceMeta.displayName(TextUtil.parseComponent("&aChance: &f" + (int) chance + "%"));
        chanceMeta.lore(List.of(TextUtil.parseComponent("&7Activation probability")));
        chanceDisplay.setItemMeta(chanceMeta);
        inv.setItem(22, chanceDisplay);

        inv.setItem(24, createGuiItem(Material.LIME_STAINED_GLASS_PANE, "&a+1%", "&7Increase chance"));
        inv.setItem(25, createGuiItem(Material.GREEN_STAINED_GLASS_PANE, "&2+10%", "&7Increase chance"));

        // Cooldown controls (row 3)
        inv.setItem(28, createGuiItem(Material.RED_STAINED_GLASS_PANE, "&c-5s", "&7Decrease cooldown"));
        inv.setItem(29, createGuiItem(Material.ORANGE_STAINED_GLASS_PANE, "&6-1s", "&7Decrease cooldown"));

        ItemStack cooldownDisplay = new ItemStack(Material.CLOCK);
        ItemMeta cooldownMeta = cooldownDisplay.getItemMeta();
        cooldownMeta.displayName(TextUtil.parseComponent("&bCooldown: &f" + cooldown + "s"));
        cooldownMeta.lore(List.of(TextUtil.parseComponent("&7Time between activations")));
        cooldownDisplay.setItemMeta(cooldownMeta);
        inv.setItem(31, cooldownDisplay);

        inv.setItem(33, createGuiItem(Material.LIME_STAINED_GLASS_PANE, "&a+1s", "&7Increase cooldown"));
        inv.setItem(34, createGuiItem(Material.GREEN_STAINED_GLASS_PANE, "&2+5s", "&7Increase cooldown"));

        // Confirm and cancel buttons
        inv.setItem(39, createGuiItem(Material.LIME_DYE, "&aConfirm", "&7Add trigger with these settings"));
        inv.setItem(41, createGuiItem(Material.RED_DYE, "&cCancel", "&7Go back"));

        GUISession session = new GUISession(GUIType.TRIGGER_CONFIG);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        session.put("trigger", trigger);
        session.put("effect", effect);
        session.put("chance", chance);
        session.put("cooldown", cooldown);
        if (armorSlot != null) {
            session.put("armorSlot", armorSlot);
        }
        openGUI(player, inv, session);
    }

    // ========== BROWSER GUIS ==========

    public void openSetBrowser(Player player) {
        var allSets = plugin.getSetManager().getAllSets();

        if (allSets.isEmpty()) {
            player.sendMessage(TextUtil.colorize("&cNo armor sets found."));
            return;
        }

        List<com.zenax.armorsets.sets.ArmorSet> sets = new ArrayList<>(allSets);
        // Reserve one slot for back button
        int contentSlots = Math.min(sets.size(), 52); // Max 53 slots for content
        int size = Math.min((int) Math.ceil((contentSlots + 1) / 9.0) * 9, 54);
        Inventory inv = Bukkit.createInventory(null, size, TextUtil.parseComponent("&8Armor Sets (&f" + sets.size() + "&8)"));

        // Only add as many items as will fit
        for (int i = 0; i < contentSlots - 1; i++) {
            ItemStack item = createSetBrowserItem(sets.get(i));
            inv.setItem(i, item);
        }

        // Add back button at the last slot
        int backSlot = inv.getSize() - 1;
        ItemStack back = createGuiItem(Material.BARRIER, "&cClose", "");
        inv.setItem(backSlot, back);

        GUISession session = new GUISession(GUIType.SET_BROWSER);
        openGUI(player, inv, session);
    }

    public void openSigilBrowser(Player player) {
        var allSigils = plugin.getSigilManager().getAllSigils();

        if (allSigils.isEmpty()) {
            player.sendMessage(TextUtil.colorize("&cNo sigils found."));
            return;
        }

        List<Sigil> sigils = new ArrayList<>(allSigils);
        // Reserve one slot for back button
        int contentSlots = Math.min(sigils.size(), 53); // Max 53 slots for content
        int size = Math.min((int) Math.ceil((contentSlots + 1) / 9.0) * 9, 54);
        Inventory inv = Bukkit.createInventory(null, size, TextUtil.parseComponent("&8Sigils (&f" + sigils.size() + "&8)"));

        // Only add as many items as will fit
        for (int i = 0; i < contentSlots; i++) {
            ItemStack item = createSigilBrowserItem(sigils.get(i));
            inv.setItem(i, item);
        }

        // Add back button at the last slot
        int backSlot = inv.getSize() - 1;
        ItemStack back = createGuiItem(Material.BARRIER, "&cClose", "");
        inv.setItem(backSlot, back);

        GUISession session = new GUISession(GUIType.FUNCTION_BROWSER);
        openGUI(player, inv, session);
    }

    // ========== EDITOR GUIS ==========

    public void openSetEditor(Player player, com.zenax.armorsets.sets.ArmorSet set) {
        Inventory inv = Bukkit.createInventory(null, 36, TextUtil.parseComponent("&8Edit Set: &b" + set.getId()));

        // Display set info
        ItemStack setDisplay = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta meta = setDisplay.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&b" + set.getId()));
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.parseComponent("&8Tier: &f" + set.getTier()));
        lore.add(TextUtil.parseComponent("&8Material: &f" + set.getMaterial().name()));
        lore.add(TextUtil.parseComponent("&8Pattern: &f" + set.getNamePattern()));
        meta.lore(lore);
        setDisplay.setItemMeta(meta);
        inv.setItem(4, setDisplay);

        // View Individual Effects
        ItemStack viewIndividual = createGuiItem(Material.DIAMOND_HELMET, "&aIndividual Effects", "&7View piece-specific effects");
        inv.setItem(10, viewIndividual);

        // View Set Synergies
        ItemStack viewSynergies = createGuiItem(Material.NETHER_STAR, "&bSet Synergies", "&7View full-set bonuses");
        inv.setItem(12, viewSynergies);

        // Add Trigger
        ItemStack addTrigger = createGuiItem(Material.LIME_DYE, "&aAdd Trigger", "&7Add new effect trigger");
        inv.setItem(14, addTrigger);

        // Remove Trigger
        ItemStack removeTrigger = createGuiItem(Material.RED_DYE, "&cRemove Trigger", "&7Remove existing trigger");
        inv.setItem(15, removeTrigger);

        // Export to YAML
        ItemStack export = createGuiItem(Material.PAPER, "&eExport Config", "&7Generate YAML file");
        inv.setItem(16, export);

        // Back button
        ItemStack back = createGuiItem(Material.BARRIER, "&cBack", "");
        inv.setItem(31, back);

        GUISession session = new GUISession(GUIType.SET_EDITOR);
        session.put("setId", set.getId());
        session.put("set", set);
        openGUI(player, inv, session);
    }

    public void openSigilEditor(Player player, Sigil sigil) {
        Inventory inv = Bukkit.createInventory(null, 36, TextUtil.parseComponent("&8Edit Sigil: &d" + sigil.getName()));

        // Display sigil info
        ItemStack sigilDisplay = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = sigilDisplay.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&d" + sigil.getName()));
        List<Component> lore = new ArrayList<>();
        for (String desc : sigil.getDescription()) {
            lore.add(TextUtil.parseComponent("&7" + TextUtil.toProperCase(desc)));
        }

        if (!sigil.getEffects().isEmpty()) {
            lore.add(Component.empty());
            lore.add(TextUtil.parseComponent("&b&lWhen Empowered:"));
            for (String triggerKey : sigil.getEffects().keySet()) {
                String triggerName = TextUtil.toProperCase(triggerKey.replace("_", " "));
                String description = TextUtil.getTriggerDescription(triggerKey);
                lore.add(TextUtil.parseComponent("&b• &3" + triggerName));
                lore.add(TextUtil.parseComponent("&7  " + TextUtil.toProperCase(description)));
                var triggerConfig = sigil.getEffects().get(triggerKey);
                for (String effect : triggerConfig.getEffects()) {
                    String effectDesc = TextUtil.getEffectDescription(effect);
                    lore.add(TextUtil.parseComponent("&8    →&7 " + TextUtil.toProperCase(effectDesc)));
                }
            }
        }

        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("&8ID: &f" + sigil.getId()));
        lore.add(TextUtil.parseComponent("&8Tier: &f" + sigil.getTier()));
        lore.add(TextUtil.parseComponent("&8Slot: &f" + TextUtil.toProperCase(sigil.getSlot())));
        meta.lore(lore);
        sigilDisplay.setItemMeta(meta);
        inv.setItem(4, sigilDisplay);

        // View Effects
        ItemStack viewEffects = createGuiItem(Material.REDSTONE, "&cEffect Triggers", "&7View current effects");
        inv.setItem(10, viewEffects);

        // Edit Item Form
        ItemStack editForm = createGuiItem(Material.CHEST, "&bItem Display", "&7Customize shard appearance");
        inv.setItem(12, editForm);

        // Add New Trigger
        ItemStack addTrigger = createGuiItem(Material.LIME_DYE, "&aAdd Trigger", "&7Add new trigger");
        inv.setItem(14, addTrigger);

        // Remove Trigger
        ItemStack removeTrigger = createGuiItem(Material.RED_DYE, "&cRemove Trigger", "&7Remove existing trigger");
        inv.setItem(15, removeTrigger);

        // Export to YAML
        ItemStack export = createGuiItem(Material.PAPER, "&eExport Config", "&7Generate YAML file");
        inv.setItem(16, export);

        // Back button
        ItemStack back = createGuiItem(Material.BARRIER, "&cBack", "");
        inv.setItem(31, back);

        GUISession session = new GUISession(GUIType.FUNCTION_EDITOR);
        session.put("sigilId", sigil.getId());
        session.put("sigil", sigil);
        openGUI(player, inv, session);
    }

    // ========== SET VIEWER GUIS ==========

    /**
     * Opens a GUI displaying individual effects for each armor piece in a set.
     * Shows helmet, chestplate, leggings, and boots effects separately.
     */
    public void openSetEffectsViewer(Player player, com.zenax.armorsets.sets.ArmorSet set) {
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent("&8Individual Effects: &b" + set.getId()));

        // Border items
        ItemStack border = createBorderItem();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
            inv.setItem(36 + i, border);
        }

        // Armor slot display positions: helmet=10, chestplate=12, leggings=14, boots=16
        String[] slots = {"helmet", "chestplate", "leggings", "boots"};
        Material[] materials = {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
        int[] positions = {10, 12, 14, 16};

        var individualEffects = set.getIndividualEffects();

        for (int i = 0; i < slots.length; i++) {
            String slotName = slots[i];
            ItemStack pieceItem = new ItemStack(materials[i]);
            ItemMeta meta = pieceItem.getItemMeta();

            meta.displayName(TextUtil.parseComponent("&b" + TextUtil.toProperCase(slotName)));

            List<Component> lore = new ArrayList<>();
            var slotEffects = individualEffects.get(slotName);

            if (slotEffects != null && !slotEffects.isEmpty()) {
                lore.add(TextUtil.parseComponent("&7Effects for this piece:"));
                lore.add(Component.empty());

                for (Map.Entry<String, com.zenax.armorsets.sets.TriggerConfig> entry : slotEffects.entrySet()) {
                    String triggerKey = entry.getKey();
                    com.zenax.armorsets.sets.TriggerConfig config = entry.getValue();

                    String triggerName = TextUtil.toProperCase(triggerKey.replace("_", " "));
                    String triggerDesc = TextUtil.getTriggerDescription(triggerKey);

                    lore.add(TextUtil.parseComponent("&b" + triggerName));
                    lore.add(TextUtil.parseComponent("&8  " + triggerDesc));
                    lore.add(TextUtil.parseComponent("&8  Chance: &f" + config.getChance() + "%"));
                    if (config.getCooldown() > 0) {
                        lore.add(TextUtil.parseComponent("&8  Cooldown: &f" + config.getCooldown() + "s"));
                    }

                    for (String effect : config.getEffects()) {
                        String effectDesc = TextUtil.getEffectDescription(effect);
                        lore.add(TextUtil.parseComponent("&7    -> " + TextUtil.toProperCase(effectDesc)));
                    }
                    lore.add(Component.empty());
                }
            } else {
                lore.add(TextUtil.parseComponent("&8No effects configured"));
            }

            meta.lore(lore);
            pieceItem.setItemMeta(meta);
            inv.setItem(positions[i], pieceItem);
        }

        // Set info in center
        ItemStack setInfo = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = setInfo.getItemMeta();
        infoMeta.displayName(TextUtil.parseComponent("&e" + set.getId()));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(TextUtil.parseComponent("&8Tier: &f" + set.getTier()));
        infoLore.add(TextUtil.parseComponent("&8Material: &f" + set.getMaterial().name()));
        infoLore.add(Component.empty());
        infoLore.add(TextUtil.parseComponent("&7Individual effects are triggered"));
        infoLore.add(TextUtil.parseComponent("&7when wearing specific armor pieces."));
        infoMeta.lore(infoLore);
        setInfo.setItemMeta(infoMeta);
        inv.setItem(22, setInfo);

        // Back button
        ItemStack back = createGuiItem(Material.BARRIER, "&cBack", "&7Return to set editor");
        inv.setItem(40, back);

        GUISession session = new GUISession(GUIType.SET_EFFECTS_VIEWER);
        session.put("setId", set.getId());
        session.put("set", set);
        openGUI(player, inv, session);
    }

    /**
     * Opens a GUI displaying all set synergies (full set bonuses).
     */
    public void openSetSynergiesViewer(Player player, com.zenax.armorsets.sets.ArmorSet set) {
        var synergies = set.getSynergies();
        int synergyCount = synergies.size();

        // Calculate inventory size based on synergies (min 27, max 54)
        int size = Math.max(27, Math.min((int) Math.ceil((synergyCount + 9) / 9.0) * 9, 54));
        Inventory inv = Bukkit.createInventory(null, size, TextUtil.parseComponent("&8Set Synergies: &b" + set.getId()));

        // Border items for top row
        ItemStack border = createBorderItem();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }

        // Set info at top center
        ItemStack setInfo = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = setInfo.getItemMeta();
        infoMeta.displayName(TextUtil.parseComponent("&b" + set.getId() + " &8- &eSynergies"));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(TextUtil.parseComponent("&8Tier: &f" + set.getTier()));
        infoLore.add(TextUtil.parseComponent("&8Total Synergies: &f" + synergyCount));
        infoLore.add(Component.empty());
        infoLore.add(TextUtil.parseComponent("&7Synergies activate when wearing"));
        infoLore.add(TextUtil.parseComponent("&7the full armor set (4 pieces)."));
        infoMeta.lore(infoLore);
        setInfo.setItemMeta(infoMeta);
        inv.setItem(4, setInfo);

        if (synergies.isEmpty()) {
            // No synergies message
            ItemStack noSynergies = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta noMeta = noSynergies.getItemMeta();
            noMeta.displayName(TextUtil.parseComponent("&8No Synergies"));
            noMeta.lore(List.of(TextUtil.parseComponent("&7This set has no synergy effects.")));
            noSynergies.setItemMeta(noMeta);
            inv.setItem(13, noSynergies);
        } else {
            // Display each synergy
            int slot = 9;
            for (com.zenax.armorsets.sets.SetSynergy synergy : synergies) {
                if (slot >= size - 1) break; // Leave room for back button

                ItemStack synergyItem = new ItemStack(Material.ENCHANTED_BOOK);
                ItemMeta meta = synergyItem.getItemMeta();

                meta.displayName(TextUtil.parseComponent("&d" + TextUtil.toProperCase(synergy.getId().replace("_", " "))));

                List<Component> lore = new ArrayList<>();
                String triggerKey = synergy.getTrigger().getConfigKey();
                String triggerName = TextUtil.toProperCase(triggerKey.replace("_", " "));
                String triggerDesc = TextUtil.getTriggerDescription(triggerKey);
                com.zenax.armorsets.sets.TriggerConfig config = synergy.getTriggerConfig();

                lore.add(TextUtil.parseComponent("&bTrigger: &f" + triggerName));
                lore.add(TextUtil.parseComponent("&8  " + triggerDesc));
                lore.add(Component.empty());
                lore.add(TextUtil.parseComponent("&eChance: &f" + config.getChance() + "%"));
                if (config.getCooldown() > 0) {
                    lore.add(TextUtil.parseComponent("&eCooldown: &f" + config.getCooldown() + "s"));
                }
                lore.add(Component.empty());
                lore.add(TextUtil.parseComponent("&aEffects:"));

                for (String effect : config.getEffects()) {
                    String effectDesc = TextUtil.getEffectDescription(effect);
                    lore.add(TextUtil.parseComponent("&7  -> " + TextUtil.toProperCase(effectDesc)));
                }

                meta.lore(lore);
                synergyItem.setItemMeta(meta);
                inv.setItem(slot, synergyItem);
                slot++;
            }
        }

        // Back button at bottom right
        ItemStack back = createGuiItem(Material.BARRIER, "&cBack", "&7Return to set editor");
        inv.setItem(size - 1, back);

        GUISession session = new GUISession(GUIType.SET_SYNERGIES_VIEWER);
        session.put("setId", set.getId());
        session.put("set", set);
        openGUI(player, inv, session);
    }

    /**
     * Opens a GUI to remove triggers from a set or sigil.
     */
    public void openTriggerRemover(Player player, String buildType, String buildId, Object target) {
        Map<String, com.zenax.armorsets.sets.TriggerConfig> triggers = new HashMap<>();
        String title;

        if ("set".equalsIgnoreCase(buildType) && target instanceof com.zenax.armorsets.sets.ArmorSet set) {
            title = "&8Remove Trigger: &b" + set.getId();
            // Collect all triggers from individual effects
            for (Map.Entry<String, Map<String, com.zenax.armorsets.sets.TriggerConfig>> slotEntry : set.getIndividualEffects().entrySet()) {
                String slot = slotEntry.getKey();
                for (Map.Entry<String, com.zenax.armorsets.sets.TriggerConfig> triggerEntry : slotEntry.getValue().entrySet()) {
                    triggers.put(slot + ":" + triggerEntry.getKey(), triggerEntry.getValue());
                }
            }
            // Also add synergy triggers
            for (com.zenax.armorsets.sets.SetSynergy synergy : set.getSynergies()) {
                triggers.put("synergy:" + synergy.getId(), synergy.getTriggerConfig());
            }
        } else if ("sigil".equalsIgnoreCase(buildType) && target instanceof Sigil sigil) {
            title = "&8Remove Trigger: &d" + sigil.getName();
            triggers.putAll(sigil.getEffects());
        } else {
            player.sendMessage(TextUtil.colorize("&cInvalid target for trigger removal"));
            return;
        }

        if (triggers.isEmpty()) {
            player.sendMessage(TextUtil.colorize("&cNo triggers to remove."));
            return;
        }

        int size = Math.max(27, Math.min((int) Math.ceil((triggers.size() + 9) / 9.0) * 9, 54));
        Inventory inv = Bukkit.createInventory(null, size, TextUtil.parseComponent(title));

        // Border top row
        ItemStack border = createBorderItem();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }

        // Add trigger items
        int slot = 9;
        for (Map.Entry<String, com.zenax.armorsets.sets.TriggerConfig> entry : triggers.entrySet()) {
            if (slot >= size - 1) break;

            String triggerKey = entry.getKey();
            com.zenax.armorsets.sets.TriggerConfig config = entry.getValue();

            ItemStack triggerItem = new ItemStack(Material.TNT);
            ItemMeta meta = triggerItem.getItemMeta();

            // Parse display name
            String displayName = triggerKey.contains(":")
                ? TextUtil.toProperCase(triggerKey.replace(":", " - ").replace("_", " "))
                : TextUtil.toProperCase(triggerKey.replace("_", " "));
            meta.displayName(TextUtil.parseComponent("&c" + displayName));

            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parseComponent("&8Chance: &f" + config.getChance() + "%"));
            if (config.getCooldown() > 0) {
                lore.add(TextUtil.parseComponent("&8Cooldown: &f" + config.getCooldown() + "s"));
            }
            lore.add(Component.empty());
            lore.add(TextUtil.parseComponent("&7Effects:"));
            for (String effect : config.getEffects()) {
                lore.add(TextUtil.parseComponent("&8  - &f" + effect));
            }
            lore.add(Component.empty());
            lore.add(TextUtil.parseComponent("&c&lClick to remove!"));

            // Store the trigger key in PDC for identification
            meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "trigger_key"),
                PersistentDataType.STRING,
                triggerKey
            );

            meta.lore(lore);
            triggerItem.setItemMeta(meta);
            inv.setItem(slot, triggerItem);
            slot++;
        }

        // Back button
        ItemStack back = createGuiItem(Material.BARRIER, "&cBack", "&7Return to editor");
        inv.setItem(size - 1, back);

        GUISession session = new GUISession(GUIType.TRIGGER_REMOVER);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        session.put("target", target);
        openGUI(player, inv, session);
    }

    /**
     * Exports an ArmorSet to YAML format and saves to the armor-sets directory.
     */
    public void exportSetToYAML(Player player, com.zenax.armorsets.sets.ArmorSet set) {
        // Create the export directory
        File exportDir = new File(plugin.getDataFolder(), "armor-sets");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        // Generate filename from set ID
        String fileName = set.getId().toLowerCase().replace(" ", "_") + ".yml";
        File exportFile = new File(exportDir, fileName);

        try (FileWriter writer = new FileWriter(exportFile)) {
            StringBuilder yaml = new StringBuilder();

            // Extract base name and tier from set ID (format: baseName_tTier)
            String setId = set.getId();
            String baseName = setId;
            int tier = set.getTier();

            if (setId.contains("_t")) {
                int lastIndex = setId.lastIndexOf("_t");
                baseName = setId.substring(0, lastIndex);
            }

            // Header comment
            yaml.append("# Armor Set Configuration\n");
            yaml.append("# Exported from ArmorSets Plugin\n");
            yaml.append("# Set: ").append(setId).append("\n\n");

            // Base set name
            yaml.append(baseName).append(":\n");
            yaml.append("  tiers:\n");
            yaml.append("    ").append(tier).append(":\n");

            // Basic properties
            yaml.append("      name_pattern: \"").append(escapeYaml(set.getNamePattern())).append("\"\n");
            yaml.append("      material: ").append(set.getMaterial().name()).append("\n");

            // Equipped messages
            List<String> equippedMsg = set.getEquippedMessage();
            if (!equippedMsg.isEmpty()) {
                yaml.append("      equipped_message:\n");
                for (String msg : equippedMsg) {
                    yaml.append("        - \"").append(escapeYaml(msg)).append("\"\n");
                }
            }

            // Unequipped messages
            List<String> unequippedMsg = set.getUnequippedMessage();
            if (!unequippedMsg.isEmpty()) {
                yaml.append("      unequipped_message:\n");
                for (String msg : unequippedMsg) {
                    yaml.append("        - \"").append(escapeYaml(msg)).append("\"\n");
                }
            }

            // Individual effects
            var individualEffects = set.getIndividualEffects();
            if (!individualEffects.isEmpty()) {
                yaml.append("\n      individual_effects:\n");
                for (Map.Entry<String, Map<String, com.zenax.armorsets.sets.TriggerConfig>> slotEntry : individualEffects.entrySet()) {
                    String slotName = slotEntry.getKey();
                    Map<String, com.zenax.armorsets.sets.TriggerConfig> triggers = slotEntry.getValue();

                    if (!triggers.isEmpty()) {
                        yaml.append("        ").append(slotName).append(":\n");
                        for (Map.Entry<String, com.zenax.armorsets.sets.TriggerConfig> triggerEntry : triggers.entrySet()) {
                            String triggerName = triggerEntry.getKey();
                            com.zenax.armorsets.sets.TriggerConfig config = triggerEntry.getValue();

                            yaml.append("          ").append(triggerName).append(":\n");
                            yaml.append("            chance: ").append(config.getChance()).append("\n");

                            List<String> effects = config.getEffects();
                            if (!effects.isEmpty()) {
                                yaml.append("            effects:\n");
                                for (String effect : effects) {
                                    yaml.append("              - ").append(effect).append("\n");
                                }
                            }

                            if (config.getCooldown() > 0) {
                                yaml.append("            cooldown: ").append(config.getCooldown()).append("\n");
                            }

                            List<String> conditions = config.getConditions();
                            if (conditions != null && !conditions.isEmpty()) {
                                yaml.append("            conditions:\n");
                                for (String condition : conditions) {
                                    yaml.append("              - ").append(condition).append("\n");
                                }
                            }
                        }
                    }
                }
            }

            // Synergies
            var synergies = set.getSynergies();
            if (!synergies.isEmpty()) {
                yaml.append("\n      synergies:\n");
                for (com.zenax.armorsets.sets.SetSynergy synergy : synergies) {
                    yaml.append("        ").append(synergy.getId()).append(":\n");
                    yaml.append("          trigger: ").append(synergy.getTrigger().getConfigKey()).append("\n");

                    com.zenax.armorsets.sets.TriggerConfig config = synergy.getTriggerConfig();
                    yaml.append("          chance: ").append(config.getChance()).append("\n");

                    List<String> effects = config.getEffects();
                    if (!effects.isEmpty()) {
                        yaml.append("          effects:\n");
                        for (String effect : effects) {
                            yaml.append("            - ").append(effect).append("\n");
                        }
                    }

                    if (config.getCooldown() > 0) {
                        yaml.append("          cooldown: ").append(config.getCooldown()).append("\n");
                    }
                }
            }

            writer.write(yaml.toString());
            writer.flush();

            player.sendMessage(TextUtil.colorize("&aSuccessfully exported set to:"));
            player.sendMessage(TextUtil.colorize("&7" + exportFile.getPath()));
            playSound(player, "socket");

        } catch (IOException e) {
            player.sendMessage(TextUtil.colorize("&cFailed to export set: " + e.getMessage()));
            plugin.getLogger().warning("Failed to export set " + set.getId() + ": " + e.getMessage());
            playSound(player, "error");
        }
    }

    /**
     * Escapes special characters for YAML string values.
     */
    private String escapeYaml(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ========== ITEM CREATORS ==========

    private ItemStack createSetBrowserItem(com.zenax.armorsets.sets.ArmorSet set) {
        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(TextUtil.parseComponent("&b" + set.getId()));

        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.parseComponent("&8Tier: &f" + set.getTier()));
        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("&7Click for details"));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSigilBrowserItem(Sigil sigil) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(TextUtil.parseComponent("&d" + sigil.getName()));

        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.parseComponent("&8ID: &f" + sigil.getId()));
        lore.add(TextUtil.parseComponent("&8Max Tier: &f" + sigil.getMaxTier()));
        lore.add(TextUtil.parseComponent("&8Slot: &f" + TextUtil.toProperCase(sigil.getSlot())));
        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("&7Click for details"));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ========== CLICK HANDLERS ==========

    private void handleBuildMainMenuClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        switch (slot) {
            case 10 -> player.sendMessage(TextUtil.colorize("&e[CREATE SET] Feature coming soon"));
            case 12 -> player.sendMessage(TextUtil.colorize("&e[CREATE SIGIL] Feature coming soon"));
            case 14 -> openSetBrowser(player); // Edit Set
            case 16 -> openSigilBrowser(player); // Edit Sigil
            case 26 -> player.closeInventory();
        }
    }

    private void handleSetBrowserClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);

        if (item == null || item.getType().isAir()) {
            return;
        }

        // Check for back button
        if (item.getType() == Material.BARRIER) {
            openBuildMainMenu(player);
            return;
        }

        // Check for set item
        if (item.getType() == Material.DIAMOND_CHESTPLATE && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String clickedDisplayName = item.getItemMeta().getDisplayName();
            // Extract plain text from the display name using Component or plain string
            String plainName = clickedDisplayName.replaceAll("§[0-9a-fk-or]", "")
                    .replaceAll("&[0-9a-fk-or]", "");

            // Find the clicked set
            var allSets = plugin.getSetManager().getAllSets();
            for (var set : allSets) {
                if (set.getId().equalsIgnoreCase(plainName)) {
                    openSetEditor(player, set);
                    return;
                }
            }
            player.sendMessage(TextUtil.colorize("&cSet not found: '" + plainName + "'"));
        }
    }

    private void handleFunctionBrowserClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);

        if (item == null || item.getType().isAir()) {
            return;
        }

        // Check for back button
        if (item.getType() == Material.BARRIER) {
            openBuildMainMenu(player);
            return;
        }

        // Check for sigil item
        if (item.getType() == Material.NETHER_STAR && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String clickedDisplayName = item.getItemMeta().getDisplayName();
            // Extract plain text from the display name
            String plainName = clickedDisplayName.replaceAll("§[0-9a-fk-or]", "")
                    .replaceAll("&[0-9a-fk-or]", "");

            // Find the clicked sigil
            var allSigils = plugin.getSigilManager().getAllSigils();
            for (var sigil : allSigils) {
                if (sigil.getName().equalsIgnoreCase(plainName)) {
                    openSigilEditor(player, sigil);
                    return;
                }
            }
            player.sendMessage(TextUtil.colorize("&cSigil not found: '" + plainName + "'"));
        }
    }

    private void handleSlotSelectorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String buildId = (String) session.get("buildId");

        // Back button
        if (slot == 22) {
            com.zenax.armorsets.sets.ArmorSet set = plugin.getSetManager().getSet(buildId);
            if (set != null) {
                openSetEditor(player, set);
            } else {
                openSetBrowser(player);
            }
            return;
        }

        // Slot selection
        String armorSlot = switch (slot) {
            case 10 -> "helmet";
            case 12 -> "chestplate";
            case 14 -> "leggings";
            case 16 -> "boots";
            default -> null;
        };

        if (armorSlot != null) {
            // Open trigger selector with the armor slot stored
            openTriggerSelectorWithSlot(player, "set", buildId, armorSlot);
        }
    }

    private void handleTriggerSelectorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        if (slot == 44) {
            // Back button - return to the appropriate editor
            String buildType = (String) session.get("buildType");
            String buildId = (String) session.get("buildId");

            if ("sigil".equalsIgnoreCase(buildType)) {
                Sigil sigil = plugin.getSigilManager().getSigil(buildId);
                if (sigil != null) {
                    openSigilEditor(player, sigil);
                } else {
                    openSigilBrowser(player);
                }
            } else if ("set".equalsIgnoreCase(buildType)) {
                com.zenax.armorsets.sets.ArmorSet set = plugin.getSetManager().getSet(buildId);
                if (set != null) {
                    openSetEditor(player, set);
                } else {
                    openSetBrowser(player);
                }
            } else {
                openBuildMainMenu(player);
            }
            return;
        }

        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);

        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String trigger = item.getItemMeta().getDisplayName()
                    .replace("§f", "")
                    .replace("&f", "");

            String buildType = (String) session.get("buildType");
            String buildId = (String) session.get("buildId");
            String armorSlot = (String) session.get("armorSlot");

            // Store the selected trigger in the session for use in effect selector
            session.put("selectedTrigger", trigger);

            openEffectSelectorWithSlot(player, buildType, buildId, trigger, armorSlot);
        }
    }

    private void handleEffectSelectorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        if (slot == 44) {
            String buildType = (String) session.get("buildType");
            String buildId = (String) session.get("buildId");
            openTriggerSelector(player, buildType, buildId);
            return;
        }

        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);

        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String effect = item.getItemMeta().getDisplayName()
                    .replace("§f", "")
                    .replace("&f", "");

            String buildType = (String) session.get("buildType");
            String buildId = (String) session.get("buildId");
            String trigger = (String) session.get("trigger");
            String armorSlot = (String) session.get("armorSlot");

            // Open config GUI with default values (100% chance, 0 cooldown)
            openTriggerConfig(player, buildType, buildId, trigger, effect, armorSlot, 100, 0);
        }
    }

    private void handleTriggerConfigClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String buildType = (String) session.get("buildType");
        String buildId = (String) session.get("buildId");
        String trigger = (String) session.get("trigger");
        String effect = (String) session.get("effect");
        String armorSlot = (String) session.get("armorSlot");
        double chance = (Double) session.get("chance");
        double cooldown = (Double) session.get("cooldown");

        // Handle chance adjustments
        switch (slot) {
            case 19 -> chance = Math.max(0, chance - 10); // -10%
            case 20 -> chance = Math.max(0, chance - 1);  // -1%
            case 24 -> chance = Math.min(100, chance + 1); // +1%
            case 25 -> chance = Math.min(100, chance + 10); // +10%
            case 28 -> cooldown = Math.max(0, cooldown - 5); // -5s
            case 29 -> cooldown = Math.max(0, cooldown - 1); // -1s
            case 33 -> cooldown = Math.min(300, cooldown + 1); // +1s
            case 34 -> cooldown = Math.min(300, cooldown + 5); // +5s
            case 39 -> { // Confirm
                addTriggerEffect(player, buildType, buildId, trigger, effect, armorSlot, chance, cooldown);
                return;
            }
            case 41 -> { // Cancel - go back to effect selector
                openEffectSelectorWithSlot(player, buildType, buildId, trigger, armorSlot);
                return;
            }
            default -> { return; } // Ignore other slots
        }

        // Refresh GUI with new values
        openTriggerConfig(player, buildType, buildId, trigger, effect, armorSlot, chance, cooldown);
    }

    /**
     * Actually adds the trigger effect after configuration.
     */
    private void addTriggerEffect(Player player, String buildType, String buildId, String trigger, String effect, String armorSlot, double chance, double cooldown) {
        String triggerKey = trigger.equalsIgnoreCase("EFFECT_STATIC") ? "effect_static" : "on_" + trigger.toLowerCase();

        if ("sigil".equalsIgnoreCase(buildType)) {
            Sigil sigil = plugin.getSigilManager().getSigil(buildId);
            if (sigil != null) {
                com.zenax.armorsets.sets.TriggerConfig triggerConfig = sigil.getEffects().get(triggerKey);
                if (triggerConfig == null) {
                    triggerConfig = new com.zenax.armorsets.sets.TriggerConfig();
                    sigil.getEffects().put(triggerKey, triggerConfig);
                }

                triggerConfig.setChance(chance);
                triggerConfig.setCooldown(cooldown);
                if (!triggerConfig.getEffects().contains(effect)) {
                    triggerConfig.getEffects().add(effect);
                }

                player.sendMessage(TextUtil.colorize("&aAdded &f" + effect + " &ato &f" + trigger + " &a(" + (int)chance + "% chance, " + cooldown + "s cooldown)"));
                playSound(player, "socket");
                openSigilEditor(player, sigil);
            } else {
                player.sendMessage(TextUtil.colorize("&cSigil not found"));
                openSigilBrowser(player);
            }
        } else if ("set".equalsIgnoreCase(buildType)) {
            com.zenax.armorsets.sets.ArmorSet set = plugin.getSetManager().getSet(buildId);
            if (set != null) {
                if (armorSlot == null) armorSlot = "helmet";

                Map<String, com.zenax.armorsets.sets.TriggerConfig> slotEffects = set.getIndividualEffects().get(armorSlot);
                if (slotEffects == null) {
                    slotEffects = new HashMap<>();
                    set.getIndividualEffects().put(armorSlot, slotEffects);
                }

                com.zenax.armorsets.sets.TriggerConfig triggerConfig = slotEffects.get(triggerKey);
                if (triggerConfig == null) {
                    triggerConfig = new com.zenax.armorsets.sets.TriggerConfig();
                    slotEffects.put(triggerKey, triggerConfig);
                }

                triggerConfig.setChance(chance);
                triggerConfig.setCooldown(cooldown);
                if (!triggerConfig.getEffects().contains(effect)) {
                    triggerConfig.getEffects().add(effect);
                }

                player.sendMessage(TextUtil.colorize("&aAdded &f" + effect + " &ato &f" + armorSlot + " " + trigger + " &a(" + (int)chance + "% chance, " + cooldown + "s cooldown)"));
                playSound(player, "socket");
                openSetEditor(player, set);
            } else {
                player.sendMessage(TextUtil.colorize("&cSet not found"));
                openSetBrowser(player);
            }
        }
    }

    private void handleConfirmationClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        if (slot == 11) {
            // Confirmed action
            String actionType = (String) session.get("actionType");
            Object actionData = session.get("actionData");
            player.sendMessage(TextUtil.colorize("&aAction confirmed: " + actionType));
            player.closeInventory();
        } else if (slot == 15) {
            // Cancelled action
            player.closeInventory();
        }
    }

    private void handleSetEditorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        com.zenax.armorsets.sets.ArmorSet set = (com.zenax.armorsets.sets.ArmorSet) session.get("set");
        String setId = (String) session.get("setId");

        switch (slot) {
            case 10 -> {
                // View Individual Effects
                if (set != null) {
                    openSetEffectsViewer(player, set);
                } else {
                    player.sendMessage(TextUtil.colorize("&cError: Set not found in session"));
                }
            }
            case 12 -> {
                // View Set Synergies
                if (set != null) {
                    openSetSynergiesViewer(player, set);
                } else {
                    player.sendMessage(TextUtil.colorize("&cError: Set not found in session"));
                }
            }
            case 14 -> {
                // Add Trigger - opens slot selector first for sets
                if (setId != null) {
                    openSlotSelector(player, setId);
                } else {
                    player.sendMessage(TextUtil.colorize("&cError: Set ID not found in session"));
                }
            }
            case 15 -> {
                // Remove Trigger
                if (set != null) {
                    openTriggerRemover(player, "set", setId, set);
                } else {
                    player.sendMessage(TextUtil.colorize("&cError: Set not found in session"));
                }
            }
            case 16 -> {
                // Export to YAML
                if (set != null) {
                    exportSetToYAML(player, set);
                } else {
                    player.sendMessage(TextUtil.colorize("&cError: Set not found in session"));
                }
            }
            case 31 -> {
                // Back button - return to set browser
                openSetBrowser(player);
            }
        }
    }

    private void handleFunctionEditorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Sigil sigil = (Sigil) session.get("sigil");
        String sigilId = (String) session.get("sigilId");

        switch (slot) {
            case 10 -> {
                // View Effects
                player.sendMessage(TextUtil.colorize("&6[EFFECT TRIGGERS] Coming soon"));
            }
            case 12 -> {
                // Edit Item Form
                player.sendMessage(TextUtil.colorize("&6[ITEM DISPLAY] Coming soon"));
            }
            case 14 -> {
                // Add Trigger
                if (sigilId != null) {
                    openTriggerSelector(player, "sigil", sigilId);
                } else {
                    player.sendMessage(TextUtil.colorize("&cError: Sigil ID not found"));
                }
            }
            case 15 -> {
                // Remove Trigger
                if (sigil != null) {
                    openTriggerRemover(player, "sigil", sigilId, sigil);
                } else {
                    player.sendMessage(TextUtil.colorize("&cError: Sigil not found"));
                }
            }
            case 16 -> {
                // Export to YAML
                player.sendMessage(TextUtil.colorize("&6[EXPORT] Coming soon"));
            }
            case 31 -> {
                // Back button - return to sigil browser
                openSigilBrowser(player);
            }
        }
    }

    private void handleSetEffectsViewerClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        // Back button is at slot 40
        if (slot == 40) {
            com.zenax.armorsets.sets.ArmorSet set = (com.zenax.armorsets.sets.ArmorSet) session.get("set");
            if (set != null) {
                openSetEditor(player, set);
            } else {
                openSetBrowser(player);
            }
            playSound(player, "close");
        }
        // Armor piece slots (10, 12, 14, 16) and info slot (22) are display-only
    }

    private void handleSetSynergiesViewerClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        int lastSlot = inv.getSize() - 1;

        // Back button is at the last slot
        if (slot == lastSlot) {
            com.zenax.armorsets.sets.ArmorSet set = (com.zenax.armorsets.sets.ArmorSet) session.get("set");
            if (set != null) {
                openSetEditor(player, set);
            } else {
                openSetBrowser(player);
            }
            playSound(player, "close");
        }
        // Synergy items are display-only
    }

    private void handleTriggerRemoverClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        int lastSlot = inv.getSize() - 1;

        // Back button
        if (slot == lastSlot) {
            String buildType = (String) session.get("buildType");
            Object target = session.get("target");

            if ("set".equalsIgnoreCase(buildType) && target instanceof com.zenax.armorsets.sets.ArmorSet set) {
                openSetEditor(player, set);
            } else if ("sigil".equalsIgnoreCase(buildType) && target instanceof Sigil sigil) {
                openSigilEditor(player, sigil);
            } else {
                openBuildMainMenu(player);
            }
            return;
        }

        // Check if clicked item is a trigger
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getType() != Material.TNT || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        String triggerKey = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "trigger_key"),
            PersistentDataType.STRING
        );

        if (triggerKey == null) {
            return;
        }

        String buildType = (String) session.get("buildType");
        Object target = session.get("target");

        boolean removed = false;

        if ("set".equalsIgnoreCase(buildType) && target instanceof com.zenax.armorsets.sets.ArmorSet set) {
            // Parse the trigger key format: "slot:trigger" or "synergy:id"
            if (triggerKey.startsWith("synergy:")) {
                String synergyId = triggerKey.substring("synergy:".length());
                removed = set.getSynergies().removeIf(s -> s.getId().equals(synergyId));
            } else if (triggerKey.contains(":")) {
                String[] parts = triggerKey.split(":", 2);
                String armorSlot = parts[0];
                String trigger = parts[1];
                Map<String, com.zenax.armorsets.sets.TriggerConfig> slotEffects = set.getIndividualEffects().get(armorSlot);
                if (slotEffects != null) {
                    removed = slotEffects.remove(trigger) != null;
                }
            }

            if (removed) {
                player.sendMessage(TextUtil.colorize("&aRemoved trigger: &f" + triggerKey));
                playSound(player, "unsocket");
                // Refresh the GUI
                openTriggerRemover(player, buildType, (String) session.get("buildId"), set);
            } else {
                player.sendMessage(TextUtil.colorize("&cFailed to remove trigger"));
                playSound(player, "error");
            }

        } else if ("sigil".equalsIgnoreCase(buildType) && target instanceof Sigil sigil) {
            removed = sigil.getEffects().remove(triggerKey) != null;

            if (removed) {
                player.sendMessage(TextUtil.colorize("&aRemoved trigger: &f" + triggerKey));
                playSound(player, "unsocket");
                // Refresh the GUI
                openTriggerRemover(player, buildType, (String) session.get("buildId"), sigil);
            } else {
                player.sendMessage(TextUtil.colorize("&cFailed to remove trigger"));
                playSound(player, "error");
            }
        }
    }

}
