package com.zenax.armorsets.events;

import org.bukkit.Material;

/**
 * Categories of conditions for organizing the condition selector GUI.
 */
public enum ConditionCategory {
    HEALTH(Material.APPLE, "Health-based conditions"),
    POTION(Material.POTION, "Potion effect conditions"),
    ENVIRONMENTAL(Material.GRASS_BLOCK, "Environmental conditions"),
    COMBAT(Material.DIAMOND_SWORD, "Combat-related conditions"),
    META(Material.NETHER_STAR, "Meta conditions");

    private final Material icon;
    private final String description;

    ConditionCategory(Material icon, String description) {
        this.icon = icon;
        this.description = description;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }
}
