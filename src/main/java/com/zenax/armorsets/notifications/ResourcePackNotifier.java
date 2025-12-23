package com.zenax.armorsets.notifications;

import com.zenax.armorsets.ArmorSetsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends notifications to players using action bar with custom resource pack fonts.
 * Displays cooldowns and buffs in top-left area using negative space positioning.
 */
public class ResourcePackNotifier {

    private final ArmorSetsPlugin plugin;

    // Active notifications per player: UUID -> notificationId -> NotificationData
    private final Map<UUID, Map<String, NotificationData>> activeNotifications = new ConcurrentHashMap<>();

    // Players with paused action bar updates (e.g., when binds UI is active)
    private final Set<UUID> pausedPlayers = ConcurrentHashMap.newKeySet();

    // Players who have successfully loaded the resource pack
    private final Set<UUID> hasResourcePack = ConcurrentHashMap.newKeySet();

    // Update task
    private BukkitTask updateTask;

    // Configuration
    private boolean enabled = true;
    private int maxVisible = 10;
    private int updateIntervalTicks = 2;

    // Unicode characters for resource pack (defined in font/default.json)
    private static final String NEGATIVE_SPACE_1 = "\uF801";
    private static final String NEGATIVE_SPACE_8 = "\uF804";
    private static final String NEGATIVE_SPACE_64 = "\uF807";
    private static final String NEGATIVE_SPACE_128 = "\uF808";

    private static final char BAR_0 = '\uE010';
    private static final char BAR_10 = '\uE011';
    private static final char BAR_20 = '\uE012';
    private static final char BAR_30 = '\uE013';
    private static final char BAR_40 = '\uE014';
    private static final char BAR_50 = '\uE015';
    private static final char BAR_60 = '\uE016';
    private static final char BAR_70 = '\uE017';
    private static final char BAR_80 = '\uE018';
    private static final char BAR_90 = '\uE019';
    private static final char BAR_100 = '\uE01A';

    public ResourcePackNotifier(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startUpdateTask();
        plugin.getLogger().info("[Notifications] ResourcePackNotifier initialized");
    }

    /**
     * Notification types
     */
    public enum NotificationType {
        COOLDOWN,   // Red bar, shows "Ready!" when complete
        BUFF        // Blue bar, silent removal
    }

    /**
     * Internal data for a notification
     */
    private static class NotificationData {
        final String id;
        final String displayName;
        final NotificationType type;
        final double totalSeconds;
        final long startTimeMs;
        final long expiryTimeMs;
        boolean notifiedReady = false;

        NotificationData(String id, String displayName, NotificationType type, double seconds) {
            this.id = id;
            this.displayName = displayName;
            this.type = type;
            this.totalSeconds = seconds;
            this.startTimeMs = System.currentTimeMillis();
            this.expiryTimeMs = startTimeMs + (long)(seconds * 1000);
        }

        double getRemainingSeconds() {
            return Math.max(0, (expiryTimeMs - System.currentTimeMillis()) / 1000.0);
        }

        double getProgress() {
            if (totalSeconds <= 0) return 0;
            return Math.max(0, Math.min(1, getRemainingSeconds() / totalSeconds));
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiryTimeMs;
        }
    }

