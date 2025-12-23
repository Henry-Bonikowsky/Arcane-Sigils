package com.zenax.dungeons.dungeon;

import com.zenax.dungeons.DungeonsAddon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles dungeon-related player events including death, respawn,
 * exit portal interaction, and disconnect handling.
 */
public class DungeonEventHandler implements Listener {
    private final DungeonsAddon addon;
    private final DungeonManager dungeonManager;
    private final DungeonCompletionHandler completionHandler;

    // Track players who are dead in dungeon (awaiting respawn)
    private final Set<UUID> deadPlayers;

    /**
     * Creates a new dungeon event handler.
     *
     * @param addon The dungeons addon
     * @param dungeonManager The dungeon manager
     * @param completionHandler The completion handler
     */
    public DungeonEventHandler(DungeonsAddon addon, DungeonManager dungeonManager,
                               DungeonCompletionHandler completionHandler) {
        this.addon = addon;
        this.dungeonManager = dungeonManager;
        this.completionHandler = completionHandler;
        this.deadPlayers = new HashSet<>();
    }

    /**
     * Handles player death in dungeons.
     * Records death and checks for party wipe.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DungeonInstance instance = dungeonManager.getPlayerInstance(player);

        if (instance == null || instance.getState() != DungeonState.ACTIVE) {
            return;
        }

        // Record the death
        instance.recordDeath(player);
        deadPlayers.add(player.getUniqueId());

        addon.info("Player " + player.getName() + " died in dungeon " + instance.getInstanceId());

        // Check for party wipe (all players dead)
        if (isPartyWiped(instance)) {
            addon.info("Party wipe detected in dungeon " + instance.getInstanceId());
            instance.setState(DungeonState.FAILED);
            completionHandler.handleFailure(instance, DungeonCompletionHandler.FailureReason.PARTY_WIPE);
        }
    }

    /**
     * Handles player respawn in dungeons.
     * Teleports player back to dungeon spawn point.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DungeonInstance instance = dungeonManager.getPlayerInstance(player);

        if (instance == null) {
            deadPlayers.remove(player.getUniqueId());
            return;
        }

        // Remove from dead players
        deadPlayers.remove(player.getUniqueId());

        // If dungeon is still active, respawn at dungeon spawn
        if (instance.getState() == DungeonState.ACTIVE) {
            event.setRespawnLocation(instance.getSpawnPoint());
            player.sendMessage("§eYou have respawned at the dungeon entrance.");
        }
        // If dungeon completed/failed, respawn at world spawn (handled by completion handler)
    }

    /**
     * Handles player movement for exit portal detection.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if actually moved to a new block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        DungeonInstance instance = dungeonManager.getPlayerInstance(player);

        if (instance == null) {
            return;
        }

        // Only check for exit portal if dungeon is completed
        if (instance.getState() != DungeonState.COMPLETED) {
            return;
        }

        // Check if player entered exit portal
        if (completionHandler.isInExitPortal(event.getTo(), instance)) {
            completionHandler.handleExitPortalUse(player, instance);
        }
    }

    /**
     * Handles player disconnect in dungeons.
     * Checks if dungeon should be abandoned.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DungeonInstance instance = dungeonManager.getPlayerInstance(player);

        if (instance == null) {
            deadPlayers.remove(player.getUniqueId());
            return;
        }

        // Remove from dead players tracking
        deadPlayers.remove(player.getUniqueId());

        // Remove from instance
        dungeonManager.removePlayerFromInstance(player);

        addon.info("Player " + player.getName() + " disconnected from dungeon " + instance.getInstanceId());

        // Check if dungeon is now empty
        if (instance.isEmpty() && instance.getState() == DungeonState.ACTIVE) {
            addon.info("Dungeon " + instance.getInstanceId() + " abandoned - all players left");
            instance.setState(DungeonState.FAILED);
            completionHandler.handleFailure(instance, DungeonCompletionHandler.FailureReason.ABANDONED);
        }
    }

    /**
     * Checks if all players in the dungeon are dead.
     *
     * @param instance The dungeon instance
     * @return true if all players are dead (party wipe)
     */
    private boolean isPartyWiped(DungeonInstance instance) {
        Set<UUID> players = instance.getPlayerUuids();
        if (players.isEmpty()) {
            return false;
        }

        for (UUID playerUuid : players) {
            // Check if player is alive
            if (!deadPlayers.contains(playerUuid)) {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline() && !player.isDead()) {
                    return false; // At least one player is alive
                }
            }
        }

        return true; // All players are dead
    }

    /**
     * Clears all tracked dead players.
     */
    public void clear() {
        deadPlayers.clear();
    }

    /**
     * Unregisters this handler.
     */
    public void unregister() {
        deadPlayers.clear();
    }
}
