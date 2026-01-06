package com.miracle.arcanesigils.events;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.flow.FlowConfig.ConditionLogic;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Manages condition evaluation for effects.
 * Conditions determine whether an effect should signal based on various factors.
 */
public class ConditionManager {

    /**
     * Check if all conditions are met using AND logic.
     * Returns false if any condition fails.
     */
    public boolean checkConditions(List<String> conditions, EffectContext context) {
        return checkConditions(conditions, context, ConditionLogic.AND);
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
                                  ConditionLogic logic) {
        if (conditions == null || conditions.isEmpty()) {
            return true; // No conditions = always pass
        }

        com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] Checking %d conditions with %s logic", conditions.size(), logic);

        if (logic == ConditionLogic.OR) {
            // OR logic: At least one condition must pass
            for (String condition : conditions) {
                boolean result = evaluateCondition(condition, context);
                com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] %s = %s (OR mode)", condition, result);
                if (result) {
                    return true; // Any passing condition allows execution
                }
            }
            com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] All conditions failed in OR mode");
            return false; // No conditions passed
        } else {
            // AND logic (default): All conditions must pass
            for (String condition : conditions) {
                boolean result = evaluateCondition(condition, context);
                com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] %s = %s (AND mode)", condition, result);
                if (!result) {
                    com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] Failed condition: %s", condition);
                    return false; // Any failed condition blocks execution
                }
            }
            com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] All conditions passed");
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
        String type = parts[0].toUpperCase().trim();

        try {
            return switch (type) {
                // ===== HEALTH CONDITIONS =====
                case "HEALTH_PERCENT" -> checkHealthPercent(context.getPlayer(), parts);
                case "HEALTH" -> checkHealth(context.getPlayer(), parts);
                case "VICTIM_HEALTH_PERCENT" -> checkVictimHealthPercent(context, parts);

                // ===== POTION CONDITIONS =====
                case "HAS_POTION" -> checkHasPotion(context.getPlayer(), parts);
                case "NO_POTION" -> checkNoPotion(context.getPlayer(), parts);

                // ===== ENVIRONMENTAL CONDITIONS =====
                case "BIOME" -> checkBiome(context.getPlayer(), parts);
                case "BLOCK_BELOW" -> checkBlockBelow(context.getPlayer(), parts);
                case "LIGHT_LEVEL" -> checkLightLevel(context.getPlayer(), parts);
                case "IN_WATER" -> context.getPlayer().isInWater();
                case "ON_GROUND" -> {
                    boolean onGround = context.getPlayer().isOnGround();
                    com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] ON_GROUND check: player %s isOnGround=%s",
                        context.getPlayer().getName(), onGround);
                    yield onGround;
                }
                case "IN_AIR" -> {
                    // More robust IN_AIR check - isOnGround() can be unreliable
                    Player p = context.getPlayer();
                    boolean clientOnGround = p.isOnGround();

                    // Also check if there's a solid block within 0.3 blocks below player's feet
                    // This handles cases where isOnGround() is wrong due to network lag
                    org.bukkit.Location feet = p.getLocation();
                    org.bukkit.block.Block below = feet.clone().subtract(0, 0.3, 0).getBlock();
                    boolean solidBelow = below.getType().isSolid();

                    // Player is in air if: not on ground AND no solid block directly below
                    // OR: has significant upward/downward velocity (clearly jumping/falling)
                    double yVel = p.getVelocity().getY();
                    boolean hasVerticalVelocity = Math.abs(yVel) > 0.1;

                    // In air = (not on ground AND no solid below) OR (significant vertical velocity AND not on ground)
                    boolean inAir = (!clientOnGround && !solidBelow) || (hasVerticalVelocity && !clientOnGround);

                    com.miracle.arcanesigils.utils.LogHelper.debug(
                        "[Conditions] IN_AIR: player=%s onGround=%s solidBelow=%s yVel=%.2f -> inAir=%s",
                        p.getName(), clientOnGround, solidBelow, yVel, inAir);
                    yield inAir;
                }
                case "HUNGER" -> checkHunger(context.getPlayer(), parts);
                case "WEATHER" -> checkWeather(context.getPlayer(), parts);

                // ===== TIME CONDITIONS =====
                case "TIME" -> checkTime(context.getPlayer(), parts);

                // ===== SIGNAL/VICTIM CONDITIONS =====
                case "HAS_VICTIM" -> context.getVictim() != null;
                case "VICTIM_IS_PLAYER" -> context.getVictim() instanceof Player;
                case "VICTIM_IS_HOSTILE" -> checkVictimIsHostile(context);

                // ===== SIGNAL TYPE CONDITIONS =====
                case "SIGNAL" -> checkSignal(context, parts);

                // ===== ARMOR CONDITIONS =====
                case "WEARING_FULL_SET" -> checkWearingFullSet(context, parts);

                // ===== PLAYER STATE CONDITIONS =====
                case "SNEAKING" -> context.getPlayer().isSneaking();
                case "SPRINTING" -> context.getPlayer().isSprinting();
                case "FLYING" -> context.getPlayer().isFlying() || context.getPlayer().isGliding();
                case "SWIMMING" -> context.getPlayer().isSwimming();

                // ===== EQUIPMENT CONDITIONS =====
                case "MAIN_HAND" -> checkMainHand(context.getPlayer(), parts);
                case "HAS_ENCHANT" -> checkHasEnchant(context.getPlayer(), parts);
                case "HOLDING_SIGIL_ITEM" -> checkHoldingSigilItem(context);
                case "DURABILITY_PERCENT" -> checkDurabilityPercent(context, parts);

                // ===== ADDITIONAL COMBAT CONDITIONS =====
                case "HAS_MARK" -> checkHasMark(context, parts);
                case "HAS_TARGET" -> checkHasTarget(context, parts);
                case "CRITICAL_HIT" -> checkCriticalHit(context);
                case "VICTIM_IS_UNDEAD" -> checkVictimIsUndead(context);
                case "ON_FIRE" -> checkOnFire(context, parts);

                // ===== ADDITIONAL ENVIRONMENTAL CONDITIONS =====
                case "DIMENSION" -> checkDimension(context.getPlayer(), parts);
                case "Y_LEVEL" -> checkYLevel(context.getPlayer(), parts);

                // ===== ADDITIONAL META CONDITIONS =====
                case "EXPERIENCE_LEVEL" -> checkExperienceLevel(context.getPlayer(), parts);

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
     * HEALTH:<10 - Health is less than 10 HP
     * HEALTH:>15 - Health is greater than 15 HP
     * HEALTH:<=5 - Health is 5 HP or less
     * HEALTH:>=10 - Health is 10 HP or more
     * HEALTH:=20 - Health is exactly 20 HP
     */
    private boolean checkHealth(Player player, String[] parts) {
        if (parts.length < 2) return true;

        double currentHealth = player.getHealth();
        String condition = parts[1];
        return evaluateComparison(currentHealth, condition);
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

    /**
     * HUNGER:<10 - Hunger is less than 10
     * HUNGER:>15 - Hunger is greater than 15
     * HUNGER:<=5 - Hunger is 5 or less
     * HUNGER:>=10 - Hunger is 10 or more
     * HUNGER:=20 - Hunger is exactly full (20)
     */
    private boolean checkHunger(Player player, String[] parts) {
        if (parts.length < 2) return true;

        int foodLevel = player.getFoodLevel();
        String condition = parts[1];
        return evaluateComparison(foodLevel, condition);
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

    // ===== MARK CONDITIONS =====

    /**
     * HAS_MARK:MARK_NAME - Check if victim/target has a mark
     * HAS_MARK:MARK_NAME:@Self - Check if player has a mark
     * HAS_MARK:MARK_NAME:@Target - Check if ability UI target has a mark
     * HAS_MARK:MARK_NAME:@Victim - Check if combat victim has a mark
     */
    private boolean checkHasMark(EffectContext context, String[] parts) {
        if (parts.length < 2) {
            return false;
        }

        String markName = parts[1].toUpperCase().trim();

        com.miracle.arcanesigils.effects.MarkManager markManager =
            com.miracle.arcanesigils.ArmorSetsPlugin.getInstance().getMarkManager();

        if (markManager == null) {
            return false;
        }

        // Determine target entity to check
        org.bukkit.entity.LivingEntity targetEntity = null;

        // Check for explicit target modifier (parts[2] if exists)
        if (parts.length >= 3) {
            String targetMod = parts[2].toLowerCase().trim();

            if (targetMod.equals("@self")) {
                targetEntity = context.getPlayer();
            } else if (targetMod.equals("@target")) {
                com.miracle.arcanesigils.binds.TargetGlowManager glowManager =
                    com.miracle.arcanesigils.ArmorSetsPlugin.getInstance().getTargetGlowManager();
                if (glowManager != null) {
                    targetEntity = glowManager.getTarget(context.getPlayer());
                }
            } else if (targetMod.equals("@victim")) {
                targetEntity = context.getVictim();
            }
        } else {
            // Default: check victim first, then ability UI target
            if (context.getVictim() != null) {
                targetEntity = context.getVictim();
            } else {
                com.miracle.arcanesigils.binds.TargetGlowManager glowManager =
                    com.miracle.arcanesigils.ArmorSetsPlugin.getInstance().getTargetGlowManager();
                if (glowManager != null) {
                    targetEntity = glowManager.getTarget(context.getPlayer());
                }
            }
        }

        if (targetEntity == null || targetEntity.isDead()) {
            return false;
        }

        return markManager.hasMark(targetEntity, markName);
    }

    // ===== SIGNAL CONDITIONS =====

    /**
     * SIGNAL:ATTACK - Only if signal type is ATTACK
     */
    private boolean checkSignal(EffectContext context, String[] parts) {
        if (parts.length < 2) return true;

        String signalName = parts[1].toUpperCase();
        return context.getSignalType().name().equals(signalName);
    }

    // ===== ARMOR CONDITIONS =====

    /**
     * WEARING_FULL_SET:arcanist_t1 - Wearing full set (if set ID provided)
     */
    private boolean checkWearingFullSet(EffectContext context, String[] parts) {
        // This would need ArmorSetsPlugin reference, defaulting to true for now
        return true; // TODO: Inject ArmorSetsPlugin to check actual set
    }

    // ===== PLAYER STATE CONDITION IMPLEMENTATIONS =====

    // Note: SNEAKING, SPRINTING, FLYING, SWIMMING are inline in the switch

    // ===== EQUIPMENT CONDITION IMPLEMENTATIONS =====

    /**
     * MAIN_HAND:DIAMOND_SWORD - Holding a diamond sword
     */
    private boolean checkMainHand(Player player, String[] parts) {
        if (parts.length < 2) return true;

        String materialName = parts[1].toUpperCase();
        org.bukkit.Material heldMaterial = player.getInventory().getItemInMainHand().getType();

        try {
            org.bukkit.Material targetMaterial = org.bukkit.Material.valueOf(materialName);
            return heldMaterial == targetMaterial;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * HAS_ENCHANT:SHARPNESS - Held item has sharpness enchantment
     */
    private boolean checkHasEnchant(Player player, String[] parts) {
        if (parts.length < 2) return true;

        String enchantName = parts[1].toUpperCase();
        org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == org.bukkit.Material.AIR) return false;

        try {
            org.bukkit.enchantments.Enchantment enchant = org.bukkit.enchantments.Enchantment.getByName(enchantName);
            if (enchant == null) {
                // Try registry lookup for newer enchantment names
                org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(enchantName.toLowerCase());
                enchant = org.bukkit.Registry.ENCHANTMENT.get(key);
            }
            if (enchant == null) return false;
            return item.containsEnchantment(enchant);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * HOLDING_SIGIL_ITEM - Player is holding the item that has this sigil socketed
     * Checks if the sourceItem (the item containing the sigil) is in the player's hand
     */
    private boolean checkHoldingSigilItem(EffectContext context) {
        // Get the source item from metadata (set by SignalHandler)
        org.bukkit.inventory.ItemStack sourceItem = context.getMetadata("sourceItem", null);
        if (sourceItem == null) {
            com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] HOLDING_SIGIL_ITEM: No sourceItem in context");
            return false;
        }

        Player player = context.getPlayer();

        // Check main hand
        org.bukkit.inventory.ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && sourceItem.isSimilar(mainHand)) {
            com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] HOLDING_SIGIL_ITEM: Holding in main hand");
            return true;
        }

        // Check off hand
        org.bukkit.inventory.ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && sourceItem.isSimilar(offHand)) {
            com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] HOLDING_SIGIL_ITEM: Holding in off hand");
            return true;
        }

        com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] HOLDING_SIGIL_ITEM: Not holding sigil item");
        return false;
    }

    /**
     * DURABILITY_PERCENT:<20 - Item durability is less than 20%
     * DURABILITY_PERCENT:>80 - Item durability is greater than 80%
     * DURABILITY_PERCENT:<=10 - Item durability is 10% or less (nearly broken)
     *
     * Checks the durability of the item that has this sigil socketed.
     * 100% = full durability, 0% = about to break
     */
    private boolean checkDurabilityPercent(EffectContext context, String[] parts) {
        if (parts.length < 2) return true;

        // Get the source item from metadata (the item with the sigil)
        org.bukkit.inventory.ItemStack sourceItem = context.getMetadata("sourceItem", null);
        if (sourceItem == null) {
            com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] DURABILITY_PERCENT: No sourceItem in context");
            return false;
        }

        // Check if item has durability
        if (!(sourceItem.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable)) {
            com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] DURABILITY_PERCENT: Item is not damageable");
            return true; // Non-damageable items always pass
        }

        // Calculate durability percentage
        // Damage = how much is lost, MaxDurability = total
        // Durability% = (MaxDurability - Damage) / MaxDurability * 100
        int maxDurability = sourceItem.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return true; // Items with no durability always pass
        }

        int currentDamage = damageable.getDamage();
        double durabilityPercent = ((double)(maxDurability - currentDamage) / maxDurability) * 100.0;

        com.miracle.arcanesigils.utils.LogHelper.debug(
            "[Conditions] DURABILITY_PERCENT: %.1f%% (damage=%d, max=%d)",
            durabilityPercent, currentDamage, maxDurability);

        String condition = parts[1];
        return evaluateComparison(durabilityPercent, condition);
    }

    // ===== ADDITIONAL COMBAT CONDITION IMPLEMENTATIONS =====

    /**
     * HAS_TARGET - Player has a valid target selected in the ability UI.
     * Used for ability/bind targeting (not combat events).
     *
     * Checks the TargetGlowManager for the player's currently selected target.
     */
    private boolean checkHasTarget(EffectContext context, String[] parts) {
        // Check if player has a target in the bind system's TargetGlowManager
        com.miracle.arcanesigils.binds.TargetGlowManager glowManager =
            com.miracle.arcanesigils.ArmorSetsPlugin.getInstance().getTargetGlowManager();

        if (glowManager != null) {
            org.bukkit.entity.LivingEntity target = glowManager.getTarget(context.getPlayer());
            if (target != null && !target.isDead()) {
                return true;
            }
        }

        // Fallback: also check context victim (for combat events)
        return context.getVictim() != null && !context.getVictim().isDead();
    }

    /**
     * CRITICAL_HIT - Attack was a critical hit
     * Critical hit = player is falling (velocity Y < 0) and not on ground
     */
    private boolean checkCriticalHit(EffectContext context) {
        Player player = context.getPlayer();
        // Critical hit requirements: falling, not on ground, not in water, not on ladder, not blind
        return !player.isOnGround()
            && player.getVelocity().getY() < 0
            && !player.isInWater()
            && !player.isClimbing()
            && player.getPotionEffect(PotionEffectType.BLINDNESS) == null;
    }

    /**
     * VICTIM_IS_UNDEAD - Target is an undead mob
     */
    private boolean checkVictimIsUndead(EffectContext context) {
        if (context.getVictim() == null) return false;

        String victimType = context.getVictim().getType().name();
        return switch (victimType) {
            case "ZOMBIE", "ZOMBIE_VILLAGER", "HUSK", "DROWNED", "ZOMBIE_HORSE",
                 "SKELETON", "STRAY", "WITHER_SKELETON", "SKELETON_HORSE",
                 "PHANTOM", "WITHER", "ZOGLIN", "ZOMBIFIED_PIGLIN" -> true;
            default -> false;
        };
    }

    /**
     * ON_FIRE - Player or victim is burning
     */
    private boolean checkOnFire(EffectContext context, String[] parts) {
        // If @Victim is specified, check victim instead
        if (parts.length >= 2 && parts[1].equalsIgnoreCase("VICTIM")) {
            return context.getVictim() != null && context.getVictim().getFireTicks() > 0;
        }
        return context.getPlayer().getFireTicks() > 0;
    }

    // ===== ADDITIONAL ENVIRONMENTAL CONDITION IMPLEMENTATIONS =====

    /**
     * DIMENSION:NETHER - Player is in the Nether
     */
    private boolean checkDimension(Player player, String[] parts) {
        if (parts.length < 2) return true;

        String dimension = parts[1].toUpperCase();
        String worldEnv = player.getWorld().getEnvironment().name();

        return switch (dimension) {
            case "OVERWORLD", "NORMAL" -> worldEnv.equals("NORMAL");
            case "NETHER", "THE_NETHER" -> worldEnv.equals("NETHER");
            case "END", "THE_END" -> worldEnv.equals("THE_END");
            default -> false;
        };
    }

    /**
     * Y_LEVEL:<64 - Player is below Y 64
     */
    private boolean checkYLevel(Player player, String[] parts) {
        if (parts.length < 2) return true;

        int yLevel = player.getLocation().getBlockY();
        String condition = parts[1];
        return evaluateComparison(yLevel, condition);
    }

    // ===== ADDITIONAL META CONDITION IMPLEMENTATIONS =====

    /**
     * EXPERIENCE_LEVEL:>30 - Player has more than 30 XP levels
     */
    private boolean checkExperienceLevel(Player player, String[] parts) {
        if (parts.length < 2) return true;

        int xpLevel = player.getLevel();
        String condition = parts[1];
        return evaluateComparison(xpLevel, condition);
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
