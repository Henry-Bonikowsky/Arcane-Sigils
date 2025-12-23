package com.zenax.armorsets.events;

/**
 * Enum representing all signal types for armor set effects.
 */
public enum SignalType {
    /**
     * Signaled when player attacks an entity.
     */
    ATTACK("ATTACK", "When attacking"),

    /**
     * Signaled when player takes damage.
     */
    DEFENSE("DEFENSE", "When taking damage"),

    /**
     * Signaled when player kills a mob.
     */
    KILL_MOB("KILL_MOB", "When killing a mob"),

    /**
     * Signaled when player kills another player.
     */
    KILL_PLAYER("KILL_PLAYER", "When killing a player"),

    /**
     * Signaled when player sneaks/shifts.
     */
    SHIFT("SHIFT", "When sneaking"),

    /**
     * Signaled when player takes fall damage.
     */
    FALL_DAMAGE("FALL_DAMAGE", "When taking fall damage"),

    /**
     * Passive effects that are always active while equipped.
     */
    EFFECT_STATIC("EFFECT_STATIC", "Passive effect"),

    /**
     * Signaled when player shoots a bow.
     */
    BOW_SHOOT("BOW_SHOOT", "When shooting a bow"),

    /**
     * Signaled when player's arrow hits a target.
     */
    BOW_HIT("BOW_HIT", "When arrow hits target"),

    /**
     * Signaled when player throws a trident.
     */
    TRIDENT_THROW("TRIDENT_THROW", "When throwing trident"),

    /**
     * Signaled periodically (every X ticks).
     */
    TICK("TICK", "Periodic effect"),

    /**
     * Signaled when player breaks a block.
     */
    BLOCK_BREAK("BLOCK_BREAK", "When breaking blocks"),

    /**
     * Signaled when player places a block.
     */
    BLOCK_PLACE("BLOCK_PLACE", "When placing blocks"),

    /**
     * Signaled when player interacts (right-click).
     */
    INTERACT("INTERACT", "When interacting"),

    /**
     * Signaled when an item is about to break (durability would reach 0).
     * This signal is cancellable - use CANCEL_EVENT to prevent the break.
     */
    ITEM_BREAK("ITEM_BREAK", "When item is about to break"),

    /**
     * Signaled when player catches something while fishing.
     */
    FISH("FISH", "When catching fish"),

    // === Behavior Sigil Signals (for spawned entities/blocks) ===

    /**
     * Signaled when a spawned entity dies.
     * Used by behavior sigils for SPAWN_ENTITY.
     */
    ENTITY_DEATH("ENTITY_DEATH", "When entity dies"),

    /**
     * Signaled when a player enters proximity of a display entity.
     * Used by behavior sigils for SPAWN_DISPLAY.
     */
    PLAYER_NEAR("PLAYER_NEAR", "When player enters radius"),

    /**
     * Signaled when a player stands on display entity blocks.
     * Used by behavior sigils for SPAWN_DISPLAY.
     */
    PLAYER_STAND("PLAYER_STAND", "When player stands on blocks"),

    /**
     * Signaled when a spawned thing's duration expires.
     * Used by all behavior sigils.
     */
    EXPIRE("EXPIRE", "When duration expires"),

    /**
     * Signaled when a projectile display entity collides with a target.
     * Victim is the hit entity. Location is the collision point.
     */
    PROJECTILE_HIT("PROJECTILE_HIT", "When projectile hits target"),

    /**
     * Signaled to behaviors when the owner attacks an entity.
     * Used by aura/trap behaviors to trigger effects when owner attacks.
     */
    OWNER_ATTACK("OWNER_ATTACK", "When owner attacks"),

    /**
     * Signaled to behaviors when the owner takes damage.
     * Used by aura/trap behaviors to trigger effects when owner is hit.
     */
    OWNER_DEFEND("OWNER_DEFEND", "When owner takes damage");

    private final String configKey;
    private final String description;

    SignalType(String configKey, String description) {
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
     * Get SignalType from config key string.
     *
     * @param key The config key (e.g., "ATTACK", "DEFENSE")
     * @return The SignalType, or null if not found
     */
    public static SignalType fromConfigKey(String key) {
        if (key == null) return null;
        String upperKey = key.toUpperCase();
        for (SignalType type : values()) {
            if (type.configKey.equals(upperKey)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if this signal type involves combat.
     */
    public boolean isCombatSignal() {
        return this == ATTACK || this == DEFENSE || this == KILL_MOB ||
               this == KILL_PLAYER || this == BOW_HIT;
    }

    /**
     * Check if this is a passive/static effect.
     */
    public boolean isPassive() {
        return this == EFFECT_STATIC;
    }

    /**
     * Check if this is a behavior sigil signal (for spawned entities/blocks).
     */
    public boolean isBehaviorSignal() {
        return this == ENTITY_DEATH || this == PLAYER_NEAR ||
               this == PLAYER_STAND || this == EXPIRE || this == PROJECTILE_HIT ||
               this == OWNER_ATTACK || this == OWNER_DEFEND;
    }
}
