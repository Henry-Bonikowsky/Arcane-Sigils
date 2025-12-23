package com.zenax.armorsets.gui.condition;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.core.SigilManager;
import com.zenax.armorsets.events.ConditionCategory;
import com.zenax.armorsets.events.ConditionType;
import com.zenax.armorsets.flow.FlowConfig;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for the CONDITION_SELECTOR GUI.
 * Allows selecting a condition type from categorized list.
 *
 * Layout (6 rows = 54 slots):
 * Row 0: Category tabs [H][P][E][C][S][Q][M][_][_]
 * Row 1-4: Condition items for selected category (paginated)
 * Row 5: [X][_][_][_][_][_][_][<][>]
 *
 * Where:
 * H = Health category, P = Potion, E = Environmental, C = Combat, S = Player State, Q = Equipment, M = Meta
 * X = Back button (slot 45)
 * < = Previous page (slot 52)
 * > = Next page (slot 53)
 */
public class ConditionSelectorHandler extends AbstractHandler {

    // Category tab slots (row 0)
    private static final int[] CATEGORY_SLOTS = {0, 1, 2, 3, 4, 5, 6};

    // Condition display slots (rows 1-4, 9 per row = 36 slots)
    private static final int[] CONDITION_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV = 52;
    private static final int SLOT_NEXT = 53;

    private static final int ITEMS_PER_PAGE = CONDITION_SLOTS.length;

    private final SigilManager sigilManager;

    public ConditionSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
        this.sigilManager = plugin.getSigilManager();
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        boolean isAbilityMode = session.getBooleanOpt("isAbilityMode");
        ConditionCategory selectedCategory = session.get("selectedCategory", ConditionCategory.class);
        int page = session.getInt("page", 0);

        if (sigil == null || signalKey == null) {
            player.sendMessage(TextUtil.colorize("§cError: Invalid session data!"));
            player.closeInventory();
            return;
        }

        // Check category tabs
        int categoryIndex = getCategoryIndex(slot);
        if (categoryIndex >= 0) {
            handleCategorySelect(player, session, sigil, signalKey, isAbilityMode, categoryIndex);
            return;
        }

