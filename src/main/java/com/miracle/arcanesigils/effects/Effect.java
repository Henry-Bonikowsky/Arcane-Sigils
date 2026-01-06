package com.miracle.arcanesigils.effects;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Interface for all effects that can be applied by armor sets and sigils.
 */
public interface Effect {

    /**
     * Get the unique identifier for this effect type.
     * @return The effect ID (e.g., "INCREASE_DAMAGE", "HEAL", "POTION")
     */
    String getId();

    /**
     * Execute this effect.
     *
     * @param context The effect context containing all relevant information
     * @return true if the effect was successfully executed
     */
    boolean execute(EffectContext context);

    /**
     * Parse effect parameters from a string.
     * Example: "POTION:SPEED:10" -> type=SPEED, duration=10
     *
     * @param effectString The full effect string from config
     * @return Parsed parameters, or null if parsing failed
     */
    EffectParams parseParams(String effectString);

    /**
     * Get a description of this effect for display purposes.
     * @return Human-readable description
     */
    String getDescription();
}
