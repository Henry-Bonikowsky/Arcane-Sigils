package com.zenax.armorsets.particles;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Defines a complete preset - a ready-to-use visual effect.
 * Presets combine:
 * - A shape (circle, spiral, beam, etc.)
 * - Modifiers (rotate, rise, expand, etc.)
 * - Layers (what to render at each point)
 * - Optional stages (sequenced animations)
 */
public class PresetDefinition {

    private String id;
    private String name;
    private List<String> description = new ArrayList<>();
    private Material icon = Material.NETHER_STAR;

    // Shape configuration
    private String shape = "circle";
    private Map<String, Object> shapeParams = new HashMap<>();

    // Modifiers
    private List<ModifierConfig> modifiers = new ArrayList<>();

    // Layers (what to render)
    private List<LayerDefinition> layers = new ArrayList<>();

    // Stages (for sequenced effects)
    private List<StageDefinition> stages = new ArrayList<>();

    // Timing
    private double duration = 1.0;  // seconds

    // Target configuration
    private String attachTo = null;  // Entity to follow (@Self, @Victim)
    private double offsetY = 0.0;

    // Sound configuration
    private List<SoundConfig> sounds = new ArrayList<>();

    public PresetDefinition(String id) {
        this.id = id;
        this.name = id;
    }

    /**
     * Check if this preset uses stages (sequenced animation).
     */
    public boolean hasStages() {
        return !stages.isEmpty();
    }

    /**
     * Get the stage at a given time.
     *
     * @param elapsed Elapsed time in seconds
     * @return The active stage, or null if past all stages
     */
    public StageDefinition getStageAt(double elapsed) {
        if (stages.isEmpty()) return null;

        double cumulative = 0;
        for (StageDefinition stage : stages) {
            cumulative += stage.duration;
            if (elapsed < cumulative) {
                return stage;
            }
        }
        return null;
    }

    /**
     * Get progress within the current stage.
     *
     * @param elapsed Elapsed time in seconds
     * @return Progress 0.0 to 1.0 within the stage
     */
    public double getStageProgress(double elapsed) {
        if (stages.isEmpty()) return elapsed / duration;

        double cumulative = 0;
        for (StageDefinition stage : stages) {
            if (elapsed < cumulative + stage.duration) {
                return (elapsed - cumulative) / stage.duration;
            }
            cumulative += stage.duration;
        }
        return 1.0;
    }

    /**
     * Get total duration including all stages.
     */
    public double getTotalDuration() {
        if (stages.isEmpty()) return duration;
        return stages.stream().mapToDouble(s -> s.duration).sum();
    }

    // ============ YAML Parsing ============

