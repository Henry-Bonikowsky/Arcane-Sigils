package com.zenax.dungeons.combat.boss;

import com.zenax.dungeons.dungeon.DungeonDifficulty;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all boss templates and active boss instances.
 * Handles loading, spawning, and tracking of bosses.
 */
public class BossManager {
    private final Map<String, BossTemplate> templates;
    private final Map<UUID, BossEntity> activeBosses;

    /**
     * Creates a new boss manager.
     */
    public BossManager() {
        this.templates = new ConcurrentHashMap<>();
        this.activeBosses = new ConcurrentHashMap<>();
    }

    /**
     * Loads all boss templates from a configuration file.
     *
     * @param config The configuration containing boss definitions
     * @return The number of templates loaded
     */
    public int loadBossTemplates(FileConfiguration config) {
        templates.clear();

        if (config == null) {
            return 0;
        }

        ConfigurationSection bossesSection = config.getConfigurationSection("bosses");
        if (bossesSection == null) {
            System.out.println("No bosses section found in configuration");
            return 0;
        }

        int loaded = 0;
        for (String bossKey : bossesSection.getKeys(false)) {
            ConfigurationSection bossSection = bossesSection.getConfigurationSection(bossKey);
            BossTemplate template = BossTemplate.fromConfig(bossSection);

            if (template != null) {
                templates.put(template.getBossId(), template);
                loaded++;
                System.out.println("Loaded boss template: " + template.getBossId());
            } else {
                System.err.println("Failed to load boss template: " + bossKey);
            }
        }

        System.out.println("Loaded " + loaded + " boss templates");
        return loaded;
    }

    /**
     * Spawns a boss at the specified location.
     *
     * @param bossId The boss template ID
     * @param location The spawn location
     * @param difficulty The dungeon difficulty (affects scaling)
     * @return The spawned BossEntity, or null if failed
     */
    public BossEntity spawnBoss(String bossId, Location location, DungeonDifficulty difficulty) {
        BossTemplate template = templates.get(bossId);
        if (template == null) {
            System.err.println("Boss template not found: " + bossId);
            return null;
        }

        if (location == null || location.getWorld() == null) {
            System.err.println("Invalid spawn location for boss: " + bossId);
            return null;
        }

        try {
            // Spawn the entity
            EntityType entityType = template.getEntityType();
            Entity entity = location.getWorld().spawnEntity(location, entityType);

            // Create boss entity
            BossEntity bossEntity = new BossEntity(entity, template);

            // Initialize with difficulty scaling
            double difficultyMultiplier = difficulty != null ? difficulty.getMobMultiplier() : 1.0;
            bossEntity.initialize(difficultyMultiplier);

            // Register the boss
            activeBosses.put(entity.getUniqueId(), bossEntity);

            System.out.println("Spawned boss: " + bossId + " at " + location);
            return bossEntity;
        } catch (Exception e) {
            System.err.println("Error spawning boss " + bossId + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets an active boss by entity UUID.
     *
     * @param entityId The entity UUID
     * @return The BossEntity, or null if not found
     */
    public BossEntity getBoss(UUID entityId) {
        return activeBosses.get(entityId);
    }

    /**
     * Gets a boss by its entity.
     *
     * @param entity The entity
     * @return The BossEntity, or null if not found
     */
    public BossEntity getBoss(Entity entity) {
        if (entity == null) {
            return null;
        }
        return activeBosses.get(entity.getUniqueId());
    }

    /**
     * Removes a boss from the active list.
     *
     * @param entityId The entity UUID
     * @return true if a boss was removed
     */
    public boolean removeBoss(UUID entityId) {
        BossEntity boss = activeBosses.remove(entityId);
        if (boss != null) {
            boss.setDead();
            return true;
        }
        return false;
    }

    /**
     * Gets a boss template by ID.
     *
     * @param bossId The boss ID
     * @return The template, or null if not found
     */
    public BossTemplate getTemplate(String bossId) {
        return templates.get(bossId);
    }

    /**
     * Gets all boss template IDs.
     *
     * @return Set of boss template IDs
     */
    public Set<String> getBossIds() {
        return new HashSet<>(templates.keySet());
    }

    /**
     * Gets all active bosses.
     *
     * @return Collection of active boss entities
     */
    public Collection<BossEntity> getActiveBosses() {
        return new ArrayList<>(activeBosses.values());
    }

    /**
     * Ticks all active bosses.
     * Should be called periodically (e.g., every tick or every few ticks).
     */
    public void tickBosses() {
        Iterator<Map.Entry<UUID, BossEntity>> iterator = activeBosses.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BossEntity> entry = iterator.next();
            BossEntity boss = entry.getValue();

            // Check if boss is still valid
            if (boss.isDead()) {
                boss.setDead();
                iterator.remove();
                continue;
            }

            // Tick the boss
            try {
                boss.tick();
            } catch (Exception e) {
                System.err.println("Error ticking boss " + boss.getBossId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Handles phase transition for a boss.
     * Called when a boss should transition to a new phase.
     *
     * @param boss The boss entity
     */
    public void handlePhaseTransition(BossEntity boss) {
        if (boss == null || boss.isDead()) {
            return;
        }

        try {
            BossPhase currentPhase = boss.getCurrentPhase();
            if (currentPhase == null) {
                return;
            }

            // The getCurrentPhase method already handles phase transitions
            // This method can be used to add additional effects or logic

            System.out.println("Boss " + boss.getBossId() + " transitioned to phase " +
                             currentPhase.getPhaseNumber());
        } catch (Exception e) {
            System.err.println("Error handling phase transition for boss " + boss.getBossId() +
                             ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clears all active bosses and removes their boss bars.
     */
    public void clearAllBosses() {
        for (BossEntity boss : activeBosses.values()) {
            boss.setDead();
        }
        activeBosses.clear();
    }

    /**
     * Gets the number of active bosses.
     *
     * @return The count of active bosses
     */
    public int getActiveBossCount() {
        return activeBosses.size();
    }

    /**
     * Gets the number of loaded templates.
     *
     * @return The count of loaded templates
     */
    public int getTemplateCount() {
        return templates.size();
    }

    /**
     * Checks if a boss template exists.
     *
     * @param bossId The boss ID
     * @return true if the template exists
     */
    public boolean hasTemplate(String bossId) {
        return templates.containsKey(bossId);
    }

    /**
     * Checks if an entity is a boss.
     *
     * @param entity The entity to check
     * @return true if the entity is a registered boss
     */
    public boolean isBoss(Entity entity) {
        if (entity == null) {
            return false;
        }
        return activeBosses.containsKey(entity.getUniqueId());
    }
}
