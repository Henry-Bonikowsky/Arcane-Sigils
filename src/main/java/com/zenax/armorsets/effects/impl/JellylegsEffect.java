package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;

public class JellylegsEffect extends AbstractEffect {
    public JellylegsEffect() { super("JELLYLEGS", "No fall damage"); }
    @Override public boolean execute(EffectContext c) { c.cancelEvent(); return true; }
}
