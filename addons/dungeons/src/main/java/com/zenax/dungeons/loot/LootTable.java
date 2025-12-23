package com.zenax.dungeons.loot;

import com.zenax.dungeons.dungeon.DungeonDifficulty;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Represents a collection of loot entries with roll mechanics.
 * Loot tables determine what items are dropped from dungeon sources.
 */
public class LootTable {
    private final String id;
    private final List<LootEntry> entries;
    private final int minRolls;
    private final int maxRolls;
    private final boolean guaranteedDrop;

    /**
     * Creates a new loot table.
     *
     * @param id Unique identifier for this loot table
     * @param entries List of loot entries
     * @param minRolls Minimum number of items to roll
     * @param maxRolls Maximum number of items to roll
     * @param guaranteedDrop Whether at least one item is guaranteed
     */
    public LootTable(String id, List<LootEntry> entries, int minRolls, int maxRolls, boolean guaranteedDrop) {
        this.id = id;
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        this.minRolls = Math.max(0, minRolls);
        this.maxRolls = Math.max(this.minRolls, maxRolls);
        this.guaranteedDrop = guaranteedDrop;
    }

    /**
     * Gets the ID of this loot table.
     *
     * @return The loot table ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the loot entries in this table.
     *
     * @return The entries list
     */
    public List<LootEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    /**
     * Gets the minimum number of rolls.
     *
     * @return The minimum rolls
     */
    public int getMinRolls() {
        return minRolls;
    }

    /**
     * Gets the maximum number of rolls.
     *
     * @return The maximum rolls
     */
    public int getMaxRolls() {
        return maxRolls;
    }

    /**
     * Checks if this table guarantees at least one drop.
     *
     * @return true if guaranteed drop is enabled
     */
    public boolean hasGuaranteedDrop() {
        return guaranteedDrop;
    }

    /**
     * Generates loot from this table.
     *
     * @param random Random instance for generation
     * @param luckModifier Luck modifier affecting drop rates
     * @param difficulty Dungeon difficulty for condition checking
     * @return List of generated ItemStacks
     */
    public List<ItemStack> generateLoot(Random random, double luckModifier, DungeonDifficulty difficulty) {
        List<ItemStack> loot = new ArrayList<>();

        if (entries.isEmpty()) {
            return loot;
        }

        // Filter entries by conditions
        List<LootEntry> validEntries = new ArrayList<>();
        for (LootEntry entry : entries) {
            if (entry.meetsConditions(difficulty)) {
                validEntries.add(entry);
            }
        }

        if (validEntries.isEmpty()) {
            return loot;
        }

        // Determine number of rolls
        int rolls = minRolls;
        if (maxRolls > minRolls) {
            rolls = minRolls + random.nextInt(maxRolls - minRolls + 1);
        }

        // Apply luck to increase rolls slightly
        if (luckModifier > 1.0 && random.nextDouble() < (luckModifier - 1.0) * 0.5) {
            rolls++;
        }

        // Perform rolls
        for (int i = 0; i < rolls; i++) {
            LootEntry selectedEntry = selectEntry(validEntries, random, luckModifier);
            if (selectedEntry != null) {
                ItemStack item = selectedEntry.generateItem(random, luckModifier);
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    loot.add(item);
                }
            }
        }

        // Guarantee at least one item if enabled
        if (guaranteedDrop && loot.isEmpty() && !validEntries.isEmpty()) {
            LootEntry fallbackEntry = selectEntry(validEntries, random, luckModifier);
            if (fallbackEntry != null) {
                ItemStack item = fallbackEntry.generateItem(random, luckModifier);
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    loot.add(item);
                }
            }
        }

        return loot;
    }

    /**
     * Selects a random entry based on weights and luck.
     *
     * @param entries List of valid entries
     * @param random Random instance
     * @param luckModifier Luck modifier
     * @return The selected entry, or null if none
     */
    private LootEntry selectEntry(List<LootEntry> entries, Random random, double luckModifier) {
        if (entries.isEmpty()) {
            return null;
        }

        // Calculate total weight
        double totalWeight = 0;
        for (LootEntry entry : entries) {
            totalWeight += entry.getEffectiveWeight(luckModifier);
        }

        if (totalWeight <= 0) {
            return entries.get(random.nextInt(entries.size()));
        }

        // Weighted random selection
        double roll = random.nextDouble() * totalWeight;
        double currentWeight = 0;

        for (LootEntry entry : entries) {
            currentWeight += entry.getEffectiveWeight(luckModifier);
            if (roll <= currentWeight) {
                return entry;
            }
        }

        // Fallback (should rarely happen due to floating point)
        return entries.get(entries.size() - 1);
    }

    /**
     * Creates a LootTable from a configuration section.
     *
     * @param id The loot table ID
     * @param section The configuration section
     * @return The created LootTable, or null if invalid
     */
    public static LootTable fromConfig(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        try {
            int minRolls = section.getInt("min-rolls", 1);
            int maxRolls = section.getInt("max-rolls", minRolls);
            boolean guaranteedDrop = section.getBoolean("guaranteed-drop", true);

            List<LootEntry> entries = new ArrayList<>();
            ConfigurationSection entriesSection = section.getConfigurationSection("entries");
            if (entriesSection != null) {
                for (String key : entriesSection.getKeys(false)) {
                    ConfigurationSection entrySection = entriesSection.getConfigurationSection(key);
                    LootEntry entry = LootEntry.fromConfig(entrySection);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }

            return new LootTable(id, entries, minRolls, maxRolls, guaranteedDrop);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the total number of entries in this table.
     *
     * @return The entry count
     */
    public int getEntryCount() {
        return entries.size();
    }

    /**
     * Gets entries matching a specific rarity.
     *
     * @param rarity The rarity to filter by
     * @return List of matching entries
     */
    public List<LootEntry> getEntriesByRarity(LootRarity rarity) {
        List<LootEntry> matching = new ArrayList<>();
        for (LootEntry entry : entries) {
            if (entry.getRarity() == rarity) {
                matching.add(entry);
            }
        }
        return matching;
    }

    /**
     * Checks if this table contains any entries of the given rarity.
     *
     * @param rarity The rarity to check
     * @return true if entries of this rarity exist
     */
    public boolean hasRarity(LootRarity rarity) {
        for (LootEntry entry : entries) {
            if (entry.getRarity() == rarity) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "LootTable{" +
               "id='" + id + '\'' +
               ", entries=" + entries.size() +
               ", rolls=" + minRolls + "-" + maxRolls +
               ", guaranteed=" + guaranteedDrop +
               '}';
    }
}
