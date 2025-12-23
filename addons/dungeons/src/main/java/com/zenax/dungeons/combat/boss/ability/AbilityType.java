package com.zenax.dungeons.combat.boss.ability;

/**
 * Represents the type of boss ability.
 * Each type determines how the ability executes and affects targets.
 */
public enum AbilityType {
    /**
     * Fires a projectile at the target.
     */
    PROJECTILE,

    /**
     * Area of effect damage or effect around a location.
     */
    AOE,

    /**
     * Summons additional mobs to aid the boss.
     */
    SUMMON,

    /**
     * Applies a positive effect to the boss.
     */
    BUFF,

    /**
     * Applies a negative effect to the target.
     */
    DEBUFF,

    /**
     * Teleports the boss to a new location.
     */
    TELEPORT,

    /**
     * Grabs or pulls the target towards the boss.
     */
    GRAB;

    /**
     * Gets an AbilityType by name (case-insensitive).
     *
     * @param name The ability type name
     * @return The AbilityType, or null if not found
     */
    public static AbilityType fromString(String name) {
        if (name == null) {
            return null;
        }
        for (AbilityType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
