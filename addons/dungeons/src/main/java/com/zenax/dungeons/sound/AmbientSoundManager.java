package com.zenax.dungeons.sound;

import com.zenax.dungeons.dungeon.DungeonInstance;
import com.zenax.dungeons.lobby.DungeonLobby;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class AmbientSoundManager {
    private final Plugin plugin;
    private final Map<UUID, BukkitTask> lobbyAmbientTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> dungeonAmbientTasks = new HashMap<>();

    public AmbientSoundManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void startLobbyAmbience(DungeonLobby lobby) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (UUID playerId : lobby.getPlayersInLobby()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) continue;

                Random random = new Random();
                double roll = random.nextDouble();

                if (roll < 0.1) {
                    player.playSound(player.getLocation(), Sound.BLOCK_POINTED_DRIPSTONE_DRIP_WATER,
                        SoundCategory.AMBIENT, 0.3f, 0.8f + random.nextFloat() * 0.4f);
                } else if (roll < 0.15) {
                    player.playSound(player.getLocation(), Sound.AMBIENT_CAVE,
                        SoundCategory.AMBIENT, 0.2f, 0.5f);
                } else if (roll < 0.2) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT,
                        SoundCategory.AMBIENT, 0.1f, 0.3f);
                } else if (roll < 0.22) {
                    player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_STEP,
                        SoundCategory.AMBIENT, 0.2f, 0.7f);
                }
            }
        }, 40L, 60L);
        lobbyAmbientTasks.put(lobby.getInstanceId(), task);
    }

    public void startDungeonAmbience(DungeonInstance instance) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (UUID playerId : instance.getPlayerUuids()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) continue;

                Random random = new Random();
                double roll = random.nextDouble();

                if (roll < 0.08) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT,
                        SoundCategory.AMBIENT, 0.15f, 0.5f);
                } else if (roll < 0.12) {
                    player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK,
                        SoundCategory.AMBIENT, 0.2f, 0.6f);
                } else if (roll < 0.18) {
                    player.playSound(player.getLocation(), Sound.AMBIENT_CAVE,
                        SoundCategory.AMBIENT, 0.3f, 0.7f);
                } else if (roll < 0.22) {
                    player.playSound(player.getLocation(), Sound.ENTITY_SKELETON_AMBIENT,
                        SoundCategory.AMBIENT, 0.1f, 0.4f);
                } else if (roll < 0.25) {
                    player.playSound(player.getLocation(), Sound.BLOCK_POINTED_DRIPSTONE_DRIP_LAVA,
                        SoundCategory.AMBIENT, 0.2f, 0.9f);
                }
            }
        }, 20L, 40L);
        dungeonAmbientTasks.put(instance.getInstanceId(), task);
    }

    public void stopLobbyAmbience(UUID lobbyId) {
        BukkitTask task = lobbyAmbientTasks.remove(lobbyId);
        if (task != null) task.cancel();
    }

    public void stopDungeonAmbience(UUID instanceId) {
        BukkitTask task = dungeonAmbientTasks.remove(instanceId);
        if (task != null) task.cancel();
    }

    public void shutdown() {
        lobbyAmbientTasks.values().forEach(BukkitTask::cancel);
        dungeonAmbientTasks.values().forEach(BukkitTask::cancel);
        lobbyAmbientTasks.clear();
        dungeonAmbientTasks.clear();
    }
}
