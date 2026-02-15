package com.miracle.arcanesigils;

import com.miracle.arcanesigils.addon.AddonManager;
import com.miracle.arcanesigils.debug.PluginDebugger;
import com.miracle.arcanesigils.binds.BindsBossBarManager;
import com.miracle.arcanesigils.binds.BindsListener;
import com.miracle.arcanesigils.binds.BindsManager;
import com.miracle.arcanesigils.binds.TargetGlowManager;
import com.miracle.arcanesigils.combat.LegacyCombatManager;
import com.miracle.arcanesigils.commands.ActivateBindCommand;
import com.miracle.arcanesigils.commands.ArmorSetsCommand;
import com.miracle.arcanesigils.commands.BindsCommand;
import com.miracle.arcanesigils.config.ConfigManager;
import com.miracle.arcanesigils.core.SigilManager;
import com.miracle.arcanesigils.core.SocketManager;
import com.miracle.arcanesigils.effects.AuraManager;
import com.miracle.arcanesigils.effects.BehaviorManager;
import com.miracle.arcanesigils.effects.CooldownNotifier;
import com.miracle.arcanesigils.effects.EffectManager;
import com.miracle.arcanesigils.effects.ProjectileManager;
import com.miracle.arcanesigils.effects.SkinChangeManager;
import com.miracle.arcanesigils.effects.StunManager;
import com.miracle.arcanesigils.events.CooldownManager;
import com.miracle.arcanesigils.events.SignalHandler;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.listeners.EnchantCapRemover;
import com.miracle.arcanesigils.particles.ShapeEngine;
import com.miracle.arcanesigils.tier.TierProgressionManager;
import com.miracle.arcanesigils.utils.ScreenShakeUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class ArmorSetsPlugin extends JavaPlugin {

    private static ArmorSetsPlugin instance;

    private ConfigManager configManager;
    private EffectManager effectManager;
    private StunManager stunManager;
    private SkinChangeManager skinChangeManager;
    private AuraManager auraManager;
    private BehaviorManager behaviorManager;
    private com.miracle.arcanesigils.effects.MarkManager markManager;
    private CooldownManager cooldownManager;
    private CooldownNotifier cooldownNotifier;
    private SigilManager sigilManager;
    private SocketManager socketManager;
    private SignalHandler signalHandler;
    private GUIManager guiManager;
    private TierProgressionManager tierProgressionManager;
    private AddonManager addonManager;
    private BindsListener bindsListener;
    private BindsManager bindsManager;
    private BindsBossBarManager bindsBossBarManager;
    private TargetGlowManager targetGlowManager;
    private ShapeEngine shapeEngine;
    private ProjectileManager projectileManager;
    private LegacyCombatManager legacyCombatManager;
    private PluginDebugger pluginDebugger;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Initializing ArcaneSigils plugin...");

        // Initialize managers in order
        initializeManagers();

        // Register commands
        registerCommands();

        // Register event listeners
        registerListeners();

        // Load addons after all core systems are ready
        loadAddons();

        getLogger().info("ArcaneSigils has been enabled!");
        getLogger().info("Loaded " + sigilManager.getSigilCount() + " sigils, " + sigilManager.getBehaviorCount() + " behaviors");
        if (addonManager.getAddonCount() > 0) {
            getLogger().info("Loaded " + addonManager.getAddonCount() + " addon(s)");
        }
    }

    @Override
    public void onDisable() {
        // Disable addons first (in reverse order)
        if (addonManager != null) {
            addonManager.disableAddons();
        }

        // Save player bind data
        if (bindsManager != null) {
            bindsManager.saveAll();
        }

        // Clean up
        if (bindsBossBarManager != null) {
            bindsBossBarManager.cleanupAll();
        }
        if (targetGlowManager != null) {
            targetGlowManager.cleanup();
        }
        if (stunManager != null) {
            stunManager.shutdown();
        }
        if (skinChangeManager != null) {
            skinChangeManager.shutdown();
        }
        if (auraManager != null) {
            auraManager.shutdown();
        }
        if (shapeEngine != null) {
            shapeEngine.stopAllAnimations();
        }
        if (behaviorManager != null) {
            behaviorManager.shutdown();
        }
        if (projectileManager != null) {
            projectileManager.shutdown();
        }
        if (cooldownManager != null) {
            cooldownManager.clearAll();
        }
        if (cooldownNotifier != null) {
            cooldownNotifier.shutdown();
        }
        if (guiManager != null) {
            guiManager.closeAll();
        }
        if (legacyCombatManager != null) {
            legacyCombatManager.disable();
        }

        // Clean up screen shake effects
        ScreenShakeUtil.cleanup();

        getLogger().info("ArcaneSigils has been disabled!");
        instance = null;
    }

    private void initializeManagers() {
        try {
            // Config must be first
            configManager = new ConfigManager(this);
            configManager.loadAll();

            // Effect manager (needed by others)
            effectManager = new EffectManager(this);

            // Shape engine (particle/visual effects system)
            shapeEngine = new ShapeEngine(this);
            shapeEngine.loadAll();

            // Stun manager (for true stun effects)
            stunManager = new StunManager(this);

            // Skin change manager (for temporary skin changes via ProtocolLib)
            skinChangeManager = new SkinChangeManager(this);

            // Aura manager (for area effect zones)
            auraManager = new AuraManager(this);

            // Behavior manager (for spawned entity/block behaviors)
            behaviorManager = new BehaviorManager(this);

            // Projectile manager (for moving display entities)
            projectileManager = new ProjectileManager(this);

            // Mark manager (for marking entities with string tags)
            markManager = new com.miracle.arcanesigils.effects.MarkManager(this);

            // Cooldown manager
            cooldownManager = new CooldownManager(this);

            // Cooldown ready notifier
            cooldownNotifier = new CooldownNotifier(this);

            // Sigil manager
            sigilManager = new SigilManager(this);
            sigilManager.loadSigils();
            sigilManager.loadBehaviors();

            // Socket manager
            socketManager = new SocketManager(this);

            // Tier progression manager (XP system)
            tierProgressionManager = new TierProgressionManager(this);

            // Signal handler
            signalHandler = new SignalHandler(this);

            // GUI manager
            guiManager = new GUIManager(this);

            // Binds manager (ability keybind system)
            bindsManager = new BindsManager(this);

            // Boss bar manager for ability UI
            bindsBossBarManager = new BindsBossBarManager(this);

            // Target glow manager (ProtocolLib-based client-side glow for targets)
            targetGlowManager = new TargetGlowManager(this);

            // Binds listener (must be created after bindsManager, bossBarManager, and targetGlowManager)
            bindsListener = new BindsListener(this, bindsManager, bindsBossBarManager, targetGlowManager);

            // Legacy Combat Manager (1.8 PvP system)
            legacyCombatManager = new LegacyCombatManager(this);

            // Plugin debugger (for identifying external plugin issues)
            pluginDebugger = new PluginDebugger(this);

            // Initialize optional hooks
            com.miracle.arcanesigils.hooks.FactionsHook.init();

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize managers!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommands() {
        // Main ArcaneSigils command
        var asCommand = getCommand("arcanesigils");
        if (asCommand != null) {
            ArmorSetsCommand armorSetsCommand = new ArmorSetsCommand(this);
            asCommand.setExecutor(armorSetsCommand);
            asCommand.setTabCompleter(armorSetsCommand);
        }

        // Binds command
        var bindsCommand = getCommand("binds");
        if (bindsCommand != null) {
            BindsCommand bindsCmd = new BindsCommand(this);
            bindsCommand.setExecutor(bindsCmd);
            bindsCommand.setTabCompleter(bindsCmd);
        }

        // ActivateBind command
        var activateBindCommand = getCommand("activatebind");
        if (activateBindCommand != null) {
            ActivateBindCommand activateCmd = new ActivateBindCommand(this);
            activateBindCommand.setExecutor(activateCmd);
            activateBindCommand.setTabCompleter(activateCmd);
        }
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(signalHandler, this);
        pm.registerEvents(guiManager, this);
        pm.registerEvents(socketManager, this);
        pm.registerEvents(bindsListener, this);
        pm.registerEvents(behaviorManager, this);
        pm.registerEvents(pluginDebugger, this);
        pm.registerEvents(skinChangeManager, this);
        pm.registerEvents(new EnchantCapRemover(), this);
    }

    private void loadAddons() {
        addonManager = new AddonManager(this);
        addonManager.loadAddons();
    }

    public void reload() {
        getLogger().info("Reloading ArcaneSigils...");

        int oldSigilCount = sigilManager.getSigilCount();
        int oldBehaviorCount = sigilManager.getBehaviorCount();

        // Clear caches
        cooldownManager.clearAll();

        // Reload configs from disk
        getLogger().info("Reloading config files from disk...");
        configManager.loadAll();

        // Reload sigil definitions
        getLogger().info("Reloading sigil definitions...");
        sigilManager.loadSigils();
        sigilManager.loadBehaviors();

        // Reload shape engine
        shapeEngine.loadAll();

        // Reload addons
        addonManager.reloadAddons();

        // Reload legacy combat system
        if (legacyCombatManager != null) {
            legacyCombatManager.reload();
        }

        int newSigilCount = sigilManager.getSigilCount();
        int newBehaviorCount = sigilManager.getBehaviorCount();

        getLogger().info("ArcaneSigils reloaded!");
        getLogger().info("Sigils: " + oldSigilCount + " -> " + newSigilCount + ", Behaviors: " + oldBehaviorCount + " -> " + newBehaviorCount);
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

    public StunManager getStunManager() {
        return stunManager;
    }

    public SkinChangeManager getSkinChangeManager() {
        return skinChangeManager;
    }

    public AuraManager getAuraManager() {
        return auraManager;
    }

    public BehaviorManager getBehaviorManager() {
        return behaviorManager;
    }

    public com.miracle.arcanesigils.effects.MarkManager getMarkManager() {
        return markManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public CooldownNotifier getCooldownNotifier() {
        return cooldownNotifier;
    }

    public SigilManager getSigilManager() {
        return sigilManager;
    }

    public SocketManager getSocketManager() {
        return socketManager;
    }

    public SignalHandler getSignalHandler() {
        return signalHandler;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public TierProgressionManager getTierProgressionManager() {
        return tierProgressionManager;
    }

    public AddonManager getAddonManager() {
        return addonManager;
    }

    public BindsManager getBindsManager() {
        return bindsManager;
    }

    public BindsBossBarManager getBindsBossBarManager() {
        return bindsBossBarManager;
    }

    public TargetGlowManager getTargetGlowManager() {
        return targetGlowManager;
    }

    public ShapeEngine getShapeEngine() {
        return shapeEngine;
    }

    public ProjectileManager getProjectileManager() {
        return projectileManager;
    }

    public LegacyCombatManager getLegacyCombatManager() {
        return legacyCombatManager;
    }

    public PluginDebugger getPluginDebugger() {
        return pluginDebugger;
    }
}
