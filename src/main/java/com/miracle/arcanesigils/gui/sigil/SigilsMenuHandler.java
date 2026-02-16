package com.miracle.arcanesigils.gui.sigil;

import java.util.ArrayList;
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
 * 6-row layout with 36 sigils per page and dedicated filter row.
 */
public class SigilsMenuHandler extends AbstractHandler {

    private static final int ITEMS_PER_PAGE = 36;
    private static final int[] SIGIL_SLOTS = new int[36];
    
    static {
        // Fill first 4 rows (0-35) with sigil slots
        for (int i = 0; i < 36; i++) {
            SIGIL_SLOTS[i] = i;
        }
    }

    public SigilsMenuHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        int page = session.getInt("page", 1);
        openGUI(guiManager, player, page);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        int page = session.getInt("page", 1);
        FilterState filterState = session.get("filterState", FilterState.class);
        if (filterState == null) {
            filterState = new FilterState();
            session.put("filterState", filterState);
        }

        // Handle sigil item clicks (slots 0-35)
        if (slot >= 0 && slot <= 35) {
            List<Sigil> allSigils = new ArrayList<>(plugin.getSigilManager().getAllSigils());
            List<Sigil> filteredSigils = filterState.applyFiltersAndSorting(allSigils);
            int index = (page - 1) * ITEMS_PER_PAGE + slot;

            if (index < filteredSigils.size()) {
                Sigil sigil = filteredSigils.get(index);

                if (event.isShiftClick()) {
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
                    SigilEditorHandler.openGUI(guiManager, player, sigil, page, null);
                }
            }
            return;
        }

        // Filter row buttons (row 4, slots 36-44)
        switch (slot) {
            case GUILayout.FILTER_RARITY -> {
                playSound(player, "click");
                RarityFilterHandler.openGUI(guiManager, player);
            }
            case GUILayout.FILTER_SLOT_TYPE -> {
                playSound(player, "click");
                SlotFilterHandler.openGUI(guiManager, player);
            }
            case GUILayout.FILTER_TIER_RANGE -> {
                playSound(player, "click");
                TierRangeFilterHandler.openGUI(guiManager, player);
            }
            case GUILayout.FILTER_CRATE -> {
                playSound(player, "click");
                CrateFilterHandler.openGUI(guiManager, player);
            }
            case GUILayout.FILTER_CLEAR -> {
                filterState.reset();
                session.put("filterState", filterState);
                playSound(player, "click");
                player.sendMessage(TextUtil.colorize("§7All filters cleared"));
                openGUI(guiManager, player, 1); // Reset to page 1
            }
            case GUILayout.FILTER_SORT -> {
                filterState.cycleSortMode();
                session.put("filterState", filterState);
                playSound(player, "click");
                openGUI(guiManager, player, page);
            }
            case GUILayout.FILTER_REVERSE -> {
                filterState.toggleReverseSortOrder();
                session.put("filterState", filterState);
                playSound(player, "click");
                openGUI(guiManager, player, page);
            }
        }

