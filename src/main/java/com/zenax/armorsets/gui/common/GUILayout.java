package com.zenax.armorsets.gui.common;

/**
 * Constants for GUI slot positions and layouts.
 */
public final class GUILayout {

    private GUILayout() {}

    // Inventory sizes
    public static final int ROWS_3 = 27;
    public static final int ROWS_5 = 45;
    public static final int ROWS_6 = 54;

    // Bottom row positions (row 2 in 3-row GUI, slots 18-26)
    public static final int BACK = 18;
    public static final int PREV_PAGE = 19;
    public static final int PAGE_INDICATOR = 22;
    /**
     * @deprecated Use {@link Browser#NEXT_PAGE} instead. Browser uses slot 26, not 25.
     */
    @Deprecated
    public static final int NEXT_PAGE = 25;

    /**
     * Constants for paginated browser GUIs (3 rows, 18 items per page).
     * Used by AbstractBrowserHandler and similar paginated handlers.
     */
    public static final class Browser {
        private Browser() {}

        /** 18 item slots (rows 0-1) */
        public static final int[] ITEM_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17
        };

        /** Number of items per page */
        public static final int ITEMS_PER_PAGE = 18;

        // Bottom row navigation
        public static final int BACK = 18;
        public static final int PREV_PAGE = 19;
        public static final int PAGE_INDICATOR = 22;
        public static final int NEXT_PAGE = 26;

        /** Decoration slots (bottom row filler between buttons) */
        public static final int[] DECORATION_SLOTS = {20, 21, 23, 24, 25};

        /**
         * Get item index from slot position.
         * @return Index 0-17, or -1 if not an item slot
         */
        public static int getItemIndex(int slot) {
            if (slot >= 0 && slot < ITEMS_PER_PAGE) {
                return slot;
            }
            return -1;
        }

        /**
         * Check if slot is an item slot (0-17).
         */
        public static boolean isItemSlot(int slot) {
            return slot >= 0 && slot < ITEMS_PER_PAGE;
        }
    }

    // Sigils Menu specific (bottom row)
    public static final int BROWSE_BEHAVIORS = 21;
    public static final int CREATE_BEHAVIOR = 23;
    public static final int CREATE_SIGIL = 24;
    public static final int FILTER = 26;

    // Edit Sigil specific (top 2 rows)
    public static final int DESCRIPTION = 3;
    public static final int PREVIEW = 4;
    public static final int RENAME = 5;
    public static final int SIGIL_CONFIG = 11;
    public static final int SIGNALS_ABILITIES = 15;
    public static final int SAVE = 19;
    public static final int FILENAME = 20;
    public static final int EXCLUSIVITY_TYPE = 25;
    public static final int EXCLUSIVITY = 26;

    // Signal/Effect Config specific
    public static final int CLEAR = 0;
    public static final int DELETE_MODE = 21;
    public static final int ADD = 22;

    // List slots in middle row (7 slots)
    public static final int[] LIST_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    // Sigil Config specific
    public static final int ITEM_DISPLAY = 10;
    public static final int SOCKETABLE_ITEMS = 13;
    public static final int TIER_CONFIG = 16;

    // Item Selector slots (7 items in middle row)
    public static final int[] ITEM_SELECTOR_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    // Socketable Selector positions (armor + weapons)
    public static final int HELMET = 10;
    public static final int CHESTPLATE = 11;
    public static final int LEGGINGS = 12;
    public static final int BOOTS = 13;
    public static final int SWORD = 14;
    public static final int AXE = 15;
    public static final int BOW = 16;
    public static final int PICKAXE = 22;

    /**
     * Check if a slot is in the list slots array.
     */
    public static boolean isListSlot(int slot) {
        for (int listSlot : LIST_SLOTS) {
            if (slot == listSlot) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the list index for a slot (0-6), or -1 if not a list slot.
     */
    public static int getListIndex(int slot) {
        for (int i = 0; i < LIST_SLOTS.length; i++) {
            if (LIST_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Check if a slot is in the item selector slots array.
     */
    public static boolean isItemSelectorSlot(int slot) {
        for (int selectorSlot : ITEM_SELECTOR_SLOTS) {
            if (slot == selectorSlot) {
                return true;
            }
        }
        return false;
    }
}
