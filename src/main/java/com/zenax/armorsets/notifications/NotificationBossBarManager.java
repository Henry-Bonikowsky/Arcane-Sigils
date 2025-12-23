package com.zenax.armorsets.notifications;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.effects.CooldownNotifier;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages BossBar displays for cooldowns and buff durations.
 * Provides smooth animated progress bars that deplete in real-time.
 */
public class NotificationBossBarManager {

    private final ArmorSetsPlugin plugin;

    // Track: playerUUID -> notificationId -> NotificationEntry
    private final Map<UUID, Map<String, NotificationEntry>> activeNotifications = new ConcurrentHashMap<>();

    // Track players with ability UI active (cooldown bars hidden)
    private final Set<UUID> abilityUIActive = ConcurrentHashMap.newKeySet();

    // Track hidden bars when ability UI is active
    private final Map<UUID, Map<String, NotificationEntry>> hiddenNotifications = new ConcurrentHashMap<>();

    // Single tick task for all updates
    private BukkitTask tickTask;

    // Configuration
    private boolean enabled = true;
    private int maxBars = 5;
    private int updateIntervalTicks = 2;
    private double lowTimeThreshold = 2.0;
    private BarStyle barStyle = BarStyle.SOLID;
    private String titleFormat = "%name% - %time%s";

    private BarColor defaultCooldownColor = BarColor.RED;
    private BarColor defaultCooldownLowColor = BarColor.YELLOW;
    private BarColor defaultDurationColor = BarColor.BLUE;
    private BarColor defaultDurationLowColor = BarColor.YELLOW;

