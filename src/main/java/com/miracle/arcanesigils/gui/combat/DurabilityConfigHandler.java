package com.miracle.arcanesigils.gui.combat;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.combat.CombatUtil;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * Sub-GUI for configuring per-material armor durability overrides.
 *
 * Layout (27 slots / 3 rows):
 * Row 0: [_][_][I][_][T][_][_][_][_]  I=Info, T=Toggle
 * Row 1: [L][C][I][G][D][N][_][_][_]  6 materials
 * Row 2: [_][_][_][_][_][_][_][_][B]  B=Back
 */
public class DurabilityConfigHandler extends AbstractHandler {

    private static final int SLOT_INFO = 2;
    private static final int SLOT_TOGGLE = 4;
    // Row 1: materials at slots 9-14
    private static final int SLOT_LEATHER = 9;
    private static final int SLOT_CHAINMAIL = 10;
    private static final int SLOT_IRON = 11;
    private static final int SLOT_GOLD = 12;
    private static final int SLOT_DIAMOND = 13;
    private static final int SLOT_NETHERITE = 14;
    private static final int SLOT_BACK = 26;

    public DurabilityConfigHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    public static void openGUI(GUIManager guiManager, Player player) {
        GUISession session = new GUISession(GUIType.DURABILITY_CONFIG);
        buildInventory(guiManager, player, session);
    }

    private static void buildInventory(GUIManager guiManager, Player player, GUISession session) {
        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
        CombatUtil combat = plugin.getCombatUtil();

        Inventory inv = Bukkit.createInventory(null, 27, "§8Durability Overrides");

        // Fill background
        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.createBackground());

        // Info
        inv.setItem(SLOT_INFO, ItemBuilder.createInfoItem("Durability Overrides",
                "§7Set max durability per armor material",
                "§7Applied on first equip (skips custom items)",
                "§7Values are for chestplate equivalent"));

        // Toggle
        boolean enabled = plugin.getConfig().getBoolean("combat.durability.enabled", false);
        inv.setItem(SLOT_TOGGLE, ItemBuilder.createToggle(enabled,
                "§e§lDurability Override",
                "§7When disabled, vanilla durability is used"));

        // Material items
        inv.setItem(SLOT_LEATHER, ItemBuilder.createConfigValue(Material.LEATHER_CHESTPLATE,
                "§6§lLeather", combat.getDurability("LEATHER"),
                "§7Vanilla: 80"));
        inv.setItem(SLOT_CHAINMAIL, ItemBuilder.createConfigValue(Material.CHAINMAIL_CHESTPLATE,
                "§7§lChainmail", combat.getDurability("CHAINMAIL"),
                "§7Vanilla: 240"));
        inv.setItem(SLOT_IRON, ItemBuilder.createConfigValue(Material.IRON_CHESTPLATE,
                "§f§lIron", combat.getDurability("IRON"),
                "§7Vanilla: 240"));
        inv.setItem(SLOT_GOLD, ItemBuilder.createConfigValue(Material.GOLDEN_CHESTPLATE,
                "§e§lGold", combat.getDurability("GOLD"),
                "§7Vanilla: 112"));
        inv.setItem(SLOT_DIAMOND, ItemBuilder.createConfigValue(Material.DIAMOND_CHESTPLATE,
                "§b§lDiamond", combat.getDurability("DIAMOND"),
                "§7Vanilla: 528"));
        inv.setItem(SLOT_NETHERITE, ItemBuilder.createConfigValue(Material.NETHERITE_CHESTPLATE,
                "§8§lNetherite", combat.getDurability("NETHERITE"),
                "§7Vanilla: 592"));

        // Back
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("Combat Config"));

        guiManager.openGUI(player, inv, session);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Runnable reopen = () -> buildInventory(guiManager, player, session);

        switch (slot) {
            case SLOT_TOGGLE -> toggleConfigBool(player, "combat.durability.enabled", "Durability Override", reopen);
            case SLOT_LEATHER -> promptConfigEdit(player, "combat.durability.leather", "Leather Durability", 1, 10000, reopen);
            case SLOT_CHAINMAIL -> promptConfigEdit(player, "combat.durability.chainmail", "Chainmail Durability", 1, 10000, reopen);
            case SLOT_IRON -> promptConfigEdit(player, "combat.durability.iron", "Iron Durability", 1, 10000, reopen);
            case SLOT_GOLD -> promptConfigEdit(player, "combat.durability.gold", "Gold Durability", 1, 10000, reopen);
            case SLOT_DIAMOND -> promptConfigEdit(player, "combat.durability.diamond", "Diamond Durability", 1, 10000, reopen);
            case SLOT_NETHERITE -> promptConfigEdit(player, "combat.durability.netherite", "Netherite Durability", 1, 10000, reopen);
            case SLOT_BACK -> {
                playSound(player, "click");
                CombatConfigHandler.openGUI(guiManager, player);
            }
            default -> { }
        }
    }

    @Override
    public void reopen(Player player, GUISession session) {
        buildInventory(guiManager, player, session);
    }
}
