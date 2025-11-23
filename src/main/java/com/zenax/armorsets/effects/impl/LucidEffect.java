package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class LucidEffect extends AbstractEffect {
    public LucidEffect() { super("LUCID", "Removes negative potion effects"); }

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
        return true;
    }
}
