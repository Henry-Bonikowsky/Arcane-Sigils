package com.zenax.armorsets.events;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.List;

/**
 * Manages condition evaluation for effects.
 * Conditions determine whether an effect should trigger based on various factors.
 */
public class ConditionManager {

    /**
     * Check if all conditions are met using AND logic.
     * Returns false if any condition fails.
     */
    public boolean checkConditions(List<String> conditions, EffectContext context) {
        return checkConditions(conditions, context, com.zenax.armorsets.sets.TriggerConfig.ConditionLogic.AND);
    }

    /**
     * Check if conditions are met using specified logic (AND/OR).
     *
     * @param conditions The list of condition strings
     * @param context    The effect context
     * @param logic      The logic mode (AND = all must pass, OR = any can pass)
     * @return True if conditions pass according to logic mode
     */
    public boolean checkConditions(List<String> conditions, EffectContext context,
                                  com.zenax.armorsets.sets.TriggerConfig.ConditionLogic logic) {
        if (conditions == null || conditions.isEmpty()) {
            return true; // No conditions = always pass
        }

        if (logic == com.zenax.armorsets.sets.TriggerConfig.ConditionLogic.OR) {
            // OR logic: At least one condition must pass
            for (String condition : conditions) {
                if (evaluateCondition(condition, context)) {
                    return true; // Any passing condition allows execution
                }
            }
            return false; // No conditions passed
        } else {
            // AND logic (default): All conditions must pass
            for (String condition : conditions) {
                if (!evaluateCondition(condition, context)) {
                    return false; // Any failed condition blocks execution
                }
            }
            return true; // All conditions passed
        }
    }

    /**
     * Evaluate a single condition string.
     * Format: CONDITION_TYPE:PARAM1:PARAM2
     */
    private boolean evaluateCondition(String condition, EffectContext context) {
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }

        String[] parts = condition.split(":");
        String type = parts[0].toUpperCase();

