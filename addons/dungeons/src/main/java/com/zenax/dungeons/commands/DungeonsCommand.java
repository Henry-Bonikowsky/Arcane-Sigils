package com.zenax.dungeons.commands;

import com.zenax.dungeons.DungeonsAddon;
import com.zenax.dungeons.dungeon.Dungeon;
import com.zenax.dungeons.dungeon.DungeonInstance;
import com.zenax.dungeons.dungeon.DungeonManager;
import com.zenax.dungeons.lobby.LobbyManager;
import com.zenax.dungeons.portal.PortalManager;
import com.zenax.dungeons.world.DungeonWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main command handler for the Dungeons addon.
 * Handles /dungeon command with all subcommands.
 */
public class DungeonsCommand implements CommandExecutor, TabCompleter {
    private final DungeonsAddon addon;
    private final DungeonManager dungeonManager;
    private final PortalManager portalManager;

    /**
     * Creates a new dungeons command handler.
     *
     * @param addon The dungeons addon instance
     * @param dungeonManager The dungeon manager
     * @param portalManager The portal manager
     */
    public DungeonsCommand(DungeonsAddon addon, DungeonManager dungeonManager, PortalManager portalManager) {
        this.addon = addon;
        this.dungeonManager = dungeonManager;
        this.portalManager = portalManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                sendHelp(sender);
                return true;

            case "list":
                return handleList(sender);

            case "info":
                return handleInfo(sender, args);

            case "leave":
                return handleLeave(sender);

            case "reset":
                return handleReset(sender);

            case "status":
                return handleStatus(sender);

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /dungeon help for help.");
                return true;
        }
    }

    /**
     * Handles the list subcommand.
     */
    private boolean handleList(CommandSender sender) {
        Map<String, Dungeon> dungeons = dungeonManager.getAllDungeons();

        if (dungeons.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No dungeons are currently available.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Available Dungeons:");
        for (Map.Entry<String, Dungeon> entry : dungeons.entrySet()) {
            Dungeon dungeon = entry.getValue();
            sender.sendMessage(ChatColor.GRAY + "  - " + ChatColor.WHITE + entry.getKey() +
                             ChatColor.GRAY + " (" + dungeon.getMinPlayers() + "-" +
                             dungeon.getMaxPlayers() + " players)");
        }

        return true;
    }

    /**
     * Handles the info subcommand.
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /dungeon info <dungeon_id>");
            return true;
        }

        String dungeonId = args[1];
        Dungeon dungeon = dungeonManager.getDungeon(dungeonId);

        if (dungeon == null) {
            sender.sendMessage(ChatColor.RED + "Dungeon not found: " + dungeonId);
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Dungeon: " + ChatColor.RESET + dungeonId);
        sender.sendMessage(ChatColor.GRAY + "Players: " + ChatColor.WHITE + dungeon.getMinPlayers() +
                         "-" + dungeon.getMaxPlayers());
        sender.sendMessage(ChatColor.GRAY + "Default Difficulty: " + ChatColor.WHITE +
                         dungeon.getDefaultDifficulty());
        sender.sendMessage(ChatColor.GRAY + "Objective Modes: " + ChatColor.WHITE +
                         dungeon.getAvailableObjectiveModes().stream()
                             .map(Object::toString)
                             .collect(Collectors.joining(", ")));

        return true;
    }

    /**
     * Handles the leave subcommand.
     */
    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can leave dungeons.");
            return true;
        }

        Player player = (Player) sender;

        // Check if in dungeon instance
        if (dungeonManager.isPlayerInDungeon(player)) {
            if (dungeonManager.removePlayerFromInstance(player)) {
                player.sendMessage(ChatColor.GREEN + "You have left the dungeon.");
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "Failed to leave dungeon.");
                return true;
            }
        }

        // Check if in lobby
        LobbyManager lobbyManager = addon.getLobbyManager();
        if (lobbyManager != null && lobbyManager.isPlayerInLobby(player)) {
            if (lobbyManager.leaveLobby(player)) {
                player.sendMessage(ChatColor.GREEN + "You have left the lobby.");
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "Failed to leave lobby.");
                return true;
            }
        }

        player.sendMessage(ChatColor.RED + "You are not in a dungeon or lobby.");
        return true;
    }

    /**
     * Handles the reset subcommand - resets the dungeon world and all instances.
     */
    private boolean handleReset(CommandSender sender) {
        if (!sender.hasPermission("dungeons.admin.reset")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to reset dungeons.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Resetting dungeon world and all instances...");

        // Get managers
        LobbyManager lobbyManager = addon.getLobbyManager();
        DungeonWorldManager worldManager = addon.getWorldManager();

        // Count active instances and lobbies
        int instanceCount = dungeonManager.getActiveInstanceCount();
        int lobbyCount = lobbyManager != null ? lobbyManager.getActiveLobbyCount() : 0;
        int portalCount = portalManager != null ? portalManager.getPortalCount() : 0;

        // Teleport all players out of dungeon world first
        if (worldManager != null && worldManager.isReady()) {
            org.bukkit.World dungeonWorld = worldManager.getDungeonWorld();
            if (dungeonWorld != null) {
                org.bukkit.World mainWorld = Bukkit.getWorlds().get(0);
                for (Player player : dungeonWorld.getPlayers()) {
                    player.teleport(mainWorld.getSpawnLocation());
                    player.sendMessage(ChatColor.YELLOW + "You have been teleported out - dungeon world is resetting.");
                }
            }
        }

        // Destroy all dungeon instances
        dungeonManager.destroyAllInstances();

        // Destroy all lobbies
        if (lobbyManager != null) {
            for (UUID lobbyId : new HashSet<>(lobbyManager.getAllLobbies().keySet())) {
                lobbyManager.destroyLobby(lobbyId);
            }
        }

        // Clear all portals
        if (portalManager != null) {
            portalManager.clearAllPortals();
        }

        // Reset the dungeon world
        if (worldManager != null) {
            worldManager.resetWorld();
        }

        sender.sendMessage(ChatColor.GREEN + "Dungeon reset complete!");
        sender.sendMessage(ChatColor.GRAY + "  - Destroyed " + instanceCount + " dungeon instance(s)");
        sender.sendMessage(ChatColor.GRAY + "  - Destroyed " + lobbyCount + " lobby(ies)");
        sender.sendMessage(ChatColor.GRAY + "  - Cleared " + portalCount + " portal(s)");
        sender.sendMessage(ChatColor.GRAY + "  - Dungeon world regenerated");

        return true;
    }

    /**
     * Handles the status subcommand - shows current dungeon system status.
     */
    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission("dungeons.admin.status")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view dungeon status.");
            return true;
        }

        LobbyManager lobbyManager = addon.getLobbyManager();
        DungeonWorldManager worldManager = addon.getWorldManager();

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Dungeon System Status:");
        sender.sendMessage(ChatColor.GRAY + "Active Instances: " + ChatColor.WHITE + dungeonManager.getActiveInstanceCount());
        sender.sendMessage(ChatColor.GRAY + "Active Lobbies: " + ChatColor.WHITE +
                         (lobbyManager != null ? lobbyManager.getActiveLobbyCount() : 0));
        sender.sendMessage(ChatColor.GRAY + "Registered Portals: " + ChatColor.WHITE +
                         (portalManager != null ? portalManager.getPortalCount() : 0));
        sender.sendMessage(ChatColor.GRAY + "Dungeon World Ready: " + ChatColor.WHITE +
                         (worldManager != null && worldManager.isReady() ? "Yes" : "No"));

        // Show players in dungeon world
        if (worldManager != null && worldManager.isReady()) {
            org.bukkit.World dungeonWorld = worldManager.getDungeonWorld();
            if (dungeonWorld != null) {
                int playerCount = dungeonWorld.getPlayers().size();
                sender.sendMessage(ChatColor.GRAY + "Players in Dungeon World: " + ChatColor.WHITE + playerCount);
            }
        }

        return true;
    }

    /**
     * Sends help message to the sender.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Dungeons Commands:");
        sender.sendMessage(ChatColor.GRAY + "/dungeon help " + ChatColor.WHITE + "- Show this help");
        sender.sendMessage(ChatColor.GRAY + "/dungeon list " + ChatColor.WHITE + "- List available dungeons");
        sender.sendMessage(ChatColor.GRAY + "/dungeon info <dungeon> " + ChatColor.WHITE + "- Show dungeon info");
        sender.sendMessage(ChatColor.GRAY + "/dungeon leave " + ChatColor.WHITE + "- Leave current dungeon/lobby");

        if (sender.hasPermission("dungeons.admin.status")) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Admin Commands:");
            sender.sendMessage(ChatColor.GRAY + "/dungeon status " + ChatColor.WHITE + "- Show system status");
            sender.sendMessage(ChatColor.GRAY + "/dungeon reset " + ChatColor.WHITE + "- Reset world and all instances");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Main subcommands
            completions.add("help");
            completions.add("list");
            completions.add("info");
            completions.add("leave");
            if (sender.hasPermission("dungeons.admin.status")) {
                completions.add("status");
            }
            if (sender.hasPermission("dungeons.admin.reset")) {
                completions.add("reset");
            }
        } else if (args.length == 2) {
            String subCmd = args[0].toLowerCase();

            if (subCmd.equals("info")) {
                // Dungeon IDs
                completions.addAll(dungeonManager.getDungeonIds());
            }
        }

        // Filter based on what the user has typed
        String currentArg = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(currentArg))
            .sorted()
            .collect(Collectors.toList());
    }
}
