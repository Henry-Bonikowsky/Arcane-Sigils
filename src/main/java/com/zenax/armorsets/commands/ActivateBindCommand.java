package com.zenax.armorsets.commands;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.binds.BindPreset;
import com.zenax.armorsets.binds.PlayerBindData;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.events.ConditionManager;
import com.zenax.armorsets.events.SignalType;
import com.zenax.armorsets.flow.FlowConfig;
import com.zenax.armorsets.flow.FlowExecutor;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Command handler for /activatebind.
 * Activates a specific bind by ID (used with the command bind system).
 */
public class ActivateBindCommand implements CommandExecutor, TabCompleter {

    private final ArmorSetsPlugin plugin;

    public ActivateBindCommand(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.colorize("§cPlayer only!"));
            return true;
        }

        if (!player.hasPermission("arcanesigils.activatebind")) {
            sender.sendMessage(TextUtil.colorize("§cNo permission!"));
            return true;
        }

        // Check arguments
        if (args.length < 1) {
            sender.sendMessage(TextUtil.colorize("§cUsage: /activatebind <id>"));
            return true;
        }

        // Parse bind ID
        int bindId;
        try {
            bindId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextUtil.colorize("§cInvalid bind ID: " + args[0]));
            return true;
        }

        // Get player's bind data
        PlayerBindData data = plugin.getBindsManager().getPlayerData(player);

        // Check if abilities are toggled on (optional - can auto-activate for commands)
        // For command binds, we'll allow activation without toggle requirement
        // Uncomment the following if you want to require toggle:
        /*
        if (!data.isToggled()) {
            sender.sendMessage(TextUtil.colorize("§cAbilities are not toggled on! Use your toggle hotkey first."));
            return true;
        }
        */

        // Get the bind from command binds
        BindPreset commandBinds = data.getCommandBinds();
        List<String> sigilIds = commandBinds.getBind(bindId);

        if (sigilIds.isEmpty()) {
            sender.sendMessage(TextUtil.colorize("§cNo abilities bound to slot " + bindId + "!"));
            return true;
        }

        // Activate the bind
        activateBind(player, sigilIds);

        return true;
    }

    /**
     * Activates a bind by executing all sigils in the bind with 1-second delays.
     */
    private void activateBind(Player player, List<String> sigilIds) {
        // Execute each sigil with 1-second delay
        for (int i = 0; i < sigilIds.size(); i++) {
            final String sigilId = sigilIds.get(i);
            final int delay = i * 20; // 20 ticks = 1 second

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                executeSigil(player, sigilId);
            }, delay);
        }

        // Send confirmation message
        if (sigilIds.size() == 1) {
            player.sendMessage(TextUtil.colorize("§aActivating ability..."));
        } else {
            player.sendMessage(TextUtil.colorize("§aActivating " + sigilIds.size() + " abilities..."));
        }
    }

    /**
     * Executes a single sigil by triggering its flow.
     * This method looks for the sigil and triggers its configured flow.
     */
    private void executeSigil(Player player, String sigilId) {
        // Get the sigil from the manager
        Sigil sigil = plugin.getSigilManager().getSigil(sigilId);

        if (sigil == null) {
            player.sendMessage(TextUtil.colorize("§cAbility not found: " + sigilId));
            return;
        }

        // Check if sigil has a flow configured
        if (!sigil.hasFlow()) {
            player.sendMessage(TextUtil.colorize("§cAbility " + sigil.getName() + " §chas no flow configured!"));
            return;
        }

        FlowConfig flow = sigil.getFlow();

        // Create effect context for manual activation
        EffectContext context = EffectContext.builder(player, SignalType.EFFECT_STATIC).build();

        // Add sigil metadata
        context.setMetadata("sourceSigilId", sigil.getId());
        context.setMetadata("sourceSigilTier", sigil.getTier());

        // Add tier scaling config for {param} placeholder replacement
        if (sigil.getTierScalingConfig() != null) {
            context.setMetadata("tierScalingConfig", sigil.getTierScalingConfig());
        }

        // Process cooldown
        String cooldownKey = "bind_action_" + sigilId;

        // Check cooldown
        if (flow.getCooldown() > 0 && plugin.getCooldownManager().isOnCooldown(player, cooldownKey)) {
            double remaining = plugin.getCooldownManager().getRemainingCooldown(player, cooldownKey);
            player.sendMessage(TextUtil.colorize("§cAbility on cooldown! (" + String.format("%.1f", remaining) + "s remaining)"));
            return;
        }

        // Check conditions
        if (!flow.getConditions().isEmpty()) {
            ConditionManager conditionManager = new ConditionManager();
            if (!conditionManager.checkConditions(flow.getConditions(), context)) {
                player.sendMessage(TextUtil.colorize("§cConditions not met for this ability!"));
                return;
            }
        }

        // Execute the flow
        FlowExecutor executor = new FlowExecutor(plugin);
        executor.execute(flow.getGraph(), context);

        // Apply cooldown with display name
        if (flow.getCooldown() > 0) {
            String displayName = TextUtil.stripColors(sigil.getName());
            plugin.getCooldownManager().setCooldown(player, cooldownKey, displayName, flow.getCooldown());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender instanceof Player player) {
            // Tab complete with bind IDs from the player's command binds
            PlayerBindData data = plugin.getBindsManager().getPlayerData(player);
            BindPreset commandBinds = data.getCommandBinds();

            // Add all configured bind IDs
            for (int bindId : commandBinds.getBinds().keySet()) {
                completions.add(String.valueOf(bindId));
            }

            // Also suggest IDs 1-27 as possibilities
            for (int i = 1; i <= 27; i++) {
                String id = String.valueOf(i);
                if (!completions.contains(id)) {
                    completions.add(id);
                }
            }
        }

        // Filter by what the user has typed
        String lastArg = args.length > 0 ? args[args.length - 1] : "";
        return completions.stream()
                .filter(s -> s.startsWith(lastArg))
                .sorted()
                .toList();
    }
}
