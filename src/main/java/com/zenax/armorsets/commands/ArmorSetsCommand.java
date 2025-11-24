package com.zenax.armorsets.commands;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.sets.ArmorSet;
import com.zenax.armorsets.utils.TextUtil;
import com.zenax.armorsets.weapons.CustomWeapon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ArmorSetsCommand implements CommandExecutor, TabCompleter {

    private final ArmorSetsPlugin plugin;

    public ArmorSetsCommand(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    private void handleBuild(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize("&cPlayer only!"));
            return;
        }
        plugin.getGuiManager().openBuildMainMenu(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "help" -> sendHelp(sender);
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender);
            case "unsocket" -> handleUnsocket(sender);
            case "socket" -> handleSocket(sender, args);
            case "build" -> handleBuild(sender, args);
            default -> sender.sendMessage(TextUtil.colorize("&cUnknown command. Use /armorsets help"));
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtil.colorize("&8&m-----------&r &d&lArmorSets Help &8&m-----------"));
        sender.sendMessage(TextUtil.colorize("&e/as reload &8- &7Reload configs"));
        sender.sendMessage(TextUtil.colorize("&e/as give sigil <player> <id> [tier] &8- &7Give sigil shard"));
        sender.sendMessage(TextUtil.colorize("&e/as give set <player> <set_id> [tier] &8- &7Give full armor set"));
        sender.sendMessage(TextUtil.colorize("&e/as give weapon <player> <id> &8- &7Give weapon"));
        sender.sendMessage(TextUtil.colorize("&e/as list sigils &8- &7List all sigils"));
        sender.sendMessage(TextUtil.colorize("&e/as list sets &8- &7List all sets"));
        sender.sendMessage(TextUtil.colorize("&e/as info &8- &7Info about held item"));
        sender.sendMessage(TextUtil.colorize("&e/as socket <id> [tier] &8- &7Socket sigil onto held armor"));
        sender.sendMessage(TextUtil.colorize("&e/as unsocket &8- &7Remove sigil from held armor"));
        sender.sendMessage(TextUtil.colorize("&8&m-----------------------------------------"));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("armorsets.reload")) {
            sender.sendMessage(TextUtil.colorize("&cNo permission!")); return;
        }
        plugin.reload();
        sender.sendMessage(TextUtil.colorize("&aArmorSets reloaded!"));
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("armorsets.give")) {
            sender.sendMessage(TextUtil.colorize("&cNo permission!")); return;
        }
        if (args.length < 4) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /as give <sigil|set|weapon> <player> <id> [tier]")); return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(TextUtil.colorize("&cPlayer not found: " + args[2])); return;
        }

        if (args[1].equalsIgnoreCase("sigil") || args[1].equalsIgnoreCase("function")) {
            String sigilId = args[3];
            int tier = 1;

            // Check if tier is specified
            if (args.length >= 5) {
                try {
                    tier = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(TextUtil.colorize("&cTier must be a number"));
                    return;
                }
            }

            // Get sigil with specified tier
            Sigil sigil = plugin.getSigilManager().getSigilWithTier(sigilId, tier);
            if (sigil == null) {
                sender.sendMessage(TextUtil.colorize("&cSigil not found: " + sigilId)); return;
            }

            // Clamp tier to max
            if (tier > sigil.getMaxTier()) {
                sender.sendMessage(TextUtil.colorize("&cMax tier for " + sigilId + " is " + sigil.getMaxTier())); return;
            }

            ItemStack item = plugin.getSigilManager().createSigilItem(sigil);
            target.getInventory().addItem(item);
            sender.sendMessage(Component.text("Gave ").color(NamedTextColor.GREEN).append(TextUtil.parseComponent(sigil.getName())).append(Component.text(" to ").color(NamedTextColor.GREEN)).append(Component.text(target.getName()).color(NamedTextColor.GREEN)));
            target.sendMessage(Component.text("You received: ").color(NamedTextColor.GREEN).append(TextUtil.parseComponent(sigil.getName())));
        }
        else if (args[1].equalsIgnoreCase("set")) {
            String setId = args[3];
            int tier = 1;

            // Check if tier is specified
            if (args.length >= 5) {
                try {
                    tier = Integer.parseInt(args[4]);
                    if (tier < 1 || tier > 10) {
                        sender.sendMessage(TextUtil.colorize("&cTier must be between 1 and 10")); return;
                    }
                    setId = args[3] + "_t" + tier;
                } catch (NumberFormatException e) {
                    sender.sendMessage(TextUtil.colorize("&cTier must be a number"));
                    return;
                }
            }

            ArmorSet set = plugin.getSetManager().getSet(setId);
            if (set == null) {
                set = plugin.getSetManager().getSet(args[3] + "_t1");
            }
            if (set == null) {
                sender.sendMessage(TextUtil.colorize("&cSet not found: " + args[3]));
                sender.sendMessage(TextUtil.colorize("&7Available sets: " + String.join(", ",
                    plugin.getSetManager().getAllSets().stream().map(ArmorSet::getId).toList())));
                return;
            }

            // Create full armor set using the set's material base
            Material baseMat = set.getMaterial();
            String materialBase = getMaterialBase(baseMat);
            String setName = set.getId().replace("_t", " Tier ");

            String[] slots = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
            for (String slot : slots) {
                Material mat = Material.getMaterial(materialBase + "_" + slot);
                if (mat == null) mat = Material.getMaterial("NETHERITE_" + slot);

                ItemStack armor = new ItemStack(mat);
                ItemMeta meta = armor.getItemMeta();
                meta.displayName(TextUtil.parseComponent("&d" + setName + " " + capitalize(slot)));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parseComponent("&7Part of the &d" + setName + " &7set"));
                lore.add(TextUtil.parseComponent("&8Tier &f" + set.getTier()));

                // Add synergy info
                if (!set.getSynergies().isEmpty()) {
                    lore.add(Component.empty());
                    lore.add(TextUtil.parseComponent("&b&lSet Synergies &8(Full Set Bonus):"));
                    for (var synergy : set.getSynergies()) {
                        String trigger = synergy.getTrigger().getConfigKey().replace("_", " ").toLowerCase();
                        String triggerDesc = TextUtil.getTriggerDescription(synergy.getTrigger().getConfigKey());
                        lore.add(TextUtil.parseComponent("&b• &3" + synergy.getId()));
                        lore.add(TextUtil.parseComponent("&7  " + TextUtil.toProperCase(triggerDesc)));
                        for (String effect : synergy.getTriggerConfig().getEffects()) {
                            String effectDesc = TextUtil.getEffectDescription(effect);
                            lore.add(TextUtil.parseComponent("&8    →&7 " + TextUtil.toProperCase(effectDesc)));
                        }
                    }
                }

                lore.add(Component.empty());
                lore.add(TextUtil.parseComponent("&7Right-click with sigil shard to socket"));
                meta.lore(lore);
                meta.addEnchant(Enchantment.UNBREAKING, 3, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                // Set PDC data before setting meta to preserve lore
                NamespacedKey setIdKey = plugin.getSetManager().getSetIdKey();
                NamespacedKey armorSlotKey = plugin.getSetManager().getArmorSlotKey();
                meta.getPersistentDataContainer().set(setIdKey, PersistentDataType.STRING, set.getId());
                meta.getPersistentDataContainer().set(armorSlotKey, PersistentDataType.STRING, slot);
                armor.setItemMeta(meta);
                target.getInventory().addItem(armor);
            }

            sender.sendMessage(TextUtil.colorize("&aGave full " + setName + " &aset to " + target.getName()));
            target.sendMessage(Component.text("You received the full ").color(NamedTextColor.GREEN).append(Component.text(setName)).append(Component.text(" armor set!").color(NamedTextColor.GREEN)));
        }
        else if (args[1].equalsIgnoreCase("weapon")) {
            CustomWeapon weapon = plugin.getWeaponManager().getWeapon(args[3]);
            if (weapon == null) {
                sender.sendMessage(TextUtil.colorize("&cWeapon not found: " + args[3]));
                return;
            }
            ItemStack item = plugin.getWeaponManager().createWeaponItem(weapon);
            target.getInventory().addItem(item);
            sender.sendMessage(Component.text("Gave ").color(NamedTextColor.GREEN).append(TextUtil.parseComponent(weapon.getName())).append(Component.text(" to ").color(NamedTextColor.GREEN)).append(Component.text(target.getName()).color(NamedTextColor.GREEN)));
            target.sendMessage(Component.text("You received: ").color(NamedTextColor.GREEN).append(TextUtil.parseComponent(weapon.getName())));
        }
        else {
            sender.sendMessage(TextUtil.colorize("&cUsage: /as give <sigil|set|weapon> <player> <id> [tier]"));
        }
    }

    private String getMaterialBase(Material mat) {
        String name = mat.name();
        if (name.contains("NETHERITE")) return "NETHERITE";
        if (name.contains("DIAMOND")) return "DIAMOND";
        if (name.contains("IRON")) return "IRON";
        if (name.contains("GOLD")) return "GOLDEN";
        if (name.contains("CHAINMAIL")) return "CHAINMAIL";
        if (name.contains("LEATHER")) return "LEATHER";
        return "NETHERITE";
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /as list <sigils|sets>")); return;
        }

        if (args[1].equalsIgnoreCase("sigils") || args[1].equalsIgnoreCase("functions")) {
            sender.sendMessage(TextUtil.colorize("&d&lSigils:"));
            for (Sigil s : plugin.getSigilManager().getAllSigils()) {
                sender.sendMessage(TextUtil.colorize("&7- &f" + s.getId() + " &8(" + s.getSlot() + " T" + s.getTier() + ")"));
            }
        } else if (args[1].equalsIgnoreCase("sets")) {
            sender.sendMessage(TextUtil.colorize("&d&lArmor Sets:"));
            for (ArmorSet s : plugin.getSetManager().getAllSets()) {
                sender.sendMessage(TextUtil.colorize("&7- &f" + s.getId() + " &8(T" + s.getTier() + ")"));
            }
        } else if (args[1].equalsIgnoreCase("weapons")) {
            sender.sendMessage(TextUtil.colorize("&d&lWeapons:"));
            for (CustomWeapon w : plugin.getWeaponManager().getAllWeapons()) {
                sender.sendMessage(TextUtil.colorize("&7- &f" + w.getId()));
            }
        }
    }

    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize("&cPlayer only!")); return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            sender.sendMessage(TextUtil.colorize("&cHold an item!")); return;
        }

        Sigil sigil = plugin.getSigilManager().getSigilFromItem(item);
        if (sigil != null) {
            sender.sendMessage(TextUtil.colorize("&d&lSigil Shard:"));
            sender.sendMessage(TextUtil.colorize("&7ID: &f" + sigil.getId()));
            sender.sendMessage(TextUtil.colorize("&7Slot: &f" + sigil.getSlot()));
            sender.sendMessage(TextUtil.colorize("&7Tier: &f" + sigil.getTier()));
            return;
        }

        Sigil socketed = plugin.getSocketManager().getSocketedSigil(item);
        if (socketed != null) {
            sender.sendMessage(TextUtil.colorize("&d&lArmor with Socket:"));
            sender.sendMessage(TextUtil.colorize("&7Socketed: &f" + socketed.getName()));
            sender.sendMessage(TextUtil.colorize("&7Sigil ID: &f" + socketed.getId()));
        } else if (plugin.getSocketManager().isArmor(item.getType())) {
            sender.sendMessage(TextUtil.colorize("&7This armor has no socketed sigil."));
        } else {
            sender.sendMessage(TextUtil.colorize("&7No ArmorSets data on this item."));
        }
    }

    private void handleSocket(CommandSender sender, String[] args) {
        if (!sender.hasPermission("armorsets.socket.command")) {
            sender.sendMessage(TextUtil.colorize("&cNo permission!")); return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize("&cPlayer only!")); return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextUtil.colorize("&cUsage: /as socket <sigil_id> [tier]")); return;
        }

        ItemStack item = p.getInventory().getItemInMainHand();
        if (!plugin.getSocketManager().isArmor(item.getType())) {
            sender.sendMessage(TextUtil.colorize("&cHold armor to socket!")); return;
        }

        String sigilId = args[1];
        int tier = 1;

        // Check if tier is specified
        if (args.length >= 3) {
            try {
                tier = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(TextUtil.colorize("&cTier must be a number")); return;
            }
        }

        // Get sigil with specified tier
        Sigil sigil = plugin.getSigilManager().getSigilWithTier(sigilId, tier);
        if (sigil == null) {
            sender.sendMessage(TextUtil.colorize("&cSigil not found: " + sigilId)); return;
        }

        // Clamp tier to max
        if (tier > sigil.getMaxTier()) {
            sender.sendMessage(TextUtil.colorize("&cMax tier for " + sigilId + " is " + sigil.getMaxTier())); return;
        }

        // Socket the sigil directly
        var result = plugin.getSocketManager().socketSigil(p, item, sigil);
        switch (result) {
            case SUCCESS -> {
                String prefix = sigil.isExclusive() ? "&6☆ " : "&a➤ ";
                sender.sendMessage(TextUtil.colorize(prefix + "&aSocketed &f" + sigil.getName() + " &aonto armor!"));
                if (sigil.isExclusive()) {
                    sender.sendMessage(TextUtil.colorize("&7(Exclusive sigil - cannot be unsocketed)"));
                }
            }
            case ALREADY_HAS_SIGIL -> sender.sendMessage(TextUtil.colorize("&cThis armor already has this sigil!"));
            case WRONG_SLOT -> sender.sendMessage(TextUtil.colorize("&cThis sigil can only go in " + sigil.getSlot().toLowerCase() + " armor!"));
            case NO_PERMISSION -> sender.sendMessage(TextUtil.colorize("&cYou don't have permission to socket sigils!"));
            case INVALID_ARMOR -> sender.sendMessage(TextUtil.colorize("&cInvalid armor piece!"));
            case TIER_TOO_LOW -> sender.sendMessage(TextUtil.colorize("&cArmor tier too low for this sigil!"));
        }
    }

    private void handleUnsocket(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(TextUtil.colorize("&cPlayer only!")); return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!plugin.getSocketManager().isArmor(item.getType())) {
            sender.sendMessage(TextUtil.colorize("&cHold armor to unsocket!")); return;
        }

        if (!plugin.getSocketManager().hasSocketedSigil(item)) {
            sender.sendMessage(TextUtil.colorize("&cNo sigils socketed on this armor!")); return;
        }

        // Get the slot number for the held item
        int slotNum = p.getInventory().getHeldItemSlot();
        plugin.getGuiManager().openUnsocketGUI(p, item, slotNum);
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> c = new ArrayList<>();
        if (args.length == 1) {
            c.addAll(Arrays.asList("help", "reload", "give", "list", "info", "socket", "unsocket", "build"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give")) c.addAll(Arrays.asList("sigil", "set", "weapon"));
            if (args[0].equalsIgnoreCase("list")) c.addAll(Arrays.asList("sigils", "sets", "weapons"));
            if (args[0].equalsIgnoreCase("socket")) {
                // Show all sigil names
                plugin.getSigilManager().getAllSigils().forEach(s -> c.add(s.getId()));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("socket")) {
            // Tab complete tier numbers for socket command
            Sigil sigil = plugin.getSigilManager().getSigil(args[1]);
            int maxTier = sigil != null ? sigil.getMaxTier() : 10;
            for (int i = 1; i <= maxTier; i++) {
                c.add(String.valueOf(i));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> c.add(p.getName()));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            if (args[1].equalsIgnoreCase("sigil") || args[1].equalsIgnoreCase("function")) {
                // Show all sigil names
                plugin.getSigilManager().getAllSigils().forEach(s -> c.add(s.getId()));
            } else if (args[1].equalsIgnoreCase("set")) {
                Set<String> baseNames = new HashSet<>();
                plugin.getSetManager().getAllSets().forEach(s -> {
                    String id = s.getId().toLowerCase();
                    String base = id.replaceAll("_t\\d+$", "");
                    baseNames.add(base);
                });
                c.addAll(baseNames);
            } else if (args[1].equalsIgnoreCase("weapon")) {
                plugin.getWeaponManager().getAllWeapons().forEach(w -> c.add(w.getId()));
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            // Tab complete tier numbers
            if (args[1].equalsIgnoreCase("sigil") || args[1].equalsIgnoreCase("function")) {
                Sigil sigil = plugin.getSigilManager().getSigil(args[3]);
                int maxTier = sigil != null ? sigil.getMaxTier() : 10;
                for (int i = 1; i <= maxTier; i++) {
                    c.add(String.valueOf(i));
                }
            } else if (args[1].equalsIgnoreCase("set")) {
                String baseName = args[3].toLowerCase();
                int maxTier = 1;
                for (ArmorSet s : plugin.getSetManager().getAllSets()) {
                    String id = s.getId().toLowerCase();
                    if (id.startsWith(baseName + "_t")) {
                        maxTier = Math.max(maxTier, s.getTier());
                    }
                }
                for (int i = 1; i <= maxTier; i++) {
                    c.add(String.valueOf(i));
                }
            }
        }
        String last = args[args.length - 1].toLowerCase();
        return c.stream().filter(s -> s.toLowerCase().startsWith(last)).toList();
    }
}
