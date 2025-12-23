package com.zenax.dungeons.lobby;

import com.zenax.dungeons.DungeonsAddon;
import com.zenax.dungeons.dungeon.Dungeon;
import com.zenax.dungeons.dungeon.DungeonDifficulty;
import com.zenax.dungeons.dungeon.DungeonManager;
import com.zenax.dungeons.dungeon.ObjectiveMode;
import com.zenax.dungeons.sound.DungeonSoundEffects;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Handles events related to dungeon lobbies.
 * Detects player interactions with hologram, settings GUI, leave portal, and disconnections.
 */
public class LobbyHandler implements Listener {
    private final Plugin plugin;
    private final LobbyManager lobbyManager;
    private final DungeonManager dungeonManager;

    private static final String SETTINGS_GUI_TITLE = "Dungeon Settings";

    /**
     * Creates a new lobby handler.
     *
     * @param lobbyManager The lobby manager instance
     * @param dungeonManager The dungeon manager instance
     */
    public LobbyHandler(LobbyManager lobbyManager, DungeonManager dungeonManager) {
        this.plugin = DungeonsAddon.getInstance().getPlugin();
        this.lobbyManager = lobbyManager;
        this.dungeonManager = dungeonManager;
    }

    /**
     * Handles player interaction with the hologram Interaction entity.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();

        // Check if it's an Interaction entity (the clickable hologram hitbox)
        if (!(clickedEntity instanceof Interaction)) {
            return;
        }

        DungeonLobby lobby = lobbyManager.getPlayerLobby(player);
        if (lobby == null) {
            return;
        }

        // Check if this is the lobby's hologram interaction entity
        UUID hologramId = lobby.getHologramEntityId();
        if (hologramId != null && clickedEntity.getUniqueId().equals(hologramId)) {
            event.setCancelled(true);
            openSettingsGUI(player, lobby);
        }
    }

    /**
     * Opens the combined settings GUI for a player.
     * Includes difficulty selection, objective mode selection, and START button.
     *
     * @param player The player to open the GUI for
     * @param lobby The lobby the player is in
     */
    private void openSettingsGUI(Player player, DungeonLobby lobby) {
        Dungeon dungeon = lobby.getDungeonTemplate();
        Inventory gui = Bukkit.createInventory(new SettingsGUIHolder(lobby), 27, SETTINGS_GUI_TITLE);

        // Row 0: Info item (slot 4)
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("\u00A76\u00A7l" + dungeon.getDisplayName());
            List<String> infoLore = new ArrayList<>();
            infoLore.add("\u00A77" + dungeon.getDescription());
            infoLore.add("");
            infoLore.add("\u00A7ePlayers: \u00A7f" + lobby.getPlayerCount() + "/" + dungeon.getMaxPlayers());
            infoLore.add("\u00A7eTime Limit: \u00A7f" + (dungeon.hasTimeLimit() ? dungeon.getTimeLimit() + "s" : "None"));
            infoMeta.setLore(infoLore);
            infoItem.setItemMeta(infoMeta);
        }
        gui.setItem(4, infoItem);

