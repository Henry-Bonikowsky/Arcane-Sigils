package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class DamageBoostEffect extends AbstractEffect {

    public DamageBoostEffect() {
        super("DAMAGE_BOOST", "Increases outgoing damage by percentage");
    }

    @Override
    public boolean execute(EffectContext context) {
        double percentage = context.getParams() != null ? context.getParams().getValue() : 0;
        if (percentage <= 0) return false;

        // Get the event if available (attack triggers)
        Object eventObj = context.getBukkitEvent();
        if (eventObj instanceof EntityDamageByEntityEvent event) {
            double currentDamage = event.getDamage();
            double boost = currentDamage * (percentage / 100.0);
            double newDamage = currentDamage + boost;

            event.setDamage(newDamage);
            debug("Boosted damage from " + currentDamage + " to " + newDamage + " (" + percentage + "%)");
            return true;
        }

        debug("DAMAGE_BOOST requires an attack event to modify");
        return false;
    }
}
