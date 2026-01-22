package com.miracle.arcanesigils.flow.nodes;

import com.miracle.arcanesigils.flow.FlowContext;
import com.miracle.arcanesigils.flow.FlowNode;
import com.miracle.arcanesigils.flow.NodeType;

import java.util.Collections;
import java.util.List;

/**
 * End node - marks the termination point of a flow.
 * Every flow should have at least one end node.
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
    public String execute(FlowContext context) {
        // End node terminates the flow
        return null;
    }

    @Override
    public List<String> validate() {
        // End node should have no connections
        return Collections.emptyList();
    }

    @Override
    public FlowNode deepCopy() {
        EndNode copy = new EndNode(getId());
        copyBaseTo(copy);
        return copy;
    }
}
