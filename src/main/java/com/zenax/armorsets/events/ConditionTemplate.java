package com.zenax.armorsets.events;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-built condition templates that users can apply with one click.
 * These templates provide common condition combinations for typical gameplay scenarios.
 */
public enum ConditionTemplate {

    GLASS_CANNON(
        Material.GLASS,
        "Glass Cannon",
        "Low HP offensive build - effects trigger when health is critical",
        List.of("HEALTH_BELOW:20")
    ),

    TANK(
        Material.SHIELD,
        "Tank",
        "High HP defensive build - effects trigger when health is high",
        List.of("HEALTH_ABOVE:15")
    ),

    NIGHT_HUNTER(
        Material.ENDER_EYE,
        "Night Hunter",
        "Darkness bonuses - effects active during night in dark areas",
        List.of("TIME:NIGHT", "LIGHT_LEVEL:<7")
    ),

    AQUATIC(
        Material.HEART_OF_THE_SEA,
        "Aquatic",
        "Water exclusive - effects only work while swimming",
        List.of("IN_WATER", "BLOCK_BELOW:WATER")
    ),

    UNDEAD_SLAYER(
        Material.GOLDEN_SWORD,
        "Undead Slayer",
        "Hostile mob hunter - bonus damage against hostile enemies",
        List.of("VICTIM_IS_HOSTILE", "HAS_VICTIM")
    ),

    SUPPORT(
        Material.GOLDEN_APPLE,
        "Support",
        "Ally healer - effects trigger when helping other players",
        List.of("HAS_POTION:REGENERATION", "VICTIM_IS_PLAYER")
    ),

    WEAKLING_EXECUTIONER(
        Material.NETHERITE_AXE,
        "Weakling Executioner",
        "Finisher build - massive damage to low health targets",
        List.of("VICTIM_HEALTH_PERCENT:<30", "HAS_VICTIM")
    ),

    BERSERKER(
        Material.TNT,
        "Berserker",
        "Risk/reward - powerful offensive effects when critically low on health",
        List.of("HEALTH_BELOW:15", "TRIGGER:ATTACK")
    );

    private final Material icon;
    private final String displayName;
    private final String description;
    private final List<String> conditions;

    ConditionTemplate(Material icon, String displayName, String description, List<String> conditions) {
        this.icon = icon;
        this.displayName = displayName;
        this.description = description;
        this.conditions = conditions;
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

    public List<String> getConditions() {
        return conditions;
    }

    /**
     * Get a template by its display name (case-insensitive).
     *
     * @param name The display name to search for
     * @return The matching template, or null if not found
     */
    public static ConditionTemplate getByDisplayName(String name) {
        if (name == null) return null;

        for (ConditionTemplate template : values()) {
            if (template.displayName.equalsIgnoreCase(name)) {
                return template;
            }
        }
        return null;
    }

    /**
     * Get formatted lore for displaying in GUI.
     *
     * @return List of lore strings with color codes
     */
    public List<String> getFormattedLore() {
        List<String> lore = new ArrayList<>();
        lore.add("&7" + description);
        lore.add("");
        lore.add("&eConditions:");
        for (String condition : conditions) {
            lore.add("&8  - &f" + condition);
        }
        return lore;
    }
}
