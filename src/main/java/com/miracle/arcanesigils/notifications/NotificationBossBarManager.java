package com.miracle.arcanesigils.notifications;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.CooldownNotifier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages boss bars for AbilityUI and Notifications with precise cursor tracking.
 *
 * Architecture:
 * - 1 boss bar for AbilityUI: renders multiple rows centered using different ascent values
 * - Up to 4 boss bars for Notifications: each anchored to left side of screen
 *
 * Cursor Tracking:
 * - Each character has a known pixel width
 * - After rendering content, calculate exact cursor position
 * - Use negative/positive space to return cursor to known position
 */
public class NotificationBossBarManager {

    private final ArmorSetsPlugin plugin;

    // AbilityUI: Single bar per player (multiple rows via different ascents)
    private final Map<UUID, BossBar> abilityUIBars = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> abilityUITexts = new ConcurrentHashMap<>();

    // Notifications: List of notification bars per player
    private final Map<UUID, List<NotificationBar>> notificationBars = new ConcurrentHashMap<>();

    // Tick task
    private BukkitTask tickTask;

    // Configuration
    private boolean enabled = true;
    private int maxNotificationBars = 4;
    private int updateIntervalTicks = 2;
    private boolean debug = false;

    // Notification anchor position (pixels left of center)
    private int notificationAnchor = 350;

    // Background dimensions
    private static final int BG_WIDTH = 154;
    private int textIndent = 10;

    // Font
    private static final Key HUD_FONT = Key.key("arcanesigils", "hud");

    // ========== CHARACTER WIDTH MAP ==========
    // Visual pixel widths from char_*.png files
    // CHAR_SPACING adds 1px between characters (not after last)
    private static final int DEFAULT_CHAR_WIDTH = 5;  // Most chars are 5px
    private static final int CHAR_SPACING = 1;  // MC adds 1px between chars

    private static final java.util.Map<Character, Integer> CHAR_WIDTHS = new java.util.HashMap<>();
    static {
        // Space - calibrated
        CHAR_WIDTHS.put(' ', 3);

        // 1px visual
        CHAR_WIDTHS.put('.', 1);
        CHAR_WIDTHS.put(':', 1);
        CHAR_WIDTHS.put('!', 1);

        // 2px visual
        CHAR_WIDTHS.put('\'', 2);
        CHAR_WIDTHS.put('i', 2);
        CHAR_WIDTHS.put('l', 2);
        CHAR_WIDTHS.put('|', 2);

        // 3px visual
        CHAR_WIDTHS.put(')', 3);
        CHAR_WIDTHS.put('t', 3);
        CHAR_WIDTHS.put('[', 3);
        CHAR_WIDTHS.put(']', 3);
        CHAR_WIDTHS.put('I', 3);

        // 4px visual
        CHAR_WIDTHS.put('(', 4);
        CHAR_WIDTHS.put('k', 4);
        CHAR_WIDTHS.put('f', 4);

        // 5px visual (default)
    }

    // Special widths
    private static final int BG_CHAR_WIDTH = 154;  // Background images

    // Negative space characters and their advances
    private static final char NEG_1 = '\uF801';
    private static final char NEG_2 = '\uF802';
    private static final char NEG_4 = '\uF803';
    private static final char NEG_8 = '\uF804';
    private static final char NEG_16 = '\uF805';
    private static final char NEG_32 = '\uF806';
    private static final char NEG_64 = '\uF807';
    private static final char NEG_128 = '\uF808';
    private static final char NEG_256 = '\uF809';
    private static final char NEG_512 = '\uF80A';

    // Positive space characters
    private static final char POS_1 = '\uF811';
    private static final char POS_2 = '\uF812';
    private static final char POS_4 = '\uF813';
    private static final char POS_8 = '\uF814';
    private static final char POS_16 = '\uF815';
    private static final char POS_32 = '\uF816';
    private static final char POS_64 = '\uF817';
    private static final char POS_128 = '\uF818';
    private static final char POS_256 = '\uF819';
    private static final char POS_512 = '\uF81A';

