package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;

/**
 * Ignite effect - sets target on fire.
 * Format: IGNITE:TICKS @Target
 */
public class IgniteEffect extends AbstractEffect {

    public IgniteEffect() {
        super("IGNITE", "Set target on fire");
    }

    @Override
    public boolean execute(EffectContext context) {
        int ticks = context.getParams() != null ? (int) context.getParams().getValue() : 100;
        ticks = Math.min(ticks, 600); // Cap at 30 seconds

        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 15.0);
        if (target == null || target == context.getPlayer()) {
            debug("Ignite failed - no valid target found");
            return false;
        }

        target.setFireTicks(ticks);

        // Effects
        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
        target.getWorld().playSound(target.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);

        debug("Ignited " + target.getName() + " for " + ticks + " ticks");
        return true;
    }
}
