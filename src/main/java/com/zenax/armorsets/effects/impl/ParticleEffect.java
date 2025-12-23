package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import com.zenax.armorsets.particles.ShapeDefinition;
import com.zenax.armorsets.particles.ShapeEngine;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified particle effect that supports:
 * - Basic particles at a location
 * - Shapes (circle, spiral, helix, sphere, line, beam, cone, ring)
 * - Point A → Point B (for trails, teleport effects, etc.)
 * - Presets from ShapeEngine
 * - Variable references ($oldPos, $targetPos, etc.)
 *
 * Formats:
 * - PARTICLE:FLAME:100 @Self                           - Basic: 100 flame particles
 * - PARTICLE:DUST:50:255:215:0:1.5 @Victim             - Dust with RGB color and size
 * - PARTICLE:FLAME:shape=circle:radius=3:points=30     - Circle shape
 * - PARTICLE:FLAME:shape=line:from=$oldPos:to=@Self    - Line from saved position to current
 * - PARTICLE:preset=fire_spiral:duration=3             - Use ShapeEngine preset
 */
public class ParticleEffect extends AbstractEffect {

    // Track active shape animations to prevent duplicate spawns from TICK signals
    // Key: "playerUUID:sigilId:signalKey" -> expiration timestamp
    private static final Map<String, Long> activeAnimations = new ConcurrentHashMap<>();

    public ParticleEffect() {
        super("PARTICLE", "Spawns particles at target location with optional shapes");
    }

    /**
     * Generate a unique key for tracking active animations.
     * Key is specific to the exact effect configuration so different effects don't block each other.
     */
    private String getAnimationKey(EffectContext context, String shapeId) {
        Player player = context.getPlayer();
        EffectParams params = context.getParams();

        // Get sigilId from metadata (set by SignalHandler.processSigilForSignal)
        String sigilId = context.getMetadata("sourceSigilId", "unknown");
        String signalKey = context.getSignalType() != null ? context.getSignalType().name() : "unknown";

        // Include shape params to make key unique per effect configuration
        String particleType = params.getString("particle_type", "FLAME");
        double radius = params.getDouble("radius", 2.0);
        int points = params.getInt("points", 20);

        return player.getUniqueId() + ":" + sigilId + ":" + signalKey + ":" +
               shapeId + ":" + particleType + ":" + radius + ":" + points;
    }

