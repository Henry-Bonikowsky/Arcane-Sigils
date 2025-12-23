package com.zenax.armorsets.gui.tier;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.constants.GUIConstants;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.tier.TierProgressionManager;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for the TIER_PROGRESS_VIEWER GUI.
 * Shows players their equipped sigils' XP progress toward next tier.
 * This is a read-only view accessible by all players.
 */
public class TierProgressViewerHandler extends AbstractHandler {

    public TierProgressViewerHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        if (slot == GUIConstants.TIER_PROGRESS_CLOSE) {
            player.closeInventory();
            playSound(player, "close");
        }
        // All other clicks are ignored - read-only view
    }

    /**
     * Build and open the TIER_PROGRESS_VIEWER GUI for a player.
     */
    public static void openGUI(ArmorSetsPlugin plugin, GUIManager guiManager, Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent("§8Sigil XP Progress"));

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Get player's armor
        PlayerInventory playerInv = player.getInventory();
        ItemStack[] armorContents = playerInv.getArmorContents();

        // Armor slot indices: 0=boots, 1=leggings, 2=chestplate, 3=helmet
        // Display slots: 10, 12, 14, 16 (spaced out)
        int[] displaySlots = {16, 14, 12, 10}; // helmet, chest, legs, boots

        List<SigilProgressEntry> entries = new ArrayList<>();
        TierProgressionManager progressManager = plugin.getTierProgressionManager();

        for (int i = 3; i >= 0; i--) { // helmet to boots
            ItemStack armor = armorContents[i];
            if (armor == null || armor.getType() == Material.AIR) continue;

            // Check for socketed sigils
            List<String> sigilDataList = plugin.getSocketManager().getSocketedSigilData(armor);
            if (sigilDataList == null || sigilDataList.isEmpty()) continue;

            // Process each socketed sigil (typically only one per armor piece)
            for (String sigilData : sigilDataList) {
                // Parse sigil data "sigilId:tier"
                String[] parts = sigilData.split(":");
                String sigilId = parts[0];
                int tier = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

                Sigil baseSigil = plugin.getSigilManager().getSigil(sigilId);
                if (baseSigil == null) continue;

                TierProgressionManager.XPProgressInfo progress = progressManager.getProgressInfo(armor, sigilId, tier);

                entries.add(new SigilProgressEntry(
                        baseSigil,
                        tier,
                        baseSigil.getMaxTier(),
                        progress,
                        armor.getType()
                ));
            }
        }

        // Display sigil progress
        int slotIndex = 0;
        for (SigilProgressEntry entry : entries) {
            if (slotIndex >= displaySlots.length) break;

            ItemStack display = buildProgressItem(entry);
            inv.setItem(displaySlots[slotIndex], display);
            slotIndex++;
        }

        // If no sigils found
        if (entries.isEmpty()) {
            inv.setItem(13, ItemBuilder.createItem(Material.BARRIER,
                    "§cNo Sigils Equipped",
                    "§7Socket sigils into your armor",
                    "§7to track XP progress here"
            ));
        }

        // Info panel
        inv.setItem(31, ItemBuilder.createItem(Material.BOOK,
                "§eXP Progress Info",
                "§7Sigils gain XP when they activate.",
                "§7More powerful effects = more XP.",
                "",
                "§7When enough XP is gained,",
                "§7sigils automatically tier up!",
                "",
                "§fTier ups increase sigil power."
        ));

        // Close button
        inv.setItem(GUIConstants.TIER_PROGRESS_CLOSE, ItemBuilder.createItem(Material.BARRIER,
                "§cClose"
        ));

        // Create session
        GUISession session = new GUISession(GUIType.TIER_PROGRESS_VIEWER);
        guiManager.openGUI(player, inv, session);
    }

    private static ItemStack buildProgressItem(SigilProgressEntry entry) {
        Sigil sigil = entry.sigil;
        TierProgressionManager.XPProgressInfo progress = entry.progress;

        String tierDisplay = toRomanNumeral(entry.currentTier);
        String maxTierDisplay = toRomanNumeral(entry.maxTier);
        String name = "§e" + sigil.getName() + " §7[&f" + tierDisplay + "§7/&f" + maxTierDisplay + "§7]";

        List<String> lore = new ArrayList<>();
        lore.add("§7Equipped on: §f" + formatArmorType(entry.armorType));
        lore.add("");

        if (progress == null || !progress.xpEnabled) {
            lore.add("§8XP Progression: §cDisabled");
        } else if (progress.maxTierReached) {
            lore.add("§a✓ Maximum Tier Reached!");
            lore.add("§7Total XP: §f" + progress.currentXP);
        } else {
            lore.add("§7XP Progress:");
            lore.add(progress.getProgressBar(20));
            lore.add("§f" + progress.currentXP + " §7/ §f" + progress.requiredXP + " XP");
            lore.add("");
            int remaining = progress.requiredXP - progress.currentXP;
            lore.add("§7" + remaining + " XP to next tier");
        }

        Material material = sigil.getItemForm() != null ? sigil.getItemForm().getMaterial() : Material.NETHER_STAR;
        return ItemBuilder.createItem(material, name, lore.toArray(new String[0]));
    }

    private static String toRomanNumeral(int tier) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
        if (tier >= 1 && tier <= 20) {
            return numerals[tier - 1];
        }
        return String.valueOf(tier);
    }

    private static String formatArmorType(Material material) {
        String name = material.name();
        if (name.contains("HELMET")) return "Helmet";
        if (name.contains("CHESTPLATE")) return "Chestplate";
        if (name.contains("LEGGINGS")) return "Leggings";
        if (name.contains("BOOTS")) return "Boots";
        return name;
    }

    private record SigilProgressEntry(
            Sigil sigil,
            int currentTier,
            int maxTier,
            TierProgressionManager.XPProgressInfo progress,
            Material armorType
    ) {}
}
