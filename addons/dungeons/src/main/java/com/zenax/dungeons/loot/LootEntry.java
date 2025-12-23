package com.zenax.dungeons.loot;

import com.zenax.dungeons.dungeon.DungeonDifficulty;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Represents a single loot entry in a loot table.
 * Each entry defines an item that can be dropped with specific conditions.
 */
public class LootEntry {
    private final Material material;
    private final int minAmount;
    private final int maxAmount;
    private final double weight;
    private final LootRarity rarity;
    private final List<String> conditions;
    private final String displayName;
    private final List<String> lore;

    /**
     * Creates a new loot entry.
     *
     * @param material The material type
     * @param minAmount Minimum amount to drop
     * @param maxAmount Maximum amount to drop
     * @param weight Selection weight (higher = more common)
     * @param rarity The rarity of this loot
     * @param conditions List of conditions that must be met
     * @param displayName Optional custom display name
     * @param lore Optional lore lines
     */
    public LootEntry(Material material, int minAmount, int maxAmount, double weight,
                     LootRarity rarity, List<String> conditions, String displayName, List<String> lore) {
        this.material = material;
        this.minAmount = Math.max(1, minAmount);
        this.maxAmount = Math.max(this.minAmount, maxAmount);
        this.weight = Math.max(0.1, weight);
        this.rarity = rarity != null ? rarity : LootRarity.COMMON;
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
        this.displayName = displayName;
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
    }

    /**
     * Gets the material type for this loot.
     *
     * @return The material
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * Gets the minimum amount.
     *
     * @return The minimum amount
     */
    public int getMinAmount() {
        return minAmount;
    }

    /**
     * Gets the maximum amount.
     *
     * @return The maximum amount
     */
    public int getMaxAmount() {
        return maxAmount;
    }

    /**
     * Gets the selection weight.
     *
     * @return The weight
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Gets the rarity of this loot.
     *
     * @return The rarity
     */
    public LootRarity getRarity() {
        return rarity;
    }

    /**
     * Gets the conditions for this loot.
     *
     * @return The conditions list
     */
    public List<String> getConditions() {
        return new ArrayList<>(conditions);
    }

    /**
     * Checks if the conditions for this loot are met.
     *
     * @param difficulty The dungeon difficulty
     * @return true if conditions are met
     */
    public boolean meetsConditions(DungeonDifficulty difficulty) {
        if (conditions.isEmpty()) {
            return true;
        }

        for (String condition : conditions) {
            if (!evaluateCondition(condition, difficulty)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluates a single condition.
     *
     * @param condition The condition string
     * @param difficulty The dungeon difficulty
     * @return true if the condition is met
     */
    private boolean evaluateCondition(String condition, DungeonDifficulty difficulty) {
        condition = condition.trim().toLowerCase();

        // Format: "difficulty>=HARD", "difficulty=NIGHTMARE", etc.
        if (condition.startsWith("difficulty")) {
            String[] parts = condition.split("[>=<]+");
            if (parts.length < 2) {
                return true;
            }

            String operator = condition.replaceAll("[^>=<]+", "");
            String targetDifficultyStr = parts[1].trim().toUpperCase();
            DungeonDifficulty targetDifficulty = DungeonDifficulty.fromString(targetDifficultyStr);

            if (targetDifficulty == null) {
                return true;
            }

            switch (operator) {
                case ">=":
                    return difficulty.ordinal() >= targetDifficulty.ordinal();
                case "<=":
                    return difficulty.ordinal() <= targetDifficulty.ordinal();
                case ">":
                    return difficulty.ordinal() > targetDifficulty.ordinal();
                case "<":
                    return difficulty.ordinal() < targetDifficulty.ordinal();
                case "=":
                case "==":
                    return difficulty == targetDifficulty;
                case "!=":
                    return difficulty != targetDifficulty;
                default:
                    return true;
            }
        }

        return true;
    }

    /**
     * Generates an ItemStack from this loot entry.
     *
     * @param random Random instance for amount calculation
     * @param luckModifier Luck modifier affecting amount
     * @return The generated ItemStack
     */
    public ItemStack generateItem(Random random, double luckModifier) {
        int amount = minAmount;
        if (maxAmount > minAmount) {
            amount = minAmount + random.nextInt(maxAmount - minAmount + 1);
        }

        // Apply luck modifier to amount (small chance to increase)
        if (luckModifier > 1.0 && random.nextDouble() < (luckModifier - 1.0)) {
            amount = (int) Math.ceil(amount * luckModifier);
        }

        amount = Math.min(material.getMaxStackSize(), amount);
        ItemStack item = new ItemStack(material, amount);

        // Apply custom name and lore if present
        if (displayName != null || !lore.isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (displayName != null) {
                    meta.setDisplayName(rarity.getColorCode() + displayName);
                }
                if (!lore.isEmpty()) {
                    List<String> coloredLore = new ArrayList<>();
                    for (String loreLine : lore) {
                        coloredLore.add(org.bukkit.ChatColor.GRAY + loreLine);
                    }
                    meta.setLore(coloredLore);
                }
                item.setItemMeta(meta);
            }
        }

        return item;
    }

    /**
     * Creates a LootEntry from a configuration section.
     *
     * @param section The configuration section
     * @return The created LootEntry, or null if invalid
     */
    public static LootEntry fromConfig(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        try {
            String materialStr = section.getString("material", "DIRT");
            Material material = Material.matchMaterial(materialStr);
            if (material == null) {
                material = Material.DIRT;
            }

            int minAmount = section.getInt("min-amount", 1);
            int maxAmount = section.getInt("max-amount", minAmount);
            double weight = section.getDouble("weight", 1.0);
            LootRarity rarity = LootRarity.fromString(section.getString("rarity", "COMMON"));
            List<String> conditions = section.getStringList("conditions");
            String displayName = section.getString("display-name");
            List<String> lore = section.getStringList("lore");

            return new LootEntry(material, minAmount, maxAmount, weight, rarity, conditions, displayName, lore);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the effective weight with luck modifier applied.
     *
     * @param luckModifier The luck modifier
     * @return The modified weight
     */
    public double getEffectiveWeight(double luckModifier) {
        // Higher rarity items benefit more from luck
        double rarityBonus = 1.0 + (rarity.ordinal() * 0.1);
        return weight * (1.0 + (luckModifier - 1.0) * rarityBonus);
    }

    @Override
    public String toString() {
        return "LootEntry{" +
               "material=" + material +
               ", amount=" + minAmount + "-" + maxAmount +
               ", weight=" + weight +
               ", rarity=" + rarity +
               '}';
    }
}
