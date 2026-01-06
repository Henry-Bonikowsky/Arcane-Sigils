package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Pull effect - pulls target toward player.
 * Format: PULL:FORCE @Target
 */
public class PullEffect extends AbstractEffect {

    public PullEffect() {
        super("PULL", "Pull target toward you");
    }

    @Override
    public boolean execute(EffectContext context) {
        double force = context.getParams() != null ? context.getParams().getValue() : 1.5;
        force = Math.min(force, 5.0); // Cap force

        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 20.0);
        if (target == null || target == context.getPlayer()) {
            debug("Pull failed - no valid target found");
            return false;
        }

        // Calculate pull direction (toward player)
        Vector direction = context.getPlayer().getLocation().toVector()
            .subtract(target.getLocation().toVector())
            .normalize();

        // Add slight upward component
        direction.setY(0.2);
        direction.multiply(force);

        target.setVelocity(direction);

        // Effects
        target.getWorld().spawnParticle(Particle.WITCH, target.getLocation(), 15, 0.3, 0.5, 0.3, 0.05);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.8f);

        debug("Pulled " + target.getName() + " with force " + force);
        return true;
    }
}
