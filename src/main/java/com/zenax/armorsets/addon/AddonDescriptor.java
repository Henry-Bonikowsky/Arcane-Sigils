package com.zenax.armorsets.addon;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Represents the addon.yml descriptor file for an addon.
 * Contains metadata about the addon including its main class,
 * dependencies, and other information.
 */
public class AddonDescriptor {

    private final String id;
    private final String name;
    private final String version;
    private final String main;
    private final String author;
    private final String description;
    private final List<String> dependencies;
    private final List<String> softDependencies;
    private final File jarFile;

    private AddonDescriptor(String id, String name, String version, String main,
                            String author, String description,
                            List<String> dependencies, List<String> softDependencies,
                            File jarFile) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.main = main;
        this.author = author;
        this.description = description;
        this.dependencies = dependencies;
        this.softDependencies = softDependencies;
        this.jarFile = jarFile;
    }

    /**
     * Load an addon descriptor from a JAR file.
     *
     * @param file The JAR file
     * @return The descriptor, or null if not found or invalid
     */
    public static AddonDescriptor fromJar(File file) {
        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("addon.yml");
            if (entry == null) {
                return null;
            }

            try (InputStream in = jar.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(in)) {

                YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
                return fromConfig(config, file);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Load an addon descriptor from a YAML configuration.
     *
     * @param config  The configuration
     * @param jarFile The JAR file this came from
     * @return The descriptor
     */
    public static AddonDescriptor fromConfig(YamlConfiguration config, File jarFile) {
        String id = config.getString("id");
        String name = config.getString("name", id);
        String version = config.getString("version", "1.0.0");
        String main = config.getString("main");
        String author = config.getString("author", "Unknown");
        String description = config.getString("description", "");

        List<String> dependencies = config.getStringList("depend");
        List<String> softDependencies = config.getStringList("softdepend");

        if (id == null || main == null) {
            return null;
        }

        return new AddonDescriptor(
            id, name, version, main, author, description,
            dependencies, softDependencies, jarFile
        );
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getMain() {
        return main;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    public List<String> getSoftDependencies() {
        return Collections.unmodifiableList(softDependencies);
    }

    public File getJarFile() {
        return jarFile;
    }

    @Override
    public String toString() {
        return String.format("%s v%s (%s)", name, version, id);
    }
}
