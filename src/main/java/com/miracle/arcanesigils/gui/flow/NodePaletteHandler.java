package com.miracle.arcanesigils.gui.flow;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.effects.Effect;
import com.miracle.arcanesigils.flow.*;
import com.miracle.arcanesigils.flow.nodes.*;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Node type selection palette for adding nodes to a flow.
 *
 * Layout (27 slots - 3 rows):
 * Row 0: Control nodes (Start, Condition, Delay, Loop, Skip Cooldown)
 * Row 1: Data nodes (Variable) + Effects button
 * Row 2: [Back][---][---][---][---][---][---][---][---]
 */
public class NodePaletteHandler extends AbstractHandler {

    private static final int INVENTORY_SIZE = 27;
    private static final int SLOT_BACK = 18;

    // Control nodes (row 0)
    private static final int SLOT_START = 0;
    private static final int SLOT_CONDITION = 1;
    private static final int SLOT_DELAY = 2;
    private static final int SLOT_LOOP = 3;
    private static final int SLOT_SKIP_COOLDOWN = 4;
    private static final int SLOT_END = 5;

    // Data nodes (row 1)
    private static final int SLOT_VARIABLE = 9;
    private static final int SLOT_EFFECTS = 13; // Opens effect browser

    public NodePaletteHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        openGUI(guiManager, player, session);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        if (slot == SLOT_BACK) {
            handleBack(player, session);
            return;
        }

        if (slot == SLOT_EFFECTS) {
            // Open effect browser
            EffectNodeBrowserHandler.openGUI(guiManager, player, session);
            return;
        }

