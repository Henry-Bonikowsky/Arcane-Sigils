package com.miracle.arcanesigils.enchanter.config;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the cost to upgrade a sigil to a specific tier.
 */
public class UpgradeCost {

    private final int requiredXP;
    private final List<ItemStack> requiredMaterials;

    public UpgradeCost(int requiredXP, List<ItemStack> requiredMaterials) {
        this.requiredXP = requiredXP;
        this.requiredMaterials = requiredMaterials != null ? new ArrayList<>(requiredMaterials) : new ArrayList<>();
    }

    /**
     * Get the required vanilla Minecraft XP points
     */
    public int getRequiredXP() {
        return requiredXP;
    }

    /**
     * Get the required material items
     */
    public List<ItemStack> getRequiredMaterials() {
        return new ArrayList<>(requiredMaterials);
    }

    /**
     * Check if a cost is empty (no XP or materials required)
     */
    public boolean isEmpty() {
        return requiredXP == 0 && requiredMaterials.isEmpty();
    }
}
