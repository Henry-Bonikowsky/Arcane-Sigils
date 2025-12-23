package com.zenax.armorsets.config;

import com.zenax.armorsets.ArmorSetsPlugin;
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
    private final Map<String, FileConfiguration> weaponConfigs = new HashMap<>();
    private FileConfiguration messagesConfig;

    // Directories
    private File sigilsDir;
    private File weaponsDir;
    private File behaviorsDir;

    public ConfigManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        createDirectories();
        loadMainConfig();
        loadMessagesConfig();
        loadSigilConfigs();
        loadWeaponConfigs();
    }

    private void createDirectories() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        sigilsDir = new File(plugin.getDataFolder(), "sigils");
        weaponsDir = new File(plugin.getDataFolder(), "weapons");

        if (!sigilsDir.exists()) {
            sigilsDir.mkdirs();
        }
        saveDefaultSigils();

        if (!weaponsDir.exists()) {
            weaponsDir.mkdirs();
        }
        saveDefaultWeapons();

        behaviorsDir = new File(plugin.getDataFolder(), "behaviors");
        if (!behaviorsDir.exists()) {
            behaviorsDir.mkdirs();
        }
        saveDefaultBehaviors();
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

    public void loadSigilConfigs() {
        sigilConfigs.clear();
        loadConfigsFromDirectory(sigilsDir, sigilConfigs);
        plugin.getLogger().info("Loaded " + sigilConfigs.size() + " sigil config files");
    }

    private void loadWeaponConfigs() {
        weaponConfigs.clear();
        loadConfigsFromDirectory(weaponsDir, weaponConfigs);
        plugin.getLogger().info("Loaded " + weaponConfigs.size() + " weapon config files");
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
    }

    private void saveDefaultWeapons() {
        saveResource("weapons/example-weapon.yml");
        saveResource("weapons/signal-examples.yml");
    }

    private void saveDefaultBehaviors() {
        saveResource("behaviors/mummy_behavior.yml");
        saveResource("behaviors/quicksand_behavior.yml");
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

    public Map<String, FileConfiguration> getSigilConfigs() {
        return sigilConfigs;
    }

    public Map<String, FileConfiguration> getWeaponConfigs() {
        return weaponConfigs;
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
