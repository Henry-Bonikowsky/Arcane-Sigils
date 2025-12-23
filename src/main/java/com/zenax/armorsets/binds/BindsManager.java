package com.zenax.armorsets.binds;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.utils.LogHelper;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for the binds system.
 * Handles player data storage, persistence, and lifecycle management.
 */
public class BindsManager {
    private final ArmorSetsPlugin plugin;
    private final File playerDataFolder;
    private final Map<UUID, PlayerBindData> playerData;

    public BindsManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        this.playerData = new ConcurrentHashMap<>();

        // Ensure playerdata folder exists
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }

        // Load data for online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayerData(player.getUniqueId());
        }
    }

    /**
     * Get or create bind data for a player.
     */
    public PlayerBindData getPlayerData(UUID playerId) {
        return playerData.computeIfAbsent(playerId, id -> {
            PlayerBindData data = loadPlayerDataFromFile(id);
            return data != null ? data : new PlayerBindData(id);
        });
    }

    /**
     * Get or create bind data for a player.
     */
    public PlayerBindData getPlayerData(Player player) {
        return getPlayerData(player.getUniqueId());
    }

    /**
     * Load player data when they join.
     */
    public void loadPlayerData(UUID playerId) {
        PlayerBindData data = loadPlayerDataFromFile(playerId);
        if (data != null) {
            playerData.put(playerId, data);
        }
    }

    /**
     * Save and unload player data when they quit.
     */
    public void unloadPlayerData(UUID playerId) {
        PlayerBindData data = playerData.remove(playerId);
        if (data != null) {
            savePlayerData(data);
        }
    }

    /**
     * Save player data to file.
     */
    public void savePlayerData(PlayerBindData data) {
        if (data == null) return;

        File file = getPlayerFile(data.getPlayerId());

        if (!data.hasAnyData()) {
            // Delete file if no data worth saving
            if (file.exists()) {
                file.delete();
                LogHelper.debug("Deleted empty player data file for " + data.getPlayerId());
            }
            return;
        }

        try {
            YamlConfiguration config = new YamlConfiguration();
            data.saveToConfig(config);
            config.save(file);
            LogHelper.debug("Saved player data for " + data.getPlayerId());
        } catch (IOException e) {
            LogHelper.severe("Failed to save player data for " + data.getPlayerId() + ": " + e.getMessage());
        }
    }

    /**
     * Save player data by UUID.
     */
    public void savePlayerData(UUID playerId) {
        PlayerBindData data = playerData.get(playerId);
        if (data != null) {
            savePlayerData(data);
        }
    }

    /**
     * Load player data from file.
     */
    private PlayerBindData loadPlayerDataFromFile(UUID playerId) {
        File file = getPlayerFile(playerId);
        if (!file.exists()) {
            return null;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            PlayerBindData data = PlayerBindData.loadFromConfig(playerId, config);
            LogHelper.debug("Loaded player data for " + playerId);
            return data;
        } catch (Exception e) {
            LogHelper.severe("Failed to load player data for " + playerId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get the file for a player's data.
     */
    private File getPlayerFile(UUID playerId) {
        return new File(playerDataFolder, playerId.toString() + ".yml");
    }

    /**
     * Save all player data (called on plugin disable).
     */
    public void saveAll() {
        for (PlayerBindData data : playerData.values()) {
            savePlayerData(data);
        }
        LogHelper.info("Saved all player bind data");
    }

    /**
     * Check if a player has the ability toggled on.
     */
    public boolean isToggled(UUID playerId) {
        PlayerBindData data = playerData.get(playerId);
        return data != null && data.isToggled();
    }

    /**
     * Toggle the ability system for a player.
     */
    public boolean toggle(UUID playerId) {
        PlayerBindData data = getPlayerData(playerId);
        data.toggle();
        return data.isToggled();
    }

    /**
     * Clear all binds for a player's current system.
     */
    public void clearAllBinds(UUID playerId) {
        PlayerBindData data = getPlayerData(playerId);
        data.getCurrentBinds().clearAll();
        if (data.getActiveSystem() == BindSystem.COMMAND) {
            data.setHighestCommandBindId(0);
        }
    }

    /**
     * Switch the active system for a player.
     */
    public void switchSystem(UUID playerId) {
        PlayerBindData data = getPlayerData(playerId);
        data.setActiveSystem(data.getActiveSystem().toggle());
    }

    /**
     * Get the plugin instance.
     */
    public ArmorSetsPlugin getPlugin() {
        return plugin;
    }
}
