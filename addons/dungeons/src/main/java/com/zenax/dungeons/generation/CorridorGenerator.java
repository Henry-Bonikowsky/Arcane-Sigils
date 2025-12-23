package com.zenax.dungeons.generation;

import com.zenax.dungeons.generation.room.Room;
import com.zenax.dungeons.generation.room.RoomConnection;
import com.zenax.dungeons.generation.room.RoomType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

/**
 * Generates corridors to connect rooms in the dungeon.
 * Handles height differences with stairs and creates proper pathways.
 */
public class CorridorGenerator {
    private final RoomGenerator roomGenerator;

    /**
     * Creates a new corridor generator.
     *
     * @param seed The seed for random generation
     */
    public CorridorGenerator(long seed) {
        this.roomGenerator = new RoomGenerator(seed);
    }

    /**
     * Generates a corridor between two locations.
     *
     * @param world The world to generate in
     * @param from Starting location
     * @param to Ending location
     * @param width Corridor width
     * @return The generated corridor Room object
     */
    public Room generateCorridor(World world, Location from, Location to, int width) {
        int x1 = from.getBlockX();
        int y1 = from.getBlockY();
        int z1 = from.getBlockZ();
        int x2 = to.getBlockX();
        int y2 = to.getBlockY();
        int z2 = to.getBlockZ();

        // Determine path type based on height difference
        int heightDiff = Math.abs(y2 - y1);

        if (heightDiff > 3) {
            // Use stairs for significant height differences
            generateStaircaseCorridor(world, from, to, width);
        } else {
            // Use L-shaped path for same or similar height
            generateLShapedCorridor(world, from, to, width);
        }

        // Create bounding box for the corridor
        BoundingBox bounds = new BoundingBox(
            Math.min(x1, x2) - width,
            Math.min(y1, y2) - 1,
            Math.min(z1, z2) - width,
            Math.max(x1, x2) + width,
            Math.max(y1, y2) + 3,
            Math.max(z1, z2) + width
        );

        // Calculate center point
        Location center = new Location(world,
            (x1 + x2) / 2.0,
            (y1 + y2) / 2.0,
            (z1 + z2) / 2.0
        );

        // Create corridor room
        String corridorId = "corridor_" + System.nanoTime();
        return new Room(corridorId, RoomType.CORRIDOR, bounds, center);
    }

    /**
     * Generates an L-shaped corridor (horizontal then turn).
     *
     * @param world The world
     * @param from Starting location
     * @param to Ending location
     * @param width Corridor width
     */
    private void generateLShapedCorridor(World world, Location from, Location to, int width) {
        int x1 = from.getBlockX();
        int y1 = from.getBlockY();
        int z1 = from.getBlockZ();
        int x2 = to.getBlockX();
        int y2 = to.getBlockY();
        int z2 = to.getBlockZ();

        // Determine which direction to go first (random choice between X-first or Z-first)
        boolean xFirst = Math.abs(x2 - x1) > Math.abs(z2 - z1);

        if (xFirst) {
            // Go along X axis first, then Z
            carvePath(world, x1, y1, z1, x2, y1, z1, width, true);
            carvePath(world, x2, y1, z1, x2, y2, z2, width, false);
        } else {
            // Go along Z axis first, then X
            carvePath(world, x1, y1, z1, x1, y1, z2, width, false);
            carvePath(world, x1, y1, z2, x2, y2, z2, width, true);
        }
    }

    /**
     * Generates a corridor with stairs for height changes.
     *
     * @param world The world
     * @param from Starting location
     * @param to Ending location
     * @param width Corridor width
     */
    private void generateStaircaseCorridor(World world, Location from, Location to, int width) {
        int x1 = from.getBlockX();
        int y1 = from.getBlockY();
        int z1 = from.getBlockZ();
        int x2 = to.getBlockX();
        int y2 = to.getBlockY();
        int z2 = to.getBlockZ();

        // Calculate total distance
        int dx = x2 - x1;
        int dy = y2 - y1;
        int dz = z2 - z1;
        int horizontalDist = (int) Math.sqrt(dx * dx + dz * dz);

        // Go horizontal first
        int midX = x1 + dx / 2;
        int midZ = z1 + dz / 2;

        // First segment (horizontal)
        carvePath(world, x1, y1, z1, midX, y1, midZ, width, true);

        // Staircase segment
        generateStairs(world, midX, y1, midZ, midX, y2, midZ, width, dy > 0);

        // Final segment (horizontal)
        carvePath(world, midX, y2, midZ, x2, y2, z2, width, true);
    }

