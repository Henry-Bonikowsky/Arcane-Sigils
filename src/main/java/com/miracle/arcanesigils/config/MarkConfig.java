package com.miracle.arcanesigils.config;

/**
 * Configuration data for a mark type.
 * Stores metadata like display name, description, and per-mark stacking behavior.
 */
public class MarkConfig {
    private final String id;
    private final String name;
    private final String description;
    private final double maxDuration;
    private final double stackIncrement;
    private final boolean stackingEnabled;

    public MarkConfig(String id, String name, String description,
                      double maxDuration, double stackIncrement, boolean stackingEnabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.maxDuration = maxDuration;
        this.stackIncrement = stackIncrement;
        this.stackingEnabled = stackingEnabled;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getMaxDuration() {
        return maxDuration;
    }

    public double getStackIncrement() {
        return stackIncrement;
    }

    public boolean isStackingEnabled() {
        return stackingEnabled;
    }
}
