package com.zenax.armorsets.events;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for building and parsing condition strings.
 * Handles conversion between GUI parameters and condition string format.
 */
public class ConditionBuilder {

    /**
     * Builds a condition string from a condition type and parameters.
     *
     * @param type The condition type
     * @param value The parameter value (if applicable)
     * @param comparison The comparison operator (for comparison-based conditions)
     * @return The formatted condition string
     */
    public static String buildConditionString(ConditionType type, int value, String comparison) {
        if (!type.hasParameters()) {
            return type.getConfigKey();
        }

        if (comparison == null || comparison.isEmpty()) {
            return type.getConfigKey() + ":" + value;
        }

        return type.getConfigKey() + ":" + comparison + value;
    }

    /**
     * Parses a condition string into its components.
     *
     * @param conditionStr The condition string (e.g., "HEALTH_BELOW:10")
     * @return A map containing parsed components
     */
    public static Map<String, Object> parseConditionString(String conditionStr) {
        Map<String, Object> result = new HashMap<>();

        if (conditionStr == null || conditionStr.isEmpty()) {
            return result;
        }

        String[] parts = conditionStr.split(":");
        if (parts.length == 0) {
            return result;
        }

        String configKey = parts[0];

        // Find condition type by config key
        for (ConditionType type : ConditionType.values()) {
            if (type.getConfigKey().equals(configKey)) {
                result.put("type", type);
                result.put("category", type.getCategory());

                // Parse parameters if present
                if (parts.length > 1 && type.hasParameters()) {
                    String paramStr = parts[1];

                    // Check for comparison operator
                    if (paramStr.matches("[<>=]+.*")) {
                        // Extract operator and value
                        int opEnd = 0;
                        for (int i = 0; i < paramStr.length(); i++) {
                            char c = paramStr.charAt(i);
                            if (c == '<' || c == '>' || c == '=') {
                                opEnd = i + 1;
                            } else {
                                break;
                            }
                        }

                        if (opEnd > 0) {
                            String operator = paramStr.substring(0, opEnd);
                            String valueStr = paramStr.substring(opEnd);

                            result.put("comparison", operator);
                            try {
                                result.put("value", Integer.parseInt(valueStr));
                            } catch (NumberFormatException e) {
                                result.put("value", 0);
                            }
                        }
                    } else {
                        // No operator, just a value or string parameter
                        try {
                            result.put("value", Integer.parseInt(paramStr));
                        } catch (NumberFormatException e) {
                            result.put("parameter", paramStr);
                        }
                    }
                }

                break;
            }
        }

        return result;
    }

    /**
     * Validates a condition string format.
     *
     * @param conditionStr The condition string to validate
     * @return true if valid, false otherwise
     */
    public static boolean validateCondition(String conditionStr) {
        if (conditionStr == null || conditionStr.isEmpty()) {
            return false;
        }

        String[] parts = conditionStr.split(":");
        String configKey = parts[0];

        for (ConditionType type : ConditionType.values()) {
            if (type.getConfigKey().equals(configKey)) {
                if (!type.hasParameters()) {
                    return parts.length == 1;
                } else {
                    return parts.length >= 2;
                }
            }
        }

        return false;
    }

    /**
     * Gets the display name for a condition string.
     *
     * @param conditionStr The condition string
     * @return The display name
     */
    public static String getDisplayName(String conditionStr) {
        if (conditionStr == null || conditionStr.isEmpty()) {
            return "Unknown Condition";
        }

        String[] parts = conditionStr.split(":");
        String configKey = parts[0];

        for (ConditionType type : ConditionType.values()) {
            if (type.getConfigKey().equals(configKey)) {
                return type.getDisplayName();
            }
        }

        return "Unknown Condition";
    }

    /**
     * Gets parameter names for a condition type.
     *
     * @param type The condition type
     * @return Array of parameter names
     */
    public static String[] getParameterNames(ConditionType type) {
        if (!type.hasParameters()) {
            return new String[0];
        }

        return new String[]{"value"};
    }
}
