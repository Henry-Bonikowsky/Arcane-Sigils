package com.miracle.arcanesigils.tier;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for sigil tier scaling using explicit parameter arrays.
 *
 * Example YAML:
 * tier:
 *   mode: PARAMETER
 *   params:
 *     damage: [5, 7, 10, 15, 20]
 *     chance: [1, 1.5, 2, 2.5, 3]
 *     duration: [2, 3, 4, 5, 6]
 *
 * Effect strings can then use {param} placeholders:
 *   - "DEAL_DAMAGE:{damage} @Victim"
 *   - "POTION:SPEED:{duration}:1"
 */
public class TierScalingConfig {

    /**
     * Scaling mode determines what type of scaling is used.
     */
    public enum ScalingMode {
        /** Effect parameter values scale with tier using explicit arrays */
        PARAMETER,
        /** Only activation chance/cooldown scales */
        ACTIVATION_ONLY,
        /** Both effect parameters AND activation chance/cooldown scale with tier */
        BOTH
    }

    private ScalingMode mode = ScalingMode.PARAMETER;
    private TierParameterConfig params = new TierParameterConfig();

    public TierScalingConfig() {
    }

    // ===== Getters and Setters =====

    public ScalingMode getMode() {
        return mode;
    }

    public void setMode(ScalingMode mode) {
        this.mode = mode;
    }

    public TierParameterConfig getParams() {
        return params;
    }

    public void setParams(TierParameterConfig params) {
        this.params = params != null ? params : new TierParameterConfig();
    }

    /**
     * Get the value for a parameter at a specific tier.
     * Convenience method that delegates to params.
     *
     * @param paramName Parameter name (e.g., "damage", "chance")
     * @param tier Current tier (1-based)
     * @return The value for this tier
     */
    public double getParamValue(String paramName, int tier) {
        return params.getValue(paramName, tier);
    }

    /**
     * Get the value as a formatted string (removes trailing zeros).
     */
    public String getParamValueAsString(String paramName, int tier) {
        return params.getValueAsString(paramName, tier);
    }

    /**
     * Check if a parameter exists.
     */
    public boolean hasParam(String paramName) {
        return params.hasParameter(paramName);
    }

    /**
     * Add a tier-scaled parameter with automatic linear scaling.
     * Used when Alex marks a param as tier-scalable in the Flow Editor.
     *
     * @param paramName Parameter name (e.g., "damage")
     * @param baseValue The base value at tier 1
     * @param maxTier Maximum tier level
     */
    public void addScaledParam(String paramName, double baseValue, int maxTier) {
        params.addParameterWithScaling(paramName, maxTier, baseValue);
    }

    /**
     * Set explicit tier values for a parameter.
     * Used when enabling tier scaling from the Flow Editor.
     *
     * @param paramName Parameter name (e.g., "chance", "cooldown")
     * @param values List of values for each tier (index 0 = tier 1)
     */
    public void setParamValues(String paramName, java.util.List<Double> values) {
        params.setValues(paramName, values);
    }

    /**
     * Check if this config has any parameters defined.
     */
    public boolean hasParams() {
        return !params.isEmpty();
    }

    /**
     * Check if activation (chance/cooldown) should scale.
     */
    public boolean shouldActivationScale() {
        return mode == ScalingMode.ACTIVATION_ONLY || mode == ScalingMode.BOTH;
    }

    /**
     * Check if parameters should scale.
     */
    public boolean shouldParamsScale() {
        return mode == ScalingMode.PARAMETER || mode == ScalingMode.BOTH;
    }

    /**
     * Load from YAML configuration section.
     *
     * @param section The 'tier' section from sigil config
     * @return Parsed config, or null if section is null
     */
    public static TierScalingConfig fromConfig(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        TierScalingConfig config = new TierScalingConfig();

        // Parse scaling mode
        String modeStr = section.getString("mode", "PARAMETER");
        try {
            config.mode = ScalingMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            config.mode = ScalingMode.PARAMETER;
        }

        // Parse parameter arrays
        ConfigurationSection paramsSection = section.getConfigurationSection("params");
        if (paramsSection != null) {
            config.params = TierParameterConfig.fromConfig(paramsSection);
        }

        return config;
    }

    /**
     * Save to YAML format map.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("mode", mode.name());

        if (!params.isEmpty()) {
            map.put("params", params.toMap());
        }

        return map;
    }

    /**
     * Create a deep copy of this config.
     */
    public TierScalingConfig copy() {
        TierScalingConfig copy = new TierScalingConfig();
        copy.mode = this.mode;
        copy.params = this.params.copy();
        return copy;
    }
}
