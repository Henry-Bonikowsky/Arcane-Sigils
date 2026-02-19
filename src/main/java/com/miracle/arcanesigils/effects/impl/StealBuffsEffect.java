package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Steal Buffs effect - steals positive potion effects from target.
 * Format: STEAL_BUFFS @Target
 */
public class StealBuffsEffect extends AbstractEffect {

    // Positive effects that can be stolen
    private static final PotionEffectType[] POSITIVE_EFFECTS = {
        PotionEffectType.SPEED,
        PotionEffectType.STRENGTH,
        PotionEffectType.REGENERATION,
        PotionEffectType.RESISTANCE,
        PotionEffectType.FIRE_RESISTANCE,
        PotionEffectType.INVISIBILITY,
        PotionEffectType.NIGHT_VISION,
        PotionEffectType.WATER_BREATHING,
        PotionEffectType.ABSORPTION,
        PotionEffectType.SATURATION,
        PotionEffectType.HASTE,
        PotionEffectType.JUMP_BOOST,
        PotionEffectType.LUCK,
        PotionEffectType.SLOW_FALLING,
        PotionEffectType.CONDUIT_POWER,
        PotionEffectType.DOLPHINS_GRACE,
        PotionEffectType.HERO_OF_THE_VILLAGE
    };

    public StealBuffsEffect() {
        super("STEAL_BUFFS", "Steal positive effects from target");
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();

        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 10.0);
        if (target == null || target == context.getPlayer()) {
            debug("Steal buffs failed - no valid target found");
            return false;
        }

        Collection<PotionEffect> targetEffects = target.getActivePotionEffects();
        List<PotionEffect> stolenEffects = new ArrayList<>();

        // Find and steal positive effects
        for (PotionEffect effect : targetEffects) {
            if (isPositiveEffect(effect.getType())) {
                stolenEffects.add(effect);
            }
        }

        if (stolenEffects.isEmpty()) {
            debug("No positive effects to steal from " + target.getName());
            return false;
        }

        // Transfer effects
        for (PotionEffect effect : stolenEffects) {
            // Remove from target
            target.removePotionEffect(effect.getType());

            // Add to player (with reduced duration)
            int newDuration = effect.getDuration() / 2; // Half duration
            player.addPotionEffect(new PotionEffect(
                effect.getType(),
                newDuration,
                effect.getAmplifier(),
                effect.isAmbient(),
                effect.hasParticles()
            ));
        }

        debug("Stole " + stolenEffects.size() + " buffs from " + target.getName());
        return true;
    }

    private boolean isPositiveEffect(PotionEffectType type) {
        for (PotionEffectType positive : POSITIVE_EFFECTS) {
            if (positive.equals(type)) {
                return true;
            }
        }
        return false;
    }
}
