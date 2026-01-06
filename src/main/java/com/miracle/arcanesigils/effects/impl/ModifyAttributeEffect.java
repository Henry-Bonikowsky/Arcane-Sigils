package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Modifies any entity attribute temporarily using attribute modifiers.
 * More flexible than potion effects - supports all attributes and percentage changes.
 *
 * Format: MODIFY_ATTRIBUTE @Target
 *
 * Params (YAML):
 *   attribute: GENERIC_MOVEMENT_SPEED  # The attribute to modify
 *   operation: MULTIPLY_SCALAR_1       # ADD_NUMBER, ADD_SCALAR, MULTIPLY_SCALAR_1
 *   value: -0.25                       # The modifier value (negative = reduction)
 *   duration: 5                        # Duration in seconds (ignored if persistent/permanent)
 *   target: @Victim                    # Who to affect
 *   persistent: true                   # If true, uses stable key that refreshes instead of stacking
 *   permanent: true                    # If true, modifier never expires (for EQUIP signals)
 *
 * Operations explained:
 *   ADD_NUMBER: Adds flat value (e.g., +2 max health)
 *   ADD_SCALAR: Adds percentage of base (e.g., 0.1 = +10% of base)
 *   MULTIPLY_SCALAR_1: Multiplies final value (e.g., -0.25 = 25% reduction)
 *
 * Common use cases:
 *   - Slow: attribute=GENERIC_MOVEMENT_SPEED, operation=MULTIPLY_SCALAR_1, value=-0.25 (25% slow)
 *   - Speed boost: attribute=GENERIC_MOVEMENT_SPEED, operation=MULTIPLY_SCALAR_1, value=0.5 (50% faster)
 *   - Max health boost: attribute=GENERIC_MAX_HEALTH, operation=ADD_NUMBER, value=5 (+5 hearts)
 *   - Attack damage boost: attribute=GENERIC_ATTACK_DAMAGE, operation=MULTIPLY_SCALAR_1, value=0.25 (25% more damage)
 *
 * NOTE: For GENERIC_MAX_HEALTH with ADD_NUMBER, value is in HEARTS (auto-converted to health points).
 *       value=1 means +1 heart, value=5 means +5 hearts.
 *
 * Persistent mode (for passives like Extra Padding with EFFECT_STATIC):
 *   When persistent=true, uses a stable modifier key based on sigil ID + attribute.
 *   This means re-applying the effect will update the existing modifier instead of stacking.
 *   The modifier expires after 2 seconds but is refreshed every 1 second by EFFECT_STATIC.
 *
 * Permanent mode (for EQUIP/UNEQUIP signals):
 *   When permanent=true, the modifier never auto-expires. It must be explicitly removed
 *   (e.g., by UNEQUIP signal or REMOVE_ATTRIBUTE_MODIFIER effect).
 *   For max health: only fills new hearts if player was already at full health (prevents combat exploits).
 */
public class ModifyAttributeEffect extends AbstractEffect {

    private static final String MODIFIER_NAME = "arcane_sigils_attr";
    private static final String PERSISTENT_PREFIX = "arcane_sigils_persist";

    public ModifyAttributeEffect() {
        super("MODIFY_ATTRIBUTE", "Temporarily modify any entity attribute");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        // Defaults
        params.set("attribute", "GENERIC_MOVEMENT_SPEED");
        params.set("operation", "MULTIPLY_SCALAR_1");
        params.setValue(-0.25); // 25% reduction by default
        params.setDuration(5);

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) {
            debug("MODIFY_ATTRIBUTE effect requires params");
            return false;
        }

        // Handle @Nearby targets - apply to multiple entities
        String targetStr = params.getTarget();
        if (targetStr != null && targetStr.startsWith("@Nearby")) {
            double radius = parseNearbyRadius(targetStr, 5);
            boolean anySuccess = false;
            for (LivingEntity entity : getNearbyEntities(context, radius)) {
                if (applyToTarget(entity, params, context)) {
                    anySuccess = true;
                }
            }
            return anySuccess;
        }

        // Single target
        LivingEntity target = getTarget(context);
        if (target == null) {
            debug("MODIFY_ATTRIBUTE effect requires a target");
            return false;
        }

