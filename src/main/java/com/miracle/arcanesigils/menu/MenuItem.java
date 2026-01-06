package com.miracle.arcanesigils.menu;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.miracle.arcanesigils.utils.TextUtil;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a clickable menu item in the GUI.
 * Provides a builder pattern for creating menu items with various properties.
 */
public class MenuItem {

    private final String id;
    private Material material;
    private String displayName;
    private List<String> lore;
    private int slot;
    private int customModelData;
    private boolean glowing;
    private MenuAction action;
    private String permission;
    private boolean closeOnClick;

    /**
     * Create a new MenuItem with the specified ID.
     *
     * @param id Unique identifier for this menu item
     */
    public MenuItem(String id) {
        this.id = id;
        this.material = Material.STONE;
        this.displayName = id;
        this.lore = new ArrayList<>();
        this.slot = 0;
        this.customModelData = 0;
        this.glowing = false;
        this.action = null;
        this.permission = null;
        this.closeOnClick = false;
    }

    /**
     * Create a MenuItem using the builder pattern.
     *
     * @param id Unique identifier for this menu item
     * @return A new MenuItem builder
     */
    public static MenuItem create(String id) {
        return new MenuItem(id);
    }

    /**
     * Set the material for this menu item.
     *
     * @param material The Material to use
     * @return This MenuItem for chaining
     */
    public MenuItem material(Material material) {
        this.material = material;
        return this;
    }

    /**
     * Set the display name for this menu item.
     * Supports color codes (&c) and hex colors (&#RRGGBB).
     *
     * @param displayName The display name with color codes
     * @return This MenuItem for chaining
     */
    public MenuItem name(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * Set the lore (description) for this menu item.
     * Supports color codes (&c) and hex colors (&#RRGGBB).
     *
     * @param lore The lore lines
     * @return This MenuItem for chaining
     */
    public MenuItem lore(List<String> lore) {
        this.lore = new ArrayList<>(lore);
        return this;
    }

    /**
     * Add a single lore line to this menu item.
     *
     * @param line The lore line to add
     * @return This MenuItem for chaining
     */
    public MenuItem addLore(String line) {
        this.lore.add(line);
        return this;
    }

    /**
     * Set the inventory slot for this menu item.
     *
     * @param slot The slot index (0-53)
     * @return This MenuItem for chaining
     */
    public MenuItem slot(int slot) {
        this.slot = slot;
        return this;
    }

    /**
     * Set the custom model data for resource pack support.
     *
     * @param customModelData The custom model data value
     * @return This MenuItem for chaining
     */
    public MenuItem customModelData(int customModelData) {
        this.customModelData = customModelData;
        return this;
    }

    /**
     * Set whether this item should have an enchantment glow.
     *
     * @param glowing Whether to add glow effect
     * @return This MenuItem for chaining
     */
    public MenuItem glowing(boolean glowing) {
        this.glowing = glowing;
        return this;
    }

    /**
     * Set the action to perform when this item is clicked.
     *
     * @param action The MenuAction to execute
     * @return This MenuItem for chaining
     */
    public MenuItem action(MenuAction action) {
        this.action = action;
        return this;
    }

    /**
     * Set the permission required to see/use this menu item.
     *
     * @param permission The permission node
     * @return This MenuItem for chaining
     */
    public MenuItem permission(String permission) {
        this.permission = permission;
        return this;
    }

    /**
     * Set whether clicking this item should close the menu.
     *
     * @param closeOnClick Whether to close menu on click
     * @return This MenuItem for chaining
     */
    public MenuItem closeOnClick(boolean closeOnClick) {
        this.closeOnClick = closeOnClick;
        return this;
    }

    /**
     * Build the ItemStack representation of this menu item.
     *
     * @return The ItemStack for this menu item
     */
    public ItemStack build() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set display name
        meta.displayName(TextUtil.parseComponent(displayName));

        // Set lore
        if (!lore.isEmpty()) {
            List<Component> componentLore = new ArrayList<>();
            for (String line : lore) {
                componentLore.add(TextUtil.parseComponent(line));
            }
            meta.lore(componentLore);
        }

        // Set custom model data
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        // Set glow effect
        if (glowing) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        // Hide attributes
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

        item.setItemMeta(meta);
        return item;
    }

    // Getters

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public int getSlot() {
        return slot;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public MenuAction getAction() {
        return action;
    }

    public String getPermission() {
        return permission;
    }

    public boolean shouldCloseOnClick() {
        return closeOnClick;
    }

    /**
     * Functional interface for menu item click actions.
     */
    @FunctionalInterface
    public interface MenuAction {
        /**
         * Execute the action when the menu item is clicked.
         *
         * @param context The click context containing player and menu information
         */
        void execute(MenuClickContext context);
    }

    /**
     * Context object passed to MenuAction when a menu item is clicked.
     */
    public static class MenuClickContext {
        private final org.bukkit.entity.Player player;
        private final MenuItem menuItem;
        private final BuildMainMenuComponent menuComponent;
        private final org.bukkit.event.inventory.ClickType clickType;

        public MenuClickContext(org.bukkit.entity.Player player, MenuItem menuItem,
                                BuildMainMenuComponent menuComponent,
                                org.bukkit.event.inventory.ClickType clickType) {
            this.player = player;
            this.menuItem = menuItem;
            this.menuComponent = menuComponent;
            this.clickType = clickType;
        }

        public org.bukkit.entity.Player getPlayer() {
            return player;
        }

        public MenuItem getMenuItem() {
            return menuItem;
        }

        public BuildMainMenuComponent getMenuComponent() {
            return menuComponent;
        }

        public org.bukkit.event.inventory.ClickType getClickType() {
            return clickType;
        }
    }
}
