package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class AquaEffect extends AbstractEffect {
    public AquaEffect() { super("AQUA", "Water breathing and movement"); }

    @Override
    public boolean execute(EffectContext context) {
        int duration = context.getParams() != null ? (int) context.getParams().getValue() * 20 : 400;
        Player player = context.getPlayer();
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, duration, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, duration, 0, false, false));
        return true;
    }
}
