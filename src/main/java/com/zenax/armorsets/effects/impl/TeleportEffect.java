package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Unified teleport effect with multiple positioning modes.
 * Format: TELEPORT:type:distance:facing @teleportee @target
 *
 * Types: RANDOM, AROUND, BEHIND
 *
 * Parameters:
 * - type: RANDOM (within radius), AROUND (around target), BEHIND (behind target) - default: RANDOM
 * - distance: teleport distance in blocks - default: 8 for RANDOM, 5 for AROUND, 2 for BEHIND
 * - facing: Direction to face after teleporting - default: ENTITY
 * - @teleportee: Who to teleport - @Self or @Victim - default: @Self
 * - @target: What/who to teleport around/behind - @Self or @Victim - default: @Self
 *
 * Facing options by type:
 *   RANDOM: KEEP (preserve current facing)
 *   AROUND: ENTITY (look at target), RANDOM (random direction), AWAY (look away from target), KEEP (preserve current)
 *   BEHIND: ENTITY (look at target), SAME (same direction as target), KEEP (preserve current)
 *
 * Examples:
 * - TELEPORT:RANDOM:8 @Self - teleport self randomly within 8 blocks
 * - TELEPORT:AROUND:5:ENTITY @Self @Victim - teleport self around victim, facing them
 * - TELEPORT:AROUND:3:RANDOM @Victim @Self - teleport victim around self, random facing
 * - TELEPORT:BEHIND:2:ENTITY @Self @Victim - teleport self behind victim, facing them
 * - TELEPORT:BEHIND:3:SAME @Victim @Victim - teleport victim behind victim, same facing
 */
public class TeleportEffect extends AbstractEffect {

