package com.miracle.arcanesigils;

/**
 * Interface for plugin components that need lifecycle management.
 * Implement this on managers/services and register with the plugin
 * to get automatic shutdown/reload without editing ArmorSetsPlugin.
 */
public interface Lifecycle {

    /** Called when the plugin enables. */
    default void onEnable() {}

    /** Called when the plugin disables — clean up resources. */
    default void onDisable() {}

    /** Called on /as reload — refresh configs, clear caches. */
    default void onReload() {}
}
