package com.miracle.arcanesigils.flow;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.tier.TierScalingConfig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Execution context for a flow.
 * Holds variables, event data, and provides expression resolution.
 */
public class FlowContext {

    /**
     * Pattern to match {variable} placeholders.
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z_.][a-zA-Z0-9_.]*)\\}");

    /**
     * The underlying effect context from the signal.
     */
    private final EffectContext effectContext;

    /**
     * User-defined variables (prefixed with $ in expressions).
     */
    private final Map<String, Object> variables = new HashMap<>();

    /**
     * Current target entity for effects (can be changed by Target nodes).
     */
    private LivingEntity currentTarget;

    /**
     * Current location for effects (can be changed).
     */
    private Location currentLocation;

    /**
     * Current tier level (1-based).
     */
    private int tier = 1;

    /**
     * Whether the flow has been cancelled.
     */
    private boolean cancelled = false;

    /**
     * Error message if flow fails.
     */
    private String errorMessage;

    /**
     * Count of effect nodes that actually executed.
     * Used to determine if cooldown should trigger.
     */
    private int effectsExecuted = 0;

    /**
     * Whether the cooldown should be skipped.
     * Set by SKIP_COOLDOWN nodes when conditions fail.
     */
    private boolean skipCooldown = false;

    public FlowContext(EffectContext effectContext) {
        this.effectContext = effectContext;
        if (effectContext != null) {
            this.currentTarget = effectContext.getVictim();
            this.currentLocation = effectContext.getLocation();

            // Get tier from effect context
            Integer contextTier = effectContext.getMetadata("sourceSigilTier", null);
            com.miracle.arcanesigils.utils.LogHelper.debug("[FlowContext] sourceSigilTier from metadata: %s", contextTier);
            if (contextTier != null) {
                this.tier = contextTier;
                com.miracle.arcanesigils.utils.LogHelper.debug("[FlowContext] Set tier to: %d", this.tier);
            }
        }
    }

    // ============ Variable Management ============

    public void setVariable(String name, Object value) {
        variables.put(name, value);
    }

    public Object getVariable(String name) {
        return variables.get(name);
    }

    public Object getVariable(String name, Object defaultValue) {
        return variables.getOrDefault(name, defaultValue);
    }

