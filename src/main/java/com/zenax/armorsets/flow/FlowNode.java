package com.zenax.armorsets.flow;

import java.util.*;

/**
 * Base class for all flow nodes in the visual scripting system.
 * Each node represents an action, condition, or control flow element.
 */
public abstract class FlowNode {

    /**
     * Unique identifier for this node within a flow.
     */
    private String id;

    /**
     * Human-readable display name for the node.
     */
    private String displayName;

    /**
     * Position on the canvas (grid coordinates).
     */
    private int gridX;
    private int gridY;

    /**
     * Parameters configured for this node.
     * Keys are parameter names, values can be literals or expressions like {damage}.
     */
    private final Map<String, Object> params = new LinkedHashMap<>();

    /**
     * Outgoing connections from this node.
     * Key is the output port name (e.g., "next", "yes", "no").
     * Value is the target node ID.
     */
    private final Map<String, String> connections = new LinkedHashMap<>();

    /**
     * Tier scaling configuration for numeric parameters.
     * Key is param name, value is list of values per tier.
     *
     * @deprecated Use sigil-level TierScalingConfig instead. Node-level tierValues
     *             will be auto-migrated to sigil TierScalingConfig on load.
     */
    @Deprecated
    private final Map<String, List<Double>> tierValues = new LinkedHashMap<>();

    protected FlowNode(String id) {
        this.id = id;
        this.displayName = getType().getDisplayName();
    }

    /**
     * Get the type of this node.
     */
    public abstract NodeType getType();

    /**
     * Get available output ports for this node type.
     * Default is single "next" port.
     */
    public List<String> getOutputPorts() {
        return Collections.singletonList("next");
    }

    /**
     * Execute this node within the given context.
     *
     * @param context The flow execution context
     * @return The output port to follow, or null to end this branch
     */
    public abstract String execute(FlowContext context);

    /**
     * Validate this node's configuration.
     *
     * @return List of validation errors (empty if valid)
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        // Check required connections
        for (String port : getOutputPorts()) {
            if (!connections.containsKey(port) || connections.get(port) == null) {
                errors.add("Output '" + port + "' is not connected");
            }
        }

        return errors;
    }

    // ============ Getters/Setters ============

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getGridX() {
        return gridX;
    }

    public void setGridX(int gridX) {
        this.gridX = gridX;
    }

    public int getGridY() {
        return gridY;
    }

    public void setGridY(int gridY) {
        this.gridY = gridY;
    }

    public void setPosition(int x, int y) {
        this.gridX = x;
        this.gridY = y;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParam(String key, Object value) {
        params.put(key, value);
    }

    public Object getParam(String key) {
        return params.get(key);
    }

    public Object getParam(String key, Object defaultValue) {
        return params.getOrDefault(key, defaultValue);
    }

    public String getStringParam(String key, String defaultValue) {
        Object val = params.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    public double getDoubleParam(String key, double defaultValue) {
        Object val = params.get(key);
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        if (val instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public int getIntParam(String key, int defaultValue) {
        return (int) getDoubleParam(key, defaultValue);
    }

    public boolean getBooleanParam(String key, boolean defaultValue) {
        Object val = params.get(key);
        if (val instanceof Boolean bool) {
            return bool;
        }
        if (val instanceof String str) {
            return "true".equalsIgnoreCase(str);
        }
        return defaultValue;
    }

    public Map<String, String> getConnections() {
        return connections;
    }

    public void setConnection(String port, String targetNodeId) {
        connections.put(port, targetNodeId);
    }

    public String getConnection(String port) {
        return connections.get(port);
    }

    public void removeConnection(String port) {
        connections.remove(port);
    }

    /**
     * @deprecated Use sigil-level TierScalingConfig instead.
     */
    @Deprecated
    public Map<String, List<Double>> getTierValues() {
        return tierValues;
    }

    /**
     * @deprecated Use sigil-level TierScalingConfig instead.
     */
    @Deprecated
    public void setTierValues(String paramName, List<Double> values) {
        tierValues.put(paramName, new ArrayList<>(values));
    }

    /**
     * @deprecated Use sigil-level TierScalingConfig instead.
     */
    @Deprecated
    public boolean hasTierScaling(String paramName) {
        return tierValues.containsKey(paramName) && !tierValues.get(paramName).isEmpty();
    }

    /**
     * Get the tier-scaled value for a parameter.
     *
     * @param paramName The parameter name
     * @param tier The current tier (1-based)
     * @param defaultValue Default if no tier scaling
     * @return The scaled value
     * @deprecated Use sigil-level TierScalingConfig instead.
     */
    @Deprecated
    public double getTierScaledValue(String paramName, int tier, double defaultValue) {
        List<Double> values = tierValues.get(paramName);
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        int index = Math.max(0, Math.min(tier - 1, values.size() - 1));
        return values.get(index);
    }

    // ============ Cloning ============

    /**
     * Create a deep copy of this node.
     * Subclasses must implement this to copy type-specific fields.
     *
     * @return A new FlowNode with copied data
     */
    public abstract FlowNode deepCopy();

    /**
     * Copy common FlowNode fields to a target node.
     * Call this from subclass deepCopy() implementations.
     *
     * @param target The target node to copy to
     */
    protected void copyBaseTo(FlowNode target) {
        target.displayName = this.displayName;
        target.gridX = this.gridX;
        target.gridY = this.gridY;

        // Deep copy params
        for (Map.Entry<String, Object> entry : this.params.entrySet()) {
            target.params.put(entry.getKey(), entry.getValue());
        }

        // Deep copy connections
        target.connections.putAll(this.connections);

        // Deep copy tier values (deprecated but still copy for compatibility)
        for (Map.Entry<String, List<Double>> entry : this.tierValues.entrySet()) {
            target.tierValues.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }
}
