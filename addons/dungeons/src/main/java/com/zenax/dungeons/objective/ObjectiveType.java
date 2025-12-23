package com.zenax.dungeons.objective;

/**
 * Enum representing different types of objectives in a dungeon.
 * Each type has a display name and description for user-facing displays.
 */
public enum ObjectiveType {
    KILL_BOSS(
        "Kill Boss",
        "Defeat the dungeon's boss monster to complete this objective"
    ),

    COLLECT_ITEMS(
        "Collect Items",
        "Gather a specific number of items scattered throughout the dungeon"
    ),

    ACTIVATE_ALTARS(
        "Activate Altars",
        "Find and activate ancient altars hidden in the dungeon"
    ),

    CLEAR_ROOMS(
        "Clear Rooms",
        "Eliminate all monsters from designated rooms"
    );

    private final String displayName;
    private final String description;

    /**
     * Creates a new objective type.
     *
     * @param displayName The display name for this objective type
     * @param description The description of this objective type
     */
    ObjectiveType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Gets the display name of this objective type.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description of this objective type.
     *
     * @return The description
     */
    public String getDescription() {
        return description;
    }
}
