package com.miracle.arcanesigils.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Disables player collision globally using the modern setCollidable API.
 * This is cleaner and doesn't conflict with scoreboards or other plugins.
 */
public class CollisionDisabler implements Listener {

    private final Plugin plugin;

    public CollisionDisabler(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        disableCollision(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Re-enable on quit for cleanup (optional)
        Player player = event.getPlayer();
        player.setCollidable(true);
    }

    /**
     * Disable collision for a specific player
     */
    private void disableCollision(Player player) {
        player.setCollidable(false);
        plugin.getLogger().info("[CollisionDisabler] Disabled collision for " + player.getName());
    }

    /**
     * Disable collision for all currently online players
     */
    public void disableForAll() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setCollidable(false);
            count++;
        }
        plugin.getLogger().info("[CollisionDisabler] Disabled collision for " + count + " online players");
    }

    /**
     * Clean up on shutdown (re-enable collision)
     */
    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setCollidable(true);
        }
        plugin.getLogger().info("[CollisionDisabler] Re-enabled collision for all players");
    }
}
