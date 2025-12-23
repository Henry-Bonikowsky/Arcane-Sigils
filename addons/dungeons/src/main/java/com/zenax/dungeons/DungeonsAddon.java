package com.zenax.dungeons;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.addon.AbstractAddon;
import com.zenax.dungeons.combat.CombatHandler;
import com.zenax.dungeons.combat.MobManager;
import com.zenax.dungeons.combat.boss.BossManager;
import com.zenax.dungeons.commands.DungeonsCommand;
import com.zenax.dungeons.dungeon.DungeonCompletionHandler;
import com.zenax.dungeons.dungeon.DungeonEventHandler;
import com.zenax.dungeons.dungeon.DungeonManager;
import com.zenax.dungeons.generation.DungeonGenerator;
import com.zenax.dungeons.lobby.LobbyHandler;
import com.zenax.dungeons.lobby.LobbyManager;
import com.zenax.dungeons.loot.LootManager;
import com.zenax.dungeons.objective.ObjectiveManager;
import com.zenax.dungeons.party.PartyManager;
import com.zenax.dungeons.portal.PortalHandler;
import com.zenax.dungeons.portal.PortalManager;
import com.zenax.dungeons.stats.StatManager;
import com.zenax.dungeons.world.DungeonWorldManager;
import com.zenax.dungeons.sound.DungeonSoundEffects;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Main addon class for the Dungeons addon.
 * Provides procedural dungeon exploration functionality for Arcane Sigils.
 */
public class DungeonsAddon extends AbstractAddon {

    private static DungeonsAddon instance;

    // Configuration
    private FileConfiguration config;
    private FileConfiguration messages;

    // Managers
    private StatManager statManager;
    private DungeonManager dungeonManager;
    private PartyManager partyManager;
    private PortalManager portalManager;
    private LobbyManager lobbyManager;
    private MobManager mobManager;
    private BossManager bossManager;
    private LootManager lootManager;
    private ObjectiveManager objectiveManager;
    private DungeonGenerator dungeonGenerator;
    private DungeonWorldManager worldManager;

    // Handlers
    private PortalHandler portalHandler;
    private LobbyHandler lobbyHandler;
    private CombatHandler combatHandler;
    private DungeonCompletionHandler completionHandler;
    private DungeonEventHandler dungeonEventHandler;

    @Override
    public void onLoad(ArmorSetsPlugin plugin) {
        super.onLoad(plugin);
        instance = this;
    }

    @Override
    public void onEnable(ArmorSetsPlugin plugin) {
        super.onEnable(plugin);

        info("Initializing Dungeons addon...");

        // Initialize sound effects
        DungeonSoundEffects.init(plugin);

        // Save default configs
        saveDefaultConfigs();

        // Load configurations
        loadConfigs();

        // Initialize managers
        initializeManagers();

        // Initialize handlers
        initializeHandlers();

        // Register command
        registerCommand();

        info("Dungeons addon v" + getVersion() + " enabled!");
        info("Loaded " + dungeonManager.getDungeonCount() + " dungeon(s)");
        info("Loaded " + mobManager.getTemplateCount() + " mob template(s)");
        info("Loaded " + bossManager.getTemplateCount() + " boss template(s)");
        info("Loaded " + lootManager.getLootTableCount() + " loot table(s)");
    }

    @Override
    public void onDisable() {
        info("Disabling Dungeons addon...");

        // Clear completion handler data
        if (completionHandler != null) {
            completionHandler.clear();
        }

        // Clear event handler data
        if (dungeonEventHandler != null) {
            dungeonEventHandler.clear();
        }

        // Shutdown managers in reverse order
        if (dungeonManager != null) {
            dungeonManager.shutdown();
        }
        if (lobbyManager != null) {
            lobbyManager.shutdown();
        }
        if (portalManager != null) {
            portalManager.savePortals();
        }
        if (worldManager != null) {
            worldManager.shutdown();
        }

        // Unregister handlers
        if (portalHandler != null) {
            portalHandler.unregister();
        }
        if (lobbyHandler != null) {
            lobbyHandler.unregister();
        }

        super.onDisable();
        instance = null;
        info("Dungeons addon disabled!");
    }

    @Override
    public void onReload() {
        info("Reloading Dungeons addon...");

        // Reload configs
        loadConfigs();

        // Reload managers
        FileConfiguration dungeonConfig = loadConfig("dungeons/example_dungeon.yml");
        dungeonManager.loadDungeons(dungeonConfig);
        FileConfiguration mobConfig = loadConfig("mobs/mobs.yml");
        if (mobConfig.contains("mobs")) {
            mobManager.loadTemplates(mobConfig.getConfigurationSection("mobs"));
        }
        bossManager.loadBossTemplates(loadConfig("mobs/bosses.yml"));
        lootManager.loadLootTables();
        portalManager.loadPortals();

        info("Dungeons addon reloaded!");
    }

    private void saveDefaultConfigs() {
        // Save default config files from resources
        saveResource("config.yml", false);
        saveResource("messages.yml", false);
        saveResource("dungeons/example_dungeon.yml", false);
        saveResource("mobs/bosses.yml", false);

        // Create directories
        new File(getDataFolder(), "dungeons").mkdirs();
        new File(getDataFolder(), "mobs").mkdirs();
        new File(getDataFolder(), "loot").mkdirs();
        new File(getDataFolder(), "rooms").mkdirs();
    }