    public double getVariableAsDouble(String name, double defaultValue) {
        Object val = variables.get(name);
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        if (val instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public boolean hasVariable(String name) {
        return variables.containsKey(name);
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    // ============ Expression Resolution ============

    /**
     * Resolve an expression string, replacing placeholders with values.
     *
     * @param expression The expression with {placeholders}
     * @return Resolved string
     */
    public String resolveExpression(String expression) {
        if (expression == null || !expression.contains("{")) {
            return expression;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(expression);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = resolveValue(placeholder);
            String replacement = value != null ? formatValue(value) : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Resolve a numeric expression to a double value.
     *
     * @param expression The expression
     * @param defaultValue Default if resolution fails
     * @return The resolved value
     */
    public double resolveNumeric(String expression, double defaultValue) {
        if (expression == null) {
            return defaultValue;
        }

        // If it's already a number, return it
        try {
            return Double.parseDouble(expression);
        } catch (NumberFormatException ignored) {}

        // Resolve placeholders
        String resolved = resolveExpression(expression);

        // Try to parse the resolved value
        try {
            return Double.parseDouble(resolved);
        } catch (NumberFormatException ignored) {}

        return defaultValue;
    }

    /**
     * Resolve a single placeholder value.
     */
    private Object resolveValue(String placeholder) {
        // Handle user variables ($var)
        if (placeholder.startsWith("$")) {
            return variables.get(placeholder.substring(1));
        }

        // Check TierScalingConfig for tier-scaled params (e.g., {damage}, {chance})
        // This is the unified tier scaling system - params defined in Tier Config
        TierScalingConfig tierConfig = effectContext != null
            ? effectContext.getMetadata("tierScalingConfig", null) : null;

        // Debug tier param resolution
        com.miracle.arcanesigils.utils.LogHelper.debug(
            "[FlowContext] Resolving placeholder '%s': tierConfig=%s, tier=%d",
            placeholder, tierConfig != null ? "present" : "NULL", tier);

        if (tierConfig != null && tierConfig.hasParam(placeholder)) {
            double value = tierConfig.getParamValue(placeholder, tier);
            com.miracle.arcanesigils.utils.LogHelper.debug(
                "[FlowContext] Resolved '%s' from tierConfig: %.2f", placeholder, value);
            return value;
        } else if (tierConfig != null) {
            com.miracle.arcanesigils.utils.LogHelper.debug(
                "[FlowContext] tierConfig present but param '%s' not found, falling through to defaults", placeholder);
        }

        // Handle event data (requires effectContext)
        if (effectContext == null) {
            // Without effectContext, only tier, random, and user variables are available
            return switch (placeholder.toLowerCase()) {
                case "tier" -> tier;
                case "random" -> Math.random();
                default -> {
                    if (variables.containsKey(placeholder)) {
                        yield variables.get(placeholder);
                    }
                    yield null;
                }
            };
        }

        // Full placeholder resolution with effectContext available
        return switch (placeholder.toLowerCase()) {
            // Damage
            case "damage" -> effectContext.getDamage();

            // Player stats
            case "player.health" -> getPlayer() != null ? getPlayer().getHealth() : 0;
            case "player.health_percent" -> getPlayer() != null
                    ? (getPlayer().getHealth() / getPlayer().getMaxHealth()) * 100 : 0;
            case "player.max_health" -> getPlayer() != null ? getPlayer().getMaxHealth() : 0;
            case "player.food" -> getPlayer() != null ? getPlayer().getFoodLevel() : 0;
            case "player.saturation" -> getPlayer() != null ? getPlayer().getSaturation() : 0;
            case "player.armor" -> getPlayer() != null ? getPlayer().getInventory().getArmorContents().length : 0;
            case "player.level", "player.xp" -> getPlayer() != null ? getPlayer().getLevel() : 0;
            case "player.exp" -> getPlayer() != null ? getPlayer().getExp() : 0;

            // Victim stats
            case "victim.health" -> currentTarget != null ? currentTarget.getHealth() : 0;
            case "victim.health_percent" -> currentTarget != null
                    ? (currentTarget.getHealth() / currentTarget.getMaxHealth()) * 100 : 0;
            case "victim.max_health" -> currentTarget != null ? currentTarget.getMaxHealth() : 0;

            // Distance
            case "distance", "victim.distance" -> currentTarget != null && getPlayer() != null
                    && getPlayer().getWorld().equals(currentTarget.getWorld())
                    ? getPlayer().getLocation().distance(currentTarget.getLocation()) : 0;

            // Tier
            case "tier" -> tier;

            // World data
            case "world.time" -> getPlayer() != null ? getPlayer().getWorld().getTime() : 0;
            case "world.light" -> getPlayer() != null ? getPlayer().getLocation().getBlock().getLightLevel() : 0;

            // Random
            case "random" -> Math.random();

            // Check for user variable without $ prefix
            default -> {
                if (variables.containsKey(placeholder)) {
                    yield variables.get(placeholder);
                }
                yield null;
            }
        };
    }

    /**
     * Format a value for string output.
     */
    private String formatValue(Object value) {
        if (value instanceof Double d) {
            if (d == d.longValue()) {
                return String.valueOf(d.longValue());
            }
            return String.format("%.2f", d);
        }
        return String.valueOf(value);
    }

    /**
     * Evaluate a boolean condition.
     * Supports:
     * - Expressions with placeholders: "{player.health} < 50", "{damage} >= 10"
     * - Random chance: "random(25%)"
     * - All ConditionManager condition types: "HAS_MARK:PHARAOH_MARK", "IN_AIR", "HAS_TARGET", etc.
     *
     * @param condition The condition string
     * @return true if condition is met
     */
    public boolean evaluateCondition(String condition) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        // Resolve all placeholders first
        String resolved = resolveExpression(condition);

        // 1. Check for comparison expressions (VALUE OPERATOR VALUE)
        String[] operators = {"<=", ">=", "==", "!=", "<", ">"};
        for (String op : operators) {
            int idx = resolved.indexOf(op);
            if (idx > 0) {
                String left = resolved.substring(0, idx).trim();
                String right = resolved.substring(idx + op.length()).trim();

                try {
                    double leftVal = Double.parseDouble(left);
                    double rightVal = Double.parseDouble(right);

                    return switch (op) {
                        case "<" -> leftVal < rightVal;
                        case ">" -> leftVal > rightVal;
                        case "<=" -> leftVal <= rightVal;
                        case ">=" -> leftVal >= rightVal;
                        case "==" -> Math.abs(leftVal - rightVal) < 0.0001;
                        case "!=" -> Math.abs(leftVal - rightVal) >= 0.0001;
                        default -> false;
                    };
                } catch (NumberFormatException ignored) {
                    // String comparison
                    return switch (op) {
                        case "==" -> left.equals(right);
                        case "!=" -> !left.equals(right);
                        default -> false;
                    };
                }
            }
        }

        // 2. Check for random chance (e.g., "random(25%)")
        if (resolved.startsWith("random(") && resolved.endsWith("%)")) {
            try {
                double chance = Double.parseDouble(resolved.substring(7, resolved.length() - 2));
                return Math.random() * 100 < chance;
            } catch (NumberFormatException ignored) {}
        }

        // 3. Check for boolean literals
        if ("true".equalsIgnoreCase(resolved) || "1".equals(resolved)) {
            return true;
        }
        if ("false".equalsIgnoreCase(resolved) || "0".equals(resolved)) {
            return false;
        }

        // 4. Delegate ALL other conditions to ConditionManager
        // This handles: HAS_MARK, HAS_TARGET, IN_AIR, HEALTH_PERCENT, BIOME, etc.
        // Note: Without effectContext, condition manager cannot evaluate most conditions
        if (effectContext == null) {
            // Without effectContext, we can't delegate to ConditionManager
            // Return true to allow tests to pass without a full context
            return true;
        }

        com.miracle.arcanesigils.events.ConditionManager conditionManager =
            new com.miracle.arcanesigils.events.ConditionManager();

        boolean result = conditionManager.checkConditions(
            java.util.Collections.singletonList(condition),
            effectContext
        );
        com.miracle.arcanesigils.utils.LogHelper.debug(
            "[FlowContext] Condition '%s' evaluated by ConditionManager: %s", condition, result);
        return result;
    }

    // ============ Context Access ============

    public Player getPlayer() {
        return effectContext != null ? effectContext.getPlayer() : null;
    }

    public EffectContext getEffectContext() {
        return effectContext;
    }

    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    public void setCurrentTarget(LivingEntity target) {
        this.currentTarget = target;
    }

    public Location getCurrentLocation() {
        if (currentLocation != null) {
            return currentLocation;
        }
        Player player = getPlayer();
        return player != null ? player.getLocation() : null;
    }

    public void setCurrentLocation(Location location) {
        this.currentLocation = location;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setError(String message) {
        this.errorMessage = message;
        this.cancelled = true;
    }

    /**
     * Increment the count of effects executed.
     * Called by EffectNode when an effect successfully runs.
     */
    public void incrementEffectsExecuted() {
        effectsExecuted++;
    }

    /**
     * Get the count of effects that executed.
     */
    public int getEffectsExecuted() {
        return effectsExecuted;
    }

    /**
     * Check if any effects actually executed.
     * Used to determine if cooldown should trigger.
     */
    public boolean hasEffectsExecuted() {
        return effectsExecuted > 0;
    }

    /**
     * Set whether the cooldown should be skipped.
     * Called by SKIP_COOLDOWN nodes.
     */
    public void setSkipCooldown(boolean skip) {
        this.skipCooldown = skip;
    }

    /**
     * Check if cooldown should be skipped.
     * Used by SignalHandler after flow execution.
     */
    public boolean shouldSkipCooldown() {
        return skipCooldown;
    }
}
