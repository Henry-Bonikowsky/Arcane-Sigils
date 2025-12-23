package com.zenax.dungeons.sound;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class DungeonSoundEffects {

    private static Plugin plugin;

    private DungeonSoundEffects() {
        // Private constructor to prevent instantiation
    }

    public static void init(Plugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static void playPortalEnter(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL,
            SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    public static void playGateOpen(Location location) {
        if (location.getWorld() == null) return;

        location.getWorld().playSound(location, Sound.BLOCK_IRON_DOOR_OPEN,
            SoundCategory.BLOCKS, 1.5f, 0.8f);

        // Delayed chain break sound for effect
        if (plugin != null) {
            Bukkit.getScheduler().runTaskLater(plugin,
                () -> location.getWorld().playSound(location, Sound.BLOCK_CHAIN_BREAK,
                    SoundCategory.BLOCKS, 1.0f, 0.9f),
                5L
            );
        }
    }

    public static void playRoomCleared(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP,
            SoundCategory.PLAYERS, 1.0f, 1.5f);
    }

    public static void playBossWarning(Location location) {
        if (location.getWorld() == null) return;

        location.getWorld().playSound(location, Sound.ENTITY_WITHER_SPAWN,
            SoundCategory.HOSTILE, 0.3f, 0.8f);
    }

    public static void playLootChestOpen(Location location) {
        if (location.getWorld() == null) return;

        location.getWorld().playSound(location, Sound.BLOCK_CHEST_OPEN,
            SoundCategory.BLOCKS, 1.0f, 1.0f);

        // Delayed level up sound for magical effect
        if (plugin != null) {
            Bukkit.getScheduler().runTaskLater(plugin,
                () -> location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS, 0.5f, 2.0f),
                3L
            );
        }
    }

    public static void playObjectiveComplete(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
            SoundCategory.PLAYERS, 1.0f, 1.2f);
    }
}
