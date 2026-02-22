package com.miracle.arcanesigils.events;

import org.bukkit.Material;

/**
 * All available condition types with their metadata.
 */
public enum ConditionType {
    // ===== HEALTH CONDITIONS =====
    HEALTH_PERCENT(
        ConditionCategory.HEALTH,
        Material.HEART_OF_THE_SEA,
        "Health Percent",
        "Check player health percentage",
        "HEALTH_PERCENT:<50",
        "Health below 50%",
        true
    ),
    HEALTH(
        ConditionCategory.HEALTH,
        Material.GOLDEN_APPLE,
        "Health (Raw HP)",
        "Check player's raw health points",
        "HEALTH:<10",
        "Health below 10 HP",
        true
    ),
    VICTIM_HEALTH_PERCENT(
        ConditionCategory.HEALTH,
        Material.WITHER_ROSE,
        "Victim Health Percent",
        "Check victim health percentage",
        "VICTIM_HEALTH_PERCENT:<30",
        "Victim health below 30%",
        true
    ),

    // ===== POTION CONDITIONS =====
    HAS_POTION(
        ConditionCategory.POTION,
        Material.SPLASH_POTION,
        "Has Potion",
        "Check if player has a potion effect",
        "HAS_POTION:STRENGTH",
        "Has Strength effect",
        true
    ),
    NO_POTION(
        ConditionCategory.POTION,
        Material.MILK_BUCKET,
        "No Potion",
        "Check if player lacks a potion effect",
        "NO_POTION:SLOWNESS",
        "No Slowness effect",
        true
    ),

    // ===== ENVIRONMENTAL CONDITIONS =====
    BIOME(
        ConditionCategory.ENVIRONMENTAL,
        Material.OAK_SAPLING,
        "Biome Check",
        "Check player's current biome",
        "BIOME:DESERT",
        "In a Desert biome",
        true
    ),
    BLOCK_BELOW(
        ConditionCategory.ENVIRONMENTAL,
        Material.STONE,
        "Block Below",
        "Check block beneath player",
        "BLOCK_BELOW:NETHERITE_BLOCK",
        "Standing on netherite",
        true
    ),
    LIGHT_LEVEL(
        ConditionCategory.ENVIRONMENTAL,
        Material.TORCH,
        "Light Level",
        "Check light level at location",
        "LIGHT_LEVEL:<7",
        "Dark area (light < 7)",
        true
    ),
    IN_WATER(
        ConditionCategory.ENVIRONMENTAL,
        Material.WATER_BUCKET,
        "In Water",
        "Check if player is in water",
        "IN_WATER",
        "Player is swimming",
        false
    ),
    ON_GROUND(
        ConditionCategory.ENVIRONMENTAL,
        Material.DIRT,
        "On Ground",
        "Check if player is on ground",
        "ON_GROUND",
        "Player is grounded",
        false
    ),
    IN_AIR(
        ConditionCategory.ENVIRONMENTAL,
        Material.FEATHER,
        "In Air",
        "Check if player is in the air (not on ground)",
        "IN_AIR",
        "Player is airborne",
        false
    ),
    HUNGER(
        ConditionCategory.HEALTH,
        Material.COOKED_BEEF,
        "Hunger Level",
        "Check player's hunger/food level",
        "HUNGER:<10",
        "Hunger below 10",
        true
    ),
    WEATHER(
        ConditionCategory.ENVIRONMENTAL,
        Material.SNOWBALL,
        "Weather Check",
        "Check current weather",
        "WEATHER:RAINING",
        "It's raining",
        true
    ),
    TIME(
        ConditionCategory.ENVIRONMENTAL,
        Material.CLOCK,
        "Time of Day",
        "Check time of day",
        "TIME:NIGHT",
        "During nighttime",
        true
    ),

    // ===== COMBAT CONDITIONS =====
    HAS_VICTIM(
        ConditionCategory.COMBAT,
        Material.SKELETON_SKULL,
        "Has Victim",
        "Check if there is a victim/target",
        "HAS_VICTIM",
        "Requires a target",
        false
    ),
    VICTIM_IS_PLAYER(
        ConditionCategory.COMBAT,
        Material.PLAYER_HEAD,
        "Victim is Player",
        "Check if victim is a player",
        "VICTIM_IS_PLAYER",
        "Target is a player",
        false
    ),
    VICTIM_IS_HOSTILE(
        ConditionCategory.COMBAT,
        Material.ZOMBIE_HEAD,
        "Victim is Hostile",
        "Check if victim is a hostile mob",
        "VICTIM_IS_HOSTILE",
        "Target is hostile",
        false
    ),
    HAS_TARGET(
        ConditionCategory.COMBAT,
        Material.ENDER_EYE,
        "Has Target (Look)",
        "Check if looking at an entity",
        "HAS_TARGET:10",
        "Must be looking at target",
        true
    ),

