package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.interception.InterceptionEvent;

/**
 * Reduces the value of incoming attribute modifiers by a percentage.
 * Used with the interception system for Ancient Crown's immunity.
 *
 * Format: REDUCE_ATTRIBUTE_VALUE
 *
 * Params (YAML):
 *   reduction_percent: 60  # Reduce attribute modifier by 60% (leaves 40%)
 *
 * Example use case:
 *   ATTRIBUTE_MODIFY signal with:
 *     effects:
 *       - REDUCE_ATTRIBUTE_VALUE
 *     params:
 *       reduction_percent: 60
 *
 * Notes:
 *   - Works by calling InterceptionEvent.modifyValue()
 *   - Requires InterceptionEvent in the EffectContext
 *   - reduction_percent=60 means 60% reduction (multiplier=0.4)
 *   - Example: -0.25 speed slow becomes -0.10 (60% weaker)
 */
public class ReduceAttributeValueEffect extends AbstractEffect {

    public ReduceAttributeValueEffect() {
        super("REDUCE_ATTRIBUTE_VALUE", "Reduce incoming attribute modifier value");
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
            debug("REDUCE_ATTRIBUTE_VALUE requires params");
            return false;
        }

        // Get the interception event from context
        InterceptionEvent interceptionEvent = context.getInterceptionEvent();
        if (interceptionEvent == null) {
            debug("REDUCE_ATTRIBUTE_VALUE requires InterceptionEvent in context");
            return false;
        }

        // Verify this is an attribute modifier event
        if (interceptionEvent.getType() != InterceptionEvent.Type.ATTRIBUTE_MODIFIER) {
            debug("REDUCE_ATTRIBUTE_VALUE can only be used with ATTRIBUTE_MODIFIER interception events");
            return false;
        }

        // Get reduction percentage (0-100)
        int reductionPercent = params.getInt("reduction_percent", 60);
        
        // Clamp to valid range
        reductionPercent = Math.max(0, Math.min(100, reductionPercent));
        
        // Store original value for logging
        double originalValue = interceptionEvent.getValue();
        
        // Calculate multiplier (reduction_percent=60 means leave 40%, so multiplier=0.4)
        double multiplier = (100 - reductionPercent) / 100.0;
        
        // Calculate what the new value would be
        double reducedValue = originalValue * multiplier;
        
        // If reduction brings value to negligible (< 0.001), cancel the modifier entirely
        if (Math.abs(reducedValue) < 0.001) {
            interceptionEvent.cancel();
            debug(String.format("REDUCE_ATTRIBUTE_VALUE: Cancelled %s (%.3f -> ~0, -%d%%)",
                interceptionEvent.getAttributeType().name(),
                originalValue,
                reductionPercent));
            return true;
        }
        
        // Otherwise, apply the reduced value
        interceptionEvent.modifyValue(multiplier);
        
        // Log if debug enabled
        debug(String.format("REDUCE_ATTRIBUTE_VALUE: Reduced %s modifier from %.3f to %.3f (-%d%%)",
            interceptionEvent.getAttributeType().name(),
            originalValue,
            interceptionEvent.getValue(),
            reductionPercent));
        
        return true;
    }
}
