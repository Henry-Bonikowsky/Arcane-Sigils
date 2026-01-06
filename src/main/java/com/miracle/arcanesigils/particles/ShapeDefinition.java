package com.miracle.arcanesigils.particles;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Defines a shape that can generate a list of points.
 * Shapes are loaded from YAML and can be:
 * - PRIMITIVE: Built-in shapes (circle, line, sphere, etc.)
 * - COMPOSED: Base shape + modifiers
 */
public class ShapeDefinition {

    private String id;
    private String name;
    private String description;
    private ShapeType type;
    private String baseShape;  // For COMPOSED types
    private Map<String, ParamDef> params = new LinkedHashMap<>();

    public ShapeDefinition(String id) {
        this.id = id;
        this.name = id;
        this.type = ShapeType.PRIMITIVE;
    }

    /**
     * Generate points for this shape.
     *
     * @param center   The center location
     * @param params   Parameter values (radius, points, etc.)
     * @param progress Animation progress 0.0 to 1.0
     * @return List of locations forming the shape
     */
    public List<Location> generatePoints(Location center, Map<String, Object> params, double progress) {
        if (center == null || center.getWorld() == null) {
            return Collections.emptyList();
        }

        // Get parameter values with defaults
        double radius = getDouble(params, "radius", 2.0);
        double height = getDouble(params, "height", 0.0);
        double turns = getDouble(params, "turns", 1.0);
        int segments = getInt(params, "segments", 10);
        double jitter = getDouble(params, "jitter", 0.0);

        // Support both "points" (direct count) and "spacing" (distance between points)
        // If spacing is provided, calculate points based on shape geometry
        double spacing = getDouble(params, "spacing", 0.0);
        int points;
        if (spacing > 0) {
            points = calculatePointsFromSpacing(id.toLowerCase(), radius, height, turns, spacing);
        } else {
            points = getInt(params, "points", 20);
        }

        return switch (id.toLowerCase()) {
            case "circle" -> generateCircle(center, radius, points, height);
            case "spiral" -> generateSpiral(center, radius, height, turns, points);
            case "helix" -> generateHelix(center, radius, height, turns, points);
            case "sphere" -> generateSphere(center, radius, points, height);
            case "line" -> generateLine(center, radius, points, height);
            case "beam" -> generateBeam(center, radius, segments, jitter, height);
            case "cone" -> generateCone(center, radius, height, points);
            case "point" -> Collections.singletonList(center.clone().add(0, height, 0));
            case "grid", "square" -> generateGrid(center, radius, points, height);
            case "disc", "filled_circle" -> generateDisc(center, radius, spacing, height);
            default -> generateCircle(center, radius, points, height);
        };
    }

    // ============ Shape Generators ============