    /**
     * Check if an animation is already running for this context.
     */
    private boolean isAnimationActive(String key) {
        Long expiration = activeAnimations.get(key);
        if (expiration == null) return false;
        if (System.currentTimeMillis() > expiration) {
            activeAnimations.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Mark an animation as active for the given duration.
     */
    private void markAnimationActive(String key, double durationSeconds) {
        long expirationTime = System.currentTimeMillis() + (long)(durationSeconds * 1000);
        activeAnimations.put(key, expirationTime);
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // Check for key=value format (new style)
        boolean hasKeyValue = cleanedString.contains("=");

        if (hasKeyValue) {
            // New format: PARTICLE:FLAME:shape=circle:radius=3
            if (parts.length >= 2 && !parts[1].contains("=")) {
                params.set("particle_type", parts[1].toUpperCase());
            }
            for (int i = 1; i < parts.length; i++) {
                if (parts[i].contains("=")) {
                    String[] kv = parts[i].split("=", 2);
                    parseKeyValue(params, kv[0].toLowerCase(), kv[1]);
                }
            }
        } else {
            // Legacy format: PARTICLE:TYPE:COUNT or PARTICLE:DUST:COUNT:R:G:B:SIZE
            if (parts.length >= 2) {
                String potentialParticle = parts[1].toUpperCase();
                try {
                    int count = Integer.parseInt(potentialParticle);
                    params.set("particle_type", "FLAME");
                    params.setValue(count);
                } catch (NumberFormatException e) {
                    params.set("particle_type", potentialParticle);
                }
            }
            if (parts.length >= 3) {
                try {
                    params.setValue(Double.parseDouble(parts[2]));
                } catch (NumberFormatException ignored) {}
            }

            // Dust particle color: PARTICLE:DUST:count:R:G:B:size
            String particleType = params.getString("particle_type", "");
            if (isDustParticle(particleType) && parts.length >= 7) {
                try {
                    params.set("red", Integer.parseInt(parts[3]));
                    params.set("green", Integer.parseInt(parts[4]));
                    params.set("blue", Integer.parseInt(parts[5]));
                    params.set("size", Float.parseFloat(parts[6]));
                } catch (NumberFormatException ignored) {}
            }
            // Material particle: PARTICLE:BLOCK:count:MATERIAL
            else if (isMaterialParticle(particleType) && parts.length >= 4) {
                params.set("material", parts[3].toUpperCase());
            }
        }

        return params;
    }

    private void parseKeyValue(EffectParams params, String key, String value) {
        switch (key) {
            // Shape params
            case "shape" -> params.set("shape", value.toLowerCase());
            case "radius" -> params.set("radius", parseDouble(value, 2.0));
            case "points" -> params.set("points", parseInt(value, 20));
            case "spacing" -> params.set("spacing", parseDouble(value, 0.5));
            case "height" -> params.set("height", parseDouble(value, 3.0));
            case "turns" -> params.set("turns", parseDouble(value, 2.0));
            case "segments" -> params.set("segments", parseInt(value, 10));
            case "jitter" -> params.set("jitter", parseDouble(value, 0.0));

            // Animation params (shapes are animated by default)
            case "duration" -> params.set("duration", parseDouble(value, 2.0));
            case "rotate_speed", "rotate" -> params.set("rotate_speed", parseDouble(value, 1.0));
            case "follow_target", "follow" -> params.set("follow_target", Boolean.parseBoolean(value));
            case "y_offset" -> params.set("y_offset", parseDouble(value, 0.0));

            // Point A → Point B
            case "from" -> params.set("from", value);
            case "to" -> params.set("to", value);

            // Preset
            case "preset" -> params.set("preset", value);

            // Basic params
            case "count" -> params.setValue(parseInt(value, 10));
            case "spread" -> params.set("spread", parseDouble(value, 0.5));
            case "speed" -> params.set("speed", parseDouble(value, 0.02));

            // Dust color
            case "red", "r" -> params.set("red", parseInt(value, 255));
            case "green", "g" -> params.set("green", parseInt(value, 0));
            case "blue", "b" -> params.set("blue", parseInt(value, 0));
            case "size" -> params.set("size", parseFloat(value, 1.0f));
            case "color" -> parseColor(params, value);

            // Material
            case "material" -> params.set("material", value.toUpperCase());

            // Pulse mode (expanding shockwave)
            case "pulse" -> params.set("pulse", Boolean.parseBoolean(value));
            case "pulse_count" -> params.set("pulse_count", parseInt(value, 3));
            case "pulse_start" -> params.set("pulse_start", parseDouble(value, 1.0));
            case "pulse_end" -> params.set("pulse_end", parseDouble(value, 7.0));
            case "pulse_delay" -> params.set("pulse_delay", parseInt(value, 10));
            case "pulse_fade" -> params.set("pulse_fade", Boolean.parseBoolean(value));
        }
    }

    private void parseColor(EffectParams params, String colorStr) {
        // Support hex color: #FFD700 or named colors
        if (colorStr.startsWith("#") && colorStr.length() == 7) {
            try {
                int rgb = Integer.parseInt(colorStr.substring(1), 16);
                params.set("red", (rgb >> 16) & 0xFF);
                params.set("green", (rgb >> 8) & 0xFF);
                params.set("blue", rgb & 0xFF);
            } catch (NumberFormatException ignored) {}
        } else {
            // Named colors
            switch (colorStr.toUpperCase()) {
                case "GOLD" -> { params.set("red", 255); params.set("green", 215); params.set("blue", 0); }
                case "RED" -> { params.set("red", 255); params.set("green", 0); params.set("blue", 0); }
                case "BLUE" -> { params.set("red", 0); params.set("green", 0); params.set("blue", 255); }
                case "GREEN" -> { params.set("red", 0); params.set("green", 255); params.set("blue", 0); }
                case "PURPLE" -> { params.set("red", 128); params.set("green", 0); params.set("blue", 255); }
                case "WHITE" -> { params.set("red", 255); params.set("green", 255); params.set("blue", 255); }
                case "BLACK" -> { params.set("red", 0); params.set("green", 0); params.set("blue", 0); }
                case "ORANGE" -> { params.set("red", 255); params.set("green", 165); params.set("blue", 0); }
                case "YELLOW" -> { params.set("red", 255); params.set("green", 255); params.set("blue", 0); }
                case "CYAN" -> { params.set("red", 0); params.set("green", 255); params.set("blue", 255); }
                case "PINK" -> { params.set("red", 255); params.set("green", 105); params.set("blue", 180); }
            }
        }
    }

    private float parseFloat(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }

    private boolean isDustParticle(String particleName) {
        // Handle format like "DUST:255:215:0" - extract just the particle name
        String baseName = particleName.contains(":") ? particleName.split(":")[0].toUpperCase() : particleName.toUpperCase();
        return baseName.equals("DUST") || baseName.equals("DUST_COLOR_TRANSITION") ||
               baseName.equals("REDSTONE");
    }

    /**
     * Convert named color to RGB values.
     */
    private int[] getColorRGB(String colorName) {
        return switch (colorName.toUpperCase()) {
            case "GOLD" -> new int[]{255, 215, 0};
            case "RED" -> new int[]{255, 0, 0};
            case "BLUE" -> new int[]{0, 0, 255};
            case "GREEN" -> new int[]{0, 255, 0};
            case "PURPLE" -> new int[]{128, 0, 128};
            case "WHITE" -> new int[]{255, 255, 255};
            case "ORANGE" -> new int[]{255, 165, 0};
            case "YELLOW" -> new int[]{255, 255, 0};
            case "CYAN" -> new int[]{0, 255, 255};
            case "PINK" -> new int[]{255, 105, 180};
            case "SAND" -> new int[]{194, 178, 128};
            case "BLACK" -> new int[]{0, 0, 0};
            default -> new int[]{255, 0, 0}; // Default to red
        };
    }

    private boolean isMaterialParticle(String particleName) {
        return particleName.equals("BLOCK") || particleName.equals("BLOCK_DUST") ||
               particleName.equals("FALLING_DUST") || particleName.equals("ITEM") ||
               particleName.equals("BLOCK_CRACK") || particleName.equals("ITEM_CRACK");
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) return false;

        // Check for preset mode
        String preset = params.getString("preset", null);
        if (preset != null) {
            return executePreset(context, preset);
        }

        // Check for shape mode (includes pulse as a shape option)
        String shape = params.getString("shape", null);
        if (shape != null && !shape.isEmpty() && !"none".equalsIgnoreCase(shape)) {
            if ("pulse".equalsIgnoreCase(shape)) {
                return executePulse(context);
            }
            return executeShape(context, shape);
        }

        // Check for Point A → Point B mode
        String fromStr = params.getString("from", null);
        String toStr = params.getString("to", null);
        if (fromStr != null || toStr != null) {
            return executeLineBetween(context, fromStr, toStr);
        }

        // Default: basic particle at location
        return executeBasic(context);
    }

    /**
     * Execute using a ShapeEngine preset.
     */
    private boolean executePreset(EffectContext context, String presetId) {
        ShapeEngine engine = getPlugin().getShapeEngine();
        if (engine == null) {
            debug("ShapeEngine not available");
            return executeBasic(context);
        }

        Location center = getTargetLocation(context);
        if (center == null) return false;

        Player owner = context.getPlayer();
        engine.playPreset(presetId, center, owner);
        return true;
    }

    /**
     * Execute pulse mode - expanding shockwave effect.
     * Creates multiple spheres that expand outward with decreasing particle density.
     *
     * Alex-friendly params (GUI names):
     * - waves: 3           - Number of expanding waves
     * - start_radius: 1    - Starting radius
     * - end_radius: 7      - Ending radius
     * - wave_delay: 10     - Ticks between waves (10 = 0.5 seconds)
     * - fade: true         - Each wave has fewer particles
     * - pulse_shape: sphere - Shape of the pulse (sphere or circle)
     */
    private boolean executePulse(EffectContext context) {
        EffectParams params = context.getParams();
        Location center = getTargetLocation(context);
        if (center == null) return false;

        // Check if animation is already running (prevents TICK spam)
        String animKey = getAnimationKey(context, "pulse");
        if (isAnimationActive(animKey)) {
            return true;
        }

        // Pulse params with Alex-friendly names (matching GUI)
        int waves = params.getInt("waves", 3);
        double startRadius = params.getDouble("start_radius", 1.0);
        double endRadius = params.getDouble("end_radius", 7.0);
        int waveDelay = params.getInt("wave_delay", 10); // ticks between waves
        double fadeIntensity = params.getDouble("fade", 1.0); // 0.0 = no fade, 1.0 = full fade

        // Shape to use for the pulse (sphere or circle for ground effect)
        String pulseShape = params.getString("pulse_shape", "sphere");
        double yOffset = params.getDouble("y_offset", 0.0);
        boolean followTarget = params.getBoolean("follow_target", false);
        double spacing = params.getDouble("spacing", 0.5); // Distance between particles

        // Particle params
        String particleName = params.getString("particle_type", "FLAME");
        double spread = params.getDouble("spread", 0.1);
        double speed = params.getDouble("speed", 0.0);

        Particle particle = getParticle(particleName);
        if (particle == null) particle = Particle.FLAME;
        Object particleData = buildParticleData(particle, particleName, params);

        // Calculate total duration and mark animation as active
        double totalDuration = (waves * waveDelay) / 20.0;
        markAnimationActive(animKey, totalDuration);

        // Calculate radius step between waves
        double radiusStep = (endRadius - startRadius) / Math.max(1, waves - 1);

        // Get target entity for follow mode
        org.bukkit.entity.LivingEntity targetEntity = getFollowTarget(context, params, followTarget);

        final Particle finalParticle = particle;
        final Object finalData = particleData;
        final org.bukkit.entity.LivingEntity finalTarget = targetEntity;
        final Location baseCenter = center.clone();
        final String finalPulseShape = pulseShape;

        // Schedule each wave
        for (int i = 0; i < waves; i++) {
            final int waveIndex = i;
            final double currentRadius = startRadius + (radiusStep * i);

            // Calculate spacing for this wave - fade increases spacing (fewer particles) outward
            final double currentSpacing;
            if (fadeIntensity > 0) {
                // Inner waves: base spacing, outer waves: up to 3x spacing based on fade
                double fadeMultiplier = 1.0 + (fadeIntensity * 2.0 * ((double) i / Math.max(1, waves - 1)));
                currentSpacing = spacing * fadeMultiplier;
            } else {
                currentSpacing = spacing;
            }

            // Schedule this wave
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    // Get current center (follow target if enabled)
                    Location waveCenter;
                    if (finalTarget != null && finalTarget.isValid()) {
                        waveCenter = finalTarget.getLocation().add(0, 1 + yOffset, 0);
                    } else {
                        waveCenter = baseCenter.clone().add(0, yOffset, 0);
                    }

                    // Generate shape points using spacing
                    Map<String, Object> shapeParams = new HashMap<>();
                    shapeParams.put("radius", currentRadius);
                    shapeParams.put("spacing", currentSpacing);
                    shapeParams.put("height", params.getDouble("height", 0.0));

                    ShapeDefinition shapeDef = new ShapeDefinition(finalPulseShape);
                    List<Location> points = shapeDef.generatePoints(waveCenter, shapeParams, 0);

                    // Spawn particles at each point
                    for (Location point : points) {
                        if (finalData != null) {
                            point.getWorld().spawnParticle(finalParticle, point, 1, spread, spread, spread, speed, finalData);
                        } else {
                            point.getWorld().spawnParticle(finalParticle, point, 1, spread, spread, spread, speed);
                        }
                    }
                }
            }.runTaskLater(getPlugin(), (long) i * waveDelay);
        }

