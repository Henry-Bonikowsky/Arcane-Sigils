package com.zenax.armorsets.binds.gui;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.binds.*;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for creating binds GUI items.
 * Contains shared item creation logic used by both Hotbar and Command handlers.
 */
public class BindsGUIHelper {

    // Slot constants for Hotbar GUI (3 rows = 27 slots)
    public static final int HOTBAR_CLEAR_BINDS_SLOT = 0;
    public static final int HOTBAR_PRESET_START_SLOT = 5;
    public static final int HOTBAR_BIND_START_SLOT = 9; // Binds 1-9 in row 2
    public static final int HOTBAR_TOGGLE_SLOT = 18;
    public static final int HOTBAR_HELD_SLOT_SLOT = 19;
    public static final int HOTBAR_INFO_SLOT = 22;
    public static final int HOTBAR_SWITCH_SLOT = 25;

    // Slot constants for Command GUI (4 rows = 36 slots)
    public static final int CMD_BIND_START_SLOT = 0; // Binds in rows 1-2
    public static final int CMD_CLEAR_SLOT = 18;
    public static final int CMD_ADD_SLOT = 19;
    public static final int CMD_DELETE_SLOT = 20;
    public static final int CMD_PRESET_START_SLOT = 23;
    public static final int CMD_PAGE_LEFT_SLOT = 27;
    public static final int CMD_TOGGLE_SLOT = 28;
    public static final int CMD_INFO_SLOT = 31;
    public static final int CMD_SWITCH_SLOT = 34;
    public static final int CMD_PAGE_RIGHT_SLOT = 35;

    /**
     * Create the "Clear Binds" button item.
     */
    public static ItemStack createClearBindsItem(boolean confirmMode) {
        if (confirmMode) {
            return ItemBuilder.createGuiItem(Material.BLACK_CONCRETE,
                    "§r&c&lClick again to confirm!",
                    "§r&7Clear all binds.");
        }
        return ItemBuilder.createGuiItem(Material.TNT,
                "§r&fClear Binds",
                "§r&c&lLeft Click §r&b- §r&fClear all binds.");
    }

    /**
     * Create a preset button item.
     */
    public static ItemStack createPresetItem(int presetIndex, boolean hasPreset, int totalAbilities) {
        Material material = hasPreset ? Material.FILLED_MAP : Material.PAPER;
        List<String> lore = new ArrayList<>();
        lore.add("§r&3&lShift Left Click §r&b- §r&fSave to preset.");
        lore.add("§r&a&lLeft Click §r&b- §r&fLoad preset.");
        lore.add("§r&c&lRight Click §r&b- §r&fDelete preset.");
        if (hasPreset) {
            lore.add("§r&7Total Abilities Binded&r&b: §r&f" + totalAbilities);
        }

        return ItemBuilder.createGuiItem(material,
                "§r&fPreset " + (presetIndex + 1),
                lore.toArray(new String[0]));
    }

    /**
     * Create a bind slot item (legacy method using simple dyes).
     */
    public static ItemStack createBindItem(int bindNumber, List<String> sigilNames, String keybindDisplay, boolean isCommandMode) {
        boolean hasSigils = sigilNames != null && !sigilNames.isEmpty();
        Material material = hasSigils ? Material.LIME_DYE : Material.LIGHT_GRAY_STAINED_GLASS_PANE;

        List<String> lore = new ArrayList<>();

        if (hasSigils) {
            if (sigilNames.size() == 1) {
                lore.add("§7Binded&r&b: §r&f" + sigilNames.get(0));
            } else {
                for (int i = 0; i < sigilNames.size(); i++) {
                    String ordinal = getOrdinal(i + 1);
                    lore.add("§7" + ordinal + "§r&b. §r&f" + sigilNames.get(i));
                }
            }
        }

        lore.add("§r&a&lLeft Click §r&b- §r&fModify.");
        lore.add("§r&c&lRight Click §r&b- §r&fClear.");

        String keyDisplay = isCommandMode ? String.valueOf(bindNumber) : keybindDisplay;

        ItemStack item = ItemBuilder.createGuiItem(material,
                "§f&lKey Bind #" + bindNumber + " §r&7(" + keyDisplay + ")",
                lore.toArray(new String[0]));
        item.setAmount(Math.min(bindNumber, 64));
        return item;
    }

