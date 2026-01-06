package com.miracle.arcanesigils.gui.common;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Abstract base class for paginated browser GUIs.
 * Provides standard layout with items, pagination, and back button.
 *
 * Layout (3 rows = 27 slots):
 * Row 0: [1][1][1][1][1][1][1][1][1]  -- Items (paginated)
 * Row 1: [1][1][1][1][1][1][1][1][1]
 * Row 2: [X][<][_][_][%][_][_][_][>]
 *
 * Where:
 * 1 = Item slots (0-17) - 18 items per page
 * X = Back button (slot 18)
 * < = Previous page (slot 19)
 * % = Page indicator (slot 22)
 * > = Next page (slot 26)
 */
public abstract class AbstractBrowserHandler<T> extends AbstractHandler {

    // Use centralized constants from GUILayout.Browser
    protected static final int[] ITEM_SLOTS = GUILayout.Browser.ITEM_SLOTS;
    protected static final int SLOT_BACK = GUILayout.Browser.BACK;
    protected static final int SLOT_PREV_PAGE = GUILayout.Browser.PREV_PAGE;
    protected static final int SLOT_PAGE_INFO = GUILayout.Browser.PAGE_INDICATOR;
    protected static final int SLOT_NEXT_PAGE = GUILayout.Browser.NEXT_PAGE;
    protected static final int ITEMS_PER_PAGE = GUILayout.Browser.ITEMS_PER_PAGE;

    protected AbstractBrowserHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        int page = session.getInt("page", 1);

        switch (slot) {
            case SLOT_BACK -> handleBack(player, session);
            case SLOT_PREV_PAGE -> handlePageChange(player, session, page - 1);
            case SLOT_NEXT_PAGE -> handlePageChange(player, session, page + 1);
            default -> {
                int itemIndex = getItemIndex(slot);
                if (itemIndex >= 0) {
                    handleItemSelection(player, session, itemIndex, page);
                } else {
                    playSound(player, "click");
                }
            }
        }
    }

    /**
     * Handle back button - subclasses implement navigation.
     */
    protected abstract void handleBack(Player player, GUISession session);

    /**
     * Handle item selection - subclasses implement selection logic.
     * @param itemIndex Index within current page (0-17)
     * @param page Current page number
     */
    protected abstract void handleItemSelection(Player player, GUISession session, int itemIndex, int page);

    /**
     * Get all items for the browser.
     */
    protected abstract List<T> getItems();

    /**
     * Build an ItemStack for display.
     */
    protected abstract ItemStack buildItemDisplay(T item);

    /**
     * Get the GUI title.
     */
    protected abstract String getTitle();

    /**
     * Get the GUIType for this browser.
     */
    protected abstract GUIType getGUIType();

    /**
     * Get the back button label.
     */
    protected String getBackButtonLabel() {
        return "Previous Menu";
    }

    /**
     * Handle page change.
     */
    protected void handlePageChange(Player player, GUISession session, int newPage) {
        List<T> items = getItems();
        int maxPage = (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);

        if (newPage < 1 || newPage > maxPage) {
            playSound(player, "error");
            return;
        }

        playSound(player, "page");
        // Update page in session and refresh without reopening
        session.put("page", newPage);
        refreshGUI(player, session, newPage);
    }

    /**
     * Refresh GUI items in place without reopening (preserves cursor position).
     */
    protected void refreshGUI(Player player, GUISession session, int page) {
        List<T> items = getItems();
        int maxPage = Math.max(1, (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(null, 27, net.kyori.adventure.text.Component.text("Browser"));

        // Fill decoration slots
        for (int slot : GUILayout.Browser.DECORATION_SLOTS) {
            inv.setItem(slot, ItemBuilder.createBackground());
        }

        // Populate items for this page
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());

        for (int i = startIndex; i < endIndex; i++) {
            T item = items.get(i);
            int slotIndex = i - startIndex;
            if (slotIndex < ITEM_SLOTS.length) {
                inv.setItem(ITEM_SLOTS[slotIndex], buildItemDisplay(item));
            }
        }

        // Navigation buttons
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton(getBackButtonLabel()));
        inv.setItem(SLOT_PREV_PAGE, ItemBuilder.createPageArrow(false, page, maxPage));
        inv.setItem(SLOT_NEXT_PAGE, ItemBuilder.createPageArrow(true, page, maxPage));
        inv.setItem(SLOT_PAGE_INFO, ItemBuilder.createPageIndicator(page, maxPage, items.size()));

        guiManager.updateGUI(player, inv, session);
    }

    /**
     * Get item index from slot position.
     */
    protected int getItemIndex(int slot) {
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Calculate actual index from page and slot index.
     */
    protected int getActualIndex(int itemIndex, int page) {
        return (page - 1) * ITEMS_PER_PAGE + itemIndex;
    }

    /**
     * Open the browser GUI.
     */
    public void openGUI(Player player, GUISession parentSession, int page) {
        List<T> items = getItems();
        int maxPage = Math.max(1, (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent(getTitle()));

        // Fill decoration slots (bottom row except buttons)
        for (int slot : GUILayout.Browser.DECORATION_SLOTS) {
            inv.setItem(slot, ItemBuilder.createBackground());
        }

        // Populate items for this page
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());

        for (int i = startIndex; i < endIndex; i++) {
            T item = items.get(i);
            int slotIndex = i - startIndex;
            if (slotIndex < ITEM_SLOTS.length) {
                inv.setItem(ITEM_SLOTS[slotIndex], buildItemDisplay(item));
            }
        }

        // Navigation buttons
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton(getBackButtonLabel()));
        inv.setItem(SLOT_PREV_PAGE, ItemBuilder.createPageArrow(false, page, maxPage));
        inv.setItem(SLOT_NEXT_PAGE, ItemBuilder.createPageArrow(true, page, maxPage));
        inv.setItem(SLOT_PAGE_INFO, ItemBuilder.createPageIndicator(page, maxPage, items.size()));

        // Create session preserving parent data
        GUISession session = new GUISession(getGUIType());
        if (parentSession != null) {
            parentSession.getData().forEach(session::put);
        }
        session.put("page", page);

        guiManager.openGUI(player, inv, session);
    }

    /**
     * Convenience method to open at page 1.
     */
    public void openGUI(Player player, GUISession parentSession) {
        openGUI(player, parentSession, 1);
    }

    /**
     * Data class for simple browser options.
     */
    public static class BrowserOption {
        public final String id;
        public final String displayName;
        public final Material material;
        public final String description;

        public BrowserOption(String id, String displayName, Material material, String description) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
            this.description = description;
        }

        public BrowserOption(String id, String displayName, Material material) {
            this(id, displayName, material, null);
        }
    }
}
