package com.zenax.armorsets.flow.nodes;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.effects.Effect;
import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectManager;
import com.zenax.armorsets.effects.EffectParams;
import com.zenax.armorsets.flow.FlowContext;
import com.zenax.armorsets.flow.FlowNode;
import com.zenax.armorsets.flow.NodeType;
import com.zenax.armorsets.utils.LogHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Effect node - executes any existing effect type.
 * Wraps the plugin's effect system for use in flows.
 */
public class EffectNode extends FlowNode {

    /**
     * The effect type ID (e.g., "DEAL_DAMAGE", "PARTICLE").
     */
    private String effectType;

    public EffectNode(String id) {
        super(id);
        setDisplayName("Effect");
    }

    public EffectNode(String id, String effectType) {
        super(id);
        this.effectType = effectType;
        setDisplayName(effectType != null ? effectType.replace("_", " ") : "Effect");
    }

    @Override
    public NodeType getType() {
        return NodeType.EFFECT;
    }

    @Override
    public String execute(FlowContext context) {
        if (effectType == null || effectType.isEmpty()) {
            LogHelper.debug("[EffectNode] No effect type configured");
            return "next";
        }

        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
        EffectManager effectManager = plugin.getEffectManager();

        // Get the effect
        Effect effect = effectManager.getEffect(effectType);
        if (effect == null) {
            LogHelper.debug("[EffectNode] Unknown effect type: %s", effectType);
            return "next";
        }

        // Check for inline chance (for behavior YAML effects)
        Object chanceObj = getParam("chance");
        if (chanceObj != null) {
            double chance = chanceObj instanceof Number ? ((Number) chanceObj).doubleValue() : 100;
            if (chance < 100 && Math.random() * 100 > chance) {
                LogHelper.debug("[EffectNode] Effect %s failed chance roll (%.1f%%)", effectType, chance);
                return "next"; // Skip this effect but continue flow
            }
        }

        // Build resolved params map (resolves {placeholder} expressions)
        Map<String, Object> resolvedParams = buildResolvedParams(context);

        // Enhanced debug: show raw vs resolved params for tier params
        Map<String, Object> rawParams = getParams();
        LogHelper.debug("[EffectNode] === Effect execution: %s ===", effectType);
        LogHelper.debug("[EffectNode] Raw node params: %s", rawParams);
        LogHelper.debug("[EffectNode] Resolved params: %s", resolvedParams);

        // Specifically log damage/speed/hp for SPAWN_ENTITY debugging
        if ("SPAWN_ENTITY".equals(effectType)) {
            LogHelper.debug("[EffectNode] SPAWN_ENTITY debug - damage: raw=%s, resolved=%s",
                rawParams.get("damage"), resolvedParams.get("damage"));
            LogHelper.debug("[EffectNode] SPAWN_ENTITY debug - speed: raw=%s, resolved=%s",
                rawParams.get("speed"), resolvedParams.get("speed"));
            LogHelper.debug("[EffectNode] SPAWN_ENTITY debug - hp: raw=%s, resolved=%s",
                rawParams.get("hp"), resolvedParams.get("hp"));
        }

        // Check for effect_string param (from behavior YAML) - use effect's parseParams()
        String effectString = (String) resolvedParams.get("effect_string");
        EffectParams params;
        if (effectString != null && !effectString.isEmpty()) {
            // Use the effect's parseParams to handle the string format
            params = effect.parseParams(effectString);
            LogHelper.debug("[EffectNode] Parsed effect_string: %s -> %s", effectString, params);
        } else {
            // Create EffectParams directly from map - bypasses string serialization
            // This preserves type info (Integer 2 stays as 2, not "2.0")
            params = EffectParams.fromMap(effectType, resolvedParams);
        }

        // Create effect context with current target
        // IMPORTANT: Pass attacker through for @Attacker target resolution (e.g., Pharaoh's Curse)
        EffectContext execContext = EffectContext.builder(context.getPlayer(), context.getEffectContext().getSignalType())
                .event(context.getEffectContext().getBukkitEvent())
                .victim(context.getCurrentTarget())
                .attacker(context.getEffectContext().getAttacker())
                .location(context.getCurrentLocation())
                .damage(context.getEffectContext().getDamage())
                .params(params)
                .build();

        // Copy metadata from flow context
        context.getEffectContext().getMetadata().forEach(execContext::setMetadata);

        // Execute
        boolean success = effect.execute(execContext);

        // Track that an effect executed (for cooldown logic)
        if (success) {
            context.incrementEffectsExecuted();
        }

        // Store result in context variable if requested
        String resultVar = getStringParam("storeAs", null);
        if (resultVar != null) {
            context.setVariable(resultVar, success);
        }

        return "next";
    }

    /**
     * Build a resolved params map from node parameters.
     * Resolves {placeholder} expressions while preserving original types.
     */
    private Map<String, Object> buildResolvedParams(FlowContext context) {
        Map<String, Object> resolved = new java.util.HashMap<>();
        Map<String, Object> nodeParams = getParams();

        for (Map.Entry<String, Object> entry : nodeParams.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Skip internal params
            if (key.equals("storeAs") || key.equals("effectType")) {
                continue;
            }

            // Resolve {placeholder} expressions if value is a string containing them
            if (value instanceof String strValue && strValue.contains("{")) {
                String resolvedStr = context.resolveExpression(strValue);
                // Try to parse resolved string back to number if it was an expression
                resolved.put(key, parseResolvedValue(resolvedStr));
            } else {
                // Keep original value (preserves Integer 2 instead of converting to "2.0")
                resolved.put(key, value);
            }
        }

        return resolved;
    }

    /**
     * Parse a resolved expression string back to a typed value.
     * Tries to preserve numeric types.
     */
    private Object parseResolvedValue(String value) {
        if (value == null) return null;

        // Try integer first
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {}

        // Try double
        try {
            double d = Double.parseDouble(value);
            // If it's a whole number, return as int
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return (int) d;
            }
            return d;
        } catch (NumberFormatException ignored) {}

        // Return as string
        return value;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (effectType == null || effectType.isEmpty()) {
            errors.add("Effect type not configured");
        } else {
            // Verify effect exists
            ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
            if (plugin != null && plugin.getEffectManager().getEffect(effectType) == null) {
                errors.add("Unknown effect type: " + effectType);
            }
        }

        // Check next connection
        if (getConnection("next") == null) {
            errors.add("Output 'next' is not connected");
        }

        return errors;
    }

    // ============ Getters/Setters ============

    public String getEffectType() {
        return effectType;
    }

    public void setEffectType(String effectType) {
        this.effectType = effectType;
        if (effectType != null) {
            setDisplayName(effectType.replace("_", " "));
        }
    }

    @Override
    public FlowNode deepCopy() {
        EffectNode copy = new EffectNode(getId());
        copy.effectType = this.effectType;
        copyBaseTo(copy);
        return copy;
    }
}
