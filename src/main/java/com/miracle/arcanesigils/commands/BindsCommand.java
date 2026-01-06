package com.miracle.arcanesigils.commands;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.binds.BindSystem;
import com.miracle.arcanesigils.binds.PlayerBindData;
import com.miracle.arcanesigils.binds.gui.BindsCommandHandler;
import com.miracle.arcanesigils.binds.gui.BindsHotbarHandler;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command handler for /binds.
 * Opens the binds GUI menu for managing ability keybinds.
 */
public class BindsCommand implements CommandExecutor, TabCompleter {

    private final ArmorSetsPlugin plugin;

    public BindsCommand(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize("§cPlayer only!"));
            return true;
        }

        if (!player.hasPermission("arcanesigils.binds")) {
            sender.sendMessage(TextUtil.colorize("§cYou don't have permission to use binds!"));
            return true;
        }

        // Get player's bind data to determine active system
        PlayerBindData data = plugin.getBindsManager().getPlayerData(player);
        BindSystem systemToOpen;

        // Check if user specified a system type
        if (args.length > 0) {
            String arg = args[0].toLowerCase();
            switch (arg) {
                case "hotbar" -> systemToOpen = BindSystem.HOTBAR;
                case "command" -> systemToOpen = BindSystem.COMMAND;
                default -> {
                    sender.sendMessage(TextUtil.colorize("§cUsage: /binds [hotbar|command]"));
                    return true;
                }
            }
        } else {
            // Default to player's current active system
            systemToOpen = data.getActiveSystem();
        }

        // Open the appropriate GUI using the handler instance
        if (systemToOpen == BindSystem.HOTBAR) {
            // Get the hotbar handler and open GUI
            BindsHotbarHandler handler = plugin.getGuiManager().getBindsHotbarHandler();
            if (handler != null) {
                handler.openHotbarBindsGUI(player);
            }
        } else {
            // Get the command handler and open GUI (page 1)
            BindsCommandHandler handler = plugin.getGuiManager().getBindsCommandHandler();
            if (handler != null) {
                handler.openCommandBindsGUI(player, 1);
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("hotbar", "command"));
        }

        // Filter by what the user has typed
        String lastArg = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .toList();
    }
}
