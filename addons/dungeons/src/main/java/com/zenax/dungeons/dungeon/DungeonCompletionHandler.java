package com.zenax.dungeons.dungeon;

import com.zenax.dungeons.DungeonsAddon;
import com.zenax.dungeons.loot.LootManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;

/**
 * Handles dungeon completion events including victory, failure, rewards,
 * exit portals, and cleanup.
 */
public class DungeonCompletionHandler {
    private final DungeonsAddon addon;
    private final DungeonManager dungeonManager;
    private final LootManager lootManager;

    // Exit portal locations per instance
    private final Map<UUID, Location> exitPortals;

    // Track which instances have been processed
    private final Set<UUID> processedInstances;

    // Delay before teleporting players out (in seconds)
    private static final int COMPLETION_DELAY = 10;
    private static final int FAILURE_DELAY = 5;

    /**
     * Creates a new completion handler.
     *
     * @param addon The dungeons addon
     * @param dungeonManager The dungeon manager
     * @param lootManager The loot manager
     */
    public DungeonCompletionHandler(DungeonsAddon addon, DungeonManager dungeonManager, LootManager lootManager) {
        this.addon = addon;
        this.dungeonManager = dungeonManager;
        this.lootManager = lootManager;
        this.exitPortals = new HashMap<>();
        this.processedInstances = new HashSet<>();
    }

    /**
     * Handles dungeon completion (victory).
     *
     * @param instance The completed dungeon instance
     */
    public void handleCompletion(DungeonInstance instance) {
        if (instance == null || processedInstances.contains(instance.getInstanceId())) {
            return;
        }
        processedInstances.add(instance.getInstanceId());

        addon.info("Processing dungeon completion for instance: " + instance.getInstanceId());

        // Calculate completion stats
        long completionTime = (System.currentTimeMillis() - instance.getStartTime()) / 1000;
        int totalDeaths = getTotalDeaths(instance);

        // Broadcast victory
        broadcastVictory(instance, completionTime, totalDeaths);

        // Distribute rewards
        distributeRewards(instance);

        // Spawn exit portal at boss room or spawn point
        Location exitLocation = instance.getRoom("boss");
        if (exitLocation == null) {
            exitLocation = instance.getSpawnPoint();
        }
        spawnExitPortal(instance, exitLocation);

        // Schedule cleanup and teleport after delay
        scheduleCompletionCleanup(instance, COMPLETION_DELAY);
    }

    /**
     * Handles dungeon failure (time expired, wipe, etc).
     *
     * @param instance The failed dungeon instance
     * @param reason The reason for failure
     */
    public void handleFailure(DungeonInstance instance, FailureReason reason) {
        if (instance == null || processedInstances.contains(instance.getInstanceId())) {
            return;
        }
        processedInstances.add(instance.getInstanceId());

        addon.info("Processing dungeon failure for instance: " + instance.getInstanceId() + " - " + reason);

        // Broadcast failure
        broadcastFailure(instance, reason);

        // No rewards on failure

        // Schedule teleport and cleanup
        scheduleFailureCleanup(instance, FAILURE_DELAY);
    }

