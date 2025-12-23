package com.zenax.dungeons.generation.layout;

import com.zenax.dungeons.dungeon.Dungeon;
import com.zenax.dungeons.generation.room.RoomType;

import java.util.*;

/**
 * Generates the abstract dungeon layout (graph) before physical generation.
 * Creates a logical structure with proper room connectivity and progression.
 */
public class DungeonLayoutGenerator {
    private final Random random;
    private final long seed;

    public DungeonLayoutGenerator(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * Generates a complete dungeon layout based on the template.
     */
    public DungeonLayout generate(Dungeon template) {
        DungeonLayout layout = new DungeonLayout(seed);

        int roomCount = template.getMinRoomCount() +
                       random.nextInt(template.getMaxRoomCount() - template.getMinRoomCount() + 1);

        // Ensure minimum rooms for a proper dungeon
        roomCount = Math.max(roomCount, 5);

        // Step 1: Create the main path (spawn -> ... -> boss)
        int mainPathLength = 3 + random.nextInt(3); // 3-5 rooms on main path
        mainPathLength = Math.min(mainPathLength, roomCount - 1);

        List<LayoutNode> mainPath = createMainPath(layout, mainPathLength);
        layout.setMainPath(mainPath);

        // Step 2: Add branch rooms off the main path
        int branchRooms = roomCount - mainPath.size();
        addBranchRooms(layout, mainPath, branchRooms);

        // Step 3: Calculate depths from spawn
        layout.calculateDepths();

        // Step 4: Place rooms on a grid for physical generation
        placeRoomsOnGrid(layout);

        return layout;
    }

    /**
     * Creates the main path from spawn to boss.
     */
    private List<LayoutNode> createMainPath(DungeonLayout layout, int length) {
        List<LayoutNode> path = new ArrayList<>();

        // Spawn room
        LayoutNode spawn = layout.addNode(RoomType.SPAWN);
        layout.setSpawnNode(spawn);
        path.add(spawn);

        // Middle rooms - mix of combat and puzzle
        for (int i = 1; i < length; i++) {
            RoomType type;
            if (random.nextDouble() < 0.7) {
                type = RoomType.COMBAT;
            } else {
                type = RoomType.PUZZLE;
            }

            LayoutNode node = layout.addNode(type);
            layout.connect(path.get(path.size() - 1), node);
            path.add(node);
        }

        // Boss room at the end
        LayoutNode boss = layout.addNode(RoomType.BOSS);
        layout.setBossNode(boss);
        layout.connect(path.get(path.size() - 1), boss);
        path.add(boss);

        return path;
    }

    /**
     * Adds branch rooms off the main path.
     */
    private void addBranchRooms(DungeonLayout layout, List<LayoutNode> mainPath, int count) {
        if (count <= 0 || mainPath.size() < 2) return;

        for (int i = 0; i < count; i++) {
            // Pick a random node on the main path (not spawn or boss)
            int attachIndex = 1 + random.nextInt(mainPath.size() - 2);
            LayoutNode attachTo = mainPath.get(attachIndex);

            // Determine room type for branch
            RoomType type;
            double roll = random.nextDouble();
            if (roll < 0.4) {
                type = RoomType.TREASURE;
            } else if (roll < 0.7) {
                type = RoomType.COMBAT;
            } else {
                type = RoomType.PUZZLE;
            }

            // Create the branch room
            LayoutNode branch = layout.addNode(type);
            layout.connect(attachTo, branch);

            // Small chance to extend the branch further
            if (random.nextDouble() < 0.3 && i < count - 1) {
                RoomType extraType = random.nextDouble() < 0.5 ? RoomType.COMBAT : RoomType.TREASURE;
                LayoutNode extra = layout.addNode(extraType);
                layout.connect(branch, extra);
                i++; // Count this as another room
            }
        }
    }

    /**
     * Places rooms on a 2D grid for physical generation.
     * Uses a simple placement algorithm that ensures no overlaps.
     */
    private void placeRoomsOnGrid(DungeonLayout layout) {
        if (layout.getSpawnNode() == null) return;

        // Grid cell size (room + corridor space)
        int cellSize = 25;

        // Track occupied cells
        Set<String> occupied = new HashSet<>();

        // Place spawn at origin
        LayoutNode spawn = layout.getSpawnNode();
        spawn.setGridX(0);
        spawn.setGridZ(0);
        occupied.add("0,0");

        // BFS to place remaining nodes
        Queue<LayoutNode> queue = new LinkedList<>();
        Set<LayoutNode> placed = new HashSet<>();
        queue.add(spawn);
        placed.add(spawn);

        // Direction offsets for neighbors (N, E, S, W)
        int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

        while (!queue.isEmpty()) {
            LayoutNode current = queue.poll();
            int cx = current.getGridX();
            int cz = current.getGridZ();

            // Shuffle directions for variety
            List<int[]> dirList = new ArrayList<>(Arrays.asList(directions));
            Collections.shuffle(dirList, random);

            int dirIndex = 0;
            for (LayoutNode neighbor : current.getNeighbors()) {
                if (placed.contains(neighbor)) continue;

                // Find an empty adjacent cell
                boolean foundSpot = false;
                for (int attempt = 0; attempt < 8 && !foundSpot; attempt++) {
                    int[] dir = dirList.get(dirIndex % dirList.size());
                    int nx = cx + dir[0];
                    int nz = cz + dir[1];
                    String key = nx + "," + nz;

                    if (!occupied.contains(key)) {
                        neighbor.setGridX(nx);
                        neighbor.setGridZ(nz);
                        occupied.add(key);
                        placed.add(neighbor);
                        queue.add(neighbor);
                        foundSpot = true;
                    }
                    dirIndex++;
                }

                // If no adjacent spot, place further away
                if (!foundSpot) {
                    for (int radius = 2; radius <= 5; radius++) {
                        for (int[] dir : dirList) {
                            int nx = cx + dir[0] * radius;
                            int nz = cz + dir[1] * radius;
                            String key = nx + "," + nz;

                            if (!occupied.contains(key)) {
                                neighbor.setGridX(nx);
                                neighbor.setGridZ(nz);
                                occupied.add(key);
                                placed.add(neighbor);
                                queue.add(neighbor);
                                foundSpot = true;
                                break;
                            }
                        }
                        if (foundSpot) break;
                    }
                }
            }
        }
    }

    /**
     * Generates a simple linear dungeon for testing.
     */
    public DungeonLayout generateSimple(int roomCount) {
        DungeonLayout layout = new DungeonLayout(seed);

        LayoutNode spawn = layout.addNode(RoomType.SPAWN);
        layout.setSpawnNode(spawn);
        spawn.setGridX(0);
        spawn.setGridZ(0);

        LayoutNode prev = spawn;
        for (int i = 1; i < roomCount - 1; i++) {
            RoomType type = random.nextDouble() < 0.6 ? RoomType.COMBAT : RoomType.TREASURE;
            LayoutNode node = layout.addNode(type);
            node.setGridX(0);
            node.setGridZ(i);
            layout.connect(prev, node);
            prev = node;
        }

        LayoutNode boss = layout.addNode(RoomType.BOSS);
        layout.setBossNode(boss);
        boss.setGridX(0);
        boss.setGridZ(roomCount - 1);
        layout.connect(prev, boss);

        layout.calculateDepths();
        return layout;
    }
}
