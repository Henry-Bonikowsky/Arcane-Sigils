package com.miracle.arcanesigils.enchanter.commands;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.enchanter.EnchanterManager;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command executor for /ce (Cosmic Enchanter) command.
 * Provides access to the Enchanter GUI and admin functions.
 */
public class EnchanterCommand implements CommandExecutor, TabCompleter {

    private final ArmorSetsPlugin plugin;
    private final EnchanterManager enchanterManager;
    private final GUIManager guiManager;

    public EnchanterCommand(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.enchanterManager = plugin.getEnchanterManager();
        this.guiManager = plugin.getGuiManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        // No args - open main GUI
        if (args.length == 0) {
            if (!player.hasPermission("arcanesigils.enchanter.use")) {
                player.sendMessage("§cYou don't have permission to use the Enchanter.");
                return true;
            }

            GUISession session = new GUISession(GUIType.ENCHANTER_MAIN);
            guiManager.reopenGUI(player, session);
            return true;
        }

        // Subcommands
        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "setblock" -> {
                return handleSetBlock(player);
            }
            case "removeblock" -> {
                return handleRemoveBlock(player);
            }
            case "info" -> {
                return handleInfo(player);
            }
            case "reload" -> {
                return handleReload(player);
            }
            default -> {
                player.sendMessage("§cUnknown subcommand. Usage:");
                player.sendMessage("§7/ce §8- Open Enchanter GUI");
                player.sendMessage("§7/ce setblock §8- Register current block");
                player.sendMessage("§7/ce removeblock §8- Unregister current block");
                player.sendMessage("§7/ce info §8- List registered blocks");
                player.sendMessage("§7/ce reload §8- Reload configuration");
                return true;
            }
        }
    }

    /**
     * Register the block the player is looking at as an Enchanter.
     */
    private boolean handleSetBlock(Player player) {
        if (!player.hasPermission("arcanesigils.enchanter.admin")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            player.sendMessage("§cYou must be looking at a block.");
            return true;
        }

        if (enchanterManager.isEnchanterBlock(targetBlock)) {
            player.sendMessage("§cThis block is already registered as an Enchanter.");
            return true;
        }

        if (enchanterManager.registerBlock(targetBlock)) {
            player.sendMessage("§aSuccessfully registered Enchanter block at " +
                formatLocation(targetBlock.getLocation()));
        } else {
            player.sendMessage("§cFailed to register Enchanter block.");
        }

        return true;
    }

    /**
     * Unregister the block the player is looking at.
     */
    private boolean handleRemoveBlock(Player player) {
        if (!player.hasPermission("arcanesigils.enchanter.admin")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            player.sendMessage("§cYou must be looking at a block.");
            return true;
        }

        if (!enchanterManager.isEnchanterBlock(targetBlock)) {
            player.sendMessage("§cThis block is not registered as an Enchanter.");
            return true;
        }

        if (enchanterManager.unregisterBlock(targetBlock)) {
            player.sendMessage("§aSuccessfully unregistered Enchanter block.");
        } else {
            player.sendMessage("§cFailed to unregister Enchanter block.");
        }

        return true;
    }

    /**
     * List all registered Enchanter locations.
     */
    private boolean handleInfo(Player player) {
        if (!player.hasPermission("arcanesigils.enchanter.admin")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        List<Location> locations = enchanterManager.getLocations();

        if (locations.isEmpty()) {
            player.sendMessage("§7No Enchanter blocks registered.");
            return true;
        }

        player.sendMessage("§7Registered Enchanter blocks (" + locations.size() + "):");
        for (Location loc : locations) {
            player.sendMessage("§8- §f" + formatLocation(loc));
        }

        return true;
    }

    /**
     * Reload Enchanter configuration.
     */
    private boolean handleReload(Player player) {
        if (!player.hasPermission("arcanesigils.enchanter.admin")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        enchanterManager.reload();
        player.sendMessage("§aEnchanter configuration reloaded.");
        return true;
    }

    /**
     * Format a location for display.
     */
    private String formatLocation(Location loc) {
        return loc.getWorld().getName() + " " +
               loc.getBlockX() + ", " +
               loc.getBlockY() + ", " +
               loc.getBlockZ();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<String> subcommands = Arrays.asList("setblock", "removeblock", "info", "reload");

            for (String sub : subcommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    if (player.hasPermission("arcanesigils.enchanter.admin")) {
                        completions.add(sub);
                    }
                }
            }

            return completions;
        }

        return new ArrayList<>();
    }
}
