package com.miracle.arcanesigils.effects.impl;
import com.miracle.arcanesigils.effects.EffectContext;

public class EnlightenedEffect extends AbstractEffect {
    public EnlightenedEffect() { super("ENLIGHTENED", "XP boost"); }
    @Override public boolean execute(EffectContext c) { c.getPlayer().giveExp((int)(c.getParams() != null ? c.getParams().getValue() : 10)); return true; }
}
