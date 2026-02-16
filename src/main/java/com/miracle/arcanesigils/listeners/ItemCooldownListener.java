package com.miracle.arcanesigils.listeners;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public class ItemCooldownListener implements Listener {
    private final ArmorSetsPlugin plugin;

    // Cooldown keys
    private static final String GAPPLE_KEY = "item_golden_apple";
    private static final String TOTEM_KEY = "item_totem_of_undying";

    public ItemCooldownListener(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle golden apple consumption.
     * Priority HIGHEST to cancel before fast-eating module processes.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGoldenAppleConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        // Check if golden apple (regular or enchanted)
        if (item.getType() != Material.GOLDEN_APPLE &&
            item.getType() != Material.ENCHANTED_GOLDEN_APPLE) {
            return;
        }

        // Check if system enabled
        if (!plugin.getConfig().getBoolean("item-cooldowns.enabled", true)) {
            return;
        }

        // Check cooldown
        if (plugin.getCooldownManager().isOnCooldown(player, GAPPLE_KEY)) {
            event.setCancelled(true);

            // Send formatted cooldown message
            double remaining = plugin.getCooldownManager().getRemainingCooldown(player, GAPPLE_KEY);
            String message = String.format("&e&lGolden Apple &7is on cooldown! &c%.1fs", remaining);
            player.sendMessage(TextUtil.colorize(message));

            // Play error sound
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Set cooldown (both types share same cooldown)
        double cooldown = plugin.getConfig().getDouble("item-cooldowns.golden-apple", 9.0);
        plugin.getCooldownManager().setCooldown(player, GAPPLE_KEY, "Golden Apple", cooldown);
    }

    /**
     * Handle totem of undying activation.
     * Priority HIGHEST to cancel before resurrection mechanics.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTotemUse(EntityResurrectEvent event) {
        // Only handle players
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Event starts cancelled if no totem - only proceed if not cancelled
        if (event.isCancelled()) {
            return;
        }

        // Check if system enabled
        if (!plugin.getConfig().getBoolean("item-cooldowns.enabled", true)) {
            return;
        }

        // Check cooldown
        if (plugin.getCooldownManager().isOnCooldown(player, TOTEM_KEY)) {
            event.setCancelled(true);

            // Send formatted cooldown message
            double remaining = plugin.getCooldownManager().getRemainingCooldown(player, TOTEM_KEY);
            String message = String.format("&6&lTotem of Undying &7is on cooldown! &c%.1fs", remaining);
            player.sendMessage(TextUtil.colorize(message));

            // Play error sound
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

            // Player will die since totem doesn't proc
            return;
        }

        // Set cooldown
        double cooldown = plugin.getConfig().getDouble("item-cooldowns.totem-of-undying", 60.0);
        plugin.getCooldownManager().setCooldown(player, TOTEM_KEY, "Totem of Undying", cooldown);
    }
}
