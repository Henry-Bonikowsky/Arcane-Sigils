package com.zenax.armorsets.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for text formatting and colorization.
 */
public final class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    // Pattern for gradient tags: <gradient:#COLOR1:#COLOR2>text</gradient>
    private static final Pattern GRADIENT_PATTERN = Pattern.compile(
            "<gradient:(#[A-Fa-f0-9]{6}):(#[A-Fa-f0-9]{6})>(.+?)</gradient>"
    );

    // Pattern for hex colors: &#RRGGBB or <#RRGGBB>
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_TAG_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");

    private TextUtil() {}

    /**
     * Colorize a string with legacy color codes and hex colors.
     */
    public static String colorize(String text) {
        if (text == null) return "";

        // Handle gradient tags using MiniMessage
        if (text.contains("<gradient:") || text.contains("<rainbow>")) {
            return colorizeWithMiniMessage(text);
        }

        // Handle hex colors &#RRGGBB first, then legacy
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
     * Colorize text using MiniMessage for advanced formatting.
     */
    public static String colorizeWithMiniMessage(String text) {
        if (text == null) return "";

        try {
            Component component = MINI_MESSAGE.deserialize(text);
            return LegacyComponentSerializer.legacySection().serialize(component);
        } catch (Exception e) {
            // Fallback to simple colorization
            return ChatColor.translateAlternateColorCodes('&', text);
        }
    }

    /**
     * Parse text to Adventure Component.
     * Handles mixed legacy codes (&) and MiniMessage tags (<gradient:...>)
     */
    public static Component parseComponent(String text) {
        if (text == null) return Component.empty();

        try {
            // Check if text has MiniMessage tags
            boolean hasMiniMessage = text.contains("<gradient:") || text.contains("<color:") ||
                    text.contains("<bold>") || text.contains("<italic>") || text.contains("<rainbow>");

            if (hasMiniMessage) {
                // Convert legacy codes to MiniMessage format first
                String converted = convertLegacyToMiniMessage(text);
                return MINI_MESSAGE.deserialize(converted);
            }

            // Pure legacy format - add italic false
            String converted = convertLegacyToMiniMessage(text);
            return MINI_MESSAGE.deserialize(converted);
        } catch (Exception e) {
            // Final fallback
            return LEGACY_SERIALIZER.deserialize(text.replaceAll("<[^>]+>", ""));
        }
    }

    /**
     * Convert legacy color codes to MiniMessage format.
     */
    private static String convertLegacyToMiniMessage(String text) {
        // If text already contains MiniMessage tags, just return it
        if (text.contains("<gradient:") || text.contains("<color:") || text.contains("<rainbow>") || text.contains("<i:false>")) {
            return text;
        }

        // Map of legacy codes to MiniMessage tags, with italic disabled
        text = text.replace("&0", "<black><i:false>");
        text = text.replace("&1", "<dark_blue><i:false>");
        text = text.replace("&2", "<dark_green><i:false>");
        text = text.replace("&3", "<dark_aqua><i:false>");
        text = text.replace("&4", "<dark_red><i:false>");
        text = text.replace("&5", "<dark_purple><i:false>");
        text = text.replace("&6", "<gold><i:false>");
        text = text.replace("&7", "<gray><i:false>");
        text = text.replace("&8", "<dark_gray><i:false>");
        text = text.replace("&9", "<blue><i:false>");
        text = text.replace("&a", "<green><i:false>").replace("&A", "<green><i:false>");
        text = text.replace("&b", "<aqua><i:false>").replace("&B", "<aqua><i:false>");
        text = text.replace("&c", "<red><i:false>").replace("&C", "<red><i:false>");
        text = text.replace("&d", "<light_purple><i:false>").replace("&D", "<light_purple><i:false>");
        text = text.replace("&e", "<yellow><i:false>").replace("&E", "<yellow><i:false>");
        text = text.replace("&f", "<white><i:false>").replace("&F", "<white><i:false>");
        text = text.replace("&l", "<bold>");
        text = text.replace("&m", "<strikethrough>").replace("&M", "<strikethrough>");
        text = text.replace("&n", "<underlined>").replace("&N", "<underlined>");
        text = text.replace("&o", "<italic>").replace("&O", "<italic>");
        text = text.replace("&r", "<reset><i:false>").replace("&R", "<reset><i:false>");
        text = text.replace("&k", "<obfuscated>").replace("&K", "<obfuscated>");

        // Handle hex colors &#RRGGBB -> <color:#RRGGBB>
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(result, "<color:#" + hexMatcher.group(1) + "><i:false>");
        }
        hexMatcher.appendTail(result);

        return result.toString();
    }

    /**
     * Translate hex color codes in format &#RRGGBB to Bukkit format.
     */
    public static String translateHexColors(String text) {
        if (text == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append("&").append(c);
            }
            matcher.appendReplacement(result, replacement.toString());
        }
        matcher.appendTail(result);

        // Also handle <#RRGGBB> format
        String text2 = result.toString();
        Matcher matcher2 = HEX_TAG_PATTERN.matcher(text2);
        StringBuilder result2 = new StringBuilder();

        while (matcher2.find()) {
            String hex = matcher2.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append("&").append(c);
            }
            matcher2.appendReplacement(result2, replacement.toString());
        }
        matcher2.appendTail(result2);

        return ChatColor.translateAlternateColorCodes('&', result2.toString());
    }

    /**
     * Colorize a list of strings.
     */
    public static List<String> colorize(List<String> lines) {
        if (lines == null) return new ArrayList<>();

        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.add(colorize(line));
        }
        return result;
    }

    /**
     * Strip all color codes from a string.
     */
    public static String stripColors(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(colorize(text));
    }

    /**
     * Create a progress bar string.
     */
    public static String createProgressBar(double current, double max, int length, String filledChar, String emptyChar, String filledColor, String emptyColor) {
        double percent = current / max;
        int filled = (int) (percent * length);

        StringBuilder bar = new StringBuilder();
        bar.append(filledColor);
        for (int i = 0; i < filled && i < length; i++) {
            bar.append(filledChar);
        }
        bar.append(emptyColor);
        for (int i = filled; i < length; i++) {
            bar.append(emptyChar);
        }

        return colorize(bar.toString());
    }

    /**
     * Format a number with commas.
     */
    public static String formatNumber(double number) {
        return String.format("%,.2f", number);
    }

    /**
     * Format seconds to mm:ss format.
     */
    public static String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }

    /**
     * Get human-readable description for a trigger name.
     */
    public static String getTriggerDescription(String trigger) {
        return switch (trigger.toUpperCase().replace("_", " ").replace("ON ", "")) {
            case "ATTACK" -> "Triggers when you attack an entity";
            case "DEFENSE" -> "Triggers when you take damage";
            case "KILL MOB" -> "Triggers when you kill a mob";
            case "KILL PLAYER" -> "Triggers when you kill a player";
            case "SHIFT" -> "Triggers when you sneak";
            case "FALL DAMAGE" -> "Triggers when you take fall damage";
            case "EFFECT STATIC" -> "Passive effect - always active";
            case "BOW HIT" -> "Triggers when arrow hits target";
            case "BOW SHOOT" -> "Triggers when you fire arrow";
            case "BLOCK BREAK" -> "Triggers when you break a block";
            case "BLOCK PLACE" -> "Triggers when you place a block";
            case "INTERACT" -> "Triggers when you right-click";
            case "TRIDENT THROW" -> "Triggers when you throw trident";
            case "TICK" -> "Triggers every game tick";
            default -> trigger;
        };
    }

    /**
     * Get human-readable description for an effect string.
     */
    public static String getEffectDescription(String effect) {
        if (effect == null || effect.isEmpty()) return effect;

        String[] parts = effect.split(":");
        String effectType = parts[0].toUpperCase();

        return switch (effectType) {
            case "HEAL" -> "Restores " + (parts.length > 1 ? parts[1] : "❤");
            case "DAMAGE" -> "Deals " + (parts.length > 1 ? parts[1] : "damage");
            case "POTION" -> {
                String potionType = parts.length > 1 ? parts[1] : "effect";
                String duration = parts.length > 2 ? parts[2] + "s" : "";
                String level = "";
                if (parts.length > 3) {
                    try {
                        level = " Lvl " + (Integer.parseInt(parts[3]) + 1);
                    } catch (NumberFormatException e) {
                        level = " Lvl " + parts[3];
                    }
                }
                yield "Applies " + potionType.toLowerCase() + level + (duration.isEmpty() ? "" : " for " + duration);
            }
            case "PARTICLE" -> "Shows " + (parts.length > 1 ? parts[1].toLowerCase() : "particle") + " effect";
            case "SOUND" -> "Plays " + (parts.length > 1 ? parts[1].toLowerCase() : "sound") + " effect";
            case "DISINTEGRATE" -> "Damages target armor";
            case "AEGIS" -> "Creates protective barrier (Level " + (parts.length > 1 ? parts[1] : "1") + ")";
            case "INCREASE_DAMAGE" -> "Increases damage by " + (parts.length > 1 ? parts[1] : "amount") + "%";
            case "MESSAGE" -> "Sends message to player";
            case "CANCEL_EVENT" -> "Negates the triggering event";
            case "TELEPORT_RANDOM" -> "Teleports " + (parts.length > 1 ? parts[1] : "8") + " blocks away";
            case "TELEPORT_AROUND" -> "Teleports " + (parts.length > 1 ? parts[1] : "5") + " blocks around target" + (parts.length > 2 ? " (facing: " + parts[2] + ")" : "");
            case "TELEPORT_BEHIND" -> "Teleports " + (parts.length > 1 ? parts[1] : "2") + " blocks behind target" + (parts.length > 2 ? " (facing: " + parts[2] + ")" : "");
            case "SMOKEBOMB" -> "Creates smoke effect and blindness";
            case "REPLENISH" -> "Restores " + (parts.length > 1 ? parts[1] : "items") + " to inventory";
            case "DEVOUR" -> "Consumes nearby drops (Level " + (parts.length > 1 ? parts[1] : "1") + ")";
            case "SPAWN_ENTITY" -> "Spawns " + (parts.length > 1 ? parts[1].toLowerCase() : "entity");
            case "GLOW" -> "Makes target visible through walls";
            case "SLOWNESS" -> "Slows target movement";
            case "WEAKNESS" -> "Reduces target damage";
            case "REGENERATION" -> "Heals over time";
            case "STRENGTH" -> "Increases melee damage";
            case "SPEED" -> "Increases movement speed";
            case "JUMP_BOOST" -> "Increases jump height";
            case "RESISTANCE" -> "Reduces incoming damage";
            case "FIRE_RESISTANCE" -> "Protects from fire damage";
            case "WATER_BREATHING" -> "Allows breathing underwater";
            case "NIGHT_VISION" -> "Allows vision in darkness";
            case "INVISIBILITY" -> "Makes you invisible";
            case "WITHER" -> "Applies wither poison";
            default -> effect;
        };
    }

    /**
     * Convert text to Proper Case (capitalize first letter of each word).
     */
    public static String toProperCase(String text) {
        if (text == null || text.isEmpty()) return text;

        // Strip color codes first to get clean text
        String clean = stripColors(text);
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : clean.toCharArray()) {
            if (Character.isWhitespace(c)) {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }
}
