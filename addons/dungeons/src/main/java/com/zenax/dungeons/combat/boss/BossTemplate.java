package com.zenax.dungeons.combat.boss;

import com.zenax.dungeons.combat.boss.ability.BossAbility;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.*;

/**
 * Represents a boss template loaded from configuration.
 * Templates define the complete boss definition including stats, phases, and abilities.
 */
public class BossTemplate {
    private final String bossId;
    private final String displayName;
    private final EntityType entityType;
    private final double baseHealth;
    private final double baseDamage;
    private final double baseDefense;
    private final double baseSpeed;
    private final List<BossPhase> phases;
    private final Map<String, BossAbility> abilities;
    private final BarColor bossBarColor;
    private final BarStyle bossBarStyle;
    private final String lootTableId;

    /**
     * Creates a new boss template.
     *
     * @param bossId Unique identifier for this boss
     * @param displayName Display name for the boss
     * @param entityType The Bukkit entity type
     * @param baseHealth Base health points
     * @param baseDamage Base damage output
     * @param baseDefense Base defense value
     * @param baseSpeed Movement speed modifier
     * @param phases List of boss phases
     * @param abilities Map of abilities by ID
     * @param bossBarColor BossBar color
     * @param bossBarStyle BossBar style
     * @param lootTableId ID of the loot table for drops
     */
    public BossTemplate(String bossId, String displayName, EntityType entityType,
                       double baseHealth, double baseDamage, double baseDefense, double baseSpeed,
                       List<BossPhase> phases, Map<String, BossAbility> abilities,
                       BarColor bossBarColor, BarStyle bossBarStyle, String lootTableId) {
        this.bossId = bossId;
        this.displayName = displayName;
        this.entityType = entityType;
        this.baseHealth = baseHealth;
        this.baseDamage = baseDamage;
        this.baseDefense = baseDefense;
        this.baseSpeed = baseSpeed;
        this.phases = new ArrayList<>(phases);
        this.abilities = new HashMap<>(abilities);
        this.bossBarColor = bossBarColor;
        this.bossBarStyle = bossBarStyle;
        this.lootTableId = lootTableId;

        // Sort phases by health threshold (descending)
        this.phases.sort((p1, p2) -> Double.compare(p2.getHealthThreshold(), p1.getHealthThreshold()));
    }

    /**
     * Loads a boss template from a configuration section.
     *
     * @param config The configuration section containing boss data
     * @return The loaded BossTemplate, or null if invalid
     */
    public static BossTemplate fromConfig(ConfigurationSection config) {
        if (config == null) {
            return null;
        }

        try {
            String bossId = config.getName();
            String displayName = config.getString("displayName", bossId);

            // Parse entity type
            String entityTypeName = config.getString("entityType", "WITHER_SKELETON");
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityTypeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid entity type: " + entityTypeName);
                return null;
            }

            // Parse stats
            double baseHealth = config.getDouble("baseHealth", 500.0);
            double baseDamage = config.getDouble("baseDamage", 20.0);
            double baseDefense = config.getDouble("baseDefense", 10.0);
            double baseSpeed = config.getDouble("baseSpeed", 1.0);

            // Parse phases
            List<BossPhase> phases = new ArrayList<>();
            ConfigurationSection phasesSection = config.getConfigurationSection("phases");
            if (phasesSection != null) {
                for (String phaseKey : phasesSection.getKeys(false)) {
                    ConfigurationSection phaseSection = phasesSection.getConfigurationSection(phaseKey);
                    BossPhase phase = BossPhase.fromConfig(phaseSection);
                    if (phase != null) {
                        phases.add(phase);
                    }
                }
            }

            // If no phases defined, create a default phase
            if (phases.isEmpty()) {
                phases.add(new BossPhase(1, 1.0, new ArrayList<>(), null, 0, false));
            }

            // Parse abilities
            Map<String, BossAbility> abilities = new HashMap<>();
            ConfigurationSection abilitiesSection = config.getConfigurationSection("abilities");
            if (abilitiesSection != null) {
                for (String abilityKey : abilitiesSection.getKeys(false)) {
                    ConfigurationSection abilitySection = abilitiesSection.getConfigurationSection(abilityKey);
                    BossAbility ability = BossAbility.fromConfig(abilitySection);
                    if (ability != null) {
                        abilities.put(ability.getId(), ability);
                    }
                }
            }

            // Parse boss bar settings
            String barColorName = config.getString("bossBar.color", "RED");
            BarColor bossBarColor;
            try {
                bossBarColor = BarColor.valueOf(barColorName.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid boss bar color: " + barColorName);
                bossBarColor = BarColor.RED;
            }

            String barStyleName = config.getString("bossBar.style", "SOLID");
            BarStyle bossBarStyle;
            try {
                bossBarStyle = BarStyle.valueOf(barStyleName.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid boss bar style: " + barStyleName);
                bossBarStyle = BarStyle.SOLID;
            }

            // Parse loot table
            String lootTableId = config.getString("lootTable", "default_boss");

            return new BossTemplate(bossId, displayName, entityType, baseHealth, baseDamage,
                                   baseDefense, baseSpeed, phases, abilities,
                                   bossBarColor, bossBarStyle, lootTableId);
        } catch (Exception e) {
            System.err.println("Error loading boss template from config: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets the phase that should be active for the given health percentage.
     *
     * @param healthPercent Current health percentage (0.0-1.0)
     * @return The active phase, or the first phase if none match
     */
    public BossPhase getPhaseForHealth(double healthPercent) {
        for (BossPhase phase : phases) {
            if (phase.shouldActivate(healthPercent)) {
                return phase;
            }
        }
        // Return first phase as fallback
        return phases.isEmpty() ? null : phases.get(0);
    }

    /**
     * Gets an ability by its ID.
     *
     * @param abilityId The ability ID
     * @return The ability, or null if not found
     */
    public BossAbility getAbility(String abilityId) {
        return abilities.get(abilityId);
    }

    /**
     * Gets all abilities available in a specific phase.
     *
     * @param phase The boss phase
     * @return List of abilities for that phase
     */
    public List<BossAbility> getAbilitiesForPhase(BossPhase phase) {
        List<BossAbility> phaseAbilities = new ArrayList<>();
        if (phase != null) {
            for (String abilityId : phase.getAbilityIds()) {
                BossAbility ability = abilities.get(abilityId);
                if (ability != null) {
                    phaseAbilities.add(ability);
                }
            }
        }
        return phaseAbilities;
    }

    public String getBossId() {
        return bossId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public double getBaseHealth() {
        return baseHealth;
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public double getBaseDefense() {
        return baseDefense;
    }

    public double getBaseSpeed() {
        return baseSpeed;
    }

    public List<BossPhase> getPhases() {
        return new ArrayList<>(phases);
    }

    public Map<String, BossAbility> getAbilities() {
        return new HashMap<>(abilities);
    }

    public BarColor getBossBarColor() {
        return bossBarColor;
    }

    public BarStyle getBossBarStyle() {
        return bossBarStyle;
    }

    public String getLootTableId() {
        return lootTableId;
    }

    @Override
    public String toString() {
        return "BossTemplate{" +
               "bossId='" + bossId + '\'' +
               ", displayName='" + displayName + '\'' +
               ", entityType=" + entityType +
               ", phases=" + phases.size() +
               ", abilities=" + abilities.size() +
               '}';
    }
}
