package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;

public class HealEffect extends AbstractEffect {

    public HealEffect() {
        super("HEAL", "Restores health instantly");
    }

    @Override
    public boolean execute(EffectContext context) {
        double amount = context.getParams() != null ? context.getParams().getValue() : 0;
        if (amount <= 0) return false;

        LivingEntity target = getTarget(context);
        if (target == null) return false;

        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH).getValue();
        double newHealth = Math.min(target.getHealth() + amount, maxHealth);
        target.setHealth(newHealth);
        return true;
    }
}
