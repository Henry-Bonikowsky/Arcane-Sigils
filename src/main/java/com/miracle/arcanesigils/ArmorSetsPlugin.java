package com.miracle.arcanesigils;

import com.miracle.arcanesigils.addon.AddonManager;
import com.miracle.arcanesigils.ai.AITrainingManager;
import com.miracle.arcanesigils.debug.PluginDebugger;
import com.miracle.arcanesigils.binds.BindsBossBarManager;
import com.miracle.arcanesigils.binds.BindsListener;
import com.miracle.arcanesigils.binds.BindsManager;
import com.miracle.arcanesigils.binds.TargetGlowManager;
import com.miracle.arcanesigils.binds.LastVictimManager;
import com.miracle.arcanesigils.combat.LegacyCombatManager;
import com.miracle.arcanesigils.commands.ActivateBindCommand;
import com.miracle.arcanesigils.commands.ArmorSetsCommand;
import com.miracle.arcanesigils.commands.BindsCommand;
import com.miracle.arcanesigils.config.ConfigManager;
import com.miracle.arcanesigils.enchanter.EnchanterManager;
import com.miracle.arcanesigils.enchanter.commands.EnchanterCommand;
import com.miracle.arcanesigils.enchanter.listeners.EnchanterBlockListener;
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
import com.miracle.arcanesigils.interception.InterceptionManager;
import com.miracle.arcanesigils.interception.PotionEffectInterceptionListener;
import com.miracle.arcanesigils.listeners.EnchantCapRemover;
import com.miracle.arcanesigils.particles.ShapeEngine;
import com.miracle.arcanesigils.tier.TierProgressionManager;
import com.miracle.arcanesigils.utils.ScreenShakeUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private com.miracle.arcanesigils.variables.PlayerVariableManager playerVariableManager;
    private com.miracle.arcanesigils.variables.SigilVariableManager sigilVariableManager;
    private SigilManager sigilManager;
    private SocketManager socketManager;
    private com.miracle.arcanesigils.sets.SetBonusManager setBonusManager;
    private com.miracle.arcanesigils.notifications.NotificationManager notificationManager;
    private SignalHandler signalHandler;
    private GUIManager guiManager;
    private TierProgressionManager tierProgressionManager;
    private AddonManager addonManager;
    private BindsListener bindsListener;
    private BindsManager bindsManager;
    private BindsBossBarManager bindsBossBarManager;
    private TargetGlowManager targetGlowManager;
    private LastVictimManager lastVictimManager;
    private ShapeEngine shapeEngine;
    private ProjectileManager projectileManager;
    private LegacyCombatManager legacyCombatManager;
    private PluginDebugger pluginDebugger;
    private com.miracle.arcanesigils.listeners.CollisionDisabler collisionDisabler;
    private AITrainingManager aiTrainingManager;
    private InterceptionManager interceptionManager;
    private com.miracle.arcanesigils.effects.AttributeModifierManager attributeModifierManager;
    private com.miracle.arcanesigils.effects.PotionEffectTracker potionEffectTracker;
    private EnchanterManager enchanterManager;

    // Track players with active Quicksand (no knockback mode)
    private final Map<UUID, Long> quicksandActivePlayers = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Initializing ArcaneSigils plugin...");

        // Initialize LogHelper first
        com.miracle.arcanesigils.utils.LogHelper.init(this);

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
        if (lastVictimManager != null) {
            lastVictimManager.cleanup();
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
        if (attributeModifierManager != null) {
            attributeModifierManager.shutdown();
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
        if (collisionDisabler != null) {
            collisionDisabler.shutdown();
        }
        if (aiTrainingManager != null) {
            aiTrainingManager.shutdown();
        }
        if (playerVariableManager != null) {
            playerVariableManager.shutdown();
        }
        if (sigilVariableManager != null) {
            sigilVariableManager.shutdown();
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

            // Now that config is loaded, refresh LogHelper debug setting
            com.miracle.arcanesigils.utils.LogHelper.refreshDebugSetting(this);

            // Effect manager (needed by others)
            effectManager = new EffectManager(this);
            
            // Attribute modifier manager (prevents stacking bugs)
            attributeModifierManager = new com.miracle.arcanesigils.effects.AttributeModifierManager(this);

            // Potion effect tracker (for Ancient Crown counter-modifiers)
            potionEffectTracker = new com.miracle.arcanesigils.effects.PotionEffectTracker(this);

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

            // Player variable manager
            playerVariableManager = new com.miracle.arcanesigils.variables.PlayerVariableManager(this);
            
            // Sigil variable manager (per-sigil state tracking)
            sigilVariableManager = new com.miracle.arcanesigils.variables.SigilVariableManager(this);

            // Sigil manager
            sigilManager = new SigilManager(this);
            sigilManager.loadSigils();
            sigilManager.loadBehaviors();

            // Socket manager
            socketManager = new SocketManager(this);

            // Set bonus manager (load set bonuses from sets/ folder)
            setBonusManager = new com.miracle.arcanesigils.sets.SetBonusManager(this);

            // Notification manager (chat notifications)
            notificationManager = new com.miracle.arcanesigils.notifications.NotificationManager(this);

            // Tier progression manager (XP system)
            tierProgressionManager = new TierProgressionManager(this);

            // Last victim manager (tracks last combat-relevant entity hit by player)
            // MUST be created before SignalHandler since SignalHandler depends on it
            lastVictimManager = new LastVictimManager(this);

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

            // Collision disabler (prevent player collisions for effects like quicksand)
            collisionDisabler = new com.miracle.arcanesigils.listeners.CollisionDisabler();

            // AI Training Manager (reward signals for AI training)
            aiTrainingManager = new AITrainingManager(this);

            // Interception Manager (effect interception system for Ancient Crown, Cleopatra, etc.)
            interceptionManager = new InterceptionManager();

            // Enchanter Manager (tier upgrade system)
            enchanterManager = new EnchanterManager(this);

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

        // Enchanter command
        var ceCommand = getCommand("ce");
        if (ceCommand != null) {
            EnchanterCommand enchanterCmd = new EnchanterCommand(this);
            ceCommand.setExecutor(enchanterCmd);
            ceCommand.setTabCompleter(enchanterCmd);
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
        pm.registerEvents(collisionDisabler, this);
        pm.registerEvents(new com.miracle.arcanesigils.listeners.QuicksandKnockbackListener(this), this);
        pm.registerEvents(new com.miracle.arcanesigils.listeners.ItemCooldownListener(this), this);

        pm.registerEvents(new PotionEffectInterceptionListener(this), this);

        pm.registerEvents(potionEffectTracker, this);
        pm.registerEvents(new com.miracle.arcanesigils.listeners.PotionDamageReductionListener(this), this);
        pm.registerEvents(new com.miracle.arcanesigils.listeners.ArmorChangeListener(this), this);
        pm.registerEvents(new EnchanterBlockListener(this), this);
        pm.registerEvents(attributeModifierManager, this);

        // Disable collision for all currently online players
        collisionDisabler.disableForAll();
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

        // Reload set bonuses
        if (setBonusManager != null) {
            setBonusManager.reload();
        }

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

    public com.miracle.arcanesigils.variables.PlayerVariableManager getPlayerVariableManager() {
        return playerVariableManager;
    }
    
    public com.miracle.arcanesigils.variables.SigilVariableManager getSigilVariableManager() {
        return sigilVariableManager;
    }

    public SigilManager getSigilManager() {
        return sigilManager;
    }

    public SocketManager getSocketManager() {
        return socketManager;
    }

    public com.miracle.arcanesigils.sets.SetBonusManager getSetBonusManager() {
        return setBonusManager;
    }

    public com.miracle.arcanesigils.notifications.NotificationManager getNotificationManager() {
        return notificationManager;
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

    public LastVictimManager getLastVictimManager() {
        return lastVictimManager;
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

    public BindsListener getBindsListener() {
        return bindsListener;
    }

    public AITrainingManager getAITrainingManager() {
        return aiTrainingManager;
    }

    public InterceptionManager getInterceptionManager() {
        return interceptionManager;
    }
    
    public com.miracle.arcanesigils.effects.AttributeModifierManager getAttributeModifierManager() {
        return attributeModifierManager;
    }

    public com.miracle.arcanesigils.effects.PotionEffectTracker getPotionEffectTracker() {
        return potionEffectTracker;
    }

    public EnchanterManager getEnchanterManager() {
        return enchanterManager;
    }

    /**
     * Register a player as having active Quicksand (no knockback mode).
     * @param playerId Player UUID
     * @param durationSeconds How long Quicksand is active
     */
    public void registerQuicksandActive(UUID playerId, int durationSeconds) {
        long expiryTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        quicksandActivePlayers.put(playerId, expiryTime);
    }
    
    /**
     * Check if a player has active Quicksand (should give no knockback).
     * @param playerId Player UUID
     * @return true if Quicksand is active
     */
    public boolean hasActiveQuicksand(UUID playerId) {
        Long expiryTime = quicksandActivePlayers.get(playerId);
        if (expiryTime == null) {
            return false;
        }
        
        // Check if expired
        if (System.currentTimeMillis() >= expiryTime) {
            quicksandActivePlayers.remove(playerId);
            return false;
        }
        
        return true;
    }
}
