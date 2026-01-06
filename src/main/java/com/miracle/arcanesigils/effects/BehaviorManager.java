package com.miracle.arcanesigils.effects;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.flow.FlowExecutor;
import com.miracle.arcanesigils.events.SignalType;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for behavior sigils on spawned entities/displays/blocks/auras.
 * Tracks spawned things and fires appropriate signals when events occur.
 *
 * Supports OWNER_ATTACK and OWNER_DEFEND signals for behaviors that react
 * to what the owner does (e.g., pull enemies when owner attacks).
 */
public class BehaviorManager implements Listener {

    private final ArmorSetsPlugin plugin;

    // Track spawned entities -> their behavior context
    private final Map<UUID, BehaviorContext> trackedEntities = new ConcurrentHashMap<>();

    // Track placed blocks -> their behavior context (key = "x,y,z,world")
    private final Map<String, BehaviorContext> trackedBlocks = new ConcurrentHashMap<>();

    // Track auras by owner UUID -> list of behavior contexts (an owner can have multiple auras)
    private final Map<UUID, java.util.List<BehaviorContext>> ownerAuras = new ConcurrentHashMap<>();

    // Tick task for passive effects and proximity checks
    private BukkitTask tickTask;

    // Proximity check radius (configurable later)
    private static final double PROXIMITY_RADIUS = 3.0;

    public BehaviorManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    /**
     * Context for a tracked behavior - stores the behavior sigil and owner info.
     */
    public static class BehaviorContext {
        private final Sigil behavior;
        private final UUID ownerUUID;
        private final long expireTime;
        private final Entity entity;
        private final Location blockLocation;
        private final double proximityRadius;
        private UUID auraId; // For aura tracking

        // For entities
        public BehaviorContext(Sigil behavior, UUID ownerUUID, Entity entity, int durationSeconds) {
            this.behavior = behavior;
            this.ownerUUID = ownerUUID;
            this.entity = entity;
            this.blockLocation = null;
            this.expireTime = durationSeconds > 0 ? System.currentTimeMillis() + (durationSeconds * 1000L) : -1;
            this.proximityRadius = PROXIMITY_RADIUS;
        }

        // For blocks/auras
        public BehaviorContext(Sigil behavior, UUID ownerUUID, Location location, int durationSeconds, double proximityRadius) {
            this.behavior = behavior;
            this.ownerUUID = ownerUUID;
            this.entity = null;
            this.blockLocation = location;
            this.expireTime = durationSeconds > 0 ? System.currentTimeMillis() + (durationSeconds * 1000L) : -1;
            this.proximityRadius = proximityRadius;
        }

        public Sigil getBehavior() {
            return behavior;
        }

        public UUID getOwnerUUID() {
            return ownerUUID;
        }

        public Entity getEntity() {
            return entity;
        }

        public Location getBlockLocation() {
            return blockLocation;
        }

        public boolean isExpired() {
            return expireTime > 0 && System.currentTimeMillis() >= expireTime;
        }

        public double getProximityRadius() {
            return proximityRadius;
        }

        public UUID getAuraId() {
            return auraId;
        }

        public void setAuraId(UUID auraId) {
            this.auraId = auraId;
        }
    }

    /**
     * Register a spawned entity with a behavior sigil.
     *
     * @param entity          The spawned entity
     * @param behavior        The behavior sigil defining its actions
     * @param ownerUUID       UUID of the player who spawned it
     * @param durationSeconds How long the entity should exist (-1 for permanent)
     */
    public void registerEntity(Entity entity, Sigil behavior, UUID ownerUUID, int durationSeconds) {
        if (entity == null || behavior == null) return;

        BehaviorContext context = new BehaviorContext(behavior, ownerUUID, entity, durationSeconds);
        trackedEntities.put(entity.getUniqueId(), context);

        LogHelper.debug("[BehaviorManager] Registered entity %s with behavior: %s (duration: %ds)",
            entity.getType().name(), behavior.getId(), durationSeconds);
    }

