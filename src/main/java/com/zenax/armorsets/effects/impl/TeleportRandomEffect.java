package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

public class TeleportRandomEffect extends AbstractEffect {

    public TeleportRandomEffect() {
        super("TELEPORT_RANDOM", "Teleports randomly within a radius");
    }

    @Override
    public boolean execute(EffectContext context) {
        double maxDistance = context.getParams() != null ? context.getParams().getValue() : 8;

        // Cap distance
        double configMax = getPlugin().getConfigManager().getMainConfig()
                .getDouble("effects.max-teleport-distance", 50);
        maxDistance = Math.min(maxDistance, configMax);

        LivingEntity target = getTarget(context);
        if (target == null) return false;

        Location currentLoc = target.getLocation();
        Location newLoc = findSafeLocation(currentLoc, maxDistance);

        if (newLoc != null) {
            // Play effects at old location
            currentLoc.getWorld().spawnParticle(Particle.PORTAL, currentLoc, 50);
            currentLoc.getWorld().playSound(currentLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            // Teleport
            target.teleport(newLoc);

            // Play effects at new location
            newLoc.getWorld().spawnParticle(Particle.PORTAL, newLoc, 50);
            newLoc.getWorld().playSound(newLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            debug("Teleported " + target.getName() + " " + maxDistance + " blocks randomly");
            return true;
        }

        return false;
    }

    private Location findSafeLocation(Location origin, double maxDistance) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempts = 0; attempts < 20; attempts++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * maxDistance;

            double x = origin.getX() + distance * Math.cos(angle);
            double z = origin.getZ() + distance * Math.sin(angle);

            Location checkLoc = new Location(origin.getWorld(), x, origin.getY(), z);
            checkLoc.setYaw(origin.getYaw());
            checkLoc.setPitch(origin.getPitch());

            // Find safe Y level
            Location safeLoc = findSafeY(checkLoc);
            if (safeLoc != null) {
                return safeLoc;
            }
        }

        return null;
    }

    private Location findSafeY(Location loc) {
        Location check = loc.clone();

        // Search up and down for safe location
        for (int yOffset = 0; yOffset <= 10; yOffset++) {
            // Check above
            check.setY(loc.getY() + yOffset);
            if (isSafeLocation(check)) {
                return check;
            }

            // Check below
            if (yOffset > 0) {
                check.setY(loc.getY() - yOffset);
                if (isSafeLocation(check) && check.getY() > check.getWorld().getMinHeight()) {
                    return check;
                }
            }
        }

        return null;
    }

    private boolean isSafeLocation(Location loc) {
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        return feet.isPassable() &&
               head.isPassable() &&
               ground.getType().isSolid() &&
               ground.getType() != Material.LAVA &&
               feet.getType() != Material.LAVA &&
               head.getType() != Material.LAVA;
    }
}
