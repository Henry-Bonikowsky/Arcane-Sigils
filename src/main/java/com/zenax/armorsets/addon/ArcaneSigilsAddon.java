package com.zenax.armorsets.addon;

import com.zenax.armorsets.ArmorSetsPlugin;
import org.bukkit.event.Listener;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Base interface for all Arcane Sigils addons.
 * Addons extend the functionality of Arcane Sigils by providing
 * custom effects, events, commands, and more.
 */
public interface ArcaneSigilsAddon {

    /**
     * Called when the addon is loaded but before it is enabled.
     * Use this for early initialization that doesn't depend on other addons.
     *
     * @param plugin The main Arcane Sigils plugin instance
     */
    default void onLoad(ArmorSetsPlugin plugin) {}

    /**
     * Called when the addon is enabled.
     * Register effects, listeners, commands, and perform main initialization here.
     *
     * @param plugin The main Arcane Sigils plugin instance
     */
    void onEnable(ArmorSetsPlugin plugin);

    /**
     * Called when the addon is disabled.
     * Clean up resources, save data, and unregister listeners here.
     */
    void onDisable();

    /**
     * Called when the addon should reload its configuration.
     */
    default void onReload() {}

    /**
     * Get the unique identifier for this addon.
     * Should be lowercase with no spaces (e.g., "dungeons", "custom-effects").
     *
     * @return The addon ID
     */
    String getId();

    /**
     * Get the display name for this addon.
     *
     * @return The addon name
     */
    String getName();

    /**
     * Get the version of this addon.
     *
     * @return The version string
     */
    String getVersion();

    /**
     * Get the author(s) of this addon.
     *
     * @return The author string
     */
    default String getAuthor() {
        return "Unknown";
    }

    /**
     * Get a description of this addon.
     *
     * @return The description
     */
    default String getDescription() {
        return "";
    }

    /**
     * Get the IDs of addons this addon depends on.
     * These addons must be loaded before this one.
     *
     * @return List of addon IDs this depends on
     */
    default List<String> getDependencies() {
        return Collections.emptyList();
    }

    /**
     * Get the IDs of addons this addon should load after (soft dependencies).
     * Unlike hard dependencies, the addon will still load if these are missing.
     *
     * @return List of addon IDs to load after
     */
    default List<String> getSoftDependencies() {
        return Collections.emptyList();
    }

    /**
     * Get event listeners to register for this addon.
     * These will be automatically registered when the addon is enabled.
     *
     * @return List of listeners to register
     */
    default List<Listener> getListeners() {
        return Collections.emptyList();
    }

    /**
     * Get the data folder for this addon.
     * Located at plugins/ArcaneSigils/addons/{addon-id}/
     *
     * @return The addon's data folder
     */
    File getDataFolder();

    /**
     * Set the data folder for this addon.
     * Called by the AddonManager during loading.
     *
     * @param folder The data folder
     */
    void setDataFolder(File folder);

    /**
     * Get a logger for this addon.
     *
     * @return The addon's logger
     */
    Logger getLogger();

    /**
     * Set the logger for this addon.
     * Called by the AddonManager during loading.
     *
     * @param logger The logger
     */
    void setLogger(Logger logger);
}