        // Navigation row buttons (row 5, slots 45-53)
        switch (slot) {
            case 45 -> { // Close button (slot 45)
                playSound(player, "close");
                player.closeInventory();
            }
            case 46 -> { // Prev page (slot 46)
                if (page > 1) {
                    playSound(player, "page");
                    session.put("page", page - 1);
                    refreshGUI(guiManager, player, session, page - 1, filterState);
                } else {
                    playSound(player, "error");
                }
            }
            case GUILayout.BROWSE_BEHAVIORS -> { // Browse Behaviors (slot 48)
                playSound(player, "click");
                com.miracle.arcanesigils.gui.behavior.BehaviorBrowserHandler.openGUI(guiManager, player);
            }
            case 49 -> { // Page indicator (slot 49)
                // No action for page indicator
            }
            case GUILayout.CREATE_BEHAVIOR -> {
                playSound(player, "click");
                final int finalPage = page;
                guiManager.getInputHelper().requestText(player, "New Behavior ID", "",
                    id -> {
                        if (id == null || id.trim().isEmpty()) {
                            player.sendMessage(TextUtil.colorize("§cInvalid behavior ID!"));
                            openGUI(guiManager, player, finalPage);
                            return;
                        }

                        if (plugin.getSigilManager().getBehavior(id) != null) {
                            player.sendMessage(TextUtil.colorize("§cBehavior with ID §f" + id + " §calready exists!"));
                            openGUI(guiManager, player, finalPage);
                            return;
                        }

                        Sigil newBehavior = new Sigil(id);
                        newBehavior.setName(id);
                        newBehavior.setSigilType(Sigil.SigilType.BEHAVIOR);
                        plugin.getSigilManager().saveBehavior(newBehavior);

                        player.sendMessage(TextUtil.colorize("§dCreated new behavior: §f" + id));
                        player.sendMessage(TextUtil.colorize("§7Add flows with signals: §fEFFECT_STATIC§7, §fTICK§7, §fEXPIRE"));
                        SigilEditorHandler.openGUI(guiManager, player, newBehavior);
                    },
                    () -> openGUI(guiManager, player, finalPage)
                );
            }
            case GUILayout.CREATE_SIGIL -> {
                playSound(player, "click");
                final int finalPage = page;
                guiManager.getInputHelper().requestText(player, "New Sigil ID", "",
                    id -> {
                        if (id == null || id.trim().isEmpty()) {
                            player.sendMessage(TextUtil.colorize("§cInvalid sigil ID!"));
                            openGUI(guiManager, player, finalPage);
                            return;
                        }

                        if (plugin.getSigilManager().getSigil(id) != null) {
                            player.sendMessage(TextUtil.colorize("§cSigil with ID §f" + id + " §calready exists!"));
                            openGUI(guiManager, player, finalPage);
                            return;
                        }

                        Sigil newSigil = new Sigil(id);
                        newSigil.setName(id);
                        player.sendMessage(TextUtil.colorize("§aCreated new sigil: §f" + id));
                        SigilEditorHandler.openGUI(guiManager, player, newSigil);
                    },
                    () -> openGUI(guiManager, player, finalPage)
                );
            }
            case 53 -> { // Next page (slot 53)
                List<Sigil> allSigils = new ArrayList<>(plugin.getSigilManager().getAllSigils());
                List<Sigil> filteredSigils = filterState.applyFiltersAndSorting(allSigils);
                int maxPage = (int) Math.ceil((double) filteredSigils.size() / ITEMS_PER_PAGE);

                if (page < maxPage) {
                    playSound(player, "page");
                    session.put("page", page + 1);
                    refreshGUI(guiManager, player, session, page + 1, filterState);
                } else {
                    playSound(player, "error");
                }
            }
        }
    }

    /**
     * Refresh GUI items in place without reopening (preserves cursor position).
     */
    private static void refreshGUI(GUIManager guiManager, Player player, GUISession session, int page, FilterState filterState) {
        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_6,
            TextUtil.parseComponent("§8Arcane Sigils"));
        buildInventory(inv, page, filterState);
        guiManager.updateGUI(player, inv, session);
    }

    /**
     * Build the inventory contents.
     */
    private static void buildInventory(Inventory inv, int page, FilterState filterState) {
        inv.clear();

        ArmorSetsPlugin plugin = (ArmorSetsPlugin) Bukkit.getPluginManager().getPlugin("ArcaneSigils");

        // Get filtered and sorted sigils
        List<Sigil> allSigils = new ArrayList<>(plugin.getSigilManager().getAllSigils());
        List<Sigil> filteredSigils = filterState.applyFiltersAndSorting(allSigils);
        int maxPage = Math.max(1, (int) Math.ceil((double) filteredSigils.size() / ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, maxPage));

        // Fill sigil slots (rows 0-3, slots 0-35)
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredSigils.size());

        if (filteredSigils.isEmpty()) {
            // Show "no results" message in center
            inv.setItem(22, ItemBuilder.createEmptyResultsItem());
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                Sigil sigil = filteredSigils.get(i);
                int slot = SIGIL_SLOTS[i - startIndex];
                inv.setItem(slot, createSigilItem(sigil));
            }
        }

        // Filter row (row 4, slots 36-44)
        inv.setItem(GUILayout.FILTER_RARITY, createFilterButton(
            Material.DIAMOND,
            "§eRarity Filter",
            filterState.getActiveRarities().isEmpty() ? null : formatRarityList(filterState.getActiveRarities()),
            !filterState.getActiveRarities().isEmpty()
        ));

        inv.setItem(GUILayout.FILTER_SLOT_TYPE, createFilterButton(
            Material.IRON_CHESTPLATE,
            "§eSlot Type Filter",
            filterState.getActiveSlotTypes().isEmpty() ? null : formatSlotTypeList(filterState.getActiveSlotTypes()),
            !filterState.getActiveSlotTypes().isEmpty()
        ));

        inv.setItem(GUILayout.FILTER_TIER_RANGE, createFilterButton(
            Material.EXPERIENCE_BOTTLE,
            "§eTier Range Filter",
            formatTierRange(filterState.getMinTier(), filterState.getMaxTier()),
            filterState.getMinTier() != null || filterState.getMaxTier() != null
        ));

        inv.setItem(GUILayout.FILTER_CRATE, createFilterButton(
            Material.CHEST,
            "§eCrate Type Filter",
            filterState.getActiveCrates().isEmpty() ? null : formatCrateList(filterState.getActiveCrates()),
            !filterState.getActiveCrates().isEmpty()
        ));

        inv.setItem(GUILayout.FILTER_CLEAR, ItemBuilder.createClearFiltersButton());

        inv.setItem(GUILayout.FILTER_SORT, createSortButton(filterState.getSortMode()));

        inv.setItem(GUILayout.FILTER_REVERSE, createReverseSortButton(filterState.isReverseSortOrder(), filterState.getSortMode()));

        // Navigation row (row 5, slots 45-53)
        inv.setItem(45, ItemBuilder.createItem(
            Material.RED_DYE,
            "§c← Close",
            "§7Close the menu"
        ));

        inv.setItem(46, ItemBuilder.createPageArrow(false, page, maxPage)); // Prev page

        inv.setItem(GUILayout.BROWSE_BEHAVIORS, ItemBuilder.createItem(
            Material.COMMAND_BLOCK,
            "§bBrowse Behaviors",
            "§7View and edit behaviors",
            "",
            "§8Behaviors define AI/effects for",
            "§8spawned entities and marks"
        ));

        inv.setItem(49, ItemBuilder.createPageIndicator(
            page, maxPage, filteredSigils.size()
        )); // Page indicator

        inv.setItem(GUILayout.CREATE_BEHAVIOR, ItemBuilder.createItem(
            Material.SPAWNER,
            "§dCreate Behavior",
            "§7Create a behavior for marks",
            "§7or spawned entities"
        ));

        inv.setItem(GUILayout.CREATE_SIGIL, ItemBuilder.createItem(
            Material.NETHER_STAR,
            "§aCreate New Sigil",
            "§7Click to create a new sigil"
        ));

        inv.setItem(53, ItemBuilder.createPageArrow(true, page, maxPage)); // Next page

        // Fill background in filter and nav rows
        for (int slot = 36; slot < 54; slot++) {
            if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
                inv.setItem(slot, ItemBuilder.createBackground());
            }
        }
    }

    /**
     * Open the Sigils Menu GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player, int page) {
        // Preserve filter state from existing session
        GUISession oldSession = guiManager.getSession(player);
        FilterState filterState = null;
        if (oldSession != null) {
            filterState = oldSession.get("filterState", FilterState.class);
        }
        if (filterState == null) {
            filterState = new FilterState();
        }

        // Create inventory
        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_6,
            TextUtil.parseComponent("§8Arcane Sigils"));

        // Normalize page
        ArmorSetsPlugin plugin = (ArmorSetsPlugin) Bukkit.getPluginManager().getPlugin("ArcaneSigils");
        List<Sigil> allSigils = new ArrayList<>(plugin.getSigilManager().getAllSigils());
        List<Sigil> filteredSigils = filterState.applyFiltersAndSorting(allSigils);
        int maxPage = Math.max(1, (int) Math.ceil((double) filteredSigils.size() / ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, maxPage));

        // Build inventory contents
        buildInventory(inv, page, filterState);

        // Create new session with correct type
        GUISession session = new GUISession(GUIType.SIGILS_MENU);
        session.put("page", page);
        session.put("filterState", filterState);

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Overload to maintain backward compatibility with old calls.
     */
    public static void openGUI(GUIManager guiManager, Player player, int page, String legacyFilter) {
        openGUI(guiManager, player, page);
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
        lore.add("§7Rarity: " + rarityColor + sigil.getRarity());
        lore.add("§7Max Tier: §f" + sigil.getMaxTier());
        lore.add("");
        lore.add("§eLeft-click §7to edit");

        return ItemBuilder.createItem(material, sigil.getName(), lore);
    }

    /**
     * Create a filter button.
     */
    private static ItemStack createFilterButton(Material material, String name, String activeDesc, boolean isActive) {
        List<String> lore = new ArrayList<>();
        if (isActive && activeDesc != null) {
            lore.add("§aActive: §f" + activeDesc);
            lore.add("§7Click to change");
        } else {
            lore.add("§7Currently: §fAll");
            lore.add("§7Click to filter");
        }

        return ItemBuilder.createFilterButton(material, name, lore, isActive);
    }

    /**
     * Create sort button.
     */
    private static ItemStack createSortButton(String sortMode) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Current: §f" + sortMode);
        lore.add("");
        lore.add("§7Modes:");
        lore.add((sortMode.equals("NONE") ? "§a▸ " : "§7- ") + "NONE");
        lore.add((sortMode.equals("ALPHABETICAL") ? "§a▸ " : "§7- ") + "ALPHABETICAL");
        lore.add((sortMode.equals("RARITY") ? "§a▸ " : "§7- ") + "RARITY");
        lore.add((sortMode.equals("TIER") ? "§a▸ " : "§7- ") + "TIER");
        lore.add("");
        lore.add("§eClick to cycle");

        return ItemBuilder.createItem(Material.COMPARATOR, "§eSort Mode", lore);
    }

    /**
     * Create reverse sort order button.
     */
    private static ItemStack createReverseSortButton(boolean isReversed, String sortMode) {
        String arrow = isReversed ? "↓" : "↑";
        String orderName = isReversed ? "Descending" : "Ascending";
        
        // Special case for TIER: default is high-to-low (descending)
        if (sortMode.equals("TIER")) {
            orderName = isReversed ? "Ascending (Low→High)" : "Descending (High→Low)";
        }
        
        List<String> lore = new ArrayList<>();
        if (sortMode.equals("NONE")) {
            lore.add("§7Order: §fN/A");
            lore.add("");
            lore.add("§7Select a sort mode first");
        } else {
            lore.add("§7Order: §f" + orderName);
            lore.add("");
            lore.add("§eClick to flip sort order");
        }

        Material material = sortMode.equals("NONE") ? Material.GRAY_DYE : Material.SPECTRAL_ARROW;
        return ItemBuilder.createItem(material, "§eSort Order " + arrow, lore);
    }

    /**
     * Format rarity list for display.
     */
    private static String formatRarityList(java.util.Set<String> rarities) {
        if (rarities.isEmpty()) return null;
        List<String> sorted = new ArrayList<>(rarities);
        sorted.sort(String::compareToIgnoreCase);
        return String.join(", ", sorted);
    }

    /**
     * Format slot type list for display.
     */
    private static String formatSlotTypeList(java.util.Set<String> slotTypes) {
        if (slotTypes.isEmpty()) return null;
        List<String> sorted = new ArrayList<>(slotTypes);
        sorted.sort(String::compareToIgnoreCase);
        // Capitalize first letter of each
        List<String> capitalized = new ArrayList<>();
        for (String slot : sorted) {
            capitalized.add(slot.substring(0, 1).toUpperCase() + slot.substring(1));
        }
        return String.join(", ", capitalized);
    }

    /**
     * Format tier range for display.
     */
    private static String formatTierRange(Integer min, Integer max) {
        if (min == null && max == null) return null;
        if (min != null && max != null) return "Tier " + min + "-" + max;
        if (min != null) return "Tier " + min + "+";
        if (max != null) return "Tier <=" + max;
        return null;
    }

    /**
     * Format crate list for display.
     */
    private static String formatCrateList(java.util.Set<String> crates) {
        if (crates.isEmpty()) return null;
        List<String> sorted = new ArrayList<>(crates);
        sorted.sort(String::compareToIgnoreCase);
        if (sorted.size() > 2) {
            return sorted.get(0) + ", " + sorted.get(1) + ", +" + (sorted.size() - 2);
        }
        return String.join(", ", sorted);
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
}