    private void loadConfigs() {
        config = loadConfig("config.yml");
        messages = loadConfig("messages.yml");
    }

    private FileConfiguration loadConfig(String path) {
        File file = new File(getDataFolder(), path);
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        return new YamlConfiguration();
    }

    private void initializeManagers() {
        // Stats manager (no dependencies)
        statManager = new StatManager();

        // Dungeon world manager - creates dedicated void world for dungeons
        worldManager = new DungeonWorldManager(plugin);
        if (!worldManager.initialize()) {
            warn("Failed to initialize dungeon world! Lobbies will use player's world.");
        }

        // Dungeon manager
        dungeonManager = new DungeonManager(plugin);
        FileConfiguration dungeonConfig = loadConfig("dungeons/example_dungeon.yml");
        dungeonManager.loadDungeons(dungeonConfig);

        // Party manager
        partyManager = new PartyManager();

        // Portal manager
        portalManager = new PortalManager(dungeonManager);
        portalManager.loadPortals();

        // Lobby manager (uses dungeon world for lobby generation)
        lobbyManager = new LobbyManager(plugin, dungeonManager, worldManager);
        lobbyManager.setPortalManager(portalManager);

        // Mob manager
        mobManager = new MobManager();
        FileConfiguration mobConfig = loadConfig("mobs/mobs.yml");
        if (mobConfig.contains("mobs")) {
            mobManager.loadTemplates(mobConfig.getConfigurationSection("mobs"));
        }

        // Boss manager
        bossManager = new BossManager();
        FileConfiguration bossConfig = loadConfig("mobs/bosses.yml");
        if (bossConfig.contains("bosses")) {
            bossManager.loadBossTemplates(bossConfig);
        }

        // Loot manager
        lootManager = new LootManager(this);
        lootManager.loadLootTables();

        // Objective manager
        objectiveManager = new ObjectiveManager();

        // Dungeon generator
        dungeonGenerator = new DungeonGenerator(plugin);
    }

    private void initializeHandlers() {
        // Portal handler
        portalHandler = new PortalHandler(portalManager, dungeonManager, lobbyManager);
        plugin.getServer().getPluginManager().registerEvents(portalHandler, plugin);

        // Lobby handler
        lobbyHandler = new LobbyHandler(lobbyManager, dungeonManager);
        plugin.getServer().getPluginManager().registerEvents(lobbyHandler, plugin);

        // Combat handler
        combatHandler = new CombatHandler(mobManager, statManager, dungeonManager);
        plugin.getServer().getPluginManager().registerEvents(combatHandler, plugin);

        // Completion handler (handles victory, failure, rewards, exit portal)
        completionHandler = new DungeonCompletionHandler(this, dungeonManager, lootManager);
        dungeonManager.setCompletionHandler(completionHandler);

        // Dungeon event handler (handles player death, respawn, exit portal interaction)
        dungeonEventHandler = new DungeonEventHandler(this, dungeonManager, completionHandler);
        plugin.getServer().getPluginManager().registerEvents(dungeonEventHandler, plugin);

        // Start dungeon update task (checks for completion/timeout)
        startDungeonUpdateTask();
    }

    /**
     * Starts the periodic task that updates dungeon instances.
     */
    private void startDungeonUpdateTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            dungeonManager.updateInstances();
        }, 20L, 20L); // Check every second
    }

    private void registerCommand() {
        DungeonsCommand command = new DungeonsCommand(
            this, dungeonManager, portalManager
        );

        // Register with Arcane Sigils' command system
        var pluginCommand = plugin.getCommand("dungeon");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        } else {
            warn("Could not register /dungeon command - make sure it's defined in plugin.yml");
        }
    }

    @Override
    public List<Listener> getListeners() {
        List<Listener> listeners = new ArrayList<>();
        if (portalHandler != null) listeners.add(portalHandler);
        if (lobbyHandler != null) listeners.add(lobbyHandler);
        if (combatHandler != null) listeners.add(combatHandler);
        if (dungeonEventHandler != null) listeners.add(dungeonEventHandler);
        return listeners;
    }

    // Getters for managers
    public static DungeonsAddon getInstance() {
        return instance;
    }

    public FileConfiguration getConfiguration() {
        return config;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public String getMessage(String key) {
        String prefix = messages.getString("prefix", "&8[&5Dungeons&8] ");
        String message = messages.getString(key, "&cMissing message: " + key);
        return prefix + message;
    }

    public String getRawMessage(String key) {
        return messages.getString(key, "&cMissing message: " + key);
    }

    public StatManager getStatManager() {
        return statManager;
    }

    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public PortalManager getPortalManager() {
        return portalManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public MobManager getMobManager() {
        return mobManager;
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public ObjectiveManager getObjectiveManager() {
        return objectiveManager;
    }

    public DungeonGenerator getDungeonGenerator() {
        return dungeonGenerator;
    }

    public DungeonCompletionHandler getCompletionHandler() {
        return completionHandler;
    }

    public DungeonWorldManager getWorldManager() {
        return worldManager;
    }

    @Override
    public String getId() {
        return "dungeons";
    }

    @Override
    public String getName() {
        return "Dungeons";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getAuthor() {
        return "Zenax";
    }

    @Override
    public String getDescription() {
        return "Procedural dungeon exploration addon for Arcane Sigils with stats, bosses, and loot";
    }
}
