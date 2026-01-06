package com.miracle.arcanesigils.utils;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility for applying screen shake effects to players.
 * Uses velocity manipulation for version-stable camera shake.
 */
public class ScreenShakeUtil {

    private static final Map<UUID, BukkitTask> activeShakes = new ConcurrentHashMap<>();
    private static final Random random = new Random();

    // Max values for safety
    private static final double MAX_INTENSITY = 1.0;
    private static final int MAX_TICKS = 60; // 3 seconds max

    private ScreenShakeUtil() {
    } // Utility class

    /**
     * Apply screen shake effect to a player.
     *
     * @param player    The player to shake
     * @param intensity Shake intensity (0.0-1.0)
     * @param ticks     Duration in ticks (20 ticks = 1 second)
     */
    public static void shake(Player player, double intensity, int ticks) {
        if (player == null || !player.isOnline()) return;

        // Clamp values
        intensity = Math.max(0.01, Math.min(intensity, MAX_INTENSITY));
        ticks = Math.max(1, Math.min(ticks, MAX_TICKS));

        // Cancel existing shake
        stopShake(player);

        final double finalIntensity = intensity;
        final int finalTicks = ticks;

        BukkitTask task = new BukkitRunnable() {
            private int ticksRemaining = finalTicks;

            @Override
            public void run() {
                if (!player.isOnline() || ticksRemaining <= 0) {
                    activeShakes.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }

                // Calculate shake vector
                // Use small Y values to create view bobbing effect
                double scale = finalIntensity * 0.08;
                double yShake = (random.nextDouble() - 0.5) * scale * 2;

                // Apply tiny velocity impulse (primarily vertical for camera shake feel)
                Vector shake = new Vector(0, yShake, 0);

                // Add current velocity to avoid fighting player movement
                Vector current = player.getVelocity();
                player.setVelocity(current.add(shake));

                ticksRemaining--;
            }
        }.runTaskTimer(ArmorSetsPlugin.getInstance(), 0L, 2L); // Every 2 ticks

        activeShakes.put(player.getUniqueId(), task);
    }

    /**
     * Apply screen shake to all players within radius of a location.
     * Intensity scales with distance (closer = stronger).
     *
     * @param center    Center location
     * @param radius    Radius in blocks
     * @param intensity Base intensity (scales with distance)
     * @param ticks     Duration in ticks
     */
    public static void shakeArea(Location center, double radius, double intensity, int ticks) {
        if (center.getWorld() == null) return;

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Player player) {
                double distance = player.getLocation().distance(center);
                if (distance <= radius) {
                    // Scale intensity based on distance (closer = stronger)
                    double scaledIntensity = intensity * (1.0 - (distance / radius));
                    shake(player, scaledIntensity, ticks);
                }
            }
        }
    }

    /**
     * Check if player currently has an active shake effect.
     */
    public static boolean isShaking(Player player) {
        return activeShakes.containsKey(player.getUniqueId());
    }

    /**
     * Stop any active shake effect on the player.
     */
    public static void stopShake(Player player) {
        BukkitTask task = activeShakes.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Clean up all active shakes (call on plugin disable).
     */
    public static void cleanup() {
        for (BukkitTask task : activeShakes.values()) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        activeShakes.clear();
    }
}
