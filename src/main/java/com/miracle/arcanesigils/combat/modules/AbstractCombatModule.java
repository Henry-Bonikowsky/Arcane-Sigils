package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.combat.LegacyCombatConfig;
import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Base class for combat modules with common functionality.
 */
public abstract class AbstractCombatModule implements CombatModule {

    protected final ArmorSetsPlugin plugin;
    protected final LegacyCombatManager manager;
    protected final LegacyCombatConfig config;
    protected boolean enabled = false;

    public AbstractCombatModule(LegacyCombatManager manager) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
        this.config = manager.getConfig();
    }

    @Override
    public boolean isEnabled() {
        return enabled && config.isModuleEnabled(getId());
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            onEnable();
            // Apply to all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyToPlayer(player);
            }
        } else {
            // Remove from all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                removeFromPlayer(player);
            }
            onDisable();
        }
    }

    @Override
    public void reload() {
        boolean shouldBeEnabled = config.isModuleEnabled(getId());
        if (shouldBeEnabled != enabled) {
            setEnabled(shouldBeEnabled);
        }
    }

    @Override
    public void onEnable() {
        // Override in subclasses if needed
    }

    @Override
    public void onDisable() {
        // Override in subclasses if needed
    }

    @Override
    public void applyToPlayer(Player player) {
        // Override in subclasses
    }

    @Override
    public void removeFromPlayer(Player player) {
        // Override in subclasses
    }
}
