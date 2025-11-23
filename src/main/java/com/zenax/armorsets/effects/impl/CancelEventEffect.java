package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;

public class CancelEventEffect extends AbstractEffect {

    public CancelEventEffect() {
        super("CANCEL_EVENT", "Cancels the triggering event");
    }

    @Override
    public boolean execute(EffectContext context) {
        context.cancelEvent();
        debug("Cancelled event for " + context.getPlayer().getName());
        return true;
    }
}
