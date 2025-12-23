package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import org.bukkit.potion.*;

/**
 * MAX_HEALTH_BOOST - Adds extra max health to the player.
 *
 * Parameters:
 * - hearts: Number of bonus hearts to add (e.g., 2.5, 5, 7)
 * - duration: Duration in seconds (default 20, use -1 for EFFECT_STATIC passive)
 *
 * Notes:
 * - HEALTH_BOOST potion: amplifier N gives +(N+1) hearts
 * - So amplifier 0 = +1 heart, amplifier 1 = +2 hearts, etc.
 * - Formula: amplifier = ceil(hearts) - 1
 */
public class MaxHealthBoostEffect extends AbstractEffect {

    public MaxHealthBoostEffect() {
        super("MAX_HEALTH_BOOST", "Gain extra max health temporarily");
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();

        // Read hearts parameter (HEALTH_BOOST: amplifier N gives +(N+1) hearts)
        double hearts = params != null ? params.getDouble("hearts", 2.5) : 2.5;
        int amplifier = Math.max(0, (int) Math.ceil(hearts) - 1);

        // Duration: default 20 seconds (400 ticks), refreshed by EFFECT_STATIC signal
        int duration = params != null ? params.getInt("duration", 20) : 20;
        int ticks = duration <= 0 ? Integer.MAX_VALUE : duration * 20;

        context.getPlayer().addPotionEffect(
            new PotionEffect(PotionEffectType.HEALTH_BOOST, ticks, amplifier, false, false)
        );

        debug("Applied HEALTH_BOOST to " + context.getPlayer().getName() +
              ": " + hearts + " hearts (amplifier " + amplifier + ") for " + duration + " seconds");
        return true;
    }
}