    @SuppressWarnings("unchecked")
    public static PresetDefinition fromConfig(String id, ConfigurationSection section) {
        PresetDefinition preset = new PresetDefinition(id);

        preset.name = section.getString("name", id);
        preset.description = section.getStringList("description");

        // Icon
        String iconStr = section.getString("icon", "NETHER_STAR");
        try {
            preset.icon = Material.valueOf(iconStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            preset.icon = Material.NETHER_STAR;
        }

        // Shape
        preset.shape = section.getString("shape", "circle");

        // Shape params
        ConfigurationSection shapeParamsSection = section.getConfigurationSection("shape_params");
        if (shapeParamsSection != null) {
            for (String key : shapeParamsSection.getKeys(false)) {
                preset.shapeParams.put(key, shapeParamsSection.get(key));
            }
        }

        // Modifiers
        List<?> modifiersList = section.getList("modifiers");
        if (modifiersList != null) {
            for (Object modObj : modifiersList) {
                if (modObj instanceof Map<?, ?> modMap) {
                    ModifierConfig config = ModifierConfig.fromMap((Map<String, Object>) modMap);
                    preset.modifiers.add(config);
                }
            }
        }

        // Layers
        List<?> layersList = section.getList("layers");
        if (layersList != null) {
            for (Object layerObj : layersList) {
                if (layerObj instanceof Map<?, ?> layerMap) {
                    LayerDefinition layer = LayerDefinition.fromMap((Map<String, Object>) layerMap);
                    preset.layers.add(layer);
                }
            }
        }

        // Stages
        List<?> stagesList = section.getList("stages");
        if (stagesList != null) {
            for (Object stageObj : stagesList) {
                if (stageObj instanceof Map<?, ?> stageMap) {
                    StageDefinition stage = StageDefinition.fromMap((Map<String, Object>) stageMap);
                    preset.stages.add(stage);
                }
            }
        }

        // Duration
        preset.duration = section.getDouble("duration", 1.0);

        // Attachment
        preset.attachTo = section.getString("attach_to", null);
        preset.offsetY = section.getDouble("offset_y", 0.0);

        // Sounds
        List<?> soundsList = section.getList("sounds");
        if (soundsList != null) {
            for (Object soundObj : soundsList) {
                if (soundObj instanceof Map<?, ?> soundMap) {
                    SoundConfig sound = SoundConfig.fromMap((Map<String, Object>) soundMap);
                    preset.sounds.add(sound);
                }
            }
        }

        return preset;
    }

    // ============ Getters ============

    public String getId() { return id; }
    public String getName() { return name; }
    public List<String> getDescription() { return description; }
    public Material getIcon() { return icon; }
    public String getShape() { return shape; }
    public Map<String, Object> getShapeParams() { return shapeParams; }
    public List<ModifierConfig> getModifiers() { return modifiers; }
    public List<LayerDefinition> getLayers() { return layers; }
    public List<StageDefinition> getStages() { return stages; }
    public double getDuration() { return duration; }
    public String getAttachTo() { return attachTo; }
    public double getOffsetY() { return offsetY; }
    public List<SoundConfig> getSounds() { return sounds; }

    // ============ Inner Classes ============

    /**
     * Configuration for a modifier instance.
     */
    public static class ModifierConfig {
        public String type;
        public Map<String, Object> params = new HashMap<>();

        @SuppressWarnings("unchecked")
        public static ModifierConfig fromMap(Map<String, Object> map) {
            ModifierConfig config = new ModifierConfig();
            config.type = (String) map.getOrDefault("type", "rotate");

            // All other keys are params
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!entry.getKey().equals("type")) {
                    config.params.put(entry.getKey(), entry.getValue());
                }
            }
            return config;
        }
    }

    /**
     * A stage in a sequenced animation.
     */
    public static class StageDefinition {
        public double duration = 1.0;
        public String shape;
        public Map<String, Object> shapeParams = new HashMap<>();
        public List<ModifierConfig> modifiers = new ArrayList<>();
        public List<LayerDefinition> layers = new ArrayList<>();

        @SuppressWarnings("unchecked")
        public static StageDefinition fromMap(Map<String, Object> map) {
            StageDefinition stage = new StageDefinition();

            Object durationObj = map.get("duration");
            if (durationObj instanceof Number) {
                stage.duration = ((Number) durationObj).doubleValue();
            }

            stage.shape = (String) map.get("shape");

            Object shapeParamsObj = map.get("shape_params");
            if (shapeParamsObj instanceof Map<?, ?>) {
                stage.shapeParams = (Map<String, Object>) shapeParamsObj;
            }

            Object modifiersObj = map.get("modifiers");
            if (modifiersObj instanceof List<?> modList) {
                for (Object modObj : modList) {
                    if (modObj instanceof Map<?, ?> modMap) {
                        stage.modifiers.add(ModifierConfig.fromMap((Map<String, Object>) modMap));
                    }
                }
            }

            Object layersObj = map.get("layers");
            if (layersObj instanceof List<?> layerList) {
                for (Object layerObj : layerList) {
                    if (layerObj instanceof Map<?, ?> layerMap) {
                        stage.layers.add(LayerDefinition.fromMap((Map<String, Object>) layerMap));
                    }
                }
            }

            return stage;
        }
    }

    /**
     * Configuration for a sound trigger.
     */
    public static class SoundConfig {
        public String sound;
        public double volume = 1.0;
        public double pitch = 1.0;
        public SoundTrigger trigger = SoundTrigger.START;
        public double interval = 0.5;  // For LOOP trigger

        @SuppressWarnings("unchecked")
        public static SoundConfig fromMap(Map<String, Object> map) {
            SoundConfig config = new SoundConfig();
            config.sound = (String) map.getOrDefault("sound", "");

            Object volumeObj = map.get("volume");
            if (volumeObj instanceof Number) config.volume = ((Number) volumeObj).doubleValue();

            Object pitchObj = map.get("pitch");
            if (pitchObj instanceof Number) config.pitch = ((Number) pitchObj).doubleValue();

            String atStr = (String) map.getOrDefault("at", "start");
            try {
                config.trigger = SoundTrigger.valueOf(atStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                config.trigger = SoundTrigger.START;
            }

            Object intervalObj = map.get("interval");
            if (intervalObj instanceof Number) config.interval = ((Number) intervalObj).doubleValue();

            return config;
        }

        public enum SoundTrigger {
            START,
            END,
            LOOP
        }
    }
}