        try {
            return switch (type) {
                // ===== HEALTH CONDITIONS =====
                case "HEALTH_PERCENT" -> checkHealthPercent(context.getPlayer(), parts);
                case "HEALTH_BELOW" -> checkHealthBelow(context.getPlayer(), parts);
                case "HEALTH_ABOVE" -> checkHealthAbove(context.getPlayer(), parts);
                case "VICTIM_HEALTH_PERCENT" -> checkVictimHealthPercent(context, parts);

                // ===== POTION CONDITIONS =====
                case "HAS_POTION" -> checkHasPotion(context.getPlayer(), parts);
                case "NO_POTION" -> checkNoPotion(context.getPlayer(), parts);

                // ===== ENVIRONMENTAL CONDITIONS =====
                case "BIOME" -> checkBiome(context.getPlayer(), parts);
                case "BLOCK_BELOW" -> checkBlockBelow(context.getPlayer(), parts);
                case "LIGHT_LEVEL" -> checkLightLevel(context.getPlayer(), parts);
                case "IN_WATER" -> context.getPlayer().isInWater();
                case "ON_GROUND" -> context.getPlayer().isOnGround();
                case "WEATHER" -> checkWeather(context.getPlayer(), parts);

                // ===== TIME CONDITIONS =====
                case "TIME" -> checkTime(context.getPlayer(), parts);

                // ===== TRIGGER/VICTIM CONDITIONS =====
                case "HAS_VICTIM" -> context.getVictim() != null;
                case "VICTIM_IS_PLAYER" -> context.getVictim() instanceof Player;
                case "VICTIM_IS_HOSTILE" -> checkVictimIsHostile(context);

                // ===== TRIGGER TYPE CONDITIONS =====
                case "TRIGGER" -> checkTrigger(context, parts);

                // ===== ARMOR CONDITIONS =====
                case "WEARING_FULL_SET" -> checkWearingFullSet(context, parts);

                // Default: unknown condition passes (fail-safe)
                default -> {
                    System.err.println("[ArmorSets] Unknown condition: " + type);
                    yield true;
                }
            };
        } catch (Exception e) {
            System.err.println("[ArmorSets] Error evaluating condition: " + condition);
            e.printStackTrace();
            return true; // Fail-safe: pass on error
        }
    }

    // ===== HEALTH CONDITION IMPLEMENTATIONS =====

    /**
     * HEALTH_PERCENT:<50 - Health is less than 50%
     * HEALTH_PERCENT:>75 - Health is greater than 75%
     * HEALTH_PERCENT:=100 - Health is exactly 100%
     */
    private boolean checkHealthPercent(Player player, String[] parts) {
        if (parts.length < 2) return true;

        double maxHealth = player.getMaxHealth();
        double currentHealth = player.getHealth();
        double percentHealth = (currentHealth / maxHealth) * 100;

        String condition = parts[1];
        return evaluateComparison(percentHealth, condition);
    }

    /**
     * HEALTH_BELOW:10 - Health below 10 HP
     */
    private boolean checkHealthBelow(Player player, String[] parts) {
        if (parts.length < 2) return true;
        try {
            double threshold = Double.parseDouble(parts[1]);
            return player.getHealth() < threshold;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * HEALTH_ABOVE:10 - Health above 10 HP
     */
    private boolean checkHealthAbove(Player player, String[] parts) {
        if (parts.length < 2) return true;
        try {
            double threshold = Double.parseDouble(parts[1]);
            return player.getHealth() > threshold;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * VICTIM_HEALTH_PERCENT:<30 - Victim health less than 30%
     */
    private boolean checkVictimHealthPercent(EffectContext context, String[] parts) {
        if (context.getVictim() == null) return false;

        double maxHealth = context.getVictim().getMaxHealth();
        double currentHealth = context.getVictim().getHealth();
        double percentHealth = (currentHealth / maxHealth) * 100;

        if (parts.length < 2) return true;
        String condition = parts[1];
        return evaluateComparison(percentHealth, condition);
    }

    // ===== POTION CONDITION IMPLEMENTATIONS =====

    /**
     * HAS_POTION:STRENGTH - Has strength effect
     * HAS_POTION:STRENGTH:>2 - Has strength with amplifier > 2
     */
    private boolean checkHasPotion(Player player, String[] parts) {
        if (parts.length < 2) return true;

        String potionName = parts[1].toUpperCase();
        PotionEffectType potionType = PotionEffectType.getByName(potionName);

        if (potionType == null) return false;

        PotionEffect effect = player.getPotionEffect(potionType);
        if (effect == null) return false;

        // If amplifier check specified
        if (parts.length >= 3) {
            int amplifier = effect.getAmplifier();
            String amplifierCondition = parts[2];
            return evaluateComparison(amplifier, amplifierCondition);
        }

        return true;
    }

    /**
     * NO_POTION:SLOWNESS - Doesn't have slowness effect
     */
    private boolean checkNoPotion(Player player, String[] parts) {
        if (parts.length < 2) return true;

        String potionName = parts[1].toUpperCase();
        PotionEffectType potionType = PotionEffectType.getByName(potionName);

        if (potionType == null) return true;
        return player.getPotionEffect(potionType) == null;
    }

    // ===== ENVIRONMENTAL CONDITION IMPLEMENTATIONS =====

    /**
     * BIOME:NETHER - In the Nether
     * BIOME:FOREST - In a forest biome
     */
    private boolean checkBiome(Player player, String[] parts) {
        if (parts.length < 2) return true;

        String biomeName = parts[1].toUpperCase();
        Biome biome = player.getLocation().getBlock().getBiome();

        try {
            Biome targetBiome = Biome.valueOf(biomeName);
            return biome == targetBiome;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * BLOCK_BELOW:NETHERITE_BLOCK - Standing on netherite block
     */
    private boolean checkBlockBelow(Player player, String[] parts) {
        if (parts.length < 2) return true;

        String blockName = parts[1].toUpperCase();
        org.bukkit.block.Block blockBelow = player.getLocation().clone().subtract(0, 1, 0).getBlock();

        try {
            org.bukkit.Material material = org.bukkit.Material.valueOf(blockName);
            return blockBelow.getType() == material;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * LIGHT_LEVEL:<7 - Dark (light level less than 7)
     * LIGHT_LEVEL:>10 - Bright
     */
    private boolean checkLightLevel(Player player, String[] parts) {
        if (parts.length < 2) return true;

        int lightLevel = player.getLocation().getBlock().getLightLevel();
        String condition = parts[1];
        return evaluateComparison(lightLevel, condition);
    }

    /**
     * WEATHER:RAINING - It's raining
     * WEATHER:CLEAR - Weather is clear
     */
    private boolean checkWeather(Player player, String[] parts) {
        if (parts.length < 2) return true;

        String weather = parts[1].toUpperCase();
        boolean isRaining = player.getWorld().hasStorm();
        boolean isThundering = player.getWorld().isThundering();

        return switch (weather) {
            case "RAINING" -> isRaining;
            case "THUNDERING" -> isThundering;
            case "CLEAR" -> !isRaining && !isThundering;
            default -> true;
        };
    }

    // ===== TIME CONDITION IMPLEMENTATIONS =====

    /**
     * TIME:NIGHT - It's nighttime (13000-23000)
     * TIME:DAY - It's daytime (0-12000)
     */
    private boolean checkTime(Player player, String[] parts) {
        if (parts.length < 2) return true;

        long time = player.getWorld().getTime();
        String timeOfDay = parts[1].toUpperCase();

        return switch (timeOfDay) {
            case "NIGHT" -> time >= 13000 && time < 24000;
            case "DAY" -> time >= 0 && time < 12000;
            case "SUNSET" -> time >= 12000 && time < 13000;
            case "SUNRISE" -> time >= 23000 || time < 1000;
            default -> true;
        };
    }

    // ===== VICTIM CONDITION IMPLEMENTATIONS =====

    /**
     * Check if victim is a hostile mob
     */
    private boolean checkVictimIsHostile(EffectContext context) {
        if (context.getVictim() == null) return false;

        // List of hostile mobs
        String victimType = context.getVictim().getType().name();
        return switch (victimType) {
            case "ZOMBIE", "SKELETON", "CREEPER", "SPIDER", "ENDERMAN", "WITCH",
                 "WITHER", "ENDER_DRAGON", "PIGLIN", "ZOMBIFIED_PIGLIN", "HUSK",
                 "STRAY", "CAVE_SPIDER", "SILVERFISH", "BLAZE", "GHAST", "SLIME",
                 "MAGMA_CUBE", "PHANTOM", "DROWNED", "WARDEN" -> true;
            default -> false;
        };
    }

    // ===== TRIGGER CONDITIONS =====

    /**
     * TRIGGER:ATTACK - Only if trigger type is ATTACK
     */
    private boolean checkTrigger(EffectContext context, String[] parts) {
        if (parts.length < 2) return true;

        String triggerName = parts[1].toUpperCase();
        return context.getTriggerType().name().equals(triggerName);
    }

    // ===== ARMOR CONDITIONS =====

    /**
     * WEARING_FULL_SET:arcanist_t1 - Wearing full set (if set ID provided)
     */
    private boolean checkWearingFullSet(EffectContext context, String[] parts) {
        // This would need ArmorSetsPlugin reference, defaulting to true for now
        return true; // TODO: Inject ArmorSetsPlugin to check actual set
    }

    // ===== UTILITY METHODS =====

    /**
     * Evaluate a comparison condition.
     * Format: <50 (less than), >75 (greater than), =100 (equals), >=50, <=50
     */
    private boolean evaluateComparison(double value, String condition) {
        if (condition.startsWith(">=")) {
            return value >= Double.parseDouble(condition.substring(2));
        } else if (condition.startsWith("<=")) {
            return value <= Double.parseDouble(condition.substring(2));
        } else if (condition.startsWith("<")) {
            return value < Double.parseDouble(condition.substring(1));
        } else if (condition.startsWith(">")) {
            return value > Double.parseDouble(condition.substring(1));
        } else if (condition.startsWith("=")) {
            return value == Double.parseDouble(condition.substring(1));
        }
        return true;
    }

    /**
     * Evaluate comparison with integer values
     */
    private boolean evaluateComparison(int value, String condition) {
        return evaluateComparison((double) value, condition);
    }
}
