package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Grapple effect - pull yourself toward target or location.
 * Format: GRAPPLE:SPEED @Target
 *
 * If target exists, grapple toward target.
 * Otherwise, grapple toward where player is looking.
 */
public class GrappleEffect extends AbstractEffect {

    public GrappleEffect() {
        super("GRAPPLE", "Pull yourself toward target");
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();
        double speed = context.getParams() != null ? context.getParams().getValue() : 1.5;
        speed = Math.min(speed, 4.0); // Cap speed

        Location targetLoc;
        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 30.0);

        if (target != null && target != player) {
            // Grapple toward target entity
            targetLoc = target.getLocation();
        } else {
            // Grapple toward where player is looking (up to 30 blocks)
            targetLoc = player.getTargetBlockExact(30) != null
                ? player.getTargetBlockExact(30).getLocation()
                : player.getLocation().add(player.getLocation().getDirection().multiply(30));
        }

        // Calculate direction toward target
        Vector direction = targetLoc.toVector()
            .subtract(player.getLocation().toVector())
            .normalize();

        // Apply velocity
        direction.multiply(speed);
        player.setVelocity(direction);

        // Visual effects - draw line to target
        Location start = player.getLocation().add(0, 1, 0);
        Vector step = targetLoc.toVector().subtract(start.toVector()).normalize().multiply(0.5);
        Location current = start.clone();
        double distance = start.distance(targetLoc);

        for (double d = 0; d < Math.min(distance, 30); d += 0.5) {
            player.getWorld().spawnParticle(Particle.CRIT, current, 1, 0, 0, 0, 0);
            current.add(step);
        }

        player.getWorld().playSound(player.getLocation(),
            Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 0.8f);
        player.getWorld().playSound(player.getLocation(),
            Sound.ITEM_TRIDENT_RIPTIDE_1, 0.5f, 1.2f);

        debug("Grappled toward " + (target != null ? target.getName() : "block") +
            " with speed " + speed);
        return true;
    }
}
