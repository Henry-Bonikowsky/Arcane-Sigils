package com.miracle.arcanesigils.effects;

/**
 * Describes a single parameter for an effect.
 * Effects declare their params by returning a list of these from getParamDefinitions().
 * The GUI reads these to render the param editor — no more giant switch statement.
 *
 * <p>Maps directly to EffectParamHandler.ParamInfo for GUI rendering.</p>
 */
public record ParamDefinition(
    String key,
    String displayName,
    ParamType type,
    Object defaultValue,
    double min,
    double max,
    String[] cycleOptions,
    String showWhen
) {

    /** Parameter types — mirrors EffectParamHandler.ParamType. */
    public enum ParamType {
        NUMBER,
        TEXT,
        TARGET,
        PARTICLE_BROWSER,
        SOUND_BROWSER,
        ATTRIBUTE_BROWSER,
        CYCLE
    }

    // --- Factory methods ---

    /** Numeric parameter with min/max bounds. */
    public static ParamDefinition number(String key, String displayName, double defaultValue, double min, double max) {
        return new ParamDefinition(key, displayName, ParamType.NUMBER, defaultValue, min, max, null, null);
    }

    /** Numeric parameter (0–100 default range). */
    public static ParamDefinition number(String key, String displayName, double defaultValue) {
        return number(key, displayName, defaultValue, 0, 100);
    }

    /** Text input parameter. */
    public static ParamDefinition text(String key, String displayName, String defaultValue) {
        return new ParamDefinition(key, displayName, ParamType.TEXT, defaultValue, 0, 0, null, null);
    }

    /** Target selector parameter (cycles through target options). */
    public static ParamDefinition target(String key, String displayName, String defaultValue, String... options) {
        String[] opts = options.length > 0 ? options : new String[]{"@Victim", "@Self", "@Nearby:5", "@Nearby:10"};
        return new ParamDefinition(key, displayName, ParamType.TARGET, defaultValue, 0, 0, opts, null);
    }

    /** Cycle parameter — clicks through a fixed set of string options. */
    public static ParamDefinition cycle(String key, String displayName, String defaultValue, String... options) {
        return new ParamDefinition(key, displayName, ParamType.CYCLE, defaultValue, 0, 0, options, null);
    }

    /** Opens the particle browser GUI. */
    public static ParamDefinition particleBrowser(String key, String displayName, String defaultValue) {
        return new ParamDefinition(key, displayName, ParamType.PARTICLE_BROWSER, defaultValue, 0, 0, null, null);
    }

    /** Opens the sound browser GUI. */
    public static ParamDefinition soundBrowser(String key, String displayName, String defaultValue) {
        return new ParamDefinition(key, displayName, ParamType.SOUND_BROWSER, defaultValue, 0, 0, null, null);
    }

    /** Opens the attribute browser GUI. */
    public static ParamDefinition attributeBrowser(String key, String displayName, String defaultValue) {
        return new ParamDefinition(key, displayName, ParamType.ATTRIBUTE_BROWSER, defaultValue, 0, 0, null, null);
    }

    // --- Conditional variants (only visible when another param has a specific value) ---

    /** Numeric parameter, only shown when condition is met. */
    public static ParamDefinition numberWhen(String key, String displayName, double defaultValue, double min, double max, String showWhen) {
        return new ParamDefinition(key, displayName, ParamType.NUMBER, defaultValue, min, max, null, showWhen);
    }

    /** Text parameter, only shown when condition is met. */
    public static ParamDefinition textWhen(String key, String displayName, String defaultValue, String showWhen) {
        return new ParamDefinition(key, displayName, ParamType.TEXT, defaultValue, 0, 0, null, showWhen);
    }

    /** Cycle parameter, only shown when condition is met. */
    public static ParamDefinition cycleWhen(String key, String displayName, String defaultValue, String showWhen, String... options) {
        return new ParamDefinition(key, displayName, ParamType.CYCLE, defaultValue, 0, 0, options, showWhen);
    }

    /** Target parameter, only shown when condition is met. */
    public static ParamDefinition targetWhen(String key, String displayName, String defaultValue, String showWhen, String... options) {
        String[] opts = options.length > 0 ? options : new String[]{"@Victim", "@Self", "@Nearby:5", "@Nearby:10"};
        return new ParamDefinition(key, displayName, ParamType.TARGET, defaultValue, 0, 0, opts, showWhen);
    }
}
