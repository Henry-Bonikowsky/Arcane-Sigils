package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.entity.LivingEntity;

/**
 * Reflect Damage effect - returns a percentage of incoming damage to attacker.
 * Format: REFLECT_DAMAGE:PERCENT
 *
 * Best used on DEFENSE signal.
 */
public class ReflectDamageEffect extends AbstractEffect {

    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);

    public ReflectDamageEffect() {
        super("REFLECT_DAMAGE", "Return damage to attacker");
    }

    @Override
    public boolean execute(EffectContext context) {
        // Re-entry guard: prevent infinite reflect bounce between two players
        if (REFLECTING.get()) return false;

        double percent = context.getParams() != null ? context.getParams().getValue() : 25.0;
        percent = Math.min(percent, 100.0); // Cap at 100%

        // This effect requires an attacker
        LivingEntity attacker = context.getVictim(); // On DEFENSE, victim is the attacker
        if (attacker == null) {
            debug("Reflect failed - no attacker");
            return false;
        }

        // Get incoming damage
        double incomingDamage = context.getDamage();
        if (incomingDamage <= 0) {
            debug("Reflect failed - no damage to reflect");
            return false;
        }

        // Calculate reflected damage
        double reflectedDamage = incomingDamage * (percent / 100.0);

        // Deal damage back to attacker (guarded against re-entry)
        try {
            REFLECTING.set(true);
            attacker.damage(reflectedDamage, context.getPlayer());
        } finally {
            REFLECTING.set(false);
        }

        debug("Reflected " + String.format("%.1f", reflectedDamage) + " damage (" +
            percent + "% of " + String.format("%.1f", incomingDamage) + ") to " + attacker.getName());
        return true;
    }
}
