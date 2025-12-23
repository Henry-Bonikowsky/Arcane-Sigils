package com.zenax.armorsets.addon;

import com.zenax.armorsets.ArmorSetsPlugin;

import java.io.File;
import java.util.logging.Logger;

/**
 * Abstract base class for Arcane Sigils addons.
 * Provides common functionality and sensible defaults.
 */
public abstract class AbstractAddon implements ArcaneSigilsAddon {

    protected ArmorSetsPlugin plugin;
    protected File dataFolder;
    protected Logger logger;
    protected boolean enabled = false;

    @Override
    public void onLoad(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.enabled = true;
    }

    @Override
    public void onDisable() {
        this.enabled = false;
    }

    /**
     * Check if this addon is currently enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the main Arcane Sigils plugin instance.
     *
     * @return The plugin instance
     */
    public ArmorSetsPlugin getPlugin() {
        return plugin;
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public void setDataFolder(File folder) {
        this.dataFolder = folder;
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Log an info message.
     *
     * @param message The message to log
     */
    public void info(String message) {
        if (logger != null) {
            logger.info(message);
        }
    }

    /**
     * Log a warning message.
     *
     * @param message The message to log
     */
    public void warn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    /**
     * Log a severe message.
     *
     * @param message The message to log
     */
    public void severe(String message) {
        if (logger != null) {
            logger.severe(message);
        }
    }

    /**
     * Save a resource from the addon JAR to the data folder.
     *
     * @param resourcePath The path to the resource in the JAR
     * @param replace      Whether to replace existing files
     */
    protected void saveResource(String resourcePath, boolean replace) {
        if (dataFolder == null) return;

        File outFile = new File(dataFolder, resourcePath);
        if (outFile.exists() && !replace) return;

        try (var in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                warn("Resource not found: " + resourcePath);
                return;
            }

            outFile.getParentFile().mkdirs();
            java.nio.file.Files.copy(in, outFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            warn("Failed to save resource: " + resourcePath);
        }
    }
}
