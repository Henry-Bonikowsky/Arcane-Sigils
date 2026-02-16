package com.miracle.arcanesigils.enchanter.gui;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.SocketManager;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Main Enchanter GUI - shows equipped items and browse options.
 * Layout: 6 rows (54 slots)
 */
public class EnchanterMainHandler extends AbstractHandler {

    // Armor slots
    private static final int HELMET_SLOT = 10;
    private static final int CHESTPLATE_SLOT = 12;
    private static final int LEGGINGS_SLOT = 14;
    private static final int BOOTS_SLOT = 16;

    // Browse buttons
    private static final int BROWSE_ALL_SLOT = 39;
    private static final int BROWSE_EXCLUSIVE_SLOT = 41;

    private enum EquipmentType {
        HELMET(HELMET_SLOT, Material.NETHERITE_HELMET, "Helmet"),
        CHESTPLATE(CHESTPLATE_SLOT, Material.NETHERITE_CHESTPLATE, "Chestplate"),
        LEGGINGS(LEGGINGS_SLOT, Material.NETHERITE_LEGGINGS, "Leggings"),
        BOOTS(BOOTS_SLOT, Material.NETHERITE_BOOTS, "Boots");

        final int slot;
        final Material material;
        final String displayName;

        EquipmentType(int slot, Material material, String displayName) {
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
        }
    }

    private enum WeaponType {
        SWORD(19, Material.NETHERITE_SWORD, "Sword"),
        AXE(20, Material.NETHERITE_AXE, "Axe"),
        BOW(21, Material.BOW, "Bow"),
        PICKAXE(22, Material.NETHERITE_PICKAXE, "Pickaxe"),
        HOE(23, Material.NETHERITE_HOE, "Hoe"),
        SHOVEL(24, Material.NETHERITE_SHOVEL, "Shovel"),
        FISHING_ROD(25, Material.FISHING_ROD, "Fishing Rod"),
        CROSSBOW(31, Material.CROSSBOW, "Crossbow");

        final int slot;
        final Material material;
        final String displayName;

        WeaponType(int slot, Material material, String displayName) {
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
        }
    }

    public EnchanterMainHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        // Browse All Sigils
        if (slot == BROWSE_ALL_SLOT) {
            playSound(player, "click");
            GUISession browseSession = new GUISession(GUIType.ENCHANTER_BROWSE_ALL);
            browseSession.put("parentType", GUIType.ENCHANTER_MAIN);
            browseSession.put("page", 1);
            guiManager.reopenGUI(player, browseSession);
            return;
        }

        // Browse Exclusive Sigils
        if (slot == BROWSE_EXCLUSIVE_SLOT) {
            playSound(player, "click");
            GUISession browseSession = new GUISession(GUIType.ENCHANTER_BROWSE_EXCLUSIVE);
            browseSession.put("parentType", GUIType.ENCHANTER_MAIN);
            browseSession.put("page", 1);
            guiManager.reopenGUI(player, browseSession);
            return;
        }

        // Check armor slots
        for (EquipmentType type : EquipmentType.values()) {
            if (slot == type.slot) {
                handleEquipmentClick(player, session, type);
                return;
            }
        }

