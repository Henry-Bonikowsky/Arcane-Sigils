package com.zenax.dungeons.loot;

import com.zenax.dungeons.DungeonsAddon;
import com.zenax.dungeons.dungeon.DungeonDifficulty;
import com.zenax.dungeons.dungeon.DungeonInstance;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages loot tables and treasure chests for the dungeon system.
 * Handles loading, generation, and distribution of loot.
 */
public class LootManager {
    private final DungeonsAddon addon;
    private final Map<String, LootTable> lootTables;
    private final Map<Location, TreasureChest> treasureChests;
    private final Map<UUID, TreasureChest> chestsById;
    private final Random random;

    /**
     * Creates a new loot manager.
     *
     * @param addon The dungeons addon instance
     */
    public LootManager(DungeonsAddon addon) {
        this.addon = addon;
        this.lootTables = new ConcurrentHashMap<>();
        this.treasureChests = new ConcurrentHashMap<>();
        this.chestsById = new ConcurrentHashMap<>();
        this.random = new Random();
    }

    /**
     * Loads loot tables from configuration.
     */
    public void loadLootTables() {
        lootTables.clear();

        File lootFile = new File(addon.getDataFolder(), "loot.yml");
        if (!lootFile.exists()) {
            createDefaultLootConfig(lootFile);
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(lootFile);
            ConfigurationSection tablesSection = config.getConfigurationSection("loot-tables");

            if (tablesSection != null) {
                for (String tableId : tablesSection.getKeys(false)) {
                    ConfigurationSection tableSection = tablesSection.getConfigurationSection(tableId);
                    LootTable table = LootTable.fromConfig(tableId, tableSection);

                    if (table != null) {
                        lootTables.put(tableId, table);
                        addon.info("Loaded loot table: " + tableId + " (" + table.getEntryCount() + " entries)");
                    } else {
                        addon.warn("Failed to load loot table: " + tableId);
                    }
                }
            }

            addon.info("Loaded " + lootTables.size() + " loot tables");
        } catch (Exception e) {
            addon.getLogger().log(Level.SEVERE, "Failed to load loot tables", e);
        }
    }

    /**
     * Creates a default loot configuration file.
     *
     * @param file The file to create
     */
    private void createDefaultLootConfig(File file) {
        try {
            file.getParentFile().mkdirs();
            FileConfiguration config = new YamlConfiguration();

            // Example basic loot table
            config.set("loot-tables.basic.min-rolls", 1);
            config.set("loot-tables.basic.max-rolls", 3);
            config.set("loot-tables.basic.guaranteed-drop", true);
            config.set("loot-tables.basic.entries.coins.material", "GOLD_NUGGET");
            config.set("loot-tables.basic.entries.coins.min-amount", 1);
            config.set("loot-tables.basic.entries.coins.max-amount", 5);
            config.set("loot-tables.basic.entries.coins.weight", 10.0);
            config.set("loot-tables.basic.entries.coins.rarity", "COMMON");

            // Example rare loot table
            config.set("loot-tables.rare.min-rolls", 2);
            config.set("loot-tables.rare.max-rolls", 4);
            config.set("loot-tables.rare.guaranteed-drop", true);
            config.set("loot-tables.rare.entries.diamond.material", "DIAMOND");
            config.set("loot-tables.rare.entries.diamond.min-amount", 1);
            config.set("loot-tables.rare.entries.diamond.max-amount", 3);
            config.set("loot-tables.rare.entries.diamond.weight", 5.0);
            config.set("loot-tables.rare.entries.diamond.rarity", "RARE");
            config.set("loot-tables.rare.entries.diamond.conditions", Arrays.asList("difficulty>=HARD"));

            config.save(file);
            addon.info("Created default loot configuration");
        } catch (IOException e) {
            addon.getLogger().log(Level.SEVERE, "Failed to create default loot config", e);
        }
    }

    /**
     * Generates loot from a loot table.
     *
     * @param tableId The loot table ID
     * @param difficulty The dungeon difficulty
     * @param luckModifier Luck modifier for generation
     * @return List of generated items
     */
    public List<ItemStack> generateLoot(String tableId, DungeonDifficulty difficulty, double luckModifier) {
        LootTable table = lootTables.get(tableId);
        if (table == null) {
            addon.warn("Loot table not found: " + tableId);
            return new ArrayList<>();
        }

        return table.generateLoot(random, luckModifier, difficulty);
    }

    /**
     * Registers a treasure chest in the system.
     *
     * @param chest The treasure chest to register
     * @return true if successfully registered
     */
    public boolean registerChest(TreasureChest chest) {
        if (chest == null) {
            return false;
        }

        treasureChests.put(chest.getLocation(), chest);
        chestsById.put(chest.getChestId(), chest);
        return true;
    }

    /**
     * Unregisters a treasure chest from the system.
     *
     * @param chest The treasure chest to unregister
     * @return true if successfully unregistered
     */
    public boolean unregisterChest(TreasureChest chest) {
        if (chest == null) {
            return false;
        }

        treasureChests.remove(chest.getLocation());
        chestsById.remove(chest.getChestId());
        return true;
    }

