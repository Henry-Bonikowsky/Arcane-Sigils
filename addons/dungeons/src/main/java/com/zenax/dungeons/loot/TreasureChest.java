package com.zenax.dungeons.loot;

import com.zenax.dungeons.dungeon.DungeonInstance;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Represents an interactive treasure chest in a dungeon.
 * Players can open chests to receive loot from a loot table.
 */
public class TreasureChest {
    private final Location location;
    private final String lootTableId;
    private boolean opened;
    private List<ItemStack> contents;
    private final DungeonInstance instance;
    private final UUID chestId;

    /**
     * Creates a new treasure chest.
     *
     * @param location The location of the chest
     * @param lootTableId The ID of the loot table to use
     * @param instance The dungeon instance this chest belongs to
     */
    public TreasureChest(Location location, String lootTableId, DungeonInstance instance) {
        this.location = location.clone();
        this.lootTableId = lootTableId;
        this.opened = false;
        this.contents = new ArrayList<>();
        this.instance = instance;
        this.chestId = UUID.randomUUID();
    }

    /**
     * Gets the unique ID of this chest.
     *
     * @return The chest UUID
     */
    public UUID getChestId() {
        return chestId;
    }

    /**
     * Gets the location of this chest.
     *
     * @return The chest location
     */
    public Location getLocation() {
        return location.clone();
    }

    /**
     * Gets the loot table ID for this chest.
     *
     * @return The loot table ID
     */
    public String getLootTableId() {
        return lootTableId;
    }

    /**
     * Checks if this chest has been opened.
     *
     * @return true if the chest is opened
     */
    public boolean isOpened() {
        return opened;
    }

    /**
     * Gets the dungeon instance this chest belongs to.
     *
     * @return The dungeon instance
     */
    public DungeonInstance getInstance() {
        return instance;
    }

    /**
     * Gets the pre-generated contents of this chest.
     *
     * @return The contents list
     */
    public List<ItemStack> getContents() {
        return new ArrayList<>(contents);
    }

    /**
     * Sets the contents of this chest.
     *
     * @param contents The new contents
     */
    public void setContents(List<ItemStack> contents) {
        this.contents = contents != null ? new ArrayList<>(contents) : new ArrayList<>();
    }

    /**
     * Opens the chest for a player.
     * Generates loot if not already opened, and displays it to the player.
     *
     * @param player The player opening the chest
     * @param lootManager The loot manager for generating loot
     * @return true if the chest was successfully opened
     */
    public boolean open(Player player, LootManager lootManager) {
        if (opened) {
            player.sendMessage(ChatColor.YELLOW + "This chest has already been looted!");
            return false;
        }

        // Generate loot if not already generated
        if (contents.isEmpty()) {
            double luckModifier = instance.getDifficulty().getLootMultiplier();
            contents = lootManager.generateLoot(lootTableId, instance.getDifficulty(), luckModifier);
        }

        // Mark as opened
        opened = true;

        // Play effects
        playOpenEffects();

        // Fill the physical chest with loot
        fillPhysicalChest();

        // Notify player
        player.sendMessage(ChatColor.GREEN + "You've discovered a treasure chest!");
        player.playSound(location, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        return true;
    }

    /**
     * Plays visual and sound effects when the chest is opened.
     */
    private void playOpenEffects() {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        // Particle effects
        world.spawnParticle(Particle.FIREWORK,
                          location.clone().add(0.5, 1.0, 0.5),
                          30, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticle(Particle.HAPPY_VILLAGER,
                          location.clone().add(0.5, 1.0, 0.5),
                          20, 0.3, 0.3, 0.3, 0.0);

        // Sound effects
        world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
        world.playSound(location, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }

    /**
     * Fills the physical chest block with the generated loot.
     */
    private void fillPhysicalChest() {
        Block block = location.getBlock();
        if (block.getState() instanceof Chest) {
            Chest chest = (Chest) block.getState();
            Inventory inventory = chest.getInventory();
            inventory.clear();

            // Add items to chest
            for (ItemStack item : contents) {
                if (item != null && item.getType() != Material.AIR) {
                    inventory.addItem(item);
                }
            }

            chest.update();
        }
    }

    /**
     * Spawns a chest block at this location.
     *
     * @return true if the chest was successfully spawned
     */
    public boolean spawnChest() {
        Block block = location.getBlock();
        if (block.getType() != Material.AIR) {
            // Try to find nearby air block
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block nearbyBlock = block.getRelative(dx, 0, dz);
                    if (nearbyBlock.getType() == Material.AIR) {
                        nearbyBlock.setType(Material.CHEST);
                        return true;
                    }
                }
            }
            return false;
        }

        block.setType(Material.CHEST);
        return true;
    }

    /**
     * Removes the chest block at this location.
     */
    public void removeChest() {
        Block block = location.getBlock();
        if (block.getType() == Material.CHEST) {
            block.setType(Material.AIR);
        }
    }

    /**
     * Checks if the chest block still exists at this location.
     *
     * @return true if the chest block exists
     */
    public boolean exists() {
        return location.getBlock().getType() == Material.CHEST;
    }

    /**
     * Resets the chest to its unopened state.
     * Clears contents and marks as not opened.
     */
    public void reset() {
        opened = false;
        contents.clear();
        fillPhysicalChest(); // Clear the physical chest
    }

    /**
     * Gets the distance from a location to this chest.
     *
     * @param loc The location to measure from
     * @return The distance in blocks
     */
    public double getDistance(Location loc) {
        if (loc.getWorld() != location.getWorld()) {
            return Double.MAX_VALUE;
        }
        return loc.distance(location);
    }

    /**
     * Checks if a player is within interaction range of this chest.
     *
     * @param player The player to check
     * @param maxDistance Maximum interaction distance
     * @return true if the player is in range
     */
    public boolean isInRange(Player player, double maxDistance) {
        return getDistance(player.getLocation()) <= maxDistance;
    }

    @Override
    public String toString() {
        return "TreasureChest{" +
               "location=" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
               ", lootTable='" + lootTableId + '\'' +
               ", opened=" + opened +
               ", items=" + contents.size() +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TreasureChest that = (TreasureChest) o;
        return Objects.equals(chestId, that.chestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chestId);
    }
}
