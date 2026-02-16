package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.interception.InterceptionEvent;

/**
 * Reduces the amplifier of incoming potion effects by a percentage.
 * Used with the interception system for Ancient Crown's immunity.
 *
 * Format: REDUCE_POTION_AMPLIFIER
 *
 * Params (YAML):
 *   reduction_percent: 60  # Reduce potion amplifier by 60% (leaves 40%)
 *
 * Example use case:
 *   POTION_EFFECT_APPLY signal with:
 *     effects:
 *       - REDUCE_POTION_AMPLIFIER
 *     params:
 *       reduction_percent: 60
 *
 * Notes:
 *   - Works by calling InterceptionEvent.modifyAmplifier()
 *   - Requires InterceptionEvent in the EffectContext
 *   - reduction_percent=60 means 60% reduction (multiplier=0.4)
 *   - Amplifier is clamped to minimum of 0
 */
public class ReducePotionAmplifierEffect extends AbstractEffect {

    public ReducePotionAmplifierEffect() {
        super("REDUCE_POTION_AMPLIFIER", "Reduce incoming potion effect amplifier");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);
        
        // Default: 60% reduction (leaves 40%)
        params.set("reduction_percent", 60);
        
        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) {
            debug("REDUCE_POTION_AMPLIFIER requires params");
            return false;
        }

        // Get the interception event from context
        InterceptionEvent interceptionEvent = context.getInterceptionEvent();
        if (interceptionEvent == null) {
            debug("REDUCE_POTION_AMPLIFIER requires InterceptionEvent in context");
            return false;
        }

        // Verify this is a potion effect event
        if (interceptionEvent.getType() != InterceptionEvent.Type.POTION_EFFECT) {
            debug("REDUCE_POTION_AMPLIFIER can only be used with POTION_EFFECT interception events");
            return false;
        }

        // Get reduction percentage (0-100)
        int reductionPercent = params.getInt("reduction_percent", 60);
        
        // Clamp to valid range
        reductionPercent = Math.max(0, Math.min(100, reductionPercent));
        
        // Store original amplifier for logging
        int originalAmplifier = interceptionEvent.getAmplifier();
        
        // Calculate multiplier (reduction_percent=60 means leave 40%, so multiplier=0.4)
        double multiplier = (100 - reductionPercent) / 100.0;
        
        // Calculate what the new amplifier would be
        int reducedAmplifier = (int) (originalAmplifier * multiplier);
        
        // If reduction brings amplifier to 0, cancel the effect entirely (prevents even first tick)
        if (reducedAmplifier <= 0) {
            interceptionEvent.cancel();
            debug(String.format("REDUCE_POTION_AMPLIFIER: Cancelled %s (amp %d -> 0, -%d%%)",
                interceptionEvent.getPotionType().getName(),
                originalAmplifier,
                reductionPercent));
            return true;
        }
        
        // Otherwise, apply the reduced amplifier
        interceptionEvent.modifyAmplifier(multiplier);
        
        // Log if debug enabled
        debug(String.format("REDUCE_POTION_AMPLIFIER: Reduced %s amplifier from %d to %d (-%d%%)",
            interceptionEvent.getPotionType().getName(),
            originalAmplifier,
            interceptionEvent.getAmplifier(),
            reductionPercent));
        
        return true;
    }
}
