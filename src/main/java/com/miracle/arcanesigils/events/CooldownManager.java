package com.miracle.arcanesigils.events;

import com.miracle.arcanesigils.ArmorSetsPlugin;
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

        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) return false;

        Long expiry = playerCooldowns.get(abilityId);
        if (expiry == null) return false;

        if (System.currentTimeMillis() >= expiry) {
            playerCooldowns.remove(abilityId);
            return false;
        }

        long remaining = expiry - System.currentTimeMillis();
        com.miracle.arcanesigils.utils.LogHelper.debug("[Cooldown] Ability %s on cooldown for %s: %.1fs remaining",
            abilityId, player.getName(), remaining / 1000.0);
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
        setCooldown(player, abilityId, abilityId, cooldownSeconds);
    }

    /**
     * Set a cooldown for a player's ability with a display name.
     *
     * @param player          The player
     * @param abilityId       The ability identifier
     * @param abilityName     The display name for notifications
     * @param cooldownSeconds Cooldown duration in seconds
     */
    public void setCooldown(Player player, String abilityId, String abilityName, double cooldownSeconds) {
        if (cooldownSeconds <= 0) return;

        UUID uuid = player.getUniqueId();
        long expiry = System.currentTimeMillis() + (long) (cooldownSeconds * 1000);

        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(abilityId, expiry);

        // Format the ability name to be human-readable
        String displayName = formatAbilityName(abilityName);

        // Track for ready notification (plays sound + shows "Ready!" when cooldown expires)
        var cooldownNotifier = plugin.getCooldownNotifier();
        if (cooldownNotifier != null) {
            cooldownNotifier.trackCooldown(player, abilityId, displayName,
                System.currentTimeMillis() + (long)(cooldownSeconds * 1000));
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
        if (remaining <= 0) {
            playerCooldowns.remove(abilityId);
            return 0;
        }

        return remaining / 1000.0;
    }

    /**
     * Get remaining ability cooldown for display purposes.
     *
     * @param player The player
     * @param abilityId The ability identifier
     * @return Ability cooldown in seconds, or 0 if not on cooldown
     */
    public double getAbilityCooldownForDisplay(Player player, String abilityId) {
        UUID uuid = player.getUniqueId();

        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) return 0;

        Long expiry = playerCooldowns.get(abilityId);
        if (expiry == null) return 0;

        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            playerCooldowns.remove(abilityId);
            return 0;
        }

        return remaining / 1000.0;
    }

    /**
     * Clear all cooldowns for a player.
     */
    public void clearCooldowns(Player player) {
        UUID uuid = player.getUniqueId();
        cooldowns.remove(uuid);
    }

    /**
     * Clear all cooldowns.
     */
    public void clearAll() {
        cooldowns.clear();
    }

    /**
     * Send cooldown message to player if enabled.
     */
    public void sendCooldownMessage(Player player, String abilityName) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("cooldowns.show-cooldown-messages", true)) {
            return;
        }

        double remaining = getAbilityCooldownForDisplay(player, abilityName);
        if (remaining <= 0) return;

        String message = plugin.getConfigManager().getMessage("cooldown-active")
                .replace("%ability%", abilityName)
                .replace("%remaining%", String.format("%.1f", remaining));

        player.sendMessage(com.miracle.arcanesigils.utils.TextUtil.colorize(message));
    }

    /**
     * Format ability name from snake_case to Title Case.
     */
    private static String formatAbilityName(String id) {
        if (id == null || id.isEmpty()) return id;

        String[] parts = id.replace("-", "_").split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                if (result.length() > 0) result.append(" ");
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }
}
