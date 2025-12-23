package com.zenax.armorsets.effects;

import com.zenax.armorsets.ArmorSetsPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active aura zones that apply effects to entities within their radius.
 * Auras are invisible zones that continuously apply potion effects to nearby entities.
 *
 * Enhanced features:
 * - followOwner: Aura follows the owner around
 * - pullOnOwnerHit: When owner is hit, pull enemies in aura toward owner
 */
public class AuraManager {

    private final ArmorSetsPlugin plugin;
    private final Map<UUID, ActiveAura> activeAuras = new ConcurrentHashMap<>();
    private BukkitRunnable tickTask;

    public AuraManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    /**
     * Starts the tick task that processes all active auras.
     */
    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, ActiveAura>> iterator = activeAuras.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<UUID, ActiveAura> entry = iterator.next();
                    ActiveAura aura = entry.getValue();

                    // Check if expired
                    if (currentTime >= aura.expiryTime) {
                        iterator.remove();
                        continue;
                    }

                    // Apply effects to entities in radius
                    processAura(aura);
                }
            }
        };
        tickTask.runTaskTimer(plugin, 10L, 10L); // Every 0.5 seconds
    }

    /**
     * Process a single aura - apply effects to entities in radius.
     */
    private void processAura(ActiveAura aura) {
        // If following owner, update location to owner's position
        if (aura.followOwner) {
            Player owner = plugin.getServer().getPlayer(aura.ownerId);
            if (owner != null && owner.isOnline()) {
                // Update to owner's location at ground level
                Location ownerLoc = owner.getLocation();
                aura.location.setX(ownerLoc.getX());
                aura.location.setY(ownerLoc.getY());
                aura.location.setZ(ownerLoc.getZ());
            }
        }

        Location center = aura.location;
        if (center.getWorld() == null) return;

        double radius = aura.radius;
        Collection<Entity> nearbyEntities = center.getWorld().getNearbyEntities(center, radius, radius, radius);

        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof LivingEntity living)) continue;

            // Check if this entity should be affected
            if (!shouldAffect(aura, living)) continue;

            // Check if actually within radius (sphere, not cube)
            if (living.getLocation().distance(center) > radius) continue;

            // Track affected entities for pull-on-hit or pull-on-attack
            if (aura.pullOnOwnerHit || aura.pullOnOwnerAttack) {
                aura.affectedEnemies.add(living.getUniqueId());
            }

            // Apply the potion effect (skip if no effect type - pull-only aura)
            if (aura.effectType != null) {
                living.addPotionEffect(new PotionEffect(
                    aura.effectType,
                    30, // Short duration, will be reapplied
                    aura.amplifier,
                    false, // ambient
                    aura.showParticles, // particles
                    true // icon
                ));
            }
        }

        // Clean up enemies no longer in range
        if (aura.pullOnOwnerHit || aura.pullOnOwnerAttack) {
            aura.affectedEnemies.removeIf(uuid -> {
                Entity entity = plugin.getServer().getEntity(uuid);
                if (entity == null || !entity.isValid()) return true;
                return entity.getLocation().distance(center) > radius;
            });
        }

        // Optional: Show particle ring around aura
        if (aura.showParticles) {
            spawnAuraParticles(center, radius);
        }
    }

    /**
     * Check if an entity should be affected by this aura.
     */
    private boolean shouldAffect(ActiveAura aura, LivingEntity entity) {
        boolean isOwner = entity.getUniqueId().equals(aura.ownerId);
        boolean isPlayer = entity instanceof Player;
        String entityName = entity instanceof Player p ? p.getName() : entity.getType().name();

        boolean result = switch (aura.affects) {
            case ENEMIES -> !isOwner;
            case ALLIES -> isOwner;
            case ALL -> true;
            case PLAYERS_ONLY -> isPlayer;
            case ENEMIES_PLAYERS_ONLY -> !isOwner && isPlayer;
            case MOBS_ONLY -> !isPlayer;
        };

        // Debug when entities are filtered (only log when NOT affected to reduce spam)
        if (!result && isPlayer) {
            com.zenax.armorsets.utils.LogHelper.debug(
                "[AuraManager] Skipping %s for aura (affects=%s, isOwner=%b)",
                entityName, aura.affects.name(), isOwner);
        }

        return result;
    }

    /**
     * Spawn particles around the aura boundary.
     */
    private void spawnAuraParticles(Location center, double radius) {
        int points = (int) (radius * 8);
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.1, z);
            center.getWorld().spawnParticle(Particle.DUST, particleLoc, 1,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(128, 0, 128), 0.8f));
        }
    }

    /**
     * Spawn a new aura at the given location.
     *
     * @param owner      The player who created the aura
     * @param location   Center of the aura
     * @param radius     Radius in blocks
     * @param duration   Duration in seconds
     * @param effectType The potion effect to apply
     * @param amplifier  Effect amplifier (0 = level 1)
     * @param affects    Who should be affected
     * @param showParticles Whether to show particles
     * @return The UUID of the created aura
     */
    public UUID spawnAura(Player owner, Location location, double radius, int duration,
                          PotionEffectType effectType, int amplifier, AffectsType affects,
                          boolean showParticles) {
        return spawnAura(owner, location, radius, duration, effectType, amplifier, affects,
                showParticles, false, false, false, 0.0);
    }

    /**
     * Spawn a new aura with extended features (follow owner, pull on hit/attack).
     *
     * @param owner           The player who created the aura
     * @param location        Center of the aura
     * @param radius          Radius in blocks
     * @param duration        Duration in seconds
     * @param effectType      The potion effect to apply
     * @param amplifier       Effect amplifier (0 = level 1)
     * @param affects         Who should be affected
     * @param showParticles   Whether to show particles
     * @param followOwner     Whether the aura follows the owner
     * @param pullOnOwnerHit  Whether to pull enemies when owner is hit (DEFENSE)
     * @param pullOnOwnerAttack Whether to pull enemies when owner attacks (ATTACK)
     * @param pullStrength    Strength of the pull (1.0 = normal)
     * @return The UUID of the created aura
     */
    public UUID spawnAura(Player owner, Location location, double radius, int duration,
                          PotionEffectType effectType, int amplifier, AffectsType affects,
                          boolean showParticles, boolean followOwner, boolean pullOnOwnerHit,
                          boolean pullOnOwnerAttack, double pullStrength) {
        UUID auraId = UUID.randomUUID();
        long expiryTime = System.currentTimeMillis() + (duration * 1000L);

        ActiveAura aura = new ActiveAura(
            auraId,
            owner.getUniqueId(),
            location.clone(),
            radius,
            expiryTime,
            effectType,
            amplifier,
            affects,
            showParticles,
            followOwner,
            pullOnOwnerHit,
            pullOnOwnerAttack,
            pullStrength
        );

        activeAuras.put(auraId, aura);
        return auraId;
    }

    /**
     * Called when a player takes damage. Triggers pull effects on any auras
     * that have pullOnOwnerHit enabled.
     *
     * @param ownerUUID The UUID of the player who was hit
     */
    public void onOwnerHit(UUID ownerUUID) {
        Player owner = plugin.getServer().getPlayer(ownerUUID);
        if (owner == null || !owner.isOnline()) return;

        Location ownerLoc = owner.getLocation();

        for (ActiveAura aura : activeAuras.values()) {
            if (!aura.ownerId.equals(ownerUUID)) continue;
            if (!aura.pullOnOwnerHit) continue;

            // Pull all affected enemies toward the owner
            for (UUID enemyUUID : aura.affectedEnemies) {
                Entity entity = plugin.getServer().getEntity(enemyUUID);
                if (entity == null || !entity.isValid() || !(entity instanceof LivingEntity living)) continue;

                // Calculate pull direction toward owner
                Vector direction = ownerLoc.toVector().subtract(living.getLocation().toVector());
                double distance = direction.length();

                if (distance > 0.5) { // Don't pull if already very close
                    direction.normalize();

                    // Pull scales with distance and strength - proportional to distance prevents overshoot
                    // pullStrength acts as multiplier (1.0 = normal, 2.0 = double strength)
                    double pullMagnitude = distance * 0.6 * aura.pullStrength;

                    direction.setY(0.2); // Slight upward arc
                    direction.multiply(pullMagnitude);

                    living.setVelocity(direction);

                    // Visual/audio feedback
                    living.getWorld().spawnParticle(Particle.WITCH, living.getLocation().add(0, 1, 0),
                            10, 0.3, 0.3, 0.3, 0.02);
                }
            }

            // Play pull sound at owner location
            if (!aura.affectedEnemies.isEmpty()) {
                owner.getWorld().playSound(ownerLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 0.6f);
            }
        }
    }

    /**
     * Called when a player attacks an entity. Triggers pull effects on any auras
     * that have pullOnOwnerAttack enabled.
     *
     * @param ownerUUID The UUID of the player who attacked
     */
    public void onOwnerAttack(UUID ownerUUID) {
        Player owner = plugin.getServer().getPlayer(ownerUUID);
        if (owner == null || !owner.isOnline()) return;

        Location ownerLoc = owner.getLocation();

        for (ActiveAura aura : activeAuras.values()) {
            if (!aura.ownerId.equals(ownerUUID)) continue;
            if (!aura.pullOnOwnerAttack) continue;

            // Pull all affected enemies toward the owner
            for (UUID enemyUUID : aura.affectedEnemies) {
                Entity entity = plugin.getServer().getEntity(enemyUUID);
                if (entity == null || !entity.isValid() || !(entity instanceof LivingEntity living)) continue;

                // Calculate pull direction toward owner
                Vector direction = ownerLoc.toVector().subtract(living.getLocation().toVector());
                double distance = direction.length();

                if (distance > 0.5) { // Don't pull if already very close
                    direction.normalize();

                    // Pull scales with distance and strength - proportional to distance prevents overshoot
                    // pullStrength acts as multiplier (1.0 = normal, 2.0 = double strength)
                    double pullMagnitude = distance * 0.6 * aura.pullStrength;

                    direction.setY(0.2); // Slight upward arc
                    direction.multiply(pullMagnitude);

                    living.setVelocity(direction);

                    // Visual/audio feedback
                    living.getWorld().spawnParticle(Particle.WITCH, living.getLocation().add(0, 1, 0),
                            10, 0.3, 0.3, 0.3, 0.02);
                }
            }

            // Play pull sound at owner location
            if (!aura.affectedEnemies.isEmpty()) {
                owner.getWorld().playSound(ownerLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 0.6f);
            }
        }
    }

    /**
     * Remove an aura by ID.
     */
    public void removeAura(UUID auraId) {
        activeAuras.remove(auraId);
    }

    /**
     * Remove all auras owned by a player.
     */
    public void removePlayerAuras(UUID playerId) {
        activeAuras.entrySet().removeIf(entry -> entry.getValue().ownerId.equals(playerId));
    }

    /**
     * Get the number of active auras.
     */
    public int getActiveAuraCount() {
        return activeAuras.size();
    }

    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        activeAuras.clear();
    }

    /**
     * Who the aura affects.
     */
    public enum AffectsType {
        ENEMIES,           // All entities except owner
        ALLIES,            // Only the owner
        ALL,               // Everyone including owner
        PLAYERS_ONLY,      // Only players
        ENEMIES_PLAYERS_ONLY, // Enemy players only
        MOBS_ONLY          // Only mobs (non-players)
    }

    /**
     * Data class for an active aura.
     */
    private static class ActiveAura {
        final UUID id;
        final UUID ownerId;
        final Location location; // Mutable for followOwner
        final double radius;
        final long expiryTime;
        final PotionEffectType effectType;
        final int amplifier;
        final AffectsType affects;
        final boolean showParticles;
        final boolean followOwner;
        final boolean pullOnOwnerHit;
        final boolean pullOnOwnerAttack;
        final double pullStrength;
        final Set<UUID> affectedEnemies; // Track enemies in aura for pull

        ActiveAura(UUID id, UUID ownerId, Location location, double radius, long expiryTime,
                   PotionEffectType effectType, int amplifier, AffectsType affects, boolean showParticles) {
            this(id, ownerId, location, radius, expiryTime, effectType, amplifier, affects,
                 showParticles, false, false, false, 0.0);
        }

        ActiveAura(UUID id, UUID ownerId, Location location, double radius, long expiryTime,
                   PotionEffectType effectType, int amplifier, AffectsType affects, boolean showParticles,
                   boolean followOwner, boolean pullOnOwnerHit, boolean pullOnOwnerAttack, double pullStrength) {
            this.id = id;
            this.ownerId = ownerId;
            this.location = location;
            this.radius = radius;
            this.expiryTime = expiryTime;
            this.effectType = effectType;
            this.amplifier = amplifier;
            this.affects = affects;
            this.showParticles = showParticles;
            this.followOwner = followOwner;
            this.pullOnOwnerHit = pullOnOwnerHit;
            this.pullOnOwnerAttack = pullOnOwnerAttack;
            this.pullStrength = pullStrength;
            // Track enemies if either pull mode is enabled
            this.affectedEnemies = (pullOnOwnerHit || pullOnOwnerAttack) ? ConcurrentHashMap.newKeySet() : Collections.emptySet();
        }
    }
}
