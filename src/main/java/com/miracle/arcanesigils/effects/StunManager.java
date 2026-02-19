package com.miracle.arcanesigils.effects;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages stunned players, preventing all movement and camera rotation.
 *
 * Three-layer freeze approach:
 * 1. PlayerMoveEvent.setTo(frozenLocation) — corrects every move attempt back to frozen position
 *    (including look direction). Server sends correction packet per move attempt only.
 * 2. walkSpeed=0 + flySpeed=0 — prevents the client from even attempting to move
 * 3. Initial teleport on stun start — ensures immediate lock before first move event
 *
 * No periodic teleport task needed — avoids hitbox desync from continuous position packets.
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
            // Remove old display entity
            if (existing.getDisplayEntityUUID() != null) {
                Entity oldDisplay = Bukkit.getEntity(existing.getDisplayEntityUUID());
                if (oldDisplay != null) oldDisplay.remove();
            }
            // Restore speeds before re-applying (in case they changed)
            player.setWalkSpeed(existing.getPreviousWalkSpeed());
            player.setFlySpeed(existing.getPreviousFlySpeed());
        }

        // Preserve vanilla immunity ticks — reducing them caused damage multiplication
        int previousMaxNoDamageTicks = player.getMaximumNoDamageTicks();

        // Store the player's exact location and look direction
        Location frozenLocation = player.getLocation().clone();

        // Store previous speeds
        float previousWalkSpeed = player.getWalkSpeed();
        float previousFlySpeed = player.getFlySpeed();

        // Create stun data
        StunData stunData = new StunData(frozenLocation, previousMaxNoDamageTicks, previousWalkSpeed, previousFlySpeed);

        // Immediately freeze: teleport to exact position + zero speeds
        player.teleport(frozenLocation);
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);

        // Spawn sand block display at player's feet
        Location displayLoc = frozenLocation.clone();
        BlockDisplay display = displayLoc.getWorld().spawn(displayLoc, BlockDisplay.class, bd -> {
            bd.setBlock(Material.SAND.createBlockData());
            bd.setPersistent(false);
        });
        stunData.setDisplayEntityUUID(display.getUniqueId());

        // Schedule unstun
        BukkitTask unstunTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            unstunPlayer(player);
        }, (long) (duration * 20));

        stunData.setUnstunTask(unstunTask);

        stunnedPlayers.put(uuid, stunData);
    }

    /**
     * Stun a player for the specified duration (overload for backwards compat).
     */
    public void stunPlayer(Player player, double duration, Player attacker, int bindSlot) {
        stunPlayer(player, duration);
    }

    /**
     * Remove stun from a player.
     */
    public void unstunPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        StunData data = stunnedPlayers.remove(uuid);
        if (data != null) {
            data.cancel();
            // Remove display entity
            if (data.getDisplayEntityUUID() != null) {
                Entity displayEntity = Bukkit.getEntity(data.getDisplayEntityUUID());
                if (displayEntity != null) displayEntity.remove();
            }
            // Restore original speeds
            player.setWalkSpeed(data.getPreviousWalkSpeed());
            player.setFlySpeed(data.getPreviousFlySpeed());
            // Restore original maximumNoDamageTicks
            player.setMaximumNoDamageTicks(data.getPreviousMaxNoDamageTicks());
            player.setNoDamageTicks(0);
        }
    }

    /**
     * Check if a player is currently stunned.
     */
    public boolean isStunned(Player player) {
        return stunnedPlayers.containsKey(player.getUniqueId());
    }

    /**
     * Get the frozen location for a stunned player.
     */
    public Location getFrozenLocation(Player player) {
        StunData data = stunnedPlayers.get(player.getUniqueId());
        return data != null ? data.getFrozenLocation() : null;
    }

    /**
     * Get remaining stun time in seconds.
     */
    public double getRemainingStunTime(Player player) {
        return 0; // Not tracked
    }

    /**
     * Freeze stunned players on every move attempt.
     * Uses setTo() to correct position + rotation on each attempt.
     * Combined with walkSpeed=0, this is the primary freeze mechanism.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        StunData data = stunnedPlayers.get(player.getUniqueId());
        if (data != null) {
            // Set destination to frozen location — corrects both position and look direction
            event.setTo(data.getFrozenLocation());
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
     * Clean up when player dies.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        unstunPlayer(event.getEntity());
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
        private final int previousMaxNoDamageTicks;
        private final float previousWalkSpeed;
        private final float previousFlySpeed;
        private BukkitTask unstunTask;
        private UUID displayEntityUUID;

        public StunData(Location frozenLocation, int previousMaxNoDamageTicks,
                        float previousWalkSpeed, float previousFlySpeed) {
            this.frozenLocation = frozenLocation;
            this.previousMaxNoDamageTicks = previousMaxNoDamageTicks;
            this.previousWalkSpeed = previousWalkSpeed;
            this.previousFlySpeed = previousFlySpeed;
        }

        public Location getFrozenLocation() {
            return frozenLocation;
        }

        public int getPreviousMaxNoDamageTicks() {
            return previousMaxNoDamageTicks;
        }

        public float getPreviousWalkSpeed() {
            return previousWalkSpeed;
        }

        public float getPreviousFlySpeed() {
            return previousFlySpeed;
        }

        public void setUnstunTask(BukkitTask unstunTask) {
            this.unstunTask = unstunTask;
        }

        public UUID getDisplayEntityUUID() {
            return displayEntityUUID;
        }

        public void setDisplayEntityUUID(UUID displayEntityUUID) {
            this.displayEntityUUID = displayEntityUUID;
        }

        public void cancel() {
            if (unstunTask != null && !unstunTask.isCancelled()) {
                unstunTask.cancel();
            }
        }
    }
}
