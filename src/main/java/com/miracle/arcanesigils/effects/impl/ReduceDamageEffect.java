package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.utils.LogHelper;
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

            LogHelper.debug("[ReduceDamage] Reduced %s damage: %.2f -> %.2f (-%.0f%%)",
                event.getCause(), currentDamage, reducedDamage, reduction);

            debug("Reduced damage from " + currentDamage + " to " + reducedDamage);
            return true;
        }

        return false;
    }
}
