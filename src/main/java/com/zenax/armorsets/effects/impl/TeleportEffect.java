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

import java.util.ArrayList;
import java.util.List;
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

        // Remove ALL @ targets from end (there can be multiple like @Self @Target)
        String cleanedString = effectString.replaceAll("(\\s+@\\w+(?::\\d+)?)+$", "").trim();
        String[] parts = cleanedString.split(":");

        // Default values
        String type = "RANDOM";
        double distance = 8;
        String facing = "KEEP";
        String teleportee = "@Self";
        String target = "@Self";

        // TELEPORT:type:distance:facing - supports both positional and key=value
        int positionalIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    switch (key) {
                        case "type" -> type = value.toUpperCase();
                        case "distance", "value" -> distance = parseDouble(value, 8);
                        case "facing" -> facing = value.toUpperCase();
                        case "teleportee" -> teleportee = value;
                        case "teleporttarget", "teleport_target" -> target = value;
                    }
                }
            } else {
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> type = part.toUpperCase();
                    case 2 -> distance = parseDouble(part, 8);
                    case 3 -> facing = part.toUpperCase();
                }
            }
        }

        // Apply type-based defaults if not set
        if (positionalIndex <= 1 && distance == 8) {
            distance = switch (type) {
                case "AROUND" -> 5;
                case "BEHIND" -> 2;
                default -> 8;
            };
        }
        if (positionalIndex <= 2 && facing.equals("KEEP")) {
            facing = switch (type) {
                case "AROUND", "BEHIND" -> "ENTITY";
                default -> "KEEP";
            };
        }

        // Extract teleportee and target from original effect string
        List<String> atTargets = new ArrayList<>();
        String[] targetParts = effectString.split("\\s+");
        for (String targetPart : targetParts) {
            if (targetPart.startsWith("@")) {
                atTargets.add(targetPart);
            }
        }

        // Assign based on count
        if (atTargets.size() == 1) {
            teleportee = atTargets.get(0);
        } else if (atTargets.size() >= 2) {
            teleportee = atTargets.get(0);
            target = atTargets.get(1);
        }

        params.set("type", type);
        params.setValue(distance);
        params.set("facing", facing);
        params.set("teleportee", teleportee);
        params.set("teleportTarget", target);
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
            (String) context.getParams().get("teleportTarget", "@Target") : "@Target";

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
            LivingEntity victim = context.getVictim();
            return victim != null ? victim : context.getPlayer();
        } else if ("@Target".equalsIgnoreCase(teleporteeStr)) {
            LivingEntity target = getGlowTarget(context);
            // Fall back to victim, then self
            if (target == null) target = context.getVictim();
            return target != null ? target : context.getPlayer();
        }
        return context.getPlayer();
    }

    private LivingEntity getTargetReference(EffectContext context, String targetStr) {
        if ("@Self".equalsIgnoreCase(targetStr)) {
            return context.getPlayer();
        } else if ("@Victim".equalsIgnoreCase(targetStr)) {
            return context.getVictim();
        } else if ("@Target".equalsIgnoreCase(targetStr)) {
            LivingEntity target = getGlowTarget(context);
            // Fall back to victim if no glow target
            if (target == null) target = context.getVictim();
            return target;
        }
        return null;
    }

    /**
     * Get the glowing target entity from the TargetGlowManager.
     * Falls back to using TargetFinder if no glow target.
     */
    private LivingEntity getGlowTarget(EffectContext context) {
        if (context.getPlayer() == null) return null;

        // First try the glow manager target
        var targetGlowManager = getPlugin().getTargetGlowManager();
        if (targetGlowManager != null) {
            LivingEntity target = targetGlowManager.getTarget(context.getPlayer());
            if (target != null) return target;
        }

        // Fall back to TargetFinder (what player is looking at)
        return com.zenax.armorsets.utils.TargetFinder.findLookTarget(context.getPlayer(), 15.0);
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
        Location teleporteeLoc = teleportee.getLocation();

        Location newLoc = findLocationAround(centerLoc, distance);
        if (newLoc == null) return null;

        // Copy original yaw/pitch for KEEP mode
        newLoc.setYaw(teleporteeLoc.getYaw());
        newLoc.setPitch(teleporteeLoc.getPitch());

        // Apply facing (may override yaw/pitch)
        applyFacingAround(newLoc, centerEntity, facing, teleportee);
        return newLoc;
    }

    private Location executeTeleportBehind(LivingEntity teleportee, LivingEntity target, double distance, String facing) {
        // Calculate position behind target
        Location targetLoc = target.getLocation();
        Location teleporteeLoc = teleportee.getLocation();
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

        // Copy original yaw/pitch for KEEP mode
        safeLoc.setYaw(teleporteeLoc.getYaw());
        safeLoc.setPitch(teleporteeLoc.getPitch());

        // Apply facing (may override yaw/pitch)
        applyFacingBehind(safeLoc, target, facing, teleportee);
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

    private void applyFacingAround(Location loc, LivingEntity target, String facing, LivingEntity teleportee) {
        switch (facing.toUpperCase()) {
            case "ENTITY" -> {
                // Look at the entity's eye level from teleportee's eye level
                Location targetEye = target.getEyeLocation();
                Location fromEye = loc.clone().add(0, teleportee.getEyeHeight(), 0);
                Vector direction = targetEye.toVector().subtract(fromEye.toVector());
                loc.setDirection(direction);
            }
            case "AWAY" -> {
                // Look away from the entity (eye to eye)
                Location targetEye = target.getEyeLocation();
                Location fromEye = loc.clone().add(0, teleportee.getEyeHeight(), 0);
                Vector direction = fromEye.toVector().subtract(targetEye.toVector());
                loc.setDirection(direction);
            }
            case "RANDOM" -> {
                // Random direction (only yaw, keep pitch level)
                float yaw = ThreadLocalRandom.current().nextFloat() * 360 - 180;
                loc.setYaw(yaw);
                loc.setPitch(0);
            }
            case "KEEP" -> {
                // Keep current facing (already set before this method is called)
            }
            default -> {
                // Default to looking at entity
                Location targetEye = target.getEyeLocation();
                Location fromEye = loc.clone().add(0, teleportee.getEyeHeight(), 0);
                Vector direction = targetEye.toVector().subtract(fromEye.toVector());
                loc.setDirection(direction);
            }
        }
    }

    private void applyFacingBehind(Location loc, LivingEntity target, String facing, LivingEntity teleportee) {
        switch (facing.toUpperCase()) {
            case "ENTITY" -> {
                // Look at the entity's eye level from teleportee's eye level
                Location targetEye = target.getEyeLocation();
                Location fromEye = loc.clone().add(0, teleportee.getEyeHeight(), 0);
                Vector direction = targetEye.toVector().subtract(fromEye.toVector());
                loc.setDirection(direction);
            }
            case "SAME" -> {
                // Face the same direction as the target
                loc.setYaw(target.getLocation().getYaw());
                loc.setPitch(target.getLocation().getPitch());
            }
            case "KEEP" -> {
                // Keep current facing (already set before this method is called)
            }
            default -> {
                // Default to looking at entity
                Location targetEye = target.getEyeLocation();
                Location fromEye = loc.clone().add(0, teleportee.getEyeHeight(), 0);
                Vector direction = targetEye.toVector().subtract(fromEye.toVector());
                loc.setDirection(direction);
            }
        }
    }

    private Location findSafeY(Location loc) {
        Location check = loc.clone();

        // First, find the actual ground level by going down until we hit solid ground
        Location groundSearch = loc.clone();
        for (int y = 0; y <= 20; y++) {
            groundSearch.setY(loc.getY() - y);
            if (groundSearch.getY() <= groundSearch.getWorld().getMinHeight()) break;

            Block block = groundSearch.getBlock();
            Block below = block.getRelative(0, -1, 0);

            // Found ground: current block is passable, block below is solid
            if (block.isPassable() && below.getType().isSolid() && below.getType() != Material.LAVA) {
                // Check if this is a safe location (2 blocks of headroom)
                if (isSafeLocation(groundSearch)) {
                    // Try to spawn 1 block higher for crit attack opportunity
                    // Need 3 blocks of headroom for this (feet, head, extra block above)
                    Location elevated = groundSearch.clone().add(0, 1, 0);
                    if (isSafeLocation(elevated)) {
                        return elevated;
                    }
                    // Fall back to ground level if no headroom
                    return groundSearch;
                }
            }
        }

        // Fallback: check at original Y level first, then small offsets
        if (isSafeLocation(loc)) {
            return loc.clone();
        }

        // Check slightly above and below (max 3 blocks)
        for (int yOffset = 1; yOffset <= 3; yOffset++) {
            check.setY(loc.getY() - yOffset);
            if (check.getY() > check.getWorld().getMinHeight() && isSafeLocation(check)) {
                return check;
            }

            check.setY(loc.getY() + yOffset);
            if (isSafeLocation(check)) {
                return check;
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
