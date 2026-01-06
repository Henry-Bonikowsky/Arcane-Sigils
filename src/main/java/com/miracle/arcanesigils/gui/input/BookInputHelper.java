package com.miracle.arcanesigils.gui.input;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Helper class for requesting multiline text input from players via writable books.
 * Books allow much more text than signs (256 chars per page, multiple pages).
 */
public class BookInputHelper implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, BookSession> activeSessions = new HashMap<>();

    public BookInputHelper(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Request multiline text input via a writable book.
     *
     * @param player       The player to request input from
     * @param title        Title hint shown in book
     * @param defaultLines Default content (each string = one line, lines separated by newlines become separate lines)
     * @param onComplete   Called with list of lines when done
     * @param onCancel     Called if cancelled
     */
    public void requestBookInput(Player player, String title, List<String> defaultLines,
                                  Consumer<List<String>> onComplete, Runnable onCancel) {
        // Cancel any existing session
        cancelSession(player.getUniqueId());

        // Create writable book
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        // Set default content
        if (defaultLines != null && !defaultLines.isEmpty()) {
            StringBuilder content = new StringBuilder();
            for (String line : defaultLines) {
                if (content.length() > 0) {
                    content.append("\n");
                }
                content.append(line);
            }
            meta.addPage(content.toString());
        } else {
            meta.addPage("");
        }

        // Title hint in lore (visible when hovering)
        meta.setDisplayName("§e§l" + title);
        List<String> lore = new ArrayList<>();
        lore.add("§7Edit this book to set " + title.toLowerCase());
        lore.add("§7Click 'Done' when finished");
        lore.add("§c§oDrop book (Q) to cancel");
        meta.setLore(lore);

        book.setItemMeta(meta);

        // Close any open inventory first
        player.closeInventory();

        // Find where to put the book - prefer main hand if empty, otherwise find empty slot
        int bookSlot;
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();

        if (mainHandItem == null || mainHandItem.getType().isAir()) {
            // Main hand is empty, use it
            bookSlot = player.getInventory().getHeldItemSlot();
            player.getInventory().setItemInMainHand(book);
        } else {
            // Find first empty slot in hotbar (slots 0-8)
            bookSlot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item == null || item.getType().isAir()) {
                    bookSlot = i;
                    break;
                }
            }

            if (bookSlot == -1) {
                // No empty hotbar slot, find any empty slot
                bookSlot = player.getInventory().firstEmpty();
            }

            if (bookSlot == -1) {
                // Inventory is full - can't give book
                player.sendMessage("§cYour inventory is full! Please make room first.");
                if (onCancel != null) {
                    onCancel.run();
                }
                return;
            }

            player.getInventory().setItem(bookSlot, book);

            // Switch to the book slot if it's in hotbar
            if (bookSlot < 9) {
                player.getInventory().setHeldItemSlot(bookSlot);
            }
        }

        // Create session (no original item stored - we only use empty slots)
        BookSession session = new BookSession(player.getUniqueId(), bookSlot, onComplete, onCancel);
        activeSessions.put(player.getUniqueId(), session);

        // Player must right-click the book to edit it (openBook only works with WRITTEN_BOOK)
        player.sendMessage("§e§lRight-click the book in your hand to edit it.");
        player.sendMessage("§7Click 'Done' when finished, or drop the book (Q) to cancel.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBookEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        BookSession session = activeSessions.get(player.getUniqueId());

        if (session == null) {
            return;
        }

        // Get the new book meta
        BookMeta newMeta = event.getNewBookMeta();

        // Extract all lines from all pages
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= newMeta.getPageCount(); i++) {
            String page = newMeta.getPage(i);
            if (page != null && !page.isEmpty()) {
                // Split page into lines
                String[] pageLines = page.split("\n");
                for (String line : pageLines) {
                    lines.add(line);
                }
            }
        }

        // Remove the book from inventory (clear the slot we placed it in)
        removeBookFromInventory(player, session);

        // Remove session
        activeSessions.remove(player.getUniqueId());

        // Call callback
        if (session.onComplete != null) {
            Bukkit.getScheduler().runTask(plugin, () -> session.onComplete.accept(lines));
        }

        // Cancel the event to prevent book from being saved to inventory
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        BookSession session = activeSessions.get(player.getUniqueId());

        if (session == null) {
            return;
        }

        ItemStack dropped = event.getItemDrop().getItemStack();
        if (dropped.getType() == Material.WRITABLE_BOOK || dropped.getType() == Material.WRITTEN_BOOK) {
            // Remove the dropped book entity
            event.getItemDrop().remove();

            // Remove session and call cancel callback
            activeSessions.remove(player.getUniqueId());
            player.sendMessage("§cBook input cancelled.");

            if (session.onCancel != null) {
                Bukkit.getScheduler().runTask(plugin, session.onCancel);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelSession(event.getPlayer().getUniqueId());
    }

    /**
     * Cancel an active book input session.
     */
    public void cancelSession(UUID playerId) {
        BookSession session = activeSessions.remove(playerId);
        if (session != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                removeBookFromInventory(player, session);
                if (session.onCancel != null) {
                    Bukkit.getScheduler().runTask(plugin, session.onCancel);
                }
            }
        }
    }

    /**
     * Check if a player has an active book session.
     */
    public boolean hasActiveSession(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Remove the book from the player's inventory.
     */
    private void removeBookFromInventory(Player player, BookSession session) {
        // Clear the slot where we placed the book
        ItemStack itemInSlot = player.getInventory().getItem(session.bookSlot);
        if (itemInSlot != null &&
            (itemInSlot.getType() == Material.WRITABLE_BOOK || itemInSlot.getType() == Material.WRITTEN_BOOK)) {
            player.getInventory().setItem(session.bookSlot, null);
        } else {
            // Book might have moved, search for it
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null &&
                    (item.getType() == Material.WRITABLE_BOOK || item.getType() == Material.WRITTEN_BOOK)) {
                    player.getInventory().setItem(i, null);
                    break;
                }
            }
        }
    }

    /**
     * Clean up when plugin is disabled.
     */
    public void shutdown() {
        for (UUID playerId : new ArrayList<>(activeSessions.keySet())) {
            cancelSession(playerId);
        }
        HandlerList.unregisterAll(this);
    }

    /**
     * Represents an active book input session.
     */
    private static class BookSession {
        final UUID playerId;
        final int bookSlot;
        final Consumer<List<String>> onComplete;
        final Runnable onCancel;

        BookSession(UUID playerId, int bookSlot,
                   Consumer<List<String>> onComplete, Runnable onCancel) {
            this.playerId = playerId;
            this.bookSlot = bookSlot;
            this.onComplete = onComplete;
            this.onCancel = onCancel;
        }
    }
}
