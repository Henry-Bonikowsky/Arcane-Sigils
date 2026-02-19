package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.event.entity.EntityDamageEvent;

public class ResistEffectsEffect extends AbstractEffect {

    public ResistEffectsEffect() {
        super("RESIST_EFFECTS", "Resist magic, poison, and wither damage");
    }

    @Override
    public boolean execute(EffectContext context) {
        double reduction = context.getParams() != null ? context.getParams().getValue() : 15;

        if (context.getBukkitEvent() instanceof EntityDamageEvent event) {
            if (event.getCause() == EntityDamageEvent.DamageCause.MAGIC ||
                event.getCause() == EntityDamageEvent.DamageCause.WITHER ||
                event.getCause() == EntityDamageEvent.DamageCause.POISON) {

                double currentDamage = event.getDamage();
                double reducedDamage = currentDamage * (1 - reduction / 100.0);
                event.setDamage(Math.max(0, reducedDamage));

                debug("Resisted effect damage from " + currentDamage + " to " + reducedDamage);
                return true;
            }
        }
        return false;
    }
}
