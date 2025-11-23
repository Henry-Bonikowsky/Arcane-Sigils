package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class IncreaseDamageEffect extends AbstractEffect {

    public IncreaseDamageEffect() {
        super("INCREASE_DAMAGE", "Increases damage dealt by a percentage");
    }

    @Override
    public boolean execute(EffectContext context) {
        double percentage = context.getParams() != null ? context.getParams().getValue() : 0;
        if (percentage <= 0) return false;

        // Cap at config max
        double maxIncrease = getPlugin().getConfigManager().getMainConfig()
                .getDouble("effects.max-damage-increase", 500);
        percentage = Math.min(percentage, maxIncrease);

        if (context.getBukkitEvent() instanceof EntityDamageByEntityEvent event) {
            double currentDamage = event.getDamage();
            double newDamage = currentDamage * (1 + percentage / 100.0);
            event.setDamage(newDamage);
            context.setDamage(newDamage);
            debug("Increased damage from " + currentDamage + " to " + newDamage);
            return true;
        }

        return false;
    }
}
