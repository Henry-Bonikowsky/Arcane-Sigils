package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;

public class EnlightenedEffect extends AbstractEffect {
    public EnlightenedEffect() { super("ENLIGHTENED", "XP boost"); }
    @Override public boolean execute(EffectContext c) { c.getPlayer().giveExp((int)(c.getParams() != null ? c.getParams().getValue() : 10)); return true; }
}
