package com.miracle.arcanesigils.binds;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.core.SocketManager;
import com.miracle.arcanesigils.utils.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Manages ability UI display via boss bars.
 * Each ability row gets its own boss bar with simple vanilla text.
 */
public class BindsBossBarManager {
    private final ArmorSetsPlugin plugin;

    // Track boss bars per player (list of bars, one per ability row)
    private final Map<UUID, List<BossBar>> playerBars = new HashMap<>();

    // Max boss bars shown (one per hotbar bind slot)
    private static final int MAX_BOSS_BARS = 9;

    public BindsBossBarManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Show boss bars for a player's active binds.
     * Prioritizes binds with at least one equipped sigil.
     */
    public void showBossBars(Player player) {
        // First hide any existing bars
        hideBossBars(player);

        PlayerBindData data = plugin.getBindsManager().getPlayerData(player);
        BindPreset currentBinds = data.getCurrentBinds();
        BindSystem activeSystem = data.getActiveSystem();

        if (currentBinds.isEmpty()) {
            return;
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

        // Sort by slot number ascending (1-9)
        entries.sort((a, b) -> Integer.compare(a.slot, b.slot));

        // Create boss bars for top entries
        List<BossBar> bars = new ArrayList<>();
        UUID playerId = player.getUniqueId();

        for (BindEntry entry : entries) {
            if (bars.size() >= MAX_BOSS_BARS) break;

            Component title = buildAbilityTitle(player, entry.slot, entry.sigilIds, activeSystem);
            if (title != null) {
                BossBar bar = BossBar.bossBar(
                    title,
                    1.0f,
                    BossBar.Color.BLUE,
                    BossBar.Overlay.PROGRESS
                );
                bars.add(bar);
                player.showBossBar(bar);
            }
        }

        if (!bars.isEmpty()) {
            playerBars.put(playerId, bars);
        }
    }

    /**
     * Hide all boss bars for a player.
     */
    public void hideBossBars(Player player) {
        UUID playerId = player.getUniqueId();
        List<BossBar> bars = playerBars.remove(playerId);
        if (bars != null) {
            for (BossBar bar : bars) {
                player.hideBossBar(bar);
            }
        }
    }

    /**
     * Update boss bars when equipment changes.
     */
    public void updateBossBars(Player player) {
        if (playerBars.containsKey(player.getUniqueId())) {
            showBossBars(player); // Refresh display
        }
    }

    /**
     * Build ability title component for a bind slot.
     */
    private Component buildAbilityTitle(Player player, int slot, List<String> sigilIds, BindSystem system) {
        if (sigilIds.isEmpty()) return null;

        // Get player's activation hotkey for display
        PlayerBindData data = plugin.getBindsManager().getPlayerData(player);
        String activationKey = getActivationKeyDisplay(data.getHeldSlotHotkey());

        Component title = Component.empty();

        // For HOTBAR system:
        //   - For the CURRENTLY HELD slot: show held slot hotkey (F/LMB/RMB)
        //   - For other slots: show slot number (1-9)
        // For COMMAND system: show activation key
        if (system == BindSystem.HOTBAR) {
            int currentHeldSlot = player.getInventory().getHeldItemSlot() + 1;  // 0-8 -> 1-9
            if (slot == currentHeldSlot) {
                title = title.append(Component.text("[" + activationKey + "] ")
                    .color(NamedTextColor.WHITE)
                    .decorate(TextDecoration.BOLD));
            } else {
                title = title.append(Component.text("[" + slot + "] ")
                    .color(NamedTextColor.GRAY)
                    .decorate(TextDecoration.BOLD));
            }
        } else {
            title = title.append(Component.text("[" + activationKey + "] ")
                .color(NamedTextColor.WHITE)
                .decorate(TextDecoration.BOLD));
        }

        // Add sigil names with equipped status
        boolean first = true;
        for (String sigilId : sigilIds) {
            Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
            if (sigil == null) continue;

            if (!first) {
                title = title.append(Component.text(" + ").color(NamedTextColor.AQUA));
            }
            first = false;

            // Get sigil name (strip tier suffix)
            String baseName = sigil.getName()
                    .replaceAll("\\s*&8\\[T\\d+\\]", "")
                    .replaceAll("\\s*\\[T\\d+\\]", "")
                    .trim();
            String displayName = TextUtil.stripColors(baseName);

            // Check if sigil is equipped
            boolean equipped = isSigilEquipped(player, sigilId);
            NamedTextColor color = equipped ? NamedTextColor.GREEN : NamedTextColor.RED;

            title = title.append(Component.text(displayName).color(color));
        }

        return title;
    }

    /**
     * Check if a sigil is equipped in player's armor or hotbar.
     */
    private boolean isSigilEquipped(Player player, String sigilId) {
        SocketManager socketManager = plugin.getSocketManager();

        // Check armor slots
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir()) continue;

            List<String> socketedData = socketManager.getSocketedSigilData(piece);
            for (String entry : socketedData) {
                String[] parts = entry.split(":");
                if (parts[0].equalsIgnoreCase(sigilId)) {
                    return true;
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
                    return true;
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
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Get display string for activation hotkey.
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
        playerBars.clear();
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
