package com.zenax.dungeons.portal;

import com.zenax.dungeons.dungeon.Dungeon;
import com.zenax.dungeons.dungeon.DungeonManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all dungeon portals.
 * Handles portal creation, removal, validation, and persistence.
 */
public class PortalManager {
    private final Plugin plugin;
    private final DungeonManager dungeonManager;
    private final Map<UUID, DungeonPortal> portals;
    private final File portalsFile;

    /**
     * Creates a new portal manager.
     *
     * @param dungeonManager The dungeon manager instance
     */
    public PortalManager(DungeonManager dungeonManager) {
        this.plugin = com.zenax.dungeons.DungeonsAddon.getInstance().getPlugin();
        this.dungeonManager = dungeonManager;
        this.portals = new ConcurrentHashMap<>();
        this.portalsFile = new File(com.zenax.dungeons.DungeonsAddon.getInstance().getDataFolder(), "portals.yml");
    }

    /**
     * Creates a new dungeon portal.
     *
     * @param dungeonId ID of the dungeon this portal leads to
     * @param location Location of the bottom-left corner of the portal frame
     * @param requiredKeyItemId ID of the key item required (null if none)
     * @param axis The axis orientation of the portal
     * @return The created DungeonPortal, or null if creation failed
     */
    public DungeonPortal createPortal(String dungeonId, Location location, String requiredKeyItemId, org.bukkit.Axis axis) {
        return createPortal(dungeonId, location, Material.CRYING_OBSIDIAN, requiredKeyItemId, axis);
    }

    /**
     * Creates a new dungeon portal with a custom frame block.
     *
     * @param dungeonId ID of the dungeon this portal leads to
     * @param location Location of the bottom-left corner of the portal frame
     * @param frameBlock Material for the portal frame
     * @param requiredKeyItemId ID of the key item required (null if none)
     * @param axis The axis orientation of the portal
     * @return The created DungeonPortal, or null if creation failed
     */
    public DungeonPortal createPortal(String dungeonId, Location location, Material frameBlock, String requiredKeyItemId, org.bukkit.Axis axis) {
        // Validate dungeon exists
        if (!dungeonManager.dungeonExists(dungeonId)) {
            plugin.getLogger().warning("Cannot create portal: Dungeon not found: " + dungeonId);
            return null;
        }

        // Check if portal already exists at this location
        if (getPortalAtLocation(location) != null) {
            plugin.getLogger().warning("Cannot create portal: Portal already exists at this location");
            return null;
        }

        UUID portalId = UUID.randomUUID();
        DungeonPortal portal = new DungeonPortal(portalId, dungeonId, location, frameBlock, requiredKeyItemId, axis, true);

        // Validate frame structure
        if (!portal.isValidFrame()) {
            plugin.getLogger().warning("Cannot create portal: Invalid frame structure at location");
            return null;
        }

        portals.put(portalId, portal);

        plugin.getLogger().info("Created portal " + portalId + " for dungeon: " + dungeonId +
                               " with " + portal.getInteriorBlocks().size() + " interior blocks");
        return portal;
    }

    /**
     * Removes a dungeon portal.
     *
     * @param portalId The UUID of the portal to remove
     * @return true if the portal was removed successfully
     */
    public boolean removePortal(UUID portalId) {
        DungeonPortal portal = portals.remove(portalId);
        if (portal == null) {
            return false;
        }

        plugin.getLogger().info("Removed portal: " + portalId);
        return true;
    }

    /**
     * Removes a dungeon portal.
     *
     * @param portal The portal to remove
     * @return true if the portal was removed successfully
     */
    public boolean removePortal(DungeonPortal portal) {
        return removePortal(portal.getId());
    }

    /**
     * Gets a portal by its UUID.
     *
     * @param portalId The portal UUID
     * @return The DungeonPortal, or null if not found
     */
    public DungeonPortal getPortal(UUID portalId) {
        return portals.get(portalId);
    }

