package com.miracle.arcanesigils.gui.sigil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.GUILayout;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.utils.TextUtil;

/**
 * Handler for the SIGILS_MENU GUI.
 * This is the main entry point that lists all sigils with pagination and filtering.
 */
public class SigilsMenuHandler extends AbstractHandler {

    private static final int ITEMS_PER_PAGE = 18;
    private static final int[] SIGIL_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};

    public SigilsMenuHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        int page = session.getInt("page", 1);
        String filter = session.get("filter", String.class);
        openGUI(guiManager, player, page, filter != null ? filter : "NONE");
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        int page = session.getInt("page", 1);
        String filter = session.get("filter", String.class);
        if (filter == null) {
            filter = "NONE";
        }

        // Handle sigil item clicks (slots 0-17)
        if (slot >= 0 && slot <= 17) {
            List<Sigil> sortedSigils = getSortedSigils(filter);
            int index = (page - 1) * ITEMS_PER_PAGE + slot;

            if (index < sortedSigils.size()) {
                Sigil sigil = sortedSigils.get(index);

                if (event.isShiftClick() && event.isRightClick()) {
                    // Shift+Right: Delete sigil (with confirmation)
                    String confirmKey = "deleteConfirm_" + sigil.getId();
                    Boolean confirmed = session.get(confirmKey, Boolean.class);
                    if (confirmed == null || !confirmed) {
                        session.put(confirmKey, true);
                        player.sendMessage(TextUtil.colorize("§eShift+Right-click again to delete §f" + sigil.getName()));
                        playSound(player, "error");
                    } else {
                        // Confirmed - delete
                        plugin.getSigilManager().deleteSigil(sigil.getId());
                        player.sendMessage(TextUtil.colorize("§cDeleted sigil: §f" + sigil.getName()));
                        playSound(player, "click");
                        openGUI(guiManager, player, page, filter);
                    }
                } else if (event.isShiftClick()) {
                    // Shift+Left: Give Tier 1 item
                    int tier = 1;
                    Sigil tieredSigil = plugin.getSigilManager().getSigilWithTier(sigil.getId(), tier);
                    ItemStack sigilItem = plugin.getSigilManager().createSigilItem(tieredSigil);
                    player.getInventory().addItem(sigilItem);
                    player.sendMessage(TextUtil.colorize("§aGave you §f" + sigil.getName() + " §a(Tier " + tier + ")"));
                    playSound(player, "socket");
                } else if (event.isRightClick()) {
                    // Right: Give max tier item
                    int tier = sigil.getMaxTier();
                    Sigil tieredSigil = plugin.getSigilManager().getSigilWithTier(sigil.getId(), tier);
                    ItemStack sigilItem = plugin.getSigilManager().createSigilItem(tieredSigil);
                    player.getInventory().addItem(sigilItem);
                    player.sendMessage(TextUtil.colorize("§aGave you §f" + sigil.getName() + " §a(Tier " + tier + ")"));
                    playSound(player, "socket");
                } else {
                    // Left click: Open editor
                    playSound(player, "click");
                    // Pass current page/filter so we can return to the same view
                    SigilEditorHandler.openGUI(guiManager, player, sigil, page, filter);
                }
            }
            return;
        }

        switch (slot) {
            case GUILayout.BACK -> {
                // Close GUI (no previous menu)
                playSound(player, "close");
                player.closeInventory();
            }
            case GUILayout.PREV_PAGE -> {
                // Previous page
                if (page > 1) {
                    playSound(player, "page");
                    session.put("page", page - 1);
                    refreshGUI(guiManager, player, session, page - 1, filter);
                } else {
                    playSound(player, "error");
                }
            }
            case GUILayout.PAGE_INDICATOR -> {
                // No action for page indicator
            }
            case GUILayout.CREATE_SIGIL -> {
                // Request sigil ID and create new sigil
                playSound(player, "click");
                final int finalPage = page;
                final String finalFilter = filter;
                guiManager.getInputHelper().requestText(player, "New Sigil ID", "",
                    id -> {
                        if (id == null || id.trim().isEmpty()) {
                            player.sendMessage(TextUtil.colorize("§cInvalid sigil ID!"));
                            openGUI(guiManager, player, finalPage, finalFilter);
                            return;
                        }

                        // Check if sigil already exists
                        if (plugin.getSigilManager().getSigil(id) != null) {
                            player.sendMessage(TextUtil.colorize("§cSigil with ID §f" + id + " §calready exists!"));
                            openGUI(guiManager, player, finalPage, finalFilter);
                            return;
                        }

                        // Create new sigil
                        Sigil newSigil = new Sigil(id);
                        newSigil.setName(id);

                        player.sendMessage(TextUtil.colorize("§aCreated new sigil: §f" + id));
                        SigilEditorHandler.openGUI(guiManager, player, newSigil);
                    },
                    () -> openGUI(guiManager, player, finalPage, finalFilter)
                );
            }
            case GUILayout.BROWSE_BEHAVIORS -> {
                // Open behaviors browser
                playSound(player, "click");
                com.miracle.arcanesigils.gui.behavior.BehaviorBrowserHandler.openGUI(guiManager, player);
            }
            case GUILayout.CREATE_BEHAVIOR -> {
                // Request behavior ID and create new behavior
                playSound(player, "click");
                final int finalPage2 = page;
                final String finalFilter2 = filter;
                guiManager.getInputHelper().requestText(player, "New Behavior ID", "",
                    id -> {
                        if (id == null || id.trim().isEmpty()) {
                            player.sendMessage(TextUtil.colorize("§cInvalid behavior ID!"));
                            openGUI(guiManager, player, finalPage2, finalFilter2);
                            return;
                        }

                        // Check if behavior already exists
                        if (plugin.getSigilManager().getBehavior(id) != null) {
                            player.sendMessage(TextUtil.colorize("§cBehavior with ID §f" + id + " §calready exists!"));
                            openGUI(guiManager, player, finalPage2, finalFilter2);
                            return;
                        }

                        // Create new behavior (sigil with type: BEHAVIOR)
                        Sigil newBehavior = new Sigil(id);
                        newBehavior.setName(id);
                        newBehavior.setSigilType(Sigil.SigilType.BEHAVIOR);

                        // Save to behaviors folder
                        plugin.getSigilManager().saveBehavior(newBehavior);

                        player.sendMessage(TextUtil.colorize("§dCreated new behavior: §f" + id));
                        player.sendMessage(TextUtil.colorize("§7Add flows with signals: §fEFFECT_STATIC§7, §fTICK§7, §fEXPIRE"));
                        SigilEditorHandler.openGUI(guiManager, player, newBehavior);
                    },
                    () -> openGUI(guiManager, player, finalPage2, finalFilter2)
                );
            }
            case GUILayout.FILTER -> {
                // Cycle filter mode
                String newFilter = cycleFilter(filter);
                playSound(player, "click");
                openGUI(guiManager, player, 1, newFilter); // Reset to page 1 when changing filter
            }
            case 25 -> { // Next page button
                List<Sigil> sortedSigils = getSortedSigils(filter);
                int maxPage = (int) Math.ceil((double) sortedSigils.size() / ITEMS_PER_PAGE);

                if (page < maxPage) {
                    playSound(player, "page");
                    session.put("page", page + 1);
                    refreshGUI(guiManager, player, session, page + 1, filter);
                } else {
                    playSound(player, "error");
                }
            }
        }
    }

    /**
     * Refresh GUI items in place without reopening (preserves cursor position).
     */
    private static void refreshGUI(GUIManager guiManager, Player player, GUISession session, int page, String filter) {
        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§8Arcane Sigils"));
        buildInventory(inv, page, filter);
        guiManager.updateGUI(player, inv, session);
    }

    /**
     * Build the inventory contents.
     */
    private static void buildInventory(Inventory inv, int page, String filter) {
        // Clear existing items first
        inv.clear();

        // Get sorted sigils
        List<Sigil> sortedSigils = getSortedSigils(filter);
        int maxPage = Math.max(1, (int) Math.ceil((double) sortedSigils.size() / ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, maxPage));

        // Fill sigil slots
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, sortedSigils.size());

        for (int i = startIndex; i < endIndex; i++) {
            Sigil sigil = sortedSigils.get(i);
            int slot = SIGIL_SLOTS[i - startIndex];
            inv.setItem(slot, createSigilItem(sigil));
        }

        // Bottom row
        inv.setItem(GUILayout.BACK, ItemBuilder.createItem(
            Material.RED_DYE,
            "§c← Close",
            "§7Close the menu"
        ));

        inv.setItem(GUILayout.PREV_PAGE, ItemBuilder.createPageArrow(false, page, maxPage));

        inv.setItem(GUILayout.PAGE_INDICATOR, ItemBuilder.createPageIndicator(
            page, maxPage, sortedSigils.size()
        ));

        inv.setItem(GUILayout.BROWSE_BEHAVIORS, ItemBuilder.createItem(
            Material.COMMAND_BLOCK,
            "§bBrowse Behaviors",
            "§7View and edit behaviors",
            "",
            "§8Behaviors define AI/effects for",
            "§8spawned entities and marks"
        ));

        inv.setItem(GUILayout.CREATE_BEHAVIOR, ItemBuilder.createItem(
            Material.SPAWNER,
            "§dCreate Behavior",
            "§7Create a behavior for marks",
            "§7or spawned entities",
            "",
            "§8Behaviors define effects that",
            "§8run while a mark/spawn is active"
        ));

        inv.setItem(GUILayout.CREATE_SIGIL, ItemBuilder.createItem(
            Material.NETHER_STAR,
            "§aCreate New Sigil",
            "§7Click to create a new sigil"
        ));

        inv.setItem(GUILayout.FILTER, createFilterItem(filter));

        inv.setItem(25, ItemBuilder.createPageArrow(true, page, maxPage)); // Next page

        // Fill remaining bottom row slots with background
        for (int slot = 18; slot < 27; slot++) {
            if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
                inv.setItem(slot, ItemBuilder.createItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "§8Arcane Sigils"
                ));
            }
        }
    }

    /**
     * Open the Sigils Menu GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player, int page, String filter) {
        if (filter == null) {
            filter = "NONE";
        }

        // Create inventory
        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§8Arcane Sigils"));

        // Normalize page
        List<Sigil> sortedSigils = getSortedSigils(filter);
        int maxPage = Math.max(1, (int) Math.ceil((double) sortedSigils.size() / ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, maxPage));

        // Build inventory contents
        buildInventory(inv, page, filter);

        // Create session
        GUISession session = new GUISession(GUIType.SIGILS_MENU);
        session.put("page", page);
        session.put("filter", filter);

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Create a sigil item for the menu.
     */
    private static ItemStack createSigilItem(Sigil sigil) {
        Material material = Material.ECHO_SHARD;
        if (sigil.getItemForm() != null && sigil.getItemForm().getMaterial() != null) {
            material = sigil.getItemForm().getMaterial();
        }

        String rarityColor = getRarityColor(sigil.getRarity());

        List<String> lore = new ArrayList<>();
        lore.add("§7ID: §f" + sigil.getId());
        lore.add("§7Rarity: " + rarityColor + sigil.getRarity());
        lore.add("§7Max Tier: §f" + sigil.getMaxTier());
        lore.add("");
        lore.add("§eLeft-click §7to edit");
        lore.add("§eRight-click §7to get max tier");
        lore.add("§eShift+Left §7to get tier 1");
        lore.add("§cShift+Right §7to delete");

        return ItemBuilder.createItem(material, sigil.getName(), lore);
    }

    /**
     * Create the filter item.
     */
    private static ItemStack createFilterItem(String currentFilter) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Current: §f" + currentFilter);
        lore.add("");
        lore.add("§7Filters:");
        lore.add((currentFilter.equals("NONE") ? "§a▸ " : "§7- ") + "NONE");
        lore.add((currentFilter.equals("RARITY") ? "§a▸ " : "§7- ") + "RARITY");
        lore.add((currentFilter.equals("MAX_TIER") ? "§a▸ " : "§7- ") + "MAX_TIER");
        lore.add((currentFilter.equals("ALPHABETICAL_ID") ? "§a▸ " : "§7- ") + "ALPHABETICAL_ID");
        lore.add("");
        lore.add("§eClick to cycle");

        return ItemBuilder.createItem(Material.COMPASS, "§eFilter", lore);
    }

    /**
     * Get sorted sigils based on filter.
     */
    private static List<Sigil> getSortedSigils(String filter) {
        List<Sigil> sigils = new ArrayList<>(
            ((ArmorSetsPlugin) Bukkit.getPluginManager().getPlugin("ArcaneSigils")).getSigilManager().getAllSigils()
        );

        if (filter == null) {
            filter = "NONE";
        }

        switch (filter) {
            case "RARITY" -> sigils.sort(Comparator.comparingInt(SigilsMenuHandler::getRarityPriority)
                .thenComparing(Sigil::getId));
            case "MAX_TIER" -> sigils.sort(Comparator.comparingInt(Sigil::getMaxTier)
                .reversed()
                .thenComparing(Sigil::getId));
            case "ALPHABETICAL_ID" -> sigils.sort(Comparator.comparing(Sigil::getId));
            default -> {
                // NONE - keep insertion order (no sorting)
            }
        }

        return sigils;
    }

    /**
     * Get rarity priority for sorting (lower = more common).
     */
    private static int getRarityPriority(Sigil sigil) {
        return switch (sigil.getRarity().toUpperCase()) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
    }

    /**
     * Get rarity color.
     */
    private static String getRarityColor(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> "§7";
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            default -> "§7";
        };
    }

    /**
     * Cycle to the next filter mode.
     */
    private static String cycleFilter(String currentFilter) {
        return switch (currentFilter) {
            case "NONE" -> "RARITY";
            case "RARITY" -> "MAX_TIER";
            case "MAX_TIER" -> "ALPHABETICAL_ID";
            case "ALPHABETICAL_ID" -> "NONE";
            default -> "NONE";
        };
    }
}