        // Row 1: Difficulty selection (slots 10-13)
        DungeonDifficulty[] difficulties = DungeonDifficulty.values();
        int[] difficultySlots = {10, 11, 12, 13};
        for (int i = 0; i < difficulties.length && i < difficultySlots.length; i++) {
            DungeonDifficulty difficulty = difficulties[i];
            ItemStack item = new ItemStack(getDifficultyIcon(difficulty));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(difficulty.getColor() + "\u00A7l" + difficulty.getDisplayName());
                List<String> lore = new ArrayList<>();
                lore.add("\u00A77Mob Multiplier: \u00A7f" + difficulty.getMobMultiplier() + "x");
                lore.add("\u00A77Loot Multiplier: \u00A7f" + difficulty.getLootMultiplier() + "x");
                lore.add("");
                if (difficulty.hasUniqueDrops()) {
                    lore.add("\u00A7d\u00A7lHas Unique Drops!");
                    lore.add("");
                }
                if (lobby.getSelectedDifficulty() == difficulty) {
                    lore.add("\u00A7a\u00A7l>>> SELECTED <<<");
                } else {
                    lore.add("\u00A7eClick to select");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            gui.setItem(difficultySlots[i], item);
        }

        // Row 2: Objective modes (slots 19-21) and START button (slot 25)
        List<ObjectiveMode> modes = dungeon.getAvailableObjectiveModes();
        int[] modeSlots = {19, 20, 21};
        for (int i = 0; i < modes.size() && i < modeSlots.length; i++) {
            ObjectiveMode mode = modes.get(i);
            ItemStack item = new ItemStack(getModeIcon(mode));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("\u00A7e\u00A7l" + mode.getDisplayName());
                List<String> lore = new ArrayList<>();
                lore.add("\u00A77" + mode.getDescription());
                lore.add("");
                if (lobby.getSelectedObjectiveMode() == mode) {
                    lore.add("\u00A7a\u00A7l>>> SELECTED <<<");
                } else {
                    lore.add("\u00A7eClick to select");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            gui.setItem(modeSlots[i], item);
        }

        // START button (slot 25)
        ItemStack startItem = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta startMeta = startItem.getItemMeta();
        if (startMeta != null) {
            startMeta.setDisplayName("\u00A7a\u00A7lSTART DUNGEON");
            List<String> startLore = new ArrayList<>();
            startLore.add("");
            startLore.add("\u00A77Current settings:");
            startLore.add("\u00A77Difficulty: " + lobby.getSelectedDifficulty().getColoredDisplayName());
            startLore.add("\u00A77Mode: \u00A7f" + lobby.getSelectedObjectiveMode().getDisplayName());
            startLore.add("");
            startLore.add("\u00A7eClick to begin the dungeon!");
            startMeta.setLore(startLore);
            startItem.setItemMeta(startMeta);
        }
        gui.setItem(25, startItem);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    /**
     * Handles clicks in the settings GUI.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof SettingsGUIHolder)) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        SettingsGUIHolder holder = (SettingsGUIHolder) inv.getHolder();
        DungeonLobby lobby = holder.getLobby();

        // Verify player is still in lobby
        if (!lobby.hasPlayer(player)) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) {
            return;
        }

        // Difficulty slots (10-13)
        DungeonDifficulty[] difficulties = DungeonDifficulty.values();
        int[] difficultySlots = {10, 11, 12, 13};
        for (int i = 0; i < difficultySlots.length && i < difficulties.length; i++) {
            if (slot == difficultySlots[i]) {
                lobby.setDifficulty(difficulties[i]);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                lobbyManager.broadcastToLobby(lobby, "\u00A7e" + player.getName() + " \u00A77set difficulty to " +
                    difficulties[i].getColoredDisplayName());
                updateHologramText(lobby);
                openSettingsGUI(player, lobby); // Refresh GUI
                return;
            }
        }

        // Objective mode slots (19-21)
        List<ObjectiveMode> modes = lobby.getDungeonTemplate().getAvailableObjectiveModes();
        int[] modeSlots = {19, 20, 21};
        for (int i = 0; i < modeSlots.length && i < modes.size(); i++) {
            if (slot == modeSlots[i]) {
                lobby.setObjectiveMode(modes.get(i));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                lobbyManager.broadcastToLobby(lobby, "\u00A7e" + player.getName() + " \u00A77set objective to \u00A7f" +
                    modes.get(i).getDisplayName());
                updateHologramText(lobby);
                openSettingsGUI(player, lobby); // Refresh GUI
                return;
            }
        }

        // START button (slot 25)
        if (slot == 25) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

            // Start the dungeon!
            lobbyManager.broadcastToLobby(lobby, "\u00A7a\u00A7l" + player.getName() + " started the dungeon!");
            lobbyManager.startDungeon(lobby);
        }
    }

    /**
     * Updates the hologram text to show current settings.
     *
     * @param lobby The lobby to update
     */
    private void updateHologramText(DungeonLobby lobby) {
        UUID hologramId = lobby.getHologramEntityId();
        if (hologramId == null) {
            return;
        }

        // Find the TextDisplay entity
        Location spawnPoint = lobby.getSpawnPoint();
        if (spawnPoint.getWorld() == null) {
            return;
        }

        Entity entity = Bukkit.getEntity(hologramId);
        if (entity instanceof TextDisplay textDisplay) {
            Dungeon dungeon = lobby.getDungeonTemplate();
            String text = "\u00A76\u00A7l" + dungeon.getDisplayName() + "\n" +
                         "\u00A77Right-click to configure\n" +
                         "\u00A78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n" +
                         "\u00A77Difficulty: " + lobby.getSelectedDifficulty().getColoredDisplayName() + "\n" +
                         "\u00A77Mode: \u00A7f" + lobby.getSelectedObjectiveMode().getDisplayName();
            textDisplay.setText(text);
        }
    }

    /**
     * Handles a player entering the leave portal.
     * Teleports them back to their original location and removes them from the lobby.
     *
     * @param player The player who entered the portal
     * @param lobby The lobby they're leaving
     */
    private void handleLeavePortal(Player player, DungeonLobby lobby) {
        // Play teleport effect
        DungeonSoundEffects.playPortalEnter(player);

        // Notify the lobby
        lobbyManager.broadcastToLobby(lobby, "\u00A7e" + player.getName() + " \u00A77has left the dungeon lobby.");

        // Remove player from lobby
        lobbyManager.leaveLobby(player);

        // Teleport to original location or world spawn
        Location returnLoc = lobby.getPlayerOriginalLocation(player.getUniqueId());
        if (returnLoc == null) {
            returnLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        player.teleport(returnLoc);

        // Send message
        player.sendMessage("\u00A7aYou have left the dungeon lobby.");
    }

    /**
     * Handles player interactions with signs and blocks in the lobby.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        DungeonLobby lobby = lobbyManager.getPlayerLobby(player);

        if (lobby == null) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        Location clickedLoc = clickedBlock.getLocation();

        // Check objective selector - open combined settings GUI
        if (isLocationMatch(clickedLoc, lobby.getObjectiveSelectorLocation())) {
            event.setCancelled(true);
            openSettingsGUI(player, lobby);
            return;
        }

        // Check difficulty selector - open combined settings GUI
        if (isLocationMatch(clickedLoc, lobby.getDifficultySelectorLocation())) {
            event.setCancelled(true);
            openSettingsGUI(player, lobby);
            return;
        }

        // Check info board
        if (isLocationMatch(clickedLoc, lobby.getInfoBoardLocation())) {
            event.setCancelled(true);
            showDungeonInfo(player, lobby);
            return;
        }

        // Check loot preview
        if (isLocationMatch(clickedLoc, lobby.getLootPreviewLocation())) {
            event.setCancelled(true);
            openLootPreviewGUI(player, lobby);
            return;
        }

        // Check for sign interactions
        if (clickedBlock.getState() instanceof Sign sign) {
            String firstLine = ChatColor.stripColor(sign.getLine(0));

            if (firstLine.equalsIgnoreCase("[Objective]") ||
                firstLine.equalsIgnoreCase("[Difficulty]") ||
                firstLine.equalsIgnoreCase("[Settings]")) {
                event.setCancelled(true);
                openSettingsGUI(player, lobby);
            } else if (firstLine.equalsIgnoreCase("[Info]")) {
                event.setCancelled(true);
                showDungeonInfo(player, lobby);
            } else if (firstLine.equalsIgnoreCase("[Loot]")) {
                event.setCancelled(true);
                openLootPreviewGUI(player, lobby);
            }
        }
    }

    /**
     * Handles player disconnections in the lobby.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DungeonLobby lobby = lobbyManager.getPlayerLobby(player);

        if (lobby != null) {
            lobbyManager.leaveLobby(player);
        }
    }

    /**
     * Opens the loot preview GUI for a player.
     *
     * @param player The player to open the GUI for
     * @param lobby The lobby the player is in
     */
    private void openLootPreviewGUI(Player player, DungeonLobby lobby) {
        Inventory gui = Bukkit.createInventory(null, 54, "Dungeon Loot Preview");

        // TODO: Implement actual loot preview from loot tables
        // For now, show placeholder items

        ItemStack placeholder = new ItemStack(Material.CHEST);
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00A7e\u00A7lLoot Preview");
            meta.setLore(Arrays.asList(
                "\u00A77Defeat enemies and bosses to",
                "\u00A77receive valuable loot!",
                "",
                "\u00A77Loot quality increases with",
                "\u00A77difficulty level."
            ));
            placeholder.setItemMeta(meta);
        }

        gui.setItem(22, placeholder);
        player.openInventory(gui);
    }

    /**
     * Shows dungeon information to a player.
     *
     * @param player The player to show info to
     * @param lobby The lobby the player is in
     */
    private void showDungeonInfo(Player player, DungeonLobby lobby) {
        Dungeon dungeon = lobby.getDungeonTemplate();

        player.sendMessage("\u00A78\u00A7m                                                    ");
        player.sendMessage("\u00A76\u00A7l" + dungeon.getDisplayName());
        player.sendMessage("");
        player.sendMessage("\u00A77" + dungeon.getDescription());
        player.sendMessage("");
        player.sendMessage("\u00A7ePlayers: \u00A7f" + dungeon.getMinPlayers() + "-" + dungeon.getMaxPlayers());
        player.sendMessage("\u00A7eTime Limit: \u00A7f" + (dungeon.hasTimeLimit() ? dungeon.getTimeLimit() + "s" : "None"));
        player.sendMessage("\u00A7eDefault Difficulty: " + dungeon.getDefaultDifficulty().getColoredDisplayName());
        player.sendMessage("");
        player.sendMessage("\u00A7eAvailable Objective Modes:");
        for (ObjectiveMode mode : dungeon.getAvailableObjectiveModes()) {
            player.sendMessage("  \u00A77- \u00A7f" + mode.getDisplayName());
        }
        player.sendMessage("");
        player.sendMessage("\u00A7eCurrent Settings:");
        player.sendMessage("  \u00A77Objective: \u00A7f" + lobby.getSelectedObjectiveMode().getDisplayName());
        player.sendMessage("  \u00A77Difficulty: " + lobby.getSelectedDifficulty().getColoredDisplayName());
        player.sendMessage("\u00A78\u00A7m                                                    ");
    }

    /**
     * Gets the icon material for an objective mode.
     *
     * @param mode The objective mode
     * @return The Material to use as an icon
     */
    private Material getModeIcon(ObjectiveMode mode) {
        return switch (mode) {
            case BOSS_KILL -> Material.DIAMOND_SWORD;
            case OBJECTIVES -> Material.WRITABLE_BOOK;
            default -> Material.PAPER;
        };
    }

    /**
     * Gets the icon material for a difficulty.
     *
     * @param difficulty The difficulty
     * @return The Material to use as an icon
     */
    private Material getDifficultyIcon(DungeonDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> Material.WOODEN_SWORD;
            case NORMAL -> Material.IRON_SWORD;
            case HARD -> Material.DIAMOND_SWORD;
            case NIGHTMARE -> Material.NETHERITE_SWORD;
            default -> Material.STICK;
        };
    }

    /**
     * Checks if two locations match (same block position).
     *
     * @param loc1 First location
     * @param loc2 Second location
     * @return true if the locations match
     */
    private boolean isLocationMatch(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) {
            return false;
        }
        return loc1.getWorld() == loc2.getWorld() &&
               loc1.getBlockX() == loc2.getBlockX() &&
               loc1.getBlockY() == loc2.getBlockY() &&
               loc1.getBlockZ() == loc2.getBlockZ();
    }

    /**
     * Registers this event handler.
     */
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Unregisters this event handler.
     */
    public void unregister() {
        PlayerInteractEvent.getHandlerList().unregister(this);
        PlayerInteractEntityEvent.getHandlerList().unregister(this);
        InventoryClickEvent.getHandlerList().unregister(this);
        PlayerQuitEvent.getHandlerList().unregister(this);
    }

    /**
     * Custom inventory holder for the settings GUI.
     */
    private static class SettingsGUIHolder implements InventoryHolder {
        private final DungeonLobby lobby;

        public SettingsGUIHolder(DungeonLobby lobby) {
            this.lobby = lobby;
        }

        public DungeonLobby getLobby() {
            return lobby;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