    private void loadConfig() {
        var config = plugin.getConfig();
        enabled = config.getBoolean("notifications.enabled", true);
        maxVisible = config.getInt("notifications.max-visible", 10);
        updateIntervalTicks = config.getInt("notifications.update-interval", 2);
    }

    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::update, updateIntervalTicks, updateIntervalTicks);
    }

    /**
     * Main update loop - sends action bar to all players with active notifications
     */
    private void update() {
        if (!enabled) return;

        for (Map.Entry<UUID, Map<String, NotificationData>> entry : activeNotifications.entrySet()) {
            UUID playerId = entry.getKey();
            Map<String, NotificationData> notifications = entry.getValue();

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                activeNotifications.remove(playerId);
                continue;
            }

            // Process expired notifications
            List<String> expired = new ArrayList<>();
            for (NotificationData data : notifications.values()) {
                if (data.isExpired()) {
                    expired.add(data.id);
                    if (data.type == NotificationType.COOLDOWN && !data.notifiedReady) {
                        data.notifiedReady = true;
                        notifyReady(player, data.displayName);
                    }
                }
            }
            expired.forEach(notifications::remove);

            // Skip sending action bar if player is paused (but still process expirations)
            if (pausedPlayers.contains(playerId)) {
                if (notifications.isEmpty()) {
                    activeNotifications.remove(playerId);
                }
                continue;
            }

            // Build and send action bar if there are active notifications
            if (!notifications.isEmpty()) {
                Component actionBar = buildActionBar(notifications.values());
                player.sendActionBar(actionBar);
            }

            // Cleanup empty maps
            if (notifications.isEmpty()) {
                activeNotifications.remove(playerId);
            }
        }
    }

    /**
     * Build the action bar component with all active notifications
     */
    private Component buildActionBar(Collection<NotificationData> notifications) {
        Component result = Component.empty();

        // Sort by remaining time (shortest first)
        List<NotificationData> sorted = new ArrayList<>(notifications);
        sorted.sort(Comparator.comparingDouble(NotificationData::getRemainingSeconds));

        // Limit to max visible
        int count = Math.min(sorted.size(), maxVisible);

        for (int i = 0; i < count; i++) {
            NotificationData data = sorted.get(i);

            // Add separator between notifications
            if (i > 0) {
                result = result.append(Component.text("  "));
            }

            // Build notification: "Name ████░░ 3s"
            Component notification = buildNotification(data);
            result = result.append(notification);
        }

        return result;
    }

    /**
     * Build a single notification component
     */
    private Component buildNotification(NotificationData data) {
        double remaining = data.getRemainingSeconds();
        double progress = data.getProgress();

        // Color based on type
        TextColor nameColor = data.type == NotificationType.COOLDOWN
            ? TextColor.color(0xFF6666)  // Light red
            : TextColor.color(0x6666FF); // Light blue

        TextColor timeColor = remaining <= 2.0
            ? TextColor.color(0xFFFF00)  // Yellow when low
            : NamedTextColor.GRAY;

        // Build: "Name"
        Component name = Component.text(truncateName(data.displayName, 12))
            .color(nameColor);

        // Build: progress bar using text characters
        Component bar = buildProgressBar(progress, data.type);

        // Build: "3.0s"
        Component time = Component.text(String.format(" %.1fs", remaining))
            .color(timeColor);

        return name.append(Component.text(" ")).append(bar).append(time);
    }

    /**
     * Build a text-based progress bar
     */
    private Component buildProgressBar(double progress, NotificationType type) {
        int filled = (int)(progress * 6);
        int empty = 6 - filled;

        TextColor fillColor = type == NotificationType.COOLDOWN
            ? TextColor.color(0xFF4444)  // Red
            : TextColor.color(0x4444FF); // Blue

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < filled; i++) bar.append("█");
        for (int i = 0; i < empty; i++) bar.append("░");

        return Component.text(bar.toString()).color(fillColor);
    }

    /**
     * Truncate name to max length
     */
    private String truncateName(String name, int maxLength) {
        if (name.length() <= maxLength) return name;
        return name.substring(0, maxLength - 2) + "..";
    }

    // ========== PUBLIC API ==========

    /**
     * Show a cooldown notification
     */
    public void showCooldown(Player player, String id, String name, double seconds) {
        if (!enabled || player == null || seconds <= 0) return;

        UUID uuid = player.getUniqueId();
        Map<String, NotificationData> notifications = activeNotifications
            .computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

        // Replace existing notification with same ID
        notifications.put(id, new NotificationData(id, name, NotificationType.COOLDOWN, seconds));

        // Enforce max limit
        enforceLimit(notifications);
    }

    /**
     * Show a buff/duration notification
     */
    public void showBuff(Player player, String id, String name, double seconds) {
        if (!enabled || player == null || seconds <= 0) return;

        UUID uuid = player.getUniqueId();
        Map<String, NotificationData> notifications = activeNotifications
            .computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

        notifications.put(id, new NotificationData(id, name, NotificationType.BUFF, seconds));
        enforceLimit(notifications);
    }

    /**
     * Notify player that an ability is ready
     */
    public void notifyReady(Player player, String abilityName) {
        if (!enabled) return;

        // Play sound
        String soundName = plugin.getConfig().getString("cooldowns.ready-sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        float pitch = (float) plugin.getConfig().getDouble("cooldowns.ready-sound-pitch", 1.5);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 0.8f, pitch);
        } catch (IllegalArgumentException e) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, pitch);
        }

        // Send ready message via action bar (brief flash)
        Component ready = Component.text(abilityName + " ")
            .color(NamedTextColor.GREEN)
            .append(Component.text("Ready!").color(NamedTextColor.WHITE));
        player.sendActionBar(ready);
    }

    /**
     * Cancel a specific notification
     */
    public void cancel(Player player, String id) {
        if (player == null) return;

        Map<String, NotificationData> notifications = activeNotifications.get(player.getUniqueId());
        if (notifications != null) {
            notifications.remove(id);
        }
    }

    /**
     * Cancel all notifications for a player
     */
    public void cancelAll(Player player) {
        if (player != null) {
            activeNotifications.remove(player.getUniqueId());
        }
    }

    /**
     * Check if notifications are enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Pause action bar updates for a player (e.g., when binds UI is active)
     */
    public void pause(Player player) {
        if (player != null) {
            pausedPlayers.add(player.getUniqueId());
        }
    }

    /**
     * Resume action bar updates for a player
     */
    public void resume(Player player) {
        if (player != null) {
            pausedPlayers.remove(player.getUniqueId());
        }
    }

    /**
     * Check if a player is paused
     */
    public boolean isPaused(Player player) {
        return player != null && pausedPlayers.contains(player.getUniqueId());
    }

    /**
     * Set whether a player has the resource pack loaded
     */
    public void setHasResourcePack(Player player, boolean has) {
        if (player == null) return;
        if (has) {
            hasResourcePack.add(player.getUniqueId());
        } else {
            hasResourcePack.remove(player.getUniqueId());
        }
    }

    /**
     * Check if a player has the resource pack loaded
     */
    public boolean hasResourcePack(Player player) {
        return player != null && hasResourcePack.contains(player.getUniqueId());
    }

    /**
     * Enforce maximum notification limit
     */
    private void enforceLimit(Map<String, NotificationData> notifications) {
        while (notifications.size() > maxVisible) {
            // Remove oldest expiring
            String toRemove = null;
            double minRemaining = Double.MAX_VALUE;

            for (NotificationData data : notifications.values()) {
                if (data.getRemainingSeconds() < minRemaining) {
                    minRemaining = data.getRemainingSeconds();
                    toRemove = data.id;
                }
            }

            if (toRemove != null) {
                notifications.remove(toRemove);
            } else {
                break;
            }
        }
    }

    /**
     * Cleanup for player logout
     */
    public void cleanup(Player player) {
        if (player != null) {
            UUID uuid = player.getUniqueId();
            activeNotifications.remove(uuid);
            pausedPlayers.remove(uuid);
            hasResourcePack.remove(uuid);
        }
    }

    /**
     * Shutdown the notifier
     */
    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        activeNotifications.clear();
        pausedPlayers.clear();
        plugin.getLogger().info("[Notifications] ResourcePackNotifier shutdown");
    }

    /**
     * Reload configuration
     */
    public void reload() {
        loadConfig();
        if (updateTask != null) {
            updateTask.cancel();
        }
        startUpdateTask();
    }

    /**
     * Format ability name to be human-readable (moved from ToastUtil)
     */
    public static String formatAbilityName(String id) {
        if (id == null || id.isEmpty()) return id;

        // Convert snake_case to Title Case
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
