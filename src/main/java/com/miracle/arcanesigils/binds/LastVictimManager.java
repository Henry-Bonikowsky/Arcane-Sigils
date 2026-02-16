package com.miracle.arcanesigils.binds;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last entity hit by each player.
 * Persists across all signal types as long as the entity is alive and within 30 blocks.
 */
public class LastVictimManager implements Listener {

    private final ArmorSetsPlugin plugin;

    // Track last hit entity per player
    private final Map<UUID, Entity> lastVictims = new ConcurrentHashMap<>();

    // Configuration
    private static final double MAX_VICTIM_RANGE = 30.0;

    public LastVictimManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;

        // Register event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Record a hit on an entity. Tracks all LivingEntity hits.
     *
     * @param player The player who hit the entity
     * @param victim The entity that was hit
     */
    public void recordHit(Player player, LivingEntity victim) {
        lastVictims.put(player.getUniqueId(), victim);
        // Recording successful - no log needed (would spam on every hit)
    }

    /**
     * Get the last victim hit by a player, with validation.
     * Returns null if the victim is dead, out of range, or in a different world.
     *
     * @param player The player
     * @return The last valid victim, or null
     */
    public LivingEntity getLastVictim(Player player) {
        UUID uuid = player.getUniqueId();
        Entity entity = lastVictims.get(uuid);

        if (entity == null) {
            return null;
        }

        // Must be a LivingEntity
        if (!(entity instanceof LivingEntity living)) {
            lastVictims.remove(uuid);
            return null;
        }

        // Check if entity is dead or invalid (cheap check - do first)
        if (living.isDead() || !living.isValid()) {
            lastVictims.remove(uuid);
            return null;
        }

        // Check same world (cheap - do before distance)
        if (player.getWorld() != living.getWorld()) {
            // Don't remove from map - might return to same world
            return null;
        }

        // Check distance (expensive - do last)
        double distance = player.getLocation().distance(living.getLocation());
        if (distance > MAX_VICTIM_RANGE) {
            // Don't remove from map - might come back in range
            return null;
        }

        // Valid victim
        return living;
    }

    /**
     * Clean up when a player quits.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastVictims.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Handle world changes - clear tracking (prevent cross-world targeting).
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        lastVictims.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Handle entity death - remove from all tracking.
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity deadEntity = event.getEntity();

        // Remove this entity from all player tracking
        lastVictims.entrySet().removeIf(entry -> entry.getValue().equals(deadEntity));

        LogHelper.debug(String.format("[LastVictim] Cleared dead entity: %s", deadEntity.getName()));
    }

    /**
     * Clean up all tracking on plugin disable.
     */
    public void cleanup() {
        lastVictims.clear();
    }
}
