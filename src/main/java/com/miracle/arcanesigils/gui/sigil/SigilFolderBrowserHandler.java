package com.miracle.arcanesigils.gui.sigil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Handler for the SIGIL_FOLDER_BROWSER GUI.
 * Shows YAML files as folders; clicking a folder opens that file's sigils.
 *
 * 3-row layout (27 slots):
 * Rows 0-1 (slots 0-17): Folder items, one per YAML file
 * Row 2 (slots 18-26): Close | Prev | Browse Behaviors | Page | Create File | Create Behavior | _ | _ | Next
 */
public class SigilFolderBrowserHandler extends AbstractHandler {

    private static final int ITEMS_PER_PAGE = 18;

    // Bottom row slot positions
    private static final int SLOT_CLOSE = 18;
    private static final int SLOT_PREV = 19;
    private static final int SLOT_BEHAVIORS = 20;
    private static final int SLOT_PAGE = 22;
    private static final int SLOT_CREATE_FILE = 23;
    private static final int SLOT_CREATE_BEHAVIOR = 24;
    private static final int SLOT_NEXT = 26;

    public SigilFolderBrowserHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
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

        // Folder item clicks (slots 0-17)
        if (slot >= 0 && slot <= 17) {
            List<String> fileNames = getFileNames();
            int index = (page - 1) * ITEMS_PER_PAGE + slot;

            if (index < fileNames.size()) {
                String sourceFile = fileNames.get(index);

                if (event.isRightClick()) {
                    // Right-click: Delete file (with confirmation)
                    Map<String, List<Sigil>> fileMap = buildFileMap(plugin);
                    int count = fileMap.getOrDefault(sourceFile, List.of()).size();
                    if (!requireConfirmation(player, session, "deleteConfirm_" + sourceFile,
                            "§eRight-click again to delete §f" + sourceFile + " §e(" + count + " sigils)")) return;
                    // Confirmed - delete all sigils in this file
                    List<Sigil> toDelete = new ArrayList<>();
                    for (Sigil s : plugin.getSigilManager().getAllSigils()) {
                        String f = s.getSourceFile();
                        if (f == null || f.isEmpty()) f = "sigils.yml";
                        if (f.equals(sourceFile)) toDelete.add(s);
                    }
                    for (Sigil s : toDelete) {
                        plugin.getSigilManager().deleteSigil(s.getId());
                    }
                    player.sendMessage(TextUtil.colorize("§cDeleted §f" + sourceFile + " §c(" + toDelete.size() + " sigils)"));
                    playSound(player, "click");
                    openGUI(guiManager, player, page);
                } else {
                    // Left-click: Open folder
                    playSound(player, "click");
                    SigilsMenuHandler.openGUI(guiManager, player, 1, sourceFile);
                }
            }
            return;
        }

