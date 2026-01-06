package com.miracle.arcanesigils.combat.sync;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.combat.LegacyCombatConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks player position history for knockback sync (rollback netcode).
 * Stores the last N ticks of player positions to calculate where the
 * player was from the client's perspective when a hit occurred.
 */
public class PositionTracker {

    private final ArmorSetsPlugin plugin;
    private final LegacyCombatConfig config;

    // Position history for each player
    private final Map<UUID, Deque<TimestampedPosition>> positionHistory = new ConcurrentHashMap<>();

    // Sprint state tracking for W-tap detection
    private final Map<UUID, SprintState> sprintStates = new ConcurrentHashMap<>();

    private BukkitTask trackingTask;
    private int historySize;

    public PositionTracker(ArmorSetsPlugin plugin, LegacyCombatConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.historySize = config.getKbSyncHistoryTicks();

        startTrackingTask();
    }

    private void startTrackingTask() {
        // Run every tick to capture position data
        trackingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            // Track position
            Deque<TimestampedPosition> history = positionHistory.computeIfAbsent(uuid,
                k -> new ArrayDeque<>(historySize + 1));

            TimestampedPosition pos = new TimestampedPosition(
                player.getLocation().clone(),
                player.getVelocity().clone(),
                player.isOnGround(),
                now
            );

            history.addLast(pos);

            // Trim to max size
            while (history.size() > historySize) {
                history.removeFirst();
            }

            // Track sprint state for W-tap detection
            SprintState state = sprintStates.computeIfAbsent(uuid, k -> new SprintState());
            boolean wasSprinting = state.isSprinting;
            boolean isSprinting = player.isSprinting();

            if (!wasSprinting && isSprinting) {
                // Started sprinting
                state.lastSprintStart = now;
            } else if (wasSprinting && !isSprinting) {
                // Stopped sprinting
                state.lastSprintEnd = now;
            }
            state.isSprinting = isSprinting;
        }
    }

    /**
     * Start tracking a player (called on join).
     */
    public void startTracking(Player player) {
        UUID uuid = player.getUniqueId();
        positionHistory.put(uuid, new ArrayDeque<>(historySize + 1));
        sprintStates.put(uuid, new SprintState());
    }

    /**
     * Stop tracking a player (called on quit).
     */
    public void stopTracking(Player player) {
        UUID uuid = player.getUniqueId();
        positionHistory.remove(uuid);
        sprintStates.remove(uuid);
    }

    /**
     * Get the player's position from a specific time in the past.
     * This is used to calculate knockback from the client's perspective.
     *
     * @param player The player
     * @param msAgo How many milliseconds ago to look back
     * @return The interpolated position, or current position if no history
     */
    public Location getPositionAt(Player player, long msAgo) {
        Deque<TimestampedPosition> history = positionHistory.get(player.getUniqueId());
        if (history == null || history.isEmpty()) {
            return player.getLocation();
        }

        long targetTime = System.currentTimeMillis() - msAgo;

        // Find the two positions that bracket the target time
        TimestampedPosition before = null;
        TimestampedPosition after = null;

        for (TimestampedPosition pos : history) {
            if (pos.timestamp <= targetTime) {
                before = pos;
            } else if (after == null) {
                after = pos;
                break;
            }
        }

        // If we only have one side, return what we have
        if (before == null && after == null) {
            return player.getLocation();
        }
        if (before == null) {
            return after.location;
        }
        if (after == null) {
            return before.location;
        }

        // Interpolate between the two positions
        double ratio = (double) (targetTime - before.timestamp) / (after.timestamp - before.timestamp);
        return interpolate(before.location, after.location, ratio);
    }

    /**
     * Get the player's velocity from a specific time in the past.
     */
    public Vector getVelocityAt(Player player, long msAgo) {
        Deque<TimestampedPosition> history = positionHistory.get(player.getUniqueId());
        if (history == null || history.isEmpty()) {
            return player.getVelocity();
        }

        long targetTime = System.currentTimeMillis() - msAgo;

        // Find the closest position to target time
        TimestampedPosition closest = null;
        long closestDiff = Long.MAX_VALUE;

        for (TimestampedPosition pos : history) {
            long diff = Math.abs(pos.timestamp - targetTime);
            if (diff < closestDiff) {
                closestDiff = diff;
                closest = pos;
            }
        }

        return closest != null ? closest.velocity : player.getVelocity();
    }

    /**
     * Check if the player was on ground at a specific time in the past.
     */
    public boolean wasOnGroundAt(Player player, long msAgo) {
        Deque<TimestampedPosition> history = positionHistory.get(player.getUniqueId());
        if (history == null || history.isEmpty()) {
            return player.isOnGround();
        }

        long targetTime = System.currentTimeMillis() - msAgo;

        // Find the closest position to target time
        TimestampedPosition closest = null;
        long closestDiff = Long.MAX_VALUE;

        for (TimestampedPosition pos : history) {
            long diff = Math.abs(pos.timestamp - targetTime);
            if (diff < closestDiff) {
                closestDiff = diff;
                closest = pos;
            }
        }

        return closest != null ? closest.onGround : player.isOnGround();
    }

    /**
     * Check if a player has W-tapped (reset their sprint) within the configured window.
     */
    public boolean hasWTappedRecently(Player player) {
        SprintState state = sprintStates.get(player.getUniqueId());
        if (state == null) return false;

        long windowMs = config.getKbWTapWindowMs();
        long now = System.currentTimeMillis();

        // W-tap: stopped sprinting then started again within window
        if (state.lastSprintEnd > 0 && state.lastSprintStart > state.lastSprintEnd) {
            return (state.lastSprintStart - state.lastSprintEnd) < windowMs;
        }

        return false;
    }

    /**
     * Get the time since the player last started sprinting.
     */
    public long getTimeSinceSprintStart(Player player) {
        SprintState state = sprintStates.get(player.getUniqueId());
        if (state == null || state.lastSprintStart <= 0) return Long.MAX_VALUE;
        return System.currentTimeMillis() - state.lastSprintStart;
    }

    private Location interpolate(Location a, Location b, double ratio) {
        double x = a.getX() + (b.getX() - a.getX()) * ratio;
        double y = a.getY() + (b.getY() - a.getY()) * ratio;
        double z = a.getZ() + (b.getZ() - a.getZ()) * ratio;
        float yaw = (float) (a.getYaw() + (b.getYaw() - a.getYaw()) * ratio);
        float pitch = (float) (a.getPitch() + (b.getPitch() - a.getPitch()) * ratio);

        return new Location(a.getWorld(), x, y, z, yaw, pitch);
    }

    public void reload() {
        this.historySize = config.getKbSyncHistoryTicks();
    }

    public void disable() {
        if (trackingTask != null) {
            trackingTask.cancel();
        }
        positionHistory.clear();
        sprintStates.clear();
    }

    /**
     * A position record with timestamp.
     */
    public record TimestampedPosition(
        Location location,
        Vector velocity,
        boolean onGround,
        long timestamp
    ) {}

    /**
     * Tracks sprint state for W-tap detection.
     */
    private static class SprintState {
        boolean isSprinting = false;
        long lastSprintStart = 0;
        long lastSprintEnd = 0;
    }
}
