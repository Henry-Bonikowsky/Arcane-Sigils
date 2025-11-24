package com.zenax.armorsets.utils;

/**
 * Utility class for converting between integers and Roman numerals.
 * Supports numbers 1-3999.
 */
public final class RomanNumerals {

    private RomanNumerals() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    private static final String[] NUMERALS = {
        "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
        "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"
    };

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    /**
     * Convert an integer to a Roman numeral string.
     * For values 1-20, uses a lookup table for efficiency.
     * For values 21-3999, computes the numeral.
     *
     * @param number The number to convert (1-3999)
     * @return The Roman numeral representation, or the number as string if out of range
     */
    public static String toRoman(int number) {
        // Handle out of range
        if (number < 1 || number > 3999) {
            return String.valueOf(number);
        }

        // Use lookup table for common values (tiers 1-20)
        if (number >= 1 && number <= 20) {
            return NUMERALS[number - 1];
        }

        // Compute for larger values
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < VALUES.length; i++) {
            while (number >= VALUES[i]) {
                result.append(SYMBOLS[i]);
                number -= VALUES[i];
            }
        }
        return result.toString();
    }

    /**
     * Convert a Roman numeral string to an integer.
     *
     * @param roman The Roman numeral string
     * @return The integer value, or -1 if invalid
     */
    public static int fromRoman(String roman) {
        if (roman == null || roman.isEmpty()) {
            return -1;
        }

        roman = roman.toUpperCase().trim();
        int result = 0;
        int i = 0;

        while (i < roman.length()) {
            // Check for two-character symbols first
            if (i + 1 < roman.length()) {
                String twoChar = roman.substring(i, i + 2);
                int twoCharValue = getSymbolValue(twoChar);
                if (twoCharValue > 0) {
                    result += twoCharValue;
                    i += 2;
                    continue;
                }
            }

            // Single character
            String oneChar = roman.substring(i, i + 1);
            int oneCharValue = getSymbolValue(oneChar);
            if (oneCharValue > 0) {
                result += oneCharValue;
                i++;
            } else {
                return -1; // Invalid character
            }
        }

        return result;
    }

    private static int getSymbolValue(String symbol) {
        return switch (symbol) {
            case "M" -> 1000;
            case "CM" -> 900;
            case "D" -> 500;
            case "CD" -> 400;
            case "C" -> 100;
            case "XC" -> 90;
            case "L" -> 50;
            case "XL" -> 40;
            case "X" -> 10;
            case "IX" -> 9;
            case "V" -> 5;
            case "IV" -> 4;
            case "I" -> 1;
            default -> 0;
        };
    }

    /**
     * Format a tier number with Roman numerals.
     *
     * @param tier The tier number
     * @return Formatted string like "Tier III" or "T3" if large
     */
    public static String formatTier(int tier) {
        if (tier <= 20) {
            return toRoman(tier);
        }
        return String.valueOf(tier);
    }

    /**
     * Format a tier for display with prefix.
     *
     * @param tier   The tier number
     * @param prefix The prefix (e.g., "Tier ", "T")
     * @return Formatted string like "Tier III"
     */
    public static String formatTierWithPrefix(int tier, String prefix) {
        return prefix + formatTier(tier);
    }
}
