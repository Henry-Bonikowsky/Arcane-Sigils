package com.miracle.arcanesigils.tier;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for explicit tier parameter arrays.
 * Stores named parameters with values for each tier level.
 *
 * Example YAML:
 * tier:
 *   mode: PARAMETER
 *   params:
 *     damage: [5, 7, 10, 15, 20]
 *     chance: [1, 1.5, 2, 2.5, 3]
 *     duration: [2, 3, 4, 5, 6]
 */
public class TierParameterConfig {

    /**
     * Map of parameter name to list of values per tier.
     * Index 0 = Tier 1 value, Index 1 = Tier 2 value, etc.
     */
    private final Map<String, List<Double>> params = new HashMap<>();

    public TierParameterConfig() {
    }

    /**
     * Get all parameter names.
     */
    public java.util.Set<String> getParameterNames() {
        return params.keySet();
    }

    /**
     * Check if a parameter exists.
     */
    public boolean hasParameter(String name) {
        return params.containsKey(name.toLowerCase());
    }

    /**
     * Get the value for a parameter at a specific tier.
     *
     * @param name Parameter name
     * @param tier Current tier (1-based)
     * @return The value for this tier, or the last available value if tier exceeds array length
     */
    public double getValue(String name, int tier) {
        List<Double> values = params.get(name.toLowerCase());
        if (values == null || values.isEmpty()) {
            return 0.0;
        }

        // Tier is 1-based, array is 0-based
        int index = Math.max(0, tier - 1);

        // If tier exceeds array length, use last value
        if (index >= values.size()) {
            index = values.size() - 1;
        }

        return values.get(index);
    }

    /**
     * Get the value as a formatted string (removes trailing zeros).
     */
    public String getValueAsString(String name, int tier) {
        double value = getValue(name, tier);
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /**
     * Get all values for a parameter.
     */
    public List<Double> getValues(String name) {
        return params.getOrDefault(name.toLowerCase(), new ArrayList<>());
    }

    /**
     * Set all values for a parameter.
     */
    public void setValues(String name, List<Double> values) {
        params.put(name.toLowerCase(), new ArrayList<>(values));
    }

    /**
     * Set a single tier value for a parameter.
     */
    public void setValue(String name, int tier, double value) {
        String key = name.toLowerCase();
        List<Double> values = params.computeIfAbsent(key, k -> new ArrayList<>());

        // Expand list if needed
        int index = tier - 1;
        while (values.size() <= index) {
            values.add(0.0);
        }

        values.set(index, value);
    }

    /**
     * Add a new parameter with default values (all same value).
     */
    public void addParameter(String name, int maxTier, double defaultValue) {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < maxTier; i++) {
            values.add(defaultValue);
        }
        params.put(name.toLowerCase(), values);
    }

    /**
     * Add a new parameter with linear scaling from base to base*2.
     * Used when Alex marks a param as tier-scalable in the Flow Editor.
     *
     * @param name Parameter name (e.g., "damage")
     * @param maxTier Maximum tier (e.g., 5)
     * @param baseValue Starting value at tier 1
     */
    public void addParameterWithScaling(String name, int maxTier, double baseValue) {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < maxTier; i++) {
            // Linear progression from baseValue to baseValue * 2
            double progress = maxTier > 1 ? (double) i / (maxTier - 1) : 0;
            double scaledValue = baseValue + (baseValue * progress);
            // Round to 2 decimal places for cleaner values
            values.add(Math.round(scaledValue * 100.0) / 100.0);
        }
        params.put(name.toLowerCase(), values);
    }

    /**
     * Remove a parameter.
     */
    public void removeParameter(String name) {
        params.remove(name.toLowerCase());
    }

    /**
     * Get the number of tiers defined (based on longest array).
     */
    public int getDefinedTiers() {
        int max = 0;
        for (List<Double> values : params.values()) {
            max = Math.max(max, values.size());
        }
        return max;
    }

    /**
     * Check if this config has any parameters defined.
     */
    public boolean isEmpty() {
        return params.isEmpty();
    }

    /**
     * Load from YAML configuration section.
     *
     * @param section The 'params' section containing parameter arrays
     * @return Parsed config
     */
    public static TierParameterConfig fromConfig(ConfigurationSection section) {
        TierParameterConfig config = new TierParameterConfig();

        if (section == null) {
            return config;
        }

        for (String paramName : section.getKeys(false)) {
            List<?> rawValues = section.getList(paramName);
            if (rawValues != null && !rawValues.isEmpty()) {
                List<Double> values = new ArrayList<>();
                for (Object val : rawValues) {
                    if (val instanceof Number) {
                        values.add(((Number) val).doubleValue());
                    } else if (val instanceof String) {
                        try {
                            values.add(Double.parseDouble((String) val));
                        } catch (NumberFormatException e) {
                            values.add(0.0);
                        }
                    }
                }
                config.params.put(paramName.toLowerCase(), values);
            }
        }

        return config;
    }

    /**
     * Convert to YAML-friendly map.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : params.entrySet()) {
            // Convert to appropriate number format (int if whole number)
            List<Object> values = new ArrayList<>();
            for (Double d : entry.getValue()) {
                if (d == d.longValue()) {
                    values.add((long) d.doubleValue());
                } else {
                    values.add(d);
                }
            }
            map.put(entry.getKey(), values);
        }
        return map;
    }

    /**
     * Create a deep copy.
     */
    public TierParameterConfig copy() {
        TierParameterConfig copy = new TierParameterConfig();
        for (Map.Entry<String, List<Double>> entry : params.entrySet()) {
            copy.params.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TierParameterConfig{");
        for (Map.Entry<String, List<Double>> entry : params.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
        }
        sb.append("}");
        return sb.toString();
    }
}
