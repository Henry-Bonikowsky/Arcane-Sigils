package com.zenax.dungeons.dungeon;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all dungeon templates and active instances.
 */
public class DungeonManager {
    private final Plugin plugin;
    private final Map<String, Dungeon> dungeonTemplates;
    private final Map<UUID, DungeonInstance> activeInstances;
    private final Map<UUID, UUID> playerToInstance;

    // Completion handler (set after construction)
    private DungeonCompletionHandler completionHandler;

    /**
     * Creates a new dungeon manager.
     *
     * @param plugin The plugin instance
     */
    public DungeonManager(Plugin plugin) {
        this.plugin = plugin;
        this.dungeonTemplates = new ConcurrentHashMap<>();
        this.activeInstances = new ConcurrentHashMap<>();
        this.playerToInstance = new ConcurrentHashMap<>();
    }

    /**
     * Loads all dungeon templates from the configuration.
     *
     * @param config The configuration to load from
     * @return The number of dungeons loaded
     */
    public int loadDungeons(FileConfiguration config) {
        dungeonTemplates.clear();

        ConfigurationSection dungeonsSection = config.getConfigurationSection("dungeons");
        if (dungeonsSection == null) {
            plugin.getLogger().warning("No dungeons section found in configuration");
            return 0;
        }

        int loaded = 0;
        for (String dungeonId : dungeonsSection.getKeys(false)) {
            ConfigurationSection dungeonSection = dungeonsSection.getConfigurationSection(dungeonId);
            if (dungeonSection == null) {
                continue;
            }

            Dungeon dungeon = Dungeon.fromConfig(dungeonSection);
            if (dungeon != null) {
                dungeonTemplates.put(dungeonId, dungeon);
                loaded++;
                plugin.getLogger().info("Loaded dungeon: " + dungeonId);
            } else {
                plugin.getLogger().warning("Failed to load dungeon: " + dungeonId);
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " dungeon(s)");
        return loaded;
    }

    /**
     * Reloads all dungeon templates from the configuration.
     *
     * @param config The configuration to reload from
     * @return The number of dungeons loaded
     */
    public int reloadDungeons(FileConfiguration config) {
        return loadDungeons(config);
    }

    /**
     * Creates a new dungeon instance.
     *
     * @param dungeonId The ID of the dungeon template
     * @param difficulty The difficulty level
     * @param objectiveMode The objective mode
     * @param world The world to create the instance in
     * @param spawnPoint The spawn point for players
     * @return The created DungeonInstance, or null if creation failed
     */
    public DungeonInstance createInstance(String dungeonId, DungeonDifficulty difficulty,
                                         ObjectiveMode objectiveMode, World world, Location spawnPoint) {
        Dungeon template = dungeonTemplates.get(dungeonId);
        if (template == null) {
            plugin.getLogger().warning("Cannot create instance: Dungeon not found: " + dungeonId);
            return null;
        }

        if (!template.hasObjectiveMode(objectiveMode)) {
            plugin.getLogger().warning("Cannot create instance: Objective mode " + objectiveMode +
                                      " not available for dungeon: " + dungeonId);
            return null;
        }

        DungeonInstance instance = new DungeonInstance(template, difficulty, objectiveMode, world, spawnPoint);
        activeInstances.put(instance.getInstanceId(), instance);

        plugin.getLogger().info("Created dungeon instance: " + instance.getInstanceId() +
                               " (" + dungeonId + ", " + difficulty + ", " + objectiveMode + ")");
        return instance;
    }

    /**
     * Creates a new dungeon instance with default settings.
     *
     * @param dungeonId The ID of the dungeon template
     * @param world The world to create the instance in
     * @param spawnPoint The spawn point for players
     * @return The created DungeonInstance, or null if creation failed
     */
    public DungeonInstance createInstance(String dungeonId, World world, Location spawnPoint) {
        Dungeon template = dungeonTemplates.get(dungeonId);
        if (template == null) {
            return null;
        }

        DungeonDifficulty difficulty = template.getDefaultDifficulty();
        ObjectiveMode mode = template.getAvailableObjectiveModes().isEmpty()
            ? ObjectiveMode.BOSS_KILL
            : template.getAvailableObjectiveModes().get(0);

        return createInstance(dungeonId, difficulty, mode, world, spawnPoint);
    }

    /**
     * Destroys a dungeon instance.
     *
     * @param instanceId The UUID of the instance to destroy
     * @return true if the instance was destroyed successfully
     */
    public boolean destroyInstance(UUID instanceId) {
        DungeonInstance instance = activeInstances.remove(instanceId);
        if (instance == null) {
            return false;
        }

        // Remove all players from the instance mapping
        for (UUID playerUuid : instance.getPlayerUuids()) {
            playerToInstance.remove(playerUuid);
        }

        plugin.getLogger().info("Destroyed dungeon instance: " + instanceId);
        return true;
    }

    /**
     * Destroys a dungeon instance.
     *
     * @param instance The instance to destroy
     * @return true if the instance was destroyed successfully
     */
    public boolean destroyInstance(DungeonInstance instance) {
        return destroyInstance(instance.getInstanceId());
    }

    /**
     * Destroys all active dungeon instances.
     */
    public void destroyAllInstances() {
        for (UUID instanceId : new java.util.HashSet<>(activeInstances.keySet())) {
            destroyInstance(instanceId);
        }
        playerToInstance.clear();
        plugin.getLogger().info("Destroyed all dungeon instances");
    }

    /**
     * Gets a dungeon template by its ID.
     *
     * @param dungeonId The dungeon ID
     * @return The Dungeon template, or null if not found
     */
    public Dungeon getDungeon(String dungeonId) {
        return dungeonTemplates.get(dungeonId);
    }

    /**
     * Gets all registered dungeon templates.
     *
     * @return A map of dungeon IDs to Dungeon templates
     */
    public Map<String, Dungeon> getAllDungeons() {
        return new HashMap<>(dungeonTemplates);
    }

    /**
     * Gets a dungeon instance by its UUID.
     *
     * @param instanceId The instance UUID
     * @return The DungeonInstance, or null if not found
     */
    public DungeonInstance getInstance(UUID instanceId) {
        return activeInstances.get(instanceId);
    }

    /**
     * Gets all active dungeon instances.
     *
     * @return A map of instance UUIDs to DungeonInstances
     */
    public Map<UUID, DungeonInstance> getAllInstances() {
        return new HashMap<>(activeInstances);
    }

    /**
     * Gets the dungeon instance a player is currently in.
     *
     * @param player The player
     * @return The DungeonInstance, or null if the player is not in a dungeon
     */
    public DungeonInstance getPlayerInstance(Player player) {
        UUID instanceId = playerToInstance.get(player.getUniqueId());
        return instanceId != null ? activeInstances.get(instanceId) : null;
    }

    /**
     * Gets the dungeon instance a player UUID is currently in.
     *
     * @param playerUuid The player UUID
     * @return The DungeonInstance, or null if the player is not in a dungeon
     */
    public DungeonInstance getPlayerInstance(UUID playerUuid) {
        UUID instanceId = playerToInstance.get(playerUuid);
        return instanceId != null ? activeInstances.get(instanceId) : null;
    }

    /**
     * Adds a player to a dungeon instance.
     *
     * @param player The player to add
     * @param instance The instance to add them to
     * @return true if the player was added successfully
     */
    public boolean addPlayerToInstance(Player player, DungeonInstance instance) {
        if (getPlayerInstance(player) != null) {
            plugin.getLogger().warning("Player " + player.getName() + " is already in a dungeon");
            return false;
        }

        if (instance.addPlayer(player)) {
            playerToInstance.put(player.getUniqueId(), instance.getInstanceId());
            plugin.getLogger().info("Added player " + player.getName() + " to instance " + instance.getInstanceId());
            return true;
        }

        return false;
    }

    /**
     * Removes a player from their current dungeon instance.
     *
     * @param player The player to remove
     * @return true if the player was removed successfully
     */
    public boolean removePlayerFromInstance(Player player) {
        DungeonInstance instance = getPlayerInstance(player);
        if (instance == null) {
            return false;
        }

        playerToInstance.remove(player.getUniqueId());
        instance.removePlayer(player);

        plugin.getLogger().info("Removed player " + player.getName() + " from instance " + instance.getInstanceId());

        // Destroy instance if empty
        if (instance.isEmpty()) {
            destroyInstance(instance);
        }

        return true;
    }

    /**
     * Checks if a player is in a dungeon.
     *
     * @param player The player to check
     * @return true if the player is in a dungeon
     */
    public boolean isPlayerInDungeon(Player player) {
        return playerToInstance.containsKey(player.getUniqueId());
    }

    /**
     * Checks if a player UUID is in a dungeon.
     *
     * @param playerUuid The player UUID to check
     * @return true if the player is in a dungeon
     */
    public boolean isPlayerInDungeon(UUID playerUuid) {
        return playerToInstance.containsKey(playerUuid);
    }

    /**
     * Gets the number of active instances.
     *
     * @return The number of active instances
     */
    public int getActiveInstanceCount() {
        return activeInstances.size();
    }

    /**
     * Gets the number of registered dungeon templates.
     *
     * @return The number of dungeon templates
     */
    public int getDungeonCount() {
        return dungeonTemplates.size();
    }

    /**
     * Gets all dungeon IDs.
     *
     * @return A set of all dungeon IDs
     */
    public Set<String> getDungeonIds() {
        return new HashSet<>(dungeonTemplates.keySet());
    }

    /**
     * Checks if a dungeon template exists.
     *
     * @param dungeonId The dungeon ID
     * @return true if the dungeon exists
     */
    public boolean dungeonExists(String dungeonId) {
        return dungeonTemplates.containsKey(dungeonId);
    }

    /**
     * Cleans up finished dungeon instances.
     * Should be called periodically to remove completed/failed dungeons.
     *
     * @return The number of instances cleaned up
     */
    public int cleanupFinishedInstances() {
        int cleaned = 0;
        Iterator<Map.Entry<UUID, DungeonInstance>> iterator = activeInstances.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, DungeonInstance> entry = iterator.next();
            DungeonInstance instance = entry.getValue();

            if (instance.getState().isTerminal() || instance.isEmpty() || instance.isTimeExpired()) {
                // Remove player mappings
                for (UUID playerUuid : instance.getPlayerUuids()) {
                    playerToInstance.remove(playerUuid);
                }

                iterator.remove();
                cleaned++;
                plugin.getLogger().info("Cleaned up finished instance: " + entry.getKey());
            }
        }

        return cleaned;
    }

