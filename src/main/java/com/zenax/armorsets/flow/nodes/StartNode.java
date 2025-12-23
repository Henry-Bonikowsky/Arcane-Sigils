package com.zenax.armorsets.flow.nodes;

import com.zenax.armorsets.flow.FlowContext;
import com.zenax.armorsets.flow.FlowNode;
import com.zenax.armorsets.flow.NodeType;

import java.util.Collections;
import java.util.List;

/**
 * Start node - entry point for a flow.
 * Every flow must have exactly one start node.
 */
public class StartNode extends FlowNode {

    public StartNode(String id) {
        super(id);
        setDisplayName("Start");
    }

    @Override
    public NodeType getType() {
        return NodeType.START;
    }

    @Override
    public String execute(FlowContext context) {
        // Start node just passes through to the next node
        return "next";
    }

    @Override
    public List<String> validate() {
        // Start node must have a "next" connection
        List<String> errors = new java.util.ArrayList<>();
        if (getConnection("next") == null) {
            errors.add("Start node must connect to another node");
        }
        return errors;
    }

    @Override
    public FlowNode deepCopy() {
        StartNode copy = new StartNode(getId());
        copyBaseTo(copy);
        return copy;
    }
}
