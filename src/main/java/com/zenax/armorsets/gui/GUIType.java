package com.zenax.armorsets.gui;

/**
 * Enum representing all GUI types in the Arcane Sigils plugin.
 */
public enum GUIType {
    // Sigil GUIs
    SIGILS_MENU,
    SIGIL_EDITOR,
    SIGIL_CONFIG,
    SIGIL_PREVIEW,

    // Behavior GUIs
    BEHAVIOR_BROWSER,

    // Signal GUIs
    SIGNAL_SELECTOR,

    // Effect GUIs
    EFFECT_PARAM,
    ATTRIBUTE_SELECTOR,

    // Particle Selector GUIs
    PARTICLE_SELECTOR,
    COLOR_SELECTOR,
    ITEM_PARTICLE_SELECTOR,
    BLOCK_PARTICLE_SELECTOR,
    SOUND_SELECTOR,

    // Item Selector GUIs
    ITEM_SELECTOR,
    SOCKETABLE_SELECTOR,

    // Socket GUIs
    SOCKET,
    UNSOCKET,

    // Tier GUIs
    TIER_CONFIG,
    TIER_XP_CONFIG,
    TIER_PARAM_EDITOR,
    TIER_PARAM_SELECTOR,
    TIER_PROGRESS,
    TIER_PROGRESS_VIEWER,

    // Binds GUIs
    BINDS_HOTBAR,
    BINDS_COMMAND,
    BINDS_EDITOR,

    // Condition GUIs
    CONDITION_CONFIG,
    CONDITION_SELECTOR,
    CONDITION_PARAM,
    CONDITION_VALUE_BROWSER,
    CONDITION_NODE_SELECTOR,

    // Flow Builder GUIs
    FLOW_LIST,
    FLOW_BUILDER,
    NODE_PALETTE,
    EFFECT_NODE_BROWSER,
    NODE_CONFIG,

    // Expression Builder GUIs
    EXPRESSION_BUILDER,
    EXPRESSION_VALUE_SELECTOR,
    EXPRESSION_OPERATOR_SELECTOR
}