    /**
     * Opens a chest at the specified location for a player.
     *
     * @param player The player opening the chest
     * @param location The location of the chest
     * @return true if the chest was successfully opened
     */
    public boolean openChest(Player player, Location location) {
        TreasureChest chest = treasureChests.get(location);
        if (chest == null) {
            return false;
        }

        return chest.open(player, this);
    }

    /**
     * Gets a chest by its location.
     *
     * @param location The chest location
     * @return The treasure chest, or null if not found
     */
    public TreasureChest getChest(Location location) {
        return treasureChests.get(location);
    }

    /**
     * Gets a chest by its UUID.
     *
     * @param chestId The chest UUID
     * @return The treasure chest, or null if not found
     */
    public TreasureChest getChest(UUID chestId) {
        return chestsById.get(chestId);
    }

    /**
     * Gets all chests for a specific dungeon instance.
     *
     * @param instance The dungeon instance
     * @return List of treasure chests
     */
    public List<TreasureChest> getChestsForInstance(DungeonInstance instance) {
        List<TreasureChest> chests = new ArrayList<>();
        for (TreasureChest chest : treasureChests.values()) {
            if (chest.getInstance().equals(instance)) {
                chests.add(chest);
            }
        }
        return chests;
    }

    /**
     * Removes all chests for a specific dungeon instance.
     *
     * @param instance The dungeon instance
     */
    public void removeChestsForInstance(DungeonInstance instance) {
        List<TreasureChest> toRemove = getChestsForInstance(instance);
        for (TreasureChest chest : toRemove) {
            chest.removeChest();
            unregisterChest(chest);
        }
    }

    /**
     * Distributes loot to a list of players.
     * Items are given in round-robin fashion.
     *
     * @param players List of players to receive loot
     * @param loot List of items to distribute
     */
    public void distributeLoot(List<Player> players, List<ItemStack> loot) {
        if (players == null || players.isEmpty() || loot == null || loot.isEmpty()) {
            return;
        }

        int playerIndex = 0;
        for (ItemStack item : loot) {
            if (item == null || item.getType() == org.bukkit.Material.AIR) {
                continue;
            }

            Player player = players.get(playerIndex);
            giveItem(player, item);

            playerIndex = (playerIndex + 1) % players.size();
        }
    }

    /**
     * Gives an item to a player.
     * If inventory is full, drops the item at player's location.
     *
     * @param player The player to give the item to
     * @param item The item to give
     */
    private void giveItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            // Inventory full, drop items
            for (ItemStack overflowItem : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
            }
            player.sendMessage(org.bukkit.ChatColor.YELLOW + "Some items were dropped because your inventory is full!");
        }
    }

    /**
     * Gets a loot table by ID.
     *
     * @param tableId The loot table ID
     * @return The loot table, or null if not found
     */
    public LootTable getLootTable(String tableId) {
        return lootTables.get(tableId);
    }

    /**
     * Gets all registered loot tables.
     *
     * @return Map of loot tables by ID
     */
    public Map<String, LootTable> getLootTables() {
        return new HashMap<>(lootTables);
    }

    /**
     * Gets all registered treasure chests.
     *
     * @return Map of treasure chests by location
     */
    public Map<Location, TreasureChest> getTreasureChests() {
        return new HashMap<>(treasureChests);
    }

    /**
     * Checks if a loot table exists.
     *
     * @param tableId The loot table ID
     * @return true if the table exists
     */
    public boolean hasLootTable(String tableId) {
        return lootTables.containsKey(tableId);
    }

    /**
     * Creates a treasure chest at a location for a dungeon instance.
     *
     * @param location The chest location
     * @param lootTableId The loot table ID to use
     * @param instance The dungeon instance
     * @return The created treasure chest, or null if failed
     */
    public TreasureChest createChest(Location location, String lootTableId, DungeonInstance instance) {
        if (!hasLootTable(lootTableId)) {
            addon.warn("Cannot create chest with invalid loot table: " + lootTableId);
            return null;
        }

        TreasureChest chest = new TreasureChest(location, lootTableId, instance);
        if (chest.spawnChest()) {
            registerChest(chest);
            return chest;
        }

        return null;
    }

    /**
     * Gets the number of registered loot tables.
     *
     * @return The loot table count
     */
    public int getLootTableCount() {
        return lootTables.size();
    }

    /**
     * Gets the number of registered treasure chests.
     *
     * @return The treasure chest count
     */
    public int getTreasureChestCount() {
        return treasureChests.size();
    }

    /**
     * Clears all treasure chests.
     * This does not affect loot tables.
     */
    public void clearChests() {
        for (TreasureChest chest : new ArrayList<>(treasureChests.values())) {
            chest.removeChest();
        }
        treasureChests.clear();
        chestsById.clear();
    }

    /**
     * Reloads loot tables from configuration.
     */
    public void reload() {
        loadLootTables();
    }
}
