package com.miracle.arcanesigils.enchanter.gui;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.core.SigilManager;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.utils.RomanNumerals;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Tier Comparison View - displays all tiers for a sigil (info-only).
 * Layout: 4 rows (36 slots)
 */
public class TierComparisonHandler extends AbstractHandler {

    private static final int BACK_BUTTON_SLOT = 27;

    public TierComparisonHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        // Back button
        if (slot == BACK_BUTTON_SLOT) {
            navigateBack(player, session);
        }
    }

    @Override
    public void reopen(Player player, GUISession session) {
        String sigilId = session.get("sigilId", String.class);
        if (sigilId == null) {
            player.closeInventory();
            return;
        }

        SigilManager sigilManager = plugin.getSigilManager();
        Sigil baseSigil = sigilManager.getSigil(sigilId);

        if (baseSigil == null) {
            player.closeInventory();
            return;
        }

        // Use Component for title to support gradients in sigil names
        net.kyori.adventure.text.Component title = TextUtil.parseComponent(
            "§7Enchanter > §f" + baseSigil.getName() + " §7Tiers");
        Inventory inv = Bukkit.createInventory(null, 36, title);

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Place tier displays
        int maxTier = baseSigil.getMaxTier();

        // Row 2 (slots 11-15) - Tiers 1-5
        int[] row2Slots = {11, 12, 13, 14, 15};
        for (int tier = 1; tier <= Math.min(5, maxTier); tier++) {
            inv.setItem(row2Slots[tier - 1], createTierItem(baseSigil, tier));
        }

        // Row 3 (slots 20-24) - Tiers 6-10 (if applicable)
        if (maxTier > 5) {
            int[] row3Slots = {20, 21, 22, 23, 24};
            for (int tier = 6; tier <= Math.min(10, maxTier); tier++) {
                inv.setItem(row3Slots[tier - 6], createTierItem(baseSigil, tier));
            }
        }

        // Back button
        inv.setItem(BACK_BUTTON_SLOT, ItemBuilder.createBackButton("Browse"));

        guiManager.openGUI(player, inv, session);
    }

    /**
     * Create a tier display item.
     */
    private ItemStack createTierItem(Sigil sigil, int tier) {
        Sigil tieredSigil = plugin.getSigilManager().cloneSigil(sigil);
        tieredSigil.setTier(tier);

        List<String> lore = new ArrayList<>();
        lore.add("§7Tier: §e" + RomanNumerals.toRoman(tier));
        lore.add("");

        // Add tier-scaled parameters if they exist
        if (sigil.getTierScalingConfig() != null && sigil.getTierScalingConfig().hasParams()) {
            lore.add("§6Parameters:");

            java.util.Set<String> paramNames = sigil.getTierScalingConfig().getParams().getParameterNames();
            for (String paramName : paramNames) {
                String value = sigil.getTierScalingConfig().getParamValueAsString(paramName, tier);
                String formattedParam = formatParameter(paramName, value);
                lore.add("§7  " + formattedParam);
            }

            lore.add("");
        }

        // Add description for this tier
        lore.addAll(tieredSigil.getDescription());

        Material material = sigil.getItemForm() != null ?
            sigil.getItemForm().getMaterial() : Material.PAPER;

        return ItemBuilder.createItem(material, "§e" + RomanNumerals.toRoman(tier), lore);
    }

    /**
     * Format a parameter name and value for display.
     * Adds appropriate units and formatting based on parameter name.
     */
    private String formatParameter(String paramName, String value) {
        String name = capitalizeFirst(paramName);
        String lower = paramName.toLowerCase();

        // Add units based on common parameter names
        if (lower.contains("chance") || lower.contains("percent")) {
            return name + ": §e" + value + "%";
        } else if (lower.contains("duration") || lower.contains("cooldown")) {
            return name + ": §e" + value + "s";
        } else if (lower.contains("damage")) {
            return name + ": §e" + value + " ❤";
        } else if (lower.contains("heal") || lower.contains("health")) {
            return name + ": §e" + value + " ❤";
        } else if (lower.contains("range") || lower.contains("radius")) {
            return name + ": §e" + value + " blocks";
        } else if (lower.contains("speed") || lower.contains("velocity")) {
            return name + ": §e" + value + "x";
        } else {
            // Generic format - no unit
            return name + ": §e" + value;
        }
    }

    /**
     * Capitalize and format parameter name (e.g., "mark_chance" -> "Mark Chance").
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;

        // Replace underscores with spaces and capitalize each word
        String[] words = str.split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }

}
