package com.miracle.arcanesigils.flow.nodes;

import com.miracle.arcanesigils.flow.FlowContext;
import com.miracle.arcanesigils.flow.FlowNode;
import com.miracle.arcanesigils.flow.NodeType;
import com.miracle.arcanesigils.utils.LogHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Loop node - repeats a section of the flow.
 * Can loop a fixed number of times or while a condition is true.
 * Has two outputs: "body" (the loop body) and "done" (after loop completes).
 */
public class LoopNode extends FlowNode {

    /**
     * Loop type.
     */
    public enum LoopType {
        /**
         * Loop a fixed number of times.
         */
        COUNT,

        /**
         * Loop while a condition is true.
         */
        WHILE
    }

    /**
     * Current iteration counter stored in context.
     */
    private static final String ITERATION_KEY_PREFIX = "_loop_iteration_";

    public LoopNode(String id) {
        super(id);
        setDisplayName("Loop");
        setParam("type", LoopType.COUNT.name());
        setParam("count", 3);
    }

    @Override
    public NodeType getType() {
        return NodeType.LOOP;
    }

    @Override
    public List<String> getOutputPorts() {
        return Arrays.asList("body", "done");
    }

    @Override
    public String execute(FlowContext context) {
        String typeStr = getStringParam("type", "COUNT");
        LoopType loopType;
        try {
            loopType = LoopType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            loopType = LoopType.COUNT;
        }

        // Get current iteration from context
        String iterKey = ITERATION_KEY_PREFIX + getId();
        int currentIteration = (int) context.getVariableAsDouble(iterKey, 0);

        boolean shouldLoop;

        if (loopType == LoopType.COUNT) {
            int maxCount = getIntParam("count", 3);

            // Resolve expression if needed
            Object countObj = getParam("count");
            if (countObj instanceof String str && str.contains("{")) {
                maxCount = (int) context.resolveNumeric(str, 3);
            }

            shouldLoop = currentIteration < maxCount;
            LogHelper.debug("[LoopNode] COUNT loop: iteration %d/%d", currentIteration + 1, maxCount);
        } else {
            // WHILE loop
            String condition = getStringParam("condition", "");
            shouldLoop = context.evaluateCondition(condition);
            LogHelper.debug("[LoopNode] WHILE loop: condition '%s' = %b (iteration %d)",
                    condition, shouldLoop, currentIteration + 1);
        }

        if (shouldLoop) {
            // Increment iteration counter
            context.setVariable(iterKey, currentIteration + 1);

            // Set iteration variable for use in effects
            context.setVariable("iteration", currentIteration + 1);

            return "body";
        } else {
            // Reset iteration counter for next time
            context.setVariable(iterKey, 0);
            return "done";
        }
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        String typeStr = getStringParam("type", "COUNT");
        if ("WHILE".equalsIgnoreCase(typeStr)) {
            String condition = getStringParam("condition", "");
            if (condition.isEmpty()) {
                errors.add("While loop requires a condition");
            }
        } else {
            int count = getIntParam("count", 0);
            if (count <= 0) {
                errors.add("Loop count must be greater than 0");
            }
        }

        if (getConnection("body") == null) {
            errors.add("'Body' path is not connected");
        }
        if (getConnection("done") == null) {
            errors.add("'Done' path is not connected");
        }

        return errors;
    }

    @Override
    public FlowNode deepCopy() {
        LoopNode copy = new LoopNode(getId());
        copyBaseTo(copy);
        return copy;
    }
}
