package com.miracle.arcanesigils.gui.signal;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.events.SignalType;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handler for the SIGNAL_SELECTOR GUI.
 * Displays all available signal types for selection.
 *
 * Layout (3 rows = 27 slots):
 * Row 0: [1][1][1][1][1][1][1][1][1]
 * Row 1: [1][1][1][1][1][1][1][1][1]
 * Row 2: [X][&][_][_][%][_][_][_][&]
 *
 * Where:
 * 1 = All signal types (paginated, 18 per page)
 * X = Back (slot 18)
 * & = Page arrows (slots 19, 26)
 * % = Page indicator (slot 22)
 */
public class SignalSelectorHandler extends AbstractHandler {

    // Slot positions
    private static final int[] SIGNAL_SLOTS = new int[18]; // Slots 0-17
    static {
        for (int i = 0; i < 18; i++) {
            SIGNAL_SLOTS[i] = i;
        }
    }
    private static final int SLOT_BACK = 18;
    private static final int SLOT_PREV_PAGE = 19;
    private static final int SLOT_PAGE_INFO = 22;
    private static final int SLOT_NEXT_PAGE = 26;

    private static final int ITEMS_PER_PAGE = 18;

    public SignalSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil == null) {
            player.closeInventory();
            return;
        }
        int page = session.getInt("page", 1);
        openGUI(guiManager, player, sigil, page);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        var v = session.validator(player);
        Sigil sigil = v.require("sigil", Sigil.class);
        if (v.handleInvalid()) return;

        int page = v.requireInt("page", 1);

        // Handle different slot clicks
        if (slot == SLOT_BACK) {
            handleBack(player, session);
        } else if (slot == SLOT_PREV_PAGE) {
            handlePreviousPage(player, session, sigil, page);
        } else if (slot == SLOT_NEXT_PAGE) {
            handleNextPage(player, session, sigil, page);
        } else if (isSignalSlot(slot)) {
            handleSignalSelect(player, session, sigil, slot, page);
        } else {
            playSound(player, "click");
        }
    }

    /**
     * Check if slot is a signal slot.
     */
    private boolean isSignalSlot(int slot) {
        return slot >= 0 && slot < ITEMS_PER_PAGE;
    }

    /**
     * Handle back button - return to appropriate GUI.
     */
    private void handleBack(Player player, GUISession session) {
        playSound(player, "click");
        Sigil sigil = session.get("sigil", Sigil.class);
        String oldSignalKey = session.get("oldSignalKey", String.class);
        Boolean returnToSettings = session.get("returnToSettings", Boolean.class);
        com.miracle.arcanesigils.flow.FlowConfig flowConfig = session.get("flowConfig", com.miracle.arcanesigils.flow.FlowConfig.class);

        if (sigil == null) {
            player.closeInventory();
            return;
        }

        if (returnToSettings != null && returnToSettings) {
            // Was selecting trigger - go to Flow Builder where START node has config
            com.miracle.arcanesigils.gui.flow.FlowBuilderHandler.openGUI(guiManager, player, sigil,
                flowConfig != null && flowConfig.getTrigger() != null ? flowConfig.getTrigger() : "flow",
                flowConfig != null ? flowConfig.getGraph() : null, flowConfig);
        } else if (oldSignalKey != null) {
            // Was changing signal - go back to flow builder
            com.miracle.arcanesigils.gui.flow.FlowBuilderHandler.openGUI(guiManager, player, sigil, oldSignalKey);
        } else {
            // Was adding new signal - go back to sigil editor
            com.miracle.arcanesigils.gui.sigil.SigilEditorHandler.openGUI(guiManager, player, sigil);
        }
    }

    /**
     * Handle previous page navigation.
     */
    private void handlePreviousPage(Player player, GUISession session, Sigil sigil, int currentPage) {
        if (currentPage <= 1) {
            playSound(player, "error");
            return;
        }

        playSound(player, "page");
        openGUI(guiManager, player, sigil, currentPage - 1);
    }

    /**
     * Handle next page navigation.
     */
    private void handleNextPage(Player player, GUISession session, Sigil sigil, int currentPage) {
        List<SignalType> allSignals = Arrays.asList(SignalType.values());
        int maxPage = (int) Math.ceil((double) allSignals.size() / ITEMS_PER_PAGE);

        if (currentPage >= maxPage) {
            playSound(player, "error");
            return;
        }

        playSound(player, "page");
        openGUI(guiManager, player, sigil, currentPage + 1);
    }

    /**
     * Handle signal selection - update flow config trigger and return to settings.
     */
    private void handleSignalSelect(Player player, GUISession session, Sigil sigil, int slot, int page) {
        List<SignalType> allSignals = Arrays.asList(SignalType.values());

        // Calculate signal index
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int signalIndex = startIndex + slot;

        if (signalIndex >= allSignals.size()) {
            playSound(player, "click");
            return;
        }

        SignalType signalType = allSignals.get(signalIndex);
        String triggerKey = signalType.getConfigKey();

        // Get or create FlowConfig
        com.miracle.arcanesigils.flow.FlowConfig flowConfig = session.get("flowConfig", com.miracle.arcanesigils.flow.FlowConfig.class);

        if (flowConfig == null) {
            // Create new flow config with this trigger
            flowConfig = new com.miracle.arcanesigils.flow.FlowConfig(com.miracle.arcanesigils.flow.FlowType.SIGNAL);
            flowConfig.setChance(100.0);
            flowConfig.setCooldown(0.0);
        }

        // Update the FlowConfig's trigger
        flowConfig.setTrigger(triggerKey);
        player.sendMessage(TextUtil.colorize("§aTrigger set to: §f" + formatSignalName(signalType.name())));
        playSound(player, "success");

        // Go to Flow Builder - START node has all configuration
        com.miracle.arcanesigils.gui.flow.FlowBuilderHandler.openGUI(guiManager, player, sigil,
            triggerKey, flowConfig.getGraph(), flowConfig);
    }

    /**
     * Open the Signal Selector GUI for a player.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil) {
        openGUI(guiManager, player, sigil, 1, null);
    }

    /**
     * Open the Signal Selector GUI for a player at a specific page.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, int page) {
        openGUI(guiManager, player, sigil, page, null);
    }

    /**
     * Open the Signal Selector GUI for changing an existing signal.
     * @param oldSignalKey The key of the signal being replaced
     */
    public static void openGUIForChange(GUIManager guiManager, Player player, Sigil sigil, String oldSignalKey) {
        openGUI(guiManager, player, sigil, 1, oldSignalKey);
    }

    /**
     * Open the Signal Selector GUI for a player at a specific page.
     * @param oldSignalKey If not null, we're changing an existing signal (replace mode)
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, int page, String oldSignalKey) {
        openGUI(guiManager, player, sigil, page, oldSignalKey, null, false);
    }

    /**
     * Open the Signal Selector GUI for selecting a trigger for FlowConfig.
     * After selection, returns to FlowSettings.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, int page, String currentTrigger,
                               com.miracle.arcanesigils.flow.FlowConfig flowConfig, boolean returnToSettings) {
        if (sigil == null) {
            player.sendMessage(TextUtil.colorize("§cError: Sigil not found!"));
            return;
        }

        String title = returnToSettings ? "§8Select Trigger" : (currentTrigger != null ? "§8Change Signal Type" : "§8Select Signal Type");
        Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent(title));

        // Fill background with gray glass panes
        ItemBuilder.fillBackground(inv);

        // Get all signal types
        List<SignalType> allSignals = Arrays.asList(SignalType.values());
        int maxPage = (int) Math.ceil((double) allSignals.size() / ITEMS_PER_PAGE);
        page = Math.max(1, Math.min(page, maxPage));

        // Calculate start and end indices for this page
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allSignals.size());

        // Add signal items for this page
        for (int i = startIndex; i < endIndex; i++) {
            SignalType signalType = allSignals.get(i);
            int slot = i - startIndex;
            inv.setItem(SIGNAL_SLOTS[slot], buildSignalTypeItem(signalType, sigil));
        }

        // Row 2: Back, Page Navigation
        String backLabel = returnToSettings ? "Flow Builder" : (currentTrigger != null ? "Effect Config" : "Sigil Editor");
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton(backLabel));
        inv.setItem(SLOT_PREV_PAGE, ItemBuilder.createPageArrow(false, page, maxPage));
        inv.setItem(SLOT_PAGE_INFO, ItemBuilder.createPageIndicator(page, maxPage, allSignals.size()));
        inv.setItem(SLOT_NEXT_PAGE, ItemBuilder.createPageArrow(true, page, maxPage));

        // Create session
        GUISession session = new GUISession(GUIType.SIGNAL_SELECTOR);
        session.put("sigil", sigil);
        session.put("page", page);
        if (currentTrigger != null && !returnToSettings) {
            session.put("oldSignalKey", currentTrigger);
        }
        if (flowConfig != null) {
            session.put("flowConfig", flowConfig);
        }
        if (returnToSettings) {
            session.put("returnToSettings", true);
        }

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Build signal type item for selection.
     */
    private static ItemStack buildSignalTypeItem(SignalType signalType, Sigil sigil) {
        Material material = getSignalMaterial(signalType);

        // Format signal name
        String formattedName = formatSignalName(signalType.name());

        // Build lore
        List<String> lore = new ArrayList<>();
        lore.add("§7" + signalType.getDescription());
        lore.add("");

        // Check if sigil already has a flow with this trigger
        if (sigil.hasFlows()) {
            com.miracle.arcanesigils.flow.FlowConfig existingFlow = sigil.getFlows().get(0);
            if (existingFlow != null && signalType.getConfigKey().equalsIgnoreCase(existingFlow.getTrigger())) {
                lore.add("§a✓ Currently selected");
                lore.add("");
            }
        }

        lore.add("§7Click to select");

        return ItemBuilder.createItem(material, "§e" + formattedName, lore);
    }

    /**
     * Format signal name for display.
     */
    private static String formatSignalName(String signalTypeName) {
        // Convert ATTACK -> Attack, KILL_MOB -> Kill Mob
        String[] parts = signalTypeName.split("_");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return formatted.toString();
    }

    /**
     * Get material for signal type display.
     */
    public static Material getSignalMaterial(SignalType type) {
        if (type == null) return Material.BARRIER;

        return switch (type) {
            case ATTACK -> Material.IRON_SWORD;
            case DEFENSE -> Material.SHIELD;
            case KILL_MOB -> Material.BONE;
            case KILL_PLAYER -> Material.PLAYER_HEAD;
            case SHIFT -> Material.FEATHER;
            case FALL_DAMAGE -> Material.LEATHER_BOOTS;
            case EFFECT_STATIC -> Material.CLOCK;
            case BOW_SHOOT -> Material.ARROW;
            case BOW_HIT -> Material.TARGET;
            case TRIDENT_THROW -> Material.TRIDENT;
            case TICK -> Material.REPEATER;
            case BLOCK_BREAK -> Material.WOODEN_PICKAXE;
            case BLOCK_PLACE -> Material.GRASS_BLOCK;
            case INTERACT -> Material.STICK;
            case ITEM_BREAK -> Material.DAMAGED_ANVIL;
            case FISH -> Material.FISHING_ROD;
            // Behavior signals (for spawned entities/blocks)
            case ENTITY_DEATH -> Material.WITHER_SKELETON_SKULL;
            case PLAYER_NEAR -> Material.ENDER_EYE;
            case PLAYER_STAND -> Material.HEAVY_WEIGHTED_PRESSURE_PLATE;
            case EXPIRE -> Material.REDSTONE_TORCH;
            case PROJECTILE_HIT -> Material.FIRE_CHARGE;
            case OWNER_ATTACK -> Material.DIAMOND_SWORD;
            case OWNER_DEFEND -> Material.IRON_CHESTPLATE;
        };
    }
}
