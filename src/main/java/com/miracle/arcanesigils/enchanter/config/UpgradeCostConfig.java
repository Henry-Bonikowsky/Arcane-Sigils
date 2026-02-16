package com.miracle.arcanesigils.enchanter.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages upgrade costs for a sigil.
 * Loads costs from sigil YAML upgrade_costs section.
 */
public class UpgradeCostConfig {

    private final Map<Integer, UpgradeCost> costs; // tier -> cost

    public UpgradeCostConfig() {
        this.costs = new HashMap<>();
    }

    /**
     * Load upgrade costs from sigil YAML configuration.
     *
     * @param section The ConfigurationSection containing upgrade_costs
     * @param sigilId The sigil ID for logging purposes
     * @return True if costs were loaded successfully
     */
    public static UpgradeCostConfig loadFromConfig(ConfigurationSection section, String sigilId) {
        UpgradeCostConfig config = new UpgradeCostConfig();

        if (section == null) {
            return config; // Empty config, no upgrade costs defined
        }

        // Parse each tier's cost (tier_2, tier_3, etc.)
        for (String key : section.getKeys(false)) {
            if (!key.startsWith("tier_")) {
                continue;
            }

            try {
                int tier = Integer.parseInt(key.substring(5)); // Extract tier number from "tier_X"
                ConfigurationSection tierSection = section.getConfigurationSection(key);

                if (tierSection == null) {
                    continue;
                }

                int xp = tierSection.getInt("xp", 0);
                List<ItemStack> materials = parseMaterials(tierSection.getList("materials"));

                config.costs.put(tier, new UpgradeCost(xp, materials));

            } catch (NumberFormatException e) {
                // Invalid tier number, skip
            }
        }

        return config;
    }

    /**
     * Parse materials list from YAML.
     * Expected format:
     * materials:
     *   - type: DIAMOND
     *     amount: 5
     *   - type: AMETHYST_SHARD
     *     amount: 10
     */
    private static List<ItemStack> parseMaterials(List<?> materialsList) {
        List<ItemStack> materials = new ArrayList<>();

        if (materialsList == null) {
            return materials;
        }

        for (Object obj : materialsList) {
            if (obj instanceof ConfigurationSection matSection) {
                String typeName = matSection.getString("type");
                int amount = matSection.getInt("amount", 1);

                if (typeName != null) {
                    try {
                        Material material = Material.valueOf(typeName);
                        materials.add(new ItemStack(material, amount));
                    } catch (IllegalArgumentException e) {
                        // Invalid material type, skip
                    }
                }
            }
        }

        return materials;
    }

    /**
     * Get the upgrade cost for a specific tier.
     *
     * @param targetTier The tier to upgrade TO (e.g., 2 for tier 1→2 upgrade)
     * @return The upgrade cost, or null if not defined
     */
    public UpgradeCost getCostForTier(int targetTier) {
        return costs.get(targetTier);
    }

    /**
     * Check if upgrade costs are defined for a tier.
     */
    public boolean hasCostForTier(int targetTier) {
        return costs.containsKey(targetTier);
    }

    /**
     * Check if this config has any upgrade costs defined.
     */
    public boolean hasCosts() {
        return !costs.isEmpty();
    }

    /**
     * Get all defined upgrade costs.
     */
    public Map<Integer, UpgradeCost> getAllCosts() {
        return new HashMap<>(costs);
    }
}
