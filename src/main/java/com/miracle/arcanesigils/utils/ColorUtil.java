package com.miracle.arcanesigils.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extended color formatting utility for the menu system.
 * Provides methods for gradients, centered text, and decorative formatting.
 */
public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[A-Fa-f0-9]{6}):(#[A-Fa-f0-9]{6})>(.+?)</gradient>");

    private ColorUtil() {
        // Utility class
    }

    /**
     * Center a message for chat display.
     * Assumes default Minecraft chat width of 154 pixels.
     *
     * @param message The message to center
     * @return The centered message with padding
     */
    public static String centerMessage(String message) {
        if (message == null || message.isEmpty()) return message;

        // Strip color codes for width calculation
        String stripped = ChatColor.stripColor(colorize(message));
        int messagePxSize = 0;

        boolean previousCode = false;
        boolean isBold = false;

        for (char c : stripped.toCharArray()) {
            if (c == '\u00A7') {
                previousCode = true;
            } else if (previousCode) {
                previousCode = false;
                isBold = (c == 'l' || c == 'L');
            } else {
                messagePxSize += getCharWidth(c, isBold);
            }
        }

        int halvedMessageSize = messagePxSize / 2;
        int toCompensate = 154 - halvedMessageSize; // 154 = half of chat width (308)
        int spaceLength = 4; // Default space width

        int compensated = 0;
        StringBuilder sb = new StringBuilder();
        while (compensated < toCompensate) {
            sb.append(" ");
            compensated += spaceLength;
        }

        return sb + message;
    }

    /**
     * Get the pixel width of a character.
     *
     * @param c      The character
     * @param isBold Whether the character is bold
     * @return The pixel width
     */
    private static int getCharWidth(char c, boolean isBold) {
        int length = switch (c) {
            case ' ', 'I', 'i', '!', '.', ',', ';', ':', '\'', '|' -> 2;
            case 'l', '`' -> 3;
            case '*', '"', '(', ')', '{', '}', '<', '>', '[', ']', 't', 'k' -> 4;
            case 'f' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
        if (isBold && c != ' ') length++;
        return length;
    }

    /**
     * Colorize a string with legacy and hex color codes.
     *
     * @param text The text to colorize
     * @return The colorized text
     */
    public static String colorize(String text) {
        if (text == null) return "";

        // Handle hex colors &#RRGGBB
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (hexMatcher.find()) {
            String hex = hexMatcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (char c : hex.toCharArray()) {
                replacement.append("\u00A7").append(c);
            }
            hexMatcher.appendReplacement(sb, replacement.toString());
        }
        hexMatcher.appendTail(sb);
        text = sb.toString();

        // Handle legacy color codes
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Create a gradient text effect between two colors.
     *
     * @param text   The text to apply gradient to
     * @param color1 The starting hex color (e.g., "#FF0000")
     * @param color2 The ending hex color (e.g., "#0000FF")
     * @return The text with gradient applied
     */
    public static String createGradient(String text, String color1, String color2) {
        if (text == null || text.isEmpty()) return text;

        Color start = Color.decode(color1);
        Color end = Color.decode(color2);

        StringBuilder result = new StringBuilder();
        int length = text.length();

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                result.append(' ');
                continue;
            }

            double ratio = length > 1 ? (double) i / (length - 1) : 0;

            int red = (int) (start.getRed() + ratio * (end.getRed() - start.getRed()));
            int green = (int) (start.getGreen() + ratio * (end.getGreen() - start.getGreen()));
            int blue = (int) (start.getBlue() + ratio * (end.getBlue() - start.getBlue()));

            String hex = String.format("%02x%02x%02x", red, green, blue);
            result.append("&#").append(hex).append(c);
        }

        return colorize(result.toString());
    }

    /**
     * Create a rainbow gradient effect on text.
     *
     * @param text The text to apply rainbow to
     * @return The text with rainbow colors
     */
    public static String createRainbow(String text) {
        if (text == null || text.isEmpty()) return text;

        StringBuilder result = new StringBuilder();
        int length = text.length();
        float hue = 0;
        float hueStep = 1.0f / Math.max(length, 1);

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                result.append(' ');
                continue;
            }

            Color color = Color.getHSBColor(hue, 1.0f, 1.0f);
            String hex = String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
            result.append("&#").append(hex).append(c);

            hue += hueStep;
            if (hue > 1.0f) hue -= 1.0f;
        }

        return colorize(result.toString());
    }

    /**
     * Create a decorative header line.
     *
     * @param title The title to display
     * @param color The color code for the line (e.g., "§d")
     * @return The formatted header
     */
    public static String createHeader(String title, String color) {
        String line = color + "§m" + "          " + "§r " + color + "§l" + title + " " + color + "§m" + "          ";
        return colorize(line);
    }

    /**
     * Create a simple divider line.
     *
     * @param color The color code (e.g., "§8")
     * @return The divider line
     */
    public static String createDivider(String color) {
        return colorize(color + "§m" + "                                        ");
    }

    /**
     * Create a box-style message with borders.
     *
     * @param lines      The content lines
     * @param borderChar The character for borders
     * @param color      The border color
     * @return The formatted box message
     */
    public static List<String> createBox(List<String> lines, char borderChar, String color) {
        List<String> result = new ArrayList<>();

        // Find max width
        int maxWidth = 0;
        for (String line : lines) {
            int width = ChatColor.stripColor(colorize(line)).length();
            if (width > maxWidth) maxWidth = width;
        }

        String border = color + String.valueOf(borderChar).repeat(maxWidth + 4);
        result.add(colorize(border));

        for (String line : lines) {
            String stripped = ChatColor.stripColor(colorize(line));
            int padding = maxWidth - stripped.length();
            result.add(colorize(color + borderChar + " " + line + " ".repeat(padding) + " " + color + borderChar));
        }

        result.add(colorize(border));
        return result;
    }

    /**
     * Format a progress bar with colors.
     *
     * @param current    Current value
     * @param max        Maximum value
     * @param length     Bar length in characters
     * @param filledChar Character for filled portion
     * @param emptyChar  Character for empty portion
     * @param filledColor Color for filled portion (e.g., "§a")
     * @param emptyColor  Color for empty portion (e.g., "§8")
     * @return The formatted progress bar
     */
    public static String createProgressBar(double current, double max, int length,
                                           char filledChar, char emptyChar,
                                           String filledColor, String emptyColor) {
        double percent = Math.min(1.0, Math.max(0.0, current / max));
        int filled = (int) (percent * length);

        StringBuilder bar = new StringBuilder();
        bar.append(filledColor);
        bar.append(String.valueOf(filledChar).repeat(filled));
        bar.append(emptyColor);
        bar.append(String.valueOf(emptyChar).repeat(length - filled));

        return colorize(bar.toString());
    }

    /**
     * Create a Component with the specified color.
     *
     * @param text  The text content
     * @param color The text color
     * @return The colored Component
     */
    public static Component createComponent(String text, NamedTextColor color) {
        return Component.text(text).color(color);
    }

    /**
     * Create a Component with hex color.
     *
     * @param text The text content
     * @param hex  The hex color (e.g., "#FF5555")
     * @return The colored Component
     */
    public static Component createComponent(String text, String hex) {
        TextColor color = TextColor.fromHexString(hex);
        return Component.text(text).color(color);
    }

    /**
     * Create a bold Component.
     *
     * @param text  The text content
     * @param color The text color
     * @return The bold Component
     */
    public static Component createBoldComponent(String text, NamedTextColor color) {
        return Component.text(text)
                .color(color)
                .decoration(TextDecoration.BOLD, true);
    }

    /**
     * Strip all color codes from text.
     *
     * @param text The text to strip
     * @return The stripped text
     */
    public static String stripColors(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(colorize(text));
    }

    /**
     * Translate color codes while preserving format.
     *
     * @param text The text to translate
     * @return The translated text
     */
    public static String translate(String text) {
        return colorize(text);
    }

    // Common color presets
    public static final String PRIMARY = "§d";      // Light purple - plugin branding
    public static final String SECONDARY = "§b";    // Aqua - secondary elements
    public static final String SUCCESS = "§a";      // Green - success messages
    public static final String ERROR = "§c";        // Red - error messages
    public static final String WARNING = "§e";      // Yellow - warning messages
    public static final String INFO = "§7";         // Gray - informational text
    public static final String MUTED = "§8";        // Dark gray - muted/disabled text
    public static final String HIGHLIGHT = "§f";    // White - highlighted text
    public static final String HEADER = "§6";       // Gold - headers

    // Hex color presets for the plugin theme
    public static final String HEX_PRIMARY = "#AA55FF";
    public static final String HEX_SECONDARY = "#55FFFF";
    public static final String HEX_ACCENT = "#FF55FF";
}
