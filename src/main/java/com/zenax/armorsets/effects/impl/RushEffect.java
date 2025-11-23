package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.*;

public class RushEffect extends AbstractEffect {
    public RushEffect() { super("RUSH", "Speed surge"); }
    @Override public boolean execute(EffectContext c) { c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, (int)(c.getParams() != null ? c.getParams().getValue() : 2))); return true; }
}