    /**
     * Register placed blocks with a behavior sigil.
     *
     * @param location        Center location of the placed blocks
     * @param behavior        The behavior sigil defining actions
     * @param ownerUUID       UUID of the player who placed them
     * @param durationSeconds How long blocks should exist
     * @param radius          Proximity radius for effects
     */
    public void registerBlocks(Location location, Sigil behavior, UUID ownerUUID, int durationSeconds, double radius) {
        if (location == null || behavior == null) return;

        String key = getLocationKey(location);
        BehaviorContext context = new BehaviorContext(behavior, ownerUUID, location, durationSeconds, radius);
        trackedBlocks.put(key, context);

        plugin.getLogger().fine("Registered blocks at " + key + " with behavior: " + behavior.getId());
    }

    /**
     * Register an aura's behavior with its owner.
     * This allows the behavior to receive OWNER_ATTACK and OWNER_DEFEND signals.
     *
     * @param auraId          UUID of the aura (for cleanup)
     * @param behavior        The behavior sigil
     * @param ownerUUID       The owner's UUID
     * @param location        The aura's location
     * @param durationSeconds How long the aura lasts
     * @param radius          The aura's radius
     */
    public void registerAura(UUID auraId, Sigil behavior, UUID ownerUUID, Location location, int durationSeconds, double radius) {
        if (behavior == null || ownerUUID == null) return;

        BehaviorContext context = new BehaviorContext(behavior, ownerUUID, location, durationSeconds, radius);
        context.setAuraId(auraId);

        ownerAuras.computeIfAbsent(ownerUUID, k -> new java.util.ArrayList<>()).add(context);

        LogHelper.debug("[BehaviorManager] Registered aura behavior '%s' for owner %s",
            behavior.getId(), ownerUUID.toString().substring(0, 8));
    }

    /**
     * Unregister an aura by ID.
     */
    public void unregisterAura(UUID auraId) {
        for (java.util.List<BehaviorContext> auras : ownerAuras.values()) {
            auras.removeIf(ctx -> auraId.equals(ctx.getAuraId()));
        }
    }

    /**
     * Unregister all auras for an owner.
     */
    public void unregisterOwnerAuras(UUID ownerUUID) {
        ownerAuras.remove(ownerUUID);
    }

    /**
     * Unregister an entity (call when entity is removed).
     */
    public void unregisterEntity(UUID entityUUID) {
        trackedEntities.remove(entityUUID);
    }

    /**
     * Unregister blocks at a location.
     */
    public void unregisterBlocks(Location location) {
        trackedBlocks.remove(getLocationKey(location));
    }

    /**
     * Check if an entity is tracked by this manager.
     */
    public boolean isTrackedEntity(UUID entityUUID) {
        return trackedEntities.containsKey(entityUUID);
    }

    /**
     * Get the behavior context for an entity.
     */
    public BehaviorContext getEntityContext(UUID entityUUID) {
        return trackedEntities.get(entityUUID);
    }

    /**
     * Generate a key string for a location.
     */
    private String getLocationKey(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "," + loc.getWorld().getName();
    }