    public NotificationBossBarManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startTickTask();
        plugin.getLogger().info("[Notifications] BossBar notification manager initialized");
    }

    /**
     * Notification types
     */
    public enum NotificationType {
        COOLDOWN,  // Time until ability ready - shows "Ready!" notification on complete
        DURATION   // Time until buff expires - silent removal
    }

    /**
     * Internal entry tracking a single notification bar
     */
    private static class NotificationEntry {
        final BossBar bossBar;
        final NotificationType type;
        final String displayName;
        final String notificationId;
        final double totalSeconds;
        final long startTimeMs;
        final long expiryTimeMs;
        final BarColor normalColor;
        final BarColor lowColor;
        boolean visible = true;
        boolean notifiedReady = false;

        NotificationEntry(BossBar bossBar, NotificationType type, String notificationId,
                          String displayName, double totalSeconds, BarColor normalColor, BarColor lowColor) {
            this.bossBar = bossBar;
            this.type = type;
            this.notificationId = notificationId;
            this.displayName = displayName;
            this.totalSeconds = totalSeconds;
            this.startTimeMs = System.currentTimeMillis();
            this.expiryTimeMs = startTimeMs + (long) (totalSeconds * 1000);
            this.normalColor = normalColor;
            this.lowColor = lowColor;
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

    /**
     * Load configuration from config.yml
     */
    private void loadConfig() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("notifications");
        if (section == null) {
            plugin.getLogger().info("[Notifications] No config section found, using defaults");
            return;
        }

        enabled = section.getBoolean("enabled", true);
        maxBars = section.getInt("max-bars", 5);
        updateIntervalTicks = section.getInt("update-interval", 2);
        lowTimeThreshold = section.getDouble("low-time-threshold", 2.0);
        titleFormat = section.getString("title-format", "%name% - %time%s");

        String styleStr = section.getString("bar-style", "SOLID");
        try {
            barStyle = BarStyle.valueOf(styleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            barStyle = BarStyle.SOLID;
        }

        // Cooldown colors
        ConfigurationSection cooldowns = section.getConfigurationSection("cooldowns");
        if (cooldowns != null) {
            defaultCooldownColor = parseColor(cooldowns.getString("color", "RED"), BarColor.RED);
            defaultCooldownLowColor = parseColor(cooldowns.getString("low-color", "YELLOW"), BarColor.YELLOW);
        }

        // Duration colors
        ConfigurationSection durations = section.getConfigurationSection("durations");
        if (durations != null) {
            defaultDurationColor = parseColor(durations.getString("color", "BLUE"), BarColor.BLUE);
            defaultDurationLowColor = parseColor(durations.getString("low-color", "YELLOW"), BarColor.YELLOW);
        }
    }

    private BarColor parseColor(String colorStr, BarColor defaultColor) {
        if (colorStr == null) return defaultColor;
        try {
            return BarColor.valueOf(colorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultColor;
        }
    }

    /**
     * Start the tick task that updates all bars
     */
    private void startTickTask() {
        if (tickTask != null) {
            tickTask.cancel();
        }

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickUpdate, updateIntervalTicks, updateIntervalTicks);
    }

    /**
     * Main tick update - runs every updateIntervalTicks
     */
    private void tickUpdate() {
        if (!enabled) return;

        for (Map.Entry<UUID, Map<String, NotificationEntry>> playerEntry : activeNotifications.entrySet()) {
            UUID playerId = playerEntry.getKey();
            Map<String, NotificationEntry> notifications = playerEntry.getValue();

            // Skip if ability UI is active
            if (abilityUIActive.contains(playerId)) {
                continue;
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                // Cleanup offline player
                cleanupPlayer(playerId);
                continue;
            }

            // Process each notification
            List<String> toRemove = new ArrayList<>();

            for (Map.Entry<String, NotificationEntry> entry : notifications.entrySet()) {
                String notificationId = entry.getKey();
                NotificationEntry notification = entry.getValue();

                if (notification.isExpired()) {
                    // Handle expiration
                    handleExpiration(player, notification);
                    toRemove.add(notificationId);
                } else {
                    // Update bar
                    updateBar(notification);
                }
            }

            // Remove expired notifications
            for (String id : toRemove) {
                NotificationEntry removed = notifications.remove(id);
                if (removed != null) {
                    removed.bossBar.removeAll();
                }
            }

            // Cleanup empty map
            if (notifications.isEmpty()) {
                activeNotifications.remove(playerId);
            }
        }
    }

    /**
     * Update a single bar's progress, title, and color
     */
    private void updateBar(NotificationEntry notification) {
        double remaining = notification.getRemainingSeconds();
        double progress = notification.getProgress();

        // Update progress
        notification.bossBar.setProgress(progress);

        // Update title with remaining time
        String timeStr = String.format("%.1f", remaining);
        String title = titleFormat
                .replace("%name%", notification.displayName)
                .replace("%time%", timeStr);
        notification.bossBar.setTitle(TextUtil.colorize(title));

        // Change color when time is low
        if (remaining <= lowTimeThreshold) {
            notification.bossBar.setColor(notification.lowColor);
        } else {
            notification.bossBar.setColor(notification.normalColor);
        }
    }

    /**
     * Handle notification expiration
     */
    private void handleExpiration(Player player, NotificationEntry notification) {
        if (notification.type == NotificationType.COOLDOWN && !notification.notifiedReady) {
            notification.notifiedReady = true;

            // Trigger ready notification via CooldownNotifier
            CooldownNotifier notifier = plugin.getCooldownNotifier();
            if (notifier != null) {
                notifier.notifyReady(player, notification.displayName);
            }
        }
        // Duration types expire silently
    }

    // ========== PUBLIC API ==========

    /**
     * Check if the notification system is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Start a cooldown bar for an ability
     *
     * @param player      The player
     * @param abilityId   Unique ID for this cooldown
     * @param displayName Display name shown in bar
     * @param seconds     Total cooldown duration
     * @param colorOverride Optional color override (null = use default)
     */
    public void startCooldown(Player player, String abilityId, String displayName, double seconds, BarColor colorOverride) {
        if (!enabled || player == null || seconds <= 0) return;

        BarColor color = colorOverride != null ? colorOverride : defaultCooldownColor;
        startNotification(player, abilityId, displayName, seconds, NotificationType.COOLDOWN, color, defaultCooldownLowColor);
    }

    /**
     * Start a duration bar for a buff/effect
     *
     * @param player      The player
     * @param buffId      Unique ID for this buff
     * @param displayName Display name shown in bar
     * @param seconds     Total buff duration
     * @param colorOverride Optional color override (null = use default)
     */
    public void startDuration(Player player, String buffId, String displayName, double seconds, BarColor colorOverride) {
        if (!enabled || player == null || seconds <= 0) return;

        BarColor color = colorOverride != null ? colorOverride : defaultDurationColor;
        startNotification(player, buffId, displayName, seconds, NotificationType.DURATION, color, defaultDurationLowColor);
    }

    /**
     * Internal method to start a notification
     */
    private void startNotification(Player player, String notificationId, String displayName,
                                   double seconds, NotificationType type, BarColor normalColor, BarColor lowColor) {
        UUID playerId = player.getUniqueId();

        // Cancel existing notification with same ID
        cancelNotification(player, notificationId);

        // Enforce max bars limit
        Map<String, NotificationEntry> playerNotifications = activeNotifications.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        enforceMaxBars(playerNotifications);

        // Create the boss bar
        String initialTitle = titleFormat
                .replace("%name%", displayName)
                .replace("%time%", String.format("%.1f", seconds));

        BossBar bossBar = Bukkit.createBossBar(
                TextUtil.colorize(initialTitle),
                normalColor,
                barStyle
        );
        bossBar.setProgress(1.0);

        // Create entry
        NotificationEntry entry = new NotificationEntry(
                bossBar, type, notificationId, displayName, seconds, normalColor, lowColor
        );

        // Add to player if ability UI is not active
        if (!abilityUIActive.contains(playerId)) {
            bossBar.addPlayer(player);
            playerNotifications.put(notificationId, entry);
        } else {
            // Store in hidden notifications
            Map<String, NotificationEntry> hidden = hiddenNotifications.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
            hidden.put(notificationId, entry);
        }
    }

    /**
     * Enforce maximum bar limit by removing oldest expiring bars
     */
    private void enforceMaxBars(Map<String, NotificationEntry> notifications) {
        while (notifications.size() >= maxBars) {
            // Find the notification with least remaining time
            String toRemove = null;
            double minRemaining = Double.MAX_VALUE;

            for (Map.Entry<String, NotificationEntry> entry : notifications.entrySet()) {
                double remaining = entry.getValue().getRemainingSeconds();
                if (remaining < minRemaining) {
                    minRemaining = remaining;
                    toRemove = entry.getKey();
                }
            }

            if (toRemove != null) {
                NotificationEntry removed = notifications.remove(toRemove);
                if (removed != null) {
                    removed.bossBar.removeAll();
                }
            } else {
                break;
            }
        }
    }

    /**
     * Cancel a specific notification
     */
    public void cancelNotification(Player player, String notificationId) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();

        // Check active notifications
        Map<String, NotificationEntry> notifications = activeNotifications.get(playerId);
        if (notifications != null) {
            NotificationEntry removed = notifications.remove(notificationId);
            if (removed != null) {
                removed.bossBar.removeAll();
            }
        }

        // Check hidden notifications
        Map<String, NotificationEntry> hidden = hiddenNotifications.get(playerId);
        if (hidden != null) {
            NotificationEntry removed = hidden.remove(notificationId);
            if (removed != null) {
                removed.bossBar.removeAll();
            }
        }
    }

    /**
     * Cancel all notifications for a player
     */
    public void cancelAllNotifications(Player player) {
        if (player == null) return;
        cleanupPlayer(player.getUniqueId());
    }

    /**
     * Check if ability UI is currently active for a player
     */
    public boolean isAbilityUIActive(Player player) {
        return player != null && abilityUIActive.contains(player.getUniqueId());
    }

    /**
     * Hide all cooldown/duration bars (called when ability UI activates)
     */
    public void hideAllBars(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        abilityUIActive.add(playerId);

        // Move active notifications to hidden
        Map<String, NotificationEntry> notifications = activeNotifications.get(playerId);
        if (notifications != null) {
            for (NotificationEntry entry : notifications.values()) {
                entry.bossBar.removePlayer(player);
                entry.visible = false;
            }
            // Move to hidden map
            hiddenNotifications.put(playerId, new ConcurrentHashMap<>(notifications));
            notifications.clear();
        }
    }

    /**
     * Show all cooldown/duration bars (called when ability UI deactivates)
     */
    public void showAllBars(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        abilityUIActive.remove(playerId);

        // Restore hidden notifications
        Map<String, NotificationEntry> hidden = hiddenNotifications.remove(playerId);
        if (hidden != null) {
            Map<String, NotificationEntry> active = activeNotifications.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

            for (Map.Entry<String, NotificationEntry> entry : hidden.entrySet()) {
                NotificationEntry notification = entry.getValue();

                // Skip if already expired while hidden
                if (notification.isExpired()) {
                    notification.bossBar.removeAll();
                    // Still trigger ready notification for cooldowns
                    if (notification.type == NotificationType.COOLDOWN && !notification.notifiedReady) {
                        notification.notifiedReady = true;
                        CooldownNotifier notifier = plugin.getCooldownNotifier();
                        if (notifier != null) {
                            notifier.notifyReady(player, notification.displayName);
                        }
                    }
                    continue;
                }

                // Re-add player to bar
                notification.bossBar.addPlayer(player);
                notification.visible = true;
                active.put(entry.getKey(), notification);
            }
        }
    }

    /**
     * Cleanup a player's notifications
     */
    private void cleanupPlayer(UUID playerId) {
        abilityUIActive.remove(playerId);

        Map<String, NotificationEntry> notifications = activeNotifications.remove(playerId);
        if (notifications != null) {
            for (NotificationEntry entry : notifications.values()) {
                entry.bossBar.removeAll();
            }
        }

        Map<String, NotificationEntry> hidden = hiddenNotifications.remove(playerId);
        if (hidden != null) {
            for (NotificationEntry entry : hidden.values()) {
                entry.bossBar.removeAll();
            }
        }
    }

    /**
     * Cleanup for player logout
     */
    public void cleanup(Player player) {
        if (player != null) {
            cleanupPlayer(player.getUniqueId());
        }
    }

    /**
     * Shutdown the manager (called on plugin disable)
     */
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        // Cleanup all bars
        for (Map<String, NotificationEntry> notifications : activeNotifications.values()) {
            for (NotificationEntry entry : notifications.values()) {
                entry.bossBar.removeAll();
            }
        }
        activeNotifications.clear();

        for (Map<String, NotificationEntry> notifications : hiddenNotifications.values()) {
            for (NotificationEntry entry : notifications.values()) {
                entry.bossBar.removeAll();
            }
        }
        hiddenNotifications.clear();
        abilityUIActive.clear();

        plugin.getLogger().info("[Notifications] BossBar notification manager shutdown");
    }

    /**
     * Reload configuration
     */
    public void reload() {
        loadConfig();
        if (tickTask != null) {
            tickTask.cancel();
        }
        startTickTask();
    }
}
