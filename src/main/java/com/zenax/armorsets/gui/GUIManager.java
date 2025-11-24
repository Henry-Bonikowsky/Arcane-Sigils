package com.zenax.armorsets.gui;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.gui.handlers.*;
import com.zenax.armorsets.sets.ArmorSet;
import com.zenax.armorsets.sets.SetSynergy;
import com.zenax.armorsets.sets.TriggerConfig;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages GUI interfaces for the ArmorSets plugin.
 * This class acts as a coordinator, delegating click handling to specialized handlers.
 */
public class GUIManager implements Listener, GUIHandlerContext {

    private final ArmorSetsPlugin plugin;
    private final Map<UUID, GUISession> activeSessions = new HashMap<>();
    private final Set<UUID> transitioning = new HashSet<>();
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private final Map<UUID, GUISession> pendingMessageInputs = new HashMap<>();
    private static final long CLICK_COOLDOWN_MS = 250;

    // Handlers
    private final List<GUIHandler> handlers = new ArrayList<>();
    private final SocketGUIHandler socketHandler;
    private final UnsocketGUIHandler unsocketHandler;
    private final ConfigHandler configHandler;

    public GUIManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;

        // Initialize handlers
        this.socketHandler = new SocketGUIHandler(plugin, this);
        this.unsocketHandler = new UnsocketGUIHandler(plugin, this);
        this.configHandler = new ConfigHandler(plugin, this);

