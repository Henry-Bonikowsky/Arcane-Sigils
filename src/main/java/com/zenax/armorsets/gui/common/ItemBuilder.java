package com.zenax.armorsets.gui.common;

import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for creating GUI items.
 * Provides static methods for common item creation patterns used throughout the GUI system.
 */
public final class ItemBuilder {

    private ItemBuilder() {
        // Utility class - prevent instantiation
    }

    /**
     * Create a GUI item with a display name and lore.
     *
     * @param material  The item material
     * @param name      The display name (color codes supported with &)
     * @param loreLines The lore lines (color codes supported with &)
     * @return The created ItemStack
     */
    public static ItemStack createGuiItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(TextUtil.parseComponent(name));
        if (loreLines != null && loreLines.length > 0) {
            List<Component> loreList = new ArrayList<>();
            for (String line : loreLines) {
                if (line != null && !line.isEmpty()) {
                    loreList.add(TextUtil.parseComponent(line));
                }
            }
            if (!loreList.isEmpty()) {
                meta.lore(loreList);
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create a border/filler item (black stained glass pane with empty name).
     *
     * @return The border ItemStack
     */
    public static ItemStack createBorderItem() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Create a close/back button item.
     *
     * @param label The button label (e.g., "Close", "Back")
     * @return The barrier ItemStack
     */
    public static ItemStack createCloseButton(String label) {
        return createGuiItem(Material.BARRIER, "&c" + label, "&7Close menu");
    }

    /**
     * Create a confirm button item.
     *
     * @param label The button label (e.g., "Confirm", "Save")
     * @return The lime dye ItemStack
     */
    public static ItemStack createConfirmButton(String label) {
        return createGuiItem(Material.LIME_DYE, "&a" + label);
    }

    /**
     * Create a cancel button item.
     *
     * @param label The button label (e.g., "Cancel", "Back")
     * @return The red dye ItemStack
     */
    public static ItemStack createCancelButton(String label) {
        return createGuiItem(Material.RED_DYE, "&c" + label);
    }

    /**
     * Create an increment button.
     *
     * @param amount The increment amount (e.g., "+1", "+10")
     * @param color  The button color ("lime" or "green")
     * @return The stained glass pane ItemStack
     */
    public static ItemStack createIncrementButton(String amount, String color) {
        Material mat = "green".equalsIgnoreCase(color)
            ? Material.GREEN_STAINED_GLASS_PANE
            : Material.LIME_STAINED_GLASS_PANE;
        String colorCode = "green".equalsIgnoreCase(color) ? "&2" : "&a";
        return createGuiItem(mat, colorCode + amount, "&7Increase");
    }

    /**
     * Create a decrement button.
     *
     * @param amount The decrement amount (e.g., "-1", "-10")
     * @param color  The button color ("red" or "orange")
     * @return The stained glass pane ItemStack
     */
    public static ItemStack createDecrementButton(String amount, String color) {
        Material mat = "orange".equalsIgnoreCase(color)
            ? Material.ORANGE_STAINED_GLASS_PANE
            : Material.RED_STAINED_GLASS_PANE;
        String colorCode = "orange".equalsIgnoreCase(color) ? "&6" : "&c";
        return createGuiItem(mat, colorCode + amount, "&7Decrease");
    }

    /**
     * Create an info/display item.
     *
     * @param material The display material
     * @param title    The title (color codes supported)
     * @param lore     The lore lines
     * @return The info ItemStack
     */
    public static ItemStack createInfoItem(Material material, String title, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parseComponent(title));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Create a navigation arrow item.
     *
     * @param direction "left" for previous, "right" for next
     * @param label     The navigation label
     * @return The arrow ItemStack
     */
    public static ItemStack createNavigationArrow(String direction, String label) {
        return createGuiItem(Material.ARROW, "&7" + label, "&7Navigate " + direction);
    }
}
