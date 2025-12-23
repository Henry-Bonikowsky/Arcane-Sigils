package com.zenax.armorsets.effects;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notifies players when their ability cooldowns expire.
 * Plays a sound and shows an action bar message.
 */
public class CooldownNotifier {

    private final ArmorSetsPlugin plugin;
    private BukkitTask tickTask;

    // Track cooldowns: playerUUID -> abilityId -> CooldownEntry
    private final Map<UUID, Map<String, CooldownEntry>> trackedCooldowns = new ConcurrentHashMap<>();

    public CooldownNotifier(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    /**
     * Register a cooldown to be tracked for notification.
     *
     * @param player       The player
     * @param abilityId    The ability ID
     * @param abilityName  The display name of the ability
     * @param expiryTimeMs When the cooldown expires (System.currentTimeMillis() + duration)
     */
    public void trackCooldown(Player player, String abilityId, String abilityName, long expiryTimeMs) {
        if (!isEnabled()) return;

        UUID uuid = player.getUniqueId();
        trackedCooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(abilityId, new CooldownEntry(abilityName, expiryTimeMs));
    }

    /**
     * Track a cooldown with just the ID (name will be ID).
     */
    public void trackCooldown(Player player, String abilityId, long expiryTimeMs) {
        trackCooldown(player, abilityId, abilityId, expiryTimeMs);
    }

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredCooldowns();
            }
        }.runTaskTimer(plugin, 10L, 10L); // Every 0.5 seconds
    }

    private void checkExpiredCooldowns() {
        long now = System.currentTimeMillis();

        trackedCooldowns.forEach((uuid, abilities) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                // Clean up offline player
                trackedCooldowns.remove(uuid);
                return;
            }

            abilities.entrySet().removeIf(entry -> {
                CooldownEntry cooldown = entry.getValue();

                if (now >= cooldown.expiryTimeMs) {
                    // Cooldown expired - notify player
                    notifyReady(player, cooldown.abilityName);
                    return true; // Remove from tracking
                }
                return false;
            });

            // Clean up empty maps
            if (abilities.isEmpty()) {
                trackedCooldowns.remove(uuid);
            }
        });
    }

    public void notifyReady(Player player, String abilityName) {
        if (!isEnabled()) return;

        // Play sound
        String soundName = plugin.getConfigManager().getMainConfig()
                .getString("cooldowns.ready-sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        float pitch = (float) plugin.getConfigManager().getMainConfig()
                .getDouble("cooldowns.ready-sound-pitch", 1.5);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 0.8f, pitch);
        } catch (IllegalArgumentException e) {
            // Invalid sound name, use default
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, pitch);
        }

        // Show ready notification via action bar
        Component ready = Component.text(abilityName + " ")
            .color(NamedTextColor.GREEN)
            .append(Component.text("Ready!").color(NamedTextColor.WHITE));
        player.sendActionBar(ready);
    }

    private boolean isEnabled() {
        return plugin.getConfigManager().getMainConfig()
                .getBoolean("cooldowns.notify-ready", true);
    }

    /**
     * Clear all tracked cooldowns for a player.
     */
    public void clearPlayer(UUID uuid) {
        trackedCooldowns.remove(uuid);
    }

    /**
     * Shutdown the notifier.
     */
    public void shutdown() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }
        trackedCooldowns.clear();
    }

    /**
     * Entry for a tracked cooldown.
     */
    private static class CooldownEntry {
        final String abilityName;
        final long expiryTimeMs;

        CooldownEntry(String abilityName, long expiryTimeMs) {
            this.abilityName = abilityName;
            this.expiryTimeMs = expiryTimeMs;
        }
    }
}
