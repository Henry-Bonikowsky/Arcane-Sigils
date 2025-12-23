package com.zenax.dungeons.generation;

import com.zenax.dungeons.generation.algorithm.NoiseGenerator;
import com.zenax.dungeons.generation.room.Room;
import com.zenax.dungeons.generation.room.RoomType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.Random;

/**
 * Generates organic cave structures using noise algorithms.
 * Creates natural-looking cave systems for dungeon variety.
 */
public class CaveGenerator {
    private final NoiseGenerator noiseGenerator;
    private final Random random;

    /**
     * Creates a new cave generator with the given seed.
     *
     * @param seed The seed for random generation
     */
    public CaveGenerator(long seed) {
        this.noiseGenerator = new NoiseGenerator(seed);
        this.random = new Random(seed);
    }

    /**
     * Generates a cave by carving through solid stone using noise.
     *
     * @param world The world to generate in
     * @param center The center point of the cave
     * @param radius The approximate radius of the cave
     * @param seed The seed for this specific cave
     * @return The generated Room object representing the cave
     */
    public Room generateCave(World world, Location center, int radius, long seed) {
        NoiseGenerator caveNoise = new NoiseGenerator(seed);

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        // Calculate bounds
        BoundingBox bounds = new BoundingBox(
            centerX - radius, centerY - radius / 2, centerZ - radius,
            centerX + radius, centerY + radius / 2, centerZ + radius
        );

        // Carve cave using 3D noise - just remove blocks, don't place any
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius / 2; y <= centerY + radius / 2; y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    double distance = Math.sqrt(
                        Math.pow(x - centerX, 2) +
                        Math.pow((y - centerY) * 1.5, 2) +
                        Math.pow(z - centerZ, 2)
                    );

                    if (distance > radius) {
                        continue;
                    }

                    // Use 3D noise for organic shape
                    double noise = caveNoise.octaveNoise3D(
                        x * 0.15, y * 0.15, z * 0.15,
                        3, 0.5, 1.0
                    );

                    // Fade out at edges
                    double fadeFactor = 1.0 - (distance / radius);
                    double carveThreshold = 0.2 - (fadeFactor * 0.4);

                    // Carve where noise exceeds threshold
                    if (noise > carveThreshold) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
        }

        // Create room object
        String caveId = "cave_" + System.nanoTime() + "_" + random.nextInt(1000);
        Room cave = new Room(caveId, RoomType.CAVE, bounds, center.clone());

        // Add spawn points
        addCaveSpawnPoints(cave, world, centerX, centerY, centerZ, radius);

        return cave;
    }

    /**
     * Smooths the cave floor to make it more walkable.
     *
     * @param world The world
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param centerZ Center Z coordinate
     * @param radius Cave radius
     */
    private void smoothCaveFloor(World world, int centerX, int centerY, int centerZ, int radius) {
        int floorY = centerY - radius / 2;

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                double distance = Math.sqrt(
                    Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2)
                );

                if (distance > radius) {
                    continue;
                }

                // Find the floor level (first air block from bottom)
                for (int y = floorY; y <= centerY + radius / 2; y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.AIR &&
                        world.getBlockAt(x, y - 1, z).getType() != Material.AIR) {

                        // Make floor solid
                        world.getBlockAt(x, y - 1, z).setType(Material.STONE);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Adds spawn points scattered throughout the cave.
     *
     * @param cave The cave room
     * @param world The world
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param centerZ Center Z coordinate
     * @param radius Cave radius
     */
    private void addCaveSpawnPoints(Room cave, World world, int centerX, int centerY, int centerZ, int radius) {
        int spawnCount = 5 + random.nextInt(6); // 5-10 spawn points

        for (int i = 0; i < spawnCount; i++) {
            // Random position within radius
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = random.nextDouble() * radius * 0.7; // Keep away from edges

            int x = centerX + (int) (Math.cos(angle) * dist);
            int z = centerZ + (int) (Math.sin(angle) * dist);

            // Find floor level
            for (int y = centerY - radius / 2; y <= centerY + radius / 2; y++) {
                if (world.getBlockAt(x, y, z).getType() == Material.AIR &&
                    world.getBlockAt(x, y - 1, z).getType() != Material.AIR) {

                    // Make sure there's headroom
                    if (world.getBlockAt(x, y + 1, z).getType() == Material.AIR) {
                        cave.addSpawnPoint(new Location(world, x + 0.5, y, z + 0.5));
                        break;
                    }
                }
            }
        }
    }

    /**
     * Selects a material for cave walls based on noise value.
     *
     * @param noise The noise value
     * @return The selected material
     */
    private Material selectCaveMaterial(double noise) {
        if (noise < -0.2) {
            return Material.COBBLESTONE;
        } else if (noise < 0.0) {
            return Material.STONE;
        } else {
            return Material.ANDESITE;
        }
    }

    /**
     * Generates a natural tunnel between two points.
     *
     * @param world The world
     * @param from Starting location
     * @param to Ending location
     * @param width Tunnel width
     */
    public void generateTunnel(World world, Location from, Location to, int width) {
        int x1 = from.getBlockX();
        int y1 = from.getBlockY();
        int z1 = from.getBlockZ();
        int x2 = to.getBlockX();
        int y2 = to.getBlockY();
        int z2 = to.getBlockZ();

        // Calculate steps
        int distance = (int) Math.ceil(from.distance(to));
        double dx = (x2 - x1) / (double) distance;
        double dy = (y2 - y1) / (double) distance;
        double dz = (z2 - z1) / (double) distance;

        // Carve tunnel
        for (int step = 0; step <= distance; step++) {
            int x = (int) (x1 + dx * step);
            int y = (int) (y1 + dy * step);
            int z = (int) (z1 + dz * step);

            // Use noise to vary tunnel shape
            double noise = noiseGenerator.noise3D(x * 0.2, y * 0.2, z * 0.2);
            int localWidth = width + (int) (noise * 2);

            // Carve spherical area
            for (int ox = -localWidth; ox <= localWidth; ox++) {
                for (int oy = -localWidth / 2; oy <= localWidth / 2; oy++) {
                    for (int oz = -localWidth; oz <= localWidth; oz++) {
                        double dist = Math.sqrt(ox * ox + oy * oy * 2 + oz * oz);
                        if (dist <= localWidth) {
                            world.getBlockAt(x + ox, y + oy, z + oz).setType(Material.AIR);
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates a cave pool (water or lava).
     *
     * @param world The world
     * @param location The pool location
     * @param radius Pool radius
     * @param isLava true for lava, false for water
     */
    public void generatePool(World world, Location location, int radius, boolean isLava) {
        Material liquid = isLava ? Material.LAVA : Material.WATER;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        for (int ox = -radius; ox <= radius; ox++) {
            for (int oz = -radius; oz <= radius; oz++) {
                double dist = Math.sqrt(ox * ox + oz * oz);
                if (dist <= radius) {
                    // Carve out a shallow depression
                    world.getBlockAt(x + ox, y - 1, z + oz).setType(Material.STONE);
                    world.getBlockAt(x + ox, y, z + oz).setType(liquid);
                }
            }
        }
    }
}
