package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.combat.ModifierType;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Causes target to take amplified damage for a duration.
 * Stores the modifier in ModifierRegistry instead of a static map.
 *
 * Format: DAMAGE_AMPLIFICATION @Target
 *
 * Params (YAML):
 *   amplification_percent: 10.0  # Percentage increase (2.5-100%)
 *   duration: 5                   # Duration in seconds
 *   target: @Victim              # Who to affect
 */
public class DamageAmplificationEffect extends AbstractEffect {

    public DamageAmplificationEffect() {
        super("DAMAGE_AMPLIFICATION", "Target takes increased damage for a duration");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);
        params.set("amplification_percent", "10.0");
        params.setDuration(5);
        if (params.getTarget() == null) {
            params.setTarget("@Victim");
        }
        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) {
            debug("DAMAGE_AMPLIFICATION effect requires params");
            return false;
        }

        LivingEntity target = getTarget(context);
        if (target == null) {
            debug("DAMAGE_AMPLIFICATION requires a target");
            return false;
        }

        double amplificationPercent = params.getDouble("amplification_percent", 10.0);
        amplificationPercent = Math.max(2.5, Math.min(100.0, amplificationPercent));

        int durationSeconds = params.getDuration() > 0 ? params.getDuration() : 5;

        // Store in ModifierRegistry as a fraction (10% -> 0.10)
        String source = "dmg_amp_" + (context.getSigilId() != null ? context.getSigilId() : "unknown");
        double fraction = amplificationPercent / 100.0;
        long durationMs = durationSeconds * 1000L;

        LogHelper.debug("[DAMAGE_AMP] Applying %.1f%% (fraction=%.4f) to %s for %ds, source=%s",
                amplificationPercent, fraction, target.getName(), durationSeconds, source);

        getPlugin().getModifierRegistry().applyModifier(
                target.getUniqueId(),
                ModifierType.DAMAGE_AMPLIFICATION,
                source,
                fraction,
                durationMs
        );
        return true;
    }
}
