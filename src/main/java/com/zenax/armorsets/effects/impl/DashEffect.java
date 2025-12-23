package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Dash effect - launches the player in a direction with configurable force.
 * Format: DASH:DIRECTION:FORCE
 *
 * Directions:
 * - FORWARD: Launch in the direction the player is facing
 * - WITH_MOMENTUM: Launch in the direction of player's current velocity
 * - UP: Launch straight up
 */
public class DashEffect extends AbstractEffect {

    public DashEffect() {
        super("DASH", "Launch yourself in a direction");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // DASH:direction:force - supports both positional and key=value
        params.set("direction", "FORWARD");
        params.setValue(1.5);

        int positionalIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    switch (key) {
                        case "direction", "dir" -> params.set("direction", value.toUpperCase());
                        case "force", "value" -> params.setValue(parseDouble(value, 1.5));
                    }
                }
            } else {
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.set("direction", part.toUpperCase());
                    case 2 -> params.setValue(parseDouble(part, 1.5));
                }
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();

        String direction = context.getParams() != null ?
            (String) context.getParams().get("direction", "FORWARD") : "FORWARD";
        double distance = context.getParams() != null ? context.getParams().getValue() : 5.0;

        // Cap distance to prevent extreme launches (max 20 blocks)
        distance = Math.min(distance, 20.0);

        // Convert distance (blocks) to velocity
        // Minecraft physics: horizontal velocity 1.0 travels ~7 blocks, vertical ~4 blocks
        // These are approximations - actual distance depends on air time, angle, etc.
        double velocity;
        switch (direction.toUpperCase()) {
            case "UP" -> velocity = distance / 4.0;           // Vertical: ~4 blocks per 1.0 velocity
            default -> velocity = distance / 7.0;             // Horizontal: ~7 blocks per 1.0 velocity
        }

        Vector velocityVec;
        switch (direction.toUpperCase()) {
            case "WITH_MOMENTUM" -> {
                // Use player's current velocity direction, or facing if standing still
                Vector currentVel = player.getVelocity();
                if (currentVel.lengthSquared() > 0.01) {
                    velocityVec = currentVel.normalize().multiply(velocity);
                } else {
                    velocityVec = player.getLocation().getDirection().multiply(velocity);
                }
            }
            case "UP" -> {
                velocityVec = new Vector(0, velocity, 0);
            }
            default -> { // FORWARD
                velocityVec = player.getLocation().getDirection().multiply(velocity);
            }
        }

        // Apply velocity
        player.setVelocity(velocityVec);

        // Visual and sound effects
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.5, 0.2, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.2f);
        return true;
    }
}
