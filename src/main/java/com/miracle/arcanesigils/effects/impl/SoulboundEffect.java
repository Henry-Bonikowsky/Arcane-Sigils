package com.miracle.arcanesigils.effects.impl;
import com.miracle.arcanesigils.effects.EffectContext;

public class SoulboundEffect extends AbstractEffect {
    public SoulboundEffect() { super("SOULBOUND", "Keep on death"); }
    @Override public boolean execute(EffectContext c) { c.setMetadata("soulbound", true); return true; }
}