    /**
     * Start the tick task for passive effects and proximity checks.
     */
    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                processTick();
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second
    }

    /**
     * Process tick - check expirations, passive effects, and proximity.
     */
    private void processTick() {
        // Process entities
        trackedEntities.entrySet().removeIf(entry -> {
            BehaviorContext ctx = entry.getValue();
            Entity entity = ctx.getEntity();

            // Check expiration
            if (ctx.isExpired()) {
                fireBehaviorSignal(ctx, SignalType.EXPIRE, null);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
                return true; // Remove from map
            }

            // Fire TICK signal for passive effects
            fireBehaviorSignal(ctx, SignalType.TICK, null);

            // Check for nearby players (PLAYER_NEAR and PLAYER_STAND)
            // This enables display entities (like quicksand) to react to players
            if (entity != null && entity.isValid() && ctx.getBehavior() != null) {
                Location entityLoc = entity.getLocation();
                if (entityLoc.getWorld() != null) {
                    double radius = ctx.getProximityRadius();
                    for (Player nearby : entityLoc.getWorld().getPlayers()) {
                        // Skip the owner - don't trigger on self
                        if (nearby.getUniqueId().equals(ctx.getOwnerUUID())) {
                            continue;
                        }

                        double distSq = nearby.getLocation().distanceSquared(entityLoc);
                        if (distSq <= radius * radius) {
                            LogHelper.debug("[BehaviorManager] Player %s within %.1f of entity %s (behavior: %s)",
                                nearby.getName(), Math.sqrt(distSq), entity.getType().name(), ctx.getBehavior().getId());
                            fireBehaviorSignal(ctx, SignalType.PLAYER_NEAR, nearby);

                            // Check if standing on entity (within 1 block vertically and 1.5 blocks XZ)
                            // This is more generous for display entities than for blocks
                            Location playerLoc = nearby.getLocation();
                            double yDiff = playerLoc.getY() - entityLoc.getY();
                            if (yDiff >= -0.5 && yDiff <= 2.0 &&
                                Math.abs(playerLoc.getX() - entityLoc.getX()) < 1.5 &&
                                Math.abs(playerLoc.getZ() - entityLoc.getZ()) < 1.5) {
                                LogHelper.debug("[BehaviorManager] PLAYER_STAND - %s standing on %s",
                                    nearby.getName(), entity.getType().name());
                                fireBehaviorSignal(ctx, SignalType.PLAYER_STAND, nearby);
                            }
                        }
                    }
                }
            }

            return false;
        });

        // Process blocks
        trackedBlocks.entrySet().removeIf(entry -> {
            BehaviorContext ctx = entry.getValue();

            // Check expiration
            if (ctx.isExpired()) {
                fireBehaviorSignal(ctx, SignalType.EXPIRE, null);
                // Block cleanup should be handled by PlaceBlocksEffect
                return true;
            }

            // Fire TICK signal
            fireBehaviorSignal(ctx, SignalType.TICK, null);

            // Check for nearby players (PLAYER_NEAR and PLAYER_STAND)
            // Exclude the owner - they shouldn't trigger their own traps
            Location loc = ctx.getBlockLocation();
            if (loc != null && loc.getWorld() != null) {
                for (Player nearby : loc.getWorld().getPlayers()) {
                    // Skip the owner - don't trigger on self
                    if (nearby.getUniqueId().equals(ctx.getOwnerUUID())) {
                        continue;
                    }
                    if (nearby.getLocation().distanceSquared(loc) <= ctx.getProximityRadius() * ctx.getProximityRadius()) {
                        fireBehaviorSignal(ctx, SignalType.PLAYER_NEAR, nearby);

                        // Check if standing on block (within 0.5 blocks Y and 1 block XZ)
                        Location playerLoc = nearby.getLocation();
                        if (Math.abs(playerLoc.getY() - loc.getY() - 1) < 0.5 &&
                            Math.abs(playerLoc.getX() - loc.getX()) < 1 &&
                            Math.abs(playerLoc.getZ() - loc.getZ()) < 1) {
                            fireBehaviorSignal(ctx, SignalType.PLAYER_STAND, nearby);
                        }
                    }
                }
            }

            return false;
        });
    }

    // === Event Handlers ===

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        // Check if a tracked entity is attacking
        BehaviorContext attackerContext = trackedEntities.get(damager.getUniqueId());
        if (attackerContext != null) {
            LogHelper.debug("[BehaviorManager] Tracked entity %s (%s) attacked %s - firing ATTACK signal",
                damager.getType().name(), damager.getUniqueId().toString().substring(0, 8),
                victim instanceof Player p ? p.getName() : victim.getType().name());
            fireBehaviorSignal(attackerContext, SignalType.ATTACK, victim instanceof LivingEntity ? (LivingEntity) victim : null);
        }

        // Check if a tracked entity is being attacked
        BehaviorContext defenderContext = trackedEntities.get(victim.getUniqueId());
        if (defenderContext != null && damager instanceof LivingEntity) {
            fireBehaviorSignal(defenderContext, SignalType.DEFENSE, (LivingEntity) damager);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        BehaviorContext context = trackedEntities.get(entity.getUniqueId());

        if (context != null) {
            // Fire death signal
            fireBehaviorSignal(context, SignalType.ENTITY_DEATH, entity.getKiller());

            // Remove from tracking
            trackedEntities.remove(entity.getUniqueId());
        }
    }

    /**
     * Fire a behavior signal with the appropriate context.
     * Checks ALL flows in the behavior for matching triggers.
     *
     * @param context    The behavior context
     * @param signalType The signal type to fire
     * @param target     The target entity (victim for attacks, attacker for defense, etc.)
     */
    private void fireBehaviorSignal(BehaviorContext context, SignalType signalType, LivingEntity target) {
        Sigil behavior = context.getBehavior();
        if (behavior == null || !behavior.hasFlows()) {
            LogHelper.debug("[BehaviorManager] No behavior or flows for signal %s", signalType.getConfigKey());
            return;
        }

        // Get owner player (needed for effect context)
        Player owner = plugin.getServer().getPlayer(context.getOwnerUUID());
        if (owner == null || !owner.isOnline()) {
            // Owner offline - can't execute effects without a player context
            LogHelper.debug("[BehaviorManager] Owner offline for signal %s", signalType.getConfigKey());
            return;
        }

        LogHelper.debug("[BehaviorManager] Firing signal %s for behavior '%s', target=%s, owner=%s",
            signalType.getConfigKey(), behavior.getId(),
            target != null ? (target instanceof Player p ? p.getName() : target.getType().name()) : "none",
            owner.getName());

        // Build effect location
        Location effectLocation;
        if (context.getEntity() != null) {
            effectLocation = context.getEntity().getLocation();
        } else if (context.getBlockLocation() != null) {
            effectLocation = context.getBlockLocation();
        } else {
            return;
        }

        // Check ALL flows for matching triggers
        for (FlowConfig flowConfig : behavior.getFlows()) {
            if (flowConfig == null) continue;

            // Check if this signal type matches the flow's trigger
            String trigger = flowConfig.getTrigger();
            if (trigger == null || !trigger.equalsIgnoreCase(signalType.getConfigKey())) {
                continue; // Signal doesn't match this flow's trigger
            }

            // Check chance
            double chance = flowConfig.getChance();
            if (chance < 100) {
                if (Math.random() * 100 > chance) {
                    continue; // Failed chance roll
                }
            }

            // Build effect context
            EffectContext effectContext = EffectContext.builder(owner, signalType)
                    .victim(target)
                    .location(effectLocation)
                    .build();

            // Execute flow
            if (flowConfig.getGraph() != null) {
                FlowExecutor executor = new FlowExecutor(plugin);
                executor.execute(flowConfig.getGraph(), effectContext);
            }
        }
    }

    /**
     * Shutdown - cancel tick task and clean up.
     */
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }

        // Remove all tracked entities
        for (BehaviorContext ctx : trackedEntities.values()) {
            Entity entity = ctx.getEntity();
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        trackedEntities.clear();
        trackedBlocks.clear();
    }

    /**
     * Get count of tracked entities.
     */
    public int getTrackedEntityCount() {
        return trackedEntities.size();
    }

    /**
     * Get count of tracked block locations.
     */
    public int getTrackedBlockCount() {
        return trackedBlocks.size();
    }
}
