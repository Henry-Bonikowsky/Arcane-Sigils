package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Applies a mark with a damage multiplier to a target.
 * Used for effects like Cleopatra's damage amplification and King's Brace damage reduction.
 */
public class ApplyDamageMarkEffect extends AbstractEffect {

    public ApplyDamageMarkEffect() {
        super("APPLY_DAMAGE_MARK", "Apply mark with damage multiplier");
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

        getPlugin().getMarkManager().applyMark(
            target, markName, duration, null, owner, damageMultiplier
        );

        debug(String.format("Applied mark %s to %s: multiplier=%.3f, duration=%.1fs",
              markName, target.getName(), damageMultiplier, duration));

        return true;
    }
}
