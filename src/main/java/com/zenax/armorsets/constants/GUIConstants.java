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
     * Standardized for all GUIs.
     */
    public static final int SLOT_BACK = 45;

    /**
     * Confirm button slot position (bottom row, center-left).
     * Standardized as slot 47 (inventory.size() - 7).
     */
    public static final int SLOT_CONFIRM = 47;

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
     * Cancel button slot position (bottom row, center-right).
     * Standardized as slot 51 (inventory.size() - 3).
     */
    public static final int SLOT_CANCEL = 51;

    /**
     * Close button slot position (bottom-right corner).
     */
    public static final int SLOT_CLOSE = 53;

    /**
     * Help/Instructions item slot position (top row, center).
     * This is where contextual help should be displayed.
     */
    public static final int SLOT_HELP_INFO = 4;

    // ===== MAIN MENU SLOT POSITIONS =====

    /**
     * Browse sigils/sigils button slot.
     */
    public static final int SLOT_BROWSE_SIGILS = 20;

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

    // ===== SLOT SELECTOR SLOTS =====

    /**
     * Slot selector: Helmet option.
     */
    public static final int SELECTOR_HELMET = 10;

    /**
     * Slot selector: Chestplate option.
     */
    public static final int SELECTOR_CHESTPLATE = 12;

    /**
     * Slot selector: Leggings option.
     */
    public static final int SELECTOR_LEGGINGS = 14;

    /**
     * Slot selector: Boots option.
     */
    public static final int SELECTOR_BOOTS = 16;

    /**
     * Slot selector: Back/close button.
     */
    public static final int SELECTOR_BACK = 22;

    // ===== SET EDITOR SLOTS =====
    // Updated for 45-slot inventory with bottom row navigation

    /**
     * Set editor: View synergies button.
     */
    public static final int SET_EDITOR_SYNERGIES = 19;

    /**
     * Set editor: Rename set button.
     */
    public static final int SET_EDITOR_RENAME = 20;

    /**
     * Set editor: Create synergy button.
     */
    public static final int SET_EDITOR_CREATE_SYNERGY = 21;

    /**
     * Set editor: Edit synergy button.
     */
    public static final int SET_EDITOR_EDIT_SYNERGY = 22;

    /**
     * Set editor: Remove signal button.
     */
    public static final int SET_EDITOR_REMOVE_SIGNAL = 23;

    /**
     * Set editor: Export button.
     */
    public static final int SET_EDITOR_EXPORT = 25;

    /**
     * Set editor: Back button (bottom row).
     * 45-slot inventory: bottom-left = 45 - 9 = 36
     */
    public static final int SET_EDITOR_BACK = 36;

    // ===== SIGIL/SIGIL EDITOR SLOTS =====
    // 54-slot inventory layout

    /**
     * sigil editor: View effects button.
     */
    public static final int SIGIL_EDITOR_EFFECTS = 19;

    /**
     * sigil editor: Edit display button.
     */
    public static final int SIGIL_EDITOR_DISPLAY = 20;

    /**
     * sigil editor: Socketable items button.
     */
    public static final int SIGIL_EDITOR_SOCKETABLE_ITEMS = 21;

    /**
     * sigil editor: Edit signal button.
     */
    public static final int SIGIL_EDITOR_EDIT_SIGNAL = 23;

    /**
     * sigil editor: Add signal button.
     */
    public static final int SIGIL_EDITOR_ADD_SIGNAL = 24;

    /**
     * sigil editor: Remove signal button.
     */
    public static final int SIGIL_EDITOR_REMOVE_SIGNAL = 25;

    /**
     * sigil editor: Export button.
     */
    public static final int SIGIL_EDITOR_EXPORT = 40;

    /**
     * sigil editor: Exclusive toggle button.
     */
    public static final int SIGIL_EDITOR_EXCLUSIVE = 41;

    /**
     * sigil editor: Exclusive type toggle button (only visible when exclusive).
     */
    public static final int SIGIL_EDITOR_EXCLUSIVE_TYPE = 42;

    /**
     * sigil editor: Activation cooldown button (for ACTION-type exclusive sigils).
     */
    public static final int SIGIL_EDITOR_ACTIVATION_COOLDOWN = 23;

    /**
     * sigil editor: Back button (bottom row).
     * 54-slot inventory: bottom-left = 54 - 9 = 45
     */
    public static final int SIGIL_EDITOR_BACK = 45;

    // ===== SYNERGY EDITOR SLOTS =====

    /**
     * Synergy editor: Select signal button.
     */
    public static final int SYNERGY_EDITOR_SIGNAL = 10;

    /**
     * Synergy editor: Add effect button.
     */
    public static final int SYNERGY_EDITOR_EFFECT = 12;

    /**
     * Synergy editor: Set chance button.
     */
    public static final int SYNERGY_EDITOR_CHANCE = 14;

    /**
     * Synergy editor: Set cooldown button.
     */
    public static final int SYNERGY_EDITOR_COOLDOWN = 16;

    /**
     * Synergy editor: Save button.
     */
    public static final int SYNERGY_EDITOR_SAVE = 31;

    /**
     * Synergy editor: Cancel button.
     */
    public static final int SYNERGY_EDITOR_CANCEL = 33;

    // ===== ITEM DISPLAY EDITOR SLOTS =====

    /**
     * Item display editor: Select material button.
     */
    public static final int DISPLAY_EDITOR_MATERIAL = 11;

    /**
     * Item display editor: Rename button.
     */
    public static final int DISPLAY_EDITOR_RENAME = 13;

    /**
     * Item display editor: Edit description button.
     */
    public static final int DISPLAY_EDITOR_DESCRIPTION = 15;

    /**
     * Item display editor: Back button (bottom row).
     * 36-slot inventory: bottom-left = 36 - 9 = 27
     */
    public static final int DISPLAY_EDITOR_BACK = 27;

    // ===== CONFIRMATION DIALOG SLOTS =====

    /**
     * Confirmation: Confirm button.
     */
    public static final int CONFIRMATION_CONFIRM = 11;

    /**
     * Confirmation: Cancel button.
     */
    public static final int CONFIRMATION_CANCEL = 15;

    // ===== CONDITION VIEWER SLOTS =====

    /**
     * Condition viewer: Toggle logic mode (AND/OR).
     */
    public static final int CONDITION_VIEWER_LOGIC_TOGGLE = 13;

    /**
     * Condition viewer: Save as preset button.
     */
    public static final int CONDITION_VIEWER_SAVE_PRESET = 23;

    /**
     * Condition viewer: Template selector button.
     */
    public static final int CONDITION_VIEWER_TEMPLATES = 25;

    /**
     * Condition viewer: Add condition button.
     */
    public static final int CONDITION_VIEWER_ADD = 27;

    /**
     * Condition viewer: Load preset button.
     */
    public static final int CONDITION_VIEWER_LOAD_PRESET = 29;

    /**
     * Condition viewer: Remove all conditions button.
     */
    public static final int CONDITION_VIEWER_REMOVE_ALL = 31;

    /**
     * Condition viewer: Back button.
     * 36-slot inventory: bottom-left = 36 - 9 = 27
     */
    public static final int CONDITION_VIEWER_BACK = 27;

    /**
     * Condition viewer: First condition slot.
     * Conditions occupy slots 9-26 (18 slots).
     */
    public static final int CONDITION_VIEWER_FIRST_SLOT = 9;

    /**
     * Condition viewer: Last condition slot.
     */
    public static final int CONDITION_VIEWER_LAST_SLOT = 26;

    // ===== CONDITION CATEGORY SELECTOR SLOTS =====

    /**
     * Condition category: Health category button.
     */
    public static final int CONDITION_CATEGORY_HEALTH = 10;

    /**
     * Condition category: Potion category button.
     */
    public static final int CONDITION_CATEGORY_POTION = 12;

    /**
     * Condition category: Environmental category button.
     */
    public static final int CONDITION_CATEGORY_ENVIRONMENTAL = 14;

    /**
     * Condition category: Combat category button.
     */
    public static final int CONDITION_CATEGORY_COMBAT = 16;

    /**
     * Condition category: Meta category button.
     */
    public static final int CONDITION_CATEGORY_META = 18;

    /**
     * Condition category: Back button.
     * 27-slot inventory: bottom-left = 27 - 9 = 18
     */
    public static final int CONDITION_CATEGORY_BACK = 18;

    // ===== CONDITION PARAMETER EDITOR SLOTS =====

    /**
     * Condition parameter editor: Comparison operator toggle.
     */
    public static final int CONDITION_PARAM_COMPARISON = 11;

    /**
     * Condition parameter editor: Decrease value by 10.
     */
    public static final int CONDITION_PARAM_VALUE_MINUS_10 = 19;

    /**
     * Condition parameter editor: Decrease value by 1.
     */
    public static final int CONDITION_PARAM_VALUE_MINUS_1 = 20;

    /**
     * Condition parameter editor: Increase value by 1.
     */
    public static final int CONDITION_PARAM_VALUE_PLUS_1 = 24;

    /**
     * Condition parameter editor: Increase value by 10.
     */
    public static final int CONDITION_PARAM_VALUE_PLUS_10 = 25;

    /**
     * Condition parameter editor: Save button.
     */
    public static final int CONDITION_PARAM_SAVE = 30;

    /**
     * Condition parameter editor: Cancel button.
     */
    public static final int CONDITION_PARAM_CANCEL = 32;

    // ===== CONDITION TEMPLATE SELECTOR SLOTS =====

    /**
     * Condition template selector: First template slot.
     * Templates occupy slots 10-17 (8 templates).
     */
    public static final int CONDITION_TEMPLATE_FIRST_SLOT = 10;

    /**
     * Condition template selector: Last template slot.
     */
    public static final int CONDITION_TEMPLATE_LAST_SLOT = 17;

    /**
     * Condition template selector: Back button.
     * 36-slot inventory: bottom-left = 36 - 9 = 27
     */
    public static final int CONDITION_TEMPLATE_BACK = 27;

    // ===== EFFECT VIEWER SLOTS =====

    /**
     * Effect viewer: Back button.
     * 45-slot inventory: bottom-left = 45 - 9 = 36
     */
    public static final int EFFECT_VIEWER_BACK = 36;

    // ===== EFFECT VALUE CONFIG SLOTS =====

    /**
     * Effect value config: Decrease value by 10.
     */
    public static final int EFFECT_VALUE_MINUS_10 = 19;

    /**
     * Effect value config: Decrease value by 1.
     */
    public static final int EFFECT_VALUE_MINUS_1 = 20;

    /**
     * Effect value config: Increase value by 1.
     */
    public static final int EFFECT_VALUE_PLUS_1 = 24;

    /**
     * Effect value config: Increase value by 10.
     */
    public static final int EFFECT_VALUE_PLUS_10 = 25;

    /**
     * Effect value config: Target @Self button.
     */
    public static final int EFFECT_VALUE_TARGET_SELF = 30;

    /**
     * Effect value config: Target @Victim button.
     */
    public static final int EFFECT_VALUE_TARGET_VICTIM = 31;

    /**
     * Effect value config: Target @Nearby button.
     */
    public static final int EFFECT_VALUE_TARGET_NEARBY = 32;

    /**
     * Effect value config: Decrease radius by 1.
     */
    public static final int EFFECT_VALUE_RADIUS_MINUS = 33;

    /**
     * Effect value config: Increase radius by 1.
     */
    public static final int EFFECT_VALUE_RADIUS_PLUS = 35;

    /**
     * Effect value config: Confirm button.
     */
    public static final int EFFECT_VALUE_CONFIRM = 39;

    /**
     * Effect value config: Back button.
     */
    public static final int EFFECT_VALUE_BACK = 41;

    // ===== TIER CONFIG SLOTS =====

    /**
     * Tier config: Scaling mode toggle (PARAMETER/ACTIVATION_ONLY).
     */
    public static final int TIER_CONFIG_MODE = 10;

    /**
     * Tier config: Power range adjustment input.
     */
    public static final int TIER_CONFIG_POWER_RANGE = 12;

    /**
     * Tier config: Curve steepness adjustment input.
     */
    public static final int TIER_CONFIG_CURVE = 14;

    /**
     * Tier config: Open parameter overrides.
     */
    public static final int TIER_CONFIG_PARAMS = 16;

    /**
     * Tier config: XP enabled toggle.
     */
    public static final int TIER_CONFIG_XP_TOGGLE = 28;

    /**
     * Tier config: Open XP config.
     */
    public static final int TIER_CONFIG_XP_SETTINGS = 30;

    /**
     * Tier config: Back button.
     */
    public static final int TIER_CONFIG_BACK = 36;

    /**
     * Tier config: Save button.
     */
    public static final int TIER_CONFIG_SAVE = 40;

    /**
     * Tier config: Scaling preview display (shows multipliers at different tiers).
     */
    public static final int TIER_CONFIG_PREVIEW = 22;

    /**
     * Tier config: Max tier setting for the sigil.
     */
    public static final int TIER_CONFIG_MAX_TIER = 20;

    // ===== TIER XP CONFIG SLOTS =====

    /**
     * Tier XP config: XP gain per activation input.
     */
    public static final int TIER_XP_GAIN = 10;

    /**
     * Tier XP config: Curve type toggle (LINEAR/EXPONENTIAL/CUSTOM).
     */
    public static final int TIER_XP_CURVE = 12;

    /**
     * Tier XP config: Base XP input.
     */
    public static final int TIER_XP_BASE = 14;

    /**
     * Tier XP config: Growth rate input.
     */
    public static final int TIER_XP_GROWTH = 16;

    /**
     * Tier XP config: Back button.
     */
    public static final int TIER_XP_BACK = 36;

    /**
     * Tier XP config: Save button.
     */
    public static final int TIER_XP_SAVE = 40;

    // ===== TIER PROGRESS VIEWER SLOTS =====

    /**
     * Tier progress viewer: First sigil slot.
     * Sigils occupy slots 10-16 (7 slots for equipped armor sigils).
     */
    public static final int TIER_PROGRESS_FIRST_SLOT = 10;

    /**
     * Tier progress viewer: Last sigil slot.
     */
    public static final int TIER_PROGRESS_LAST_SLOT = 16;

    /**
     * Tier progress viewer: Close button.
     */
    public static final int TIER_PROGRESS_CLOSE = 40;

    // ===== TIER PARAM CONFIG SLOTS (54-slot GUI) =====

    /**
     * Tier param config: Category filter row start.
     */
    public static final int TIER_PARAM_CAT_START = 10;

    /**
     * Tier param config: First parameter content slot (row 3).
     */
    public static final int TIER_PARAM_CONTENT_START = 18;

    /**
     * Tier param config: Last parameter content slot (row 5, excluding right edge).
     */
    public static final int TIER_PARAM_CONTENT_END = 43;

    /**
     * Tier param config: Back button.
     */
    public static final int TIER_PARAM_BACK = 45;

    /**
     * Tier param config: Previous page button.
     */
    public static final int TIER_PARAM_PREV = 46;

    /**
     * Tier param config: Apply preset button.
     */
    public static final int TIER_PARAM_PRESET = 47;

    /**
     * Tier param config: Save configuration button.
     */
    public static final int TIER_PARAM_SAVE = 48;

    /**
     * Tier param config: Info/help button.
     */
    public static final int TIER_PARAM_INFO = 49;

    /**
     * Tier param config: Load preset button.
     */
    public static final int TIER_PARAM_LOAD = 50;

    /**
     * Tier param config: Next page button.
     */
    public static final int TIER_PARAM_NEXT = 52;

    // ===== TIER PARAM EDITOR SLOTS (45-slot GUI) =====

    /**
     * Tier param editor: Parameter name display.
     */
    public static final int TIER_PARAM_EDIT_NAME = 4;

    /**
     * Tier param editor: Enable/disable toggle.
     */
    public static final int TIER_PARAM_EDIT_ENABLE = 10;

    /**
     * Tier param editor: Weight input.
     */
    public static final int TIER_PARAM_EDIT_WEIGHT = 19;

    /**
     * Tier param editor: Minimum value input.
     */
    public static final int TIER_PARAM_EDIT_MIN = 21;

    /**
     * Tier param editor: Maximum value input.
     */
    public static final int TIER_PARAM_EDIT_MAX = 23;

    /**
     * Tier param editor: Curve type cycle.
     */
    public static final int TIER_PARAM_EDIT_CURVE = 25;

    /**
     * Tier param editor: Preview scaled values.
     */
    public static final int TIER_PARAM_EDIT_PREVIEW = 31;

    /**
     * Tier param editor: Back button.
     */
    public static final int TIER_PARAM_EDIT_BACK = 36;

    /**
     * Tier param editor: Reset to defaults.
     */
    public static final int TIER_PARAM_EDIT_RESET = 40;

    /**
     * Tier param editor: Save changes.
     */
    public static final int TIER_PARAM_EDIT_SAVE = 44;

    // ===== TIER PARAM PRESET SELECTOR SLOTS (45-slot GUI) =====

    /**
     * Tier param preset selector: Title/info display.
     */
    public static final int TIER_PRESET_TITLE = 4;

    /**
     * Tier param preset selector: Preset slots (7 presets in row 1).
     * Slots 10-16 for the 7 built-in presets.
     */
    public static final int TIER_PRESET_SLOT_START = 10;
    public static final int TIER_PRESET_SLOT_END = 16;

    /**
     * Tier param preset selector: Info/help button.
     */
    public static final int TIER_PRESET_INFO = 31;

    /**
     * Tier param preset selector: Back button.
     */
    public static final int TIER_PRESET_BACK = 36;

    // ===== MATERIAL SELECTOR SLOTS =====

    /**
     * Material selector: Back button.
     */
    public static final int MATERIAL_SELECTOR_BACK = 49;

    /**
     * Material selector: First content slot.
     * Materials occupy slots 0-44 (45 slots, 5 rows).
     */
    public static final int MATERIAL_SELECTOR_FIRST_SLOT = 0;

    /**
     * Material selector: Last content slot.
     */
    public static final int MATERIAL_SELECTOR_LAST_SLOT = 44;

    // ===== UTILITY METHODS =====

    /**
     * Get the back button slot position for a given inventory size.
     * Typically bottom-left area (size - 9 for last row start).
     *
     * @param inventorySize The total inventory size
     * @return The back button slot position
     */
    public static int getBackSlot(int inventorySize) {
        return inventorySize - 9;  // First slot of last row
    }

    /**
     * Get the confirm button slot position for a given inventory size.
     * Standardized as (size - 7).
     *
     * @param inventorySize The total inventory size
     * @return The confirm button slot position
     */
    public static int getConfirmSlot(int inventorySize) {
        return inventorySize - 7;
    }

    /**
     * Get the cancel button slot position for a given inventory size.
     * Standardized as (size - 3).
     *
     * @param inventorySize The total inventory size
     * @return The cancel button slot position
     */
    public static int getCancelSlot(int inventorySize) {
        return inventorySize - 3;
    }

    /**
     * Get the close button slot position for a given inventory size.
     * Typically bottom-right corner (size - 1).
     *
     * @param inventorySize The total inventory size
     * @return The close button slot position
     */
    public static int getCloseSlot(int inventorySize) {
        return inventorySize - 1;
    }
}
