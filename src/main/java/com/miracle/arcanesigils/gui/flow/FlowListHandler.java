package com.miracle.arcanesigils.gui.flow;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.flow.FlowGraph;
import com.miracle.arcanesigils.flow.FlowType;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.GUILayout;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.utils.TextUtil;
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
                    playSound(player, "click");
                    session.put("editingFlowIndex", index);

                    String signalKey = flow.getTrigger() != null ? flow.getTrigger() : "flow";

                    // Smart routing based on shift-click and tier params
                    boolean hasTierParams = sigil.getTierScalingConfig() != null &&
                                           !sigil.getTierScalingConfig().getParams().isEmpty();

                    if (event.isShiftClick()) {
                        // Shift-click = always open Flow Builder (advanced mode)
                        FlowBuilderHandler.openGUI(
                            guiManager, player, sigil,
                            signalKey, flow.getGraph(), flow
                        );
                    } else if (hasTierParams) {
                        // Has tier params = open Quick Param Editor (Alex-friendly)
                        com.miracle.arcanesigils.gui.params.QuickParamEditorHandler.openGUI(
                            guiManager, player, sigil, signalKey
                        );
                    } else {
                        // No tier params = open Flow Builder to create them
                        FlowBuilderHandler.openGUI(
                            guiManager, player, sigil,
                            signalKey, flow.getGraph(), flow
                        );
                    }
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
                com.miracle.arcanesigils.gui.sigil.SigilEditorHandler.openGUI(
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
            case 24 -> {
                // Create new flow
                playSound(player, "click");
                createNewFlow(player, session, sigil);
            }
            case 25 -> { // Next page button
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

        // Add START node (flows end naturally when there's no next connection)
        var startNode = new com.miracle.arcanesigils.flow.nodes.StartNode("start");
        startNode.setParam("flow_type", "SIGNAL");
        startNode.setParam("cooldown", 0.0);
        startNode.setParam("chance", 100.0);
        newFlow.getGraph().addNode(startNode);
        newFlow.getGraph().setStartNodeId("start");

        // Add flow to sigil now (behaviors allow multiple same triggers)
        // The trigger will be set when user selects one
        sigil.getFlows().add(newFlow);

        // Open signal selector - it will update the trigger and open FlowBuilder
        com.miracle.arcanesigils.gui.signal.SignalSelectorHandler.openGUI(
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
                inv.setItem(slot, createFlowItem(flow, flowIndex, sigil));
            } else {
                inv.setItem(slot, new ItemStack(Material.AIR));
            }
        }

        // Bottom row controls
        inv.setItem(GUILayout.BACK, ItemBuilder.createBackButton("Sigil Editor"));

        inv.setItem(GUILayout.PREV_PAGE, ItemBuilder.createPageArrow(false, page, maxPage));

        inv.setItem(GUILayout.PAGE_INDICATOR, ItemBuilder.createPageIndicator(page, maxPage, flows.size()));

        inv.setItem(24, ItemBuilder.createItem(
            Material.EMERALD,
            "&a&lAdd Flow",
            "&7Click to add a new flow",
            "",
            "&8Behaviors can have multiple flows",
            "&8with the same trigger type"
        ));

        inv.setItem(25, ItemBuilder.createPageArrow(true, page, maxPage)); // Next page

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
    private static ItemStack createFlowItem(FlowConfig flow, int index, Sigil sigil) {
        List<String> lore = new ArrayList<>();

        String trigger = flow.getTrigger() != null ? flow.getTrigger() : "UNKNOWN";
        boolean isAbility = flow.isAbility();

        // Material based on trigger type
        Material material = getMaterialForTrigger(trigger);

        // Display name with trigger prominently shown
        String displayName = flow.getTrigger() != null
            ? "&e" + flow.getTrigger() + " &7Flow"
            : "&7Unnamed Flow";

        // Flow info
        lore.add("&8Index: " + index);
        lore.add("");
        lore.add("&7Type: &f" + (flow.getType() == FlowType.SIGNAL ? "Signal" : "Ability"));
        if (flow.getTrigger() != null) {
            lore.add("&7Trigger: &f" + flow.getTrigger());
        }

        if (flow.getCooldown() < 0) {
            lore.add("&7Cooldown: &aTier-scaled");
        } else if (flow.getCooldown() > 0) {
            lore.add("&7Cooldown: &f" + flow.getCooldown() + "s");
        }

        if (flow.getChance() < 0) {
            lore.add("&7Chance: &aTier-scaled");
        } else if (flow.getChance() < 100) {
            lore.add("&7Chance: &f" + (int) flow.getChance() + "%");
        }

        int nodeCount = flow.getGraph() != null ? flow.getGraph().getNodeCount() : 0;
        lore.add("&7Nodes: &f" + nodeCount);

        lore.add("");

        // Smart routing hints based on tier params
        boolean hasTierParams = sigil.getTierScalingConfig() != null &&
                               !sigil.getTierScalingConfig().getParams().isEmpty();
        if (hasTierParams) {
            lore.add("&eClick: &7Quick edit params");
            lore.add("&eShift+Click: &7Advanced editor");
        } else {
            lore.add("&eClick to edit");
        }
        lore.add("&cRight-click to delete");

        return ItemBuilder.createItem(material, displayName, lore);
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
