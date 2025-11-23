package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;

public class UnbreakableEffect extends AbstractEffect {
    public UnbreakableEffect() { super("UNBREAKABLE", "Prevents durability loss"); }

    @Override
    public boolean execute(EffectContext context) {
        context.setMetadata("unbreakable", true);
        return true;
    }
}