        return true;
    }

    /**
     * Execute with a shape (circle, spiral, etc.).
     *
     * Modes:
     * - mode=display (default): Uses display entities spawned ONCE and teleported each tick (efficient, crisp)
     * - mode=particle: Traditional particle spawning (can be spammy, use tick_rate to reduce)
     */
    private boolean executeShape(EffectContext context, String shapeId) {
        EffectParams params = context.getParams();
        Location center = getTargetLocation(context);
        if (center == null) return false;

        // Check if animation is already running (prevents TICK spam)
        String animKey = getAnimationKey(context, shapeId);
        if (isAnimationActive(animKey)) {
            return true; // Animation already running, skip silently
        }

        // Mark animation as active for its duration
        double duration = params.getDouble("duration", 2.0);
        markAnimationActive(animKey, duration);

        // Mode selection - default to actual particles for PARTICLE effect
        String mode = params.getString("mode", "particle");

        if (mode.equalsIgnoreCase("display")) {
            return executeShapeDisplayMode(context, shapeId, center, params);
        } else {
            return executeShapeParticleMode(context, shapeId, center, params);
        }
    }

    /**
     * Display mode: Spawn display entities ONCE, teleport them each tick.
     * Efficient and crisp - no particle spam.
     */
    private boolean executeShapeDisplayMode(EffectContext context, String shapeId, Location center, EffectParams params) {
        // Animation params
        double duration = params.getDouble("duration", 2.0);
        double rotateSpeed = params.getDouble("rotate_speed", 1.0);
        boolean followTarget = params.getBoolean("follow_target", false);
        double yOffset = params.getDouble("y_offset", 0.0);
        int tickRate = params.getInt("tick_rate", 1);

        // Shape params
        double radius = params.getDouble("radius", 2.0);
        int points = params.getInt("points", 20);
        double shapeHeight = params.getDouble("height", 3.0); // For spiral/helix
        double turns = params.getDouble("turns", 2.0);
        double spacing = params.getDouble("spacing", 0.0); // 0 = use points, >0 = calculate from spacing

        // Display params
        String particleName = params.getString("particle_type", "FLAME");
        float scale = params.getFloat("scale", 0.3f); // Small by default for particle-like appearance
        boolean glow = params.getBoolean("glow", true);

        // Get target entity for follow mode
        org.bukkit.entity.LivingEntity targetEntity = getFollowTarget(context, params, followTarget);

        // Generate initial points
        Location initialCenter = center.clone().add(0, yOffset, 0);
        Map<String, Object> shapeParams = buildShapeParams(radius, points, shapeHeight, turns, spacing);
        ShapeDefinition shapeDef = new ShapeDefinition(shapeId);
        List<Location> initialPoints = shapeDef.generatePoints(initialCenter, shapeParams, 0);

        if (initialPoints.isEmpty()) return false;

        // Spawn display entities ONCE
        List<org.bukkit.entity.Display> displays = new ArrayList<>();
        org.bukkit.Material displayMaterial = getDisplayMaterial(particleName);

        for (Location point : initialPoints) {
            org.bukkit.entity.BlockDisplay display = point.getWorld().spawn(point, org.bukkit.entity.BlockDisplay.class, d -> {
                d.setBlock(displayMaterial.createBlockData());
                d.setGlowing(glow);
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.setPersistent(false);
                // Set small scale for particle-like appearance
                org.bukkit.util.Transformation transform = new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0, 0, 0),
                    new org.joml.AxisAngle4f(0, 0, 1, 0),
                    new org.joml.Vector3f(scale, scale, scale),
                    new org.joml.AxisAngle4f(0, 0, 1, 0)
                );
                d.setTransformation(transform);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            });
            displays.add(display);
        }

        // Store base offsets from center for rotation
        final List<double[]> baseOffsets = new ArrayList<>();
        for (Location point : initialPoints) {
            baseOffsets.add(new double[]{
                point.getX() - initialCenter.getX(),
                point.getY() - initialCenter.getY(),
                point.getZ() - initialCenter.getZ()
            });
        }

        // Animation task - teleport displays each tick
        int totalTicks = (int) (duration * 20);
        final org.bukkit.entity.LivingEntity finalTarget = targetEntity;
        final Location baseCenter = center.clone();

        new org.bukkit.scheduler.BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    // Remove all displays
                    for (org.bukkit.entity.Display d : displays) {
                        if (d.isValid()) d.remove();
                    }
                    this.cancel();
                    return;
                }

                // Only update on tick_rate intervals
                if (tick % tickRate == 0) {
                    double progress = (double) tick / totalTicks;
                    double rotation = progress * rotateSpeed * 2 * Math.PI;
                    double cos = Math.cos(rotation);
                    double sin = Math.sin(rotation);

                    // Get current center
                    Location currentCenter;
                    if (finalTarget != null && finalTarget.isValid()) {
                        currentCenter = finalTarget.getLocation().add(0, 1 + yOffset, 0);
                    } else {
                        currentCenter = baseCenter.clone().add(0, yOffset, 0);
                    }

                    // Teleport each display to its rotated position
                    for (int i = 0; i < displays.size(); i++) {
                        org.bukkit.entity.Display display = displays.get(i);
                        if (!display.isValid()) continue;

                        double[] offset = baseOffsets.get(i);
                        double newX = offset[0] * cos - offset[2] * sin;
                        double newZ = offset[0] * sin + offset[2] * cos;

                        Location newLoc = currentCenter.clone().add(newX, offset[1], newZ);
                        display.teleport(newLoc);
                    }
                }

                tick++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        return true;
    }

    /**
     * Particle mode: Traditional particle spawning each tick.
     * Use tick_rate to reduce spam.
     */
    private boolean executeShapeParticleMode(EffectContext context, String shapeId, Location center, EffectParams params) {
        // Animation params
        double duration = params.getDouble("duration", 2.0);
        double rotateSpeed = params.getDouble("rotate_speed", 1.0);
        boolean followTarget = params.getBoolean("follow_target", false);
        double yOffset = params.getDouble("y_offset", 0.0);
        int tickRate = params.getInt("tick_rate", 2); // Default to every 2 ticks to reduce spam

        // Shape params
        double radius = params.getDouble("radius", 2.0);
        int points = params.getInt("points", 20);
        double shapeHeight = params.getDouble("height", 3.0);
        double turns = params.getDouble("turns", 2.0);
        int segments = params.getInt("segments", 10);
        double jitter = params.getDouble("jitter", 0.0);
        double spacing = params.getDouble("spacing", 0.0); // 0 = use points, >0 = calculate from spacing

        // Particle params
        String particleName = params.getString("particle_type", "FLAME");
        int count = params.getInt("count", 1);
        double spread = params.getDouble("spread", 0.0); // No spread by default
        double speed = params.getDouble("speed", 0.0);

        Particle particle = getParticle(particleName);
        if (particle == null) particle = Particle.FLAME;
        Object particleData = buildParticleData(particle, particleName, params);

        // Get target entity
        org.bukkit.entity.LivingEntity targetEntity = getFollowTarget(context, params, followTarget);

        int totalTicks = (int) (duration * 20);
        final Particle finalParticle = particle;
        final Object finalData = particleData;
        final org.bukkit.entity.LivingEntity finalTarget = targetEntity;

        new org.bukkit.scheduler.BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    this.cancel();
                    return;
                }

                // Only spawn on tick_rate intervals
                if (tick % tickRate == 0) {
                    double progress = (double) tick / totalTicks;
                    double rotation = progress * rotateSpeed * 2 * Math.PI;

                    Location currentCenter;
                    if (finalTarget != null && finalTarget.isValid()) {
                        currentCenter = finalTarget.getLocation().add(0, 1 + yOffset, 0);
                    } else {
                        currentCenter = center.clone().add(0, yOffset, 0);
                    }

                    Map<String, Object> shapeParams = new HashMap<>();
                    shapeParams.put("radius", radius);
                    shapeParams.put("spacing", spacing);
                    shapeParams.put("height", shapeHeight);
                    shapeParams.put("turns", turns);
                    shapeParams.put("segments", segments);
                    shapeParams.put("jitter", jitter);

                    ShapeDefinition shapeDef = new ShapeDefinition(shapeId);
                    List<Location> shapePoints = shapeDef.generatePoints(currentCenter, shapeParams, progress);

                    // Apply rotation
                    if (rotation != 0) {
                        double cos = Math.cos(rotation);
                        double sin = Math.sin(rotation);
                        for (Location point : shapePoints) {
                            double dx = point.getX() - currentCenter.getX();
                            double dz = point.getZ() - currentCenter.getZ();
                            point.setX(currentCenter.getX() + dx * cos - dz * sin);
                            point.setZ(currentCenter.getZ() + dx * sin + dz * cos);
                        }
                    }

                    // Spawn particles
                    for (Location point : shapePoints) {
                        if (finalData != null) {
                            point.getWorld().spawnParticle(finalParticle, point, count, spread, spread, spread, speed, finalData);
                        } else {
                            point.getWorld().spawnParticle(finalParticle, point, count, spread, spread, spread, speed);
                        }
                    }
                }

                tick++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        return true;
    }

    /**
     * Get target entity for follow mode.
     */
    private org.bukkit.entity.LivingEntity getFollowTarget(EffectContext context, EffectParams params, boolean followTarget) {
        if (!followTarget) return null;

        String targetStr = params.getTarget();
        if (targetStr != null && targetStr.toUpperCase().contains("VICTIM") && context.getVictim() != null) {
            return context.getVictim();
        } else if (targetStr != null && targetStr.toUpperCase().contains("ATTACKER") && context.getAttacker() != null) {
            return context.getAttacker();
        }
        return context.getPlayer();
    }

    /**
     * Build shape params map.
     */
    private Map<String, Object> buildShapeParams(double radius, int points, double height, double turns, double spacing) {
        Map<String, Object> params = new HashMap<>();
        params.put("radius", radius);
        params.put("points", points);
        params.put("height", height);
        params.put("turns", turns);
        params.put("spacing", spacing); // ShapeDefinition will use this to calculate points if > 0
        return params;
    }

    /**
     * Get display material based on particle name (for visual approximation).
     */
    private org.bukkit.Material getDisplayMaterial(String particleName) {
        return switch (particleName.toUpperCase()) {
            case "FLAME", "SOUL_FIRE_FLAME" -> org.bukkit.Material.GLOWSTONE;
            case "SMOKE", "LARGE_SMOKE" -> org.bukkit.Material.COAL_BLOCK;
            case "CLOUD" -> org.bukkit.Material.WHITE_WOOL;
            case "HEART" -> org.bukkit.Material.REDSTONE_BLOCK;
            case "CRIT", "ENCHANTED_HIT" -> org.bukkit.Material.DIAMOND_BLOCK;
            case "WITCH", "PORTAL" -> org.bukkit.Material.PURPLE_WOOL;
            case "DRIPPING_WATER", "SNOWFLAKE" -> org.bukkit.Material.BLUE_ICE;
            case "DRIPPING_LAVA" -> org.bukkit.Material.MAGMA_BLOCK;
            case "ELECTRIC_SPARK" -> org.bukkit.Material.LIGHT_BLUE_WOOL;
            case "HAPPY_VILLAGER" -> org.bukkit.Material.EMERALD_BLOCK;
            default -> org.bukkit.Material.GLOWSTONE;
        };
    }

    /**
     * Execute line/beam between Point A and Point B.
     */
    private boolean executeLineBetween(EffectContext context, String fromStr, String toStr) {
        EffectParams params = context.getParams();

        // Resolve 'from' location
        Location from = resolveLocation(context, fromStr);
        if (from == null) {
            from = context.getPlayer().getLocation();
        }

        // Resolve 'to' location
        Location to = resolveLocation(context, toStr);
        if (to == null) {
            to = getTargetLocation(context);
        }

        if (from == null || to == null) {
            debug("Could not resolve from/to locations");
            return false;
        }

        int points = params.getInt("points", 20);
        double jitter = params.getDouble("jitter", 0.0);

        // Generate line points
        ShapeDefinition shapeDef = new ShapeDefinition("line");
        List<Location> linePoints;

        if (jitter > 0) {
            linePoints = shapeDef.generateBeamBetween(from, to, points, jitter);
        } else {
            linePoints = shapeDef.generateLineBetween(from, to, points);
        }

        return spawnAtPoints(context, linePoints);
    }

    /**
     * Resolve a location string to an actual Location.
     * Supports: @Self, @Victim, @Attacker, $variableName, or coordinates.
     */
    private Location resolveLocation(EffectContext context, String locStr) {
        if (locStr == null) return null;

        // Variable reference: $oldPos, $savedLoc, etc.
        if (locStr.startsWith("$")) {
            String varName = locStr.substring(1);
            Object stored = context.getVariable(varName);
            if (stored instanceof Location) {
                return (Location) stored;
            }
            // Try component variables: $oldX, $oldY, $oldZ
            if (varName.endsWith("X") || varName.endsWith("Y") || varName.endsWith("Z")) {
                String baseName = varName.substring(0, varName.length() - 1);
                Object x = context.getVariable(baseName + "X");
                Object y = context.getVariable(baseName + "Y");
                Object z = context.getVariable(baseName + "Z");
                if (x instanceof Number && y instanceof Number && z instanceof Number) {
                    return new Location(
                        context.getPlayer().getWorld(),
                        ((Number) x).doubleValue(),
                        ((Number) y).doubleValue(),
                        ((Number) z).doubleValue()
                    );
                }
            }
            return null;
        }

        // Target reference: @Self, @Victim, etc.
        if (locStr.startsWith("@")) {
            return resolveTargetLocation(context, locStr);
        }

        return null;
    }

    private Location resolveTargetLocation(EffectContext context, String targetStr) {
        switch (targetStr.toUpperCase()) {
            case "@SELF" -> { return context.getPlayer().getLocation().add(0, 1, 0); }
            case "@VICTIM" -> {
                if (context.getVictim() != null) {
                    return context.getVictim().getLocation().add(0, 1, 0);
                }
            }
            case "@ATTACKER" -> {
                if (context.getAttacker() != null) {
                    return context.getAttacker().getLocation().add(0, 1, 0);
                }
            }
        }

        // @Nearby:radius
        if (targetStr.toUpperCase().startsWith("@NEARBY")) {
            return context.getPlayer().getLocation().add(0, 1, 0);
        }

        return null;
    }

    /**
     * Basic particle spawn at a single location.
     */
    private boolean executeBasic(EffectContext context) {
        EffectParams params = context.getParams();
        String particleName = params.getString("particle_type", "FLAME");
        // Check both "count" param (from YAML key=value) and getValue() (from legacy string format)
        int count = params.getInt("count", (int) params.getValue());
        if (count <= 0) count = 10;

        Particle particle = getParticle(particleName);
        if (particle == null) {
            debug("Unknown particle type: " + particleName);
            return false;
        }

        Object particleData = buildParticleData(particle, particleName, params);
        String targetStr = params.getTarget();

        // Handle @Nearby
        if (targetStr != null && targetStr.toUpperCase().startsWith("@NEARBY")) {
            double radius = parseNearbyRadius(targetStr, 5);
            for (LivingEntity entity : getNearbyEntities(context, radius)) {
                spawnParticles(entity.getLocation().add(0, 1, 0), particle, count, particleData, params);
            }
            return true;
        }

        Location loc = getTargetLocation(context);
        if (loc != null) {
            spawnParticles(loc.add(0, 1, 0), particle, count, particleData, params);
            return true;
        }

        return false;
    }

    /**
     * Spawn particles at a list of points.
     */
    private boolean spawnAtPoints(EffectContext context, List<Location> points) {
        if (points.isEmpty()) return false;

        EffectParams params = context.getParams();
        String particleName = params.getString("particle_type", "FLAME");
        // Check both "count" param (from YAML key=value) and getValue() (from legacy string format)
        int totalCount = params.getInt("count", (int) params.getValue());
        if (totalCount <= 0) totalCount = 10;
        int countPerPoint = Math.max(1, totalCount / Math.max(1, points.size()));
        if (countPerPoint <= 0) countPerPoint = 1;

        Particle particle = getParticle(particleName);
        if (particle == null) {
            particle = Particle.FLAME;
        }

        Object particleData = buildParticleData(particle, particleName, params);

        for (Location point : points) {
            spawnParticles(point, particle, countPerPoint, particleData, params);
        }

        return true;
    }

    private Object buildParticleData(Particle particle, String particleName, EffectParams params) {
        // Check if particle name contains RGB (e.g., "DUST:255:215:0")
        String baseParticleName = particleName.contains(":") ? particleName.split(":")[0] : particleName;

        if (isDustParticle(baseParticleName)) {
            int red, green, blue;

            // Priority 1: RGB embedded in particle_type string (e.g., "DUST:255:215:0")
            if (particleName.contains(":")) {
                String[] parts = particleName.split(":");
                if (parts.length >= 4) {
                    try {
                        red = Integer.parseInt(parts[1]);
                        green = Integer.parseInt(parts[2]);
                        blue = Integer.parseInt(parts[3]);
                    } catch (NumberFormatException e) {
                        red = 255; green = 0; blue = 0;
                    }
                } else {
                    red = 255; green = 0; blue = 0;
                }
            }
            // Priority 2: Named color from GUI (e.g., color: GOLD)
            else {
                String colorName = params.getString("color", null);
                if (colorName != null && !colorName.isEmpty()) {
                    int[] rgb = getColorRGB(colorName);
                    red = rgb[0];
                    green = rgb[1];
                    blue = rgb[2];
                } else {
                    // Priority 3: Explicit RGB values
                    red = params.getInt("red", 255);
                    green = params.getInt("green", 0);
                    blue = params.getInt("blue", 0);
                }
            }

            float size = params.getFloat("size", 1.0f);

            Color color = Color.fromRGB(
                Math.max(0, Math.min(255, red)),
                Math.max(0, Math.min(255, green)),
                Math.max(0, Math.min(255, blue))
            );
            return new Particle.DustOptions(color, size);
        }

        if (isMaterialParticle(particleName)) {
            String materialName = params.getString("material", "STONE");
            Material material;
            try {
                material = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                material = Material.STONE;
            }

            if (particleName.equals("ITEM") || particleName.equals("ITEM_CRACK")) {
                return new ItemStack(material);
            } else {
                return material.createBlockData();
            }
        }

        return null;
    }

    private void spawnParticles(Location loc, Particle particle, int count, Object data, EffectParams params) {
        double spread = params.getDouble("spread", 0.3);
        double speed = params.getDouble("speed", 0.02);

        if (data != null) {
            loc.getWorld().spawnParticle(particle, loc, count, spread, spread, spread, speed, data);
        } else {
            loc.getWorld().spawnParticle(particle, loc, count, spread, spread, spread, speed);
        }
    }

    private Particle getParticle(String name) {
        // Handle format like "DUST:255:215:0" - extract just the particle name
        String particleName = name.contains(":") ? name.split(":")[0] : name;

        try {
            return Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return switch (particleName.toUpperCase()) {
                case "FIRE" -> Particle.FLAME;
                case "SOUL_FIRE" -> Particle.SOUL_FIRE_FLAME;
                case "MAGIC" -> Particle.ENCHANT;
                case "SPARKLE" -> Particle.END_ROD;
                case "REVERSE_PORTAL" -> Particle.PORTAL;
                case "HEART" -> Particle.HEART;
                case "ANGRY" -> Particle.ANGRY_VILLAGER;
                case "HAPPY" -> Particle.HAPPY_VILLAGER;
                case "SAND" -> Particle.FALLING_DUST;
                default -> null;
            };
        }
    }
}
