package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Swap effect - swap positions with target.
 * Format: SWAP @Target
 */
public class SwapEffect extends AbstractEffect {

    public SwapEffect() {
        super("SWAP", "Swap positions with target");
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();

        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 15.0);
        if (target == null || target == context.getPlayer()) {
            debug("Swap failed - no valid target found");
            return false;
        }

        // Store locations
        Location playerLoc = player.getLocation().clone();
        Location targetLoc = target.getLocation().clone();

        // Preserve yaw/pitch for each entity
        playerLoc.setYaw(target.getLocation().getYaw());
        playerLoc.setPitch(target.getLocation().getPitch());
        targetLoc.setYaw(player.getLocation().getYaw());
        targetLoc.setPitch(player.getLocation().getPitch());

        // Spawn particles at original locations
        player.getWorld().spawnParticle(Particle.PORTAL, playerLoc.add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
        target.getWorld().spawnParticle(Particle.PORTAL, targetLoc.add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);

        // Swap positions
        player.teleport(target.getLocation());
        target.teleport(playerLoc);

        // Sound effects
        player.getWorld().playSound(player.getLocation(),
            Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        target.getWorld().playSound(target.getLocation(),
            Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        // Spawn particles at new locations
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
        target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);

        debug("Swapped positions with " + target.getName());
        return true;
    }
}
