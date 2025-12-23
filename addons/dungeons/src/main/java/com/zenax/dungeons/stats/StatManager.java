package com.zenax.dungeons.stats;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages StatProfiles for all players and provides utility methods for
 * stat-based calculations like damage and defense.
 */
public class StatManager {
    private final Map<UUID, StatProfile> profiles;

    /**
     * Creates a new StatManager.
     */
    public StatManager() {
        this.profiles = new ConcurrentHashMap<>();
    }

    /**
     * Gets or creates a StatProfile for a player.
     *
     * @param player The player
     * @return The player's StatProfile
     */
    public StatProfile getProfile(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), uuid -> new StatProfile());
    }

    /**
     * Gets a StatProfile by UUID, if it exists.
     *
     * @param uuid The player's UUID
     * @return The StatProfile, or null if not found
     */
    public StatProfile getProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    /**
     * Creates a new StatProfile for a player.
     * If a profile already exists, it will be replaced.
     *
     * @param player The player
     * @return The newly created StatProfile
     */
    public StatProfile createProfile(Player player) {
        StatProfile profile = new StatProfile();
        profiles.put(player.getUniqueId(), profile);
        return profile;
    }

    /**
     * Removes a player's StatProfile.
     *
     * @param player The player
     * @return true if a profile was removed, false otherwise
     */
    public boolean removeProfile(Player player) {
        return profiles.remove(player.getUniqueId()) != null;
    }

    /**
     * Removes a StatProfile by UUID.
     *
     * @param uuid The player's UUID
     * @return true if a profile was removed, false otherwise
     */
    public boolean removeProfile(UUID uuid) {
        return profiles.remove(uuid) != null;
    }

    /**
     * Checks if a player has a StatProfile.
     *
     * @param player The player
     * @return true if the player has a profile, false otherwise
     */
    public boolean hasProfile(Player player) {
        return profiles.containsKey(player.getUniqueId());
    }

    /**
     * Gets the number of active profiles.
     *
     * @return The profile count
     */
    public int getProfileCount() {
        return profiles.size();
    }

    /**
     * Clears all profiles.
     */
    public void clearAllProfiles() {
        profiles.clear();
    }

    /**
     * Calculates the final damage output for an attacker.
     * Formula: baseDamage * (1 + (strength / 100))
     *
     * @param attacker The attacking player
     * @param baseDamage The base damage before strength modifier
     * @return The calculated damage
     */
    public double calculateDamage(Player attacker, double baseDamage) {
        StatProfile profile = getProfile(attacker);
        double strength = profile.getEffectiveStat(Stat.STRENGTH);

        // Each point of strength gives 1% increased damage
        double multiplier = 1.0 + (strength / 100.0);
        return baseDamage * multiplier;
    }

    /**
     * Calculates the final damage taken after defense reduction.
     * Formula: incomingDamage * (1 - (defense / (defense + 100)))
     *
     * @param defender The defending player
     * @param incomingDamage The damage before defense reduction
     * @return The reduced damage amount
     */
    public double calculateDefense(Player defender, double incomingDamage) {
        StatProfile profile = getProfile(defender);
        double defense = profile.getEffectiveStat(Stat.DEFENSE);

        // Diminishing returns formula: reduction = defense / (defense + 100)
        double reduction = defense / (defense + 100.0);
        double damageMultiplier = 1.0 - reduction;

        return incomingDamage * damageMultiplier;
    }

    /**
     * Calculates the critical hit chance based on luck.
     * Formula: min(luck / 2, 50) (caps at 50%)
     *
     * @param player The player
     * @return The critical hit chance as a percentage (0-50)
     */
    public double calculateCritChance(Player player) {
        StatProfile profile = getProfile(player);
        double luck = profile.getEffectiveStat(Stat.LUCK);

        // Each 2 points of luck = 1% crit chance, capped at 50%
        return Math.min(luck / 2.0, 50.0);
    }

    /**
     * Calculates the maximum health bonus from vitality.
     * Formula: 20 + (vitality * 2)
     *
     * @param player The player
     * @return The maximum health value
     */
    public double calculateMaxHealth(Player player) {
        StatProfile profile = getProfile(player);
        double vitality = profile.getEffectiveStat(Stat.VITALITY);

        // Base health is 20 (10 hearts), each vitality adds 2 HP
        return 20.0 + (vitality * 2.0);
    }

    /**
     * Calculates the movement speed multiplier from speed stat.
     * Formula: 0.2 * (1 + (speed / 100))
     *
     * @param player The player
     * @return The movement speed value (Minecraft's speed attribute)
     */
    public double calculateMovementSpeed(Player player) {
        StatProfile profile = getProfile(player);
        double speed = profile.getEffectiveStat(Stat.SPEED);

        // Base speed is 0.2, each point of speed gives 1% increased movement
        double baseSpeed = 0.2;
        double multiplier = 1.0 + (speed / 100.0);
        return baseSpeed * multiplier;
    }

    /**
     * Calculates the ability damage multiplier from intelligence.
     * Formula: 1 + (intelligence / 100)
     *
     * @param player The player
     * @return The ability damage multiplier
     */
    public double calculateAbilityDamage(Player player) {
        StatProfile profile = getProfile(player);
        double intelligence = profile.getEffectiveStat(Stat.INTELLIGENCE);

        // Each point of intelligence gives 1% increased ability damage
        return 1.0 + (intelligence / 100.0);
    }

    /**
     * Calculates the cooldown reduction from intelligence.
     * Formula: min(intelligence / 2, 40) (caps at 40%)
     *
     * @param player The player
     * @return The cooldown reduction as a percentage (0-40)
     */
    public double calculateCooldownReduction(Player player) {
        StatProfile profile = getProfile(player);
        double intelligence = profile.getEffectiveStat(Stat.INTELLIGENCE);

        // Each 2 points of intelligence = 1% CDR, capped at 40%
        return Math.min(intelligence / 2.0, 40.0);
    }

    /**
     * Applies a buff to a player.
     *
     * @param player The player to buff
     * @param stat The stat to buff
     * @param value The value of the buff
     * @param type The type of buff (FLAT or PERCENT)
     * @param source The source of the buff
     * @param durationMillis The duration in milliseconds
     * @return The created StatModifier
     */
    public StatModifier applyBuff(Player player, Stat stat, double value, ModifierType type, String source, long durationMillis) {
        StatProfile profile = getProfile(player);
        StatModifier modifier = StatModifier.timed(stat, value, type, source, durationMillis);
        profile.addModifier(modifier);
        return modifier;
    }

    /**
     * Applies a permanent buff to a player.
     *
     * @param player The player to buff
     * @param stat The stat to buff
     * @param value The value of the buff
     * @param type The type of buff (FLAT or PERCENT)
     * @param source The source of the buff
     * @return The created StatModifier
     */
    public StatModifier applyPermanentBuff(Player player, Stat stat, double value, ModifierType type, String source) {
        StatProfile profile = getProfile(player);
        StatModifier modifier;
        if (type == ModifierType.FLAT) {
            modifier = StatModifier.flat(stat, value, source);
        } else {
            modifier = StatModifier.percent(stat, value, source);
        }
        profile.addModifier(modifier);
        return modifier;
    }

    /**
     * Applies a debuff to a player (negative value).
     *
     * @param player The player to debuff
     * @param stat The stat to debuff
     * @param value The value of the debuff (will be negated)
     * @param type The type of debuff (FLAT or PERCENT)
     * @param source The source of the debuff
     * @param durationMillis The duration in milliseconds
     * @return The created StatModifier
     */
    public StatModifier applyDebuff(Player player, Stat stat, double value, ModifierType type, String source, long durationMillis) {
        // Negate the value for debuffs
        return applyBuff(player, stat, -Math.abs(value), type, source, durationMillis);
    }

    /**
     * Removes all buffs/debuffs from a specific source for a player.
     *
     * @param player The player
     * @param source The source to remove
     * @return The number of modifiers removed
     */
    public int removeBuffsBySource(Player player, String source) {
        StatProfile profile = getProfile(player);
        return profile.removeModifiersBySource(source);
    }

    /**
     * Removes a specific modifier by ID from a player.
     *
     * @param player The player
     * @param modifierId The modifier ID to remove
     * @return true if the modifier was removed, false otherwise
     */
    public boolean removeModifier(Player player, String modifierId) {
        StatProfile profile = getProfile(player);
        return profile.removeModifier(modifierId);
    }

    /**
     * Clears all expired modifiers for all players.
     */
    public void clearExpiredModifiers() {
        for (StatProfile profile : profiles.values()) {
            profile.clearExpiredModifiers();
        }
    }

    /**
     * Clears all expired modifiers for a specific player.
     *
     * @param player The player
     * @return The number of modifiers cleared
     */
    public int clearExpiredModifiers(Player player) {
        StatProfile profile = getProfile(player);
        return profile.clearExpiredModifiers();
    }

    /**
     * Gets all player UUIDs with active profiles.
     *
     * @return A set of UUIDs
     */
    public java.util.Set<UUID> getActivePlayerUUIDs() {
        return profiles.keySet();
    }

    /**
     * Resets a player's profile to default values.
     *
     * @param player The player
     */
    public void resetProfile(Player player) {
        StatProfile profile = getProfile(player);
        profile.reset();
    }
}
