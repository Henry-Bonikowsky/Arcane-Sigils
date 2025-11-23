package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.*;

public class MomentumEffect extends AbstractEffect {
    public MomentumEffect() { super("MOMENTUM", "Speed boost"); }
    @Override public boolean execute(EffectContext c) { c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, (int)(c.getParams() != null ? c.getParams().getValue() : 1) - 1)); return true; }
}
