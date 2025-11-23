package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class NightowlEffect extends AbstractEffect {
    public NightowlEffect() { super("NIGHTOWL", "Night vision"); }

    @Override
    public boolean execute(EffectContext context) {
        int duration = context.getParams() != null ? (int) context.getParams().getValue() * 20 : 400;
        context.getPlayer().addPotionEffect(
                new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0, false, false)
        );
        return true;
    }
}
