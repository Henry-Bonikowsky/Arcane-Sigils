package com.zenax.armorsets.gui;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.input.BookInputHelper;
import com.zenax.armorsets.gui.input.SignInputHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central manager for all GUI interactions in the Arcane Sigils plugin.
 */
public class GUIManager implements Listener {

    private final ArmorSetsPlugin plugin;
    private final Map<UUID, GUISession> activeSessions;
    private final SignInputHelper inputHelper;
    private final BookInputHelper bookInputHelper;

    // Handler instances will be added as they're created
    private final Map<GUIType, AbstractHandler> handlers;

    public GUIManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();
        this.inputHelper = new SignInputHelper(plugin);
        this.bookInputHelper = new BookInputHelper(plugin);
        this.handlers = new HashMap<>();

        // Register sigil handlers
        registerHandler(GUIType.SIGILS_MENU, new com.zenax.armorsets.gui.sigil.SigilsMenuHandler(plugin, this));
        registerHandler(GUIType.SIGIL_EDITOR, new com.zenax.armorsets.gui.sigil.SigilEditorHandler(plugin, this));
        registerHandler(GUIType.SIGIL_CONFIG, new com.zenax.armorsets.gui.sigil.SigilConfigHandler(plugin, this));
        registerHandler(GUIType.ITEM_SELECTOR, new com.zenax.armorsets.gui.sigil.ItemSelectorHandler(plugin, this));
        registerHandler(GUIType.SOCKETABLE_SELECTOR, new com.zenax.armorsets.gui.sigil.SocketableSelectorHandler(plugin, this));

        // Register behavior handlers
        registerHandler(GUIType.BEHAVIOR_BROWSER, new com.zenax.armorsets.gui.behavior.BehaviorBrowserHandler(plugin, this));

        // Register signal handlers
        registerHandler(GUIType.SIGNAL_SELECTOR, new com.zenax.armorsets.gui.signal.SignalSelectorHandler(plugin, this));

        // Register effect handlers
        // NOTE: EFFECT_CONFIG and EFFECT_SELECTOR removed - replaced by FlowBuilder
        registerHandler(GUIType.EFFECT_PARAM, new com.zenax.armorsets.gui.effect.EffectParamHandler(plugin, this));
        registerHandler(GUIType.ATTRIBUTE_SELECTOR, new com.zenax.armorsets.gui.effect.AttributeSelectorHandler(plugin, this));
        registerHandler(GUIType.PARTICLE_SELECTOR, new com.zenax.armorsets.gui.effect.ParticleSelectorHandler(plugin, this));
        registerHandler(GUIType.COLOR_SELECTOR, new com.zenax.armorsets.gui.effect.ColorSelectorHandler(plugin, this));
        registerHandler(GUIType.ITEM_PARTICLE_SELECTOR, new com.zenax.armorsets.gui.effect.ItemParticleSelectorHandler(plugin, this));
        registerHandler(GUIType.BLOCK_PARTICLE_SELECTOR, new com.zenax.armorsets.gui.effect.BlockParticleSelectorHandler(plugin, this));
        registerHandler(GUIType.SOUND_SELECTOR, new com.zenax.armorsets.gui.effect.SoundSelectorHandler(plugin, this));

        // Register socket handlers
        registerHandler(GUIType.SOCKET, new com.zenax.armorsets.gui.socket.SocketHandler(plugin, this));
        registerHandler(GUIType.UNSOCKET, new com.zenax.armorsets.gui.socket.UnsocketHandler(plugin, this));

        // Register tier handlers
        registerHandler(GUIType.TIER_CONFIG, new com.zenax.armorsets.gui.tier.TierConfigHandler(plugin, this));
        registerHandler(GUIType.TIER_XP_CONFIG, new com.zenax.armorsets.gui.tier.TierXPConfigHandler(plugin, this));
        registerHandler(GUIType.TIER_PARAM_EDITOR, new com.zenax.armorsets.gui.tier.TierParamEditorHandler(plugin, this));
        registerHandler(GUIType.TIER_PARAM_SELECTOR, new com.zenax.armorsets.gui.tier.TierParamSelectorHandler(plugin, this));
        registerHandler(GUIType.TIER_PROGRESS_VIEWER, new com.zenax.armorsets.gui.tier.TierProgressViewerHandler(plugin, this));

        // Register binds handlers
        registerHandler(GUIType.BINDS_HOTBAR, new com.zenax.armorsets.binds.gui.BindsHotbarHandler(plugin, this));
        registerHandler(GUIType.BINDS_COMMAND, new com.zenax.armorsets.binds.gui.BindsCommandHandler(plugin, this));
        registerHandler(GUIType.BINDS_EDITOR, new com.zenax.armorsets.binds.gui.BindsEditorHandler(plugin, this));

