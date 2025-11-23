package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.*;

public class GearsEffect extends AbstractEffect {
    public GearsEffect() { super("GEARS", "Speed"); }
    @Override public boolean execute(EffectContext c) { int l = (int)(c.getParams() != null ? c.getParams().getValue() : 2); int d = c.getParams() != null && c.getParams().getDuration() > 0 ? c.getParams().getDuration() * 20 : 200; c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, d, l-1)); return true; }
}
