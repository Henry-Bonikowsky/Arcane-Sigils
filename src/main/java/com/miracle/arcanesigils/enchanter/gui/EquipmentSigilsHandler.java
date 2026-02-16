package com.miracle.arcanesigils.enchanter.gui;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.core.SigilManager;
import com.miracle.arcanesigils.core.SocketManager;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Equipment Sigils Screen - shows all sigils socketed on a specific item.
 * Layout: 4 rows (36 slots)
 */
public class EquipmentSigilsHandler extends AbstractHandler {

    private static final int BACK_BUTTON_SLOT = 27;

    public EquipmentSigilsHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
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

        // Check if clicked on a sigil
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }

        // Get the sigil ID from the item's PDC or lore
        String sigilId = getSigilIdFromItem(clickedItem);
        if (sigilId == null) {
            return;
        }

        SigilManager sigilManager = plugin.getSigilManager();
        Sigil sigil = sigilManager.getSigil(sigilId);
        if (sigil == null) {
            return;
        }

        playSound(player, "click");

        // Get the armor item from session
        ItemStack armorItem = session.get("armorItem", ItemStack.class);

        // If exclusive, show tier comparison (info only)
        if (sigil.isExclusive()) {
            GUISession tierSession = new GUISession(GUIType.ENCHANTER_TIER_COMPARISON);
            tierSession.put("parentType", GUIType.ENCHANTER_EQUIPMENT);
            tierSession.put("sigilId", sigilId);
            tierSession.put("armorItem", armorItem);
            guiManager.reopenGUI(player, tierSession);
        } else {
            // Regular sigil - show upgrade screen
            GUISession upgradeSession = new GUISession(GUIType.ENCHANTER_UPGRADE);
            upgradeSession.put("parentType", GUIType.ENCHANTER_EQUIPMENT);
            upgradeSession.put("sigilId", sigilId);
            upgradeSession.put("armorItem", armorItem);
            guiManager.reopenGUI(player, upgradeSession);
        }
    }

    @Override
    public void reopen(Player player, GUISession session) {
        ItemStack armorItem = session.get("armorItem", ItemStack.class);
        if (armorItem == null) {
            player.closeInventory();
            return;
        }

        String itemName = armorItem.getType().name().toLowerCase().replace('_', ' ');
        Inventory inv = Bukkit.createInventory(null, 36,
            TextUtil.colorize("§7Enchanter > §f" + itemName));

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Get socketed sigils
        SocketManager socketManager = plugin.getSocketManager();
        List<String> sigilData = socketManager.getSocketedSigilData(armorItem);

        // Place sigil items (rows 2-3, centered)
        int[] sigilSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int sigilIndex = 0;

        for (String entry : sigilData) {
            if (sigilIndex >= sigilSlots.length) break;

            String[] parts = entry.split(":");
            String sigilId = parts[0];
            int tier = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

            Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
            if (sigil != null) {
                sigil.setTier(tier); // Set the tier for display
                ItemStack sigilItem = createSigilDisplayItem(sigil);
                inv.setItem(sigilSlots[sigilIndex], sigilItem);
                sigilIndex++;
            }
        }

        // Back button
        inv.setItem(BACK_BUTTON_SLOT, ItemBuilder.createBackButton("Main Menu"));

        guiManager.openGUI(player, inv, session);
    }

    /**
     * Create a display item for a sigil.
     */
    private ItemStack createSigilDisplayItem(Sigil sigil) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Tier: §e" + toRomanNumeral(sigil.getTier()));
        lore.addAll(sigil.getDescription());

        if (sigil.isExclusive()) {
            lore.add("");
            lore.add("§d§lEXCLUSIVE");
            lore.add("§7Click to view tier information");
        } else {
            lore.add("");
            lore.add("§aClick to upgrade");
        }

        Material material = sigil.getItemForm() != null ?
            sigil.getItemForm().getMaterial() : Material.PAPER;

        return ItemBuilder.createItem(material, sigil.getName(), lore);
    }

    /**
     * Extract sigil ID from a display item (stored in NBT or parsed from lore).
     */
    private String getSigilIdFromItem(ItemStack item) {
        // For now, we'll need to match based on the item's display name
        // This is a simplification - in production you'd store the ID in PDC
        if (!item.hasItemMeta()) return null;

        Component displayName = item.getItemMeta().displayName();
        if (displayName == null) return null;

        String plainName = TextUtil.stripColors(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(displayName));

        // Find matching sigil by name
        for (Sigil sigil : plugin.getSigilManager().getAllSigils()) {
            if (TextUtil.stripColors(sigil.getName()).equals(plainName)) {
                return sigil.getId();
            }
        }

        return null;
    }

    /**
     * Convert tier number to roman numeral.
     */
    private String toRomanNumeral(int tier) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
        if (tier >= 1 && tier <= 20) {
            return numerals[tier - 1];
        }
        return String.valueOf(tier);
    }
}
