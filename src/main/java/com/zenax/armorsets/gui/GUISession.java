package com.zenax.armorsets.gui;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an active GUI session for a player.
 * Stores the GUI type, associated data, and any context-specific information
 * needed to handle interactions and state management.
 */
public class    GUISession {

    private final GUIType type;
    private final ItemStack armor;
    private final int armorSlot;
    private final Map<String, Object> data;
    private final long createdAt;

    /**
     * Create a session with armor context.
     *
     * @param type      The GUI type
     * @param armor     The armor piece being modified (can be null)
     * @param armorSlot The slot the armor is in (-1 if not applicable)
     */
    public GUISession(GUIType type, ItemStack armor, int armorSlot) {
        this.type = type;
        this.armor = armor;
        this.armorSlot = armorSlot;
        this.data = new HashMap<>();
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * Create a session without armor context.
     *
     * @param type The GUI type
     */
    public GUISession(GUIType type) {
        this(type, null, -1);
    }

    // ===== GETTERS =====

    /**
     * Get the GUI type for this session.
     */
    public GUIType getType() {
        return type;
    }

    /**
     * Get the armor piece associated with this session.
     */
    public ItemStack getArmor() {
        return armor;
    }

    /**
     * Get the armor slot this session is working with.
     */
    public int getArmorSlot() {
        return armorSlot;
    }

    /**
     * Get the timestamp when this session was created.
     */
    public long getCreatedAt() {
        return createdAt;
    }

    // ===== DATA STORAGE =====

    /**
     * Store a value in the session data.
     *
     * @param key   The key to store under
     * @param value The value to store
     */
    public void put(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Retrieve a value from the session data.
     *
     * @param key The key to look up
     * @return The value, or null if not found
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * Retrieve a value from the session data with type casting.
     *
     * @param key   The key to look up
     * @param type  The expected type
     * @param <T>   The type parameter
     * @return The value cast to the expected type, or null if not found or wrong type
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Retrieve a string value from the session data.
     *
     * @param key The key to look up
     * @return The string value, or null if not found
     */
    public String getString(String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Retrieve an integer value from the session data.
     *
     * @param key          The key to look up
     * @param defaultValue The default value if not found
     * @return The integer value, or the default if not found
     */
    public int getInt(String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Retrieve a double value from the session data.
     *
     * @param key          The key to look up
     * @param defaultValue The default value if not found
     * @return The double value, or the default if not found
     */
    public double getDouble(String key, double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    /**
     * Check if a key exists in the session data.
     *
     * @param key The key to check
     * @return true if the key exists
     */
    public boolean has(String key) {
        return data.containsKey(key);
    }

    /**
     * Remove a value from the session data.
     *
     * @param key The key to remove
     * @return The removed value, or null if not found
     */
    public Object remove(String key) {
        return data.remove(key);
    }

    /**
     * Clear all session data.
     */
    public void clearData() {
        data.clear();
    }

    // ===== CONVENIENCE METHODS =====

    /**
     * Check if this session has armor context.
     */
    public boolean hasArmor() {
        return armor != null;
    }

    /**
     * Get the build type (for build menu sessions).
     */
    public String getBuildType() {
        return getString("buildType");
    }

    /**
     * Get the build ID (for build menu sessions).
     */
    public String getBuildId() {
        return getString("buildId");
    }

    /**
     * Get the trigger name (for trigger-related sessions).
     */
    public String getTrigger() {
        return getString("trigger");
    }

    /**
     * Get the effect name (for effect-related sessions).
     */
    public String getEffect() {
        return getString("effect");
    }

    /**
     * Get the armor slot name (for slot-specific sessions).
     */
    public String getArmorSlotName() {
        return getString("armorSlot");
    }

    /**
     * Get the current chance value (for trigger config sessions).
     */
    public double getChance() {
        return getDouble("chance", 100.0);
    }

    /**
     * Get the current cooldown value (for trigger config sessions).
     */
    public double getCooldown() {
        return getDouble("cooldown", 0.0);
    }

    @Override
    public String toString() {
        return "GUISession{" +
                "type=" + type +
                ", armorSlot=" + armorSlot +
                ", data=" + data +
                '}';
    }
}
