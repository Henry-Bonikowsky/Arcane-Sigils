package com.miracle.arcanesigils.gui.sigil;

import com.miracle.arcanesigils.ArmorSetsPlugin;
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
 * Filter submenu for selecting slot types.
 */
public class SlotFilterHandler extends AbstractHandler {

    // Slot types matching Sigil.getSocketables() values
    private static final String[] SLOT_TYPES = {
        "helmet", "chestplate", "leggings", "boots", 
        "weapon", "sword", "axe", "bow"
    };

    private static final Material[] SLOT_MATERIALS = {
        Material.IRON_HELMET, Material.IRON_CHESTPLATE, 
        Material.IRON_LEGGINGS, Material.IRON_BOOTS,
        Material.IRON_SWORD, Material.IRON_SWORD,
        Material.IRON_AXE, Material.BOW
    };

    private static final int[] SLOT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 22};

    public SlotFilterHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        openGUI(guiManager, player);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        FilterState filterState = session.get("filterState", FilterState.class);
        if (filterState == null) {
            filterState = new FilterState();
        }

        // Check if clicking a slot type slot
        for (int i = 0; i < SLOT_SLOTS.length; i++) {
            if (slot == SLOT_SLOTS[i]) {
                String slotType = SLOT_TYPES[i];
                filterState.toggleSlotType(slotType);
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
        GUISession session = new GUISession(GUIType.SLOT_FILTER);
        session.put("filterState", filterState);
        
        // Preserve page number
        if (oldSession != null) {
            Integer page = oldSession.getInt("page", 1);
            session.put("page", page);
        }

        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§eFilter: Slot Type"));

        buildInventory(inv, filterState);

        guiManager.openGUI(player, inv, session);
    }

    private static void refreshGUI(GUIManager guiManager, Player player, GUISession session, FilterState filterState) {
        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§eFilter: Slot Type"));

        buildInventory(inv, filterState);
        guiManager.updateGUI(player, inv, session);
    }

    private static void buildInventory(Inventory inv, FilterState filterState) {
        inv.clear();

        // Slot type toggle buttons
        for (int i = 0; i < SLOT_TYPES.length; i++) {
            String slotType = SLOT_TYPES[i];
            boolean active = filterState.getActiveSlotTypes().contains(slotType);
            inv.setItem(SLOT_SLOTS[i], createSlotButton(slotType, SLOT_MATERIALS[i], active));
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

    private static ItemStack createSlotButton(String slotType, Material material, boolean active) {
        // Capitalize first letter for display
        String displayName = slotType.substring(0, 1).toUpperCase() + slotType.substring(1);

        List<String> lore = new ArrayList<>();
        if (active) {
            lore.add("§a✓ Active");
            lore.add("§7Click to deactivate");
        } else {
            lore.add("§7Inactive");
            lore.add("§7Click to activate");
        }

        ItemStack item = ItemBuilder.createItem(material, "§e" + displayName, lore);
        if (active) {
            ItemBuilder.addGlow(item);
        }
        return item;
    }
}
