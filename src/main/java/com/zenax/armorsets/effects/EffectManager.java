package com.zenax.armorsets.effects;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.effects.impl.*;
import com.zenax.armorsets.events.TriggerType;

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

    public EffectManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        registerDefaultEffects();
    }

    private void registerDefaultEffects() {
        // Damage/Combat Effects
        registerEffect(new IncreaseDamageEffect());
        registerEffect(new DisintegrateEffect());
        registerEffect(new AegisEffect());
        registerEffect(new WardEffect());
        registerEffect(new CancelEventEffect());
        registerEffect(new LifestealEffect());

        // Movement Effects
        registerEffect(new TeleportRandomEffect());
        registerEffect(new SmokebombEffect());
        registerEffect(new DodgeEffect());
        registerEffect(new MomentumEffect());
        registerEffect(new WingsEffect());
        registerEffect(new SpringsEffect());
        registerEffect(new GearsEffect());
        registerEffect(new FeatherweightEffect());
        registerEffect(new JellylegsEffect());

        // Healing/Sustain Effects
        registerEffect(new HealEffect());
        registerEffect(new DevourEffect());
        registerEffect(new ReplenishEffect());
        registerEffect(new PatchEffect());
        registerEffect(new RestoreEffect());
        registerEffect(new PhoenixEffect());
        registerEffect(new AngelicEffect());
        registerEffect(new ImmortalEffect());

        // Potion Effect
        registerEffect(new PotionEffectEffect());

        // Utility Effects
        registerEffect(new SoulboundEffect());
        registerEffect(new UnbreakableEffect());
        registerEffect(new AquaEffect());
        registerEffect(new NightowlEffect());
        registerEffect(new LucidEffect());
        registerEffect(new InquisitiveEffect());
        registerEffect(new EnlightenedEffect());
        registerEffect(new ImplantsEffect());
        registerEffect(new GuardiansEffect());
        registerEffect(new AllureEffect());
        registerEffect(new RushEffect());

        // Visual/Audio Effects
        registerEffect(new ParticleEffect());
        registerEffect(new SoundEffect());
        registerEffect(new MessageEffect());

        // Spawning Effects
        registerEffect(new SpawnEntityEffect());

        // Special Effects
        registerEffect(new BleedingEffect());
        registerEffect(new FreezingEffect());
        registerEffect(new BlinkEffect());

        plugin.getLogger().info("Registered " + effects.size() + " effect types");
    }

    public void registerEffect(Effect effect) {
        effects.put(effect.getId().toUpperCase(), effect);
    }

    public Effect getEffect(String id) {
        return effects.get(id.toUpperCase());
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

        for (String effectString : effectStrings) {
            try {
                if (executeEffect(effectString, context)) {
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
        EffectContext execContext = EffectContext.builder(context.getPlayer(), context.getTriggerType())
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
