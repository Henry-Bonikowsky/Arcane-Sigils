package com.miracle.arcanesigils.commands;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.tier.TierProgressionManager;
import com.miracle.arcanesigils.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main command handler for Arcane Sigils.
 *
 * Commands (from PDF spec):
 * /as - Open Sigil Menu
 * /as help - Sends player list of commands and descriptions
 * /as reload - Reload configs
 * /as give sigil <id> [tier] - Give sigil shard to self
 * /as give sigil <player> <id> [tier] - Give sigil shard to player
 * /as list sigils - Opens pagination GUI with all sigils in debug form
 * /as socket <id> [tier] - Socket sigil to held item
 * /as unsocket - Open unsocket GUI
 * /as progress - View sigil XP progress
 */
public class ArmorSetsCommand implements CommandExecutor, TabCompleter {

    private final ArmorSetsPlugin plugin;

    public ArmorSetsCommand(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /as with no args opens Sigil Menu (per PDF spec)
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(TextUtil.colorize("§cPlayer only!"));
                return true;
            }
            plugin.getGuiManager().openSigilsMenu(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(sender);
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender);
            case "unsocket" -> handleUnsocket(sender);
            case "socket" -> handleSocket(sender, args);
            case "progress", "xp" -> handleProgress(sender);
            case "behaviors", "behavior" -> handleBehaviors(sender);
            case "combat" -> handleCombat(sender);
            case "debug" -> handleDebug(sender, args);
            case "ai" -> handleAI(sender, args);
            default -> sender.sendMessage(TextUtil.colorize("§cUnknown command. Use §e/as help"));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtil.colorize("§8&m-----------&r §3&lArcane Sigils §8&m-----------"));
        sender.sendMessage(TextUtil.colorize("§a/as §8- §7Open Sigil Menu"));
        sender.sendMessage(TextUtil.colorize("§a/as help §8- §7Show this help message"));
        sender.sendMessage(TextUtil.colorize("§a/as reload §8- §7Reload configs"));
        sender.sendMessage(TextUtil.colorize("§a/as give sigil <id> [tier] §8- §7Give sigil shard"));
        sender.sendMessage(TextUtil.colorize("§a/as list sigils §8- §7Open sigil list GUI"));
        sender.sendMessage(TextUtil.colorize("§a/as socket <id> [tier] §8- §7Socket sigil to held item"));
        sender.sendMessage(TextUtil.colorize("§a/as unsocket §8- §7Open unsocket GUI"));
        sender.sendMessage(TextUtil.colorize("§a/as ai [on|off] §8- §7Toggle AI training mode"));
        sender.sendMessage(TextUtil.colorize("§a/as progress §8- §7View sigil XP progress"));
        sender.sendMessage(TextUtil.colorize("§a/as behaviors §8- §7Open behaviors menu"));
        sender.sendMessage(TextUtil.colorize("§a/as combat §8- §7Open legacy combat settings"));
        sender.sendMessage(TextUtil.colorize("§8&m---------------------------------------"));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("arcanesigils.reload")) {
            sender.sendMessage(TextUtil.colorize("§cNo permission!"));
            return;
        }
        plugin.reload();

        // Refresh all socketed items for online players based on new YAML config
        int updatedItems = plugin.getSocketManager().refreshAllPlayerItems();

        sender.sendMessage(TextUtil.colorize("§aArcane Sigils reloaded!"));
        sender.sendMessage(TextUtil.colorize("§7Updated §f" + updatedItems + "§7 socketed items."));
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("arcanesigils.give")) {
            sender.sendMessage(TextUtil.colorize("§cNo permission!"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("§cUsage: /as give sigil <id> [tier]"));
            sender.sendMessage(TextUtil.colorize("§cUsage: /as give sigil <player> <id> [tier]"));
            return;
        }

        if (args[1].equalsIgnoreCase("sigil")) {
            handleGiveSigil(sender, args);
        } else {
            sender.sendMessage(TextUtil.colorize("§cUsage: /as give sigil <id> [tier]"));
        }
    }

    private void handleGiveSigil(CommandSender sender, String[] args) {
        // /as give sigil <id> [tier] - give to self
        // /as give sigil <player> <id> [tier] - give to player

        if (args.length < 3) {
            sender.sendMessage(TextUtil.colorize("§cUsage: /as give sigil <id> [tier]"));
            return;
        }

        Player target;
        String sigilId;
        int tier = 1;
        int tierArgIndex;

        // Check if args[2] is a player name or sigil ID
        Player possibleTarget = Bukkit.getPlayer(args[2]);
        if (possibleTarget != null && args.length >= 4) {
            // /as give sigil <player> <id> [tier]
            target = possibleTarget;
            sigilId = args[3];
            tierArgIndex = 4;
        } else {
            // /as give sigil <id> [tier] - give to self
            if (!(sender instanceof Player p)) {
                sender.sendMessage(TextUtil.colorize("§cSpecify a player: /as give sigil <player> <id> [tier]"));
                return;
            }
            target = p;
            sigilId = args[2];
            tierArgIndex = 3;
        }

        // Parse tier if specified
        if (args.length > tierArgIndex) {
            try {
                tier = Integer.parseInt(args[tierArgIndex]);
            } catch (NumberFormatException e) {
                sender.sendMessage(TextUtil.colorize("§cTier must be a number"));
                return;
            }
        }

        // Get sigil with specified tier
        Sigil sigil = plugin.getSigilManager().getSigilWithTier(sigilId, tier);
        if (sigil == null) {
            sender.sendMessage(TextUtil.colorize("§cSigil not found: §f" + sigilId));
            return;
        }

        // Clamp tier to max
        if (tier > sigil.getMaxTier()) {
            sender.sendMessage(TextUtil.colorize("§cMax tier for §f" + sigilId + "§c is §f" + sigil.getMaxTier()));
            return;
        }

        ItemStack item = plugin.getSigilManager().createSigilItem(sigil);
        target.getInventory().addItem(item);

        sender.sendMessage(Component.text("Gave ").color(NamedTextColor.GREEN)
                .append(TextUtil.parseComponent(sigil.getName()))
                .append(Component.text(" to ").color(NamedTextColor.GREEN))
                .append(Component.text(target.getName()).color(NamedTextColor.GREEN)));

        if (!sender.equals(target)) {
            target.sendMessage(Component.text("You received: ").color(NamedTextColor.GREEN)
                    .append(TextUtil.parseComponent(sigil.getName())));
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("§cUsage: /as list sigils"));
            return;
        }

        if (args[1].equalsIgnoreCase("sigils")) {
            // Open GUI per PDF spec (pagination gui with all sigils in debug form)
            if (sender instanceof Player p) {
                plugin.getGuiManager().openSigilsMenu(p);
            } else {
                // Console fallback - list in chat
                sender.sendMessage(TextUtil.colorize("§3&lSigils:"));
                for (Sigil s : plugin.getSigilManager().getAllSigils()) {
                    String exclusive = s.isExclusive() ? " §6[Exclusive]" : "";
                    sender.sendMessage(TextUtil.colorize("§7- §f" + s.getId() +
                            " §8(Max T" + s.getMaxTier() + ", " + s.getRarity() + ")" + exclusive));
                }
            }
        } else {
            sender.sendMessage(TextUtil.colorize("§cUsage: /as list sigils"));
        }
    }

    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize("§cPlayer only!"));
            return;
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            sender.sendMessage(TextUtil.colorize("§cHold an item!"));
            return;
        }

        Sigil sigil = plugin.getSigilManager().getSigilFromItem(item);
        if (sigil != null) {
            sender.sendMessage(TextUtil.colorize("§3&lSigil Shard:"));
            sender.sendMessage(TextUtil.colorize("§7ID: §f" + sigil.getId()));
            sender.sendMessage(TextUtil.colorize("§7Name: §f" + sigil.getName()));
            sender.sendMessage(TextUtil.colorize("§7Tier: §f" + sigil.getTier() + "/" + sigil.getMaxTier()));
            sender.sendMessage(TextUtil.colorize("§7Rarity: §f" + sigil.getRarity()));
            sender.sendMessage(TextUtil.colorize("§7Exclusive: §f" + (sigil.isExclusive() ? "Yes" : "No")));
            return;
        }

        List<Sigil> socketed = plugin.getSocketManager().getSocketedSigils(item);
        if (!socketed.isEmpty()) {
            sender.sendMessage(TextUtil.colorize("§3&lSocketed Sigils:"));
            for (Sigil s : socketed) {
                String exclusive = s.isExclusive() ? " §6[Exclusive]" : "";
                sender.sendMessage(TextUtil.colorize("§7- §f" + s.getName() + " §7T" + s.getTier() + exclusive));
            }
        } else if (plugin.getSocketManager().isSocketable(item.getType())) {
            sender.sendMessage(TextUtil.colorize("§7This item has no socketed sigils."));
        } else {
            sender.sendMessage(TextUtil.colorize("§7No Arcane Sigils data on this item."));
        }
    }

    private void handleSocket(CommandSender sender, String[] args) {
        if (!sender.hasPermission("arcanesigils.socket.command")) {
            sender.sendMessage(TextUtil.colorize("§cNo permission!"));
            return;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize("§cPlayer only!"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("§cUsage: /as socket <id> [tier]"));
            return;
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (!plugin.getSocketManager().isSocketable(item.getType())) {
            sender.sendMessage(TextUtil.colorize("§cHold an item (armor, tool, or weapon) to socket!"));
            return;
        }

        String sigilId = args[1];
        int tier = 1;

        // Check if tier is specified
        if (args.length >= 3) {
            try {
                tier = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(TextUtil.colorize("§cTier must be a number"));
                return;
            }
        }

        // Get sigil with specified tier
        Sigil sigil = plugin.getSigilManager().getSigilWithTier(sigilId, tier);
        if (sigil == null) {
            sender.sendMessage(TextUtil.colorize("§cSigil not found: §f" + sigilId));
            return;
        }

        // Clamp tier to max
        if (tier > sigil.getMaxTier()) {
            sender.sendMessage(TextUtil.colorize("§cMax tier for §f" + sigilId + "§c is §f" + sigil.getMaxTier()));
            return;
        }

        // Socket the sigil directly
        var result = plugin.getSocketManager().socketSigil(p, item, sigil);
        switch (result) {
            case SUCCESS -> {
                String prefix = sigil.isExclusive() ? "§6* " : "§a> ";
                sender.sendMessage(TextUtil.colorize(prefix + "§aSocketed §f" + sigil.getName() + " §aonto item!"));
                if (sigil.isExclusive()) {
                    sender.sendMessage(TextUtil.colorize("§7&o(Exclusive sigil - cannot be unsocketed)"));
                }
            }
            case ALREADY_HAS_SIGIL -> sender.sendMessage(TextUtil.colorize("§cThis item already has this sigil!"));
            case WRONG_SLOT -> sender.sendMessage(TextUtil.colorize("§cThis sigil cannot be socketed into this item type!"));
            case NO_PERMISSION -> sender.sendMessage(TextUtil.colorize("§cYou don't have permission to socket sigils!"));
            case INVALID_ITEM -> sender.sendMessage(TextUtil.colorize("§cInvalid item!"));
            case TIER_TOO_LOW -> sender.sendMessage(TextUtil.colorize("§cItem tier too low for this sigil!"));
        }
    }

    private void handleUnsocket(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize("§cPlayer only!"));
            return;
        }

        // Open unsocket GUI per PDF spec
        plugin.getGuiManager().openUnsocketGUI(p);
    }

    private void handleProgress(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize("§cPlayer only!"));
            return;
        }

        sender.sendMessage(TextUtil.colorize("§8&m-----------&r §3&lSigil Progress §8&m-----------"));

        boolean foundAny = false;
        String[] slotNames = {"Boots", "Leggings", "Chestplate", "Helmet"};
        ItemStack[] armor = p.getInventory().getArmorContents();

        for (int i = 0; i < armor.length; i++) {
            ItemStack item = armor[i];
            if (item == null || item.getType().isAir()) continue;

            List<Sigil> sigils = plugin.getSocketManager().getSocketedSigils(item);
            if (sigils.isEmpty()) continue;

            foundAny = true;
            sender.sendMessage(TextUtil.colorize("§7" + slotNames[i] + ":"));

            for (Sigil sigil : sigils) {
                displaySigilProgress(sender, item, sigil);
            }
        }

        // Also check held items
        ItemStack mainHand = p.getInventory().getItemInMainHand();
        if (!mainHand.getType().isAir() && plugin.getSocketManager().isSocketable(mainHand.getType())) {
            List<Sigil> heldSigils = plugin.getSocketManager().getSocketedSigils(mainHand);
            if (!heldSigils.isEmpty()) {
                foundAny = true;
                sender.sendMessage(TextUtil.colorize("§7Main Hand:"));
                for (Sigil sigil : heldSigils) {
                    displaySigilProgress(sender, mainHand, sigil);
                }
            }
        }

        if (!foundAny) {
            sender.sendMessage(TextUtil.colorize("§7No sigils equipped."));
        }

        sender.sendMessage(TextUtil.colorize("§8&m---------------------------------------"));
    }

    private void handleBehaviors(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize("§cPlayer only!"));
            return;
        }

        if (!p.hasPermission("arcanesigils.behaviors")) {
            sender.sendMessage(TextUtil.colorize("§cNo permission!"));
            return;
        }

        // Open behaviors browser GUI
        com.miracle.arcanesigils.gui.behavior.BehaviorBrowserHandler.openGUI(plugin.getGuiManager(), p);
    }

    private void handleCombat(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize("§cPlayer only!"));
            return;
        }

        if (!p.hasPermission("arcanesigils.combat")) {
            sender.sendMessage(TextUtil.colorize("§cNo permission!"));
            return;
        }

        // Open combat settings GUI
        com.miracle.arcanesigils.combat.gui.CombatSettingsHandler.openGUI(plugin.getGuiManager(), p);
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("arcanesigils.admin")) {
            sender.sendMessage(TextUtil.colorize("§cNo permission!"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("§e=== Plugin Debugger ==="));
            sender.sendMessage(TextUtil.colorize("§a/as debug damage §8- §7Toggle damage event debugging"));
            sender.sendMessage(TextUtil.colorize("§a/as debug saturation §8- §7Toggle saturation debugging"));
            sender.sendMessage(TextUtil.colorize("§a/as debug list §8- §7List all event listeners"));
            sender.sendMessage(TextUtil.colorize("§a/as debug status §8- §7Show current debug status"));
            return;
        }

        var debugger = plugin.getPluginDebugger();
        if (debugger == null) {
            sender.sendMessage(TextUtil.colorize("§cDebugger not initialized!"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "damage" -> {
                boolean newState = !debugger.isDebugDamage();
                debugger.setDebugDamage(newState);
                sender.sendMessage(TextUtil.colorize("§eDamage debugging: " + (newState ? "§aENABLED" : "§cDISABLED")));
                if (newState) {
                    sender.sendMessage(TextUtil.colorize("§7Check console for listener list and cancelled events."));
                }
            }
            case "saturation" -> {
                boolean newState = !debugger.isDebugSaturation();
                debugger.setDebugSaturation(newState);
                sender.sendMessage(TextUtil.colorize("§eSaturation debugging: " + (newState ? "§aENABLED" : "§cDISABLED")));
                if (newState) {
                    sender.sendMessage(TextUtil.colorize("§7Check console for saturation changes."));
                }
            }
            case "list" -> {
                sender.sendMessage(TextUtil.colorize("§eListing listeners to console..."));
                debugger.listDamageListeners();
                debugger.listSaturationListeners();
                sender.sendMessage(TextUtil.colorize("§aDone! Check console for full listener list."));
            }
            case "status" -> {
                sender.sendMessage(TextUtil.colorize("§e=== Debug Status ==="));
                sender.sendMessage(TextUtil.colorize("§7Damage debugging: " + (debugger.isDebugDamage() ? "§aON" : "§cOFF")));
                sender.sendMessage(TextUtil.colorize("§7Saturation debugging: " + (debugger.isDebugSaturation() ? "§aON" : "§cOFF")));
            }
            default -> sender.sendMessage(TextUtil.colorize("§cUnknown debug option. Use §e/as debug"));
        }
    }

    private void displaySigilProgress(CommandSender sender, ItemStack item, Sigil sigil) {
        TierProgressionManager.XPProgressInfo info =
                plugin.getTierProgressionManager().getProgressInfo(item, sigil.getId(), sigil.getTier());

        String tierRoman = toRomanNumeral(sigil.getTier());
        Sigil baseSigil = plugin.getSigilManager().getSigil(sigil.getId());
        int maxTier = baseSigil != null ? baseSigil.getMaxTier() : sigil.getMaxTier();

        if (info == null || !info.xpEnabled) {
            // XP disabled
            sender.sendMessage(TextUtil.colorize("  §f" + sigil.getName() + " §7" + tierRoman + " §8(No XP)"));
        } else if (info.maxTierReached) {
            // Max tier
            sender.sendMessage(TextUtil.colorize("  §f" + sigil.getName() + " §a" + tierRoman + " §8(MAX)"));
        } else {
            // Show progress
            String bar = info.getProgressBar(10);
            sender.sendMessage(TextUtil.colorize("  §f" + sigil.getName() + " §7" + tierRoman +
                    " " + bar + " §7" + info.currentXP + "/" + info.requiredXP));
        }
    }

    private String toRomanNumeral(int tier) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
        if (tier >= 1 && tier <= 20) {
            return numerals[tier - 1];
        }
        return String.valueOf(tier);
    }

    private void handleAI(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize("§cPlayer only!"));
            return;
        }

        var aiManager = plugin.getAITrainingManager();
        if (aiManager == null) {
            sender.sendMessage(TextUtil.colorize("§cAI training system not available!"));
            return;
        }

        if (args.length == 1) {
            // Toggle
            boolean current = aiManager.isEnabledForPlayer(player);
            aiManager.setEnabledForPlayer(player, !current);
            String status = !current ? "§aENABLED" : "§cDISABLED";
            sender.sendMessage(TextUtil.colorize("§eAI Training Mode: " + status));
            if (!current) {
                sender.sendMessage(TextUtil.colorize("§7Chat reward signals will now appear"));
            } else {
                sender.sendMessage(TextUtil.colorize("§7Chat messages disabled"));
            }
            return;
        }

        String mode = args[1].toLowerCase();
        switch (mode) {
            case "on" -> {
                aiManager.setEnabledForPlayer(player, true);
                sender.sendMessage(TextUtil.colorize("§aAI Training Mode ENABLED"));
                sender.sendMessage(TextUtil.colorize("§7Chat reward signals will now appear"));
            }
            case "off" -> {
                aiManager.setEnabledForPlayer(player, false);
                sender.sendMessage(TextUtil.colorize("§cAI Training Mode DISABLED"));
                sender.sendMessage(TextUtil.colorize("§7Chat messages disabled"));
            }
            default -> sender.sendMessage(TextUtil.colorize("§cUsage: /as ai [on|off]"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // /as <subcommand>
            completions.addAll(Arrays.asList("help", "reload", "give", "list", "info", "socket", "unsocket", "progress", "behaviors", "combat", "debug", "ai"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "give" -> completions.add("sigil");
                case "list" -> completions.add("sigils");
                case "socket" -> {
                    // Show all sigil IDs
                    plugin.getSigilManager().getAllSigils().forEach(s -> completions.add(s.getId()));
                }
                case "debug" -> completions.addAll(Arrays.asList("damage", "saturation", "list", "status"));
                case "ai" -> completions.addAll(Arrays.asList("on", "off"));
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "socket" -> {
                    // Tier numbers for socket
                    Sigil sigil = plugin.getSigilManager().getSigil(args[1]);
                    int maxTier = sigil != null ? sigil.getMaxTier() : 10;
                    for (int i = 1; i <= maxTier; i++) {
                        completions.add(String.valueOf(i));
                    }
                }
                case "give" -> {
                    if (args[1].equalsIgnoreCase("sigil")) {
                        // /as give sigil <id or player>
                        // Show both sigil IDs and online players
                        plugin.getSigilManager().getAllSigils().forEach(s -> completions.add(s.getId()));
                        Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                    }
                }
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("sigil")) {
                // Could be /as give sigil <player> <id> - show sigil IDs
                // Or /as give sigil <id> <tier> - show tier numbers
                Player possibleTarget = Bukkit.getPlayer(args[2]);
                if (possibleTarget != null) {
                    // It's a player, show sigil IDs
                    plugin.getSigilManager().getAllSigils().forEach(s -> completions.add(s.getId()));
                } else {
                    // Probably a sigil ID, show tier numbers
                    Sigil sigil = plugin.getSigilManager().getSigil(args[2]);
                    int maxTier = sigil != null ? sigil.getMaxTier() : 10;
                    for (int i = 1; i <= maxTier; i++) {
                        completions.add(String.valueOf(i));
                    }
                }
            }
        } else if (args.length == 5) {
            if (args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("sigil")) {
                // /as give sigil <player> <id> <tier>
                Sigil sigil = plugin.getSigilManager().getSigil(args[3]);
                int maxTier = sigil != null ? sigil.getMaxTier() : 10;
                for (int i = 1; i <= maxTier; i++) {
                    completions.add(String.valueOf(i));
                }
            }
        }

        // Filter by what user has typed
        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .sorted()
                .toList();
    }
}
