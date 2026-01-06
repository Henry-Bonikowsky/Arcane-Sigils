package com.miracle.arcanesigils.binds;

/**
 * The two available bind systems players can switch between.
 */
public enum BindSystem {
    HOTBAR("Hotbar", 9),      // Uses hotbar keys 1-9
    COMMAND("Command", 27);   // Uses /activatebind ID, max 27 binds (2 pages)

    private final String displayName;
    private final int maxBinds;

    BindSystem(String displayName, int maxBinds) {
        this.displayName = displayName;
        this.maxBinds = maxBinds;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxBinds() {
        return maxBinds;
    }

    public BindSystem toggle() {
        return this == HOTBAR ? COMMAND : HOTBAR;
    }
}
