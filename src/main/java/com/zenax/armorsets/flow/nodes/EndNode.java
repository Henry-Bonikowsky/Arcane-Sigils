package com.zenax.armorsets.flow.nodes;

import com.zenax.armorsets.flow.FlowContext;
import com.zenax.armorsets.flow.FlowNode;
import com.zenax.armorsets.flow.NodeType;

import java.util.Collections;
import java.util.List;

/**
 * End node - terminates a branch of the flow.
 * Has no output connections.
 */
public class EndNode extends FlowNode {

    public EndNode(String id) {
        super(id);
        setDisplayName("End");
    }

    @Override
    public NodeType getType() {
        return NodeType.END;
    }

    @Override
    public List<String> getOutputPorts() {
        // End node has no outputs
        return Collections.emptyList();
    }

    @Override
    public String execute(FlowContext context) {
        // End node terminates the branch
        return null;
    }

    @Override
    public List<String> validate() {
        // End node is always valid
        return Collections.emptyList();
    }

    @Override
    public FlowNode deepCopy() {
        EndNode copy = new EndNode(getId());
        copyBaseTo(copy);
        return copy;
    }
}
