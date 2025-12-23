package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.AuraManager;
import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Spawns an aura zone that continuously applies a potion effect to entities within its radius.
 * Useful for area denial abilities like the Cursed Pillar or Quick Sand.
 *
 * Format: SPAWN_AURA:DURATION:RADIUS:EFFECT_TYPE:AMPLIFIER:SHOW_PARTICLES:AFFECTS @Target
 * Examples:
 *   SPAWN_AURA:10:3:WEAKNESS:4:true:ENEMIES @Self
 *   SPAWN_AURA:15:5:SLOWNESS:1:true:ENEMIES_PLAYERS_ONLY @Victim
 *
 * Extended parameters (via params in YAML):
 *   follow_owner: true/false - Aura follows the owner around
 *   pull_on_hit: true/false - Pull enemies toward owner when owner is hit
 *   pull_strength: 1.0 - Strength of the pull
 */
public class SpawnAuraEffect extends AbstractEffect {

    public SpawnAuraEffect() {
        super("SPAWN_AURA", "Creates an aura zone that applies effects to entities in radius");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // SPAWN_AURA:DURATION:RADIUS:EFFECT_TYPE:AMPLIFIER:SHOW_PARTICLES:AFFECTS
        // Supports both positional and key=value format
        int positionalIndex = 0;

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];

            if (part.contains("=")) {
                // Key=value format
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    switch (key) {
                        case "duration" -> params.setDuration((int) parseDouble(value, 10));
                        case "radius" -> params.set("radius", parseDouble(value, 3.0));
                        case "effect_type", "type" -> params.set("effect_type", value.toUpperCase());
                        case "amplifier", "amp" -> params.setAmplifier((int) parseDouble(value, 0));
                        case "show_particles", "particles" -> params.set("show_particles", Boolean.parseBoolean(value));
                        case "affects" -> params.set("affects", value.toUpperCase());
                        case "follow_owner" -> params.set("follow_owner", Boolean.parseBoolean(value));
                        case "pull_on_hit" -> params.set("pull_on_hit", Boolean.parseBoolean(value));
                        case "pull_on_attack" -> params.set("pull_on_attack", Boolean.parseBoolean(value));
                        case "pull_strength" -> params.set("pull_strength", parseDouble(value, 1.0));
                        case "interval" -> params.set("interval", parseDouble(value, 1.0));
                        case "potion_type" -> params.set("effect_type", value.toUpperCase());
                        case "potion_duration" -> params.set("potion_duration", (int) parseDouble(value, 2));
                        case "potion_amplifier" -> params.setAmplifier((int) parseDouble(value, 0));
                    }
                }
            } else {
                // Positional format
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> {
                        try { params.setDuration((int) Double.parseDouble(part)); } catch (NumberFormatException ignored) {}
                    }
                    case 2 -> {
                        try { params.set("radius", Double.parseDouble(part)); } catch (NumberFormatException ignored) {}
                    }
                    case 3 -> params.set("effect_type", part.toUpperCase());
                    case 4 -> {
                        try { params.setAmplifier((int) Double.parseDouble(part)); } catch (NumberFormatException ignored) {}
                    }
                    case 5 -> params.set("show_particles", part.equalsIgnoreCase("true"));
                    case 6 -> params.set("affects", part.toUpperCase());
                }
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) return false;

        Player owner = context.getPlayer();
        if (owner == null) return false;

        // Parse parameters
        int duration = params.getDuration() > 0 ? params.getDuration() : 10;
        double radius = params.getDouble("radius", 3.0);
        String effectTypeStr = params.getString("effect_type", "WEAKNESS");
        int amplifier = params.getAmplifier();
        boolean showParticles = params.getBoolean("show_particles", true);
        String affectsStr = params.getString("affects", "ENEMIES");

        // Parse effect type (NONE = pull-only aura, no potion effect)
        PotionEffectType effectType = null;
        if (!"NONE".equalsIgnoreCase(effectTypeStr)) {
            effectType = PotionEffectType.getByName(effectTypeStr);
            if (effectType == null) {
                // Try common aliases
                effectType = switch (effectTypeStr) {
                    case "SLOW" -> PotionEffectType.SLOWNESS;
                    case "DAMAGE_RESISTANCE", "RESISTANCE" -> PotionEffectType.RESISTANCE;
                    case "FAST_DIGGING", "HASTE" -> PotionEffectType.HASTE;
                    case "SLOW_DIGGING", "MINING_FATIGUE" -> PotionEffectType.MINING_FATIGUE;
                    case "INCREASE_DAMAGE", "STRENGTH" -> PotionEffectType.STRENGTH;
                    case "HEAL", "INSTANT_HEALTH" -> PotionEffectType.INSTANT_HEALTH;
                    case "HARM", "INSTANT_DAMAGE" -> PotionEffectType.INSTANT_DAMAGE;
                    case "JUMP", "JUMP_BOOST" -> PotionEffectType.JUMP_BOOST;
                    case "CONFUSION", "NAUSEA" -> PotionEffectType.NAUSEA;
                    default -> null;
                };
            }

            if (effectType == null) {
                debug("Unknown potion effect type: " + effectTypeStr);
                return false;
            }
        }

        // Parse affects type
        AuraManager.AffectsType affects;
        try {
            affects = AuraManager.AffectsType.valueOf(affectsStr);
        } catch (IllegalArgumentException e) {
            affects = AuraManager.AffectsType.ENEMIES;
        }

        // Get spawn location
        Location location = getTargetLocation(context);
        if (location == null || location.getWorld() == null) {
            debug("No valid location for SPAWN_AURA");
            return false;
        }

        // Get or create aura manager
        AuraManager auraManager = getPlugin().getAuraManager();
        if (auraManager == null) {
            debug("AuraManager not available");
            return false;
        }

        // Parse extended parameters
        boolean followOwner = params.getBoolean("follow_owner", false);
        boolean pullOnHit = params.getBoolean("pull_on_hit", false);
        boolean pullOnAttack = params.getBoolean("pull_on_attack", false);
        double pullStrength = params.getDouble("pull_strength", 1.0);

        // Spawn the aura with extended features
        auraManager.spawnAura(owner, location, radius, duration, effectType, amplifier, affects,
                showParticles, followOwner, pullOnHit, pullOnAttack, pullStrength);

        debug("Spawned aura for owner " + owner.getName() + " (" + owner.getUniqueId().toString().substring(0, 8) +
              ") at " + location.toVector() + ": radius=" + radius + ", effect=" + (effectType != null ? effectType.getName() : "NONE") +
              ", amplifier=" + amplifier + ", affects=" + affects.name() + ", duration=" + duration + "s" +
              (followOwner ? " (following)" : "") + (pullOnAttack ? " (pull-on-attack)" : ""));

        return true;
    }
}
