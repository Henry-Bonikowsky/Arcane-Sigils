package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.AttributeModifierManager;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.interception.InterceptionEvent;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Reduces the potency of incoming potion effects by a percentage while keeping visual level unchanged.
 * Uses counter-modifiers to offset attribute changes from potions.
 *
 * Format: REDUCE_POTION_POTENCY
 *
 * Params (YAML):
 *   reduction_percent: 60  # Reduce potion potency by 60% (leaves 40%)
 *
 * Example use case:
 *   POTION_EFFECT_APPLY signal with:
 *     effects:
 *       - REDUCE_POTION_POTENCY
 *     params:
 *       reduction_percent: 60
 *
 * Behavior:
 *   - For attribute-based effects (Slowness, Speed, Strength, Weakness):
 *     Applies counter-modifiers to offset the potion's attribute changes
 *   - For damage effects (Poison, Wither):
 *     No counter-modifier needed (handled by PotionDamageReductionListener)
 *   - Visual potion level stays unchanged (Slowness I stays Slowness I)
 *   - Counter-modifiers are automatically cleaned up when potion expires
 *
 * Notes:
 *   - Requires InterceptionEvent in the EffectContext
 *   - reduction_percent=60 means 60% reduction (40% potency remains)
 *   - Counter-modifiers tracked by PotionEffectTracker for cleanup
 */
public class ReducePotionPotencyEffect extends AbstractEffect {

    /**
     * Helper class to map potion effects to their attribute modifications.
     */
    private static class AttributeMapping {
        final Attribute attribute;
        final double baseValue;
        final Operation operation;

        AttributeMapping(Attribute attribute, double baseValue, Operation operation) {
            this.attribute = attribute;
            this.baseValue = baseValue;
            this.operation = operation;
        }
    }

    public ReducePotionPotencyEffect() {
        super("REDUCE_POTION_POTENCY", "Reduce incoming potion effect potency using counter-modifiers");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        // Default: 60% reduction (leaves 40%)
        params.set("reduction_percent", 60);

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        debug("[ReducePotionPotency] Effect executing...");

        EffectParams params = context.getParams();
        if (params == null) {
            debug("[ReducePotionPotency] FAILED: No params");
            return false;
        }

        // Get the interception event from context
        InterceptionEvent interceptionEvent = context.getInterceptionEvent();
        if (interceptionEvent == null) {
            debug("[ReducePotionPotency] FAILED: No InterceptionEvent in context");
            return false;
        }

        // Verify this is a potion effect event
        if (interceptionEvent.getType() != InterceptionEvent.Type.POTION_EFFECT) {
            debug("[ReducePotionPotency] FAILED: Not a POTION_EFFECT interception event");
            return false;
        }

        Player target = interceptionEvent.getTarget();
        PotionEffectType potionType = interceptionEvent.getPotionType();
        int amplifier = interceptionEvent.getAmplifier();
        int duration = interceptionEvent.getDuration();

        debug(String.format("[ReducePotionPotency] Processing: %s (amp %d, duration %d) for player %s",
            potionType.getName(), amplifier, duration, target.getName()));

        // Get reduction percentage (0-100)
        int reductionPercent = params.getInt("reduction_percent", 60);
        reductionPercent = Math.max(0, Math.min(100, reductionPercent));

        debug(String.format("[ReducePotionPotency] Reduction percent: %d%%", reductionPercent));

        // Get attribute mapping for this potion type
        AttributeMapping mapping = getAttributeMapping(potionType, amplifier);

        // If no attribute mapping (e.g., POISON, WITHER), skip counter-modifier
        // These effects are handled by PotionDamageReductionListener
        if (mapping == null) {
            debug(String.format("[ReducePotionPotency] No attribute mapping for %s - damage-based effect, handled by PotionDamageReductionListener",
                potionType.getName()));
            return true;
        }

        debug(String.format("[ReducePotionPotency] Attribute mapping found: %s (operation: %s, base: %.3f)",
            mapping.attribute.name(), mapping.operation.name(), mapping.baseValue));

        // Calculate counter-modifier value
        // Example: Slowness I (-15% speed), 60% reduction
        // Target effect = -15% * 0.4 = -6%
        // Counter value = -15% - (-6%) = +9%

        double fullEffect = mapping.baseValue;
        double targetEffect = fullEffect * (1.0 - reductionPercent / 100.0);
        double counterValue = fullEffect - targetEffect;

        debug(String.format("[ReducePotionPotency] Calculation: Full=%.3f, Target=%.3f, Counter=%.3f",
            fullEffect, targetEffect, counterValue));

        // Generate unique counter-modifier name
        String modifierName = String.format("ancient_crown_counter_%s_%d",
            potionType.getName().toLowerCase(),
            System.currentTimeMillis() % 10000);

        debug(String.format("[ReducePotionPotency] Applying counter-modifier: %s (duration: %d ticks)",
            modifierName, duration));

        // Apply counter-modifier via AttributeModifierManager
        int durationTicks = duration; // Duration is already in ticks
        getPlugin().getAttributeModifierManager().setNamedModifier(
            target,
            mapping.attribute,
            modifierName,
            counterValue,
            mapping.operation,
            durationTicks
        );

        // Track this counter-modifier for cleanup when potion expires
        getPlugin().getPotionEffectTracker().trackEffect(target, potionType, modifierName);

        LogHelper.info(String.format("[AncientCrown] Applied counter-modifier for %s (lvl %d): %.1f%% resistance",
            potionType.getName(),
            amplifier + 1,
            reductionPercent));

        debug(String.format("[ReducePotionPotency] SUCCESS: Applied counter-modifier for %s (amp %d, -%d%%)",
            potionType.getName(),
            amplifier,
            reductionPercent));

        return true;
    }

