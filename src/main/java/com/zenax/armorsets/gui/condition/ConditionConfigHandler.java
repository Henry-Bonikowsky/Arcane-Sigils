package com.zenax.armorsets.gui.condition;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.core.SigilManager;
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
 * Handler for the CONDITION_CONFIG GUI.
 * Allows viewing and managing conditions for a specific signal or ability.
 *
 * Layout (3 rows = 27 slots):
 * Row 0: [L][_][_][_][_][_][_][_][_]
 * Row 1: [_][C][C][C][C][C][C][C][_]
 * Row 2: [X][_][_][D][A][_][_][_][_]
 *
 * Where:
 * L = Logic toggle AND/OR (slot 0) [COMPARATOR]
 * C = Condition slots (10-16) - 7 condition items
 * X = Back button (slot 18) [RED_DYE]
 * D = Delete mode toggle (slot 21)
 * A = Add condition (slot 22)
 */
public class ConditionConfigHandler extends AbstractHandler {

    // Slot positions
    private static final int SLOT_LOGIC = 0;
    private static final int[] CONDITION_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int SLOT_BACK = 18;
    private static final int SLOT_DELETE_MODE = 21;
    private static final int SLOT_ADD = 22;

    private final SigilManager sigilManager;

    public ConditionConfigHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
        this.sigilManager = plugin.getSigilManager();
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        boolean isAbilityMode = session.getBooleanOpt("isAbilityMode");

        if (sigil == null || signalKey == null) {
            player.sendMessage(TextUtil.colorize("§cError: Invalid session data!"));
            player.closeInventory();
            return;
        }

        // Get conditions list and logic based on mode
        List<String> conditions;
        FlowConfig.ConditionLogic logic;
        if (isAbilityMode) {
            Sigil.ActivationConfig activation = sigil.getActivation();
            if (activation == null) {
                activation = new Sigil.ActivationConfig();
                sigil.setActivation(activation);
            }
            conditions = activation.getConditions();
            logic = activation.getConditionLogic();
        } else {
            FlowConfig flowConfig = sigil.getFlowForTrigger(signalKey);
            if (flowConfig == null) {
                player.sendMessage(TextUtil.colorize("§cError: Flow not found!"));
                player.closeInventory();
                return;
            }
            conditions = flowConfig.getConditions();
            logic = flowConfig.getConditionLogic();
        }

