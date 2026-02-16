package com.miracle.arcanesigils.effects;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.impl.*;
import com.miracle.arcanesigils.particles.ShapeEffect;
import com.miracle.arcanesigils.tier.TierScalingConfig;

import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manager for all effect types in the plugin.
 */
public class EffectManager {

    private final ArmorSetsPlugin plugin;
    private final Map<String, Effect> effects = new HashMap<>();

    // Pattern to parse effect strings like "POTION:SPEED:10 @Victim"
    private static final Pattern EFFECT_PATTERN = Pattern.compile(
            "^([A-Z_]+)(?::(.+?))?(?:\\s+(@\\w+(?::\\d+)?))?$"
    );

    // Pattern to find {param} placeholders
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    public EffectManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        registerDefaultEffects();
    }

    private void registerDefaultEffects() {
        // Damage/Combat Effects
        registerEffect(new DealDamageEffect());
        registerEffect(new DamageBoostEffect());
        registerEffect(new ReduceDamageEffect());
        registerEffect(new CancelEventEffect());
        registerEffect(new LifestealEffect());
        registerEffect(new DamageArmorEffect());
        registerEffect(new BleedingEffect());
        registerEffect(new CleaveEffect());
        registerEffect(new ExecuteEffect());
        registerEffect(new ReflectDamageEffect());

        // Movement Effects
        registerEffect(new TeleportEffect());
        registerEffect(new SmokebombEffect());
        registerEffect(new DodgeEffect());
        registerEffect(new DashEffect());
        registerEffect(new KnockbackEffect());
        registerEffect(new PullEffect());
        registerEffect(new LaunchEffect());
        registerEffect(new GrappleEffect());
        registerEffect(new SwapEffect());
        registerEffect(new GroundSlamEffect());
        registerEffect(new ModifyAttributeEffect());

        // Healing/Sustain Effects
        registerEffect(new HealEffect());
        registerEffect(new AbsorbtionEffect());
        registerEffect(new SaturateEffect());
        registerEffect(new PhoenixEffect());
        registerEffect(new MaxHealthBoostEffect());
        registerEffect(new ResistEffectsEffect());
        registerEffect(new ClearNegativeEffectsEffect());
        registerEffect(new InvulnerabilityHitsEffect());

        // Potion Effect
        registerEffect(new PotionEffectEffect());

        // Utility Effects
        registerEffect(new SoulboundEffect());
        registerEffect(new InquisitiveEffect());
        registerEffect(new EnlightenedEffect());
        registerEffect(new AllureEffect());
        registerEffect(new RepairArmorEffect());
        registerEffect(new RepairItemEffect());
        registerEffect(new DecreaseSigilTierEffect());
        registerEffect(new DisarmEffect());
        registerEffect(new StealBuffsEffect());

        // Visual/Audio Effects
        registerEffect(new ParticleEffect());
        registerEffect(new SoundEffect());
        registerEffect(new MessageEffect());
        registerEffect(new ShapeEffect());

        // Spawning/Environmental Effects
        registerEffect(new SpawnEntityEffect());
        registerEffect(new SpawnDisplayEffect());
        registerEffect(new FreezingEffect());
        registerEffect(new IgniteEffect());
        registerEffect(new LightningEffect());
        registerEffect(new ExplosionEffect());

        // Loot/Item Effects
        registerEffect(new DropHeadEffect());
        registerEffect(new RemoveRandomEnchantEffect());
        registerEffect(new GiveItemEffect());

        // Pharaoh Set Effects
        registerEffect(new StunEffect());
        registerEffect(new SummonMummyEffect());
        registerEffect(new ChangeSkinEffect());
        
        // Cleopatra Set Effects
        registerEffect(new RemoveBuffsEffect());
        registerEffect(new DamageAmplificationEffect());
        registerEffect(new ApplySuppressionEffect());
        
        // King's Brace Effects
        registerEffect(new UpdateChargeDREffect());

        // Ancient Crown Effects
        registerEffect(new RegisterAncientCrownImmunityEffect());

        // Mark System
        registerEffect(new MarkEffect());
        registerEffect(new ApplyDamageMarkEffect());
        registerEffect(new ApplyUniversalMarkEffect());
        registerEffect(new PullToOwnerEffect());
        registerEffect(new RegisterNoKnockbackEffect());

        // Buff System
        registerEffect(new DamageReductionBuffEffect());
        
        // Interception System
        registerEffect(new ReducePotionAmplifierEffect());
        registerEffect(new ReducePotionPotencyEffect());
        registerEffect(new ReduceAttributeValueEffect());

        plugin.getLogger().info("Registered " + effects.size() + " effect types");
    }

    public void registerEffect(Effect effect) {
        effects.put(effect.getId().toUpperCase(), effect);
    }

    public Effect getEffect(String id) {
        String upperid = id.toUpperCase();

        // Handle common aliases
        upperid = switch (upperid) {
            case "DAMAGE" -> "DEAL_DAMAGE";
            case "INCREASE_DAMAGE", "BOOST_DAMAGE", "DMG_BOOST" -> "DAMAGE_BOOST";
            case "AEGIS", "DEFENSE" -> "REDUCE_DAMAGE";
            case "BUFF", "POTION_EFFECT" -> "POTION"; // Alias to POTION (the registered name)
            case "MSG" -> "MESSAGE";
            case "TP" -> "TELEPORT";
            case "FX", "EFFECT" -> "PARTICLE";
            case "ANGELIC", "MAX_HP" -> "MAX_HEALTH_BOOST";
            case "DEVOUR" -> "ABSORBTION";
            case "DISINTEGRATE" -> "DAMAGE_ARMOR";
            case "LUCID", "CLEANSE" -> "CLEAR_NEGATIVE_EFFECTS";
            case "REPLENISH", "HUNGER" -> "SATURATE";
            case "WARD", "RESIST_MAGIC" -> "RESIST_EFFECTS";
            default -> upperid;
        };

        return effects.get(upperid);
    }

    public Collection<Effect> getAllEffects() {
        return Collections.unmodifiableCollection(effects.values());
    }

    /**
     * Parse and execute a list of effect strings.
     *
     * @param effectStrings List of effect strings from config
     * @param context       The effect context
     * @return Number of effects successfully executed
     */
    public int executeEffects(List<String> effectStrings, EffectContext context) {
        int executed = 0;

        // Get tier info from context for placeholder replacement
        TierScalingConfig tierConfig = context.getMetadata("tierScalingConfig", null);
        Integer tier = context.getMetadata("sourceSigilTier", null);

        for (String effectString : effectStrings) {
            try {
                // Replace {param} placeholders with tier-appropriate values
                String resolvedEffect = resolvePlaceholders(effectString, tierConfig, tier);

                if (executeEffect(resolvedEffect, context)) {
                    executed++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to execute effect: " + effectString, e);
            }
        }

        return executed;
    }

    /**
     * Replace {param} placeholders in an effect string with tier-appropriate values.
     *
     * @param effectString The effect string with placeholders
     * @param tierConfig   The tier scaling config (may be null)
     * @param tier         The current tier (may be null)
     * @return The effect string with placeholders replaced
     */
    public String resolvePlaceholders(String effectString, TierScalingConfig tierConfig, Integer tier) {
        if (effectString == null || tierConfig == null || tier == null) {
            return effectString;
        }

        // Check if there are any placeholders to replace
        if (!effectString.contains("{")) {
            return effectString;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(effectString);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String paramName = matcher.group(1);

            // Get the value for this parameter at the current tier
            String replacement;
            if (tierConfig.hasParam(paramName)) {
                replacement = tierConfig.getParamValueAsString(paramName, tier);
            } else {
                // Parameter not found, leave as-is (or use 0)
                replacement = matcher.group(0); // Keep original {param}
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Parse and execute a single effect string.
     *
     * @param effectString The effect string (e.g., "POTION:SPEED:10 @Victim")
     * @param context      The effect context
     * @return true if effect was executed successfully
     */
    public boolean executeEffect(String effectString, EffectContext context) {
        ParsedEffect parsed = parseEffectString(effectString);
        if (parsed == null) {
            plugin.getLogger().warning("Failed to parse effect: " + effectString);
            return false;
        }

        Effect effect = getEffect(parsed.effectType);
        if (effect == null) {
            plugin.getLogger().warning("Unknown effect type: " + parsed.effectType);
            return false;
        }

        // Parse parameters
        EffectParams params = effect.parseParams(effectString);
        if (params == null) {
            params = new EffectParams(parsed.effectType);
        }

        // Set target from parsed string
        if (parsed.target != null) {
            params.setTarget(parsed.target);
        }

        // Create new context with params
        EffectContext execContext = EffectContext.builder(context.getPlayer(), context.getSignalType())
                .event(context.getBukkitEvent())
                .victim(context.getVictim())
                .location(context.getLocation())
                .damage(context.getDamage())
                .params(params)
                .build();

        // Copy metadata
        context.getMetadata().forEach(execContext::setMetadata);

        return effect.execute(execContext);
    }

    /**
     * Parse an effect string into its components.
     */
    public ParsedEffect parseEffectString(String effectString) {
        if (effectString == null || effectString.isEmpty()) {
            return null;
        }

        Matcher matcher = EFFECT_PATTERN.matcher(effectString.trim());
        if (!matcher.matches()) {
            // Try simple format without regex
            String[] parts = effectString.split("\\s+");
            if (parts.length > 0) {
                String[] effectParts = parts[0].split(":");
                String target = parts.length > 1 ? parts[parts.length - 1] : null;
                if (target != null && !target.startsWith("@")) {
                    target = null;
                }
                return new ParsedEffect(effectParts[0], effectString, target);
            }
            return null;
        }

        String effectType = matcher.group(1);
        String target = matcher.group(3);

        return new ParsedEffect(effectType, effectString, target);
    }

    /**
     * Validate an effect string.
     *
     * @param effectString The effect string to validate
     * @return true if the effect string is valid
     */
    public boolean isValidEffect(String effectString) {
        ParsedEffect parsed = parseEffectString(effectString);
        if (parsed == null) return false;
        return effects.containsKey(parsed.effectType.toUpperCase());
    }

    /**
     * Container for parsed effect data.
     */
    public static class ParsedEffect {
        public final String effectType;
        public final String fullString;
        public final String target;

        public ParsedEffect(String effectType, String fullString, String target) {
            this.effectType = effectType;
            this.fullString = fullString;
            this.target = target;
        }
    }
}
