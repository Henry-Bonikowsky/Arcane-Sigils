package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.entity.EntityDamageEvent;

public class ReduceDamageEffect extends AbstractEffect {

    public ReduceDamageEffect() {
        super("REDUCE_DAMAGE", "Reduce all incoming damage");
    }

    @Override
    public boolean execute(EffectContext context) {
        double reduction = context.getParams() != null ? context.getParams().getValue() : 10;

        if (context.getBukkitEvent() instanceof EntityDamageEvent event) {
            double currentDamage = event.getDamage();
            double reducedDamage = currentDamage * (1 - reduction / 100.0);
            reducedDamage = Math.max(0, reducedDamage);

            event.setDamage(reducedDamage);
            context.setDamage(reducedDamage);

            // Visual feedback
            context.getPlayer().getWorld().spawnParticle(
                    Particle.END_ROD,
                    context.getPlayer().getLocation().add(0, 1, 0),
                    15, 0.5, 0.5, 0.5, 0.02
            );
            context.getPlayer().playSound(
                    context.getPlayer().getLocation(),
                    Sound.ITEM_SHIELD_BLOCK,
                    0.5f, 1.5f
            );

            debug("Reduced damage from " + currentDamage + " to " + reducedDamage);
            return true;
        }

        return false;
    }
}
