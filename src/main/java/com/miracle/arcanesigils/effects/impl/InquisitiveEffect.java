package com.miracle.arcanesigils.effects.impl;
import com.miracle.arcanesigils.effects.EffectContext;

public class InquisitiveEffect extends AbstractEffect {
    public InquisitiveEffect() { super("INQUISITIVE", "Experience gain boost"); }
    @Override public boolean execute(EffectContext c) { c.setMetadata("xp_multiplier", c.getParams() != null ? c.getParams().getValue() : 50); return true; }
}
