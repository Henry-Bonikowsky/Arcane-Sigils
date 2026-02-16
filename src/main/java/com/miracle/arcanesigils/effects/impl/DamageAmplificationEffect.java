package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Causes target to take amplified damage for a duration.
 * Applied as an attribute modifier that reduces armor effectiveness.
 * 
 * Format: DAMAGE_AMPLIFICATION @Target
 * 
 * Params (YAML):
 *   amplification_percent: 10.0  # Percentage increase (2.5-20%)
 *   duration: 5                   # Duration in seconds
 *   target: @Victim              # Who to affect
 * 
 * Example: 
 *   DAMAGE_AMPLIFICATION @Victim with amplification_percent=15 means target takes 15% more damage
 * 
 * Implementation:
 *   Reduces armor value by applying negative modifier, making target more vulnerable
 */
public class DamageAmplificationEffect extends AbstractEffect {

    private static final String MODIFIER_NAME = "arcane_sigils_damage_amp";

    public DamageAmplificationEffect() {
        super("DAMAGE_AMPLIFICATION", "Target takes increased damage for a duration");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);
        
        // Defaults
        params.set("amplification_percent", "10.0"); // 10% damage increase
        params.setDuration(5); // 5 seconds
        
        // Default to targeting victim
        if (params.getTarget() == null) {
            params.setTarget("@Victim");
        }
        
        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) {
            debug("DAMAGE_AMPLIFICATION effect requires params");
            return false;
        }

        LivingEntity target = getTarget(context);
        if (target == null) {
            debug("DAMAGE_AMPLIFICATION requires a target");
            return false;
        }

        // Get amplification percentage (2.5-20%)
        double amplificationPercent = params.getDouble("amplification_percent", 10.0);
        amplificationPercent = Math.max(2.5, Math.min(20.0, amplificationPercent));
        
        // Get duration
        int durationSeconds = params.getDuration() > 0 ? params.getDuration() : 5;

        // Get the armor attribute
        AttributeInstance armorAttr = target.getAttribute(Attribute.ARMOR);
        if (armorAttr == null) {
            debug("Target has no armor attribute");
            return false;
        }

        // Calculate armor reduction
        // To make target take X% more damage, we reduce their armor by approximately that percentage
        // Convert percentage to multiplier (e.g., 15% -> -0.15)
        double armorMultiplier = -(amplificationPercent / 100.0);

        // Create unique modifier key
        UUID modifierId = UUID.randomUUID();
        NamespacedKey key = new NamespacedKey(getPlugin(), MODIFIER_NAME + "_" + modifierId.toString().substring(0, 8));

        // Create the attribute modifier using MULTIPLY_SCALAR_1
        // This multiplies the current armor value
        AttributeModifier modifier = new AttributeModifier(
            key,
            armorMultiplier,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1
        );

        // Apply the modifier
        armorAttr.addModifier(modifier);

        // Schedule removal after duration
        ArmorSetsPlugin plugin = getPlugin();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (target.isValid()) {
                armorAttr.removeModifier(modifier);
                
                // Clear visual on expiration
                target.getWorld().spawnParticle(
                    Particle.CLOUD,
                    target.getLocation().add(0, 1, 0),
                    10,
                    0.3, 0.5, 0.3,
                    0.02
                );
            }
        }, durationSeconds * 20L);

        // Visual feedback - red skull particles
        target.getWorld().spawnParticle(
            Particle.DUST,
            target.getLocation().add(0, 1, 0),
            35,
            0.5, 0.8, 0.5,
            0.1,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 1.5f)
        );

        // Small red hearts falling
        target.getWorld().spawnParticle(
            Particle.HEART,
            target.getLocation().add(0, 1.5, 0),
            8,
            0.3, 0.3, 0.3,
            0.1
        );

        // Sound effect - ominous debuff
        target.getWorld().playSound(
            target.getLocation(),
            Sound.ENTITY_WITHER_HURT,
            0.6f,
            1.2f
        );

        // Notification for player targets
        if (target instanceof Player player) {
            player.sendMessage("§c§lVULNERABLE! §7+" + String.format("%.1f", amplificationPercent) + "% damage taken");
        }

        debug(String.format("Applied %.1f%% damage amplification to %s for %d seconds", 
            amplificationPercent, target.getName(), durationSeconds));
        
        return true;
    }
}
