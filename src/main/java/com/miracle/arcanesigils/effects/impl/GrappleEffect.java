package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Location;
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

        debug("Grappled toward " + (target != null ? target.getName() : "block") +
            " with speed " + speed);
        return true;
    }
}
