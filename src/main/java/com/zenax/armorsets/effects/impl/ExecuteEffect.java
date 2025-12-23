package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;

/**
 * Execute effect - deals bonus damage to low health targets.
 * Format: EXECUTE:THRESHOLD:BONUS_DAMAGE @Target
 *
 * - threshold: Health percentage below which execute triggers (default 30%)
 * - bonus_damage: Extra damage dealt when executing (default 10)
 */
public class ExecuteEffect extends AbstractEffect {

    public ExecuteEffect() {
        super("EXECUTE", "Bonus damage to low health targets");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // EXECUTE:threshold:bonus_damage - supports both positional and key=value
        params.setValue(30.0);
        params.set("bonusDamage", 10.0);

        int positionalIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    switch (key) {
                        case "threshold", "value" -> params.setValue(parseDouble(value, 30.0));
                        case "bonus_damage", "bonusdamage", "damage" -> params.set("bonusDamage", parseDouble(value, 10.0));
                    }
                }
            } else {
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.setValue(parseDouble(part, 30.0));
                    case 2 -> params.set("bonusDamage", parseDouble(part, 10.0));
                }
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        double threshold = context.getParams() != null ? context.getParams().getValue() : 30.0;
        double bonusDamage = context.getParams() != null ?
            ((Number) context.getParams().get("bonusDamage", 10.0)).doubleValue() : 10.0;

        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 10.0);
        if (target == null || target == context.getPlayer()) {
            debug("Execute failed - no valid target found");
            return false;
        }

        // Calculate target's health percentage
        double maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double currentHealth = target.getHealth();
        double healthPercent = (currentHealth / maxHealth) * 100;

        if (healthPercent <= threshold) {
            // Execute! Deal bonus damage
            target.damage(bonusDamage, context.getPlayer());

            // Visual effects
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);

            debug("Executed " + target.getName() + " (at " + String.format("%.1f", healthPercent) +
                "% health) for " + bonusDamage + " bonus damage");
            return true;
        }

        debug("Execute failed - " + target.getName() + " health " +
            String.format("%.1f", healthPercent) + "% > " + threshold + "% threshold");
        return false;
    }
}
