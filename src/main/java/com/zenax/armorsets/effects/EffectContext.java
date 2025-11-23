package com.zenax.armorsets.effects;

import com.zenax.armorsets.events.TriggerType;
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
    private final TriggerType triggerType;
    private final Event bukkitEvent;
    private LivingEntity victim;
    private Location location;
    private double damage;
    private final EffectParams params;
    private final Map<String, Object> metadata;
    private boolean cancelled;

    private EffectContext(Builder builder) {
        this.player = builder.player;
        this.triggerType = builder.triggerType;
        this.bukkitEvent = builder.bukkitEvent;
        this.victim = builder.victim;
        this.location = builder.location;
        this.damage = builder.damage;
        this.params = builder.params;
        this.metadata = builder.metadata;
        this.cancelled = false;
    }

    public Player getPlayer() {
        return player;
    }

    public TriggerType getTriggerType() {
        return triggerType;
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
        }
        return player;
    }

    public static Builder builder(Player player, TriggerType triggerType) {
        return new Builder(player, triggerType);
    }

    public static class Builder {
        private final Player player;
        private final TriggerType triggerType;
        private Event bukkitEvent;
        private LivingEntity victim;
        private Location location;
        private double damage;
        private EffectParams params;
        private final Map<String, Object> metadata = new HashMap<>();

        public Builder(Player player, TriggerType triggerType) {
            this.player = player;
            this.triggerType = triggerType;
        }

        public Builder event(Event event) {
            this.bukkitEvent = event;
            return this;
        }

        public Builder victim(LivingEntity victim) {
            this.victim = victim;
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

        public EffectContext build() {
            return new EffectContext(this);
        }
    }
}
