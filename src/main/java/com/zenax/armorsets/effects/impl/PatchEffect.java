package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.*;

public class PatchEffect extends AbstractEffect {
    public PatchEffect() { super("PATCH", "Regen"); }
    @Override public boolean execute(EffectContext c) { int d = (int)(c.getParams() != null ? c.getParams().getValue() * 20 : 100); int l = c.getParams() != null ? c.getParams().getAmplifier() : 0; c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, d, l)); return true; }
}