        switch (slot) {
            case SLOT_LOGIC -> handleLogicToggle(player, session, sigil, signalKey, isAbilityMode);
            case SLOT_BACK -> handleBack(player, session, isAbilityMode);
            case SLOT_DELETE_MODE -> handleDeleteMode(player, session);
            case SLOT_ADD -> handleAdd(player, session);
            default -> handleConditionSlot(player, session, sigil, signalKey, conditions, slot, isAbilityMode);
        }
    }

    /**
     * Handle AND/OR logic toggle.
     */
    private void handleLogicToggle(Player player, GUISession session, Sigil sigil, String signalKey, boolean isAbilityMode) {
        FlowConfig.ConditionLogic newLogic;

        if (isAbilityMode) {
            Sigil.ActivationConfig activation = sigil.getActivation();
            FlowConfig.ConditionLogic currentLogic = activation.getConditionLogic();
            newLogic = (currentLogic == FlowConfig.ConditionLogic.AND)
                ? FlowConfig.ConditionLogic.OR : FlowConfig.ConditionLogic.AND;
            activation.setConditionLogic(newLogic);
        } else {
            FlowConfig flowConfig = sigil.getFlowForTrigger(signalKey);
            FlowConfig.ConditionLogic currentLogic = flowConfig.getConditionLogic();
            newLogic = (currentLogic == FlowConfig.ConditionLogic.AND)
                ? FlowConfig.ConditionLogic.OR : FlowConfig.ConditionLogic.AND;
            flowConfig.setConditionLogic(newLogic);
        }

        player.sendMessage(TextUtil.colorize("§aLogic mode: §f" + newLogic.name()));
        playSound(player, "click");

        // Save and refresh
        sigilManager.saveSigil(sigil);
        if (isAbilityMode) {
            openAbilityGUI(guiManager, player, sigil);
        } else {
            openGUI(guiManager, player, sigil, signalKey);
        }
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
                // Go back to Sigil Editor for ability mode
                com.zenax.armorsets.gui.sigil.SigilEditorHandler.openGUI(guiManager, player, sigil);
            } else {
                // Go back to Flow Builder for signal mode
                com.zenax.armorsets.gui.flow.FlowBuilderHandler.openGUI(guiManager, player, sigil, signalKey);
            }
        } else {
            player.closeInventory();
        }
    }

    /**
     * Handle delete mode toggle.
     */
    private void handleDeleteMode(Player player, GUISession session) {
        boolean deleteMode = session.getBooleanOpt("deleteMode");
        boolean newDeleteMode = !deleteMode;
        boolean isAbilityMode = session.getBooleanOpt("isAbilityMode");

        String status = newDeleteMode ? "§cDelete Mode" : "§7Normal Mode";
        player.sendMessage(TextUtil.colorize("§7Mode: " + status));
        playSound(player, "click");

        // Refresh GUI with delete mode preserved
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);

        if (isAbilityMode) {
            openAbilityGUI(guiManager, player, sigil, newDeleteMode);
        } else {
            openGUI(guiManager, player, sigil, signalKey, newDeleteMode);
        }
    }

    /**
     * Handle add condition button.
     */
    private void handleAdd(Player player, GUISession session) {
        playSound(player, "click");

        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        boolean isAbilityMode = session.getBooleanOpt("isAbilityMode");

        // Open condition selector
        ConditionSelectorHandler.openGUI(guiManager, player, sigil, signalKey, isAbilityMode);
    }

    /**
     * Handle condition slot clicks.
     */
    private void handleConditionSlot(Player player, GUISession session, Sigil sigil, String signalKey,
                                     List<String> conditions, int slot, boolean isAbilityMode) {
        // Check if this is a condition slot
        int conditionIndex = getConditionIndex(slot);
        if (conditionIndex < 0 || conditionIndex >= conditions.size()) {
            playSound(player, "click");
            return;
        }

        boolean deleteMode = session.getBooleanOpt("deleteMode");

        if (deleteMode) {
            // Delete this condition
            String removedCondition = conditions.remove(conditionIndex);
            player.sendMessage(TextUtil.colorize("§cRemoved condition: §f" + removedCondition));
            playSound(player, "success");

            // Auto-save
            sigilManager.saveSigil(sigil);

            // Refresh GUI
            if (isAbilityMode) {
                openAbilityGUI(guiManager, player, sigil);
            } else {
                openGUI(guiManager, player, sigil, signalKey);
            }
        } else {
            // Edit this condition
            String conditionString = conditions.get(conditionIndex);
            playSound(player, "click");

            // Parse condition type
            String[] parts = conditionString.split(":");
            String conditionType = parts[0].toUpperCase();

            // Check if this condition type should use browser (BIOME, POTION, etc.)
            if (ConditionValueBrowserHandler.shouldUseBrowser(conditionType)) {
                // Open browser for selection-based conditions
                ConditionValueBrowserHandler.openGUI(guiManager, player, sigil, signalKey, conditionIndex, conditionType, isAbilityMode);
            } else {
                // Open param handler for numeric/custom conditions
                ConditionParamHandler.openGUI(guiManager, player, sigil, signalKey, conditionIndex, conditionString, isAbilityMode);
            }
        }
    }

    /**
     * Get condition index from slot position.
     */
    private int getConditionIndex(int slot) {
        for (int i = 0; i < CONDITION_SLOTS.length; i++) {
            if (CONDITION_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Open the Condition Config GUI for signal conditions.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey) {
        openGUI(guiManager, player, sigil, signalKey, false);
    }

    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey, boolean deleteMode) {
        if (sigil == null || signalKey == null) {
            player.sendMessage(TextUtil.colorize("§cError: Invalid parameters!"));
            return;
        }

        FlowConfig flowConfig = sigil.getFlowForTrigger(signalKey);
        if (flowConfig == null) {
            player.sendMessage(TextUtil.colorize("§cError: Flow not found!"));
            return;
        }

        List<String> conditions = flowConfig.getConditions();
        FlowConfig.ConditionLogic logic = flowConfig.getConditionLogic();

        Inventory inv = Bukkit.createInventory(null, 27,
            TextUtil.parseComponent("§7" + formatSignalName(signalKey) + " > §fConditions"));

        buildInventory(inv, conditions, logic, deleteMode, "Effect Config");

        // Create session
        GUISession session = new GUISession(GUIType.CONDITION_CONFIG);
        session.put("sigil", sigil);
        session.put("signalKey", signalKey);
        session.put("deleteMode", deleteMode);

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Open the Condition Config GUI for ability conditions.
     */
    public static void openAbilityGUI(GUIManager guiManager, Player player, Sigil sigil) {
        openAbilityGUI(guiManager, player, sigil, false);
    }

    public static void openAbilityGUI(GUIManager guiManager, Player player, Sigil sigil, boolean deleteMode) {
        if (sigil == null) {
            player.sendMessage(TextUtil.colorize("§cError: Invalid sigil!"));
            return;
        }

        Sigil.ActivationConfig activation = sigil.getActivation();
        if (activation == null) {
            activation = new Sigil.ActivationConfig();
            sigil.setActivation(activation);
        }

        List<String> conditions = activation.getConditions();
        FlowConfig.ConditionLogic logic = activation.getConditionLogic();

        Inventory inv = Bukkit.createInventory(null, 27,
            TextUtil.parseComponent("§7" + sigil.getName() + " > §fConditions"));

        buildInventory(inv, conditions, logic, deleteMode, "Sigil Editor");

        // Create session with ability flag
        GUISession session = new GUISession(GUIType.CONDITION_CONFIG);
        session.put("sigil", sigil);
        session.put("signalKey", "ability");
        session.put("isAbilityMode", true);
        session.put("deleteMode", deleteMode);

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Build the inventory contents.
     */
    private static void buildInventory(Inventory inv, List<String> conditions,
                                       FlowConfig.ConditionLogic logic, boolean deleteMode, String backTo) {
        // Fill decoration slots with background
        for (int i = 1; i <= 8; i++) {
            inv.setItem(i, ItemBuilder.createBackground());
        }
        inv.setItem(9, ItemBuilder.createBackground());
        inv.setItem(17, ItemBuilder.createBackground());
        inv.setItem(19, ItemBuilder.createBackground());
        inv.setItem(20, ItemBuilder.createBackground());
        for (int i = 23; i <= 26; i++) {
            inv.setItem(i, ItemBuilder.createBackground());
        }

        // Logic toggle button (slot 0)
        String logicColor = logic == FlowConfig.ConditionLogic.AND ? "§a" : "§6";
        String logicDesc = logic == FlowConfig.ConditionLogic.AND
            ? "§7All conditions must be true" : "§7Any condition can be true";
        inv.setItem(SLOT_LOGIC, ItemBuilder.createItem(
            Material.COMPARATOR,
            logicColor + "Logic: " + logic.name(),
            logicDesc,
            "",
            "§7Click to toggle AND/OR"
        ));

        // Condition items (slots 10-16)
        for (int i = 0; i < Math.min(conditions.size(), CONDITION_SLOTS.length); i++) {
            inv.setItem(CONDITION_SLOTS[i], buildConditionItem(conditions.get(i), i));
        }

        // Back button
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton(backTo));

        // Delete mode toggle
        Material deleteMaterial = deleteMode ? Material.LIME_DYE : Material.RED_DYE;
        String deleteStatus = deleteMode ? "§cDelete Mode: ON" : "§7Delete Mode: OFF";
        inv.setItem(SLOT_DELETE_MODE, ItemBuilder.createItem(
            deleteMaterial,
            deleteStatus,
            deleteMode ? "§7Click conditions to DELETE them" : "§7Click conditions to EDIT them",
            "",
            "§7Click to toggle"
        ));

        // Add condition button
        inv.setItem(SLOT_ADD, ItemBuilder.createItem(
            Material.LIME_DYE,
            "§aAdd Condition",
            "§7Add a new activation condition",
            "",
            "§7Click to add"
        ));
    }

    /**
     * Build a condition item for display.
     */
    private static ItemStack buildConditionItem(String conditionString, int index) {
        // Parse condition type from string
        String[] parts = conditionString.split(":");
        String typeStr = parts[0].toUpperCase();

        // Try to get the condition type enum
        ConditionType type = null;
        try {
            type = ConditionType.valueOf(typeStr);
        } catch (IllegalArgumentException ignored) {}

        Material material = type != null ? type.getIcon() : Material.PAPER;
        String displayName = type != null ? type.getDisplayName() : typeStr;

        // Build lore with parameters
        List<String> lore = new ArrayList<>();
        lore.add("§7Condition #" + (index + 1));
        lore.add("");

        if (type != null) {
            lore.add("§7" + type.getDescription());
            lore.add("");
        }

        // Show parameters if any
        if (parts.length > 1) {
            lore.add("§7Parameters:");
            for (int i = 1; i < parts.length; i++) {
                lore.add("§8  - §f" + parts[i]);
            }
            lore.add("");
        }

        lore.add("§eRaw: §f" + conditionString);
        lore.add("");
        lore.add("§7Left-click to edit");
        lore.add("§7Right-click (delete mode) to remove");

        return ItemBuilder.createItem(material, "§e" + displayName, lore);
    }

    /**
     * Format signal name for display.
     */
    private static String formatSignalName(String signalKey) {
        return TextUtil.toProperCase(signalKey.replace("_", " "));
    }
}
