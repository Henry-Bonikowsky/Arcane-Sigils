package com.miracle.arcanesigils.enchanter.gui;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.core.SigilManager;
import com.miracle.arcanesigils.core.SocketManager;
import com.miracle.arcanesigils.enchanter.UpgradeTransaction;
import com.miracle.arcanesigils.enchanter.config.UpgradeCost;
import com.miracle.arcanesigils.enchanter.config.UpgradeCostConfig;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.utils.RomanNumerals;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Upgrade Screen - shows current tier, upgrade button, and next tier.
 * Layout: 3 rows (27 slots)
 */
public class UpgradeScreenHandler extends AbstractHandler {

    private static final int CURRENT_TIER_SLOT = 12;
    private static final int UPGRADE_BUTTON_SLOT = 13;
    private static final int NEXT_TIER_SLOT = 14;
    private static final int BACK_BUTTON_SLOT = 18;

    public UpgradeScreenHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        // Back button
        if (slot == BACK_BUTTON_SLOT) {
            navigateBack(player, session);
            return;
        }

        // Upgrade button
        if (slot == UPGRADE_BUTTON_SLOT) {
            handleUpgrade(player, session);
            return;
        }
    }

    @Override
    public void reopen(Player player, GUISession session) {
        String sigilId = session.get("sigilId", String.class);
        ItemStack armorItem = session.get("armorItem", ItemStack.class);

        if (sigilId == null || armorItem == null) {
            player.closeInventory();
            return;
        }

        SigilManager sigilManager = plugin.getSigilManager();
        Sigil baseSigil = sigilManager.getSigil(sigilId);

        if (baseSigil == null) {
            player.closeInventory();
            return;
        }

        // Get current tier from armor
        int currentTier = getCurrentTier(armorItem, sigilId);

        Inventory inv = Bukkit.createInventory(null, 27,
            TextUtil.colorize("§7Enchanter > §f" + baseSigil.getName() + " §7Upgrade"));

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Current tier display
        inv.setItem(CURRENT_TIER_SLOT, createTierDisplay(baseSigil, currentTier, true));

        // Upgrade button
        UpgradeCostConfig costConfig = baseSigil.getUpgradeCostConfig();
        inv.setItem(UPGRADE_BUTTON_SLOT, createUpgradeButton(player, baseSigil, currentTier, costConfig));

        // Next tier display
        if (currentTier < baseSigil.getMaxTier()) {
            inv.setItem(NEXT_TIER_SLOT, createTierDisplay(baseSigil, currentTier + 1, false));
        }

        // Back button
        inv.setItem(BACK_BUTTON_SLOT, ItemBuilder.createBackButton("Equipment Sigils"));

        guiManager.openGUI(player, inv, session);
    }

    /**
     * Handle the upgrade button click.
     */
    private void handleUpgrade(Player player, GUISession session) {
        String sigilId = session.get("sigilId", String.class);
        ItemStack armorItem = session.get("armorItem", ItemStack.class);

        if (sigilId == null || armorItem == null) {
            return;
        }

        SigilManager sigilManager = plugin.getSigilManager();
        Sigil baseSigil = sigilManager.getSigil(sigilId);

        if (baseSigil == null) {
            return;
        }

        int currentTier = getCurrentTier(armorItem, sigilId);

        // Find the actual armor item in player's inventory (not the clone)
        ItemStack actualArmorItem = findActualArmorItem(player, armorItem);
        if (actualArmorItem == null) {
            player.sendMessage("§cCould not find the armor item in your inventory!");
            playSound(player, "error");
            return;
        }

        // Create and execute transaction
        UpgradeCostConfig costConfig = baseSigil.getUpgradeCostConfig();
        UpgradeTransaction transaction = new UpgradeTransaction(
            plugin, player, actualArmorItem, baseSigil, currentTier, costConfig
        );

        UpgradeTransaction.Result result = transaction.execute();

        if (result.isSuccess()) {
            player.sendMessage(result.getMessage());
            playSound(player, "success");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);
            player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5);

            // Update session with new armor item
            session.put("armorItem", actualArmorItem.clone());

            // Refresh GUI
            reopen(player, session);
        } else {
            player.sendMessage(result.getMessage());
            playSound(player, "error");
        }
    }

    /**
     * Create a tier display item.
     */
    private ItemStack createTierDisplay(Sigil sigil, int tier, boolean isCurrent) {
        Sigil tieredSigil = plugin.getSigilManager().cloneSigil(sigil);
        tieredSigil.setTier(tier);

        List<String> lore = new ArrayList<>();
        lore.add("§7Tier: §e" + RomanNumerals.toRoman(tier));
        lore.add("");

        // Add parameter values for this tier
        // This is simplified - in reality you'd use TierScalingCalculator
        lore.addAll(tieredSigil.getDescription());

        if (isCurrent) {
            lore.add("");
            lore.add("§a§lCURRENT");
        }

        Material material = sigil.getItemForm() != null ?
            sigil.getItemForm().getMaterial() : Material.PAPER;

        String prefix = isCurrent ? "§a" : "§e";
        return ItemBuilder.createItem(material, prefix + sigil.getName(), lore);
    }

    /**
     * Create the upgrade button with cost information.
     */
    private ItemStack createUpgradeButton(Player player, Sigil sigil, int currentTier, UpgradeCostConfig costConfig) {
        int targetTier = currentTier + 1;

        // Check if at max tier
        if (currentTier >= sigil.getMaxTier()) {
            return ItemBuilder.createItem(
                Material.GRAY_DYE,
                "§7Maximum Tier Reached",
                "§cThis sigil is already at maximum tier!"
            );
        }

        // Check if cost is defined
        if (costConfig == null || !costConfig.hasCostForTier(targetTier)) {
            return ItemBuilder.createItem(
                Material.RED_DYE,
                "§cUpgrade Unavailable",
                "§7No upgrade cost defined for tier " + targetTier
            );
        }

        UpgradeCost cost = costConfig.getCostForTier(targetTier);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + sigil.getName() + " §e" + RomanNumerals.toRoman(currentTier) + " §7→ §e" + RomanNumerals.toRoman(targetTier));
        lore.add("");

        // XP cost
        boolean hasEnoughXP = player.getTotalExperience() >= cost.getRequiredXP();
        String xpColor = hasEnoughXP ? "§a" : "§c";
        lore.add("§7XP: " + xpColor + cost.getRequiredXP());

        // Material costs
        if (!cost.getRequiredMaterials().isEmpty()) {
            lore.add("§7Materials:");
            for (ItemStack material : cost.getRequiredMaterials()) {
                boolean hasEnough = hasEnoughMaterial(player, material.getType(), material.getAmount());
                String matColor = hasEnough ? "§a" : "§c";
                String matName = formatMaterialName(material.getType());
                lore.add("  " + matColor + "- " + material.getAmount() + "x " + matName);
            }
        }

        lore.add("");

        // Check if player can afford
        boolean canAfford = hasEnoughXP;
        for (ItemStack material : cost.getRequiredMaterials()) {
            if (!hasEnoughMaterial(player, material.getType(), material.getAmount())) {
                canAfford = false;
                break;
            }
        }

        if (canAfford) {
            lore.add("§aClick to upgrade");
            return ItemBuilder.createItem(Material.LIME_DYE, "§a§lUpgrade", lore);
        } else {
            lore.add("§cInsufficient resources");
            return ItemBuilder.createItem(Material.BROWN_DYE, "§7§lUpgrade", lore);
        }
    }

    /**
     * Get current tier of a sigil from armor item.
     */
    private int getCurrentTier(ItemStack armorItem, String sigilId) {
        SocketManager socketManager = plugin.getSocketManager();
        List<String> sigilData = socketManager.getSocketedSigilData(armorItem);

        for (String entry : sigilData) {
            String[] parts = entry.split(":");
            if (parts[0].equals(sigilId)) {
                return parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            }
        }

        return 1;
    }

    /**
     * Find the actual armor item in player's inventory (not a clone).
     */
    private ItemStack findActualArmorItem(Player player, ItemStack template) {
        // Check armor slots
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() == template.getType()) {
                // Check if it has the same sigils
                if (hasSameSigils(armor, template)) {
                    return armor;
                }
            }
        }

        // Check main inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == template.getType()) {
                if (hasSameSigils(item, template)) {
                    return item;
                }
            }
        }

        return null;
    }

    /**
     * Check if two items have the same sigils.
     */
    private boolean hasSameSigils(ItemStack item1, ItemStack item2) {
        SocketManager socketManager = plugin.getSocketManager();
        List<String> sigils1 = socketManager.getSocketedSigilData(item1);
        List<String> sigils2 = socketManager.getSocketedSigilData(item2);
        return sigils1.equals(sigils2);
    }

    /**
     * Check if player has enough of a material.
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
     * Format material name for display.
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
