package com.zenax.dungeons.generation;

import com.zenax.dungeons.dungeon.Dungeon;
import com.zenax.dungeons.dungeon.DungeonDifficulty;
import com.zenax.dungeons.dungeon.DungeonInstance;
import com.zenax.dungeons.dungeon.ObjectiveMode;
import com.zenax.dungeons.generation.algorithm.NoiseGenerator;
import com.zenax.dungeons.generation.layout.DungeonLayout;
import com.zenax.dungeons.generation.layout.DungeonLayoutGenerator;
import com.zenax.dungeons.generation.layout.LayoutEdge;
import com.zenax.dungeons.generation.layout.LayoutNode;
import com.zenax.dungeons.generation.room.Room;
import com.zenax.dungeons.generation.room.RoomType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.function.Consumer;

/**
 * Main orchestrator for dungeon generation.
 * Uses a layout-first approach: generates abstract graph, then physical blocks.
 */
public class DungeonGenerator {
    private final Plugin plugin;
    private final Random random;

    // Grid cell size for room placement
    private static final int CELL_SIZE = 25;

    public DungeonGenerator(Plugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    /**
     * Generates a dungeon on the main thread.
     */
    public void generateDungeonAsync(Dungeon template, World world, Location origin,
                                    Consumer<Double> progressCallback,
                                    Consumer<DungeonInstance> completionCallback) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    DungeonInstance instance = generateDungeon(template, world, origin, progressCallback);
                    completionCallback.accept(instance);
                } catch (Exception e) {
                    e.printStackTrace();
                    completionCallback.accept(null);
                }
            }
        }.runTask(plugin);
    }

    public DungeonInstance generateDungeon(Dungeon template, World world, Location origin) {
        return generateDungeon(template, world, origin, null);
    }

    /**
     * Main generation method using layout-first approach.
     * Uses 4-phase generation: fill corridors, fill rooms, carve rooms, carve corridors.
     */
    public DungeonInstance generateDungeon(Dungeon template, World world, Location origin,
                                          Consumer<Double> progressCallback) {
        long seed = System.currentTimeMillis() + random.nextLong();
        updateProgress(progressCallback, 0.0);

        // Step 1: Generate abstract layout
        DungeonLayoutGenerator layoutGenerator = new DungeonLayoutGenerator(seed);
        DungeonLayout layout = layoutGenerator.generate(template);
        updateProgress(progressCallback, 0.05);

        plugin.getLogger().info("Generated dungeon layout: " + layout.getNodes().size() + " rooms");

        // Prepare for physical generation
        Map<String, Material> theme = template.getThemeMaterials();
        Material wallMat = theme.getOrDefault("wall", Material.STONE);
        int baseY = origin.getBlockY();
        int originX = origin.getBlockX();
        int originZ = origin.getBlockZ();

        // Pre-calculate room positions
        Map<LayoutNode, int[]> nodePositions = new HashMap<>();
        for (LayoutNode node : layout.getNodes()) {
            int roomX = originX + (node.getGridX() * CELL_SIZE);
            int roomZ = originZ + (node.getGridZ() * CELL_SIZE);
            nodePositions.put(node, new int[]{roomX, roomZ});
        }

        NoiseGenerator noise = new NoiseGenerator(seed);

        // ===== PHASE 1: Fill room stone =====
        for (LayoutNode node : layout.getNodes()) {
            int[] pos = nodePositions.get(node);
            fillRoomStone(world, node, pos[0], baseY, pos[1], wallMat, noise);
        }
        updateProgress(progressCallback, 0.2);

        // ===== PHASE 2: Fill corridor stone =====
        for (LayoutEdge edge : layout.getEdges()) {
            int[] posA = nodePositions.get(edge.getNodeA());
            int[] posB = nodePositions.get(edge.getNodeB());
            if (posA != null && posB != null) {
                fillCorridorStone(world, posA[0], baseY + 1, posA[1], posB[0], posB[1], wallMat, noise);
            }
        }
        updateProgress(progressCallback, 0.4);

        // ===== PHASE 3: Carve corridors =====
        for (LayoutEdge edge : layout.getEdges()) {
            int[] posA = nodePositions.get(edge.getNodeA());
            int[] posB = nodePositions.get(edge.getNodeB());
            if (posA != null && posB != null) {
                carveCorridorInterior(world, posA[0], baseY + 1, posA[1], posB[0], posB[1], noise);
            }
        }
        updateProgress(progressCallback, 0.6);

        // ===== PHASE 4: Carve rooms =====
        Map<LayoutNode, Room> nodeToRoom = new HashMap<>();
        for (LayoutNode node : layout.getNodes()) {
            int[] pos = nodePositions.get(node);
            Room room = carveRoomInterior(world, node, pos[0], baseY, pos[1], theme, noise);
            nodeToRoom.put(node, room);
        }
        updateProgress(progressCallback, 0.8);

        // Calculate spawn location
        Location spawnLocation = origin.clone().add(0, 2, 0);
        if (layout.getSpawnNode() != null) {
            Room spawnRoom = nodeToRoom.get(layout.getSpawnNode());
            if (spawnRoom != null) {
                spawnLocation = spawnRoom.getCenter().add(0, 1, 0);
            }
        }

        // Create dungeon instance
        DungeonDifficulty difficulty = template.getDefaultDifficulty();
        ObjectiveMode objectiveMode = template.getAvailableObjectiveModes().isEmpty() ?
                                     ObjectiveMode.BOSS_KILL :
                                     template.getAvailableObjectiveModes().get(0);

        DungeonInstance instance = new DungeonInstance(template, difficulty, objectiveMode, world, spawnLocation);

        for (Map.Entry<LayoutNode, Room> entry : nodeToRoom.entrySet()) {
            instance.addRoom(entry.getValue().getId(), entry.getValue().getCenter());
        }

        updateProgress(progressCallback, 0.95);

        instance.setState(com.zenax.dungeons.dungeon.DungeonState.LOBBY);
        updateProgress(progressCallback, 1.0);

        plugin.getLogger().info("Dungeon generation complete: " + layout.getNodes().size() + " rooms");
        return instance;
    }

    /**
     * PHASE 1: Fills room volume with varied stone mix.
     */
    private void fillRoomStone(World world, LayoutNode node, int centerX, int baseY, int centerZ,
                               Material wallMat, NoiseGenerator noise) {
        int halfWidth = 16;
        int halfDepth = 16;
        int height = 12;

        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int z = -halfDepth; z <= halfDepth; z++) {
                for (int y = -2; y <= height + 2; y++) {
                    Block block = world.getBlockAt(centerX + x, baseY + y, centerZ + z);
                    setWallBlock(block, wallMat, noise, centerX + x, centerZ + z);
                }
            }
        }
    }

    /**
     * PHASE 4: Carves room interior - ONLY places air using lobby cave noise logic.
     */
    private Room carveRoomInterior(World world, LayoutNode node, int centerX, int baseY, int centerZ,
                                   Map<String, Material> theme, NoiseGenerator noise) {
        int halfWidth = 14;
        int halfDepth = 14;
        int height = 12;

        NoiseGenerator roomNoise = new NoiseGenerator(noise.getSeed() + node.getId());
        Random random = new Random(noise.getSeed() + node.getId());

        for (int x = -halfWidth - 2; x <= halfWidth + 2; x++) {
            for (int z = -halfDepth - 2; z <= halfDepth + 2; z++) {
                int worldX = centerX + x;
                int worldZ = centerZ + z;

                // Noise for organic shape (same as lobby)
                double ceilingNoise = roomNoise.octaveNoise2D(worldX * 0.12, worldZ * 0.12, 3, 0.5, 1.0);
                double floorNoise = roomNoise.octaveNoise2D(worldX * 0.15 + 100, worldZ * 0.15 + 100, 2, 0.5, 1.0);
                double widthNoise = roomNoise.octaveNoise2D(worldX * 0.08, worldZ * 0.1, 2, 0.5, 1.0);

                double baseCaveWidth = halfWidth + widthNoise * 3;
                double baseCeilingHeight = 7 + ceilingNoise * 3;
                double baseFloorOffset = floorNoise * 2;

                double distFromCenter = Math.sqrt(x * x + z * z);

                // Circular falloff
                double edgeFactor = 1.0 - (distFromCenter / (halfWidth + 3));
                edgeFactor = Math.max(0, Math.min(1, edgeFactor));

                double effectiveWidth = baseCaveWidth * edgeFactor;
                if (distFromCenter > effectiveWidth + 2) continue;

                // Wall blend factor
                double wallBlend = 1.0;
                if (distFromCenter > effectiveWidth - 2) {
                    wallBlend = (effectiveWidth + 2 - distFromCenter) / 4.0;
                }
                wallBlend = Math.max(0, Math.min(1, wallBlend));

                // 3D noise for wall irregularity
                double wallNoise3D = roomNoise.octaveNoise3D(worldX * 0.2, baseY * 0.15, worldZ * 0.2, 2, 0.5, 1.0);
                wallBlend += wallNoise3D * 0.3;
                wallBlend = Math.max(0, Math.min(1, wallBlend));

                if (wallBlend < 0.2) continue; // Don't carve - solid wall

                // Floor and ceiling for this column
                int floorY = baseY + (int) Math.round(baseFloorOffset);
                int ceilingY = baseY + (int) Math.round(baseCeilingHeight * wallBlend);

                // ONLY CARVE AIR
                for (int y = -2; y <= height + 3; y++) {
                    int worldY = baseY + y;

                    if (worldY > floorY && worldY < ceilingY) {
                        // Skip some blocks in transitional zone for organic walls
                        if (wallBlend < 0.5 && random.nextFloat() < (0.5 - wallBlend)) continue;
                        world.getBlockAt(worldX, worldY, worldZ).setType(Material.AIR);
                    }
                }
            }
        }

        // Alcoves - carve more air
        carveRoomAlcoves(world, roomNoise, random, centerX, baseY, centerZ, halfWidth, halfDepth, height);

        // Formations
        addRoomFormations(world, roomNoise, random, centerX, baseY, centerZ, halfWidth, halfDepth, height);

        // Room object
        BoundingBox bounds = new BoundingBox(centerX - halfWidth, baseY, centerZ - halfDepth,
                                              centerX + halfWidth, baseY + height, centerZ + halfDepth);
        Location center = new Location(world, centerX, baseY + 2, centerZ);
        String roomId = node.getType().name().toLowerCase() + "_" + node.getId();
        Room room = new Room(roomId, node.getType(), bounds, center);
        addRoomSpawnPoints(room, world, node, centerX - halfWidth, baseY, centerZ - halfDepth, centerX + halfWidth, centerZ + halfDepth);

        return room;
    }

    /**
     * Sets a wall block with material variation.
     */
    private void setWallBlock(Block block, Material baseMat, NoiseGenerator noise, int x, int z) {
        double variation = noise.noise2D(x * 0.25, z * 0.25);
        if (variation > 0.4) {
            block.setType(Material.COBBLESTONE);
        } else if (variation > 0.1) {
            block.setType(baseMat);
        } else if (variation > -0.2) {
            block.setType(Material.ANDESITE);
        } else {
            block.setType(Material.DIORITE);
        }
    }

    /**
     * Carves alcoves in room walls - ONLY carves air.
     */
    private void carveRoomAlcoves(World world, NoiseGenerator noise, Random random,
                                  int centerX, int baseY, int centerZ,
                                  int halfWidth, int halfLength, int height) {
        // X walls
        for (int z = -halfLength + 2; z <= halfLength - 2; z++) {
            for (int y = 2; y < height - 2; y++) {
                double noiseVal = noise.noise3D(
                    (centerX - halfWidth) * 0.25, (baseY + y) * 0.3, (centerZ + z) * 0.25
                );
                if (noiseVal > 0.35) {
                    int depth = noiseVal > 0.5 ? 2 : 1;
                    for (int d = 0; d < depth; d++) {
                        world.getBlockAt(centerX - halfWidth + 1 + d, baseY + y, centerZ + z).setType(Material.AIR);
                    }
                }

                noiseVal = noise.noise3D(
                    (centerX + halfWidth) * 0.25, (baseY + y) * 0.3, (centerZ + z) * 0.25
                );
                if (noiseVal > 0.35) {
                    int depth = noiseVal > 0.5 ? 2 : 1;
                    for (int d = 0; d < depth; d++) {
                        world.getBlockAt(centerX + halfWidth - 1 - d, baseY + y, centerZ + z).setType(Material.AIR);
                    }
                }
            }
        }

        // Z walls
        for (int x = -halfWidth + 2; x <= halfWidth - 2; x++) {
            for (int y = 2; y < height - 2; y++) {
                double noiseVal = noise.noise3D(
                    (centerX + x) * 0.25, (baseY + y) * 0.3, (centerZ - halfLength) * 0.25
                );
                if (noiseVal > 0.35) {
                    int depth = noiseVal > 0.5 ? 2 : 1;
                    for (int d = 0; d < depth; d++) {
                        world.getBlockAt(centerX + x, baseY + y, centerZ - halfLength + 1 + d).setType(Material.AIR);
                    }
                }

                noiseVal = noise.noise3D(
                    (centerX + x) * 0.25, (baseY + y) * 0.3, (centerZ + halfLength) * 0.25
                );
                if (noiseVal > 0.35) {
                    int depth = noiseVal > 0.5 ? 2 : 1;
                    for (int d = 0; d < depth; d++) {
                        world.getBlockAt(centerX + x, baseY + y, centerZ + halfLength - 1 - d).setType(Material.AIR);
                    }
                }
            }
        }
    }

    /**
     * Adds stalactites and stalagmites to room.
     */
    private void addRoomFormations(World world, NoiseGenerator noise, Random random,
                                   int centerX, int baseY, int centerZ,
                                   int halfWidth, int halfLength, int height) {
        for (int x = -halfWidth + 2; x <= halfWidth - 2; x++) {
            for (int z = -halfLength + 2; z <= halfLength - 2; z++) {
                // Skip center area
                if (Math.abs(x) <= 3 && Math.abs(z) <= 3) continue;

                int worldX = centerX + x;
                int worldZ = centerZ + z;

                double formationNoise = noise.noise2D(worldX * 0.3, worldZ * 0.3);

                // Stalactites
                if (formationNoise > 0.4 && random.nextFloat() < 0.2) {
                    int ceilingY = findCeiling(world, worldX, baseY, worldZ, height);
                    if (ceilingY > 0) {
                        int len = 1 + random.nextInt(3);
                        placeStalactite(world, worldX, ceilingY, worldZ, len);
                    }
                }

                // Stalagmites
                if (formationNoise < -0.3 && random.nextFloat() < 0.15) {
                    int floorY = findFloor(world, worldX, baseY, worldZ);
                    if (floorY > 0) {
                        int len = 1 + random.nextInt(2);
                        placeStalagmite(world, worldX, floorY + 1, worldZ, len);
                    }
                }
            }
        }
    }

    private int findCeiling(World world, int x, int baseY, int z, int height) {
        for (int y = baseY + height - 1; y > baseY; y--) {
            if (world.getBlockAt(x, y, z).getType() != Material.AIR &&
                world.getBlockAt(x, y - 1, z).getType() == Material.AIR) {
                return y;
            }
        }
        return -1;
    }

    private int findFloor(World world, int x, int baseY, int z) {
        for (int y = baseY + 5; y >= baseY - 2; y--) {
            Block block = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            if (block.getType().isSolid() && !above.getType().isSolid()) {
                return y;
            }
        }
        return -1;
    }

    private void placeStalactite(World world, int x, int ceilingY, int z, int length) {
        for (int i = 0; i < length; i++) {
            Block block = world.getBlockAt(x, ceilingY - i - 1, z);
            if (block.getType() != Material.AIR) break;
            block.setType(Material.POINTED_DRIPSTONE);
            if (block.getBlockData() instanceof org.bukkit.block.data.type.PointedDripstone pd) {
                pd.setVerticalDirection(org.bukkit.block.BlockFace.DOWN);
                pd.setThickness(i == length - 1 ?
                    org.bukkit.block.data.type.PointedDripstone.Thickness.TIP :
                    (i == 0 ? org.bukkit.block.data.type.PointedDripstone.Thickness.BASE :
                        org.bukkit.block.data.type.PointedDripstone.Thickness.MIDDLE));
                block.setBlockData(pd);
            }
        }
    }

    private void placeStalagmite(World world, int x, int floorY, int z, int length) {
        for (int i = 0; i < length; i++) {
            Block block = world.getBlockAt(x, floorY + i, z);
            if (block.getType() != Material.AIR) break;
            block.setType(Material.POINTED_DRIPSTONE);
            if (block.getBlockData() instanceof org.bukkit.block.data.type.PointedDripstone pd) {
                pd.setVerticalDirection(org.bukkit.block.BlockFace.UP);
                pd.setThickness(i == length - 1 ?
                    org.bukkit.block.data.type.PointedDripstone.Thickness.TIP :
                    (i == 0 ? org.bukkit.block.data.type.PointedDripstone.Thickness.BASE :
                        org.bukkit.block.data.type.PointedDripstone.Thickness.MIDDLE));
                block.setBlockData(pd);
            }
        }
    }

    /**
     * Adds spawn points to a room based on its type.
     */
    private void addRoomSpawnPoints(Room room, World world, LayoutNode node,
                                   int minX, int minY, int minZ, int maxX, int maxZ) {
        Random spawnRandom = new Random(node.getId());
        int floorY = minY + 1;

        switch (node.getType()) {
            case SPAWN:
                room.addSpawnPoint(room.getCenter());
                break;

            case COMBAT:
                int combatSpawns = 3 + node.getDepth(); // More spawns deeper in
                for (int i = 0; i < combatSpawns; i++) {
                    int sx = minX + 2 + spawnRandom.nextInt(Math.max(1, maxX - minX - 4));
                    int sz = minZ + 2 + spawnRandom.nextInt(Math.max(1, maxZ - minZ - 4));
                    room.addSpawnPoint(new Location(world, sx + 0.5, floorY, sz + 0.5));
                }
                break;

            case TREASURE:
                room.addChestLocation(room.getCenter().clone().add(0, 0, 0));
                if (spawnRandom.nextDouble() < 0.5) {
                    room.addSpawnPoint(new Location(world, minX + 2, floorY, minZ + 2));
                    room.addSpawnPoint(new Location(world, maxX - 2, floorY, maxZ - 2));
                }
                break;

            case PUZZLE:
                room.addChestLocation(room.getCenter().clone().add(0, 0, 2));
                break;

            case BOSS:
                room.addSpawnPoint(room.getCenter().clone().add(0, 0, 5));
                room.addChestLocation(room.getCenter().clone().add(0, 0, -3));
                break;
        }
    }

    /**
     * PHASE 2: Fills corridor path with varied stone mix (L-shaped).
     */
    private void fillCorridorStone(World world, int x1, int y, int z1, int x2, int z2,
                                   Material wallMat, NoiseGenerator noise) {
        int halfWidth = 5;
        int height = 8;

        // First leg (X direction)
        int xDir = x2 > x1 ? 1 : -1;
        for (int x = x1; x != x2; x += xDir) {
            for (int ox = -halfWidth; ox <= halfWidth; ox++) {
                for (int oz = -halfWidth; oz <= halfWidth; oz++) {
                    for (int oy = -2; oy <= height + 2; oy++) {
                        Block block = world.getBlockAt(x + ox, y + oy, z1 + oz);
                        setWallBlock(block, wallMat, noise, x + ox, z1 + oz);
                    }
                }
            }
        }

        // Second leg (Z direction)
        int zDir = z2 > z1 ? 1 : -1;
        for (int z = z1; z != z2 + zDir; z += zDir) {
            for (int ox = -halfWidth; ox <= halfWidth; ox++) {
                for (int oz = -halfWidth; oz <= halfWidth; oz++) {
                    for (int oy = -2; oy <= height + 2; oy++) {
                        Block block = world.getBlockAt(x2 + ox, y + oy, z + oz);
                        setWallBlock(block, wallMat, noise, x2 + ox, z + oz);
                    }
                }
            }
        }
    }

    /**
     * PHASE 3: Carves corridor interior - ONLY places air.
     */
    private void carveCorridorInterior(World world, int x1, int y, int z1, int x2, int z2, NoiseGenerator noise) {
        int halfWidth = 3;
        int height = 4;

        // First leg (X direction)
        int xDir = x2 > x1 ? 1 : -1;
        for (int x = x1; x != x2; x += xDir) {
            carveCorridorSlice(world, x, y, z1, halfWidth, height, noise);
        }

        // Second leg (Z direction)
        int zDir = z2 > z1 ? 1 : -1;
        for (int z = z1; z != z2 + zDir; z += zDir) {
            carveCorridorSlice(world, x2, y, z, halfWidth, height, noise);
        }
    }

    /**
     * Carves a single corridor slice - ONLY places air. Cave-like with noise variation.
     */
    private void carveCorridorSlice(World world, int centerX, int baseY, int centerZ,
                                    int halfWidth, int height, NoiseGenerator noise) {
        Random random = new Random(noise.getSeed() + centerX * 31 + centerZ);

        for (int ox = -halfWidth - 1; ox <= halfWidth + 1; ox++) {
            for (int oz = -halfWidth - 1; oz <= halfWidth + 1; oz++) {
                int worldX = centerX + ox;
                int worldZ = centerZ + oz;

                // Noise for organic shape
                double ceilingNoise = noise.octaveNoise2D(worldX * 0.15, worldZ * 0.15, 3, 0.5, 1.0);
                double floorNoise = noise.octaveNoise2D(worldX * 0.18 + 100, worldZ * 0.18 + 100, 2, 0.5, 1.0);
                double widthNoise = noise.octaveNoise2D(worldX * 0.1, worldZ * 0.1, 2, 0.5, 1.0);

                double baseCaveWidth = halfWidth + widthNoise * 2;
                double distFromCenter = Math.sqrt(ox * ox + oz * oz);

                // Skip if outside corridor width
                if (distFromCenter > baseCaveWidth + 1) continue;

                // Wall blend for edges
                double wallBlend = 1.0;
                if (distFromCenter > baseCaveWidth - 1.5) {
                    wallBlend = (baseCaveWidth + 1 - distFromCenter) / 2.5;
                }
                wallBlend = Math.max(0, Math.min(1, wallBlend));

                // 3D noise for wall irregularity
                double wallNoise3D = noise.noise3D(worldX * 0.25, baseY * 0.2, worldZ * 0.25);
                wallBlend += wallNoise3D * 0.3;
                wallBlend = Math.max(0, Math.min(1, wallBlend));

                if (wallBlend < 0.2) continue; // Don't carve - solid wall

                // Variable floor and ceiling per column
                double floorOffset = floorNoise * 1.5;
                floorOffset += distFromCenter * 0.2; // Rise at edges
                int floorY = baseY + (int) Math.round(floorOffset) - 1;

                double ceilOffset = height + ceilingNoise * 1.5;
                ceilOffset *= wallBlend; // Lower at edges
                int ceilingY = baseY + (int) Math.round(ceilOffset);

                // ONLY CARVE AIR
                for (int worldY = floorY + 1; worldY < ceilingY; worldY++) {
                    // Skip some blocks in transitional zone for organic walls
                    if (wallBlend < 0.5 && random.nextFloat() < (0.5 - wallBlend)) continue;
                    world.getBlockAt(worldX, worldY, worldZ).setType(Material.AIR);
                }
            }
        }
    }

    private void updateProgress(Consumer<Double> callback, double progress) {
        if (callback != null) {
            callback.accept(progress);
        }
    }

    public long generateSeed() {
        return System.currentTimeMillis() + random.nextLong();
    }
}
