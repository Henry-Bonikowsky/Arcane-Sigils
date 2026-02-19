package com.miracle.arcanesigils.gui.flow;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.flow.*;
import com.miracle.arcanesigils.flow.nodes.*;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.gui.sigil.SigilEditorHandler;
import com.miracle.arcanesigils.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Main Flow Builder GUI - visual canvas for editing effect flows.
 *
 * Layout (54 slots - 6 rows):
 * Row 0: [Cancel][Save][Test][Help][Templ][Conn][Delete][----][----]
 * Row 1: [  ↑  ][Node][Node][Node][Node][Node][Node][Node][Node]
 * Row 2: [  ←  ][Node][Node][Node][Node][Node][Node][Node][Node]
 * Row 3: [  →  ][Node][Node][Node][Node][Node][Node][Node][Node]
 * Row 4: [  ↓  ][Node][Node][Node][Node][Node][Node][Node][Node]
 * Row 5: [Add Node][----][----][----][----][----][----][----][----]
 *
 * Canvas: 8x4 grid (columns 1-8, rows 1-4)
 * Click node to select, click again to open full parameter editor
 */
public class FlowBuilderHandler extends AbstractHandler {

    // Layout constants
    private static final int INVENTORY_SIZE = 54;

    // Top row buttons
    private static final int SLOT_CANCEL = 0;
    private static final int SLOT_SAVE = 1;
    private static final int SLOT_TEST = 2;
    private static final int SLOT_HELP = 3;
    private static final int SLOT_TEMPLATES = 4;
    private static final int SLOT_CONNECT = 5;
    private static final int SLOT_DELETE = 6;
    private static final int SLOT_SETTINGS = 7;
    private static final int SLOT_TIER_CONFIG = 8;

    // Navigation (column 0)
    private static final int SLOT_NAV_UP = 9;
    private static final int SLOT_NAV_LEFT = 18;
    private static final int SLOT_NAV_RIGHT = 27;
    private static final int SLOT_NAV_DOWN = 36;
    private static final int SLOT_ADD_NODE = 45;

    // Canvas slots (8x4 grid - expanded from 5x4)
    private static final int[][] CANVAS_SLOTS = {
            {10, 11, 12, 13, 14, 15, 16, 17},  // Row 1
            {19, 20, 21, 22, 23, 24, 25, 26},  // Row 2
            {28, 29, 30, 31, 32, 33, 34, 35},  // Row 3
            {37, 38, 39, 40, 41, 42, 43, 44}   // Row 4
    };

    // Canvas dimensions
    private static final int CANVAS_WIDTH = 8;
    private static final int CANVAS_HEIGHT = 4;

    public FlowBuilderHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        FlowGraph graph = session.get("flow", FlowGraph.class);
        FlowConfig flowConfig = session.get("flowConfig", FlowConfig.class);

        if (sigil == null || graph == null) {
            player.closeInventory();
            return;
        }

