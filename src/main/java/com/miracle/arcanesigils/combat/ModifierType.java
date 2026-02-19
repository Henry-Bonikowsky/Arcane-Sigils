package com.miracle.arcanesigils.combat;

/**
 * Types of damage modifiers tracked by ModifierRegistry.
 * Each type defines how multiple sources are aggregated.
 */
public enum ModifierType {

    /**
     * Damage amplification - target takes MORE damage.
     * Additive: sources sum their percentages, then convert to multiplier.
     * Example: two sources of 10% each = 1.0 + 0.20 = 1.20 multiplier.
     */
    DAMAGE_AMPLIFICATION(AggregationMode.ADDITIVE),

    /**
     * Damage reduction - target takes LESS damage.
     * Additive: sources sum their percentages, then convert to multiplier.
     * Example: two sources of 10% each = 1.0 - 0.20 = 0.80 multiplier.
     */
    DAMAGE_REDUCTION(AggregationMode.ADDITIVE),

    /**
     * Charge-based damage reduction (King's Brace).
     * Additive stacking.
     */
    CHARGE_DR(AggregationMode.ADDITIVE);

    private final AggregationMode aggregationMode;

    ModifierType(AggregationMode aggregationMode) {
        this.aggregationMode = aggregationMode;
    }

    public AggregationMode getAggregationMode() {
        return aggregationMode;
    }

    public enum AggregationMode {
        /** Sum percentages: multiplier = 1.0 + sum(values) for amp, 1.0 - sum(values) for reduction */
        ADDITIVE,
        /** Multiply all values together */
        MULTIPLICATIVE
    }
}
