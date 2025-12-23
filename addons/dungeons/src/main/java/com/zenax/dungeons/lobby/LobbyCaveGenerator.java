package com.zenax.dungeons.lobby;

import com.zenax.dungeons.dungeon.Dungeon;
import com.zenax.dungeons.generation.algorithm.NoiseGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.Color;

import java.util.Map;
import java.util.Random;

/**
 * Generates procedural cave lobbies with full terraforming.
 * Each lobby is unique due to noise-based generation.
 */
public class LobbyCaveGenerator {
    private final NoiseGenerator noiseGenerator;
    private final Random random;
    private final long seed;

    // Lobby dimensions
    private static final int WIDTH = 12;   // X axis (-6 to +6)
    private static final int DEPTH = 18;   // Z axis (0 to 17)
    private static final int HEIGHT = 8;   // Y axis

    // Key locations (relative to base)
    private static final int SPAWN_Z = 3;
    private static final int HOLOGRAM_Z = 14;
    private static final int GATE_Z = 18;

    /**
     * Creates a new lobby cave generator with a random seed.
     */
    public LobbyCaveGenerator() {
        this(System.currentTimeMillis());
    }

    /**
     * Creates a new lobby cave generator with the given seed.
     *
     * @param seed The seed for random generation
     */
    public LobbyCaveGenerator(long seed) {
        this.seed = seed;
        this.noiseGenerator = new NoiseGenerator(seed);
        this.random = new Random(seed);
    }

    /**
     * Generates a complete cave lobby at the given location.
     *
     * @param baseLocation The base location for the lobby
     * @param dungeon The dungeon template (for theming)
     * @return A CaveLobbyResult containing key locations
     */
    public CaveLobbyResult generate(Location baseLocation, Dungeon dungeon) {
        World world = baseLocation.getWorld();
        if (world == null) {
            return null;
        }

        int baseX = baseLocation.getBlockX();
        int baseY = baseLocation.getBlockY();
        int baseZ = baseLocation.getBlockZ();

        // Get theme materials
        Map<String, Material> theme = dungeon.getThemeMaterials();
        Material wallMat = theme.getOrDefault("wall", Material.STONE);
        Material floorMat = theme.getOrDefault("floor", Material.STONE);
        Material accentMat = theme.getOrDefault("accent", Material.DRIPSTONE_BLOCK);

        // Phase 1: Fill the entire area with stone (create the rock mass)
        fillArea(world, baseX, baseY, baseZ);

        // Phase 2: Carve out the cave using 3D noise (handles floor/ceiling organically)
        carveCave(world, baseX, baseY, baseZ, wallMat, floorMat);

        // Phase 3: Build floor height map for formations (without modifying blocks)
        int[][] floorHeights = buildFloorHeightMap(world, baseX, baseY, baseZ);

        // Phase 4: Add stalactites and stalagmites
        addFormations(world, baseX, baseY, baseZ, floorHeights, accentMat);

        // Phase 5: Add wall irregularities
        carveWallAlcoves(world, baseX, baseY, baseZ);

        // Phase 6: Add decorations (moss, glow lichen, etc.)
        addDecorations(world, baseX, baseY, baseZ, theme);

        // Phase 7: Create the back wall portal (full nether portal)
        Location portalLocation = generateBackWallPortal(world, baseX, baseY, baseZ);

        // Phase 8: Create the gate at the dungeon entrance
        Location gateLocation = new Location(world, baseX, baseY + 1, baseZ + GATE_Z);
        generateGate(gateLocation, theme);

        // Phase 9: Add lighting
        addLighting(world, baseX, baseY, baseZ);

        // Calculate key locations
        Location spawnPoint = new Location(world, baseX + 0.5, baseY + 2, baseZ + SPAWN_Z + 0.5);
        Location hologramLocation = new Location(world, baseX + 0.5, baseY + 3, baseZ + HOLOGRAM_Z + 0.5);
        Location entranceLocation = new Location(world, baseX + 0.5, baseY + 2, baseZ + GATE_Z + 2.5);

        return new CaveLobbyResult(
            spawnPoint,
            hologramLocation,
            gateLocation,
            entranceLocation,
            portalLocation
        );
    }

