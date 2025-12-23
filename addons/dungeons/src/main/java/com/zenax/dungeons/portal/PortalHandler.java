package com.zenax.dungeons.portal;

import com.zenax.dungeons.DungeonsAddon;
import com.zenax.dungeons.dungeon.Dungeon;
import com.zenax.dungeons.dungeon.DungeonManager;
import com.zenax.dungeons.lobby.DungeonLobby;
import com.zenax.dungeons.lobby.LobbyManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Handles portal-related events and player interactions.
 * Players are teleported to the dungeon lobby when the nether portal event fires
 * (after standing in the portal for the vanilla delay time).
 */
public class PortalHandler implements Listener {
    private final Plugin plugin;
    private final PortalManager portalManager;
    private final DungeonManager dungeonManager;
    private final LobbyManager lobbyManager;

    // Cooldown to prevent immediate re-entry after exiting a lobby
    private final Set<UUID> recentlyTeleported = new HashSet<>();

    // Cooldown to prevent double-firing of ignite events
    private final Map<UUID, Long> ignitionCooldowns = new HashMap<>();
    private static final long IGNITION_COOLDOWN_MS = 500;

    /**
     * Creates a new portal handler.
     *
     * @param portalManager The portal manager instance
     * @param dungeonManager The dungeon manager instance
     * @param lobbyManager The lobby manager instance
     */
    public PortalHandler(PortalManager portalManager, DungeonManager dungeonManager, LobbyManager lobbyManager) {
        this.plugin = DungeonsAddon.getInstance().getPlugin();
        this.portalManager = portalManager;
        this.dungeonManager = dungeonManager;
        this.lobbyManager = lobbyManager;
    }

    /**
     * Handles block ignition events to create dungeon portals.
     * Uses the same event as vanilla nether portals for consistency.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockIgnite(BlockIgniteEvent event) {
        // Only handle flint and steel ignition
        if (event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
            return;
        }

        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (player == null) {
            return;
        }

        // Prevent double-firing
        long now = System.currentTimeMillis();
        Long lastIgnite = ignitionCooldowns.get(player.getUniqueId());
        if (lastIgnite != null && now - lastIgnite < IGNITION_COOLDOWN_MS) {
            return;
        }
        ignitionCooldowns.put(player.getUniqueId(), now);

        // Try to ignite a dungeon portal at this location
        if (tryIgnitePortal(player, block)) {
            // Cancel the fire placement - we're creating a portal instead
            event.setCancelled(true);
        }
    }

    /**
     * Attempts to ignite a crying obsidian portal frame.
     * Searches for a valid frame structure around the clicked block.
     *
     * @param player The player igniting the portal
     * @param clickedBlock The block that was clicked
     * @return true if a portal was successfully ignited
     */
    private boolean tryIgnitePortal(Player player, Block clickedBlock) {
        // Check if this is near a lobby exit portal (don't create dungeon portals on exit portals)
        if (lobbyManager.getLobbyNearExitPortal(clickedBlock.getLocation(), 5.0) != null) {
            return false;
        }

        // Check if there's already a portal here
        DungeonPortal existingPortal = portalManager.getPortalAtLocation(clickedBlock.getLocation());
        if (existingPortal != null) {
            return false;
        }

        // Try to find a valid portal frame around this block
        PortalFrameResult frameResult = findPortalFrameBase(clickedBlock);
        if (frameResult == null) {
            return false; // Not a valid portal frame
        }

        Location frameBase = frameResult.location();
        org.bukkit.Axis portalAxis = frameResult.axis();

        // Check if a portal already exists at this frame location
        existingPortal = portalManager.getPortalAtLocation(frameBase);
        if (existingPortal != null) {
            return false;
        }

        // Get the default/first dungeon
        String dungeonId = getDefaultDungeonId();
        if (dungeonId == null) {
            plugin.getLogger().warning("No dungeons configured - portal ignition failed");
            return false;
        }

        Dungeon dungeon = dungeonManager.getDungeon(dungeonId);
        if (dungeon == null) {
            plugin.getLogger().warning("Dungeon not found: " + dungeonId);
            return false;
        }

        // Create the portal with the detected axis orientation
        DungeonPortal portal = portalManager.createPortal(dungeonId, frameBase, null, portalAxis);
        if (portal == null) {
            return false;
        }

        // Fill the portal interior with nether portal blocks (with correct rotation)
        fillPortalInterior(portal);

        // Save portals immediately after creation
        portalManager.savePortals();

        // Play end portal creation sound
        World world = frameBase.getWorld();
        if (world != null) {
            Location center = frameBase.clone().add(1.5, 2.0, 0.5);
            world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.0f);
        }

