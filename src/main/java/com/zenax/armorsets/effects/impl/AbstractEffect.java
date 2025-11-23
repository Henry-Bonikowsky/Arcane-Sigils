package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.effects.Effect;
import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Abstract base class for all effects, providing common functionality.
 */
public abstract class AbstractEffect implements Effect {

    protected final String id;
    protected final String description;

    protected AbstractEffect(String id, String description) {
        this.id = id;
        this.description = description;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Parse a simple effect string format: EFFECT_TYPE:VALUE
     */
    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = new EffectParams(id);

        // Remove target selector from string
        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();

        // Parse target selector
        if (effectString.contains("@")) {
            String[] parts = effectString.split("\\s+");
            for (String part : parts) {
                if (part.startsWith("@")) {
                    params.setTarget(part);
                    break;
                }
            }
        }

        // Parse effect:value format
        String[] parts = cleanedString.split(":");
        if (parts.length >= 2) {
            try {
                params.setValue(Double.parseDouble(parts[1]));
            } catch (NumberFormatException e) {
                params.set("param1", parts[1]);
            }
        }
        if (parts.length >= 3) {
            try {
                params.setDuration(Integer.parseInt(parts[2]));
            } catch (NumberFormatException e) {
                params.set("param2", parts[2]);
            }
        }
        if (parts.length >= 4) {
            try {
                params.setAmplifier(Integer.parseInt(parts[3]));
            } catch (NumberFormatException e) {
                params.set("param3", parts[3]);
            }
        }

        return params;
    }

    /**
     * Get the target entity based on context and params.
     */
    protected LivingEntity getTarget(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) {
            return context.getPlayer();
        }

        String target = params.getTarget();
        if (target == null || target.equalsIgnoreCase("@Self")) {
            return context.getPlayer();
        } else if (target.equalsIgnoreCase("@Victim")) {
            return context.getVictim();
        }

        return context.getPlayer();
    }

    /**
     * Get the target location based on context and params.
     */
    protected Location getTargetLocation(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) {
            return context.getPlayer().getLocation();
        }

        String target = params.getTarget();
        return context.getTargetLocation(target);
    }

    /**
     * Get nearby entities within a radius.
     */
    protected List<LivingEntity> getNearbyEntities(EffectContext context, double radius) {
        Location loc = getTargetLocation(context);
        Collection<Entity> entities = loc.getWorld().getNearbyEntities(loc, radius, radius, radius);

        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity living) {
                if (entity != context.getPlayer()) {
                    result.add(living);
                }
            }
        }
        return result;
    }

    /**
     * Parse radius from @Nearby:X target selector.
     */
    protected double parseNearbyRadius(String target, double defaultRadius) {
        if (target != null && target.startsWith("@Nearby:")) {
            try {
                return Double.parseDouble(target.substring(8));
            } catch (NumberFormatException e) {
                return defaultRadius;
            }
        }
        return defaultRadius;
    }

    /**
     * Get the plugin instance.
     */
    protected ArmorSetsPlugin getPlugin() {
        return ArmorSetsPlugin.getInstance();
    }

    /**
     * Log a debug message.
     */
    protected void debug(String message) {
        if (getPlugin().getConfigManager().getMainConfig().getBoolean("settings.debug", false)) {
            getPlugin().getLogger().info("[DEBUG] " + message);
        }
    }
}
