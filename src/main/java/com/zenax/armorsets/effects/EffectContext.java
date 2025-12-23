package com.zenax.armorsets.effects;

import com.zenax.armorsets.events.SignalType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import java.util.HashMap;
import java.util.Map;

/**
 * Context object containing all information needed to execute an effect.
 */
public class EffectContext {

    private final Player player;
    private final SignalType signalType;
    private final Event bukkitEvent;
    private LivingEntity victim;
    private LivingEntity attacker;
    private Location location;
    private double damage;
    private final EffectParams params;
    private final Map<String, Object> metadata;
    private final Map<String, Object> variables;
    private boolean cancelled;
    private final String sigilId;
    private final String signalKey;

    private EffectContext(Builder builder) {
        this.player = builder.player;
        this.signalType = builder.signalType;
        this.bukkitEvent = builder.bukkitEvent;
        this.victim = builder.victim;
        this.attacker = builder.attacker;
        this.location = builder.location;
        this.damage = builder.damage;
        this.params = builder.params;
        this.metadata = builder.metadata;
        this.variables = builder.variables;
        this.cancelled = false;
        this.sigilId = builder.sigilId;
        this.signalKey = builder.signalKey;
    }

    public Player getPlayer() {
        return player;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public Event getBukkitEvent() {
        return bukkitEvent;
    }

    public LivingEntity getVictim() {
        return victim;
    }

    public void setVictim(LivingEntity victim) {
        this.victim = victim;
    }

    public LivingEntity getAttacker() {
        return attacker;
    }

    public void setAttacker(LivingEntity attacker) {
        this.attacker = attacker;
    }

    public Location getLocation() {
        return location != null ? location : player.getLocation();
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public EffectParams getParams() {
        return params;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        Object value = metadata.get(key);
        return value != null ? (T) value : defaultValue;
    }

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Get a stored variable value.
     * Variables can be set by effects (e.g., storing position before teleport).
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String name) {
        return variables != null ? (T) variables.get(name) : null;
    }

    /**
     * Get a stored variable with default value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String name, T defaultValue) {
        if (variables == null) return defaultValue;
        Object value = variables.get(name);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Store a variable for use by subsequent effects.
     */
    public void setVariable(String name, Object value) {
        if (variables != null) {
            variables.put(name, value);
        }
    }

    /**
     * Check if a variable exists.
     */
    public boolean hasVariable(String name) {
        return variables != null && variables.containsKey(name);
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public String getSigilId() {
        return sigilId;
    }

    public String getSignalKey() {
        return signalKey;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * Cancel the underlying Bukkit event if it's cancellable.
     */
    public void cancelEvent() {
        if (bukkitEvent instanceof Cancellable cancellable) {
            cancellable.setCancelled(true);
            this.cancelled = true;
        }
    }

    /**
     * Get the target based on the target selector string.
     *
     * @param targetSelector The target selector (@Self, @Victim, @Nearby:radius)
     * @return The target location
     */
    public Location getTargetLocation(String targetSelector) {
        if (targetSelector == null || targetSelector.equalsIgnoreCase("@Self")) {
            return player.getLocation();
        } else if (targetSelector.equalsIgnoreCase("@Victim") && victim != null) {
            return victim.getLocation();
        } else if (targetSelector.equalsIgnoreCase("@Attacker") && attacker != null) {
            return attacker.getLocation();
        } else if (targetSelector.startsWith("@Nearby")) {
            // For @Nearby, return player location (effects will handle radius)
            return player.getLocation();
        }
        return player.getLocation();
    }

    /**
     * Get the target entity based on the target selector.
     *
     * @param targetSelector The target selector
     * @return The target entity, or null if not applicable
     */
    public LivingEntity getTargetEntity(String targetSelector) {
        if (targetSelector == null || targetSelector.equalsIgnoreCase("@Self")) {
            return player;
        } else if (targetSelector.equalsIgnoreCase("@Victim")) {
            return victim;
        } else if (targetSelector.equalsIgnoreCase("@Attacker")) {
            return attacker;
        }
        return player;
    }

    public static Builder builder(Player player, SignalType signalType) {
        return new Builder(player, signalType);
    }

    public static class Builder {
        private final Player player;
        private final SignalType signalType;
        private Event bukkitEvent;
        private LivingEntity victim;
        private LivingEntity attacker;
        private Location location;
        private double damage;
        private EffectParams params;
        private final Map<String, Object> metadata = new HashMap<>();
        private final Map<String, Object> variables = new HashMap<>();
        private String sigilId;
        private String signalKey;

        public Builder(Player player, SignalType signalType) {
            this.player = player;
            this.signalType = signalType;
        }

        public Builder event(Event event) {
            this.bukkitEvent = event;
            return this;
        }

        public Builder victim(LivingEntity victim) {
            this.victim = victim;
            return this;
        }

        public Builder attacker(LivingEntity attacker) {
            this.attacker = attacker;
            return this;
        }

        public Builder location(Location location) {
            this.location = location;
            return this;
        }

        public Builder damage(double damage) {
            this.damage = damage;
            return this;
        }

        public Builder params(EffectParams params) {
            this.params = params;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder variable(String name, Object value) {
            this.variables.put(name, value);
            return this;
        }

        public Builder sigilId(String sigilId) {
            this.sigilId = sigilId;
            return this;
        }

        public Builder signalKey(String signalKey) {
            this.signalKey = signalKey;
            return this;
        }

        public EffectContext build() {
            return new EffectContext(this);
        }
    }
}
