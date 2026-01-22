package com.miracle.arcanesigils.sets;

/**
 * Tracks the active state of a set bonus for a player.
 */
public class SetBonusState {
    private final String setName;
    private final int tier;
    private final long activatedAt;

    public SetBonusState(String setName, int tier) {
        this.setName = setName;
        this.tier = tier;
        this.activatedAt = System.currentTimeMillis();
    }

    public String getSetName() {
        return setName;
    }

    public int getTier() {
        return tier;
    }

    public long getActivatedAt() {
        return activatedAt;
    }
}
