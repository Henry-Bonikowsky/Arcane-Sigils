package com.zenax.armorsets.gui.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.GUILayout;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.gui.sigil.SigilEditorHandler;
import com.zenax.armorsets.utils.TextUtil;

/**
 * Handler for browsing and managing behavior sigils.
 * Behaviors are sigils with type=BEHAVIOR used for spawned entity/block AI.
 */
public class BehaviorBrowserHandler extends AbstractHandler {

    private static final int ITEMS_PER_PAGE = 18;
    private static final int[] BEHAVIOR_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};

    public BehaviorBrowserHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        int page = session.getInt("page", 1);

        // Handle behavior item clicks (slots 0-17)
        if (slot >= 0 && slot <= 17) {
            List<Sigil> behaviors = getSortedBehaviors();
            int index = (page - 1) * ITEMS_PER_PAGE + slot;

            if (index < behaviors.size()) {
                Sigil behavior = behaviors.get(index);

                if (event.isRightClick()) {
                    // Right-click: Delete behavior (with confirmation)
                    String confirmKey = "deleteConfirm_" + behavior.getId();
                    Boolean confirmed = session.get(confirmKey, Boolean.class);
                    if (confirmed == null || !confirmed) {
                        session.put(confirmKey, true);
                        player.sendMessage(TextUtil.colorize("§eRight-click again to delete §f" + behavior.getName()));
                        playSound(player, "error");
                    } else {
                        // Confirmed - delete
                        plugin.getSigilManager().deleteBehavior(behavior.getId());
                        player.sendMessage(TextUtil.colorize("§cDeleted behavior: §f" + behavior.getName()));
                        playSound(player, "click");
                        openGUI(guiManager, player, page);
                    }
                } else {
                    // Left-click: Open behavior editor
                    playSound(player, "click");
                    SigilEditorHandler.openGUI(guiManager, player, behavior, page, "BEHAVIOR");
                }
            }
            return;
        }

        switch (slot) {
            case GUILayout.BACK -> {
                playSound(player, "close");
                player.closeInventory();
            }
            case GUILayout.PREV_PAGE -> {
                if (page > 1) {
                    playSound(player, "page");
                    openGUI(guiManager, player, page - 1);
                } else {
                    playSound(player, "error");
                }
            }
            case GUILayout.CREATE_SIGIL -> {
                // Create new behavior
                playSound(player, "click");
                final int finalPage = page;
                guiManager.getInputHelper().requestText(player, "Behavior ID", "",
                    id -> {
                        if (id == null || id.trim().isEmpty()) {
                            player.sendMessage(TextUtil.colorize("&cInvalid behavior ID!"));
                            openGUI(guiManager, player, finalPage);
                            return;
                        }

                        // Check if behavior already exists
                        if (plugin.getSigilManager().getBehavior(id) != null) {
                            player.sendMessage(TextUtil.colorize("&cBehavior with ID &f" + id + " &calready exists!"));
                            openGUI(guiManager, player, finalPage);
                            return;
                        }

                        // Create new behavior sigil
                        Sigil newBehavior = new Sigil(id);
                        newBehavior.setName(id);
                        newBehavior.setSigilType(Sigil.SigilType.BEHAVIOR);

                        // Register and save
                        plugin.getSigilManager().registerBehavior(newBehavior);
                        plugin.getSigilManager().saveBehavior(newBehavior);

                        player.sendMessage(TextUtil.colorize("&aCreated new behavior: &f" + id));
                        SigilEditorHandler.openGUI(guiManager, player, newBehavior, finalPage, "BEHAVIOR");
                    },
                    () -> openGUI(guiManager, player, finalPage)
                );
            }
            case GUILayout.NEXT_PAGE -> {
                List<Sigil> behaviors = getSortedBehaviors();
                int maxPage = Math.max(1, (int) Math.ceil((double) behaviors.size() / ITEMS_PER_PAGE));

                if (page < maxPage) {
                    playSound(player, "page");
                    openGUI(guiManager, player, page + 1);
                } else {
                    playSound(player, "error");
                }
            }
        }
    }

    /**
     * Open the Behavior Browser GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player) {
        openGUI(guiManager, player, 1);
    }

    /**
     * Open the Behavior Browser GUI at a specific page.
     */
    public static void openGUI(GUIManager guiManager, Player player, int page) {
        // Create inventory
        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("&8Entity Behaviors"));

        // Get sorted behaviors
        List<Sigil> behaviors = getSortedBehaviorsStatic();
        int maxPage = Math.max(1, (int) Math.ceil((double) behaviors.size() / ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, maxPage));

        // Calculate start/end indices
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, behaviors.size());

        // Fill behavior slots
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int sigilIndex = startIndex + i;
            int slot = BEHAVIOR_SLOTS[i];

            if (sigilIndex < endIndex) {
                Sigil behavior = behaviors.get(sigilIndex);
                inv.setItem(slot, createBehaviorItem(behavior));
            } else {
                // Empty slot
                inv.setItem(slot, new ItemStack(Material.AIR));
            }
        }

        // Bottom row controls
        inv.setItem(GUILayout.BACK, ItemBuilder.createItem(
            Material.RED_DYE,
            "&c<- Close",
            "&7Close the menu"
        ));

        inv.setItem(GUILayout.PREV_PAGE, ItemBuilder.createPageArrow(false, page, maxPage));

        inv.setItem(GUILayout.PAGE_INDICATOR, ItemBuilder.createPageIndicator(page, maxPage, behaviors.size()));

        inv.setItem(GUILayout.CREATE_SIGIL, ItemBuilder.createItem(
            Material.EMERALD,
            "&a&lCreate Behavior",
            "&7Click to create a new behavior"
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
        GUISession session = new GUISession(GUIType.BEHAVIOR_BROWSER);
        session.put("page", page);

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Create an item representing a behavior.
     */
    private static ItemStack createBehaviorItem(Sigil behavior) {
        List<String> lore = new ArrayList<>();
        lore.add("&8ID: " + behavior.getId());
        lore.add("");

        // Show flows
        if (behavior.hasFlow()) {
            lore.add("&7Flows:");
            for (com.zenax.armorsets.flow.FlowConfig flow : behavior.getFlows()) {
                String trigger = flow.getTrigger() != null ? flow.getTrigger() : "flow";
                int nodeCount = flow.getGraph() != null ? flow.getGraph().getNodeCount() : 0;
                lore.add("  &e" + trigger + " &7(" + nodeCount + " nodes)");
            }
        } else {
            lore.add("&7No flows configured");
        }

        lore.add("");
        lore.add("&eLeft-click to edit");
        lore.add("&cRight-click to delete");

        return ItemBuilder.createItem(Material.COMMAND_BLOCK, "&b" + behavior.getName(), lore);
    }

    /**
     * Get all behaviors sorted by name (instance version).
     */
    private List<Sigil> getSortedBehaviors() {
        List<Sigil> behaviors = new ArrayList<>(plugin.getSigilManager().getAllBehaviors());
        behaviors.sort(Comparator.comparing(Sigil::getName, String.CASE_INSENSITIVE_ORDER));
        return behaviors;
    }

    /**
     * Get all behaviors sorted by name (static version).
     */
    private static List<Sigil> getSortedBehaviorsStatic() {
        ArmorSetsPlugin plugin = (ArmorSetsPlugin) Bukkit.getPluginManager().getPlugin("ArcaneSigils");
        if (plugin == null) {
            return new ArrayList<>();
        }
        List<Sigil> behaviors = new ArrayList<>(plugin.getSigilManager().getAllBehaviors());
        behaviors.sort(Comparator.comparing(Sigil::getName, String.CASE_INSENSITIVE_ORDER));
        return behaviors;
    }
}