    /**
     * Carves a straight path between two points.
     *
     * @param world The world
     * @param x1 Start X
     * @param y1 Start Y
     * @param z1 Start Z
     * @param x2 End X
     * @param y2 End Y
     * @param z2 End Z
     * @param width Path width
     * @param handleHeight Whether to handle height differences gradually
     */
    private void carvePath(World world, int x1, int y1, int z1, int x2, int y2, int z2, int width, boolean handleHeight) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int dz = z2 - z1;
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));

        if (steps == 0) {
            return;
        }

        double stepX = dx / (double) steps;
        double stepY = handleHeight ? (dy / (double) steps) : 0;
        double stepZ = dz / (double) steps;

        for (int i = 0; i <= steps; i++) {
            int x = (int) (x1 + stepX * i);
            int y = handleHeight ? (int) (y1 + stepY * i) : y1;
            int z = (int) (z1 + stepZ * i);

            carveCorridorSection(world, x, y, z, width);
        }

        // If not handling height gradually, carve the end height
        if (!handleHeight && y2 != y1) {
            carveCorridorSection(world, x2, y2, z2, width);
        }
    }

    /**
     * Carves a single corridor cross-section through solid stone.
     * Just carves air - walls are the surrounding stone that's already there.
     *
     * @param world The world
     * @param x Center X coordinate
     * @param y Floor Y coordinate
     * @param z Center Z coordinate
     * @param width Corridor width
     */
    private void carveCorridorSection(World world, int x, int y, int z, int width) {
        int halfWidth = width / 2;

        // Carve air space (3 blocks high, width blocks wide)
        for (int ox = -halfWidth; ox <= halfWidth; ox++) {
            for (int oz = -halfWidth; oz <= halfWidth; oz++) {
                for (int oy = 1; oy <= 3; oy++) {
                    world.getBlockAt(x + ox, y + oy, z + oz).setType(Material.AIR);
                }
            }
        }
    }

    /**
     * Generates stairs between two Y levels.
     *
     * @param world The world
     * @param x X coordinate
     * @param y1 Start Y
     * @param z Z coordinate
     * @param x2 End X (usually same as x)
     * @param y2 End Y
     * @param z2 End Z (usually same as z)
     * @param width Staircase width
     * @param ascending true if going up, false if going down
     */
    private void generateStairs(World world, int x, int y1, int z, int x2, int y2, int z2, int width, boolean ascending) {
        int heightDiff = Math.abs(y2 - y1);
        int yDir = ascending ? 1 : -1;

        // Determine primary direction
        int dx = x2 - x;
        int dz = z2 - z;
        int horizontalDist = Math.max(Math.abs(dx), Math.abs(dz));

        // If no horizontal movement, just stack stairs vertically
        if (horizontalDist == 0) {
            for (int i = 0; i < heightDiff; i++) {
                int currentY = y1 + (i * yDir);
                carveCorridorSection(world, x, currentY, z, width);
                // Place stair blocks
                world.getBlockAt(x, currentY + yDir, z).setType(Material.STONE_STAIRS);
            }
            return;
        }

        // Distribute stairs along horizontal distance
        double stepX = dx / (double) heightDiff;
        double stepZ = dz / (double) heightDiff;

        for (int i = 0; i <= heightDiff; i++) {
            int currentX = (int) (x + stepX * i);
            int currentZ = (int) (z + stepZ * i);
            int currentY = y1 + (i * yDir);

            carveCorridorSection(world, currentX, currentY, currentZ, width);

            // Place stair block
            if (i < heightDiff) {
                world.getBlockAt(currentX, currentY + 1, currentZ).setType(Material.STONE_STAIRS);
            }
        }
    }

    /**
     * Creates a connection between two rooms.
     *
     * @param world The world
     * @param from Source room
     * @param to Destination room
     * @param width Corridor width
     * @return The room connection object
     */
    public RoomConnection connectRooms(World world, Room from, Room to, int width) {
        Location fromCenter = from.getCenter();
        Location toCenter = to.getCenter();

        // Generate the corridor
        Room corridor = generateCorridor(world, fromCenter, toCenter, width);

        // Create doors in the walls
        roomGenerator.placeDoorway(world, fromCenter, width);
        roomGenerator.placeDoorway(world, toCenter, width);

        // Create connection object
        Location connectionPoint = new Location(world,
            (fromCenter.getX() + toCenter.getX()) / 2,
            (fromCenter.getY() + toCenter.getY()) / 2,
            (fromCenter.getZ() + toCenter.getZ()) / 2
        );

        RoomConnection connection = new RoomConnection(from, to, connectionPoint, connectionPoint);

        // Add connection to both rooms
        from.addConnection(connection);
        to.addConnection(connection);

        return connection;
    }
}