    /**
     * Broadcasts victory message to all players in the dungeon.
     */
    private void broadcastVictory(DungeonInstance instance, long completionTime, int deaths) {
        String timeFormatted = formatTime(completionTime);

        // Title
        Title title = Title.title(
            Component.text("VICTORY!", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("Dungeon Completed!", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        );

        // Chat message
        Component divider = Component.text("═══════════════════════════════════", NamedTextColor.GOLD);
        Component header = Component.text()
            .append(Component.newline())
            .append(divider)
            .append(Component.newline())
            .append(Component.text("  ★ ", NamedTextColor.YELLOW))
            .append(Component.text("DUNGEON COMPLETED!", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(" ★", NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(divider)
            .build();

        Component stats = Component.text()
            .append(Component.newline())
            .append(Component.text("  Dungeon: ", NamedTextColor.GRAY))
            .append(Component.text(instance.getTemplate().getDisplayName(), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("  Difficulty: ", NamedTextColor.GRAY))
            .append(Component.text(instance.getDifficulty().getDisplayName(), getDifficultyColor(instance.getDifficulty())))
            .append(Component.newline())
            .append(Component.text("  Time: ", NamedTextColor.GRAY))
            .append(Component.text(timeFormatted, NamedTextColor.AQUA))
            .append(Component.newline())
            .append(Component.text("  Deaths: ", NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(deaths), deaths == 0 ? NamedTextColor.GREEN : NamedTextColor.RED))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("  Teleporting in " + COMPLETION_DELAY + " seconds...", NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(divider)
            .build();

        for (UUID playerUuid : instance.getPlayerUuids()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.showTitle(title);
                player.sendMessage(header);
                player.sendMessage(stats);

                // Play victory sounds
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                // Spawn fireworks
                spawnVictoryFirework(player.getLocation());
            }
        }
    }

    /**
     * Broadcasts failure message to all players in the dungeon.
     */
    private void broadcastFailure(DungeonInstance instance, FailureReason reason) {
        // Title
        Title title = Title.title(
            Component.text("DUNGEON FAILED", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text(reason.getMessage(), NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        );

        // Chat message
        Component divider = Component.text("═══════════════════════════════════", NamedTextColor.DARK_RED);
        Component message = Component.text()
            .append(Component.newline())
            .append(divider)
            .append(Component.newline())
            .append(Component.text("  ✖ ", NamedTextColor.RED))
            .append(Component.text("DUNGEON FAILED", NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.text(" ✖", NamedTextColor.RED))
            .append(Component.newline())
            .append(divider)
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("  Reason: ", NamedTextColor.GRAY))
            .append(Component.text(reason.getMessage(), NamedTextColor.RED))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("  Teleporting in " + FAILURE_DELAY + " seconds...", NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(divider)
            .build();

        for (UUID playerUuid : instance.getPlayerUuids()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.showTitle(title);
                player.sendMessage(message);

                // Play failure sound
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.5f);
            }
        }
    }

    /**
     * Distributes rewards to players based on dungeon completion.
     */
    private void distributeRewards(DungeonInstance instance) {
        List<Player> players = getOnlinePlayers(instance);
        if (players.isEmpty()) {
            return;
        }

        // Get loot table from dungeon template
        String lootTableId = instance.getTemplate().getCompletionLootTable();
        if (lootTableId == null || lootTableId.isEmpty()) {
            lootTableId = "basic"; // Default loot table
        }

        // Generate loot based on difficulty
        double luckModifier = instance.getDifficulty().getLootMultiplier();
        List<ItemStack> loot = lootManager.generateLoot(lootTableId, instance.getDifficulty(), luckModifier);

        if (loot.isEmpty()) {
            addon.warn("No loot generated for dungeon completion");
            return;
        }

        // Distribute loot to players
        lootManager.distributeLoot(players, loot);

        // Notify players
        for (Player player : players) {
            player.sendMessage(Component.text()
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(Component.text("✦ ", NamedTextColor.GOLD))
                .append(Component.text("Rewards have been distributed!", NamedTextColor.GREEN))
                .build());
        }

        addon.info("Distributed " + loot.size() + " items to " + players.size() + " players");
    }

    /**
     * Spawns an exit portal at the specified location.
     */
    public void spawnExitPortal(DungeonInstance instance, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        Location portalLoc = location.clone().add(0, 1, 0);
        World world = portalLoc.getWorld();

        // Create a small end portal frame structure
        // Using end gateway for the visual effect
        Location center = portalLoc.clone();

        // Place crying obsidian frame (3x3 on ground)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue; // Skip center
                Location frameLoc = center.clone().add(x, -1, z);
                frameLoc.getBlock().setType(Material.CRYING_OBSIDIAN);
            }
        }

        // Place green stained glass in center as exit indicator
        center.clone().add(0, -1, 0).getBlock().setType(Material.LIME_STAINED_GLASS);

        // Place beacon beam effect (sea lantern for glow)
        center.getBlock().setType(Material.SEA_LANTERN);

        // Store the portal location
        exitPortals.put(instance.getInstanceId(), center);

        // Spawn particles
        startExitPortalParticles(instance, center);

        // Notify players
        for (UUID playerUuid : instance.getPlayerUuids()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text()
                    .append(Component.text("  ", NamedTextColor.GRAY))
                    .append(Component.text("➤ ", NamedTextColor.GREEN))
                    .append(Component.text("An exit portal has appeared!", NamedTextColor.GREEN))
                    .build());
            }
        }
    }

    /**
     * Starts spawning particles at the exit portal.
     */
    private void startExitPortalParticles(DungeonInstance instance, Location center) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // Stop if instance is no longer active
                if (!exitPortals.containsKey(instance.getInstanceId())) {
                    cancel();
                    return;
                }

                World world = center.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                // Spiral particles
                double angle = ticks * 0.2;
                double radius = 0.8;
                double x = center.getX() + 0.5 + Math.cos(angle) * radius;
                double z = center.getZ() + 0.5 + Math.sin(angle) * radius;

                Location particleLoc = new Location(world, x, center.getY() + 0.5 + (ticks % 20) * 0.1, z);
                world.spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);

                ticks++;

                // Auto-cancel after 60 seconds
                if (ticks > 1200) {
                    cancel();
                }
            }
        }.runTaskTimer(addon.getPlugin(), 0L, 2L);
    }

    /**
     * Checks if a location is within an exit portal.
     */
    public boolean isInExitPortal(Location location, DungeonInstance instance) {
        Location portalLoc = exitPortals.get(instance.getInstanceId());
        if (portalLoc == null || location.getWorld() != portalLoc.getWorld()) {
            return false;
        }

        // Check if within 1.5 blocks of portal center
        return location.distance(portalLoc) <= 1.5;
    }

    /**
     * Gets the exit portal location for an instance.
     */
    public Location getExitPortal(DungeonInstance instance) {
        return exitPortals.get(instance.getInstanceId());
    }

    /**
     * Schedules cleanup after successful completion.
     */
    private void scheduleCompletionCleanup(DungeonInstance instance, int delaySeconds) {
        new BukkitRunnable() {
            @Override
            public void run() {
                performCleanup(instance, true);
            }
        }.runTaskLater(addon.getPlugin(), delaySeconds * 20L);
    }

    /**
     * Schedules cleanup after failure.
     */
    private void scheduleFailureCleanup(DungeonInstance instance, int delaySeconds) {
        new BukkitRunnable() {
            @Override
            public void run() {
                performCleanup(instance, false);
            }
        }.runTaskLater(addon.getPlugin(), delaySeconds * 20L);
    }

    /**
     * Performs full cleanup of a dungeon instance.
     */
    private void performCleanup(DungeonInstance instance, boolean success) {
        UUID instanceId = instance.getInstanceId();
        addon.info("Performing cleanup for instance: " + instanceId);

        // Teleport all players out
        teleportPlayersOut(instance);

        // Remove all mobs
        cleanupMobs(instance);

        // Remove treasure chests
        lootManager.removeChestsForInstance(instance);

        // Remove exit portal
        removeExitPortal(instance);

        // Clean up generated blocks (if tracking them)
        // This would require storing generated block locations

        // Mark instance for destruction
        instance.setState(success ? DungeonState.COMPLETED : DungeonState.FAILED);

        // Remove from processed set
        processedInstances.remove(instanceId);
        exitPortals.remove(instanceId);

        // Destroy the instance
        dungeonManager.destroyInstance(instance);

        addon.info("Cleanup complete for instance: " + instanceId);
    }

    /**
     * Teleports all players out of the dungeon.
     */
    private void teleportPlayersOut(DungeonInstance instance) {
        // Get the world spawn or a saved return location
        Location returnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();

        for (UUID playerUuid : new HashSet<>(instance.getPlayerUuids())) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                // Play teleport effect
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 50, 0.5, 1, 0.5, 0.1);

                // Teleport
                player.teleport(returnLocation);

                // Send message
                player.sendMessage(Component.text()
                    .append(Component.text("You have left the dungeon.", NamedTextColor.GRAY))
                    .build());

                // Remove from instance
                dungeonManager.removePlayerFromInstance(player);
            }
        }
    }

    /**
     * Removes all mobs from the dungeon instance.
     */
    private void cleanupMobs(DungeonInstance instance) {
        World world = instance.getWorld();
        if (world == null) {
            return;
        }

        for (UUID mobUuid : instance.getActiveMobs()) {
            Entity entity = world.getEntity(mobUuid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
    }

    /**
     * Removes the exit portal structure.
     */
    private void removeExitPortal(DungeonInstance instance) {
        Location portalLoc = exitPortals.get(instance.getInstanceId());
        if (portalLoc == null || portalLoc.getWorld() == null) {
            return;
        }

        // Remove frame blocks
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Location frameLoc = portalLoc.clone().add(x, -1, z);
                if (frameLoc.getBlock().getType() == Material.CRYING_OBSIDIAN ||
                    frameLoc.getBlock().getType() == Material.LIME_STAINED_GLASS) {
                    frameLoc.getBlock().setType(Material.AIR);
                }
            }
        }

        // Remove center block
        if (portalLoc.getBlock().getType() == Material.SEA_LANTERN) {
            portalLoc.getBlock().setType(Material.AIR);
        }
    }

    /**
     * Spawns a victory firework at the location.
     */
    private void spawnVictoryFirework(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        // Spawn particle burst instead of actual firework
        world.spawnParticle(Particle.FIREWORK, location.clone().add(0, 2, 0), 100, 2, 2, 2, 0.1);
        world.spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0, 1, 0), 50, 1, 1, 1, 0);
    }

    /**
     * Gets the total deaths for an instance.
     */
    private int getTotalDeaths(DungeonInstance instance) {
        int total = 0;
        for (UUID playerUuid : instance.getPlayerUuids()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                total += instance.getDeaths(player);
            }
        }
        return total;
    }

    /**
     * Gets online players in the instance.
     */
    private List<Player> getOnlinePlayers(DungeonInstance instance) {
        List<Player> players = new ArrayList<>();
        for (UUID playerUuid : instance.getPlayerUuids()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    /**
     * Formats seconds into MM:SS format.
     */
    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    /**
     * Converts DungeonDifficulty ChatColor to NamedTextColor.
     */
    private NamedTextColor getDifficultyColor(DungeonDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.YELLOW;
            case HARD -> NamedTextColor.RED;
            case NIGHTMARE -> NamedTextColor.DARK_PURPLE;
        };
    }

    /**
     * Handles a player using the exit portal.
     */
    public void handleExitPortalUse(Player player, DungeonInstance instance) {
        // Play teleport effect
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 50, 0.5, 1, 0.5, 0.1);

        // Teleport to spawn
        Location returnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
        player.teleport(returnLocation);

        // Remove from instance
        dungeonManager.removePlayerFromInstance(player);

        player.sendMessage(Component.text("You have left the dungeon.", NamedTextColor.GRAY));
    }

    /**
     * Clears all tracked data.
     */
    public void clear() {
        exitPortals.clear();
        processedInstances.clear();
    }

    /**
     * Enum for failure reasons.
     */
    public enum FailureReason {
        TIME_EXPIRED("Time limit exceeded"),
        PARTY_WIPE("All players have fallen"),
        ABANDONED("All players left the dungeon"),
        ADMIN_ENDED("Ended by administrator");

        private final String message;

        FailureReason(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
