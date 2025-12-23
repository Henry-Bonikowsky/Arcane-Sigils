package com.zenax.armorsets.effects;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.events.SignalType;
import com.zenax.armorsets.flow.FlowConfig;
import com.zenax.armorsets.flow.FlowExecutor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages projectile display entities - handles movement, collision detection,
 * and triggering on-hit behaviors.
 */
public class ProjectileManager {

    private final ArmorSetsPlugin plugin;
    private BukkitTask tickTask;

    // Track active projectiles by display entity UUID
    private final Map<UUID, ProjectileContext> activeProjectiles = new ConcurrentHashMap<>();

    // Tick interval (2 ticks = 0.1 seconds for smooth movement)
    private static final long TICK_INTERVAL = 2L;

    public ProjectileManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    /**
     * Movement type for projectiles.
     */
    public enum MovementType {
        HOMING,      // Auto-tracks toward target
        DIRECTIONAL  // Flies in a straight line
    }

    /**
     * Context for tracking a projectile.
     */
    public static class ProjectileContext {
        final UUID displayUUID;
        final UUID ownerUUID;
        final Location spawnLocation;
        final LivingEntity homingTarget;  // null for DIRECTIONAL
        final Vector direction;           // For DIRECTIONAL mode
        final double speed;               // blocks per second
        final double hitRadius;
        final boolean pierce;
        final String onHitBehaviorId;
        final double maxDistance;
        final long expireTime;
        final MovementType movementType;
        final Set<UUID> alreadyHit;       // For pierce mode

        public ProjectileContext(UUID displayUUID, UUID ownerUUID, Location spawnLocation,
                                 LivingEntity homingTarget, Vector direction, double speed,
                                 double hitRadius, boolean pierce, String onHitBehaviorId,
                                 double maxDistance, int maxTimeSeconds, MovementType movementType) {
            this.displayUUID = displayUUID;
            this.ownerUUID = ownerUUID;
            this.spawnLocation = spawnLocation.clone();
            this.homingTarget = homingTarget;
            this.direction = direction != null ? direction.clone().normalize() : null;
            this.speed = speed;
            this.hitRadius = hitRadius;
            this.pierce = pierce;
            this.onHitBehaviorId = onHitBehaviorId;
            this.maxDistance = maxDistance;
            this.expireTime = System.currentTimeMillis() + (maxTimeSeconds * 1000L);
            this.movementType = movementType;
            this.alreadyHit = ConcurrentHashMap.newKeySet();
        }
    }

