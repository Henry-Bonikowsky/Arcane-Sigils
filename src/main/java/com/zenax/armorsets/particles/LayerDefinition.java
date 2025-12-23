package com.zenax.armorsets.particles;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Defines a layer in a preset - what to render at shape points.
 * Layers can be:
 * - particle: Spawn particles
 * - block_display: Spawn BlockDisplay entities
 * - text_display: Spawn TextDisplay entities
 * - item_display: Spawn ItemDisplay entities
 * - sound: Play sounds
 */
public class LayerDefinition {

    private LayerType type = LayerType.PARTICLE;

    // Particle settings
    private String particle = "FLAME";
    private int count = 1;
    private double spread = 0.1;
    private double speed = 0.02;

    // Color settings (for DUST particles or displays)
    private int[] colorStart = {255, 100, 0};  // RGB
    private int[] colorEnd = null;  // If set, interpolates

    // Size/scale settings
    private double sizeStart = 1.0;
    private double sizeEnd = -1;  // If >= 0, interpolates

    // Opacity settings
    private double opacityStart = 1.0;
    private double opacityEnd = -1;  // If >= 0, interpolates

    // Display settings
    private String block = "STONE";
    private String item = "DIAMOND";
    private String text = "";
    private String billboard = "CENTER";
    private boolean glow = false;
    private String transform = "GROUND";

    // Timing
    private double delay = 0.0;  // Delay before this layer starts (seconds)

    // Sound settings
    private String sound = "";
    private double volume = 1.0;
    private double pitch = 1.0;

    public LayerDefinition() {}

    /**
     * Render this layer at the given points.
     *
     * @param world    The world to render in
     * @param points   Points to render at
     * @param progress Animation progress 0.0 to 1.0
     * @param duration Total duration in seconds
     * @return List of spawned display entities (for cleanup)
     */
    public List<Entity> render(World world, List<Location> points, double progress, double duration) {
        List<Entity> spawned = new ArrayList<>();

        // Check delay
        double delayProgress = delay / duration;
        if (progress < delayProgress) {
            return spawned;
        }

        // Adjust progress for delay
        double layerProgress = (progress - delayProgress) / (1.0 - delayProgress);
        layerProgress = Math.max(0, Math.min(1, layerProgress));

        switch (type) {
            case PARTICLE -> renderParticles(world, points, layerProgress);
            case BLOCK_DISPLAY -> spawned.addAll(renderBlockDisplays(world, points, layerProgress));
            case TEXT_DISPLAY -> spawned.addAll(renderTextDisplays(world, points, layerProgress));
            case ITEM_DISPLAY -> spawned.addAll(renderItemDisplays(world, points, layerProgress));
            case SOUND -> renderSound(world, points, layerProgress);
        }

        return spawned;
    }

    // ============ Particle Rendering ============

    private void renderParticles(World world, List<Location> points, double progress) {
        Particle particleType = getParticleType();
        if (particleType == null) return;

        // Calculate interpolated values
        Color color = getInterpolatedColor(progress);
        double size = getInterpolatedSize(progress);

        // Build particle data
        Object particleData = buildParticleData(particleType, color, (float) size);

        for (Location loc : points) {
            if (particleData != null) {
                world.spawnParticle(particleType, loc, count, spread, spread, spread, speed, particleData);
            } else {
                world.spawnParticle(particleType, loc, count, spread, spread, spread, speed);
            }
        }
    }

