package com.zenax.armorsets.binds.gui;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.binds.*;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Handler for BINDS_HOTBAR GUI type.
 * Manages the hotbar binds configuration interface for players.
 */
public class BindsHotbarHandler extends AbstractHandler {

    // Double-click confirmation window in milliseconds
    private static final long DOUBLE_CLICK_WINDOW = 3000L;

    public BindsHotbarHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        BindsManager bindsManager = plugin.getBindsManager();
        PlayerBindData playerData = bindsManager.getPlayerData(player);
        BindPreset currentBinds = playerData.getHotbarBinds();

        // Clear Binds (slot 0)
        if (slot == BindsGUIHelper.HOTBAR_CLEAR_BINDS_SLOT) {
            handleClearBinds(player, session, playerData, currentBinds);
            return;
        }

        // Preset buttons (slots 5-8)
        if (slot >= BindsGUIHelper.HOTBAR_PRESET_START_SLOT &&
            slot < BindsGUIHelper.HOTBAR_PRESET_START_SLOT + 4) {
            int presetIndex = slot - BindsGUIHelper.HOTBAR_PRESET_START_SLOT;
            handlePresetClick(player, session, playerData, presetIndex, event);
            return;
        }

        // Bind slots (slots 9-17)
        if (slot >= BindsGUIHelper.HOTBAR_BIND_START_SLOT &&
            slot < BindsGUIHelper.HOTBAR_BIND_START_SLOT + 9) {
            int bindNumber = slot - BindsGUIHelper.HOTBAR_BIND_START_SLOT + 1;
            handleBindSlotClick(player, session, playerData, currentBinds, bindNumber, event);
            return;
        }

        // Toggle Selection (slot 18)
        if (slot == BindsGUIHelper.HOTBAR_TOGGLE_SLOT) {
            handleToggleSelection(player, session, playerData, event);
            return;
        }

        // Held Slot (slot 19)
        if (slot == BindsGUIHelper.HOTBAR_HELD_SLOT_SLOT) {
            handleHeldSlot(player, session, playerData, event);
            return;
        }

        // Information (slot 22) - no action
        if (slot == BindsGUIHelper.HOTBAR_INFO_SLOT) {
            playSound(player, "click");
            return;
        }

