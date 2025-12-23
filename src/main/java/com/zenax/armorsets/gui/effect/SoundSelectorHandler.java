package com.zenax.armorsets.gui.effect;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Browser for selecting sound types with volume and pitch controls.
 *
 * Layout (3 rows = 27 slots):
 * Row 0: [S][S][S][S][S][S][S][S][S]  -- Sound items (paginated)
 * Row 1: [S][S][S][S][S][S][S][S][S]
 * Row 2: [X][<][V][_][%][_][P][_][>]
 *
 * V = Volume control (slot 20) - Left click +0.1, Right click -0.1
 * P = Pitch control (slot 24) - Left click +0.1, Right click -0.1
 */
public class SoundSelectorHandler extends AbstractHandler {

    private static final int[] ITEM_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17
    };
    private static final int SLOT_BACK = 18;
    private static final int SLOT_PREV_PAGE = 19;
    private static final int SLOT_VOLUME = 20;
    private static final int SLOT_PAGE_INFO = 22;
    private static final int SLOT_PITCH = 24;
    private static final int SLOT_NEXT_PAGE = 26;

    private static final int ITEMS_PER_PAGE = 18;
    private static final List<SoundOption> SOUND_OPTIONS = buildSoundOptions();

    public SoundSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        int page = session.getInt("page", 1);
        ClickType clickType = event.getClick();

        switch (slot) {
            case SLOT_BACK -> handleBack(player, session);
            case SLOT_PREV_PAGE -> handlePageChange(player, session, page - 1);
            case SLOT_NEXT_PAGE -> handlePageChange(player, session, page + 1);
            case SLOT_VOLUME -> handleVolumeChange(player, session, clickType);
            case SLOT_PITCH -> handlePitchChange(player, session, clickType);
            default -> {
                int itemIndex = getItemIndex(slot);
                if (itemIndex >= 0) {
                    handleSoundSelection(player, session, itemIndex, page);
                } else {
                    playSound(player, "click");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleBack(Player player, GUISession session) {
        playSound(player, "click");
        Map<String, Object> params = (Map<String, Object>) session.get("params");
        if (params == null) {
            params = new java.util.HashMap<>();
        }
        returnToEffectParam(player, session, params);
    }

    private void handlePageChange(Player player, GUISession session, int newPage) {
        int totalPages = (int) Math.ceil((double) SOUND_OPTIONS.size() / ITEMS_PER_PAGE);
        if (newPage < 1 || newPage > totalPages) {
            playSound(player, "error");
            return;
        }
        playSound(player, "click");
        session.put("page", newPage);
        openGUI(player, session, newPage);
    }

    private void handleVolumeChange(Player player, GUISession session, ClickType clickType) {
        double volume = session.getDouble("soundVolume", 1.0);

        if (clickType.isLeftClick()) {
            volume = Math.min(2.0, volume + 0.1);
        } else if (clickType.isRightClick()) {
            volume = Math.max(0.1, volume - 0.1);
        }

        // Round to 1 decimal place
        volume = Math.round(volume * 10.0) / 10.0;
        session.put("soundVolume", volume);

        // Play preview sound
        String currentSound = session.get("selectedSound", String.class);
        if (currentSound != null) {
            double pitch = session.getDouble("soundPitch", 1.0);
            playSoundPreview(player, currentSound, volume, pitch);
        }

        playSound(player, "click");
        refreshGUI(player, session);
    }

    private void handlePitchChange(Player player, GUISession session, ClickType clickType) {
        double pitch = session.getDouble("soundPitch", 1.0);

        if (clickType.isLeftClick()) {
            pitch = Math.min(2.0, pitch + 0.1);
        } else if (clickType.isRightClick()) {
            pitch = Math.max(0.5, pitch - 0.1);
        }

        // Round to 1 decimal place
        pitch = Math.round(pitch * 10.0) / 10.0;
        session.put("soundPitch", pitch);

        // Play preview sound
        String currentSound = session.get("selectedSound", String.class);
        if (currentSound != null) {
            double volume = session.getDouble("soundVolume", 1.0);
            playSoundPreview(player, currentSound, volume, pitch);
        }

        playSound(player, "click");
        refreshGUI(player, session);
    }

    private void handleSoundSelection(Player player, GUISession session, int itemIndex, int page) {
        int actualIndex = (page - 1) * ITEMS_PER_PAGE + itemIndex;
        if (actualIndex >= SOUND_OPTIONS.size()) {
            playSound(player, "click");
            return;
        }

        SoundOption option = SOUND_OPTIONS.get(actualIndex);
        session.put("selectedSound", option.soundName);

        // Play preview
        double volume = session.getDouble("soundVolume", 1.0);
        double pitch = session.getDouble("soundPitch", 1.0);
        playSoundPreview(player, option.soundName, volume, pitch);

        // Apply to params and return
        applySelection(player, session, option.soundName, volume, pitch);
    }

    private void playSoundPreview(Player player, String soundName, double volume, double pitch) {
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, (float) volume, (float) pitch);
        } catch (IllegalArgumentException e) {
            // Invalid sound, ignore
        }
    }

    @SuppressWarnings("unchecked")
    private void applySelection(Player player, GUISession session, String soundName, double volume, double pitch) {
        Map<String, Object> params = (Map<String, Object>) session.get("params");
        if (params == null) {
            params = new java.util.HashMap<>();
        }
        params.put("sound", soundName);
        params.put("value", volume);
        params.put("pitch", pitch);
        session.put("params", params);

        player.sendMessage(TextUtil.colorize("§aSound set to: §f" + soundName +
            " §7(Vol: " + volume + ", Pitch: " + pitch + ")"));
        returnToEffectParam(player, session, params);
    }

    @SuppressWarnings("unchecked")
    private void returnToEffectParam(Player player, GUISession session, Map<String, Object> params) {
        // Check if opened from Flow Builder (has configNode)
        Object configNode = session.get("configNode");
        if (configNode != null) {
            // Apply to Flow Builder node and return to Flow Builder
            returnToFlowBuilder(player, session, params);
            return;
        }

        // Original EffectParamHandler return path
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        String effectType = session.get("effectType", String.class);
        Integer effectIndex = session.get("effectIndex", Integer.class);
        if (sigil != null && signalKey != null && effectType != null) {
            // Use openGUIWithParams to preserve the updated params
            EffectParamHandler.openGUIWithParams(guiManager, player, sigil, signalKey,
                effectIndex != null ? effectIndex : -1, effectType, params);
        } else {
            player.closeInventory();
        }
    }

    /**
     * Return to Flow Builder after sound selection.
     */
    private void returnToFlowBuilder(Player player, GUISession session, Map<String, Object> params) {
        try {
            // Get the node and apply the sound selection
            Object configNode = session.get("configNode");

            // Apply sound parameters to the node
            if (configNode != null) {
                java.lang.reflect.Method setParam = configNode.getClass().getMethod("setParam", String.class, Object.class);
                if (params.containsKey("sound")) {
                    setParam.invoke(configNode, "sound", params.get("sound"));
                }
                if (params.containsKey("value")) {
                    setParam.invoke(configNode, "value", params.get("value"));
                }
                if (params.containsKey("pitch")) {
                    setParam.invoke(configNode, "pitch", params.get("pitch"));
                }
            }

            // Return to Flow Builder
            Sigil sigil = session.get("sigil", Sigil.class);
            String signalKey = session.get("signalKey", String.class);
            Object flow = session.get("flow");

            if (sigil != null && signalKey != null) {
                com.zenax.armorsets.gui.flow.FlowBuilderHandler.openGUI(
                    guiManager, player, sigil, signalKey,
                    (com.zenax.armorsets.flow.FlowGraph) flow, session);
            } else {
                player.closeInventory();
            }
        } catch (Exception e) {
            player.sendMessage(TextUtil.colorize("§cError returning to flow builder"));
            player.closeInventory();
        }
    }

    private int getItemIndex(int slot) {
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    private void refreshGUI(Player player, GUISession session) {
        int page = session.getInt("page", 1);
        openGUI(player, session, page);
    }

    public void openGUI(Player player, GUISession session, int page) {
        session.put("page", page);

        int totalPages = (int) Math.ceil((double) SOUND_OPTIONS.size() / ITEMS_PER_PAGE);
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.colorize("§8Select Sound (Page " + page + "/" + totalPages + ")"));

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Add sound items
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && (startIndex + i) < SOUND_OPTIONS.size(); i++) {
            SoundOption option = SOUND_OPTIONS.get(startIndex + i);
            inv.setItem(ITEM_SLOTS[i], buildSoundItem(option, session));
        }

        // Navigation row
        inv.setItem(SLOT_BACK, ItemBuilder.createItem(Material.ARROW, "§cBack", List.of("§7Return to Effect Config")));

        if (page > 1) {
            inv.setItem(SLOT_PREV_PAGE, ItemBuilder.createItem(Material.ARROW, "§ePrevious Page", List.of()));
        }
        if (page < totalPages) {
            inv.setItem(SLOT_NEXT_PAGE, ItemBuilder.createItem(Material.ARROW, "§eNext Page", List.of()));
        }

        // Volume control
        double volume = session.getDouble("soundVolume", 1.0);
        inv.setItem(SLOT_VOLUME, ItemBuilder.createItem(Material.BELL, "§6Volume: §f" + volume, List.of(
            "§7Left Click: §a+0.1",
            "§7Right Click: §c-0.1",
            "",
            "§7Range: 0.1 - 2.0"
        )));

        // Page info
        inv.setItem(SLOT_PAGE_INFO, ItemBuilder.createItem(Material.PAPER, "§fPage " + page + "/" + totalPages, List.of(
            "§7" + SOUND_OPTIONS.size() + " sounds available"
        )));

        // Pitch control
        double pitch = session.getDouble("soundPitch", 1.0);
        inv.setItem(SLOT_PITCH, ItemBuilder.createItem(Material.NOTE_BLOCK, "§dPitch: §f" + pitch, List.of(
            "§7Left Click: §a+0.1",
            "§7Right Click: §c-0.1",
            "",
            "§7Range: 0.5 - 2.0"
        )));

        guiManager.openGUI(player, inv, session);
    }

    private ItemStack buildSoundItem(SoundOption option, GUISession session) {
        String selectedSound = session.get("selectedSound", String.class);
        boolean isSelected = option.soundName.equals(selectedSound);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + option.category);
        lore.add("");
        if (isSelected) {
            lore.add("§a\u2714 Selected");
        } else {
            lore.add("§7Click to select & preview");
        }

        ItemStack item = ItemBuilder.createItem(option.displayMaterial,
            (isSelected ? "§a" : "§e") + option.displayName, lore);

        if (isSelected) {
            ItemBuilder.addGlowEffect(item);
        }

        return item;
    }

    /**
     * Static method to open the GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player, GUISession oldSession) {
        openGUI(guiManager, player, oldSession, 1);
    }

    public static void openGUI(GUIManager guiManager, Player player, GUISession oldSession, int page) {
        // Create new session with correct type
        GUISession session = new GUISession(GUIType.SOUND_SELECTOR);

        // Copy essential data from old session
        session.put("sigil", oldSession.get("sigil"));
        session.put("signalKey", oldSession.get("signalKey"));
        session.put("effectType", oldSession.get("effectType"));
        session.put("effectIndex", oldSession.get("effectIndex"));
        session.put("params", oldSession.get("params"));

        // Copy Flow Builder specific data if present
        if (oldSession.get("configNode") != null) {
            session.put("configNode", oldSession.get("configNode"));
            session.put("flow", oldSession.get("flow"));
            session.put("originalFlow", oldSession.get("originalFlow"));
            session.put("flowBuilderSession", oldSession.get("flowBuilderSession"));
        }

        // Initialize volume/pitch from existing params if available
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) session.get("params");
        if (params != null) {
            if (params.containsKey("value")) {
                session.put("soundVolume", ((Number) params.get("value")).doubleValue());
            }
            if (params.containsKey("pitch")) {
                session.put("soundPitch", ((Number) params.get("pitch")).doubleValue());
            }
            if (params.containsKey("sound")) {
                session.put("selectedSound", params.get("sound").toString());
            }
        }

        ArmorSetsPlugin plugin = (ArmorSetsPlugin) guiManager.getInputHelper().getPlugin();
        SoundSelectorHandler handler = new SoundSelectorHandler(plugin, guiManager);
        handler.openGUI(player, session, page);
    }

    private static List<SoundOption> buildSoundOptions() {
        List<SoundOption> options = new ArrayList<>();

        // Combat sounds
        options.add(new SoundOption("ENTITY_PLAYER_ATTACK_CRIT", "Critical Hit", Material.DIAMOND_SWORD, "Combat"));
        options.add(new SoundOption("ENTITY_PLAYER_ATTACK_STRONG", "Strong Attack", Material.IRON_SWORD, "Combat"));
        options.add(new SoundOption("ENTITY_PLAYER_ATTACK_SWEEP", "Sweep Attack", Material.GOLDEN_SWORD, "Combat"));
        options.add(new SoundOption("ENTITY_PLAYER_ATTACK_KNOCKBACK", "Knockback", Material.SHIELD, "Combat"));
        options.add(new SoundOption("ENTITY_ARROW_HIT_PLAYER", "Arrow Hit", Material.ARROW, "Combat"));
        options.add(new SoundOption("ITEM_SHIELD_BLOCK", "Shield Block", Material.SHIELD, "Combat"));
        options.add(new SoundOption("ENCHANT_THORNS_HIT", "Thorns Hit", Material.CACTUS, "Combat"));

        // Magic sounds
        options.add(new SoundOption("ENTITY_EVOKER_CAST_SPELL", "Cast Spell", Material.ENCHANTED_BOOK, "Magic"));
        options.add(new SoundOption("ENTITY_ILLUSIONER_CAST_SPELL", "Illusion Spell", Material.ENDER_EYE, "Magic"));
        options.add(new SoundOption("ENTITY_EVOKER_PREPARE_ATTACK", "Prepare Attack", Material.BLAZE_POWDER, "Magic"));
        options.add(new SoundOption("ENTITY_ZOMBIE_VILLAGER_CURE", "Cure", Material.GOLDEN_APPLE, "Magic"));
        options.add(new SoundOption("BLOCK_ENCHANTMENT_TABLE_USE", "Enchant", Material.ENCHANTING_TABLE, "Magic"));
        options.add(new SoundOption("BLOCK_BEACON_ACTIVATE", "Beacon Activate", Material.BEACON, "Magic"));
        options.add(new SoundOption("BLOCK_BEACON_POWER_SELECT", "Beacon Power", Material.NETHER_STAR, "Magic"));
        options.add(new SoundOption("ENTITY_ELDER_GUARDIAN_CURSE", "Elder Curse", Material.PRISMARINE_SHARD, "Magic"));

        // Effect sounds
        options.add(new SoundOption("ENTITY_PLAYER_LEVELUP", "Level Up", Material.EXPERIENCE_BOTTLE, "Effects"));
        options.add(new SoundOption("ENTITY_EXPERIENCE_ORB_PICKUP", "XP Pickup", Material.LIME_DYE, "Effects"));
        options.add(new SoundOption("ITEM_TOTEM_USE", "Totem Use", Material.TOTEM_OF_UNDYING, "Effects"));
        options.add(new SoundOption("BLOCK_END_PORTAL_SPAWN", "Portal Spawn", Material.END_PORTAL_FRAME, "Effects"));
        options.add(new SoundOption("ENTITY_ENDERMAN_TELEPORT", "Teleport", Material.ENDER_PEARL, "Effects"));
        options.add(new SoundOption("ENTITY_WITHER_SPAWN", "Wither Spawn", Material.WITHER_SKELETON_SKULL, "Effects"));
        options.add(new SoundOption("ENTITY_DRAGON_FIREBALL_EXPLODE", "Dragon Fireball", Material.DRAGON_BREATH, "Effects"));

        // Nature sounds
        options.add(new SoundOption("ENTITY_LIGHTNING_BOLT_THUNDER", "Thunder", Material.LIGHTNING_ROD, "Nature"));
        options.add(new SoundOption("ENTITY_LIGHTNING_BOLT_IMPACT", "Lightning Impact", Material.TRIDENT, "Nature"));
        options.add(new SoundOption("BLOCK_FIRE_EXTINGUISH", "Fire Extinguish", Material.WATER_BUCKET, "Nature"));
        options.add(new SoundOption("ITEM_FIRECHARGE_USE", "Fire Charge", Material.FIRE_CHARGE, "Nature"));
        options.add(new SoundOption("ENTITY_GENERIC_EXPLODE", "Explosion", Material.TNT, "Nature"));

        // Item sounds
        options.add(new SoundOption("ITEM_TRIDENT_THUNDER", "Trident Thunder", Material.TRIDENT, "Items"));
        options.add(new SoundOption("ITEM_TRIDENT_RIPTIDE_1", "Riptide 1", Material.TRIDENT, "Items"));
        options.add(new SoundOption("ITEM_TRIDENT_RIPTIDE_2", "Riptide 2", Material.TRIDENT, "Items"));
        options.add(new SoundOption("ITEM_TRIDENT_RIPTIDE_3", "Riptide 3", Material.TRIDENT, "Items"));
        options.add(new SoundOption("ITEM_ARMOR_EQUIP_NETHERITE", "Netherite Equip", Material.NETHERITE_CHESTPLATE, "Items"));
        options.add(new SoundOption("ITEM_ARMOR_EQUIP_DIAMOND", "Diamond Equip", Material.DIAMOND_CHESTPLATE, "Items"));

        // UI sounds
        options.add(new SoundOption("UI_BUTTON_CLICK", "Button Click", Material.STONE_BUTTON, "UI"));
        options.add(new SoundOption("BLOCK_NOTE_BLOCK_PLING", "Pling", Material.NOTE_BLOCK, "UI"));
        options.add(new SoundOption("BLOCK_NOTE_BLOCK_CHIME", "Chime", Material.BELL, "UI"));
        options.add(new SoundOption("BLOCK_NOTE_BLOCK_BELL", "Bell", Material.BELL, "UI"));
        options.add(new SoundOption("BLOCK_NOTE_BLOCK_HARP", "Harp", Material.NOTE_BLOCK, "UI"));
        options.add(new SoundOption("BLOCK_NOTE_BLOCK_BASS", "Bass", Material.NOTE_BLOCK, "UI"));
        options.add(new SoundOption("BLOCK_NOTE_BLOCK_XYLOPHONE", "Xylophone", Material.NOTE_BLOCK, "UI"));

        // Mob sounds
        options.add(new SoundOption("ENTITY_BLAZE_SHOOT", "Blaze Shoot", Material.BLAZE_ROD, "Mobs"));
        options.add(new SoundOption("ENTITY_GHAST_SHOOT", "Ghast Shoot", Material.GHAST_TEAR, "Mobs"));
        options.add(new SoundOption("ENTITY_WITHER_SHOOT", "Wither Shoot", Material.WITHER_SKELETON_SKULL, "Mobs"));
        options.add(new SoundOption("ENTITY_SHULKER_BULLET_HIT", "Shulker Bullet", Material.SHULKER_SHELL, "Mobs"));
        options.add(new SoundOption("ENTITY_GUARDIAN_ATTACK", "Guardian Attack", Material.PRISMARINE_CRYSTALS, "Mobs"));
        options.add(new SoundOption("ENTITY_RAVAGER_ROAR", "Ravager Roar", Material.RAVAGER_SPAWN_EGG, "Mobs"));
        options.add(new SoundOption("ENTITY_WARDEN_SONIC_BOOM", "Sonic Boom", Material.SCULK_SHRIEKER, "Mobs"));
        options.add(new SoundOption("ENTITY_WARDEN_ROAR", "Warden Roar", Material.SCULK, "Mobs"));

        // Breeze sounds (1.21+)
        options.add(new SoundOption("ENTITY_BREEZE_WIND_BURST", "Wind Burst", Material.WIND_CHARGE, "Breeze"));
        options.add(new SoundOption("ENTITY_BREEZE_SHOOT", "Breeze Shoot", Material.WIND_CHARGE, "Breeze"));
        options.add(new SoundOption("ENTITY_BREEZE_JUMP", "Breeze Jump", Material.WIND_CHARGE, "Breeze"));

        // Block sounds
        options.add(new SoundOption("BLOCK_ANVIL_USE", "Anvil Use", Material.ANVIL, "Blocks"));
        options.add(new SoundOption("BLOCK_ANVIL_LAND", "Anvil Land", Material.ANVIL, "Blocks"));
        options.add(new SoundOption("BLOCK_GLASS_BREAK", "Glass Break", Material.GLASS, "Blocks"));
        options.add(new SoundOption("BLOCK_AMETHYST_BLOCK_CHIME", "Amethyst Chime", Material.AMETHYST_SHARD, "Blocks"));
        options.add(new SoundOption("BLOCK_RESPAWN_ANCHOR_CHARGE", "Respawn Charge", Material.RESPAWN_ANCHOR, "Blocks"));
        options.add(new SoundOption("BLOCK_RESPAWN_ANCHOR_DEPLETE", "Respawn Deplete", Material.CRYING_OBSIDIAN, "Blocks"));
        options.add(new SoundOption("BLOCK_CONDUIT_ACTIVATE", "Conduit Activate", Material.CONDUIT, "Blocks"));
        options.add(new SoundOption("BLOCK_PORTAL_TRIGGER", "Portal Trigger", Material.OBSIDIAN, "Blocks"));

        // Misc sounds
        options.add(new SoundOption("ENTITY_FISHING_BOBBER_THROW", "Fishing Throw", Material.FISHING_ROD, "Misc"));
        options.add(new SoundOption("ENTITY_FISHING_BOBBER_RETRIEVE", "Fishing Retrieve", Material.COD, "Misc"));
        options.add(new SoundOption("ENTITY_BAT_TAKEOFF", "Bat Takeoff", Material.BAT_SPAWN_EGG, "Misc"));
        options.add(new SoundOption("ENTITY_FIREWORK_ROCKET_BLAST", "Firework Blast", Material.FIREWORK_ROCKET, "Misc"));
        options.add(new SoundOption("ENTITY_FIREWORK_ROCKET_LARGE_BLAST", "Large Firework", Material.FIREWORK_STAR, "Misc"));
        options.add(new SoundOption("ENTITY_FIREWORK_ROCKET_TWINKLE", "Firework Twinkle", Material.FIREWORK_ROCKET, "Misc"));
        options.add(new SoundOption("ENTITY_ITEM_BREAK", "Item Break", Material.BARRIER, "Misc"));

        return options;
    }

    /**
     * Sound option data class.
     */
    public static class SoundOption {
        public final String soundName;
        public final String displayName;
        public final Material displayMaterial;
        public final String category;

        public SoundOption(String soundName, String displayName, Material displayMaterial, String category) {
            this.soundName = soundName;
            this.displayName = displayName;
            this.displayMaterial = displayMaterial;
            this.category = category;
        }
    }
}
