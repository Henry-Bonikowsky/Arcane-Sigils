package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.*;

public class GuardiansEffect extends AbstractEffect {
    public GuardiansEffect() { super("GUARDIANS", "Protection"); }
    @Override public boolean execute(EffectContext c) { c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, (int)(c.getParams() != null ? c.getParams().getValue() * 20 : 200), 1)); return true; }
}
