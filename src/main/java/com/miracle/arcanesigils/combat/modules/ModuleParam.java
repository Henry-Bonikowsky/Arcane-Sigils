package com.miracle.arcanesigils.combat.modules;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Describes a configurable parameter for a combat module.
 * Used to build dynamic GUIs for runtime configuration.
 */
public class ModuleParam {

    private final String key;
    private final String displayName;
    private final String description;
    private final ParamType type;
    private final Supplier<Object> getter;
    private final Consumer<Object> setter;
    private final double min;
    private final double max;
    private final double step;

    private ModuleParam(Builder builder) {
        this.key = builder.key;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.type = builder.type;
        this.getter = builder.getter;
        this.setter = builder.setter;
        this.min = builder.min;
        this.max = builder.max;
        this.step = builder.step;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public ParamType getType() { return type; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }

    public Object getValue() {
        return getter.get();
    }

    @SuppressWarnings("unchecked")
    public void setValue(Object value) {
        setter.accept(value);
    }

    public double getDoubleValue() {
        Object val = getValue();
        if (val instanceof Number n) return n.doubleValue();
        return 0;
    }

    public int getIntValue() {
        Object val = getValue();
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    public boolean getBoolValue() {
        Object val = getValue();
        if (val instanceof Boolean b) return b;
        return false;
    }

    /**
     * Increment the value by step amount.
     */
    public void increment() {
        if (type == ParamType.BOOLEAN) {
            setValue(!getBoolValue());
        } else if (type == ParamType.INTEGER) {
            int newVal = Math.min((int) max, getIntValue() + (int) step);
            setValue(newVal);
        } else {
            double newVal = Math.min(max, getDoubleValue() + step);
            setValue(newVal);
        }
    }

    /**
     * Decrement the value by step amount.
     */
    public void decrement() {
        if (type == ParamType.BOOLEAN) {
            setValue(!getBoolValue());
        } else if (type == ParamType.INTEGER) {
            int newVal = Math.max((int) min, getIntValue() - (int) step);
            setValue(newVal);
        } else {
            double newVal = Math.max(min, getDoubleValue() - step);
            setValue(newVal);
        }
    }

    /**
     * Get formatted display value.
     */
    public String getFormattedValue() {
        Object val = getValue();
        if (type == ParamType.BOOLEAN) {
            return ((Boolean) val) ? "Yes" : "No";
        } else if (type == ParamType.INTEGER) {
            return String.valueOf(((Number) val).intValue());
        } else if (type == ParamType.PERCENTAGE) {
            return String.format("%.0f%%", ((Number) val).doubleValue());
        } else if (type == ParamType.MILLISECONDS) {
            return ((Number) val).intValue() + "ms";
        } else if (type == ParamType.TICKS) {
            return ((Number) val).intValue() + " ticks";
        } else if (type == ParamType.SECONDS) {
            return String.format("%.1fs", ((Number) val).doubleValue());
        } else {
            // DOUBLE - format nicely
            double d = ((Number) val).doubleValue();
            if (d == (int) d) {
                return String.valueOf((int) d);
            }
            return String.format("%.2f", d);
        }
    }

    public enum ParamType {
        BOOLEAN,
        INTEGER,
        DOUBLE,
        PERCENTAGE,    // 0-100, displayed with %
        MILLISECONDS,  // displayed with "ms"
        TICKS,         // displayed with "ticks"
        SECONDS        // displayed with "s"
    }

    // Builder pattern for clean construction
    public static Builder builder(String key) {
        return new Builder(key);
    }

    public static class Builder {
        private final String key;
        private String displayName;
        private String description = "";
        private ParamType type = ParamType.DOUBLE;
        private Supplier<Object> getter;
        private Consumer<Object> setter;
        private double min = 0;
        private double max = 100;
        private double step = 1;

        public Builder(String key) {
            this.key = key;
            this.displayName = key;
        }

        public Builder displayName(String name) {
            this.displayName = name;
            return this;
        }

        public Builder description(String desc) {
            this.description = desc;
            return this;
        }

        public Builder type(ParamType type) {
            this.type = type;
            return this;
        }

        public Builder range(double min, double max) {
            this.min = min;
            this.max = max;
            return this;
        }

        public Builder step(double step) {
            this.step = step;
            return this;
        }

        public Builder boolValue(Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this.type = ParamType.BOOLEAN;
            this.getter = () -> getter.get();
            this.setter = v -> setter.accept((Boolean) v);
            return this;
        }

        public Builder intValue(Supplier<Integer> getter, Consumer<Integer> setter) {
            this.type = ParamType.INTEGER;
            this.getter = () -> getter.get();
            this.setter = v -> setter.accept(((Number) v).intValue());
            return this;
        }

        public Builder doubleValue(Supplier<Double> getter, Consumer<Double> setter) {
            this.type = ParamType.DOUBLE;
            this.getter = () -> getter.get();
            this.setter = v -> setter.accept(((Number) v).doubleValue());
            return this;
        }

        public Builder percentValue(Supplier<Double> getter, Consumer<Double> setter) {
            this.type = ParamType.PERCENTAGE;
            this.getter = () -> getter.get();
            this.setter = v -> setter.accept(((Number) v).doubleValue());
            return this;
        }

        public Builder msValue(Supplier<Integer> getter, Consumer<Integer> setter) {
            this.type = ParamType.MILLISECONDS;
            this.getter = () -> getter.get();
            this.setter = v -> setter.accept(((Number) v).intValue());
            return this;
        }

        public Builder tickValue(Supplier<Integer> getter, Consumer<Integer> setter) {
            this.type = ParamType.TICKS;
            this.getter = () -> getter.get();
            this.setter = v -> setter.accept(((Number) v).intValue());
            return this;
        }

        public Builder secondValue(Supplier<Double> getter, Consumer<Double> setter) {
            this.type = ParamType.SECONDS;
            this.getter = () -> getter.get();
            this.setter = v -> setter.accept(((Number) v).doubleValue());
            return this;
        }

        public ModuleParam build() {
            if (getter == null || setter == null) {
                throw new IllegalStateException("Getter and setter must be set");
            }
            return new ModuleParam(this);
        }
    }
}
