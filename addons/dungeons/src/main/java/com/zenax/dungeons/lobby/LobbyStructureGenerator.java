package com.zenax.dungeons.lobby;

import com.zenax.dungeons.dungeon.Dungeon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Generates lobby portal structures.
 * This class handles leave portal generation and cleanup.
 */
public class LobbyStructureGenerator {

    /**
     * Generates a leave portal structure at the given location.
     * Uses crying obsidian frame with actual nether portal blocks.
     *
     * @param location The base location for the portal (bottom-left corner)
     */
    public static void generateLeavePortal(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Create a 4x5 crying obsidian frame
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

        // Send block updates to nearby players
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
    public static void removeLeavePortal(Location location) {
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
     * Checks if a location is within the leave portal area.
     *
     * @param location The location to check
     * @param portalLoc The portal base location
     * @return true if the location is within the leave portal
     */
    public static boolean isInLeavePortal(Location location, Location portalLoc) {
        if (portalLoc == null || location == null) return false;
        if (location.getWorld() == null || portalLoc.getWorld() == null) return false;
        if (!location.getWorld().equals(portalLoc.getWorld())) return false;

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
     * Generates gate barrier blocks at the specified location.
     *
     * @param gateLocation The location to generate the gate
     * @param gateHeight The height of the gate in blocks
     */
    public static void generateGate(Location gateLocation, int gateHeight) {
        World world = gateLocation.getWorld();
        if (world == null) return;

        int x = gateLocation.getBlockX();
        int y = gateLocation.getBlockY();
        int z = gateLocation.getBlockZ();

        for (int dy = 0; dy < gateHeight; dy++) {
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
     * @param gateHeight The height of the gate in blocks
     */
    public static void removeGate(Location gateLocation, int gateHeight) {
        World world = gateLocation.getWorld();
        if (world == null) return;

        int x = gateLocation.getBlockX();
        int y = gateLocation.getBlockY();
        int z = gateLocation.getBlockZ();

        for (int dy = 0; dy < gateHeight; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                Block block = world.getBlockAt(x + dx, y + dy, z);
                if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR);
                }
            }
        }
    }
}
