package com.miracle.arcanesigils.events;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects conflicts, redundancies, and warnings in condition combinations.
 * Helps users avoid impossible or illogical condition setups.
 */
public class ConflictDetector {

    /**
     * Severity levels for conflicts.
     */
    public enum ConflictSeverity {
        IMPOSSIBLE(Material.BARRIER, "§c", "IMPOSSIBLE - These conditions cannot both be true"),
        CONFLICTING(Material.YELLOW_WOOL, "§e", "CONFLICTING - These conditions contradict each other"),
        REDUNDANT(Material.ORANGE_WOOL, "§6", "REDUNDANT - One condition implies the other"),
        WARNING(Material.GRAY_WOOL, "§7", "WARNING - These conditions may not work as expected");

        private final Material icon;
        private final String colorCode;
        private final String description;

        ConflictSeverity(Material icon, String colorCode, String description) {
            this.icon = icon;
            this.colorCode = colorCode;
            this.description = description;
        }

        public Material getIcon() {
            return icon;
        }

        public String getColorCode() {
            return colorCode;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Represents a detected conflict between conditions.
     */
    public static class Conflict {
        private final ConflictSeverity severity;
        private final String condition1;
        private final String condition2;
        private final String reason;

        public Conflict(ConflictSeverity severity, String condition1, String condition2, String reason) {
            this.severity = severity;
            this.condition1 = condition1;
            this.condition2 = condition2;
            this.reason = reason;
        }

        public ConflictSeverity getSeverity() {
            return severity;
        }

        public String getCondition1() {
            return condition1;
        }

        public String getCondition2() {
            return condition2;
        }

        public String getReason() {
            return reason;
        }

        public String getFormattedMessage() {
            return severity.getColorCode() + severity.name() + ": §7" + reason;
        }
    }

    /**
     * Detect all conflicts in a list of conditions.
     *
     * @param conditions The list of condition strings
     * @return List of detected conflicts
     */
    public static List<Conflict> detectConflicts(List<String> conditions) {
        List<Conflict> conflicts = new ArrayList<>();

        if (conditions == null || conditions.size() < 2) {
            return conflicts;
        }

        for (int i = 0; i < conditions.size(); i++) {
            for (int j = i + 1; j < conditions.size(); j++) {
                String cond1 = conditions.get(i);
                String cond2 = conditions.get(j);

                Conflict conflict = checkPairConflict(cond1, cond2);
                if (conflict != null) {
                    conflicts.add(conflict);
                }
            }
        }

        return conflicts;
    }

    /**
     * Check if a specific condition conflicts with any in a list.
     *
     * @param newCondition The new condition to check
     * @param existingConditions The existing conditions
     * @return List of conflicts, empty if none
     */
    public static List<Conflict> detectConflictsWithNew(String newCondition, List<String> existingConditions) {
        List<Conflict> conflicts = new ArrayList<>();

        if (existingConditions == null || existingConditions.isEmpty()) {
            return conflicts;
        }

        for (String existing : existingConditions) {
            Conflict conflict = checkPairConflict(newCondition, existing);
            if (conflict != null) {
                conflicts.add(conflict);
            }
        }

        return conflicts;
    }

    /**
     * Check for conflicts between two specific conditions.
     *
     * @param cond1 First condition string
     * @param cond2 Second condition string
     * @return A Conflict object if conflict detected, null otherwise
     */
    private static Conflict checkPairConflict(String cond1, String cond2) {
        String type1 = getConditionType(cond1);
        String type2 = getConditionType(cond2);

        // IMPOSSIBLE: Contradictory health conditions
        if (type1.equals("HEALTH_BELOW") && type2.equals("HEALTH_ABOVE")) {
            double threshold1 = extractNumericValue(cond1);
            double threshold2 = extractNumericValue(cond2);
            if (threshold1 <= threshold2) {
                return new Conflict(
                    ConflictSeverity.IMPOSSIBLE,
                    cond1, cond2,
                    "Cannot have health below " + threshold1 + " and above " + threshold2
                );
            }
        }

        if (type1.equals("HEALTH_ABOVE") && type2.equals("HEALTH_BELOW")) {
            double threshold1 = extractNumericValue(cond1);
            double threshold2 = extractNumericValue(cond2);
            if (threshold1 >= threshold2) {
                return new Conflict(
                    ConflictSeverity.IMPOSSIBLE,
                    cond1, cond2,
                    "Cannot have health above " + threshold1 + " and below " + threshold2
                );
            }
        }

        // IMPOSSIBLE: Contradictory health percent conditions
        if (type1.equals("HEALTH_PERCENT") && type2.equals("HEALTH_PERCENT")) {
            String comp1 = extractComparison(cond1);
            String comp2 = extractComparison(cond2);
            double val1 = extractNumericValue(cond1);
            double val2 = extractNumericValue(cond2);

            if ((comp1.startsWith("<") && comp2.startsWith(">") && val1 <= val2) ||
                (comp1.startsWith(">") && comp2.startsWith("<") && val1 >= val2)) {
                return new Conflict(
                    ConflictSeverity.IMPOSSIBLE,
                    cond1, cond2,
                    "Contradictory health percentage ranges"
                );
            }
        }

        // IMPOSSIBLE: Mutually exclusive weather
        if (type1.equals("WEATHER") && type2.equals("WEATHER")) {
            String weather1 = extractParameter(cond1, 1);
            String weather2 = extractParameter(cond2, 1);
            if (!weather1.equals(weather2)) {
                return new Conflict(
                    ConflictSeverity.IMPOSSIBLE,
                    cond1, cond2,
                    "Weather cannot be both " + weather1 + " and " + weather2
                );
            }
        }

        // IMPOSSIBLE: Contradictory time
        if (type1.equals("TIME") && type2.equals("TIME")) {
            String time1 = extractParameter(cond1, 1);
            String time2 = extractParameter(cond2, 1);
            if (time1.equals("DAY") && time2.equals("NIGHT") || time1.equals("NIGHT") && time2.equals("DAY")) {
                return new Conflict(
                    ConflictSeverity.IMPOSSIBLE,
                    cond1, cond2,
                    "Time cannot be both DAY and NIGHT"
                );
            }
        }

        // REDUNDANT: IN_WATER + BLOCK_BELOW:WATER
        if ((type1.equals("IN_WATER") && type2.equals("BLOCK_BELOW") && cond2.contains("WATER")) ||
            (type2.equals("IN_WATER") && type1.equals("BLOCK_BELOW") && cond1.contains("WATER"))) {
            return new Conflict(
                ConflictSeverity.REDUNDANT,
                cond1, cond2,
                "IN_WATER already implies standing on/in water"
            );
        }

        // REDUNDANT: TIME:DAY + high light level
        if (type1.equals("TIME") && type2.equals("LIGHT_LEVEL")) {
            if (cond1.contains("DAY") && extractComparison(cond2).startsWith(">")) {
                double lightLevel = extractNumericValue(cond2);
                if (lightLevel >= 12) {
                    return new Conflict(
                        ConflictSeverity.REDUNDANT,
                        cond1, cond2,
                        "Daytime already implies high light levels"
                    );
                }
            }
        }

        // WARNING: TIME:NIGHT + high light level
        if (type1.equals("TIME") && type2.equals("LIGHT_LEVEL")) {
            if (cond1.contains("NIGHT") && extractComparison(cond2).startsWith(">")) {
                double lightLevel = extractNumericValue(cond2);
                if (lightLevel > 7) {
                    return new Conflict(
                        ConflictSeverity.WARNING,
                        cond1, cond2,
                        "Night is typically dark - high light level unlikely"
                    );
                }
            }
        }

        // WARNING: Overlapping health ranges
        if (type1.equals("HEALTH_BELOW") && type2.equals("HEALTH_ABOVE")) {
            double below = extractNumericValue(cond1);
            double above = extractNumericValue(cond2);
            if (above < below && (below - above) < 5) {
                return new Conflict(
                    ConflictSeverity.WARNING,
                    cond1, cond2,
                    "Narrow health range (" + above + "-" + below + " HP)"
                );
            }
        }

        // WARNING: VICTIM conditions without HAS_VICTIM
        if ((type1.startsWith("VICTIM_") && !type2.equals("HAS_VICTIM")) ||
            (type2.startsWith("VICTIM_") && !type1.equals("HAS_VICTIM"))) {
            // Check if HAS_VICTIM exists in the pair
            if (!type1.equals("HAS_VICTIM") && !type2.equals("HAS_VICTIM")) {
                return new Conflict(
                    ConflictSeverity.WARNING,
                    cond1, cond2,
                    "Victim conditions should include HAS_VICTIM check"
                );
            }
        }

        return null;
    }

    // ===== HELPER METHODS =====

    private static String getConditionType(String condition) {
        if (condition == null || condition.isEmpty()) return "";
        int colonIndex = condition.indexOf(':');
        return colonIndex > 0 ? condition.substring(0, colonIndex) : condition;
    }

    private static String extractParameter(String condition, int paramIndex) {
        if (condition == null || condition.isEmpty()) return "";
        String[] parts = condition.split(":");
        return parts.length > paramIndex ? parts[paramIndex] : "";
    }

    private static double extractNumericValue(String condition) {
        String param = extractParameter(condition, 1);
        if (param.isEmpty()) return 0;

        // Remove comparison operators
        param = param.replaceAll("[<>=]", "");

        try {
            return Double.parseDouble(param);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String extractComparison(String condition) {
        String param = extractParameter(condition, 1);
        if (param.isEmpty()) return "";

        if (param.startsWith(">=")) return ">=";
        if (param.startsWith("<=")) return "<=";
        if (param.startsWith("<")) return "<";
        if (param.startsWith(">")) return ">";
        if (param.startsWith("=")) return "=";

        return "";
    }

    /**
     * Get a user-friendly conflict summary for display.
     *
     * @param conflicts List of conflicts
     * @return Formatted string summarizing conflicts
     */
    public static String getConflictSummary(List<Conflict> conflicts) {
        if (conflicts.isEmpty()) {
            return "§aNo conflicts detected";
        }

        StringBuilder summary = new StringBuilder();
        long impossible = conflicts.stream().filter(c -> c.getSeverity() == ConflictSeverity.IMPOSSIBLE).count();
        long conflicting = conflicts.stream().filter(c -> c.getSeverity() == ConflictSeverity.CONFLICTING).count();
        long redundant = conflicts.stream().filter(c -> c.getSeverity() == ConflictSeverity.REDUNDANT).count();
        long warnings = conflicts.stream().filter(c -> c.getSeverity() == ConflictSeverity.WARNING).count();

        if (impossible > 0) summary.append("§c").append(impossible).append(" Impossible ");
        if (conflicting > 0) summary.append("§e").append(conflicting).append(" Conflicting ");
        if (redundant > 0) summary.append("§6").append(redundant).append(" Redundant ");
        if (warnings > 0) summary.append("§7").append(warnings).append(" Warning");

        return summary.toString().trim();
    }
}
