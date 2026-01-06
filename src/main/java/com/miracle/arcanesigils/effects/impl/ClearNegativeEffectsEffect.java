package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class ClearNegativeEffectsEffect extends AbstractEffect {

    public ClearNegativeEffectsEffect() {
        super("CLEAR_NEGATIVE_EFFECTS", "Remove all negative potion effects");
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.WITHER);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.HUNGER);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        debug("Cleared negative effects from " + player.getName());
        return true;
    }
}
