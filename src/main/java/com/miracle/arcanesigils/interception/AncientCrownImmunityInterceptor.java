package com.miracle.arcanesigils.interception;

import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

/**
 * Interceptor that blocks negative effects by chance based on Ancient Crown tier.
 * Provides passive immunity to debuffs for the wearer.
 *
 * Chance-based blocking:
 * - Potion effects: Roll dice, either block completely or let through at full strength
 * - Attribute modifiers: Proportional reduction (not chance-based)
 *
 * Tier scaling:
 * - T1: 20% chance to block
 * - T2: 40% chance to block
 * - T3: 60% chance to block
 * - T4: 80% chance to block
 * - T5: 100% chance to block (always blocks)
 */
public class AncientCrownImmunityInterceptor implements EffectInterceptor {

    private final Player wearer;
    private double immunityPercent; // 0.0 to 1.0 (mutable for tier updates)
    private boolean active;
    private final Random random = new Random();

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
                // Roll dice for chance-based blocking
                double roll = random.nextDouble(); // 0.0 to 1.0

                if (roll < immunityPercent) {
                    // Block completely
                    event.cancel();
                    LogHelper.info("Ancient Crown blocked " + type.getKey().getKey() +
                                   " (" + String.format("%.0f%%", immunityPercent * 100) + " chance) for " + wearer.getName());
                    return new InterceptionResult(true);
                } else {
                    // Failed roll - let effect through at full strength
                    LogHelper.info("Ancient Crown failed to block " + type.getKey().getKey() +
                                   " (rolled " + String.format("%.1f%%", roll * 100) +
                                   " vs " + String.format("%.0f%%", immunityPercent * 100) + ") for " + wearer.getName());
                    return InterceptionResult.PASS;
                }
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

    /**
     * Get the current immunity percentage (0.0 to 1.0).
     */
    public double getImmunityPercent() {
        return immunityPercent;
    }

    /**
     * Update the immunity percentage (for tier changes).
     * @param newPercent The new percentage (0-100, will be converted to 0.0-1.0)
     */
    public void setImmunityPercent(double newPercent) {
        this.immunityPercent = Math.max(0.0, Math.min(1.0, newPercent / 100.0));
        LogHelper.info("[AncientCrown] Updated immunity for " + wearer.getName() + " to " + (immunityPercent * 100) + "%");
    }
}
