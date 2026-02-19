package com.miracle.arcanesigils.gui.combat;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.combat.ModifierRegistry;
import com.miracle.arcanesigils.combat.ModifierType;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * GUI for viewing all active modifiers on a specific player.
 * Shows damage modifiers, marks, and attribute modifiers.
 * Admin only, read-only display.
 */
public class ModifierViewerHandler extends AbstractHandler {

    public ModifierViewerHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    public static void openGUI(GUIManager guiManager, Player viewer, Player target) {
        GUISession session = new GUISession(GUIType.MODIFIER_VIEWER);
        session.put("targetUUID", target.getUniqueId());
        session.put("targetName", target.getName());

        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
        ModifierRegistry registry = plugin.getModifierRegistry();

        Inventory inv = Bukkit.createInventory(null, 54, "§8Modifiers: §f" + target.getName());

        // Player head - slot 4
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        skullMeta.setOwningPlayer(target);
        skullMeta.setDisplayName("§b§l" + target.getName());
        skullMeta.setLore(List.of(
                "§7Health: §f" + String.format("%.1f", target.getHealth()) + "/"
                        + String.format("%.1f", target.getMaxHealth()),
                "§7UUID: §8" + target.getUniqueId().toString().substring(0, 8) + "..."
        ));
        head.setItemMeta(skullMeta);
        inv.setItem(4, head);

        int slot = 9; // Start at second row

        // === Typed Modifiers (Damage Amp, Reduction, Charge DR) ===
        Map<ModifierType, Map<String, double[]>> typedMods = registry.getActiveModifiers(target.getUniqueId());

        if (typedMods.isEmpty()) {
            inv.setItem(slot++, createInfoItem(Material.GRAY_DYE, "§7No Damage Modifiers",
                    "§8No active damage amplification,", "§8reduction, or charge DR."));
        } else {
            for (var entry : typedMods.entrySet()) {
                ModifierType type = entry.getKey();
                Material mat = switch (type) {
                    case DAMAGE_AMPLIFICATION -> Material.RED_DYE;
                    case DAMAGE_REDUCTION -> Material.BLUE_DYE;
                    case CHARGE_DR -> Material.PURPLE_DYE;
                };
                String typeName = switch (type) {
                    case DAMAGE_AMPLIFICATION -> "§c§lDamage Amplification";
                    case DAMAGE_REDUCTION -> "§9§lDamage Reduction";
                    case CHARGE_DR -> "§5§lCharge DR";
                };

                List<String> lore = new ArrayList<>();
                lore.add("§7Multiplier: §e" + String.format("%.2f", registry.getMultiplier(target.getUniqueId(), type)));
                lore.add("");
                lore.add("§7Sources:");
                for (var src : entry.getValue().entrySet()) {
                    double value = src.getValue()[0];
                    double remaining = src.getValue()[1];
                    String time = remaining < 0 ? "§dpermanent" : "§e" + String.format("%.1fs", remaining);
                    lore.add("§8- §f" + src.getKey() + " §7(" + String.format("%.1f%%", value * 100) + ") " + time);
                }

                inv.setItem(slot++, createLoreItem(mat, typeName, lore));
                if (slot >= 18) break;
            }
        }

        // === Marks ===
        slot = 18; // Third row
        Set<String> marks = registry.getMarks(target);
        if (marks.isEmpty()) {
            inv.setItem(slot++, createInfoItem(Material.GRAY_DYE, "§7No Active Marks",
                    "§8No marks applied to this player."));
        } else {
            for (String markName : marks) {
                double remaining = registry.getRemainingDuration(target, markName);
                String time = remaining < 0 ? "§dpermanent" : "§e" + String.format("%.1fs", remaining);
                inv.setItem(slot++, createInfoItem(Material.NAME_TAG, "§6§l" + markName,
                        "§7Remaining: " + time));
                if (slot >= 27) break;
            }
        }

        // === Attribute Modifiers (scan Bukkit directly for our plugin's modifiers) ===
        slot = 27; // Fourth row
        Map<Attribute, List<AttributeModifier>> attrMods = new LinkedHashMap<>();
        for (Attribute attr : Attribute.values()) {
            AttributeInstance inst = target.getAttribute(attr);
            if (inst == null) continue;
            for (AttributeModifier mod : inst.getModifiers()) {
                String key = mod.getKey().getKey();
                if (key.startsWith("arcane_sigils_attr") || key.startsWith("arcane_sigils_persist")) {
                    attrMods.computeIfAbsent(attr, k -> new ArrayList<>()).add(mod);
                }
            }
        }
        if (attrMods.isEmpty()) {
            inv.setItem(slot++, createInfoItem(Material.GRAY_DYE, "§7No Attribute Modifiers",
                    "§8No active attribute modifiers."));
        } else {
            for (var entry : attrMods.entrySet()) {
                String attrName = formatAttribute(entry.getKey());
                List<String> lore = new ArrayList<>();
                lore.add("§7Active modifiers:");
                for (AttributeModifier mod : entry.getValue()) {
                    String op = switch (mod.getOperation()) {
                        case ADD_NUMBER -> "flat";
                        case ADD_SCALAR -> "base%";
                        case MULTIPLY_SCALAR_1 -> "mult";
                    };
                    String valStr = String.format("%.2f", mod.getAmount());
                    // Extract source from key (after prefix)
                    String key = mod.getKey().getKey();
                    String source = key.replaceFirst("^arcane_sigils_(attr|persist)_?", "");
                    if (source.length() > 20) source = source.substring(0, 20) + "..";
                    lore.add("§8- §f" + source + " §7(" + op + " " + valStr + ")");
                }
                inv.setItem(slot++, createLoreItem(Material.IRON_CHESTPLATE, "§e§l" + attrName, lore));
                if (slot >= 36) break;
            }
        }

        // Refresh button - slot 49
        ItemStack refresh = new ItemStack(Material.LIME_DYE);
        ItemMeta refreshMeta = refresh.getItemMeta();
        refreshMeta.setDisplayName("§a§lRefresh");
        refreshMeta.setLore(List.of("§7Click to refresh data"));
        refresh.setItemMeta(refreshMeta);
        inv.setItem(49, refresh);

        // Close button - slot 53
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§c§lClose");
        close.setItemMeta(closeMeta);
        inv.setItem(53, close);

        guiManager.openGUI(viewer, inv, session);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        if (slot == 49) {
            // Refresh
            playSound(player, "click");
            UUID targetUUID = session.get("targetUUID", UUID.class);
            if (targetUUID != null) {
                Player target = Bukkit.getPlayer(targetUUID);
                if (target != null && target.isOnline()) {
                    openGUI(guiManager, player, target);
                } else {
                    player.sendMessage("§cPlayer is no longer online.");
                    player.closeInventory();
                }
            }
        } else if (slot == 53) {
            playSound(player, "close");
            player.closeInventory();
        }
    }

    @Override
    public void reopen(Player player, GUISession session) {
        UUID targetUUID = session.get("targetUUID", UUID.class);
        if (targetUUID != null) {
            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null) {
                openGUI(guiManager, player, target);
            }
        }
    }

    private static ItemStack createInfoItem(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(loreLines));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createLoreItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String formatAttribute(Attribute attribute) {
        String name = attribute.name();
        if (name.contains("MOVEMENT_SPEED")) return "Movement Speed";
        if (name.contains("ATTACK_DAMAGE")) return "Attack Damage";
        if (name.contains("ATTACK_SPEED")) return "Attack Speed";
        if (name.contains("MAX_HEALTH")) return "Max Health";
        if (name.contains("ARMOR")) return "Armor";
        if (name.contains("KNOCKBACK")) return "Knockback Resist";
        return name.replace("GENERIC_", "").replace("_", " ");
    }
}
