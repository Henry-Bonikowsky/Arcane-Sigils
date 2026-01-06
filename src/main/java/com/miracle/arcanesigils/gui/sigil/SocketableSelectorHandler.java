package com.miracle.arcanesigils.gui.sigil;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Handler for the SOCKETABLE_SELECTOR GUI.
 * Allows multi-selection of which item types a sigil can be socketed into.
 */
public class SocketableSelectorHandler extends AbstractHandler {

    /**
     * Represents a socketable item type option.
     */
    private static class SocketableOption {
        final int slot;
        final String id;
        final Material material;
        final String displayName;

        SocketableOption(int slot, String id, Material material, String displayName) {
            this.slot = slot;
            this.id = id;
            this.material = material;
            this.displayName = displayName;
        }
    }

    // Available socketable item types with their GUI positions
    // These must match what SocketManager.getItemType() returns
    private static final SocketableOption[] OPTIONS = {
            // Armor types (row 1)
            new SocketableOption(10, "helmet", Material.IRON_HELMET, "§fHelmet"),
            new SocketableOption(11, "chestplate", Material.IRON_CHESTPLATE, "§fChestplate"),
            new SocketableOption(12, "leggings", Material.IRON_LEGGINGS, "§fLeggings"),
            new SocketableOption(13, "boots", Material.IRON_BOOTS, "§fBoots"),
            // Weapon/Tool types (row 1 continued + row 2)
            new SocketableOption(14, "weapon", Material.DIAMOND_SWORD, "§fWeapons"),
            new SocketableOption(15, "bow", Material.BOW, "§fBows"),
            new SocketableOption(16, "tool", Material.DIAMOND_PICKAXE, "§fTools"),
            // Row 2
            new SocketableOption(19, "axe", Material.DIAMOND_AXE, "§fAxes"),
            new SocketableOption(20, "offhand", Material.SHIELD, "§fOffhand")
    };

    public SocketableSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil == null) {
            player.sendMessage(TextUtil.colorize("§cError: Sigil not found"));
            return;
        }

        // Back button
        if (slot == 18) {
            SigilConfigHandler.openGUI(plugin, guiManager, player, sigil);
            playSound(player, "close");
            return;
        }

        // Check if clicked slot is a socketable option
        for (SocketableOption option : OPTIONS) {
            if (slot == option.slot) {
                toggleSocketableItem(player, sigil, option.id);
                // Refresh GUI to show updated selection
                openGUI(plugin, guiManager, player, sigil);
                playSound(player, "click");
                return;
            }
        }
    }

    /**
     * Toggle a socketable item type on/off.
     */
    private void toggleSocketableItem(Player player, Sigil sigil, String itemType) {
        Set<String> socketableItems = sigil.getSocketables();
        if (socketableItems == null) {
            socketableItems = new HashSet<>();
            sigil.setSocketables(socketableItems);
        }

        if (socketableItems.contains(itemType)) {
            socketableItems.remove(itemType);
            player.sendMessage(TextUtil.colorize("§7Removed: §f" + formatItemType(itemType)));
        } else {
            socketableItems.add(itemType);
            player.sendMessage(TextUtil.colorize("§7Added: §f" + formatItemType(itemType)));
        }

        // Auto-save
        plugin.getSigilManager().saveSigil(sigil);
    }

    /**
     * Build and open the SOCKETABLE_SELECTOR GUI for a sigil.
     */
    public static void openGUI(ArmorSetsPlugin plugin, GUIManager guiManager, Player player, Sigil sigil) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("§8Select Socketable Items"));

        // Fill background with gray glass panes
        ItemBuilder.fillBackground(inv);

        // Get currently selected socketable items
        Set<String> selectedItems = sigil.getSocketables();
        if (selectedItems == null) {
            selectedItems = new HashSet<>();
        }

        // Add all socketable options
        for (SocketableOption option : OPTIONS) {
            boolean isSelected = selectedItems.contains(option.id);

            String lore = isSelected
                    ? "§aSelected\n&7Click to deselect"
                    : "§7Not selected\n&7Click to select";

            ItemStack item = ItemBuilder.createItem(option.material, option.displayName, lore.split("\n"));

            // Add glow if selected
            if (isSelected) {
                item = ItemBuilder.addGlow(item);
            }

            inv.setItem(option.slot, item);
        }

        // Back button (slot 18)
        inv.setItem(18, ItemBuilder.createItem(Material.RED_DYE, "§c← Back",
                "§7Return to sigil config"));

        // Create session
        GUISession session = new GUISession(GUIType.SOCKETABLE_SELECTOR);
        session.put("sigil", sigil);
        session.put("sigilId", sigil.getId());

        guiManager.openGUI(player, inv, session);
    }

    /**
     * Format item type name for display.
     */
    private static String formatItemType(String itemType) {
        return capitalize(itemType.toLowerCase().replace('_', ' '));
    }

    /**
     * Capitalize first letter of each word.
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] words = str.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
                result.append(" ");
            }
        }
        return result.toString().trim();
    }
}
