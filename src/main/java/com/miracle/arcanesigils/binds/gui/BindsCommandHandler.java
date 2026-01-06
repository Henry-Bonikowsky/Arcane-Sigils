package com.miracle.arcanesigils.binds.gui;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.binds.*;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Handler for BINDS_COMMAND GUI type.
 * Manages the command binds configuration interface for players.
 * Supports pagination for up to 27 binds across 2 pages (18 binds per page).
 */
public class BindsCommandHandler extends AbstractHandler {

    // Double-click confirmation window in milliseconds
    private static final long DOUBLE_CLICK_WINDOW = 3000L;

    // Pagination constants
    private static final int BINDS_PER_PAGE = 18; // Slots 0-17 (rows 0-1)
    private static final int MAX_BINDS = 27; // Maximum binds across 2 pages
    private static final int TOTAL_PAGES = 2;

    public BindsCommandHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        BindsManager bindsManager = plugin.getBindsManager();
        PlayerBindData playerData = bindsManager.getPlayerData(player);
        BindPreset currentBinds = playerData.getCommandBinds();

        // Get current page from session (default to 0)
        int currentPage = session.getInt("page", 0);

        // Bind slots (slots 0-17) - only respond if slot has a bind (not AIR)
        if (slot >= BindsGUIHelper.CMD_BIND_START_SLOT && slot < BindsGUIHelper.CMD_BIND_START_SLOT + BINDS_PER_PAGE) {
            // Check if the clicked slot actually has an item (bind exists)
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) {
                // Empty slot - do nothing
                return;
            }

