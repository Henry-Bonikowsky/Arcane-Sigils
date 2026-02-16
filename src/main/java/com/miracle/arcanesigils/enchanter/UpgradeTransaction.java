package com.miracle.arcanesigils.enchanter;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.core.SocketManager;
import com.miracle.arcanesigils.enchanter.config.UpgradeCost;
import com.miracle.arcanesigils.enchanter.config.UpgradeCostConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the transaction logic for upgrading a sigil tier.
 * Validates eligibility, deducts costs, and updates the sigil tier.
 */
public class UpgradeTransaction {

    private final ArmorSetsPlugin plugin;
    private final Player player;
    private final ItemStack armorItem;
    private final Sigil sigil;
    private final int currentTier;
    private final int targetTier;
    private final UpgradeCostConfig costConfig;

    /**
     * Result of an upgrade transaction.
     */
    public static class Result {
        private final boolean success;
        private final String message;

        public Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public static Result success(String message) {
            return new Result(true, message);
        }

        public static Result failure(String message) {
            return new Result(false, message);
        }
    }

    public UpgradeTransaction(ArmorSetsPlugin plugin, Player player, ItemStack armorItem,
                            Sigil sigil, int currentTier, UpgradeCostConfig costConfig) {
        this.plugin = plugin;
        this.player = player;
        this.armorItem = armorItem;
        this.sigil = sigil;
        this.currentTier = currentTier;
        this.targetTier = currentTier + 1;
        this.costConfig = costConfig;
    }

    /**
     * Validate if the upgrade is possible.
     */
    public Result validate() {
        // Check if already at max tier
        if (currentTier >= sigil.getMaxTier()) {
            return Result.failure("§cAlready at maximum tier!");
        }

        // Check if target tier is sequential (can't skip tiers)
        if (targetTier != currentTier + 1) {
            return Result.failure("§cMust upgrade sequentially!");
        }

        // Check if upgrade costs are defined
        if (costConfig == null || !costConfig.hasCostForTier(targetTier)) {
            return Result.failure("§cNo upgrade cost defined for tier " + targetTier);
        }

        UpgradeCost cost = costConfig.getCostForTier(targetTier);

        // Check if player has enough XP
        if (player.getTotalExperience() < cost.getRequiredXP()) {
            return Result.failure("§cInsufficient XP! Need " + cost.getRequiredXP() + " XP");
        }

        // Check if player has required materials
        for (ItemStack required : cost.getRequiredMaterials()) {
            if (!hasEnoughMaterial(player, required.getType(), required.getAmount())) {
                return Result.failure("§cInsufficient materials! Need " + required.getAmount() + "x " +
                    formatMaterialName(required.getType()));
            }
        }

        return Result.success("Upgrade validated");
    }

    /**
     * Execute the upgrade transaction.
     * Deducts costs and updates the sigil tier.
     */
    public Result execute() {
        // Validate first
        Result validationResult = validate();
        if (!validationResult.isSuccess()) {
            return validationResult;
        }

        UpgradeCost cost = costConfig.getCostForTier(targetTier);

        // Deduct XP
        int newXP = player.getTotalExperience() - cost.getRequiredXP();
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0);
        player.giveExp(newXP);

        // Deduct materials
        for (ItemStack required : cost.getRequiredMaterials()) {
            removeMaterial(player, required.getType(), required.getAmount());
        }

        // Update sigil tier on armor
        SocketManager socketManager = plugin.getSocketManager();
        socketManager.updateSocketedSigilTier(armorItem, sigil.getId(), targetTier);
        socketManager.refreshArmorLore(armorItem);

        return Result.success("§aSuccessfully upgraded " + sigil.getName() + " §ato tier " + targetTier + "!");
    }

    /**
     * Get the upgrade cost for this transaction.
     */
    public UpgradeCost getCost() {
        if (costConfig == null) {
            return null;
        }
        return costConfig.getCostForTier(targetTier);
    }

    /**
     * Check if player has enough of a specific material.
     */
    private boolean hasEnoughMaterial(Player player, Material material, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    /**
     * Remove a specific amount of material from player inventory.
     */
    private void removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material && remaining > 0) {
                int toRemove = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - toRemove);
                remaining -= toRemove;
            }
        }
    }

    /**
     * Format material name for display (DIAMOND -> Diamond).
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
}
