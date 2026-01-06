package com.miracle.arcanesigils.particles;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Defines a modifier that transforms shape points over time.
 * Modifiers are loaded from YAML and can:
 * - Rotate points around an axis
 * - Move points (rise, expand, shrink)
 * - Pulse (scale in and out)
 * - Follow an entity
 */
public class ModifierDefinition {

    private String id;
    private String name;
    private String description;
    private ModifierType type;
    private String axis = "Y";  // X, Y, or Z
    private Map<String, ParamDef> params = new LinkedHashMap<>();

    public ModifierDefinition(String id) {
        this.id = id;
        this.name = id;
        this.type = ModifierType.TRANSFORM;
    }

    /**
     * Apply this modifier to a list of points.
     *
     * @param points   The points to modify (modified in place)
     * @param center   The center of rotation/transformation
     * @param progress Animation progress 0.0 to 1.0
     * @param params   Modifier parameters (speed, etc.)
     */
    public void apply(List<Location> points, Location center, double progress, Map<String, Object> params) {
        double speed = getDouble(params, "speed", 360);
        double amount = getDouble(params, "amount", 1.0);

        switch (id.toLowerCase()) {
            case "rotate" -> applyRotate(points, center, progress, speed);
            case "rise" -> applyRise(points, progress, speed);
            case "fall" -> applyRise(points, progress, -speed);
            case "expand" -> applyExpand(points, center, progress, speed);
            case "shrink" -> applyExpand(points, center, progress, -speed);
            case "pulse" -> {
                String shape = getString(params, "shape", "sphere");
                boolean includeY = !shape.equalsIgnoreCase("circle");
                applyPulse(points, center, progress, speed, amount, includeY);
            }
            case "wobble" -> applyWobble(points, progress, amount);
            case "wave" -> applyWave(points, center, progress, speed, amount);
        }
    }

    // ============ Modifier Implementations ============

    private void applyRotate(List<Location> points, Location center, double progress, double degreesPerSecond) {
        double totalDegrees = degreesPerSecond * progress;
        double radians = Math.toRadians(totalDegrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        for (Location loc : points) {
            double dx = loc.getX() - center.getX();
            double dy = loc.getY() - center.getY();
            double dz = loc.getZ() - center.getZ();

            switch (axis.toUpperCase()) {
                case "Y" -> {
                    // Rotate around Y axis
                    double newX = dx * cos - dz * sin;
                    double newZ = dx * sin + dz * cos;
                    loc.setX(center.getX() + newX);
                    loc.setZ(center.getZ() + newZ);
                }
                case "X" -> {
                    // Rotate around X axis
                    double newY = dy * cos - dz * sin;
                    double newZ = dy * sin + dz * cos;
                    loc.setY(center.getY() + newY);
                    loc.setZ(center.getZ() + newZ);
                }
                case "Z" -> {
                    // Rotate around Z axis
                    double newX = dx * cos - dy * sin;
                    double newY = dx * sin + dy * cos;
                    loc.setX(center.getX() + newX);
                    loc.setY(center.getY() + newY);
                }
            }
        }
    }

    private void applyRise(List<Location> points, double progress, double blocksPerSecond) {
        double offset = blocksPerSecond * progress;
        for (Location loc : points) {
            loc.setY(loc.getY() + offset);
        }
    }

    private void applyExpand(List<Location> points, Location center, double progress, double scalePerSecond) {
        double scale = 1.0 + (scalePerSecond * progress);
        if (scale <= 0) scale = 0.01; // Prevent negative/zero scale

        for (Location loc : points) {
            double dx = loc.getX() - center.getX();
            double dy = loc.getY() - center.getY();
            double dz = loc.getZ() - center.getZ();

            loc.setX(center.getX() + dx * scale);
            loc.setY(center.getY() + dy * scale);
            loc.setZ(center.getZ() + dz * scale);
        }
    }

    private void applyPulse(List<Location> points, Location center, double progress, double cyclesPerSecond, double amount, boolean includeY) {
        // Pulse = oscillating scale (sin wave)
        // shape=sphere scales X/Y/Z, shape=circle scales only X/Z (flat horizontal)
        double phase = progress * cyclesPerSecond * 2 * Math.PI;
        double scale = 1.0 + amount * Math.sin(phase);

        for (Location loc : points) {
            double dx = loc.getX() - center.getX();
            double dy = loc.getY() - center.getY();
            double dz = loc.getZ() - center.getZ();

            loc.setX(center.getX() + dx * scale);
            if (includeY) {
                loc.setY(center.getY() + dy * scale);
            }
            loc.setZ(center.getZ() + dz * scale);
        }
    }

    private void applyWobble(List<Location> points, double progress, double amount) {
        // Add random-ish wobble based on progress
        Random random = new Random((long) (progress * 1000));
        for (Location loc : points) {
            loc.add(
                (random.nextDouble() - 0.5) * amount,
                (random.nextDouble() - 0.5) * amount,
                (random.nextDouble() - 0.5) * amount
            );
        }
    }

    private void applyWave(List<Location> points, Location center, double progress, double speed, double amplitude) {
        // Wave effect - Y offset based on distance from center
        for (Location loc : points) {
            double dist = Math.sqrt(
                Math.pow(loc.getX() - center.getX(), 2) +
                Math.pow(loc.getZ() - center.getZ(), 2)
            );
            double phase = (dist * 2 + progress * speed) * Math.PI;
            double yOffset = Math.sin(phase) * amplitude;
            loc.setY(loc.getY() + yOffset);
        }
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

    private String getString(Map<String, Object> params, String key, String defaultVal) {
        Object val = params.get(key);
        if (val instanceof String) {
            return (String) val;
        }
        return defaultVal;
    }

    // ============ YAML Parsing ============

    public static ModifierDefinition fromConfig(String id, ConfigurationSection section) {
        ModifierDefinition modifier = new ModifierDefinition(id);
        modifier.name = section.getString("name", id);
        modifier.description = section.getString("description", "");
        modifier.axis = section.getString("axis", "Y").toUpperCase();

        String typeStr = section.getString("type", "TRANSFORM").toUpperCase();
        try {
            modifier.type = ModifierType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            modifier.type = ModifierType.TRANSFORM;
        }

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
                    modifier.params.put(paramKey, param);
                }
            }
        }

        return modifier;
    }

    // ============ Getters ============

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ModifierType getType() { return type; }
    public String getAxis() { return axis; }
    public Map<String, ParamDef> getParams() { return params; }

    // ============ Inner Classes ============

    public enum ModifierType {
        TRANSFORM,   // Modifies positions
        APPEARANCE   // Modifies visual properties (handled by renderer)
    }

    public static class ParamDef {
        public String name;
        public double defaultValue;
        public double min;
        public double max;
        public String description;
    }

    /**
     * An instance of a modifier with specific parameter values.
     */
    public static class ModifierInstance {
        private final ModifierDefinition definition;
        private final Map<String, Object> params;

        public ModifierInstance(ModifierDefinition definition, Map<String, Object> params) {
            this.definition = definition;
            this.params = params != null ? params : new HashMap<>();
        }

        public void apply(List<Location> points, Location center, double progress) {
            definition.apply(points, center, progress, params);
        }

        public ModifierDefinition getDefinition() { return definition; }
        public Map<String, Object> getParams() { return params; }
    }
}
