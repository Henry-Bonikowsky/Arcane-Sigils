package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Applies a universal damage mark (DAMAGE_AMPLIFICATION or DAMAGE_REDUCTION).
 * Supports multi-source stacking where multiple sigils can contribute to the same mark.
 * Each sigil can only contribute once (re-applying refreshes duration).
 *
 * Parameters:
 * - mark_type: "DAMAGE_AMPLIFICATION" or "DAMAGE_REDUCTION"
 * - sigil_id: Unique sigil identifier for per-sigil tracking
 * - percent: Percentage increase/decrease (10.0 = 10%)
 * - duration: Duration in seconds
 * - target: Target entity (@Victim, @Self, etc.)
 */
public class ApplyUniversalMarkEffect extends AbstractEffect {

    public ApplyUniversalMarkEffect() {
        super("APPLY_UNIVERSAL_MARK", "Apply universal damage mark (amplification or reduction)");
    }

    @Override
    public boolean execute(EffectContext context) {
        // Get parameters
        String markType = context.getParams().getString("mark_type", "DAMAGE_AMPLIFICATION");
        String sigilId = context.getParams().getString("sigil_id", "unknown");
        double percent = context.getParams().getDouble("percent", 10.0);
        double duration = context.getParams().getDouble("duration", 5.0);

        LivingEntity target = getTarget(context);
        if (target == null) {
            debug("No target for APPLY_UNIVERSAL_MARK");
            return false;
        }

        Player owner = context.getPlayer();

        // Convert percent to multiplier based on mark type
        double multiplier;
        if (markType.equals("DAMAGE_AMPLIFICATION")) {
            multiplier = 1.0 + (percent / 100.0); // +20% -> 1.20
        } else if (markType.equals("DAMAGE_REDUCTION")) {
            multiplier = 1.0 - (percent / 100.0); // +20% DR -> 0.80
        } else {
            debug("Unknown mark type: " + markType);
            return false;
        }

        // Apply multi-source mark
        getPlugin().getMarkManager().applyMultiSourceMark(
            target, markType, sigilId, multiplier, duration, owner
        );

        return true;
    }
}
