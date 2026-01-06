package com.miracle.arcanesigils.addon;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Custom class loader for loading addon JAR files.
 * Allows addons to have their own classes while still accessing
 * Arcane Sigils and Bukkit APIs.
 */
public class AddonClassLoader extends URLClassLoader {

    private final AddonDescriptor descriptor;
    private final AddonManager manager;
    private ArcaneSigilsAddon addon;

    static {
        ClassLoader.registerAsParallelCapable();
    }

    /**
     * Create a new addon class loader.
     *
     * @param descriptor The addon descriptor
     * @param manager    The addon manager
     * @param parent     The parent class loader (usually the plugin's)
     * @throws MalformedURLException If the JAR URL is invalid
     */
    public AddonClassLoader(AddonDescriptor descriptor, AddonManager manager, ClassLoader parent)
            throws MalformedURLException {
        super(new URL[]{descriptor.getJarFile().toURI().toURL()}, parent);
        this.descriptor = descriptor;
        this.manager = manager;
    }

    /**
     * Load the main addon class and create an instance.
     *
     * @return The addon instance
     * @throws Exception If loading fails
     */
    public ArcaneSigilsAddon loadAddon() throws Exception {
        Class<?> mainClass = Class.forName(descriptor.getMain(), true, this);

        if (!ArcaneSigilsAddon.class.isAssignableFrom(mainClass)) {
            throw new IllegalArgumentException(
                "Main class " + descriptor.getMain() + " does not implement ArcaneSigilsAddon"
            );
        }

        @SuppressWarnings("unchecked")
        Class<? extends ArcaneSigilsAddon> addonClass =
            (Class<? extends ArcaneSigilsAddon>) mainClass;

        addon = addonClass.getDeclaredConstructor().newInstance();
        return addon;
    }

    /**
     * Get the addon instance loaded by this class loader.
     *
     * @return The addon instance, or null if not loaded yet
     */
    public ArcaneSigilsAddon getAddon() {
        return addon;
    }

    /**
     * Get the addon descriptor.
     *
     * @return The descriptor
     */
    public AddonDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }
}
