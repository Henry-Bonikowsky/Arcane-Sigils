package com.zenax.armorsets.gui.condition;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.core.SigilManager;
import com.zenax.armorsets.events.ConditionType;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.utils.TextUtil;
import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.SignGUIAction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for the CONDITION_PARAM GUI.
 * Allows configuring parameters for a condition.
 *
 * Layout (3 rows = 27 slots):
 * Row 0: [_][_][_][N][_][_][_][_][_]
 * Row 1: [_][V][V][V][C][C][C][_][_]
 * Row 2: [X][_][_][_][S][_][_][_][_]
 *
 * Where:
 * N = Condition name/type display (slot 3)
 * V = Value input (slots 10-12)
 * C = Comparison operators (slots 13-15)
 * X = Back/Cancel (slot 18)
 * S = Save/Confirm (slot 22)
 */
public class ConditionParamHandler extends AbstractHandler {

    // Slot positions
    private static final int SLOT_NAME = 3;
    private static final int SLOT_VALUE = 11;
    private static final int[] COMPARISON_SLOTS = {13, 14, 15};
    private static final int SLOT_BACK = 18;
    private static final int SLOT_SAVE = 22;

    private static final String[] COMPARISONS = {"<", "=", ">"};
    private static final String[] COMPARISON_NAMES = {"Less Than", "Equals", "Greater Than"};

    private final SigilManager sigilManager;

    public ConditionParamHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
        this.sigilManager = plugin.getSigilManager();
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        int conditionIndex = session.getInt("conditionIndex", -1);
        String conditionType = session.get("conditionType", String.class);
        boolean isAbilityMode = session.getBooleanOpt("isAbilityMode");

        if (sigil == null || signalKey == null || conditionType == null) {
            player.sendMessage(TextUtil.colorize("§cError: Invalid session data!"));
            player.closeInventory();
            return;
        }

        // Check comparison slots
        int compIndex = getComparisonIndex(slot);
        if (compIndex >= 0) {
            handleComparisonSelect(player, session, sigil, signalKey, conditionIndex, conditionType, isAbilityMode, compIndex);
            return;
        }

