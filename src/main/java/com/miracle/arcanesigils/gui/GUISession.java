package com.miracle.arcanesigils.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.*;

/**
 * Represents a player's GUI session with arbitrary data storage.
 */
public class GUISession {

    /** Standard flow context keys that child sessions inherit automatically. */
    private static final Set<String> FLOW_CONTEXT_KEYS = Set.of(
            "sigil", "signalKey", "flow", "originalFlow",
            "selectedNode", "viewX", "viewY",
            "addNodeX", "addNodeY", "flowConfig"
    );

    private final GUIType type;
    private final Map<String, Object> data;
    private Inventory inventory;

    public GUISession(GUIType type) {
        this.type = type;
        this.data = new HashMap<>();
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public GUIType getType() {
        return type;
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value == null) return null;
        return (T) value;
    }

    public Object get(String key) {
        return data.get(key);
    }

    public int getInt(String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Integer) return (Integer) value;
        return defaultValue;
    }

    public double getDouble(String key, double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        return defaultValue;
    }

    public boolean getBooleanOpt(String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        return false;
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    public void remove(String key) {
        data.remove(key);
    }

    public Map<String, Object> getData() {
        return Collections.unmodifiableMap(data);
    }

    public void clear() {
        data.clear();
    }

    /**
     * Create a child session that inherits flow context keys from this session.
     * Use this instead of manually copying sigil/signalKey/flow/etc.
     *
     * @param childType The GUIType for the child session
     * @return New session with flow context keys copied
     */
    public GUISession deriveChild(GUIType childType) {
        GUISession child = new GUISession(childType);
        for (String key : FLOW_CONTEXT_KEYS) {
            Object value = data.get(key);
            if (value != null) {
                child.data.put(key, value);
            }
        }
        return child;
    }

    /**
     * Copy specific keys from this session to another.
     */
    public void copyTo(GUISession target, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) {
                target.data.put(key, value);
            }
        }
    }

    /**
     * Create a session validator for requiring multiple values at once.
     */
    public Validator validator(Player player) {
        return new Validator(this, player);
    }

    /**
     * Helper class for validating required session data.
     */
    public static class Validator {
        private final GUISession session;
        private final Player player;
        private final List<String> missingKeys = new ArrayList<>();

        public Validator(GUISession session, Player player) {
            this.session = session;
            this.player = player;
        }

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

        public int requireInt(String key, int defaultValue) {
            return session.getInt(key, defaultValue);
        }

        public boolean handleInvalid() {
            if (missingKeys.isEmpty()) return false;
            player.sendMessage("§cError: Missing session data: " + String.join(", ", missingKeys));
            player.closeInventory();
            return true;
        }

        public boolean isValid() {
            return missingKeys.isEmpty();
        }

        public List<String> getMissingKeys() {
            return Collections.unmodifiableList(missingKeys);
        }
    }
}
