package com.zenax.dungeons.combat;

import com.zenax.dungeons.dungeon.DungeonDifficulty;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.UUID;

/**
 * Represents an active instance of a mob spawned in a dungeon.
 * Tracks the mob's state, scaling, and relationship to the dungeon instance.
 */
public class DungeonMob {
    private final UUID entityId;
    private final MobTemplate template;
    private final MobTier tier;
    private final UUID dungeonInstanceId;
    private final String roomId;
    private double scaledHealth;
    private double scaledDamage;
    private final Entity entity;
    private boolean isDead;

    /**
     * Creates a new dungeon mob instance.
     *
     * @param entity The actual Bukkit entity
     * @param template The mob template this is based on
     * @param dungeonInstanceId The dungeon instance this mob belongs to
     * @param roomId The room this mob spawned in
     */
    public DungeonMob(Entity entity, MobTemplate template, UUID dungeonInstanceId, String roomId) {
        this.entityId = entity.getUniqueId();
        this.template = template;
        this.tier = template.getTier();
        this.dungeonInstanceId = dungeonInstanceId;
        this.roomId = roomId;
        this.entity = entity;
        this.isDead = false;

        // Initialize with base stats
        this.scaledHealth = template.getBaseHealth();
        this.scaledDamage = template.getBaseDamage();
    }

    /**
     * Applies difficulty and tier scaling to the mob's stats.
     *
     * @param difficulty The dungeon difficulty
     */
    public void applyScaling(DungeonDifficulty difficulty) {
        // Apply tier multipliers
        double tierHealthMultiplier = tier.getHealthMultiplier();
        double tierDamageMultiplier = tier.getDamageMultiplier();

        // Apply difficulty multipliers
        double difficultyMultiplier = difficulty.getMobMultiplier();

        // Calculate final scaled values
        this.scaledHealth = template.getBaseHealth() * tierHealthMultiplier * difficultyMultiplier;
        this.scaledDamage = template.getBaseDamage() * tierDamageMultiplier * difficultyMultiplier;

        // Apply to entity if it's a LivingEntity
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            living.setMaxHealth(scaledHealth);
            living.setHealth(scaledHealth);

            // Set custom name
            living.setCustomName(template.getFormattedName());
            living.setCustomNameVisible(true);

            // Apply speed modifier
            double speedModifier = template.getBaseSpeed();
            if (speedModifier != 1.0) {
                // Speed attribute modification would go here if needed
                // living.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(baseSpeed * speedModifier);
            }
        }
    }

    /**
     * Marks this mob as dead.
     */
    public void setDead() {
        this.isDead = true;
    }

    /**
     * Checks if this mob is dead.
     *
     * @return true if the mob is dead or the entity is invalid
     */
    public boolean isDead() {
        if (isDead) {
            return true;
        }
        if (entity == null || !entity.isValid()) {
            isDead = true;
            return true;
        }
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            if (living.isDead() || living.getHealth() <= 0) {
                isDead = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the Bukkit entity.
     *
     * @return The entity, or null if invalid
     */
    public Entity getEntity() {
        if (entity != null && entity.isValid()) {
            return entity;
        }
        return null;
    }

    /**
     * Gets the living entity if this is a LivingEntity.
     *
     * @return The LivingEntity, or null if not applicable
     */
    public LivingEntity getLivingEntity() {
        if (entity instanceof LivingEntity && entity.isValid()) {
            return (LivingEntity) entity;
        }
        return null;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public MobTemplate getTemplate() {
        return template;
    }

    public MobTier getTier() {
        return tier;
    }

    public UUID getDungeonInstanceId() {
        return dungeonInstanceId;
    }

    public String getRoomId() {
        return roomId;
    }

    public double getScaledHealth() {
        return scaledHealth;
    }

    public double getScaledDamage() {
        return scaledDamage;
    }

    /**
     * Gets the current health of the mob.
     *
     * @return Current health, or 0 if dead or invalid
     */
    public double getCurrentHealth() {
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            return living.getHealth();
        }
        return 0.0;
    }

    /**
     * Gets the health percentage (0.0 to 1.0).
     *
     * @return Health percentage
     */
    public double getHealthPercentage() {
        if (scaledHealth <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, getCurrentHealth() / scaledHealth));
    }

    @Override
    public String toString() {
        return "DungeonMob{" +
               "entityId=" + entityId +
               ", template=" + template.getId() +
               ", tier=" + tier +
               ", health=" + getCurrentHealth() + "/" + scaledHealth +
               ", isDead=" + isDead +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DungeonMob that = (DungeonMob) o;
        return entityId.equals(that.entityId);
    }

    @Override
    public int hashCode() {
        return entityId.hashCode();
    }
}
