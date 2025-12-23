package com.zenax.dungeons.generation.algorithm;

import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Binary Space Partitioning (BSP) generator for dungeon layout.
 * Recursively divides space into regions suitable for room placement.
 */
public class BSPGenerator {
    private final Random random;

    /**
     * Creates a new BSP generator with the given seed.
     *
     * @param seed The seed for random generation
     */
    public BSPGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generates a list of regions by recursively partitioning the given area.
     *
     * @param area The initial bounding box to partition
     * @param minRoomSize Minimum size for a room region
     * @return List of bounding boxes representing potential room locations
     */
    public List<BoundingBox> generate(BoundingBox area, int minRoomSize) {
        List<BoundingBox> regions = new ArrayList<>();
        partition(area, minRoomSize, regions, 0, 5);
        return regions;
    }

    /**
     * Generates regions with a target room count.
     *
     * @param area The initial bounding box to partition
     * @param minRoomSize Minimum size for a room region
     * @param targetRoomCount Target number of rooms to generate
     * @return List of bounding boxes representing potential room locations
     */
    public List<BoundingBox> generateWithCount(BoundingBox area, int minRoomSize, int targetRoomCount) {
        List<BoundingBox> regions = new ArrayList<>();
        int maxDepth = (int) Math.ceil(Math.log(targetRoomCount) / Math.log(2));
        partition(area, minRoomSize, regions, 0, maxDepth);
        return regions;
    }

    /**
     * Recursively partitions the space.
     *
     * @param box The current bounding box to partition
     * @param minSize Minimum size for a partition
     * @param regions List to collect the final regions
     * @param depth Current recursion depth
     * @param maxDepth Maximum recursion depth
     */
    private void partition(BoundingBox box, int minSize, List<BoundingBox> regions, int depth, int maxDepth) {
        // Stop if we've reached max depth or if the box is too small
        if (depth >= maxDepth || !canSplit(box, minSize)) {
            regions.add(box.clone());
            return;
        }

        // Randomly decide to split or not (higher chance at lower depths)
        double splitChance = 1.0 - (depth / (double) maxDepth) * 0.5;
        if (random.nextDouble() > splitChance) {
            regions.add(box.clone());
            return;
        }

        // Determine split direction based on aspect ratio
        boolean splitHorizontally = shouldSplitHorizontally(box);

        // Perform the split
        BoundingBox[] split = splitBox(box, splitHorizontally, minSize);
        if (split == null) {
            // Couldn't split, add as is
            regions.add(box.clone());
            return;
        }

        // Recursively partition both halves
        partition(split[0], minSize, regions, depth + 1, maxDepth);
        partition(split[1], minSize, regions, depth + 1, maxDepth);
    }

    /**
     * Determines if the box should be split horizontally or vertically.
     *
     * @param box The box to evaluate
     * @return true to split horizontally (along X axis), false for vertically (along Z axis)
     */
    private boolean shouldSplitHorizontally(BoundingBox box) {
        double width = box.getWidthX();
        double depth = box.getWidthZ();

        // If width is much larger than depth, split horizontally
        if (width > depth * 1.5) {
            return true;
        }
        // If depth is much larger than width, split vertically
        else if (depth > width * 1.5) {
            return false;
        }
        // If relatively square, choose randomly
        else {
            return random.nextBoolean();
        }
    }

    /**
     * Splits a bounding box into two parts.
     *
     * @param box The box to split
     * @param horizontal true to split horizontally, false for vertically
     * @param minSize Minimum size for each resulting box
     * @return Array of two boxes, or null if split is not possible
     */
    private BoundingBox[] splitBox(BoundingBox box, boolean horizontal, int minSize) {
        if (horizontal) {
            double width = box.getWidthX();
            if (width < minSize * 2) {
                return null;
            }

            // Choose split point (with some randomness in the middle range)
            double minSplit = box.getMinX() + minSize;
            double maxSplit = box.getMaxX() - minSize;
            if (minSplit >= maxSplit) {
                return null;
            }

            double splitPoint = minSplit + random.nextDouble() * (maxSplit - minSplit);

            BoundingBox box1 = new BoundingBox(
                box.getMinX(), box.getMinY(), box.getMinZ(),
                splitPoint, box.getMaxY(), box.getMaxZ()
            );

            BoundingBox box2 = new BoundingBox(
                splitPoint, box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ()
            );

            return new BoundingBox[]{box1, box2};
        } else {
            double depth = box.getWidthZ();
            if (depth < minSize * 2) {
                return null;
            }

            double minSplit = box.getMinZ() + minSize;
            double maxSplit = box.getMaxZ() - minSize;
            if (minSplit >= maxSplit) {
                return null;
            }

            double splitPoint = minSplit + random.nextDouble() * (maxSplit - minSplit);

            BoundingBox box1 = new BoundingBox(
                box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), splitPoint
            );

            BoundingBox box2 = new BoundingBox(
                box.getMinX(), box.getMinY(), splitPoint,
                box.getMaxX(), box.getMaxY(), box.getMaxZ()
            );

            return new BoundingBox[]{box1, box2};
        }
    }

    /**
     * Checks if a box can be split given the minimum size constraint.
     *
     * @param box The box to check
     * @param minSize Minimum size requirement
     * @return true if the box can be split
     */
    private boolean canSplit(BoundingBox box, int minSize) {
        return box.getWidthX() >= minSize * 2 || box.getWidthZ() >= minSize * 2;
    }

    /**
     * Shrinks a bounding box by a random amount to create padding between rooms.
     *
     * @param box The box to shrink
     * @param minPadding Minimum padding on each side
     * @param maxPadding Maximum padding on each side
     * @return A new, smaller bounding box
     */
    public BoundingBox shrinkBox(BoundingBox box, int minPadding, int maxPadding) {
        int paddingX = minPadding + random.nextInt(maxPadding - minPadding + 1);
        int paddingZ = minPadding + random.nextInt(maxPadding - minPadding + 1);

        return new BoundingBox(
            box.getMinX() + paddingX,
            box.getMinY(),
            box.getMinZ() + paddingZ,
            box.getMaxX() - paddingX,
            box.getMaxY(),
            box.getMaxZ() - paddingZ
        );
    }
}
