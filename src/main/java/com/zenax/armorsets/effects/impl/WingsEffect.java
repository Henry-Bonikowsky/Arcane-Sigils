package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.*;

public class WingsEffect extends AbstractEffect {
    public WingsEffect() { super("WINGS", "Flight"); }
    @Override public boolean execute(EffectContext c) { int d = (int)(c.getParams() != null ? c.getParams().getValue() * 20 : 100); c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, d, 0)); c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, d+100, 0)); return true; }
}
