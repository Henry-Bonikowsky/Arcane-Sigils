package com.zenax.armorsets;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.net.URI;
import java.util.UUID;

/**
 * Handles automatic resource pack distribution to players.
 */
public class ResourcePackHandler implements Listener {

    private final ArmorSetsPlugin plugin;
    private boolean enabled = false;
    private String packUrl = "";
    private String packHash = "";
    private boolean required = false;
    private String kickMessage = "This server requires the Arcane Sigils resource pack.";

    public ResourcePackHandler(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        var config = plugin.getConfig();

        enabled = config.getBoolean("resource-pack.enabled", false);
        packUrl = config.getString("resource-pack.url", "");
        packHash = config.getString("resource-pack.hash", "");
        required = config.getBoolean("resource-pack.required", false);
        kickMessage = config.getString("resource-pack.kick-message",
            "This server requires the Arcane Sigils resource pack.");

        if (enabled && packUrl.isEmpty()) {
            plugin.getLogger().warning("[ResourcePack] Enabled but no URL configured!");
            enabled = false;
        }

        if (enabled) {
            plugin.getLogger().info("[ResourcePack] Will prompt players to download: " + packUrl);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled || packUrl.isEmpty()) return;

        Player player = event.getPlayer();

        // Delay slightly to ensure player is fully connected
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            sendResourcePack(player);
        }, 20L); // 1 second delay
    }

    private void sendResourcePack(Player player) {
        try {
            Component prompt = Component.text("Arcane Sigils UI Pack", NamedTextColor.GOLD);

            // Build resource pack request
            ResourcePackInfo.Builder infoBuilder = ResourcePackInfo.resourcePackInfo()
                .uri(URI.create(packUrl));

            // Add hash if provided
            if (!packHash.isEmpty()) {
                infoBuilder.hash(packHash);
            }

            ResourcePackInfo packInfo = infoBuilder.build();

            ResourcePackRequest.Builder requestBuilder = ResourcePackRequest.resourcePackRequest()
                .packs(packInfo)
                .prompt(prompt);

            if (required) {
                requestBuilder.required(true);
            }

            player.sendResourcePacks(requestBuilder.build());

        } catch (Exception e) {
            plugin.getLogger().warning("[ResourcePack] Failed to send pack to " + player.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();

        switch (event.getStatus()) {
            case ACCEPTED -> plugin.getLogger().info("[ResourcePack] " + player.getName() + " accepted the pack");
            case DECLINED -> {
                plugin.getLogger().info("[ResourcePack] " + player.getName() + " declined the pack");
                if (required) {
                    player.kick(Component.text(kickMessage, NamedTextColor.RED));
                }
            }
            case FAILED_DOWNLOAD -> {
                plugin.getLogger().warning("[ResourcePack] " + player.getName() + " failed to download");
                if (required) {
                    player.kick(Component.text("Failed to download resource pack. Please try again.", NamedTextColor.RED));
                }
            }
            case SUCCESSFULLY_LOADED -> {
                plugin.getLogger().info("[ResourcePack] " + player.getName() + " loaded the pack successfully");
                // Mark player as having resource pack
                var notifier = plugin.getResourcePackNotifier();
                if (notifier != null) {
                    notifier.setHasResourcePack(player, true);
                }
            }
            default -> {}
        }
    }

    public void reload() {
        loadConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
