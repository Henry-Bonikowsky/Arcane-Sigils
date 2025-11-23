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