        // Check navigation and back
        switch (slot) {
            case SLOT_BACK -> handleBack(player, session, isAbilityMode);
            case SLOT_PREV -> handlePrevPage(player, session, sigil, signalKey, isAbilityMode, selectedCategory, page);
            case SLOT_NEXT -> handleNextPage(player, session, sigil, signalKey, isAbilityMode, selectedCategory, page);
            default -> handleConditionSelect(player, session, sigil, signalKey, slot, selectedCategory, page, isAbilityMode);
        }
    }

    /**
     * Handle category tab selection.
     */
    private void handleCategorySelect(Player player, GUISession session, Sigil sigil, String signalKey,
                                       boolean isAbilityMode, int categoryIndex) {
        ConditionCategory[] categories = ConditionCategory.values();
        if (categoryIndex >= categories.length) {
            playSound(player, "click");
            return;
        }

        ConditionCategory newCategory = categories[categoryIndex];
        playSound(player, "click");

        // Reopen with new category
        openGUI(guiManager, player, sigil, signalKey, isAbilityMode, newCategory, 0);
    }

    /**
     * Handle back button.
     */
    private void handleBack(Player player, GUISession session, boolean isAbilityMode) {
        playSound(player, "click");
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);

        if (sigil != null) {
            if (isAbilityMode) {
                ConditionConfigHandler.openAbilityGUI(guiManager, player, sigil);
            } else {
                ConditionConfigHandler.openGUI(guiManager, player, sigil, signalKey);
            }
        } else {
            player.closeInventory();
        }
    }

    /**
     * Handle previous page.
     */
    private void handlePrevPage(Player player, GUISession session, Sigil sigil, String signalKey,
                                boolean isAbilityMode, ConditionCategory category, int page) {
        if (page > 0) {
            playSound(player, "click");
            openGUI(guiManager, player, sigil, signalKey, isAbilityMode, category, page - 1);
        } else {
            playSound(player, "error");
        }
    }

    /**
     * Handle next page.
     */
    private void handleNextPage(Player player, GUISession session, Sigil sigil, String signalKey,
                                boolean isAbilityMode, ConditionCategory category, int page) {
        ConditionType[] conditions = ConditionType.getByCategory(category);
        int maxPages = (int) Math.ceil((double) conditions.length / ITEMS_PER_PAGE);

        if (page < maxPages - 1) {
            playSound(player, "click");
            openGUI(guiManager, player, sigil, signalKey, isAbilityMode, category, page + 1);
        } else {
            playSound(player, "error");
        }
    }

    /**
     * Handle condition selection.
     */
    private void handleConditionSelect(Player player, GUISession session, Sigil sigil, String signalKey,
                                       int slot, ConditionCategory category, int page, boolean isAbilityMode) {
        int conditionSlotIndex = getConditionSlotIndex(slot);
        if (conditionSlotIndex < 0) {
            playSound(player, "click");
            return;
        }

        ConditionType[] conditions = ConditionType.getByCategory(category);
        int globalIndex = page * ITEMS_PER_PAGE + conditionSlotIndex;

        if (globalIndex >= conditions.length) {
            playSound(player, "click");
            return;
        }

        ConditionType selectedType = conditions[globalIndex];
        playSound(player, "success");

        // If condition has parameters, determine whether to show browser or param editor
        if (selectedType.hasParameters()) {
            // Check if this condition type should use browser picker (BIOME, POTION, etc.)
            if (ConditionValueBrowserHandler.shouldUseBrowser(selectedType.name())) {
                // Open browser for selection-based conditions
                ConditionValueBrowserHandler.openGUI(guiManager, player, sigil, signalKey, -1, selectedType.name(), isAbilityMode);
            } else {
                // Open param handler for numeric conditions (HEALTH, LIGHT_LEVEL, etc.)
                ConditionParamHandler.openGUI(guiManager, player, sigil, signalKey, -1, selectedType.name(), isAbilityMode);
            }
        } else {
            // Add condition directly (no parameters)
            addCondition(player, sigil, signalKey, selectedType.name(), isAbilityMode);
        }
    }

    /**
     * Add a condition directly (for conditions without parameters).
     */
    private void addCondition(Player player, Sigil sigil, String signalKey, String conditionString, boolean isAbilityMode) {
        List<String> conditions;

        if (isAbilityMode) {
            Sigil.ActivationConfig activation = sigil.getActivation();
            if (activation == null) {
                activation = new Sigil.ActivationConfig();
                sigil.setActivation(activation);
            }
            conditions = activation.getConditions();
        } else {
            FlowConfig flowConfig = sigil.getFlowForTrigger(signalKey);
            conditions = flowConfig != null ? flowConfig.getConditions() : new ArrayList<>();
        }

        conditions.add(conditionString);
        player.sendMessage(TextUtil.colorize("§aAdded condition: §f" + conditionString));

        // Save and return to config
        sigilManager.saveSigil(sigil);

        if (isAbilityMode) {
            ConditionConfigHandler.openAbilityGUI(guiManager, player, sigil);
        } else {
            ConditionConfigHandler.openGUI(guiManager, player, sigil, signalKey);
        }
    }

    /**
     * Get category index from slot.
     */
    private int getCategoryIndex(int slot) {
        for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
            if (CATEGORY_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get condition slot index from slot.
     */
    private int getConditionSlotIndex(int slot) {
        for (int i = 0; i < CONDITION_SLOTS.length; i++) {
            if (CONDITION_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Open the Condition Selector GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey, boolean isAbilityMode) {
        openGUI(guiManager, player, sigil, signalKey, isAbilityMode, ConditionCategory.HEALTH, 0);
    }

    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey,
                               boolean isAbilityMode, ConditionCategory category, int page) {
        if (sigil == null || signalKey == null) {
            player.sendMessage(TextUtil.colorize("§cError: Invalid parameters!"));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54,
            TextUtil.parseComponent("§7Conditions > §fSelect Type"));

        // Fill row 5 with background
        for (int i = 45; i <= 53; i++) {
            inv.setItem(i, ItemBuilder.createBackground());
        }
        // Fill unused category slots
        for (int i = 7; i <= 8; i++) {
            inv.setItem(i, ItemBuilder.createBackground());
        }

        // Build category tabs
        ConditionCategory[] categories = ConditionCategory.values();
        for (int i = 0; i < Math.min(categories.length, CATEGORY_SLOTS.length); i++) {
            ConditionCategory cat = categories[i];
            boolean selected = cat == category;
            inv.setItem(CATEGORY_SLOTS[i], buildCategoryTab(cat, selected));
        }

        // Get conditions for selected category
        ConditionType[] conditions = ConditionType.getByCategory(category);
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, conditions.length);

        // Build condition items
        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex;
            inv.setItem(CONDITION_SLOTS[slotIndex], buildConditionTypeItem(conditions[i]));
        }

        // Back button
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("Conditions"));

        // Pagination
        int maxPages = (int) Math.ceil((double) conditions.length / ITEMS_PER_PAGE);
        if (maxPages <= 0) maxPages = 1;

        if (page > 0) {
            inv.setItem(SLOT_PREV, ItemBuilder.createItem(
                Material.ARROW,
                "§ePrevious Page",
                "§7Page " + page + "/" + maxPages
            ));
        }

        if (page < maxPages - 1) {
            inv.setItem(SLOT_NEXT, ItemBuilder.createItem(
                Material.ARROW,
                "§eNext Page",
                "§7Page " + (page + 2) + "/" + maxPages
            ));
        }

        // Create session
        GUISession session = new GUISession(GUIType.CONDITION_SELECTOR);
        session.put("sigil", sigil);
        session.put("signalKey", signalKey);
        session.put("isAbilityMode", isAbilityMode);
        session.put("selectedCategory", category);
        session.put("page", page);

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Build a category tab item.
     */
    private static ItemStack buildCategoryTab(ConditionCategory category, boolean selected) {
        Material material = getCategoryMaterial(category);
        String prefix = selected ? "§a" : "§7";
        String indicator = selected ? " §a(Selected)" : "";

        List<String> lore = new ArrayList<>();
        lore.add("§7" + category.getDescription());
        lore.add("");
        lore.add("§7Conditions: §f" + ConditionType.getByCategory(category).length);
        lore.add("");
        lore.add("§7Click to view");

        return ItemBuilder.createItem(material, prefix + TextUtil.toProperCase(category.name().replace("_", " ")) + indicator, lore);
    }

    /**
     * Get material for category.
     */
    private static Material getCategoryMaterial(ConditionCategory category) {
        return switch (category) {
            case HEALTH -> Material.RED_DYE;
            case POTION -> Material.POTION;
            case ENVIRONMENTAL -> Material.GRASS_BLOCK;
            case COMBAT -> Material.IRON_SWORD;
            case PLAYER_STATE -> Material.LEATHER_BOOTS;
            case EQUIPMENT -> Material.DIAMOND_CHESTPLATE;
            case META -> Material.ENDER_EYE;
        };
    }

    /**
     * Build a condition type item for display.
     */
    private static ItemStack buildConditionTypeItem(ConditionType type) {
        Material material = type.getIcon();
        String displayName = "§e" + type.getDisplayName();

        List<String> lore = new ArrayList<>();
        lore.add("§7" + type.getDescription());
        lore.add("");
        lore.add("§7Example: §f" + type.getExampleFormat());
        lore.add("§8" + type.getExampleDescription());
        lore.add("");

        if (type.hasParameters()) {
            lore.add("§aRequires configuration");
        } else {
            lore.add("§aNo parameters needed");
        }

        lore.add("");
        lore.add("§7Click to select");

        return ItemBuilder.createItem(material, displayName, lore);
    }
}
