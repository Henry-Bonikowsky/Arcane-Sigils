package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.combat.ModifierType;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.entity.LivingEntity;

/**
 * Applies a universal damage modifier (DAMAGE_AMPLIFICATION or DAMAGE_REDUCTION)
 * via ModifierRegistry. Supports multi-source stacking where multiple sigils
 * can contribute to the same modifier type.
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
        super("APPLY_UNIVERSAL_MARK", "Apply universal damage modifier (amplification or reduction)");
    }

    @Override
    public boolean execute(EffectContext context) {
        String markType = context.getParams().getString("mark_type", "DAMAGE_AMPLIFICATION");
        String sigilId = context.getParams().getString("sigil_id", "unknown");
        double percent = context.getParams().getDouble("percent", 10.0);
        double duration = context.getParams().getDouble("duration", 5.0);

        // Info-level for diagnosis: show tier and computed percent
        Integer tier = context.getMetadata("sourceSigilTier", null);
        LogHelper.info("[APPLY_UNIVERSAL_MARK] %s | sigil=%s tier=%s | percent=%.4f | duration=%.1fs",
                markType, sigilId, tier, percent, duration);

        LivingEntity target = getTarget(context);
        if (target == null) {
            LogHelper.debug("[APPLY_UNIVERSAL_MARK] No target! Aborting.");
            return false;
        }

        LogHelper.debug("[APPLY_UNIVERSAL_MARK] target=%s (UUID=%s)",
                target.getName(), target.getUniqueId());

        ModifierType type;
        double fraction;
        if (markType.equals("DAMAGE_AMPLIFICATION")) {
            type = ModifierType.DAMAGE_AMPLIFICATION;
            fraction = percent / 100.0;
        } else if (markType.equals("DAMAGE_REDUCTION")) {
            type = ModifierType.DAMAGE_REDUCTION;
            fraction = percent / 100.0;
        } else {
            LogHelper.debug("[APPLY_UNIVERSAL_MARK] Unknown mark type: %s", markType);
            return false;
        }

        if (fraction <= 0.0001) {
            // Zero or negligible value — remove the modifier entirely
            LogHelper.debug("[APPLY_UNIVERSAL_MARK] Removing: type=%s, source=%s (fraction=%.4f ≈ 0)",
                    type, sigilId, fraction);
            getPlugin().getModifierRegistry().removeModifier(
                    target.getUniqueId(),
                    type,
                    sigilId
            );
            LogHelper.debug("[APPLY_UNIVERSAL_MARK] REMOVED modifier for %s", target.getName());
        } else {
            LogHelper.debug("[APPLY_UNIVERSAL_MARK] Storing: type=%s, source=%s, fraction=%.4f, durationMs=%d",
                    type, sigilId, fraction, (long) (duration * 1000));
            getPlugin().getModifierRegistry().applyModifier(
                    target.getUniqueId(),
                    type,
                    sigilId,
                    fraction,
                    (long) (duration * 1000)
            );
            LogHelper.debug("[APPLY_UNIVERSAL_MARK] SUCCESS - modifier stored for %s", target.getName());
        }
        return true;
    }
}