        plugin.getLogger().info("Player " + player.getName() + " created dungeon portal at " +
                               frameBase.getBlockX() + ", " + frameBase.getBlockY() + ", " + frameBase.getBlockZ());

        return true;
    }

    /**
     * Result of portal frame search containing location and orientation.
     */
    private record PortalFrameResult(Location location, org.bukkit.Axis axis) {}

    /**
     * Finds the base location of a valid portal frame around a block.
     * Searches in multiple directions to find a valid 4x5 crying obsidian frame.
     *
     * @param block The block to search around
     * @return The frame result with location and axis, or null if not found
     */
    private PortalFrameResult findPortalFrameBase(Block block) {
        // Search in a radius around the clicked block for a valid frame
        for (int dy = -4; dy <= 0; dy++) {
            for (int dx = -3; dx <= 0; dx++) {
                for (int dz = -3; dz <= 0; dz++) {
                    Block potentialBase = block.getRelative(dx, dy, dz);

                    // Check if this could be the bottom-left corner of a frame
                    if (potentialBase.getType() == Material.CRYING_OBSIDIAN) {
                        // Try both orientations
                        // EAST direction = frame extends along X axis, portal blocks oriented along X
                        if (isValidFrameAt(potentialBase, BlockFace.EAST)) {
                            return new PortalFrameResult(potentialBase.getLocation(), org.bukkit.Axis.X);
                        }
                        // SOUTH direction = frame extends along Z axis, portal blocks oriented along Z
                        if (isValidFrameAt(potentialBase, BlockFace.SOUTH)) {
                            return new PortalFrameResult(potentialBase.getLocation(), org.bukkit.Axis.Z);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Checks if there's a valid 4x5 portal frame at the given base location.
     *
     * @param baseBlock The bottom-left corner block
     * @param direction The direction to check (EAST or SOUTH for the width)
     * @return true if a valid frame exists
     */
    private boolean isValidFrameAt(Block baseBlock, BlockFace direction) {
        Material frameBlock = Material.CRYING_OBSIDIAN;
        int width = 4;
        int height = 5;

        // Check bottom row (4 blocks wide)
        for (int x = 0; x < width; x++) {
            Block check = baseBlock.getRelative(direction, x);
            if (check.getType() != frameBlock) {
                return false;
            }
        }

        // Check top row (4 blocks wide)
        for (int x = 0; x < width; x++) {
            Block check = baseBlock.getRelative(BlockFace.UP, height - 1).getRelative(direction, x);
            if (check.getType() != frameBlock) {
                return false;
            }
        }

        // Check left side (middle 3 blocks)
        for (int y = 1; y < height - 1; y++) {
            Block check = baseBlock.getRelative(BlockFace.UP, y);
            if (check.getType() != frameBlock) {
                return false;
            }
        }

        // Check right side (middle 3 blocks)
        for (int y = 1; y < height - 1; y++) {
            Block check = baseBlock.getRelative(direction, width - 1).getRelative(BlockFace.UP, y);
            if (check.getType() != frameBlock) {
                return false;
            }
        }

        // Check that interior is air or already filled with portal (2x3 interior)
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                Block check = baseBlock.getRelative(BlockFace.UP, y).getRelative(direction, x);
                Material type = check.getType();
                if (!type.isAir() && type != Material.NETHER_PORTAL) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Fills the portal interior with nether portal blocks.
     * Uses the portal's stored axis for correct orientation.
     *
     * @param portal The portal to fill
     */
    private void fillPortalInterior(DungeonPortal portal) {
        Set<Location> interiorBlocks = portal.getInteriorBlocks();
        org.bukkit.Axis axis = portal.getAxis();
        World world = portal.getLocation().getWorld();

        // Set all portal blocks
        for (Location loc : interiorBlocks) {
            Block block = loc.getBlock();
            if (block.getType().isAir() || block.getType() == Material.FIRE) {
                // Create BlockData with correct axis BEFORE setting the block
                org.bukkit.block.data.BlockData data = Material.NETHER_PORTAL.createBlockData();
                if (data instanceof org.bukkit.block.data.Orientable orientable) {
                    orientable.setAxis(axis);
                }
                // Use false for physics - we manage portals ourselves, avoid POI conflicts
                block.setBlockData(data, false);
            }
        }

        // Force send block updates to all nearby players
        if (world != null) {
            Location center = portal.getLocation();
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distanceSquared(center) < 10000) { // Within 100 blocks
                    for (Location loc : interiorBlocks) {
                        p.sendBlockChange(loc, loc.getBlock().getBlockData());
                    }
                }
            }
        }
    }

    /**
     * Clears the portal interior (sets blocks back to air).
     * Uses setType with false to avoid POI data mismatch errors.
     *
     * @param portal The portal to clear
     */
    private void clearPortalInterior(DungeonPortal portal) {
        Set<Location> interiorBlocks = portal.getInteriorBlocks();
        for (Location loc : interiorBlocks) {
            Block block = loc.getBlock();
            if (block.getType() == Material.NETHER_PORTAL) {
                // Use false to skip physics/POI updates - prevents "POI data mismatch" errors
                block.setType(Material.AIR, false);
            }
        }
    }

    /**
     * Gets the default dungeon ID (first available dungeon).
     *
     * @return The dungeon ID, or null if none available
     */
    private String getDefaultDungeonId() {
        Set<String> dungeonIds = dungeonManager.getDungeonIds();
        if (dungeonIds.isEmpty()) {
            return null;
        }
        return dungeonIds.iterator().next();
    }

    /**
     * Intercepts nether portal teleportation to redirect to dungeon lobby.
     * This fires after the player has stood in the portal for the vanilla delay time.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }

        Player player = event.getPlayer();

        // Skip if player was recently teleported (prevents loops)
        if (recentlyTeleported.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // CRITICAL: If player is in a lobby, ANY portal event is an exit attempt
        if (lobbyManager.isPlayerInLobby(player)) {
            event.setCancelled(true);

            DungeonLobby lobby = lobbyManager.getPlayerLobby(player);
            if (lobby != null) {
                // Get their original location (where they were before entering the lobby)
                Location returnLoc = lobby.getPlayerOriginalLocation(player.getUniqueId());
                if (returnLoc == null) {
                    returnLoc = lobby.getReturnLocation();
                }
                if (returnLoc == null || returnLoc.getWorld() == null) {
                    // Fallback to player's bed spawn, then world spawn
                    returnLoc = player.getRespawnLocation();
                    if (returnLoc == null) {
                        returnLoc = plugin.getServer().getWorlds().get(0).getSpawnLocation();
                    }
                }

                // Add cooldown to prevent immediate re-entry
                recentlyTeleported.add(player.getUniqueId());
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    recentlyTeleported.remove(player.getUniqueId());
                }, 40L); // 2 second cooldown

                player.teleport(returnLoc);
                player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.2f);
                player.sendMessage("\u00A7aYou have left the dungeon lobby.");

                // Remove player from lobby
                lobbyManager.leaveLobby(player);
            }
            return;
        }

        DungeonPortal portal = portalManager.getPortalNearLocation(player.getLocation(), 3.0);

        if (portal == null) {
            return; // Regular nether portal, let it proceed
        }

        // Cancel the nether teleport
        event.setCancelled(true);

        // Teleport to lobby
        teleportPlayerToLobby(player, portal);
    }

    /**
     * Teleports a player to a dungeon lobby.
     * Creates a new lobby if needed.
     *
     * @param player The player to teleport
     * @param portal The dungeon portal they entered
     */
    private void teleportPlayerToLobby(Player player, DungeonPortal portal) {
        // Check if player is already in a dungeon or lobby
        if (dungeonManager.isPlayerInDungeon(player)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        if (lobbyManager.isPlayerInLobby(player)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Validate portal
        if (!portal.isActive() || !portal.isValidFrame()) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Validate key
        if (!portalManager.validateKey(player, portal)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Get dungeon
        Dungeon dungeon = dungeonManager.getDungeon(portal.getDungeonId());
        if (dungeon == null) {
            plugin.getLogger().warning("Dungeon not found: " + portal.getDungeonId());
            return;
        }

        // Create party set with just this player
        Set<UUID> party = new HashSet<>();
        party.add(player.getUniqueId());

        // Save player's current location before teleporting
        Location originalLocation = player.getLocation().clone();

        // Create lobby
        DungeonLobby lobby = lobbyManager.createLobbyForParty(dungeon, party);
        if (lobby == null) {
            plugin.getLogger().severe("Failed to create lobby for player: " + player.getName());
            return;
        }

        // Save original location to lobby for restoration on disconnect/shutdown
        lobby.savePlayerOriginalLocation(player.getUniqueId(), originalLocation);

        // Set return location to player's original location (where they were before entering)
        lobby.setReturnLocation(originalLocation);

        // Store the portal ID in the lobby so it can be destroyed when dungeon starts
        lobby.setEntryPortalId(portal.getId());

        // Add to recently teleported to prevent immediate exit
        recentlyTeleported.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            recentlyTeleported.remove(player.getUniqueId());
        }, 40L); // 2 second cooldown

        // Teleport player
        lobbyManager.teleportToLobby(player, lobby.getInstanceId());

        // Play effects
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.2f);

        // Don't destroy portal here - it should persist until dungeon starts
        // Portal will be cleared in LobbyManager.startDungeon()

        plugin.getLogger().info("Player " + player.getName() + " teleported to lobby for dungeon: " + dungeon.getId());
    }

    /**
     * Handles portal frame block being broken.
     * Destroys dungeon entry portals, but protects exit portal blocks.
     * If portal has an active lobby, destroys the lobby and ejects players.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // Check if this is a crying obsidian or nether portal block
        if (block.getType() != Material.CRYING_OBSIDIAN && block.getType() != Material.NETHER_PORTAL) {
            return;
        }

        // Check if this block is part of an exit portal (in a lobby) - protect it
        DungeonLobby lobby = lobbyManager.getLobbyNearExitPortal(block.getLocation(), 3.0);
        if (lobby != null) {
            // Block is part of an exit portal - prevent breaking
            event.setCancelled(true);
            return;
        }

        // Check if this block is part of a dungeon entry portal
        DungeonPortal portal = portalManager.getPortalAtLocation(block.getLocation());
        if (portal != null) {
            // Check if there's a lobby associated with this portal
            DungeonLobby associatedLobby = findLobbyByEntryPortal(portal.getId());
            if (associatedLobby != null) {
                // Eject all players and destroy the lobby
                ejectLobbyPlayers(associatedLobby, block.getLocation());
                lobbyManager.destroyLobby(associatedLobby.getInstanceId());
                plugin.getLogger().info("Lobby " + associatedLobby.getInstanceId() +
                                       " destroyed due to entry portal break");
            }

            // Portal was broken - destroy the portal
            destroyPortal(portal);
            plugin.getLogger().info("Portal " + portal.getId() + " destroyed due to block break at " +
                                   block.getLocation().getBlockX() + ", " +
                                   block.getLocation().getBlockY() + ", " +
                                   block.getLocation().getBlockZ());
        }
    }

    /**
     * Finds a lobby that was entered via the given portal ID.
     */
    private DungeonLobby findLobbyByEntryPortal(UUID portalId) {
        for (DungeonLobby lobby : lobbyManager.getAllLobbies().values()) {
            if (portalId.equals(lobby.getEntryPortalId())) {
                return lobby;
            }
        }
        return null;
    }

    /**
     * Ejects all players from a lobby, sending them back to their original locations.
     */
    private void ejectLobbyPlayers(DungeonLobby lobby, Location portalLocation) {
        for (UUID playerUuid : lobby.getPlayersInLobby()) {
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                // Get their original location, or fallback to the portal location, or world spawn
                Location returnLoc = lobby.getPlayerOriginalLocation(playerUuid);
                if (returnLoc == null) {
                    returnLoc = portalLocation;
                }
                if (returnLoc == null || returnLoc.getWorld() == null) {
                    returnLoc = org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
                }

                player.teleport(returnLoc);
                player.sendMessage("\u00A7c\u00A7lThe dungeon portal was destroyed! You have been ejected.");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
            }
        }
    }

    /**
     * Handles liquid (water/lava) flowing into portal blocks.
     * Destroys dungeon entry portals, protects exit portals.
     * If portal has an active lobby, destroys the lobby and ejects players.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();

        // Check if liquid is flowing into a nether portal block
        if (toBlock.getType() != Material.NETHER_PORTAL) {
            return;
        }

        // Check if this is an exit portal - protect it
        DungeonLobby lobby = lobbyManager.getLobbyNearExitPortal(toBlock.getLocation(), 3.0);
        if (lobby != null) {
            event.setCancelled(true);
            return;
        }

        // Check if this portal block is part of a dungeon entry portal
        DungeonPortal portal = portalManager.getPortalAtLocation(toBlock.getLocation());
        if (portal == null) {
            return;
        }

        // Check if there's a lobby associated with this portal
        DungeonLobby associatedLobby = findLobbyByEntryPortal(portal.getId());
        if (associatedLobby != null) {
            // Eject all players and destroy the lobby
            ejectLobbyPlayers(associatedLobby, toBlock.getLocation());
            lobbyManager.destroyLobby(associatedLobby.getInstanceId());
            plugin.getLogger().info("Lobby " + associatedLobby.getInstanceId() +
                                   " destroyed due to entry portal liquid damage");
        }

        // Liquid is breaking our portal - destroy it
        destroyPortal(portal);
        plugin.getLogger().info("Portal " + portal.getId() + " destroyed due to liquid flow");
    }

    /**
     * Prevents dungeon portals from creating nether portal links.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPortalCreate(PortalCreateEvent event) {
        // Check if any of the blocks are part of a dungeon portal
        for (org.bukkit.block.BlockState state : event.getBlocks()) {
            DungeonPortal portal = portalManager.getPortalAtLocation(state.getLocation());
            if (portal != null) {
                // This is a dungeon portal area - prevent vanilla portal creation
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Destroys a portal completely - clears interior and removes from storage.
     *
     * @param portal The portal to destroy
     */
    private void destroyPortal(DungeonPortal portal) {
        // Clear the portal interior (remove nether portal blocks)
        clearPortalInterior(portal);

        // Play break sound
        Location loc = getPortalCenter(portal);
        if (loc != null && loc.getWorld() != null) {
            loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
            loc.getWorld().spawnParticle(Particle.PORTAL, loc, 50, 0.5, 1, 0.5, 0.5);
        }

        // Remove from storage
        portalManager.removePortal(portal.getId());
    }

    /**
     * Gets the center location of a portal's interior.
     *
     * @param portal The portal
     * @return The center location
     */
    private Location getPortalCenter(DungeonPortal portal) {
        Set<Location> interior = portal.getInteriorBlocks();
        if (interior.isEmpty()) {
            return portal.getLocation().clone().add(1.5, 2.0, 0.5);
        }

        double x = 0, y = 0, z = 0;
        for (Location loc : interior) {
            x += loc.getX() + 0.5;
            y += loc.getY() + 0.5;
            z += loc.getZ() + 0.5;
        }
        int count = interior.size();
        return new Location(portal.getLocation().getWorld(), x / count, y / count, z / count);
    }

    /**
     * Unregisters this handler from event handling.
     */
    public void unregister() {
        HandlerList.unregisterAll(this);
    }
}