    private Particle getParticleType() {
        try {
            return Particle.valueOf(particle.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try aliases
            return switch (particle.toUpperCase()) {
                case "FIRE" -> Particle.FLAME;
                case "MAGIC" -> Particle.ENCHANT;
                case "SPARKLE" -> Particle.END_ROD;
                case "HEART" -> Particle.HEART;
                case "REDSTONE" -> Particle.DUST;
                default -> Particle.FLAME;
            };
        }
    }

    private Object buildParticleData(Particle particle, Color color, float size) {
        String name = particle.name();
        if (name.equals("DUST") || name.equals("REDSTONE")) {
            return new Particle.DustOptions(color != null ? color : Color.RED, size);
        }
        if (name.equals("DUST_COLOR_TRANSITION") && colorEnd != null) {
            Color endColor = Color.fromRGB(colorEnd[0], colorEnd[1], colorEnd[2]);
            return new Particle.DustTransition(
                color != null ? color : Color.RED,
                endColor,
                size
            );
        }
        if (name.equals("BLOCK") || name.equals("BLOCK_DUST") || name.equals("FALLING_DUST")) {
            try {
                Material mat = Material.valueOf(block.toUpperCase());
                return mat.createBlockData();
            } catch (IllegalArgumentException e) {
                return Material.STONE.createBlockData();
            }
        }
        if (name.equals("ITEM") || name.equals("ITEM_CRACK")) {
            try {
                Material mat = Material.valueOf(item.toUpperCase());
                return new ItemStack(mat);
            } catch (IllegalArgumentException e) {
                return new ItemStack(Material.DIAMOND);
            }
        }
        return null;
    }

    // ============ Display Entity Rendering ============

    private List<Entity> renderBlockDisplays(World world, List<Location> points, double progress) {
        List<Entity> entities = new ArrayList<>();
        double scale = getInterpolatedSize(progress);

        Material blockMaterial;
        try {
            blockMaterial = Material.valueOf(block.toUpperCase());
            if (!blockMaterial.isBlock()) blockMaterial = Material.STONE;
        } catch (IllegalArgumentException e) {
            blockMaterial = Material.STONE;
        }

        Material finalMat = blockMaterial;
        for (Location loc : points) {
            BlockDisplay display = world.spawn(loc, BlockDisplay.class, bd -> {
                bd.setBlock(finalMat.createBlockData());
                bd.setBillboard(getBillboardMode());
                bd.setGlowing(glow);
                bd.setPersistent(false);

                if (Math.abs(scale - 1.0) > 0.01) {
                    bd.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f((float) scale, (float) scale, (float) scale),
                        new AxisAngle4f(0, 0, 1, 0)
                    ));
                }
            });
            entities.add(display);
        }
        return entities;
    }

    private List<Entity> renderTextDisplays(World world, List<Location> points, double progress) {
        List<Entity> entities = new ArrayList<>();
        double scale = getInterpolatedSize(progress);

        for (Location loc : points) {
            TextDisplay display = world.spawn(loc, TextDisplay.class, td -> {
                td.setText(text);
                td.setBillboard(getBillboardMode());
                td.setGlowing(glow);
                td.setPersistent(false);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setShadowed(true);

                if (Math.abs(scale - 1.0) > 0.01) {
                    td.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f((float) scale, (float) scale, (float) scale),
                        new AxisAngle4f(0, 0, 1, 0)
                    ));
                }
            });
            entities.add(display);
        }
        return entities;
    }

    private List<Entity> renderItemDisplays(World world, List<Location> points, double progress) {
        List<Entity> entities = new ArrayList<>();
        double scale = getInterpolatedSize(progress);

        Material itemMaterial;
        try {
            itemMaterial = Material.valueOf(item.toUpperCase());
        } catch (IllegalArgumentException e) {
            itemMaterial = Material.DIAMOND;
        }

        ItemStack itemStack = new ItemStack(itemMaterial);
        ItemDisplay.ItemDisplayTransform displayTransform;
        try {
            displayTransform = ItemDisplay.ItemDisplayTransform.valueOf(transform.toUpperCase());
        } catch (IllegalArgumentException e) {
            displayTransform = ItemDisplay.ItemDisplayTransform.GROUND;
        }

        ItemDisplay.ItemDisplayTransform finalTransform = displayTransform;
        for (Location loc : points) {
            ItemDisplay display = world.spawn(loc, ItemDisplay.class, id -> {
                id.setItemStack(itemStack);
                id.setItemDisplayTransform(finalTransform);
                id.setBillboard(getBillboardMode());
                id.setGlowing(glow);
                id.setPersistent(false);

                if (Math.abs(scale - 1.0) > 0.01) {
                    id.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f((float) scale, (float) scale, (float) scale),
                        new AxisAngle4f(0, 0, 1, 0)
                    ));
                }
            });
            entities.add(display);
        }
        return entities;
    }

    // ============ Sound Rendering ============

    private void renderSound(World world, List<Location> points, double progress) {
        if (sound.isEmpty() || points.isEmpty()) return;

        // Play at center point
        Location center = points.get(0);
        try {
            org.bukkit.Sound soundType = org.bukkit.Sound.valueOf(sound.toUpperCase());
            world.playSound(center, soundType, (float) volume, (float) pitch);
        } catch (IllegalArgumentException ignored) {}
    }

    // ============ Interpolation ============

    private Color getInterpolatedColor(double progress) {
        if (colorStart == null) return null;

        int r = colorStart[0];
        int g = colorStart[1];
        int b = colorStart[2];

        if (colorEnd != null) {
            r = (int) (colorStart[0] + (colorEnd[0] - colorStart[0]) * progress);
            g = (int) (colorStart[1] + (colorEnd[1] - colorStart[1]) * progress);
            b = (int) (colorStart[2] + (colorEnd[2] - colorStart[2]) * progress);
        }

        return Color.fromRGB(
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b))
        );
    }

    private double getInterpolatedSize(double progress) {
        if (sizeEnd < 0) return sizeStart;
        return sizeStart + (sizeEnd - sizeStart) * progress;
    }

    private Display.Billboard getBillboardMode() {
        try {
            return Display.Billboard.valueOf(billboard.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Display.Billboard.CENTER;
        }
    }

    // ============ YAML Parsing ============

    @SuppressWarnings("unchecked")
    public static LayerDefinition fromMap(Map<String, Object> map) {
        LayerDefinition layer = new LayerDefinition();

        String typeStr = (String) map.getOrDefault("type", "particle");
        try {
            layer.type = LayerType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            layer.type = LayerType.PARTICLE;
        }

        // Particle settings
        layer.particle = (String) map.getOrDefault("particle", "FLAME");
        layer.count = getInt(map, "count", 1);
        layer.spread = getDouble(map, "spread", 0.1);
        layer.speed = getDouble(map, "speed", 0.02);

        // Color
        String colorStartStr = (String) map.get("color_start");
        if (colorStartStr != null) {
            layer.colorStart = parseColor(colorStartStr);
        } else if (map.containsKey("color")) {
            layer.colorStart = parseColor((String) map.get("color"));
        }
        String colorEndStr = (String) map.get("color_end");
        if (colorEndStr != null) {
            layer.colorEnd = parseColor(colorEndStr);
        }

        // Size
        layer.sizeStart = getDouble(map, "size_start", getDouble(map, "size", 1.0));
        layer.sizeEnd = getDouble(map, "size_end", -1);

        // Opacity
        layer.opacityStart = getDouble(map, "opacity_start", 1.0);
        layer.opacityEnd = getDouble(map, "opacity_end", -1);

        // Display settings
        layer.block = (String) map.getOrDefault("block", "STONE");
        layer.item = (String) map.getOrDefault("item", "DIAMOND");
        layer.text = (String) map.getOrDefault("text", "");
        layer.billboard = (String) map.getOrDefault("billboard", "CENTER");
        layer.glow = (Boolean) map.getOrDefault("glow", false);
        layer.transform = (String) map.getOrDefault("transform", "GROUND");

        // Timing
        layer.delay = getDouble(map, "delay", 0.0);

        // Sound
        layer.sound = (String) map.getOrDefault("sound", "");
        layer.volume = getDouble(map, "volume", 1.0);
        layer.pitch = getDouble(map, "pitch", 1.0);

        return layer;
    }

    private static int[] parseColor(String colorStr) {
        if (colorStr == null) return new int[]{255, 255, 255};
        String[] parts = colorStr.split(",");
        if (parts.length >= 3) {
            try {
                return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
                };
            } catch (NumberFormatException e) {
                return new int[]{255, 255, 255};
            }
        }
        return new int[]{255, 255, 255};
    }

    private static double getDouble(Map<String, Object> map, String key, double defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultVal;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }

    // ============ Getters & Setters ============

    public LayerType getType() { return type; }
    public void setType(LayerType type) { this.type = type; }

    public String getParticle() { return particle; }
    public void setParticle(String particle) { this.particle = particle; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public double getDelay() { return delay; }
    public void setDelay(double delay) { this.delay = delay; }

    // ============ Enums ============

    public enum LayerType {
        PARTICLE,
        BLOCK_DISPLAY,
        TEXT_DISPLAY,
        ITEM_DISPLAY,
        SOUND
    }
}
