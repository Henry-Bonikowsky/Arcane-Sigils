package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Launch effect - launches target into the air.
 * Format: LAUNCH:HEIGHT @Target
 */
public class LaunchEffect extends AbstractEffect {

    public LaunchEffect() {
        super("LAUNCH", "Launch target into the air");
    }

    @Override
    public boolean execute(EffectContext context) {
        double height = context.getParams() != null ? context.getParams().getValue() : 1.5;
        height = Math.min(height, 5.0); // Cap height

        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 15.0);
        if (target == null || target == context.getPlayer()) {
            debug("Launch failed - no valid target found");
            return false;
        }

        // Launch straight up
        target.setVelocity(new Vector(0, height, 0));

        // Effects
        target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 20, 0.3, 0.1, 0.3, 0.1);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 0.7f);

        debug("Launched " + target.getName() + " with height " + height);
        return true;
    }
}
