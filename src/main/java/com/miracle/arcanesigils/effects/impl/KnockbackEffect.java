package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Knockback effect - pushes target away from player.
 * Format: KNOCKBACK:FORCE @Target
 */
public class KnockbackEffect extends AbstractEffect {

    public KnockbackEffect() {
        super("KNOCKBACK", "Push target away");
    }

    @Override
    public boolean execute(EffectContext context) {
        double force = context.getParams() != null ? context.getParams().getValue() : 1.5;
        force = Math.min(force, 5.0); // Cap force

        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 15.0);
        if (target == null || target == context.getPlayer()) {
            debug("Knockback failed - no valid target found");
            return false;
        }

        // Calculate knockback direction (away from player)
        Vector direction = target.getLocation().toVector()
            .subtract(context.getPlayer().getLocation().toVector())
            .normalize();

        // Add upward component
        direction.setY(0.3);
        direction.multiply(force);

        target.setVelocity(direction);

        debug("Knocked back " + target.getName() + " with force " + force);
        return true;
    }
}
