package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.utils.LogHelper;
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

            // INFO level logging for visibility
            LogHelper.info(String.format("[AncientCrown] Reduced %s damage: %.2f → %.2f (-%.0f%%)",
                event.getCause(), currentDamage, reducedDamage, reduction));

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
