package com.miracle.arcanesigils.constants;

/**
 * Constants for player inventory slot positions and armor slot indices.
 * These values map to Bukkit's internal slot numbering system.
 */
public final class SlotPositions {

    private SlotPositions() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ===== ARMOR SLOTS (Player Inventory) =====
    // These are the raw slot numbers in the player's inventory

    /**
     * Helmet slot in player inventory.
     */
    public static final int HELMET_SLOT = 39;

    /**
     * Chestplate slot in player inventory.
     */
    public static final int CHESTPLATE_SLOT = 38;

    /**
     * Leggings slot in player inventory.
     */
    public static final int LEGGINGS_SLOT = 37;

    /**
     * Boots slot in player inventory.
     */
    public static final int BOOTS_SLOT = 36;

    // ===== ARMOR CONTENTS ARRAY INDICES =====
    // Used with player.getInventory().getArmorContents()
    // Index 0 = boots, 1 = leggings, 2 = chestplate, 3 = helmet

    /**
     * Boots index in armor contents array.
     */
    public static final int ARMOR_BOOTS_INDEX = 0;

    /**
     * Leggings index in armor contents array.
     */
    public static final int ARMOR_LEGGINGS_INDEX = 1;

    /**
     * Chestplate index in armor contents array.
     */
    public static final int ARMOR_CHESTPLATE_INDEX = 2;

    /**
     * Helmet index in armor contents array.
     */
    public static final int ARMOR_HELMET_INDEX = 3;

    /**
     * Number of armor slots.
     */
    public static final int ARMOR_SLOT_COUNT = 4;

    // ===== HOTBAR SLOTS =====

    /**
     * First hotbar slot.
     */
    public static final int HOTBAR_START = 0;

    /**
     * Last hotbar slot.
     */
    public static final int HOTBAR_END = 8;

    /**
     * Number of hotbar slots.
     */
    public static final int HOTBAR_SIZE = 9;

    // ===== GUI ARMOR DISPLAY SLOTS =====
    // Standard positions for displaying armor pieces in GUIs

    /**
     * GUI slot for boots display.
     */
    public static final int GUI_BOOTS_DISPLAY = 29;

    /**
     * GUI slot for leggings display.
     */
    public static final int GUI_LEGGINGS_DISPLAY = 20;

    /**
     * GUI slot for chestplate display.
     */
    public static final int GUI_CHESTPLATE_DISPLAY = 22;

    /**
     * GUI slot for helmet display.
     */
    public static final int GUI_HELMET_DISPLAY = 24;

    /**
     * Array of GUI armor display slots in order: boots, leggings, chestplate, helmet.
     */
    public static final int[] GUI_ARMOR_SLOTS = {
        GUI_BOOTS_DISPLAY,
        GUI_LEGGINGS_DISPLAY,
        GUI_CHESTPLATE_DISPLAY,
        GUI_HELMET_DISPLAY
    };

    // ===== SLOT NAME MAPPINGS =====

    /**
     * Human-readable names for armor slots.
     * Index matches ARMOR_*_INDEX constants.
     */
    public static final String[] ARMOR_SLOT_NAMES = {
        "Boots", "Leggings", "Chestplate", "Helmet"
    };

    // ===== UTILITY METHODS =====

    /**
     * Get the slot name for an armor contents array index.
     * @param index The index (0-3)
     * @return The slot name or "Unknown" if invalid
     */
    public static String getSlotName(int index) {
        if (index >= 0 && index < ARMOR_SLOT_NAMES.length) {
            return ARMOR_SLOT_NAMES[index];
        }
        return "Unknown";
    }

    /**
     * Get the config key for an armor contents array index.
     * @param index The index (0-3)
     * @return The lowercase slot key (boots, leggings, chestplate, helmet)
     */
    public static String getSlotKey(int index) {
        return switch (index) {
            case ARMOR_BOOTS_INDEX -> "boots";
            case ARMOR_LEGGINGS_INDEX -> "leggings";
            case ARMOR_CHESTPLATE_INDEX -> "chestplate";
            case ARMOR_HELMET_INDEX -> "helmet";
            default -> null;
        };
    }

    /**
     * Check if a slot number is an armor slot in the player inventory.
     * @param slot The slot number
     * @return true if it's an armor slot
     */
    public static boolean isArmorSlot(int slot) {
        return slot >= BOOTS_SLOT && slot <= HELMET_SLOT;
    }

    /**
     * Check if a slot number is in the hotbar.
     * @param slot The slot number
     * @return true if it's a hotbar slot
     */
    public static boolean isHotbarSlot(int slot) {
        return slot >= HOTBAR_START && slot <= HOTBAR_END;
    }
}
