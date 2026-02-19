package com.miracle.arcanesigils.gui.sigil;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.RarityUtil;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.GUILayout;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
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
 * Filter submenu for selecting rarities.
 */
public class RarityFilterHandler extends AbstractHandler {

    private static final String[] RARITIES = {
        "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"
    };

    private static final int[] RARITY_SLOTS = {10, 11, 12, 13, 14, 15};

    public RarityFilterHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        openGUI(guiManager, player);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        // Get filter state from session
        FilterState filterState = session.get("filterState", FilterState.class);
        if (filterState == null) {
            filterState = new FilterState();
        }

        // Check if clicking a rarity slot
        for (int i = 0; i < RARITY_SLOTS.length; i++) {
            if (slot == RARITY_SLOTS[i]) {
                String rarity = RARITIES[i];
                filterState.toggleRarity(rarity);
                session.put("filterState", filterState);
                playSound(player, "click");
                refreshGUI(guiManager, player, session, filterState);
                return;
            }
        }

        // Back button
        if (slot == GUILayout.BACK) {
            playSound(player, "close");
            int page = session.getInt("page", 1);
            SigilsMenuHandler.openGUI(guiManager, player, page, null);
        }
    }

    public static void openGUI(GUIManager guiManager, Player player) {
        // Preserve filter state from existing session
        GUISession oldSession = guiManager.getSession(player);
        FilterState filterState = null;
        if (oldSession != null) {
            filterState = oldSession.get("filterState", FilterState.class);
        }
        if (filterState == null) {
            filterState = new FilterState();
        }

        // Create new session with correct type
        GUISession session = new GUISession(GUIType.RARITY_FILTER);
        session.put("filterState", filterState);
        
        // Preserve page number
        if (oldSession != null) {
            Integer page = oldSession.getInt("page", 1);
            session.put("page", page);
        }

        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§eFilter: Rarity"));

        buildInventory(inv, filterState);

        guiManager.openGUI(player, inv, session);
    }

    private static void refreshGUI(GUIManager guiManager, Player player, GUISession session, FilterState filterState) {
        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§eFilter: Rarity"));

        buildInventory(inv, filterState);
        guiManager.updateGUI(player, inv, session);
    }

    private static void buildInventory(Inventory inv, FilterState filterState) {
        inv.clear();

        // Rarity toggle buttons
        for (int i = 0; i < RARITIES.length; i++) {
            String rarity = RARITIES[i];
            boolean active = filterState.getActiveRarities().contains(rarity);
            inv.setItem(RARITY_SLOTS[i], createRarityButton(rarity, active));
        }

        // Back button
        inv.setItem(GUILayout.BACK, ItemBuilder.createItem(
            Material.RED_DYE,
            "§c← Back",
            "§7Return to sigil browser"
        ));

        // Fill background
        ItemBuilder.fillBackground(inv);
    }

    private static ItemStack createRarityButton(String rarity, boolean active) {
        String color = RarityUtil.getColor(rarity);
        Material material = getRarityMaterial(rarity);

        List<String> lore = new ArrayList<>();
        if (active) {
            lore.add("§a✓ Active");
            lore.add("§7Click to deactivate");
        } else {
            lore.add("§7Inactive");
            lore.add("§7Click to activate");
        }

        ItemStack item = ItemBuilder.createItem(material, color + rarity, lore);
        if (active) {
            ItemBuilder.addGlow(item);
        }
        return item;
    }

    private static Material getRarityMaterial(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> Material.GRAY_DYE;
            case "UNCOMMON" -> Material.LIME_DYE;
            case "RARE" -> Material.LIGHT_BLUE_DYE;
            case "EPIC" -> Material.PURPLE_DYE;
            case "LEGENDARY" -> Material.ORANGE_DYE;
            case "MYTHIC" -> Material.MAGENTA_DYE;
            default -> Material.GRAY_DYE;
        };
    }
}