    /**
     * Maps potion effect types to their corresponding attribute modifications.
     *
     * @param potionType The potion effect type
     * @param amplifier The potion amplifier (0 = level I, 1 = level II, etc.)
     * @return The attribute mapping, or null if no attribute modifier (damage-based effects)
     */
    private AttributeMapping getAttributeMapping(PotionEffectType potionType, int amplifier) {
        // Movement speed effects
        if (potionType.equals(PotionEffectType.SLOWNESS)) {
            // Slowness: -15% per level
            // Level I (amp 0) = -15%, Level II (amp 1) = -30%, etc.
            double value = -0.15 * (amplifier + 1);
            return new AttributeMapping(Attribute.MOVEMENT_SPEED, value, Operation.ADD_SCALAR);
        }

        if (potionType.equals(PotionEffectType.SPEED)) {
            // Speed: +20% per level
            double value = 0.20 * (amplifier + 1);
            return new AttributeMapping(Attribute.MOVEMENT_SPEED, value, Operation.ADD_SCALAR);
        }

        // Attack damage effects
        if (potionType.equals(PotionEffectType.STRENGTH)) {
            // Strength: +3 damage per level (ADD_NUMBER, not scalar)
            double value = 3.0 * (amplifier + 1);
            return new AttributeMapping(Attribute.ATTACK_DAMAGE, value, Operation.ADD_NUMBER);
        }

        if (potionType.equals(PotionEffectType.WEAKNESS)) {
            // Weakness: -4 damage (flat reduction, regardless of level)
            double value = -4.0;
            return new AttributeMapping(Attribute.ATTACK_DAMAGE, value, Operation.ADD_NUMBER);
        }

        // Damage-based effects (no attribute modifier - handled by damage listener)
        if (potionType.equals(PotionEffectType.POISON) ||
            potionType.equals(PotionEffectType.WITHER)) {
            return null;
        }

        // Other effects not currently supported
        // Could add: JUMP, SLOW_FALLING, LUCK, etc.
        return null;
    }
}
