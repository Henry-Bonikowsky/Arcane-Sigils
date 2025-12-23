package com.zenax.dungeons.combat.boss.ability;

import com.zenax.dungeons.combat.boss.BossEntity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a boss ability that can be executed during combat.
 * Abilities have types, cooldowns, and configurable parameters.
 */
public abstract class BossAbility {
    private final String id;
    private final String displayName;
    private final int cooldownTicks;
    private final double damage;
    private final double range;
    private final AbilityType type;
    private final Map<String, Object> params;

    /**
     * Creates a new boss ability.
     *
     * @param id Unique identifier for this ability
     * @param displayName Display name for the ability
     * @param cooldownTicks Cooldown duration in ticks
     * @param damage Base damage dealt by the ability
     * @param range Maximum range of the ability
     * @param type The ability type
     * @param params Additional type-specific parameters
     */
    public BossAbility(String id, String displayName, int cooldownTicks, double damage,
                      double range, AbilityType type, Map<String, Object> params) {
        this.id = id;
        this.displayName = displayName;
        this.cooldownTicks = Math.max(0, cooldownTicks);
        this.damage = damage;
        this.range = range;
        this.type = type;
        this.params = new HashMap<>(params);
    }

    /**
     * Loads a boss ability from a configuration section.
     * Subclasses should override this to create their specific ability instances.
     *
     * @param config The configuration section containing ability data
     * @return The loaded BossAbility, or null if invalid
     */
    public static BossAbility fromConfig(ConfigurationSection config) {
        if (config == null) {
            return null;
        }

        try {
            String id = config.getName();
            String displayName = config.getString("displayName", id);
            int cooldownTicks = config.getInt("cooldown", 100);
            double damage = config.getDouble("damage", 0.0);
            double range = config.getDouble("range", 10.0);

            String typeName = config.getString("type", "PROJECTILE");
            AbilityType type = AbilityType.fromString(typeName);
            if (type == null) {
                System.err.println("Invalid ability type: " + typeName);
                return null;
            }

            // Load additional parameters
            Map<String, Object> params = new HashMap<>();
            ConfigurationSection paramsSection = config.getConfigurationSection("params");
            if (paramsSection != null) {
                for (String key : paramsSection.getKeys(false)) {
                    params.put(key, paramsSection.get(key));
                }
            }

            // Return a generic ability instance - in practice, you'd use a factory
            // to create specific ability implementations based on the type or ID
            return new GenericBossAbility(id, displayName, cooldownTicks, damage, range, type, params);
        } catch (Exception e) {
            System.err.println("Error loading boss ability from config: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Executes this ability.
     *
     * @param boss The boss entity using the ability
     * @param target The target player (can be null for some abilities)
     * @return true if the ability was executed successfully, false otherwise
     */
    public abstract boolean execute(BossEntity boss, Player target);

    /**
     * Checks if this ability can be used on the given target.
     *
     * @param boss The boss entity
     * @param target The target player
     * @return true if the ability can be used, false otherwise
     */
    public boolean canUse(BossEntity boss, Player target) {
        // Basic checks
        if (boss == null || boss.isDead()) {
            return false;
        }

        // Check range if target is specified
        if (target != null && range > 0) {
            if (boss.getEntity() == null || target.getLocation() == null) {
                return false;
            }
            double distance = boss.getEntity().getLocation().distance(target.getLocation());
            if (distance > range) {
                return false;
            }
        }

        return true;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public double getDamage() {
        return damage;
    }

    public double getRange() {
        return range;
    }

    public AbilityType getType() {
        return type;
    }

    public Map<String, Object> getParams() {
        return new HashMap<>(params);
    }

    public Object getParam(String key) {
        return params.get(key);
    }

    public Object getParam(String key, Object defaultValue) {
        return params.getOrDefault(key, defaultValue);
    }

    @Override
    public String toString() {
        return "BossAbility{" +
               "id='" + id + '\'' +
               ", displayName='" + displayName + '\'' +
               ", type=" + type +
               ", cooldown=" + cooldownTicks +
               '}';
    }

    /**
     * Generic implementation for basic abilities.
     * This is used when loading from config before specific implementations are registered.
     */
    private static class GenericBossAbility extends BossAbility {
        public GenericBossAbility(String id, String displayName, int cooldownTicks,
                                 double damage, double range, AbilityType type,
                                 Map<String, Object> params) {
            super(id, displayName, cooldownTicks, damage, range, type, params);
        }

        @Override
        public boolean execute(BossEntity boss, Player target) {
            // Generic implementation - does nothing
            // Specific abilities should override this
            System.out.println("Generic ability executed: " + getId());
            return false;
        }
    }
}
