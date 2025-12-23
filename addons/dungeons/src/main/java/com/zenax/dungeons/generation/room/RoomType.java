package com.zenax.dungeons.generation.room;

/**
 * Enum representing different types of rooms in a dungeon.
 * Each room type has different characteristics and purposes.
 */
public enum RoomType {
    /**
     * The starting room where players spawn.
     * Usually safe and contains no hostile mobs.
     */
    SPAWN("Spawn Room", false),

    /**
     * A standard combat room filled with hostile mobs.
     * Players must clear these rooms to progress.
     */
    COMBAT("Combat Room", true),

    /**
     * A treasure room containing valuable loot.
     * May contain mobs guarding the treasure.
     */
    TREASURE("Treasure Room", true),

    /**
     * A puzzle room requiring players to solve challenges.
     * May contain light mob resistance.
     */
    PUZZLE("Puzzle Room", true),

    /**
     * The boss room containing the dungeon's main boss.
     * Usually the final challenge of the dungeon.
     */
    BOSS("Boss Room", true),

    /**
     * A corridor connecting two rooms.
     * Typically narrow and may contain light mob spawns.
     */
    CORRIDOR("Corridor", true),

    /**
     * A natural cave area with organic shapes.
     * Can contain mobs and resources.
     */
    CAVE("Cave", true);

    private final String displayName;
    private final boolean canHaveMobs;

    /**
     * Creates a room type.
     *
     * @param displayName The display name of the room type
     * @param canHaveMobs Whether this room type can spawn mobs
     */
    RoomType(String displayName, boolean canHaveMobs) {
        this.displayName = displayName;
        this.canHaveMobs = canHaveMobs;
    }

    /**
     * Gets the display name of this room type.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this room type can have mobs.
     *
     * @return true if mobs can spawn in this room type
     */
    public boolean canHaveMobs() {
        return canHaveMobs;
    }

    /**
     * Gets a room type from a string.
     *
     * @param str The string to parse
     * @return The matching RoomType, or COMBAT if not found
     */
    public static RoomType fromString(String str) {
        if (str == null) {
            return COMBAT;
        }
        try {
            return RoomType.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMBAT;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
