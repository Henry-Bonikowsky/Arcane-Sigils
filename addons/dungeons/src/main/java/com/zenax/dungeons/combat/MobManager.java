package com.zenax.dungeons.combat;

import com.zenax.dungeons.dungeon.DungeonDifficulty;
import com.zenax.dungeons.dungeon.DungeonInstance;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages mob templates and active mob instances.
 * Handles loading templates from config, spawning mobs, and tracking active mobs.
 */
public class MobManager {
    private final Map<String, MobTemplate> templates;
    private final Map<UUID, DungeonMob> activeMobs;

    /**
     * Creates a new MobManager.
     */
    public MobManager() {
        this.templates = new ConcurrentHashMap<>();
        this.activeMobs = new ConcurrentHashMap<>();
    }

    /**
     * Loads mob templates from a configuration section.
     *
     * @param config The configuration section containing mob templates
     * @return The number of templates loaded
     */
    public int loadTemplates(ConfigurationSection config) {
        if (config == null) {
            return 0;
        }

        int loaded = 0;
        for (String key : config.getKeys(false)) {
            ConfigurationSection mobConfig = config.getConfigurationSection(key);
            if (mobConfig != null) {
                MobTemplate template = MobTemplate.fromConfig(mobConfig);
                if (template != null) {
                    templates.put(template.getId(), template);
                    loaded++;
                } else {
                    System.err.println("Failed to load mob template: " + key);
                }
            }
        }

        System.out.println("Loaded " + loaded + " mob templates");
        return loaded;
    }

    /**
     * Gets a mob template by its ID.
     *
     * @param templateId The template ID
     * @return The MobTemplate, or null if not found
     */
    public MobTemplate getTemplate(String templateId) {
        return templates.get(templateId);
    }

    /**
     * Checks if a template exists.
     *
     * @param templateId The template ID
     * @return true if the template exists
     */
    public boolean hasTemplate(String templateId) {
        return templates.containsKey(templateId);
    }

    /**
     * Gets all template IDs.
     *
     * @return Map of all templates
     */
    public Map<String, MobTemplate> getAllTemplates() {
        return new ConcurrentHashMap<>(templates);
    }

