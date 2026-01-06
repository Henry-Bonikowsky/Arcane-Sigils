package com.miracle.arcanesigils.gui.condition;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.events.ConditionCategory;
import com.miracle.arcanesigils.events.ConditionType;
import com.miracle.arcanesigils.flow.FlowGraph;
import com.miracle.arcanesigils.flow.nodes.ConditionNode;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.gui.flow.FlowBuilderHandler;
import com.miracle.arcanesigils.gui.input.SignInputHelper;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for selecting a condition type for Flow Builder CONDITION nodes.
 * This provides a visual GUI for Alex to pick conditions instead of typing them.
 *
 * Layout (6 rows = 54 slots):
 * Row 0: Category tabs [H][P][E][C][S][Q][M][_][_]
 * Row 1-4: Condition items for selected category (paginated)
 * Row 5: [X][_][_][_][_][_][_][<][>]
 */
public class FlowConditionSelectorHandler extends AbstractHandler {

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

    private final SignInputHelper inputHelper;

    public FlowConditionSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
        this.inputHelper = guiManager.getInputHelper();
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        ConditionNode conditionNode = session.get("conditionNode", ConditionNode.class);
        ConditionCategory selectedCategory = session.get("selectedCategory", ConditionCategory.class);
        int page = session.getInt("page", 0);

        if (conditionNode == null) {
            player.sendMessage(TextUtil.colorize("&cError: Invalid session data!"));
            player.closeInventory();
            return;
        }

        // Check category tabs
        int categoryIndex = getCategoryIndex(slot);
        if (categoryIndex >= 0) {
            handleCategorySelect(player, session, categoryIndex);
            return;
        }

