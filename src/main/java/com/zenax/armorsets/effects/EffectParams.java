package com.zenax.armorsets.effects;

import java.util.HashMap;
import java.util.Map;

/**
 * Container for effect parameters parsed from configuration strings.
 */
public class EffectParams {

    private final String effectType;
    private final Map<String, Object> params;
    private String target;
    private double value;
    private int duration;
    private int amplifier;

    public EffectParams(String effectType) {
        this.effectType = effectType;
        this.params = new HashMap<>();
        this.target = "@Self";
        this.value = 0;
        this.duration = 0;
        this.amplifier = 0;
    }

    /**
     * Create EffectParams directly from a Map, bypassing string serialization.
     * This preserves type information (e.g., Integer 2 vs String "2.0").
     *
     * @param effectType The effect type ID
     * @param params Map of parameter names to values
     * @return A new EffectParams with all values set
     */
    public static EffectParams fromMap(String effectType, Map<String, Object> params) {
        EffectParams result = new EffectParams(effectType);

        if (params == null) return result;

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) continue;

            // Handle special fields - map common param names to core fields
            // NOTE: Some effects read via getValue(), others via getDouble("name")
            // We store numeric params in BOTH places to support both access patterns
            switch (key) {
                case "target" -> result.setTarget(value.toString());
                // Map common numeric param names to BOTH the value field AND params map
                // This way effects using getValue() OR getDouble("damage") both work
                case "distance", "force", "amount", "damage", "heal",
                     "chance", "radius", "range", "power", "strength", "height" -> {
                    result.setValue(toDouble(value));
                    result.set(key, value); // Also store in params map for named access
                }
                // "value" is special - it can be numeric OR a string (e.g., material name for SPAWN_DISPLAY)
                // Store it both ways so both getValue() and getString("value") work
                case "value" -> {
                    result.setValue(toDouble(value));
                    result.set(key, value); // Also store in params map for string access
                }
                // Material/content names for SPAWN_DISPLAY - store as strings
                case "material", "block", "item", "text", "display_type", "type" -> result.set(key, value);
                case "duration" -> result.setDuration(toInt(value));
                case "amplifier" -> result.setAmplifier(toInt(value));
                // All other params stored directly - effects read by name
                // e.g., count, hp, speed, entity_type, etc.
                default -> result.set(key, value); // Preserve original type
            }
        }

        return result;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number num) return num.doubleValue();
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static int toInt(Object value) {
        if (value instanceof Number num) return num.intValue();
        if (value instanceof String str) {
            try {
                // Handle "2.0" style doubles from YAML
                return (int) Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    public String getEffectType() {
        return effectType;
    }

    public String getTarget() {
        return target;
    }

    public EffectParams setTarget(String target) {
        this.target = target;
        return this;
    }

    public double getValue() {
        return value;
    }

    public EffectParams setValue(double value) {
        this.value = value;
        return this;
    }

    public int getDuration() {
        return duration;
    }

    public EffectParams setDuration(int duration) {
        this.duration = duration;
        return this;
    }

    public int getAmplifier() {
        return amplifier;
    }

    public EffectParams setAmplifier(int amplifier) {
        this.amplifier = amplifier;
        return this;
    }

    public EffectParams set(String key, Object value) {
        params.put(key, value);
        return this;
    }

    public Object get(String key) {
        return params.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        Object value = params.get(key);
        return value != null ? (T) value : defaultValue;
    }

    public String getString(String key, String defaultValue) {
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public double getDouble(String key, double defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public float getFloat(String key, float defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number num) {
            return num.floatValue();
        }
        if (value instanceof String str) {
            try {
                return Float.parseFloat(str);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return defaultValue;
    }

    public boolean has(String key) {
        return params.containsKey(key);
    }

    @Override
    public String toString() {
        return "EffectParams{" +
                "effectType='" + effectType + '\'' +
                ", target='" + target + '\'' +
                ", value=" + value +
                ", duration=" + duration +
                ", amplifier=" + amplifier +
                ", params=" + params +
                '}';
    }
}
