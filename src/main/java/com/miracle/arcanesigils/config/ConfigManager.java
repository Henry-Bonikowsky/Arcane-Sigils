package com.miracle.arcanesigils.config;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {

    private final ArmorSetsPlugin plugin;
    private FileConfiguration mainConfig;
    private final Map<String, FileConfiguration> sigilConfigs = new HashMap<>();
    private FileConfiguration messagesConfig;
    private FileConfiguration marksConfig;
    private final Map<String, MarkConfig> markConfigs = new HashMap<>();

    // Directories
    private File sigilsDir;
    private File behaviorsDir;
    private File marksDir;

    public ConfigManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        createDirectories();
        loadMainConfig();
        loadMessagesConfig();
        loadMarksConfig();
        loadSigilConfigs();
    }

    private void createDirectories() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        sigilsDir = new File(plugin.getDataFolder(), "sigils");
        if (!sigilsDir.exists()) {
            sigilsDir.mkdirs();
        }
        saveDefaultSigils();

        behaviorsDir = new File(plugin.getDataFolder(), "behaviors");
        if (!behaviorsDir.exists()) {
            behaviorsDir.mkdirs();
        }
        saveDefaultBehaviors();

        marksDir = new File(plugin.getDataFolder(), "marks");
        if (!marksDir.exists()) {
            marksDir.mkdirs();
        }
    }

    private void loadMainConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        mainConfig = plugin.getConfig();
    }

    private void loadMessagesConfig() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void loadMarksConfig() {
        File marksFile = new File(marksDir, "marks.yml");
        if (!marksFile.exists()) {
            saveResource("marks/marks.yml");
        }
        marksConfig = YamlConfiguration.loadConfiguration(marksFile);
        
        // Parse mark configs into MarkConfig objects
        markConfigs.clear();
        if (marksConfig.contains("marks")) {
            for (String markId : marksConfig.getConfigurationSection("marks").getKeys(false)) {
                String path = "marks." + markId;
                String name = marksConfig.getString(path + ".name", markId);
                String description = marksConfig.getString(path + ".description", "");
                double maxDuration = marksConfig.getDouble(path + ".max_duration", 3.0);
                double stackIncrement = marksConfig.getDouble(path + ".stack_increment", 1.0);
                boolean stackingEnabled = marksConfig.getBoolean(path + ".stacking_enabled", true);
                
                MarkConfig config = new MarkConfig(markId, name, description,
                    maxDuration, stackIncrement, stackingEnabled);
                markConfigs.put(markId, config);
            }
        }
        plugin.getLogger().info("Loaded " + markConfigs.size() + " mark configurations");
    }

    public void loadSigilConfigs() {
        sigilConfigs.clear();
        loadConfigsFromDirectory(sigilsDir, sigilConfigs);
        plugin.getLogger().info("Loaded " + sigilConfigs.size() + " sigil config files");
    }



    private void loadConfigsFromDirectory(File directory, Map<String, FileConfiguration> configMap) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return;

        for (File file : files) {
            try {
                // Force fresh read from disk using InputStreamReader to bypass any caching
                YamlConfiguration config = new YamlConfiguration();
                try (java.io.InputStreamReader reader = new java.io.InputStreamReader(
                        new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
                    config.load(reader);
                }
                String name = file.getName().replace(".yml", "").replace(".yaml", "");
                configMap.put(name, config);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load config: " + file.getName(), e);
            }
        }
    }

    private void saveDefaultSigils() {
        saveResource("sigils/default-sigils.yml");
        saveResource("sigils/pharaoh-set.yml");
        saveResource("sigils/seasonal-pass.yml");
        saveResource("sigils/mummy-kit.yml");
        saveResource("sigils/test-dummy.yml");
    }

    private void saveDefaultBehaviors() {
        saveResource("behaviors/mummy_behavior.yml");
        saveResource("behaviors/quicksand_behavior.yml");
        saveResource("behaviors/quicksand_pull_behavior.yml");
        saveResource("behaviors/wolf_companion.yml");
        saveResource("behaviors/royal_guard_behavior.yml");
    }

    private void saveResource(String resourcePath) {
        try {
            File outFile = new File(plugin.getDataFolder(), resourcePath);
            // Only save if file doesn't exist - don't overwrite user changes
            if (outFile.exists()) {
                return;
            }

            InputStream in = plugin.getResource(resourcePath);
            if (in != null) {
                outFile.getParentFile().mkdirs();

                FileConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                config.save(outFile);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save default resource: " + resourcePath, e);
        }
    }

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public FileConfiguration getMarksConfig() {
        return marksConfig;
    }

    public Map<String, MarkConfig> getMarkConfigs() {
        return markConfigs;
    }

    public MarkConfig getMarkConfig(String markId) {
        return markConfigs.get(markId.toUpperCase());
    }

    public Map<String, FileConfiguration> getSigilConfigs() {
        return sigilConfigs;
    }



    public String getMessage(String key) {
        return messagesConfig.getString("messages." + key, "§cMissing message: " + key);
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return message;
    }
}
