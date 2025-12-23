package com.zenax.dungeons.world;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Random;

/**
 * Manages the dedicated dungeon world.
 * Creates and maintains a void world specifically for dungeons and lobbies.
 * The world is deleted and recreated on each server startup for a clean slate.
 */
public class DungeonWorldManager {
    private final Plugin plugin;
    private World dungeonWorld;

    private static final String DUNGEON_WORLD_NAME = "dungeon_world";

    /**
     * Creates a new dungeon world manager.
     *
     * @param plugin The plugin instance
     */
    public DungeonWorldManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the dungeon world.
     * Deletes any existing dungeon world and creates a fresh one for a clean slate.
     *
     * @return true if initialization was successful
     */
    public boolean initialize() {
        // Delete existing dungeon world if it exists (clean slate each startup)
        World existingWorld = Bukkit.getWorld(DUNGEON_WORLD_NAME);
        if (existingWorld != null) {
            plugin.getLogger().info("Removing old dungeon world for clean slate...");
            deleteWorld(existingWorld);
        }

        // Create a fresh void world for dungeons
        plugin.getLogger().info("Creating fresh dungeon world...");

        WorldCreator creator = new WorldCreator(DUNGEON_WORLD_NAME);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);
        creator.generator(new VoidChunkGenerator());
        creator.generateStructures(false);

        dungeonWorld = creator.createWorld();

        if (dungeonWorld != null) {
            // Configure the world
            dungeonWorld.setDifficulty(Difficulty.NORMAL);
            dungeonWorld.setSpawnFlags(true, false); // Allow monsters, no animals
            dungeonWorld.setTime(18000); // Midnight
            dungeonWorld.setStorm(false);
            dungeonWorld.setThundering(false);
            dungeonWorld.setKeepSpawnInMemory(false);
            dungeonWorld.setAutoSave(false); // Don't save since we delete on restart

            // Set game rules
            dungeonWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            dungeonWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            dungeonWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false); // We control spawning
            dungeonWorld.setGameRule(GameRule.MOB_GRIEFING, false);
            dungeonWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            dungeonWorld.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
            dungeonWorld.setGameRule(GameRule.KEEP_INVENTORY, false);

            plugin.getLogger().info("Fresh dungeon world created successfully!");
            return true;
        } else {
            plugin.getLogger().severe("Failed to create dungeon world!");
            return false;
        }
    }

    /**
     * Deletes a world completely (unloads and removes files).
     *
     * @param world The world to delete
     */
    private void deleteWorld(World world) {
        String worldName = world.getName();

        // Teleport any players out of the world first
        World mainWorld = Bukkit.getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            // Use player's bed spawn if available, otherwise world spawn
            Location spawnLoc = player.getRespawnLocation();
            if (spawnLoc == null) {
                spawnLoc = mainWorld.getSpawnLocation();
            }
            player.teleport(spawnLoc);
        }

        // Unload the world
        Bukkit.unloadWorld(world, false);

        // Delete the world folder
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            deleteFolder(worldFolder);
            plugin.getLogger().info("Deleted old dungeon world folder: " + worldName);
        }
    }

    /**
     * Recursively deletes a folder and all its contents.
     *
     * @param folder The folder to delete
     */
    private void deleteFolder(File folder) {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteFolder(file);
                }
            }
        }
        folder.delete();
    }

    /**
     * Gets the dungeon world.
     *
     * @return The dungeon world, or null if not initialized
     */
    public World getDungeonWorld() {
        return dungeonWorld;
    }

    /**
     * Gets a location in the dungeon world at a specific grid position.
     * Useful for placing lobbies at predictable locations.
     *
     * @param gridX The X grid position
     * @param gridZ The Z grid position
     * @return A location at the grid position
     */
    public Location getGridLocation(int gridX, int gridZ) {
        if (dungeonWorld == null) {
            return null;
        }

        // Each grid cell is 100 blocks apart
        int x = gridX * 100;
        int z = gridZ * 100;
        int y = 64; // Standard height for dungeons

        return new Location(dungeonWorld, x, y, z);
    }

    /**
     * Gets the next available location for a new lobby/dungeon.
     *
     * @param instanceIndex The index of the instance (used for positioning)
     * @return A location for the new instance
     */
    public Location getNextInstanceLocation(int instanceIndex) {
        if (dungeonWorld == null) {
            return null;
        }

        // Spiral pattern for instance placement
        int gridX = 0;
        int gridZ = 0;

        if (instanceIndex > 0) {
            // Simple grid pattern: instances are placed in a grid
            int gridSize = 10; // 10x10 grid before wrapping
            gridX = instanceIndex % gridSize;
            gridZ = instanceIndex / gridSize;
        }

        return getGridLocation(gridX, gridZ);
    }

    /**
     * Checks if the dungeon world is ready for use.
     *
     * @return true if the world is ready
     */
    public boolean isReady() {
        return dungeonWorld != null;
    }

    /**
     * Unloads and deletes the dungeon world.
     * Called on plugin disable to ensure clean state for next startup.
     */
    public void shutdown() {
        if (dungeonWorld != null) {
            plugin.getLogger().info("Cleaning up dungeon world...");
            deleteWorld(dungeonWorld);
            dungeonWorld = null;
        }
    }

    /**
     * Resets the dungeon world by deleting and recreating it.
     * Teleports all players out first.
     *
     * @return true if reset was successful
     */
    public boolean resetWorld() {
        plugin.getLogger().info("Resetting dungeon world...");

        // Teleport players out first
        if (dungeonWorld != null) {
            World mainWorld = Bukkit.getWorlds().get(0);
            for (Player player : dungeonWorld.getPlayers()) {
                Location spawnLoc = player.getRespawnLocation();
                if (spawnLoc == null) {
                    spawnLoc = mainWorld.getSpawnLocation();
                }
                player.teleport(spawnLoc);
            }
        }

        // Delete and recreate
        shutdown();
        return initialize();
    }

    /**
     * A chunk generator that generates a void world.
     */
    private static class VoidChunkGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }

        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateBedrock() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }
}