        switch (slot) {
            case SLOT_CLOSE -> {
                playSound(player, "close");
                player.closeInventory();
            }
            case SLOT_PREV -> {
                if (page > 1) {
                    playSound(player, "page");
                    openGUI(guiManager, player, page - 1);
                } else {
                    playSound(player, "error");
                }
            }
            case SLOT_BEHAVIORS -> {
                playSound(player, "click");
                com.miracle.arcanesigils.gui.behavior.BehaviorBrowserHandler.openGUI(guiManager, player);
            }
            case SLOT_CREATE_FILE -> {
                playSound(player, "click");
                final int finalPage = page;
                guiManager.getInputHelper().requestText(player, "Filename", "",
                    filename -> {
                        if (filename == null || filename.trim().isEmpty()) {
                            player.sendMessage(TextUtil.colorize("§cInvalid filename!"));
                            openGUI(guiManager, player, finalPage);
                            return;
                        }

                        String finalFilename = filename.trim();
                        if (!finalFilename.endsWith(".yml")) {
                            finalFilename += ".yml";
                        }

                        // Open the sigils menu for this (empty) file
                        player.sendMessage(TextUtil.colorize("§aOpened new file: §f" + finalFilename));
                        SigilsMenuHandler.openGUI(guiManager, player, 1, finalFilename);
                    },
                    () -> openGUI(guiManager, player, finalPage)
                );
            }
            case SLOT_CREATE_BEHAVIOR -> {
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
                        SigilEditorHandler.openGUI(guiManager, player, newBehavior);
                    },
                    () -> openGUI(guiManager, player, finalPage)
                );
            }
            case SLOT_NEXT -> {
                List<String> fileNames = getFileNames();
                int maxPage = Math.max(1, (int) Math.ceil((double) fileNames.size() / ITEMS_PER_PAGE));

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
     * Open the folder browser at page 1.
     */
    public static void openGUI(GUIManager guiManager, Player player) {
        openGUI(guiManager, player, 1);
    }

    /**
     * Open the folder browser at a specific page.
     */
    public static void openGUI(GUIManager guiManager, Player player, int page) {
        ArmorSetsPlugin plugin = (ArmorSetsPlugin) Bukkit.getPluginManager().getPlugin("ArcaneSigils");

        // Build file -> sigils map
        Map<String, List<Sigil>> fileMap = buildFileMap(plugin);
        List<String> fileNames = new ArrayList<>(fileMap.keySet());
        fileNames.sort(String.CASE_INSENSITIVE_ORDER);

        int maxPage = Math.max(1, (int) Math.ceil((double) fileNames.size() / ITEMS_PER_PAGE));
        page = Math.max(1, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(null, GUILayout.ROWS_3,
            TextUtil.parseComponent("§8Arcane Sigils"));

        // Fill folder items (rows 0-1)
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, fileNames.size());

        for (int i = startIndex; i < endIndex; i++) {
            String fileName = fileNames.get(i);
            List<Sigil> sigils = fileMap.get(fileName);
            int slot = i - startIndex;
            inv.setItem(slot, createFolderItem(fileName, sigils));
        }

        // Bottom row
        inv.setItem(SLOT_CLOSE, ItemBuilder.createItem(
            Material.RED_DYE, "§c← Close", "§7Close the menu"
        ));

        inv.setItem(SLOT_PREV, ItemBuilder.createPageArrow(false, page, maxPage));

        inv.setItem(SLOT_BEHAVIORS, ItemBuilder.createItem(
            Material.COMMAND_BLOCK, "§bBrowse Behaviors",
            "§7View and edit behaviors", "",
            "§8Behaviors define AI/effects for",
            "§8spawned entities and marks"
        ));

        inv.setItem(SLOT_PAGE, ItemBuilder.createPageIndicator(page, maxPage, fileNames.size()));

        inv.setItem(SLOT_CREATE_FILE, ItemBuilder.createItem(
            Material.NETHER_STAR, "§aCreate New File",
            "§7Click to create a new sigil file"
        ));

        inv.setItem(SLOT_CREATE_BEHAVIOR, ItemBuilder.createItem(
            Material.SPAWNER, "§dCreate Behavior",
            "§7Create a behavior for marks",
            "§7or spawned entities"
        ));

        inv.setItem(SLOT_NEXT, ItemBuilder.createPageArrow(true, page, maxPage));

        // Fill background in bottom row
        for (int slot = 18; slot < 27; slot++) {
            if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
                inv.setItem(slot, ItemBuilder.createBackground());
            }
        }

        GUISession session = new GUISession(GUIType.SIGIL_FOLDER_BROWSER);
        session.put("page", page);

        guiManager.openGUI(player, inv, session);
    }

    /**
     * Build a map of sourceFile -> list of sigils.
     */
    private static Map<String, List<Sigil>> buildFileMap(ArmorSetsPlugin plugin) {
        Map<String, List<Sigil>> fileMap = new LinkedHashMap<>();

        for (Sigil sigil : plugin.getSigilManager().getAllSigils()) {
            String file = sigil.getSourceFile();
            if (file == null || file.isEmpty()) {
                file = "sigils.yml";
            }
            fileMap.computeIfAbsent(file, k -> new ArrayList<>()).add(sigil);
        }

        return fileMap;
    }

    /**
     * Get sorted list of unique file names (instance method).
     */
    private List<String> getFileNames() {
        Map<String, List<Sigil>> fileMap = buildFileMap(plugin);
        List<String> fileNames = new ArrayList<>(fileMap.keySet());
        fileNames.sort(String.CASE_INSENSITIVE_ORDER);
        return fileNames;
    }

    /**
     * Create a folder item for a YAML file.
     */
    private static ItemStack createFolderItem(String fileName, List<Sigil> sigils) {
        boolean hasExclusive = sigils.stream().anyMatch(Sigil::isExclusive);
        Material material = hasExclusive ? Material.ENDER_CHEST : Material.CHEST;

        String prettyName = prettifyFileName(fileName);

        List<String> lore = new ArrayList<>();
        lore.add("§7File: §f" + fileName);
        lore.add("§7Sigils: §f" + sigils.size());
        lore.add("");

        // Show first few sigil names
        int preview = Math.min(5, sigils.size());
        for (int i = 0; i < preview; i++) {
            lore.add("§8- " + sigils.get(i).getName());
        }
        if (sigils.size() > 5) {
            lore.add("§8... and " + (sigils.size() - 5) + " more");
        }

        lore.add("");
        lore.add("§eClick to open");
        lore.add("§cRight-click to delete");

        return ItemBuilder.createItem(material, "§6" + prettyName, lore);
    }

    /**
     * Convert a filename like "pharaoh-set.yml" to "Pharaoh Set".
     */
    static String prettifyFileName(String fileName) {
        String name = fileName.replace(".yml", "").replace(".yaml", "");
        // Replace hyphens and underscores with spaces
        name = name.replace('-', ' ').replace('_', ' ');
        // Capitalize each word
        String[] words = name.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
