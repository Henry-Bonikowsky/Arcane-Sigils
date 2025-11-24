package com.zenax.armorsets.gui;

/**
 * Enumeration of all GUI types in the ArmorSets plugin.
 * Each type represents a different screen or interface that players can interact with.
 */
public enum GUIType {

    // ===== CORE SOCKET/UNSOCKET GUIS =====

    /**
     * GUI for socketing a sigil into an armor piece.
     */
    SOCKET,

    /**
     * GUI for removing a socketed sigil from an armor piece.
     */
    UNSOCKET,

    // ===== BROWSER GUIS =====

    /**
     * Browser for viewing all available sigils.
     */
    FUNCTION_BROWSER,

    /**
     * Browser for viewing all available armor sets.
     */
    SET_BROWSER,

    /**
     * Paginated list of functions (legacy).
     */
    FUNCTION_LIST,

    // ===== BUILD MENU GUIS =====

    /**
     * Main build menu for creating/editing sets and sigils.
     */
    BUILD_MAIN_MENU,

    /**
     * Selector for choosing which armor slot to add triggers to.
     */
    SLOT_SELECTOR,

    /**
     * Selector for choosing which trigger type to use.
     */
    TRIGGER_SELECTOR,

    /**
     * Selector for choosing which effect to apply.
     */
    EFFECT_SELECTOR,

    /**
     * Configuration GUI for trigger settings (chance, cooldown).
     */
    TRIGGER_CONFIG,

    /**
     * General effect configuration GUI.
     */
    EFFECT_CONFIG,

    // ===== EDITOR GUIS =====

    /**
     * Editor for modifying armor set properties.
     */
    SET_EDITOR,

    /**
     * Editor for modifying sigil properties.
     */
    FUNCTION_EDITOR,

    /**
     * Viewer for set effects and triggers.
     */
    SET_EFFECTS_VIEWER,

    /**
     * Viewer for set synergies (full set bonuses).
     */
    SET_SYNERGIES_VIEWER,

    /**
     * GUI for removing triggers from a set or sigil.
     */
    TRIGGER_REMOVER,

    /**
     * GUI for creating a new synergy.
     */
    SYNERGY_CREATOR,

    /**
     * GUI for editing an existing synergy.
     */
    SYNERGY_EDITOR,

    /**
     * GUI for creating a new sigil.
     */
    SIGIL_CREATOR,

    /**
     * Viewer for effect details.
     */
    EFFECT_VIEWER,

    /**
     * Editor for item display properties (lore, name, material).
     */
    ITEM_DISPLAY_EDITOR,

    // ===== EFFECT-SPECIFIC CONFIGURATION GUIS =====

    /**
     * Configuration for value-based effects (damage amount, heal amount).
     */
    EFFECT_VALUE_CONFIG,

    /**
     * Configuration for particle effects (type, count, spread).
     */
    EFFECT_PARTICLE_CONFIG,

    /**
     * Configuration for sound effects (sound type, volume, pitch).
     */
    EFFECT_SOUND_CONFIG,

    /**
     * Configuration for potion effects (type, duration, amplifier).
     */
    EFFECT_POTION_CONFIG,

    /**
     * Configuration for message effects (message text, type).
     */
    EFFECT_MESSAGE_CONFIG,

    /**
     * Configuration for teleport effects (distance, facing).
     */
    EFFECT_TELEPORT_CONFIG,

    // ===== UTILITY GUIS =====

    /**
     * Confirmation dialog for destructive actions.
     */
    CONFIRMATION,

    /**
     * Generic GUI type for custom implementations.
     */
    GENERIC;

    /**
     * Check if this GUI type is a browser view.
     */
    public boolean isBrowser() {
        return this == FUNCTION_BROWSER || this == SET_BROWSER || this == FUNCTION_LIST;
    }

    /**
     * Check if this GUI type is an editor view.
     */
    public boolean isEditor() {
        return this == SET_EDITOR || this == FUNCTION_EDITOR ||
               this == SYNERGY_EDITOR || this == ITEM_DISPLAY_EDITOR;
    }

    /**
     * Check if this GUI type is a configuration view.
     */
    public boolean isConfig() {
        return this == TRIGGER_CONFIG || this == EFFECT_CONFIG ||
               this == EFFECT_VALUE_CONFIG || this == EFFECT_PARTICLE_CONFIG ||
               this == EFFECT_SOUND_CONFIG || this == EFFECT_POTION_CONFIG ||
               this == EFFECT_MESSAGE_CONFIG || this == EFFECT_TELEPORT_CONFIG;
    }

    /**
     * Check if this GUI type is a selector view.
     */
    public boolean isSelector() {
        return this == SLOT_SELECTOR || this == TRIGGER_SELECTOR || this == EFFECT_SELECTOR;
    }

    /**
     * Check if this GUI type supports pagination.
     */
    public boolean supportsPagination() {
        return isBrowser();
    }
}
