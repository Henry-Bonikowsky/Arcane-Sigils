package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.effects.PotionEffectTracker;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Strips defensive buffs from a target, removing protection layers.
 * 
 * Format: REMOVE_BUFFS @Target
 * 
 * Removes:
 * - All saturation (set to 0)
 * - RESISTANCE potion effects
 * - REGENERATION potion effects
 * - Damage reduction attribute modifiers
 * 
 * Example: REMOVE_BUFFS @Victim
 */
public class RemoveBuffsEffect extends AbstractEffect {

    private static final String MODIFIER_PREFIX = "arcane_sigils";

    public RemoveBuffsEffect() {
        super("REMOVE_BUFFS", "Strip all defensive buffs from target");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);
        
        // Default to targeting victim
        if (params.getTarget() == null) {
            params.setTarget("@Victim");
        }
        
        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        LivingEntity target = getTarget(context);

        if (target == null) {
            debug("REMOVE_BUFFS requires a target");
            return false;
        }

        int removedCount = 0;

        // 1. Remove saturation (players only)
        if (target instanceof Player player) {
            if (player.getSaturation() > 0) {
                player.setSaturation(0);
                removedCount++;
                debug("Removed saturation from " + player.getName());
            }

            // 1b. Remove absorption hearts (golden apple hearts)
            if (player.getAbsorptionAmount() > 0) {
                player.setAbsorptionAmount(0);
                removedCount++;
                debug("Removed absorption from " + player.getName());
            }
        }

        // Get tracker for storing effects (for restoration after suppression)
        PotionEffectTracker tracker = ArmorSetsPlugin.getInstance().getPotionEffectTracker();

        // 2. Remove RESISTANCE potion effects (store for restoration)
        if (target.hasPotionEffect(PotionEffectType.RESISTANCE)) {
            PotionEffect resistance = target.getPotionEffect(PotionEffectType.RESISTANCE);
            if (resistance != null && tracker != null && target instanceof Player p) {
                tracker.storeSuppressedEffect(p, resistance);
                debug("Stored RESISTANCE (dur=" + resistance.getDuration() + ", amp=" + resistance.getAmplifier() + ") for restoration");
            }
            target.removePotionEffect(PotionEffectType.RESISTANCE);
            removedCount++;
            debug("Removed RESISTANCE from " + target.getName());
        }

        // 3. Remove REGENERATION potion effects (store for restoration)
        if (target.hasPotionEffect(PotionEffectType.REGENERATION)) {
            PotionEffect regen = target.getPotionEffect(PotionEffectType.REGENERATION);
            if (regen != null && tracker != null && target instanceof Player p) {
                tracker.storeSuppressedEffect(p, regen);
                debug("Stored REGENERATION (dur=" + regen.getDuration() + ", amp=" + regen.getAmplifier() + ") for restoration");
            }
            target.removePotionEffect(PotionEffectType.REGENERATION);
            removedCount++;
            debug("Removed REGENERATION from " + target.getName());
        }

        // 4. Remove damage reduction attribute modifiers
        AttributeInstance armorAttr = target.getAttribute(Attribute.ARMOR);
        if (armorAttr != null) {
            List<AttributeModifier> toRemove = new ArrayList<>();
            for (AttributeModifier mod : armorAttr.getModifiers()) {
                // Remove modifiers from this plugin (arcane_sigils prefix)
                if (mod.getKey().getKey().startsWith(MODIFIER_PREFIX) && mod.getAmount() > 0) {
                    toRemove.add(mod);
                }
            }
            for (AttributeModifier mod : toRemove) {
                armorAttr.removeModifier(mod);
                removedCount++;
                debug("Removed armor modifier: " + mod.getKey().getKey());
            }
        }

        // Check armor toughness too
        AttributeInstance toughnessAttr = target.getAttribute(Attribute.ARMOR_TOUGHNESS);
        if (toughnessAttr != null) {
            List<AttributeModifier> toRemove = new ArrayList<>();
            for (AttributeModifier mod : toughnessAttr.getModifiers()) {
                if (mod.getKey().getKey().startsWith(MODIFIER_PREFIX) && mod.getAmount() > 0) {
                    toRemove.add(mod);
                }
            }
            for (AttributeModifier mod : toRemove) {
                toughnessAttr.removeModifier(mod);
                removedCount++;
                debug("Removed toughness modifier: " + mod.getKey().getKey());
            }
        }

        // Visual and audio feedback
        if (removedCount > 0) {
            // Dark purple particles - debuff visual
            target.getWorld().spawnParticle(
                Particle.DUST,
                target.getLocation().add(0, 1, 0),
                30,
                0.5, 0.8, 0.5,
                0.1,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(75, 0, 130), 1.2f)
            );

            // Smoke particles rising up
            target.getWorld().spawnParticle(
                Particle.SMOKE,
                target.getLocation().add(0, 0.5, 0),
                15,
                0.3, 0.5, 0.3,
                0.05
            );

            // Sound effect - breaking/shattering
            target.getWorld().playSound(
                target.getLocation(),
                Sound.BLOCK_GLASS_BREAK,
                0.8f,
                0.8f
            );

            debug("Removed " + removedCount + " buffs from " + target.getName());
            return true;
        }

        debug("No buffs to remove from " + target.getName());
        return false;
    }
}
