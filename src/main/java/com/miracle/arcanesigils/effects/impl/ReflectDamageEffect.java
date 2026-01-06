package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.events.SignalType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;

/**
 * Reflect Damage effect - returns a percentage of incoming damage to attacker.
 * Format: REFLECT_DAMAGE:PERCENT
 *
 * Best used on DEFENSE signal.
 */
public class ReflectDamageEffect extends AbstractEffect {

    public ReflectDamageEffect() {
        super("REFLECT_DAMAGE", "Return damage to attacker");
    }

    @Override
    public boolean execute(EffectContext context) {
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

        // Deal damage back to attacker
        attacker.damage(reflectedDamage, context.getPlayer());

        // Visual effects
        context.getPlayer().getWorld().spawnParticle(Particle.ENCHANTED_HIT,
            context.getPlayer().getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
        attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
            attacker.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.1);
        context.getPlayer().getWorld().playSound(context.getPlayer().getLocation(),
            Sound.ENCHANT_THORNS_HIT, 1.0f, 1.2f);

        debug("Reflected " + String.format("%.1f", reflectedDamage) + " damage (" +
            percent + "% of " + String.format("%.1f", incomingDamage) + ") to " + attacker.getName());
        return true;
    }
}
