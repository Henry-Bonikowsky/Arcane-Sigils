package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;

public class ParticleEffect extends AbstractEffect {

    public ParticleEffect() {
        super("PARTICLE", "Spawns particles at target location");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        if (parts.length >= 2) {
            params.set("particle_type", parts[1].toUpperCase());
        }
        if (parts.length >= 3) {
            try {
                params.setValue(Double.parseDouble(parts[2])); // count
            } catch (NumberFormatException ignored) {}
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) return false;

        String particleName = params.getString("particle_type", "FLAME");
        int count = (int) params.getValue();
        if (count <= 0) count = 10;

        Particle particle = getParticle(particleName);
        if (particle == null) {
            debug("Unknown particle type: " + particleName);
            return false;
        }

        String targetStr = params.getTarget();

        // Handle @Nearby
        if (targetStr != null && targetStr.startsWith("@Nearby")) {
            double radius = parseNearbyRadius(targetStr, 5);
            for (LivingEntity entity : getNearbyEntities(context, radius)) {
                spawnParticles(entity.getLocation().add(0, 1, 0), particle, count);
            }
            return true;
        }

        Location loc = getTargetLocation(context);
        if (loc != null) {
            spawnParticles(loc.add(0, 1, 0), particle, count);
            return true;
        }

        return false;
    }

    private void spawnParticles(Location loc, Particle particle, int count) {
        loc.getWorld().spawnParticle(particle, loc, count, 0.5, 0.5, 0.5, 0.02);
    }

    private Particle getParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try common aliases
            return switch (name.toUpperCase()) {
                case "FIRE" -> Particle.FLAME;
                case "SOUL_FIRE" -> Particle.SOUL_FIRE_FLAME;
                case "MAGIC" -> Particle.ENCHANT;
                case "SPARKLE" -> Particle.END_ROD;
                case "REVERSE_PORTAL" -> Particle.PORTAL;
                case "HEART" -> Particle.HEART;
                case "ANGRY" -> Particle.ANGRY_VILLAGER;
                case "HAPPY" -> Particle.HAPPY_VILLAGER;
                default -> null;
            };
        }
    }
}
