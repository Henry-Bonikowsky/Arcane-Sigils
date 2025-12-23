package com.zenax.armorsets.gui.flow;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.flow.FlowConfig;
import com.zenax.armorsets.flow.FlowGraph;
import com.zenax.armorsets.flow.FlowType;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.GUILayout;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler for managing multiple flows on a sigil (primarily for BEHAVIOR type).
 * Shows a list of all flows with add/edit/delete options.
 *
 * Unlike regular sigils (which have one flow), behaviors can have multiple
 * flows with the same trigger (e.g., two ATTACK flows for mark + curse).
 */
public class FlowListHandler extends AbstractHandler {

    private static final int ITEMS_PER_PAGE = 18;
    private static final int[] FLOW_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};

    public FlowListHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        var v = session.validator(player);
        Sigil sigil = v.require("sigil", Sigil.class);
        if (v.handleInvalid()) return;

        int page = session.getInt("page", 1);

        // Handle flow item clicks (slots 0-17)
        if (slot >= 0 && slot <= 17) {
            List<FlowConfig> flows = sigil.getFlows();
            int index = (page - 1) * ITEMS_PER_PAGE + slot;

            if (index < flows.size()) {
                FlowConfig flow = flows.get(index);

                if (event.isLeftClick()) {
                    // Edit flow
                    playSound(player, "click");
                    session.put("editingFlowIndex", index);
                    FlowBuilderHandler.openGUI(
                        guiManager, player, sigil,
                        flow.getTrigger() != null ? flow.getTrigger() : "flow",
                        flow.getGraph(), flow
                    );
                } else if (event.isRightClick()) {
                    // Delete flow (with confirmation)
                    Boolean confirmed = session.get("deleteConfirm_" + index, Boolean.class);
                    if (confirmed == null || !confirmed) {
                        session.put("deleteConfirm_" + index, true);
                        player.sendMessage(TextUtil.colorize("&eRight-click again to delete this flow"));
                        playSound(player, "error");
                    } else {
                        // Confirmed - delete the flow
                        flows.remove(index);
                        player.sendMessage(TextUtil.colorize("&cFlow deleted"));
                        playSound(player, "click");
                        openGUI(guiManager, player, sigil, page);
                    }
                }
            }
            return;
        }

        switch (slot) {
            case GUILayout.BACK -> {
                playSound(player, "close");
                // Return to sigil editor
                com.zenax.armorsets.gui.sigil.SigilEditorHandler.openGUI(
                    guiManager, player, sigil,
                    session.getInt("parentPage", 1),
                    session.get("parentFilter", String.class)
                );
            }
            case GUILayout.PREV_PAGE -> {
                if (page > 1) {
                    playSound(player, "page");
                    openGUI(guiManager, player, sigil, page - 1);
                } else {
                    playSound(player, "error");
                }
            }
            case GUILayout.CREATE_SIGIL -> {
                // Create new flow
                playSound(player, "click");
                createNewFlow(player, session, sigil);
            }
            case GUILayout.NEXT_PAGE -> {
                List<FlowConfig> flows = sigil.getFlows();
                int maxPage = Math.max(1, (int) Math.ceil((double) flows.size() / ITEMS_PER_PAGE));

                if (page < maxPage) {
                    playSound(player, "page");
                    openGUI(guiManager, player, sigil, page + 1);
                } else {
                    playSound(player, "error");
                }
            }
        }
    }

    /**
     * Create a new flow - ask for trigger type first.
     * Opens SignalSelectorHandler which will create the flow and open FlowBuilder.
     */
    private void createNewFlow(Player player, GUISession session, Sigil sigil) {
        // Create a new empty FlowConfig - SignalSelectorHandler will set the trigger
        String flowId = "flow_" + System.currentTimeMillis();
        FlowConfig newFlow = new FlowConfig();
        newFlow.setType(FlowType.SIGNAL);
        newFlow.setGraph(new FlowGraph(flowId));
        newFlow.setChance(100.0);
        newFlow.setCooldown(0.0);

        // Add START and END nodes
        var startNode = new com.zenax.armorsets.flow.nodes.StartNode("start");
        startNode.setParam("flow_type", "SIGNAL");
        startNode.setParam("cooldown", 0.0);
        startNode.setParam("chance", 100.0);
        newFlow.getGraph().addNode(startNode);

        var endNode = new com.zenax.armorsets.flow.nodes.EndNode("end");
        endNode.setPosition(0, 1);
        newFlow.getGraph().addNode(endNode);

        // Connect START -> END (using "next" as the output port)
        newFlow.getGraph().connect(startNode.getId(), "next", endNode.getId());

        // Add flow to sigil now (behaviors allow multiple same triggers)
        // The trigger will be set when user selects one
        sigil.getFlows().add(newFlow);

        // Open signal selector - it will update the trigger and open FlowBuilder
        com.zenax.armorsets.gui.signal.SignalSelectorHandler.openGUI(
            guiManager, player, sigil, 1, null, newFlow, true);
    }

    /**
     * Open the Flow List GUI for a sigil.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil) {
        openGUI(guiManager, player, sigil, 1);
    }

    /**
     * Open the Flow List GUI for a sigil at a specific page.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, int page) {
        if (sigil == null) {
            player.sendMessage(TextUtil.colorize("&cError: Sigil not found!"));
            return;
        }

        List<FlowConfig> flows = sigil.getFlows();
        int maxPage = Math.max(1, (int) Math.ceil((double) flows.size() / ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, maxPage));

        // Title shows it's for flows
        String title = sigil.isBehavior() ? "Behavior Flows" : "Sigil Flows";
        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("&8" + title + " > &f" + sigil.getName()));

        // Calculate start/end indices
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, flows.size());

        // Fill flow slots
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int flowIndex = startIndex + i;
            int slot = FLOW_SLOTS[i];

            if (flowIndex < endIndex) {
                FlowConfig flow = flows.get(flowIndex);
                inv.setItem(slot, createFlowItem(flow, flowIndex));
            } else {
                inv.setItem(slot, new ItemStack(Material.AIR));
            }
        }

        // Bottom row controls
        inv.setItem(GUILayout.BACK, ItemBuilder.createBackButton("Sigil Editor"));

        inv.setItem(GUILayout.PREV_PAGE, ItemBuilder.createPageArrow(false, page, maxPage));

        inv.setItem(GUILayout.PAGE_INDICATOR, ItemBuilder.createPageIndicator(page, maxPage, flows.size()));

        inv.setItem(GUILayout.CREATE_SIGIL, ItemBuilder.createItem(
            Material.EMERALD,
            "&a&lAdd Flow",
            "&7Click to add a new flow",
            "",
            "&8Behaviors can have multiple flows",
            "&8with the same trigger type"
        ));

        inv.setItem(GUILayout.NEXT_PAGE, ItemBuilder.createPageArrow(true, page, maxPage));

        // Fill remaining bottom row slots with background
        for (int slot = 18; slot < 27; slot++) {
            if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
                inv.setItem(slot, ItemBuilder.createItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "&8Arcane Sigils"
                ));
            }
        }

        // Create and register session
        GUISession session = new GUISession(GUIType.FLOW_LIST);
        session.put("sigil", sigil);
        session.put("page", page);

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Create an item representing a flow.
     */
    private static ItemStack createFlowItem(FlowConfig flow, int index) {
        List<String> lore = new ArrayList<>();

        String trigger = flow.getTrigger() != null ? flow.getTrigger() : "UNKNOWN";
        boolean isAbility = flow.isAbility();

        // Material based on trigger type
        Material material = getMaterialForTrigger(trigger);

        // Flow info
        lore.add("&8Index: " + index);
        lore.add("");
        lore.add("&7Type: " + (isAbility ? "&dAbility" : "&6Signal"));
        lore.add("&7Trigger: &f" + trigger);

        if (flow.getCooldown() > 0) {
            lore.add("&7Cooldown: &f" + flow.getCooldown() + "s");
        }

        if (flow.getChance() < 100) {
            lore.add("&7Chance: &f" + (int) flow.getChance() + "%");
        }

        int nodeCount = flow.getGraph() != null ? flow.getGraph().getNodeCount() : 0;
        lore.add("&7Nodes: &f" + nodeCount);

        lore.add("");
        lore.add("&eLeft-click to edit");
        lore.add("&cRight-click to delete");

        return ItemBuilder.createItem(material, "&b" + trigger + " Flow", lore);
    }

    /**
     * Get a material icon based on trigger type.
     */
    private static Material getMaterialForTrigger(String trigger) {
        if (trigger == null) return Material.PAPER;

        return switch (trigger.toUpperCase()) {
            case "ATTACK", "ON_ATTACK" -> Material.IRON_SWORD;
            case "DEFENSE", "ON_DEFENSE", "DEFEND" -> Material.SHIELD;
            case "KILL", "KILL_MOB", "ON_KILL" -> Material.BONE;
            case "DEATH", "ENTITY_DEATH", "ON_DEATH" -> Material.SKELETON_SKULL;
            case "TICK" -> Material.CLOCK;
            case "EXPIRE" -> Material.BARRIER;
            case "PLAYER_NEAR" -> Material.COMPASS;
            case "PLAYER_STAND" -> Material.GRASS_BLOCK;
            case "SHIFT", "SNEAK", "ON_SNEAK" -> Material.LEATHER_BOOTS;
            case "JUMP", "ON_JUMP" -> Material.FEATHER;
            default -> Material.PAPER;
        };
    }
}
