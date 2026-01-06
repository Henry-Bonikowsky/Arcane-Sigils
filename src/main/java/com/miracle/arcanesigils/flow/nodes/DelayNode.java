package com.miracle.arcanesigils.flow.nodes;

import com.miracle.arcanesigils.flow.FlowContext;
import com.miracle.arcanesigils.flow.FlowNode;
import com.miracle.arcanesigils.flow.NodeType;
import com.miracle.arcanesigils.utils.LogHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Delay node - pauses flow execution for a specified duration.
 * The flow resumes asynchronously after the delay.
 */
public class DelayNode extends FlowNode {

    public DelayNode(String id) {
        super(id);
        setDisplayName("Delay");
        setParam("duration", 1.0); // Default 1 second
    }

    public DelayNode(String id, double durationSeconds) {
        super(id);
        setDisplayName("Delay");
        setParam("duration", durationSeconds);
    }

    @Override
    public NodeType getType() {
        return NodeType.DELAY;
    }

    @Override
    public String execute(FlowContext context) {
        double duration = getDoubleParam("duration", 1.0);

        // Check if duration is an expression
        Object durationObj = getParam("duration");
        if (durationObj instanceof String str && str.contains("{")) {
            duration = context.resolveNumeric(str, 1.0);
        }

        LogHelper.debug("[DelayNode] Delaying for %.2f seconds", duration);

        // The actual delay is handled by FlowExecutor
        // We just return "next" to signal continuation
        return "next";
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        double duration = getDoubleParam("duration", 0);
        if (duration <= 0) {
            errors.add("Duration must be greater than 0");
        }

        if (getConnection("next") == null) {
            errors.add("Output 'next' is not connected");
        }

        return errors;
    }

    @Override
    public FlowNode deepCopy() {
        DelayNode copy = new DelayNode(getId());
        copyBaseTo(copy);
        return copy;
    }
}