        openGUIInternal(guiManager, player, sigil, signalKey, graph, flowConfig, session);
    }

    @Override
    public void handleClose(Player player, GUISession session, org.bukkit.event.inventory.InventoryCloseEvent event) {
        // Manual save mode - no auto-save on close
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        switch (slot) {
            case SLOT_CANCEL -> handleCancel(player, session);
            case SLOT_SAVE -> handleSave(player, session, event);
            case SLOT_TEST -> handleTest(player, session, event);
            case SLOT_HELP -> handleHelp(player);
            case SLOT_TEMPLATES -> handleTemplates(player, session);
            case SLOT_SETTINGS -> handleSettings(player, session);
            case SLOT_TIER_CONFIG -> handleTierConfig(player, session);
            case SLOT_NAV_UP -> handleNavigation(player, session, 0, -1);
            case SLOT_NAV_DOWN -> handleNavigation(player, session, 0, 1);
            case SLOT_NAV_LEFT -> handleNavigation(player, session, -1, 0);
            case SLOT_NAV_RIGHT -> handleNavigation(player, session, 1, 0);
            case SLOT_ADD_NODE -> handleAddNode(player, session);
            case SLOT_CONNECT -> handleStartConnect(player, session);
            case SLOT_DELETE -> handleDeleteNode(player, session);
            default -> {
                // Check canvas clicks
                int[] canvasPos = getCanvasPosition(slot);
                if (canvasPos != null) {
                    handleCanvasClick(player, session, canvasPos[0], canvasPos[1], event);
                }
            }
        }
    }

    private void handleCancel(Player player, GUISession session) {
        FlowGraph graph = session.get("flow", FlowGraph.class);
        FlowGraph original = session.get("originalFlow", FlowGraph.class);

        if (graph != null && original != null && !graphsEqual(graph, original)) {
            // Show confirmation
            player.sendMessage(TextUtil.colorize("§eDiscard changes? Type §f/as confirm §eto discard or click Save."));
            session.put("awaitingConfirm", true);
        } else {
            goBack(player, session);
        }
    }

    private void goBack(Player player, GUISession session) {
        playSound(player, "click");
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil != null) {
            SigilEditorHandler.openGUI(guiManager, player, sigil);
        } else {
            player.closeInventory();
        }
    }

    /**
     * Handle manual save button click.
     * Only saves if the flow is valid (no errors).
     */
    private void handleSave(Player player, GUISession session, InventoryClickEvent event) {
        Sigil sigil = session.get("sigil", Sigil.class);
        FlowGraph graph = session.get("flow", FlowGraph.class);

        if (graph == null || sigil == null) {
            player.sendMessage(TextUtil.colorize("§cNo flow to save!"));
            playSound(player, "error");
            return;
        }

        // Validate before saving
        List<String> errors = graph.validate();
        if (!errors.isEmpty()) {
            player.sendMessage(TextUtil.colorize("§cCannot save flow with errors:"));
            errors.forEach(e -> player.sendMessage(TextUtil.colorize("§c• " + e)));
            playSound(player, "error");
            return;
        }

        // Save using existing autoSave logic
        autoSave(player, session);

        player.sendMessage(TextUtil.colorize("§aFlow saved successfully!"));
        playSound(player, "success");

        // Refresh GUI to update save button state
        refreshGUI(player, session);
    }

    /**
     * Save the flow graph to the sigil's YAML file.
     * Only saves if the graph is valid (has start, end, and proper connections).
     */
    private void autoSave(Player player, GUISession session) {
        FlowGraph graph = session.get("flow", FlowGraph.class);
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);

        if (graph == null || sigil == null) return;

        List<String> errors = graph.validate();
        if (!errors.isEmpty()) {
            if (player != null) {
                player.sendMessage(net.kyori.adventure.text.Component.text(
                    TextUtil.colorize("&eFlow has errors - changes not saved")));
            }
            return;
        }

        FlowConfig flowConfig = session.get("flowConfig", FlowConfig.class);
        flowConfig = FlowSaveUtil.resolveFlowConfig(flowConfig, sigil, signalKey);
        session.put("flowConfig", flowConfig);
        flowConfig.setGraph(graph);

        FlowSaveUtil.syncStartNodeToConfig(graph, flowConfig);

        if (!FlowSaveUtil.saveFlowToSigil(sigil, flowConfig)) {
            if (player != null) {
                player.sendMessage(TextUtil.colorize("&cFailed to save flow! Check console for errors."));
            }
            return;
        }

        session.put("originalFlow", cloneGraph(graph));
    }

    private void handleTest(Player player, GUISession session, InventoryClickEvent event) {
        FlowGraph graph = session.get("flow", FlowGraph.class);
        Sigil sigil = session.get("sigil", Sigil.class);
        Integer tier = session.get("tier", Integer.class);

        if (graph == null) {
            player.sendMessage(TextUtil.colorize("§cNo flow to test!"));
            return;
        }

        // Validate first
        List<String> errors = graph.validate();
        if (!errors.isEmpty()) {
            player.sendMessage(TextUtil.colorize("§cFlow has errors - cannot test!"));
            errors.forEach(e -> player.sendMessage(TextUtil.colorize("§c• " + e)));
            playSound(player, "error");
            return;
        }

        player.closeInventory();

        // Create test context
        com.miracle.arcanesigils.effects.EffectContext effectContext =
                com.miracle.arcanesigils.effects.EffectContext.builder(player, com.miracle.arcanesigils.events.SignalType.INTERACT)
                        .location(player.getLocation())
                        .victim(player)
                        .build();

        if (sigil != null && tier != null) {
            effectContext.setMetadata("sourceSigilTier", tier);
        } else if (sigil != null) {
            effectContext.setMetadata("sourceSigilTier", 1);
        }

        // Execute with test mode enabled
        FlowContext flowContext = new FlowContext(effectContext);
        flowContext.setTestMode(true);

        try {
            FlowExecutor.executeWithContext(graph, flowContext);

            // Display execution tree
            player.sendMessage("");
            player.sendMessage(TextUtil.colorize("§b§lFlow Test Execution:"));
            List<String> trace = flowContext.getExecutionTrace();
            if (!trace.isEmpty()) {
                trace.forEach(line -> player.sendMessage(TextUtil.colorize(line)));
                player.sendMessage(TextUtil.colorize("§7Flow completed - " + trace.size() + " nodes executed"));
            } else {
                player.sendMessage(TextUtil.colorize("§7No nodes executed (empty flow)"));
            }
            player.sendMessage("");

            playSound(player, "success");
        } catch (Exception e) {
            player.sendMessage(TextUtil.colorize("§cFlow execution failed: " + e.getMessage()));
            e.printStackTrace();
            playSound(player, "error");
        }

        // Reopen GUI after delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reopen(player, session);
        }, 40L);
    }

    private void handleHelp(Player player) {
        playSound(player, "click");
        player.sendMessage(TextUtil.colorize("§e§l=== Flow Builder Help ==="));
        player.sendMessage(TextUtil.colorize("§7• §fClick empty slot§7: Add node there"));
        player.sendMessage(TextUtil.colorize("§7• §fClick node§7: Select it"));
        player.sendMessage(TextUtil.colorize("§7• §fClick selected node§7: Edit parameters"));
        player.sendMessage(TextUtil.colorize("§7• §fShift+Left click§7: Connect (§aYES§7 for conditions)"));
        player.sendMessage(TextUtil.colorize("§7• §fShift+Right click§7: Connect §cNO§7 (conditions only)"));
        player.sendMessage(TextUtil.colorize("§7• §fArrows§7: Pan the canvas view"));
        player.sendMessage(TextUtil.colorize("§7• §fDelete§7: Remove selected node"));
        player.sendMessage(TextUtil.colorize("§7• §fTest§7: Run the flow on yourself"));
    }

    private void handleTemplates(Player player, GUISession session) {
        playSound(player, "click");
        player.sendMessage(TextUtil.colorize("§eTemplates coming soon!"));
    }

    private void handleSettings(Player player, GUISession session) {
        playSound(player, "click");
        // Open START node config directly - all settings (signal type, chance, cooldown) are there
        FlowGraph graph = session.get("flow", FlowGraph.class);
        if (graph != null) {
            FlowNode startNode = graph.getStartNode();
            if (startNode != null) {
                session.put("selectedNode", startNode.getId());
                session.put("configNode", startNode);
                NodeConfigHandler.openGUI(guiManager, player, session, startNode, 0);
                return;
            }
        }
        player.sendMessage(TextUtil.colorize("§eNo START node found. Add nodes to your flow first."));
    }

    private void handleTierConfig(Player player, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil == null) {
            player.sendMessage(TextUtil.colorize("§cNo sigil found!"));
            playSound(player, "error");
            return;
        }

        String signalKey = session.get("signalKey", String.class);
        playSound(player, "click");
        guiManager.openTierConfigFromFlowBuilder(player, sigil, signalKey);
    }

    private void handleNavigation(Player player, GUISession session, int dx, int dy) {
        int viewX = session.getInt("viewX", 0);
        int viewY = session.getInt("viewY", 0);
        session.put("viewX", viewX + dx);
        session.put("viewY", viewY + dy);
        playSound(player, "click");
        refreshGUI(player, session);
    }

    private void handleAddNode(Player player, GUISession session) {
        playSound(player, "click");
        NodePaletteHandler.openGUI(guiManager, player, session);
    }

    private void handleStartConnect(Player player, GUISession session) {
        String selectedId = session.get("selectedNode", String.class);
        if (selectedId == null) {
            player.sendMessage(TextUtil.colorize("§cSelect a node first!"));
            playSound(player, "error");
            return;
        }

        Boolean connecting = session.get("connecting", Boolean.class);
        if (connecting != null && connecting) {
            // Cancel connection mode
            session.remove("connecting");
            session.remove("connectingPort");
            player.sendMessage(TextUtil.colorize("§eConnection cancelled."));
        } else {
            // Start connection mode
            FlowGraph graph = session.get("flow", FlowGraph.class);
            FlowNode node = graph != null ? graph.getNode(selectedId) : null;

            if (node != null) {
                List<String> ports = node.getOutputPorts();
                if (ports.isEmpty()) {
                    player.sendMessage(TextUtil.colorize("§cThis node has no outputs!"));
                    playSound(player, "error");
                    return;
                }

                session.put("connecting", true);
                if (ports.size() == 1) {
                    session.put("connectingPort", ports.get(0));
                    player.sendMessage(TextUtil.colorize("§aClick a target node to connect."));
                } else {
                    // Need to select port - show port selection
                    player.sendMessage(TextUtil.colorize("§eSelect output port:"));
                    for (int i = 0; i < ports.size(); i++) {
                        String port = ports.get(i);
                        String connected = node.getConnection(port);
                        String status = connected != null ? "§7(→ " + connected + ")" : "§8(empty)";
                        player.sendMessage(TextUtil.colorize("§f  " + (i + 1) + ". §e" + port + " " + status));
                    }
                    session.put("connectingPort", ports.get(0)); // Default to first
                }
            }
        }
        playSound(player, "click");
        refreshGUI(player, session);
    }

    private void handleDeleteNode(Player player, GUISession session) {
        String selectedId = session.get("selectedNode", String.class);
        FlowGraph graph = session.get("flow", FlowGraph.class);

        if (selectedId == null || graph == null) {
            player.sendMessage(TextUtil.colorize("§cSelect a node first!"));
            playSound(player, "error");
            return;
        }

        FlowNode node = graph.getNode(selectedId);
        if (node != null && node.getType() == NodeType.START) {
            player.sendMessage(TextUtil.colorize("§cCannot delete the Start node!"));
            playSound(player, "error");
            return;
        }

        graph.removeNode(selectedId);
        session.remove("selectedNode");
        player.sendMessage(TextUtil.colorize("§aNode deleted."));
        playSound(player, "click");
        refreshGUI(player, session);
    }

    private void handleCanvasClick(Player player, GUISession session, int canvasX, int canvasY, InventoryClickEvent event) {
        FlowGraph graph = session.get("flow", FlowGraph.class);
        if (graph == null) return;

        int viewX = session.getInt("viewX", 0);
        int viewY = session.getInt("viewY", 0);
        int gridX = viewX + canvasX;
        int gridY = viewY + canvasY;

        FlowNode nodeAtPos = graph.getNodeAt(gridX, gridY);

        Boolean connecting = session.get("connecting", Boolean.class);

        if (connecting != null && connecting) {
            // Completing a connection
            if (nodeAtPos != null) {
                String sourceId = session.get("selectedNode", String.class);
                String port = session.get("connectingPort", String.class);

                if (sourceId != null && port != null && !sourceId.equals(nodeAtPos.getId())) {
                    graph.connect(sourceId, port, nodeAtPos.getId());
                    player.sendMessage(TextUtil.colorize("§aConnected " + port + " → " + nodeAtPos.getDisplayName()));
                }
            }
            session.remove("connecting");
            session.remove("connectingPort");
            playSound(player, "click");
            refreshGUI(player, session);
            return;
        }

        if (event.isShiftClick() && nodeAtPos != null) {
            // Start connection from this node
            session.put("selectedNode", nodeAtPos.getId());

            // For nodes with multiple outputs (like CONDITION), use right-click for second port
            List<String> ports = nodeAtPos.getOutputPorts();
            if (ports.size() > 1 && event.isRightClick()) {
                // Shift+Right click = second port ("no" for conditions)
                session.put("connecting", true);
                session.put("connectingPort", ports.get(1)); // "no" port
                player.sendMessage(TextUtil.colorize("§aConnecting §e" + ports.get(1) + "§a port. Click target node."));
                playSound(player, "click");
                refreshGUI(player, session);
            } else {
                // Shift+Left click = first port ("yes" for conditions, "next" for others)
                handleStartConnect(player, session);
            }
            return;
        }

        if (nodeAtPos != null) {
            String selectedId = session.get("selectedNode", String.class);

            if (nodeAtPos.getId().equals(selectedId)) {
                // Already selected - open param editor
                playSound(player, "click");
                NodeConfigHandler.openGUI(guiManager, player, session, nodeAtPos, 0);
            } else {
                // Select node
                session.put("selectedNode", nodeAtPos.getId());
                playSound(player, "click");
                refreshGUI(player, session);
            }
        } else {
            // Empty slot - open node palette to add here
            session.put("addNodeX", gridX);
            session.put("addNodeY", gridY);
            NodePaletteHandler.openGUI(guiManager, player, session);
        }
    }

    private int[] getCanvasPosition(int slot) {
        for (int y = 0; y < CANVAS_HEIGHT; y++) {
            for (int x = 0; x < CANVAS_WIDTH; x++) {
                if (CANVAS_SLOTS[y][x] == slot) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private void refreshGUI(Player player, GUISession session) {
        // Build a new inventory with updated items
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE,
                Component.text("Flow Builder")); // Title doesn't matter for update
        buildInventory(inv, session);

        // Update in place without reopening (preserves cursor position)
        guiManager.updateGUI(player, inv, session);
    }

    // ============ Static Open Methods ============

    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey) {
        FlowConfig flowConfig = null;
        FlowGraph graph = null;

        // Try to get existing FlowConfig for this trigger from sigil's flows list
        if (sigil != null) {
            // Check for ABILITY flows first (exclusive sigils)
            if ("ABILITY".equals(signalKey)) {
                flowConfig = sigil.getAbilityFlow();
            }
            // Then check for SIGNAL flows
            if (flowConfig == null && signalKey != null) {
                flowConfig = sigil.getFlowForTrigger(signalKey);
            }
            // Fallback: check for ability flow on exclusive sigils even if signalKey doesn't match
            if (flowConfig == null && sigil.isExclusive()) {
                flowConfig = sigil.getAbilityFlow();
            }
            if (flowConfig != null && flowConfig.getGraph() != null) {
                graph = cloneGraph(flowConfig.getGraph());
            }
        }

        // If no existing flow found, create a new one
        if (flowConfig == null) {
            // Create ABILITY flow for exclusive sigils, SIGNAL flow otherwise
            if (sigil != null && sigil.isExclusive() && "ABILITY".equals(signalKey)) {
                flowConfig = new FlowConfig(FlowType.ABILITY);
            } else {
                flowConfig = new FlowConfig(FlowType.SIGNAL);
                flowConfig.setTrigger(signalKey != null ? signalKey : "ATTACK");
            }
            graph = createDefaultFlow();
            flowConfig.setGraph(graph);
        } else if (graph == null) {
            graph = createDefaultFlow();
        }

        openGUI(guiManager, player, sigil, signalKey, graph, flowConfig);
    }

    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey,
                               FlowGraph graph, FlowConfig flowConfig) {
        openGUIInternal(guiManager, player, sigil, signalKey, graph, flowConfig, null);
    }

    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey, FlowGraph graph) {
        openGUIInternal(guiManager, player, sigil, signalKey, graph, null, null);
    }

    // Backward compatible overload - session as last param
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey,
                               FlowGraph graph, GUISession existingSession) {
        openGUIInternal(guiManager, player, sigil, signalKey, graph, null, existingSession);
    }

    // Full method with FlowConfig and session
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey,
                               FlowGraph graph, FlowConfig flowConfig, GUISession existingSession) {
        openGUIInternal(guiManager, player, sigil, signalKey, graph, flowConfig, existingSession);
    }

    private static void openGUIInternal(GUIManager guiManager, Player player, Sigil sigil, String signalKey,
                               FlowGraph graph, FlowConfig flowConfig, GUISession existingSession) {

        // CRITICAL: If flowConfig is null, try to get it from existing session or sigil
        // This ensures conditions are NEVER lost during navigation
        if (flowConfig == null) {
            // First try existing session
            if (existingSession != null) {
                flowConfig = existingSession.get("flowConfig", FlowConfig.class);
            }
            // If still null, get from sigil
            if (flowConfig == null && sigil != null && signalKey != null) {
                flowConfig = sigil.getFlowForTrigger(signalKey);
            }
        }

        // Manual save mode - no auto-save on return from sub-GUIs

        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE,
                TextUtil.parseComponent("§7" + (sigil != null ? sigil.getName() : "New") + " > §fFlow Builder"));

        // Create or reuse session
        GUISession session;
        if (existingSession != null && existingSession.getType() == GUIType.FLOW_BUILDER) {
            session = existingSession;
            session.put("flow", graph);
            if (flowConfig != null) {
                session.put("flowConfig", flowConfig);
            }
        } else {
            session = new GUISession(GUIType.FLOW_BUILDER);
            session.put("sigil", sigil);
            session.put("signalKey", signalKey);
            session.put("flow", graph);
            session.put("originalFlow", cloneGraph(graph));
            session.put("viewX", 0);
            session.put("viewY", 0);
            if (flowConfig != null) {
                session.put("flowConfig", flowConfig);
            }
        }

        // Sync flow config values to START node so NodeConfigHandler displays correctly
        // CRITICAL: Must sync chance, cooldown, and priority - otherwise autoSave will overwrite
        // FlowConfig values with START node defaults (100% chance, 0 cooldown)
        if (graph != null && flowConfig != null) {
            FlowNode startNode = graph.getStartNode();
            if (startNode != null) {
                startNode.setParam("flow_type", flowConfig.getType().name());

                // Sync chance - only if not already set in START node params
                // This prevents overwriting user's changes during the session
                if (startNode.getParam("chance") == null && flowConfig.getChance() != 100.0) {
                    startNode.setParam("chance", flowConfig.getChance());
                }

                // Sync cooldown - only if not already set in START node params
                if (startNode.getParam("cooldown") == null && flowConfig.getCooldown() > 0) {
                    startNode.setParam("cooldown", flowConfig.getCooldown());
                }

                // Sync priority - only if not already set and not default
                if (startNode.getParam("priority") == null && flowConfig.getPriority() != 1) {
                    startNode.setParam("priority", flowConfig.getPriority());
                }
            }
        }

        buildInventory(inv, session);
        guiManager.openGUI(player, inv, session);
    }

    private static void buildInventory(Inventory inv, GUISession session) {
        FlowGraph graph = session.get("flow", FlowGraph.class);
        String selectedId = session.get("selectedNode", String.class);
        Boolean connecting = session.get("connecting", Boolean.class);
        int viewX = session.getInt("viewX", 0);
        int viewY = session.getInt("viewY", 0);

        // Top row buttons
        inv.setItem(SLOT_CANCEL, ItemBuilder.createItem(Material.BARRIER, "§c← Back", "§7Return to sigil editor"));

        // Save button - validates before saving
        List<String> errors = graph != null ? graph.validate() : List.of("No flow graph");
        if (errors.isEmpty()) {
            inv.setItem(SLOT_SAVE, ItemBuilder.createItem(
                Material.EMERALD_BLOCK, "§a§lSave Flow",
                "§7Click to save this flow",
                "",
                "§aFlow is valid and ready to save"
            ));
        } else {
            List<String> errorLore = new ArrayList<>();
            errorLore.add("§7Fix errors before saving:");
            errorLore.add("");
            errors.stream().limit(5).forEach(e -> errorLore.add("§c• " + e));
            if (errors.size() > 5) {
                errorLore.add("§c• ... and " + (errors.size() - 5) + " more");
            }
            inv.setItem(SLOT_SAVE, ItemBuilder.createItem(
                Material.BARRIER, "§c§lCannot Save",
                errorLore.toArray(new String[0])
            ));
        }

        inv.setItem(SLOT_TEST, ItemBuilder.createItem(Material.EMERALD, "§bTest", "§7Run flow on yourself"));
        inv.setItem(SLOT_HELP, ItemBuilder.createItem(Material.BOOK, "§eHelp", "§7Show controls"));
        inv.setItem(SLOT_TEMPLATES, ItemBuilder.createItem(Material.CHEST, "§6Templates", "§7Load/save templates"));

        // Connect and Delete buttons
        String connectText = (connecting != null && connecting) ? "§cCancel Connect" : "§eConnect";
        inv.setItem(SLOT_CONNECT, ItemBuilder.createItem(Material.STRING, connectText,
                "§7Connect selected node's output"));
        inv.setItem(SLOT_DELETE, ItemBuilder.createItem(Material.TNT, "§cDelete Node",
                "§7Remove selected node"));

        // Settings button - opens START node config
        inv.setItem(SLOT_SETTINGS, ItemBuilder.createItem(Material.COMPARATOR, "§6Settings",
                "§7Configure cooldown, chance,",
                "§7and signal type",
                "",
                "§eClick to open"));

        // Tier Config button
        inv.setItem(SLOT_TIER_CONFIG, ItemBuilder.createItem(Material.EXPERIENCE_BOTTLE, "§6Tier Scaling",
                "§7Set values for {damage}, {chance}, etc.",
                "",
                "§7These scale your ability by tier",
                "",
                "§eClick to configure"));

        // Navigation
        inv.setItem(SLOT_NAV_UP, ItemBuilder.createItem(Material.ARROW, "§f↑ Pan Up"));
        inv.setItem(SLOT_NAV_LEFT, ItemBuilder.createItem(Material.ARROW, "§f← Pan Left"));
        inv.setItem(SLOT_NAV_RIGHT, ItemBuilder.createItem(Material.ARROW, "§f→ Pan Right"));
        inv.setItem(SLOT_NAV_DOWN, ItemBuilder.createItem(Material.ARROW, "§f↓ Pan Down"));
        inv.setItem(SLOT_ADD_NODE, ItemBuilder.createItem(Material.NETHER_STAR, "§a+ Add Node", "§7Add a new node"));

        // Canvas (8x4 grid)
        if (graph != null) {
            for (int y = 0; y < CANVAS_HEIGHT; y++) {
                for (int x = 0; x < CANVAS_WIDTH; x++) {
                    int gridX = viewX + x;
                    int gridY = viewY + y;
                    FlowNode node = graph.getNodeAt(gridX, gridY);

                    ItemStack item;
                    if (node != null) {
                        item = buildNodeItem(node, node.getId().equals(selectedId), connecting != null && connecting);
                    } else {
                        item = ItemBuilder.createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                                "§8Empty", "§7Click to add node", "§7Position: " + gridX + ", " + gridY);
                    }
                    inv.setItem(CANVAS_SLOTS[y][x], item);
                }
            }
        }

        // Fill bottom row (row 5, slots 46-53) with background
        for (int i = 46; i <= 53; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, ItemBuilder.createBackground());
            }
        }
    }

    private static ItemStack buildNodeItem(FlowNode node, boolean selected, boolean connectMode) {
        // Use semantic icons for effect nodes, default material for others
        Material material;
        if (node.getType() == NodeType.EFFECT) {
            EffectNode en = (EffectNode) node;
            material = getEffectMaterial(en.getEffectType());
        } else {
            material = node.getType().getMaterial();
        }
        String name = (selected ? "§a▶ " : "§e") + node.getDisplayName();

        List<String> lore = new ArrayList<>();
        lore.add("§7Type: §f" + node.getType().name());
        lore.add("§7ID: §8" + node.getId());

        // Show connections
        if (!node.getConnections().isEmpty()) {
            lore.add("");
            lore.add("§7Outputs:");
            for (Map.Entry<String, String> conn : node.getConnections().entrySet()) {
                String target = conn.getValue() != null ? conn.getValue() : "§8(empty)";
                lore.add("§8  " + conn.getKey() + " → §f" + target);
            }
        }

        lore.add("");
        if (connectMode) {
            lore.add("§aClick to connect here");
        } else if (selected) {
            lore.add("§aClick again to edit params");
            // Show different connection hints for condition nodes
            if (node.getType() == NodeType.CONDITION) {
                lore.add("§eShift+Left click§7: connect §aYES");
                lore.add("§eShift+Right click§7: connect §cNO");
            } else if (node.getOutputPorts().size() > 1) {
                lore.add("§eShift+Left click§7: first output");
                lore.add("§eShift+Right click§7: second output");
            } else {
                lore.add("§eShift+Click to connect");
            }
        } else {
            lore.add("§7Click to select");
        }

        // Add glow effect for selected
        ItemStack item = ItemBuilder.createItem(material, name, lore);
        if (selected) {
            item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1);
            var meta = item.getItemMeta();
            if (meta != null) {
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /**
     * Get semantic icon material for an effect type.
     * Helps Alex instantly recognize what each effect does at a glance.
     */
    private static Material getEffectMaterial(String effectType) {
        if (effectType == null) return Material.NETHER_STAR;
        return switch (effectType.toUpperCase()) {
            case "DEAL_DAMAGE", "DAMAGE" -> Material.IRON_SWORD;
            case "DAMAGE_BOOST" -> Material.DIAMOND_SWORD;
            case "HEAL" -> Material.GOLDEN_APPLE;
            case "LIFESTEAL" -> Material.GHAST_TEAR;
            case "TELEPORT" -> Material.ENDER_PEARL;
            case "PARTICLE", "SHAPE" -> Material.FIREWORK_ROCKET;
            case "POTION_EFFECT", "POTION" -> Material.POTION;
            case "IGNITE" -> Material.FIRE_CHARGE;
            case "LIGHTNING" -> Material.LIGHTNING_ROD;
            case "KNOCKBACK", "LAUNCH", "PULL" -> Material.SLIME_BALL;
            case "STUN" -> Material.COBWEB;
            case "EXPLOSION" -> Material.TNT;
            case "MESSAGE" -> Material.PAPER;
            case "SOUND" -> Material.BELL;
            case "SPAWN_ENTITY", "SUMMON_MUMMY" -> Material.ZOMBIE_SPAWN_EGG;
            case "DODGE" -> Material.FEATHER;
            case "DASH", "GRAPPLE" -> Material.RABBIT_FOOT;
            case "ABSORBTION" -> Material.GOLDEN_CHESTPLATE;
            case "REDUCE_DAMAGE" -> Material.SHIELD;
            case "REFLECT_DAMAGE" -> Material.IRON_CHESTPLATE;
            case "CLEAVE" -> Material.NETHERITE_AXE;
            case "EXECUTE" -> Material.NETHERITE_SWORD;
            case "MARK" -> Material.TARGET;
            case "SPAWN_DISPLAY" -> Material.ARMOR_STAND;
            case "GIVE_ITEM" -> Material.CHEST;
            case "CANCEL_EVENT" -> Material.BARRIER;
            case "PHOENIX" -> Material.BLAZE_POWDER;
            case "CLEAR_NEGATIVE_EFFECTS" -> Material.MILK_BUCKET;
            case "SATURATE" -> Material.COOKED_BEEF;
            case "DISARM" -> Material.STICK;
            case "SWAP" -> Material.ENDER_EYE;
            case "MAX_HEALTH_BOOST" -> Material.RED_DYE;
            case "REPAIR_ARMOR" -> Material.ANVIL;
            case "DAMAGE_ARMOR" -> Material.IRON_PICKAXE;
            case "GROUND_SLAM" -> Material.HEAVY_CORE;
            case "STEAL_BUFFS" -> Material.MAGENTA_GLAZED_TERRACOTTA;
            case "RESIST_EFFECTS" -> Material.SHULKER_SHELL;
            case "DROP_HEAD" -> Material.PLAYER_HEAD;
            case "REMOVE_RANDOM_ENCHANT" -> Material.GRINDSTONE;
            case "DECREASE_SIGIL_TIER" -> Material.EXPERIENCE_BOTTLE;
            default -> Material.NETHER_STAR;
        };
    }

    private static FlowGraph createDefaultFlow() {
        FlowGraph graph = new FlowGraph("new_flow");
        StartNode start = new StartNode("start");
        start.setPosition(2, 1);
        graph.addNode(start);
        graph.setStartNodeId("start");
        // Flows end naturally when there's no next connection
        return graph;
    }

    private static FlowGraph cloneGraph(FlowGraph original) {
        if (original == null) return null;
        return original.deepCopy();
    }

    private boolean graphsEqual(FlowGraph a, FlowGraph b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        // Quick structural comparison
        if (a.getNodeCount() != b.getNodeCount()) return false;
        if (!java.util.Objects.equals(a.getStartNodeId(), b.getStartNodeId())) return false;

        // Compare all nodes
        for (FlowNode nodeA : a.getNodes()) {
            FlowNode nodeB = b.getNode(nodeA.getId());
            if (nodeB == null) return false;
            if (!nodesEqual(nodeA, nodeB)) return false;
        }

        return true;
    }

    private boolean nodesEqual(FlowNode a, FlowNode b) {
        if (!a.getType().equals(b.getType())) return false;
        if (a.getGridX() != b.getGridX() || a.getGridY() != b.getGridY()) return false;
        if (!a.getParams().equals(b.getParams())) return false;
        if (!a.getConnections().equals(b.getConnections())) return false;
        return true;
    }

}
