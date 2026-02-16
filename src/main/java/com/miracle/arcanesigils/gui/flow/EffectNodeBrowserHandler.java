package com.miracle.arcanesigils.gui.flow;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.effects.Effect;
import com.miracle.arcanesigils.effects.EffectManager;
import com.miracle.arcanesigils.flow.FlowGraph;
import com.miracle.arcanesigils.flow.NodeType;
import com.miracle.arcanesigils.flow.nodes.EffectNode;
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
import java.util.Collection;
import java.util.List;

/**
 * Browser for selecting an effect type to add as an Effect node.
 * Paginated list of all 50+ effects.
 */
public class EffectNodeBrowserHandler extends AbstractHandler {

    private static final int INVENTORY_SIZE = 54;
    private static final int EFFECTS_PER_PAGE = 45;

    // Effect slots (rows 0-4)
    private static final int[] EFFECT_SLOTS = new int[45];
    static {
        for (int i = 0; i < 45; i++) {
            EFFECT_SLOTS[i] = i;
        }
    }

    // Bottom row
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV = 46;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 52;

    private final EffectManager effectManager;

    public EffectNodeBrowserHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
        this.effectManager = plugin.getEffectManager();
    }

    @Override
    public void reopen(Player player, GUISession session) {
        openGUI(guiManager, player, session);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        int page = session.getInt("page", 1);

        if (slot == SLOT_BACK) {
            handleBack(player, session);
        } else if (slot == SLOT_PREV) {
            if (page > 1) {
                session.put("page", page - 1);
                playSound(player, "page");
                refreshGUI(player, session);
            }
        } else if (slot == SLOT_NEXT) {
            List<Effect> effects = new ArrayList<>(effectManager.getAllEffects());
            int maxPage = (int) Math.ceil((double) effects.size() / EFFECTS_PER_PAGE);
            if (page < maxPage) {
                session.put("page", page + 1);
                playSound(player, "page");
                refreshGUI(player, session);
            }
        } else if (slot < 45) {
            handleEffectSelect(player, session, slot, page);
        }
    }

    private void handleEffectSelect(Player player, GUISession session, int slot, int page) {
        List<Effect> effects = new ArrayList<>(effectManager.getAllEffects());
        int index = (page - 1) * EFFECTS_PER_PAGE + slot;

        if (index >= effects.size()) {
            return;
        }

        Effect effect = effects.get(index);
        String effectType = effect.getId();

        // Add effect node to flow
        FlowGraph graph = session.get("flow", FlowGraph.class);
        if (graph == null) {
            player.sendMessage(TextUtil.colorize("§cError: No flow!"));
            return;
        }

        int gridX = session.getInt("addNodeX", 0);
        int gridY = session.getInt("addNodeY", 0);

        if (graph.isPositionOccupied(gridX, gridY)) {
            player.sendMessage(TextUtil.colorize("§cPosition already occupied!"));
            playSound(player, "error");
            return;
        }

        String nodeId = graph.generateNodeId();
        EffectNode node = new EffectNode(nodeId, effectType);
        node.setPosition(gridX, gridY);
        graph.addNode(node);

        player.sendMessage(TextUtil.colorize("§aAdded " + effectType + " effect node"));
        playSound(player, "success");

        session.put("selectedNode", nodeId);
        session.remove("addNodeX");
        session.remove("addNodeY");

        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        FlowBuilderHandler.openGUI(guiManager, player, sigil, signalKey, graph, session);
    }

    private void handleBack(Player player, GUISession session) {
        playSound(player, "click");
        NodePaletteHandler.openGUI(guiManager, player, session);
    }

    private void refreshGUI(Player player, GUISession session) {
        openGUI(guiManager, player, session);
    }

    public static void openGUI(GUIManager guiManager, Player player, GUISession flowSession) {
        EffectManager effectManager = ArmorSetsPlugin.getInstance().getEffectManager();
        List<Effect> effects = new ArrayList<>(effectManager.getAllEffects());

        int page = flowSession.getInt("page", 1);
        int maxPage = (int) Math.ceil((double) effects.size() / EFFECTS_PER_PAGE);
        page = Math.max(1, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE,
                TextUtil.parseComponent("§7Add Node > §fEffects"));

        // Effect items
        int startIndex = (page - 1) * EFFECTS_PER_PAGE;
        for (int i = 0; i < EFFECTS_PER_PAGE; i++) {
            int effectIndex = startIndex + i;
            if (effectIndex < effects.size()) {
                Effect effect = effects.get(effectIndex);
                inv.setItem(i, buildEffectItem(effect));
            }
        }

        // Bottom row
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("Node Palette"));
        inv.setItem(SLOT_PREV, ItemBuilder.createPageArrow(false, page, maxPage));
        inv.setItem(SLOT_PAGE, ItemBuilder.createPageIndicator(page, maxPage, effects.size()));
        inv.setItem(SLOT_NEXT, ItemBuilder.createPageArrow(true, page, maxPage));

        // Fill empty bottom row
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, ItemBuilder.createBackground());
            }
        }

        // Create session preserving flow data
        GUISession session = new GUISession(GUIType.EFFECT_NODE_BROWSER);
        session.put("sigil", flowSession.get("sigil"));
        session.put("signalKey", flowSession.get("signalKey"));
        session.put("flow", flowSession.get("flow"));
        session.put("originalFlow", flowSession.get("originalFlow"));
        session.put("selectedNode", flowSession.get("selectedNode"));
        session.put("viewX", flowSession.get("viewX"));
        session.put("viewY", flowSession.get("viewY"));
        session.put("addNodeX", flowSession.get("addNodeX"));
        session.put("addNodeY", flowSession.get("addNodeY"));
        session.put("page", page);

        guiManager.openGUI(player, inv, session);
    }

    private static ItemStack buildEffectItem(Effect effect) {
        String id = effect.getId();
        Material material = getEffectMaterial(id);

        List<String> lore = new ArrayList<>();
        if (effect.getDescription() != null) {
            lore.add("§7" + effect.getDescription());
        }
        lore.add("");
        lore.add("§eClick to add as node");

        return ItemBuilder.createItem(material,
                "§b" + TextUtil.toProperCase(id.replace("_", " ")),
                lore);
    }

    private static Material getEffectMaterial(String effectId) {
        return switch (effectId.toUpperCase()) {
            case "DEAL_DAMAGE" -> Material.IRON_SWORD;
            case "DAMAGE_BOOST" -> Material.DIAMOND_SWORD;
            case "REDUCE_DAMAGE" -> Material.SHIELD;
            case "LIFESTEAL" -> Material.GHAST_TEAR;
            case "HEAL" -> Material.GOLDEN_APPLE;
            case "ABSORBTION" -> Material.ENCHANTED_GOLDEN_APPLE;
            case "POTION" -> Material.POTION;
            case "PARTICLE" -> Material.FIREWORK_ROCKET;
            case "SOUND" -> Material.NOTE_BLOCK;
            case "MESSAGE" -> Material.OAK_SIGN;
            case "TELEPORT" -> Material.ENDER_PEARL;
            case "DASH" -> Material.FEATHER;
            case "KNOCKBACK" -> Material.STICK;
            case "PULL" -> Material.FISHING_ROD;
            case "LAUNCH" -> Material.FIREWORK_ROCKET;
            case "STUN" -> Material.COBWEB;
            case "MARK" -> Material.NAME_TAG;
            case "SPAWN_ENTITY" -> Material.SPAWNER;
            case "LIGHTNING" -> Material.TRIDENT;
            case "EXPLOSION" -> Material.TNT;
            case "IGNITE" -> Material.FIRE_CHARGE;
            case "FREEZING" -> Material.BLUE_ICE;
            case "SPAWN_DISPLAY" -> Material.ARMOR_STAND;
            case "GIVE_ITEM" -> Material.CHEST;
            case "CANCEL_EVENT" -> Material.BARRIER;
            case "SOULBOUND" -> Material.SOUL_LANTERN;
            default -> Material.PAPER;
        };
    }
}
