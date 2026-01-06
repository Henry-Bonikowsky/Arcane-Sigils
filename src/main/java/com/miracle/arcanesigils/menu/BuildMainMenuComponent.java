package com.miracle.arcanesigils.menu;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.constants.GUIConstants;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Primary UI/command interface component for the ArmorWeapon Plugin.
 * Provides a comprehensive inventory-based GUI system for browsing sigils,
 * armor sets, socketing, and accessing help information.
 *
 * <p>Features:
 * <ul>
 *   <li>Main menu with categorized options</li>
 *   <li>Pagination support for large lists</li>
 *   <li>Player armor information display</li>
 *   <li>Sigil socket/unsocket sigilality access</li>
 *   <li>Navigation history with back buttons</li>
 *   <li>5-minute inactivity timeout</li>
 * </ul>
 *
 * @author ArmorSets Team
 * @version 1.0
 */
public class BuildMainMenuComponent implements Listener {

    private static final String MENU_IDENTIFIER = "armorsets_menu";
    private static final int MENU_SIZE = GUIConstants.STANDARD_MENU_SIZE;

    // Content area slots (use GUIConstants for consistency)
    private static final int[] CONTENT_SLOTS = GUIConstants.CONTENT_SLOTS;

    // Navigation slots (use GUIConstants for consistency)
    private static final int SLOT_BACK = GUIConstants.SLOT_BACK;
    private static final int SLOT_PREV_PAGE = GUIConstants.SLOT_PREV_PAGE;
    private static final int SLOT_INFO = GUIConstants.SLOT_INFO;
    private static final int SLOT_NEXT_PAGE = GUIConstants.SLOT_NEXT_PAGE;
    private static final int SLOT_CLOSE = GUIConstants.SLOT_CLOSE;

    // Main menu slots (use GUIConstants for consistency)
    private static final int SLOT_BROWSE_SIGILS = GUIConstants.SLOT_BROWSE_SIGILS;
    private static final int SLOT_SOCKET = GUIConstants.SLOT_SOCKET;
    private static final int SLOT_UNSOCKET = GUIConstants.SLOT_UNSOCKET;
    private static final int SLOT_ARMOR_INFO = GUIConstants.SLOT_ARMOR_INFO;
    private static final int SLOT_HELP = GUIConstants.SLOT_HELP;

    private final ArmorSetsPlugin plugin;
    private final Map<UUID, MenuState> playerStates;
    private final Map<UUID, Inventory> activeMenus;
    private BukkitTask cleanupTask;

    /**
     * Creates a new BuildMainMenuComponent.
     *
     * @param plugin The ArmorSetsPlugin instance
     */
    public BuildMainMenuComponent(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.playerStates = new ConcurrentHashMap<>();
        this.activeMenus = new ConcurrentHashMap<>();

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Start cleanup task for expired sessions
        startCleanupTask();
    }

