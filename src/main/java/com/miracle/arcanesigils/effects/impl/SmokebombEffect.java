package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SmokebombEffect extends AbstractEffect {

    public SmokebombEffect() {
        super("SMOKEBOMB", "Creates a smoke cloud that blinds nearby enemies");
    }

    @Override
    public boolean execute(EffectContext context) {
        double radius = context.getParams() != null ? context.getParams().getValue() : 5;
        if (radius <= 0) radius = 5;

        // Apply blindness to nearby entities
        for (LivingEntity entity : getNearbyEntities(context, radius)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
        }

        debug("Smokebomb deployed with radius " + radius);
        return true;
    }
}
