package com.miracle.arcanesigils.flow.nodes;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.Effect;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectManager;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.flow.FlowContext;
import com.miracle.arcanesigils.flow.FlowNode;
import com.miracle.arcanesigils.flow.NodeType;
import com.miracle.arcanesigils.utils.LogHelper;

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
        LogHelper.debug("[EffectNode] execute() START - effect=%s", effectType);
        if (effectType == null || effectType.isEmpty()) {
            LogHelper.debug("[EffectNode] No effect type configured - ABORTING");
            return "next";
        }

        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
        EffectManager effectManager = plugin.getEffectManager();

        // Get the effect
        LogHelper.debug("[EffectNode] Getting effect from manager: %s", effectType);
        Effect effect = effectManager.getEffect(effectType);
        LogHelper.debug("[EffectNode] Effect instance: %s", effect != null ? effect.getClass().getSimpleName() : "NULL");
        if (effect == null) {
            LogHelper.debug("[EffectNode] Unknown effect type: %s - ABORTING", effectType);
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
        // IMPORTANT: Copy sigilId from metadata for persistent modifier key generation
        String sigilId = context.getEffectContext().getMetadata("sourceSigilId", null);
        LogHelper.debug("[EffectNode] Creating EffectContext: player=%s, victim=%s, currentTarget=%s",
            context.getPlayer() != null ? context.getPlayer().getName() : "NULL",
            context.getEffectContext().getVictim() != null ? context.getEffectContext().getVictim().getName() : "NULL",
            context.getCurrentTarget() != null ? context.getCurrentTarget().getName() : "NULL");
        EffectContext execContext = EffectContext.builder(context.getPlayer(), context.getEffectContext().getSignalType())
                .event(context.getEffectContext().getBukkitEvent())
                .victim(context.getCurrentTarget())
                .attacker(context.getEffectContext().getAttacker())
                .location(context.getCurrentLocation())
                .damage(context.getEffectContext().getDamage())
                .params(params)
                .sigilId(sigilId)
                .flowContext(context)
                .build();
        LogHelper.debug("[EffectNode] EffectContext built: execContext.player=%s, execContext.params.target=%s",
            execContext.getPlayer() != null ? execContext.getPlayer().getName() : "NULL",
            params != null ? params.getTarget() : "NULL");

        // Copy metadata from flow context
        context.getEffectContext().getMetadata().forEach(execContext::setMetadata);

        // CRITICAL: Copy InterceptionEvent for POTION_EFFECT_APPLY and ATTRIBUTE_MODIFY signals
        // Effects like REDUCE_POTION_POTENCY require this to access potion type/amplifier/duration
        LogHelper.debug("[EffectNode] Checking InterceptionEvent: original=%s",
            context.getEffectContext().getInterceptionEvent() != null ? "present" : "NULL");

        if (context.getEffectContext().getInterceptionEvent() != null) {
            execContext.setInterceptionEvent(context.getEffectContext().getInterceptionEvent());
            LogHelper.debug("[EffectNode] Copied InterceptionEvent to execContext");
        } else {
            LogHelper.debug("[EffectNode] NO InterceptionEvent in original context!");
        }

        // Copy currentPotionEffect if set (for IS_NEGATIVE_EFFECT condition)
        if (context.getEffectContext().getCurrentPotionEffect() != null) {
            execContext.setCurrentPotionEffect(context.getEffectContext().getCurrentPotionEffect());
        }

        // Execute
        LogHelper.debug("[EffectNode] About to execute effect: %s", effectType);
        boolean success = effect.execute(execContext);
        LogHelper.debug("[EffectNode] Effect execution result: %s", success);

        // Track that an effect executed (for cooldown logic)
        if (success) {
            context.incrementEffectsExecuted();
            LogHelper.debug("[EffectNode] Effect success - incremented effectsExecuted");
        } else {
            LogHelper.debug("[EffectNode] Effect FAILED - not incrementing effectsExecuted");
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
                Object parsedValue = parseResolvedValue(resolvedStr);
                LogHelper.debug("[EffectNode] Resolved param '%s': '%s' -> '%s' -> %s (tier=%d)",
                    key, strValue, resolvedStr, parsedValue, context.getTier());
                resolved.put(key, parsedValue);
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
