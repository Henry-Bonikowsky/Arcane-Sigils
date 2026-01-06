package com.miracle.arcanesigils.listeners;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

/**
 * Removes vanilla enchantment level caps.
 * Allows players to enchant items past normal vanilla limits
 * (e.g., Sharpness 10, Protection 10, etc.)
 */
public class EnchantCapRemover implements Listener {

    /**
     * Allow anvil combinations that exceed vanilla enchantment caps.
     * This runs at HIGHEST priority to override vanilla restrictions.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getItem(0);
        ItemStack second = inv.getItem(1);
        
        if (first == null || first.getType() == Material.AIR) return;
        if (second == null || second.getType() == Material.AIR) return;
        
        // Get result or create new one based on first item
        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) {
            result = first.clone();
        }
        
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) return;
        
        // Combine enchantments from both items, bypassing level caps
        Map<Enchantment, Integer> enchants = resultMeta.getEnchants();
        
        // Add enchantments from second item
        if (second.getType() == Material.ENCHANTED_BOOK) {
            // Handle enchanted books
            EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) second.getItemMeta();
            if (bookMeta != null) {
                for (Map.Entry<Enchantment, Integer> entry : bookMeta.getStoredEnchants().entrySet()) {
                    Enchantment ench = entry.getKey();
                    int level = entry.getValue();
                    
                    // If result already has this enchantment, add levels
                    if (enchants.containsKey(ench)) {
                        int currentLevel = enchants.get(ench);
                        level = (currentLevel == level) ? level + 1 : Math.max(currentLevel, level);
                    }
                    
                    // Apply without cap (true = ignore level restrictions)
                    resultMeta.addEnchant(ench, level, true);
                }
            }
        } else if (second.hasItemMeta() && second.getItemMeta().hasEnchants()) {
            // Handle enchanted items
            for (Map.Entry<Enchantment, Integer> entry : second.getItemMeta().getEnchants().entrySet()) {
                Enchantment ench = entry.getKey();
                int level = entry.getValue();
                
                // If result already has this enchantment, add levels
                if (enchants.containsKey(ench)) {
                    int currentLevel = enchants.get(ench);
                    level = (currentLevel == level) ? level + 1 : Math.max(currentLevel, level);
                }
                
                // Apply without cap (true = ignore level restrictions)
                resultMeta.addEnchant(ench, level, true);
            }
        }
        
        result.setItemMeta(resultMeta);
        event.setResult(result);
    }

    /**
     * Intercept /enchant command to allow levels beyond vanilla caps.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEnchantCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase();
        
        // Check if it's an enchant command
        if (!msg.startsWith("/enchant ") && !msg.startsWith("/minecraft:enchant ")) {
            return;
        }
        
        // Parse command: /enchant <target> <enchantment> [level]
        String[] parts = event.getMessage().split(" ");
        if (parts.length < 3) return; // Need at least target and enchantment
        
        // Get player (target)
        Player player = event.getPlayer();
        if (parts[1].equals("@s") || parts[1].equals("@p") || parts[1].equalsIgnoreCase(player.getName())) {
            // Target is the player themselves or @s/@p
        } else {
            // Target is someone else - try to get that player
            Player target = player.getServer().getPlayer(parts[1]);
            if (target != null) {
                player = target;
            } else {
                return; // Target not found, let vanilla handle it
            }
        }
        
        // Get enchantment
        String enchantName = parts[2];
        Enchantment enchantment = null;
        
        // Try with minecraft: namespace
        try {
            enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantName.toLowerCase()));
        } catch (Exception e) {
            // Try direct lookup
            enchantment = Enchantment.getByName(enchantName.toUpperCase());
        }
        
        if (enchantment == null) return; // Invalid enchantment, let vanilla handle error
        
        // Get level (default 1 if not specified)
        int level = 1;
        if (parts.length >= 4) {
            try {
                level = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                return; // Invalid level, let vanilla handle error
            }
        }
        
        // Check if level exceeds vanilla max
        int vanillaMax = enchantment.getMaxLevel();
        if (level > vanillaMax) {
            // Cancel vanilla command and apply enchantment manually
            event.setCancelled(true);
            
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) {
                player.sendMessage("§cYou must be holding an item to enchant!");
                return;
            }
            
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // Apply enchantment bypassing level cap
                meta.addEnchant(enchantment, level, true);
                item.setItemMeta(meta);
                
                String enchantDisplayName = enchantName.substring(0, 1).toUpperCase() + 
                                           enchantName.substring(1).replace("_", " ");
                player.sendMessage("§aEnchanted item with " + enchantDisplayName + " " + level);
            }
        }
        // If level <= vanillaMax, let vanilla command handle it normally
    }
}
