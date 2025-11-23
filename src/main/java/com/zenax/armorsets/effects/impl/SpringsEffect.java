package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.potion.*;

public class SpringsEffect extends AbstractEffect {
    public SpringsEffect() { super("SPRINGS", "Jump boost"); }
    @Override public boolean execute(EffectContext c) { int l = (int)(c.getParams() != null ? c.getParams().getValue() : 2); int d = c.getParams() != null && c.getParams().getDuration() > 0 ? c.getParams().getDuration() * 20 : 200; c.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, d, l-1)); return true; }
}
