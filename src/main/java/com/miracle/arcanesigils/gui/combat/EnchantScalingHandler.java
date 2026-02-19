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
 * Sub-GUI for configuring Protection or Sharpness enchant scaling.
 * Reused for both enchant types via session key "enchantType".
 *
 * Layout (27 slots / 3 rows):
 * Row 0: [_][_][I][_][T][_][_][_][_]  I=Info, T=Toggle
 * Row 1: [1][2][3][4][5][_][_][_][_]  Levels 1-5
 * Row 2: [6][7][8][9][10][_][_][_][B] Levels 6-10, B=Back
 */
public class EnchantScalingHandler extends AbstractHandler {

    private static final int SLOT_INFO = 2;
    private static final int SLOT_TOGGLE = 4;
    private static final int SLOT_BACK = 26;

    public EnchantScalingHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    public static void openGUI(GUIManager guiManager, Player player, String enchantType) {
        GUISession session = new GUISession(GUIType.ENCHANT_SCALING);
        session.put("enchantType", enchantType);
        buildInventory(guiManager, player, session);
    }

    private static void buildInventory(GUIManager guiManager, Player player, GUISession session) {
        String enchantType = session.get("enchantType", String.class);
        boolean isProtection = "protection".equals(enchantType);
        boolean isUnbreaking = "unbreaking".equals(enchantType);
        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
        CombatUtil combat = plugin.getCombatUtil();

        String title = isProtection ? "§8Protection Scaling"
                : isUnbreaking ? "§8Unbreaking Scaling" : "§8Sharpness Scaling";
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Fill background
        for (int i = 0; i < 27; i++) inv.setItem(i, ItemBuilder.createBackground());

        // Info
        if (isProtection) {
            inv.setItem(SLOT_INFO, ItemBuilder.createInfoItem("Protection Scaling",
                    "§7Configure DR% per Protection level",
                    "§7Applied per armor piece",
                    "§7Example: Prot 4 on 4 pieces = 4 x value"));
        } else if (isUnbreaking) {
            inv.setItem(SLOT_INFO, ItemBuilder.createInfoItem("Unbreaking Scaling",
                    "§7Configure durability ignore chance per level",
                    "§7Chance that a durability hit is ignored",
                    "§7Works on ALL items (armor, weapons, tools)"));
        } else {
            inv.setItem(SLOT_INFO, ItemBuilder.createInfoItem("Sharpness Scaling",
                    "§7Configure bonus damage per Sharpness level",
                    "§7Replaces vanilla formula (0.5 * level + 0.5)",
                    "§7Applied to weapon's Sharpness enchant"));
        }

        // Enabled toggle
        String configKey = isProtection ? "combat.protection-scaling.enabled"
                : isUnbreaking ? "combat.unbreaking-scaling.enabled"
                : "combat.sharpness-scaling.enabled";
        boolean enabled = plugin.getConfig().getBoolean(configKey, false);
        String toggleName = isProtection ? "§b§lProtection Override"
                : isUnbreaking ? "§e§lUnbreaking Override"
                : "§c§lSharpness Override";
        inv.setItem(SLOT_TOGGLE, ItemBuilder.createToggle(enabled, toggleName,
                "§7When disabled, vanilla behavior is used"));

        // Levels 1-5 (row 1, slots 9-13)
        for (int level = 1; level <= 5; level++) {
            inv.setItem(8 + level, createLevelItem(enchantType, level, combat));
        }

        // Levels 6-10 (row 2, slots 18-22)
        for (int level = 6; level <= 10; level++) {
            inv.setItem(12 + level, createLevelItem(enchantType, level, combat));
        }

        // Back button
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("Combat Config"));

        guiManager.openGUI(player, inv, session);
    }

    private static org.bukkit.inventory.ItemStack createLevelItem(String enchantType, int level, CombatUtil combat) {
        boolean isProtection = "protection".equals(enchantType);
        boolean isUnbreaking = "unbreaking".equals(enchantType);

        Material mat = isProtection ? Material.IRON_CHESTPLATE
                : isUnbreaking ? Material.ANVIL : Material.IRON_SWORD;

        double value;
        String unit;
        String vanillaNote;

        if (isProtection) {
            value = combat.getProtectionDR(level);
            unit = "% DR per piece";
            vanillaNote = "§7Vanilla: " + (level * 4) + "% EPF contribution";
        } else if (isUnbreaking) {
            value = combat.getUnbreakingIgnoreChance(level);
            unit = " ignore chance";
            double vanillaChance = 1.0 - (0.6 + 0.4 / (level + 1));
            vanillaNote = "§7Vanilla: " + String.format("%.1f", vanillaChance * 100) + "% ignore chance";
        } else {
            value = combat.getSharpnessBonus(level);
            unit = " bonus damage";
            vanillaNote = "§7Vanilla: " + String.format("%.1f", 0.5 * level + 0.5) + " bonus";
        }

        return ItemBuilder.createConfigValue(mat,
                "§e§lLevel " + level,
                value,
                vanillaNote,
                "§7Current: §f" + value + unit);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String enchantType = session.get("enchantType", String.class);
        boolean isProtection = "protection".equals(enchantType);
        Runnable reopen = () -> buildInventory(guiManager, player, session);

        boolean isUnbreaking = "unbreaking".equals(enchantType);

        switch (slot) {
            case SLOT_TOGGLE -> {
                String key = isProtection ? "combat.protection-scaling.enabled"
                        : isUnbreaking ? "combat.unbreaking-scaling.enabled"
                        : "combat.sharpness-scaling.enabled";
                String label = isProtection ? "Protection Scaling"
                        : isUnbreaking ? "Unbreaking Scaling"
                        : "Sharpness Scaling";
                toggleConfigBool(player, key, label, reopen);
            }
            case SLOT_BACK -> {
                playSound(player, "click");
                CombatConfigHandler.openGUI(guiManager, player);
            }
            default -> {
                int level = getLevelFromSlot(slot);
                if (level > 0) {
                    String configSection = isProtection ? "combat.protection-scaling."
                            : isUnbreaking ? "combat.unbreaking-scaling."
                            : "combat.sharpness-scaling.";
                    String key = configSection + level;
                    String prefix = isProtection ? "Prot" : isUnbreaking ? "Unbreak" : "Sharp";
                    String label = prefix + " Level " + level;
                    double max = isProtection ? 100.0 : isUnbreaking ? 1.0 : 50.0;
                    promptConfigEdit(player, key, label, 0, max, reopen);
                }
            }
        }
    }

    private int getLevelFromSlot(int slot) {
        // Row 1: slots 9-13 = levels 1-5
        if (slot >= 9 && slot <= 13) return slot - 8;
        // Row 2: slots 18-22 = levels 6-10
        if (slot >= 18 && slot <= 22) return slot - 12;
        return -1;
    }

    @Override
    public void reopen(Player player, GUISession session) {
        buildInventory(guiManager, player, session);
    }
}
