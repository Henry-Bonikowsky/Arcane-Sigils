package com.zenax.armorsets.binds;

/**
 * Toggle hotkey options for enabling/disabling the ability selection UI.
 * These are combo keybinds that players can choose from.
 */
public enum ToggleHotkey {
    SNEAK_SWAP("Sneak + Swap Hand", "[SNEAK] + [SWAP HAND]"),
    DOUBLE_SWAP("Double Tap Swap Hand", "Double Tap [SWAP HAND]"),
    ATTACK_USE("Attack + Use Item", "[ATTACK] + [USE ITEM]"),
    SNEAK_USE("Sneak + Use Item", "[SNEAK] + [USE ITEM]"),
    SNEAK_ATTACK("Sneak + Attack", "[SNEAK] + [ATTACK]");

    private final String displayName;
    private final String loreFormat;

    ToggleHotkey(String displayName, String loreFormat) {
        this.displayName = displayName;
        this.loreFormat = loreFormat;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLoreFormat() {
        return loreFormat;
    }

    public ToggleHotkey next() {
        ToggleHotkey[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
