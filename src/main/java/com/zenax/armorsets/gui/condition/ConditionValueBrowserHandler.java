package com.zenax.armorsets.gui.condition;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.GUILayout;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Browser for selecting condition values.
 * Shows clickable options instead of text input for selection-based conditions.
 *
 * **Alex Check:** This is critical for usability - Alex can click "DESERT"
 * instead of typing it perfectly from memory.
 */
public class ConditionValueBrowserHandler extends AbstractHandler {

    private static final int[] ITEM_SLOTS = GUILayout.Browser.ITEM_SLOTS;
    private static final int SLOT_BACK = GUILayout.Browser.BACK;
    private static final int SLOT_PREV_PAGE = GUILayout.Browser.PREV_PAGE;
    private static final int SLOT_PAGE_INFO = GUILayout.Browser.PAGE_INDICATOR;
    private static final int SLOT_NEXT_PAGE = GUILayout.Browser.NEXT_PAGE;
    private static final int ITEMS_PER_PAGE = GUILayout.Browser.ITEMS_PER_PAGE;

    public ConditionValueBrowserHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        int page = session.getInt("page", 1);

        switch (slot) {
            case SLOT_BACK -> handleBack(player, session);
            case SLOT_PREV_PAGE -> handlePageChange(player, session, page - 1);
            case SLOT_NEXT_PAGE -> handlePageChange(player, session, page + 1);
            default -> {
                int itemIndex = getItemIndex(slot);
                if (itemIndex >= 0) {
                    handleItemSelection(player, session, itemIndex, page);
                } else {
                    playSound(player, "click");
                }
            }
        }
    }

    private void handleBack(Player player, GUISession session) {
        playSound(player, "click");
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        boolean isAbilityMode = session.getBooleanOpt("isAbilityMode");

        if (sigil != null && signalKey != null) {
            ConditionSelectorHandler.openGUI(guiManager, player, sigil, signalKey, isAbilityMode);
        } else {
            player.closeInventory();
        }
    }

    private void handlePageChange(Player player, GUISession session, int newPage) {
        String conditionType = session.get("conditionType", String.class);
        List<ValueOption> items = generateOptions(conditionType);
        int maxPage = Math.max(1, (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE));

        if (newPage < 1 || newPage > maxPage) {
            playSound(player, "error");
            return;
        }

        playSound(player, "page");

        // Reopen with new page
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        int conditionIndex = session.getInt("conditionIndex", -1);
        boolean isAbilityMode = session.getBooleanOpt("isAbilityMode");

        openGUI(guiManager, player, sigil, signalKey, conditionIndex, conditionType, isAbilityMode, newPage);
    }

    private void handleItemSelection(Player player, GUISession session, int itemIndex, int page) {
        String conditionType = session.get("conditionType", String.class);
        List<ValueOption> items = generateOptions(conditionType);

        int actualIndex = (page - 1) * ITEMS_PER_PAGE + itemIndex;

        if (actualIndex < 0 || actualIndex >= items.size()) {
            playSound(player, "error");
            return;
        }

        ValueOption selected = items.get(actualIndex);
        playSound(player, "click");

        // Get session data
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        int conditionIndex = session.getInt("conditionIndex", -1);
        boolean isAbilityMode = session.getBooleanOpt("isAbilityMode");

        // Check if "Custom" option was selected - fall back to text input
        if ("__CUSTOM__".equals(selected.value)) {
            ConditionParamHandler.openGUI(guiManager, player, sigil, signalKey, conditionIndex, conditionType, isAbilityMode);
            return;
        }

        // Build and save the condition
        String conditionString = conditionType + ":" + selected.value;

        // Get conditions list and update
        List<String> conditions;
        if (isAbilityMode) {
            Sigil.ActivationConfig activation = sigil.getActivation();
            if (activation == null) {
                activation = new Sigil.ActivationConfig();
                sigil.setActivation(activation);
            }
            conditions = activation.getConditions();
        } else {
            com.zenax.armorsets.flow.FlowConfig flowConfig = sigil.getFlowForTrigger(signalKey);
            conditions = flowConfig != null ? flowConfig.getConditions() : new ArrayList<>();
        }

        // Update or add condition
        if (conditionIndex >= 0 && conditionIndex < conditions.size()) {
            conditions.set(conditionIndex, conditionString);
            player.sendMessage(TextUtil.colorize("§aUpdated condition: §f" + conditionString));
        } else {
            conditions.add(conditionString);
            player.sendMessage(TextUtil.colorize("§aAdded condition: §f" + conditionString));
        }

        // Save and return
        plugin.getSigilManager().saveSigil(sigil);

        if (isAbilityMode) {
            ConditionConfigHandler.openAbilityGUI(guiManager, player, sigil);
        } else {
            ConditionConfigHandler.openGUI(guiManager, player, sigil, signalKey);
        }
    }

    private int getItemIndex(int slot) {
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Open the browser for a specific condition type.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey,
                               int conditionIndex, String conditionType, boolean isAbilityMode) {
        openGUI(guiManager, player, sigil, signalKey, conditionIndex, conditionType, isAbilityMode, 1);
    }

    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey,
                               int conditionIndex, String conditionType, boolean isAbilityMode, int page) {
        List<ValueOption> options = generateOptions(conditionType);

        if (options.isEmpty()) {
            player.sendMessage(TextUtil.colorize("§cNo options available for this condition type!"));
            return;
        }

        int maxPage = Math.max(1, (int) Math.ceil((double) options.size() / ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(null, 27,
            TextUtil.parseComponent("§7Select Value > §f" + conditionType));

        // Fill decoration slots
        for (int slot : GUILayout.Browser.DECORATION_SLOTS) {
            inv.setItem(slot, ItemBuilder.createBackground());
        }

        // Populate items for this page
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, options.size());

        for (int i = startIndex; i < endIndex; i++) {
            ValueOption option = options.get(i);
            int slotIndex = i - startIndex;
            if (slotIndex < ITEM_SLOTS.length) {
                // Special display for "Custom" option
                if ("__CUSTOM__".equals(option.value)) {
                    inv.setItem(ITEM_SLOTS[slotIndex], ItemBuilder.createItem(
                        option.material,
                        "§6Other (Type Custom)",
                        "§7Can't find what you need?",
                        "§7Click to type a custom value",
                        "",
                        "§eClick to enter text"
                    ));
                } else {
                    inv.setItem(ITEM_SLOTS[slotIndex], ItemBuilder.createItem(
                        option.material,
                        "§e" + option.displayName,
                        "§7Value: §f" + option.value,
                        "",
                        "§7Click to select"
                    ));
                }
            }
        }

        // Navigation buttons
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("Condition Selector"));
        inv.setItem(SLOT_PREV_PAGE, ItemBuilder.createPageArrow(false, page, maxPage));
        inv.setItem(SLOT_NEXT_PAGE, ItemBuilder.createPageArrow(true, page, maxPage));
        inv.setItem(SLOT_PAGE_INFO, ItemBuilder.createPageIndicator(page, maxPage, options.size()));

        // Create session
        GUISession session = new GUISession(GUIType.CONDITION_VALUE_BROWSER);
        session.put("sigil", sigil);
        session.put("signalKey", signalKey);
        session.put("conditionIndex", conditionIndex);
        session.put("conditionType", conditionType);
        session.put("isAbilityMode", isAbilityMode);
        session.put("page", page);

        guiManager.openGUI(player, inv, session);
    }

    /**
     * Generate options based on condition type.
     * Each list includes a "Custom" option at the end for Alex to type his own value.
     */
    private static List<ValueOption> generateOptions(String conditionType) {
        if (conditionType == null) return new ArrayList<>();

        List<ValueOption> options = switch (conditionType.toUpperCase()) {
            case "BIOME" -> generateBiomeOptions();
            case "HAS_POTION", "NO_POTION" -> generatePotionOptions();
            case "WEATHER" -> generateWeatherOptions();
            case "TIME" -> generateTimeOptions();
            case "DIMENSION" -> generateDimensionOptions();
            case "SIGNAL" -> generateSignalOptions();
            case "MAIN_HAND", "BLOCK_BELOW" -> generateCommonMaterialOptions();
            case "HAS_ENCHANT" -> generateEnchantmentOptions();
            default -> new ArrayList<>();
        };

        // Add "Custom" option at the end so Alex can type his own value if needed
        if (!options.isEmpty()) {
            options.add(new ValueOption("__CUSTOM__", "§7Other (Type Custom)", Material.WRITABLE_BOOK));
        }

        return options;
    }

    /**
     * Generate biome options.
     */
    private static List<ValueOption> generateBiomeOptions() {
        List<ValueOption> options = new ArrayList<>();

        // Group common biomes at the top for Alex
        addBiomeOption(options, Biome.PLAINS, Material.GRASS_BLOCK, "Plains");
        addBiomeOption(options, Biome.FOREST, Material.OAK_LOG, "Forest");
        addBiomeOption(options, Biome.DESERT, Material.SAND, "Desert");
        addBiomeOption(options, Biome.OCEAN, Material.WATER_BUCKET, "Ocean");
        addBiomeOption(options, Biome.SWAMP, Material.LILY_PAD, "Swamp");
        addBiomeOption(options, Biome.JUNGLE, Material.JUNGLE_LOG, "Jungle");
        addBiomeOption(options, Biome.SAVANNA, Material.ACACIA_LOG, "Savanna");
        addBiomeOption(options, Biome.TAIGA, Material.SPRUCE_LOG, "Taiga");
        addBiomeOption(options, Biome.SNOWY_PLAINS, Material.SNOW_BLOCK, "Snowy Plains");
        addBiomeOption(options, Biome.BADLANDS, Material.RED_SAND, "Badlands");
        addBiomeOption(options, Biome.MUSHROOM_FIELDS, Material.RED_MUSHROOM_BLOCK, "Mushroom Fields");
        addBiomeOption(options, Biome.DARK_FOREST, Material.DARK_OAK_LOG, "Dark Forest");
        addBiomeOption(options, Biome.BEACH, Material.SAND, "Beach");
        addBiomeOption(options, Biome.RIVER, Material.WATER_BUCKET, "River");
        addBiomeOption(options, Biome.DEEP_OCEAN, Material.PRISMARINE, "Deep Ocean");
        addBiomeOption(options, Biome.NETHER_WASTES, Material.NETHERRACK, "Nether Wastes");
        addBiomeOption(options, Biome.CRIMSON_FOREST, Material.CRIMSON_STEM, "Crimson Forest");
        addBiomeOption(options, Biome.WARPED_FOREST, Material.WARPED_STEM, "Warped Forest");
        addBiomeOption(options, Biome.SOUL_SAND_VALLEY, Material.SOUL_SAND, "Soul Sand Valley");
        addBiomeOption(options, Biome.BASALT_DELTAS, Material.BASALT, "Basalt Deltas");
        addBiomeOption(options, Biome.THE_END, Material.END_STONE, "The End");
        addBiomeOption(options, Biome.END_HIGHLANDS, Material.PURPUR_BLOCK, "End Highlands");

        return options;
    }

    private static void addBiomeOption(List<ValueOption> options, Biome biome, Material mat, String displayName) {
        options.add(new ValueOption(biome.name(), displayName, mat));
    }

    /**
     * Generate potion effect options.
     */
    private static List<ValueOption> generatePotionOptions() {
        List<ValueOption> options = new ArrayList<>();

        // Common combat potions first
        options.add(new ValueOption("STRENGTH", "Strength", Material.BLAZE_POWDER));
        options.add(new ValueOption("SPEED", "Speed", Material.SUGAR));
        options.add(new ValueOption("REGENERATION", "Regeneration", Material.GHAST_TEAR));
        options.add(new ValueOption("RESISTANCE", "Resistance", Material.IRON_INGOT));
        options.add(new ValueOption("FIRE_RESISTANCE", "Fire Resistance", Material.MAGMA_CREAM));
        options.add(new ValueOption("INVISIBILITY", "Invisibility", Material.FERMENTED_SPIDER_EYE));
        options.add(new ValueOption("NIGHT_VISION", "Night Vision", Material.GOLDEN_CARROT));
        options.add(new ValueOption("JUMP_BOOST", "Jump Boost", Material.RABBIT_FOOT));
        options.add(new ValueOption("SLOW_FALLING", "Slow Falling", Material.PHANTOM_MEMBRANE));
        options.add(new ValueOption("ABSORPTION", "Absorption", Material.GOLDEN_APPLE));
        options.add(new ValueOption("HASTE", "Haste", Material.GOLDEN_PICKAXE));
        options.add(new ValueOption("INSTANT_HEALTH", "Instant Health", Material.GLISTERING_MELON_SLICE));

        // Negative effects
        options.add(new ValueOption("POISON", "Poison", Material.SPIDER_EYE));
        options.add(new ValueOption("SLOWNESS", "Slowness", Material.SOUL_SAND));
        options.add(new ValueOption("WEAKNESS", "Weakness", Material.WOODEN_SWORD));
        options.add(new ValueOption("WITHER", "Wither", Material.WITHER_SKELETON_SKULL));
        options.add(new ValueOption("BLINDNESS", "Blindness", Material.INK_SAC));
        options.add(new ValueOption("HUNGER", "Hunger", Material.ROTTEN_FLESH));
        options.add(new ValueOption("MINING_FATIGUE", "Mining Fatigue", Material.PRISMARINE_SHARD));
        options.add(new ValueOption("NAUSEA", "Nausea", Material.PUFFERFISH));

        return options;
    }

    /**
     * Generate weather options.
     */
    private static List<ValueOption> generateWeatherOptions() {
        List<ValueOption> options = new ArrayList<>();
        options.add(new ValueOption("CLEAR", "Clear", Material.SUNFLOWER));
        options.add(new ValueOption("RAINING", "Raining", Material.WATER_BUCKET));
        options.add(new ValueOption("THUNDERING", "Thunderstorm", Material.LIGHTNING_ROD));
        return options;
    }

    /**
     * Generate time of day options.
     */
    private static List<ValueOption> generateTimeOptions() {
        List<ValueOption> options = new ArrayList<>();
        options.add(new ValueOption("DAY", "Day (6:00 - 18:00)", Material.SUNFLOWER));
        options.add(new ValueOption("NIGHT", "Night (18:00 - 6:00)", Material.COAL));
        options.add(new ValueOption("SUNSET", "Sunset (18:00)", Material.ORANGE_DYE));
        options.add(new ValueOption("SUNRISE", "Sunrise (6:00)", Material.YELLOW_DYE));
        return options;
    }

    /**
     * Generate dimension options.
     */
    private static List<ValueOption> generateDimensionOptions() {
        List<ValueOption> options = new ArrayList<>();
        options.add(new ValueOption("OVERWORLD", "Overworld", Material.GRASS_BLOCK));
        options.add(new ValueOption("NETHER", "The Nether", Material.NETHERRACK));
        options.add(new ValueOption("THE_END", "The End", Material.END_STONE));
        return options;
    }

    /**
     * Generate signal type options.
     */
    private static List<ValueOption> generateSignalOptions() {
        List<ValueOption> options = new ArrayList<>();
        // Common combat signals first
        options.add(new ValueOption("ATTACK", "Attack", Material.IRON_SWORD));
        options.add(new ValueOption("DEFENSE", "Defense", Material.SHIELD));
        options.add(new ValueOption("KILL_MOB", "Kill Mob", Material.ZOMBIE_HEAD));
        options.add(new ValueOption("KILL_PLAYER", "Kill Player", Material.PLAYER_HEAD));
        options.add(new ValueOption("SHIFT", "Sneak/Shift", Material.LEATHER_BOOTS));
        options.add(new ValueOption("FALL_DAMAGE", "Fall Damage", Material.FEATHER));
        options.add(new ValueOption("EFFECT_STATIC", "Passive", Material.BEACON));
        options.add(new ValueOption("BOW_SHOOT", "Bow Shoot", Material.BOW));
        options.add(new ValueOption("BOW_HIT", "Bow Hit", Material.ARROW));
        options.add(new ValueOption("TRIDENT_THROW", "Trident Throw", Material.TRIDENT));
        options.add(new ValueOption("TICK", "Tick (Periodic)", Material.CLOCK));
        options.add(new ValueOption("BLOCK_BREAK", "Block Break", Material.DIAMOND_PICKAXE));
        options.add(new ValueOption("BLOCK_PLACE", "Block Place", Material.GRASS_BLOCK));
        options.add(new ValueOption("INTERACT", "Interact", Material.STICK));
        options.add(new ValueOption("ITEM_BREAK", "Item Break", Material.WOODEN_SWORD));
        options.add(new ValueOption("FISH", "Fishing", Material.FISHING_ROD));
        return options;
    }

    /**
     * Generate common material options (for MAIN_HAND, BLOCK_BELOW).
     */
    private static List<ValueOption> generateCommonMaterialOptions() {
        List<ValueOption> options = new ArrayList<>();

        // Weapons
        options.add(new ValueOption("DIAMOND_SWORD", "Diamond Sword", Material.DIAMOND_SWORD));
        options.add(new ValueOption("NETHERITE_SWORD", "Netherite Sword", Material.NETHERITE_SWORD));
        options.add(new ValueOption("IRON_SWORD", "Iron Sword", Material.IRON_SWORD));
        options.add(new ValueOption("BOW", "Bow", Material.BOW));
        options.add(new ValueOption("CROSSBOW", "Crossbow", Material.CROSSBOW));
        options.add(new ValueOption("TRIDENT", "Trident", Material.TRIDENT));

        // Tools
        options.add(new ValueOption("DIAMOND_PICKAXE", "Diamond Pickaxe", Material.DIAMOND_PICKAXE));
        options.add(new ValueOption("NETHERITE_PICKAXE", "Netherite Pickaxe", Material.NETHERITE_PICKAXE));
        options.add(new ValueOption("DIAMOND_AXE", "Diamond Axe", Material.DIAMOND_AXE));
        options.add(new ValueOption("NETHERITE_AXE", "Netherite Axe", Material.NETHERITE_AXE));
        options.add(new ValueOption("FISHING_ROD", "Fishing Rod", Material.FISHING_ROD));
        options.add(new ValueOption("SHIELD", "Shield", Material.SHIELD));

        // Common blocks
        options.add(new ValueOption("STONE", "Stone", Material.STONE));
        options.add(new ValueOption("NETHERITE_BLOCK", "Netherite Block", Material.NETHERITE_BLOCK));
        options.add(new ValueOption("DIAMOND_BLOCK", "Diamond Block", Material.DIAMOND_BLOCK));
        options.add(new ValueOption("GOLD_BLOCK", "Gold Block", Material.GOLD_BLOCK));
        options.add(new ValueOption("IRON_BLOCK", "Iron Block", Material.IRON_BLOCK));
        options.add(new ValueOption("OBSIDIAN", "Obsidian", Material.OBSIDIAN));
        options.add(new ValueOption("SAND", "Sand", Material.SAND));
        options.add(new ValueOption("GRASS_BLOCK", "Grass Block", Material.GRASS_BLOCK));
        options.add(new ValueOption("DIRT", "Dirt", Material.DIRT));
        options.add(new ValueOption("WATER", "Water", Material.WATER_BUCKET));
        options.add(new ValueOption("LAVA", "Lava", Material.LAVA_BUCKET));

        return options;
    }

    /**
     * Generate enchantment options.
     */
    private static List<ValueOption> generateEnchantmentOptions() {
        List<ValueOption> options = new ArrayList<>();

        // Combat enchants
        options.add(new ValueOption("SHARPNESS", "Sharpness", Material.DIAMOND_SWORD));
        options.add(new ValueOption("SMITE", "Smite", Material.ROTTEN_FLESH));
        options.add(new ValueOption("BANE_OF_ARTHROPODS", "Bane of Arthropods", Material.SPIDER_EYE));
        options.add(new ValueOption("FIRE_ASPECT", "Fire Aspect", Material.BLAZE_POWDER));
        options.add(new ValueOption("KNOCKBACK", "Knockback", Material.PISTON));
        options.add(new ValueOption("SWEEPING_EDGE", "Sweeping Edge", Material.IRON_SWORD));

        // Protection enchants
        options.add(new ValueOption("PROTECTION", "Protection", Material.IRON_CHESTPLATE));
        options.add(new ValueOption("FIRE_PROTECTION", "Fire Protection", Material.MAGMA_CREAM));
        options.add(new ValueOption("BLAST_PROTECTION", "Blast Protection", Material.TNT));
        options.add(new ValueOption("PROJECTILE_PROTECTION", "Projectile Protection", Material.ARROW));
        options.add(new ValueOption("FEATHER_FALLING", "Feather Falling", Material.FEATHER));
        options.add(new ValueOption("THORNS", "Thorns", Material.CACTUS));

        // Bow enchants
        options.add(new ValueOption("POWER", "Power", Material.BOW));
        options.add(new ValueOption("PUNCH", "Punch", Material.SLIME_BALL));
        options.add(new ValueOption("FLAME", "Flame", Material.FIRE_CHARGE));
        options.add(new ValueOption("INFINITY", "Infinity", Material.SPECTRAL_ARROW));

        // Tool enchants
        options.add(new ValueOption("EFFICIENCY", "Efficiency", Material.GOLDEN_PICKAXE));
        options.add(new ValueOption("FORTUNE", "Fortune", Material.DIAMOND));
        options.add(new ValueOption("SILK_TOUCH", "Silk Touch", Material.GRASS_BLOCK));
        options.add(new ValueOption("UNBREAKING", "Unbreaking", Material.ANVIL));
        options.add(new ValueOption("MENDING", "Mending", Material.EXPERIENCE_BOTTLE));

        return options;
    }

    /**
     * Check if a condition type should use the browser.
     * Note: HAS_MARK is excluded because mark names are custom user-defined strings.
     */
    public static boolean shouldUseBrowser(String conditionType) {
        return switch (conditionType.toUpperCase()) {
            case "BIOME", "HAS_POTION", "NO_POTION", "WEATHER", "TIME",
                 "DIMENSION", "SIGNAL", "MAIN_HAND", "BLOCK_BELOW", "HAS_ENCHANT" -> true;
            // HAS_MARK excluded - needs custom text input for user-defined mark names
            default -> false;
        };
    }

    /**
     * Value option data class.
     */
    public static class ValueOption {
        public final String value;
        public final String displayName;
        public final Material material;

        public ValueOption(String value, String displayName, Material material) {
            this.value = value;
            this.displayName = displayName;
            this.material = material;
        }
    }
}
