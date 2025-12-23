package com.zenax.armorsets.addon;

import com.zenax.armorsets.ArmorSetsPlugin;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the lifecycle of Arcane Sigils addons.
 * Handles discovery, loading, enabling, disabling, and reloading of addons.
 */
public class AddonManager {

    private final ArmorSetsPlugin plugin;
    private final File addonsFolder;
    private final Map<String, ArcaneSigilsAddon> addons = new LinkedHashMap<>();
    private final Map<String, AddonClassLoader> classLoaders = new HashMap<>();
    private final Map<String, AddonDescriptor> descriptors = new HashMap<>();

    public AddonManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.addonsFolder = new File(plugin.getDataFolder(), "addons");

        if (!addonsFolder.exists()) {
            addonsFolder.mkdirs();
        }
    }

    /**
     * Discover and load all addons from the addons folder.
     * Call this during plugin enable after all core managers are initialized.
     */
    public void loadAddons() {
        plugin.getLogger().info("Loading addons from " + addonsFolder.getPath());

        // Discover addon JARs
        List<AddonDescriptor> discovered = discoverAddons();
        if (discovered.isEmpty()) {
            plugin.getLogger().info("No addons found.");
            return;
        }

        plugin.getLogger().info("Found " + discovered.size() + " addon(s)");

        // Sort by dependencies
        List<AddonDescriptor> sorted = sortByDependencies(discovered);

        // Load each addon
        for (AddonDescriptor descriptor : sorted) {
            try {
                loadAddon(descriptor);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                    "Failed to load addon: " + descriptor.getName(), e);
            }
        }

        // Enable all loaded addons
        for (ArcaneSigilsAddon addon : addons.values()) {
            try {
                enableAddon(addon);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                    "Failed to enable addon: " + addon.getName(), e);
            }
        }

        plugin.getLogger().info("Enabled " + addons.size() + " addon(s)");
    }

    /**
     * Discover addon JAR files in the addons folder.
     *
     * @return List of addon descriptors
     */
    private List<AddonDescriptor> discoverAddons() {
        List<AddonDescriptor> discovered = new ArrayList<>();

        File[] files = addonsFolder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null) return discovered;

        for (File file : files) {
            AddonDescriptor descriptor = AddonDescriptor.fromJar(file);
            if (descriptor != null) {
                discovered.add(descriptor);
                plugin.getLogger().info("Discovered addon: " + descriptor);
            } else {
                plugin.getLogger().warning("Invalid addon JAR (missing addon.yml): " + file.getName());
            }
        }

        return discovered;
    }

    /**
     * Sort addons by their dependencies so they load in the correct order.
     *
     * @param addons The addons to sort
     * @return Sorted list
     */
    private List<AddonDescriptor> sortByDependencies(List<AddonDescriptor> addons) {
        Map<String, AddonDescriptor> byId = new HashMap<>();
        for (AddonDescriptor desc : addons) {
            byId.put(desc.getId(), desc);
        }

        List<AddonDescriptor> sorted = new ArrayList<>();
        Set<String> loaded = new HashSet<>();

        // Simple topological sort
        while (sorted.size() < addons.size()) {
            boolean progress = false;

            for (AddonDescriptor desc : addons) {
                if (loaded.contains(desc.getId())) continue;

                // Check if all dependencies are loaded
                boolean depsLoaded = true;
                for (String dep : desc.getDependencies()) {
                    if (!loaded.contains(dep) && byId.containsKey(dep)) {
                        depsLoaded = false;
                        break;
                    }
                }

                if (depsLoaded) {
                    sorted.add(desc);
                    loaded.add(desc.getId());
                    progress = true;
                }
            }

            if (!progress) {
                // Circular dependency or missing dependency
                for (AddonDescriptor desc : addons) {
                    if (!loaded.contains(desc.getId())) {
                        plugin.getLogger().warning("Could not load addon due to missing dependencies: "
                            + desc.getName() + " (needs: " + desc.getDependencies() + ")");
                    }
                }
                break;
            }
        }

        return sorted;
    }

    /**
     * Load a single addon from its descriptor.
     *
     * @param descriptor The addon descriptor
     * @throws Exception If loading fails
     */
    private void loadAddon(AddonDescriptor descriptor) throws Exception {
        // Check dependencies
        for (String dep : descriptor.getDependencies()) {
            if (!addons.containsKey(dep)) {
                throw new IllegalStateException("Missing dependency: " + dep);
            }
        }

        // Create class loader
        AddonClassLoader loader = new AddonClassLoader(
            descriptor, this, plugin.getClass().getClassLoader()
        );

        // Load the addon class
        ArcaneSigilsAddon addon = loader.loadAddon();

        // Set up addon resources
        File dataFolder = new File(addonsFolder, descriptor.getId());
        addon.setDataFolder(dataFolder);

        Logger logger = Logger.getLogger("ArcaneSigils:" + descriptor.getName());
        logger.setParent(plugin.getLogger());
        addon.setLogger(logger);

        // Store references
        classLoaders.put(descriptor.getId(), loader);
        descriptors.put(descriptor.getId(), descriptor);
        addons.put(descriptor.getId(), addon);

        // Call onLoad
        addon.onLoad(plugin);

        plugin.getLogger().info("Loaded addon: " + descriptor);
    }

    /**
     * Enable an addon.
     *
     * @param addon The addon to enable
     */
    private void enableAddon(ArcaneSigilsAddon addon) {
        addon.onEnable(plugin);

        // Register listeners
        for (Listener listener : addon.getListeners()) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        addon.getLogger().info(addon.getName() + " v" + addon.getVersion() + " enabled");
    }

    /**
     * Disable all addons.
     * Call this during plugin disable.
     */
    public void disableAddons() {
        // Disable in reverse order
        List<ArcaneSigilsAddon> reversed = new ArrayList<>(addons.values());
        Collections.reverse(reversed);

        for (ArcaneSigilsAddon addon : reversed) {
            try {
                disableAddon(addon);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                    "Failed to disable addon: " + addon.getName(), e);
            }
        }

        // Close class loaders
        for (AddonClassLoader loader : classLoaders.values()) {
            try {
                loader.close();
            } catch (IOException e) {
                // Ignore
            }
        }

        addons.clear();
        classLoaders.clear();
        descriptors.clear();
    }

    /**
     * Disable a single addon.
     *
     * @param addon The addon to disable
     */
    private void disableAddon(ArcaneSigilsAddon addon) {
        // Unregister listeners
        for (Listener listener : addon.getListeners()) {
            HandlerList.unregisterAll(listener);
        }

        addon.onDisable();
        addon.getLogger().info(addon.getName() + " disabled");
    }

    /**
     * Reload all addons.
     */
    public void reloadAddons() {
        for (ArcaneSigilsAddon addon : addons.values()) {
            try {
                addon.onReload();
                addon.getLogger().info("Reloaded");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                    "Failed to reload addon: " + addon.getName(), e);
            }
        }
    }

    /**
     * Get an addon by its ID.
     *
     * @param id The addon ID
     * @return The addon, or null if not found
     */
    public ArcaneSigilsAddon getAddon(String id) {
        return addons.get(id);
    }

    /**
     * Get an addon by its type.
     *
     * @param type The addon class
     * @param <T>  The addon type
     * @return The addon instance, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T extends ArcaneSigilsAddon> T getAddon(Class<T> type) {
        for (ArcaneSigilsAddon addon : addons.values()) {
            if (type.isInstance(addon)) {
                return (T) addon;
            }
        }
        return null;
    }

    /**
     * Check if an addon is loaded.
     *
     * @param id The addon ID
     * @return true if loaded
     */
    public boolean isLoaded(String id) {
        return addons.containsKey(id);
    }

    /**
     * Get all loaded addons.
     *
     * @return Collection of addons
     */
    public Collection<ArcaneSigilsAddon> getAddons() {
        return Collections.unmodifiableCollection(addons.values());
    }

    /**
     * Get the number of loaded addons.
     *
     * @return Addon count
     */
    public int getAddonCount() {
        return addons.size();
    }

    /**
     * Get the addons folder.
     *
     * @return The addons folder
     */
    public File getAddonsFolder() {
        return addonsFolder;
    }
}
