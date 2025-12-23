package com.zenax.dungeons.sound;

import com.zenax.dungeons.dungeon.DungeonInstance;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class DungeonMusicManager {
    private final Plugin plugin;
    private final Map<UUID, BukkitTask> musicTasks = new HashMap<>();
    private final Map<UUID, String> currentThemes = new HashMap<>();

    public DungeonMusicManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void startDungeonMusic(DungeonInstance instance, String theme) {
        stopMusic(instance.getInstanceId());

        Sound music = getThemeMusic(theme);
        currentThemes.put(instance.getInstanceId(), theme);

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (UUID playerId : instance.getPlayerUuids()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.playSound(player.getLocation(), music, SoundCategory.MUSIC, 0.5f, 1.0f);
                }
            }
        }, 0L, 4800L); // Repeat every 4 minutes (4800 ticks)

        musicTasks.put(instance.getInstanceId(), task);
    }

    public void startBossMusic(DungeonInstance instance) {
        stopMusic(instance.getInstanceId());

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (UUID playerId : instance.getPlayerUuids()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.playSound(player.getLocation(), Sound.MUSIC_DRAGON,
                        SoundCategory.MUSIC, 0.6f, 1.0f);
                }
            }
        }, 0L, 3600L); // Repeat every 3 minutes (3600 ticks)

        musicTasks.put(instance.getInstanceId(), task);
        currentThemes.put(instance.getInstanceId(), "boss");
    }

    public void playVictoryMusic(DungeonInstance instance) {
        stopMusic(instance.getInstanceId());

        for (UUID playerId : instance.getPlayerUuids()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundCategory.MUSIC, 1.0f, 1.0f);
            }
        }
    }

    public void playDefeatMusic(DungeonInstance instance) {
        stopMusic(instance.getInstanceId());

        for (UUID playerId : instance.getPlayerUuids()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH,
                    SoundCategory.MUSIC, 0.8f, 0.5f);
            }
        }
    }

    public void stopMusic(UUID instanceId) {
        BukkitTask task = musicTasks.remove(instanceId);
        if (task != null) {
            task.cancel();
        }
        currentThemes.remove(instanceId);
    }

    public Sound getThemeMusic(String theme) {
        if (theme == null) {
            return Sound.MUSIC_OVERWORLD_DRIPSTONE_CAVES;
        }

        return switch (theme.toLowerCase()) {
            case "nether" -> Sound.MUSIC_NETHER_BASALT_DELTAS;
            case "cave" -> Sound.MUSIC_OVERWORLD_DRIPSTONE_CAVES;
            case "end" -> Sound.MUSIC_END;
            case "crimson" -> Sound.MUSIC_NETHER_CRIMSON_FOREST;
            case "soul" -> Sound.MUSIC_NETHER_SOUL_SAND_VALLEY;
            case "lush" -> Sound.MUSIC_OVERWORLD_LUSH_CAVES;
            case "deep_dark" -> Sound.MUSIC_OVERWORLD_DEEP_DARK;
            default -> Sound.MUSIC_OVERWORLD_DRIPSTONE_CAVES;
        };
    }

    public void shutdown() {
        musicTasks.values().forEach(BukkitTask::cancel);
        musicTasks.clear();
        currentThemes.clear();
    }
}
