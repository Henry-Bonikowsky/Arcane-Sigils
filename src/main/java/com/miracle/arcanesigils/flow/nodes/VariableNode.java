package com.miracle.arcanesigils.flow.nodes;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.flow.FlowContext;
import com.miracle.arcanesigils.flow.FlowNode;
import com.miracle.arcanesigils.flow.NodeType;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Variable node - sets or modifies a variable in the flow context.
 * Variables can be used in expressions throughout the flow.
 */
public class VariableNode extends FlowNode {

    /**
     * Variable scope.
     */
    public enum VariableScope {
        /**
         * Flow-scoped - stored in FlowContext, lasts for one flow execution.
         */
        FLOW,

        /**
         * Player-scoped - stored in PlayerVariableManager, persists across flows with duration.
         */
        PLAYER,

        /**
         * Sigil-scoped - stored in SigilVariableManager, per-instance (player + sigilId + slot).
         */
        SIGIL
    }

    /**
     * Operation type.
     */
    public enum Operation {
        /**
         * Set variable to a value.
         */
        SET,

        /**
         * Add to variable (numeric).
         */
        ADD,

        /**
         * Subtract from variable (numeric).
         */
        SUBTRACT,

        /**
         * Multiply variable (numeric).
         */
        MULTIPLY,

        /**
         * Divide variable (numeric).
         */
        DIVIDE
    }

    public VariableNode(String id) {
        super(id);
        setDisplayName("Variable");
        setParam("operation", Operation.SET.name());
        setParam("scope", VariableScope.FLOW.name());
        setParam("name", "myVar");
        setParam("value", 0);
        setParam("duration", 0); // Only for PLAYER and SIGIL scopes
        setParam("sigilId", ""); // Only for SIGIL scope
        setParam("slot", ""); // Only for SIGIL scope (e.g., "CHESTPLATE")
    }

    @Override
    public NodeType getType() {
        return NodeType.VARIABLE;
    }

    @Override
    public String execute(FlowContext context) {
        // Get scope
        String scopeStr = getStringParam("scope", "FLOW");
        VariableScope scope;
        try {
            scope = VariableScope.valueOf(scopeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            scope = VariableScope.FLOW;
        }

        // Get operation
        String opStr = getStringParam("operation", "SET");
        Operation operation;
        try {
            operation = Operation.valueOf(opStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            operation = Operation.SET;
        }

        String varName = getStringParam("name", "myVar");
        Object rawValue = getParam("value", 0);

        // Resolve value if it's an expression
        double newValue;
        if (rawValue instanceof String str) {
            newValue = context.resolveNumeric(str, 0);
        } else if (rawValue instanceof Number num) {
            newValue = num.doubleValue();
        } else {
            newValue = 0;
        }

        // Get current value for operations other than SET
        double currentValue = context.getVariableAsDouble(varName, 0);

        double result = switch (operation) {
            case SET -> newValue;
            case ADD -> currentValue + newValue;
            case SUBTRACT -> currentValue - newValue;
            case MULTIPLY -> currentValue * newValue;
            case DIVIDE -> newValue != 0 ? currentValue / newValue : currentValue;
        };

        // Handle scope
        if (scope == VariableScope.PLAYER) {
            Player player = context.getPlayer();
            if (player == null) {
                LogHelper.debug("[VariableNode] PLAYER scope requires a player context");
                return "next";
            }

            // Get duration (only for player scope)
            Object durationObj = getParam("duration");
            int duration;
            if (durationObj instanceof String str) {
                duration = (int) context.resolveNumeric(str, 0);
            } else if (durationObj instanceof Number num) {
                duration = num.intValue();
            } else {
                duration = 0;
            }

            // Store in player variable manager
            ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
            plugin.getPlayerVariableManager().setVariable(player.getUniqueId(), varName, result, duration);

            LogHelper.debug("[VariableNode] PLAYER %s %s: %s (%.2f -> %.2f, duration=%ds)",
                    operation, varName, rawValue, currentValue, result, duration);
        } else if (scope == VariableScope.SIGIL) {
            Player player = context.getPlayer();
            if (player == null) {
                LogHelper.debug("[VariableNode] SIGIL scope requires a player context");
                return "next";
            }

            // Get sigilId and slot params
            String sigilId = getStringParam("sigilId", "");
            String slot = getStringParam("slot", "");
            
            if (sigilId.isEmpty() || slot.isEmpty()) {
                LogHelper.debug("[VariableNode] SIGIL scope requires sigilId and slot params");
                return "next";
            }

            // Get duration
            Object durationObj = getParam("duration");
            int duration;
            if (durationObj instanceof String str) {
                duration = (int) context.resolveNumeric(str, 0);
            } else if (durationObj instanceof Number num) {
                duration = num.intValue();
            } else {
                duration = 0;
            }

            ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
            
            // For operations other than SET, get current sigil variable value
            if (operation != Operation.SET) {
                Object currentObj = plugin.getSigilVariableManager()
                    .getSigilVariable(player, sigilId, slot, varName);
                if (currentObj instanceof Number num) {
                    currentValue = num.doubleValue();
                } else {
                    currentValue = 0;
                }
                
                // Recalculate result with actual current value
                result = switch (operation) {
                    case SET -> newValue;
                    case ADD -> currentValue + newValue;
                    case SUBTRACT -> currentValue - newValue;
                    case MULTIPLY -> currentValue * newValue;
                    case DIVIDE -> newValue != 0 ? currentValue / newValue : currentValue;
                };
            }
            
            // Store in sigil variable manager
            plugin.getSigilVariableManager()
                .setSigilVariable(player, sigilId, slot, varName, result, duration);

            LogHelper.debug("[VariableNode] SIGIL %s %s (sigil=%s, slot=%s): %s (%.2f -> %.2f, duration=%ds)",
                    operation, varName, sigilId, slot, rawValue, currentValue, result, duration);
        } else {
            // Flow-scoped - existing behavior
            context.setVariable(varName, result);

            LogHelper.info("[VariableNode] FLOW %s %s: raw=%s, current=%s, result=%s",
                    operation, varName, rawValue,
                    java.math.BigDecimal.valueOf(currentValue).stripTrailingZeros().toPlainString(),
                    java.math.BigDecimal.valueOf(result).stripTrailingZeros().toPlainString());
        }

        return "next";
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        String varName = getStringParam("name", "");
        if (varName.isEmpty()) {
            errors.add("Variable name is required");
        } else if (!varName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            errors.add("Invalid variable name (use letters, numbers, underscore)");
        }

        if (getConnection("next") == null) {
            errors.add("Output 'next' is not connected");
        }

        return errors;
    }

    @Override
    public FlowNode deepCopy() {
        VariableNode copy = new VariableNode(getId());
        copyBaseTo(copy);
        return copy;
    }
}
