package com.miracle.arcanesigils.particles;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * Core engine for the shape/particle system.
 * Loads shapes, modifiers, and presets from YAML.
 * Provides methods to generate and render visual effects.
 */
public class ShapeEngine {

    private final ArmorSetsPlugin plugin;

    private final Map<String, ShapeDefinition> shapes = new HashMap<>();
    private final Map<String, ModifierDefinition> modifiers = new HashMap<>();
    private final Map<String, PresetDefinition> presets = new HashMap<>();

    // Track active animations for cleanup
    private final Map<UUID, ActiveAnimation> activeAnimations = new HashMap<>();

    public ShapeEngine(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load all shapes, modifiers, and presets.
     */
    public void loadAll() {
        shapes.clear();
        modifiers.clear();
        presets.clear();

        // Create directories and save defaults
        createDirectories();

        // Load from plugin data folder
        loadShapesFromDirectory(new File(plugin.getDataFolder(), "particles/shapes"));
        loadModifiersFromDirectory(new File(plugin.getDataFolder(), "particles/modifiers"));
        loadPresetsFromDirectory(new File(plugin.getDataFolder(), "particles/presets"));

        // Register built-in shapes that don't need YAML
        registerBuiltinShapes();

        plugin.getLogger().info(String.format(
            "[ShapeEngine] Loaded %d shapes, %d modifiers, %d presets",
            shapes.size(), modifiers.size(), presets.size()
        ));
    }

    /**
     * Register built-in shapes that are always available.
     */
    private void registerBuiltinShapes() {
        String[] builtinShapes = {"circle", "spiral", "helix", "sphere", "line", "beam", "cone", "point", "ring"};
        for (String shapeId : builtinShapes) {
            if (!shapes.containsKey(shapeId)) {
                shapes.put(shapeId, new ShapeDefinition(shapeId));
            }
        }
    }

    /**
     * Create directories and save default configs.
     */
    private void createDirectories() {
        File particlesDir = new File(plugin.getDataFolder(), "particles");
        File shapesDir = new File(particlesDir, "shapes");
        File modifiersDir = new File(particlesDir, "modifiers");
        File presetsDir = new File(particlesDir, "presets");

        shapesDir.mkdirs();
        modifiersDir.mkdirs();
        presetsDir.mkdirs();

        // Save defaults
        saveDefaultResource("particles/shapes/circle.yml");
        saveDefaultResource("particles/shapes/spiral.yml");
        saveDefaultResource("particles/modifiers/rotate.yml");
        saveDefaultResource("particles/modifiers/rise.yml");
        saveDefaultResource("particles/presets/fire_spiral.yml");
        saveDefaultResource("particles/presets/expanding_ring.yml");
    }

    private void saveDefaultResource(String resourcePath) {
        File outFile = new File(plugin.getDataFolder(), resourcePath);
        if (outFile.exists()) return;

        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                outFile.getParentFile().mkdirs();
                FileConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8)
                );
                config.save(outFile);
            }
        } catch (IOException e) {
            // Resource doesn't exist in JAR, that's OK
        }
    }

    // ============ Loading ============

    private void loadShapesFromDirectory(File directory) {
        if (!directory.exists()) return;

        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                for (String key : config.getKeys(false)) {
                    ConfigurationSection section = config.getConfigurationSection(key);
                    if (section != null) {
                        ShapeDefinition shape = ShapeDefinition.fromConfig(key, section);
                        shapes.put(key.toLowerCase(), shape);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load shape file: " + file.getName(), e);
            }
        }
    }

    private void loadModifiersFromDirectory(File directory) {
        if (!directory.exists()) return;

        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                for (String key : config.getKeys(false)) {
                    ConfigurationSection section = config.getConfigurationSection(key);
                    if (section != null) {
                        ModifierDefinition modifier = ModifierDefinition.fromConfig(key, section);
                        modifiers.put(key.toLowerCase(), modifier);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load modifier file: " + file.getName(), e);
            }
        }
    }

    private void loadPresetsFromDirectory(File directory) {
        if (!directory.exists()) return;

        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                for (String key : config.getKeys(false)) {
                    ConfigurationSection section = config.getConfigurationSection(key);
                    if (section != null) {
                        PresetDefinition preset = PresetDefinition.fromConfig(key, section);
                        presets.put(key.toLowerCase(), preset);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load preset file: " + file.getName(), e);
            }
        }
    }

    // ============ Generation ============

    /**
     * Generate points for a shape.
     */
    public List<Location> generateShape(String shapeId, Map<String, Object> params, Location center, double progress) {
        ShapeDefinition shape = shapes.get(shapeId.toLowerCase());
        if (shape == null) {
            // Try as built-in
            shape = new ShapeDefinition(shapeId);
        }
        return shape.generatePoints(center, params, progress);
    }

    /**
     * Apply modifiers to a list of points.
     */
    public void applyModifiers(List<Location> points, List<ModifierDefinition.ModifierInstance> mods,
                                Location center, double progress) {
        for (ModifierDefinition.ModifierInstance mod : mods) {
            mod.apply(points, center, progress);
        }
    }

    /**
     * Create modifier instances from preset config.
     */
    public List<ModifierDefinition.ModifierInstance> createModifierInstances(List<PresetDefinition.ModifierConfig> configs) {
        List<ModifierDefinition.ModifierInstance> instances = new ArrayList<>();
        for (PresetDefinition.ModifierConfig config : configs) {
            ModifierDefinition def = modifiers.get(config.type.toLowerCase());
            if (def == null) {
                // Create built-in modifier
                def = new ModifierDefinition(config.type);
            }
            instances.add(new ModifierDefinition.ModifierInstance(def, config.params));
        }
        return instances;
    }

    // ============ Playback ============

    /**
     * Play a preset at a location.
     *
     * @param presetId The preset to play
     * @param center   Center location
     * @param owner    Owner entity (for attachment)
     * @return Animation ID for tracking
     */
    public UUID playPreset(String presetId, Location center, LivingEntity owner) {
        PresetDefinition preset = presets.get(presetId.toLowerCase());
        if (preset == null) {
            plugin.getLogger().warning("[ShapeEngine] Preset not found: " + presetId);
            return null;
        }

        UUID animationId = UUID.randomUUID();
        ActiveAnimation animation = new ActiveAnimation(animationId, preset, center, owner);
        activeAnimations.put(animationId, animation);

        // Start animation
        animation.start();

        return animationId;
    }

    /**
     * Stop an animation.
     */
    public void stopAnimation(UUID animationId) {
        ActiveAnimation animation = activeAnimations.remove(animationId);
        if (animation != null) {
            animation.stop();
        }
    }

    /**
     * Stop all animations.
     */
    public void stopAllAnimations() {
        for (ActiveAnimation animation : new ArrayList<>(activeAnimations.values())) {
            animation.stop();
        }
        activeAnimations.clear();
    }

    // ============ Getters ============

    public ShapeDefinition getShape(String id) {
        return shapes.get(id.toLowerCase());
    }

    public ModifierDefinition getModifier(String id) {
        return modifiers.get(id.toLowerCase());
    }

    public PresetDefinition getPreset(String id) {
        return presets.get(id.toLowerCase());
    }

    public Collection<ShapeDefinition> getAllShapes() {
        return shapes.values();
    }

    public Collection<ModifierDefinition> getAllModifiers() {
        return modifiers.values();
    }

    public Collection<PresetDefinition> getAllPresets() {
        return presets.values();
    }

    // ============ Active Animation ============

    /**
     * Tracks and runs an active animation.
     */
    private class ActiveAnimation {
        private final UUID id;
        private final PresetDefinition preset;
        private final Location baseCenter;
        private final LivingEntity owner;
        private BukkitRunnable task;
        private final List<Entity> spawnedEntities = new ArrayList<>();
        private boolean stopped = false;
        private double lastSoundLoopTime = -1;

        public ActiveAnimation(UUID id, PresetDefinition preset, Location baseCenter, LivingEntity owner) {
            this.id = id;
            this.preset = preset;
            this.baseCenter = baseCenter.clone();
            this.owner = owner;
        }

        public void start() {
            double totalDuration = preset.getTotalDuration();
            int totalTicks = (int) (totalDuration * 20);

            // Play start sounds
            playSounds(PresetDefinition.SoundConfig.SoundTrigger.START, 0);

            task = new BukkitRunnable() {
                int frame = 0;

                @Override
                public void run() {
                    if (stopped || frame >= totalTicks) {
                        finish();
                        return;
                    }

                    double elapsed = frame / 20.0;
                    double progress = elapsed / totalDuration;

                    // Get current center (may follow entity)
                    Location center = getCurrentCenter();
                    if (center == null) {
                        finish();
                        return;
                    }

                    // Determine what to render
                    String shapeId;
                    Map<String, Object> shapeParams;
                    List<PresetDefinition.ModifierConfig> modConfigs;
                    List<LayerDefinition> layers;

                    if (preset.hasStages()) {
                        PresetDefinition.StageDefinition stage = preset.getStageAt(elapsed);
                        if (stage == null) {
                            finish();
                            return;
                        }
                        shapeId = stage.shape != null ? stage.shape : preset.getShape();
                        shapeParams = new HashMap<>(preset.getShapeParams());
                        shapeParams.putAll(stage.shapeParams);
                        modConfigs = stage.modifiers.isEmpty() ? preset.getModifiers() : stage.modifiers;
                        layers = stage.layers.isEmpty() ? preset.getLayers() : stage.layers;
                        progress = preset.getStageProgress(elapsed);
                    } else {
                        shapeId = preset.getShape();
                        shapeParams = preset.getShapeParams();
                        modConfigs = preset.getModifiers();
                        layers = preset.getLayers();
                    }

                    // Generate points
                    List<Location> points = generateShape(shapeId, shapeParams, center, progress);

                    // Apply modifiers
                    List<ModifierDefinition.ModifierInstance> modInstances = createModifierInstances(modConfigs);
                    applyModifiers(points, modInstances, center, progress);

                    // Render layers
                    for (LayerDefinition layer : layers) {
                        List<Entity> spawned = layer.render(center.getWorld(), points, progress, totalDuration);
                        spawnedEntities.addAll(spawned);

                        // Schedule removal for display entities (they persist)
                        if (!spawned.isEmpty()) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    for (Entity e : spawned) {
                                        if (e.isValid()) e.remove();
                                    }
                                }
                            }.runTaskLater(plugin, 2L); // Remove after 2 ticks
                        }
                    }

                    // Loop sounds
                    playSounds(PresetDefinition.SoundConfig.SoundTrigger.LOOP, elapsed);

                    frame++;
                }
            };
            task.runTaskTimer(plugin, 0L, 1L);
        }

        private Location getCurrentCenter() {
            if (preset.getAttachTo() != null && owner != null && owner.isValid()) {
                Location loc = owner.getLocation().clone();
                loc.add(0, preset.getOffsetY(), 0);
                return loc;
            }
            return baseCenter.clone();
        }

        private void playSounds(PresetDefinition.SoundConfig.SoundTrigger trigger, double elapsed) {
            Location loc = getCurrentCenter();
            if (loc == null) return;

            for (PresetDefinition.SoundConfig sound : preset.getSounds()) {
                if (sound.trigger != trigger) continue;

                if (trigger == PresetDefinition.SoundConfig.SoundTrigger.LOOP) {
                    // Check interval
                    if (lastSoundLoopTime < 0 || elapsed - lastSoundLoopTime >= sound.interval) {
                        playSound(sound, loc);
                        lastSoundLoopTime = elapsed;
                    }
                } else {
                    playSound(sound, loc);
                }
            }
        }

        private void playSound(PresetDefinition.SoundConfig sound, Location loc) {
            try {
                org.bukkit.Sound soundType = org.bukkit.Sound.valueOf(sound.sound.toUpperCase());
                loc.getWorld().playSound(loc, soundType, (float) sound.volume, (float) sound.pitch);
            } catch (IllegalArgumentException ignored) {}
        }

        public void stop() {
            stopped = true;
            if (task != null) {
                task.cancel();
            }
            cleanup();
        }

        private void finish() {
            // Play end sounds
            playSounds(PresetDefinition.SoundConfig.SoundTrigger.END, preset.getTotalDuration());

            activeAnimations.remove(id);
            cleanup();
            cancel();
        }

        private void cleanup() {
            for (Entity e : spawnedEntities) {
                if (e != null && e.isValid()) {
                    e.remove();
                }
            }
            spawnedEntities.clear();
        }

        private void cancel() {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
    }
}
