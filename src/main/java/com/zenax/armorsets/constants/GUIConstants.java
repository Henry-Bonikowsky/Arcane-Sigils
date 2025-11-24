package com.zenax.armorsets.constants;

/**
 * Constants for GUI dimensions, timing, and common slot positions.
 * These values ensure consistency across all inventory-based interfaces.
 */
public final class GUIConstants {

    private GUIConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ===== INVENTORY DIMENSIONS =====

    /**
     * Number of slots per inventory row.
     */
    public static final int ROW_SIZE = 9;

    /**
     * Minimum inventory size (3 rows).
     */
    public static final int MIN_INVENTORY_SIZE = 27;

    /**
     * Maximum inventory size (6 rows).
     */
    public static final int MAX_INVENTORY_SIZE = 54;

    /**
     * Standard menu size used for most GUIs.
     */
    public static final int STANDARD_MENU_SIZE = 54;

    // ===== CLICK HANDLING =====

    /**
     * Cooldown between click processing (in milliseconds).
     * Prevents accidental double-clicks.
     */
    public static final long CLICK_COOLDOWN_MS = 250L;

    // ===== PAGINATION =====

    /**
     * Number of content items displayed per page.
     * Based on 4 rows of 7 items (excluding borders).
     */
    public static final int ITEMS_PER_PAGE = 28;

    // ===== NAVIGATION SLOT POSITIONS =====
    // These are standard positions in a 54-slot (6 row) inventory

    /**
     * Back button slot position (bottom-left area).
     */
    public static final int SLOT_BACK = 45;

    /**
     * Previous page button slot position.
     */
    public static final int SLOT_PREV_PAGE = 48;

    /**
     * Information/status display slot (center bottom).
     */
    public static final int SLOT_INFO = 49;

    /**
     * Next page button slot position.
     */
    public static final int SLOT_NEXT_PAGE = 50;

    /**
     * Close button slot position (bottom-right corner).
     */
    public static final int SLOT_CLOSE = 53;

    // ===== MAIN MENU SLOT POSITIONS =====

    /**
     * Browse functions/sigils button slot.
     */
    public static final int SLOT_BROWSE_FUNCTIONS = 20;

    /**
     * Browse armor sets button slot.
     */
    public static final int SLOT_BROWSE_SETS = 22;

    /**
     * Socket sigil button slot.
     */
    public static final int SLOT_SOCKET = 24;

    /**
     * Unsocket sigil button slot.
     */
    public static final int SLOT_UNSOCKET = 29;

    /**
     * Armor info button slot.
     */
    public static final int SLOT_ARMOR_INFO = 31;

    /**
     * Help button slot.
     */
    public static final int SLOT_HELP = 33;

    // ===== CONTENT AREA SLOTS =====

    /**
     * Slot positions for content items in a paginated view.
     * 4 rows x 7 columns = 28 slots, avoiding borders.
     */
    public static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    // ===== SOCKET GUI SLOTS =====

    /**
     * Socket GUI: Armor piece display slot.
     */
    public static final int SOCKET_ARMOR_DISPLAY = 13;

    /**
     * Socket GUI: Current sigil or empty socket slot.
     */
    public static final int SOCKET_SIGIL_SLOT = 11;

    /**
     * Socket GUI: Action button slot.
     */
    public static final int SOCKET_ACTION_SLOT = 15;

    // ===== BUILD MENU SLOTS =====

    /**
     * Build menu: Create set button slot.
     */
    public static final int BUILD_CREATE_SET = 10;

    /**
     * Build menu: Create sigil button slot.
     */
    public static final int BUILD_CREATE_SIGIL = 12;

    /**
     * Build menu: Edit set button slot.
     */
    public static final int BUILD_EDIT_SET = 14;

    /**
     * Build menu: Edit sigil button slot.
     */
    public static final int BUILD_EDIT_SIGIL = 16;

    /**
     * Build menu: Close/back button slot.
     */
    public static final int BUILD_CLOSE = 26;
}
