package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.*;

public class RestoreEffect extends AbstractEffect {
    public RestoreEffect() { super("RESTORE", "Regen"); }
    @Override public boolean execute(EffectContext c) { int d = (int)(c.getParams() != null ? c.getParams().getValue() * 20 : 200); int l = c.getParams() != null ? c.getParams().getAmplifier() : 1; c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, d, l)); return true; }
}