        // Switch System (slot 25)
        if (slot == BindsGUIHelper.HOTBAR_SWITCH_SLOT) {
            handleSwitchSystem(player, bindsManager);
            return;
        }
    }

    /**
     * Handle Clear Binds button click.
     * Requires double-click confirmation.
     */
    private void handleClearBinds(Player player, GUISession session, PlayerBindData playerData, BindPreset currentBinds) {
        Long lastClickTime = session.get("clearConfirmTime", Long.class);
        long now = System.currentTimeMillis();

        if (lastClickTime != null && (now - lastClickTime) < DOUBLE_CLICK_WINDOW) {
            // Confirmed - clear all binds
            currentBinds.clearAll();
            session.remove("clearConfirmTime");
            player.sendMessage(TextUtil.colorize("§aAll hotbar binds cleared."));
            playSound(player, "unsocket");
            openHotbarBindsGUI(player);
        } else {
            // First click - set confirmation timer
            session.put("clearConfirmTime", now);
            playSound(player, "click");
            openHotbarBindsGUI(player); // Refresh to show confirm state
        }
    }

    /**
     * Handle preset button click.
     * Shift+Left = Save, Left = Load, Right = Delete
     */
    private void handlePresetClick(Player player, GUISession session, PlayerBindData playerData, int presetIndex, InventoryClickEvent event) {
        if (event.isShiftClick() && event.isLeftClick()) {
            // Save to preset
            playerData.saveToPreset(presetIndex);
            player.sendMessage(TextUtil.colorize("§aSaved current binds to Preset " + (presetIndex + 1) + "."));
            playSound(player, "socket");
            openHotbarBindsGUI(player);
        } else if (event.isLeftClick()) {
            // Load preset
            if (playerData.hasPreset(presetIndex)) {
                playerData.loadFromPreset(presetIndex);
                player.sendMessage(TextUtil.colorize("§aLoaded binds from Preset " + (presetIndex + 1) + "."));
                playSound(player, "click");
                openHotbarBindsGUI(player);
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
                openHotbarBindsGUI(player);
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
                                      BindPreset currentBinds, int bindNumber, InventoryClickEvent event) {
        if (event.isLeftClick()) {
            // Open BINDS_EDITOR for this slot
            BindsEditorHandler editorHandler = new BindsEditorHandler(plugin, guiManager);
            editorHandler.openBindsEditor(player, bindNumber, GUIType.BINDS_HOTBAR);
            playSound(player, "click");
        } else if (event.isRightClick()) {
            // Clear this bind
            List<String> currentSigils = currentBinds.getBind(bindNumber);
            if (currentSigils != null && !currentSigils.isEmpty()) {
                currentBinds.clearBind(bindNumber);
                player.sendMessage(TextUtil.colorize("§cCleared bind slot " + bindNumber + "."));
                playSound(player, "unsocket");
                openHotbarBindsGUI(player);
            } else {
                player.sendMessage(TextUtil.colorize("§cBind slot " + bindNumber + " is already empty."));
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
            openHotbarBindsGUI(player);
        }
    }

    /**
     * Handle Held Slot button click.
     * Click = cycle hotkey, Shift+Click = display in chat
     */
    private void handleHeldSlot(Player player, GUISession session, PlayerBindData playerData, InventoryClickEvent event) {
        if (event.isShiftClick()) {
            // Display current hotkey in chat
            HeldSlotHotkey current = playerData.getHeldSlotHotkey();
            player.sendMessage(TextUtil.colorize("§aCurrent held slot hotkey: §f" + current.getDisplayName()));
            playSound(player, "click");
        } else {
            // Cycle to next hotkey
            HeldSlotHotkey current = playerData.getHeldSlotHotkey();
            HeldSlotHotkey next = current.next();
            playerData.setHeldSlotHotkey(next);
            player.sendMessage(TextUtil.colorize("§aHeld slot hotkey changed to: §f" + next.getDisplayName()));
            playSound(player, "click");
            openHotbarBindsGUI(player);
        }
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
            openHotbarBindsGUI(player);
        } else {
            BindsCommandHandler commandHandler = new BindsCommandHandler(plugin, guiManager);
            commandHandler.openCommandBindsGUI(player, 0);
        }
    }

    /**
     * Open the Hotbar Binds GUI for a player.
     */
    public void openHotbarBindsGUI(Player player) {
        BindsManager bindsManager = plugin.getBindsManager();
        PlayerBindData playerData = bindsManager.getPlayerData(player);
        BindPreset currentBinds = playerData.getHotbarBinds();

        // Create inventory
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.colorize("§6&lHotbar Binds"));

        // Check if in confirm mode for Clear Binds
        GUISession existingSession = plugin.getGuiManager().getSession(player);
        boolean confirmMode = false;
        if (existingSession != null && existingSession.has("clearConfirmTime")) {
            Long lastClickTime = existingSession.get("clearConfirmTime", Long.class);
            if (lastClickTime != null && (System.currentTimeMillis() - lastClickTime) < DOUBLE_CLICK_WINDOW) {
                confirmMode = true;
            }
        }

        // Row 0: Clear Binds, empty slots, 4 Presets
        inv.setItem(BindsGUIHelper.HOTBAR_CLEAR_BINDS_SLOT, BindsGUIHelper.createClearBindsItem(confirmMode));

        for (int i = 1; i < 5; i++) {
            inv.setItem(i, BindsGUIHelper.createFillerItem());
        }

        for (int i = 0; i < 4; i++) {
            int slot = BindsGUIHelper.HOTBAR_PRESET_START_SLOT + i;
            boolean hasPreset = playerData.hasPreset(i);
            int totalAbilities = hasPreset ? playerData.getHotbarPresets()[i].getTotalAbilitiesBound() : 0;
            inv.setItem(slot, BindsGUIHelper.createPresetItem(i, hasPreset, totalAbilities));
        }

        // Row 1: 9 Bind slots (keys 1-9)
        for (int i = 0; i < 9; i++) {
            int bindNumber = i + 1;
            int slot = BindsGUIHelper.HOTBAR_BIND_START_SLOT + i;
            List<String> boundSigils = currentBinds.getBind(bindNumber);

            // Get keybind display (1-9)
            String keybindDisplay = String.valueOf(bindNumber);

            // Use new method that shows actual socketed item
            inv.setItem(slot, BindsGUIHelper.createBindItemWithSocketedItem(
                    plugin, player, bindNumber, boundSigils, keybindDisplay, false));
        }

        // Row 2: Toggle Selection, Held Slot, empty, Info, empty, Switch System, empty
        inv.setItem(BindsGUIHelper.HOTBAR_TOGGLE_SLOT,
                    BindsGUIHelper.createToggleSelectionItem(playerData.getToggleHotkey()));
        inv.setItem(BindsGUIHelper.HOTBAR_HELD_SLOT_SLOT,
                    BindsGUIHelper.createHeldSlotItem(playerData.getHeldSlotHotkey()));
        inv.setItem(20, BindsGUIHelper.createFillerItem());
        inv.setItem(21, BindsGUIHelper.createFillerItem());
        inv.setItem(BindsGUIHelper.HOTBAR_INFO_SLOT,
                    BindsGUIHelper.createInfoItem(true));
        inv.setItem(23, BindsGUIHelper.createFillerItem());
        inv.setItem(24, BindsGUIHelper.createFillerItem());
        inv.setItem(BindsGUIHelper.HOTBAR_SWITCH_SLOT,
                    BindsGUIHelper.createSwitchSystemItem(BindSystem.HOTBAR));
        inv.setItem(26, BindsGUIHelper.createFillerItem());

        // Create session
        GUISession session = new GUISession(GUIType.BINDS_HOTBAR);
        if (confirmMode && existingSession != null) {
            session.put("clearConfirmTime", existingSession.get("clearConfirmTime"));
        }

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }
}
