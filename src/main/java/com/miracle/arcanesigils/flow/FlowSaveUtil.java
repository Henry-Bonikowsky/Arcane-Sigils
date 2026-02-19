package com.miracle.arcanesigils.flow;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.flow.nodes.StartNode;
import com.miracle.arcanesigils.utils.LogHelper;

/**
 * Shared utility for syncing StartNode params to FlowConfig and saving flows.
 * Eliminates duplication between FlowBuilderHandler.autoSave, autoSaveStatic, and NodeConfigHandler.saveFlow.
 */
public final class FlowSaveUtil {

    private FlowSaveUtil() {}

    /**
     * Sync StartNode params (cooldown, chance, priority, signal_type) to FlowConfig.
     * Handles tier placeholder sentinels ({cooldown}, {chance}).
     */
    public static void syncStartNodeToConfig(FlowGraph graph, FlowConfig flowConfig) {
        if (graph == null || flowConfig == null) return;

        FlowNode startNodeRaw = graph.getStartNode();
        if (!(startNodeRaw instanceof StartNode startNode)) return;

        // Handle cooldown — check for tier placeholder first
        Object cooldownVal = startNode.getParam("cooldown");
        if (cooldownVal != null && cooldownVal.toString().contains("{")) {
            flowConfig.setCooldown(-1.0); // Sentinel: tier-scaled
        } else {
            flowConfig.setCooldown(startNode.getDoubleParam("cooldown", 0.0));
        }

        // Handle chance — check for tier placeholder first
        Object chanceVal = startNode.getParam("chance");
        if (chanceVal != null && chanceVal.toString().contains("{")) {
            flowConfig.setChance(-1.0); // Sentinel: tier-scaled
        } else {
            flowConfig.setChance(startNode.getDoubleParam("chance", 100.0));
        }

        flowConfig.setPriority(startNode.getIntParam("priority", 1));

        // Sync signal_type to trigger if changed in StartNode
        String signalType = startNode.getStringParam("signal_type", null);
        if (signalType != null && flowConfig.getType() == FlowType.SIGNAL) {
            flowConfig.setTrigger(signalType);
        }
    }

    /**
     * Get or create a FlowConfig for a sigil + signal key, resolving from session or sigil.
     */
    public static FlowConfig resolveFlowConfig(FlowConfig existing, Sigil sigil, String signalKey) {
        if (existing != null) return existing;

        // Try to find existing config on the sigil
        FlowConfig fromSigil = sigil.getFlowForTrigger(signalKey);
        if (fromSigil != null) return fromSigil;

        // Create new
        FlowConfig config = new FlowConfig();
        if (sigil.isExclusive() && "ABILITY".equals(signalKey)) {
            config.setType(FlowType.ABILITY);
        } else {
            config.setType(FlowType.SIGNAL);
            config.setTrigger(signalKey != null ? signalKey : "ATTACK");
        }
        return config;
    }

    /**
     * Save a flow to a sigil: remove old flow, add updated one, persist to YAML.
     *
     * @return true if saved successfully
     */
    public static boolean saveFlowToSigil(Sigil sigil, FlowConfig flowConfig) {
        if (sigil == null || flowConfig == null) return false;

        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();

        if (flowConfig.isAbility()) {
            FlowConfig existingAbility = sigil.getAbilityFlow();
            if (existingAbility != null) {
                sigil.removeFlow(existingAbility);
            }
        } else {
            sigil.removeFlowByTrigger(flowConfig.getTrigger());
        }

        boolean added = sigil.addFlow(flowConfig);
        if (!added) {
            LogHelper.warning("[FlowSaveUtil] Failed to add flow to sigil '%s'", sigil.getId());
            return false;
        }

        plugin.getSigilManager().saveSigil(sigil);
        return true;
    }
}
