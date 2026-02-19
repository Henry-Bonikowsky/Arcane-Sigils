package com.miracle.arcanesigils.core;

/**
 * Single source of truth for rarity colors and ordering.
 * Replaces the duplicate getRarityColor()/getRarityOrder() methods
 * previously scattered across SigilManager, SocketManager, and GUI handlers.
 */
public final class RarityUtil {

    private RarityUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Get the Minecraft color code for a rarity tier. */
    public static String getColor(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> "\u00a77";
            case "UNCOMMON" -> "\u00a7a";
            case "RARE" -> "\u00a79";
            case "EPIC" -> "\u00a75";
            case "LEGENDARY" -> "\u00a76";
            case "MYTHIC" -> "\u00a7d";
            default -> "\u00a77";
        };
    }

    /** Get sort order for a rarity tier (higher = rarer). */
    public static int getOrder(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
    }
}
