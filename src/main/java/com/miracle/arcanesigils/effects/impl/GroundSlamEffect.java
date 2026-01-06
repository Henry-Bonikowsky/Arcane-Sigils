package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.utils.ScreenShakeUtil;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * Ground Slam effect - AoE damage and knockback around player.
 * Format: GROUND_SLAM:DAMAGE:RADIUS:KNOCKBACK
 */
public class GroundSlamEffect extends AbstractEffect {

    public GroundSlamEffect() {
        super("GROUND_SLAM", "AoE damage and knockback");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // GROUND_SLAM:damage:radius:knockback - supports both positional and key=value
        params.setValue(5.0);
        params.set("radius", 4.0);
        params.set("knockback", 1.0);

        int positionalIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    switch (key) {
                        case "damage", "value" -> params.setValue(parseDouble(value, 5.0));
                        case "radius" -> params.set("radius", parseDouble(value, 4.0));
                        case "knockback", "kb" -> params.set("knockback", parseDouble(value, 1.0));
                    }
                }
            } else {
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.setValue(parseDouble(part, 5.0));
                    case 2 -> params.set("radius", parseDouble(part, 4.0));
                    case 3 -> params.set("knockback", parseDouble(part, 1.0));
                }
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();
        double damage = context.getParams() != null ? context.getParams().getValue() : 5.0;
        double radius = context.getParams() != null ?
            ((Number) context.getParams().get("radius", 4.0)).doubleValue() : 4.0;
        double knockback = context.getParams() != null ?
            ((Number) context.getParams().get("knockback", 1.0)).doubleValue() : 1.0;

        // Cap values
        damage = Math.min(damage, 20.0);
        radius = Math.min(radius, 10.0);
        knockback = Math.min(knockback, 3.0);

        // Get nearby entities
        Collection<Entity> nearby = player.getWorld().getNearbyEntities(
            player.getLocation(), radius, radius, radius);

        int hitCount = 0;
        for (Entity entity : nearby) {
            if (entity instanceof LivingEntity living && entity != player) {
                // Deal damage
                living.damage(damage, player);

                // Apply knockback away from player
                Vector direction = living.getLocation().toVector()
                    .subtract(player.getLocation().toVector())
                    .normalize();
                direction.setY(0.5);
                direction.multiply(knockback);
                living.setVelocity(direction);

                // Screen shake for hit players
                if (living instanceof Player victim) {
                    double distance = victim.getLocation().distance(player.getLocation());
                    double shakeIntensity = 0.7 * (1.0 - (distance / radius));
                    ScreenShakeUtil.shake(victim, shakeIntensity, 12);
                }

                hitCount++;
            }
        }

        // Visual effects - ground crack pattern
        for (int i = 0; i < 360; i += 15) {
            double angle = Math.toRadians(i);
            for (double r = 0.5; r <= radius; r += 0.5) {
                double x = Math.cos(angle) * r;
                double z = Math.sin(angle) * r;
                player.getWorld().spawnParticle(Particle.BLOCK,
                    player.getLocation().add(x, 0.1, z), 1,
                    player.getLocation().getBlock().getRelative(0, -1, 0).getBlockData());
            }
        }

        player.getWorld().spawnParticle(Particle.EXPLOSION,
            player.getLocation(), 3, 0.5, 0.1, 0.5, 0);
        player.getWorld().playSound(player.getLocation(),
            Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        player.getWorld().playSound(player.getLocation(),
            Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);

        debug("Ground slam hit " + hitCount + " enemies for " + damage + " damage");
        return hitCount > 0;
    }
}
