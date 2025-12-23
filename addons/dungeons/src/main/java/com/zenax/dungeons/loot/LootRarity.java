package com.zenax.dungeons.loot;

import org.bukkit.ChatColor;

/**
 * Represents the rarity levels for loot items.
 * Each rarity has a different color and value multiplier.
 */
public enum LootRarity {
    COMMON("Common", ChatColor.WHITE, 1.0),
    UNCOMMON("Uncommon", ChatColor.GREEN, 1.5),
    RARE("Rare", ChatColor.BLUE, 2.0),
    EPIC("Epic", ChatColor.DARK_PURPLE, 3.0),
    LEGENDARY("Legendary", ChatColor.GOLD, 5.0);

    private final String displayName;
    private final ChatColor color;
    private final double valueMultiplier;

    /**
     * Creates a new loot rarity.
     *
     * @param displayName The display name for the rarity
     * @param color The color code for displaying the rarity
     * @param valueMultiplier Multiplier for item value
     */
    LootRarity(String displayName, ChatColor color, double valueMultiplier) {
        this.displayName = displayName;
        this.color = color;
        this.valueMultiplier = valueMultiplier;
    }

    /**
     * Gets the display name of this rarity.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the color code for this rarity.
     *
     * @return The ChatColor
     */
    public ChatColor getColor() {
        return color;
    }

    /**
     * Gets the value multiplier for this rarity.
     *
     * @return The value multiplier
     */
    public double getValueMultiplier() {
        return valueMultiplier;
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
     * Gets the color code string (for use in item names).
     *
     * @return The color code string
     */
    public String getColorCode() {
        return color.toString();
    }

    /**
     * Gets a rarity by its name (case-insensitive).
     *
     * @param name The rarity name
     * @return The LootRarity, or COMMON if not found
     */
    public static LootRarity fromString(String name) {
        if (name == null) {
            return COMMON;
        }
        for (LootRarity rarity : values()) {
            if (rarity.name().equalsIgnoreCase(name) || rarity.displayName.equalsIgnoreCase(name)) {
                return rarity;
            }
        }
        return COMMON;
    }

    /**
     * Gets the next higher rarity level.
     *
     * @return The next rarity, or this rarity if already at maximum
     */
    public LootRarity getNext() {
        int ordinal = ordinal();
        if (ordinal < values().length - 1) {
            return values()[ordinal + 1];
        }
        return this;
    }

    /**
     * Gets the previous lower rarity level.
     *
     * @return The previous rarity, or this rarity if already at minimum
     */
    public LootRarity getPrevious() {
        int ordinal = ordinal();
        if (ordinal > 0) {
            return values()[ordinal - 1];
        }
        return this;
    }
}
