package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.*;

public class AngelicEffect extends AbstractEffect {
    public AngelicEffect() { super("ANGELIC", "Health boost"); }
    @Override public boolean execute(EffectContext c) { int d = (int)(c.getParams() != null ? c.getParams().getValue() * 20 : 400); int l = c.getParams() != null ? c.getParams().getAmplifier() : 1; c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, d, l)); return true; }
}
