package com.miracle.arcanesigils.gui.common;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.utils.TextUtil;
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
     * Handle inventory close event. Override for cleanup (returning items, etc).
     */
    public void handleClose(Player player, GUISession session, InventoryCloseEvent event) {
    }

    /**
     * Reopen this GUI with given session data.
     * Called by GUIManager.reopenGUI() when navigating back.
     * Handlers must override to support back-navigation to them.
     */
    public void reopen(Player player, GUISession session) {
        player.closeInventory();
    }

    // ============ Sounds ============

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

    // ============ Navigation ============

    /**
     * Navigate back using session's parentType.
     * Used by enchanter GUIs and simple parent-child relationships.
     * For flow sub-GUIs, call the parent handler's openGUI directly instead.
     */
    protected void navigateBack(Player player, GUISession session) {
        GUIType parentType = session.get("parentType", GUIType.class);
        if (parentType == null) {
            player.closeInventory();
            playSound(player, "close");
            return;
        }
        playSound(player, "click");
        GUISession parentSession = new GUISession(parentType);
        session.getData().forEach((key, value) -> {
            if (!key.equals("parentType")) {
                parentSession.put(key, value);
            }
        });
        guiManager.reopenGUI(player, parentSession);
    }

    // ============ Confirmation Pattern ============

    /**
     * Require a click-again confirmation for destructive actions.
     * Returns false (not yet confirmed) on first click, true on second.
     *
     * Usage:
     *   if (!requireConfirmation(player, session, "delete_" + id, "§cClick again to confirm deletion!")) return;
     *   // ... destructive action here ...
     */
    protected boolean requireConfirmation(Player player, GUISession session,
                                           String confirmKey, String warningMessage) {
        Boolean confirmed = session.get(confirmKey, Boolean.class);
        if (confirmed == null || !confirmed) {
            session.put(confirmKey, true);
            player.sendMessage(TextUtil.colorize(warningMessage));
            playSound(player, "error");
            return false;
        }
        session.remove(confirmKey);
        return true;
    }

    // ============ Config Editing Pattern ============

    /**
     * Prompt player to edit a numeric config value, then save + hot-reload + reopen.
     * Subclasses override {@link #onConfigReload(String)} for domain-specific reloading.
     *
     * @param player    The editing player
     * @param configKey Full config path (e.g., "combat.soft-cap.threshold")
     * @param label     Human-readable label for the prompt
     * @param min       Minimum allowed value
     * @param max       Maximum allowed value
     * @param reopener  Runnable that reopens this GUI after edit
     */
    protected void promptConfigEdit(Player player, String configKey, String label,
                                     double min, double max, Runnable reopener) {
        playSound(player, "click");
        double current = plugin.getConfig().getDouble(configKey, 0.0);
        guiManager.getInputHelper().requestNumber(player, label, current, min, max,
                value -> {
                    plugin.getConfig().set(configKey, value);
                    plugin.saveConfig();
                    onConfigReload(configKey);
                    player.sendMessage("§a" + label + " set to §f" + value);
                    reopener.run();
                },
                reopener
        );
    }

    /**
     * Toggle a boolean config value, save + hot-reload + reopen.
     *
     * @param player    The editing player
     * @param configKey Full config path
     * @param label     Human-readable label
     * @param reopener  Runnable that reopens this GUI after toggle
     */
    protected void toggleConfigBool(Player player, String configKey, String label, Runnable reopener) {
        playSound(player, "click");
        boolean current = plugin.getConfig().getBoolean(configKey, false);
        boolean newValue = !current;
        plugin.getConfig().set(configKey, newValue);
        plugin.saveConfig();
        onConfigReload(configKey);
        player.sendMessage("§a" + label + " set to §f" + (newValue ? "enabled" : "disabled"));
        reopener.run();
    }

    /**
     * Called after a config value is changed. Override to hot-reload domain-specific systems.
     * Default: reloads CombatUtil for combat.* keys.
     */
    protected void onConfigReload(String configKey) {
        if (configKey.startsWith("combat.")) {
            plugin.getCombatUtil().loadConfig(plugin.getConfig());
        }
    }

    // ============ Title Helpers ============

    protected static String buildTitle(String current, GUISession session) {
        String breadcrumb = session != null ? session.get("breadcrumb", String.class) : null;
        if (breadcrumb != null && !breadcrumb.isEmpty()) {
            return "§7" + breadcrumb + " > §f" + current;
        }
        return "§8" + current;
    }

    protected static String buildSimpleTitle(String prefix, String value) {
        return "§8" + prefix + ": §f" + value;
    }

    protected static String getShortTypeName(GUIType type) {
        if (type == null) return "";
        return switch (type) {
            case SIGIL_FOLDER_BROWSER -> "Files";
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
