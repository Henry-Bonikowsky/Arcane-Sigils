package com.zenax.dungeons.dungeon;

/**
 * Represents the different objective modes a dungeon can have.
 */
public enum ObjectiveMode {
    /**
     * Complete the dungeon by defeating the boss.
     */
    BOSS_KILL("Defeat the Boss", "Defeat the dungeon boss to complete"),

    /**
     * Complete the dungeon by finishing all objectives.
     */
    OBJECTIVES("Complete All Objectives", "Complete all objectives to finish the dungeon");

    private final String displayName;
    private final String description;

    /**
     * Creates a new objective mode.
     *
     * @param displayName The display name for this mode
     * @param description A description of what this mode requires
     */
    ObjectiveMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Gets the display name of this objective mode.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description of this objective mode.
     *
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets an objective mode by its name (case-insensitive).
     *
     * @param name The objective mode name
     * @return The ObjectiveMode, or null if not found
     */
    public static ObjectiveMode fromString(String name) {
        for (ObjectiveMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name) || mode.displayName.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * Checks if this mode requires a boss kill.
     *
     * @return true if this mode requires killing a boss
     */
    public boolean requiresBoss() {
        return this == BOSS_KILL;
    }

    /**
     * Checks if this mode requires completing objectives.
     *
     * @return true if this mode requires completing objectives
     */
    public boolean requiresObjectives() {
        return this == OBJECTIVES;
    }
}
