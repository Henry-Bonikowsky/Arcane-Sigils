package com.zenax.armorsets.flow.nodes;

import com.zenax.armorsets.flow.FlowContext;
import com.zenax.armorsets.flow.FlowNode;
import com.zenax.armorsets.flow.NodeType;
import com.zenax.armorsets.utils.LogHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Math node - performs mathematical calculations and stores result in a variable.
 */
public class MathNode extends FlowNode {

    /**
     * Math operation type.
     */
    public enum MathOperation {
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        MODULO,
        MIN,
        MAX,
        ABS,
        ROUND,
        FLOOR,
        CEIL,
        RANDOM,      // Random between left and right
        POWER,       // left ^ right
        SQRT         // Square root of left
    }

    public MathNode(String id) {
        super(id);
        setDisplayName("Math");
        setParam("operation", MathOperation.ADD.name());
        setParam("left", 0);
        setParam("right", 0);
        setParam("result", "result");
    }

    @Override
    public NodeType getType() {
        return NodeType.MATH;
    }

    @Override
    public String execute(FlowContext context) {
        String opStr = getStringParam("operation", "ADD");
        MathOperation operation;
        try {
            operation = MathOperation.valueOf(opStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            operation = MathOperation.ADD;
        }

        // Get operands
        double left = resolveOperand(context, "left");
        double right = resolveOperand(context, "right");

        // Perform operation
        double result = switch (operation) {
            case ADD -> left + right;
            case SUBTRACT -> left - right;
            case MULTIPLY -> left * right;
            case DIVIDE -> right != 0 ? left / right : 0;
            case MODULO -> right != 0 ? left % right : 0;
            case MIN -> Math.min(left, right);
            case MAX -> Math.max(left, right);
            case ABS -> Math.abs(left);
            case ROUND -> Math.round(left);
            case FLOOR -> Math.floor(left);
            case CEIL -> Math.ceil(left);
            case RANDOM -> left + ThreadLocalRandom.current().nextDouble() * (right - left);
            case POWER -> Math.pow(left, right);
            case SQRT -> Math.sqrt(left);
        };

        // Store result
        String resultVar = getStringParam("result", "result");
        context.setVariable(resultVar, result);

        LogHelper.debug("[MathNode] %s: %.2f %s %.2f = %.2f -> $%s",
                operation, left, getOperatorSymbol(operation), right, result, resultVar);

        return "next";
    }

    private double resolveOperand(FlowContext context, String paramName) {
        Object value = getParam(paramName, 0);

        if (value instanceof Number num) {
            return num.doubleValue();
        }

        if (value instanceof String str) {
            // Check if it's a variable reference
            if (str.startsWith("$")) {
                return context.getVariableAsDouble(str.substring(1), 0);
            }
            // Resolve as expression
            return context.resolveNumeric(str, 0);
        }

        return 0;
    }

    private String getOperatorSymbol(MathOperation op) {
        return switch (op) {
            case ADD -> "+";
            case SUBTRACT -> "-";
            case MULTIPLY -> "*";
            case DIVIDE -> "/";
            case MODULO -> "%";
            case POWER -> "^";
            default -> op.name();
        };
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        String resultVar = getStringParam("result", "");
        if (resultVar.isEmpty()) {
            errors.add("Result variable name is required");
        } else if (!resultVar.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            errors.add("Invalid result variable name");
        }

        if (getConnection("next") == null) {
            errors.add("Output 'next' is not connected");
        }

        return errors;
    }

    @Override
    public FlowNode deepCopy() {
        MathNode copy = new MathNode(getId());
        copyBaseTo(copy);
        return copy;
    }
}
