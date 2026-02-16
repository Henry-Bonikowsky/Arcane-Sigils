package com.miracle.arcanesigils.interception;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.AttributeModifierManager;
import com.miracle.arcanesigils.effects.PotionEffectTracker;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Interceptor that reduces negative effects by a percentage based on Ancient Crown tier.
 * Provides passive immunity to debuffs for the wearer.
 *
 * Counter-modifier approach:
 * - Allows potion effects through but applies counter-modifiers to offset the impact
 * - For Slowness: applies +movement speed to counter the slowdown
 * - For Weakness: applies +attack damage to counter the damage reduction
 * - Counter-modifiers are tracked and removed when effects expire
 *
 * Tier scaling:
 * - T1: 20% immunity
 * - T2: 40% immunity
 * - T3: 60% immunity
 * - T4: 80% immunity
 * - T5: 100% immunity (complete block)
 */
public class AncientCrownImmunityInterceptor implements EffectInterceptor {

    private final Player wearer;
    private final double immunityPercent; // 0.0 to 1.0
    private boolean active;

    // Base effect values for potion effects (per amplifier level)
    private static final double SLOWNESS_PER_LEVEL = 0.15; // 15% speed reduction per level
    private static final double WEAKNESS_PER_LEVEL = 4.0;  // 4 attack damage reduction per level

    public AncientCrownImmunityInterceptor(Player wearer, double immunityPercent) {
        this.wearer = wearer;
        this.immunityPercent = Math.max(0.0, Math.min(1.0, immunityPercent / 100.0));
        this.active = true;
    }

    @Override
    public InterceptionResult intercept(InterceptionEvent event) {
        // Only intercept effects on the wearer
        if (!event.getTarget().equals(wearer)) {
            return InterceptionResult.PASS;
        }

        if (event.getType() == InterceptionEvent.Type.POTION_EFFECT) {
            PotionEffectType type = event.getPotionType();

            // Only affect negative potion effects
            if (isNegativeEffect(type)) {
                // At 100% immunity, block completely
                if (immunityPercent >= 1.0) {
                    event.cancel();
                    return new InterceptionResult(true);
                }

                // Apply counter-modifier to offset the effect
                applyCounterModifier(type, event.getAmplifier(), event.getDuration());
                return new InterceptionResult(true); // Effect handled (but not cancelled)
            }
        } else if (event.getType() == InterceptionEvent.Type.ATTRIBUTE_MODIFIER) {
            double value = event.getValue();

            // Only affect negative modifiers
            if (value < 0) {
                // At 100% immunity, block completely
                if (immunityPercent >= 1.0) {
                    event.cancel();
                    return new InterceptionResult(true);
                }

                // Reduce negative value by immunity percentage
                event.modifyValue(1.0 - immunityPercent);
                return new InterceptionResult(true); // Effect modified
            }
        }

        return InterceptionResult.PASS;
    }

    /**
     * Apply a counter-modifier to offset a negative potion effect.
     * The counter-modifier counters the immunity percentage of the effect.
     */
    private void applyCounterModifier(PotionEffectType type, int amplifier, int duration) {
        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
        AttributeModifierManager modManager = plugin.getAttributeModifierManager();
        PotionEffectTracker tracker = plugin.getPotionEffectTracker();

        if (modManager == null || tracker == null) return;

        // Calculate counter-modifier based on effect type
        Attribute attribute = null;
        double counterValue = 0;
        String modifierName = "ancient_crown_counter_" + type.getKey().getKey();

        if (type.equals(PotionEffectType.SLOWNESS)) {
            // Slowness: counter with +movement speed
            attribute = Attribute.MOVEMENT_SPEED;
            // Each amplifier level = 15% slow, counter with immunity% of that
            double totalSlow = SLOWNESS_PER_LEVEL * (amplifier + 1);
            counterValue = totalSlow * immunityPercent * 0.1; // Base speed is 0.1, so multiply
        } else if (type.equals(PotionEffectType.WEAKNESS)) {
            // Weakness: counter with +attack damage
            attribute = Attribute.ATTACK_DAMAGE;
            // Each amplifier level = 4 damage reduction, counter with immunity% of that
            double totalWeakness = WEAKNESS_PER_LEVEL * (amplifier + 1);
            counterValue = totalWeakness * immunityPercent;
        }

        if (attribute != null && counterValue > 0) {
            // Convert duration from ticks to seconds
            int durationSeconds = duration / 20;

            // Apply the counter-modifier
            modManager.setNamedModifier(
                wearer,
                attribute,
                modifierName,
                counterValue,
                AttributeModifier.Operation.ADD_NUMBER,
                durationSeconds
            );

            // Track it for cleanup when effect ends
            tracker.trackEffect(wearer, type, modifierName);
        }
    }

    private boolean isNegativeEffect(PotionEffectType type) {
        // List of negative potion effects
        return type == PotionEffectType.SLOWNESS ||
               type == PotionEffectType.MINING_FATIGUE ||
               type == PotionEffectType.INSTANT_DAMAGE ||
               type == PotionEffectType.NAUSEA ||
               type == PotionEffectType.BLINDNESS ||
               type == PotionEffectType.HUNGER ||
               type == PotionEffectType.WEAKNESS ||
               type == PotionEffectType.POISON ||
               type == PotionEffectType.WITHER ||
               type == PotionEffectType.LEVITATION ||
               type == PotionEffectType.UNLUCK ||
               type == PotionEffectType.DARKNESS;
    }

    @Override
    public int getPriority() {
        return 1; // Standard priority
    }

    @Override
    public boolean isActive() {
        return active && wearer.isOnline() && wearer.isValid();
    }

    public void deactivate() {
        this.active = false;
    }

    public Player getWearer() {
        return wearer;
    }
}
