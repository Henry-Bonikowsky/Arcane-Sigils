package com.zenax.armorsets.constants;

/**
 * Constants for task scheduling intervals throughout the plugin.
 * All values are in ticks unless otherwise noted (20 ticks = 1 second).
 */
public final class TaskIntervals {

    private TaskIntervals() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ===== ARMOR AND SET DETECTION =====

    /**
     * Interval for checking armor changes (in ticks).
     * Lower values = more responsive but higher CPU usage.
     * Default: 5 ticks (250ms)
     */
    public static final long ARMOR_CHECK_TICKS = 5L;

    /**
     * Initial delay before starting the armor check task (in ticks).
     */
    public static final long ARMOR_CHECK_INITIAL_DELAY = 10L;

    // ===== EFFECT PROCESSING =====

    /**
     * Default interval for static/passive effect application (in ticks).
     * This is overridable via config: settings.effect-check-interval
     * Default: 20 ticks (1 second)
     */
    public static final int DEFAULT_STATIC_EFFECT_INTERVAL = 20;

    /**
     * Default interval for TICK signal processing (in ticks).
     * This is overridable via config: settings.tick-interval
     * Default: 1 tick
     */
    public static final int DEFAULT_TICK_SIGNAL_INTERVAL = 1;

    /**
     * Initial delay for effect tasks (in ticks).
     */
    public static final long EFFECT_TASK_INITIAL_DELAY = 20L;

    // ===== SESSION MANAGEMENT =====

    /**
     * Cleanup task interval for expired menu sessions (in ticks).
     * Runs every minute to clear stale sessions.
     */
    public static final long SESSION_CLEANUP_INTERVAL_TICKS = 20L * 60; // 1 minute

    /**
     * Session timeout duration (in milliseconds).
     * Sessions inactive for this long are considered expired.
     */
    public static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes
}
