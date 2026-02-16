package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.binds.TargetGlowManager;
import com.miracle.arcanesigils.effects.Effect;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.flow.FlowContext;
import com.miracle.arcanesigils.utils.TargetFinder;
import com.miracle.arcanesigils.utils.TextUtil;
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
     * Supports both positional and key=value formats.
     */
    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = new EffectParams(id);

        // Remove target selector from string
        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();

        // Parse target selector
        if (effectString.contains("@")) {
            String[] spaceParts = effectString.split("\\s+");
            for (String part : spaceParts) {
                if (part.startsWith("@")) {
                    params.setTarget(part);
                    break;
                }
            }
        }

        // Parse effect:value format - supports both positional and key=value
        String[] parts = cleanedString.split(":");
        int positionalIndex = 0;

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];

            if (part.contains("=")) {
                // Key=value format
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    parseKeyValueParam(params, key, value);
                }
            } else {
                // Positional format
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> {
                        try {
                            params.setValue(Double.parseDouble(part));
                        } catch (NumberFormatException e) {
                            params.set("param1", part);
                        }
                    }
                    case 2 -> {
                        try {
                            params.setDuration(Integer.parseInt(part));
                        } catch (NumberFormatException e) {
                            params.set("param2", part);
                        }
                    }
                    case 3 -> {
                        try {
                            params.setAmplifier(Integer.parseInt(part));
                        } catch (NumberFormatException e) {
                            params.set("param3", part);
                        }
                    }
                }
            }
        }

        return params;
    }

    /**
     * Parse a key=value parameter. Override in subclasses for custom params.
     */
    protected void parseKeyValueParam(EffectParams params, String key, String value) {
        switch (key) {
            case "value" -> params.setValue(parseDouble(value, 0));
            case "duration" -> params.setDuration(parseInt(value, 0));
            case "amplifier" -> params.setAmplifier(parseInt(value, 0));
            default -> params.set(key, value);
        }
    }

    /**
     * Helper to parse int safely. Handles "2.0" style doubles from YAML.
     */
    protected int parseInt(String s, int defaultVal) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            // YAML often stores integers as "2.0" - try parsing as double
            try {
                return (int) Double.parseDouble(s);
            } catch (NumberFormatException e2) {
                return defaultVal;
            }
        }
    }

    /**
     * Helper to parse double safely.
     */
    protected double parseDouble(String s, double defaultVal) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * Get the target entity based on context and params.
     * For ability-style effects (no victim), automatically finds targets in front of the player.
     */
    protected LivingEntity getTarget(EffectContext context) {
        return getTarget(context, 10.0); // Default 10 block range
    }

    /**
     * Get the target entity based on context and params with custom range.
     */
    protected LivingEntity getTarget(EffectContext context, double range) {
        EffectParams params = context.getParams();
        if (params == null) {
            return context.getPlayer();
        }

        String target = params.getTarget();
        if (target == null || target.equalsIgnoreCase("@Self")) {
            return context.getPlayer();
        } else if (target.equalsIgnoreCase("@Victim")) {
            // If victim exists (combat event), use it
            if (context.getVictim() != null) {
                return context.getVictim();
            }
            // Otherwise, find target in front of player (ability-style)
            LivingEntity lookTarget = TargetFinder.findLookTarget(context.getPlayer(), range);
            if (lookTarget != null) {
                return lookTarget;
            }
            // Fallback to self if no target found
            return null;
        } else if (target.equalsIgnoreCase("@Attacker")) {
            // Target the entity that hit the player (for DEFENSE signals)
            if (context.getAttacker() != null) {
                return context.getAttacker();
            }
            // Fallback to null if no attacker
            return null;
        } else if (target.equalsIgnoreCase("@Target")) {
            // Target from bind menu selection (for ABILITY flows)
            TargetGlowManager targetManager = ArmorSetsPlugin.getInstance().getTargetGlowManager();
            if (targetManager != null) {
                LivingEntity bindTarget = targetManager.getTarget(context.getPlayer());
                if (bindTarget != null && !bindTarget.isDead()) {
                    return bindTarget;
                }
            }

            // No valid target - stop flow with error
            FlowContext flowContext = context.getFlowContext();
            if (flowContext != null) {
                String abilityName = context.getSigilId() != null
                    ? context.getSigilId()
                    : "Ability";
                flowContext.setError(abilityName + " requires a target!");
            }

            return null;
        } else if (target.equalsIgnoreCase("@LookTarget") || target.equalsIgnoreCase("@Look")) {
            // Explicitly target what player is looking at
            return TargetFinder.findLookTarget(context.getPlayer(), range);
        }

        return context.getPlayer();
    }

    /**
     * Get multiple targets in front of the player (for cone/AoE abilities).
     */
    protected List<LivingEntity> getTargetsInCone(EffectContext context, double range, double angleDeg) {
        return TargetFinder.findEntitiesInCone(context.getPlayer(), range, angleDeg);
    }

    /**
     * Get multiple targets in a line in front of the player.
     */
    protected List<LivingEntity> getTargetsInLine(EffectContext context, double range, double width) {
        return TargetFinder.findEntitiesInLine(context.getPlayer(), range, width);
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
     * Get nearby allies (faction members/allies) within a radius.
     * Falls back to all nearby entities if Factions is not available.
     */
    protected List<LivingEntity> getNearbyAllies(EffectContext context, double radius) {
        List<LivingEntity> nearby = getNearbyEntities(context, radius);
        if (!com.miracle.arcanesigils.hooks.FactionsHook.isAvailable()) {
            return nearby;
        }
        Player player = context.getPlayer();
        return nearby.stream()
            .filter(entity -> {
                if (entity instanceof Player target) {
                    return com.miracle.arcanesigils.hooks.FactionsHook.isAlly(player, target);
                }
                return false;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get nearby enemies within a radius.
     * Falls back to all nearby entities if Factions is not available.
     */
    protected List<LivingEntity> getNearbyEnemies(EffectContext context, double radius) {
        List<LivingEntity> nearby = getNearbyEntities(context, radius);
        if (!com.miracle.arcanesigils.hooks.FactionsHook.isAvailable()) {
            return nearby;
        }
        Player player = context.getPlayer();
        return nearby.stream()
            .filter(entity -> {
                if (entity instanceof Player target) {
                    return com.miracle.arcanesigils.hooks.FactionsHook.isEnemy(player, target);
                }
                return true; // Non-players (mobs) count as enemies
            })
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Parse radius from @Nearby:X or @NearbyEntities:X target selector.
     */
    protected double parseNearbyRadius(String target, double defaultRadius) {
        if (target == null) return defaultRadius;

        // Handle @NearbyEntities:X format
        if (target.startsWith("@NearbyEntities:")) {
            try {
                return Double.parseDouble(target.substring(16));
            } catch (NumberFormatException e) {
                return defaultRadius;
            }
        }

        // Handle @Nearby:X format
        if (target.startsWith("@Nearby:")) {
            try {
                return Double.parseDouble(target.substring(8));
            } catch (NumberFormatException e) {
                return defaultRadius;
            }
        }

        // Handle @NearbyAllies:X format
        if (target.startsWith("@NearbyAllies:")) {
            try {
                return Double.parseDouble(target.substring(14));
            } catch (NumberFormatException e) {
                return defaultRadius;
            }
        }

        // Handle @NearbyEnemies:X format
        if (target.startsWith("@NearbyEnemies:")) {
            try {
                return Double.parseDouble(target.substring(15));
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
