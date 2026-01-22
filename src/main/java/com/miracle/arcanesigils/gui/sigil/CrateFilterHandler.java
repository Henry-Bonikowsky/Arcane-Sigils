package com.miracle.arcanesigils.gui.sigil;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
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
import java.util.Set;

/**
 * Filter submenu for selecting crates.
 * Dynamically builds list from all unique crate values in loaded sigils.
 */
public class CrateFilterHandler extends AbstractHandler {

    private static final int[] CRATE_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17
    };

    public CrateFilterHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
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

        // Get all crates
        List<Sigil> allSigils = new ArrayList<>(plugin.getSigilManager().getAllSigils());
        Set<String> allCrates = FilterState.getUniqueCrates(allSigils);
        List<String> crateList = new ArrayList<>(allCrates);
        crateList.sort(String::compareToIgnoreCase);

        // Check if clicking a crate slot
        for (int i = 0; i < CRATE_SLOTS.length && i < crateList.size(); i++) {
            if (slot == CRATE_SLOTS[i]) {
                String crate = crateList.get(i);
                filterState.toggleCrate(crate);
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
        GUISession session = new GUISession(GUIType.CRATE_FILTER);
        session.put("filterState", filterState);
        
        // Preserve page number
        if (oldSession != null) {
            Integer page = oldSession.getInt("page", 1);
            session.put("page", page);
        }

        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§eFilter: Crate Type"));

        ArmorSetsPlugin plugin = (ArmorSetsPlugin) Bukkit.getPluginManager().getPlugin("ArcaneSigils");
        buildInventory(inv, filterState, plugin);

        guiManager.openGUI(player, inv, session);
    }

    private static void refreshGUI(GUIManager guiManager, Player player, GUISession session, FilterState filterState) {
        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§eFilter: Crate Type"));

        ArmorSetsPlugin plugin = (ArmorSetsPlugin) Bukkit.getPluginManager().getPlugin("ArcaneSigils");
        buildInventory(inv, filterState, plugin);
        guiManager.updateGUI(player, inv, session);
    }

    private static void buildInventory(Inventory inv, FilterState filterState, ArmorSetsPlugin plugin) {
        inv.clear();

        // Get all unique crates from loaded sigils
        List<Sigil> allSigils = new ArrayList<>(plugin.getSigilManager().getAllSigils());
        Set<String> allCrates = FilterState.getUniqueCrates(allSigils);
        List<String> crateList = new ArrayList<>(allCrates);
        crateList.sort(String::compareToIgnoreCase);

        // Create toggle buttons for each crate
        for (int i = 0; i < crateList.size() && i < CRATE_SLOTS.length; i++) {
            String crate = crateList.get(i);
            boolean active = filterState.getActiveCrates().contains(crate);
            inv.setItem(CRATE_SLOTS[i], createCrateButton(crate, active));
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

    private static ItemStack createCrateButton(String crate, boolean active) {
        Material material = crate.equals("Standard/Uncrated") ? Material.CHEST : Material.ENDER_CHEST;

        List<String> lore = new ArrayList<>();
        if (active) {
            lore.add("§a✓ Active");
            lore.add("§7Click to deactivate");
        } else {
            lore.add("§7Inactive");
            lore.add("§7Click to activate");
        }

        ItemStack item = ItemBuilder.createItem(material, "§e" + crate, lore);
        if (active) {
            ItemBuilder.addGlow(item);
        }
        return item;
    }
}
