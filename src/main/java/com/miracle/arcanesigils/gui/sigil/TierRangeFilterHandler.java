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
 * Filter submenu for selecting tier range (min/max).
 */
public class TierRangeFilterHandler extends AbstractHandler {

    private static final int MIN_TIER_SLOT = 11;
    private static final int MAX_TIER_SLOT = 15;
    private static final int CLEAR_SLOT = 22;

    public TierRangeFilterHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
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

        if (slot == MIN_TIER_SLOT) {
            // Request min tier value
            playSound(player, "click");
            final FilterState finalFilterState = filterState;
            guiManager.getInputHelper().requestText(player, "Minimum Tier", "1",
                input -> {
                    try {
                        int minTier = Integer.parseInt(input);
                        if (minTier < 1 || minTier > 10) {
                            player.sendMessage(TextUtil.colorize("§cTier must be between 1 and 10"));
                        } else {
                            finalFilterState.setMinTier(minTier);
                            session.put("filterState", finalFilterState);
                            player.sendMessage(TextUtil.colorize("§aMinimum tier set to §f" + minTier));
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(TextUtil.colorize("§cInvalid number"));
                    }
                    openGUI(guiManager, player);
                },
                () -> openGUI(guiManager, player)
            );
            return;
        }

        if (slot == MAX_TIER_SLOT) {
            // Request max tier value
            playSound(player, "click");
            final FilterState finalFilterState = filterState;
            guiManager.getInputHelper().requestText(player, "Maximum Tier", "10",
                input -> {
                    try {
                        int maxTier = Integer.parseInt(input);
                        if (maxTier < 1 || maxTier > 10) {
                            player.sendMessage(TextUtil.colorize("§cTier must be between 1 and 10"));
                        } else {
                            finalFilterState.setMaxTier(maxTier);
                            session.put("filterState", finalFilterState);
                            player.sendMessage(TextUtil.colorize("§aMaximum tier set to §f" + maxTier));
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(TextUtil.colorize("§cInvalid number"));
                    }
                    openGUI(guiManager, player);
                },
                () -> openGUI(guiManager, player)
            );
            return;
        }

        if (slot == CLEAR_SLOT) {
            // Clear tier filter
            filterState.setMinTier(null);
            filterState.setMaxTier(null);
            session.put("filterState", filterState);
            playSound(player, "click");
            player.sendMessage(TextUtil.colorize("§7Tier filter cleared"));
            refreshGUI(guiManager, player, session, filterState);
            return;
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
        GUISession session = new GUISession(GUIType.TIER_FILTER);
        session.put("filterState", filterState);
        
        // Preserve page number
        if (oldSession != null) {
            Integer page = oldSession.getInt("page", 1);
            session.put("page", page);
        }

        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§eFilter: Tier Range"));

        buildInventory(inv, filterState);

        guiManager.openGUI(player, inv, session);
    }

    private static void refreshGUI(GUIManager guiManager, Player player, GUISession session, FilterState filterState) {
        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§eFilter: Tier Range"));

        buildInventory(inv, filterState);
        guiManager.updateGUI(player, inv, session);
    }

    private static void buildInventory(Inventory inv, FilterState filterState) {
        inv.clear();

        // Min tier button
        Integer minTier = filterState.getMinTier();
        List<String> minLore = new ArrayList<>();
        if (minTier != null) {
            minLore.add("§aCurrent: §f" + minTier);
            minLore.add("§7Click to change");
        } else {
            minLore.add("§7Not set");
            minLore.add("§7Click to set");
        }
        ItemStack minItem = ItemBuilder.createItem(Material.EXPERIENCE_BOTTLE, 
            "§eMinimum Tier", minLore);
        if (minTier != null) {
            ItemBuilder.addGlow(minItem);
        }
        inv.setItem(MIN_TIER_SLOT, minItem);

        // Max tier button
        Integer maxTier = filterState.getMaxTier();
        List<String> maxLore = new ArrayList<>();
        if (maxTier != null) {
            maxLore.add("§aCurrent: §f" + maxTier);
            maxLore.add("§7Click to change");
        } else {
            maxLore.add("§7Not set");
            maxLore.add("§7Click to set");
        }
        ItemStack maxItem = ItemBuilder.createItem(Material.EXPERIENCE_BOTTLE, 
            "§eMaximum Tier", maxLore);
        if (maxTier != null) {
            ItemBuilder.addGlow(maxItem);
        }
        inv.setItem(MAX_TIER_SLOT, maxItem);

        // Clear button
        inv.setItem(CLEAR_SLOT, ItemBuilder.createItem(
            Material.BARRIER,
            "§cClear Tier Filter",
            "§7Reset min/max tier"
        ));

        // Back button
        inv.setItem(GUILayout.BACK, ItemBuilder.createItem(
            Material.RED_DYE,
            "§c← Back",
            "§7Return to sigil browser"
        ));

        // Fill background
        ItemBuilder.fillBackground(inv);
    }
}