    public NotificationBossBarManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startTickTask();
        plugin.getLogger().info("[HUD] BossBar manager initialized with precise cursor tracking");
    }

    public enum NotificationType {
        COOLDOWN,  // Shows "Ready!" on complete
        DURATION   // Silent removal
    }

    private static class NotificationBar {
        final BossBar bossBar;
        final NotificationData data;
        final int slot;  // Which slot (0-3) for vertical positioning

        NotificationBar(BossBar bossBar, NotificationData data, int slot) {
            this.bossBar = bossBar;
            this.data = data;
            this.slot = slot;
        }
    }

    private static class NotificationData {
        final NotificationType type;
        final String notificationId;
        final String displayName;
        final double totalSeconds;
        final long startTimeMs;
        final long expiryTimeMs;
        boolean notifiedReady = false;

        NotificationData(NotificationType type, String notificationId, String displayName, double totalSeconds) {
            this.type = type;
            this.notificationId = notificationId;
            this.displayName = displayName;
            this.totalSeconds = totalSeconds;
            this.startTimeMs = System.currentTimeMillis();
            this.expiryTimeMs = startTimeMs + (long) (totalSeconds * 1000);
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

    // ========== CURSOR TRACKING ==========

    /**
     * Calculate pixel width of a string.
     * Width = sum of character widths (Minecraft auto-detects from bitmap content).
     * Note: Minecraft MAY add 1px spacing - test and adjust CHAR_SPACING if needed.
     */
    private int calculateWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        int width = 0;
        int visibleChars = 0;
        for (char c : text.toCharArray()) {
            int charWidth = getCharWidth(c);
            width += charWidth;
            if (charWidth > 0) visibleChars++;
        }
        // Add inter-character spacing if Minecraft uses it (set to 0 to disable)
        if (CHAR_SPACING > 0 && visibleChars > 1) {
            width += (visibleChars - 1) * CHAR_SPACING;
        }
        return width;
    }

    /**
     * Get width of a single character based on actual bitmap pixel content.
     */
    private int getCharWidth(char c) {
        // Background characters (E000 series)
        if (c >= '\uE000' && c <= '\uE014') {
            return BG_CHAR_WIDTH;
        }
        // Negative space
        if (c == NEG_1) return -1;
        if (c == NEG_2) return -2;
        if (c == NEG_4) return -4;
        if (c == NEG_8) return -8;
        if (c == NEG_16) return -16;
        if (c == NEG_32) return -32;
        if (c == NEG_64) return -64;
        if (c == NEG_128) return -128;
        if (c == NEG_256) return -256;
        if (c == NEG_512) return -512;
        // Positive space
        if (c == POS_1) return 1;
        if (c == POS_2) return 2;
        if (c == POS_4) return 4;
        if (c == POS_8) return 8;
        if (c == POS_16) return 16;
        if (c == POS_32) return 32;
        if (c == POS_64) return 64;
        if (c == POS_128) return 128;
        if (c == POS_256) return 256;
        if (c == POS_512) return 512;

        // Row characters (A1xx-A4xx) - map back to ASCII for width lookup
        if (c >= 0xA100 && c < 0xA500) {
            char ascii = (char)(c & 0xFF);  // Get the low byte (ASCII equivalent)
            return CHAR_WIDTHS.getOrDefault(ascii, DEFAULT_CHAR_WIDTH);
        }

        // Middle line characters (E1xx) - map back to ASCII
        if (c >= 0xE100 && c < 0xE200) {
            char ascii = mapMiddleLineToAscii(c);
            return CHAR_WIDTHS.getOrDefault(ascii, DEFAULT_CHAR_WIDTH);
        }

        // ASCII characters - use width map
        if (c >= 0x20 && c <= 0x7F) {
            return CHAR_WIDTHS.getOrDefault(c, DEFAULT_CHAR_WIDTH);
        }

        // Default for unknown characters
        return DEFAULT_CHAR_WIDTH;
    }

    /**
     * Map middle line character (E1xx) back to ASCII for width lookup.
     */
    private char mapMiddleLineToAscii(char c) {
        if (c == '\uE100') return ' ';
        if (c == '\uE101') return '!';
        if (c == '\uE102') return '\'';
        if (c == '\uE103') return '(';
        if (c == '\uE104') return ')';
        if (c == '\uE105') return '-';
        if (c == '\uE106') return '.';
        if (c == '\uE107') return ':';
        if (c == '\uE108') return '?';
        if (c == '\uE109') return '[';
        if (c == '\uE10A') return ']';
        if (c == '\uE10B') return '|';
        if (c >= '\uE130' && c <= '\uE139') return (char)('0' + (c - '\uE130'));
        if (c >= '\uE141' && c <= '\uE15A') return (char)('A' + (c - '\uE141'));
        if (c >= '\uE161' && c <= '\uE17A') return (char)('a' + (c - '\uE161'));
        return ' ';
    }

    /**
     * Build negative space string for given pixel offset.
     */
    private String buildNegativeSpace(int pixels) {
        if (pixels <= 0) return "";
        StringBuilder sb = new StringBuilder();
        int remaining = pixels;
        while (remaining >= 512) { sb.append(NEG_512); remaining -= 512; }
        while (remaining >= 256) { sb.append(NEG_256); remaining -= 256; }
        while (remaining >= 128) { sb.append(NEG_128); remaining -= 128; }
        while (remaining >= 64) { sb.append(NEG_64); remaining -= 64; }
        while (remaining >= 32) { sb.append(NEG_32); remaining -= 32; }
        while (remaining >= 16) { sb.append(NEG_16); remaining -= 16; }
        while (remaining >= 8) { sb.append(NEG_8); remaining -= 8; }
        while (remaining >= 4) { sb.append(NEG_4); remaining -= 4; }
        while (remaining >= 2) { sb.append(NEG_2); remaining -= 2; }
        while (remaining >= 1) { sb.append(NEG_1); remaining -= 1; }
        return sb.toString();
    }

    /**
     * Build positive space string for given pixel offset.
     */
    private String buildPositiveSpace(int pixels) {
        if (pixels <= 0) return "";
        StringBuilder sb = new StringBuilder();
        int remaining = pixels;
        while (remaining >= 512) { sb.append(POS_512); remaining -= 512; }
        while (remaining >= 256) { sb.append(POS_256); remaining -= 256; }
        while (remaining >= 128) { sb.append(POS_128); remaining -= 128; }
        while (remaining >= 64) { sb.append(POS_64); remaining -= 64; }
        while (remaining >= 32) { sb.append(POS_32); remaining -= 32; }
        while (remaining >= 16) { sb.append(POS_16); remaining -= 16; }
        while (remaining >= 8) { sb.append(POS_8); remaining -= 8; }
        while (remaining >= 4) { sb.append(POS_4); remaining -= 4; }
        while (remaining >= 2) { sb.append(POS_2); remaining -= 2; }
        while (remaining >= 1) { sb.append(POS_1); remaining -= 1; }
        return sb.toString();
    }

    // ========== NOTIFICATION RENDERING (ANCHOR-BASED) ==========

    /**
     * Build notification panel using anchor-based positioning.
     * Anchor is at -notificationAnchor pixels from center.
     * After rendering, cursor returns to anchor for next element.
     */
    private Component buildNotificationPanel(NotificationData notification, int slot) {
        double remaining = notification.getRemainingSeconds();
        double progress = notification.getProgress();

        Component panel = Component.empty();

        // 1. Move from center (0) to anchor (-notificationAnchor)
        panel = panel.append(Component.text(buildNegativeSpace(notificationAnchor)).font(HUD_FONT));

        // 2. Render background
        char bgChar = getBackgroundChar(progress);
        panel = panel.append(Component.text(String.valueOf(bgChar)).color(NamedTextColor.WHITE).font(HUD_FONT));

        // 3. Return to anchor
        panel = panel.append(Component.text(buildNegativeSpace(BG_WIDTH)).font(HUD_FONT));

        // 4. Move right for text indent
        panel = panel.append(Component.text(buildPositiveSpace(textIndent)).font(HUD_FONT));

        // 5. Render top line (ability name)
        String topText = notification.displayName;
        if (topText.length() > 20) topText = topText.substring(0, 20);
        panel = panel.append(Component.text(topText).color(NamedTextColor.WHITE).font(HUD_FONT));
        int topWidth = calculateWidth(topText);

        // 6. Return to anchor
        panel = panel.append(Component.text(buildNegativeSpace(textIndent + topWidth)).font(HUD_FONT));

        // 7. Move right for middle line text indent
        panel = panel.append(Component.text(buildPositiveSpace(textIndent)).font(HUD_FONT));

        // 8. Render middle line (cooldown/duration time)
        String typeLabel = notification.type == NotificationType.COOLDOWN ? "Cooldown" : "Duration";
        String middleRaw = typeLabel + ": " + String.format("%.1fs", remaining);
        String middleText = toMiddleLine(middleRaw);
        panel = panel.append(Component.text(middleText).color(NamedTextColor.WHITE).font(HUD_FONT));
        int middleWidth = calculateWidth(middleText);

        // 9. Return to anchor
        panel = panel.append(Component.text(buildNegativeSpace(textIndent + middleWidth)).font(HUD_FONT));

        // 10. Return to center (0) for clean state
        panel = panel.append(Component.text(buildPositiveSpace(notificationAnchor)).font(HUD_FONT));

        return panel;
    }

    // ========== ABILITY UI RENDERING (CENTER-BASED) ==========

    /**
     * Build AbilityUI panel with centered rows.
     * Pattern for each row: back half, render, back half.
     * Where "half" = half the width of that specific row's text.
     */
    private Component buildAbilityUIPanel(List<String> abilityTexts) {
        Component panel = Component.empty();

        int numRows = Math.min(abilityTexts.size(), 5);

        for (int row = 0; row < numRows; row++) {
            String text = abilityTexts.get(row);
            if (text == null || text.isEmpty()) continue;

            // Strip color codes for width calculation
            String stripped = text.replaceAll("\u00A7[0-9a-fk-or]", "");

            // Convert to row-specific characters (different ascent per row)
            String rowText = toRowLine(stripped, row);

            // Calculate width using actual character widths from bitmap analysis
            int width = calculateWidth(stripped);
            int halfWidth = width / 2;

            // Debug: log calculations
            if (debug) {
                plugin.getLogger().info("[AbilityUI] Row " + row + ": '" + stripped +
                    "' width=" + width + " half=" + halfWidth);
            }

            // Pattern: back half, render, back remaining half
            // Cursor starts at 0 (center of boss bar)

            // 1. Back by half width (rounds down for odd widths)
            panel = panel.append(Component.text(buildNegativeSpace(halfWidth)).font(HUD_FONT));

            // 2. Render text (cursor advances by 'width' pixels)
            panel = panel.append(Component.text(rowText).color(NamedTextColor.WHITE).font(HUD_FONT));

            // 3. Back by remaining half (width - halfWidth handles odd widths)
            // After render: cursor at -halfWidth + width
            // Need to go back (width - halfWidth) to reach 0
            panel = panel.append(Component.text(buildNegativeSpace(width - halfWidth)).font(HUD_FONT));
        }

        return panel;
    }

    /**
     * Convert text to row-specific characters.
     * Row 0 = ASCII (ascent -6)
     * Row 1-4 = 0xA100-0xA400 series (different ascents)
     */
    private String toRowLine(String text, int row) {
        if (row == 0) {
            return text;  // ASCII
        }

        int base;
        switch (row) {
            case 1: base = 0xA100; break;
            case 2: base = 0xA200; break;
            case 3: base = 0xA300; break;
            case 4: base = 0xA400; break;
            default: return text;
        }

        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append((char)(base + c));
        }
        return sb.toString();
    }

    /**
     * Convert text to middle line characters (E100 series, ascent -18).
     */
    private String toMiddleLine(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(toMiddleLineChar(c));
        }
        return sb.toString();
    }

    private char toMiddleLineChar(char c) {
        if (c == ' ') return '\uE100';
        if (c == '!') return '\uE101';
        if (c == '\'') return '\uE102';
        if (c == '(') return '\uE103';
        if (c == ')') return '\uE104';
        if (c == '-') return '\uE105';
        if (c == '.') return '\uE106';
        if (c == ':') return '\uE107';
        if (c == '?') return '\uE108';
        if (c == '[') return '\uE109';
        if (c == ']') return '\uE10A';
        if (c == '|') return '\uE10B';
        if (c >= '0' && c <= '9') return (char)('\uE130' + (c - '0'));
        if (c >= 'A' && c <= 'Z') return (char)('\uE141' + (c - 'A'));
        if (c >= 'a' && c <= 'z') return (char)('\uE161' + (c - 'a'));
        return '\uE100';  // Space fallback
    }

    /**
     * Get background character based on progress (E000 series).
     */
    private char getBackgroundChar(double progress) {
        int percent = (int)(progress * 100);
        int index;
        if (percent >= 98) index = 20;
        else if (percent >= 93) index = 19;
        else if (percent >= 88) index = 18;
        else if (percent >= 83) index = 17;
        else if (percent >= 78) index = 16;
        else if (percent >= 73) index = 15;
        else if (percent >= 68) index = 14;
        else if (percent >= 63) index = 13;
        else if (percent >= 58) index = 12;
        else if (percent >= 53) index = 11;
        else if (percent >= 48) index = 10;
        else if (percent >= 43) index = 9;
        else if (percent >= 38) index = 8;
        else if (percent >= 33) index = 7;
        else if (percent >= 28) index = 6;
        else if (percent >= 23) index = 5;
        else if (percent >= 18) index = 4;
        else if (percent >= 13) index = 3;
        else if (percent >= 8) index = 2;
        else if (percent >= 3) index = 1;
        else index = 0;
        return (char)(0xE000 + index);
    }

    // ========== CONFIG & TICK ==========

    private void loadConfig() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("notifications");
        if (section == null) return;

        enabled = section.getBoolean("enabled", true);
        maxNotificationBars = Math.min(section.getInt("max-bars", 5) - 1, 4);
        updateIntervalTicks = section.getInt("update-interval", 2);
        debug = plugin.getConfig().getBoolean("settings.debug", false);
        notificationAnchor = section.getInt("left-shift-offset", 350);
        textIndent = section.getInt("text-indent", 10);
    }

    private void startTickTask() {
        if (tickTask != null) tickTask.cancel();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickUpdate, updateIntervalTicks, updateIntervalTicks);
    }

    private void tickUpdate() {
        if (!enabled) return;

        for (Map.Entry<UUID, List<NotificationBar>> entry : new HashMap<>(notificationBars).entrySet()) {
            UUID playerId = entry.getKey();
            List<NotificationBar> bars = entry.getValue();

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                cleanupPlayer(playerId);
                continue;
            }

            Iterator<NotificationBar> iter = bars.iterator();
            while (iter.hasNext()) {
                NotificationBar bar = iter.next();

                if (bar.data.isExpired()) {
                    handleExpiration(player, bar.data);
                    player.hideBossBar(bar.bossBar);
                    iter.remove();
                } else {
                    updateNotificationBar(bar);
                }
            }

            if (bars.isEmpty()) {
                notificationBars.remove(playerId);
            }
        }
    }

    private void updateNotificationBar(NotificationBar bar) {
        Component title = buildNotificationPanel(bar.data, bar.slot);
        bar.bossBar.name(title);
        bar.bossBar.progress((float) bar.data.getProgress());
    }

    private void handleExpiration(Player player, NotificationData notification) {
        if (notification.type == NotificationType.COOLDOWN && !notification.notifiedReady) {
            notification.notifiedReady = true;
            CooldownNotifier notifier = plugin.getCooldownNotifier();
            if (notifier != null) {
                notifier.notifyReady(player, notification.displayName);
            }
        }
    }

    // ========== PUBLIC API - NOTIFICATIONS ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void startCooldown(Player player, String abilityId, String displayName, double seconds, org.bukkit.boss.BarColor colorOverride) {
        if (!enabled || player == null || seconds <= 0) return;
        startNotification(player, abilityId, displayName, seconds, NotificationType.COOLDOWN);
    }

    public void startDuration(Player player, String buffId, String displayName, double seconds, org.bukkit.boss.BarColor colorOverride) {
        if (!enabled || player == null || seconds <= 0) return;
        startNotification(player, buffId, displayName, seconds, NotificationType.DURATION);
    }

    private void startNotification(Player player, String notificationId, String displayName,
                                   double seconds, NotificationType type) {
        UUID playerId = player.getUniqueId();

        cancelNotification(player, notificationId);

        List<NotificationBar> bars = notificationBars.computeIfAbsent(playerId, k -> new ArrayList<>());

        if (bars.size() >= maxNotificationBars) {
            NotificationBar oldest = null;
            double minRemaining = Double.MAX_VALUE;
            for (NotificationBar bar : bars) {
                double remaining = bar.data.getRemainingSeconds();
                if (remaining < minRemaining) {
                    minRemaining = remaining;
                    oldest = bar;
                }
            }
            if (oldest != null) {
                player.hideBossBar(oldest.bossBar);
                bars.remove(oldest);
            }
        }

        int slot = bars.size();
        NotificationData data = new NotificationData(type, notificationId, displayName, seconds);
        BossBar bossBar = BossBar.bossBar(
            Component.empty(),
            1.0f,
            BossBar.Color.WHITE,
            BossBar.Overlay.PROGRESS
        );

        NotificationBar bar = new NotificationBar(bossBar, data, slot);
        bars.add(bar);
        player.showBossBar(bossBar);
        updateNotificationBar(bar);

        if (debug) {
            plugin.getLogger().info("[Notifications] Started " + type + " for " + player.getName() +
                ": " + displayName + " (" + seconds + "s)");
        }
    }

    public void cancelNotification(Player player, String notificationId) {
        if (player == null) return;

        List<NotificationBar> bars = notificationBars.get(player.getUniqueId());
        if (bars != null) {
            Iterator<NotificationBar> iter = bars.iterator();
            while (iter.hasNext()) {
                NotificationBar bar = iter.next();
                if (bar.data.notificationId.equals(notificationId)) {
                    player.hideBossBar(bar.bossBar);
                    iter.remove();
                    break;
                }
            }
        }
    }

    public void cancelAllNotifications(Player player) {
        if (player == null) return;

        List<NotificationBar> bars = notificationBars.remove(player.getUniqueId());
        if (bars != null) {
            for (NotificationBar bar : bars) {
                player.hideBossBar(bar.bossBar);
            }
        }
    }

    // ========== PUBLIC API - ABILITY UI ==========

    public void activateAbilityUI(Player player, List<String> abilityTexts) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();

        abilityUITexts.put(playerId, new ArrayList<>(abilityTexts));

        BossBar bar = abilityUIBars.get(playerId);
        boolean isNew = (bar == null);

        if (isNew) {
            bar = BossBar.bossBar(
                Component.empty(),
                1.0f,
                BossBar.Color.WHITE,
                BossBar.Overlay.PROGRESS
            );
            abilityUIBars.put(playerId, bar);
        }

        // Use custom font for AbilityUI panel
        Component panel = buildAbilityUIPanel(abilityTexts);
        bar.name(panel);

        // Ensure AbilityUI is on top by showing it first, then re-showing notifications
        // Boss bar order: first shown = top, last shown = bottom
        if (isNew) {
            player.showBossBar(bar);
            // Re-show any existing notification bars so they appear below AbilityUI
            reorderNotificationsBelow(player);
        }

        if (debug) {
            plugin.getLogger().info("[AbilityUI] Activated for " + player.getName() + " with " + abilityTexts.size() + " texts");
        }
    }

    /**
     * Re-show all notification bars so they appear below AbilityUI.
     * Called after showing AbilityUI to maintain proper ordering.
     */
    private void reorderNotificationsBelow(Player player) {
        List<NotificationBar> bars = notificationBars.get(player.getUniqueId());
        if (bars != null && !bars.isEmpty()) {
            for (NotificationBar nb : bars) {
                player.hideBossBar(nb.bossBar);
                player.showBossBar(nb.bossBar);
            }
        }
    }

    public void deactivateAbilityUI(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();

        BossBar bar = abilityUIBars.remove(playerId);
        if (bar != null) {
            player.hideBossBar(bar);
        }
        abilityUITexts.remove(playerId);

        if (debug) {
            plugin.getLogger().info("[AbilityUI] Deactivated for " + player.getName());
        }
    }

    public boolean isAbilityUIActive(Player player) {
        if (player == null) return false;
        return abilityUIBars.containsKey(player.getUniqueId());
    }

    public void updateAbilityUISlot(Player player, int slot, String abilityText) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();

        List<String> texts = abilityUITexts.get(playerId);
        BossBar bar = abilityUIBars.get(playerId);

        if (texts != null && bar != null) {
            while (texts.size() <= slot) {
                texts.add("");
            }
            texts.set(slot, abilityText);
            bar.name(buildAbilityUIPanel(texts));
        }
    }

    // ========== CLEANUP ==========

    private void cleanupPlayer(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);

        BossBar abilityBar = abilityUIBars.remove(playerId);
        if (abilityBar != null && player != null) {
            player.hideBossBar(abilityBar);
        }
        abilityUITexts.remove(playerId);

        List<NotificationBar> bars = notificationBars.remove(playerId);
        if (bars != null && player != null) {
            for (NotificationBar bar : bars) {
                player.hideBossBar(bar.bossBar);
            }
        }
    }

    public void cleanup(Player player) {
        if (player != null) {
            cleanupPlayer(player.getUniqueId());
        }
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        for (Map.Entry<UUID, BossBar> entry : abilityUIBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        abilityUIBars.clear();
        abilityUITexts.clear();

        for (Map.Entry<UUID, List<NotificationBar>> entry : notificationBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                for (NotificationBar bar : entry.getValue()) {
                    player.hideBossBar(bar.bossBar);
                }
            }
        }
        notificationBars.clear();

        plugin.getLogger().info("[HUD] BossBar manager shutdown");
    }

    public void reload() {
        loadConfig();
        if (tickTask != null) tickTask.cancel();
        startTickTask();
    }

    public static String formatAbilityName(String id) {
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
