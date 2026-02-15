package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.List;

public class DealDamageEffect extends AbstractEffect {

    public DealDamageEffect() {
        super("DEAL_DAMAGE", "Deals direct damage to targets");
    }

    @Override
    public boolean execute(EffectContext context) {
        double damage = context.getParams() != null ? context.getParams().getValue() : 0;
        if (damage <= 0) return false;

        String targetSpec = context.getParams() != null ? context.getParams().getTarget() : "@Victim";

        // Check if @Nearby target is specified (e.g., @Nearby:10, @NearbyAllies:10, @NearbyEnemies:10)
        if (targetSpec != null && targetSpec.startsWith("@Nearby")) {
            double radius = parseNearbyRadius(targetSpec);
            if (radius > 0) {
                java.util.List<LivingEntity> nearbyEntities;
                if (targetSpec.startsWith("@NearbyAllies")) {
                    nearbyEntities = getNearbyAllies(context, radius);
                } else if (targetSpec.startsWith("@NearbyEnemies")) {
                    nearbyEntities = getNearbyEnemies(context, radius);
                } else {
                    nearbyEntities = getNearbyEntities(context, radius);
                }
                for (LivingEntity entity : nearbyEntities) {
                    entity.damage(damage, context.getPlayer());
                }
                debug("Dealt " + damage + " damage to " + nearbyEntities.size() + " entities within " + radius + " blocks");
                return true;
            }
        }

        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 15.0);
        if (target != null && target != context.getPlayer()) {
            target.damage(damage, context.getPlayer());
            debug("Dealt " + damage + " damage to " + target.getName());
            return true;
        }

        debug("Deal damage failed - no valid target found");
        return false;
    }

    private void damageEntitiesInRadius(EffectContext context, double radius, double damage) {
        Location center = context.getLocation();
        LivingEntity damager = context.getPlayer();

        for (LivingEntity entity : center.getWorld().getLivingEntities()) {
            if (entity.getLocation().distance(center) <= radius && entity != damager) {
                entity.damage(damage, damager);
            }
        }
        debug("Dealt " + damage + " damage to entities within " + radius + " blocks");
    }

    private double parseNearbyRadius(String targetSpec) {
        if (targetSpec.startsWith("@NearbyAllies:")) {
            try {
                return Double.parseDouble(targetSpec.substring(14));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (targetSpec.startsWith("@NearbyEnemies:")) {
            try {
                return Double.parseDouble(targetSpec.substring(15));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        // Parse format: @Nearby:10
        if (targetSpec.startsWith("@Nearby:")) {
            try {
                return Double.parseDouble(targetSpec.substring(8));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