    public TeleportEffect() {
        super("TELEPORT", "Teleports with various positioning modes");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // Default values
        String type = "RANDOM";
        double distance = 8;
        String facing = "KEEP";
        String teleportee = "@Self";
        String target = "@Self";

        if (parts.length >= 2) {
            type = parts[1].toUpperCase();
            // Set distance defaults based on type
            distance = switch (type) {
                case "AROUND" -> 5;
                case "BEHIND" -> 2;
                default -> 8; // RANDOM
            };
            // Set facing defaults based on type
            facing = switch (type) {
                case "AROUND", "BEHIND" -> "ENTITY";
                default -> "KEEP";
            };
        }
        if (parts.length >= 3) {
            try {
                distance = Double.parseDouble(parts[2]);
            } catch (NumberFormatException ignored) {}
        }
        if (parts.length >= 4) {
            facing = parts[3].toUpperCase();
        }

        // Extract teleportee and target from original effect string
        String[] targetParts = effectString.split("\\s+");
        for (int i = 0; i < targetParts.length; i++) {
            if (targetParts[i].startsWith("@")) {
                if (i == targetParts.length - 1) {
                    // Only one target specified - it's the teleportee
                    teleportee = targetParts[i];
                } else if (i == targetParts.length - 2) {
                    // Two targets specified - first is teleportee, second is target
                    teleportee = targetParts[i];
                    if (i + 1 < targetParts.length && targetParts[i + 1].startsWith("@")) {
                        target = targetParts[i + 1];
                    }
                }
            }
        }

        params.set("type", type);
        params.setValue(distance);
        params.set("facing", facing);
        params.set("teleportee", teleportee);
        params.set("target", target);
        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        String type = context.getParams() != null ?
            (String) context.getParams().get("type", "RANDOM") : "RANDOM";
        double distance = context.getParams() != null ? context.getParams().getValue() : 8;
        String facing = context.getParams() != null ?
            (String) context.getParams().get("facing", "KEEP") : "KEEP";
        String teleporteeStr = context.getParams() != null ?
            (String) context.getParams().get("teleportee", "@Self") : "@Self";
        String targetStr = context.getParams() != null ?
            (String) context.getParams().get("target", "@Self") : "@Self";

        // Cap distance
        double configMax = getPlugin().getConfigManager().getMainConfig()
                .getDouble("effects.max-teleport-distance", 50);
        distance = Math.min(distance, configMax);

        // Determine who to teleport
        LivingEntity teleportee = getTeleportee(context, teleporteeStr);
        if (teleportee == null) {
            debug("Could not determine teleportee");
            return false;
        }

        // Determine the target reference point
        LivingEntity targetRef = getTargetReference(context, targetStr);
        if (targetRef == null) {
            targetRef = teleportee;
        }

        Location safeLoc = null;
        String debugMessage = "";

        if ("RANDOM".equalsIgnoreCase(type)) {
            safeLoc = executeTeleportRandom(teleportee, distance, facing);
            debugMessage = "Teleported " + teleportee.getName() + " randomly " + distance + " blocks (facing: " + facing + ")";
        } else if ("AROUND".equalsIgnoreCase(type)) {
            safeLoc = executeTeleportAround(teleportee, targetRef, distance, facing);
            debugMessage = "Teleported " + teleportee.getName() + " " + distance + " blocks around " + targetRef.getName() + " (facing: " + facing + ")";
        } else if ("BEHIND".equalsIgnoreCase(type)) {
            safeLoc = executeTeleportBehind(teleportee, targetRef, distance, facing);
            debugMessage = "Teleported " + teleportee.getName() + " behind " + targetRef.getName() + " (facing: " + facing + ")";
        } else {
            debug("Unknown teleport type: " + type);
            return false;
        }

        if (safeLoc == null) {
            return false;
        }

        Location currentLoc = teleportee.getLocation();

        // Play effects at old location
        currentLoc.getWorld().spawnParticle(Particle.PORTAL, currentLoc, 50);
        currentLoc.getWorld().playSound(currentLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // Teleport
        teleportee.teleport(safeLoc);

        // Play effects at new location
        safeLoc.getWorld().spawnParticle(Particle.PORTAL, safeLoc, 50);
        safeLoc.getWorld().playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        debug(debugMessage);
        return true;
    }

    private LivingEntity getTeleportee(EffectContext context, String teleporteeStr) {
        if ("@Self".equalsIgnoreCase(teleporteeStr)) {
            return context.getPlayer();
        } else if ("@Victim".equalsIgnoreCase(teleporteeStr)) {
            return context.getVictim();
        }
        return context.getPlayer();
    }

    private LivingEntity getTargetReference(EffectContext context, String targetStr) {
        if ("@Self".equalsIgnoreCase(targetStr)) {
            return context.getPlayer();
        } else if ("@Victim".equalsIgnoreCase(targetStr)) {
            return context.getVictim();
        }
        return null;
    }

    private Location executeTeleportRandom(LivingEntity teleportee, double distance, String facing) {
        Location currentLoc = teleportee.getLocation();
        Location newLoc = findSafeLocationRandom(currentLoc, distance);
        if (newLoc == null) return null;

        // Apply facing
        applyFacingRandom(newLoc, facing, currentLoc);
        return newLoc;
    }

    private Location executeTeleportAround(LivingEntity teleportee, LivingEntity centerEntity, double distance, String facing) {
        Location centerLoc = centerEntity.getLocation();

        Location newLoc = findLocationAround(centerLoc, distance);
        if (newLoc == null) return null;

        // Apply facing
        applyFacingAround(newLoc, centerEntity, facing);
        return newLoc;
    }

    private Location executeTeleportBehind(LivingEntity teleportee, LivingEntity target, double distance, String facing) {
        // Calculate position behind target
        Location targetLoc = target.getLocation();
        Vector behindDir = targetLoc.getDirection().multiply(-1).normalize();
        Location behindLoc = targetLoc.clone().add(behindDir.multiply(distance));

        // Find safe location
        Location safeLoc = findSafeY(behindLoc);
        if (safeLoc == null) {
            // Try slightly different positions if exact behind doesn't work
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI / 4) * i; // Try 8 directions around behind
                Vector offset = behindDir.clone().rotateAroundY(angle);
                Location tryLoc = targetLoc.clone().add(offset.multiply(distance));
                safeLoc = findSafeY(tryLoc);
                if (safeLoc != null) break;
            }
        }

