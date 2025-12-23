package com.zenax.dungeons.lobby;

import com.zenax.dungeons.DungeonsAddon;
import com.zenax.dungeons.dungeon.*;
import com.zenax.dungeons.generation.DungeonGenerator;
import com.zenax.dungeons.world.DungeonWorldManager;
import com.zenax.dungeons.sound.AmbientSoundManager;
import com.zenax.dungeons.sound.DungeonSoundEffects;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

/**
 * Manages all active dungeon lobbies.
 * Handles lobby creation, player management, settings, and dungeon start transitions.
 */
public class LobbyManager {
    private final Plugin plugin;
    private final DungeonManager dungeonManager;
    private final DungeonWorldManager worldManager;
    private com.zenax.dungeons.portal.PortalManager portalManager;
    private AmbientSoundManager ambientSoundManager;

    private final Map<UUID, DungeonLobby> activeLobbies;
    private final Map<UUID, UUID> playerToLobby;
    private final Map<UUID, BukkitTask> lobbyCountdowns;

    // Counter for lobby instance placement
    private final AtomicInteger instanceCounter = new AtomicInteger(0);

    private static final int COUNTDOWN_DURATION = 10; // seconds
    private static final int GATE_HEIGHT = 3; // blocks high

    /**
     * Creates a new lobby manager.
     *
     * @param plugin The plugin instance
     * @param dungeonManager The dungeon manager instance
     * @param worldManager The dungeon world manager instance
     */
    public LobbyManager(Plugin plugin, DungeonManager dungeonManager, DungeonWorldManager worldManager) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
        this.worldManager = worldManager;
        this.activeLobbies = new ConcurrentHashMap<>();
        this.playerToLobby = new ConcurrentHashMap<>();
        this.lobbyCountdowns = new ConcurrentHashMap<>();
    }

    /**
     * Sets the portal manager reference (called after initialization to avoid circular dependency).
     */
    public void setPortalManager(com.zenax.dungeons.portal.PortalManager portalManager) {
        this.portalManager = portalManager;
    }

    public void setAmbientSoundManager(AmbientSoundManager ambientSoundManager) {
        this.ambientSoundManager = ambientSoundManager;
    }

    /**
     * Creates a new lobby for a dungeon.
     *
     * @param dungeon The dungeon template to create a lobby for
     * @param spawnPoint The spawn point for players entering the lobby
     * @param readyPlatformCenter The center of the ready platform
     * @param gateLocation The location of the gate/barrier blocks
     * @param dungeonEntranceLocation The location players will be teleported to when entering
     * @return The created DungeonLobby
     */
    public DungeonLobby createLobby(Dungeon dungeon, Location spawnPoint, Location readyPlatformCenter,
                                   Location gateLocation, Location dungeonEntranceLocation) {
        DungeonLobby lobby = new DungeonLobby(dungeon, spawnPoint, readyPlatformCenter,
                                             gateLocation, dungeonEntranceLocation);
        activeLobbies.put(lobby.getInstanceId(), lobby);

        // Generate gate barrier blocks
        generateGate(gateLocation);

        plugin.getLogger().info("Created lobby for dungeon: " + dungeon.getId() +
                               " (Instance: " + lobby.getInstanceId() + ")");
        return lobby;
    }

    /**
     * Creates a new lobby for a party of players who entered through a portal.
     * Uses procedural cave generation for unique lobby environments.
     *
     * @param dungeon The dungeon template to create a lobby for
     * @param partyMembers The set of player UUIDs who will be in this lobby
     * @return The created DungeonLobby, or null if creation failed
     */
    public DungeonLobby createLobbyForParty(Dungeon dungeon, Set<UUID> partyMembers) {
        if (dungeon == null || partyMembers == null || partyMembers.isEmpty()) {
            return null;
        }

        // Get the dungeon world for lobby generation
        World lobbyWorld = worldManager != null && worldManager.isReady()
            ? worldManager.getDungeonWorld()
            : null;

        // Fallback to first player's world if dungeon world is not available
        if (lobbyWorld == null) {
            for (UUID playerUuid : partyMembers) {
                Player player = plugin.getServer().getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    lobbyWorld = player.getWorld();
                    break;
                }
            }
        }

        if (lobbyWorld == null) {
            plugin.getLogger().warning("No world available for lobby creation");
            return null;
        }

        // Get base location for this lobby instance (grid-based placement)
        int instanceIndex = instanceCounter.getAndIncrement();
        int gridX = instanceIndex % 10;
        int gridZ = instanceIndex / 10;
        Location baseLocation = new Location(lobbyWorld, gridX * 100, -20, gridZ * 100);

        // Generate cave lobby using procedural generator
        LobbyCaveGenerator generator = new LobbyCaveGenerator();
        LobbyCaveGenerator.CaveLobbyResult result = generator.generate(baseLocation, dungeon);

        if (result == null) {
            plugin.getLogger().warning("Failed to generate cave lobby");
            return null;
        }

        // Create the lobby with generated locations
        DungeonLobby lobby = new DungeonLobby(dungeon, result.getSpawnPoint(),
            result.getSpawnPoint(), result.getGateLocation(), result.getEntranceLocation());

        lobby.setBackPortalCorner(result.getPortalLocation());
        activeLobbies.put(lobby.getInstanceId(), lobby);

        // Pre-add all party members to the lobby
        for (UUID playerUuid : partyMembers) {
            lobby.addPlayer(playerUuid);
            playerToLobby.put(playerUuid, lobby.getInstanceId());
        }

        // Spawn the hologram (TextDisplay + Interaction) and store the Interaction's ID
        Interaction hologramInteraction = generator.spawnHologram(result.getHologramLocation(), lobby);
        if (hologramInteraction != null) {
            lobby.setHologramEntityId(hologramInteraction.getUniqueId());
        }

        // Start ambient sounds
        if (ambientSoundManager != null) {
            ambientSoundManager.startLobbyAmbience(lobby);
        }

        plugin.getLogger().info("Created cave lobby for party (" + partyMembers.size() + " players) for dungeon: " +
                               dungeon.getId() + " (Instance: " + lobby.getInstanceId() + ")");

        return lobby;
    }

    /**
     * Generates a leave portal structure at the given location.
     * Uses crying obsidian frame with actual nether portal blocks.
     * This portal functions like a regular dungeon portal but teleports back.
     *
     * @param location The base location for the portal (bottom-left corner)
     */
    private void generateLeavePortal(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Create a 4x5 crying obsidian frame (same as player portals)
        // Portal extends along X axis (width), so axis is X

        // Bottom row (4 blocks)
        for (int dx = 0; dx < 4; dx++) {
            world.getBlockAt(x + dx, y, z).setType(Material.CRYING_OBSIDIAN);
        }

        // Top row (4 blocks)
        for (int dx = 0; dx < 4; dx++) {
            world.getBlockAt(x + dx, y + 4, z).setType(Material.CRYING_OBSIDIAN);
        }

        // Left side (3 blocks, excluding corners)
        for (int dy = 1; dy <= 3; dy++) {
            world.getBlockAt(x, y + dy, z).setType(Material.CRYING_OBSIDIAN);
        }

        // Right side (3 blocks, excluding corners)
        for (int dy = 1; dy <= 3; dy++) {
            world.getBlockAt(x + 3, y + dy, z).setType(Material.CRYING_OBSIDIAN);
        }

        // Interior - nether portal blocks with correct axis (2x3)
        // Create BlockData with axis BEFORE setting, use physics=false to avoid POI issues
        // (we manage this portal ourselves, don't need vanilla POI tracking)
        for (int dx = 1; dx <= 2; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                org.bukkit.block.data.BlockData data = Material.NETHER_PORTAL.createBlockData();
                if (data instanceof org.bukkit.block.data.Orientable orientable) {
                    orientable.setAxis(org.bukkit.Axis.X);
                }
                block.setBlockData(data, false);
            }
        }

        // Send block updates to nearby players for rendering
        Location center = new Location(world, x + 1.5, y + 2, z);
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(center) < 10000) {
                for (int dx = 1; dx <= 2; dx++) {
                    for (int dy = 1; dy <= 3; dy++) {
                        Location loc = new Location(world, x + dx, y + dy, z);
                        p.sendBlockChange(loc, loc.getBlock().getBlockData());
                    }
                }
            }
        }
    }

    /**
     * Removes the leave portal structure at the given location.
     *
     * @param location The base location of the portal
     */
    public void removeLeavePortal(Location location) {
        if (location == null) return;

        World world = location.getWorld();
        if (world == null) return;

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Remove the 4x5 portal structure
        for (int dx = 0; dx < 4; dx++) {
            for (int dy = 0; dy < 5; dy++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                Material type = block.getType();
                if (type == Material.CRYING_OBSIDIAN || type == Material.NETHER_PORTAL) {
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    /**
     * Checks if a location is within the leave portal area of a lobby.
     *
     * @param location The location to check
     * @param lobby The lobby to check against
     * @return true if the location is within the leave portal
     */
    public boolean isInLeavePortal(Location location, DungeonLobby lobby) {
        Location portalLoc = lobby.getLeavePortalLocation();
        if (portalLoc == null || location == null) return false;
        if (location.getWorld() == null || portalLoc.getWorld() == null) return false;
        if (!location.getWorld().equals(portalLoc.getWorld())) return false;

        // Portal interior is at x+1 and x+2, y+1 to y+3, same z
        int px = portalLoc.getBlockX();
        int py = portalLoc.getBlockY();
        int pz = portalLoc.getBlockZ();

        int lx = location.getBlockX();
        int ly = location.getBlockY();
        int lz = location.getBlockZ();

        // Check if within the 2x3 interior (x+1 to x+2, y+1 to y+3)
        return lz == pz &&
               lx >= px + 1 && lx <= px + 2 &&
               ly >= py + 1 && ly <= py + 3;
    }

    /**
     * Checks if a player is near any lobby's leave portal.
     * Uses exact block position matching for reliability.
     *
     * @param location The location to check
     * @param maxDistance Maximum distance to check (used as fallback)
     * @return The lobby if near a leave portal, null otherwise
     */
    public DungeonLobby getLobbyNearExitPortal(Location location, double maxDistance) {
        if (location == null || location.getWorld() == null) return null;

        for (DungeonLobby lobby : activeLobbies.values()) {
            // Check new-style back wall portal first
            if (isInBackPortal(location, lobby)) {
                return lobby;
            }

            // Also check old-style leave portal for backwards compatibility
            if (isInLeavePortal(location, lobby)) {
                return lobby;
            }

            // Fallback to distance-based check for edge cases
            Location portalLoc = lobby.getLeavePortalLocation();
            if (portalLoc == null || portalLoc.getWorld() == null) continue;
            if (!portalLoc.getWorld().equals(location.getWorld())) continue;

            // Check distance to portal center (x+1.5, y+2, z+0.5)
            Location portalCenter = portalLoc.clone().add(1.5, 2, 0.5);
            if (location.distance(portalCenter) <= maxDistance) {
                return lobby;
            }
        }
        return null;
    }

    /**
     * Checks if a location is within the back wall portal area of a lobby.
     * Uses dynamic detection by checking if the block is a nether portal.
     *
     * @param location The location to check
     * @param lobby The lobby to check against
     * @return true if the location is within the back portal
     */
    private boolean isInBackPortal(Location location, DungeonLobby lobby) {
        Location portalCorner = lobby.getBackPortalCorner();
        if (portalCorner == null || location == null) return false;
        if (location.getWorld() == null || portalCorner.getWorld() == null) return false;
        if (!location.getWorld().equals(portalCorner.getWorld())) return false;

        int pz = portalCorner.getBlockZ();
        int lz = location.getBlockZ();

        // Must be on same Z plane as portal (or within 1 block)
        if (Math.abs(lz - pz) > 1) return false;

        // Check if the block at this location is a nether portal
        Block block = location.getBlock();
        if (block.getType() == Material.NETHER_PORTAL) {
            return true;
        }

        // Also check the block at feet level (player location might be slightly offset)
        Block feetBlock = location.clone().add(0, -0.5, 0).getBlock();
        return feetBlock.getType() == Material.NETHER_PORTAL;
    }

    /**
     * Creates a new lobby with all interactive locations specified.
     */
    public DungeonLobby createLobby(Dungeon dungeon, Location spawnPoint, Location readyPlatformCenter,
                                   Location gateLocation, Location dungeonEntranceLocation,
                                   Location objectiveSelectorLocation, Location difficultySelectorLocation,
                                   Location infoBoardLocation, Location lootPreviewLocation) {
        DungeonLobby lobby = new DungeonLobby(dungeon, spawnPoint, readyPlatformCenter,
                                             gateLocation, dungeonEntranceLocation,
                                             objectiveSelectorLocation, difficultySelectorLocation,
                                             infoBoardLocation, lootPreviewLocation);
        activeLobbies.put(lobby.getInstanceId(), lobby);

        // Generate gate barrier blocks
        generateGate(gateLocation);

        plugin.getLogger().info("Created lobby for dungeon: " + dungeon.getId() +
                               " (Instance: " + lobby.getInstanceId() + ")");
        return lobby;
    }

    /**
     * Destroys a lobby and removes all associated data.
     *
     * @param instanceId The UUID of the lobby instance
     * @return true if the lobby was destroyed successfully
     */
    public boolean destroyLobby(UUID instanceId) {
        DungeonLobby lobby = activeLobbies.remove(instanceId);
        if (lobby == null) {
            return false;
        }

        // Cancel any active countdown
        BukkitTask countdown = lobbyCountdowns.remove(instanceId);
        if (countdown != null) {
            countdown.cancel();
        }

        // Remove all players from the lobby mapping
        for (UUID playerUuid : lobby.getPlayersInLobby()) {
            playerToLobby.remove(playerUuid);
        }

        // Remove hologram entities (Interaction + TextDisplay)
        if (lobby.getHologramEntityId() != null) {
            org.bukkit.entity.Entity interactionEntity = org.bukkit.Bukkit.getEntity(lobby.getHologramEntityId());
            if (interactionEntity != null) {
                // Also remove nearby TextDisplay (spawned with the Interaction)
                for (org.bukkit.entity.Entity nearby : interactionEntity.getNearbyEntities(2, 2, 2)) {
                    if (nearby instanceof org.bukkit.entity.TextDisplay) {
                        nearby.remove();
                    }
                }
                interactionEntity.remove();
            }
        }

        // Stop ambient sounds
        if (ambientSoundManager != null) {
            ambientSoundManager.stopLobbyAmbience(instanceId);
        }

        // Remove gate
        removeGate(lobby.getGateLocation());

        // Clean up entry portal (un-ignite and remove from database)
        if (portalManager != null && lobby.getEntryPortalId() != null) {
            com.zenax.dungeons.portal.DungeonPortal entryPortal = portalManager.getPortal(lobby.getEntryPortalId());
            if (entryPortal != null) {
                // Clear the nether portal blocks
                for (org.bukkit.Location loc : entryPortal.getInteriorBlocks()) {
                    org.bukkit.block.Block block = loc.getBlock();
                    if (block.getType() == Material.NETHER_PORTAL) {
                        block.setType(Material.AIR, false);
                    }
                }
                // Remove from storage
                portalManager.removePortal(entryPortal.getId());
                portalManager.savePortals();
                plugin.getLogger().info("Removed entry portal: " + entryPortal.getId());
            }
        }

        plugin.getLogger().info("Destroyed lobby: " + instanceId);
        return true;
    }

    /**
     * Destroys a lobby.
     *
     * @param lobby The lobby to destroy
     * @return true if the lobby was destroyed successfully
     */
    public boolean destroyLobby(DungeonLobby lobby) {
        return destroyLobby(lobby.getInstanceId());
    }

    /**
     * Teleports a player to a lobby.
     *
     * @param player The player to teleport
     * @param lobbyId The UUID of the lobby
     * @return true if the player was teleported successfully
     */
    public boolean teleportToLobby(Player player, UUID lobbyId) {
        DungeonLobby lobby = activeLobbies.get(lobbyId);
        if (lobby == null) {
            return false;
        }

        // Check if player is already in THIS lobby (pre-added by createLobbyForParty)
        UUID currentLobbyId = playerToLobby.get(player.getUniqueId());
        if (currentLobbyId != null && currentLobbyId.equals(lobbyId)) {
            // Player already in target lobby, just teleport
            player.teleport(lobby.getSpawnPoint());
            plugin.getLogger().info("Teleported player " + player.getName() + " to lobby " + lobbyId);
            return true;
        }

        // Remove from different lobby if in one
        if (currentLobbyId != null) {
            leaveLobby(player);
        }

        if (lobby.isFull()) {
            return false;
        }

        // Add to new lobby
        if (!lobby.addPlayer(player)) {
            return false;
        }

        playerToLobby.put(player.getUniqueId(), lobbyId);
        player.teleport(lobby.getSpawnPoint());

        plugin.getLogger().info("Teleported player " + player.getName() + " to lobby " + lobbyId);
        return true;
    }

    /**
     * Removes a player from their current lobby.
     *
     * @param player The player to remove
     * @return true if the player was removed successfully
     */
    public boolean leaveLobby(Player player) {
        UUID lobbyId = playerToLobby.remove(player.getUniqueId());
        if (lobbyId == null) {
            return false;
        }

        DungeonLobby lobby = activeLobbies.get(lobbyId);
        if (lobby == null) {
            return false;
        }

        lobby.removePlayer(player);

        // Broadcast to remaining players
        broadcastToLobby(lobby, "§e" + player.getName() + " §7has left the lobby!");

        // Cancel countdown if no longer all ready
        if (!lobby.areAllReady() && lobby.isCountdownActive()) {
            cancelCountdown(lobby);
            broadcastToLobby(lobby, "§cCountdown cancelled - not all players are ready!");
        }

        // DON'T destroy lobby when empty - portal should remain usable
        // Lobby will be destroyed when dungeon starts or portal is broken

        plugin.getLogger().info("Player " + player.getName() + " left lobby " + lobbyId);
        return true;
    }

    /**
     * Sets the objective mode for a lobby.
     *
     * @param lobbyId The UUID of the lobby
     * @param mode The objective mode to set
     * @return true if the mode was set successfully
     */
    public boolean setObjectiveMode(UUID lobbyId, ObjectiveMode mode) {
        DungeonLobby lobby = activeLobbies.get(lobbyId);
        if (lobby == null) {
            return false;
        }

        if (!lobby.setObjectiveMode(mode)) {
            return false;
        }

        broadcastToLobby(lobby, "§aObjective mode set to: §e" + mode.getDisplayName());
        plugin.getLogger().info("Lobby " + lobbyId + " objective mode set to: " + mode);
        return true;
    }

    /**
     * Sets the difficulty for a lobby.
     *
     * @param lobbyId The UUID of the lobby
     * @param difficulty The difficulty to set
     * @return true if the difficulty was set successfully
     */
    public boolean setDifficulty(UUID lobbyId, DungeonDifficulty difficulty) {
        DungeonLobby lobby = activeLobbies.get(lobbyId);
        if (lobby == null) {
            return false;
        }

        lobby.setDifficulty(difficulty);
        broadcastToLobby(lobby, "§aDifficulty set to: " + difficulty.getColoredDisplayName());
        plugin.getLogger().info("Lobby " + lobbyId + " difficulty set to: " + difficulty);
        return true;
    }

    /**
     * Checks the ready status of a lobby and starts countdown if all are ready.
     *
     * @param lobby The lobby to check
     */
    public void checkReadyStatus(DungeonLobby lobby) {
        if (lobby.areAllReady() && !lobby.isCountdownActive()) {
            startCountdown(lobby);
        } else if (!lobby.areAllReady() && lobby.isCountdownActive()) {
            cancelCountdown(lobby);
        }
    }

    /**
     * Starts the countdown for a lobby.
     *
     * @param lobby The lobby to start countdown for
     */
    private void startCountdown(DungeonLobby lobby) {
        lobby.startCountdown();
        broadcastToLobby(lobby, "§aAll players ready! Starting in " + COUNTDOWN_DURATION + " seconds...");

        BukkitTask task = new BukkitRunnable() {
            int timeLeft = COUNTDOWN_DURATION;

            @Override
            public void run() {
                if (!lobby.areAllReady()) {
                    broadcastToLobby(lobby, "§cCountdown cancelled - not all players are ready!");
                    lobby.cancelCountdown();
                    cancel();
                    return;
                }

                if (timeLeft <= 0) {
                    startDungeon(lobby);
                    cancel();
                    return;
                }

                if (timeLeft <= 5 || timeLeft % 5 == 0) {
                    broadcastToLobby(lobby, "§eStarting in " + timeLeft + "...");
                }

                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        lobbyCountdowns.put(lobby.getInstanceId(), task);
    }

    /**
     * Cancels the countdown for a lobby.
     *
     * @param lobby The lobby to cancel countdown for
     */
    private void cancelCountdown(DungeonLobby lobby) {
        lobby.cancelCountdown();
        BukkitTask task = lobbyCountdowns.remove(lobby.getInstanceId());
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Opens the gate by removing barrier blocks.
     *
     * @param lobby The lobby to open the gate for
     */
    public void openGate(DungeonLobby lobby) {
        removeGate(lobby.getGateLocation());
        DungeonSoundEffects.playGateOpen(lobby.getGateLocation());
        broadcastToLobby(lobby, "§aThe gate has opened!");
    }

    /**
     * Starts the dungeon for a lobby, transitioning players to the active dungeon.
     *
     * @param lobby The lobby to start
     * @return The created DungeonInstance, or null if creation failed
     */
    public DungeonInstance startDungeon(DungeonLobby lobby) {
        Dungeon template = lobby.getDungeonTemplate();
        if (template == null) {
            broadcastToLobby(lobby, "§cDungeon template not found!");
            return null;
        }

        // Stop lobby ambience
        if (ambientSoundManager != null) {
            ambientSoundManager.stopLobbyAmbience(lobby.getInstanceId());
        }

        // Remove the leave portal before starting (players can no longer leave)
        removeLeavePortal(lobby.getLeavePortalLocation());

        // Destroy the entry portal now that dungeon is starting
        if (portalManager != null && lobby.getEntryPortalId() != null) {
            com.zenax.dungeons.portal.DungeonPortal entryPortal = portalManager.getPortal(lobby.getEntryPortalId());
            if (entryPortal != null) {
                // Clear interior portal blocks
                for (Location loc : entryPortal.getInteriorBlocks()) {
                    Block block = loc.getBlock();
                    if (block.getType() == Material.NETHER_PORTAL) {
                        block.setType(Material.AIR, false);
                    }
                }
                // Clear frame blocks (crying obsidian)
                for (Location loc : entryPortal.getFrameBlocks()) {
                    Block block = loc.getBlock();
                    if (block.getType() == Material.CRYING_OBSIDIAN) {
                        block.setType(Material.AIR);
                    }
                }
                portalManager.removePortal(entryPortal.getId());
                plugin.getLogger().info("Destroyed entry portal: " + entryPortal.getId());
            }
        }

        // Open gate
        openGate(lobby);

        // Broadcast generating message
        broadcastToLobby(lobby, "§6§lGENERATING DUNGEON...");
        broadcastToLobby(lobby, "§7Please wait while the dungeon is being created.");

        // Get the dungeon generator and generate asynchronously
        DungeonGenerator generator = DungeonsAddon.getInstance().getDungeonGenerator();
        World world = lobby.getSpawnPoint().getWorld();
        Location dungeonOrigin = lobby.getDungeonEntranceLocation().clone().add(0, 0, 10);

        // Capture lobby data before async operation
        Set<UUID> playerUuids = new HashSet<>(lobby.getPlayersInLobby());
        DungeonDifficulty difficulty = lobby.getSelectedDifficulty();
        ObjectiveMode objectiveMode = lobby.getSelectedObjectiveMode();
        UUID lobbyInstanceId = lobby.getInstanceId();

        generator.generateDungeonAsync(template, world, dungeonOrigin,
            // Progress callback
            progress -> {
                int percent = (int) (progress * 100);
                if (percent % 25 == 0) {
                    for (UUID uuid : playerUuids) {
                        Player p = plugin.getServer().getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            p.sendMessage("§7Generation progress: §e" + percent + "%");
                        }
                    }
                }
            },
            // Completion callback
            instance -> {
                if (instance == null) {
                    for (UUID uuid : playerUuids) {
                        Player p = plugin.getServer().getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            p.sendMessage("§cFailed to generate dungeon!");
                        }
                    }
                    plugin.getLogger().severe("Failed to generate dungeon for lobby: " + lobbyInstanceId);
                    return;
                }

                // Update instance with selected settings
                instance.setState(DungeonState.ACTIVE);

                // Teleport all players to dungeon spawn
                Location spawnPoint = instance.getSpawnPoint();
                for (UUID uuid : playerUuids) {
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        dungeonManager.addPlayerToInstance(player, instance);
                        player.teleport(spawnPoint);
                        player.sendMessage("§aThe dungeon has begun! Good luck!");
                        player.sendMessage("§eObjective: §f" + objectiveMode.getDisplayName());
                        player.sendMessage("§eDifficulty: " + difficulty.getColoredDisplayName());
                    }
                }

                plugin.getLogger().info("Started dungeon for lobby " + lobbyInstanceId +
                                       " (Instance: " + instance.getInstanceId() + ")");
            }
        );

        // Destroy the lobby (players will be teleported when generation completes)
        destroyLobby(lobby);

        return null; // Instance is created asynchronously
    }

    /**
     * Gets a lobby by its instance ID.
     *
     * @param instanceId The instance ID
     * @return The DungeonLobby, or null if not found
     */
    public DungeonLobby getLobby(UUID instanceId) {
        return activeLobbies.get(instanceId);
    }

    /**
     * Gets the lobby a player is currently in.
     *
     * @param player The player
     * @return The DungeonLobby, or null if the player is not in a lobby
     */
    public DungeonLobby getPlayerLobby(Player player) {
        UUID lobbyId = playerToLobby.get(player.getUniqueId());
        return lobbyId != null ? activeLobbies.get(lobbyId) : null;
    }

    /**
     * Gets the lobby a player UUID is currently in.
     *
     * @param playerUuid The player UUID
     * @return The DungeonLobby, or null if the player is not in a lobby
     */
    public DungeonLobby getPlayerLobby(UUID playerUuid) {
        UUID lobbyId = playerToLobby.get(playerUuid);
        return lobbyId != null ? activeLobbies.get(lobbyId) : null;
    }

    /**
     * Checks if a player is in a lobby.
     *
     * @param player The player to check
     * @return true if the player is in a lobby
     */
    public boolean isPlayerInLobby(Player player) {
        return playerToLobby.containsKey(player.getUniqueId());
    }

    /**
     * Checks if a player UUID is in a lobby.
     *
     * @param playerUuid The player UUID to check
     * @return true if the player is in a lobby
     */
    public boolean isPlayerInLobby(UUID playerUuid) {
        return playerToLobby.containsKey(playerUuid);
    }

    /**
     * Gets all active lobbies.
     *
     * @return A map of instance IDs to DungeonLobbies
     */
    public Map<UUID, DungeonLobby> getAllLobbies() {
        return new HashMap<>(activeLobbies);
    }

    /**
     * Gets the number of active lobbies.
     *
     * @return The number of active lobbies
     */
    public int getActiveLobbyCount() {
        return activeLobbies.size();
    }

    /**
     * Checks if a location is within any lobby's back portal (full wall portal).
     *
     * @param location The location to check
     * @return The lobby if the location is in a back portal, null otherwise
     */
    public DungeonLobby getLobbyAtBackPortal(Location location) {
        if (location == null || location.getWorld() == null) return null;

        for (DungeonLobby lobby : activeLobbies.values()) {
            Location portalCorner = lobby.getBackPortalCorner();
            if (portalCorner == null) continue;
            if (!portalCorner.getWorld().equals(location.getWorld())) continue;

            // Back portal is 7 wide, 5 tall at z=portalCorner.z
            int px = portalCorner.getBlockX();
            int py = portalCorner.getBlockY();
            int pz = portalCorner.getBlockZ();

            int lx = location.getBlockX();
            int ly = location.getBlockY();
            int lz = location.getBlockZ();

            if (lz == pz && lx >= px && lx <= px + 6 && ly >= py && ly <= py + 4) {
                return lobby;
            }
        }
        return null;
    }

    /**
     * Broadcasts a message to all players in a lobby.
     *
     * @param lobby The lobby to broadcast to
     * @param message The message to broadcast
     */
    public void broadcastToLobby(DungeonLobby lobby, String message) {
        for (UUID playerUuid : lobby.getPlayersInLobby()) {
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Generates gate barrier blocks at the specified location.
     *
     * @param gateLocation The location to generate the gate
     */
    private void generateGate(Location gateLocation) {
        World world = gateLocation.getWorld();
        if (world == null) {
            return;
        }

        int x = gateLocation.getBlockX();
        int y = gateLocation.getBlockY();
        int z = gateLocation.getBlockZ();

        // Create a 3x3 barrier wall
        for (int dy = 0; dy < GATE_HEIGHT; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                block.setType(Material.BARRIER);
            }
        }
    }

    /**
     * Removes gate barrier blocks at the specified location.
     *
     * @param gateLocation The location to remove the gate
     */
    private void removeGate(Location gateLocation) {
        World world = gateLocation.getWorld();
        if (world == null) {
            return;
        }

        int x = gateLocation.getBlockX();
        int y = gateLocation.getBlockY();
        int z = gateLocation.getBlockZ();

        // Remove the 3x3 barrier wall
        for (int dy = 0; dy < GATE_HEIGHT; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR);
                }
            }
        }
    }

    /**
     * Cleans up empty lobbies.
     *
     * @return The number of lobbies cleaned up
     */
    public int cleanupEmptyLobbies() {
        int cleaned = 0;
        Iterator<Map.Entry<UUID, DungeonLobby>> iterator = activeLobbies.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, DungeonLobby> entry = iterator.next();
            DungeonLobby lobby = entry.getValue();

            if (lobby.isEmpty()) {
                // Cancel countdown if active
                BukkitTask task = lobbyCountdowns.remove(entry.getKey());
                if (task != null) {
                    task.cancel();
                }

                // Remove gate
                removeGate(lobby.getGateLocation());

                iterator.remove();
                cleaned++;
                plugin.getLogger().info("Cleaned up empty lobby: " + entry.getKey());
            }
        }

        return cleaned;
    }

    /**
     * Shuts down the lobby manager, destroying all active lobbies.
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down LobbyManager...");

        // Cancel all countdowns
        for (BukkitTask task : lobbyCountdowns.values()) {
            task.cancel();
        }
        lobbyCountdowns.clear();

        // Destroy all lobbies
        for (UUID lobbyId : new HashSet<>(activeLobbies.keySet())) {
            destroyLobby(lobbyId);
        }

        playerToLobby.clear();

        plugin.getLogger().info("LobbyManager shutdown complete");
    }
}
