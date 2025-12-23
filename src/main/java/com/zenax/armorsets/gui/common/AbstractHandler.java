package com.zenax.armorsets.gui.common;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Abstract base class for GUI handlers.
 */
public abstract class AbstractHandler {

    protected final ArmorSetsPlugin plugin;
    protected final GUIManager guiManager;

    protected AbstractHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * Handle a click event in the GUI.
     */
    public abstract void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event);

    /**
     * Handle inventory close event. Override in handlers that need to return items to players.
     * Called when inventory is closed by any means (ESC, clicking outside, etc.)
     */
    public void handleClose(Player player, GUISession session, InventoryCloseEvent event) {
        // Default: do nothing. Handlers can override if they need to return items.
    }

    /**
     * Play a sound to the player.
     */
    protected void playSound(Player player, String soundType) {
        switch (soundType.toLowerCase()) {
            case "click" -> player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            case "success" -> player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            case "error" -> player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            case "page" -> player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            case "open" -> player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            case "close" -> player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
            case "socket" -> player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
            case "unsocket" -> player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
            default -> player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    /**
     * Navigate back to parent GUI using session's parentType.
     * This is the centralized back navigation - handlers can override if needed.
     * Uses "click" sound for navigation (not "close" which is for session end).
     */
    protected void navigateBack(Player player, GUISession session) {
        GUIType parentType = session.get("parentType", GUIType.class);

        if (parentType == null) {
            // No parent - close entirely
            player.closeInventory();
            playSound(player, "close");
            return;
        }

        playSound(player, "click");

        // Create session with parent's data and reopen
        GUISession parentSession = new GUISession(parentType);
        // Copy relevant data
        session.getData().forEach((key, value) -> {
            if (!key.equals("parentType")) {
                parentSession.put(key, value);
            }
        });

        guiManager.reopenGUI(player, parentSession);
    }

    /**
     * @deprecated Use navigateBack instead for cleaner back navigation
     */
    @Deprecated
    protected void openPreviousGUI(Player player, GUISession session) {
        navigateBack(player, session);
    }

    /**
     * Reopen this GUI with the given session data.
     * Called by GUIManager.reopenGUI() when navigating back or refreshing.
     *
     * Handlers should override this to rebuild and display their GUI
     * using the session data (sigil, page, filter, etc.).
     *
     * Default implementation closes the inventory - handlers must override
     * to support navigation back to them.
     *
     * @param player The player to reopen the GUI for
     * @param session The session containing state to restore
     */
    public void reopen(Player player, GUISession session) {
        // Default: close inventory. Handlers override to support back navigation.
        player.closeInventory();
    }

    // ============ Breadcrumb Title Helpers ============

    /**
     * Build a breadcrumb title showing navigation context.
     * Example: "Sigils > Fire Burst > Config"
     *
     * @param current Current page name
     * @param session Session containing breadcrumb data
     * @return Formatted title with breadcrumb prefix
     */
    protected static String buildTitle(String current, GUISession session) {
        String breadcrumb = session != null ? session.get("breadcrumb", String.class) : null;
        if (breadcrumb != null && !breadcrumb.isEmpty()) {
            return "§7" + breadcrumb + " > §f" + current;
        }
        return "§8" + current;
    }

    /**
     * Build a simple title with dark gray prefix.
     * Example: "§8Sigil Editor: §fFire Burst"
     *
     * @param prefix Gray prefix text
     * @param value White value text
     * @return Formatted title
     */
    protected static String buildSimpleTitle(String prefix, String value) {
        return "§8" + prefix + ": §f" + value;
    }

    /**
     * Get short name for a GUIType (for breadcrumbs).
     * Converts SIGIL_EDITOR -> "Editor", TIER_CONFIG -> "Tiers"
     */
    protected static String getShortTypeName(GUIType type) {
        if (type == null) return "";
        return switch (type) {
            case SIGILS_MENU -> "Sigils";
            case SIGIL_EDITOR -> "Editor";
            case SIGIL_CONFIG -> "Config";
            case SIGNAL_SELECTOR -> "Signals";
            case TIER_CONFIG -> "Tiers";
            case TIER_XP_CONFIG -> "XP";
            case SOCKET -> "Socket";
            case UNSOCKET -> "Unsocket";
            case FLOW_BUILDER -> "Flow";
            case NODE_PALETTE -> "Nodes";
            case CONDITION_CONFIG -> "Conditions";
            case EFFECT_PARAM -> "Params";
            default -> type.name().replace("_", " ");
        };
    }
}
