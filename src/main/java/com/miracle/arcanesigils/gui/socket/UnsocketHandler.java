package com.miracle.arcanesigils.gui.socket;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.RarityUtil;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.core.SocketManager;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for the UNSOCKET GUI.
 * Allows players to remove a socketed sigil from equipment.
 */
public class UnsocketHandler extends AbstractHandler {

    // Slot positions
    private static final int ITEM_SLOT = 10;  // Row 1, column 1
    private static final int BACK_SLOT = 18;
    // Sigils displayed on columns 3-8 across all rows (up to 18 sigils)
    private static final int[] SIGIL_DISPLAY_SLOTS = {
        3, 4, 5, 6, 7, 8,       // Row 0
        12, 13, 14, 15, 16, 17, // Row 1
        21, 22, 23, 24, 25, 26  // Row 2
    };

    public UnsocketHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        openGUI(guiManager, player);
    }

    @Override
    public void handleClose(Player player, GUISession session, InventoryCloseEvent event) {
        // Return item to player when GUI is closed by any means
        Inventory inv = event.getInventory();
        returnItemToPlayer(player, inv);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = event.getInventory();

        // Handle shift-click from player inventory (bottom inventory)
        if (event.isShiftClick() && slot >= inv.getSize()) {
            // Player shift-clicked in their inventory - try to move item to ITEM_SLOT
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && !clickedItem.getType().isAir()) {
                ItemStack currentInSlot = inv.getItem(ITEM_SLOT);
                if (currentInSlot == null || currentInSlot.getType().isAir()) {
                    // ITEM_SLOT is empty - move the item there
                    inv.setItem(ITEM_SLOT, clickedItem.clone());
                    event.setCurrentItem(null);
                    event.setCancelled(true);
                    // Refresh display
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.getOpenInventory().getTopInventory().equals(inv)) {
                            refreshSigilDisplay(player, inv);
                        }
                    });
                    return;
                }
            }
            event.setCancelled(true);
            return;
        }

        // Handle item placement/retrieval (don't cancel event)
        if (slot == ITEM_SLOT) {
            event.setCancelled(false);
            // Schedule a refresh after the item is placed/removed
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().equals(inv)) {
                    refreshSigilDisplay(player, inv);
                }
            });
            return;
        }

        // Handle action buttons
        if (slot == BACK_SLOT) {
            // Item will be returned by handleClose when inventory closes
            player.closeInventory();
            playSound(player, "close");
            return;
        }

        // Handle sigil slot clicks
        if (isSigilDisplaySlot(slot)) {
            ItemStack equipmentItem = inv.getItem(ITEM_SLOT);

            if (equipmentItem == null || equipmentItem.getType().isAir()) {
                player.sendMessage(TextUtil.colorize("§cNo equipment in slot!"));
                playSound(player, "error");
                return;
            }

            // Get the sigil at this slot
            List<Sigil> socketedSigils = plugin.getSocketManager().getSocketedSigils(equipmentItem);
            int sigilIndex = getSigilIndex(slot);

            if (sigilIndex >= socketedSigils.size()) {
                player.sendMessage(TextUtil.colorize("§cNo sigil in this slot!"));
                playSound(player, "error");
                return;
            }

            Sigil sigilToRemove = socketedSigils.get(sigilIndex);

            // Check if sigil is exclusive
            if (sigilToRemove.isExclusive()) {
                player.sendMessage(TextUtil.colorize("§c&lExclusive! §fThis sigil cannot be removed!"));
                playSound(player, "error");
                return;
            }

            // Request confirmation
            String sigilId = sigilToRemove.getId();
            requestConfirmation(player, inv, sigilId, sigilToRemove.getName());
        }
    }

    /**
     * Request confirmation before unsocketing.
     */
    private void requestConfirmation(Player player, Inventory inv, String sigilId, String sigilName) {
        // Create confirmation GUI overlay
        player.sendMessage(TextUtil.colorize("§eAre you sure you want to unsocket §f" + sigilName + "§e?"));
        player.sendMessage(TextUtil.colorize("§7Click the sigil again to confirm, or click cancel."));

        // Store pending removal in session
        GUISession session = guiManager.getSession(player);
        if (session != null) {
            String pendingId = session.get("pending_removal", String.class);

            if (sigilId.equals(pendingId)) {
                // Second click - confirm removal
                performUnsocket(player, inv, sigilId);
                session.remove("pending_removal");
            } else {
                // First click - mark as pending
                session.put("pending_removal", sigilId);
                playSound(player, "click");
            }
        }
    }

    /**
     * Perform the unsocket operation.
     */
    private void performUnsocket(Player player, Inventory inv, String sigilId) {
        ItemStack equipmentItem = inv.getItem(ITEM_SLOT);

        if (equipmentItem == null || equipmentItem.getType().isAir()) {
            player.sendMessage(TextUtil.colorize("§cNo equipment in slot!"));
            playSound(player, "error");
            return;
        }

        // Unsocket the sigil
        Sigil removedSigil = plugin.getSocketManager().unsocketSigilById(player, equipmentItem, sigilId);

        if (removedSigil == null) {
            player.sendMessage(TextUtil.colorize("§cFailed to unsocket sigil! It may be exclusive."));
            playSound(player, "error");
            return;
        }

        // Give sigil shard to player
        ItemStack sigilShard = plugin.getSigilManager().createSigilItem(removedSigil);
        player.getInventory().addItem(sigilShard);

        // Update equipment in slot
        inv.setItem(ITEM_SLOT, equipmentItem);

        player.sendMessage(TextUtil.colorize("§a&lUnsocketed! §f" + removedSigil.getName() + " §ahas been removed!"));
        playSound(player, "success");

        // Refresh display
        refreshSigilDisplay(player, inv);
    }

    /**
     * Open the Unsocket GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player) {
        // Create inventory
        Inventory inv = Bukkit.createInventory(null, 27,
            TextUtil.parseComponent("§8Unsocket Sigil"));

        // Fill with background
        ItemBuilder.fillBackground(inv);

        // Set up slots
        setupSlots(inv);

        // Create session
        GUISession session = new GUISession(GUIType.UNSOCKET);

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Set up the GUI slots.
     */
    private static void setupSlots(Inventory inv) {
        // Clear item slot
        inv.setItem(ITEM_SLOT, null);

        // Back button
        inv.setItem(BACK_SLOT, ItemBuilder.createItem(
            Material.RED_DYE,
            "§c✗ Cancel",
            "§7Return item and close"
        ));

        // Clear sigil display slots
        for (int slot : SIGIL_DISPLAY_SLOTS) {
            inv.setItem(slot, ItemBuilder.createItem(
                Material.GRAY_STAINED_GLASS_PANE,
                "§7Empty Slot",
                "§7Place equipment above"
            ));
        }
    }

    /**
     * Refresh the sigil display based on the equipped item.
     */
    private void refreshSigilDisplay(Player player, Inventory inv) {
        ItemStack equipmentItem = inv.getItem(ITEM_SLOT);

        // Clear sigil slots first
        for (int slot : SIGIL_DISPLAY_SLOTS) {
            inv.setItem(slot, ItemBuilder.createItem(
                Material.GRAY_STAINED_GLASS_PANE,
                "§7Empty Slot"
            ));
        }

        if (equipmentItem == null || equipmentItem.getType().isAir()) {
            return;
        }

        // Get socketed sigils
        SocketManager socketManager = plugin.getSocketManager();
        List<Sigil> socketedSigils = socketManager.getSocketedSigils(equipmentItem);

        if (socketedSigils.isEmpty()) {
            for (int slot : SIGIL_DISPLAY_SLOTS) {
                inv.setItem(slot, ItemBuilder.createItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "§7No Sigils",
                    "§7This item has no sigils"
                ));
            }
            return;
        }

        // Display each socketed sigil
        for (int i = 0; i < socketedSigils.size() && i < SIGIL_DISPLAY_SLOTS.length; i++) {
            Sigil sigil = socketedSigils.get(i);
            int slot = SIGIL_DISPLAY_SLOTS[i];

            // Get sigil material
            Material material = Material.ECHO_SHARD;
            if (sigil.getItemForm() != null && sigil.getItemForm().getMaterial() != null) {
                material = sigil.getItemForm().getMaterial();
            }

            String rarityColor = RarityUtil.getColor(sigil.getRarity());
            List<String> lore = new ArrayList<>();

            lore.add("§7Tier: §f" + sigil.getTier());
            lore.add("§7Rarity: " + rarityColor + sigil.getRarity());
            lore.add("");

            if (sigil.isExclusive()) {
                lore.add("§c&lEXCLUSIVE");
                lore.add("§7Cannot be removed");
                if (sigil.getCrate() != null) {
                    String prefix = sigil.getLorePrefix() != null ? sigil.getLorePrefix() : "⚖";
                    lore.add("§6" + prefix + " §e" + sigil.getCrate() + " Exclusive §6" + prefix);
                }
            } else {
                lore.add("§eClick to unsocket");
                lore.add("§7Click twice to confirm");
            }

            ItemStack displayItem = ItemBuilder.createItem(material, sigil.getName(), lore);

            // Add glow for exclusive sigils
            if (sigil.isExclusive()) {
                displayItem = ItemBuilder.addGlow(displayItem);
            }

            inv.setItem(slot, displayItem);
        }
    }

    /**
     * Return item to player inventory.
     */
    private void returnItemToPlayer(Player player, Inventory inv) {
        ItemStack equipmentItem = inv.getItem(ITEM_SLOT);

        if (equipmentItem != null && !equipmentItem.getType().isAir()) {
            player.getInventory().addItem(equipmentItem);
        }
    }

    /**
     * Check if a slot is a sigil display slot.
     */
    private boolean isSigilDisplaySlot(int slot) {
        for (int displaySlot : SIGIL_DISPLAY_SLOTS) {
            if (slot == displaySlot) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the sigil index for a display slot.
     */
    private int getSigilIndex(int slot) {
        for (int i = 0; i < SIGIL_DISPLAY_SLOTS.length; i++) {
            if (SIGIL_DISPLAY_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

}
