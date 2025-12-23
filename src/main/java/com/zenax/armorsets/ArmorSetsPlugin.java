package com.zenax.armorsets;

import com.zenax.armorsets.addon.AddonManager;
import com.zenax.armorsets.binds.BindsBossBarManager;
import com.zenax.armorsets.binds.BindsListener;
import com.zenax.armorsets.binds.BindsManager;
import com.zenax.armorsets.binds.TargetGlowManager;
import com.zenax.armorsets.commands.ActivateBindCommand;
import com.zenax.armorsets.commands.ArmorSetsCommand;
import com.zenax.armorsets.commands.BindsCommand;
import com.zenax.armorsets.config.ConfigManager;
import com.zenax.armorsets.core.SigilManager;
import com.zenax.armorsets.core.SocketManager;
import com.zenax.armorsets.effects.AuraManager;
import com.zenax.armorsets.effects.BehaviorManager;
import com.zenax.armorsets.effects.CooldownNotifier;
import com.zenax.armorsets.effects.EffectManager;
import com.zenax.armorsets.effects.ProjectileManager;
import com.zenax.armorsets.effects.StunManager;
import com.zenax.armorsets.events.CooldownManager;
import com.zenax.armorsets.events.SignalHandler;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.notifications.NotificationBossBarManager;
import com.zenax.armorsets.notifications.ResourcePackNotifier;
import com.zenax.armorsets.particles.ShapeEngine;
import com.zenax.armorsets.tier.TierProgressionManager;
import com.zenax.armorsets.utils.ScreenShakeUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class ArmorSetsPlugin extends JavaPlugin {

    private static ArmorSetsPlugin instance;

    private ConfigManager configManager;
    private EffectManager effectManager;
    private StunManager stunManager;
    private AuraManager auraManager;
    private BehaviorManager behaviorManager;
    private com.zenax.armorsets.effects.MarkManager markManager;
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
    private NotificationBossBarManager notificationBossBarManager;
    private ResourcePackNotifier resourcePackNotifier;
    private TargetGlowManager targetGlowManager;
    private ShapeEngine shapeEngine;
    private ProjectileManager projectileManager;
    private ResourcePackHandler resourcePackHandler;

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
        if (resourcePackNotifier != null) {
            resourcePackNotifier.shutdown();
        }
        if (notificationBossBarManager != null) {
            notificationBossBarManager.shutdown();
        }
        if (guiManager != null) {
            guiManager.closeAll();
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

            // Aura manager (for area effect zones)
            auraManager = new AuraManager(this);

            // Behavior manager (for spawned entity/block behaviors)
            behaviorManager = new BehaviorManager(this);

            // Projectile manager (for moving display entities)
            projectileManager = new ProjectileManager(this);

            // Mark manager (for marking entities with string tags)
            markManager = new com.zenax.armorsets.effects.MarkManager(this);

            // Cooldown manager
            cooldownManager = new CooldownManager(this);

            // Cooldown ready notifier
            cooldownNotifier = new CooldownNotifier(this);

            // Notification boss bar manager (cooldown/duration progress bars) - fallback
            notificationBossBarManager = new NotificationBossBarManager(this);

            // Resource pack notifier (primary notification system)
            resourcePackNotifier = new ResourcePackNotifier(this);

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

            // Resource pack handler (prompts players to download pack on join)
            resourcePackHandler = new ResourcePackHandler(this);

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
        pm.registerEvents(resourcePackHandler, this);
    }

    private void loadAddons() {
        addonManager = new AddonManager(this);
        addonManager.loadAddons();
    }

    public void reload() {
        getLogger().info("Reloading ArcaneSigils...");

        // Clear caches
        cooldownManager.clearAll();

        // Reload configs
        configManager.loadAll();

        // Reload data
        sigilManager.loadSigils();
        sigilManager.loadBehaviors();

        // Reload shape engine
        shapeEngine.loadAll();

        // Reload notification managers
        if (resourcePackNotifier != null) {
            resourcePackNotifier.reload();
        }
        if (notificationBossBarManager != null) {
            notificationBossBarManager.reload();
        }

        // Reload addons
        addonManager.reloadAddons();

        getLogger().info("ArcaneSigils reloaded!");
        getLogger().info("Loaded " + sigilManager.getSigilCount() + " sigils, " + sigilManager.getBehaviorCount() + " behaviors");
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

    public AuraManager getAuraManager() {
        return auraManager;
    }

    public BehaviorManager getBehaviorManager() {
        return behaviorManager;
    }

    public com.zenax.armorsets.effects.MarkManager getMarkManager() {
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

    public NotificationBossBarManager getNotificationBossBarManager() {
        return notificationBossBarManager;
    }

    public ResourcePackNotifier getResourcePackNotifier() {
        return resourcePackNotifier;
    }
}
