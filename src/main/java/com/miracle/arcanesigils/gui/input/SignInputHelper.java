package com.miracle.arcanesigils.gui.input;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.SignGUIAction;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.Consumer;

/**
 * Helper class for requesting text/number input from players via Sign GUI.
 * Uses the SignGUI library for packet-based sign editing.
 */
public class SignInputHelper {

    private final JavaPlugin plugin;

    public SignInputHelper(Plugin plugin) {
        this.plugin = (JavaPlugin) plugin;
    }

    /**
     * Get the plugin instance.
     */
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * Request validated text input from a player via Sign GUI.
     * Re-prompts if validation fails, with error message.
     *
     * @param player The player to request input from
     * @param title Title shown on the sign
     * @param defaultValue Default value pre-filled
     * @param validation Validation rules to apply
     * @param onComplete Called with valid input
     * @param onCancel Called if player cancels
     */
    public void requestText(Player player, String title, String defaultValue,
                           InputValidation validation,
                           Consumer<String> onComplete, Runnable onCancel) {
        requestTextInternal(player, title, defaultValue, validation, onComplete, onCancel);
    }

    /**
     * Request text input from a player via Sign GUI (no validation).
     */
    public void requestText(Player player, String title, String defaultValue,
                           Consumer<String> onComplete, Runnable onCancel) {
        requestTextInternal(player, title, defaultValue, null, onComplete, onCancel);
    }

    /**
     * Internal method for text input with optional validation.
     */
    private void requestTextInternal(Player player, String title, String defaultValue,
                                    InputValidation validation,
                                    Consumer<String> onComplete, Runnable onCancel) {
        try {
            SignGUI.builder()
                .setLines(
                    defaultValue != null ? defaultValue : "",
                    "^^^^^^^^^^^^^^^^",
                    title.length() > 15 ? title.substring(0, 15) : title,
                    ""
                )
                .setColor(DyeColor.BLACK)
                .setHandler((p, result) -> {
                    // Get input from line 0 (top line where user types)
                    String input = result.getLine(0).trim();

                    if (input.isEmpty() || input.equalsIgnoreCase("cancel")) {
                        p.sendMessage("§cInput cancelled.");
                        if (onCancel != null) {
                            // Run synchronously
                            return List.of(SignGUIAction.runSync(plugin, () -> onCancel.run()));
                        }
                        return List.of();
                    }

                    // Apply validation if provided
                    if (validation != null) {
                        // Sanitize input (strip color codes if not allowed)
                        input = validation.sanitize(input);

                        InputValidation.ValidationResult validationResult = validation.validate(input);
                        if (!validationResult.isValid()) {
                            p.sendMessage("§c" + validationResult.getErrorMessage());
                            // Re-prompt with the invalid input as default
                            final String invalidInput = input;
                            return List.of(SignGUIAction.runSync(plugin, () -> {
                                requestTextInternal(p, title, invalidInput, validation, onComplete, onCancel);
                            }));
                        }
                    }

                    final String finalInput = input;
                    p.sendMessage("§aInput received: §f" + finalInput);
                    // Run synchronously to avoid async inventory issues
                    return List.of(SignGUIAction.runSync(plugin, () -> onComplete.accept(finalInput)));
                })
                .build()
                .open(player);
        } catch (Exception e) {
            player.sendMessage("§cSign GUI failed: " + e.getMessage());
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }

    /**
     * Request multiline text input (3 lines) from a player via Sign GUI.
     * Used for descriptions that need multiple lines.
     */
    public void requestMultilineText(Player player, List<String> defaultLines,
                                     Consumer<List<String>> onComplete, Runnable onCancel) {
        try {
            // Prepare default lines (up to 3)
            String line0 = defaultLines.size() > 0 ? defaultLines.get(0) : "";
            String line1 = defaultLines.size() > 1 ? defaultLines.get(1) : "";
            String line2 = defaultLines.size() > 2 ? defaultLines.get(2) : "";

            SignGUI.builder()
                .setLines(
                    line0,
                    line1,
                    line2,
                    "§7§o~ description ~"
                )
                .setColor(DyeColor.BLACK)
                .setHandler((p, result) -> {
                    // Get input from lines 0, 1, 2
                    String input0 = result.getLine(0).trim();
                    String input1 = result.getLine(1).trim();
                    String input2 = result.getLine(2).trim();

                    // Collect non-empty lines
                    List<String> lines = new java.util.ArrayList<>();
                    if (!input0.isEmpty()) lines.add(input0);
                    if (!input1.isEmpty()) lines.add(input1);
                    if (!input2.isEmpty()) lines.add(input2);

                    if (lines.isEmpty()) {
                        p.sendMessage("§cDescription cleared.");
                    } else {
                        p.sendMessage("§aDescription updated (" + lines.size() + " lines)");
                    }

                    // Run synchronously to avoid async inventory issues
                    return List.of(SignGUIAction.runSync(plugin, () -> onComplete.accept(lines)));
                })
                .build()
                .open(player);
        } catch (Exception e) {
            player.sendMessage("§cSign GUI failed: " + e.getMessage());
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }

    /**
     * Request number input from a player via Sign GUI.
     */
    public void requestNumber(Player player, String title, double defaultValue,
                             double min, double max,
                             Consumer<Double> onComplete, Runnable onCancel) {
        try {
            SignGUI.builder()
                .setLines(
                    formatNumber(defaultValue),
                    "^^^^^^^^^^^^^^^^",
                    title.length() > 15 ? title.substring(0, 15) : title,
                    ""
                )
                .setColor(DyeColor.BLACK)
                .setHandler((p, result) -> {
                    // Get input from line 0 (top line where user types)
                    String input = result.getLine(0).trim();

                    if (input.isEmpty() || input.equalsIgnoreCase("cancel")) {
                        p.sendMessage("§cInput cancelled.");
                        if (onCancel != null) {
                            return List.of(SignGUIAction.runSync(plugin, () -> onCancel.run()));
                        }
                        return List.of();
                    }

                    try {
                        double value = Double.parseDouble(input);

                        if (value < min || value > max) {
                            p.sendMessage("§cNumber must be between " + min + " and " + max + "!");
                            if (onCancel != null) {
                                return List.of(SignGUIAction.runSync(plugin, () -> onCancel.run()));
                            }
                            return List.of();
                        }

                        p.sendMessage("§aInput received: §f" + value);
                        // Run synchronously
                        return List.of(SignGUIAction.runSync(plugin, () -> onComplete.accept(value)));

                    } catch (NumberFormatException e) {
                        p.sendMessage("§cInvalid number!");
                        if (onCancel != null) {
                            return List.of(SignGUIAction.runSync(plugin, () -> onCancel.run()));
                        }
                        return List.of();
                    }
                })
                .build()
                .open(player);
        } catch (Exception e) {
            player.sendMessage("§cSign GUI failed: " + e.getMessage());
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }

    /**
     * Request integer input from a player via Sign GUI.
     */
    public void requestInteger(Player player, String title, int defaultValue,
                              int min, int max,
                              Consumer<Integer> onComplete, Runnable onCancel) {
        requestNumber(player, title, defaultValue, min, max,
            value -> onComplete.accept(value.intValue()),
            onCancel);
    }

    /**
     * Format a number for display - shows "1" instead of "1.0" for whole numbers,
     * but keeps decimals like "1.5" or "3.25" when needed.
     */
    private String formatNumber(double value) {
        if (value == (long) value) {
            // Whole number - show without decimal
            return String.valueOf((long) value);
        } else {
            // Has decimal component - show as-is
            return String.valueOf(value);
        }
    }
}
