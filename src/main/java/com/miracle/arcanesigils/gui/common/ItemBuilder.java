package com.miracle.arcanesigils.gui.common;

import com.miracle.arcanesigils.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for building GUI items with consistent styling.
 */
public final class ItemBuilder {

    private ItemBuilder() {}

    /**
     * Create an item with display name and lore (varargs).
     */
    public static ItemStack createItem(Material material, String name, String... lore) {
        return createItem(material, name, Arrays.asList(lore));
    }

    /**
     * Create an item with display name and lore (list).
     */
    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Set display name
            meta.displayName(TextUtil.parseComponent(name));

            // Set lore
            if (lore != null && !lore.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(TextUtil.parseComponent(line));
                }
                meta.lore(loreComponents);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Fill all empty slots in an inventory with a background item.
     */
    public static void fillBackground(Inventory inventory) {
        ItemStack background = createBackground();

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType() == Material.AIR) {
                inventory.setItem(i, background);
            }
        }
    }

    /**
     * Create a single background item (gray glass pane).
     */
    public static ItemStack createBackground() {
        return createItem(
            Material.GRAY_STAINED_GLASS_PANE,
            "§8Arcane Sigils"
        );
    }

    /**
     * Create a "Back" button.
     */
    public static ItemStack createBackButton(String previousGuiName) {
        return createItem(
            Material.RED_DYE,
            "§c← Back",
            "§7Return to §f" + previousGuiName
        );
    }

    /**
     * Create a page navigation arrow.
     */
    public static ItemStack createPageArrow(boolean isNext, int currentPage, int maxPage) {
        if (isNext) {
            if (currentPage >= maxPage) {
                // No next page
                return createItem(
                    Material.GRAY_DYE,
                    "§7Next Page →",
                    "§cNo more pages"
                );
            } else {
                return createItem(
                    Material.LIME_DYE,
                    "§aNext Page →",
                    "§7Click to go to page §f" + (currentPage + 1)
                );
            }
        } else {
            if (currentPage <= 1) {
                // No previous page
                return createItem(
                    Material.GRAY_DYE,
                    "§7← Previous Page",
                    "§cNo previous pages"
                );
            } else {
                return createItem(
                    Material.LIME_DYE,
                    "§a← Previous Page",
                    "§7Click to go to page §f" + (currentPage - 1)
                );
            }
        }
    }

    /**
     * Create a page indicator item.
     */
    public static ItemStack createPageIndicator(int currentPage, int maxPage, int totalItems) {
        return createItem(
            Material.PAPER,
            "§ePage §f" + currentPage + "§7/&f" + maxPage,
            "§7Total Items: §f" + totalItems
        );
    }

    /**
     * Add enchantment glow effect to an item.
     */
    public static ItemStack addGlow(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Alias for addGlow - used by binds system.
     */
    public static ItemStack addGlowEffect(ItemStack item) {
        return addGlow(item);
    }

    /**
     * Create a GUI item - alias for createItem (used by binds system).
     */
    public static ItemStack createGuiItem(Material material, String name, String... lore) {
        return createItem(material, name, lore);
    }

    /**
     * Create a confirmation button (green).
     */
    public static ItemStack createConfirmButton(String action) {
        return createItem(
            Material.LIME_DYE,
            "§a✓ Confirm",
            "§7Click to confirm " + action
        );
    }

    /**
     * Create a cancel button (red).
     */
    public static ItemStack createCancelButton() {
        return createItem(
            Material.RED_DYE,
            "§c✗ Cancel",
            "§7Click to cancel"
        );
    }

    /**
     * Create an info item.
     */
    public static ItemStack createInfoItem(String title, String... info) {
        return createItem(
            Material.BOOK,
            "§e&l" + title,
            info
        );
    }
}
