package com.zenax.dungeons.stats;

import java.util.UUID;

/**
 * Represents a modification to a stat, either permanent or temporary.
 * Can be a flat value or percentage-based.
 */
public class StatModifier {
    private final String id;
    private final Stat stat;
    private final double value;
    private final ModifierType type;
    private final String source;
    private final long expiresAt; // -1 for permanent

    /**
     * Private constructor to enforce use of factory methods.
     *
     * @param id Unique identifier for this modifier
     * @param stat The stat being modified
     * @param value The value of the modification
     * @param type The type of modification (FLAT or PERCENT)
     * @param source The source of the modification
     * @param expiresAt When the modifier expires (-1 for permanent)
     */
    private StatModifier(String id, Stat stat, double value, ModifierType type, String source, long expiresAt) {
        this.id = id;
        this.stat = stat;
        this.value = value;
        this.type = type;
        this.source = source;
        this.expiresAt = expiresAt;
    }

    /**
     * Gets the unique identifier of this modifier.
     *
     * @return The modifier ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the stat being modified.
     *
     * @return The stat
     */
    public Stat getStat() {
        return stat;
    }

    /**
     * Gets the value of the modification.
     *
     * @return The value
     */
    public double getValue() {
        return value;
    }

    /**
     * Gets the type of modification.
     *
     * @return The modifier type
     */
    public ModifierType getType() {
        return type;
    }

    /**
     * Gets the source of this modification.
     *
     * @return The source
     */
    public String getSource() {
        return source;
    }

    /**
     * Gets the expiration timestamp of this modifier.
     *
     * @return The expiration time in milliseconds, or -1 for permanent
     */
    public long getExpiresAt() {
        return expiresAt;
    }

    /**
     * Checks if this modifier has expired.
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        if (expiresAt == -1) {
            return false;
        }
        return System.currentTimeMillis() > expiresAt;
    }

    /**
     * Checks if this modifier is permanent.
     *
     * @return true if permanent, false otherwise
     */
    public boolean isPermanent() {
        return expiresAt == -1;
    }

    /**
     * Creates a permanent flat modifier.
     *
     * @param stat The stat to modify
     * @param value The flat value to add
     * @param source The source of the modifier
     * @return A new StatModifier
     */
    public static StatModifier flat(Stat stat, double value, String source) {
        return new StatModifier(
            UUID.randomUUID().toString(),
            stat,
            value,
            ModifierType.FLAT,
            source,
            -1
        );
    }

    /**
     * Creates a permanent percentage modifier.
     *
     * @param stat The stat to modify
     * @param value The percentage value (e.g., 0.15 for 15%)
     * @param source The source of the modifier
     * @return A new StatModifier
     */
    public static StatModifier percent(Stat stat, double value, String source) {
        return new StatModifier(
            UUID.randomUUID().toString(),
            stat,
            value,
            ModifierType.PERCENT,
            source,
            -1
        );
    }

    /**
     * Creates a timed flat modifier.
     *
     * @param stat The stat to modify
     * @param value The flat value to add
     * @param source The source of the modifier
     * @param durationMillis Duration in milliseconds
     * @return A new StatModifier
     */
    public static StatModifier timed(Stat stat, double value, ModifierType type, String source, long durationMillis) {
        return new StatModifier(
            UUID.randomUUID().toString(),
            stat,
            value,
            type,
            source,
            System.currentTimeMillis() + durationMillis
        );
    }

    /**
     * Creates a custom modifier with a specific ID.
     *
     * @param id Custom ID for the modifier
     * @param stat The stat to modify
     * @param value The value of the modification
     * @param type The type of modification
     * @param source The source of the modifier
     * @param expiresAt When the modifier expires (-1 for permanent)
     * @return A new StatModifier
     */
    public static StatModifier create(String id, Stat stat, double value, ModifierType type, String source, long expiresAt) {
        return new StatModifier(id, stat, value, type, source, expiresAt);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        StatModifier that = (StatModifier) obj;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "StatModifier{" +
                "id='" + id + '\'' +
                ", stat=" + stat +
                ", value=" + value +
                ", type=" + type +
                ", source='" + source + '\'' +
                ", expiresAt=" + (expiresAt == -1 ? "PERMANENT" : expiresAt) +
                '}';
    }
}