    /**
     * Register a projectile for tracking.
     *
     * @param display         The display entity
     * @param owner           The player who fired the projectile
     * @param homingTarget    Target entity for HOMING mode (null for DIRECTIONAL)
     * @param direction       Direction vector for DIRECTIONAL mode
     * @param speed           Speed in blocks per second
     * @param hitRadius       Collision detection radius
     * @param pierce          Whether to continue after hitting
     * @param onHitBehaviorId Behavior sigil ID to trigger on hit
     * @param maxDistance     Max distance before despawn
     * @param maxTimeSeconds  Max time before despawn
     * @param movementType    HOMING or DIRECTIONAL
     */
    public void registerProjectile(Display display, Player owner, LivingEntity homingTarget,
                                   Vector direction, double speed, double hitRadius, boolean pierce,
                                   String onHitBehaviorId, double maxDistance, int maxTimeSeconds,
                                   MovementType movementType) {
        if (display == null || owner == null) return;

        ProjectileContext context = new ProjectileContext(
                display.getUniqueId(),
                owner.getUniqueId(),
                display.getLocation(),
                homingTarget,
                direction,
                speed,
                hitRadius,
                pierce,
                onHitBehaviorId,
                maxDistance,
                maxTimeSeconds,
                movementType
        );

        activeProjectiles.put(display.getUniqueId(), context);
    }

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                processProjectiles();
            }
        }.runTaskTimer(plugin, TICK_INTERVAL, TICK_INTERVAL);
    }

    private void processProjectiles() {
        long now = System.currentTimeMillis();
        double tickDelta = TICK_INTERVAL / 20.0; // Seconds per tick

        activeProjectiles.entrySet().removeIf(entry -> {
            ProjectileContext ctx = entry.getValue();
            Entity entity = Bukkit.getEntity(ctx.displayUUID);

            // Remove if display no longer exists
            if (entity == null || !entity.isValid() || !(entity instanceof Display display)) {
                return true;
            }

            // Check time expiry
            if (now >= ctx.expireTime) {
                display.remove();
                return true;
            }

            // Check max distance
            Location currentLoc = display.getLocation();
            if (currentLoc.distanceSquared(ctx.spawnLocation) > ctx.maxDistance * ctx.maxDistance) {
                display.remove();
                return true;
            }

            // Calculate movement
            Vector moveVector = calculateMoveVector(ctx, currentLoc);
            if (moveVector == null) {
                // Invalid state, remove projectile
                display.remove();
                return true;
            }

            // Apply speed (blocks per tick)
            double distance = ctx.speed * tickDelta;
            moveVector.multiply(distance);

            // Move the display entity
            Location newLoc = currentLoc.add(moveVector);
            display.teleport(newLoc);

            // Collision detection
            boolean hitSomething = checkCollision(ctx, newLoc);
            if (hitSomething && !ctx.pierce) {
                display.remove();
                return true;
            }

            return false;
        });
    }

    private Vector calculateMoveVector(ProjectileContext ctx, Location currentLoc) {
        if (ctx.movementType == MovementType.HOMING) {
            // Homing: recalculate direction toward target each tick
            if (ctx.homingTarget != null && ctx.homingTarget.isValid()) {
                Location targetLoc = ctx.homingTarget.getLocation().add(0, 1, 0); // Aim at center
                return targetLoc.toVector().subtract(currentLoc.toVector()).normalize();
            } else {
                // Target gone, use last direction or forward
                return ctx.direction != null ? ctx.direction : new Vector(0, 0, 1);
            }
        } else {
            // Directional: use fixed direction
            return ctx.direction != null ? ctx.direction.clone() : new Vector(0, 0, 1);
        }
    }

    private boolean checkCollision(ProjectileContext ctx, Location location) {
        if (location.getWorld() == null) return false;

        double r = ctx.hitRadius;
        Collection<Entity> nearby = location.getWorld().getNearbyEntities(location, r, r, r);

        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living.getUniqueId().equals(ctx.ownerUUID)) continue; // Don't hit owner
            if (ctx.alreadyHit.contains(living.getUniqueId())) continue; // Already hit this one

            // Sphere collision check (check center of entity)
            Location entityCenter = living.getLocation().add(0, living.getHeight() / 2, 0);
            if (entityCenter.distanceSquared(location) > r * r) continue;

            // HIT!
            ctx.alreadyHit.add(living.getUniqueId());
            fireHitBehavior(ctx, living, location);
            return true;
        }
        return false;
    }

    private void fireHitBehavior(ProjectileContext ctx, LivingEntity hitEntity, Location hitLocation) {
        if (ctx.onHitBehaviorId == null || ctx.onHitBehaviorId.isEmpty()) return;

        Sigil hitBehavior = plugin.getSigilManager().getBehavior(ctx.onHitBehaviorId);
        if (hitBehavior == null || !hitBehavior.hasFlow()) {
            plugin.getLogger().fine("On-hit behavior not found or has no flow: " + ctx.onHitBehaviorId);
            return;
        }

        Player owner = plugin.getServer().getPlayer(ctx.ownerUUID);
        if (owner == null || !owner.isOnline()) return;

        // Get flow config
        FlowConfig flowConfig = hitBehavior.getFlow();
        if (flowConfig == null || flowConfig.getGraph() == null) return;

        // Check if trigger matches PROJECTILE_HIT
        String trigger = flowConfig.getTrigger();
        if (trigger != null && !trigger.equalsIgnoreCase(SignalType.PROJECTILE_HIT.getConfigKey())) {
            // Trigger doesn't match - still execute but log
            plugin.getLogger().fine("Behavior " + ctx.onHitBehaviorId + " has trigger " + trigger +
                    " but was called from PROJECTILE_HIT");
        }

        // Check chance
        double chance = flowConfig.getChance();
        if (chance < 100 && Math.random() * 100 > chance) {
            return;
        }

        // Build effect context with PROJECTILE_HIT signal
        EffectContext effectContext = EffectContext.builder(owner, SignalType.PROJECTILE_HIT)
                .victim(hitEntity)
                .location(hitLocation)
                .build();

        // Execute flow
        FlowExecutor executor = new FlowExecutor(plugin);
        executor.execute(flowConfig.getGraph(), effectContext);

        plugin.getLogger().fine("Fired PROJECTILE_HIT for behavior " + ctx.onHitBehaviorId +
                " on " + hitEntity.getName());
    }

    /**
     * Remove all projectiles owned by a player.
     */
    public void removePlayerProjectiles(UUID ownerUUID) {
        activeProjectiles.entrySet().removeIf(entry -> {
            if (entry.getValue().ownerUUID.equals(ownerUUID)) {
                Entity entity = Bukkit.getEntity(entry.getValue().displayUUID);
                if (entity != null) {
                    entity.remove();
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Get count of active projectiles.
     */
    public int getActiveCount() {
        return activeProjectiles.size();
    }

    /**
     * Shutdown - cancel tick task and clean up all projectiles.
     */
    public void shutdown() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }

        // Remove all projectile display entities
        for (ProjectileContext ctx : activeProjectiles.values()) {
            Entity entity = Bukkit.getEntity(ctx.displayUUID);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        activeProjectiles.clear();
    }
}
