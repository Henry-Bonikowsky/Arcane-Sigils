package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
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

        debug("Ignited " + target.getName() + " for " + ticks + " ticks");
        return true;
    }
}
