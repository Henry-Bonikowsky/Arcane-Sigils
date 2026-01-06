package com.miracle.arcanesigils.gui.sigil;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.gui.tier.TierConfigHandler;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handler for the SIGIL_CONFIG GUI.
 * Allows configuration of:
 * - Item display (material)
 * - Socketable items (which item types can hold this sigil)
 * - Tier configuration
 */
public class SigilConfigHandler extends AbstractHandler {

    public SigilConfigHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil == null) {
            player.closeInventory();
            return;
        }
        openGUI(plugin, guiManager, player, sigil);
    }

    // Rarity options for cycling
    private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "SPECIAL"};

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        var v = session.validator(player);
        Sigil sigil = v.require("sigil", Sigil.class);
        if (v.handleInvalid()) return;

        switch (slot) {
            case 10 -> { // Item Display (D) - opens Item Selector
                openItemSelector(player, sigil);
            }
            case 12 -> { // Socketable Items (S) - opens Socketable Selector (moved from 13)
                openSocketableSelector(player, sigil);
            }
            case 14 -> { // Rarity - cycle through rarities
                cycleRarity(player, sigil);
            }
            case 16 -> { // Tier Config (T) - opens TIER_CONFIG
                openTierConfig(player, sigil);
            }
            case 18 -> { // Back button (X) - return to Sigil Editor
                playSound(player, "click");
                SigilEditorHandler.openGUI(guiManager, player, sigil);
            }
        }
    }

    /**
     * Cycle through rarity options.
     */
    private void cycleRarity(Player player, Sigil sigil) {
        String currentRarity = sigil.getRarity();
        int currentIndex = 0;
        for (int i = 0; i < RARITIES.length; i++) {
            if (RARITIES[i].equalsIgnoreCase(currentRarity)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % RARITIES.length;
        String newRarity = RARITIES[nextIndex];
        sigil.setRarity(newRarity);

        String rarityColor = getRarityColor(newRarity);
        player.sendMessage(TextUtil.colorize("§7Rarity set to: " + rarityColor + newRarity));
        playSound(player, "click");

        // Refresh GUI
        openGUI(plugin, guiManager, player, sigil);
    }

    /**
     * Open the Item Selector GUI.
     */
    private void openItemSelector(Player player, Sigil sigil) {
        ItemSelectorHandler.openGUI(plugin, guiManager, player, sigil);
        playSound(player, "click");
    }

    /**
     * Open the Socketable Selector GUI.
     */
    private void openSocketableSelector(Player player, Sigil sigil) {
        SocketableSelectorHandler.openGUI(plugin, guiManager, player, sigil);
        playSound(player, "click");
    }

    /**
     * Open the Tier Config GUI.
     */
    private void openTierConfig(Player player, Sigil sigil) {
        playSound(player, "click");
        TierConfigHandler.openGUI(guiManager, player, sigil);
    }

    /**
     * Build and open the SIGIL_CONFIG GUI for a sigil.
     */
    public static void openGUI(ArmorSetsPlugin plugin, GUIManager guiManager, Player player, Sigil sigil) {
        // Breadcrumb title: "Sigils > Fire Burst > Config"
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("§7" + sigil.getName() + " > §fConfig"));

        // Fill background with gray glass panes
        ItemBuilder.fillBackground(inv);

        // Item Display (slot 10)
        Material currentMaterial = sigil.getItemForm() != null
                ? sigil.getItemForm().getMaterial()
                : Material.ECHO_SHARD;
        List<String> displayLore = new ArrayList<>();
        displayLore.add("§7Current Material: §f" + formatMaterialName(currentMaterial));
        displayLore.add("");
        displayLore.add("§eClick to change");

        inv.setItem(10, ItemBuilder.createItem(Material.CHEST, "§eItem Display", displayLore));

        // Socketable Items (slot 12 - moved from 13)
        Set<String> socketableItems = sigil.getSocketables();
        List<String> socketableLore = new ArrayList<>();
        socketableLore.add("§7Selected items:");
        for (String item : socketableItems) {
            socketableLore.add("§8- §f" + formatItemType(item));
        }
        socketableLore.add("");
        socketableLore.add("§eClick to configure");

        inv.setItem(12, ItemBuilder.createItem(Material.IRON_SWORD, "§eSocketable Items", socketableLore));

        // Rarity (slot 14)
        String currentRarity = sigil.getRarity() != null ? sigil.getRarity() : "COMMON";
        String rarityColor = getRarityColor(currentRarity);
        inv.setItem(14, ItemBuilder.createItem(getRarityMaterial(currentRarity), rarityColor + "Rarity",
                "§7Current: " + rarityColor + currentRarity,
                "",
                "§eClick to cycle"));

        // Tier Config (slot 16)
        inv.setItem(16, ItemBuilder.createItem(Material.EXPERIENCE_BOTTLE, "§eTier Configuration",
                "§7Configure tier scaling",
                "",
                "§eClick to configure"));

        // Back button (slot 18)
        inv.setItem(18, ItemBuilder.createItem(Material.RED_DYE, "§c← Back",
                "§7Return to sigil editor"));

        // Create session
        GUISession session = new GUISession(GUIType.SIGIL_CONFIG);
        session.put("sigil", sigil);
        session.put("sigilId", sigil.getId());

        guiManager.openGUI(player, inv, session);
    }

    /**
     * Get color code for rarity.
     */
    private static String getRarityColor(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> "§7";
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            case "SPECIAL" -> "§b";
            default -> "§7";
        };
    }

    /**
     * Get material for rarity display.
     */
    private static Material getRarityMaterial(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> Material.COAL;
            case "UNCOMMON" -> Material.IRON_INGOT;
            case "RARE" -> Material.GOLD_INGOT;
            case "EPIC" -> Material.DIAMOND;
            case "LEGENDARY" -> Material.EMERALD;
            case "MYTHIC" -> Material.NETHER_STAR;
            case "SPECIAL" -> Material.DRAGON_EGG;
            default -> Material.COAL;
        };
    }

    /**
     * Format material name for display.
     */
    private static String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        return capitalize(name);
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
