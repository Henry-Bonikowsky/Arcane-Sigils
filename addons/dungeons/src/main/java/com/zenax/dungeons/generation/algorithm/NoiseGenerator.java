package com.zenax.dungeons.generation.algorithm;

import java.util.Random;

/**
 * Perlin/Simplex noise generator for creating organic shapes.
 * Used for cave generation and natural-looking terrain features.
 */
public class NoiseGenerator {
    private final long seed;
    private final Random random;
    private final int[] permutation;

    // Gradient vectors for 3D noise
    private static final int[][] GRADIENTS_3D = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
        {1, 1, 0}, {-1, 1, 0}, {0, -1, 1}, {0, -1, -1}
    };

    /**
     * Creates a new noise generator with the given seed.
     *
     * @param seed The seed for random generation
     */
    public NoiseGenerator(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
        this.permutation = new int[512];

        // Initialize permutation array
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }

        // Shuffle using Fisher-Yates
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = p[i];
            p[i] = p[j];
            p[j] = temp;
        }

        // Duplicate for wrapping
        for (int i = 0; i < 512; i++) {
            permutation[i] = p[i & 255];
        }
    }

    /**
     * Generates 2D noise at the given coordinates.
     *
     * @param x X coordinate
     * @param z Z coordinate
     * @return Noise value between -1.0 and 1.0
     */
    public double noise2D(double x, double z) {
        return noise3D(x, z, 0);
    }

    /**
     * Generates 3D Perlin noise at the given coordinates.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Noise value between -1.0 and 1.0
     */
    public double noise3D(double x, double y, double z) {
        // Find unit cube that contains point
        int X = fastFloor(x) & 255;
        int Y = fastFloor(y) & 255;
        int Z = fastFloor(z) & 255;

        // Find relative x, y, z of point in cube
        x -= fastFloor(x);
        y -= fastFloor(y);
        z -= fastFloor(z);

        // Compute fade curves for each of x, y, z
        double u = fade(x);
        double v = fade(y);
        double w = fade(z);

        // Hash coordinates of the 8 cube corners
        int A = permutation[X] + Y;
        int AA = permutation[A] + Z;
        int AB = permutation[A + 1] + Z;
        int B = permutation[X + 1] + Y;
        int BA = permutation[B] + Z;
        int BB = permutation[B + 1] + Z;

        // Add blended results from 8 corners of cube
        return lerp(w,
            lerp(v,
                lerp(u, grad(permutation[AA], x, y, z),
                        grad(permutation[BA], x - 1, y, z)),
                lerp(u, grad(permutation[AB], x, y - 1, z),
                        grad(permutation[BB], x - 1, y - 1, z))),
            lerp(v,
                lerp(u, grad(permutation[AA + 1], x, y, z - 1),
                        grad(permutation[BA + 1], x - 1, y, z - 1)),
                lerp(u, grad(permutation[AB + 1], x, y - 1, z - 1),
                        grad(permutation[BB + 1], x - 1, y - 1, z - 1)))
        );
    }

    /**
     * Generates octave noise (layered noise) for more natural patterns.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param octaves Number of noise layers to combine
     * @param persistence How much each octave contributes
     * @param scale Scale of the noise
     * @return Combined noise value
     */
    public double octaveNoise3D(double x, double y, double z, int octaves, double persistence, double scale) {
        double total = 0;
        double frequency = scale;
        double amplitude = 1;
        double maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            total += noise3D(x * frequency, y * frequency, z * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2;
        }

        return total / maxValue;
    }

    /**
     * Generates octave noise in 2D.
     *
     * @param x X coordinate
     * @param z Z coordinate
     * @param octaves Number of noise layers to combine
     * @param persistence How much each octave contributes
     * @param scale Scale of the noise
     * @return Combined noise value
     */
    public double octaveNoise2D(double x, double z, int octaves, double persistence, double scale) {
        return octaveNoise3D(x, 0, z, octaves, persistence, scale);
    }

    /**
     * Fast floor function.
     *
     * @param x The value to floor
     * @return Floored integer value
     */
    private int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    /**
     * Fade function for smoothing (6t^5 - 15t^4 + 10t^3).
     *
     * @param t Input value
     * @return Smoothed value
     */
    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /**
     * Linear interpolation.
     *
     * @param t Interpolation factor
     * @param a Start value
     * @param b End value
     * @return Interpolated value
     */
    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /**
     * Gradient function.
     *
     * @param hash Hash value
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Gradient value
     */
    private double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        int[] gradient = GRADIENTS_3D[h];
        return gradient[0] * x + gradient[1] * y + gradient[2] * z;
    }

    /**
     * Gets the seed used by this generator.
     *
     * @return The seed
     */
    public long getSeed() {
        return seed;
    }
}
