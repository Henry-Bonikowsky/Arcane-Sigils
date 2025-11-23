package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.*;

public class FeatherweightEffect extends AbstractEffect {
    public FeatherweightEffect() { super("FEATHERWEIGHT", "Slow fall"); }
    @Override public boolean execute(EffectContext c) { int d = (int)(c.getParams() != null ? c.getParams().getValue() * 20 : 200); if(d<=0)d=200; c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, d, 0)); return true; }
}
