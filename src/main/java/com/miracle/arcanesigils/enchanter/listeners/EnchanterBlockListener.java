package com.miracle.arcanesigils.enchanter.listeners;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.enchanter.EnchanterManager;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Listens for player interactions with Enchanter blocks.
 * Opens the Enchanter GUI when a registered block is right-clicked.
 */
public class EnchanterBlockListener implements Listener {

    private final ArmorSetsPlugin plugin;
    private final EnchanterManager enchanterManager;
    private final GUIManager guiManager;

    public EnchanterBlockListener(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.enchanterManager = plugin.getEnchanterManager();
        this.guiManager = plugin.getGuiManager();
    }

    /**
     * Handle player right-clicking on blocks.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Player player = event.getPlayer();

        // Check if this is a registered Enchanter block
        if (!enchanterManager.isEnchanterBlock(clickedBlock)) {
            return;
        }

        // Cancel the default enchanting table GUI
        event.setCancelled(true);

        // Check permission
        if (!player.hasPermission("arcanesigils.enchanter.use")) {
            player.sendMessage("§cYou don't have permission to use the Enchanter.");
            return;
        }

        // Open Enchanter GUI
        GUISession session = new GUISession(GUIType.ENCHANTER_MAIN);
        guiManager.reopenGUI(player, session);
    }

    /**
     * Prevent the vanilla enchanting table GUI from opening for Enchanter blocks.
     * This is a backup in case the PlayerInteractEvent doesn't fully prevent it.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // Check if opening an enchanting table inventory
        if (event.getInventory().getType() != InventoryType.ENCHANTING) {
            return;
        }

        // Check if the inventory holder is a block
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof org.bukkit.block.EnchantingTable)) {
            return;
        }

        // Get the enchanting table block location
        org.bukkit.block.EnchantingTable enchantingTable = (org.bukkit.block.EnchantingTable) holder;
        Block block = enchantingTable.getBlock();

        // If this is a registered Enchanter block, cancel the vanilla GUI
        if (enchanterManager.isEnchanterBlock(block)) {
            event.setCancelled(true);

            // Open our custom GUI instead
            if (player.hasPermission("arcanesigils.enchanter.use")) {
                GUISession session = new GUISession(GUIType.ENCHANTER_MAIN);
                guiManager.reopenGUI(player, session);
            } else {
                player.sendMessage("§cYou don't have permission to use the Enchanter.");
            }
        }
    }
}