    /**
     * Spawns a mob from a template at the specified location.
     *
     * @param template The mob template to spawn
     * @param location The location to spawn at
     * @param difficulty The dungeon difficulty for scaling
     * @param dungeonInstanceId The dungeon instance ID
     * @param roomId The room ID where the mob spawns
     * @return The spawned DungeonMob, or null if spawn failed
     */
    public DungeonMob spawnMob(MobTemplate template, Location location, DungeonDifficulty difficulty,
                              UUID dungeonInstanceId, String roomId) {
        if (template == null || location == null || location.getWorld() == null) {
            return null;
        }

        try {
            // Spawn the entity
            EntityType entityType = template.getEntityType();
            Entity entity = location.getWorld().spawnEntity(location, entityType);

            if (!(entity instanceof LivingEntity)) {
                entity.remove();
                System.err.println("Cannot spawn non-living entity: " + entityType);
                return null;
            }

            // Create dungeon mob wrapper
            DungeonMob dungeonMob = new DungeonMob(entity, template, dungeonInstanceId, roomId);

            // Apply scaling
            dungeonMob.applyScaling(difficulty);

            // Apply equipment
            applyEquipment((LivingEntity) entity, template);

            // Register the mob
            activeMobs.put(entity.getUniqueId(), dungeonMob);

            return dungeonMob;
        } catch (Exception e) {
            System.err.println("Error spawning mob: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Spawns a mob by template ID.
     *
     * @param templateId The template ID
     * @param location The location to spawn at
     * @param difficulty The dungeon difficulty
     * @param dungeonInstanceId The dungeon instance ID
     * @param roomId The room ID
     * @return The spawned DungeonMob, or null if spawn failed
     */
    public DungeonMob spawnMob(String templateId, Location location, DungeonDifficulty difficulty,
                              UUID dungeonInstanceId, String roomId) {
        MobTemplate template = getTemplate(templateId);
        if (template == null) {
            System.err.println("Unknown mob template: " + templateId);
            return null;
        }
        return spawnMob(template, location, difficulty, dungeonInstanceId, roomId);
    }

    /**
     * Applies equipment to a living entity from a template.
     *
     * @param entity The entity to equip
     * @param template The mob template with equipment data
     */
    private void applyEquipment(LivingEntity entity, MobTemplate template) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }

        Map<String, ItemStack> equipmentMap = template.getEquipment();
        for (Map.Entry<String, ItemStack> entry : equipmentMap.entrySet()) {
            String slot = entry.getKey().toLowerCase();
            ItemStack item = entry.getValue();

            if (item == null) {
                continue;
            }

            switch (slot) {
                case "helmet":
                case "head":
                    equipment.setHelmet(item);
                    equipment.setHelmetDropChance(0.0f);
                    break;
                case "chestplate":
                case "chest":
                    equipment.setChestplate(item);
                    equipment.setChestplateDropChance(0.0f);
                    break;
                case "leggings":
                case "legs":
                    equipment.setLeggings(item);
                    equipment.setLeggingsDropChance(0.0f);
                    break;
                case "boots":
                case "feet":
                    equipment.setBoots(item);
                    equipment.setBootsDropChance(0.0f);
                    break;
                case "mainhand":
                case "hand":
                    equipment.setItemInMainHand(item);
                    equipment.setItemInMainHandDropChance(0.0f);
                    break;
                case "offhand":
                    equipment.setItemInOffHand(item);
                    equipment.setItemInOffHandDropChance(0.0f);
                    break;
            }
        }
    }

    /**
     * Gets a dungeon mob by entity UUID.
     *
     * @param entityId The entity UUID
     * @return The DungeonMob, or null if not found
     */
    public DungeonMob getMob(UUID entityId) {
        return activeMobs.get(entityId);
    }

    /**
     * Gets a dungeon mob by entity.
     *
     * @param entity The entity
     * @return The DungeonMob, or null if not found
     */
    public DungeonMob getMob(Entity entity) {
        if (entity == null) {
            return null;
        }
        return getMob(entity.getUniqueId());
    }

    /**
     * Checks if an entity is a dungeon mob.
     *
     * @param entityId The entity UUID
     * @return true if the entity is a tracked dungeon mob
     */
    public boolean isDungeonMob(UUID entityId) {
        return activeMobs.containsKey(entityId);
    }

    /**
     * Checks if an entity is a dungeon mob.
     *
     * @param entity The entity
     * @return true if the entity is a tracked dungeon mob
     */
    public boolean isDungeonMob(Entity entity) {
        return entity != null && isDungeonMob(entity.getUniqueId());
    }

    /**
     * Removes a mob from tracking.
     *
     * @param entityId The entity UUID
     * @return The removed DungeonMob, or null if not found
     */
    public DungeonMob removeMob(UUID entityId) {
        return activeMobs.remove(entityId);
    }

    /**
     * Removes a mob from tracking.
     *
     * @param entity The entity
     * @return The removed DungeonMob, or null if not found
     */
    public DungeonMob removeMob(Entity entity) {
        if (entity == null) {
            return null;
        }
        return removeMob(entity.getUniqueId());
    }

    /**
     * Despawns all mobs for a specific dungeon instance.
     *
     * @param instance The dungeon instance
     * @return The number of mobs despawned
     */
    public int despawnAllMobs(DungeonInstance instance) {
        if (instance == null) {
            return 0;
        }
        return despawnAllMobs(instance.getInstanceId());
    }

    /**
     * Despawns all mobs for a specific dungeon instance.
     *
     * @param dungeonInstanceId The dungeon instance ID
     * @return The number of mobs despawned
     */
    public int despawnAllMobs(UUID dungeonInstanceId) {
        int despawned = 0;

        // Find all mobs belonging to this instance
        for (Map.Entry<UUID, DungeonMob> entry : activeMobs.entrySet()) {
            DungeonMob mob = entry.getValue();
            if (mob.getDungeonInstanceId().equals(dungeonInstanceId)) {
                Entity entity = mob.getEntity();
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
                activeMobs.remove(entry.getKey());
                despawned++;
            }
        }

        return despawned;
    }

    /**
     * Gets the number of active mobs.
     *
     * @return The active mob count
     */
    public int getActiveMobCount() {
        return activeMobs.size();
    }

    /**
     * Gets the number of active mobs for a dungeon instance.
     *
     * @param dungeonInstanceId The dungeon instance ID
     * @return The mob count for this instance
     */
    public int getActiveMobCount(UUID dungeonInstanceId) {
        return (int) activeMobs.values().stream()
                .filter(mob -> mob.getDungeonInstanceId().equals(dungeonInstanceId))
                .count();
    }

    /**
     * Cleans up dead or invalid mobs.
     *
     * @return The number of mobs cleaned up
     */
    public int cleanupDeadMobs() {
        int cleaned = 0;
        for (Map.Entry<UUID, DungeonMob> entry : activeMobs.entrySet()) {
            if (entry.getValue().isDead()) {
                activeMobs.remove(entry.getKey());
                cleaned++;
            }
        }
        return cleaned;
    }

    /**
     * Clears all mob data.
     */
    public void clearAll() {
        // Despawn all active mobs
        for (DungeonMob mob : activeMobs.values()) {
            Entity entity = mob.getEntity();
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        activeMobs.clear();
    }

    /**
     * Gets the number of loaded templates.
     *
     * @return The template count
     */
    public int getTemplateCount() {
        return templates.size();
    }
}
