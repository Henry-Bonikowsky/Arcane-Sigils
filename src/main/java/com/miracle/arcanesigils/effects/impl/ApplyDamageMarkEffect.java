package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.combat.ModifierType;
import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Applies a mark AND a damage modifier to a target.
 * The mark is a tag (for HAS_MARK conditions), and the modifier affects damage calculation.
 * Used for effects like Cleopatra's damage amplification and King's Brace damage reduction.
 */
public class ApplyDamageMarkEffect extends AbstractEffect {

    public ApplyDamageMarkEffect() {
        super("APPLY_DAMAGE_MARK", "Apply mark with damage modifier");
    }

    @Override
    public boolean execute(EffectContext context) {
        String markName = context.getParams().getString("mark_name", "DAMAGE_MARK");
        double duration = context.getParams().getDouble("duration", 5.0);
        double damageMultiplier = context.getParams().getDouble("damage_multiplier", 1.0);

        LivingEntity target = getTarget(context);
        if (target == null) {
            debug("No target for APPLY_DAMAGE_MARK");
            return false;
        }

        Player owner = context.getPlayer();
        long durationMs = (long) (duration * 1000);

        // Apply the mark tag (for HAS_MARK condition checks)
        getPlugin().getModifierRegistry().applyMark(target, markName, duration, null, owner);

        // Apply the damage modifier if not 1.0
        if (damageMultiplier != 1.0) {
            String source = markName + "_" + (context.getSigilId() != null ? context.getSigilId() : "unknown");
            if (damageMultiplier > 1.0) {
                // Amplification: convert multiplier to fraction (1.20 -> 0.20)
                getPlugin().getModifierRegistry().applyModifier(
                        target.getUniqueId(),
                        ModifierType.DAMAGE_AMPLIFICATION,
                        source,
                        damageMultiplier - 1.0,
                        durationMs
                );
            } else {
                // Reduction: convert multiplier to fraction (0.80 -> 0.20)
                getPlugin().getModifierRegistry().applyModifier(
                        target.getUniqueId(),
                        ModifierType.DAMAGE_REDUCTION,
                        source,
                        1.0 - damageMultiplier,
                        durationMs
                );
            }
        }

        debug(String.format("Applied mark %s to %s: multiplier=%.3f, duration=%.1fs",
                markName, target.getName(), damageMultiplier, duration));

        return true;
    }
}
