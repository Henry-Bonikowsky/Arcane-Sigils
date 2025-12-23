package com.zenax.dungeons.stats;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Holds stat values and modifiers for a player or entity.
 * Manages both base stats and dynamic modifiers.
 */
public class StatProfile {
    private final Map<Stat, Double> baseStats;
    private final List<StatModifier> modifiers;

    /**
     * Creates a new StatProfile with default values for all stats.
     */
    public StatProfile() {
        this.baseStats = new ConcurrentHashMap<>();
        this.modifiers = new CopyOnWriteArrayList<>();

        // Initialize all stats with their default values
        for (Stat stat : Stat.values()) {
            baseStats.put(stat, stat.getDefaultValue());
        }
    }

    /**
     * Gets the base value of a stat (without modifiers).
     *
     * @param stat The stat to query
     * @return The base value
     */
    public double getBaseStat(Stat stat) {
        return baseStats.getOrDefault(stat, stat.getDefaultValue());
    }

    /**
     * Sets the base value of a stat.
     *
     * @param stat The stat to set
     * @param value The new base value
     */
    public void setBaseStat(Stat stat, double value) {
        baseStats.put(stat, value);
    }

    /**
     * Gets the effective value of a stat (base + all modifiers).
     * Applies flat modifiers first, then percentage modifiers.
     *
     * @param stat The stat to calculate
     * @return The effective value after all modifiers
     */
    public double getEffectiveStat(Stat stat) {
        double base = getBaseStat(stat);
        double flatBonus = 0.0;
        double percentBonus = 0.0;

        // Clear expired modifiers before calculation
        clearExpiredModifiers();

        // Sum up all modifiers for this stat
        for (StatModifier modifier : modifiers) {
            if (modifier.getStat() == stat) {
                if (modifier.getType() == ModifierType.FLAT) {
                    flatBonus += modifier.getValue();
                } else if (modifier.getType() == ModifierType.PERCENT) {
                    percentBonus += modifier.getValue();
                }
            }
        }

        // Apply flat modifiers first, then percentage
        double result = base + flatBonus;
        result = result * (1.0 + percentBonus);

        return Math.max(0, result); // Ensure non-negative result
    }

    /**
     * Gets all effective stat values.
     *
     * @return A map of all stats to their effective values
     */
    public Map<Stat, Double> getAllEffectiveStats() {
        Map<Stat, Double> result = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            result.put(stat, getEffectiveStat(stat));
        }
        return result;
    }

    /**
     * Adds a modifier to this profile.
     *
     * @param modifier The modifier to add
     */
    public void addModifier(StatModifier modifier) {
        if (modifier != null) {
            modifiers.add(modifier);
        }
    }

    /**
     * Removes a modifier by its ID.
     *
     * @param modifierId The ID of the modifier to remove
     * @return true if a modifier was removed, false otherwise
     */
    public boolean removeModifier(String modifierId) {
        return modifiers.removeIf(m -> m.getId().equals(modifierId));
    }

    /**
     * Removes all modifiers from a specific source.
     *
     * @param source The source to match
     * @return The number of modifiers removed
     */
    public int removeModifiersBySource(String source) {
        int removed = 0;
        Iterator<StatModifier> iterator = modifiers.iterator();
        while (iterator.hasNext()) {
            StatModifier modifier = iterator.next();
            if (modifier.getSource().equals(source)) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Removes all modifiers for a specific stat.
     *
     * @param stat The stat to match
     * @return The number of modifiers removed
     */
    public int removeModifiersByStat(Stat stat) {
        int removed = 0;
        Iterator<StatModifier> iterator = modifiers.iterator();
        while (iterator.hasNext()) {
            StatModifier modifier = iterator.next();
            if (modifier.getStat() == stat) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Clears all expired modifiers from this profile.
     *
     * @return The number of modifiers removed
     */
    public int clearExpiredModifiers() {
        int removed = 0;
        Iterator<StatModifier> iterator = modifiers.iterator();
        while (iterator.hasNext()) {
            StatModifier modifier = iterator.next();
            if (modifier.isExpired()) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Clears all modifiers from this profile.
     */
    public void clearAllModifiers() {
        modifiers.clear();
    }

    /**
     * Gets all active modifiers for a specific stat.
     *
     * @param stat The stat to query
     * @return A list of active modifiers for the stat
     */
    public List<StatModifier> getModifiers(Stat stat) {
        clearExpiredModifiers();
        return modifiers.stream()
                .filter(m -> m.getStat() == stat)
                .collect(Collectors.toList());
    }

    /**
     * Gets all active modifiers.
     *
     * @return A list of all active modifiers
     */
    public List<StatModifier> getAllModifiers() {
        clearExpiredModifiers();
        return new ArrayList<>(modifiers);
    }

    /**
     * Gets the number of active modifiers.
     *
     * @return The modifier count
     */
    public int getModifierCount() {
        clearExpiredModifiers();
        return modifiers.size();
    }

    /**
     * Checks if this profile has any modifiers for a specific stat.
     *
     * @param stat The stat to check
     * @return true if there are modifiers for the stat, false otherwise
     */
    public boolean hasModifiers(Stat stat) {
        clearExpiredModifiers();
        return modifiers.stream().anyMatch(m -> m.getStat() == stat);
    }

    /**
     * Resets all stats to their default values and clears all modifiers.
     */
    public void reset() {
        for (Stat stat : Stat.values()) {
            baseStats.put(stat, stat.getDefaultValue());
        }
        modifiers.clear();
    }

    /**
     * Creates a copy of this StatProfile.
     *
     * @return A new StatProfile with the same values
     */
    public StatProfile copy() {
        StatProfile copy = new StatProfile();
        for (Map.Entry<Stat, Double> entry : baseStats.entrySet()) {
            copy.setBaseStat(entry.getKey(), entry.getValue());
        }
        for (StatModifier modifier : modifiers) {
            copy.addModifier(modifier);
        }
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("StatProfile{\n");
        for (Stat stat : Stat.values()) {
            sb.append("  ").append(stat.getDisplayName()).append(": ")
              .append(getBaseStat(stat)).append(" -> ")
              .append(getEffectiveStat(stat)).append("\n");
        }
        sb.append("  Active Modifiers: ").append(getModifierCount()).append("\n");
        sb.append("}");
        return sb.toString();
    }
}
