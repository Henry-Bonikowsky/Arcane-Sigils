package com.miracle.arcanesigils;

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

        if (enabled && packHash.isEmpty()) {
            plugin.getLogger().warning("[ResourcePack] Enabled but no hash configured! Use: sha1sum ArcaneSigils-RP.zip");
            enabled = false;
        }

        if (enabled) {
            plugin.getLogger().info("[ResourcePack] URL: " + packUrl);
            plugin.getLogger().info("[ResourcePack] Hash: " + (packHash.isEmpty() ? "(none)" : packHash));
            plugin.getLogger().info("[ResourcePack] Required: " + required);
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
            plugin.getLogger().info("[ResourcePack] Sending pack to " + player.getName());
            plugin.getLogger().info("[ResourcePack] -> URL: " + packUrl);
            plugin.getLogger().info("[ResourcePack] -> Hash: " + (packHash.isEmpty() ? "(none)" : packHash.substring(0, Math.min(16, packHash.length())) + "..."));

            Component prompt = Component.text("Arcane Sigils UI Pack", NamedTextColor.GOLD);

            // Build resource pack request (hash is required)
            ResourcePackInfo packInfo = ResourcePackInfo.resourcePackInfo()
                .uri(URI.create(packUrl))
                .hash(packHash)
                .build();
            plugin.getLogger().info("[ResourcePack] -> PackInfo ID: " + packInfo.id());

            ResourcePackRequest.Builder requestBuilder = ResourcePackRequest.resourcePackRequest()
                .packs(packInfo)
                .prompt(prompt);

            if (required) {
                requestBuilder.required(true);
            }

            player.sendResourcePacks(requestBuilder.build());
            plugin.getLogger().info("[ResourcePack] -> Request sent successfully");

        } catch (Exception e) {
            plugin.getLogger().warning("[ResourcePack] Failed to send pack to " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        UUID packId = event.getID();

        plugin.getLogger().info("[ResourcePack] Status update for " + player.getName() +
            ": " + event.getStatus() + " (pack ID: " + packId + ")");

        switch (event.getStatus()) {
            case ACCEPTED -> plugin.getLogger().info("[ResourcePack] " + player.getName() + " ACCEPTED - downloading...");
            case DECLINED -> {
                plugin.getLogger().warning("[ResourcePack] " + player.getName() + " DECLINED the pack");
                if (required) {
                    player.kick(Component.text(kickMessage, NamedTextColor.RED));
                }
            }
            case FAILED_DOWNLOAD -> {
                plugin.getLogger().severe("[ResourcePack] " + player.getName() + " FAILED_DOWNLOAD!");
                plugin.getLogger().severe("[ResourcePack] -> Check if URL is accessible: " + packUrl);
                plugin.getLogger().severe("[ResourcePack] -> Check if hash matches the file");
                plugin.getLogger().severe("[ResourcePack] -> Pack ID was: " + packId);
                if (required) {
                    player.kick(Component.text("Failed to download resource pack. Please try again.", NamedTextColor.RED));
                }
            }
            case SUCCESSFULLY_LOADED -> plugin.getLogger().info("[ResourcePack] " + player.getName() + " SUCCESSFULLY_LOADED!");
            case DOWNLOADED -> plugin.getLogger().info("[ResourcePack] " + player.getName() + " DOWNLOADED - applying...");
            case INVALID_URL -> plugin.getLogger().severe("[ResourcePack] " + player.getName() + " INVALID_URL! URL: " + packUrl);
            case FAILED_RELOAD -> plugin.getLogger().severe("[ResourcePack] " + player.getName() + " FAILED_RELOAD!");
            case DISCARDED -> plugin.getLogger().info("[ResourcePack] " + player.getName() + " DISCARDED");
            default -> plugin.getLogger().info("[ResourcePack] " + player.getName() + " unknown status: " + event.getStatus());
        }
    }

    public void reload() {
        loadConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