        return applyToTarget(target, params, context);
    }

    /**
     * Apply the attribute modifier to a single target.
     */
    private boolean applyToTarget(LivingEntity target, EffectParams params, EffectContext context) {
        // Get attribute name
        String attributeName = params.getString("attribute", "GENERIC_MOVEMENT_SPEED");
        Attribute attribute = parseAttribute(attributeName);
        if (attribute == null) {
            debug("Unknown attribute: " + attributeName);
            return false;
        }

        // Get the attribute instance
        AttributeInstance attrInstance = target.getAttribute(attribute);
        if (attrInstance == null) {
            debug("Target has no " + attribute.name() + " attribute");
            return false;
        }

        // Get operation
        String operationName = params.getString("operation", "MULTIPLY_SCALAR_1");
        AttributeModifier.Operation operation = parseOperation(operationName);

        // Get value - support both 'value' key and percentage for backwards compatibility
        double value = params.getValue();
        if (value == 0) {
            // Check for percentage param (for backwards compat with SLOW_MOVEMENT)
            double percentage = params.getDouble("percentage", 0);
            if (percentage != 0) {
                // Convert percentage to multiplier (e.g., 25% slow = -0.25)
                value = -(percentage / 100.0);
            } else {
                value = -0.25; // Default 25% reduction
            }
        }

        // For max health with ADD_NUMBER: treat value as hearts (multiply by 2 for health points)
        // This makes config intuitive: value=1 means +1 heart, value=5 means +5 hearts
        if (attribute == Attribute.MAX_HEALTH && operation == AttributeModifier.Operation.ADD_NUMBER) {
            value = value * 2;
        }

        // Check if this is a persistent or permanent modifier
        boolean persistent = params.getBoolean("persistent", false);
        boolean permanent = params.getBoolean("permanent", false);
        String sigilId = context.getSigilId();

        // Track if this is a first application (no existing modifier found)
        // Used to determine if we should fill max health on health boosts
        boolean isFirstApplication = true;

        NamespacedKey key;
        if ((persistent || permanent) && sigilId != null) {
            // Use stable key based on sigil ID + attribute for persistent/permanent modifiers
            // Sanitize to valid NamespacedKey format: lowercase, alphanumeric + underscores
            String sanitized = (sigilId + "_" + attributeName).toLowerCase().replaceAll("[^a-z0-9_]", "_");
            String keyString = PERSISTENT_PREFIX + "_" + sanitized;
            key = new NamespacedKey(getPlugin(), keyString);

            // Remove existing modifier with this key before adding new one
            // Compare by key string to ensure proper matching
            AttributeModifier toRemove = null;
            for (AttributeModifier existing : attrInstance.getModifiers()) {
                if (existing.getKey().getKey().equals(keyString)) {
                    toRemove = existing;
                    break;
                }
            }
            if (toRemove != null) {
                attrInstance.removeModifier(toRemove);
                isFirstApplication = false; // This is a refresh, not first application
            }
        } else {
            // Generate unique key for temporary modifier
            UUID modifierId = UUID.randomUUID();
            key = new NamespacedKey(getPlugin(), MODIFIER_NAME + "_" + modifierId.toString().substring(0, 8));
        }

        // Create the attribute modifier
        AttributeModifier modifier = new AttributeModifier(key, value, operation);

        // For max health boosts: capture current health BEFORE applying modifier
        // to determine if player was at full health
        double healthBeforeModifier = 0;
        double maxHealthBeforeModifier = 0;
        if (attribute == Attribute.MAX_HEALTH && target instanceof Player player) {
            healthBeforeModifier = player.getHealth();
            maxHealthBeforeModifier = attrInstance.getValue();
        }

        // Apply the modifier
        attrInstance.addModifier(modifier);

        // If this is max health and we're adding, only fill new hearts if:
        // 1. This is the first application (not a refresh)
        // 2. Player was already at full health (prevents combat healing exploit)
        if (attribute == Attribute.MAX_HEALTH && value > 0 && target instanceof Player player) {
            // Check if player was at full health before the modifier (with small tolerance for rounding)
            boolean wasAtFullHealth = healthBeforeModifier >= maxHealthBeforeModifier - 0.5;

            if (isFirstApplication && wasAtFullHealth) {
                // Player was at full health, fill the new hearts too
                player.setHealth(attrInstance.getValue());
            }
        }

        // Get duration - permanent and persistent modifiers never auto-expire
        // Permanent: for EQUIP signals - must be explicitly removed
        // Persistent: for EFFECT_STATIC - removed when armor is unequipped (armor check task handles this)
        // Temporary: uses configured duration
        int durationSeconds;
        boolean shouldScheduleRemoval;

        if (permanent || persistent) {
            // Permanent/persistent modifier - never auto-expires
            // Persistent modifiers are cleaned up when armor is removed (EFFECT_STATIC stops)
            shouldScheduleRemoval = false;
            durationSeconds = 0;
        } else {
            // Temporary modifier - uses configured duration
            shouldScheduleRemoval = true;
            durationSeconds = params.getDuration() > 0 ? params.getDuration() : 5;
        }

        // Schedule removal of modifier after duration (unless permanent)
        if (shouldScheduleRemoval) {
            ArmorSetsPlugin plugin = getPlugin();
            final AttributeModifier finalModifier = modifier;
            final Attribute finalAttribute = attribute;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (target.isValid()) {
                    attrInstance.removeModifier(finalModifier);

                    // If max health was reduced and player is now over max, cap it
                    if (finalAttribute == Attribute.MAX_HEALTH && target instanceof Player player) {
                        if (player.getHealth() > attrInstance.getValue()) {
                            player.setHealth(attrInstance.getValue());
                        }
                    }
                }
            }, durationSeconds * 20L);
        }

        // Removed verbose debug logging - these fire too frequently

        // Visual feedback based on attribute type (skip for persistent/permanent to avoid spam)
        if (!persistent && !permanent) {
            spawnAttributeParticles(target, attribute, value > 0);

            // Sound feedback
            if (target instanceof Player player) {
                Sound sound = value > 0 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_PLAYER_HURT;
                player.playSound(player.getLocation(), sound, 0.5f, value > 0 ? 1.5f : 0.5f);
            }
        }

        return true;
    }

    /**
     * Parse attribute name to Attribute enum.
     */
    private Attribute parseAttribute(String name) {
        if (name == null) return null;

        String upper = name.toUpperCase().replace(" ", "_");

        // Try direct match first
        try {
            return Attribute.valueOf(upper);
        } catch (IllegalArgumentException ignored) {}

        // Try with GENERIC_ prefix
        if (!upper.startsWith("GENERIC_") && !upper.startsWith("PLAYER_")) {
            try {
                return Attribute.valueOf("GENERIC_" + upper);
            } catch (IllegalArgumentException ignored) {}
        }

        // Common aliases
        return switch (upper) {
            case "SPEED", "MOVEMENT_SPEED", "MOVE_SPEED" -> Attribute.MOVEMENT_SPEED;
            case "HEALTH", "MAX_HEALTH" -> Attribute.MAX_HEALTH;
            case "DAMAGE", "ATTACK_DAMAGE", "ATTACK" -> Attribute.ATTACK_DAMAGE;
            case "ARMOR" -> Attribute.ARMOR;
            case "ARMOR_TOUGHNESS", "TOUGHNESS" -> Attribute.ARMOR_TOUGHNESS;
            case "ATTACK_SPEED" -> Attribute.ATTACK_SPEED;
            case "KNOCKBACK_RESISTANCE", "KB_RESIST" -> Attribute.KNOCKBACK_RESISTANCE;
            case "LUCK" -> Attribute.LUCK;
            case "FOLLOW_RANGE", "RANGE" -> Attribute.FOLLOW_RANGE;
            default -> null;
        };
    }

    /**
     * Parse operation name to Operation enum.
     */
    private AttributeModifier.Operation parseOperation(String name) {
        if (name == null) return AttributeModifier.Operation.MULTIPLY_SCALAR_1;

        String upper = name.toUpperCase().replace(" ", "_");

        return switch (upper) {
            case "ADD", "ADD_NUMBER", "FLAT" -> AttributeModifier.Operation.ADD_NUMBER;
            case "ADD_SCALAR", "ADD_PERCENT", "BASE_PERCENT" -> AttributeModifier.Operation.ADD_SCALAR;
            case "MULTIPLY", "MULTIPLY_SCALAR_1", "PERCENT", "FINAL_PERCENT" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            default -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
        };
    }

    /**
     * Spawn particles based on attribute type and whether it's a buff or debuff.
     */
    private void spawnAttributeParticles(LivingEntity target, Attribute attribute, boolean isBuff) {
        org.bukkit.Color color;

        // Color based on attribute type
        if (attribute.name().contains("HEALTH")) {
            color = isBuff ? org.bukkit.Color.fromRGB(255, 100, 100) : org.bukkit.Color.fromRGB(100, 0, 0);
        } else if (attribute.name().contains("MOVEMENT") || attribute.name().contains("SPEED")) {
            color = isBuff ? org.bukkit.Color.fromRGB(100, 200, 255) : org.bukkit.Color.fromRGB(60, 60, 80);
        } else if (attribute.name().contains("DAMAGE") || attribute.name().contains("ATTACK")) {
            color = isBuff ? org.bukkit.Color.fromRGB(255, 50, 50) : org.bukkit.Color.fromRGB(150, 50, 50);
        } else if (attribute.name().contains("ARMOR")) {
            color = isBuff ? org.bukkit.Color.fromRGB(200, 200, 255) : org.bukkit.Color.fromRGB(100, 100, 150);
        } else {
            color = isBuff ? org.bukkit.Color.fromRGB(100, 255, 100) : org.bukkit.Color.fromRGB(150, 150, 150);
        }

        target.getWorld().spawnParticle(
                Particle.DUST,
                target.getLocation().add(0, 1, 0),
                25,
                0.4, 0.6, 0.4,
                0.05,
                new Particle.DustOptions(color, 1.0f)
        );
    }

    /**
     * Get all available attributes for the browser.
     */
    public static String[] getAvailableAttributes() {
        return new String[] {
            "GENERIC_MOVEMENT_SPEED",
            "GENERIC_MAX_HEALTH",
            "GENERIC_ATTACK_DAMAGE",
            "GENERIC_ATTACK_SPEED",
            "GENERIC_ARMOR",
            "GENERIC_ARMOR_TOUGHNESS",
            "GENERIC_KNOCKBACK_RESISTANCE",
            "GENERIC_LUCK",
            "GENERIC_FOLLOW_RANGE",
            "GENERIC_ATTACK_KNOCKBACK",
            "GENERIC_FLYING_SPEED",
            "GENERIC_MAX_ABSORPTION",
            "GENERIC_SCALE",
            "GENERIC_STEP_HEIGHT",
            "GENERIC_GRAVITY",
            "GENERIC_SAFE_FALL_DISTANCE",
            "GENERIC_FALL_DAMAGE_MULTIPLIER",
            "GENERIC_JUMP_STRENGTH",
            "GENERIC_BURNING_TIME",
            "GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE",
            "GENERIC_MOVEMENT_EFFICIENCY",
            "GENERIC_OXYGEN_BONUS",
            "GENERIC_WATER_MOVEMENT_EFFICIENCY",
            "PLAYER_BLOCK_INTERACTION_RANGE",
            "PLAYER_ENTITY_INTERACTION_RANGE",
            "PLAYER_BLOCK_BREAK_SPEED",
            "PLAYER_MINING_EFFICIENCY",
            "PLAYER_SNEAKING_SPEED",
            "PLAYER_SUBMERGED_MINING_SPEED",
            "PLAYER_SWEEPING_DAMAGE_RATIO"
        };
    }

    /**
     * Get a friendly display name for an attribute.
     */
    public static String getAttributeDisplayName(String attribute) {
        if (attribute == null) return "Unknown";

        return switch (attribute) {
            case "GENERIC_MOVEMENT_SPEED" -> "Movement Speed";
            case "GENERIC_MAX_HEALTH" -> "Max Health";
            case "GENERIC_ATTACK_DAMAGE" -> "Attack Damage";
            case "GENERIC_ATTACK_SPEED" -> "Attack Speed";
            case "GENERIC_ARMOR" -> "Armor";
            case "GENERIC_ARMOR_TOUGHNESS" -> "Armor Toughness";
            case "GENERIC_KNOCKBACK_RESISTANCE" -> "Knockback Resistance";
            case "GENERIC_LUCK" -> "Luck";
            case "GENERIC_FOLLOW_RANGE" -> "Follow Range";
            case "GENERIC_ATTACK_KNOCKBACK" -> "Attack Knockback";
            case "GENERIC_FLYING_SPEED" -> "Flying Speed";
            case "GENERIC_MAX_ABSORPTION" -> "Max Absorption";
            case "GENERIC_SCALE" -> "Entity Scale";
            case "GENERIC_STEP_HEIGHT" -> "Step Height";
            case "GENERIC_GRAVITY" -> "Gravity";
            case "GENERIC_SAFE_FALL_DISTANCE" -> "Safe Fall Distance";
            case "GENERIC_FALL_DAMAGE_MULTIPLIER" -> "Fall Damage Multiplier";
            case "GENERIC_JUMP_STRENGTH" -> "Jump Strength";
            case "GENERIC_BURNING_TIME" -> "Burning Time";
            case "GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE" -> "Explosion KB Resistance";
            case "GENERIC_MOVEMENT_EFFICIENCY" -> "Movement Efficiency";
            case "GENERIC_OXYGEN_BONUS" -> "Oxygen Bonus";
            case "GENERIC_WATER_MOVEMENT_EFFICIENCY" -> "Water Movement";
            case "PLAYER_BLOCK_INTERACTION_RANGE" -> "Block Interaction Range";
            case "PLAYER_ENTITY_INTERACTION_RANGE" -> "Entity Interaction Range";
            case "PLAYER_BLOCK_BREAK_SPEED" -> "Block Break Speed";
            case "PLAYER_MINING_EFFICIENCY" -> "Mining Efficiency";
            case "PLAYER_SNEAKING_SPEED" -> "Sneaking Speed";
            case "PLAYER_SUBMERGED_MINING_SPEED" -> "Underwater Mining Speed";
            case "PLAYER_SWEEPING_DAMAGE_RATIO" -> "Sweeping Damage Ratio";
            default -> attribute.replace("GENERIC_", "").replace("PLAYER_", "").replace("_", " ");
        };
    }

    /**
     * Get a description for an attribute.
     */
    public static String getAttributeDescription(String attribute) {
        if (attribute == null) return "";

        return switch (attribute) {
            case "GENERIC_MOVEMENT_SPEED" -> "Walking/running speed (base: 0.1)";
            case "GENERIC_MAX_HEALTH" -> "Maximum health points (base: 20 = 10 hearts)";
            case "GENERIC_ATTACK_DAMAGE" -> "Base melee damage (base: 1.0)";
            case "GENERIC_ATTACK_SPEED" -> "Attacks per second (base: 4.0)";
            case "GENERIC_ARMOR" -> "Damage reduction from armor (base: 0)";
            case "GENERIC_ARMOR_TOUGHNESS" -> "Reduces armor penetration (base: 0)";
            case "GENERIC_KNOCKBACK_RESISTANCE" -> "Chance to resist knockback (0-1)";
            case "GENERIC_LUCK" -> "Affects loot table quality";
            case "GENERIC_FOLLOW_RANGE" -> "AI detection range for mobs";
            case "GENERIC_ATTACK_KNOCKBACK" -> "Knockback dealt by attacks";
            case "GENERIC_FLYING_SPEED" -> "Flight speed when using elytra";
            case "GENERIC_MAX_ABSORPTION" -> "Maximum absorption hearts";
            case "GENERIC_SCALE" -> "Entity size multiplier";
            case "GENERIC_STEP_HEIGHT" -> "Max height entity can step up";
            case "GENERIC_GRAVITY" -> "Gravitational pull strength";
            case "GENERIC_SAFE_FALL_DISTANCE" -> "Fall distance before damage";
            case "GENERIC_FALL_DAMAGE_MULTIPLIER" -> "Fall damage multiplier";
            case "GENERIC_JUMP_STRENGTH" -> "Jump height/strength";
            case "GENERIC_BURNING_TIME" -> "How long entity burns";
            case "GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE" -> "Explosion knockback resistance";
            case "GENERIC_MOVEMENT_EFFICIENCY" -> "Movement through difficult terrain";
            case "GENERIC_OXYGEN_BONUS" -> "Underwater breathing bonus";
            case "GENERIC_WATER_MOVEMENT_EFFICIENCY" -> "Swimming speed";
            case "PLAYER_BLOCK_INTERACTION_RANGE" -> "How far player can interact with blocks";
            case "PLAYER_ENTITY_INTERACTION_RANGE" -> "How far player can interact with entities";
            case "PLAYER_BLOCK_BREAK_SPEED" -> "Mining speed multiplier";
            case "PLAYER_MINING_EFFICIENCY" -> "Mining efficiency bonus";
            case "PLAYER_SNEAKING_SPEED" -> "Speed while sneaking";
            case "PLAYER_SUBMERGED_MINING_SPEED" -> "Mining speed underwater";
            case "PLAYER_SWEEPING_DAMAGE_RATIO" -> "Sweeping edge damage ratio";
            default -> "Modifies " + attribute.replace("_", " ").toLowerCase();
        };
    }
}
