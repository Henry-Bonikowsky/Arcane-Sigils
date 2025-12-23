package com.zenax.armorsets.events;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a saved condition preset that can be loaded and applied to signals.
 * Presets allow players to save and reuse custom condition groups.
 */
public class ConditionPreset {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private String id;
    private String name;
    private String description;
    private List<String> conditions;
    private LocalDateTime created;
    private String creator;

    public ConditionPreset(String id, String name, String description, List<String> conditions, String creator) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.conditions = new ArrayList<>(conditions);
        this.created = LocalDateTime.now();
        this.creator = creator;
    }

    public ConditionPreset(String id, String name, String description, List<String> conditions, LocalDateTime created, String creator) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.conditions = new ArrayList<>(conditions);
        this.created = created;
        this.creator = creator;
    }

    // ===== GETTERS =====

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getConditions() {
        return new ArrayList<>(conditions);
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public String getCreator() {
        return creator;
    }

    // ===== SETTERS =====

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setConditions(List<String> conditions) {
        this.conditions = new ArrayList<>(conditions);
    }

    // ===== PERSISTENCE METHODS =====

    /**
     * Save this preset to a YAML configuration section.
     *
     * @param section The configuration section to save to
     */
    public void saveToConfig(ConfigurationSection section) {
        section.set("name", name);
        section.set("description", description);
        section.set("conditions", conditions);
        section.set("created", created.format(TIMESTAMP_FORMAT));
        section.set("creator", creator);
    }

    /**
     * Load a preset from a YAML configuration section.
     *
     * @param id      The preset ID
     * @param section The configuration section to load from
     * @return The loaded preset, or null if invalid
     */
    public static ConditionPreset loadFromConfig(String id, ConfigurationSection section) {
        if (section == null) return null;

        try {
            String name = section.getString("name", id);
            String description = section.getString("description", "");
            List<String> conditions = section.getStringList("conditions");
            String createdStr = section.getString("created");
            String creator = section.getString("creator", "Unknown");

            LocalDateTime created = createdStr != null ?
                LocalDateTime.parse(createdStr, TIMESTAMP_FORMAT) :
                LocalDateTime.now();

            return new ConditionPreset(id, name, description, conditions, created, creator);
        } catch (Exception e) {
            System.err.println("[ArmorSets] Failed to load condition preset: " + id);
            e.printStackTrace();
            return null;
        }
    }

    // ===== STATIC PERSISTENCE METHODS =====

    /**
     * Load all presets from the presets file.
     *
     * @param presetsFile The presets YAML file
     * @return Map of preset ID to ConditionPreset
     */
    public static Map<String, ConditionPreset> loadAllPresets(File presetsFile) {
        Map<String, ConditionPreset> presets = new HashMap<>();

        if (!presetsFile.exists()) {
            return presets;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(presetsFile);
            ConfigurationSection presetsSection = config.getConfigurationSection("presets");

            if (presetsSection != null) {
                for (String presetId : presetsSection.getKeys(false)) {
                    ConfigurationSection presetSection = presetsSection.getConfigurationSection(presetId);
                    ConditionPreset preset = loadFromConfig(presetId, presetSection);

                    if (preset != null) {
                        presets.put(presetId, preset);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ArmorSets] Failed to load condition presets");
            e.printStackTrace();
        }

        return presets;
    }

    /**
     * Save a preset to the presets file.
     *
     * @param presetsFile The presets YAML file
     * @param preset      The preset to save
     */
    public static void savePreset(File presetsFile, ConditionPreset preset) {
        try {
            // Ensure parent directory exists
            presetsFile.getParentFile().mkdirs();

            // Load or create config
            YamlConfiguration config = presetsFile.exists() ?
                YamlConfiguration.loadConfiguration(presetsFile) :
                new YamlConfiguration();

            // Save preset
            ConfigurationSection presetsSection = config.getConfigurationSection("presets");
            if (presetsSection == null) {
                presetsSection = config.createSection("presets");
            }

            ConfigurationSection presetSection = presetsSection.getConfigurationSection(preset.getId());
            if (presetSection == null) {
                presetSection = presetsSection.createSection(preset.getId());
            }

            preset.saveToConfig(presetSection);

            // Write to file
            config.save(presetsFile);
        } catch (IOException e) {
            System.err.println("[ArmorSets] Failed to save condition preset: " + preset.getId());
            e.printStackTrace();
        }
    }

    /**
     * Delete a preset from the presets file.
     *
     * @param presetsFile The presets YAML file
     * @param presetId    The ID of the preset to delete
     * @return True if deleted, false otherwise
     */
    public static boolean deletePreset(File presetsFile, String presetId) {
        if (!presetsFile.exists()) return false;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(presetsFile);
            ConfigurationSection presetsSection = config.getConfigurationSection("presets");

            if (presetsSection != null && presetsSection.contains(presetId)) {
                presetsSection.set(presetId, null);
                config.save(presetsFile);
                return true;
            }
        } catch (IOException e) {
            System.err.println("[ArmorSets] Failed to delete condition preset: " + presetId);
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Generate a unique preset ID from a name.
     *
     * @param name          The preset name
     * @param existingIds   Set of existing preset IDs
     * @return A unique ID
     */
    public static String generateId(String name, java.util.Set<String> existingIds) {
        String baseId = name.toLowerCase()
            .replaceAll("[^a-z0-9_]", "_")
            .replaceAll("_+", "_")
            .trim();

        if (baseId.isEmpty()) {
            baseId = "preset";
        }

        String id = baseId;
        int counter = 1;

        while (existingIds.contains(id)) {
            id = baseId + "_" + counter;
            counter++;
        }

        return id;
    }
}
