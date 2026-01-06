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

/**
 * Handler for the ITEM_SELECTOR GUI.
 * Allows selection of the item form material for a sigil.
 */
public class ItemSelectorHandler extends AbstractHandler {

    // Available materials for sigil item forms
    private static final Material[] MATERIALS = {
            Material.AMETHYST_SHARD,
            Material.EMERALD,
            Material.NETHER_STAR,
            Material.AMETHYST_CLUSTER,
            Material.ECHO_SHARD,
            Material.PRISMARINE_CRYSTALS,
            Material.DIAMOND
    };

    public ItemSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
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

        // Material selection (slots 10-16)
        if (slot >= 10 && slot <= 16) {
            int index = slot - 10;
            if (index < MATERIALS.length) {
                Material selectedMaterial = MATERIALS[index];

                // Update sigil's item form material
                if (sigil.getItemForm() == null) {
                    sigil.setItemForm(new Sigil.ItemForm());
                }
                sigil.getItemForm().setMaterial(selectedMaterial);

                // Auto-save
                plugin.getSigilManager().saveSigil(sigil);

                player.sendMessage(TextUtil.colorize("§aItem material set to: §f" + formatMaterialName(selectedMaterial)));

                // Refresh GUI to show new selection
                openGUI(plugin, guiManager, player, sigil);
                playSound(player, "click");
            }
        }
    }

    /**
     * Build and open the ITEM_SELECTOR GUI for a sigil.
     */
    public static void openGUI(ArmorSetsPlugin plugin, GUIManager guiManager, Player player, Sigil sigil) {
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("§8Select Item Material"));

        // Fill background with gray glass panes
        ItemBuilder.fillBackground(inv);

        // Get currently selected material
        Material currentMaterial = sigil.getItemForm() != null
                ? sigil.getItemForm().getMaterial()
                : Material.ECHO_SHARD;

        // Add material options (slots 10-16)
        for (int i = 0; i < MATERIALS.length; i++) {
            Material mat = MATERIALS[i];
            boolean isSelected = mat == currentMaterial;

            String name = "§f" + formatMaterialName(mat);
            String lore = isSelected ? "§aCurrently selected" : "§7Click to select";

            ItemStack item = ItemBuilder.createItem(mat, name, lore);

            // Add glow if selected
            if (isSelected) {
                item = ItemBuilder.addGlow(item);
            }

            inv.setItem(10 + i, item);
        }

        // Back button (slot 18)
        inv.setItem(18, ItemBuilder.createItem(Material.RED_DYE, "§c← Back",
                "§7Return to sigil config"));

        // Create session
        GUISession session = new GUISession(GUIType.ITEM_SELECTOR);
        session.put("sigil", sigil);
        session.put("sigilId", sigil.getId());

        guiManager.openGUI(player, inv, session);
    }

    /**
     * Format material name for display.
     */
    private static String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        return capitalize(name);
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