    // ===== META CONDITIONS =====
    SIGNAL(
        ConditionCategory.META,
        Material.REDSTONE_TORCH,
        "Signal Type",
        "Check signal event type",
        "SIGNAL:ATTACK",
        "Only on ATTACK signal",
        true
    ),
    WEARING_FULL_SET(
        ConditionCategory.META,
        Material.NETHERITE_CHESTPLATE,
        "Wearing Full Set",
        "Check if wearing full armor set",
        "WEARING_FULL_SET:arcanist_t1",
        "Wearing arcanist set",
        true
    ),

    // ===== PLAYER STATE CONDITIONS =====
    SNEAKING(
        ConditionCategory.PLAYER_STATE,
        Material.LEATHER_BOOTS,
        "Sneaking",
        "Check if player is sneaking",
        "SNEAKING",
        "Player is crouching",
        false
    ),
    SPRINTING(
        ConditionCategory.PLAYER_STATE,
        Material.RABBIT_FOOT,
        "Sprinting",
        "Check if player is sprinting",
        "SPRINTING",
        "Player is running",
        false
    ),
    FLYING(
        ConditionCategory.PLAYER_STATE,
        Material.ELYTRA,
        "Flying",
        "Check if player is flying or gliding",
        "FLYING",
        "Player is in flight",
        false
    ),
    SWIMMING(
        ConditionCategory.PLAYER_STATE,
        Material.COD,
        "Swimming",
        "Check if player is swimming",
        "SWIMMING",
        "Player is swimming",
        false
    ),
    HAS_PLAYER_VARIABLE(
        ConditionCategory.PLAYER_STATE,
        Material.WRITABLE_BOOK,
        "Has Player Variable",
        "Check if player has a variable set (ability state, etc.)",
        "HAS_PLAYER_VARIABLE:quicksand_active",
        "Player has 'quicksand_active' variable set",
        true
    ),

    // ===== EQUIPMENT CONDITIONS =====
    MAIN_HAND(
        ConditionCategory.EQUIPMENT,
        Material.DIAMOND_SWORD,
        "Main Hand Item",
        "Check item in main hand",
        "MAIN_HAND:DIAMOND_SWORD",
        "Holding a diamond sword",
        true
    ),
    HAS_ENCHANT(
        ConditionCategory.EQUIPMENT,
        Material.ENCHANTED_BOOK,
        "Has Enchantment",
        "Check if held item has enchantment",
        "HAS_ENCHANT:SHARPNESS",
        "Weapon has Sharpness",
        true
    ),
    HOLDING_SIGIL_ITEM(
        ConditionCategory.EQUIPMENT,
        Material.ECHO_SHARD,
        "Holding Sigil Item",
        "Check if player is holding the item with this sigil socketed",
        "HOLDING_SIGIL_ITEM",
        "Must be holding the socketed item",
        false
    ),
    DURABILITY_PERCENT(
        ConditionCategory.EQUIPMENT,
        Material.ANVIL,
        "Durability Percent",
        "Check item durability percentage",
        "DURABILITY_PERCENT:<20",
        "Durability below 20%",
        true
    ),

    // ===== SET BONUS CONDITIONS =====
    HAS_SET_BONUS(
        ConditionCategory.SET,
        Material.DIAMOND_CHESTPLATE,
        "Set Bonus Active",
        "Check if player has active set bonus at minimum tier",
        "HAS_SET_BONUS:ancient_set:2",
        "Player has ancient_set tier 2+",
        false
    ),
    IS_BLOCKING_SWORD(
        ConditionCategory.COMBAT,
        Material.IRON_SWORD,
        "Blocking with Sword",
        "Check if player is blocking with a sword (1.8 style)",
        "IS_BLOCKING_SWORD",
        "Player is blocking with sword",
        false
    ),

    // ===== ADDITIONAL COMBAT CONDITIONS =====
    HAS_MARK(
        ConditionCategory.COMBAT,
        Material.PAPER,
        "Has Mark",
        "Check if target has a mark applied",
        "HAS_MARK:PHARAOH_MARK",
        "Target has Pharaoh's Mark",
        true
    ),
    CRITICAL_HIT(
        ConditionCategory.COMBAT,
        Material.GOLDEN_SWORD,
        "Critical Hit",
        "Check if attack was a critical hit",
        "CRITICAL_HIT",
        "Attack is a critical hit",
        false
    ),
    VICTIM_IS_UNDEAD(
        ConditionCategory.COMBAT,
        Material.ROTTEN_FLESH,
        "Victim is Undead",
        "Check if target is an undead mob",
        "VICTIM_IS_UNDEAD",
        "Target is undead",
        false
    ),
    ON_FIRE(
        ConditionCategory.COMBAT,
        Material.BLAZE_POWDER,
        "On Fire",
        "Check if player or victim is burning",
        "ON_FIRE",
        "Entity is burning",
        false
    ),