        // Check navigation and back
        switch (slot) {
            case SLOT_BACK -> handleBack(player, session);
            case SLOT_PREV -> handlePrevPage(player, session, selectedCategory, page);
            case SLOT_NEXT -> handleNextPage(player, session, selectedCategory, page);
            default -> handleConditionSelect(player, session, slot, selectedCategory, page);
        }
    }

    /**
     * Handle category tab selection.
     */
    private void handleCategorySelect(Player player, GUISession session, int categoryIndex) {
        ConditionCategory[] categories = ConditionCategory.values();
        if (categoryIndex >= categories.length) {
            playSound(player, "click");
            return;
        }

        ConditionCategory newCategory = categories[categoryIndex];
        playSound(player, "click");

        // Reopen with new category
        session.put("selectedCategory", newCategory);
        session.put("page", 0);
        openGUI(guiManager, player, session);
    }

    /**
     * Handle back button - return to Flow Builder.
     */
    private void handleBack(Player player, GUISession session) {
        playSound(player, "click");
        returnToFlowBuilder(player, session);
    }

    /**
     * Handle previous page.
     */
    private void handlePrevPage(Player player, GUISession session, ConditionCategory category, int page) {
        if (page > 0) {
            playSound(player, "click");
            session.put("page", page - 1);
            openGUI(guiManager, player, session);
        } else {
            playSound(player, "error");
        }
    }

    /**
     * Handle next page.
     */
    private void handleNextPage(Player player, GUISession session, ConditionCategory category, int page) {
        ConditionType[] conditions = ConditionType.getByCategory(category);
        int maxPages = (int) Math.ceil((double) conditions.length / ITEMS_PER_PAGE);

        if (page < maxPages - 1) {
            playSound(player, "click");
            session.put("page", page + 1);
            openGUI(guiManager, player, session);
        } else {
            playSound(player, "error");
        }
    }

    /**
     * Handle condition selection.
     */
    private void handleConditionSelect(Player player, GUISession session, int slot,
                                       ConditionCategory category, int page) {
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
        ConditionNode conditionNode = session.get("conditionNode", ConditionNode.class);

        playSound(player, "success");

        // If condition has parameters, ask for them
        if (selectedType.hasParameters()) {
            // Open parameter input using sign input
            String paramHint = selectedType.getExampleFormat().replace(selectedType.name() + ":", "");
            String currentCondition = conditionNode.getCondition();
            String defaultValue = paramHint.isEmpty() ? "50" : paramHint;

            inputHelper.requestText(player, selectedType.getDisplayName() + " Value", defaultValue,
                    value -> {
                        // Build condition string
                        String conditionString = selectedType.name() + ":" + value;
                        conditionNode.setCondition(conditionString);
                        player.sendMessage(TextUtil.colorize("&aCondition set: &f" + conditionString));
                        returnToFlowBuilder(player, session);
                    },
                    () -> {
                        // Cancelled - just set type without params
                        conditionNode.setCondition(selectedType.name());
                        player.sendMessage(TextUtil.colorize("&aCondition set: &f" + selectedType.name()));
                        returnToFlowBuilder(player, session);
                    }
            );
        } else {
            // No parameters - set directly
            conditionNode.setCondition(selectedType.name());
            player.sendMessage(TextUtil.colorize("&aCondition set: &f" + selectedType.name()));
            returnToFlowBuilder(player, session);
        }
    }

    /**
     * Return to the Flow Builder GUI.
     */
    private void returnToFlowBuilder(Player player, GUISession session) {
        GUISession flowSession = session.get("flowBuilderSession", GUISession.class);
        if (flowSession != null) {
            Sigil sigil = flowSession.get("sigil", Sigil.class);
            String signalKey = flowSession.get("signalKey", String.class);
            FlowGraph flow = flowSession.get("flow", FlowGraph.class);

            if (sigil != null && signalKey != null) {
                FlowBuilderHandler.openGUI(guiManager, player, sigil, signalKey, flow, flowSession);
                return;
            }
        }
        player.closeInventory();
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
     * Open the Flow Condition Selector GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player, GUISession session) {
        ConditionCategory category = session.get("selectedCategory", ConditionCategory.class);
        if (category == null) {
            category = ConditionCategory.HEALTH;
            session.put("selectedCategory", category);
        }

        int page = session.getInt("page", 0);

        Inventory inv = Bukkit.createInventory(null, 54,
            TextUtil.parseComponent("&7Condition Node > &fSelect Type"));

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
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("Flow Builder"));

        // Pagination
        int maxPages = (int) Math.ceil((double) conditions.length / ITEMS_PER_PAGE);
        if (maxPages <= 0) maxPages = 1;

        if (page > 0) {
            inv.setItem(SLOT_PREV, ItemBuilder.createItem(
                Material.ARROW,
                "&ePrevious Page",
                "&7Page " + page + "/" + maxPages
            ));
        }

        if (page < maxPages - 1) {
            inv.setItem(SLOT_NEXT, ItemBuilder.createItem(
                Material.ARROW,
                "&eNext Page",
                "&7Page " + (page + 2) + "/" + maxPages
            ));
        }

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Build a category tab item.
     */
    private static ItemStack buildCategoryTab(ConditionCategory category, boolean selected) {
        Material material = getCategoryMaterial(category);
        String prefix = selected ? "&a" : "&7";
        String indicator = selected ? " &a(Selected)" : "";

        List<String> lore = new ArrayList<>();
        lore.add("&7" + category.getDescription());
        lore.add("");
        lore.add("&7Conditions: &f" + ConditionType.getByCategory(category).length);
        lore.add("");
        lore.add("&7Click to view");

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
        String displayName = "&e" + type.getDisplayName();

        List<String> lore = new ArrayList<>();
        lore.add("&7" + type.getDescription());
        lore.add("");
        lore.add("&7Example: &f" + type.getExampleFormat());
        lore.add("&8" + type.getExampleDescription());
        lore.add("");

        if (type.hasParameters()) {
            lore.add("&aRequires configuration");
        } else {
            lore.add("&aNo parameters needed");
        }

        lore.add("");
        lore.add("&7Click to select");

        return ItemBuilder.createItem(material, displayName, lore);
    }
}