        NodeType type = getNodeTypeForSlot(slot);
        if (type != null) {
            addNodeToFlow(player, session, type, null);
        }
    }

    private NodeType getNodeTypeForSlot(int slot) {
        return switch (slot) {
            case SLOT_START -> NodeType.START;
            case SLOT_CONDITION -> NodeType.CONDITION;
            case SLOT_DELAY -> NodeType.DELAY;
            case SLOT_LOOP -> NodeType.LOOP;
            case SLOT_SKIP_COOLDOWN -> NodeType.SKIP_COOLDOWN;
            case SLOT_END -> NodeType.END;
            case SLOT_VARIABLE -> NodeType.VARIABLE;
            default -> null;
        };
    }

    private void addNodeToFlow(Player player, GUISession session, NodeType type, String effectType) {
        FlowGraph graph = session.get("flow", FlowGraph.class);
        if (graph == null) {
            player.sendMessage(TextUtil.colorize("§cError: No flow!"));
            return;
        }

        // Get position
        int gridX = session.getInt("addNodeX", 0);
        int gridY = session.getInt("addNodeY", 0);

        // Check if position is occupied
        if (graph.isPositionOccupied(gridX, gridY)) {
            player.sendMessage(TextUtil.colorize("§cPosition already occupied!"));
            playSound(player, "error");
            return;
        }

        // Check for duplicate start node
        if (type == NodeType.START && graph.getStartNode() != null) {
            player.sendMessage(TextUtil.colorize("§cFlow already has a Start node!"));
            playSound(player, "error");
            return;
        }

        // Create node
        String nodeId = graph.generateNodeId();
        FlowNode node = createNode(type, nodeId, effectType);
        node.setPosition(gridX, gridY);

        graph.addNode(node);

        // Set as start if it's a start node
        if (type == NodeType.START) {
            graph.setStartNodeId(nodeId);
        }

        player.sendMessage(TextUtil.colorize("§aAdded " + type.getDisplayName() + " node"));
        playSound(player, "success");

        // Select the new node and return to builder
        session.put("selectedNode", nodeId);
        session.remove("addNodeX");
        session.remove("addNodeY");

        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        FlowBuilderHandler.openGUI(guiManager, player, sigil, signalKey, graph, session);
    }

    public void addEffectNode(Player player, GUISession session, String effectType) {
        addNodeToFlow(player, session, NodeType.EFFECT, effectType);
    }

    private FlowNode createNode(NodeType type, String nodeId, String effectType) {
        return switch (type) {
            case START -> new StartNode(nodeId);
            case EFFECT -> {
                EffectNode node = new EffectNode(nodeId);
                node.setEffectType(effectType);
                yield node;
            }
            case CONDITION -> new ConditionNode(nodeId);
            case DELAY -> new DelayNode(nodeId);
            case LOOP -> new LoopNode(nodeId);
            case VARIABLE -> new VariableNode(nodeId);
            case SKIP_COOLDOWN -> new SkipCooldownNode(nodeId);
            case END -> new EndNode(nodeId);
        };
    }

    private void handleBack(Player player, GUISession session) {
        playSound(player, "click");
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        FlowGraph graph = session.get("flow", FlowGraph.class);
        FlowBuilderHandler.openGUI(guiManager, player, sigil, signalKey, graph, session);
    }

    public static void openGUI(GUIManager guiManager, Player player, GUISession flowSession) {
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE,
                TextUtil.parseComponent("§7Flow Builder > §fAdd Node"));

        // Control flow nodes
        inv.setItem(SLOT_START, ItemBuilder.createItem(NodeType.START.getMaterial(),
                "§a" + NodeType.START.getDisplayName(),
                "§7" + NodeType.START.getDescription(),
                "", "§eClick to add"));

        inv.setItem(SLOT_CONDITION, ItemBuilder.createItem(NodeType.CONDITION.getMaterial(),
                "§e" + NodeType.CONDITION.getDisplayName(),
                "§7" + NodeType.CONDITION.getDescription(),
                "§8Has 'yes' and 'no' outputs",
                "", "§eClick to add"));

        inv.setItem(SLOT_DELAY, ItemBuilder.createItem(NodeType.DELAY.getMaterial(),
                "§b" + NodeType.DELAY.getDisplayName(),
                "§7" + NodeType.DELAY.getDescription(),
                "", "§eClick to add"));

        inv.setItem(SLOT_LOOP, ItemBuilder.createItem(NodeType.LOOP.getMaterial(),
                "§d" + NodeType.LOOP.getDisplayName(),
                "§7" + NodeType.LOOP.getDescription(),
                "§8Count or While condition",
                "", "§eClick to add"));

        // Skip Cooldown node - prevents cooldown when condition fails
        inv.setItem(SLOT_SKIP_COOLDOWN, ItemBuilder.createItem(NodeType.SKIP_COOLDOWN.getMaterial(),
                "§c" + NodeType.SKIP_COOLDOWN.getDisplayName(),
                "§7" + NodeType.SKIP_COOLDOWN.getDescription(),
                "§8Place on condition 'no' branch",
                "§8to skip cooldown on failure",
                "", "§eClick to add"));

        // End node - marks flow termination
        inv.setItem(SLOT_END, ItemBuilder.createItem(NodeType.END.getMaterial(),
                "§c" + NodeType.END.getDisplayName(),
                "§7" + NodeType.END.getDescription(),
                "§8Terminal node - no connections",
                "", "§eClick to add"));

        // Data nodes
        inv.setItem(SLOT_VARIABLE, ItemBuilder.createItem(NodeType.VARIABLE.getMaterial(),
                "§e" + NodeType.VARIABLE.getDisplayName(),
                "§7" + NodeType.VARIABLE.getDescription(),
                "", "§eClick to add"));

        // Effects browser button
        inv.setItem(SLOT_EFFECTS, ItemBuilder.createItem(Material.DIAMOND,
                "§b+ Effect Node",
                "§7Browse all 50+ effects",
                "", "§eClick to browse effects"));

        // Fill empty slots
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, ItemBuilder.createBackground());
            }
            if (inv.getItem(i + 9) == null) {
                inv.setItem(i + 9, ItemBuilder.createBackground());
            }
        }

        // Back button
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("Flow Builder"));
        for (int i = 19; i < 27; i++) {
            inv.setItem(i, ItemBuilder.createBackground());
        }

        // Create session that preserves flow data
        GUISession session = new GUISession(GUIType.NODE_PALETTE);
        session.put("sigil", flowSession.get("sigil"));
        session.put("signalKey", flowSession.get("signalKey"));
        session.put("flow", flowSession.get("flow"));
        session.put("originalFlow", flowSession.get("originalFlow"));
        session.put("selectedNode", flowSession.get("selectedNode"));
        session.put("viewX", flowSession.get("viewX"));
        session.put("viewY", flowSession.get("viewY"));
        session.put("addNodeX", flowSession.get("addNodeX"));
        session.put("addNodeY", flowSession.get("addNodeY"));

        guiManager.openGUI(player, inv, session);
    }
}
