package com.miracle.arcanesigils.combat;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.combat.modules.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Central manager for the 1.8 Legacy Combat system.
 * Coordinates all combat modules and handles player lifecycle.
 */
public class LegacyCombatManager implements Listener {

    private final ArmorSetsPlugin plugin;
    private final LegacyCombatConfig config;
    private final Map<String, CombatModule> modules = new HashMap<>();

    public LegacyCombatManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.config = new LegacyCombatConfig(plugin);

        // Register event listener
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Initialize modules
        initializeModules();

        plugin.getLogger().info("Legacy Combat system initialized with " + modules.size() + " modules");
    }

    private void initializeModules() {
        // Register all combat modules
        registerModule(new AttackCooldownModule(this));
        registerModule(new SweepAttackModule(this));
        registerModule(new HitSoundFilterModule(this));
        registerModule(new CustomImmunityModule(this));
        registerModule(new HitboxModule(this));
        registerModule(new CriticalHitModule(this));
        registerModule(new RegenerationModule(this));
        registerModule(new SwordBlockingModule(this));
        registerModule(new FishingRodModule(this));
        registerModule(new KnockbackModule(this));
        registerModule(new ToolDamageModule(this));
        registerModule(new GoldenAppleModule(this));
        registerModule(new AttackIndicatorModule(this));
        registerModule(new PotionModule(this));
        registerModule(new ProjectileKnockbackModule(this));
        registerModule(new CPSLimitModule(this));

        // Enable modules based on config
        for (CombatModule module : modules.values()) {
            try {
                if (config.isModuleEnabled(module.getId())) {
                    module.setEnabled(true);
                    plugin.getLogger().info("  - " + module.getDisplayName() + ": Enabled");
                } else {
                    plugin.getLogger().info("  - " + module.getDisplayName() + ": Disabled");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to initialize module: " + module.getId(), e);
            }
        }
    }

    private void registerModule(CombatModule module) {
        modules.put(module.getId(), module);

        // If module is a listener, register it
        if (module instanceof Listener listener) {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Start tracking player position for KB sync

        // Apply all enabled modules to the player
        for (CombatModule module : modules.values()) {
            if (module.isEnabled()) {
                try {
                    module.applyToPlayer(player);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                        "Failed to apply module " + module.getId() + " to " + player.getName(), e);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Stop tracking player position

        // Remove module effects from player
        for (CombatModule module : modules.values()) {
            if (module.isEnabled()) {
                try {
                    module.removeFromPlayer(player);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                        "Failed to remove module " + module.getId() + " from " + player.getName(), e);
                }
            }
        }
    }

    /**
     * Reload all configs and modules.
     */
    public void reload() {
        config.load();

        for (CombatModule module : modules.values()) {
            module.reload();
        }

        plugin.getLogger().info("Legacy Combat system reloaded");
    }

    /**
     * Disable all modules (called on plugin disable).
     */
    public void disable() {
        for (CombatModule module : modules.values()) {
            if (module.isEnabled()) {
                module.setEnabled(false);
            }
        }
    }

    /**
     * Get a module by ID.
     */
    @SuppressWarnings("unchecked")
    public <T extends CombatModule> T getModule(String id) {
        return (T) modules.get(id);
    }

    /**
     * Get all registered modules.
     */
    public Map<String, CombatModule> getModules() {
        return modules;
    }

    public ArmorSetsPlugin getPlugin() {
        return plugin;
    }

    public LegacyCombatConfig getConfig() {
        return config;
    }

}
