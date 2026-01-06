package com.miracle.arcanesigils.combat.modules;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Interface for all 1.8 combat modules.
 * Each module handles one aspect of legacy combat mechanics.
 */
public interface CombatModule {

    /**
     * Get the unique identifier for this module.
     */
    String getId();

    /**
     * Get display name for GUI.
     */
    String getDisplayName();

    /**
     * Check if this module is enabled.
     */
    boolean isEnabled();

    /**
     * Enable or disable this module.
     */
    void setEnabled(boolean enabled);

    /**
     * Called when the module is first loaded.
     */
    void onEnable();

    /**
     * Called when the module is being disabled.
     */
    void onDisable();

    /**
     * Called when config is reloaded - refresh cached values.
     */
    void reload();

    /**
     * Apply module effects to a player (e.g., on join).
     */
    void applyToPlayer(Player player);

    /**
     * Remove module effects from a player (e.g., on quit).
     */
    void removeFromPlayer(Player player);

    /**
     * Get all configurable parameters for this module.
     * Used by the GUI to build runtime configuration screens.
     */
    default List<ModuleParam> getConfigParams() {
        return Collections.emptyList();
    }
}
