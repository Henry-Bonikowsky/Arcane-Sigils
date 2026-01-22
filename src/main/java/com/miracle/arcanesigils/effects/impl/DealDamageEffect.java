package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public class DealDamageEffect extends AbstractEffect {

    public DealDamageEffect() {
        super("DEAL_DAMAGE", "Deals direct damage to targets");
    }

    @Override
    public boolean execute(EffectContext context) {
        double damage = context.getParams() != null ? context.getParams().getValue() : 0;
        if (damage <= 0) return false;

        String targetSpec = context.getParams() != null ? context.getParams().getTarget() : "@Victim";

        // Check if @Nearby:X format is specified (e.g., @Nearby:10)
        if (targetSpec != null && targetSpec.startsWith("@Nearby:")) {
            double radius = parseNearbyRadius(targetSpec);
            if (radius > 0) {
                damageEntitiesInRadius(context, radius, damage);
                return true;
            }
        }

        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 15.0);
        if (target != null && target != context.getPlayer()) {
            target.damage(damage, context.getPlayer());
            debug("Dealt " + damage + " damage to " + target.getName());
            
            // AI Training: Accumulate total damage dealt
            Double currentTotal = context.getVariable("aiTraining_totalDamage");
            double newTotal = (currentTotal != null ? currentTotal : 0.0) + damage;
            context.setVariable("aiTraining_totalDamage", newTotal);
            
            return true;
        }

        debug("Deal damage failed - no valid target found");
        return false;
    }

    private void damageEntitiesInRadius(EffectContext context, double radius, double damage) {
        Location center = context.getLocation();
        LivingEntity damager = context.getPlayer();

        int hitCount = 0;
        for (LivingEntity entity : center.getWorld().getLivingEntities()) {
            if (entity.getLocation().distance(center) <= radius && entity != damager) {
                entity.damage(damage, damager);
                hitCount++;
            }
        }
        
        // AI Training: Accumulate total damage dealt (damage * hitCount)
        if (hitCount > 0) {
            Double currentTotal = context.getVariable("aiTraining_totalDamage");
            double newTotal = (currentTotal != null ? currentTotal : 0.0) + (damage * hitCount);
            context.setVariable("aiTraining_totalDamage", newTotal);
        }
        
        debug("Dealt " + damage + " damage to entities within " + radius + " blocks");
    }

    private double parseNearbyRadius(String targetSpec) {
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
