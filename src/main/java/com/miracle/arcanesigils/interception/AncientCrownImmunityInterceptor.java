package com.miracle.arcanesigils.interception;

import com.miracle.arcanesigils.utils.LogHelper;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Interceptor that reduces negative effects based on Ancient Crown tier.
 * Provides passive percentage-based resistance to debuffs for the wearer.
 *
 * Crown reduces POTENCY only (not duration):
 * - Attribute modifiers: Value reduced (e.g., -0.25 speed at 60% immunity -> -0.10)
 * - Potion effects: Amplifier reduced proportionally (duration unchanged)
 * - At 100% immunity (T5): effects are blocked completely
 *
 * Tier scaling:
 * - T1: 20% reduction
 * - T2: 40% reduction
 * - T3: 60% reduction
 * - T4: 80% reduction
 * - T5: 100% reduction (complete block)
 */
public class AncientCrownImmunityInterceptor implements EffectInterceptor {

    private final Player wearer;
    private double immunityPercent; // 0.0 to 1.0 (mutable for tier updates)
    private boolean active;

    public AncientCrownImmunityInterceptor(Player wearer, double immunityPercent) {
        this.wearer = wearer;
        this.immunityPercent = Math.max(0.0, Math.min(1.0, immunityPercent / 100.0));
        this.active = true;
    }

    @Override
    public InterceptionResult intercept(InterceptionEvent event) {
        if (!event.getTarget().equals(wearer)) {
            return InterceptionResult.PASS;
        }

        if (event.getType() == InterceptionEvent.Type.ATTRIBUTE_MODIFIER) {
            return interceptAttribute(event);
        } else if (event.getType() == InterceptionEvent.Type.POTION_EFFECT) {
            return interceptPotion(event);
        }

        return InterceptionResult.PASS;
    }

    private InterceptionResult interceptAttribute(InterceptionEvent event) {
        double value = event.getValue();
        if (value >= 0) return InterceptionResult.PASS; // Only reduce negative modifiers

        String attrName = formatAttributeName(event.getAttributeType());

        if (immunityPercent >= 1.0) {
            event.cancel();
            notifyWearer("Negated " + attrName + " debuff!");
            return new InterceptionResult(true);
        }

        double remaining = 1.0 - immunityPercent;
        event.modifyValue(remaining);
        notifyWearer(String.format("Reduced %s debuff by %.0f%%", attrName, immunityPercent * 100));
        LogHelper.info(String.format("[AncientCrown] Reduced attribute modifier by %.0f%% for %s (%.3f -> %.3f)",
                immunityPercent * 100, wearer.getName(), value, event.getValue()));
        return new InterceptionResult(true);
    }

    private InterceptionResult interceptPotion(InterceptionEvent event) {
        PotionEffectType type = event.getPotionType();
        if (!isNegativeEffect(type)) return InterceptionResult.PASS;

        String effectName = formatEffectName(type);

        if (immunityPercent >= 1.0) {
            event.cancel();
            notifyWearer("Negated " + effectName + "!");
            return new InterceptionResult(true);
        }

        double remaining = 1.0 - immunityPercent;

        // Only reduce amplifier, NOT duration — crown reduces potency only
        event.modifyAmplifier(remaining);

        notifyWearer(String.format("Reduced %s by %.0f%%", effectName, immunityPercent * 100));
        LogHelper.info(String.format("[AncientCrown] Reduced %s by %.0f%% for %s (amp: %d, dur: %d)",
                type.getKey().getKey(), immunityPercent * 100, wearer.getName(),
                event.getAmplifier(), event.getDuration()));
        return new InterceptionResult(true);
    }

    private void notifyWearer(String message) {
        wearer.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gradient:#FFD700:#CD853F><bold>Ancient Crown!</bold></gradient> <gray>" + message + "</gray>"));
    }

    private String formatEffectName(PotionEffectType type) {
        String raw = type.getKey().getKey().replace("_", " ");
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String formatAttributeName(org.bukkit.attribute.Attribute attribute) {
        if (attribute == null) return "Unknown";
        String name = attribute.name();
        if (name.contains("MOVEMENT_SPEED")) return "Movement Speed";
        if (name.contains("MAX_HEALTH")) return "Max Health";
        if (name.contains("ATTACK_DAMAGE")) return "Attack Damage";
        if (name.contains("ATTACK_SPEED")) return "Attack Speed";
        if (name.contains("ARMOR_TOUGHNESS")) return "Armor Toughness";
        if (name.contains("ARMOR")) return "Armor";
        if (name.contains("KNOCKBACK")) return "Knockback Resistance";
        return name.replace("_", " ").toLowerCase();
    }

    private boolean isNegativeEffect(PotionEffectType type) {
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
        return 1;
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

    public double getImmunityPercent() {
        return immunityPercent;
    }

    public void setImmunityPercent(double newPercent) {
        this.immunityPercent = Math.max(0.0, Math.min(1.0, newPercent / 100.0));
        LogHelper.info("[AncientCrown] Updated immunity for " + wearer.getName() + " to " + (immunityPercent * 100) + "%");
    }
}