            int bindNumber = (currentPage * BINDS_PER_PAGE) + slot + 1;
            if (bindNumber <= MAX_BINDS) {
                handleBindSlotClick(player, session, playerData, currentBinds, bindNumber, event, currentPage);
            }
            return;
        }

        // Clear Binds (slot 18)
        if (slot == BindsGUIHelper.CMD_CLEAR_SLOT) {
            handleClearBinds(player, session, playerData, currentBinds, currentPage);
            return;
        }

        // Add Bind (slot 19)
        if (slot == BindsGUIHelper.CMD_ADD_SLOT) {
            handleAddBind(player, session, playerData, currentBinds, currentPage);
            return;
        }

        // Delete Bind (slot 20)
        if (slot == BindsGUIHelper.CMD_DELETE_SLOT) {
            handleDeleteBind(player, session, playerData, currentBinds, currentPage);
            return;
        }

        // Preset buttons (slots 23-26)
        if (slot >= BindsGUIHelper.CMD_PRESET_START_SLOT &&
            slot < BindsGUIHelper.CMD_PRESET_START_SLOT + 4) {
            int presetIndex = slot - BindsGUIHelper.CMD_PRESET_START_SLOT;
            handlePresetClick(player, session, playerData, presetIndex, event, currentPage);
            return;
        }

        // Page Left (slot 27)
        if (slot == BindsGUIHelper.CMD_PAGE_LEFT_SLOT) {
            handlePageChange(player, session, currentPage - 1);
            return;
        }

        // Toggle Selection (slot 28)
        if (slot == BindsGUIHelper.CMD_TOGGLE_SLOT) {
            handleToggleSelection(player, session, playerData, event);
            return;
        }

        // Information (slot 31) - no action
        if (slot == BindsGUIHelper.CMD_INFO_SLOT) {
            playSound(player, "click");
            return;
        }

        // Switch System (slot 34)
        if (slot == BindsGUIHelper.CMD_SWITCH_SLOT) {
            handleSwitchSystem(player, bindsManager);
            return;
        }

        // Page Right (slot 35)
        if (slot == BindsGUIHelper.CMD_PAGE_RIGHT_SLOT) {
            handlePageChange(player, session, currentPage + 1);
            return;
        }
    }

    /**
     * Handle Clear Binds button click.
     * Requires double-click confirmation.
     */
    private void handleClearBinds(Player player, GUISession session, PlayerBindData playerData,
                                   BindPreset currentBinds, int currentPage) {
        Long lastClickTime = session.get("clearConfirmTime", Long.class);
        long now = System.currentTimeMillis();

        if (lastClickTime != null && (now - lastClickTime) < DOUBLE_CLICK_WINDOW) {
            // Confirmed - clear all binds
            currentBinds.clearAll();
            session.remove("clearConfirmTime");
            player.sendMessage(TextUtil.colorize("§aAll command binds cleared."));
            playSound(player, "unsocket");
            openCommandBindsGUI(player, currentPage);
        } else {
            // First click - set confirmation timer
            session.put("clearConfirmTime", now);
            playSound(player, "click");
            openCommandBindsGUI(player, currentPage); // Refresh to show confirm state
        }
    }

    /**
     * Handle Add Bind button click.
     * Creates the next bind in the list and refreshes the GUI.
     */
    private void handleAddBind(Player player, GUISession session, PlayerBindData playerData,
                                BindPreset currentBinds, int currentPage) {
        // Find the highest bind ID
        int highestId = 0;
        for (int id : currentBinds.getBinds().keySet()) {
            if (id > highestId) {
                highestId = id;
            }
        }

        int nextId = highestId + 1;

        // Check if we've reached the maximum
        if (nextId > MAX_BINDS) {
            player.sendMessage(TextUtil.colorize("§cMaximum bind limit reached (27 binds)."));
            playSound(player, "error");
            return;
        }

        // Create an empty bind for this slot
        currentBinds.setBind(nextId, new java.util.ArrayList<>());

        player.sendMessage(TextUtil.colorize("§aCreated bind #" + nextId + "."));
        playSound(player, "socket");

        // Calculate which page the new bind is on and navigate there
        int newBindPage = (nextId - 1) / BINDS_PER_PAGE;
        openCommandBindsGUI(player, newBindPage);
    }

    /**
     * Handle Delete Bind button click.
     * Deletes the highest ID bind.
     */
    private void handleDeleteBind(Player player, GUISession session, PlayerBindData playerData,
                                   BindPreset currentBinds, int currentPage) {
        // Find the highest bind ID
        int highestId = 0;
        for (int id : currentBinds.getBinds().keySet()) {
            if (id > highestId) {
                highestId = id;
            }
        }

        if (highestId == 0) {
            player.sendMessage(TextUtil.colorize("§cNo binds to delete."));
            playSound(player, "error");
            return;
        }

        currentBinds.clearBind(highestId);
        player.sendMessage(TextUtil.colorize("§cDeleted bind #" + highestId + "."));
        playSound(player, "unsocket");
        openCommandBindsGUI(player, currentPage);
    }

    /**
     * Handle preset button click.
     * Shift+Left = Save, Left = Load, Right = Delete
     */
    private void handlePresetClick(Player player, GUISession session, PlayerBindData playerData,
                                    int presetIndex, InventoryClickEvent event, int currentPage) {
        if (event.isShiftClick() && event.isLeftClick()) {
            // Save to preset
            playerData.saveToPreset(presetIndex);
            player.sendMessage(TextUtil.colorize("§aSaved current binds to Preset " + (presetIndex + 1) + "."));
            playSound(player, "socket");
            openCommandBindsGUI(player, currentPage);
        } else if (event.isLeftClick()) {
            // Load preset
            if (playerData.hasPreset(presetIndex)) {
                playerData.loadFromPreset(presetIndex);
                player.sendMessage(TextUtil.colorize("§aLoaded binds from Preset " + (presetIndex + 1) + "."));
                playSound(player, "click");
                openCommandBindsGUI(player, currentPage);
            } else {
                player.sendMessage(TextUtil.colorize("§cPreset " + (presetIndex + 1) + " is empty."));
                playSound(player, "error");
            }
        } else if (event.isRightClick()) {
            // Delete preset
            if (playerData.hasPreset(presetIndex)) {
                playerData.deletePreset(presetIndex);
                player.sendMessage(TextUtil.colorize("§cDeleted Preset " + (presetIndex + 1) + "."));
                playSound(player, "unsocket");
                openCommandBindsGUI(player, currentPage);
            } else {
                player.sendMessage(TextUtil.colorize("§cPreset " + (presetIndex + 1) + " is already empty."));
                playSound(player, "error");
            }
        }
    }

    /**
     * Handle bind slot click.
     * Left = Open editor, Right = Clear bind
     */
    private void handleBindSlotClick(Player player, GUISession session, PlayerBindData playerData,
                                      BindPreset currentBinds, int bindNumber, InventoryClickEvent event,
                                      int currentPage) {
        if (event.isLeftClick()) {
            // Open BINDS_EDITOR for this slot
            playSound(player, "click");
            BindsEditorHandler editorHandler = new BindsEditorHandler(plugin, guiManager);
            editorHandler.openBindsEditor(player, bindNumber, GUIType.BINDS_COMMAND);
        } else if (event.isRightClick()) {
            // Clear this bind
            List<String> currentSigils = currentBinds.getBind(bindNumber);
            if (currentSigils != null && !currentSigils.isEmpty()) {
                currentBinds.clearBind(bindNumber);
                player.sendMessage(TextUtil.colorize("§cCleared bind #" + bindNumber + "."));
                playSound(player, "unsocket");
                openCommandBindsGUI(player, currentPage);
            } else {
                player.sendMessage(TextUtil.colorize("§cBind #" + bindNumber + " is already empty."));
                playSound(player, "error");
            }
        }
    }

    /**
     * Handle Toggle Selection button click.
     * Click = cycle hotkey, Shift+Click = display in chat
     */
    private void handleToggleSelection(Player player, GUISession session, PlayerBindData playerData, InventoryClickEvent event) {
        if (event.isShiftClick()) {
            // Display current hotkey in chat
            ToggleHotkey current = playerData.getToggleHotkey();
            player.sendMessage(TextUtil.colorize("§aCurrent toggle hotkey: §f" + current.getDisplayName()));
            playSound(player, "click");
        } else {
            // Cycle to next hotkey
            ToggleHotkey current = playerData.getToggleHotkey();
            ToggleHotkey next = current.next();
            playerData.setToggleHotkey(next);
            player.sendMessage(TextUtil.colorize("§aToggle hotkey changed to: §f" + next.getDisplayName()));
            playSound(player, "click");
            // Get current page and reopen
            int currentPage = session.getInt("page", 0);
            openCommandBindsGUI(player, currentPage);
        }
    }

    /**
     * Handle page change navigation.
     */
    private void handlePageChange(Player player, GUISession session, int newPage) {
        // Check if pagination is enabled
        boolean needsPagination = session.getBooleanOpt("needsPagination");
        if (!needsPagination) {
            // No pagination, ignore click
            return;
        }

        // Clamp page to valid range
        if (newPage < 0) newPage = 0;
        if (newPage >= TOTAL_PAGES) newPage = TOTAL_PAGES - 1;

        playSound(player, "click");
        openCommandBindsGUI(player, newPage);
    }

    /**
     * Handle Switch System button click.
     * Switches between HOTBAR and COMMAND systems.
     */
    private void handleSwitchSystem(Player player, BindsManager bindsManager) {
        bindsManager.switchSystem(player.getUniqueId());
        PlayerBindData playerData = bindsManager.getPlayerData(player);
        BindSystem newSystem = playerData.getActiveSystem();

        player.sendMessage(TextUtil.colorize("§aSwitched to " + newSystem.getDisplayName() + " binds system."));
        playSound(player, "click");

        // Open the appropriate GUI for the new system
        if (newSystem == BindSystem.HOTBAR) {
            // Get the hotbar handler and open hotbar GUI
            BindsHotbarHandler hotbarHandler = new BindsHotbarHandler(plugin, guiManager);
            hotbarHandler.openHotbarBindsGUI(player);
        } else {
            openCommandBindsGUI(player, 0);
        }
    }

    /**
     * Open the Command Binds GUI for a player at the specified page.
     *
     * @param player The player to open the GUI for
     * @param page   The page number (0-indexed)
     */
    public void openCommandBindsGUI(Player player, int page) {
        BindsManager bindsManager = plugin.getBindsManager();
        PlayerBindData playerData = bindsManager.getPlayerData(player);
        BindPreset currentBinds = playerData.getCommandBinds();

        // Count total binds to determine if pagination is needed
        int totalBinds = currentBinds.getBinds().size();
        boolean needsPagination = totalBinds > BINDS_PER_PAGE;
        int totalPages = needsPagination ? TOTAL_PAGES : 1;

        // Clamp page to valid range
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        // Create inventory (4 rows = 36 slots)
        Inventory inv = Bukkit.createInventory(null, 36, TextUtil.colorize("§6&lCommand Binds"));

        // Check if in confirm mode for Clear Binds
        GUISession existingSession = plugin.getGuiManager().getSession(player);
        boolean confirmMode = false;
        if (existingSession != null && existingSession.has("clearConfirmTime")) {
            Long lastClickTime = existingSession.get("clearConfirmTime", Long.class);
            if (lastClickTime != null && (System.currentTimeMillis() - lastClickTime) < DOUBLE_CLICK_WINDOW) {
                confirmMode = true;
            }
        }

        // Row 0-1 (slots 0-17): Bind slots - only show existing binds, leave empty slots as AIR
        int startBindId = (page * BINDS_PER_PAGE) + 1;
        for (int i = 0; i < BINDS_PER_PAGE; i++) {
            int bindNumber = startBindId + i;
            int slot = BindsGUIHelper.CMD_BIND_START_SLOT + i;

            // Only show bind item if this bind exists in the map (even if empty)
            if (currentBinds.getBinds().containsKey(bindNumber)) {
                List<String> boundSigils = currentBinds.getBind(bindNumber);
                // For command mode, keybind display is just the ID
                String keybindDisplay = String.valueOf(bindNumber);
                // Use new method that shows actual socketed item
                inv.setItem(slot, BindsGUIHelper.createBindItemWithSocketedItem(
                        plugin, player, bindNumber, boundSigils, keybindDisplay, true));
            }
            // Otherwise leave as AIR (null/empty)
        }

        // Row 2 (slots 18-26): Clear Binds, Add Bind, Delete Bind, empty, Presets
        inv.setItem(BindsGUIHelper.CMD_CLEAR_SLOT, BindsGUIHelper.createClearBindsItem(confirmMode));
        inv.setItem(BindsGUIHelper.CMD_ADD_SLOT, BindsGUIHelper.createAddBindItem());
        inv.setItem(BindsGUIHelper.CMD_DELETE_SLOT, BindsGUIHelper.createDeleteBindItem());
        inv.setItem(21, BindsGUIHelper.createFillerItem());
        inv.setItem(22, BindsGUIHelper.createFillerItem());

        for (int i = 0; i < 4; i++) {
            int slot = BindsGUIHelper.CMD_PRESET_START_SLOT + i;
            boolean hasPreset = playerData.hasPreset(i);
            int totalAbilities = hasPreset ? playerData.getCommandPresets()[i].getTotalAbilitiesBound() : 0;
            inv.setItem(slot, BindsGUIHelper.createPresetItem(i, hasPreset, totalAbilities));
        }

        // Row 3 (slots 27-35): Page arrows (only if needed), Toggle Selection, Info, Switch System
        if (needsPagination) {
            inv.setItem(BindsGUIHelper.CMD_PAGE_LEFT_SLOT,
                        BindsGUIHelper.createPageArrow(true, page, totalPages));
            inv.setItem(BindsGUIHelper.CMD_PAGE_RIGHT_SLOT,
                        BindsGUIHelper.createPageArrow(false, page, totalPages));
        } else {
            // No pagination needed - leave slots empty (AIR)
        }

        inv.setItem(BindsGUIHelper.CMD_TOGGLE_SLOT,
                    BindsGUIHelper.createToggleSelectionItem(playerData.getToggleHotkey()));
        inv.setItem(29, BindsGUIHelper.createFillerItem());
        inv.setItem(30, BindsGUIHelper.createFillerItem());
        inv.setItem(BindsGUIHelper.CMD_INFO_SLOT,
                    BindsGUIHelper.createInfoItem(false));
        inv.setItem(32, BindsGUIHelper.createFillerItem());
        inv.setItem(33, BindsGUIHelper.createFillerItem());
        inv.setItem(BindsGUIHelper.CMD_SWITCH_SLOT,
                    BindsGUIHelper.createSwitchSystemItem(BindSystem.COMMAND));

        // Create session
        GUISession session = new GUISession(GUIType.BINDS_COMMAND);
        session.put("page", page);
        session.put("needsPagination", needsPagination);
        if (confirmMode && existingSession != null) {
            session.put("clearConfirmTime", existingSession.get("clearConfirmTime"));
        }

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }
}
