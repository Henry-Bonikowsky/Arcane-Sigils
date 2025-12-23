package com.zenax.armorsets.utils;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Utility class for finding targets in front of a player.
 * Used by effects that need targets when no victim is provided (ability-style usage).
 */
public class TargetFinder {

    /**
     * Find a single entity the player is looking at.
     *
     * @param player The player
     * @param range  Maximum range to search
     * @return The target entity, or null if none found
     */
    public static LivingEntity findLookTarget(Player player, double range) {
        return findLookTarget(player, range, e -> true);
    }

    /**
     * Find a single entity the player is looking at with a filter.
     *
     * @param player The player
     * @param range  Maximum range to search
     * @param filter Additional filter for valid targets
     * @return The target entity, or null if none found
     */
    public static LivingEntity findLookTarget(Player player, double range, Predicate<LivingEntity> filter) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection().normalize();

        // Get nearby entities
        Collection<Entity> nearby = player.getWorld().getNearbyEntities(
                player.getLocation(), range, range, range);

        LivingEntity closest = null;
        double closestAngle = Double.MAX_VALUE;

        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == player) continue;
            if (!filter.test(living)) continue;

            // Check if entity is in front of player
            Vector toEntity = entity.getLocation().add(0, 1, 0).toVector()
                    .subtract(eyeLocation.toVector());
            double distance = toEntity.length();

            if (distance > range) continue;

            toEntity.normalize();

            // Calculate angle between look direction and direction to entity
            double dot = direction.dot(toEntity);
            double angle = Math.acos(Math.max(-1, Math.min(1, dot)));

            // Check if within ~30 degree cone and closer angle than previous
            if (angle < Math.toRadians(30) && angle < closestAngle) {
                // Check line of sight - don't target entities behind walls
                if (!hasLineOfSight(player, living)) continue;

                closestAngle = angle;
                closest = living;
            }
        }

        return closest;
    }

    /**
     * Check if player has line of sight to target (no blocks in the way).
     *
     * @param player The player
     * @param target The target entity
     * @return true if there's clear line of sight
     */
    public static boolean hasLineOfSight(Player player, LivingEntity target) {
        Location eyeLocation = player.getEyeLocation();
        Location targetLocation = target.getEyeLocation();

        // Use Bukkit's built-in ray trace for block collision
        var result = player.getWorld().rayTraceBlocks(
                eyeLocation,
                targetLocation.toVector().subtract(eyeLocation.toVector()).normalize(),
                eyeLocation.distance(targetLocation)
        );

        // If rayTrace returns null, there's no block in the way
        return result == null;
    }

    /**
     * Find all entities in a cone in front of the player.
     *
     * @param player   The player
     * @param range    Maximum range
     * @param angleDeg Cone angle in degrees (half-angle, e.g., 45 = 90 degree cone)
     * @return List of entities in the cone
     */
    public static List<LivingEntity> findEntitiesInCone(Player player, double range, double angleDeg) {
        return findEntitiesInCone(player, range, angleDeg, e -> true);
    }

    /**
     * Find all entities in a cone in front of the player with a filter.
     *
     * @param player   The player
     * @param range    Maximum range
     * @param angleDeg Cone angle in degrees (half-angle)
     * @param filter   Additional filter for valid targets
     * @return List of entities in the cone
     */
    public static List<LivingEntity> findEntitiesInCone(Player player, double range, double angleDeg,
                                                         Predicate<LivingEntity> filter) {
        List<LivingEntity> targets = new ArrayList<>();
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection().normalize();
        double maxAngle = Math.toRadians(angleDeg);

        Collection<Entity> nearby = player.getWorld().getNearbyEntities(
                player.getLocation(), range, range, range);

        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == player) continue;
            if (!filter.test(living)) continue;

            Vector toEntity = entity.getLocation().add(0, 1, 0).toVector()
                    .subtract(eyeLocation.toVector());
            double distance = toEntity.length();

            if (distance > range) continue;

            toEntity.normalize();
            double dot = direction.dot(toEntity);
            double angle = Math.acos(Math.max(-1, Math.min(1, dot)));

            if (angle <= maxAngle) {
                targets.add(living);
            }
        }

        // Sort by distance (closest first)
        targets.sort(Comparator.comparingDouble(e ->
                e.getLocation().distanceSquared(player.getLocation())));

        return targets;
    }

    /**
     * Find all entities in a line in front of the player.
     *
     * @param player The player
     * @param range  Maximum range
     * @param width  Width of the line (radius from center)
     * @return List of entities in the line
     */
    public static List<LivingEntity> findEntitiesInLine(Player player, double range, double width) {
        return findEntitiesInLine(player, range, width, e -> true);
    }

    /**
     * Find all entities in a line in front of the player with a filter.
     *
     * @param player The player
     * @param range  Maximum range
     * @param width  Width of the line (radius from center)
     * @param filter Additional filter for valid targets
     * @return List of entities in the line
     */
    public static List<LivingEntity> findEntitiesInLine(Player player, double range, double width,
                                                         Predicate<LivingEntity> filter) {
        List<LivingEntity> targets = new ArrayList<>();
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();

        Collection<Entity> nearby = player.getWorld().getNearbyEntities(
                player.getLocation(), range, range, range);

        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == player) continue;
            if (!filter.test(living)) continue;

            // Calculate perpendicular distance from entity to the line
            Vector toEntity = entity.getLocation().add(0, 1, 0).toVector()
                    .subtract(start.toVector());

            // Project toEntity onto direction to get the point on the line
            double projection = toEntity.dot(direction);

            // Must be in front of player and within range
            if (projection < 0 || projection > range) continue;

            // Calculate perpendicular distance
            Vector pointOnLine = direction.clone().multiply(projection);
            double perpDistance = toEntity.clone().subtract(pointOnLine).length();

            if (perpDistance <= width) {
                targets.add(living);
            }
        }

        // Sort by distance (closest first)
        targets.sort(Comparator.comparingDouble(e ->
                e.getLocation().distanceSquared(player.getLocation())));

        return targets;
    }

    /**
     * Find entities in a radius around a location.
     *
     * @param location Center location
     * @param radius   Search radius
     * @param exclude  Entity to exclude (usually the player)
     * @return List of living entities in radius
     */
    public static List<LivingEntity> findEntitiesInRadius(Location location, double radius, Entity exclude) {
        return findEntitiesInRadius(location, radius, exclude, e -> true);
    }

    /**
     * Find entities in a radius around a location with a filter.
     *
     * @param location Center location
     * @param radius   Search radius
     * @param exclude  Entity to exclude
     * @param filter   Additional filter
     * @return List of living entities in radius
     */
    public static List<LivingEntity> findEntitiesInRadius(Location location, double radius,
                                                           Entity exclude, Predicate<LivingEntity> filter) {
        List<LivingEntity> targets = new ArrayList<>();

        Collection<Entity> nearby = location.getWorld().getNearbyEntities(
                location, radius, radius, radius);

        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == exclude) continue;
            if (!filter.test(living)) continue;

            if (living.getLocation().distanceSquared(location) <= radius * radius) {
                targets.add(living);
            }
        }

        return targets;
    }

    /**
     * Check if an entity is a valid combat target (not the same team, etc.)
     * This is a basic check - can be extended for faction plugins, etc.
     */
    public static boolean isValidTarget(Player attacker, LivingEntity target) {
        if (target == attacker) return false;

        // If target is a player, check for PvP restrictions could go here
        // For now, all living entities are valid targets

        return true;
    }
}
