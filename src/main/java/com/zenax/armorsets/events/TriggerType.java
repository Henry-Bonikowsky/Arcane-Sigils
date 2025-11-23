package com.zenax.armorsets.events;

/**
 * Enum representing all trigger types for armor set effects.
 */
public enum TriggerType {
    /**
     * Triggered when player attacks an entity.
     */
    ATTACK("ATTACK", "When attacking"),

    /**
     * Triggered when player takes damage.
     */
    DEFENSE("DEFENSE", "When taking damage"),

    /**
     * Triggered when player kills a mob.
     */
    KILL_MOB("KILL_MOB", "When killing a mob"),

    /**
     * Triggered when player kills another player.
     */
    KILL_PLAYER("KILL_PLAYER", "When killing a player"),

    /**
     * Triggered when player sneaks/shifts.
     */
    SHIFT("SHIFT", "When sneaking"),

    /**
     * Triggered when player takes fall damage.
     */
    FALL_DAMAGE("FALL_DAMAGE", "When taking fall damage"),

    /**
     * Passive effects that are always active while equipped.
     */
    EFFECT_STATIC("EFFECT_STATIC", "Passive effect"),

    /**
     * Triggered when player shoots a bow.
     */
    BOW_SHOOT("BOW_SHOOT", "When shooting a bow"),

    /**
     * Triggered when player's arrow hits a target.
     */
    BOW_HIT("BOW_HIT", "When arrow hits target"),

    /**
     * Triggered when player throws a trident.
     */
    TRIDENT_THROW("TRIDENT_THROW", "When throwing trident"),

    /**
     * Triggered periodically (every X ticks).
     */
    TICK("TICK", "Periodic effect"),

    /**
     * Triggered when player breaks a block.
     */
    BLOCK_BREAK("BLOCK_BREAK", "When breaking blocks"),

    /**
     * Triggered when player places a block.
     */
    BLOCK_PLACE("BLOCK_PLACE", "When placing blocks"),

    /**
     * Triggered when player interacts (right-click).
     */
    INTERACT("INTERACT", "When interacting");

    private final String configKey;
    private final String description;

    TriggerType(String configKey, String description) {
        this.configKey = configKey;
        this.description = description;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get TriggerType from config key string.
     *
     * @param key The config key (e.g., "ATTACK", "DEFENSE")
     * @return The TriggerType, or null if not found
     */
    public static TriggerType fromConfigKey(String key) {
        if (key == null) return null;
        String upperKey = key.toUpperCase();
        for (TriggerType type : values()) {
            if (type.configKey.equals(upperKey)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if this trigger type involves combat.
     */
    public boolean isCombatTrigger() {
        return this == ATTACK || this == DEFENSE || this == KILL_MOB ||
               this == KILL_PLAYER || this == BOW_HIT;
    }

    /**
     * Check if this is a passive/static effect.
     */
    public boolean isPassive() {
        return this == EFFECT_STATIC;
    }
}
