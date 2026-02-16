package com.miracle.arcanesigils.ai;

/**
 * Represents the computed state of a bind slot for AI training.
 * Used to update scoreboard objectives.
 */
public class BindState {
    
    private final boolean ready;           // All sigils in slot ready (no cooldowns)
    private final int cooldownPercent;     // Average cooldown % (0-100, 0=ready, 100=just used)
    private final int type;                // 0=empty, 25=utility, 50=defensive, 100=offensive
    
    public BindState(boolean ready, int cooldownPercent, int type) {
        this.ready = ready;
        this.cooldownPercent = Math.max(0, Math.min(100, cooldownPercent));
        this.type = type;
    }
    
    public boolean isReady() {
        return ready;
    }
    
    public int getCooldownPercent() {
        return cooldownPercent;
    }
    
    public int getType() {
        return type;
    }
    
    public int getReadyAsInt() {
        return ready ? 1 : 0;
    }
}
