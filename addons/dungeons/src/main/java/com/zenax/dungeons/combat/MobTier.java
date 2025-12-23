package com.zenax.dungeons.combat;

import org.bukkit.ChatColor;

/**
 * Represents the tier/rank of a dungeon mob.
 * Each tier has different stat multipliers and visual prefixes.
 */
public enum MobTier {
    MINION(1.0, 1.0, ChatColor.WHITE + "[Minion] ", ChatColor.WHITE),
    ELITE(2.0, 1.5, ChatColor.GOLD + "[Elite] ", ChatColor.GOLD),
    BOSS(5.0, 3.0, ChatColor.DARK_RED + "[BOSS] ", ChatColor.DARK_RED);

    private final double healthMultiplier;
    private final double damageMultiplier;
    private final String prefix;
    private final ChatColor color;

    /**
     * Creates a new mob tier.
     *
     * @param healthMultiplier Multiplier for mob health
     * @param damageMultiplier Multiplier for mob damage
     * @param prefix Display prefix for mob names
     * @param color Color code for the tier
     */
    MobTier(double healthMultiplier, double damageMultiplier, String prefix, ChatColor color) {
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.prefix = prefix;
        this.color = color;
    }

    /**
     * Gets the health multiplier for this tier.
     *
     * @return The health multiplier
     */
    public double getHealthMultiplier() {
        return healthMultiplier;
    }

    /**
     * Gets the damage multiplier for this tier.
     *
     * @return The damage multiplier
     */
    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    /**
     * Gets the display prefix for this tier.
     *
     * @return The prefix string with color codes
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Gets the color code for this tier.
     *
     * @return The ChatColor
     */
    public ChatColor getColor() {
        return color;
    }

    /**
     * Formats a mob name with the tier prefix.
     *
     * @param baseName The base mob name
     * @return The formatted name with tier prefix
     */
    public String formatName(String baseName) {
        return prefix + ChatColor.RESET + baseName;
    }

    /**
     * Gets a tier by its name (case-insensitive).
     *
     * @param name The tier name
     * @return The MobTier, or null if not found
     */
    public static MobTier fromString(String name) {
        for (MobTier tier : values()) {
            if (tier.name().equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return null;
    }
}
