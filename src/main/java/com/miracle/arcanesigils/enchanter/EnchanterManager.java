package com.miracle.arcanesigils.enchanter;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages Enchanter block locations and configuration.
 * Tracks registered blocks that players can interact with to access the Enchanter GUI.
 */
public class EnchanterManager {

    private final ArmorSetsPlugin plugin;
    private final List<Location> enchanterLocations;
    private Material blockType;

    public EnchanterManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.enchanterLocations = new ArrayList<>();
        loadConfig();
    }

    /**
     * Load Enchanter configuration from config.yml
     */
    public void loadConfig() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("enchanter");
        if (section == null) {
            // Set default values
            blockType = Material.ENCHANTING_TABLE;
            plugin.getConfig().set("enchanter.block_type", "ENCHANTING_TABLE");
            plugin.getConfig().set("enchanter.locations", new ArrayList<>());
            plugin.saveConfig();
            return;
        }

        // Load block type
        String blockTypeName = section.getString("block_type", "ENCHANTING_TABLE");
        try {
            blockType = Material.valueOf(blockTypeName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid Enchanter block type: " + blockTypeName + ", using ENCHANTING_TABLE");
            blockType = Material.ENCHANTING_TABLE;
        }

        // Load registered locations
        enchanterLocations.clear();
        List<?> locationsList = section.getList("locations");
        if (locationsList != null) {
            for (Object obj : locationsList) {
                if (obj instanceof ConfigurationSection locSection) {
                    String worldName = locSection.getString("world");
                    int x = locSection.getInt("x");
                    int y = locSection.getInt("y");
                    int z = locSection.getInt("z");

                    org.bukkit.World world = plugin.getServer().getWorld(worldName);
                    if (world != null) {
                        enchanterLocations.add(new Location(world, x, y, z));
                    } else {
                        plugin.getLogger().warning("Enchanter location in unknown world: " + worldName);
                    }
                }
            }
        }

        plugin.getLogger().info("Loaded " + enchanterLocations.size() + " Enchanter locations");
    }

    /**
     * Save registered Enchanter locations to config.yml
     */
    public void saveLocations() {
        List<Map<String, Object>> locationsList = new ArrayList<>();
        for (Location loc : enchanterLocations) {
            Map<String, Object> locMap = new HashMap<>();
            locMap.put("world", loc.getWorld().getName());
            locMap.put("x", loc.getBlockX());
            locMap.put("y", loc.getBlockY());
            locMap.put("z", loc.getBlockZ());
            locationsList.add(locMap);
        }

        plugin.getConfig().set("enchanter.locations", locationsList);
        plugin.saveConfig();
    }

    /**
     * Register a block as an Enchanter station
     */
    public boolean registerBlock(Block block) {
        if (block == null) {
            return false;
        }

        Location loc = block.getLocation();

        // Check if already registered
        if (isEnchanterBlock(block)) {
            return false;
        }

        enchanterLocations.add(loc);
        saveLocations();
        return true;
    }

    /**
     * Unregister an Enchanter block
     */
    public boolean unregisterBlock(Block block) {
        if (block == null) {
            return false;
        }

        Location loc = block.getLocation();
        boolean removed = enchanterLocations.removeIf(l ->
            l.getWorld().equals(loc.getWorld()) &&
            l.getBlockX() == loc.getBlockX() &&
            l.getBlockY() == loc.getBlockY() &&
            l.getBlockZ() == loc.getBlockZ()
        );

        if (removed) {
            saveLocations();
        }
        return removed;
    }

    /**
     * Check if a block is a registered Enchanter
     */
    public boolean isEnchanterBlock(Block block) {
        if (block == null) {
            return false;
        }

        Location loc = block.getLocation();
        return enchanterLocations.stream().anyMatch(l ->
            l.getWorld().equals(loc.getWorld()) &&
            l.getBlockX() == loc.getBlockX() &&
            l.getBlockY() == loc.getBlockY() &&
            l.getBlockZ() == loc.getBlockZ()
        );
    }

    /**
     * Get all registered Enchanter locations
     */
    public List<Location> getLocations() {
        return new ArrayList<>(enchanterLocations);
    }

    /**
     * Get the configured Enchanter block type
     */
    public Material getBlockType() {
        return blockType;
    }

    /**
     * Reload configuration
     */
    public void reload() {
        loadConfig();
    }
}
