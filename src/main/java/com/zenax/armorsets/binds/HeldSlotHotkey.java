package com.zenax.armorsets.binds;

/**
 * Held slot hotkey options for activating the ability on the currently held slot.
 * Used to work around Minecraft's limitation where pressing the same slot key doesn't fire an event.
 */
public enum HeldSlotHotkey {
    SWAP_HAND("Swap Hand", "[SWAP HAND]"),
    ATTACK("Attack", "[ATTACK]"),
    USE_ITEM("Use Item", "[USE ITEM]");

    private final String displayName;
    private final String loreFormat;

    HeldSlotHotkey(String displayName, String loreFormat) {
        this.displayName = displayName;
        this.loreFormat = loreFormat;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLoreFormat() {
        return loreFormat;
    }

    public HeldSlotHotkey next() {
        HeldSlotHotkey[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