    // ===== ADDITIONAL ENVIRONMENTAL CONDITIONS =====
    DIMENSION(
        ConditionCategory.ENVIRONMENTAL,
        Material.END_PORTAL_FRAME,
        "Dimension",
        "Check current dimension",
        "DIMENSION:NETHER",
        "In the Nether",
        true
    ),
    Y_LEVEL(
        ConditionCategory.ENVIRONMENTAL,
        Material.DIAMOND_ORE,
        "Y Level",
        "Check player's Y coordinate",
        "Y_LEVEL:<64",
        "Below Y level 64",
        true
    ),

    // ===== ADDITIONAL META CONDITIONS =====
    EXPERIENCE_LEVEL(
        ConditionCategory.META,
        Material.EXPERIENCE_BOTTLE,
        "Experience Level",
        "Check player's XP level",
        "EXPERIENCE_LEVEL:>30",
        "XP level above 30",
        true
    ),

    // ===== SIGNAL-SPECIFIC CONDITIONS =====
    IS_NEGATIVE_EFFECT(
        ConditionCategory.SIGNAL_SPECIFIC,
        Material.SPLASH_POTION,
        "Is Negative Effect",
        "Check if potion effect is harmful (POTION_EFFECT_APPLY signal only)",
        "IS_NEGATIVE_EFFECT",
        "Effect is negative (Poison, Slowness, Weakness, etc.)",
        false
    ),
    IS_NEGATIVE_MODIFIER(
        ConditionCategory.SIGNAL_SPECIFIC,
        Material.IRON_CHESTPLATE,
        "Is Negative Modifier",
        "Check if attribute modifier is harmful (ATTRIBUTE_MODIFY signal only)",
        "IS_NEGATIVE_MODIFIER",
        "Modifier reduces a stat (negative speed, damage, etc.)",
        false
    ),
    // ===== FACTION CONDITIONS =====
    IS_ALLY(
        ConditionCategory.FACTION,
        Material.LIME_BANNER,
        "Is Ally",
        "Check if target is a faction ally or member",
        "IS_ALLY",
        "Target is in your faction or allied",
        false
    ),
    IS_ENEMY(
        ConditionCategory.FACTION,
        Material.RED_BANNER,
        "Is Enemy",
        "Check if target is a faction enemy",
        "IS_ENEMY",
        "Target is an enemy faction member",
        false
    ),
    IS_TRUCE(
        ConditionCategory.FACTION,
        Material.YELLOW_BANNER,
        "Is Truce",
        "Check if target is in a truce faction",
        "IS_TRUCE",
        "Target is in a truce faction",
        false
    ),
    IS_NEUTRAL(
        ConditionCategory.FACTION,
        Material.WHITE_BANNER,
        "Is Neutral",
        "Check if target is faction-neutral",
        "IS_NEUTRAL",
        "Target is faction-neutral",
        false
    ),
    IN_OWN_TERRITORY(
        ConditionCategory.FACTION,
        Material.GREEN_BANNER,
        "In Own Territory",
        "Check if player is in their faction's claimed land",
        "IN_OWN_TERRITORY",
        "Player is on home turf",
        false
    ),
    IN_ENEMY_TERRITORY(
        ConditionCategory.FACTION,
        Material.RED_WOOL,
        "In Enemy Territory",
        "Check if player is in enemy faction's claimed land",
        "IN_ENEMY_TERRITORY",
        "Player is raiding enemy land",
        false
    ),
    IN_WARZONE(
        ConditionCategory.FACTION,
        Material.TNT,
        "In Warzone",
        "Check if player is in a warzone chunk",
        "IN_WARZONE",
        "Player is in warzone",
        false
    ),
    IN_SAFEZONE(
        ConditionCategory.FACTION,
        Material.GOLDEN_APPLE,
        "In Safezone",
        "Check if player is in a safezone chunk",
        "IN_SAFEZONE",
        "Player is in safezone",
        false
    ),
    HAS_FACTION(
        ConditionCategory.FACTION,
        Material.IRON_SWORD,
        "Has Faction",
        "Check if player belongs to any faction",
        "HAS_FACTION",
        "Player is in a faction",
        false
    ),

    IS_POTION_DAMAGE(
        ConditionCategory.SIGNAL_SPECIFIC,
        Material.WITHER_ROSE,
        "Is Potion Damage",
        "Check if damage is from poison or wither effect (DEFENSE signal only)",
        "IS_POTION_DAMAGE",
        "Damage from poison/wither DOT",
        false
    );

    private final ConditionCategory category;
    private final Material icon;
    private final String displayName;
    private final String description;
    private final String exampleFormat;
    private final String exampleDescription;
    private final boolean hasParameters;
    private final String detailedDescription;
    private final String usageExample;
    private final String[] relatedConditions;
    private final String tips;

