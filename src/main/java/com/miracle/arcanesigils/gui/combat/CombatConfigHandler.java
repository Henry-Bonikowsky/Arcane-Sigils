package com.miracle.arcanesigils.gui.combat;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * GUI for configuring all combat/balance settings.
 * Admin only. Edits config.yml values and hot-reloads.
 *
 * Layout (4 rows = 36 slots):
 * Row 0: [_][_][_][_][I][_][_][_][_]  I=Info header
 * Row 1: [S][P][S'][U][_][B][C][H][_]  P=Protection, S'=Sharpness, U=Unbreaking, B=Blocking, C=Crit, H=Heart Cap
 * Row 2: [S][G][E][T][P'][_][_][_][_]  G=Gapple, E=EGapple, T=Totem, P'=Pearl
 * Row 3: [_][_][_][_][X][_][_][_][_]  X=Close
 */
public class CombatConfigHandler extends AbstractHandler {

    // Row 1: Enchant Scalars + Combat Values
    private static final int SLOT_PROTECTION_SCALING = 10;
    private static final int SLOT_SHARPNESS_SCALING = 11;
    private static final int SLOT_UNBREAKING_SCALING = 12;
    private static final int SLOT_BLOCKING_DR = 14;
    private static final int SLOT_CRIT_MULTIPLIER = 15;
    private static final int SLOT_MAX_DMG_PER_HIT = 16;

    // Row 2: Item Cooldowns
    private static final int SLOT_GAPPLE_CD = 19;
    private static final int SLOT_EGAPPLE_CD = 20;
    private static final int SLOT_TOTEM_CD = 21;
    private static final int SLOT_PEARL_CD = 22;

    // Row 3: Navigation
    private static final int SLOT_CLOSE = 31;

    public CombatConfigHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    public static void openGUI(GUIManager guiManager, Player player) {
        GUISession session = new GUISession(GUIType.COMBAT_CONFIG);
        FileConfiguration c = ArmorSetsPlugin.getInstance().getConfig();

        Inventory inv = Bukkit.createInventory(null, 36, "§8Combat Configuration");

        // Fill background
        ItemStack bg = ItemBuilder.createBackground();
        for (int i = 0; i < 36; i++) inv.setItem(i, bg);

        // Row 0: Header
        inv.setItem(4, ItemBuilder.createInfoItem("Combat & Balance",
                "§7All combat-related settings.",
                "§7Click any value to edit.",
                "§7Changes apply immediately."));

        // Row 1: Enchant Scalars + Combat Values
        inv.setItem(9, ItemBuilder.createSection(Material.IRON_SWORD, "§c§l--- Combat Settings ---"));
        inv.setItem(SLOT_PROTECTION_SCALING, createSubGuiItem(Material.ENCHANTED_BOOK,
                "§b§lProtection Scaling",
                c.getBoolean("combat.protection-scaling.enabled", false),
                "§7Override vanilla Protection enchant",
                "§7Configure DR% per level per piece"));
        inv.setItem(SLOT_SHARPNESS_SCALING, createSubGuiItem(Material.DIAMOND_SWORD,
                "§c§lSharpness Scaling",
                c.getBoolean("combat.sharpness-scaling.enabled", false),
                "§7Override vanilla Sharpness enchant",
                "§7Configure bonus damage per level"));
        inv.setItem(SLOT_UNBREAKING_SCALING, createSubGuiItem(Material.ANVIL,
                "§e§lUnbreaking Scaling",
                c.getBoolean("combat.unbreaking-scaling.enabled", false),
                "§7Override vanilla Unbreaking enchant",
                "§7Configure durability ignore chance per level"));
        inv.setItem(SLOT_BLOCKING_DR, ItemBuilder.createConfigValue(Material.SHIELD, "§9§lBlocking DR %",
                c.getDouble("combat.blocking-damage-reduction", 1.0),
                "§7Shield blocking damage reduction (0-1)",
                "§71.0 = 100% block (vanilla)", "§70.5 = 50% block"));
        inv.setItem(SLOT_CRIT_MULTIPLIER, ItemBuilder.createConfigValue(Material.DIAMOND_AXE, "§c§lCrit Multiplier",
                c.getDouble("combat.crit-multiplier", 1.5),
                "§7Critical hit damage multiplier",
                "§71.5 = vanilla (50% bonus)"));
        inv.setItem(SLOT_MAX_DMG_PER_HIT, ItemBuilder.createConfigValue(Material.NETHERITE_SWORD, "§4§lMax Damage Per Hit",
                c.getDouble("combat.max-damage-per-hit", 20.0),
                "§7Hard cap on damage in half-hearts",
                "§720 = max 10 hearts of damage"));

        // Row 2: Item Cooldowns
        inv.setItem(18, ItemBuilder.createSection(Material.CLOCK, "§6§l--- Item Cooldowns ---"));
        inv.setItem(SLOT_GAPPLE_CD, ItemBuilder.createConfigValue(Material.GOLDEN_APPLE, "§6§lGolden Apple CD",
                c.getDouble("item-cooldowns.golden-apple", 9.0), "§7Cooldown in seconds"));
        inv.setItem(SLOT_EGAPPLE_CD, ItemBuilder.createConfigValue(Material.ENCHANTED_GOLDEN_APPLE, "§d§lEnchanted Gapple CD",
                c.getDouble("item-cooldowns.enchanted-golden-apple", 9.0), "§7Cooldown in seconds"));
        inv.setItem(SLOT_TOTEM_CD, ItemBuilder.createConfigValue(Material.TOTEM_OF_UNDYING, "§a§lTotem CD",
                c.getDouble("item-cooldowns.totem-of-undying", 60.0), "§7Cooldown in seconds"));
        inv.setItem(SLOT_PEARL_CD, ItemBuilder.createConfigValue(Material.ENDER_EYE, "§5§lEnder Pearl CD",
                c.getDouble("item-cooldowns.ender-pearl", 0.0), "§7Cooldown in seconds", "§70 = disabled"));

        // Row 3: Close
        inv.setItem(SLOT_CLOSE, ItemBuilder.createCloseButton());

        guiManager.openGUI(player, inv, session);
    }

    private static ItemStack createSubGuiItem(Material mat, String name, boolean enabled,
                                               String... desc) {
        java.util.List<String> lore = new java.util.ArrayList<>();
        for (String line : desc) lore.add(line);
        lore.add("");
        lore.add(enabled ? "§aEnabled" : "§cDisabled");
        lore.add("");
        lore.add("§eClick to configure");
        return ItemBuilder.createItem(mat, name, lore);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Runnable reopen = () -> openGUI(guiManager, player);

        switch (slot) {
            // Enchant Scalars
            case SLOT_PROTECTION_SCALING -> {
                playSound(player, "click");
                EnchantScalingHandler.openGUI(guiManager, player, "protection");
            }
            case SLOT_SHARPNESS_SCALING -> {
                playSound(player, "click");
                EnchantScalingHandler.openGUI(guiManager, player, "sharpness");
            }
            case SLOT_UNBREAKING_SCALING -> {
                playSound(player, "click");
                EnchantScalingHandler.openGUI(guiManager, player, "unbreaking");
            }

            // Combat Values
            case SLOT_BLOCKING_DR -> promptConfigEdit(player, "combat.blocking-damage-reduction", "Blocking DR", 0, 1, reopen);
            case SLOT_CRIT_MULTIPLIER -> promptConfigEdit(player, "combat.crit-multiplier", "Crit Multiplier", 0.1, 10, reopen);
            case SLOT_MAX_DMG_PER_HIT -> promptConfigEdit(player, "combat.max-damage-per-hit", "Max Damage Per Hit", 1, 200, reopen);

            // Item Cooldowns
            case SLOT_GAPPLE_CD -> promptConfigEdit(player, "item-cooldowns.golden-apple", "Gapple CD", 0, 600, reopen);
            case SLOT_EGAPPLE_CD -> promptConfigEdit(player, "item-cooldowns.enchanted-golden-apple", "EGapple CD", 0, 600, reopen);
            case SLOT_TOTEM_CD -> promptConfigEdit(player, "item-cooldowns.totem-of-undying", "Totem CD", 0, 600, reopen);
            case SLOT_PEARL_CD -> promptConfigEdit(player, "item-cooldowns.ender-pearl", "Ender Pearl CD", 0, 600, reopen);

            case SLOT_CLOSE -> {
                playSound(player, "close");
                player.closeInventory();
            }
            default -> { }
        }
    }

    @Override
    public void reopen(Player player, GUISession session) {
        openGUI(guiManager, player);
    }
}
