package com.zenax.dungeons.stats;

/**
 * Enum representing the different stats available in the Dungeons system.
 * Each stat has a display name, color code, description, and default value.
 */
public enum Stat {
    STRENGTH(
        "Strength",
        "&c",
        "Increases melee damage output",
        10.0
    ),
    DEFENSE(
        "Defense",
        "&9",
        "Reduces incoming damage",
        10.0
    ),
    VITALITY(
        "Vitality",
        "&a",
        "Increases maximum health",
        10.0
    ),
    SPEED(
        "Speed",
        "&b",
        "Increases movement speed",
        10.0
    ),
    LUCK(
        "Luck",
        "&e",
        "Increases critical hit chance and loot quality",
        10.0
    ),
    INTELLIGENCE(
        "Intelligence",
        "&d",
        "Increases ability damage and reduces cooldowns",
        10.0
    );

    private final String displayName;
    private final String colorCode;
    private final String description;
    private final double defaultValue;

    /**
     * Constructor for the Stat enum.
     *
     * @param displayName The human-readable name of the stat
     * @param colorCode The Minecraft color code for the stat
     * @param description A description of what the stat does
     * @param defaultValue The default value for this stat
     */
    Stat(String displayName, String colorCode, String description, double defaultValue) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    /**
     * Gets the display name of the stat.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the color code for the stat.
     *
     * @return The Minecraft color code
     */
    public String getColorCode() {
        return colorCode;
    }

    /**
     * Gets the description of the stat.
     *
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the default value for this stat.
     *
     * @return The default value
     */
    public double getDefaultValue() {
        return defaultValue;
    }

    /**
     * Gets the colored display name of the stat.
     *
     * @return The colored display name
     */
    public String getColoredName() {
        return colorCode + displayName;
    }
}
