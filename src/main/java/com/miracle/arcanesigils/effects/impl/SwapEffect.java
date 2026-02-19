package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Location;
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

        // Swap positions
        player.teleport(target.getLocation());
        target.teleport(playerLoc);

        debug("Swapped positions with " + target.getName());
        return true;
    }
}