    /**
     * Sets the completion handler for this manager.
     *
     * @param handler The completion handler
     */
    public void setCompletionHandler(DungeonCompletionHandler handler) {
        this.completionHandler = handler;
    }

    /**
     * Gets the completion handler.
     *
     * @return The completion handler
     */
    public DungeonCompletionHandler getCompletionHandler() {
        return completionHandler;
    }

    /**
     * Updates all active dungeon instances.
     * Should be called periodically to check for completion, time limits, etc.
     */
    public void updateInstances() {
        for (DungeonInstance instance : activeInstances.values()) {
            if (instance.getState() != DungeonState.ACTIVE) {
                continue;
            }

            // Check time limit
            if (instance.isTimeExpired()) {
                instance.setState(DungeonState.FAILED);
                plugin.getLogger().info("Instance " + instance.getInstanceId() + " failed due to time limit");

                // Trigger failure handling
                if (completionHandler != null) {
                    completionHandler.handleFailure(instance, DungeonCompletionHandler.FailureReason.TIME_EXPIRED);
                }
                continue;
            }

            // Check completion
            if (instance.isComplete()) {
                instance.setState(DungeonState.COMPLETED);
                plugin.getLogger().info("Instance " + instance.getInstanceId() + " completed");

                // Trigger completion handling
                if (completionHandler != null) {
                    completionHandler.handleCompletion(instance);
                }
            }
        }
    }

    /**
     * Shuts down the dungeon manager, destroying all active instances.
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down DungeonManager...");

        for (UUID instanceId : new HashSet<>(activeInstances.keySet())) {
            destroyInstance(instanceId);
        }

        dungeonTemplates.clear();
        playerToInstance.clear();

        plugin.getLogger().info("DungeonManager shutdown complete");
    }
}
