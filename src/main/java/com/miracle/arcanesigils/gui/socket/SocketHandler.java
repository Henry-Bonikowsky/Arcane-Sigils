package com.miracle.arcanesigils.gui.socket;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.core.SocketManager;
import com.miracle.arcanesigils.core.SocketManager.SocketResult;
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

/**
 * Handler for the SOCKET GUI.
 * Allows players to socket a sigil shard into equipment.
 */
public class SocketHandler extends AbstractHandler {

    // Slot positions
    private static final int ITEM_SLOT = 11;
    private static final int ARROW_SLOT = 13;
    private static final int SIGIL_SLOT = 15;
    private static final int BACK_SLOT = 18;
    private static final int CONFIRM_SLOT = 22;

    public SocketHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        openGUI(guiManager, player);
    }

    @Override
    public void handleClose(Player player, GUISession session, InventoryCloseEvent event) {
        // Return items to player when GUI is closed by any means
        Inventory inv = event.getInventory();
        returnItemsToPlayer(player, inv);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = event.getInventory();

        // Handle shift-click from player inventory (bottom inventory)
        if (event.isShiftClick() && slot >= inv.getSize()) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && !clickedItem.getType().isAir()) {
                // Determine which slot to place in based on item type
                int targetSlot = -1;

                if (plugin.getSigilManager().isSigilItem(clickedItem)) {
                    // It's a sigil - put in SIGIL_SLOT if empty
                    ItemStack currentInSlot = inv.getItem(SIGIL_SLOT);
                    if (currentInSlot == null || currentInSlot.getType().isAir()) {
                        targetSlot = SIGIL_SLOT;
                    }
                } else if (plugin.getSocketManager().isSocketable(clickedItem)) {
                    // It's equipment - put in ITEM_SLOT if empty
                    ItemStack currentInSlot = inv.getItem(ITEM_SLOT);
                    if (currentInSlot == null || currentInSlot.getType().isAir()) {
                        targetSlot = ITEM_SLOT;
                    }
                }

                if (targetSlot != -1) {
                    inv.setItem(targetSlot, clickedItem.clone());
                    event.setCurrentItem(null);
                    event.setCancelled(true);
                    // Refresh display
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.getOpenInventory().getTopInventory().equals(inv)) {
                            refreshGUI(player, inv);
                        }
                    });
                    return;
                }
            }
            event.setCancelled(true);
            return;
        }

        // Handle item placement/retrieval slots (don't cancel event)
        if (slot == ITEM_SLOT || slot == SIGIL_SLOT) {
            event.setCancelled(false);
            // Schedule a refresh after the item is placed/removed
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().equals(inv)) {
                    refreshGUI(player, inv);
                }
            });
            return;
        }

        // Handle action buttons
        switch (slot) {
            case BACK_SLOT -> {
                // Items will be returned by handleClose when inventory closes
                player.closeInventory();
                playSound(player, "close");
            }
            case CONFIRM_SLOT -> {
                // Validate and socket sigil
                ItemStack equipmentItem = inv.getItem(ITEM_SLOT);
                ItemStack sigilItem = inv.getItem(SIGIL_SLOT);

                if (equipmentItem == null || equipmentItem.getType().isAir()) {
                    player.sendMessage(TextUtil.colorize("§cYou must place equipment in the left slot!"));
                    playSound(player, "error");
                    return;
                }

                if (sigilItem == null || sigilItem.getType().isAir()) {
                    player.sendMessage(TextUtil.colorize("§cYou must place a sigil shard in the right slot!"));
                    playSound(player, "error");
                    return;
                }

                // Validate it's a sigil
                if (!plugin.getSigilManager().isSigilItem(sigilItem)) {
                    player.sendMessage(TextUtil.colorize("§cThat's not a sigil shard!"));
                    playSound(player, "error");
                    return;
                }

                Sigil sigil = plugin.getSigilManager().getSigilFromItem(sigilItem);
                if (sigil == null) {
                    player.sendMessage(TextUtil.colorize("§cInvalid sigil shard!"));
                    playSound(player, "error");
                    return;
                }

                // Validate equipment is socketable
                SocketManager socketManager = plugin.getSocketManager();
                if (!socketManager.isSocketable(equipmentItem)) {
                    player.sendMessage(TextUtil.colorize("§cThat item cannot have sigils socketed!"));
                    playSound(player, "error");
                    return;
                }

                // Attempt to socket
                SocketResult result = socketManager.socketSigil(player, equipmentItem, sigil);

                if (result == SocketResult.SUCCESS) {
                    // Remove sigil from slot
                    sigilItem.setAmount(sigilItem.getAmount() - 1);
                    if (sigilItem.getAmount() <= 0) {
                        inv.setItem(SIGIL_SLOT, null);
                    } else {
                        inv.setItem(SIGIL_SLOT, sigilItem);
                    }

                    // Update equipment item in slot
                    inv.setItem(ITEM_SLOT, equipmentItem);

                    player.sendMessage(TextUtil.colorize("§a&lSocketed! §f" + sigil.getName() + " §ahas been added to your item!"));
                    playSound(player, "success");

                    // Refresh GUI
                    refreshGUI(player, inv);
                } else {
                    // Handle errors
                    String message = switch (result) {
                        case WRONG_SLOT -> "§cThis sigil can only be socketed into: " + String.join(", ", sigil.getSocketables()) + "!";
                        case ALREADY_HAS_SIGIL -> "§cThis item already has this sigil!";
                        case NO_PERMISSION -> "§cYou don't have permission to socket sigils!";
                        case INVALID_ITEM -> "§cInvalid item!";
                        case TIER_TOO_LOW -> "§cYour item tier is too low for this sigil!";
                        default -> "§cFailed to socket sigil!";
                    };
                    player.sendMessage(TextUtil.colorize(message));
                    playSound(player, "error");
                }
            }
        }
    }

    /**
     * Open the Socket GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player) {
        // Create inventory
        Inventory inv = Bukkit.createInventory(null, 27,
            TextUtil.parseComponent("§8Socket Sigil"));

        // Fill with background
        ItemBuilder.fillBackground(inv);

        // Set up slots
        setupSlots(inv);

        // Create session
        GUISession session = new GUISession(GUIType.SOCKET);

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Set up the GUI slots.
     */
    private static void setupSlots(Inventory inv) {
        // Clear item slots
        inv.setItem(ITEM_SLOT, null);
        inv.setItem(SIGIL_SLOT, null);

        // Arrow indicator
        inv.setItem(ARROW_SLOT, ItemBuilder.createItem(
            Material.ARROW,
            "§3→",
            "§7Place equipment on the left",
            "§7Place sigil shard on the right"
        ));

        // Back button
        inv.setItem(BACK_SLOT, ItemBuilder.createItem(
            Material.RED_DYE,
            "§c✗ Cancel",
            "§7Return items and close"
        ));

        // Confirm button (initially disabled)
        inv.setItem(CONFIRM_SLOT, ItemBuilder.createItem(
            Material.GRAY_DYE,
            "§7✓ Confirm",
            "§cPlace both items first"
        ));
    }

    /**
     * Refresh the GUI to update confirm button state.
     */
    private void refreshGUI(Player player, Inventory inv) {
        ItemStack equipmentItem = inv.getItem(ITEM_SLOT);
        ItemStack sigilItem = inv.getItem(SIGIL_SLOT);

        boolean hasEquipment = equipmentItem != null && !equipmentItem.getType().isAir();
        boolean hasSigil = sigilItem != null && !sigilItem.getType().isAir();

        if (hasEquipment && hasSigil) {
            // Both items present - enable confirm
            boolean isValidSigil = plugin.getSigilManager().isSigilItem(sigilItem);
            boolean isSocketable = plugin.getSocketManager().isSocketable(equipmentItem);

            if (isValidSigil && isSocketable) {
                Sigil sigil = plugin.getSigilManager().getSigilFromItem(sigilItem);

                if (sigil != null && plugin.getSocketManager().canSigilSocketInto(sigil, equipmentItem)) {
                    inv.setItem(CONFIRM_SLOT, ItemBuilder.createItem(
                        Material.LIME_DYE,
                        "§a✓ Confirm",
                        "§7Click to socket sigil"
                    ));
                } else {
                    inv.setItem(CONFIRM_SLOT, ItemBuilder.createItem(
                        Material.ORANGE_DYE,
                        "§6⚠ Incompatible",
                        "§cThis sigil cannot be socketed",
                        "§cinto this item type!"
                    ));
                }
            } else {
                inv.setItem(CONFIRM_SLOT, ItemBuilder.createItem(
                    Material.ORANGE_DYE,
                    "§6⚠ Invalid",
                    !isValidSigil ? "§cNot a sigil shard!" : "§cItem cannot be socketed!"
                ));
            }
        } else {
            // Items missing - disable confirm
            inv.setItem(CONFIRM_SLOT, ItemBuilder.createItem(
                Material.GRAY_DYE,
                "§7✓ Confirm",
                "§cPlace both items first"
            ));
        }
    }

    /**
     * Return items to player inventory.
     */
    private void returnItemsToPlayer(Player player, Inventory inv) {
        ItemStack equipmentItem = inv.getItem(ITEM_SLOT);
        ItemStack sigilItem = inv.getItem(SIGIL_SLOT);

        if (equipmentItem != null && !equipmentItem.getType().isAir()) {
            player.getInventory().addItem(equipmentItem);
        }

        if (sigilItem != null && !sigilItem.getType().isAir()) {
            player.getInventory().addItem(sigilItem);
        }
    }
}
