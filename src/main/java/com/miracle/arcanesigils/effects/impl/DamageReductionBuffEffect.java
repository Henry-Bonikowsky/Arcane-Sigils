package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.combat.ModifierType;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.entity.LivingEntity;

/**
 * Applies a temporary flat damage reduction buff to the target.
 * Stores the modifier in ModifierRegistry instead of a static map.
 *
 * Format: DAMAGE_REDUCTION_BUFF:duration:percent @Target
 * Example: DAMAGE_REDUCTION_BUFF:5:25 (5 seconds, 25% damage reduction)
 */
public class DamageReductionBuffEffect extends AbstractEffect {

    public DamageReductionBuffEffect() {
        super("DAMAGE_REDUCTION_BUFF", "Apply temporary flat damage reduction");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        int positionalIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    switch (key) {
                        case "duration" -> params.setDuration((int) parseDouble(value, 5));
                        case "percent", "value", "reduction" -> params.setValue(parseDouble(value, 25.0));
                    }
                }
            } else {
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.setDuration((int) parseDouble(part, 5));
                    case 2 -> params.setValue(parseDouble(part, 25.0));
                }
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        LivingEntity target = getTarget(context);
        if (target == null) {
            debug("DAMAGE_REDUCTION_BUFF requires a target");
            return false;
        }

        EffectParams params = context.getParams();

        int durationSeconds = params != null && params.getDuration() > 0 ? params.getDuration() : 5;
        double percent = params != null && params.getValue() > 0 ? params.getValue() : 25.0;
        percent = Math.min(percent, 80.0);

        // Store in ModifierRegistry as a fraction (25% -> 0.25)
        String source = "dr_buff_" + (context.getSigilId() != null ? context.getSigilId() : "unknown");
        double fraction = percent / 100.0;
        long durationMs = durationSeconds * 1000L;

        LogHelper.debug("[DR_BUFF] Applying %.1f%% (fraction=%.4f) to %s for %ds, source=%s",
                percent, fraction, target.getName(), durationSeconds, source);

        getPlugin().getModifierRegistry().applyModifier(
                target.getUniqueId(),
                ModifierType.DAMAGE_REDUCTION,
                source,
                fraction,
                durationMs
        );
        return true;
    }
}
