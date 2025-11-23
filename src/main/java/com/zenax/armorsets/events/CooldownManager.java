package com.zenax.armorsets.events;

import com.zenax.armorsets.ArmorSetsPlugin;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cooldowns for abilities and effects.
 */
public class CooldownManager {

    private final ArmorSetsPlugin plugin;

    // Map of player UUID -> ability ID -> expiry time
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    // Global cooldown tracking
    private final Map<UUID, Long> globalCooldowns = new ConcurrentHashMap<>();

    public CooldownManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if a player has an ability on cooldown.
     *
     * @param player    The player
     * @param abilityId The ability identifier
     * @return true if on cooldown
     */
    public boolean isOnCooldown(Player player, String abilityId) {
        UUID uuid = player.getUniqueId();

        // Check global cooldown first
        Long globalExpiry = globalCooldowns.get(uuid);
        if (globalExpiry != null && System.currentTimeMillis() < globalExpiry) {
            return true;
        }

        // Check specific ability cooldown
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) return false;

        Long expiry = playerCooldowns.get(abilityId);
        if (expiry == null) return false;

        if (System.currentTimeMillis() >= expiry) {
            playerCooldowns.remove(abilityId);
            return false;
        }

        return true;
    }

    /**
     * Set a cooldown for a player's ability.
     *
     * @param player          The player
     * @param abilityId       The ability identifier
     * @param cooldownSeconds Cooldown duration in seconds
     */
    public void setCooldown(Player player, String abilityId, double cooldownSeconds) {
        if (cooldownSeconds <= 0) return;

        UUID uuid = player.getUniqueId();
        long expiry = System.currentTimeMillis() + (long) (cooldownSeconds * 1000);

        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(abilityId, expiry);

        // Also set global cooldown
        int globalCooldownTicks = plugin.getConfigManager().getMainConfig()
                .getInt("cooldowns.global-cooldown", 10);
        if (globalCooldownTicks > 0) {
            long globalExpiry = System.currentTimeMillis() + (globalCooldownTicks * 50L);
            globalCooldowns.put(uuid, globalExpiry);
        }
    }

    /**
     * Get remaining cooldown time in seconds.
     *
     * @param player    The player
     * @param abilityId The ability identifier
     * @return Remaining seconds, or 0 if not on cooldown
     */
    public double getRemainingCooldown(Player player, String abilityId) {
        UUID uuid = player.getUniqueId();

        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) return 0;

        Long expiry = playerCooldowns.get(abilityId);
        if (expiry == null) return 0;

        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000.0 : 0;
    }

    /**
     * Clear all cooldowns for a player.
     */
    public void clearCooldowns(Player player) {
        UUID uuid = player.getUniqueId();
        cooldowns.remove(uuid);
        globalCooldowns.remove(uuid);
    }

    /**
     * Clear all cooldowns.
     */
    public void clearAll() {
        cooldowns.clear();
        globalCooldowns.clear();
    }

    /**
     * Send cooldown message to player if enabled.
     */
    public void sendCooldownMessage(Player player, String abilityName) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("cooldowns.show-cooldown-messages", true)) {
            return;
        }

        double remaining = getRemainingCooldown(player, abilityName);
        if (remaining <= 0) return;

        String message = plugin.getConfigManager().getMessage("cooldown-active")
                .replace("%ability%", abilityName)
                .replace("%remaining%", String.format("%.1f", remaining));

        player.sendMessage(com.zenax.armorsets.utils.TextUtil.colorize(message));
    }
}
