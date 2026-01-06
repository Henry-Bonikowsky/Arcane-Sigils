package com.miracle.arcanesigils.flow.nodes;

import com.miracle.arcanesigils.flow.FlowContext;
import com.miracle.arcanesigils.flow.FlowNode;
import com.miracle.arcanesigils.flow.NodeType;
import com.miracle.arcanesigils.utils.LogHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Variable node - sets or modifies a variable in the flow context.
 * Variables can be used in expressions throughout the flow.
 */
public class VariableNode extends FlowNode {

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
        setParam("name", "myVar");
        setParam("value", 0);
    }

    @Override
    public NodeType getType() {
        return NodeType.VARIABLE;
    }

    @Override
    public String execute(FlowContext context) {
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

        context.setVariable(varName, result);

        LogHelper.debug("[VariableNode] %s %s: %s (%.2f -> %.2f)",
                operation, varName, rawValue, currentValue, result);

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