    private List<Location> generateCircle(Location center, double radius, int points, double height) {
        List<Location> locations = new ArrayList<>();
        double y = center.getY() + height;
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            locations.add(new Location(center.getWorld(), x, y, z));
        }
        return locations;
    }

    private List<Location> generateSpiral(Location center, double radius, double height, double turns, int points) {
        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            double angle = 2 * Math.PI * turns * t;
            double x = center.getX() + radius * Math.cos(angle);
            double y = center.getY() + height * t;
            double z = center.getZ() + radius * Math.sin(angle);
            locations.add(new Location(center.getWorld(), x, y, z));
        }
        return locations;
    }

    private List<Location> generateHelix(Location center, double radius, double height, double turns, int points) {
        // DNA-style double helix - strands weave in and out of each other
        List<Location> locations = new ArrayList<>();
        int halfPoints = points / 2;

        // Oscillation creates the weaving/crossing effect
        double oscillation = radius * 0.3; // How much strands weave in/out
        double weavesPerTurn = 2.0; // How many times strands cross per full rotation

        for (int i = 0; i < halfPoints; i++) {
            double t = (double) i / halfPoints;
            double angle = 2 * Math.PI * turns * t;
            double y = center.getY() + height * t;

            // Weave factor oscillates as we go up
            double weaveAngle = angle * weavesPerTurn;
            double weave = Math.sin(weaveAngle) * oscillation;

            // First strand - radius oscillates inward/outward
            double r1 = radius + weave;
            double x1 = center.getX() + r1 * Math.cos(angle);
            double z1 = center.getZ() + r1 * Math.sin(angle);
            locations.add(new Location(center.getWorld(), x1, y, z1));

            // Second strand - opposite oscillation (when strand 1 is out, strand 2 is in)
            double r2 = radius - weave;
            double x2 = center.getX() + r2 * Math.cos(angle + Math.PI);
            double z2 = center.getZ() + r2 * Math.sin(angle + Math.PI);
            locations.add(new Location(center.getWorld(), x2, y, z2));
        }
        return locations;
    }

    private List<Location> generateSphere(Location center, double radius, int points, double height) {
        List<Location> locations = new ArrayList<>();
        // Fibonacci sphere for even distribution
        // Note: height param is ignored for sphere - sphere is centered on the given center
        // Use y_offset in PARTICLE effect to adjust vertical position
        double goldenRatio = (1 + Math.sqrt(5)) / 2;
        double baseY = center.getY(); // Sphere centered at given location, not offset by height
        for (int i = 0; i < points; i++) {
            double theta = 2 * Math.PI * i / goldenRatio;
            double phi = Math.acos(1 - 2 * (i + 0.5) / points);

            double x = center.getX() + radius * Math.sin(phi) * Math.cos(theta);
            double y = baseY + radius * Math.cos(phi);
            double z = center.getZ() + radius * Math.sin(phi) * Math.sin(theta);
            locations.add(new Location(center.getWorld(), x, y, z));
        }
        return locations;
    }

    private List<Location> generateLine(Location center, double length, int points, double height) {
        List<Location> locations = new ArrayList<>();
        // Horizontal line along X axis (doesn't depend on player facing)
        double y = center.getY() + height;
        double halfLength = length / 2;

        for (int i = 0; i < points; i++) {
            double t = (double) i / (points - 1);
            double x = center.getX() - halfLength + length * t;
            locations.add(new Location(center.getWorld(), x, y, center.getZ()));
        }
        return locations;
    }

    /**
     * Generate a line between two points (Point A → Point B).
     * This is the proper way to draw trails, teleport effects, etc.
     */
    public List<Location> generateLineBetween(Location from, Location to, int points) {
        List<Location> locations = new ArrayList<>();
        if (from == null || to == null || from.getWorld() == null) {
            return locations;
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();

        for (int i = 0; i < points; i++) {
            double t = (double) i / (points - 1);
            locations.add(new Location(
                from.getWorld(),
                from.getX() + dx * t,
                from.getY() + dy * t,
                from.getZ() + dz * t
            ));
        }
        return locations;
    }

    /**
     * Generate a beam between two points with optional jitter (for lightning effects).
     */
    public List<Location> generateBeamBetween(Location from, Location to, int segments, double jitter) {
        List<Location> locations = new ArrayList<>();
        if (from == null || to == null || from.getWorld() == null) {
            return locations;
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();

        Random random = new Random();
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            Location point = new Location(
                from.getWorld(),
                from.getX() + dx * t,
                from.getY() + dy * t,
                from.getZ() + dz * t
            );

            // Add jitter to middle points (not endpoints)
            if (jitter > 0 && i > 0 && i < segments) {
                point.add(
                    (random.nextDouble() - 0.5) * jitter,
                    (random.nextDouble() - 0.5) * jitter,
                    (random.nextDouble() - 0.5) * jitter
                );
            }
            locations.add(point);
        }
        return locations;
    }

    private List<Location> generateBeam(Location center, double length, int segments, double jitter, double height) {
        // Beam with optional jitter for lightning effect (horizontal along X axis)
        List<Location> locations = new ArrayList<>();
        double y = center.getY() + height;
        double halfLength = length / 2;

        Random random = new Random();
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double x = center.getX() - halfLength + length * t;
            Location point = new Location(center.getWorld(), x, y, center.getZ());

            if (jitter > 0 && i > 0 && i < segments) {
                point.add(
                    (random.nextDouble() - 0.5) * jitter,
                    (random.nextDouble() - 0.5) * jitter,
                    (random.nextDouble() - 0.5) * jitter
                );
            }
            locations.add(point);
        }
        return locations;
    }

    private List<Location> generateCone(Location center, double radius, double height, int points) {
        List<Location> locations = new ArrayList<>();
        int rings = Math.max(3, points / 8);
        int pointsPerRing = points / rings;

        for (int r = 0; r < rings; r++) {
            double t = (double) r / (rings - 1);
            double ringRadius = radius * (1 - t); // Shrinks toward top
            double ringHeight = height * t;

            for (int i = 0; i < pointsPerRing; i++) {
                double angle = (2 * Math.PI * i) / pointsPerRing;
                double x = center.getX() + ringRadius * Math.cos(angle);
                double y = center.getY() + ringHeight;
                double z = center.getZ() + ringRadius * Math.sin(angle);
                locations.add(new Location(center.getWorld(), x, y, z));
            }
        }
        return locations;
    }

    /**
     * Generate a square grid of points centered on the location.
     * The 'radius' is used as half the side length (so full side = 2*radius).
     * The 'points' parameter determines grid density (sqrt(points) per side).
     */
    private List<Location> generateGrid(Location center, double radius, int points, double height) {
        List<Location> locations = new ArrayList<>();

        // Calculate grid dimensions
        int gridSize = (int) Math.ceil(Math.sqrt(points));
        if (gridSize < 2) gridSize = 2;

        double spacing = (2 * radius) / (gridSize - 1);
        double halfSize = radius;
        double y = center.getY() + height;

        for (int x = 0; x < gridSize; x++) {
            for (int z = 0; z < gridSize; z++) {
                double offsetX = -halfSize + x * spacing;
                double offsetZ = -halfSize + z * spacing;
                locations.add(new Location(center.getWorld(), center.getX() + offsetX, y, center.getZ() + offsetZ));
            }
        }
        return locations;
    }

    /**
     * Generate a filled circle (disc) of points centered on the location.
     * Uses concentric rings to fill the circular area.
     * 'spacing' controls how far apart points are placed.
     */
    private List<Location> generateDisc(Location center, double radius, double spacing, double height) {
        List<Location> locations = new ArrayList<>();
        double y = center.getY() + height;

        // Default spacing if not specified
        if (spacing <= 0) spacing = 0.5;

        // Always include center point
        locations.add(new Location(center.getWorld(), center.getX(), y, center.getZ()));

        // Generate concentric rings from center outward
        for (double r = spacing; r <= radius; r += spacing) {
            // Calculate points for this ring (circumference / spacing)
            int pointsInRing = Math.max(6, (int) Math.ceil(2 * Math.PI * r / spacing));
            for (int i = 0; i < pointsInRing; i++) {
                double angle = (2 * Math.PI * i) / pointsInRing;
                double x = center.getX() + r * Math.cos(angle);
                double z = center.getZ() + r * Math.sin(angle);
                locations.add(new Location(center.getWorld(), x, y, z));
            }
        }
        return locations;
    }

    // ============ Spacing Calculation ============

    /**
     * Calculate how many points are needed to achieve a desired spacing.
     * This allows users to specify "0.5 blocks apart" instead of "20 points".
     *
     * @param shapeId Shape type (circle, spiral, helix, etc.)
     * @param radius  Shape radius
     * @param height  Shape height (for spiral/helix)
     * @param turns   Number of turns (for spiral/helix)
     * @param spacing Desired distance between points
     * @return Number of points needed
     */
    private int calculatePointsFromSpacing(String shapeId, double radius, double height, double turns, double spacing) {
        if (spacing <= 0) return 20; // Fallback

        double totalLength;
        switch (shapeId) {
            case "circle", "ring" -> {
                // Circumference = 2πr
                totalLength = 2 * Math.PI * radius;
            }
            case "spiral" -> {
                // Approximate arc length of spiral: sqrt((2πr*turns)² + height²)
                double horizontalLength = 2 * Math.PI * radius * turns;
                totalLength = Math.sqrt(horizontalLength * horizontalLength + height * height);
            }
            case "helix" -> {
                // Double helix = 2 spirals, each with arc length
                double horizontalLength = 2 * Math.PI * radius * turns;
                totalLength = 2 * Math.sqrt(horizontalLength * horizontalLength + height * height);
            }
            case "sphere" -> {
                // Approximate surface coverage: 4πr² / spacing² for area-based distribution
                double surfaceArea = 4 * Math.PI * radius * radius;
                return Math.max(8, Math.min(200, (int) (surfaceArea / (spacing * spacing))));
            }
            case "line" -> {
                // Line length = 2 * radius (from -radius to +radius)
                totalLength = 2 * radius;
            }
            case "beam" -> {
                // Same as line
                totalLength = 2 * radius;
            }
            case "cone" -> {
                // Approximate: slant height * circumference average
                double slantHeight = Math.sqrt(radius * radius + height * height);
                double avgCircumference = Math.PI * radius; // Average of base and top (0)
                totalLength = slantHeight + avgCircumference;
            }
            case "grid", "square" -> {
                // Grid: calculate points per side based on spacing, then square it
                // Side length = 2 * radius, points per side = ceil(sideLength / spacing) + 1
                double sideLength = 2 * radius;
                int pointsPerSide = (int) Math.ceil(sideLength / spacing) + 1;
                return Math.max(4, Math.min(625, pointsPerSide * pointsPerSide)); // Max 25x25 grid
            }
            case "disc", "filled_circle" -> {
                // Disc uses spacing directly in generateDisc, not point count
                // Approximate: area / spacing² for distribution
                double area = Math.PI * radius * radius;
                return Math.max(1, Math.min(200, (int) (area / (spacing * spacing))));
            }
            default -> {
                totalLength = 2 * Math.PI * radius; // Default to circle
            }
        }

        int points = (int) Math.ceil(totalLength / spacing);
        return Math.max(4, Math.min(200, points)); // Clamp between 4 and 200
    }

    // ============ Utility ============

    private double getDouble(Map<String, Object> params, String key, double defaultVal) {
        Object val = params.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        ParamDef def = this.params.get(key);
        return def != null ? def.defaultValue : defaultVal;
    }

    private int getInt(Map<String, Object> params, String key, int defaultVal) {
        return (int) getDouble(params, key, defaultVal);
    }

    // ============ YAML Parsing ============

    public static ShapeDefinition fromConfig(String id, ConfigurationSection section) {
        ShapeDefinition shape = new ShapeDefinition(id);
        shape.name = section.getString("name", id);
        shape.description = section.getString("description", "");

        String typeStr = section.getString("type", "PRIMITIVE").toUpperCase();
        try {
            shape.type = ShapeType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            shape.type = ShapeType.PRIMITIVE;
        }

        shape.baseShape = section.getString("base", null);

        // Parse parameters
        ConfigurationSection paramsSection = section.getConfigurationSection("params");
        if (paramsSection != null) {
            for (String paramKey : paramsSection.getKeys(false)) {
                ConfigurationSection paramSection = paramsSection.getConfigurationSection(paramKey);
                if (paramSection != null) {
                    ParamDef param = new ParamDef();
                    param.name = paramKey;
                    param.defaultValue = paramSection.getDouble("default", 0);
                    param.min = paramSection.getDouble("min", Double.MIN_VALUE);
                    param.max = paramSection.getDouble("max", Double.MAX_VALUE);
                    param.description = paramSection.getString("description", "");
                    shape.params.put(paramKey, param);
                }
            }
        }

        return shape;
    }

    // ============ Getters ============

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ShapeType getType() { return type; }
    public String getBaseShape() { return baseShape; }
    public Map<String, ParamDef> getParams() { return params; }

    // ============ Inner Classes ============

    public enum ShapeType {
        PRIMITIVE,   // Built-in shape
        COMPOSED     // Base shape + modifiers
    }

    public static class ParamDef {
        public String name;
        public double defaultValue;
        public double min;
        public double max;
        public String description;
    }
}