        // Register condition handlers
        registerHandler(GUIType.CONDITION_CONFIG, new com.zenax.armorsets.gui.condition.ConditionConfigHandler(plugin, this));
        registerHandler(GUIType.CONDITION_SELECTOR, new com.zenax.armorsets.gui.condition.ConditionSelectorHandler(plugin, this));
        registerHandler(GUIType.CONDITION_PARAM, new com.zenax.armorsets.gui.condition.ConditionParamHandler(plugin, this));
        registerHandler(GUIType.CONDITION_VALUE_BROWSER, new com.zenax.armorsets.gui.condition.ConditionValueBrowserHandler(plugin, this));
        registerHandler(GUIType.CONDITION_NODE_SELECTOR, new com.zenax.armorsets.gui.condition.FlowConditionSelectorHandler(plugin, this));

        // Register flow builder handlers
        registerHandler(GUIType.FLOW_LIST, new com.zenax.armorsets.gui.flow.FlowListHandler(plugin, this));
        registerHandler(GUIType.FLOW_BUILDER, new com.zenax.armorsets.gui.flow.FlowBuilderHandler(plugin, this));
        // FlowSettings removed - all config now in START node
        registerHandler(GUIType.NODE_PALETTE, new com.zenax.armorsets.gui.flow.NodePaletteHandler(plugin, this));
        registerHandler(GUIType.EFFECT_NODE_BROWSER, new com.zenax.armorsets.gui.flow.EffectNodeBrowserHandler(plugin, this));
        registerHandler(GUIType.NODE_CONFIG, new com.zenax.armorsets.gui.flow.NodeConfigHandler(plugin, this));

