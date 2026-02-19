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
import com.miracle.arcanesigils.core.RarityUtil;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.GUILayout;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.utils.TextUtil;

/**
 * Handler for the SIGILS_MENU GUI - in-folder view showing sigils from a single YAML file.
 * 3-row layout with 18 sigils per page.
 */
public class SigilsMenuHandler extends AbstractHandler {

    private static final int ITEMS_PER_PAGE = 18;

    // Bottom row slot positions
    private static final int SLOT_BACK = 18;
    private static final int SLOT_PREV = 19;
    private static final int SLOT_PAGE = 22;
    private static final int SLOT_CREATE_SIGIL = 23;
    private static final int SLOT_NEXT = 26;

    public SigilsMenuHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        int page = session.getInt("page", 1);
        String sourceFile = session.get("sourceFile", String.class);
        if (sourceFile == null) sourceFile = "sigils.yml";
        openGUI(guiManager, player, page, sourceFile);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        int page = session.getInt("page", 1);
        String sourceFile = session.get("sourceFile", String.class);
        if (sourceFile == null) sourceFile = "sigils.yml";

        // Handle sigil item clicks (slots 0-17)
        if (slot >= 0 && slot <= 17) {
            List<Sigil> sigils = getSigilsForFile(sourceFile);
            int index = (page - 1) * ITEMS_PER_PAGE + slot;

            if (index < sigils.size()) {
                Sigil sigil = sigils.get(index);

                if (event.isShiftClick() && event.isRightClick()) {
                    // Shift+Right: Delete sigil (with confirmation)
                    if (!requireConfirmation(player, session, "deleteConfirm_" + sigil.getId(),
                            "§eShift+right-click again to delete §f" + sigil.getName())) return;
                    plugin.getSigilManager().deleteSigil(sigil.getId());
                    player.sendMessage(TextUtil.colorize("§cDeleted sigil: §f" + sigil.getName()));
                    playSound(player, "click");
                    openGUI(guiManager, player, page, sourceFile);
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
                    SigilEditorHandler.openGUI(guiManager, player, sigil, page, sourceFile);
                }
            }
            return;
        }