        // Register handlers
        handlers.add(socketHandler);
        handlers.add(unsocketHandler);
        handlers.add(configHandler);
    }

    // ===== EVENT HANDLERS =====

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GUISession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        event.setCancelled(true);

        // Click cooldown
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(playerId);
        if (lastClick != null && (now - lastClick) < CLICK_COOLDOWN_MS) {
            return;
        }
        lastClickTime.put(playerId, now);

        int slot = event.getRawSlot();

        // Find appropriate handler
        for (GUIHandler handler : handlers) {
            if (handler.canHandle(session.getType())) {
                handler.handleClick(player, session, slot, event);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            UUID uuid = player.getUniqueId();
            if (transitioning.contains(uuid)) {
                transitioning.remove(uuid);
                return;
            }
            if (pendingMessageInputs.containsKey(uuid)) {
                return;
            }
            activeSessions.remove(uuid);
            lastClickTime.remove(uuid);
            playSound(player, "close");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        if (message.startsWith("/as input ") && hasPendingMessageInput(player.getUniqueId())) {
            event.setCancelled(true);
            String input = message.substring(10).trim();
            plugin.getLogger().info("Capturing input from " + player.getName() + ": " + input);
            handleMessageInput(player, input);
        }
    }

    // ===== GUIHandlerContext IMPLEMENTATION =====

    @Override
    public void openGUI(Player player, Inventory inv, GUISession session) {
        UUID uuid = player.getUniqueId();
        if (activeSessions.containsKey(uuid)) {
            transitioning.add(uuid);
        }
        activeSessions.put(uuid, session);
        player.openInventory(inv);
        playSound(player, "open");
    }

    @Override
    public void openSocketGUI(Player player, ItemStack armor, int armorSlot) {
        socketHandler.openSocketGUI(player, armor, armorSlot);
    }

    @Override
    public void openUnsocketGUI(Player player, ItemStack armor, int armorSlot) {
        unsocketHandler.openUnsocketGUI(player, armor, armorSlot);
    }

    @Override
    public void openBuildMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("&8Build Menu"));

        ItemStack createSet = ItemBuilder.createGuiItem(Material.DIAMOND_CHESTPLATE, "&b&lCreate Set", "&7Build new armor set");
        ItemStack createSigil = ItemBuilder.createGuiItem(Material.ECHO_SHARD, "&5&lCreate Sigil", "&7Build new sigil");
        ItemStack editSet = ItemBuilder.createGuiItem(Material.COMPARATOR, "&6&lEdit Set", "&7Modify existing set");
        ItemStack editSigil = ItemBuilder.createGuiItem(Material.REDSTONE, "&c&lEdit Sigil", "&7Modify existing sigil");
        ItemStack back = ItemBuilder.createGuiItem(Material.BARRIER, "&cClose", "&7Close menu");

        inv.setItem(10, createSet);
        inv.setItem(12, createSigil);
        inv.setItem(14, editSet);
        inv.setItem(16, editSigil);
        inv.setItem(26, back);

        GUISession session = new GUISession(GUIType.BUILD_MAIN_MENU);
        openGUI(player, inv, session);
    }

    @Override
    public void openSetBrowser(Player player) {
        var allSets = plugin.getSetManager().getAllSets();
        if (allSets.isEmpty()) {
            player.sendMessage(TextUtil.colorize("&cNo armor sets found."));
            return;
        }

        List<ArmorSet> sets = new ArrayList<>(allSets);
        int contentSlots = Math.min(sets.size(), 52);
        int size = Math.min((int) Math.ceil((contentSlots + 1) / 9.0) * 9, 54);
        Inventory inv = Bukkit.createInventory(null, size, TextUtil.parseComponent("&8Armor Sets (&f" + sets.size() + "&8)"));

        for (int i = 0; i < contentSlots - 1 && i < sets.size(); i++) {
            ItemStack item = createSetBrowserItem(sets.get(i));
            inv.setItem(i, item);
        }

        inv.setItem(inv.getSize() - 1, ItemBuilder.createGuiItem(Material.BARRIER, "&cClose", ""));

        GUISession session = new GUISession(GUIType.SET_BROWSER);
        openGUI(player, inv, session);
    }

    @Override
    public void openSigilBrowser(Player player) {
        var allSigils = plugin.getSigilManager().getAllSigils();
        if (allSigils.isEmpty()) {
            player.sendMessage(TextUtil.colorize("&cNo sigils found."));
            return;
        }

        List<Sigil> sigils = new ArrayList<>(allSigils);
        int contentSlots = Math.min(sigils.size(), 53);
        int size = Math.min((int) Math.ceil((contentSlots + 1) / 9.0) * 9, 54);
        Inventory inv = Bukkit.createInventory(null, size, TextUtil.parseComponent("&8Sigils (&f" + sigils.size() + "&8)"));

        for (int i = 0; i < contentSlots && i < sigils.size(); i++) {
            ItemStack item = createSigilBrowserItem(sigils.get(i));
            inv.setItem(i, item);
        }

        inv.setItem(inv.getSize() - 1, ItemBuilder.createGuiItem(Material.BARRIER, "&cClose", ""));

        GUISession session = new GUISession(GUIType.FUNCTION_BROWSER);
        openGUI(player, inv, session);
    }

    @Override
    public void openSetEditor(Player player, ArmorSet set) {
        Inventory inv = Bukkit.createInventory(null, 36, TextUtil.parseComponent("&8Edit Set: &b" + set.getId()));

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

        inv.setItem(10, ItemBuilder.createGuiItem(Material.NETHER_STAR, "&bView Synergies", "&7View all set bonuses"));
        inv.setItem(11, ItemBuilder.createGuiItem(Material.NAME_TAG, "&eRename Set", "&7Change set name"));
        inv.setItem(12, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aAdd Synergy", "&7Create new set bonus"));
        inv.setItem(13, ItemBuilder.createGuiItem(Material.OAK_SIGN, "&6Edit Synergy", "&7Modify existing bonus"));
        inv.setItem(14, ItemBuilder.createGuiItem(Material.ORANGE_DYE, "&6Manage Synergies", "&7Edit or remove bonuses"));
        inv.setItem(16, ItemBuilder.createGuiItem(Material.PAPER, "&eExport Config", "&7Generate YAML file"));
        inv.setItem(31, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack", ""));

        GUISession session = new GUISession(GUIType.SET_EDITOR);
        session.put("setId", set.getId());
        session.put("set", set);
        openGUI(player, inv, session);
    }

    @Override
    public void openSigilEditor(Player player, Sigil sigil) {
        Inventory inv = Bukkit.createInventory(null, 36, TextUtil.parseComponent("&8Edit Sigil: &d" + sigil.getName()));

        ItemStack sigilDisplay = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = sigilDisplay.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&d" + sigil.getName()));
        List<Component> lore = new ArrayList<>();
        for (String desc : sigil.getDescription()) {
            lore.add(TextUtil.parseComponent("&7" + TextUtil.toProperCase(desc)));
        }
        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("&8ID: &f" + sigil.getId()));
        lore.add(TextUtil.parseComponent("&8Tier: &f" + sigil.getTier()));
        lore.add(TextUtil.parseComponent("&8Slot: &f" + TextUtil.toProperCase(sigil.getSlot())));
        meta.lore(lore);
        sigilDisplay.setItemMeta(meta);
        inv.setItem(4, sigilDisplay);

        inv.setItem(10, ItemBuilder.createGuiItem(Material.REDSTONE, "&cEffect Triggers", "&7View current effects"));
        inv.setItem(11, ItemBuilder.createGuiItem(Material.CHEST, "&bItem Display", "&7Customize shard appearance"));
        inv.setItem(12, ItemBuilder.createGuiItem(Material.OAK_SIGN, "&6Edit Trigger", "&7Modify existing trigger"));
        inv.setItem(13, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aAdd Trigger", "&7Add new trigger"));
        inv.setItem(14, ItemBuilder.createGuiItem(Material.RED_DYE, "&cRemove Trigger", "&7Remove existing trigger"));
        inv.setItem(15, ItemBuilder.createGuiItem(Material.PAPER, "&eExport Config", "&7Generate YAML file"));
        inv.setItem(31, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack", ""));

        GUISession session = new GUISession(GUIType.FUNCTION_EDITOR);
        session.put("sigilId", sigil.getId());
        session.put("sigil", sigil);
        openGUI(player, inv, session);
    }

    @Override
    public void openSigilCreator(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("&8Create New Sigil"));

        inv.setItem(10, ItemBuilder.createGuiItem(Material.PAPER, "&eInstructions",
            "&7Click CREATE below",
            "&7Then type sigil ID in chat",
            "&7(lowercase, underscores for spaces)"));
        inv.setItem(12, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aCreate", "&7Click to start creating"));
        inv.setItem(14, ItemBuilder.createGuiItem(Material.RED_DYE, "&cCancel", "&7Go back"));

        GUISession session = new GUISession(GUIType.SIGIL_CREATOR);
        openGUI(player, inv, session);
    }

    @Override
    public void openSynergyCreator(Player player, String setId) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("&8Create Synergy: &b" + setId));

        inv.setItem(10, ItemBuilder.createGuiItem(Material.PAPER, "&eInstructions",
            "&7Click CREATE below",
            "&7Then type synergy ID in chat",
            "&7(lowercase, underscores for spaces)"));
        inv.setItem(12, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aCreate", "&7Click to start creating"));
        inv.setItem(14, ItemBuilder.createGuiItem(Material.RED_DYE, "&cCancel", "&7Go back"));

        GUISession session = new GUISession(GUIType.SYNERGY_CREATOR);
        session.put("setId", setId);
        openGUI(player, inv, session);

        player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<synergy ID> (lowercase, use underscores for spaces)"));
    }

    @Override
    public void openSynergyEditor(Player player, String setId, String synergyId) {
        ArmorSet set = plugin.getSetManager().getSet(setId);
        if (set == null) {
            player.sendMessage(TextUtil.colorize("&cSet not found"));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 36, TextUtil.parseComponent("&8Edit Synergy: &d" + synergyId));

        ItemStack synergyInfo = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = synergyInfo.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&d" + synergyId));
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.parseComponent("&7Set: &f" + setId));
        lore.add(TextUtil.parseComponent("&7Status: &aNew"));
        meta.lore(lore);
        synergyInfo.setItemMeta(meta);
        inv.setItem(4, synergyInfo);

        inv.setItem(10, ItemBuilder.createGuiItem(Material.REDSTONE_TORCH, "&aSelect Trigger", "&7Choose activation event"));
        inv.setItem(12, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aAdd Effect", "&7Add effect to synergy"));
        inv.setItem(14, ItemBuilder.createGuiItem(Material.YELLOW_DYE, "&eSet Chance", "&7Adjust activation chance"));
        inv.setItem(16, ItemBuilder.createGuiItem(Material.ORANGE_DYE, "&6Set Cooldown", "&7Adjust cooldown time"));
        inv.setItem(31, ItemBuilder.createGuiItem(Material.NETHER_STAR, "&aSave Synergy", "&7Save to set"));
        inv.setItem(33, ItemBuilder.createGuiItem(Material.BARRIER, "&cCancel", "&7Discard changes"));

        GUISession session = new GUISession(GUIType.SYNERGY_EDITOR);
        session.put("setId", setId);
        session.put("set", set);
        session.put("synergyId", synergyId);
        session.put("chance", 100.0);
        session.put("cooldown", 0.0);
        session.put("effects", new ArrayList<String>());
        openGUI(player, inv, session);
    }

    @Override
    public void openSynergyEditor(Player player, ArmorSet set) {
        var synergies = set.getSynergies();
        if (synergies.isEmpty()) {
            player.sendMessage(TextUtil.colorize("&cNo synergies to edit"));
            return;
        }

        int rows = Math.max(3, (int) Math.ceil((synergies.size() + 9) / 9.0));
        int size = Math.min(rows * 9, 54);
        Inventory inv = Bukkit.createInventory(null, size, TextUtil.parseComponent("&8Select Synergy to Edit"));

        int slot = 0;
        for (SetSynergy synergy : synergies) {
            ItemStack item = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(TextUtil.parseComponent("&b" + synergy.getId()));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parseComponent("&7Trigger: &f" + synergy.getTrigger().getConfigKey()));
            lore.add(TextUtil.parseComponent("&7Click to edit"));
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        GUISession session = new GUISession(GUIType.SYNERGY_EDITOR);
        session.put("set", set);
        session.put("setId", set.getId());
        openGUI(player, inv, session);
    }

    @Override
    public void openSetSynergiesViewer(Player player, ArmorSet set) {
        var synergies = set.getSynergies();
        int size = Math.max(27, Math.min((int) Math.ceil((synergies.size() + 9) / 9.0) * 9, 54));
        Inventory inv = Bukkit.createInventory(null, size, TextUtil.parseComponent("&8Set Synergies: &b" + set.getId()));

        ItemStack border = ItemBuilder.createBorderItem();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }

        ItemStack setInfo = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = setInfo.getItemMeta();
        infoMeta.displayName(TextUtil.parseComponent("&b" + set.getId() + " &8- &eSynergies"));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(TextUtil.parseComponent("&8Tier: &f" + set.getTier()));
        infoLore.add(TextUtil.parseComponent("&8Total Synergies: &f" + synergies.size()));
        infoMeta.lore(infoLore);
        setInfo.setItemMeta(infoMeta);
        inv.setItem(4, setInfo);

        if (synergies.isEmpty()) {
            ItemStack noSynergies = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta noMeta = noSynergies.getItemMeta();
            noMeta.displayName(TextUtil.parseComponent("&8No Synergies"));
            noSynergies.setItemMeta(noMeta);
            inv.setItem(13, noSynergies);
        } else {
            int slot = 9;
            for (SetSynergy synergy : synergies) {
                if (slot >= size - 1) break;
                ItemStack synergyItem = createSynergyDisplayItem(synergy);
                inv.setItem(slot++, synergyItem);
            }
        }

        inv.setItem(size - 1, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack", "&7Return to set editor"));

        GUISession session = new GUISession(GUIType.SET_SYNERGIES_VIEWER);
        session.put("setId", set.getId());
        session.put("set", set);
        openGUI(player, inv, session);
    }

    @Override
    public void openTriggerRemover(Player player, String buildType, String buildId, Object target) {
        Map<String, TriggerConfig> triggers = new HashMap<>();
        String title;

        if ("set".equalsIgnoreCase(buildType) && target instanceof ArmorSet set) {
            title = "&8Remove Trigger: &b" + set.getId();
            for (SetSynergy synergy : set.getSynergies()) {
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

        ItemStack border = ItemBuilder.createBorderItem();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }

        int slot = 9;
        for (Map.Entry<String, TriggerConfig> entry : triggers.entrySet()) {
            if (slot >= size - 1) break;

            String triggerKey = entry.getKey();
            TriggerConfig config = entry.getValue();

            ItemStack triggerItem = new ItemStack(Material.TNT);
            ItemMeta meta = triggerItem.getItemMeta();
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
            lore.add(TextUtil.parseComponent("&c&lClick to remove!"));

            meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "trigger_key"),
                PersistentDataType.STRING,
                triggerKey
            );

            meta.lore(lore);
            triggerItem.setItemMeta(meta);
            inv.setItem(slot++, triggerItem);
        }

        inv.setItem(size - 1, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack", "&7Return to editor"));

        GUISession session = new GUISession(GUIType.TRIGGER_REMOVER);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        session.put("target", target);
        openGUI(player, inv, session);
    }

    @Override
    public void openTriggerSelector(Player player, String buildType, String buildId) {
        openTriggerSelectorWithSlot(player, buildType, buildId, null);
    }

    @Override
    public void openTriggerSelectorWithSlot(Player player, String buildType, String buildId, String armorSlot) {
        String title = armorSlot != null
            ? "&8Select Trigger: " + TextUtil.toProperCase(armorSlot)
            : "&8Select Trigger: " + buildId;
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent(title));

        String[] triggers = {"ATTACK", "DEFENSE", "KILL_MOB", "KILL_PLAYER", "SHIFT", "FALL_DAMAGE",
                "EFFECT_STATIC", "TICK", "BOW_HIT", "BOW_SHOOT", "BLOCK_BREAK", "BLOCK_PLACE", "INTERACT", "TRIDENT_THROW"};

        for (int i = 0; i < triggers.length; i++) {
            String trigger = triggers[i];
            String desc = TextUtil.getTriggerDescription(trigger);
            inv.setItem(i, ItemBuilder.createGuiItem(Material.OAK_BUTTON, "&f" + trigger, "&8" + desc));
        }

        inv.setItem(44, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack", ""));

        GUISession session = new GUISession(GUIType.TRIGGER_SELECTOR);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        if (armorSlot != null) {
            session.put("armorSlot", armorSlot);
        }
        openGUI(player, inv, session);
    }

    @Override
    public void openEffectSelectorWithSlot(Player player, String buildType, String buildId, String trigger, String armorSlot) {
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent("&8Select Effects"));

        String[] effects = {"HEAL", "DAMAGE", "POTION", "PARTICLE", "SOUND", "DISINTEGRATE", "AEGIS",
                "INCREASE_DAMAGE", "MESSAGE", "CANCEL_EVENT", "TELEPORT", "SMOKEBOMB", "REPLENISH"};

        for (int i = 0; i < effects.length; i++) {
            String effect = effects[i];
            String desc = TextUtil.getEffectDescription(effect);
            inv.setItem(i, ItemBuilder.createGuiItem(Material.REDSTONE, "&f" + effect, "&8" + desc));
        }

        inv.setItem(44, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack", ""));

        GUISession session = new GUISession(GUIType.EFFECT_SELECTOR);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        session.put("trigger", trigger);
        if (armorSlot != null) {
            session.put("armorSlot", armorSlot);
        }
        openGUI(player, inv, session);
    }

    @Override
    public void openTriggerConfig(Player player, String buildType, String buildId, String trigger, String effect, String armorSlot, double chance, double cooldown) {
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent("&8Configure Trigger"));

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

        inv.setItem(19, ItemBuilder.createGuiItem(Material.RED_STAINED_GLASS_PANE, "&c-10%", "&7Decrease chance"));
        inv.setItem(20, ItemBuilder.createGuiItem(Material.ORANGE_STAINED_GLASS_PANE, "&6-1%", "&7Decrease chance"));

        ItemStack chanceDisplay = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta chanceMeta = chanceDisplay.getItemMeta();
        chanceMeta.displayName(TextUtil.parseComponent("&aChance: &f" + (int) chance + "%"));
        chanceDisplay.setItemMeta(chanceMeta);
        inv.setItem(22, chanceDisplay);

        inv.setItem(24, ItemBuilder.createGuiItem(Material.LIME_STAINED_GLASS_PANE, "&a+1%", "&7Increase chance"));
        inv.setItem(25, ItemBuilder.createGuiItem(Material.GREEN_STAINED_GLASS_PANE, "&2+10%", "&7Increase chance"));

        inv.setItem(28, ItemBuilder.createGuiItem(Material.RED_STAINED_GLASS_PANE, "&c-5s", "&7Decrease cooldown"));
        inv.setItem(29, ItemBuilder.createGuiItem(Material.ORANGE_STAINED_GLASS_PANE, "&6-1s", "&7Decrease cooldown"));

        ItemStack cooldownDisplay = new ItemStack(Material.CLOCK);
        ItemMeta cooldownMeta = cooldownDisplay.getItemMeta();
        cooldownMeta.displayName(TextUtil.parseComponent("&bCooldown: &f" + cooldown + "s"));
        cooldownDisplay.setItemMeta(cooldownMeta);
        inv.setItem(31, cooldownDisplay);

        inv.setItem(33, ItemBuilder.createGuiItem(Material.LIME_STAINED_GLASS_PANE, "&a+1s", "&7Increase cooldown"));
        inv.setItem(34, ItemBuilder.createGuiItem(Material.GREEN_STAINED_GLASS_PANE, "&2+5s", "&7Increase cooldown"));

        inv.setItem(39, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aConfirm", "&7Add trigger with these settings"));
        inv.setItem(41, ItemBuilder.createGuiItem(Material.RED_DYE, "&cCancel", "&7Go back"));

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

    @Override
    public void openEffectViewer(Player player, Sigil sigil) {
        var effects = sigil.getEffects();
        int totalEffects = effects.values().stream().mapToInt(c -> c.getEffects().size()).sum();
        int size = Math.max(36, Math.min((int) Math.ceil((totalEffects + 18) / 9.0) * 9, 54));
        Inventory inv = Bukkit.createInventory(null, size, TextUtil.parseComponent("&8Effects: &d" + sigil.getName()));

        ItemStack border = ItemBuilder.createBorderItem();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }

        ItemStack sigilInfo = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = sigilInfo.getItemMeta();
        infoMeta.displayName(TextUtil.parseComponent("&d" + sigil.getName()));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(TextUtil.parseComponent("&8Triggers: &f" + effects.size()));
        infoLore.add(TextUtil.parseComponent("&8Total Effects: &f" + totalEffects));
        infoMeta.lore(infoLore);
        sigilInfo.setItemMeta(infoMeta);
        inv.setItem(4, sigilInfo);

        if (effects.isEmpty()) {
            ItemStack noEffects = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta noMeta = noEffects.getItemMeta();
            noMeta.displayName(TextUtil.parseComponent("&8No Effects Configured"));
            noEffects.setItemMeta(noMeta);
            inv.setItem(13, noEffects);
        } else {
            int slot = 9;
            for (var entry : effects.entrySet()) {
                if (slot >= size - 1) break;
                ItemStack triggerItem = createTriggerDisplayItem(entry.getKey(), entry.getValue());
                inv.setItem(slot++, triggerItem);
            }
        }

        inv.setItem(size - 1, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack", "&7Return to editor"));

        GUISession session = new GUISession(GUIType.EFFECT_VIEWER);
        session.put("sigil", sigil);
        openGUI(player, inv, session);
    }

    @Override
    public void openItemDisplayEditor(Player player, Sigil sigil) {
        if (sigil.getItemForm() == null) {
            sigil.setItemForm(new Sigil.ItemForm());
        }

        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("&8Edit Item: &d" + sigil.getName()));

        inv.setItem(10, ItemBuilder.createGuiItem(Material.PAPER, "&eInstructions",
            "&7This editor allows you to",
            "&7customize the item display"));
        inv.setItem(11, ItemBuilder.createGuiItem(Material.AMETHYST_SHARD, "&bSelect Material",
            "&7Current: &f" + sigil.getItemForm().getMaterial().name()));
        inv.setItem(12, ItemBuilder.createGuiItem(Material.NAME_TAG, "&aEdit Name",
            "&7Current: &f" + sigil.getName()));
        inv.setItem(14, ItemBuilder.createGuiItem(Material.BOOK, "&aEdit Description",
            "&7Click to edit sigil description"));
        inv.setItem(22, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aDone", "&7Close editor"));

        GUISession session = new GUISession(GUIType.ITEM_DISPLAY_EDITOR);
        session.put("sigil", sigil);
        openGUI(player, inv, session);
    }

    @Override
    public void openMaterialSelector(Player player, Sigil sigil) {
        if (sigil.getItemForm() == null) {
            sigil.setItemForm(new Sigil.ItemForm());
        }

        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parseComponent("&8Select Shard/Crystal Material"));

        Material[] materials = {
            Material.ECHO_SHARD, Material.AMETHYST_SHARD, Material.PRISMARINE_SHARD,
            Material.PRISMARINE_CRYSTALS, Material.QUARTZ, Material.DIAMOND,
            Material.EMERALD, Material.REDSTONE, Material.LAPIS_LAZULI,
            Material.GLOWSTONE_DUST, Material.BLAZE_ROD, Material.BLAZE_POWDER,
            Material.ENDER_PEARL, Material.ENDER_EYE, Material.NETHER_STAR
        };

        Material currentMaterial = sigil.getItemForm().getMaterial();
        for (int i = 0; i < materials.length && i < 45; i++) {
            Material mat = materials[i];
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            String displayName = mat == currentMaterial ? "&a&l" + mat.name() + " &7(Selected)" : "&f" + mat.name();
            meta.displayName(TextUtil.parseComponent(displayName));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        inv.setItem(49, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack", "&7Return to item editor"));

        GUISession session = new GUISession(GUIType.GENERIC);
        session.put("sigil", sigil);
        session.put("menuType", "MATERIAL_SELECTOR");
        openGUI(player, inv, session);
    }

    @Override
    public void openTriggerEditor(Player player, Sigil sigil) {
        var effects = sigil.getEffects();
        if (effects.isEmpty()) {
            player.sendMessage(TextUtil.colorize("&cNo triggers to edit"));
            return;
        }

        int rows = Math.max(3, (int) Math.ceil((effects.size() + 9) / 9.0));
        int size = Math.min(rows * 9, 54);
        Inventory inv = Bukkit.createInventory(null, size, TextUtil.parseComponent("&8Select Trigger to Edit"));

        int slot = 0;
        List<String> triggerKeys = new ArrayList<>();
        for (String triggerKey : effects.keySet()) {
            ItemStack item = new ItemStack(Material.REDSTONE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(TextUtil.parseComponent("&c" + TextUtil.toProperCase(triggerKey.replace("_", " "))));
            List<Component> lore = new ArrayList<>();
            var config = effects.get(triggerKey);
            lore.add(TextUtil.parseComponent("&7Mode: &f" + config.getTriggerMode()));
            lore.add(TextUtil.parseComponent("&7Click to edit"));
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
            triggerKeys.add(triggerKey);
        }

        GUISession session = new GUISession(GUIType.SYNERGY_EDITOR);
        session.put("sigil", sigil);
        session.put("sigilId", sigil.getId());
        session.put("isSigilTriggerEditor", true);
        session.put("triggerKeys", triggerKeys);
        openGUI(player, inv, session);
    }

    @Override
    public void openSlotSelector(Player player, String setId) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("&8Select Armor Slot"));

        inv.setItem(10, ItemBuilder.createGuiItem(Material.DIAMOND_HELMET, "&bHelmet", "&7Add trigger to helmet"));
        inv.setItem(12, ItemBuilder.createGuiItem(Material.DIAMOND_CHESTPLATE, "&bChestplate", "&7Add trigger to chestplate"));
        inv.setItem(14, ItemBuilder.createGuiItem(Material.DIAMOND_LEGGINGS, "&bLeggings", "&7Add trigger to leggings"));
        inv.setItem(16, ItemBuilder.createGuiItem(Material.DIAMOND_BOOTS, "&bBoots", "&7Add trigger to boots"));
        inv.setItem(22, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack", ""));

        GUISession session = new GUISession(GUIType.SLOT_SELECTOR);
        session.put("buildType", "set");
        session.put("buildId", setId);
        openGUI(player, inv, session);
    }

    @Override
    public void openEffectConfigForType(Player player, String buildType, String buildId, String trigger, String effect, String armorSlot) {
        switch (effect.toUpperCase()) {
            case "PARTICLE" -> openEffectParticleConfig(player, buildType, buildId, trigger, armorSlot);
            case "SOUND" -> openEffectSoundConfig(player, buildType, buildId, trigger, armorSlot);
            case "POTION" -> openEffectPotionConfig(player, buildType, buildId, trigger, armorSlot);
            case "MESSAGE" -> openEffectMessageConfig(player, buildType, buildId, trigger, armorSlot);
            case "TELEPORT" -> openEffectTeleportConfig(player, buildType, buildId, trigger, armorSlot);
            case "CANCEL_EVENT" -> openTriggerConfig(player, buildType, buildId, trigger, "CANCEL_EVENT", armorSlot, 100, 0);
            default -> openEffectValueConfig(player, buildType, buildId, trigger, effect, armorSlot);
        }
    }

    @Override
    public void openEffectValueConfig(Player player, String buildType, String buildId, String trigger, String effect, String armorSlot) {
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent("&8Configure: &f" + effect));

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(TextUtil.parseComponent("&e" + effect));
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        double defaultValue = getDefaultValueForEffect(effect);

        inv.setItem(19, ItemBuilder.createGuiItem(Material.RED_STAINED_GLASS_PANE, "&c-10", "&7Decrease by 10"));
        inv.setItem(20, ItemBuilder.createGuiItem(Material.ORANGE_STAINED_GLASS_PANE, "&6-1", "&7Decrease by 1"));

        ItemStack valueDisplay = new ItemStack(Material.GOLD_INGOT);
        ItemMeta valueMeta = valueDisplay.getItemMeta();
        valueMeta.displayName(TextUtil.parseComponent("&aValue: &f" + (int) defaultValue));
        valueDisplay.setItemMeta(valueMeta);
        inv.setItem(22, valueDisplay);

        inv.setItem(24, ItemBuilder.createGuiItem(Material.LIME_STAINED_GLASS_PANE, "&a+1", "&7Increase by 1"));
        inv.setItem(25, ItemBuilder.createGuiItem(Material.GREEN_STAINED_GLASS_PANE, "&2+10", "&7Increase by 10"));

        inv.setItem(30, ItemBuilder.createGuiItem(Material.PLAYER_HEAD, "&a&l@Self &7(Selected)", "&7Apply to yourself"));
        inv.setItem(31, ItemBuilder.createGuiItem(Material.ZOMBIE_HEAD, "&c@Victim", "&7Apply to target/victim"));

        inv.setItem(39, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aConfirm", "&7Continue to trigger config"));
        inv.setItem(41, ItemBuilder.createGuiItem(Material.RED_DYE, "&cCancel", "&7Go back"));

        GUISession session = new GUISession(GUIType.EFFECT_VALUE_CONFIG);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        session.put("trigger", trigger);
        session.put("effect", effect);
        session.put("value", defaultValue);
        session.put("target", "@Self");
        session.put("radius", 5);
        if (armorSlot != null) {
            session.put("armorSlot", armorSlot);
        }
        openGUI(player, inv, session);
    }

    @Override
    public void openEffectParticleConfig(Player player, String buildType, String buildId, String trigger, String armorSlot) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parseComponent("&8Configure: &fPARTICLE"));
        inv.setItem(4, ItemBuilder.createGuiItem(Material.BLAZE_POWDER, "&ePARTICLE Effect", "&7Spawns particles"));
        inv.setItem(48, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aConfirm", "&7Continue"));
        inv.setItem(50, ItemBuilder.createGuiItem(Material.RED_DYE, "&cCancel", "&7Go back"));

        GUISession session = new GUISession(GUIType.EFFECT_PARTICLE_CONFIG);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        session.put("trigger", trigger);
        session.put("particleType", "FLAME");
        session.put("count", 10);
        if (armorSlot != null) session.put("armorSlot", armorSlot);
        openGUI(player, inv, session);
    }

    @Override
    public void openEffectSoundConfig(Player player, String buildType, String buildId, String trigger, String armorSlot) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parseComponent("&8Configure: &fSOUND"));
        inv.setItem(4, ItemBuilder.createGuiItem(Material.NOTE_BLOCK, "&eSOUND Effect", "&7Plays a sound"));
        inv.setItem(48, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aConfirm", "&7Continue"));
        inv.setItem(50, ItemBuilder.createGuiItem(Material.RED_DYE, "&cCancel", "&7Go back"));

        GUISession session = new GUISession(GUIType.EFFECT_SOUND_CONFIG);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        session.put("trigger", trigger);
        session.put("soundType", "ENTITY_EXPERIENCE_ORB_PICKUP");
        session.put("volume", 1.0);
        session.put("pitch", 1.0);
        if (armorSlot != null) session.put("armorSlot", armorSlot);
        openGUI(player, inv, session);
    }

    @Override
    public void openEffectPotionConfig(Player player, String buildType, String buildId, String trigger, String armorSlot) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parseComponent("&8Configure: &fPOTION"));
        inv.setItem(4, ItemBuilder.createGuiItem(Material.POTION, "&ePOTION Effect", "&7Applies potion effect"));
        inv.setItem(51, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aConfirm", "&7Continue"));
        inv.setItem(53, ItemBuilder.createGuiItem(Material.RED_DYE, "&cCancel", "&7Go back"));

        GUISession session = new GUISession(GUIType.EFFECT_POTION_CONFIG);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        session.put("trigger", trigger);
        session.put("potionType", "SPEED");
        session.put("duration", 10);
        session.put("amplifier", 0);
        session.put("target", "@Self");
        if (armorSlot != null) session.put("armorSlot", armorSlot);
        openGUI(player, inv, session);
    }

    @Override
    public void openEffectMessageConfig(Player player, String buildType, String buildId, String trigger, String armorSlot) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("&8Configure: &fMESSAGE"));
        inv.setItem(4, ItemBuilder.createGuiItem(Material.WRITABLE_BOOK, "&eMESSAGE Effect", "&7Sends a message"));
        inv.setItem(18, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aConfirm", "&7Continue"));
        inv.setItem(26, ItemBuilder.createGuiItem(Material.RED_DYE, "&cCancel", "&7Go back"));

        GUISession session = new GUISession(GUIType.EFFECT_MESSAGE_CONFIG);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        session.put("trigger", trigger);
        session.put("message", "&aYou feel stronger!");
        if (armorSlot != null) session.put("armorSlot", armorSlot);
        openGUI(player, inv, session);
    }

    @Override
    public void openEffectTeleportConfig(Player player, String buildType, String buildId, String trigger, String armorSlot) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parseComponent("&8Configure: &fTELEPORT"));
        inv.setItem(4, ItemBuilder.createGuiItem(Material.CHORUS_FRUIT, "&eTELEPORT Effect", "&7Teleports target"));
        inv.setItem(45, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aConfirm", "&7Continue"));
        inv.setItem(53, ItemBuilder.createGuiItem(Material.RED_DYE, "&cCancel", "&7Go back"));

        GUISession session = new GUISession(GUIType.EFFECT_TELEPORT_CONFIG);
        session.put("buildType", buildType);
        session.put("buildId", buildId);
        session.put("trigger", trigger);
        session.put("type", "RANDOM");
        session.put("distance", 8.0);
        session.put("facing", "KEEP");
        session.put("teleportee", "@Self");
        session.put("target", "@Self");
        if (armorSlot != null) session.put("armorSlot", armorSlot);
        openGUI(player, inv, session);
    }

    @Override
    public void exportSetToYAML(Player player, ArmorSet set) {
        File exportDir = new File(plugin.getDataFolder(), "armor-sets");
        if (!exportDir.exists()) exportDir.mkdirs();

        String fileName = set.getId().toLowerCase().replace(" ", "_") + ".yml";
        File exportFile = new File(exportDir, fileName);

        try {
            YamlConfiguration config = exportFile.exists()
                ? YamlConfiguration.loadConfiguration(exportFile)
                : new YamlConfiguration();

            String setId = set.getId();
            String baseName = setId.contains("_t") ? setId.substring(0, setId.lastIndexOf("_t")) : setId;
            String tierPath = baseName + ".tiers." + set.getTier();

            config.set(tierPath + ".name_pattern", set.getNamePattern());
            config.set(tierPath + ".material", set.getMaterial().name());

            config.save(exportFile);
            player.sendMessage(TextUtil.colorize("&aSuccessfully exported set to:"));
            player.sendMessage(TextUtil.colorize("&7" + exportFile.getPath()));
            playSound(player, "socket");

        } catch (IOException e) {
            player.sendMessage(TextUtil.colorize("&cFailed to export set: " + e.getMessage()));
            playSound(player, "error");
        }
    }

    @Override
    public void exportSigilToYAML(Player player, Sigil sigil) {
        File sigilDir = new File(plugin.getDataFolder(), "sigils");
        if (!sigilDir.exists()) sigilDir.mkdirs();

        String fileName = sigil.getSlot().toLowerCase() + "-sigils.yml";
        File targetFile = new File(sigilDir, fileName);

        try {
            YamlConfiguration config = targetFile.exists()
                ? YamlConfiguration.loadConfiguration(targetFile)
                : new YamlConfiguration();

            String sigilPath = sigil.getId();
            config.set(sigilPath + ".name", sigil.getName());
            config.set(sigilPath + ".slot", sigil.getSlot());
            config.set(sigilPath + ".tier", sigil.getTier());
            config.set(sigilPath + ".rarity", sigil.getRarity());

            config.save(targetFile);
            player.sendMessage(TextUtil.colorize("&aSuccessfully saved sigil to:"));
            player.sendMessage(TextUtil.colorize("&7" + targetFile.getPath()));
            playSound(player, "socket");

            plugin.getSigilManager().loadSigils();

        } catch (IOException e) {
            player.sendMessage(TextUtil.colorize("&cFailed to save sigil: " + e.getMessage()));
            playSound(player, "error");
        }
    }

    @Override
    public Map<UUID, GUISession> getPendingMessageInputs() {
        return pendingMessageInputs;
    }

    @Override
    public void addPendingMessageInput(UUID playerId, GUISession session) {
        pendingMessageInputs.put(playerId, session);
    }

    @Override
    public void playSound(Player player, String soundType) {
        String soundName = plugin.getConfigManager().getMainConfig()
                .getString("gui.sounds." + soundType, "BLOCK_NOTE_BLOCK_PLING");
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public void openSigilTriggerConfigEditor(Player player, Sigil sigil, String triggerKey) {
        var triggerConfig = sigil.getEffects().get(triggerKey);
        if (triggerConfig == null) {
            player.sendMessage(TextUtil.colorize("&cTrigger not found"));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent("&8Edit Trigger: &c" + TextUtil.toProperCase(triggerKey.replace("_", " "))));

        ItemStack info = new ItemStack(Material.REDSTONE);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(TextUtil.parseComponent("&c" + TextUtil.toProperCase(triggerKey.replace("_", " "))));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(TextUtil.parseComponent("&7Mode: &f" + triggerConfig.getTriggerMode()));
        infoLore.add(TextUtil.parseComponent("&7Chance: &f" + (int) triggerConfig.getChance() + "%"));
        infoLore.add(TextUtil.parseComponent("&7Cooldown: &f" + triggerConfig.getCooldown() + "s"));
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        inv.setItem(10, ItemBuilder.createGuiItem(Material.REPEATER, "&bTrigger Mode", "&7Toggle CHANCE/COOLDOWN"));
        inv.setItem(12, ItemBuilder.createGuiItem(Material.EXPERIENCE_BOTTLE, "&aSet Chance", "&7Use: /as input <chance>"));
        inv.setItem(14, ItemBuilder.createGuiItem(Material.CLOCK, "&aCooldown", "&7Use: /as input <cooldown>"));
        inv.setItem(16, ItemBuilder.createGuiItem(Material.NETHER_STAR, "&bEffects", "&7View effects"));
        inv.setItem(40, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack", ""));

        GUISession session = new GUISession(GUIType.GENERIC);
        session.put("sigil", sigil);
        session.put("sigilId", sigil.getId());
        session.put("triggerKey", triggerKey);
        session.put("triggerConfig", triggerConfig);
        session.put("isEditingSigilTrigger", true);
        openGUI(player, inv, session);
    }

    @Override
    public void openTriggerSelectorForSynergy(Player player, String setId, String synergyId) {
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent("&8Select Trigger: &b" + synergyId));

        String[] triggers = {"ATTACK", "DEFENSE", "KILL_MOB", "KILL_PLAYER", "SHIFT", "FALL_DAMAGE",
                "EFFECT_STATIC", "TICK", "BOW_HIT", "BOW_SHOOT", "BLOCK_BREAK", "BLOCK_PLACE", "INTERACT", "TRIDENT_THROW"};

        for (int i = 0; i < triggers.length; i++) {
            inv.setItem(i, ItemBuilder.createGuiItem(Material.SUNFLOWER, "&b" + TextUtil.toProperCase(triggers[i].replace("_", " ")),
                    "&7Synergy trigger"));
        }

        GUISession session = new GUISession(GUIType.TRIGGER_SELECTOR);
        session.put("setId", setId);
        session.put("synergyId", synergyId);
        session.put("creationMode", "SYNERGY");
        openGUI(player, inv, session);
    }

    // ===== PUBLIC API METHODS =====

    public void closeAll() {
        for (UUID uuid : new HashSet<>(activeSessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory();
            }
        }
        activeSessions.clear();
    }

    public boolean hasPendingMessageInput(UUID playerId) {
        return pendingMessageInputs.containsKey(playerId);
    }

    public boolean handleMessageInput(Player player, String input) {
        GUISession session = pendingMessageInputs.remove(player.getUniqueId());
        if (session == null) return false;

        String inputType = session.getString("inputType");

        if ("SIGIL_ID".equals(inputType)) {
            return handleSigilIdInput(player, input);
        }
        if ("SYNERGY_ID".equals(inputType)) {
            return handleSynergyIdInput(player, input, session);
        }
        if ("RENAME_SET".equals(inputType)) {
            return handleRenameSetInput(player, input, session);
        }
        if ("RENAME_SIGIL".equals(inputType)) {
            return handleRenameSigilInput(player, input, session);
        }
        if ("EDIT_SIGIL_DESCRIPTION".equals(inputType)) {
            return handleEditSigilDescriptionInput(player, input, session);
        }
        if ("SIGIL_TRIGGER_CHANCE".equals(inputType)) {
            return handleSigilTriggerChanceInput(player, input, session);
        }
        if ("SIGIL_TRIGGER_COOLDOWN".equals(inputType)) {
            return handleSigilTriggerCooldownInput(player, input, session);
        }

        return false;
    }

    // ===== PRIVATE HELPER METHODS =====

    private ItemStack createSetBrowserItem(ArmorSet set) {
        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&b" + set.getId()));
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.parseComponent("&8Tier: &f" + set.getTier()));
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
        lore.add(TextUtil.parseComponent("&7Click for details"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSynergyDisplayItem(SetSynergy synergy) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&d" + TextUtil.toProperCase(synergy.getId().replace("_", " "))));
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.parseComponent("&bTrigger: &f" + synergy.getTrigger().getConfigKey()));
        lore.add(TextUtil.parseComponent("&eChance: &f" + synergy.getTriggerConfig().getChance() + "%"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTriggerDisplayItem(String triggerKey, TriggerConfig config) {
        ItemStack item = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&b" + TextUtil.toProperCase(triggerKey.replace("_", " "))));
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.parseComponent("&eChance: &f" + config.getChance() + "%"));
        lore.add(TextUtil.parseComponent("&aEffects: &f" + config.getEffects().size()));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private double getDefaultValueForEffect(String effect) {
        return switch (effect.toUpperCase()) {
            case "HEAL" -> 4;
            case "DAMAGE", "INCREASE_DAMAGE" -> 10;
            case "AEGIS" -> 20;
            case "DISINTEGRATE" -> 2;
            case "TELEPORT" -> 8;
            case "SMOKEBOMB" -> 5;
            case "REPLENISH" -> 4;
            default -> 10;
        };
    }

    // ===== INPUT HANDLERS =====

    private boolean handleSigilIdInput(Player player, String sigilId) {
        if (sigilId.isEmpty() || !sigilId.matches("^[a-z0-9_]+$")) {
            player.sendMessage(TextUtil.colorize("&cInvalid sigil ID"));
            openSigilCreator(player);
            return false;
        }

        if (plugin.getSigilManager().getSigil(sigilId) != null) {
            player.sendMessage(TextUtil.colorize("&cA sigil with that ID already exists"));
            openSigilCreator(player);
            return false;
        }

        Sigil newSigil = new Sigil(sigilId);
        newSigil.setName(TextUtil.toProperCase(sigilId.replace("_", " ")));
        newSigil.setDescription(List.of("A new sigil"));

        openSlotSelectorForSigilCreation(player, newSigil);
        return true;
    }

    private void openSlotSelectorForSigilCreation(Player player, Sigil sigil) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("&8Select Armor Slot"));

        inv.setItem(10, ItemBuilder.createGuiItem(Material.DIAMOND_HELMET, "&bHelmet", "&7Sigil for helmet"));
        inv.setItem(12, ItemBuilder.createGuiItem(Material.DIAMOND_CHESTPLATE, "&bChestplate", "&7Sigil for chestplate"));
        inv.setItem(14, ItemBuilder.createGuiItem(Material.DIAMOND_LEGGINGS, "&bLeggings", "&7Sigil for leggings"));
        inv.setItem(16, ItemBuilder.createGuiItem(Material.DIAMOND_BOOTS, "&bBoots", "&7Sigil for boots"));
        inv.setItem(26, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack", ""));

        GUISession session = new GUISession(GUIType.SLOT_SELECTOR);
        session.put("creationMode", "SIGIL");
        session.put("sigil", sigil);
        openGUI(player, inv, session);
    }

    private boolean handleSynergyIdInput(Player player, String synergyId, GUISession session) {
        String setId = session.getString("setId");
        ArmorSet set = plugin.getSetManager().getSet(setId);

        if (set == null || synergyId.isEmpty() || !synergyId.matches("^[a-z0-9_]+$")) {
            player.sendMessage(TextUtil.colorize("&cInvalid synergy ID"));
            openSynergyCreator(player, setId);
            return false;
        }

        player.sendMessage(TextUtil.colorize("&aCreated synergy: &f" + synergyId));
        openTriggerSelectorForSynergy(player, setId, synergyId);
        return true;
    }

    private boolean handleRenameSetInput(Player player, String newName, GUISession session) {
        ArmorSet set = session.get("set", ArmorSet.class);
        if (set != null && !newName.isEmpty()) {
            set.setNamePattern(newName);
            player.sendMessage(TextUtil.colorize("&aSet renamed to: &f" + newName));
            openSetEditor(player, set);
            return true;
        }
        return false;
    }

    private boolean handleRenameSigilInput(Player player, String newName, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil != null && !newName.isEmpty()) {
            sigil.setName(newName);
            player.sendMessage(TextUtil.colorize("&aSigil renamed to: &f" + newName));
            openItemDisplayEditor(player, sigil);
            return true;
        }
        return false;
    }

    private boolean handleEditSigilDescriptionInput(Player player, String description, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil != null && !description.isEmpty()) {
            sigil.setDescription(List.of(description.split("\\|")));
            player.sendMessage(TextUtil.colorize("&aDescription updated"));
            openItemDisplayEditor(player, sigil);
            return true;
        }
        return false;
    }

    private boolean handleSigilTriggerChanceInput(Player player, String input, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        String triggerKey = session.getString("triggerKey");

        if (sigil != null && triggerKey != null) {
            try {
                double chance = Double.parseDouble(input);
                var config = sigil.getEffects().get(triggerKey);
                if (config != null) {
                    config.setChance(Math.max(0, Math.min(100, chance)));
                    player.sendMessage(TextUtil.colorize("&aChance set to: &f" + (int) chance + "%"));
                    openSigilTriggerConfigEditor(player, sigil, triggerKey);
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        player.sendMessage(TextUtil.colorize("&cInvalid input"));
        return false;
    }

    private boolean handleSigilTriggerCooldownInput(Player player, String input, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        String triggerKey = session.getString("triggerKey");

        if (sigil != null && triggerKey != null) {
            try {
                double cooldown = Double.parseDouble(input);
                var config = sigil.getEffects().get(triggerKey);
                if (config != null) {
                    config.setCooldown(Math.max(0, cooldown));
                    player.sendMessage(TextUtil.colorize("&aCooldown set to: &f" + cooldown + "s"));
                    openSigilTriggerConfigEditor(player, sigil, triggerKey);
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        player.sendMessage(TextUtil.colorize("&cInvalid input"));
        return false;
    }
}
