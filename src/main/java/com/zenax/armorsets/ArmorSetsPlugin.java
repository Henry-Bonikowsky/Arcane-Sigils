package com.zenax.armorsets;

import com.zenax.armorsets.commands.ArmorSetsCommand;
import com.zenax.armorsets.config.ConfigManager;
import com.zenax.armorsets.core.SigilManager;
import com.zenax.armorsets.core.SocketManager;
import com.zenax.armorsets.effects.EffectManager;
import com.zenax.armorsets.events.CooldownManager;
import com.zenax.armorsets.events.TriggerHandler;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.sets.SetManager;
import com.zenax.armorsets.weapons.WeaponManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class ArmorSetsPlugin extends JavaPlugin {

    private static ArmorSetsPlugin instance;

    private ConfigManager configManager;
    private EffectManager effectManager;
    private CooldownManager cooldownManager;
    private SigilManager sigilManager;
    private SocketManager socketManager;
    private SetManager setManager;
    private WeaponManager weaponManager;
    private TriggerHandler triggerHandler;
    private GUIManager guiManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Initializing ArmorSets plugin...");

        // Initialize managers in order
        initializeManagers();

        // Register commands
        registerCommands();

        // Register event listeners
        registerListeners();

        getLogger().info("ArmorSets has been enabled!");
        getLogger().info("Loaded " + setManager.getSetCount() + " armor sets");
        getLogger().info("Loaded " + sigilManager.getSigilCount() + " sigils");
        getLogger().info("Loaded " + weaponManager.getWeaponCount() + " weapons");
    }

    @Override
    public void onDisable() {
        // Clean up
        if (cooldownManager != null) {
            cooldownManager.clearAll();
        }
        if (guiManager != null) {
            guiManager.closeAll();
        }

        getLogger().info("ArmorSets has been disabled!");
        instance = null;
    }

    private void initializeManagers() {
        try {
            // Config must be first
            configManager = new ConfigManager(this);
            configManager.loadAll();

            // Effect manager (needed by others)
            effectManager = new EffectManager(this);

            // Cooldown manager
            cooldownManager = new CooldownManager(this);

            // Sigil manager
            sigilManager = new SigilManager(this);
            sigilManager.loadSigils();

            // Socket manager
            socketManager = new SocketManager(this);

            // Set manager
            setManager = new SetManager(this);
            setManager.loadSets();

            // Weapon manager
            weaponManager = new WeaponManager(this);
            weaponManager.loadWeapons();

            // Trigger handler
            triggerHandler = new TriggerHandler(this);

            // GUI manager
            guiManager = new GUIManager(this);

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize managers!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommands() {
        var command = getCommand("armorsets");
        if (command != null) {
            ArmorSetsCommand armorSetsCommand = new ArmorSetsCommand(this);
            command.setExecutor(armorSetsCommand);
            command.setTabCompleter(armorSetsCommand);
        }
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(triggerHandler, this);
        pm.registerEvents(guiManager, this);
        pm.registerEvents(socketManager, this);
    }

    public void reload() {
        getLogger().info("Reloading ArmorSets...");

        // Clear caches
        cooldownManager.clearAll();

        // Reload configs
        configManager.loadAll();

        // Reload data
        sigilManager.loadSigils();
        setManager.loadSets();
        weaponManager.loadWeapons();

        getLogger().info("ArmorSets reloaded!");
        getLogger().info("Loaded " + setManager.getSetCount() + " armor sets");
        getLogger().info("Loaded " + sigilManager.getSigilCount() + " sigils");
        getLogger().info("Loaded " + weaponManager.getWeaponCount() + " weapons");
    }

    public static ArmorSetsPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EffectManager getEffectManager() {
        return effectManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public SigilManager getSigilManager() {
        return sigilManager;
    }

    public SocketManager getSocketManager() {
        return socketManager;
    }

    public SetManager getSetManager() {
        return setManager;
    }

    public WeaponManager getWeaponManager() {
        return weaponManager;
    }

    public TriggerHandler getTriggerHandler() {
        return triggerHandler;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }
}