        // Check weapon slots
        for (WeaponType type : WeaponType.values()) {
            if (slot == type.slot) {
                handleWeaponClick(player, session, type);
                return;
            }
        }
    }

    /**
     * Handle click on armor placeholder
     */
    private void handleEquipmentClick(Player player, GUISession session, EquipmentType type) {
        ItemStack actual = getEquippedArmor(player, type);

        // Do nothing if not equipped or has 0 sigils
        if (actual == null || getSigilCount(actual) == 0) {
            return;
        }

        // Open equipment sigils screen
        playSound(player, "click");
        GUISession equipSession = new GUISession(GUIType.ENCHANTER_EQUIPMENT);
        equipSession.put("parentType", GUIType.ENCHANTER_MAIN);
        equipSession.put("armorItem", actual.clone());
        guiManager.reopenGUI(player, equipSession);
    }

    /**
     * Handle click on weapon placeholder
     */
    private void handleWeaponClick(Player player, GUISession session, WeaponType type) {
        ItemStack actual = getMatchingWeapon(player, type);

        // Do nothing if not in inventory or has 0 sigils
        if (actual == null || getSigilCount(actual) == 0) {
            return;
        }

        // Open equipment sigils screen
        playSound(player, "click");
        GUISession equipSession = new GUISession(GUIType.ENCHANTER_EQUIPMENT);
        equipSession.put("parentType", GUIType.ENCHANTER_MAIN);
        equipSession.put("armorItem", actual.clone());
        guiManager.reopenGUI(player, equipSession);
    }

    /**
     * Create armor placeholder item showing sigil count
     */
    private ItemStack createEquipmentPlaceholder(Player player, EquipmentType type) {
        ItemStack actual = getEquippedArmor(player, type);
        int sigilCount = actual != null ? getSigilCount(actual) : 0;

        String name = String.format("§f%s §7(%d Sigils)", type.displayName, sigilCount);

        java.util.List<String> lore = new java.util.ArrayList<>();
        if (sigilCount > 0) {
            lore.add("§7Click to view sigils");
        } else {
            lore.add("§7No sigils socketed");
        }

        return ItemBuilder.createItem(type.material, name, lore);
    }

    /**
     * Create weapon/tool placeholder item showing sigil count
     */
    private ItemStack createWeaponPlaceholder(Player player, WeaponType type) {
        ItemStack actual = getMatchingWeapon(player, type);
        int sigilCount = actual != null ? getSigilCount(actual) : 0;

        String name = String.format("§f%s §7(%d Sigils)", type.displayName, sigilCount);

        java.util.List<String> lore = new java.util.ArrayList<>();
        if (sigilCount > 0) {
            lore.add("§7Click to view sigils");
        } else {
            lore.add("§7No sigils socketed");
        }

        return ItemBuilder.createItem(type.material, name, lore);
    }

    /**
     * Get equipped armor piece by type
     */
    private ItemStack getEquippedArmor(Player player, EquipmentType type) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        return switch (type) {
            case HELMET -> armor[3];
            case CHESTPLATE -> armor[2];
            case LEGGINGS -> armor[1];
            case BOOTS -> armor[0];
        };
    }

    /**
     * Find matching weapon/tool in player inventory
     */
    private ItemStack getMatchingWeapon(Player player, WeaponType type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == type.material) {
                return item;
            }
        }
        return null;
    }

    /**
     * Count sigils socketed on an item
     */
    private int getSigilCount(ItemStack item) {
        if (item == null) return 0;
        SocketManager socketManager = plugin.getSocketManager();
        return socketManager.getSocketedSigilData(item).size();
    }

    @Override
    public void reopen(Player player, GUISession session) {
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.colorize("§8Enchanter"));

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Place armor placeholders
        for (EquipmentType type : EquipmentType.values()) {
            inv.setItem(type.slot, createEquipmentPlaceholder(player, type));
        }

        // Place weapon/tool placeholders
        for (WeaponType type : WeaponType.values()) {
            inv.setItem(type.slot, createWeaponPlaceholder(player, type));
        }

        // Browse buttons
        inv.setItem(BROWSE_ALL_SLOT, ItemBuilder.createItem(
            Material.KNOWLEDGE_BOOK,
            "§eBrowse All Sigils",
            "§7View all non-exclusive sigils",
            "§7and their tier information"
        ));

        inv.setItem(BROWSE_EXCLUSIVE_SLOT, ItemBuilder.createItem(
            Material.NETHER_STAR,
            "§dBrowse Exclusive Sigils",
            "§7View all exclusive sigils",
            "§7from crates and events"
        ));

        guiManager.openGUI(player, inv, session);
    }
}