        switch (slot) {
            case SLOT_VALUE -> handleValueInput(player, session, sigil, signalKey, conditionIndex, conditionType, isAbilityMode);
            case SLOT_BACK -> handleBack(player, session, isAbilityMode);
            case SLOT_SAVE -> handleSave(player, session, sigil, signalKey, conditionIndex, isAbilityMode);
            default -> playSound(player, "click");
        }
    }

    /**
     * Handle comparison operator selection.
     */
    private void handleComparisonSelect(Player player, GUISession session, Sigil sigil, String signalKey,
                                        int conditionIndex, String conditionType, boolean isAbilityMode, int compIndex) {
        String comparison = COMPARISONS[compIndex];
        session.put("comparison", comparison);
        playSound(player, "click");

        // Refresh GUI
        String value = session.get("value", String.class);
        openGUI(guiManager, player, sigil, signalKey, conditionIndex, conditionType, isAbilityMode, value, comparison);
    }

    /**
     * Handle value input via sign.
     */
    private void handleValueInput(Player player, GUISession session, Sigil sigil, String signalKey,
                                  int conditionIndex, String conditionType, boolean isAbilityMode) {
        playSound(player, "click");

        String currentValue = session.get("value", String.class);
        if (currentValue == null) currentValue = "";
        String comparison = session.get("comparison", String.class);
        if (comparison == null) comparison = "<";

        // Get the condition type for context
        ConditionType type = null;
        try {
            type = ConditionType.valueOf(conditionType);
        } catch (IllegalArgumentException ignored) {}

        String hint = type != null ? getInputHint(type) : "Enter value";

        final String finalComparison = comparison;

        try {
            SignGUI signGUI = SignGUI.builder()
                .setLines("", "^^^^^", hint, "")
                .setHandler((p, result) -> {
                    String input = result.getLine(0).trim();

                    if (input.isEmpty()) {
                        return List.of(SignGUIAction.run(() -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                openGUI(guiManager, player, sigil, signalKey, conditionIndex, conditionType, isAbilityMode, "", finalComparison);
                            });
                        }));
                    }

                    return List.of(SignGUIAction.run(() -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            openGUI(guiManager, player, sigil, signalKey, conditionIndex, conditionType, isAbilityMode, input, finalComparison);
                        });
                    }));
                })
                .build();

            signGUI.open(player);
        } catch (Exception e) {
            player.sendMessage(com.zenax.armorsets.utils.TextUtil.colorize("§cFailed to open sign input. Please try again."));
            e.printStackTrace();
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
            // Go back to condition selector
            ConditionSelectorHandler.openGUI(guiManager, player, sigil, signalKey, isAbilityMode);
        } else {
            player.closeInventory();
        }
    }

    /**
     * Handle save button.
     */
    private void handleSave(Player player, GUISession session, Sigil sigil, String signalKey,
                            int conditionIndex, boolean isAbilityMode) {
        String conditionType = session.get("conditionType", String.class);
        String value = session.get("value", String.class);
        String comparison = session.get("comparison", String.class);

        // Build final condition string
        String conditionString = buildConditionString(conditionType, value, comparison);

        if (conditionString == null || conditionString.isEmpty()) {
            player.sendMessage(TextUtil.colorize("§cPlease configure the condition parameters!"));
            playSound(player, "error");
            return;
        }

        // Get conditions list
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

        playSound(player, "success");

        // Save and return to config
        sigilManager.saveSigil(sigil);

        if (isAbilityMode) {
            ConditionConfigHandler.openAbilityGUI(guiManager, player, sigil);
        } else {
            ConditionConfigHandler.openGUI(guiManager, player, sigil, signalKey);
        }
    }

    /**
     * Build condition string from components.
     */
    private String buildConditionString(String type, String value, String comparison) {
        if (type == null) return null;

        // Get condition type to check if it needs parameters
        ConditionType condType = null;
        try {
            condType = ConditionType.valueOf(type);
        } catch (IllegalArgumentException ignored) {}

        if (condType != null && !condType.hasParameters()) {
            return type;
        }

        // For conditions with value parameters
        if (value != null && !value.isEmpty()) {
            // Check if this is a comparison condition (numeric)
            if (comparison != null && isNumericCondition(type)) {
                return type + ":" + comparison + value;
            }
            return type + ":" + value;
        }

        return type;
    }

    /**
     * Check if condition type uses numeric comparison.
     */
    private boolean isNumericCondition(String type) {
        return switch (type.toUpperCase()) {
            case "HEALTH_PERCENT", "HEALTH", "VICTIM_HEALTH_PERCENT", "HUNGER",
                 "LIGHT_LEVEL", "Y_LEVEL", "EXPERIENCE_LEVEL" -> true;
            default -> false;
        };
    }

    /**
     * Get input hint for condition type.
     */
    private String getInputHint(ConditionType type) {
        return switch (type) {
            case HEALTH_PERCENT, VICTIM_HEALTH_PERCENT -> "Percent (0-100)";
            case HEALTH -> "HP amount";
            case HUNGER -> "Food (0-20)";
            case LIGHT_LEVEL -> "Light (0-15)";
            case Y_LEVEL -> "Y coordinate";
            case EXPERIENCE_LEVEL -> "XP level";
            case HAS_POTION, NO_POTION -> "Potion type";
            case BIOME -> "Biome name";
            case BLOCK_BELOW -> "Block type";
            case WEATHER -> "CLEAR/RAINING";
            case TIME -> "DAY/NIGHT";
            case MAIN_HAND -> "Material";
            case HAS_ENCHANT -> "Enchant name";
            case DIMENSION -> "Dimension";
            default -> "Enter value";
        };
    }

    /**
     * Get comparison index from slot.
     */
    private int getComparisonIndex(int slot) {
        for (int i = 0; i < COMPARISON_SLOTS.length; i++) {
            if (COMPARISON_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Open the Condition Param GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey,
                               int conditionIndex, String conditionString, boolean isAbilityMode) {
        // Parse existing condition
        String[] parts = conditionString.split(":");
        String conditionType = parts[0].toUpperCase();
        String value = "";
        String comparison = "<";

        // Extract value and comparison from existing condition
        if (parts.length > 1) {
            String param = parts[1];
            if (param.startsWith(">=") || param.startsWith("<=")) {
                comparison = param.substring(0, 2);
                value = param.substring(2);
            } else if (param.startsWith("<") || param.startsWith(">") || param.startsWith("=")) {
                comparison = param.substring(0, 1);
                value = param.substring(1);
            } else {
                value = param;
            }
        }

        openGUI(guiManager, player, sigil, signalKey, conditionIndex, conditionType, isAbilityMode, value, comparison);
    }

    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey,
                               int conditionIndex, String conditionType, boolean isAbilityMode,
                               String value, String comparison) {
        if (sigil == null || signalKey == null || conditionType == null) {
            player.sendMessage(TextUtil.colorize("§cError: Invalid parameters!"));
            return;
        }

        // Get condition type info
        ConditionType type = null;
        try {
            type = ConditionType.valueOf(conditionType);
        } catch (IllegalArgumentException ignored) {}

        Inventory inv = Bukkit.createInventory(null, 27,
            TextUtil.parseComponent("§7Condition > §f" + (type != null ? type.getDisplayName() : conditionType)));

        // Fill with background
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, ItemBuilder.createBackground());
        }

        // Condition name display (slot 3)
        if (type != null) {
            List<String> nameLore = new ArrayList<>();
            nameLore.add("§7" + type.getDescription());
            nameLore.add("");
            nameLore.add("§7Example: §f" + type.getExampleFormat());
            nameLore.add("§8" + type.getExampleDescription());
            inv.setItem(SLOT_NAME, ItemBuilder.createItem(type.getIcon(), "§e" + type.getDisplayName(), nameLore));
        } else {
            inv.setItem(SLOT_NAME, ItemBuilder.createItem(Material.PAPER, "§e" + conditionType, "§7Unknown condition type"));
        }

        // Check if this condition needs parameters
        boolean needsValue = type == null || type.hasParameters();
        boolean needsComparison = isNumericConditionType(conditionType);

        if (needsValue) {
            // Value input button (slot 11)
            String valueDisplay = (value != null && !value.isEmpty()) ? value : "§7Click to set";
            inv.setItem(SLOT_VALUE, ItemBuilder.createItem(
                Material.NAME_TAG,
                "§bValue: " + valueDisplay,
                "§7Click to enter a value",
                "",
                "§7Current: §f" + (value != null && !value.isEmpty() ? value : "(none)")
            ));
        }

        if (needsComparison) {
            // Comparison operator buttons (slots 13-15)
            for (int i = 0; i < COMPARISONS.length; i++) {
                boolean selected = COMPARISONS[i].equals(comparison);
                Material mat = selected ? Material.LIME_DYE : Material.GRAY_DYE;
                String prefix = selected ? "§a" : "§7";
                inv.setItem(COMPARISON_SLOTS[i], ItemBuilder.createItem(
                    mat,
                    prefix + COMPARISONS[i] + " " + COMPARISON_NAMES[i],
                    selected ? "§aSelected" : "§7Click to select"
                ));
            }
        }

        // Back button
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("Condition Selector"));

        // Preview and save button
        String preview = buildPreviewString(conditionType, value, comparison, needsComparison);
        inv.setItem(SLOT_SAVE, ItemBuilder.createItem(
            Material.LIME_DYE,
            "§aSave Condition",
            "§7Preview: §f" + preview,
            "",
            "§7Click to save"
        ));

        // Create session
        GUISession session = new GUISession(GUIType.CONDITION_PARAM);
        session.put("sigil", sigil);
        session.put("signalKey", signalKey);
        session.put("conditionIndex", conditionIndex);
        session.put("conditionType", conditionType);
        session.put("isAbilityMode", isAbilityMode);
        session.put("value", value);
        session.put("comparison", comparison);

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Check if condition type uses numeric comparison (static version).
     */
    private static boolean isNumericConditionType(String type) {
        return switch (type.toUpperCase()) {
            case "HEALTH_PERCENT", "HEALTH", "VICTIM_HEALTH_PERCENT", "HUNGER",
                 "LIGHT_LEVEL", "Y_LEVEL", "EXPERIENCE_LEVEL" -> true;
            default -> false;
        };
    }

    /**
     * Build preview string.
     */
    private static String buildPreviewString(String type, String value, String comparison, boolean numeric) {
        if (value == null || value.isEmpty()) {
            return type;
        }
        if (numeric && comparison != null) {
            return type + ":" + comparison + value;
        }
        return type + ":" + value;
    }
}
