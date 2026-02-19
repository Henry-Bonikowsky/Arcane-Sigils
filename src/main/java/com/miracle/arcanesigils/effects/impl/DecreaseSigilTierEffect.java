package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Decrease sigil tier effect - decreases the tier of the sigil that triggered this effect.
 * If the sigil is at tier 1, removes it entirely from the item.
 * Format: DECREASE_SIGIL_TIER
 *
 * No parameters - automatically targets the source sigil.
 */
public class DecreaseSigilTierEffect extends AbstractEffect {

    public DecreaseSigilTierEffect() {
        super("DECREASE_SIGIL_TIER", "Decrease sigil tier (remove if tier 1)");
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();

        // Get the source sigil and item from metadata
        String sigilId = context.getMetadata("sourceSigilId", null);
        ItemStack sourceItem = context.getMetadata("sourceItem", null);

        if (sigilId == null || sourceItem == null) {
            debug("No source sigil metadata found for DECREASE_SIGIL_TIER");
            return false;
        }

        ArmorSetsPlugin plugin = getPlugin();
        NamespacedKey socketedKey = plugin.getSocketManager().getSocketedSigilKey();

        // Get current sigil data
        ItemMeta meta = sourceItem.getItemMeta();
        if (meta == null) return false;

        String data = meta.getPersistentDataContainer().get(socketedKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return false;

        List<String> sigilEntries = new ArrayList<>(Arrays.asList(data.split(",")));
        boolean modified = false;
        String actionTaken = "";

        // Find and modify the target sigil
        for (int i = 0; i < sigilEntries.size(); i++) {
            String entry = sigilEntries.get(i);
            String[] parts = entry.split(":");
            String entryId = parts[0];

            if (entryId.equalsIgnoreCase(sigilId)) {
                int currentTier = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

                if (currentTier <= 1) {
                    // Remove the sigil entirely
                    sigilEntries.remove(i);
                    actionTaken = "removed";

                    // Notify player
                    Sigil baseSigil = plugin.getSigilManager().getSigil(sigilId);
                    String sigilName = baseSigil != null ? baseSigil.getName() : sigilId;
                    player.sendMessage(com.miracle.arcanesigils.utils.TextUtil.colorize(
                        "§c" + sigilName + " §7has been §cdestroyed&7!"));
                } else {
                    // Decrease tier by 1
                    int newTier = currentTier - 1;
                    sigilEntries.set(i, entryId + ":" + newTier);
                    actionTaken = "tier " + currentTier + " -> " + newTier;

                    // Notify player
                    Sigil baseSigil = plugin.getSigilManager().getSigil(sigilId);
                    String sigilName = baseSigil != null ? baseSigil.getName() : sigilId;
                    player.sendMessage(com.miracle.arcanesigils.utils.TextUtil.colorize(
                        "§e" + sigilName + " §7tier decreased to §e" + newTier + "§7!"));
                }

                modified = true;
                break;
            }
        }

        if (!modified) {
            debug("Could not find sigil " + sigilId + " to decrease tier");
            return false;
        }

        // Update the item's PDC
        if (sigilEntries.isEmpty()) {
            meta.getPersistentDataContainer().remove(socketedKey);
        } else {
            meta.getPersistentDataContainer().set(socketedKey, PersistentDataType.STRING,
                String.join(",", sigilEntries));
        }

        // Update lore
        plugin.getSocketManager().updateItemLorePublic(meta, sigilEntries, sourceItem.getType());
        sourceItem.setItemMeta(meta);

        debug("Decreased sigil tier: " + sigilId + " (" + actionTaken + ")");
        return true;
    }
}
