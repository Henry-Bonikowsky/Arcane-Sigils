package com.zenax.dungeons.combat;

import com.zenax.dungeons.dungeon.DungeonDifficulty;
import com.zenax.dungeons.stats.StatManager;
import org.bukkit.entity.Player;

import java.util.Random;

/**
 * Static utility class for calculating damage in dungeon combat.
 * Handles player vs mob and mob vs player damage calculations with stat modifiers.
 */
public class DamageCalculator {
    private static final Random RANDOM = new Random();
    private static final double CRIT_MULTIPLIER = 1.5;

    // Private constructor to prevent instantiation
    private DamageCalculator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Calculates damage dealt by a player to a mob.
     * Applies strength stat and critical hit chance.
     *
     * @param player The attacking player
     * @param mob The target mob
     * @param baseDamage The base damage before modifiers
     * @param statManager The stat manager for stat lookups
     * @return The final calculated damage
     */
    public static double calculatePlayerDamage(Player player, DungeonMob mob, double baseDamage, StatManager statManager) {
        if (player == null || mob == null || statManager == null) {
            return baseDamage;
        }

        // Apply player's strength stat
        double damage = statManager.calculateDamage(player, baseDamage);

        // Check for critical hit
        double critChance = statManager.calculateCritChance(player);
        if (rollCritical(critChance)) {
            damage = applyCritical(damage, CRIT_MULTIPLIER);
        }

        // Ensure non-negative damage
        return Math.max(0, damage);
    }

    /**
     * Calculates damage dealt by a mob to a player.
     * Applies mob's scaled damage and player's defense.
     *
     * @param mob The attacking mob
     * @param player The target player
     * @param baseDamage The base damage (typically mob's scaled damage)
     * @param statManager The stat manager for stat lookups
     * @return The final calculated damage after defense reduction
     */
    public static double calculateMobDamage(DungeonMob mob, Player player, double baseDamage, StatManager statManager) {
        if (mob == null || player == null || statManager == null) {
            return baseDamage;
        }

        // Use mob's scaled damage if baseDamage is 0
        double damage = baseDamage > 0 ? baseDamage : mob.getScaledDamage();

        // Apply player's defense stat
        damage = statManager.calculateDefense(player, damage);

        // Ensure non-negative damage
        return Math.max(0, damage);
    }

    /**
     * Applies critical hit multiplier to damage.
     *
     * @param damage The base damage
     * @param critMultiplier The critical hit multiplier (typically 1.5 for 50% bonus)
     * @return The damage after critical multiplier
     */
    public static double applyCritical(double damage, double critMultiplier) {
        return damage * critMultiplier;
    }

    /**
     * Applies critical hit with chance calculation.
     *
     * @param damage The base damage
     * @param critChance The critical hit chance (0-100)
     * @return The damage, potentially multiplied if crit succeeds
     */
    public static double applyCriticalWithChance(double damage, double critChance) {
        if (rollCritical(critChance)) {
            return applyCritical(damage, CRIT_MULTIPLIER);
        }
        return damage;
    }

    /**
     * Rolls for a critical hit.
     *
     * @param critChance The critical hit chance (0-100)
     * @return true if critical hit succeeds
     */
    public static boolean rollCritical(double critChance) {
        if (critChance <= 0) {
            return false;
        }
        if (critChance >= 100) {
            return true;
        }
        return RANDOM.nextDouble() * 100 < critChance;
    }

    /**
     * Applies difficulty scaling to a value.
     *
     * @param value The base value
     * @param difficulty The dungeon difficulty
     * @return The scaled value
     */
    public static double applyDifficultyScaling(double value, DungeonDifficulty difficulty) {
        if (difficulty == null) {
            return value;
        }
        return value * difficulty.getMobMultiplier();
    }

    /**
     * Calculates damage with all modifiers applied.
     * This is a comprehensive calculation that includes base damage, stats, crits, and difficulty.
     *
     * @param attacker The attacking player
     * @param defender The defending mob
     * @param baseDamage The base weapon/attack damage
     * @param difficulty The dungeon difficulty
     * @param statManager The stat manager
     * @return The final damage value
     */
    public static double calculateFullDamage(Player attacker, DungeonMob defender, double baseDamage,
                                            DungeonDifficulty difficulty, StatManager statManager) {
        if (attacker == null || defender == null || statManager == null) {
            return baseDamage;
        }

        // Start with player damage calculation (includes strength and crit)
        double damage = calculatePlayerDamage(attacker, defender, baseDamage, statManager);

        // Difficulty scaling is already applied to mob stats, not to player damage
        // So we don't apply it again here

        return Math.max(0, damage);
    }

    /**
     * Calculates damage with all modifiers for mob attacking player.
     *
     * @param attacker The attacking mob
     * @param defender The defending player
     * @param difficulty The dungeon difficulty
     * @param statManager The stat manager
     * @return The final damage value
     */
    public static double calculateFullDamage(DungeonMob attacker, Player defender,
                                            DungeonDifficulty difficulty, StatManager statManager) {
        if (attacker == null || defender == null || statManager == null) {
            return 0.0;
        }

        // Get mob's scaled damage (already includes difficulty)
        double baseDamage = attacker.getScaledDamage();

        // Apply player's defense
        return calculateMobDamage(attacker, defender, baseDamage, statManager);
    }

    /**
     * Gets the default critical hit multiplier.
     *
     * @return The default crit multiplier (1.5)
     */
    public static double getDefaultCritMultiplier() {
        return CRIT_MULTIPLIER;
    }

    /**
     * Calculates the percentage of damage reduction from defense.
     *
     * @param defense The defense value
     * @return The reduction percentage (0.0 to 1.0)
     */
    public static double calculateDefenseReduction(double defense) {
        if (defense <= 0) {
            return 0.0;
        }
        // Diminishing returns formula: reduction = defense / (defense + 100)
        return defense / (defense + 100.0);
    }

    /**
     * Calculates the strength damage multiplier.
     *
     * @param strength The strength value
     * @return The damage multiplier (1.0 = no bonus)
     */
    public static double calculateStrengthMultiplier(double strength) {
        // Each point of strength gives 1% increased damage
        return 1.0 + (strength / 100.0);
    }

    /**
     * Formats damage for display (rounded to 1 decimal place).
     *
     * @param damage The damage value
     * @return Formatted damage string
     */
    public static String formatDamage(double damage) {
        return String.format("%.1f", damage);
    }
}
