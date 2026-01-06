package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;

public class CancelEventEffect extends AbstractEffect {

    public CancelEventEffect() {
        super("CANCEL_EVENT", "Cancels the signaling event");
    }

    @Override
    public boolean execute(EffectContext context) {
        context.cancelEvent();
        debug("Cancelled event for " + context.getPlayer().getName());
        return true;
    }
}
