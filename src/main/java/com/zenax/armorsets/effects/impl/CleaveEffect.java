package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Cleave effect - deals damage to all nearby enemies.
 * Format: CLEAVE:DAMAGE:RADIUS
 */
public class CleaveEffect extends AbstractEffect {

    public CleaveEffect() {
        super("CLEAVE", "Damage all nearby enemies");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // CLEAVE:damage:radius - supports both positional and key=value
        params.setValue(3.0);
        params.set("radius", 3.0);

        int positionalIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    switch (key) {
                        case "damage", "value" -> params.setValue(parseDouble(value, 3.0));
                        case "radius" -> params.set("radius", parseDouble(value, 3.0));
                    }
                }
            } else {
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.setValue(parseDouble(part, 3.0));
                    case 2 -> params.set("radius", parseDouble(part, 3.0));
                }
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();
        double damage = context.getParams() != null ? context.getParams().getValue() : 3.0;
        double radius = context.getParams() != null ?
            ((Number) context.getParams().get("radius", 3.0)).doubleValue() : 3.0;

        // Cap values
        damage = Math.min(damage, 20.0);
        radius = Math.min(radius, 10.0);

        // Get nearby entities
        Collection<Entity> nearby = player.getWorld().getNearbyEntities(
            player.getLocation(), radius, radius, radius);

        int hitCount = 0;
        for (Entity entity : nearby) {
            if (entity instanceof LivingEntity living && entity != player) {
                living.damage(damage, player);
                hitCount++;
            }
        }

        if (hitCount > 0) {
            // Visual effects
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                player.getLocation().add(0, 1, 0), 5, radius / 2, 0.5, radius / 2, 0);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);

            debug("Cleave hit " + hitCount + " enemies for " + damage + " damage");
            return true;
        }

        return false;
    }
}
