package com.zenax.dungeons.combat;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Represents a mob template loaded from configuration.
 * Templates define the base properties of mobs that can be spawned in dungeons.
 */
public class MobTemplate {
    private final String id;
    private final String displayName;
    private final EntityType entityType;
    private final MobTier tier;
    private final double baseHealth;
    private final double baseDamage;
    private final double baseSpeed;
    private final Map<String, ItemStack> equipment;
    private final List<String> abilityIds;
    private final String lootTableId;

    /**
     * Creates a new mob template.
     *
     * @param id Unique identifier for this template
     * @param displayName Display name for the mob
     * @param entityType The Bukkit entity type
     * @param tier The mob tier (MINION, ELITE, BOSS)
     * @param baseHealth Base health points
     * @param baseDamage Base damage output
     * @param baseSpeed Movement speed modifier
     * @param equipment Map of equipment slots to items
     * @param abilityIds List of ability IDs this mob can use
     * @param lootTableId ID of the loot table for drops
     */
    public MobTemplate(String id, String displayName, EntityType entityType, MobTier tier,
                      double baseHealth, double baseDamage, double baseSpeed,
                      Map<String, ItemStack> equipment, List<String> abilityIds, String lootTableId) {
        this.id = id;
        this.displayName = displayName;
        this.entityType = entityType;
        this.tier = tier;
        this.baseHealth = baseHealth;
        this.baseDamage = baseDamage;
        this.baseSpeed = baseSpeed;
        this.equipment = new HashMap<>(equipment);
        this.abilityIds = new ArrayList<>(abilityIds);
        this.lootTableId = lootTableId;
    }

    /**
     * Loads a mob template from a configuration section.
     *
     * @param config The configuration section containing mob data
     * @return The loaded MobTemplate, or null if invalid
     */
    public static MobTemplate fromConfig(ConfigurationSection config) {
        if (config == null) {
            return null;
        }

        try {
            String id = config.getName();
            String displayName = config.getString("displayName", id);

            // Parse entity type
            String entityTypeName = config.getString("entityType", "ZOMBIE");
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityTypeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid entity type: " + entityTypeName);
                return null;
            }

            // Parse tier
            String tierName = config.getString("tier", "MINION");
            MobTier tier = MobTier.fromString(tierName);
            if (tier == null) {
                System.err.println("Invalid mob tier: " + tierName);
                tier = MobTier.MINION;
            }

            // Parse stats
            double baseHealth = config.getDouble("baseHealth", 20.0);
            double baseDamage = config.getDouble("baseDamage", 5.0);
            double baseSpeed = config.getDouble("baseSpeed", 1.0);

            // Parse equipment
            Map<String, ItemStack> equipment = new HashMap<>();
            ConfigurationSection equipmentSection = config.getConfigurationSection("equipment");
            if (equipmentSection != null) {
                for (String slot : equipmentSection.getKeys(false)) {
                    String materialName = equipmentSection.getString(slot);
                    if (materialName != null && !materialName.isEmpty()) {
                        try {
                            Material material = Material.valueOf(materialName.toUpperCase());
                            equipment.put(slot.toLowerCase(), new ItemStack(material));
                        } catch (IllegalArgumentException e) {
                            System.err.println("Invalid material for equipment slot " + slot + ": " + materialName);
                        }
                    }
                }
            }

            // Parse abilities
            List<String> abilityIds = config.getStringList("abilities");
            if (abilityIds == null) {
                abilityIds = new ArrayList<>();
            }

            // Parse loot table
            String lootTableId = config.getString("lootTable", "default");

            return new MobTemplate(id, displayName, entityType, tier, baseHealth, baseDamage,
                                  baseSpeed, equipment, abilityIds, lootTableId);
        } catch (Exception e) {
            System.err.println("Error loading mob template from config: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public MobTier getTier() {
        return tier;
    }

    public double getBaseHealth() {
        return baseHealth;
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public double getBaseSpeed() {
        return baseSpeed;
    }

    public Map<String, ItemStack> getEquipment() {
        return new HashMap<>(equipment);
    }

    public List<String> getAbilityIds() {
        return new ArrayList<>(abilityIds);
    }

    public String getLootTableId() {
        return lootTableId;
    }

    /**
     * Gets the formatted display name with tier prefix.
     *
     * @return The formatted name
     */
    public String getFormattedName() {
        return tier.formatName(displayName);
    }

    @Override
    public String toString() {
        return "MobTemplate{" +
               "id='" + id + '\'' +
               ", displayName='" + displayName + '\'' +
               ", tier=" + tier +
               ", entityType=" + entityType +
               '}';
    }
}
