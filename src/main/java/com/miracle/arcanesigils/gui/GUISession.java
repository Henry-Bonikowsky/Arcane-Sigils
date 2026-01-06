package com.miracle.arcanesigils.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.*;

/**
 * Represents a player's GUI session with arbitrary data storage and navigation history.
 */
public class GUISession {

    private final GUIType type;
    private final Map<String, Object> data;
    private final Stack<NavigationFrame> navigationHistory;
    private Inventory inventory; // Store reference to avoid reopening on refresh

    public GUISession(GUIType type) {
        this.type = type;
        this.data = new HashMap<>();
        this.navigationHistory = new Stack<>();
    }

    /**
     * Get the inventory associated with this session.
     */
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Set the inventory for this session.
     */
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * Get the current GUI type.
     */
    public GUIType getType() {
        return type;
    }

    /**
     * Store arbitrary data in the session.
     */
    public void put(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Retrieve data from the session with type safety.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * Get raw value without type checking.
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * Get an integer value with a default.
     */
    public int getInt(String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return defaultValue;
    }

    /**
     * Get a double value with a default.
     */
    public double getDouble(String key, double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    /**
     * Get a boolean value (optional).
     */
    public boolean getBooleanOpt(String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    /**
     * Check if a key exists in session data.
     */
    public boolean has(String key) {
        return data.containsKey(key);
    }

    /**
     * Create a session validator for requiring multiple values at once.
     * Usage:
     * <pre>
     * var v = session.validator(player);
     * Sigil sigil = v.require("sigil", Sigil.class);
     * String key = v.require("signalKey", String.class);
     * if (v.handleInvalid()) return;
     * // Both sigil and key are guaranteed non-null here
     * </pre>
     */
    public Validator validator(Player player) {
        return new Validator(this, player);
    }

    /**
     * Remove a key from session data.
     */
    public void remove(String key) {
        data.remove(key);
    }

    /**
     * Get all session data (read-only).
     */
    public Map<String, Object> getData() {
        return Collections.unmodifiableMap(data);
    }

    /**
     * Clear all session data.
     */
    public void clear() {
        data.clear();
    }

    /**
     * Push current GUI state onto navigation history.
     */
    public void pushNavigation(GUIType previousType, Map<String, Object> previousData) {
        navigationHistory.push(new NavigationFrame(previousType, new HashMap<>(previousData)));
    }

    /**
     * Pop previous GUI state from navigation history.
     */
    public NavigationFrame popNavigation() {
        if (navigationHistory.isEmpty()) {
            return null;
        }
        return navigationHistory.pop();
    }

    /**
     * Check if there's navigation history.
     */
    public boolean hasNavigationHistory() {
        return !navigationHistory.isEmpty();
    }

    /**
     * Clear navigation history.
     */
    public void clearNavigationHistory() {
        navigationHistory.clear();
    }

    /**
     * Represents a snapshot of a previous GUI state.
     */
    public static class NavigationFrame {
        private final GUIType type;
        private final Map<String, Object> data;

        public NavigationFrame(GUIType type, Map<String, Object> data) {
            this.type = type;
            this.data = data;
        }

        public GUIType getType() {
            return type;
        }

        public Map<String, Object> getData() {
            return data;
        }
    }

    /**
     * Helper class for validating required session data.
     * Collects missing keys and provides a single error handling point.
     */
    public static class Validator {
        private final GUISession session;
        private final Player player;
        private final List<String> missingKeys = new ArrayList<>();

        public Validator(GUISession session, Player player) {
            this.session = session;
            this.player = player;
        }

        /**
         * Require a value from session. If null, records the key as missing.
         * @return The value (may be null if missing - check with handleInvalid())
         */
        @SuppressWarnings("unchecked")
        public <T> T require(String key, Class<T> type) {
            Object value = session.data.get(key);
            if (value == null) {
                missingKeys.add(key);
                return null;
            }
            try {
                return (T) value;
            } catch (ClassCastException e) {
                missingKeys.add(key + " (wrong type)");
                return null;
            }
        }

        /**
         * Require an integer with a default if missing (does not mark as invalid).
         */
        public int requireInt(String key, int defaultValue) {
            return session.getInt(key, defaultValue);
        }

        /**
         * Check if validation failed and handle it.
         * Sends error message and closes inventory if any required values were missing.
         * @return true if invalid (caller should return), false if all valid
         */
        public boolean handleInvalid() {
            if (missingKeys.isEmpty()) {
                return false;
            }

            player.sendMessage("§cError: Missing session data: " + String.join(", ", missingKeys));
            player.closeInventory();
            return true;
        }

        /**
         * Check if any required values were missing.
         */
        public boolean isValid() {
            return missingKeys.isEmpty();
        }

        /**
         * Get list of missing keys (for custom error handling).
         */
        public List<String> getMissingKeys() {
            return Collections.unmodifiableList(missingKeys);
        }
    }
}
