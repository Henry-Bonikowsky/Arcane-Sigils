package com.zenax.armorsets.binds;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.core.SocketManager;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages boss bar displays for ability binds.
 * Shows active binds with equipped status and hotkey/ID.
 */
public class BindsBossBarManager {
    private final ArmorSetsPlugin plugin;
    private final Map<UUID, List<BossBar>> playerBossBars;

    // Minecraft limits boss bars to 5-6 visible bars
    private static final int MAX_BOSS_BARS = 5;

    public BindsBossBarManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.playerBossBars = new HashMap<>();
    }

    /**
     * Show boss bars for a player's active binds.
     * Prioritizes binds with at least one equipped sigil.
     */
    public void showBossBars(Player player) {
        hideBossBars(player); // Clear existing bars first

        // Pause action bar notifications while ability UI is active
        var resourcePackNotifier = plugin.getResourcePackNotifier();
        if (resourcePackNotifier != null) {
            resourcePackNotifier.pause(player);
        }

        // Hide cooldown boss bars while ability UI is active (fallback)
        var notificationManager = plugin.getNotificationBossBarManager();
        if (notificationManager != null) {
            notificationManager.hideAllBars(player);
        }

        PlayerBindData data = plugin.getBindsManager().getPlayerData(player);
        BindPreset currentBinds = data.getCurrentBinds();
        BindSystem activeSystem = data.getActiveSystem();

        if (currentBinds.isEmpty()) {
            return; // No binds to show
        }

        // Build list of bind entries with priority
        List<BindEntry> entries = new ArrayList<>();

        for (Map.Entry<Integer, List<String>> bind : currentBinds.getBinds().entrySet()) {
            int slot = bind.getKey();
            List<String> sigilIds = bind.getValue();

            if (sigilIds.isEmpty()) continue;

            // Check how many sigils are equipped
            int equippedCount = 0;
            for (String sigilId : sigilIds) {
                if (isSigilEquipped(player, sigilId)) {
                    equippedCount++;
                }
            }

            entries.add(new BindEntry(slot, sigilIds, equippedCount));
        }

        // Sort: binds with equipped sigils first, then by slot number
        entries.sort((a, b) -> {
            if (a.equippedCount != b.equippedCount) {
                return Integer.compare(b.equippedCount, a.equippedCount);
            }
            return Integer.compare(a.slot, b.slot);
        });

        // Create boss bars for top entries (up to MAX_BOSS_BARS)
        List<BossBar> bossBars = new ArrayList<>();
        int count = 0;

        for (BindEntry entry : entries) {
            if (count >= MAX_BOSS_BARS) break;

            BossBar bossBar = createBossBar(player, entry.slot, entry.sigilIds, activeSystem);
            if (bossBar != null) {
                bossBar.addPlayer(player);
                bossBars.add(bossBar);
                count++;
            }
        }

        if (!bossBars.isEmpty()) {
            playerBossBars.put(player.getUniqueId(), bossBars);
        }
    }

    /**
     * Hide all boss bars for a player.
     */
    public void hideBossBars(Player player) {
        List<BossBar> bossBars = playerBossBars.remove(player.getUniqueId());
        if (bossBars != null) {
            for (BossBar bossBar : bossBars) {
                bossBar.removePlayer(player);
                bossBar.removeAll();
            }
        }

        // Resume action bar notifications when ability UI deactivates
        var resourcePackNotifier = plugin.getResourcePackNotifier();
        if (resourcePackNotifier != null) {
            resourcePackNotifier.resume(player);
        }

        // Restore cooldown boss bars when ability UI deactivates (fallback)
        var notificationManager = plugin.getNotificationBossBarManager();
        if (notificationManager != null) {
            notificationManager.showAllBars(player);
        }
    }

    /**
     * Update boss bars when equipment changes.
     */
    public void updateBossBars(Player player) {
        if (playerBossBars.containsKey(player.getUniqueId())) {
            showBossBars(player); // Refresh display
        }
    }

    /**
     * Create a boss bar for a bind slot.
     */
    private BossBar createBossBar(Player player, int slot, List<String> sigilIds, BindSystem system) {
        if (sigilIds.isEmpty()) return null;

        // Build the title text
        StringBuilder title = new StringBuilder();

        // Get player's activation hotkey for display
        PlayerBindData data = plugin.getBindsManager().getPlayerData(player);
        String activationKey = getActivationKeyDisplay(data.getHeldSlotHotkey());

        // For HOTBAR system:
        //   - For the CURRENTLY HELD slot: show held slot hotkey (F/LMB/RMB) since that activates it
        //   - For other slots: show slot number (1-9) since pressing that switches to and activates it
        // For COMMAND system: show activation key
        if (system == BindSystem.HOTBAR) {
            int currentHeldSlot = player.getInventory().getHeldItemSlot() + 1;  // 0-8 -> 1-9
            if (slot == currentHeldSlot) {
                // Currently held slot - show held slot hotkey
                title.append("§r&f&l").append(activationKey).append(" §r&b- ");
            } else {
                // Other slots - show slot number
                title.append("§r&f&l").append(slot).append(" §r&b- ");
            }
        } else {
            title.append("§r&f&l").append(activationKey).append(" §r&b- ");
        }

        // Add sigil displays showing sigil names with equipped status
        List<String> sigilDisplays = new ArrayList<>();
        for (String sigilId : sigilIds) {
            Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
            if (sigil == null) continue;

            // Get sigil name (strip tier suffix and color codes for clean display)
            String baseName = sigil.getName()
                    .replaceAll("\\s*&8\\[T\\d+\\]", "")
                    .replaceAll("\\s*\\[T\\d+\\]", "")
                    .trim();
            String displayName = TextUtil.stripColors(baseName);

            // Check if sigil is equipped
            ItemStack socketedItem = findSocketedItem(player, sigilId);
            String color = (socketedItem != null) ? "§r&a" : "§r&c"; // Green if equipped, red if not

            sigilDisplays.add(color + displayName);
        }

        // Join displays with " §r&b+ "
        title.append(String.join(" §r&b+ ", sigilDisplays));

        // Create boss bar
        BossBar bossBar = Bukkit.createBossBar(
                TextUtil.colorize(title.toString()),
                BarColor.BLUE,
                BarStyle.SOLID
        );

        bossBar.setProgress(1.0);

        return bossBar;
    }

    /**
     * Format a material name for display (e.g., DIAMOND_CHESTPLATE -> "DIAMOND CHESTPLATE")
     */
    private String formatMaterialName(org.bukkit.Material material) {
        return material.name().replace("_", " ");
    }

    /**
     * Find the item that has a sigil socketed into it.
     * Returns the ItemStack, or null if not equipped.
     */
    private ItemStack findSocketedItem(Player player, String sigilId) {
        SocketManager socketManager = plugin.getSocketManager();

        // Check armor slots
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir()) continue;

            List<String> socketedData = socketManager.getSocketedSigilData(piece);
            for (String entry : socketedData) {
                String[] parts = entry.split(":");
                if (parts[0].equalsIgnoreCase(sigilId)) {
                    return piece;
                }
            }
        }

        // Check hotbar slots (0-8)
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < 9; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;

            List<String> socketedData = socketManager.getSocketedSigilData(item);
            for (String entry : socketedData) {
                String[] parts = entry.split(":");
                if (parts[0].equalsIgnoreCase(sigilId)) {
                    return item;
                }
            }
        }

        // Check offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            List<String> socketedData = socketManager.getSocketedSigilData(offhand);
            for (String entry : socketedData) {
                String[] parts = entry.split(":");
                if (parts[0].equalsIgnoreCase(sigilId)) {
                    return offhand;
                }
            }
        }

        return null;
    }

    /**
     * Check if a sigil is equipped in player's armor or hotbar.
     */
    private boolean isSigilEquipped(Player player, String sigilId) {
        return findSocketedItem(player, sigilId) != null;
    }

    /**
     * Get display string for activation hotkey (default Minecraft keybinds).
     */
    private String getActivationKeyDisplay(HeldSlotHotkey hotkey) {
        return switch (hotkey) {
            case SWAP_HAND -> "F";
            case ATTACK -> "LMB";
            case USE_ITEM -> "RMB";
        };
    }

    /**
     * Clean up boss bars for a player (called on quit).
     */
    public void cleanup(Player player) {
        hideBossBars(player);
    }

    /**
     * Clean up all boss bars (called on plugin disable).
     */
    public void cleanupAll() {
        for (List<BossBar> bossBars : playerBossBars.values()) {
            for (BossBar bossBar : bossBars) {
                bossBar.removeAll();
            }
        }
        playerBossBars.clear();
    }

    /**
     * Internal class to store bind entry data for sorting.
     */
    private static class BindEntry {
        final int slot;
        final List<String> sigilIds;
        final int equippedCount;

        BindEntry(int slot, List<String> sigilIds, int equippedCount) {
            this.slot = slot;
            this.sigilIds = sigilIds;
            this.equippedCount = equippedCount;
        }
    }
}
