package com.zenax.dungeons.generation;

import com.zenax.dungeons.generation.room.Room;
import com.zenax.dungeons.generation.room.RoomType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.Map;
import java.util.Random;

/**
 * Generates rectangular rooms in the dungeon.
 * Handles placement of floors, walls, ceilings, and marking special locations.
 */
public class RoomGenerator {
    private final Random random;

    /**
     * Creates a new room generator with the given seed.
     *
     * @param seed The seed for random generation
     */
    public RoomGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generates a room by carving out the interior from solid stone.
     * The area should already be filled with stone by DungeonGenerator.
     *
     * @param world The world to generate in
     * @param bounds The bounding box defining the room's space
     * @param roomType The type of room to generate
     * @param themeMaterials Map of material types to their actual materials
     * @return The generated Room object
     */
    public Room generateRoom(World world, BoundingBox bounds, RoomType roomType, Map<String, Material> themeMaterials) {
        Material floorMaterial = themeMaterials.getOrDefault("floor", Material.STONE);

        int minX = (int) bounds.getMinX();
        int minY = (int) bounds.getMinY();
        int minZ = (int) bounds.getMinZ();
        int maxX = (int) bounds.getMaxX();
        int maxY = (int) bounds.getMaxY();
        int maxZ = (int) bounds.getMaxZ();

        // Carve out the room interior (leave 1-block walls which are already stone)
        for (int x = minX + 1; x < maxX; x++) {
            for (int z = minZ + 1; z < maxZ; z++) {
                // Floor - use floor material
                world.getBlockAt(x, minY, z).setType(floorMaterial);

                // Carve air above floor
                for (int y = minY + 1; y < maxY; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

        // Calculate center
        double centerX = (minX + maxX) / 2.0;
        double centerY = minY + 1;
        double centerZ = (minZ + maxZ) / 2.0;
        Location center = new Location(world, centerX, centerY, centerZ);

        // Create room object
        String roomId = generateRoomId(roomType);
        Room room = new Room(roomId, roomType, bounds, center);

        // Mark spawn points and chest locations based on room type
        markSpecialLocations(room, world, bounds, roomType);

        return room;
    }

    /**
     * Marks special locations in the room (spawn points, chest locations).
     *
     * @param room The room to mark locations in
     * @param world The world
     * @param bounds The room bounds
     * @param roomType The type of room
     */
    private void markSpecialLocations(Room room, World world, BoundingBox bounds, RoomType roomType) {
        int minX = (int) bounds.getMinX() + 2;
        int minY = (int) bounds.getMinY() + 1;
        int minZ = (int) bounds.getMinZ() + 2;
        int maxX = (int) bounds.getMaxX() - 2;
        int maxZ = (int) bounds.getMaxZ() - 2;

        // Ensure we have space to place things
        if (maxX <= minX || maxZ <= minZ) {
            return;
        }

        switch (roomType) {
            case SPAWN:
                // Single spawn point in center
                room.addSpawnPoint(room.getCenter());
                break;

            case COMBAT:
                // Multiple spawn points scattered around
                int spawnCount = 3 + random.nextInt(3); // 3-5 spawn points
                for (int i = 0; i < spawnCount; i++) {
                    int x = minX + random.nextInt(maxX - minX + 1);
                    int z = minZ + random.nextInt(maxZ - minZ + 1);
                    room.addSpawnPoint(new Location(world, x + 0.5, minY, z + 0.5));
                }
                break;

            case TREASURE:
                // 2-4 chest locations
                int chestCount = 2 + random.nextInt(3);
                for (int i = 0; i < chestCount; i++) {
                    int x = minX + random.nextInt(maxX - minX + 1);
                    int z = minZ + random.nextInt(maxZ - minZ + 1);
                    room.addChestLocation(new Location(world, x, minY, z));
                }
                // Maybe some guard mobs
                if (random.nextDouble() < 0.7) {
                    room.addSpawnPoint(new Location(world, minX + 0.5, minY, minZ + 0.5));
                    room.addSpawnPoint(new Location(world, maxX + 0.5, minY, maxZ + 0.5));
                }
                break;

            case PUZZLE:
                // Chest location for reward
                room.addChestLocation(new Location(world,
                    (minX + maxX) / 2.0, minY, (minZ + maxZ) / 2.0));
                // Light mob spawns
                if (random.nextDouble() < 0.5) {
                    room.addSpawnPoint(new Location(world, minX + 0.5, minY, minZ + 0.5));
                }
                break;

            case BOSS:
                // Boss spawn in center
                room.addSpawnPoint(room.getCenter());
                // Boss loot chest
                room.addChestLocation(new Location(world,
                    room.getCenter().getX(), minY, room.getCenter().getZ() + 3));
                break;

            case CORRIDOR:
                // Maybe 1-2 spawn points in corridors
                if (random.nextDouble() < 0.3) {
                    int x = minX + random.nextInt(Math.max(1, maxX - minX + 1));
                    int z = minZ + random.nextInt(Math.max(1, maxZ - minZ + 1));
                    room.addSpawnPoint(new Location(world, x + 0.5, minY, z + 0.5));
                }
                break;

            case CAVE:
                // Handle in CaveGenerator
                break;
        }
    }

    /**
     * Generates a unique room ID.
     *
     * @param roomType The room type
     * @return A unique room ID string
     */
    private String generateRoomId(RoomType roomType) {
        return roomType.name().toLowerCase() + "_" + System.nanoTime() + "_" + random.nextInt(1000);
    }

    /**
     * Places a door in a wall at the specified location.
     *
     * @param world The world
     * @param location The location to place the door
     * @param width The width of the door opening
     */
    public void placeDoorway(World world, Location location, int width) {
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Clear a doorway (2 blocks high, width blocks wide)
        for (int dy = 0; dy < 2; dy++) {
            for (int dw = -width / 2; dw <= width / 2; dw++) {
                // Try both X and Z directions to handle walls in both orientations
                world.getBlockAt(x + dw, y + dy, z).setType(Material.AIR);
                world.getBlockAt(x, y + dy, z + dw).setType(Material.AIR);
            }
        }
    }
}
