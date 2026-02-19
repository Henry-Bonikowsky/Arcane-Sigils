package com.miracle.arcanesigils.listeners;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for armor changes and clears sigil variables when armor is unequipped.
 * This ensures that sigil-scoped variables (like King's Brace charge) are properly reset
 * when the armor piece is removed.
 * 
 * Also manages Ancient Crown interceptor registration for passive immunity.
 */
public class ArmorChangeListener implements Listener {
    private final ArmorSetsPlugin plugin;
    
    public ArmorChangeListener(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Handle armor unequip via inventory clicks.
     * Clears sigil variables when armor is removed from equipment slots.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        // Check if clicking on armor slot
        int slot = event.getSlot();
        String armorSlot = getArmorSlotFromInventorySlot(slot);
        
        if (armorSlot != null) {
            // Get the item that's being removed from the armor slot
            ItemStack currentArmor = event.getCurrentItem();
            
            // If there's armor in that slot, clear its variables after the click completes
            if (currentArmor != null && !currentArmor.getType().isAir() && isArmorMaterial(currentArmor.getType())) {
                // Schedule cleanup for next tick to ensure the item is actually removed
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    // Verify the armor slot is now empty or different
                    ItemStack newArmor = getArmorInSlot(player, armorSlot);
                    if (newArmor == null || newArmor.getType().isAir() || !newArmor.equals(currentArmor)) {
                        clearSigilVariablesForSlot(player, armorSlot);

                    }
                }, 1L);
            }
        }
        
        // Handle shift-click from armor slots
        if (event.isShiftClick()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir() && isArmorMaterial(clicked.getType())) {
                String clickedArmorSlot = getArmorSlotFromMaterial(clicked.getType());
                if (clickedArmorSlot != null) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        clearSigilVariablesForSlot(player, clickedArmorSlot);
                    }, 1L);
                }
            }
        }
    }
    
    /**
     * Handle armor drop event.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        
        if (isArmorMaterial(item.getType())) {
            String armorSlot = getArmorSlotFromMaterial(item.getType());
            if (armorSlot != null) {
                // Check if this armor was actually equipped
                ItemStack equipped = getArmorInSlot(player, armorSlot);
                if (equipped == null || equipped.getType().isAir()) {
                    clearSigilVariablesForSlot(player, armorSlot);

                }
            }
        }
    }
    
    /**
     * Check for Ancient Crown when player joins.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // No additional checks needed on join
    }
    
    /**
     * Clean up interceptors when player quits.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.getInterceptionManager() != null) {
            plugin.getInterceptionManager().unregisterAll(event.getPlayer());
        }
    }
    
    /**
     * Clear all sigil variables for a specific armor slot.
     */
    private void clearSigilVariablesForSlot(Player player, String slot) {
        plugin.getSigilVariableManager().clearSlotVariables(player.getUniqueId(), slot);
    }
    
    /**
     * Get armor slot name from inventory slot index.
     * @return HELMET, CHESTPLATE, LEGGINGS, BOOTS, or null if not an armor slot
     */
    private String getArmorSlotFromInventorySlot(int slot) {
        // Standard inventory slots: 5=helmet, 6=chestplate, 7=leggings, 8=boots
        return switch (slot) {
            case 5 -> "HELMET";
            case 6 -> "CHESTPLATE";
            case 7 -> "LEGGINGS";
            case 8 -> "BOOTS";
            default -> null;
        };
    }
    
    /**
     * Get armor slot name from material type.
     */
    private String getArmorSlotFromMaterial(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) return "HELMET";
        if (name.endsWith("_CHESTPLATE")) return "CHESTPLATE";
        if (name.endsWith("_LEGGINGS")) return "LEGGINGS";
        if (name.endsWith("_BOOTS")) return "BOOTS";
        if (name.equals("ELYTRA")) return "CHESTPLATE";
        if (name.endsWith("_SKULL") || name.endsWith("_HEAD")) return "HELMET";
        return null;
    }
    
    /**
     * Check if material is armor.
     */
    private boolean isArmorMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || 
               name.endsWith("_CHESTPLATE") || 
               name.endsWith("_LEGGINGS") || 
               name.endsWith("_BOOTS") ||
               name.equals("ELYTRA") ||
               name.endsWith("_SKULL") ||
               name.endsWith("_HEAD");
    }
    
    /**
     * Get the armor item in a specific slot.
     */
    private ItemStack getArmorInSlot(Player player, String slot) {
        return switch (slot) {
            case "HELMET" -> player.getInventory().getHelmet();
            case "CHESTPLATE" -> player.getInventory().getChestplate();
            case "LEGGINGS" -> player.getInventory().getLeggings();
            case "BOOTS" -> player.getInventory().getBoots();
            default -> null;
        };
    }
}
