package com.miracle.arcanesigils.effects;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages stunned players, preventing all movement and camera rotation.
 * Uses per-tick teleportation to lock players in place.
 */
public class StunManager implements Listener {

    private final ArmorSetsPlugin plugin;
    private final Map<UUID, StunData> stunnedPlayers = new ConcurrentHashMap<>();

    public StunManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Stun a player for the specified duration.
     *
     * @param player   The player to stun
     * @param duration Duration in seconds
     */
    public void stunPlayer(Player player, double duration) {
        UUID uuid = player.getUniqueId();

        // If already stunned, cancel existing
        if (stunnedPlayers.containsKey(uuid)) {
            StunData existing = stunnedPlayers.get(uuid);
            existing.cancel();
        }

        // Store the player's exact location and look direction
        Location frozenLocation = player.getLocation().clone();

        // Create stun data
        StunData stunData = new StunData(frozenLocation);

        // Per-tick teleport to freeze position and rotation
        BukkitTask freezeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (player.isOnline() && stunnedPlayers.containsKey(uuid)) {
                player.teleport(frozenLocation);
            }
        }, 0L, 1L);

        stunData.setFreezeTask(freezeTask);

        // Schedule unstun
        BukkitTask unstunTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            unstunPlayer(player);
        }, (long) (duration * 20));

        stunData.setUnstunTask(unstunTask);

        stunnedPlayers.put(uuid, stunData);
    }

    /**
     * Remove stun from a player.
     */
    public void unstunPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        StunData data = stunnedPlayers.remove(uuid);
        if (data != null) {
            data.cancel();
        }
    }

    /**
     * Check if a player is currently stunned.
     */
    public boolean isStunned(Player player) {
        return stunnedPlayers.containsKey(player.getUniqueId());
    }

    /**
     * Get remaining stun time in seconds.
     */
    public double getRemainingStunTime(Player player) {
        StunData data = stunnedPlayers.get(player.getUniqueId());
        if (data == null) return 0;
        return data.getRemainingTime();
    }

    /**
     * Cancel movement for stunned players.
     * Note: We just cancel the event, we don't use setTo() which causes hitbox desync.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isStunned(player)) {
            // Cancel the event - the per-tick teleport handles position
            event.setCancelled(true);
        }
    }

    /**
     * Clean up when player quits.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unstunPlayer(event.getPlayer());
    }

    /**
     * Shutdown and clean up all stuns.
     */
    public void shutdown() {
        for (UUID uuid : stunnedPlayers.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                unstunPlayer(player);
            }
        }
        stunnedPlayers.clear();
    }

    /**
     * Data class to track stun state.
     */
    private static class StunData {
        private final Location frozenLocation;
        private final long startTime;
        private BukkitTask freezeTask;
        private BukkitTask unstunTask;

        public StunData(Location frozenLocation) {
            this.frozenLocation = frozenLocation;
            this.startTime = System.currentTimeMillis();
        }

        public Location getFrozenLocation() {
            return frozenLocation;
        }

        public void setFreezeTask(BukkitTask freezeTask) {
            this.freezeTask = freezeTask;
        }

        public void setUnstunTask(BukkitTask unstunTask) {
            this.unstunTask = unstunTask;
        }

        public double getRemainingTime() {
            return 0; // Would need to track end time for accurate value
        }

        public void cancel() {
            if (freezeTask != null && !freezeTask.isCancelled()) {
                freezeTask.cancel();
            }
            if (unstunTask != null && !unstunTask.isCancelled()) {
                unstunTask.cancel();
            }
        }
    }
}
