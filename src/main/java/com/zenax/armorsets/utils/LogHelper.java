package com.zenax.armorsets.utils;

import com.zenax.armorsets.ArmorSetsPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized logging utility for the ArmorSets plugin.
 * Provides consistent logging across all classes and replaces
 * direct System.err.println() calls with proper logger usage.
 */
public final class LogHelper {

    private LogHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    private static Logger logger;
    private static boolean debugEnabled = false;

    /**
     * Initialize the logger with the plugin instance.
     * Should be called during plugin enable.
     *
     * @param plugin The ArmorSetsPlugin instance
     */
    public static void init(ArmorSetsPlugin plugin) {
        logger = plugin.getLogger();
        refreshDebugSetting(plugin);
    }

    /**
     * Refresh the debug setting from config.
     * Call this after config reloads.
     *
     * @param plugin The ArmorSetsPlugin instance
     */
    public static void refreshDebugSetting(ArmorSetsPlugin plugin) {
        debugEnabled = plugin.getConfigManager().getMainConfig()
                .getBoolean("settings.debug", false);
    }

    /**
     * Log an info message.
     *
     * @param message The message to log
     */
    public static void info(String message) {
        if (logger != null) {
            logger.info(message);
        }
    }

    /**
     * Log a formatted info message.
     *
     * @param format The format string
     * @param args   The format arguments
     */
    public static void info(String format, Object... args) {
        if (logger != null) {
            logger.info(String.format(format, args));
        }
    }

    /**
     * Log a warning message.
     *
     * @param message The message to log
     */
    public static void warning(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    /**
     * Log a formatted warning message.
     *
     * @param format The format string
     * @param args   The format arguments
     */
    public static void warning(String format, Object... args) {
        if (logger != null) {
            logger.warning(String.format(format, args));
        }
    }

    /**
     * Log a warning message with an exception.
     *
     * @param message   The message to log
     * @param throwable The exception
     */
    public static void warning(String message, Throwable throwable) {
        if (logger != null) {
            logger.log(Level.WARNING, message, throwable);
        }
    }

    /**
     * Log a severe/error message.
     *
     * @param message The message to log
     */
    public static void severe(String message) {
        if (logger != null) {
            logger.severe(message);
        }
    }

    /**
     * Log a formatted severe/error message.
     *
     * @param format The format string
     * @param args   The format arguments
     */
    public static void severe(String format, Object... args) {
        if (logger != null) {
            logger.severe(String.format(format, args));
        }
    }

    /**
     * Log a severe/error message with an exception.
     *
     * @param message   The message to log
     * @param throwable The exception
     */
    public static void severe(String message, Throwable throwable) {
        if (logger != null) {
            logger.log(Level.SEVERE, message, throwable);
        }
    }

    /**
     * Log a debug message (only if debug is enabled in config).
     *
     * @param message The message to log
     */
    public static void debug(String message) {
        if (debugEnabled && logger != null) {
            logger.info("[DEBUG] " + message);
        }
    }

    /**
     * Log a formatted debug message (only if debug is enabled in config).
     *
     * @param format The format string
     * @param args   The format arguments
     */
    public static void debug(String format, Object... args) {
        if (debugEnabled && logger != null) {
            logger.info("[DEBUG] " + String.format(format, args));
        }
    }

    /**
     * Check if debug logging is enabled.
     *
     * @return true if debug is enabled
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Log an unknown condition warning.
     * Used by ConditionManager for unknown condition types.
     *
     * @param conditionType The unknown condition type
     */
    public static void warnUnknownCondition(String conditionType) {
        warning("[Conditions] Unknown condition type: %s", conditionType);
    }

    /**
     * Log a condition evaluation error.
     *
     * @param condition The condition string
     * @param error     The error that occurred
     */
    public static void warnConditionError(String condition, Throwable error) {
        warning("[Conditions] Error evaluating condition: " + condition, error);
    }

    /**
     * Log an effect execution error.
     *
     * @param effectString The effect string
     * @param error        The error that occurred
     */
    public static void warnEffectError(String effectString, Throwable error) {
        warning("[Effects] Failed to execute effect: " + effectString, error);
    }

    /**
     * Log a config loading error.
     *
     * @param fileName The config file name
     * @param error    The error that occurred
     */
    public static void warnConfigError(String fileName, Throwable error) {
        warning("[Config] Failed to load config: " + fileName, error);
    }

    /**
     * Log a successful effect execution (debug level).
     *
     * @param effectType  The effect type
     * @param target      The target description
     * @param details     Additional details
     */
    public static void debugEffect(String effectType, String target, String details) {
        debug("[Effects] %s on %s: %s", effectType, target, details);
    }

    /**
     * Log a signal activation (debug level).
     *
     * @param signalType The signal type
     * @param playerId    The player identifier
     */
    public static void debugSignal(String signalType, String playerId) {
        debug("[Signals] %s activated for %s", signalType, playerId);
    }
}
