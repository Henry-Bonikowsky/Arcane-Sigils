package com.miracle.arcanesigils.flow.nodes;

import com.miracle.arcanesigils.flow.FlowContext;
import com.miracle.arcanesigils.flow.FlowNode;
import com.miracle.arcanesigils.flow.NodeType;
import com.miracle.arcanesigils.utils.LogHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Condition node - branches based on a condition.
 * Has two outputs: "yes" (condition true) and "no" (condition false).
 * BOTH outputs must be connected for the flow to be valid.
 */
public class ConditionNode extends FlowNode {

    /**
     * The condition expression to evaluate.
     * Examples:
     *   "{player.health_percent} < 50"
     *   "{victim.health} > 10"
     *   "random(25%)"
     *   "{damage} >= 5"
     */
    private String condition;

    public ConditionNode(String id) {
        super(id);
        setDisplayName("Condition");
    }

    public ConditionNode(String id, String condition) {
        super(id);
        this.condition = condition;
        setDisplayName("Condition");
    }

    @Override
    public NodeType getType() {
        return NodeType.CONDITION;
    }

    @Override
    public List<String> getOutputPorts() {
        return Arrays.asList("yes", "no");
    }

    @Override
    public String execute(FlowContext context) {
        if (condition == null || condition.isEmpty()) {
            LogHelper.debug("[ConditionNode] No condition configured, defaulting to 'yes'");
            return "yes";
        }

        // In test mode, randomly choose YES or NO to demonstrate both paths
        if (context.isTestMode()) {
            boolean result = Math.random() > 0.5;
            String path = result ? "yes" : "no";
            context.addTraceEntry("§a✓ CONDITION §7(" + condition + ") §e→ " + path.toUpperCase() + " path §7(test mode)");
            LogHelper.debug("[ConditionNode] Test mode - randomly chose: %s", path);
            return path;
        }

        // Normal execution with condition evaluation
        boolean result = context.evaluateCondition(condition);
        LogHelper.debug("[ConditionNode] Condition '%s' evaluated to: %b", condition, result);

        String path = result ? "yes" : "no";
        if (context.isTestMode()) {
            context.addTraceEntry("  §e→ " + path.toUpperCase() + " path");
        }

        return path;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        // Condition is required
        if (condition == null || condition.isEmpty()) {
            errors.add("Condition expression not configured");
        }

        // Both outputs must be connected
        if (getConnection("yes") == null) {
            errors.add("'Yes' path is not connected");
        }
        if (getConnection("no") == null) {
            errors.add("'No' path is not connected");
        }

        return errors;
    }

    // ============ Getters/Setters ============

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    @Override
    public FlowNode deepCopy() {
        ConditionNode copy = new ConditionNode(getId());
        copy.condition = this.condition;
        copyBaseTo(copy);
        return copy;
    }
}
