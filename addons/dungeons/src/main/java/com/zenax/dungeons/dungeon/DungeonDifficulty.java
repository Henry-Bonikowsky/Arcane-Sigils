package com.zenax.dungeons.dungeon;

import org.bukkit.ChatColor;

/**
 * Represents the difficulty levels for dungeons.
 * Each difficulty affects mob statistics and loot quality.
 */
public enum DungeonDifficulty {
    EASY(0.75, 0.5, "Easy", ChatColor.GREEN),
    NORMAL(1.0, 1.0, "Normal", ChatColor.YELLOW),
    HARD(1.5, 1.5, "Hard", ChatColor.RED),
    NIGHTMARE(2.0, 2.0, "Nightmare", ChatColor.DARK_PURPLE);

    private final double mobMultiplier;
    private final double lootMultiplier;
    private final String displayName;
    private final ChatColor color;

    /**
     * Creates a new dungeon difficulty.
     *
     * @param mobMultiplier Multiplier for mob statistics (health, damage, etc.)
     * @param lootMultiplier Multiplier for loot quality and quantity
     * @param displayName Display name for the difficulty
     * @param color Color code for displaying the difficulty
     */
    DungeonDifficulty(double mobMultiplier, double lootMultiplier, String displayName, ChatColor color) {
        this.mobMultiplier = mobMultiplier;
        this.lootMultiplier = lootMultiplier;
        this.displayName = displayName;
        this.color = color;
    }

    /**
     * Gets the mob statistics multiplier.
     *
     * @return The multiplier for mob stats
     */
    public double getMobMultiplier() {
        return mobMultiplier;
    }

    /**
     * Gets the loot quality multiplier.
     *
     * @return The multiplier for loot
     */
    public double getLootMultiplier() {
        return lootMultiplier;
    }

    /**
     * Gets the display name of this difficulty.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the color code for this difficulty.
     *
     * @return The ChatColor
     */
    public ChatColor getColor() {
        return color;
    }

    /**
     * Gets the colored display name.
     *
     * @return The display name with color formatting
     */
    public String getColoredDisplayName() {
        return color + displayName;
    }

    /**
     * Checks if this difficulty has unique drops.
     * Currently only NIGHTMARE difficulty has unique drops.
     *
     * @return true if this difficulty has unique drops
     */
    public boolean hasUniqueDrops() {
        return this == NIGHTMARE;
    }

    /**
     * Gets a difficulty by its name (case-insensitive).
     *
     * @param name The difficulty name
     * @return The DungeonDifficulty, or null if not found
     */
    public static DungeonDifficulty fromString(String name) {
        for (DungeonDifficulty difficulty : values()) {
            if (difficulty.name().equalsIgnoreCase(name) || difficulty.displayName.equalsIgnoreCase(name)) {
                return difficulty;
            }
        }
        return null;
    }
}
