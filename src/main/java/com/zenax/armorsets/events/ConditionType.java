package com.zenax.armorsets.events;

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
    HEALTH_BELOW(
        ConditionCategory.HEALTH,
        Material.POISONOUS_POTATO,
        "Health Below",
        "Check if health is below a value",
        "HEALTH_BELOW:10",
        "Health below 10 HP",
        true
    ),
    HEALTH_ABOVE(
        ConditionCategory.HEALTH,
        Material.GOLDEN_APPLE,
        "Health Above",
        "Check if health is above a value",
        "HEALTH_ABOVE:15",
        "Health above 15 HP",
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

    // ===== META CONDITIONS =====
    TRIGGER(
        ConditionCategory.META,
        Material.REDSTONE_TORCH,
        "Trigger Type",
        "Check trigger event type",
        "TRIGGER:ATTACK",
        "Only on ATTACK trigger",
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
            case HEALTH_BELOW -> "Triggers when player's health drops below a specific value. Direct HP check without percentage calculation. Perfect for emergency abilities.";
            case HEALTH_ABOVE -> "Triggers when player's health is above a specific value. Ideal for high-health tank builds or health-gated abilities.";
            case VICTIM_HEALTH_PERCENT -> "Checks the target's health percentage. Excellent for execute mechanics or finishing moves on low-health enemies.";
            case HAS_POTION -> "Verifies player has an active potion effect. Can optionally check amplifier level. Great for synergy-based builds.";
            case NO_POTION -> "Checks that player does NOT have a specific potion effect. Useful for preventing effect stacking or creating conditional buffs.";
            case BIOME -> "Restricts effects to specific biomes. Creates location-themed builds like desert warriors or ocean specialists.";
            case BLOCK_BELOW -> "Checks the block type directly beneath the player. Enables terrain-based bonuses like standing on netherite or water.";
            case LIGHT_LEVEL -> "Measures ambient light at player's location. Perfect for shadow assassins or daylight warriors.";
            case IN_WATER -> "Simple check if player is submerged in water. Essential for aquatic builds and underwater combat.";
            case ON_GROUND -> "Checks if player is standing on solid ground (not jumping/falling). Good for grounded combat styles.";
            case WEATHER -> "Tests current weather conditions (RAINING, THUNDERING, CLEAR). Creates weather-reactive builds.";
            case TIME -> "Checks time of day (NIGHT, DAY, SUNSET, SUNRISE). Enables time-based powers like nocturnal hunters.";
            case HAS_VICTIM -> "Verifies a target exists for victim-targeted effects. Essential prerequisite for VICTIM conditions.";
            case VICTIM_IS_PLAYER -> "Checks if the target is a player (not a mob). Creates PvP-specific abilities.";
            case VICTIM_IS_HOSTILE -> "Verifies target is a hostile mob. Perfect for PvE-focused builds and mob hunting.";
            case TRIGGER -> "Restricts effect to a specific trigger type. Allows multi-trigger configurations with conditional activation.";
            case WEARING_FULL_SET -> "Checks if player is wearing a complete armor set. Enables set-synergy effects.";
        };
    }

    private String generateUsageExample() {
        return switch (this) {
            case HEALTH_PERCENT -> "Glass cannon DPS build: Massive damage below 50% health";
            case HEALTH_BELOW -> "Last stand ability: Invulnerability when health drops below 4 HP";
            case HEALTH_ABOVE -> "Tank sustain: Regeneration only when above 15 HP";
            case VICTIM_HEALTH_PERCENT -> "Execute: Deal 200% damage to targets below 30% health";
            case HAS_POTION -> "Synergy: Speed boost only while Strength is active";
            case NO_POTION -> "Pure build: Bonus damage only when no potion effects present";
            case BIOME -> "Desert warrior: Fire resistance and strength in desert biomes";
            case BLOCK_BELOW -> "Netherite mastery: Bonus stats when standing on netherite blocks";
            case LIGHT_LEVEL -> "Shadow assassin: Increased damage in darkness (light < 7)";
            case IN_WATER -> "Aquatic predator: Speed and strength while swimming";
            case ON_GROUND -> "Grounded fighter: Defense bonus only when on solid ground";
            case WEATHER -> "Storm caller: Lightning effects during thunderstorms";
            case TIME -> "Night hunter: Bonus damage and vision during night";
            case HAS_VICTIM -> "Combat-only: Abilities only work in active combat";
            case VICTIM_IS_PLAYER -> "PvP specialist: Extra damage against players only";
            case VICTIM_IS_HOSTILE -> "Mob slayer: Increased rewards when killing hostile mobs";
            case TRIGGER -> "Versatile: Different effects on attack vs defense";
            case WEARING_FULL_SET -> "Set mastery: Ultimate ability only with full legendary set";
        };
    }

    private String[] generateRelatedConditions() {
        return switch (this) {
            case HEALTH_PERCENT -> new String[]{"HEALTH_BELOW", "HEALTH_ABOVE"};
            case HEALTH_BELOW -> new String[]{"HEALTH_PERCENT", "VICTIM_HEALTH_PERCENT"};
            case HEALTH_ABOVE -> new String[]{"HEALTH_PERCENT", "HAS_POTION"};
            case VICTIM_HEALTH_PERCENT -> new String[]{"HAS_VICTIM", "VICTIM_IS_HOSTILE"};
            case HAS_POTION -> new String[]{"NO_POTION", "TIME"};
            case NO_POTION -> new String[]{"HAS_POTION"};
            case BIOME -> new String[]{"WEATHER", "TIME", "BLOCK_BELOW"};
            case BLOCK_BELOW -> new String[]{"BIOME", "ON_GROUND"};
            case LIGHT_LEVEL -> new String[]{"TIME", "BIOME"};
            case IN_WATER -> new String[]{"BLOCK_BELOW", "BIOME"};
            case ON_GROUND -> new String[]{"BLOCK_BELOW"};
            case WEATHER -> new String[]{"TIME", "BIOME"};
            case TIME -> new String[]{"LIGHT_LEVEL", "WEATHER"};
            case HAS_VICTIM -> new String[]{"VICTIM_IS_PLAYER", "VICTIM_IS_HOSTILE", "VICTIM_HEALTH_PERCENT"};
            case VICTIM_IS_PLAYER -> new String[]{"HAS_VICTIM", "TRIGGER"};
            case VICTIM_IS_HOSTILE -> new String[]{"HAS_VICTIM", "BIOME"};
            case TRIGGER -> new String[]{"HAS_VICTIM"};
            case WEARING_FULL_SET -> new String[]{"HEALTH_ABOVE", "HAS_POTION"};
        };
    }

    private String generateTips() {
        return switch (this) {
            case HEALTH_PERCENT -> "Use with caution - percentage scales with max health changes";
            case HEALTH_BELOW -> "Consider combining with cooldowns to avoid spam at low health";
            case HEALTH_ABOVE -> "Great for tank builds that reward staying healthy";
            case VICTIM_HEALTH_PERCENT -> "Always pair with HAS_VICTIM to avoid null errors";
            case HAS_POTION -> "Can check amplifier level with third parameter (e.g., STRENGTH:>2)";
            case NO_POTION -> "Useful for 'pure' builds that avoid external buffs";
            case BIOME -> "Be aware of biome transitions - effect may flicker";
            case BLOCK_BELOW -> "Checks one block down - player must be grounded";
            case LIGHT_LEVEL -> "Includes both sky and block light - torches count!";
            case IN_WATER -> "Only checks if submerged - rain doesn't count";
            case ON_GROUND -> "False while jumping, falling, or swimming";
            case WEATHER -> "Weather is world-specific - different per dimension";
            case TIME -> "Time ranges: DAY(0-12000), NIGHT(13000-24000)";
            case HAS_VICTIM -> "Required for any VICTIM-based conditions";
            case VICTIM_IS_PLAYER -> "Only works on combat triggers with a target";
            case VICTIM_IS_HOSTILE -> "Mob list is predefined - check ConditionManager for details";
            case TRIGGER -> "Allows same trigger config to behave differently per trigger type";
            case WEARING_FULL_SET -> "Requires exact set ID match - case sensitive";
        };
    }

    /**
     * Get formatted lore for GUI display with all detailed information.
     *
     * @return List of formatted lore lines
     */
    public java.util.List<String> getFormattedDetailedLore() {
        java.util.List<String> lore = new java.util.ArrayList<>();

        lore.add("&7" + description);
        lore.add("");
        lore.add("&eDetailed Description:");
        lore.add("&f" + detailedDescription);
        lore.add("");
        lore.add("&eExample Format: &f" + exampleFormat);
        lore.add("&7" + exampleDescription);
        lore.add("");
        lore.add("&eUsage Example:");
        lore.add("&a" + usageExample);
        lore.add("");

        if (relatedConditions.length > 0) {
            lore.add("&eRelated Conditions:");
            for (String related : relatedConditions) {
                lore.add("&8  - &b" + related);
            }
            lore.add("");
        }

        lore.add("&eTips:");
        lore.add("&6" + tips);

        return lore;
    }
}