        if (safeLoc == null) {
            debug("Could not find safe location behind " + target.getName());
            return null;
        }

        // Apply facing
        applyFacingBehind(safeLoc, target, facing);
        return safeLoc;
    }

    private Location findSafeLocationRandom(Location origin, double maxDistance) {
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

    private Location findLocationAround(Location center, double distance) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempts = 0; attempts < 20; attempts++) {
            double angle = random.nextDouble() * 2 * Math.PI;

            double x = center.getX() + distance * Math.cos(angle);
            double z = center.getZ() + distance * Math.sin(angle);

            Location checkLoc = new Location(center.getWorld(), x, center.getY(), z);
            Location safeLoc = findSafeY(checkLoc);
            if (safeLoc != null) {
                return safeLoc;
            }
        }

        return null;
    }

    private void applyFacingRandom(Location loc, String facing, Location originalLoc) {
        switch (facing.toUpperCase()) {
            case "RANDOM" -> {
                // Random direction
                float yaw = ThreadLocalRandom.current().nextFloat() * 360 - 180;
                float pitch = ThreadLocalRandom.current().nextFloat() * 180 - 90;
                loc.setYaw(yaw);
                loc.setPitch(pitch);
            }
            case "KEEP" -> {
                // Keep current facing from original location
                loc.setYaw(originalLoc.getYaw());
                loc.setPitch(originalLoc.getPitch());
            }
            default -> {
                // Default to KEEP
                loc.setYaw(originalLoc.getYaw());
                loc.setPitch(originalLoc.getPitch());
            }
        }
    }

    private void applyFacingAround(Location loc, LivingEntity target, String facing) {
        switch (facing.toUpperCase()) {
            case "ENTITY" -> {
                // Look at the entity
                Vector direction = target.getLocation().toVector().subtract(loc.toVector());
                loc.setDirection(direction);
            }
            case "AWAY" -> {
                // Look away from the entity
                Vector direction = loc.toVector().subtract(target.getLocation().toVector());
                loc.setDirection(direction);
            }
            case "RANDOM" -> {
                // Random direction
                float yaw = ThreadLocalRandom.current().nextFloat() * 360 - 180;
                float pitch = ThreadLocalRandom.current().nextFloat() * 180 - 90;
                loc.setYaw(yaw);
                loc.setPitch(pitch);
            }
            case "KEEP" -> {
                // Keep current facing (do nothing, already copied from player)
            }
            default -> {
                // Default to looking at entity
                Vector direction = target.getLocation().toVector().subtract(loc.toVector());
                loc.setDirection(direction);
            }
        }
    }

    private void applyFacingBehind(Location loc, LivingEntity target, String facing) {
        switch (facing.toUpperCase()) {
            case "ENTITY" -> {
                // Look at the entity
                Vector direction = target.getLocation().toVector().subtract(loc.toVector());
                loc.setDirection(direction);
            }
            case "SAME" -> {
                // Face the same direction as the target
                loc.setYaw(target.getLocation().getYaw());
                loc.setPitch(target.getLocation().getPitch());
            }
            case "KEEP" -> {
                // Keep current facing (do nothing)
            }
            default -> {
                // Default to looking at entity
                Vector direction = target.getLocation().toVector().subtract(loc.toVector());
                loc.setDirection(direction);
            }
        }
    }

    private Location findSafeY(Location loc) {
        Location check = loc.clone();

        for (int yOffset = 0; yOffset <= 10; yOffset++) {
            check.setY(loc.getY() + yOffset);
            if (isSafeLocation(check)) {
                return check;
            }

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