        // Bottom row buttons
        switch (slot) {
            case SLOT_BACK -> {
                playSound(player, "click");
                SigilFolderBrowserHandler.openGUI(guiManager, player);
            }
            case SLOT_PREV -> {
                if (page > 1) {
                    playSound(player, "page");
                    session.put("page", page - 1);
                    refreshGUI(guiManager, player, session, page - 1, sourceFile);
                } else {
                    playSound(player, "error");
                }
            }
            case SLOT_CREATE_SIGIL -> {
                playSound(player, "click");
                final int finalPage = page;
                final String finalSourceFile = sourceFile;
                guiManager.getInputHelper().requestText(player, "New Sigil ID", "",
                    id -> {
                        if (id == null || id.trim().isEmpty()) {
                            player.sendMessage(TextUtil.colorize("§cInvalid sigil ID!"));
                            openGUI(guiManager, player, finalPage, finalSourceFile);
                            return;
                        }

                        if (plugin.getSigilManager().getSigil(id) != null) {
                            player.sendMessage(TextUtil.colorize("§cSigil with ID §f" + id + " §calready exists!"));
                            openGUI(guiManager, player, finalPage, finalSourceFile);
                            return;
                        }

                        Sigil newSigil = new Sigil(id);
                        newSigil.setName(id);
                        newSigil.setSourceFile(finalSourceFile);
                        player.sendMessage(TextUtil.colorize("§aCreated new sigil: §f" + id));
                        SigilEditorHandler.openGUI(guiManager, player, newSigil, finalPage, finalSourceFile);
                    },
                    () -> openGUI(guiManager, player, finalPage, finalSourceFile)
                );
            }
            case SLOT_NEXT -> {
                List<Sigil> sigils = getSigilsForFile(sourceFile);
                int maxPage = Math.max(1, (int) Math.ceil((double) sigils.size() / ITEMS_PER_PAGE));

                if (page < maxPage) {
                    playSound(player, "page");
                    session.put("page", page + 1);
                    refreshGUI(guiManager, player, session, page + 1, sourceFile);
                } else {
                    playSound(player, "error");
                }
            }
        }
    }

    /**
     * Refresh GUI items in place without reopening (preserves cursor position).
     */
    private static void refreshGUI(GUIManager guiManager, Player player, GUISession session, int page, String sourceFile) {
        String prettyName = SigilFolderBrowserHandler.prettifyFileName(sourceFile);
        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§8" + prettyName));
        buildInventory(inv, page, sourceFile);
        guiManager.updateGUI(player, inv, session);
    }

    /**
     * Build the inventory contents.
     */
    private static void buildInventory(Inventory inv, int page, String sourceFile) {
        inv.clear();

        List<Sigil> sigils = getSigilsForFileStatic(sourceFile);
        int maxPage = Math.max(1, (int) Math.ceil((double) sigils.size() / ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, maxPage));

        // Fill sigil slots (rows 0-1, slots 0-17)
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, sigils.size());

        if (sigils.isEmpty()) {
            inv.setItem(4, ItemBuilder.createItem(
                Material.BARRIER, "§7No sigils in this file",
                "§7Click §aCreate New Sigil §7to add one"
            ));
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                Sigil sigil = sigils.get(i);
                int slot = i - startIndex;
                inv.setItem(slot, createSigilItem(sigil));
            }
        }

        // Bottom row (row 2, slots 18-26)
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("File Browser"));

        inv.setItem(SLOT_PREV, ItemBuilder.createPageArrow(false, page, maxPage));

        inv.setItem(SLOT_PAGE, ItemBuilder.createPageIndicator(page, maxPage, sigils.size()));

        inv.setItem(SLOT_CREATE_SIGIL, ItemBuilder.createItem(
            Material.NETHER_STAR, "§aCreate New Sigil",
            "§7Click to create a new sigil",
            "§7in this file"
        ));

        inv.setItem(SLOT_NEXT, ItemBuilder.createPageArrow(true, page, maxPage));

        // Fill background in bottom row
        for (int slot = 18; slot < 27; slot++) {
            if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
                inv.setItem(slot, ItemBuilder.createBackground());
            }
        }
    }

    /**
     * Open the Sigils Menu GUI for a specific source file.
     */
    public static void openGUI(GUIManager guiManager, Player player, int page, String sourceFile) {
        if (sourceFile == null) sourceFile = "sigils.yml";

        List<Sigil> sigils = getSigilsForFileStatic(sourceFile);
        int maxPage = Math.max(1, (int) Math.ceil((double) sigils.size() / ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, maxPage));

        String prettyName = SigilFolderBrowserHandler.prettifyFileName(sourceFile);
        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§8" + prettyName));

        buildInventory(inv, page, sourceFile);

        GUISession session = new GUISession(GUIType.SIGILS_MENU);
        session.put("page", page);
        session.put("sourceFile", sourceFile);

        guiManager.openGUI(player, inv, session);
    }

    /**
     * Get sigils for a specific file, sorted by name (static version).
     */
    private static List<Sigil> getSigilsForFileStatic(String sourceFile) {
        ArmorSetsPlugin plugin = (ArmorSetsPlugin) Bukkit.getPluginManager().getPlugin("ArcaneSigils");
        if (plugin == null) return new ArrayList<>();
        return getSigilsForFile(plugin, sourceFile);
    }

    /**
     * Get sigils for a specific file, sorted by name (instance method).
     */
    private List<Sigil> getSigilsForFile(String sourceFile) {
        return getSigilsForFile(plugin, sourceFile);
    }

    /**
     * Get sigils for a specific file, sorted by name.
     */
    private static List<Sigil> getSigilsForFile(ArmorSetsPlugin plugin, String sourceFile) {
        List<Sigil> result = new ArrayList<>();
        for (Sigil sigil : plugin.getSigilManager().getAllSigils()) {
            String file = sigil.getSourceFile();
            if (file == null || file.isEmpty()) file = "sigils.yml";
            if (file.equals(sourceFile)) {
                result.add(sigil);
            }
        }
        result.sort(java.util.Comparator.comparing(Sigil::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /**
     * Create a sigil item for the menu.
     */
    private static ItemStack createSigilItem(Sigil sigil) {
        Material material = Material.ECHO_SHARD;
        if (sigil.getItemForm() != null && sigil.getItemForm().getMaterial() != null) {
            material = sigil.getItemForm().getMaterial();
        }

        String rarityColor = RarityUtil.getColor(sigil.getRarity());

        List<String> lore = new ArrayList<>();
        lore.add("§7Rarity: " + rarityColor + sigil.getRarity());
        lore.add("§7Max Tier: §f" + sigil.getMaxTier());
        lore.add("");
        lore.add("§eLeft-click §7to edit");
        lore.add("§eShift-click §7for Tier 1 item");
        lore.add("§eRight-click §7for max tier item");
        lore.add("§cShift+right-click §7to delete");

        return ItemBuilder.createItem(material, sigil.getName(), lore);
    }

}