    /**
     * Fills the entire lobby area with stone to create the rock mass.
     */
    private void fillArea(World world, int baseX, int baseY, int baseZ) {
        int halfWidth = WIDTH / 2;
        for (int x = -halfWidth - 5; x <= halfWidth + 5; x++) {
            for (int z = -5; z <= DEPTH + 5; z++) {
                for (int y = -3; y <= HEIGHT + 5; y++) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.STONE);
                }
            }
        }
    }

    /**
     * Carves the main cave shape using organic 3D noise-based distance functions.
     * Creates an actual cave tunnel, not a box.
     */
    private void carveCave(World world, int baseX, int baseY, int baseZ, Material wallMat, Material floorMat) {
        int halfWidth = WIDTH / 2;

        for (int x = -halfWidth - 2; x <= halfWidth + 2; x++) {
            for (int z = -2; z < DEPTH + 2; z++) {
                // Calculate noise-based ceiling and floor heights for organic shape
                double ceilingNoise = noiseGenerator.octaveNoise2D(
                    (baseX + x) * 0.12,
                    (baseZ + z) * 0.12,
                    3, 0.5, 1.0
                );
                double floorNoise = noiseGenerator.octaveNoise2D(
                    (baseX + x) * 0.15 + 100,
                    (baseZ + z) * 0.15 + 100,
                    2, 0.5, 1.0
                );

                // Width noise - makes cave wider/narrower organically
                double widthNoise = noiseGenerator.octaveNoise2D(
                    (baseX + x) * 0.08,
                    (baseZ + z) * 0.1,
                    2, 0.5, 1.0
                );

                // Base cave dimensions that vary with noise
                double baseCaveWidth = halfWidth + widthNoise * 3; // halfWidth +/- 3 blocks
                double baseCeilingHeight = 7 + ceilingNoise * 3; // 4-10 blocks
                double baseFloorOffset = floorNoise * 2; // -2 to +2 blocks of floor variation

                // Distance from center line (x=0)
                double distFromCenter = Math.abs(x);

                // Edge falloff for front and back
                double zFactor = 1.0;
                if (z < 3) {
                    zFactor = z / 3.0;
                } else if (z > DEPTH - 3) {
                    zFactor = (DEPTH - z) / 3.0;
                }
                zFactor = Math.max(0, Math.min(1, zFactor));

                // Calculate if this x position is inside the cave
                double effectiveWidth = baseCaveWidth * zFactor;

                // Skip if outside cave width
                if (distFromCenter > effectiveWidth + 2) {
                    continue;
                }

                // Calculate wall blend factor (1 = definitely cave, 0 = definitely wall)
                double wallBlend = 1.0;
                if (distFromCenter > effectiveWidth - 2) {
                    wallBlend = (effectiveWidth + 2 - distFromCenter) / 4.0;
                }
                wallBlend = Math.max(0, Math.min(1, wallBlend)) * zFactor;

                // Add 3D noise for wall irregularity
                double wallNoise3D = noiseGenerator.octaveNoise3D(
                    (baseX + x) * 0.2,
                    baseY * 0.15,
                    (baseZ + z) * 0.2,
                    2, 0.5, 1.0
                );

                // Adjust blend with 3D noise for organic edges
                wallBlend += wallNoise3D * 0.3;
                wallBlend = Math.max(0, Math.min(1, wallBlend));

                // Calculate floor and ceiling for this position
                int floorY = baseY + (int) Math.round(baseFloorOffset);
                int ceilingY = baseY + (int) Math.round(baseCeilingHeight * wallBlend);

                // Carve the column
                for (int y = -2; y <= HEIGHT + 3; y++) {
                    int worldY = baseY + y;
                    Block block = world.getBlockAt(baseX + x, worldY, baseZ + z);

                    if (wallBlend < 0.2) {
                        // Solid rock
                        setWallBlock(block, wallMat, baseX + x, baseZ + z);
                    } else if (worldY <= floorY) {
                        // Below floor - solid with variation
                        setWallBlock(block, floorMat, baseX + x, baseZ + z);
                    } else if (worldY >= ceilingY) {
                        // Above ceiling - solid
                        setWallBlock(block, wallMat, baseX + x, baseZ + z);
                    } else if (wallBlend < 0.5 && random.nextFloat() < (0.5 - wallBlend)) {
                        // Transitional zone - some blocks, some air
                        setWallBlock(block, wallMat, baseX + x, baseZ + z);
                    } else {
                        // Inside cave - air
                        block.setType(Material.AIR);
                    }
                }
            }
        }

        // Ensure minimal walkable path through center (not flat, just passable)
        for (int z = 2; z < DEPTH - 2; z++) {
            for (int x = -1; x <= 1; x++) {
                // Find the floor level at this position
                int floorY = baseY;
                for (int y = baseY + 3; y >= baseY - 1; y--) {
                    if (world.getBlockAt(baseX + x, y, baseZ + z).getType().isSolid()) {
                        floorY = y;
                        break;
                    }
                }
                // Ensure 3 blocks of air above floor for walking
                for (int y = 1; y <= 3; y++) {
                    Block block = world.getBlockAt(baseX + x, floorY + y, baseZ + z);
                    if (block.getType().isSolid()) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    /**
     * Sets a wall block with material variation.
     */
    private void setWallBlock(Block block, Material baseMat, int x, int z) {
        double variation = noiseGenerator.noise2D(x * 0.25, z * 0.25);
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
     * Builds a floor height map by scanning existing blocks.
     * Does NOT modify any blocks - just reads the carved cave state.
     */
    private int[][] buildFloorHeightMap(World world, int baseX, int baseY, int baseZ) {
        int halfWidth = WIDTH / 2;
        int[][] floorHeights = new int[WIDTH + 1][DEPTH];

        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int z = 0; z < DEPTH; z++) {
                // Scan downward from expected height to find the floor
                int floorY = baseY;
                for (int y = baseY + 5; y >= baseY - 2; y--) {
                    Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                    Block above = world.getBlockAt(baseX + x, y + 1, baseZ + z);
                    if (block.getType().isSolid() && !above.getType().isSolid()) {
                        floorY = y;
                        break;
                    }
                }
                floorHeights[x + halfWidth][z] = floorY;
            }
        }

        return floorHeights;
    }

    /**
     * Adds stalactites and stalagmites.
     */
    private void addFormations(World world, int baseX, int baseY, int baseZ, int[][] floorHeights, Material accentMat) {
        int halfWidth = WIDTH / 2;

        for (int x = -halfWidth + 2; x <= halfWidth - 2; x++) {
            for (int z = 2; z < DEPTH - 2; z++) {
                // Skip center path
                if (Math.abs(x) <= 3) continue;

                double formationNoise = noiseGenerator.noise2D(
                    (baseX + x) * 0.3,
                    (baseZ + z) * 0.3
                );

                // Stalactites (hanging from ceiling)
                if (formationNoise > 0.4 && random.nextFloat() < 0.25) {
                    int ceilingY = findCeiling(world, baseX + x, baseY, baseZ + z);
                    if (ceilingY > 0) {
                        int length = 1 + random.nextInt(3);
                        placeStalactite(world, baseX + x, ceilingY, baseZ + z, length);
                    }
                }

                // Stalagmites (rising from floor)
                if (formationNoise < -0.3 && random.nextFloat() < 0.2) {
                    int floorY = floorHeights[x + halfWidth][z];
                    int length = 1 + random.nextInt(2);
                    placeStalagmite(world, baseX + x, floorY + 1, baseZ + z, length);
                }

                // Rock clusters
                if (Math.abs(formationNoise) < 0.1 && random.nextFloat() < 0.1) {
                    int floorY = floorHeights[x + halfWidth][z];
                    placeRockCluster(world, baseX + x, floorY + 1, baseZ + z, accentMat);
                }
            }
        }
    }

    /**
     * Finds the ceiling Y coordinate at a position.
     */
    private int findCeiling(World world, int x, int baseY, int z) {
        for (int y = baseY + HEIGHT - 1; y > baseY; y--) {
            if (world.getBlockAt(x, y, z).getType() != Material.AIR &&
                world.getBlockAt(x, y - 1, z).getType() == Material.AIR) {
                return y;
            }
        }
        return -1;
    }

    /**
     * Places a stalactite at the given location.
     */
    private void placeStalactite(World world, int x, int ceilingY, int z, int length) {
        for (int i = 0; i < length; i++) {
            Block block = world.getBlockAt(x, ceilingY - i - 1, z);
            if (block.getType() != Material.AIR) break;

            if (i == length - 1) {
                // Tip
                block.setType(Material.POINTED_DRIPSTONE);
                if (block.getBlockData() instanceof PointedDripstone pd) {
                    pd.setVerticalDirection(BlockFace.DOWN);
                    pd.setThickness(PointedDripstone.Thickness.TIP);
                    block.setBlockData(pd);
                }
            } else {
                block.setType(Material.POINTED_DRIPSTONE);
                if (block.getBlockData() instanceof PointedDripstone pd) {
                    pd.setVerticalDirection(BlockFace.DOWN);
                    pd.setThickness(i == 0 ? PointedDripstone.Thickness.BASE : PointedDripstone.Thickness.MIDDLE);
                    block.setBlockData(pd);
                }
            }
        }
    }

    /**
     * Places a stalagmite at the given location.
     */
    private void placeStalagmite(World world, int x, int floorY, int z, int length) {
        for (int i = 0; i < length; i++) {
            Block block = world.getBlockAt(x, floorY + i, z);
            if (block.getType() != Material.AIR) break;

            if (i == length - 1) {
                // Tip
                block.setType(Material.POINTED_DRIPSTONE);
                if (block.getBlockData() instanceof PointedDripstone pd) {
                    pd.setVerticalDirection(BlockFace.UP);
                    pd.setThickness(PointedDripstone.Thickness.TIP);
                    block.setBlockData(pd);
                }
            } else {
                block.setType(Material.POINTED_DRIPSTONE);
                if (block.getBlockData() instanceof PointedDripstone pd) {
                    pd.setVerticalDirection(BlockFace.UP);
                    pd.setThickness(i == 0 ? PointedDripstone.Thickness.BASE : PointedDripstone.Thickness.MIDDLE);
                    block.setBlockData(pd);
                }
            }
        }
    }

    /**
     * Places a small rock cluster.
     */
    private void placeRockCluster(World world, int x, int y, int z, Material accentMat) {
        world.getBlockAt(x, y, z).setType(accentMat);
        if (random.nextBoolean()) {
            world.getBlockAt(x + 1, y, z).setType(Material.COBBLESTONE);
        }
        if (random.nextBoolean()) {
            world.getBlockAt(x, y, z + 1).setType(Material.COBBLESTONE);
        }
    }

    /**
     * Carves alcoves and bumps in the walls.
     */
    private void carveWallAlcoves(World world, int baseX, int baseY, int baseZ) {
        int halfWidth = WIDTH / 2;

        // Process wall edges
        for (int z = 2; z < DEPTH - 2; z++) {
            for (int y = 2; y < HEIGHT - 2; y++) {
                double noise = noiseGenerator.noise3D(
                    (baseX - halfWidth) * 0.25,
                    (baseY + y) * 0.3,
                    (baseZ + z) * 0.25
                );

                // Left wall alcoves
                if (noise > 0.35) {
                    int depth = noise > 0.5 ? 2 : 1;
                    for (int d = 0; d < depth; d++) {
                        world.getBlockAt(baseX - halfWidth + 1 + d, baseY + y, baseZ + z).setType(Material.AIR);
                    }
                }

                // Right wall alcoves
                noise = noiseGenerator.noise3D(
                    (baseX + halfWidth) * 0.25,
                    (baseY + y) * 0.3,
                    (baseZ + z) * 0.25
                );
                if (noise > 0.35) {
                    int depth = noise > 0.5 ? 2 : 1;
                    for (int d = 0; d < depth; d++) {
                        world.getBlockAt(baseX + halfWidth - 1 - d, baseY + y, baseZ + z).setType(Material.AIR);
                    }
                }
            }
        }
    }

    /**
     * Adds decorative elements like moss, glow lichen, etc.
     */
    private void addDecorations(World world, int baseX, int baseY, int baseZ, Map<String, Material> theme) {
        int halfWidth = WIDTH / 2;

        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int z = 0; z < DEPTH; z++) {
                for (int y = 1; y < HEIGHT; y++) {
                    Block block = world.getBlockAt(baseX + x, baseY + y, baseZ + z);
                    if (block.getType() != Material.AIR) continue;

                    // Check adjacent solid blocks for decoration placement
                    double decorNoise = random.nextDouble();

                    // Moss on floor edges
                    Block below = world.getBlockAt(baseX + x, baseY + y - 1, baseZ + z);
                    if (below.getType().isSolid() && decorNoise < 0.05) {
                        block.setType(Material.MOSS_CARPET);
                        continue;
                    }

                    // Glow lichen on walls (sparse)
                    if (decorNoise < 0.02) {
                        Block north = world.getBlockAt(baseX + x, baseY + y, baseZ + z - 1);
                        Block south = world.getBlockAt(baseX + x, baseY + y, baseZ + z + 1);
                        Block east = world.getBlockAt(baseX + x + 1, baseY + y, baseZ + z);
                        Block west = world.getBlockAt(baseX + x - 1, baseY + y, baseZ + z);

                        if (north.getType().isSolid() || south.getType().isSolid() ||
                            east.getType().isSolid() || west.getType().isSolid()) {
                            // Can place glow lichen
                            block.setType(Material.GLOW_LICHEN);
                        }
                    }

                    // Cobwebs in corners (very sparse)
                    if (decorNoise > 0.98 && y > 5) {
                        Block above = world.getBlockAt(baseX + x, baseY + y + 1, baseZ + z);
                        if (above.getType().isSolid()) {
                            block.setType(Material.COBWEB);
                        }
                    }
                }
            }
        }
    }

    /**
     * Generates the full back wall portal by finding the actual back wall of the carved cave.
     * Starts at z=2 and iterates backwards to find where air begins.
     */
    private Location generateBackWallPortal(World world, int baseX, int baseY, int baseZ) {
        int halfWidth = WIDTH / 2;

        // Start at z=2 and go backwards to find where air is
        int portalZ = baseZ + 2;
        for (int z = 2; z >= 0; z--) {
            boolean foundAir = false;
            for (int x = -halfWidth; x <= halfWidth; x++) {
                for (int y = 1; y <= HEIGHT; y++) {
                    Block block = world.getBlockAt(baseX + x, baseY + y, baseZ + z);
                    if (block.getType() == Material.AIR) {
                        foundAir = true;
                        break;
                    }
                }
                if (foundAir) break;
            }
            if (foundAir) {
                portalZ = baseZ + z;
                break;
            }
        }

        // Find the bounds of the air opening at portalZ
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int x = -halfWidth - 2; x <= halfWidth + 2; x++) {
            for (int y = 1; y <= HEIGHT + 2; y++) {
                Block block = world.getBlockAt(baseX + x, baseY + y, portalZ);
                if (block.getType() == Material.AIR) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        // Final fallback
        if (minX == Integer.MAX_VALUE) {
            minX = -2;
            maxX = 2;
            minY = 1;
            maxY = 5;
        }

        // Ensure minimum portal size
        if (maxX - minX < 1) {
            int center = (minX + maxX) / 2;
            minX = center - 1;
            maxX = center + 1;
        }
        if (maxY - minY < 2) {
            maxY = minY + 3;
        }

        // Create obsidian backing behind the portal
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int y = minY - 1; y <= maxY + 1; y++) {
                world.getBlockAt(baseX + x, baseY + y, portalZ - 1).setType(Material.OBSIDIAN);
            }
        }

        // Fill the portal area with nether portal blocks
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Block block = world.getBlockAt(baseX + x, baseY + y, portalZ);
                block.setType(Material.AIR); // Clear first
                org.bukkit.block.data.BlockData data = Material.NETHER_PORTAL.createBlockData();
                if (data instanceof org.bukkit.block.data.Orientable orientable) {
                    orientable.setAxis(org.bukkit.Axis.X);
                }
                block.setBlockData(data, false);
            }
        }

        return new Location(world, baseX + minX, baseY + minY, portalZ);
    }

    /**
     * Generates the gate blocking the dungeon entrance.
     */
    private void generateGate(Location gateLocation, Map<String, Material> theme) {
        World world = gateLocation.getWorld();
        if (world == null) return;

        int x = gateLocation.getBlockX();
        int y = gateLocation.getBlockY();
        int z = gateLocation.getBlockZ();

        // Gate is 5 wide, 4 tall
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                Material gateMat;
                if (dy == 0 || dy == 3 || Math.abs(dx) == 2) {
                    // Frame
                    gateMat = Material.IRON_BARS;
                } else {
                    // Inner bars
                    gateMat = Material.IRON_BARS;
                }
                world.getBlockAt(x + dx, y + dy, z).setType(gateMat);
            }
        }
    }

    /**
     * Adds lighting throughout the cave by finding the highest air block with solid above.
     */
    private void addLighting(World world, int baseX, int baseY, int baseZ) {
        int halfWidth = WIDTH / 2;

        // Place lanterns scattered throughout the cave
        for (int z = 3; z < DEPTH - 2; z += 4) {
            for (int x = -halfWidth + 1; x <= halfWidth - 1; x += 3) {
                // Find highest air block with solid block above it
                int lanternY = -1;
                for (int y = baseY + HEIGHT; y > baseY + 2; y--) {
                    Block current = world.getBlockAt(baseX + x, y, baseZ + z);
                    Block above = world.getBlockAt(baseX + x, y + 1, baseZ + z);
                    if (current.getType() == Material.AIR && above.getType().isSolid()) {
                        lanternY = y;
                        break;
                    }
                }

                // Place lantern if valid position found
                if (lanternY > baseY + 2 && random.nextFloat() < 0.6) {
                    Block lanternBlock = world.getBlockAt(baseX + x, lanternY, baseZ + z);
                    if (lanternBlock.getType() == Material.AIR) {
                        lanternBlock.setType(Material.LANTERN);
                    }
                }
            }
        }
    }

    /**
     * Spawns a text display hologram with an interaction hitbox at the specified location.
     *
     * @param location The location to spawn the hologram
     * @param lobby The lobby for getting settings
     * @return The spawned Interaction entity (the clickable part)
     */
    public Interaction spawnHologram(Location location, DungeonLobby lobby) {
        World world = location.getWorld();
        if (world == null) return null;

        // Spawn the visual TextDisplay
        world.spawn(location.clone().add(0, 0.5, 0), TextDisplay.class, display -> {
            String text = buildHologramText(lobby);
            display.setText(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setShadowed(true);
            display.setBackgroundColor(Color.fromARGB(120, 20, 20, 40));
            display.setSeeThrough(false);
            display.setLineWidth(200);
        });

        // Spawn an Interaction entity for right-click detection
        return world.spawn(location, Interaction.class, interaction -> {
            interaction.setInteractionWidth(2.0f);
            interaction.setInteractionHeight(2.0f);
            interaction.setResponsive(true);
        });
    }

    /**
     * Builds the hologram text based on lobby settings.
     */
    public String buildHologramText(DungeonLobby lobby) {
        return "\u00A76\u00A7l" + lobby.getDungeonTemplate().getDisplayName() + "\n" +
               "\u00A77Right-click to configure\n" +
               "\u00A78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n" +
               "\u00A77Difficulty: \u00A7e" + lobby.getSelectedDifficulty().getDisplayName() + "\n" +
               "\u00A77Mode: \u00A7e" + lobby.getSelectedObjectiveMode().getDisplayName();
    }

    /**
     * Result class containing all key locations from cave generation.
     */
    public static class CaveLobbyResult {
        private final Location spawnPoint;
        private final Location hologramLocation;
        private final Location gateLocation;
        private final Location entranceLocation;
        private final Location portalLocation;

        public CaveLobbyResult(Location spawnPoint, Location hologramLocation,
                               Location gateLocation, Location entranceLocation,
                               Location portalLocation) {
            this.spawnPoint = spawnPoint;
            this.hologramLocation = hologramLocation;
            this.gateLocation = gateLocation;
            this.entranceLocation = entranceLocation;
            this.portalLocation = portalLocation;
        }

        public Location getSpawnPoint() { return spawnPoint; }
        public Location getHologramLocation() { return hologramLocation; }
        public Location getGateLocation() { return gateLocation; }
        public Location getEntranceLocation() { return entranceLocation; }
        public Location getPortalLocation() { return portalLocation; }
    }
}
