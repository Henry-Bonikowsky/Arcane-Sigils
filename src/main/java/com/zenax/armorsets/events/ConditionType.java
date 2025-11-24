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
}
