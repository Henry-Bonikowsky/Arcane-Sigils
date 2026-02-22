package com.miracle.arcanesigils.events;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.attribute.Attribute;
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

    private final com.miracle.arcanesigils.ArmorSetsPlugin plugin;

    public ConditionManager(com.miracle.arcanesigils.ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Check if all conditions are met using AND logic.
     * Returns false if any condition fails.
     */
    public boolean checkConditions(List<String> conditions, EffectContext context) {
        if (conditions == null || conditions.isEmpty()) {
            return true; // No conditions = always pass
        }

        com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] Checking %d conditions: %s",
            conditions.size(), conditions);

        // All conditions must pass (AND logic)
        for (String condition : conditions) {
            boolean result = evaluateCondition(condition, context);
            com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions]   %s = %s", condition, result);
            if (!result) {
                com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] Failed condition: %s", condition);
                return false; // Any failed condition blocks execution
            }
        }
        com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] All conditions passed");
        return true; // All conditions passed
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
                    // Simple reliable IN_AIR check
                    Player p = context.getPlayer();
                    boolean onGround = p.isOnGround();
                    double yVel = p.getVelocity().getY();

                    // Player is in air if not on ground OR has significant Y velocity
                    boolean inAir = !onGround || Math.abs(yVel) > 0.1;
                    yield inAir;
                }
                case "SIGIL_ON_COOLDOWN" -> {
                    // Check if a specific sigil is on cooldown
                    // Format: SIGIL_ON_COOLDOWN <sigil_id>
                    if (parts.length < 2) {
                        yield false; // No sigil ID provided - edge case, return false
                    }

                    String sigilId = parts[1].toLowerCase();
                    Player player = context.getPlayer();

                    // Build cooldown key for the sigil's main flow
                    // Format: sigil_<id>_<signal>_flow
                    String signalKey = context.getSignalType().getConfigKey();
                    String cooldownKey = "sigil_" + sigilId + "_" + signalKey + "_flow";

                    // Return true if sigil IS on cooldown
                    // Returns false if:
                    // - Sigil not equipped (cooldown doesn't exist)
                    // - Sigil not on cooldown
                    // - Cooldown expired
                    boolean onCooldown = plugin.getCooldownManager().isOnCooldown(player, cooldownKey);
                    yield onCooldown;
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
                case "HAS_PLAYER_VARIABLE" -> {
                    Player p = context.getPlayer();
                    if (p == null) yield false;
                    String varName = parts.length > 1 ? parts[1] : null;
                    if (varName == null || varName.isEmpty()) yield false;
                    com.miracle.arcanesigils.ArmorSetsPlugin pluginInstance = com.miracle.arcanesigils.ArmorSetsPlugin.getInstance();
                    yield pluginInstance.getPlayerVariableManager().hasVariable(p.getUniqueId(), varName);
                }

                // ===== EQUIPMENT CONDITIONS =====
                case "MAIN_HAND" -> checkMainHand(context.getPlayer(), parts);
                case "HAS_ENCHANT" -> checkHasEnchant(context.getPlayer(), parts);
                case "HOLDING_SIGIL_ITEM" -> checkHoldingSigilItem(context);
                case "DURABILITY_PERCENT" -> checkDurabilityPercent(context, parts);

                // ===== SET BONUS CONDITIONS =====
                case "HAS_SET_BONUS" -> {
                    // Format: HAS_SET_BONUS:ancient_set:2
                    if (parts.length < 2) yield false;

                    String setName = parts[1];
                    int minTier = 1;
                    if (parts.length >= 3) {
                        try {
                            minTier = Integer.parseInt(parts[2]);
                        } catch (NumberFormatException e) {
                            minTier = 1;
                        }
                    }

                    var setBonusManager = plugin.getSetBonusManager();
                    if (setBonusManager == null) yield false;

                    int playerTier = setBonusManager.getSetBonusTier(context.getPlayer(), setName);
                    yield playerTier >= minTier;
                }
                case "IS_BLOCKING_SWORD" -> context.getPlayer() != null
                        && com.miracle.arcanesigils.listeners.SwordBlockListener.isBlocking(context.getPlayer());

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

                // ===== SIGNAL-SPECIFIC CONDITIONS =====
                case "IS_NEGATIVE_EFFECT" -> checkIsNegativeEffect(context);
                case "IS_NEGATIVE_MODIFIER" -> checkIsNegativeModifier(context);
                case "IS_POTION_DAMAGE" -> checkIsPotionDamage(context);

                // ===== FACTION CONDITIONS =====
                case "IS_ALLY" -> checkFactionRelation(context, "ALLY");
                case "IS_ENEMY" -> checkFactionRelation(context, "ENEMY");
                case "IS_TRUCE" -> checkFactionRelation(context, "TRUCE");
                case "IS_NEUTRAL" -> checkFactionRelation(context, "NEUTRAL");
                case "IN_OWN_TERRITORY" -> com.miracle.arcanesigils.hooks.FactionsHook.isInOwnTerritory(context.getPlayer());
                case "IN_ENEMY_TERRITORY" -> com.miracle.arcanesigils.hooks.FactionsHook.isInEnemyTerritory(context.getPlayer());
                case "IN_WARZONE" -> com.miracle.arcanesigils.hooks.FactionsHook.isInWarzone(context.getPlayer());
                case "IN_SAFEZONE" -> com.miracle.arcanesigils.hooks.FactionsHook.isInSafezone(context.getPlayer());
                case "HAS_FACTION" -> com.miracle.arcanesigils.hooks.FactionsHook.hasFaction(context.getPlayer());

                // Default: unknown condition FAILS (safety)
                default -> {
                    com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] UNKNOWN CONDITION TYPE: %s - FAILING", type);
                    yield false;
                }
            };
        } catch (Exception e) {
            com.miracle.arcanesigils.utils.LogHelper.severe("[Conditions] ERROR evaluating condition: " + condition, e);
            return false; // Fail on error - don't allow execution
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
        boolean result = evaluateComparison(percentHealth, condition);

        com.miracle.arcanesigils.utils.LogHelper.debug("[Conditions] HEALTH_PERCENT check: player=%s, current=%.1f, max=%.1f, percent=%.1f%%, condition=%s, result=%s",
            player.getName(), currentHealth, maxHealth, percentHealth, condition, result);

        return result;
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
     * BLOCK_BELOW:SAND,RED_SAND - Standing on sand or red sand (comma-separated)
     */
    private boolean checkBlockBelow(Player player, String[] parts) {
        if (parts.length < 2) return true;

        String blockNames = parts[1].toUpperCase();
        org.bukkit.block.Block blockBelow = player.getLocation().clone().subtract(0, 1, 0).getBlock();
        org.bukkit.Material blockType = blockBelow.getType();

        // Support comma-separated materials (e.g., "SAND,RED_SAND")
        String[] materials = blockNames.split(",");
        
        for (String materialName : materials) {
            try {
                org.bukkit.Material material = org.bukkit.Material.valueOf(materialName.trim());
                if (blockType == material) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Invalid material name, skip it
            }
        }
        
        return false;
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

        com.miracle.arcanesigils.combat.ModifierRegistry registry =
            com.miracle.arcanesigils.ArmorSetsPlugin.getInstance().getModifierRegistry();

        if (registry == null) {
            com.miracle.arcanesigils.utils.LogHelper.warning("[HAS_MARK] ModifierRegistry is null!");
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
            com.miracle.arcanesigils.utils.LogHelper.debug("[HAS_MARK] No valid target entity (null or dead)");
            return false;
        }

        boolean hasMark = registry.hasMark(targetEntity, markName);

        com.miracle.arcanesigils.utils.LogHelper.debug(
            String.format("[HAS_MARK] Checking %s for mark '%s': %s",
                targetEntity.getName(), markName, hasMark ? "YES" : "NO"));



        return hasMark;
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
        int maxDurability = damageable.hasMaxDamage()
            ? damageable.getMaxDamage()
            : sourceItem.getType().getMaxDurability();
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

    // ===== SIGNAL-SPECIFIC CONDITION IMPLEMENTATIONS =====

    /**
     * IS_NEGATIVE_EFFECT - Check if the potion effect being applied is harmful.
     * Only works in POTION_EFFECT_APPLY signal flows.
     * 
     * Negative effects include: Poison, Wither, Slowness, Weakness, Instant Damage,
     * Hunger, Nausea, Blindness, Bad Omen, Unluck, Darkness, Levitation (harmful), Glowing (PvP debuff)
     */
    private boolean checkIsNegativeEffect(EffectContext context) {
        PotionEffectType effectType = context.getCurrentPotionEffect();
        
        if (effectType == null) {
            com.miracle.arcanesigils.utils.LogHelper.debug(
                "[Conditions] IS_NEGATIVE_EFFECT: No effect in context - returning false");
            return false;
        }
        
        // List of harmful/negative potion effects
        boolean isNegative = switch (effectType.getKey().getKey().toUpperCase()) {
            case "POISON", "WITHER", "SLOWNESS", "SLOW", "SLOW_DIGGING", "MINING_FATIGUE",
                 "WEAKNESS", "INSTANT_DAMAGE", "HARM", "HUNGER", "NAUSEA", "CONFUSION",
                 "BLINDNESS", "BAD_OMEN", "UNLUCK", "DARKNESS", "LEVITATION", "GLOWING" -> true;
            default -> false;
        };
        
        com.miracle.arcanesigils.utils.LogHelper.debug(
            "[Conditions] IS_NEGATIVE_EFFECT: effect=%s, isNegative=%s",
            effectType.getKey().getKey(), isNegative);
        
        return isNegative;
    }

    /**
     * IS_NEGATIVE_MODIFIER - Check if the attribute modifier being applied is harmful.
     * Only works in ATTRIBUTE_MODIFY signal flows.
     * 
     * Checks if the modifier value is negative (reduces a stat).
     * Most attributes use negative values for debuffs, except for attributes where
     * higher values are worse (like knockback resistance where negative = less resistance = worse).
     */
    private boolean checkIsNegativeModifier(EffectContext context) {
        Attribute attribute = context.getCurrentAttribute();
        double modifierValue = context.getCurrentModifierValue();
        
        if (attribute == null) {
            com.miracle.arcanesigils.utils.LogHelper.debug(
                "[Conditions] IS_NEGATIVE_MODIFIER: No attribute in context - returning false");
            return false;
        }
        
        // For most attributes, negative values are harmful (reduce the stat)
        // Exceptions could be added here if needed (e.g., knockback resistance)
        boolean isNegative = modifierValue < 0;
        
        com.miracle.arcanesigils.utils.LogHelper.debug(
            "[Conditions] IS_NEGATIVE_MODIFIER: attribute=%s, value=%.2f, isNegative=%s",
            attribute.getKey().getKey(), modifierValue, isNegative);
        
        return isNegative;
    }

    /**
     * IS_POTION_DAMAGE - Check if damage is from poison or wither effect.
     * Only works in DEFENSE signal flows.
     *
     * Checks the damage cause to see if it's POISON or WITHER DOT.
     */
    private boolean checkIsPotionDamage(EffectContext context) {
        if (context.getBukkitEvent() == null || !(context.getBukkitEvent() instanceof org.bukkit.event.entity.EntityDamageEvent)) {
            com.miracle.arcanesigils.utils.LogHelper.debug(
                "[Conditions] IS_POTION_DAMAGE: Not a damage event - returning false");
            return false;
        }

        org.bukkit.event.entity.EntityDamageEvent damageEvent = (org.bukkit.event.entity.EntityDamageEvent) context.getBukkitEvent();
        org.bukkit.event.entity.EntityDamageEvent.DamageCause cause = damageEvent.getCause();

        boolean isPotionDamage = cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.POISON ||
                                 cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.WITHER;

        com.miracle.arcanesigils.utils.LogHelper.debug(
            "[AncientCrown] IS_POTION_DAMAGE check: cause=%s, isPotionDamage=%s",
            cause, isPotionDamage);

        com.miracle.arcanesigils.utils.LogHelper.debug(
            "[Conditions] IS_POTION_DAMAGE: cause=%s, isPotionDamage=%s",
            cause, isPotionDamage);

        return isPotionDamage;
    }

    // ===== FACTION CONDITION IMPLEMENTATIONS =====

    /**
     * Check faction relation between player and victim/target.
     * Returns false if no target, target isn't a player, or Factions unavailable.
     */
    private boolean checkFactionRelation(EffectContext context, String expectedRelation) {
        if (!com.miracle.arcanesigils.hooks.FactionsHook.isAvailable()) return false;

        // Get target: victim first, then ability UI target
        org.bukkit.entity.LivingEntity targetEntity = context.getVictim();
        if (targetEntity == null) {
            com.miracle.arcanesigils.binds.TargetGlowManager glowManager =
                com.miracle.arcanesigils.ArmorSetsPlugin.getInstance().getTargetGlowManager();
            if (glowManager != null) {
                targetEntity = glowManager.getTarget(context.getPlayer());
            }
        }

        if (!(targetEntity instanceof Player target)) return false;

        String relation = com.miracle.arcanesigils.hooks.FactionsHook.getRelation(context.getPlayer(), target);
        if (relation == null) return false;

        return switch (expectedRelation) {
            case "ALLY" -> "MEMBER".equals(relation) || "ALLY".equals(relation);
            case "ENEMY" -> "ENEMY".equals(relation);
            case "TRUCE" -> "TRUCE".equals(relation);
            case "NEUTRAL" -> "NEUTRAL".equals(relation);
            default -> false;
        };
    }

}
