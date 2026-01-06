package com.miracle.arcanesigils.flow;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified configuration for a sigil's flow.
 * Replaces both SignalConfig (for event-triggered effects) and ActivationConfig (for abilities).
 *
 * Example YAML:
 * <pre>
 * flow:
 *   type: SIGNAL          # SIGNAL (event-triggered) or ABILITY (bind-activated)
 *   trigger: ON_ATTACK    # For SIGNAL: which event triggers this flow
 *   cooldown: 5           # Seconds between activations
 *   chance: 100           # % chance to activate (SIGNAL only)
 *   conditions:           # Optional conditions
 *     logic: AND
 *     list:
 *       - "VICTIM_IS_PLAYER"
 *
 *   # Flow graph definition
 *   id: "attack_flow"
 *   startNodeId: "start"
 *   nodes:
 *     - id: "start"
 *       type: START
 *       next: "deal_damage"
 *     - ...
 * </pre>
 */
public class FlowConfig {

    /**
     * Type of flow - SIGNAL or ABILITY
     */
    private FlowType type = FlowType.SIGNAL;

    /**
     * For SIGNAL type: which event triggers this flow.
     * Examples: ON_ATTACK, ON_DEFEND, ON_KILL, PASSIVE
     * Null for ABILITY type.
     */
    private String trigger;

    /**
     * Cooldown in seconds between activations.
     */
    private double cooldown = 0.0;

    /**
     * Activation chance as percentage (0-100).
     * Only used for SIGNAL type. ABILITY is always 100% when activated.
     */
    private double chance = 100.0;

    /**
     * List of condition strings that must pass for activation.
     */
    private List<String> conditions = new ArrayList<>();

    /**
     * Logic for combining conditions - AND (all must pass) or OR (any can pass).
     */
    private ConditionLogic conditionLogic = ConditionLogic.AND;

    /**
     * Priority for flow execution order when multiple flows share the same trigger.
     * Higher priority flows are checked first. Default is 1.
     * If a higher priority flow's conditions pass, lower priority flows won't execute.
     */
    private int priority = 1;

    /**
     * The visual flow graph containing all nodes.
     */
    private FlowGraph graph;

    public FlowConfig() {
        this.graph = new FlowGraph("default");
    }

    public FlowConfig(FlowType type) {
        this.type = type;
        this.graph = new FlowGraph("default");
    }

    // ============ Getters and Setters ============

    public FlowType getType() {
        return type;
    }

    public void setType(FlowType type) {
        this.type = type;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public double getCooldown() {
        return cooldown;
    }

    public void setCooldown(double cooldown) {
        this.cooldown = cooldown;
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = chance;
    }

    public List<String> getConditions() {
        return conditions;
    }

    public void setConditions(List<String> conditions) {
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }

    public void addCondition(String condition) {
        this.conditions.add(condition);
    }

    public void removeCondition(String condition) {
        this.conditions.remove(condition);
    }

    public ConditionLogic getConditionLogic() {
        return conditionLogic;
    }

    public void setConditionLogic(ConditionLogic conditionLogic) {
        this.conditionLogic = conditionLogic != null ? conditionLogic : ConditionLogic.AND;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public FlowGraph getGraph() {
        return graph;
    }

    public void setGraph(FlowGraph graph) {
        this.graph = graph;
    }

    // ============ Convenience Methods ============

    /**
     * Check if this is a signal-triggered flow.
     */
    public boolean isSignal() {
        return type == FlowType.SIGNAL;
    }

    /**
     * Check if this is a bind-activated ability.
     */
    public boolean isAbility() {
        return type == FlowType.ABILITY;
    }

    /**
     * Check if the flow has any nodes.
     */
    public boolean hasNodes() {
        return graph != null && graph.getNodeCount() > 0;
    }

    /**
     * Check if the flow is valid and ready to execute.
     */
    public boolean isValid() {
        return graph != null && graph.isValid();
    }

    /**
     * Get validation errors for the flow.
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (type == FlowType.SIGNAL && (trigger == null || trigger.isEmpty())) {
            errors.add("Signal flow must have a trigger");
        }

        if (graph == null) {
            errors.add("Flow has no graph");
        } else {
            errors.addAll(graph.validate());
        }

        return errors;
    }

    /**
     * Logic for combining multiple conditions.
     */
    public enum ConditionLogic {
        AND,  // All conditions must pass (default)
        OR    // Any condition can pass
    }

    // ============ Cloning ============

    /**
     * Create a deep copy of this flow config.
     *
     * @return A new FlowConfig with copied data
     */
    public FlowConfig deepCopy() {
        FlowConfig copy = new FlowConfig(this.type);
        copy.trigger = this.trigger;
        copy.cooldown = this.cooldown;
        copy.chance = this.chance;
        copy.conditions = new ArrayList<>(this.conditions);
        copy.conditionLogic = this.conditionLogic;
        copy.graph = this.graph != null ? this.graph.deepCopy() : null;
        return copy;
    }
}
