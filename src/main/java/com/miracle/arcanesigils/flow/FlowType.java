package com.miracle.arcanesigils.flow;

/**
 * Type of flow - determines how the sigil is activated.
 */
public enum FlowType {
    /**
     * SIGNAL - Flow is triggered by game events (ON_ATTACK, ON_DEFEND, etc.)
     * Has trigger, chance, and can fire automatically when conditions are met.
     */
    SIGNAL,

    /**
     * ABILITY - Flow is activated by player using a bind key.
     * No trigger or chance - player manually activates when desired.
     */
    ABILITY
}