    /**
     * Create a bind slot item showing the actual socketed item.
     * Preserves ItemsAdder CustomModelData by cloning the original item.
     *
     * Visual indicators:
     * - Glow: Item is in player's inventory (anywhere)
     * - Green name: Equipped (armor) or Hotbar (0-8)
     * - Red name: Main inventory (9-35), offhand, or not in inventory
     *
     * @param plugin        The plugin instance for accessing managers
     * @param player        The player to check inventory for
     * @param bindNumber    The bind slot number
     * @param sigilIds      List of sigil IDs bound to this slot
     * @param keybindDisplay The keybind display string
     * @param isCommandMode Whether this is command mode (affects display)
     * @return The ItemStack to display in the GUI
     */
    public static ItemStack createBindItemWithSocketedItem(
            ArmorSetsPlugin plugin,
            Player player,
            int bindNumber,
            List<String> sigilIds,
            String keybindDisplay,
            boolean isCommandMode) {

        boolean hasSigils = sigilIds != null && !sigilIds.isEmpty();

        if (!hasSigils) {
            // No sigils - show light gray glass pane
            ItemStack item = ItemBuilder.createGuiItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    "§f&lKey Bind #" + bindNumber + " §r&7(" + keybindDisplay + ")",
                    "§r&a&lLeft Click §r&b- §r&fModify.",
                    "§r&c&lRight Click §r&b- §r&fClear.");
            item.setAmount(Math.min(bindNumber, 64));
            return item;
        }

        // Get BindsEditorHandler to use its helper methods
        BindsEditorHandler editorHandler = new BindsEditorHandler(plugin, plugin.getGuiManager());

        // Get first sigil to determine display item
        String firstSigilId = sigilIds.get(0);
        ItemStack socketedItem = editorHandler.findSocketedItem(player, firstSigilId);

        ItemStack displayItem;

        if (socketedItem != null) {
            // Clone the actual item (preserves CustomModelData for ItemsAdder)
            displayItem = socketedItem.clone();
        } else {
            // Sigil not in inventory - use RED_DYE as fallback
            displayItem = new ItemStack(Material.RED_DYE);
        }

        // Check if first sigil needs glow (is in inventory anywhere)
        BindsEditorHandler.SigilLocation firstLocation = editorHandler.getSigilLocation(player, firstSigilId);
        if (firstLocation != BindsEditorHandler.SigilLocation.NOT_FOUND) {
            ItemBuilder.addGlowEffect(displayItem);
        }

        // Build lore with sigil names and colors
        List<String> lore = new ArrayList<>();

        if (sigilIds.size() == 1) {
            // Single sigil
            Sigil sigil = plugin.getSigilManager().getSigil(firstSigilId);
            String sigilName = sigil != null ? TextUtil.stripColors(sigil.getName()) : firstSigilId;
            String nameColor = (firstLocation == BindsEditorHandler.SigilLocation.EQUIPPED ||
                               firstLocation == BindsEditorHandler.SigilLocation.HOTBAR) ? "§a" : "§c";
            lore.add("§7Binded&r&b: §r" + nameColor + sigilName);
        } else {
            // Multiple sigils
            for (int i = 0; i < sigilIds.size(); i++) {
                String sigilId = sigilIds.get(i);
                Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
                String sigilName = sigil != null ? TextUtil.stripColors(sigil.getName()) : sigilId;

                BindsEditorHandler.SigilLocation location = editorHandler.getSigilLocation(player, sigilId);
                String nameColor = (location == BindsEditorHandler.SigilLocation.EQUIPPED ||
                                   location == BindsEditorHandler.SigilLocation.HOTBAR) ? "§a" : "§c";

                String ordinal = getOrdinal(i + 1);
                lore.add("§7" + ordinal + "§r&b. §r" + nameColor + sigilName);
            }
        }

        lore.add("§r&a&lLeft Click §r&b- §r&fModify.");
        lore.add("§r&c&lRight Click §r&b- §r&fClear.");