        // Register expression builder handlers
        registerHandler(GUIType.EXPRESSION_BUILDER, new com.zenax.armorsets.gui.flow.ExpressionBuilderHandler(plugin, this));
        registerHandler(GUIType.EXPRESSION_VALUE_SELECTOR, new com.zenax.armorsets.gui.flow.ExpressionValueSelectorHandler(plugin, this));
        registerHandler(GUIType.EXPRESSION_OPERATOR_SELECTOR, new com.zenax.armorsets.gui.flow.ExpressionOperatorSelectorHandler(plugin, this));
    }

    /**
     * Register a handler for a specific GUI type.
     */
    public void registerHandler(GUIType type, AbstractHandler handler) {
        handlers.put(type, handler);
    }

    /**
     * Open a GUI for a player with the given session.
     */
    public void openGUI(Player player, Inventory inventory, GUISession session) {
        // Close any existing GUI
        if (activeSessions.containsKey(player.getUniqueId())) {
            player.closeInventory();
        }

        // Store session
        activeSessions.put(player.getUniqueId(), session);

        // Open inventory
        player.openInventory(inventory);
    }

    /**
     * Reopen a GUI after navigation (e.g., going back).
     * Delegates to the handler's reopen() method.
     */
    public void reopenGUI(Player player, GUISession session) {
        AbstractHandler handler = handlers.get(session.getType());
        if (handler != null) {
            handler.reopen(player, session);
        } else {
            player.closeInventory();
            player.sendMessage("§cError: No handler for " + session.getType());
        }
    }

    /**
     * Get the active session for a player.
     */
    public GUISession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    /**
     * Close a GUI and remove the session.
     */
    public void closeGUI(Player player) {
        activeSessions.remove(player.getUniqueId());
        player.closeInventory();
    }

    /**
     * Close all active GUIs.
     */
    public void closeAll() {
        for (UUID uuid : activeSessions.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory();
            }
        }
        activeSessions.clear();
    }

    /**
     * Get the input helper for text/number requests.
     */
    public SignInputHelper getInputHelper() {
        return inputHelper;
    }

    /**
     * Get the book input helper for multiline text requests (descriptions).
     */
    public BookInputHelper getBookInputHelper() {
        return bookInputHelper;
    }

    // Event handlers

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        GUISession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        // Get clicked slot
        int slot = event.getRawSlot();

        // For SOCKET and UNSOCKET GUIs, allow shift-clicks from bottom inventory to be handled
        boolean isSocketGui = session.getType() == GUIType.SOCKET || session.getType() == GUIType.UNSOCKET;
        boolean isBottomInventoryShiftClick = event.isShiftClick() && slot >= event.getInventory().getSize();

        if (isSocketGui && isBottomInventoryShiftClick) {
            // Route shift-clicks to handler for socket/unsocket GUIs
            AbstractHandler handler = handlers.get(session.getType());
            if (handler != null) {
                handler.handleClick(player, session, slot, event);
            }
            return;
        }

        // Only cancel and handle clicks in the top inventory (the GUI)
        // Clicks in player's own inventory (bottom) should not be cancelled
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        // Cancel clicks in the GUI
        event.setCancelled(true);

        // Route to handler based on GUI type
        AbstractHandler handler = handlers.get(session.getType());
        if (handler != null) {
            handler.handleClick(player, session, slot, event);
        } else {
            player.sendMessage("§cError: No handler registered for " + session.getType());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        GUISession session = activeSessions.get(uuid);

        if (session != null) {
            // Call the handler's close method to handle item returns
            AbstractHandler handler = handlers.get(session.getType());
            if (handler != null) {
                handler.handleClose(player, session, event);
            }

            // Remove session on close
            // Note: Don't remove if we're reopening a GUI (the reopen will update the session)
            // To handle this properly, we'll use a delayed task
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Check if player still has no custom inventory open
                // When no GUI is open, the top inventory is the crafting grid (size 4 or 5)
                // Our GUIs are typically 27, 36, 45, or 54 slots
                int topSize = player.getOpenInventory().getTopInventory().getSize();
                if (topSize <= 5) {
                    // Player has no custom GUI open - remove session
                    activeSessions.remove(uuid);
                }
            }, 1L);
        }
    }

    // Helper methods for opening specific GUIs
    // These will be implemented as handlers are created

    /**
     * Open the main Sigils menu (browser).
     */
    public void openSigilsMenu(Player player) {
        openSigilsMenu(player, 1);
    }

    /**
     * Open the main Sigils menu at a specific page.
     */
    public void openSigilsMenu(Player player, int page) {
        com.zenax.armorsets.gui.sigil.SigilsMenuHandler.openGUI(this, player, page, "NONE");
    }

    /**
     * Open the Sigil Editor GUI for editing a sigil.
     */
    public void openSigilEditor(Player player, Sigil sigil) {
        com.zenax.armorsets.gui.sigil.SigilEditorHandler.openGUI(this, player, sigil);
    }

    /**
     * Open the Sigil Config GUI (item display, socketable items, tiers).
     */
    public void openSigilConfig(Player player, Sigil sigil) {
        com.zenax.armorsets.gui.sigil.SigilConfigHandler.openGUI(plugin, this, player, sigil);
    }


    /**
     * Open the Socket GUI for socketing a sigil.
     */
    public void openSocketGUI(Player player) {
        com.zenax.armorsets.gui.socket.SocketHandler.openGUI(this, player);
    }

    /**
     * Open the Unsocket GUI for removing a sigil.
     */
    public void openUnsocketGUI(Player player) {
        com.zenax.armorsets.gui.socket.UnsocketHandler.openGUI(this, player);
    }

    /**
     * Open the Tier Config GUI for a sigil.
     */
    public void openTierConfig(Player player, Sigil sigil) {
        com.zenax.armorsets.gui.tier.TierConfigHandler.openGUI(this, player, sigil);
    }

    /**
     * Open the Tier Config GUI from Flow Builder, with return navigation.
     * When back is clicked, auto-saves and returns to the flow builder.
     */
    public void openTierConfigFromFlowBuilder(Player player, Sigil sigil, String signalKey) {
        com.zenax.armorsets.gui.tier.TierConfigHandler.openGUI(this, player, sigil, null, null, null, signalKey);
    }

    /**
     * Open the Tier Progress GUI to view all equipped sigils' tier progression.
     */
    public void openTierProgress(Player player, Sigil sigil) {
        com.zenax.armorsets.gui.tier.TierProgressViewerHandler.openGUI(plugin, this, player);
    }

    /**
     * Open the Sigil Browser GUI for listing all sigils.
     */
    public void openSigilBrowser(Player player) {
        openSigilsMenu(player);
    }

    /**
     * Open the Tier XP Config GUI for a sigil.
     */
    public void openTierXPConfig(Player player, Sigil sigil) {
        com.zenax.armorsets.gui.tier.TierXPConfigHandler.openGUI(this, player, sigil);
    }

    /**
     * Open the Flow Builder GUI for a sigil's signal.
     */
    public void openFlowBuilder(Player player, Sigil sigil, String signalKey) {
        com.zenax.armorsets.flow.FlowConfig flowConfig = sigil.getFlowForTrigger(signalKey);
        com.zenax.armorsets.flow.FlowGraph flow = flowConfig != null ? flowConfig.getGraph() : null;
        // IMPORTANT: Pass flowConfig so conditions are preserved!
        com.zenax.armorsets.gui.flow.FlowBuilderHandler.openGUI(this, player, sigil, signalKey, flow, flowConfig, null);
    }

    /**
     * Get the Binds Hotbar Handler instance.
     */
    public com.zenax.armorsets.binds.gui.BindsHotbarHandler getBindsHotbarHandler() {
        AbstractHandler handler = handlers.get(GUIType.BINDS_HOTBAR);
        if (handler instanceof com.zenax.armorsets.binds.gui.BindsHotbarHandler) {
            return (com.zenax.armorsets.binds.gui.BindsHotbarHandler) handler;
        }
        return null;
    }

    /**
     * Get the Binds Command Handler instance.
     */
    public com.zenax.armorsets.binds.gui.BindsCommandHandler getBindsCommandHandler() {
        AbstractHandler handler = handlers.get(GUIType.BINDS_COMMAND);
        if (handler instanceof com.zenax.armorsets.binds.gui.BindsCommandHandler) {
            return (com.zenax.armorsets.binds.gui.BindsCommandHandler) handler;
        }
        return null;
    }
}
