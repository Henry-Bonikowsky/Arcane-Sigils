package com.miracle.arcanesigils.interception;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Interceptor that reduces negative effects by a percentage based on Ancient Crown tier.
 * Provides passive immunity to debuffs for the wearer.
 *
 * Reduces:
 * - Negative potion effects (Slowness, Poison, Weakness, etc.) by reducing amplifier
 * - Instant damage by reducing damage amount
 * - Negative attribute modifiers by reducing negative value
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

                // Reduce amplifier by immunity percentage
                event.modifyAmplifier(1.0 - immunityPercent);
                return new InterceptionResult(true); // Effect modified
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