        // Set display name and lore
        String keyDisplay = isCommandMode ? String.valueOf(bindNumber) : keybindDisplay;
        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parseComponent("§f&lKey Bind #" + bindNumber + " §r&7(" + keyDisplay + ")"));

            List<Component> componentLore = new ArrayList<>();
            for (String line : lore) {
                componentLore.add(TextUtil.parseComponent(line));
            }
            meta.lore(componentLore);

            displayItem.setItemMeta(meta);
        }

        displayItem.setAmount(Math.min(bindNumber, 64));
        return displayItem;
    }

    /**
     * Create the toggle ability selection button.
     */
    public static ItemStack createToggleSelectionItem(ToggleHotkey selected) {
        List<String> lore = new ArrayList<>();
        lore.add("§r&3Select what hotkey you wish to use to toggle the ability selection UI");
        lore.add("§r&7");

        for (ToggleHotkey hotkey : ToggleHotkey.values()) {
            String prefix = (hotkey == selected) ? "§r&a" : "§r&7";
            lore.add(prefix + "- " + hotkey.getLoreFormat());
        }

        lore.add("");
        lore.add("§r&a&lClick §r&b- §r&fcycle hotkeys.");
        lore.add("§r&a&lShift Click §r&b- §r&fdisplay your bound hotkey in chat.");

        return ItemBuilder.createGuiItem(Material.COMPASS,
                "§r&fToggle Ability Selection UI",
                lore.toArray(new String[0]));
    }

    /**
     * Create the held slot bind button.
     */
    public static ItemStack createHeldSlotItem(HeldSlotHotkey selected) {
        List<String> lore = new ArrayList<>();
        lore.add("§r&3Due to Minecraft's limitations, selecting the ability by hotbar slot you are already holding");
        lore.add("§r&3has to be done with a specific hotkey. You can select that hotkey here.");
        lore.add("");
        lore.add("§r&c&oNote that some toggle ability selection UI hotkeys can conflict with these. Make sure");
        lore.add("§r&c&oto verify that your hotkeys work before you head into combat!");
        lore.add("§r&7");

        for (HeldSlotHotkey hotkey : HeldSlotHotkey.values()) {
            String prefix = (hotkey == selected) ? "§r&a" : "§r&7";
            lore.add(prefix + "- " + hotkey.getLoreFormat());
        }

        lore.add("");
        lore.add("§r&a&lClick §r&b- §r&fcycle hotkeys.");
        lore.add("§r&a&lShift Click §r&b- §r&fdisplay your bound hotkey in chat.");

        return ItemBuilder.createGuiItem(Material.IRON_SWORD,
                "§r&fHeld Slot Ability",
                lore.toArray(new String[0]));
    }

    /**
     * Create the information button.
     */
    public static ItemStack createInfoItem(boolean isHotbar) {
        List<String> lore = new ArrayList<>();
        lore.add("§r&3Abilities are powerful sigil effects that must be triggered manually. This menu allows you");
        lore.add("§r&3to select what abilities you want to use, so that you can quickly access them in combat.");
        lore.add("");
        lore.add("§r&7&oHow do I use an ability?");

        if (isHotbar) {
            lore.add("§r&7* §r&fOpen up the ability selection UI by pressing your selected hotkey. Manage said");
            lore.add("§r&7  hotkey at the Compass in the far left of this menu. Now with the ability selection UI open,");
            lore.add("§r&7  you can easily activate your abilities using your hotbar.");
            lore.add("");
            lore.add("§r&7&oWhy can I not use my ability?");
            lore.add("§r&7* §r&fWhen your ability is displayed in §r&cred text&r&f in the ability selection UI, it means that");
            lore.add("§r&7  the sigil is not equipped. To use an ability, the sigil must be equipped in your armor or your");
            lore.add("§r&7  hotbar.");
        } else {
            lore.add("§r&7* §r&fIn order for the commands system to work efficiently, a macro or auto command");
            lore.add("§r&7  keybind system is required. For those that wish to stay vanilla, the hotbar system is");
            lore.add("§r&7  recommended.");
        }

        return ItemBuilder.createGuiItem(Material.BOOK,
                "§r&fInformation",
                lore.toArray(new String[0]));
    }

    /**
     * Create the switch system button.
     */
    public static ItemStack createSwitchSystemItem(BindSystem currentSystem) {
        Material material = currentSystem == BindSystem.HOTBAR ? Material.BUNDLE : Material.COMMAND_BLOCK;
        String hotbarColor = currentSystem == BindSystem.HOTBAR ? "§r&a" : "§r&7";
        String commandColor = currentSystem == BindSystem.COMMAND ? "§r&a" : "§r&7";

        return ItemBuilder.createGuiItem(material,
                "§r&fSwitch Hotkey System",
                "§r&7",
                hotbarColor + "[HOTBAR]",
                commandColor + "[COMMAND]");
    }

    /**
     * Create the add bind button (Commands only).
     */
    public static ItemStack createAddBindItem() {
        return ItemBuilder.createGuiItem(Material.LIME_DYE,
                "§r&fAdd Bind",
                "§r&a&lLeft Click §r&b- §r&fAdd bind.");
    }

    /**
     * Create the delete bind button (Commands only).
     */
    public static ItemStack createDeleteBindItem() {
        return ItemBuilder.createGuiItem(Material.LAVA_BUCKET,
                "§r&fDelete Bind",
                "§r&a&lClick §r&b- §r&fDelete most recent bind");
    }

    /**
     * Create a page arrow button.
     */
    public static ItemStack createPageArrow(boolean isLeft, int currentPage, int totalPages) {
        String name = isLeft ? "§r&fPrevious Page" : "§r&fNext Page";
        return ItemBuilder.createGuiItem(Material.ARROW,
                name,
                "§r&3Page " + (currentPage + 1) + "/" + totalPages);
    }

    /**
     * Create a filler/underscore item.
     */
    public static ItemStack createFillerItem() {
        return ItemBuilder.createGuiItem(Material.GRAY_STAINED_GLASS_PANE,
                "§r&8Arcane Sigils");
    }

    /**
     * Create the back button for editor GUI.
     */
    public static ItemStack createBackItem() {
        return ItemBuilder.createGuiItem(Material.BARRIER,
                "§r&cBack to binds.");
    }

    /**
     * Get ordinal suffix (1st, 2nd, 3rd, etc.)
     */
    private static String getOrdinal(int n) {
        if (n >= 11 && n <= 13) {
            return n + "th";
        }
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }
}
