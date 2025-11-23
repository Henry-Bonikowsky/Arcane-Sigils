package com.zenax.armorsets.effects.impl;
import com.zenax.armorsets.effects.EffectContext;

public class DodgeEffect extends AbstractEffect {
    public DodgeEffect() { super("DODGE", "Evasion"); }
    @Override public boolean execute(EffectContext c) { c.cancelEvent(); return true; }
}
