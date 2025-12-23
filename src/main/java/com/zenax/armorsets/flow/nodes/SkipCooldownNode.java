package com.zenax.armorsets.flow.nodes;

import com.zenax.armorsets.flow.FlowContext;
import com.zenax.armorsets.flow.FlowNode;
import com.zenax.armorsets.flow.NodeType;

import java.util.Collections;
import java.util.List;

/**
 * Skip Cooldown node - prevents cooldown from triggering when reached.
 * Place this on a condition's "no" branch to skip cooldown when the condition fails.
 * Has no output connections - terminates the branch.
 */
public class SkipCooldownNode extends FlowNode {

    public SkipCooldownNode(String id) {
        super(id);
        setDisplayName("Skip Cooldown");
    }

    @Override
    public NodeType getType() {
        return NodeType.SKIP_COOLDOWN;
    }

    @Override
    public List<String> getOutputPorts() {
        // Skip cooldown node has no outputs - it ends the branch
        return Collections.emptyList();
    }

    @Override
    public String execute(FlowContext context) {
        // Mark that cooldown should be skipped
        context.setSkipCooldown(true);

        // End this branch
        return null;
    }

    @Override
    public List<String> validate() {
        // Skip cooldown node is always valid - no connections required
        return Collections.emptyList();
    }

    @Override
    public FlowNode deepCopy() {
        SkipCooldownNode copy = new SkipCooldownNode(getId());
        copyBaseTo(copy);
        return copy;
    }
}