    ConditionType(ConditionCategory category, Material icon, String displayName,
                  String description, String exampleFormat, String exampleDescription,
                  boolean hasParameters) {
        this.category = category;
        this.icon = icon;
        this.displayName = displayName;
        this.description = description;
        this.exampleFormat = exampleFormat;
        this.exampleDescription = exampleDescription;
        this.hasParameters = hasParameters;

        // Generate detailed info based on type
        this.detailedDescription = generateDetailedDescription();
        this.usageExample = generateUsageExample();
        this.relatedConditions = generateRelatedConditions();
        this.tips = generateTips();
    }

    public ConditionCategory getCategory() {
        return category;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getExampleFormat() {
        return exampleFormat;
    }

    public String getExampleDescription() {
        return exampleDescription;
    }

    public boolean hasParameters() {
        return hasParameters;
    }

    public String getDetailedDescription() {
        return detailedDescription;
    }

    public String getUsageExample() {
        return usageExample;
    }

    public String[] getRelatedConditions() {
        return relatedConditions;
    }

    public String getTips() {
        return tips;
    }

    /**
     * Get the config key for this condition (uppercase enum name).
     */
    public String getConfigKey() {
        return this.name();
    }

    /**
     * Get all condition types for a specific category.
     */
    public static ConditionType[] getByCategory(ConditionCategory category) {
        return java.util.Arrays.stream(values())
            .filter(type -> type.category == category)
            .toArray(ConditionType[]::new);
    }

    // ===== DETAILED INFORMATION GENERATORS =====

    private String generateDetailedDescription() {
        return switch (this) {
            case HEALTH_PERCENT -> "Checks the player's current health as a percentage of maximum health. Supports comparison operators (<, >, <=, >=, =). Useful for glass cannon builds or last-stand mechanics.";
            case HEALTH -> "Checks the player's raw health points (HP). Supports comparison operators (<, >, <=, >=, =). Direct HP check without percentage calculation. Perfect for exact health thresholds.";
            case VICTIM_HEALTH_PERCENT -> "Checks the target's health percentage. Excellent for execute mechanics or finishing moves on low-health enemies.";
            case HAS_POTION -> "Verifies player has an active potion effect. Can optionally check amplifier level. Great for synergy-based builds.";
            case NO_POTION -> "Checks that player does NOT have a specific potion effect. Useful for preventing effect stacking or creating conditional buffs.";
            case BIOME -> "Restricts effects to specific biomes. Creates location-themed builds like desert warriors or ocean specialists.";
            case BLOCK_BELOW -> "Checks the block type directly beneath the player. Enables terrain-based bonuses like standing on netherite or water.";
            case LIGHT_LEVEL -> "Measures ambient light at player's location. Perfect for shadow assassins or daylight warriors.";
            case IN_WATER -> "Simple check if player is submerged in water. Essential for aquatic builds and underwater combat.";
            case ON_GROUND -> "Checks if player is standing on solid ground (not jumping/falling). Good for grounded combat styles.";
            case IN_AIR -> "Checks if player is NOT on ground (jumping, falling, or flying). Perfect for aerial combat or double-jump mechanics.";
            case HUNGER -> "Checks the player's food level (0-20). Supports comparison operators (<, >, <=, >=, =). Useful for hunger-based effects.";
            case WEATHER -> "Tests current weather conditions (RAINING, THUNDERING, CLEAR). Creates weather-reactive builds.";
            case TIME -> "Checks time of day (NIGHT, DAY, SUNSET, SUNRISE). Enables time-based powers like nocturnal hunters.";
            case HAS_VICTIM -> "Verifies a target exists for victim-targeted effects. Essential prerequisite for VICTIM conditions.";
            case VICTIM_IS_PLAYER -> "Checks if the target is a player (not a mob). Creates PvP-specific abilities.";
            case VICTIM_IS_HOSTILE -> "Verifies target is a hostile mob. Perfect for PvE-focused builds and mob hunting.";
            case HAS_TARGET -> "Checks if player is looking at an entity within range. For abilities/binds that need a target.";
            case SIGNAL -> "Restricts effect to a specific signal type. Allows multi-signal configurations with conditional activation.";
            case WEARING_FULL_SET -> "Checks if player is wearing a complete armor set. Enables set-synergy effects.";
            case SNEAKING -> "Checks if the player is currently sneaking/crouching. Great for stealth builds or alternative activation modes.";
            case SPRINTING -> "Checks if the player is currently sprinting. Perfect for mobility-based abilities or speed builds.";
            case FLYING -> "Checks if player is flying (creative) or gliding (elytra). Enables aerial combat mechanics.";
            case SWIMMING -> "Checks if player is in swimming animation. More specific than IN_WATER for aquatic builds.";
            case HAS_PLAYER_VARIABLE -> "Checks if player has a variable set using VariableNode with player scope. Perfect for tracking ability active states, cooldowns, or persistent flags across flows. Use with VariableNode (scope: player) to create duration-based state tracking.";
            case MAIN_HAND -> "Checks the item type in the player's main hand. Enables weapon-specific abilities.";
            case HAS_ENCHANT -> "Checks if the held item has a specific enchantment. Creates enchantment-synergy effects.";
            case HOLDING_SIGIL_ITEM -> "Checks if the player is currently holding (main or off hand) the item that has this sigil socketed. Useful for weapon-based sigils that only work when actively wielded.";
            case DURABILITY_PERCENT -> "Checks the durability percentage of the sigil's socketed item. 100% = full durability, 0% = about to break. Perfect for auto-repair effects or low-durability warnings.";
            case HAS_MARK -> "Checks if target has a mark. Checks victim first, then ability UI target. Use @Self to check yourself, @Target for UI target only, @Victim for combat only.";
            case CRITICAL_HIT -> "Checks if the attack was a critical hit (falling + attacking). Perfect for crit-focused builds.";
            case VICTIM_IS_UNDEAD -> "Checks if target is an undead mob (zombie, skeleton, etc). Ideal for holy/smite builds.";
            case ON_FIRE -> "Checks if the player or victim is currently burning. Fire-themed ability enabler.";
            case DIMENSION -> "Checks which dimension the player is in (OVERWORLD, NETHER, END). Dimension-specific abilities.";
            case Y_LEVEL -> "Checks the player's Y coordinate height. Mining bonuses or depth-based effects.";
            case EXPERIENCE_LEVEL -> "Checks the player's XP level. Enables level-gated abilities or XP cost mechanics.";
            case IS_NEGATIVE_EFFECT -> "Checks if the potion effect being applied is harmful. Only works in POTION_EFFECT_APPLY signal flows. Returns true for negative effects like Poison, Slowness, Weakness, Wither, etc. Used by Ancient Crown to selectively reduce incoming debuffs.";
            case IS_NEGATIVE_MODIFIER -> "Checks if the attribute modifier being applied is harmful (reduces a stat). Only works in ATTRIBUTE_MODIFY signal flows. Returns true for negative modifiers like movement speed reduction, damage reduction, etc. Used by Ancient Crown to selectively reduce incoming stat debuffs.";
            case IS_POTION_DAMAGE -> "Checks if damage is from poison or wither damage-over-time effect. Only works in DEFENSE signal flows. Returns true for POISON and WITHER damage causes. Used by Ancient Crown to reduce DOT damage.";
            case HAS_SET_BONUS -> "Checks if player has an active set bonus at minimum tier. Useful for abilities that synergize with set bonuses.";
            case IS_BLOCKING_SWORD -> "Checks if player is blocking with a sword (1.8 style combat). Perfect for defensive sword abilities.";
            case IS_ALLY -> "Checks if the target player is in the same faction or an allied faction. Requires Factions plugin. Always false if target isn't a player or Factions not loaded.";
            case IS_ENEMY -> "Checks if the target player is in an enemy faction. Requires Factions plugin. Always false if target isn't a player or Factions not loaded.";
            case IS_TRUCE -> "Checks if the target player is in a truce faction. Requires Factions plugin.";
            case IS_NEUTRAL -> "Checks if the target player is faction-neutral (no relation set). Requires Factions plugin.";
            case IN_OWN_TERRITORY -> "Checks if the player is standing in their own faction's claimed land. Perfect for homeland defense sigils.";
            case IN_ENEMY_TERRITORY -> "Checks if the player is in an enemy faction's claimed land. Ideal for raiding sigils.";
            case IN_WARZONE -> "Checks if the player is in a designated warzone chunk.";
            case IN_SAFEZONE -> "Checks if the player is in a safezone chunk.";
            case HAS_FACTION -> "Checks if the player belongs to any faction. Useful for gating faction-only abilities.";
        };
    }

    private String generateUsageExample() {
        return switch (this) {
            case HEALTH_PERCENT -> "Glass cannon DPS build: Massive damage below 50% health";
            case HEALTH -> "Last stand: HEALTH:<4 activates emergency heal. Tank build: HEALTH:>15 enables regen";
            case VICTIM_HEALTH_PERCENT -> "Execute: Deal 200% damage to targets below 30% health";
            case HAS_POTION -> "Synergy: Speed boost only while Strength is active";
            case NO_POTION -> "Pure build: Bonus damage only when no potion effects present";
            case BIOME -> "Desert warrior: Fire resistance and strength in desert biomes";
            case BLOCK_BELOW -> "Netherite mastery: Bonus stats when standing on netherite blocks";
            case LIGHT_LEVEL -> "Shadow assassin: Increased damage in darkness (light < 7)";
            case IN_WATER -> "Aquatic predator: Speed and strength while swimming";
            case ON_GROUND -> "Grounded fighter: Defense bonus only when on solid ground";
            case IN_AIR -> "Sky Stepper: Double jump by pressing sneak while in the air";
            case HUNGER -> "Meal Planning: Auto-feed when HUNGER:<20";
            case WEATHER -> "Storm caller: Lightning effects during thunderstorms";
            case TIME -> "Night hunter: Bonus damage and vision during night";
            case HAS_VICTIM -> "Combat-only: Abilities only work in active combat";
            case VICTIM_IS_PLAYER -> "PvP specialist: Extra damage against players only";
            case VICTIM_IS_HOSTILE -> "Mob slayer: Increased rewards when killing hostile mobs";
            case HAS_TARGET -> "Ability targeting: Only fire projectile if looking at an enemy";
            case SIGNAL -> "Versatile: Different effects on attack vs defense";
            case WEARING_FULL_SET -> "Set mastery: Ultimate ability only with full legendary set";
            case SNEAKING -> "Stealth strike: Bonus backstab damage while sneaking";
            case SPRINTING -> "Charge attack: Extra impact damage while sprinting into combat";
            case FLYING -> "Aerial dive: Massive AoE damage when attacking while gliding";
            case SWIMMING -> "Aquatic fury: Speed and strength while swimming in water";
            case HAS_PLAYER_VARIABLE -> "Quicksand activation: Only apply marks while 'quicksand_active' variable is set (6s duration)";
            case MAIN_HAND -> "Sword mastery: Bonus crit chance when holding any sword type";
            case HAS_ENCHANT -> "Enchanted warrior: Fire aspect weapons gain extra burn damage";
            case HOLDING_SIGIL_ITEM -> "Staff of Ra: Healing effect only works when holding the staff with the sigil";
            case DURABILITY_PERCENT -> "Emergency Repair: Auto-repair effect triggers when durability drops below 20%";
            case HAS_MARK -> "Pharaoh synergy: Extra damage and wither against marked targets";
            case CRITICAL_HIT -> "Precision striker: Critical hits deal triple damage instead of 1.5x";
            case VICTIM_IS_UNDEAD -> "Holy warrior: Smite damage against zombies and skeletons";
            case ON_FIRE -> "Flame dancer: While burning, gain fire resistance and damage boost";
            case DIMENSION -> "Nether walker: Fire immunity and strength while in the Nether";
            case Y_LEVEL -> "Deep miner: Fortune effect on ores below Y level 20";
            case EXPERIENCE_LEVEL -> "Veteran: Unlock ultimate ability at level 50+";
            case IS_NEGATIVE_EFFECT -> "Ancient Crown immunity: Reduce only negative potion effects by X%, let positive effects through";
            case IS_NEGATIVE_MODIFIER -> "Ancient Crown immunity: Reduce only negative attribute modifiers by X%, let positive buffs through";
            case IS_POTION_DAMAGE -> "Ancient Crown immunity: Reduce poison/wither damage by X%";
            case HAS_SET_BONUS -> "Set synergy: Extra effect when wearing 2+ Ancient set pieces at tier 3+";
            case IS_BLOCKING_SWORD -> "Divine shield: While blocking with sword, chance to grant invulnerability";
            case IS_ALLY -> "Healer build: Healing sigil only targets allies";
            case IS_ENEMY -> "Assassin: Extra damage only against enemies";
            case IS_TRUCE -> "Diplomat: Special interaction with truce factions";
            case IS_NEUTRAL -> "Mercenary: Effects that work on unaligned players";
            case IN_OWN_TERRITORY -> "Homeland Defender: Bonus damage on home claims";
            case IN_ENEMY_TERRITORY -> "Raider's Edge: Bonus effects when invading enemy land";
            case IN_WARZONE -> "Warzone Fighter: Combat buffs in warzone areas";
            case IN_SAFEZONE -> "Safe Haven: Passive regen while in safezone";
            case HAS_FACTION -> "Faction Warrior: Abilities only work if you're in a faction";
        };
    }

    private String[] generateRelatedConditions() {
        return switch (this) {
            case HEALTH_PERCENT -> new String[]{"HEALTH", "VICTIM_HEALTH_PERCENT"};
            case HEALTH -> new String[]{"HEALTH_PERCENT", "VICTIM_HEALTH_PERCENT"};
            case VICTIM_HEALTH_PERCENT -> new String[]{"HAS_VICTIM", "VICTIM_IS_HOSTILE"};
            case HAS_POTION -> new String[]{"NO_POTION", "TIME"};
            case NO_POTION -> new String[]{"HAS_POTION"};
            case BIOME -> new String[]{"WEATHER", "TIME", "BLOCK_BELOW"};
            case BLOCK_BELOW -> new String[]{"BIOME", "ON_GROUND"};
            case LIGHT_LEVEL -> new String[]{"TIME", "BIOME"};
            case IN_WATER -> new String[]{"BLOCK_BELOW", "BIOME"};
            case ON_GROUND -> new String[]{"BLOCK_BELOW", "IN_AIR"};
            case IN_AIR -> new String[]{"ON_GROUND"};
            case HUNGER -> new String[]{"HEALTH", "HEALTH_PERCENT"};
            case WEATHER -> new String[]{"TIME", "BIOME"};
            case TIME -> new String[]{"LIGHT_LEVEL", "WEATHER"};
            case HAS_VICTIM -> new String[]{"VICTIM_IS_PLAYER", "VICTIM_IS_HOSTILE", "VICTIM_HEALTH_PERCENT"};
            case VICTIM_IS_PLAYER -> new String[]{"HAS_VICTIM", "SIGNAL"};
            case VICTIM_IS_HOSTILE -> new String[]{"HAS_VICTIM", "BIOME"};
            case HAS_TARGET -> new String[]{"HAS_VICTIM", "SIGNAL"};
            case SIGNAL -> new String[]{"HAS_VICTIM"};
            case WEARING_FULL_SET -> new String[]{"HEALTH", "HAS_POTION"};
            case SNEAKING -> new String[]{"SPRINTING", "ON_GROUND"};
            case SPRINTING -> new String[]{"SNEAKING", "IN_AIR"};
            case FLYING -> new String[]{"IN_AIR", "DIMENSION"};
            case SWIMMING -> new String[]{"IN_WATER", "BIOME"};
            case HAS_PLAYER_VARIABLE -> new String[]{"SIGNAL", "WEARING_FULL_SET"};
            case MAIN_HAND -> new String[]{"HAS_ENCHANT", "SIGNAL"};
            case HAS_ENCHANT -> new String[]{"MAIN_HAND", "SIGNAL"};
            case HOLDING_SIGIL_ITEM -> new String[]{"MAIN_HAND", "SIGNAL"};
            case DURABILITY_PERCENT -> new String[]{"HOLDING_SIGIL_ITEM", "MAIN_HAND"};
            case HAS_MARK -> new String[]{"HAS_VICTIM", "VICTIM_IS_PLAYER", "HAS_POTION"};
            case CRITICAL_HIT -> new String[]{"IN_AIR", "HAS_VICTIM"};
            case VICTIM_IS_UNDEAD -> new String[]{"HAS_VICTIM", "VICTIM_IS_HOSTILE"};
            case ON_FIRE -> new String[]{"HAS_VICTIM", "WEATHER"};
            case DIMENSION -> new String[]{"BIOME", "Y_LEVEL"};
            case Y_LEVEL -> new String[]{"DIMENSION", "LIGHT_LEVEL"};
            case EXPERIENCE_LEVEL -> new String[]{"HEALTH", "SIGNAL"};
            case IS_NEGATIVE_EFFECT -> new String[]{"SIGNAL"};
            case IS_NEGATIVE_MODIFIER -> new String[]{"SIGNAL"};
            case IS_POTION_DAMAGE -> new String[]{"SIGNAL"};
            case HAS_SET_BONUS -> new String[]{"HEALTH_PERCENT", "HAS_POTION"};
            case IS_BLOCKING_SWORD -> new String[]{"MAIN_HAND", "SIGNAL"};
            case IS_ALLY -> new String[]{"IS_ENEMY", "HAS_FACTION"};
            case IS_ENEMY -> new String[]{"IS_ALLY", "HAS_FACTION"};
            case IS_TRUCE -> new String[]{"IS_NEUTRAL", "HAS_FACTION"};
            case IS_NEUTRAL -> new String[]{"IS_TRUCE", "IS_ENEMY"};
            case IN_OWN_TERRITORY -> new String[]{"IN_ENEMY_TERRITORY", "HAS_FACTION"};
            case IN_ENEMY_TERRITORY -> new String[]{"IN_OWN_TERRITORY", "HAS_FACTION"};
            case IN_WARZONE -> new String[]{"IN_SAFEZONE", "IN_ENEMY_TERRITORY"};
            case IN_SAFEZONE -> new String[]{"IN_WARZONE", "IN_OWN_TERRITORY"};
            case HAS_FACTION -> new String[]{"IS_ALLY", "IS_ENEMY"};
        };
    }

    private String generateTips() {
        return switch (this) {
            case HEALTH_PERCENT -> "Use with caution - percentage scales with max health changes";
            case HEALTH -> "Use < for low health activation, > for high health. Consider cooldowns for spam prevention";
            case VICTIM_HEALTH_PERCENT -> "Always pair with HAS_VICTIM to avoid null errors";
            case HAS_POTION -> "Can check amplifier level with third parameter (e.g., STRENGTH:>2)";
            case NO_POTION -> "Useful for 'pure' builds that avoid external buffs";
            case BIOME -> "Be aware of biome transitions - effect may flicker";
            case BLOCK_BELOW -> "Checks one block down - player must be grounded";
            case LIGHT_LEVEL -> "Includes both sky and block light - torches count!";
            case IN_WATER -> "Only checks if submerged - rain doesn't count";
            case ON_GROUND -> "False while jumping, falling, or swimming";
            case IN_AIR -> "True while jumping, falling, flying - opposite of ON_GROUND";
            case HUNGER -> "Food level ranges 0-20. Use < for hungry, > for well-fed checks";
            case WEATHER -> "Weather is world-specific - different per dimension";
            case TIME -> "Time ranges: DAY(0-12000), NIGHT(13000-24000)";
            case HAS_VICTIM -> "Required for any VICTIM-based conditions";
            case VICTIM_IS_PLAYER -> "Only works on combat signals with a target";
            case VICTIM_IS_HOSTILE -> "Mob list is predefined - check ConditionManager for details";
            case HAS_TARGET -> "HAS_TARGET:10 checks 10 blocks. Use for ability binds, not combat";
            case SIGNAL -> "Allows same signal config to behave differently per signal type";
            case WEARING_FULL_SET -> "Requires exact set ID match - case sensitive";
            case SNEAKING -> "Cannot be sneaking and sprinting simultaneously";
            case SPRINTING -> "Sprinting requires movement - standing still returns false";
            case FLYING -> "Works for both creative flight and elytra gliding";
            case SWIMMING -> "Requires underwater swimming animation, not just being in water";
            case HAS_PLAYER_VARIABLE -> "Use with VariableNode (scope: player) to track persistent state. Variables expire automatically after duration. Format: HAS_PLAYER_VARIABLE:variable_name";
            case MAIN_HAND -> "Use material names like DIAMOND_SWORD or NETHERITE_AXE";
            case HAS_ENCHANT -> "Use enchantment names like SHARPNESS, FIRE_ASPECT, MENDING";
            case HOLDING_SIGIL_ITEM -> "Useful for weapon sigils that should only work when wielded, not just in inventory";
            case DURABILITY_PERCENT -> "Checks the item with this sigil socketed. Use <20 for 'nearly broken', >80 for 'well maintained'";
            case HAS_MARK -> "Format: HAS_MARK:NAME or HAS_MARK:NAME:@Self / @Target / @Victim";
            case CRITICAL_HIT -> "Critical hits require falling and attacking - not just jumping";
            case VICTIM_IS_UNDEAD -> "Includes: Zombie, Skeleton, Wither, Phantom, Drowned, etc.";
            case ON_FIRE -> "Checks player by default, use @Victim for target check";
            case DIMENSION -> "Values: OVERWORLD, THE_NETHER, THE_END";
            case Y_LEVEL -> "Use < for underground, > for high altitude effects";
            case EXPERIENCE_LEVEL -> "Checks display level (0-max), not total XP points";
            case IS_NEGATIVE_EFFECT -> "Only usable in POTION_EFFECT_APPLY signal flows. Checks the effect being applied to the player.";
            case IS_NEGATIVE_MODIFIER -> "Only usable in ATTRIBUTE_MODIFY signal flows. Checks the modifier being applied to the player.";
            case IS_POTION_DAMAGE -> "Only usable in DEFENSE signal flows. Checks if damage cause is POISON or WITHER.";
            case HAS_SET_BONUS -> "Format: HAS_SET_BONUS:ancient_set:2 - Requires Ancient set at tier 2 or higher";
            case IS_BLOCKING_SWORD -> "Requires 1.8 combat module enabled. Only works when blocking with a sword item.";
            case IS_ALLY -> "Requires Factions plugin. Checks MEMBER and ALLY relations. Returns false for non-players.";
            case IS_ENEMY -> "Requires Factions plugin. Only true for ENEMY relation. TRUCE and NEUTRAL are NOT enemies.";
            case IS_TRUCE -> "Requires Factions plugin. TRUCE is a specific relation - not the same as neutral.";
            case IS_NEUTRAL -> "Requires Factions plugin. NEUTRAL = no relation set between factions.";
            case IN_OWN_TERRITORY -> "Requires Factions plugin. Only true in your faction's claimed chunks.";
            case IN_ENEMY_TERRITORY -> "Requires Factions plugin. True in any enemy faction's claims.";
            case IN_WARZONE -> "Requires Factions plugin. Warzone is set by server admins.";
            case IN_SAFEZONE -> "Requires Factions plugin. Safezone is set by server admins.";
            case HAS_FACTION -> "Requires Factions plugin. False for factionless players.";
        };
    }

    /**
     * Get formatted lore for GUI display with all detailed information.
     *
     * @return List of formatted lore lines
     */
    public java.util.List<String> getFormattedDetailedLore() {
        java.util.List<String> lore = new java.util.ArrayList<>();

        lore.add("§7" + description);
        lore.add("");
        lore.add("§eDetailed Description:");
        lore.add("§f" + detailedDescription);
        lore.add("");
        lore.add("§eExample Format: §f" + exampleFormat);
        lore.add("§7" + exampleDescription);
        lore.add("");
        lore.add("§eUsage Example:");
        lore.add("§a" + usageExample);
        lore.add("");

        if (relatedConditions.length > 0) {
            lore.add("§eRelated Conditions:");
            for (String related : relatedConditions) {
                lore.add("§8  - §b" + related);
            }
            lore.add("");
        }

        lore.add("§eTips:");
        lore.add("§6" + tips);

        return lore;
    }
}