    /**
     * Displays the main menu to a player.
     * This is the primary entry point for the GUI system.
     *
     * @param player The player to show the menu to
     */
    public void displayMainMenu(Player player) {
        MenuState state = getOrCreateState(player);
        state.reset();
        state.setCurrentMenu(MenuState.MenuType.MAIN_MENU);

        Inventory menu = createMainMenuInventory(player);
        activeMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f);
    }

    /**
     * Handles a menu selection from a player.
     *
     * @param player    The player who made the selection
     * @param selection The selection identifier
     */
    public void handleMenuSelection(Player player, String selection) {
        MenuState state = getOrCreateState(player);
        state.updateInteraction();

        switch (selection.toUpperCase()) {
            case "BROWSE_SIGILS" -> openSigilBrowser(player);
            case "SOCKET" -> openSocketMenu(player);
            case "UNSOCKET" -> openUnsocketMenu(player);
            case "ARMOR_INFO" -> displayArmorInfo(player);
            case "HELP" -> displayCommandHelp(player);
            case "BACK" -> handleBackNavigation(player);
            case "CLOSE" -> player.closeInventory();
            case "PREV_PAGE" -> handlePreviousPage(player);
            case "NEXT_PAGE" -> handleNextPage(player);
            default -> player.sendMessage(TextUtil.colorize("§cUnknown menu action: " + selection));
        }
    }

    /**
     * Displays quick information panel to the player.
     * Shows a summary of their current equipment and socketed sigils.
     *
     * @param player The player to show info to
     */
    public void displayQuickInfo(Player player) {
        player.sendMessage(TextUtil.colorize(""));
        player.sendMessage(TextUtil.colorize("§8&m----------&r §d&lQuick Info §8&m----------"));

        // Show socketed sigils
        player.sendMessage(TextUtil.colorize(""));
        player.sendMessage(TextUtil.colorize("§b&lSocketed Sigils:"));

        ItemStack[] armor = player.getInventory().getArmorContents();
        String[] slotNames = {"Boots", "Leggings", "Chestplate", "Helmet"};
        boolean hasAny = false;

        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece != null && !piece.getType().isAir()) {
                Sigil sigil = plugin.getSocketManager().getSocketedSigil(piece);
                if (sigil != null) {
                    player.sendMessage(TextUtil.colorize("§7" + slotNames[i] + ": §b" + sigil.getName()));
                    hasAny = true;
                }
            }
        }

        if (!hasAny) {
            player.sendMessage(TextUtil.colorize("§8  No sigils socketed"));
        }

        player.sendMessage(TextUtil.colorize("§8&m---------------------------------"));
        player.sendMessage(TextUtil.colorize(""));
    }

    /**
     * Displays detailed armor information for the player.
     * Shows each equipped piece with its properties and effects.
     *
     * @param player The player to show armor info to
     */
    public void displayArmorInfo(Player player) {
        MenuState state = getOrCreateState(player);
        state.navigateTo(MenuState.MenuType.ARMOR_INFO);

        Inventory menu = createArmorInfoInventory(player);
        activeMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f);
    }

    /**
     * Displays the command help menu.
     * Lists all available commands with descriptions.
     *
     * @param player The player to show help to
     */
    public void displayCommandHelp(Player player) {
        MenuState state = getOrCreateState(player);
        state.navigateTo(MenuState.MenuType.HELP_COMMANDS);

        Inventory menu = createHelpInventory(player);
        activeMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f);
    }

    /**
     * Refreshes the current menu for a player.
     * Re-renders the current view with updated data.
     *
     * @param player The player whose menu should be refreshed
     */
    public void refreshMenu(Player player) {
        MenuState state = playerStates.get(player.getUniqueId());
        if (state == null) return;

        state.updateInteraction();

        Inventory newMenu = switch (state.getCurrentMenu()) {
            case MAIN_MENU -> createMainMenuInventory(player);
            case BROWSE_SIGILS -> createSigilBrowserInventory(player, state.getCurrentPage());
            case ARMOR_INFO -> createArmorInfoInventory(player);
            case HELP_COMMANDS -> createHelpInventory(player);
            case SOCKET_SIGIL -> createSocketMenuInventory(player);
            case UNSOCKET_SIGIL -> createUnsocketMenuInventory(player);
            default -> createMainMenuInventory(player);
        };

        activeMenus.put(player.getUniqueId(), newMenu);
        player.openInventory(newMenu);
    }

    // ============ Private Menu Creation Methods ============

    private Inventory createMainMenuInventory(Player player) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
                TextUtil.parseComponent("§8&l[ §d&lArmor Weapon Plugin §8&l]"));

        // Fill border
        fillBorder(inv);

        // Header decoration
        setItem(inv, 4, createDecorativeItem(Material.NETHER_STAR, "§d&lMain Menu",
                Arrays.asList("§7Welcome to the ArmorSets menu!", "",
                        "§8Select an option below")));

        // Main menu options
        setItem(inv, SLOT_BROWSE_SIGILS, createMenuItem(
                Material.ENCHANTED_BOOK, "§b&lBrowse Sigils",
                Arrays.asList("§7View all available sigils",
                        "§7that can be socketed into armor.",
                        "", "§8Click to browse")));

        setItem(inv, SLOT_SOCKET, createMenuItem(
                Material.END_CRYSTAL, "§a&lSocket Sigil",
                Arrays.asList("§7Socket a sigil",
                        "§7into your equipped armor.",
                        "", "§8Click to socket")));

        setItem(inv, SLOT_UNSOCKET, createMenuItem(
                Material.REDSTONE, "§c&lUnsocket Sigil",
                Arrays.asList("§7Remove a socketed sigil",
                        "§7from your armor piece.",
                        "", "§8Click to unsocket")));

        setItem(inv, SLOT_ARMOR_INFO, createMenuItem(
                Material.ARMOR_STAND, "§e&lArmor Info",
                Arrays.asList("§7View information about",
                        "§7your currently equipped armor.",
                        "", "§8Click to view")));

        setItem(inv, SLOT_HELP, createMenuItem(
                Material.BOOK, "§6&lHelp & Commands",
                Arrays.asList("§7View available commands",
                        "§7and plugin information.",
                        "", "§8Click to view")));

        // Bottom navigation
        setItem(inv, SLOT_INFO, createInfoItem(player));
        setItem(inv, SLOT_CLOSE, createCloseButton());

        return inv;
    }

    private Inventory createSigilBrowserInventory(Player player, int page) {
        List<Sigil> allSigils = new ArrayList<>(plugin.getSigilManager().getAllSigils());

        // Sort by name
        allSigils.sort(Comparator.comparing(Sigil::getName));

        int totalPages = (int) Math.ceil((double) allSigils.size() / GUIConstants.ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        MenuState state = getOrCreateState(player);
        state.setTotalPages(totalPages);

        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
                TextUtil.parseComponent("§8&l[ §b&lSigils §8- §7Page " + (page + 1) + "/" + totalPages + " §8&l]"));

        fillBorder(inv);

        // Header
        setItem(inv, 4, createDecorativeItem(Material.NETHER_STAR, "§b&lSigils",
                Arrays.asList("§7Browse all available sigils",
                        "§7Click any sigil for details",
                        "",
                        "§8Total: §f" + allSigils.size() + " sigils")));

        // Populate content slots
        int startIndex = page * GUIConstants.ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + GUIConstants.ITEMS_PER_PAGE, allSigils.size());

        for (int i = 0; i < CONTENT_SLOTS.length && (startIndex + i) < endIndex; i++) {
            Sigil sigil = allSigils.get(startIndex + i);
            setItem(inv, CONTENT_SLOTS[i], createSigilDisplayItem(sigil));
        }

        // Navigation
        addNavigationButtons(inv, page, totalPages);
        setItem(inv, SLOT_BACK, createBackButton());
        setItem(inv, SLOT_CLOSE, createCloseButton());

        return inv;
    }

    private Inventory createArmorInfoInventory(Player player) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
                TextUtil.parseComponent("§8&l[ §e&lArmor Information §8&l]"));

        fillBorder(inv);

        // Header
        setItem(inv, 4, createDecorativeItem(Material.ARMOR_STAND, "§e&lYour Armor",
                Arrays.asList("§7View your equipped armor",
                        "§7and socketed sigils")));

        // Display armor pieces
        ItemStack[] armor = player.getInventory().getArmorContents();
        int[] armorSlots = {29, 20, 22, 24}; // Boots, Legs, Chest, Helmet positions in GUI
        String[] slotNames = {"Boots", "Leggings", "Chestplate", "Helmet"};
        Material[] emptyMaterials = {
                Material.LEATHER_BOOTS, Material.LEATHER_LEGGINGS,
                Material.LEATHER_CHESTPLATE, Material.LEATHER_HELMET
        };

        for (int i = 0; i < 4; i++) {
            ItemStack piece = armor[i];
            if (piece != null && !piece.getType().isAir()) {
                // Show actual armor piece with socket info
                setItem(inv, armorSlots[i], createArmorDisplayItem(piece, slotNames[i]));
            } else {
                // Show empty slot placeholder
                setItem(inv, armorSlots[i], createEmptySlotItem(slotNames[i], emptyMaterials[i]));
            }
        }

        // Navigation
        setItem(inv, SLOT_BACK, createBackButton());
        setItem(inv, SLOT_CLOSE, createCloseButton());

        return inv;
    }

    private Inventory createHelpInventory(Player player) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
                TextUtil.parseComponent("§8&l[ §6&lHelp & Commands §8&l]"));

        fillBorder(inv);

        // Header
        setItem(inv, 4, createDecorativeItem(Material.BOOK, "§6&lCommand Reference",
                Arrays.asList("§7Available commands and help")));

        // Command items
        int slot = 10;

        setItem(inv, slot++, createHelpCommandItem("/armorsets help",
                "Show help message", "armorsets.help"));
        setItem(inv, slot++, createHelpCommandItem("/armorsets reload",
                "Reload plugin configuration", "armorsets.reload"));
        setItem(inv, slot++, createHelpCommandItem("/armorsets give sigil <player> <id> [tier]",
                "Give a sigil shard to a player", "armorsets.give"));
        setItem(inv, slot++, createHelpCommandItem("/armorsets give weapon <player> <id>",
                "Give a weapon to a player", "armorsets.give"));
        setItem(inv, slot++, createHelpCommandItem("/armorsets list sigils",
                "List all sigils", "armorsets.list"));
        slot++; // Skip to next row
        slot++;
        slot++;
        setItem(inv, slot++, createHelpCommandItem("/armorsets info",
                "Show info about held item", "armorsets.info"));
        setItem(inv, slot++, createHelpCommandItem("/armorsets unsocket",
                "Remove sigil from held armor", "armorsets.socket"));
        setItem(inv, slot++, createHelpCommandItem("/armorsets build",
                "Open the build menu GUI", "armorsets.build"));

        // Tips section
        setItem(inv, 40, createDecorativeItem(Material.LIGHT_BLUE_DYE, "§b&lTips",
                Arrays.asList(
                        "§7- Right-click armor with a sigil",
                        "§7  shard to socket it",
                        "§7- Higher tier = stronger effects"
                )));

        // Navigation
        setItem(inv, SLOT_BACK, createBackButton());
        setItem(inv, SLOT_CLOSE, createCloseButton());

        return inv;
    }

    private Inventory createSocketMenuInventory(Player player) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
                TextUtil.parseComponent("§8&l[ §a&lSocket Sigil §8&l]"));

        fillBorder(inv);

        setItem(inv, 4, createDecorativeItem(Material.END_CRYSTAL, "§a&lSocket Sigil",
                Arrays.asList("§7Select an armor piece to",
                        "§7socket a sigil into")));

        // Display armor slots that can accept sockets
        ItemStack[] armor = player.getInventory().getArmorContents();
        int[] armorSlots = {29, 20, 22, 24};
        String[] slotNames = {"Boots", "Leggings", "Chestplate", "Helmet"};

        for (int i = 0; i < 4; i++) {
            ItemStack piece = armor[i];
            if (piece != null && !piece.getType().isAir()) {
                boolean hasSocket = plugin.getSocketManager().hasSocketedSigil(piece);
                if (hasSocket) {
                    setItem(inv, armorSlots[i], createAlreadySocketedItem(piece, slotNames[i]));
                } else {
                    setItem(inv, armorSlots[i], createSocketableArmorItem(piece, slotNames[i]));
                }
            } else {
                setItem(inv, armorSlots[i], createEmptySlotItem(slotNames[i],
                        Material.valueOf("LEATHER_" + slotNames[i].toUpperCase())));
            }
        }

        // Instructions
        setItem(inv, 31, createDecorativeItem(Material.PAPER, "§7Instructions",
                Arrays.asList(
                        "§8Hold a sigil shard and",
                        "§8right-click while wearing armor,",
                        "§8or drag the shard onto armor",
                        "§8in your inventory."
                )));

        setItem(inv, SLOT_BACK, createBackButton());
        setItem(inv, SLOT_CLOSE, createCloseButton());

        return inv;
    }

    private Inventory createUnsocketMenuInventory(Player player) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
                TextUtil.parseComponent("§8&l[ §c&lUnsocket Sigil §8&l]"));

        fillBorder(inv);

        setItem(inv, 4, createDecorativeItem(Material.REDSTONE, "§c&lUnsocket Sigil",
                Arrays.asList("§7Click an armor piece to",
                        "§7remove its socketed sigil")));

        // Display armor with socketed sigils
        ItemStack[] armor = player.getInventory().getArmorContents();
        int[] armorSlots = {29, 20, 22, 24};
        String[] slotNames = {"Boots", "Leggings", "Chestplate", "Helmet"};

        boolean hasAnySocket = false;
        for (int i = 0; i < 4; i++) {
            ItemStack piece = armor[i];
            if (piece != null && !piece.getType().isAir()) {
                Sigil sigil = plugin.getSocketManager().getSocketedSigil(piece);
                if (sigil != null) {
                    setItem(inv, armorSlots[i], createUnsocketableItem(piece, slotNames[i], sigil));
                    hasAnySocket = true;
                } else {
                    setItem(inv, armorSlots[i], createNoSocketItem(piece, slotNames[i]));
                }
            } else {
                setItem(inv, armorSlots[i], createEmptySlotItem(slotNames[i],
                        Material.valueOf("LEATHER_" + slotNames[i].toUpperCase())));
            }
        }

        // Info message
        if (!hasAnySocket) {
            setItem(inv, 31, createDecorativeItem(Material.BARRIER, "§cNo Socketed Sigils",
                    Arrays.asList("§7You don't have any armor",
                            "§7with socketed sigils.")));
        } else {
            setItem(inv, 31, createDecorativeItem(Material.PAPER, "§7Instructions",
                    Arrays.asList("§8Click an armor piece with",
                            "§8a socketed sigil to",
                            "§8remove it. You will receive",
                            "§8the sigil shard back.")));
        }

        setItem(inv, SLOT_BACK, createBackButton());
        setItem(inv, SLOT_CLOSE, createCloseButton());

        return inv;
    }

    // ============ Item Creation Helpers ============

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(TextUtil.parseComponent(name));
        List<Component> componentLore = new ArrayList<>();
        for (String line : lore) {
            componentLore.add(TextUtil.parseComponent(line));
        }
        meta.lore(componentLore);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDecorativeItem(Material material, String name, List<String> lore) {
        ItemStack item = createMenuItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSigilDisplayItem(Sigil sigil) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(TextUtil.parseComponent("§d" + sigil.getName()));

        List<Component> lore = new ArrayList<>();
        for (String desc : sigil.getDescription()) {
            lore.add(TextUtil.parseComponent("§7" + desc));
        }
        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("§8Slot: §f" + TextUtil.toProperCase(sigil.getSlot())));
        lore.add(TextUtil.parseComponent("§8Tier: §f" + sigil.getTier()));
        lore.add(Component.empty());

        if (sigil.hasFlows()) {
            lore.add(TextUtil.parseComponent("§b&lFlows:"));
            for (com.miracle.arcanesigils.flow.FlowConfig flow : sigil.getFlows()) {
                String flowName = flow.getTrigger() != null ? flow.getTrigger() : "flow";
                flowName = TextUtil.toProperCase(flowName.replace("_", " "));
                lore.add(TextUtil.parseComponent("§b- §3" + flowName));
            }
        }

        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("§8Click for details"));

        meta.lore(lore);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createArmorDisplayItem(ItemStack armor, String slotName) {
        ItemStack display = armor.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        // Get original lore and add socket info
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();

        Sigil sigil = plugin.getSocketManager().getSocketedSigil(armor);
        if (sigil != null) {
            lore.add(Component.empty());
            lore.add(TextUtil.parseComponent("§a&lSocketed Sigil:"));
            lore.add(TextUtil.parseComponent("§a- §f" + sigil.getName()));
        } else {
            lore.add(Component.empty());
            lore.add(TextUtil.parseComponent("§8No sigil socketed"));
        }

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack createEmptySlotItem(String slotName, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(TextUtil.parseComponent("§8Empty " + slotName + " Slot"));
        meta.lore(List.of(TextUtil.parseComponent("§7No armor equipped")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSocketableArmorItem(ItemStack armor, String slotName) {
        ItemStack display = armor.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("§a&lReady for Socket"));
        lore.add(TextUtil.parseComponent("§7This armor can accept a sigil"));

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack createAlreadySocketedItem(ItemStack armor, String slotName) {
        ItemStack display = armor.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        Sigil sigil = plugin.getSocketManager().getSocketedSigil(armor);

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("§c&lAlready Socketed"));
        if (sigil != null) {
            lore.add(TextUtil.parseComponent("§7Contains: §f" + sigil.getName()));
        }
        lore.add(TextUtil.parseComponent("§8Unsocket first to add new sigil"));

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack createUnsocketableItem(ItemStack armor, String slotName, Sigil sigil) {
        ItemStack display = armor.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.parseComponent("§7Slot: §f" + slotName));
        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("§c&lSocketed Sigil:"));
        lore.add(TextUtil.parseComponent("§c- §f" + sigil.getName()));
        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("§e&lClick to Unsocket"));
        lore.add(TextUtil.parseComponent("§7You will receive the shard back"));

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack createNoSocketItem(ItemStack armor, String slotName) {
        ItemStack display = armor.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("§8No sigil to unsocket"));

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack createHelpCommandItem(String command, String description, String permission) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(TextUtil.parseComponent("§e" + command));
        meta.lore(List.of(
                TextUtil.parseComponent("§7" + description),
                Component.empty(),
                TextUtil.parseComponent("§8Permission: " + permission)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
        }

        meta.displayName(TextUtil.parseComponent("§a&l" + player.getName()));

        meta.lore(List.of(
                TextUtil.parseComponent("§8Click for quick info")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(TextUtil.parseComponent("§c&lBack"));
        meta.lore(List.of(TextUtil.parseComponent("§7Return to previous menu")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(TextUtil.parseComponent("§c&lClose Menu"));
        meta.lore(List.of(TextUtil.parseComponent("§7Close this menu")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPrevPageButton(int currentPage) {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (currentPage > 0) {
            meta.displayName(TextUtil.parseComponent("§e&lPrevious Page"));
            meta.lore(List.of(TextUtil.parseComponent("§7Go to page " + currentPage)));
        } else {
            item.setType(Material.GRAY_DYE);
            meta.displayName(TextUtil.parseComponent("§8First Page"));
            meta.lore(List.of(TextUtil.parseComponent("§7No previous pages")));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextPageButton(int currentPage, int totalPages) {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (currentPage < totalPages - 1) {
            meta.displayName(TextUtil.parseComponent("§e&lNext Page"));
            meta.lore(List.of(TextUtil.parseComponent("§7Go to page " + (currentPage + 2))));
        } else {
            item.setType(Material.GRAY_DYE);
            meta.displayName(TextUtil.parseComponent("§8Last Page"));
            meta.lore(List.of(TextUtil.parseComponent("§7No more pages")));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBorderItem() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    // ============ Navigation Helpers ============

    private void openSigilBrowser(Player player) {
        MenuState state = getOrCreateState(player);
        state.navigateTo(MenuState.MenuType.BROWSE_SIGILS);

        Inventory menu = createSigilBrowserInventory(player, 0);
        activeMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f);
    }

    private void openSocketMenu(Player player) {
        MenuState state = getOrCreateState(player);
        state.navigateTo(MenuState.MenuType.SOCKET_SIGIL);

        Inventory menu = createSocketMenuInventory(player);
        activeMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f);
    }

    private void openUnsocketMenu(Player player) {
        MenuState state = getOrCreateState(player);
        state.navigateTo(MenuState.MenuType.UNSOCKET_SIGIL);

        Inventory menu = createUnsocketMenuInventory(player);
        activeMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu);
        playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f);
    }

    private void handleBackNavigation(Player player) {
        MenuState state = playerStates.get(player.getUniqueId());
        if (state == null || !state.navigateBack()) {
            displayMainMenu(player);
            return;
        }

        refreshMenu(player);
        playSound(player, Sound.UI_BUTTON_CLICK, 0.5f);
    }

    private void handlePreviousPage(Player player) {
        MenuState state = playerStates.get(player.getUniqueId());
        if (state != null && state.previousPage()) {
            refreshMenu(player);
            playSound(player, Sound.UI_BUTTON_CLICK, 0.5f);
        } else {
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f);
        }
    }

    private void handleNextPage(Player player) {
        MenuState state = playerStates.get(player.getUniqueId());
        if (state != null && state.nextPage()) {
            refreshMenu(player);
            playSound(player, Sound.UI_BUTTON_CLICK, 0.5f);
        } else {
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f);
        }
    }

    private void addNavigationButtons(Inventory inv, int currentPage, int totalPages) {
        setItem(inv, SLOT_PREV_PAGE, createPrevPageButton(currentPage));
        setItem(inv, SLOT_NEXT_PAGE, createNextPageButton(currentPage, totalPages));
    }

    private void fillBorder(Inventory inv) {
        ItemStack border = createBorderItem();
        int size = inv.getSize();

        // Top row
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }
        // Bottom row
        for (int i = size - 9; i < size; i++) {
            inv.setItem(i, border);
        }
        // Left and right columns
        for (int i = 9; i < size - 9; i += 9) {
            inv.setItem(i, border);
            inv.setItem(i + 8, border);
        }
    }

    private void setItem(Inventory inv, int slot, ItemStack item) {
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, item);
        }
    }

    // ============ Event Handlers ============

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        // Check if this is our menu
        Inventory activeMenu = activeMenus.get(player.getUniqueId());
        if (activeMenu == null || event.getView().getTopInventory() != activeMenu) return;

        // Cancel all clicks in our menu
        event.setCancelled(true);

        // Ignore clicks in player inventory
        if (clickedInv != activeMenu) return;

        int slot = event.getSlot();
        MenuState state = playerStates.get(player.getUniqueId());
        if (state == null) return;

        state.updateInteraction();

        // Handle based on current menu type
        switch (state.getCurrentMenu()) {
            case MAIN_MENU -> handleMainMenuClick(player, slot);
            case BROWSE_SIGILS -> handleSigilBrowserClick(player, slot, event.getClick());
            case ARMOR_INFO -> handleArmorInfoClick(player, slot);
            case HELP_COMMANDS -> handleHelpClick(player, slot);
            case SOCKET_SIGIL -> handleSocketMenuClick(player, slot);
            case UNSOCKET_SIGIL -> handleUnsocketMenuClick(player, slot);
            default -> handleCommonNavigationClick(player, slot);
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case SLOT_BROWSE_SIGILS -> handleMenuSelection(player, "BROWSE_SIGILS");
            case SLOT_SOCKET -> handleMenuSelection(player, "SOCKET");
            case SLOT_UNSOCKET -> handleMenuSelection(player, "UNSOCKET");
            case SLOT_ARMOR_INFO -> handleMenuSelection(player, "ARMOR_INFO");
            case SLOT_HELP -> handleMenuSelection(player, "HELP");
            case SLOT_INFO -> displayQuickInfo(player);
            case SLOT_CLOSE -> player.closeInventory();
        }
    }

    private void handleSigilBrowserClick(Player player, int slot, ClickType clickType) {
        handleCommonNavigationClick(player, slot);

        // Check if clicked on a sigil item
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == slot) {
                MenuState state = playerStates.get(player.getUniqueId());
                if (state == null) return;

                List<Sigil> allSigils = new ArrayList<>(plugin.getSigilManager().getAllSigils());
                allSigils.sort(Comparator.comparing(Sigil::getName));

                int index = state.getCurrentPage() * GUIConstants.ITEMS_PER_PAGE + i;
                if (index < allSigils.size()) {
                    Sigil sigil = allSigils.get(index);
                    showSigilDetails(player, sigil);
                }
                return;
            }
        }
    }

    private void handleArmorInfoClick(Player player, int slot) {
        handleCommonNavigationClick(player, slot);
    }

    private void handleHelpClick(Player player, int slot) {
        handleCommonNavigationClick(player, slot);
    }

    private void handleSocketMenuClick(Player player, int slot) {
        handleCommonNavigationClick(player, slot);
        // Socket menu is mostly informational, actual socketing happens via item interaction
    }

    private void handleUnsocketMenuClick(Player player, int slot) {
        handleCommonNavigationClick(player, slot);

        // Check for unsocket action
        int[] armorSlots = {29, 20, 22, 24}; // Boots, Legs, Chest, Helmet
        int[] invSlots = {36, 37, 38, 39}; // Bukkit inventory slot indices

        for (int i = 0; i < armorSlots.length; i++) {
            if (slot == armorSlots[i]) {
                ItemStack armor = player.getInventory().getArmorContents()[i];
                if (armor != null && !armor.getType().isAir()) {
                    Sigil sigil = plugin.getSocketManager().getSocketedSigil(armor);
                    if (sigil != null) {
                        // Perform unsocket
                        Sigil removed = plugin.getSocketManager().unsocketSigil(player, armor);
                        if (removed != null) {
                            ItemStack shard = plugin.getSigilManager().createSigilItem(removed);
                            player.getInventory().addItem(shard);
                            player.sendMessage(TextUtil.colorize("§aUnsocketed §f" + removed.getName() + "§a! Shard returned."));
                            playSound(player, Sound.BLOCK_ANVIL_USE, 0.5f);
                            // Refresh menu
                            refreshMenu(player);
                        }
                    }
                }
                return;
            }
        }
    }

    private void handleCommonNavigationClick(Player player, int slot) {
        switch (slot) {
            case SLOT_BACK -> handleMenuSelection(player, "BACK");
            case SLOT_CLOSE -> handleMenuSelection(player, "CLOSE");
            case SLOT_PREV_PAGE -> handleMenuSelection(player, "PREV_PAGE");
            case SLOT_NEXT_PAGE -> handleMenuSelection(player, "NEXT_PAGE");
            case SLOT_INFO -> displayQuickInfo(player);
        }
    }

    private void showSigilDetails(Player player, Sigil sigil) {
        player.sendMessage(TextUtil.colorize(""));
        player.sendMessage(TextUtil.colorize("§8&m----------&r §d&l" + sigil.getName() + " §8&m----------"));
        player.sendMessage(TextUtil.colorize("§7ID: §f" + sigil.getId()));
        player.sendMessage(TextUtil.colorize("§7Slot: §f" + sigil.getSlot()));
        player.sendMessage(TextUtil.colorize("§7Tier: §f" + sigil.getTier()));
        player.sendMessage(TextUtil.colorize(""));

        if (!sigil.getDescription().isEmpty()) {
            player.sendMessage(TextUtil.colorize("§b&lDescription:"));
            for (String line : sigil.getDescription()) {
                player.sendMessage(TextUtil.colorize("§7  " + line));
            }
        }

        if (sigil.hasFlows()) {
            player.sendMessage(TextUtil.colorize(""));
            player.sendMessage(TextUtil.colorize("§b&lFlows:"));
            for (com.miracle.arcanesigils.flow.FlowConfig flow : sigil.getFlows()) {
                String flowName = flow.getTrigger() != null ? flow.getTrigger() : "flow";
                flowName = TextUtil.toProperCase(flowName.replace("_", " "));
                int nodeCount = flow.hasNodes() ? flow.getGraph().getNodeCount() : 0;
                player.sendMessage(TextUtil.colorize("§b  " + flowName + ": §7(" + nodeCount + " nodes)"));
            }
        }

        player.sendMessage(TextUtil.colorize("§8&m-----------------------------------------"));
        playSound(player, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory activeMenu = activeMenus.get(player.getUniqueId());
        if (activeMenu != null && event.getInventory() == activeMenu) {
            activeMenus.remove(player.getUniqueId());
            // Don't remove state immediately - allow re-opening
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerStates.remove(uuid);
        activeMenus.remove(uuid);
    }

    // ============ Utility Methods ============

    private MenuState getOrCreateState(Player player) {
        return playerStates.computeIfAbsent(player.getUniqueId(), MenuState::new);
    }

    private void playSound(Player player, Sound sound, float pitch) {
        player.playSound(player.getLocation(), sound, 0.5f, pitch);
    }

    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            playerStates.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }, 20L * 60, 20L * 60); // Run every minute
    }

    /**
     * Cleanup resources when the component is disabled.
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        // Close all active menus
        for (UUID uuid : new HashSet<>(activeMenus.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory();
            }
        }

        playerStates.clear();
        activeMenus.clear();
    }

    /**
     * Get the plugin instance.
     *
     * @return The ArmorSetsPlugin instance
     */
    public ArmorSetsPlugin getPlugin() {
        return plugin;
    }

    /**
     * Check if a player has an active menu open.
     *
     * @param player The player to check
     * @return true if the player has a menu open
     */
    public boolean hasActiveMenu(Player player) {
        return activeMenus.containsKey(player.getUniqueId());
    }

    /**
     * Get the current menu state for a player.
     *
     * @param player The player
     * @return The MenuState or null if not found
     */
    public MenuState getMenuState(Player player) {
        return playerStates.get(player.getUniqueId());
    }
}