    /**
     * Gets a portal near a specific location.
     * Checks distance to all portal interior blocks.
     *
     * @param location The location to check
     * @return The DungeonPortal near this location, or null if none exists
     */
    public DungeonPortal getPortalAtLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        for (DungeonPortal portal : portals.values()) {
            // Check if same world
            Location portalLoc = portal.getLocation();
            if (portalLoc.getWorld() == null || !portalLoc.getWorld().equals(location.getWorld())) {
                continue;
            }

            // Check distance to interior blocks
            for (Location interior : portal.getInteriorBlocks()) {
                if (interior.getBlockX() == location.getBlockX() &&
                    interior.getBlockY() == location.getBlockY() &&
                    interior.getBlockZ() == location.getBlockZ()) {
                    return portal;
                }
            }

            // Check distance to frame blocks
            for (Location frame : portal.getFrameBlocks()) {
                if (frame.getBlockX() == location.getBlockX() &&
                    frame.getBlockY() == location.getBlockY() &&
                    frame.getBlockZ() == location.getBlockZ()) {
                    return portal;
                }
            }
        }

        return null;
    }

    /**
     * Gets a portal within a certain distance of a location.
     *
     * @param location The location to check
     * @param maxDistance Maximum distance to check
     * @return The closest DungeonPortal, or null if none within range
     */
    public DungeonPortal getPortalNearLocation(Location location, double maxDistance) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        DungeonPortal closest = null;
        double closestDist = maxDistance + 1;

        for (DungeonPortal portal : portals.values()) {
            Location portalLoc = portal.getLocation();
            if (portalLoc.getWorld() == null || !portalLoc.getWorld().equals(location.getWorld())) {
                continue;
            }

            // Check distance to each interior block
            for (Location interior : portal.getInteriorBlocks()) {
                double dist = location.distance(interior.clone().add(0.5, 0.5, 0.5));
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = portal;
                }
            }
        }

        return closestDist <= maxDistance ? closest : null;
    }

    /**
     * Gets all portals for a specific dungeon.
     *
     * @param dungeonId The dungeon ID
     * @return List of all portals for this dungeon
     */
    public List<DungeonPortal> getPortalsForDungeon(String dungeonId) {
        List<DungeonPortal> result = new ArrayList<>();
        for (DungeonPortal portal : portals.values()) {
            if (portal.getDungeonId().equals(dungeonId)) {
                result.add(portal);
            }
        }
        return result;
    }

    /**
     * Gets all registered portals.
     *
     * @return Map of portal UUIDs to DungeonPortals
     */
    public Map<UUID, DungeonPortal> getAllPortals() {
        return new HashMap<>(portals);
    }

    /**
     * Gets the number of registered portals.
     *
     * @return The number of portals
     */
    public int getPortalCount() {
        return portals.size();
    }

    /**
     * Clears all registered portals and removes their blocks.
     */
    public void clearAllPortals() {
        for (DungeonPortal portal : new java.util.ArrayList<>(portals.values())) {
            // Clear portal interior blocks
            for (Location loc : portal.getInteriorBlocks()) {
                Block block = loc.getBlock();
                if (block.getType() == Material.NETHER_PORTAL) {
                    block.setType(Material.AIR, false);
                }
            }
            // Clear frame blocks
            for (Location loc : portal.getFrameBlocks()) {
                Block block = loc.getBlock();
                if (block.getType() == Material.CRYING_OBSIDIAN) {
                    block.setType(Material.AIR);
                }
            }
        }
        portals.clear();
        savePortals();
        plugin.getLogger().info("Cleared all portals");
    }

    /**
     * Validates if a player has the required key item for a portal.
     *
     * @param player The player to check
     * @param portal The portal to validate against
     * @return true if the player has the required key or no key is required
     */
    public boolean validateKey(Player player, DungeonPortal portal) {
        if (!portal.requiresKey()) {
            return true;
        }

        String requiredKeyItemId = portal.getRequiredKeyItemId();

        // Check player's inventory for the key item
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) {
                continue;
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                continue;
            }

            // Check if item has a custom model data or persistent data container key
            // This is a basic implementation - you may want to integrate with your item system
            if (meta.hasDisplayName() && meta.getDisplayName().contains(requiredKeyItemId)) {
                return true;
            }

            // Check lore for key ID
            if (meta.hasLore()) {
                List<String> lore = meta.getLore();
                if (lore != null) {
                    for (String line : lore) {
                        if (line.contains(requiredKeyItemId)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Validates all portals, checking frame structures and revalidating.
     * Removes portals with invalid frames.
     *
     * @return The number of invalid portals removed
     */
    public int validateAllPortals() {
        int removed = 0;
        Iterator<Map.Entry<UUID, DungeonPortal>> iterator = portals.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, DungeonPortal> entry = iterator.next();
            DungeonPortal portal = entry.getValue();

            if (!portal.isValidFrame()) {
                plugin.getLogger().warning("Portal " + entry.getKey() + " has invalid frame, removing...");
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " invalid portal(s)");
        }

        return removed;
    }

    /**
     * Spawns particle effects for all active portals.
     * Should be called periodically (e.g., every few ticks).
     */
    public void spawnAllParticleEffects() {
        for (DungeonPortal portal : portals.values()) {
            if (portal.isActive()) {
                portal.spawnParticleEffects();
            }
        }
    }

    /**
     * Loads all portals from the portals configuration file.
     *
     * @return The number of portals loaded
     */
    public int loadPortals() {
        if (!portalsFile.exists()) {
            plugin.getLogger().info("No portals file found, starting with empty portal list");
            return 0;
        }

        portals.clear();

        FileConfiguration config = YamlConfiguration.loadConfiguration(portalsFile);
        ConfigurationSection portalsSection = config.getConfigurationSection("portals");

        if (portalsSection == null) {
            plugin.getLogger().warning("No portals section found in portals.yml");
            return 0;
        }

        int loaded = 0;
        for (String portalIdStr : portalsSection.getKeys(false)) {
            ConfigurationSection portalSection = portalsSection.getConfigurationSection(portalIdStr);
            if (portalSection == null) {
                continue;
            }

            DungeonPortal portal = DungeonPortal.fromConfig(portalSection);
            if (portal != null) {
                portals.put(portal.getId(), portal);
                loaded++;

                // Regenerate portal blocks if they're missing
                if (portal.isActive() && portal.isValidFrame()) {
                    fillPortalInterior(portal);
                }
            } else {
                plugin.getLogger().warning("Failed to load portal: " + portalIdStr);
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " portal(s)");
        return loaded;
    }

    /**
     * Saves all portals to the portals configuration file.
     *
     * @return true if save was successful
     */
    public boolean savePortals() {
        try {
            // Create parent directories if they don't exist
            if (!portalsFile.getParentFile().exists()) {
                portalsFile.getParentFile().mkdirs();
            }

            FileConfiguration config = new YamlConfiguration();
            ConfigurationSection portalsSection = config.createSection("portals");

            for (Map.Entry<UUID, DungeonPortal> entry : portals.entrySet()) {
                ConfigurationSection portalSection = portalsSection.createSection(entry.getKey().toString());
                entry.getValue().saveToConfig(portalSection);
            }

            config.save(portalsFile);
            plugin.getLogger().info("Saved " + portals.size() + " portal(s) to file");
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save portals: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Fills the portal interior with nether portal blocks.
     * Used when loading portals from file to regenerate the visual blocks.
     *
     * @param portal The portal to fill
     */
    private void fillPortalInterior(DungeonPortal portal) {
        Set<Location> interiorBlocks = portal.getInteriorBlocks();
        org.bukkit.Axis axis = portal.getAxis();
        World world = portal.getLocation().getWorld();

        if (world == null || interiorBlocks.isEmpty()) {
            return;
        }

        for (Location loc : interiorBlocks) {
            Block block = loc.getBlock();
            if (block.getType().isAir() || block.getType() == Material.FIRE) {
                org.bukkit.block.data.BlockData data = Material.NETHER_PORTAL.createBlockData();
                if (data instanceof org.bukkit.block.data.Orientable orientable) {
                    orientable.setAxis(axis);
                }
                // Use false for physics - avoid POI conflicts
                block.setBlockData(data, false);
            }
        }

        plugin.getLogger().info("Regenerated portal blocks for portal " + portal.getId());
    }

    /**
     * Shuts down the portal manager, saving all portals.
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down PortalManager...");
        savePortals();
        portals.clear();
        plugin.getLogger().info("PortalManager shutdown complete");
    }
}
