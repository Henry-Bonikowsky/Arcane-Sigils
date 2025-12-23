# UI/UX Improvements Implementation Guide

This document describes the UI/UX improvements implemented to address the issues from `Improvements.md` Section 5.

## Summary of Changes

### Completed Enhancements

#### 1. Standardized Slot Positions (Issue 5.1)

**Problem**: Different GUIs used inconsistent slot positions for similar functions (back, confirm, cancel buttons).

**Solution**: Added standardized constants to `GUIConstants.java`:

```java
// Standardized navigation positions for 54-slot inventories
public static final int SLOT_BACK = 45;      // Bottom-left (inventory.size() - 9)
public static final int SLOT_CONFIRM = 47;   // Bottom row, center-left (size - 7)
public static final int SLOT_CANCEL = 51;    // Bottom row, center-right (size - 3)
public static final int SLOT_CLOSE = 53;     // Bottom-right corner (size - 1)
public static final int SLOT_HELP_INFO = 4;  // Top row, center (for help items)

// Utility methods for variable-sized inventories
public static int getBackSlot(int inventorySize) { return inventorySize - 9; }
public static int getConfirmSlot(int inventorySize) { return inventorySize - 7; }
public static int getCancelSlot(int inventorySize) { return inventorySize - 3; }
public static int getCloseSlot(int inventorySize) { return inventorySize - 1; }
```

**Files Modified**:
- `src/main/java/com/zenax/armorsets/constants/GUIConstants.java`

**Benefits**:
- Consistent button placement across all GUIs
- Easier for users to learn GUI navigation
- Muscle memory works across different screens

---

#### 2. Visual Feedback for Current Selection (Issue 5.2)

**Problem**: No visual indication of which item is currently selected in configuration GUIs.

**Solution**: Added glowing effect methods to `ItemBuilder.java`:

```java
/**
 * Add a glowing enchantment effect to an item to indicate selection.
 * Uses UNBREAKING enchantment with HIDE_ENCHANTS flag.
 */
public static ItemStack addGlowEffect(ItemStack item)

/**
 * Create a GUI item with visual selection indicator (glowing effect).
 */
public static ItemStack createSelectableItem(Material material, String name, boolean selected, String... loreLines)
```

**Usage Example**:
```java
// Show @Self as selected, @Victim as not selected
inv.setItem(30, ItemBuilder.createSelectableItem(Material.PLAYER_HEAD, "&a@Self", true, "&7Apply to yourself"));
inv.setItem(31, ItemBuilder.createSelectableItem(Material.ZOMBIE_HEAD, "&c@Victim", false, "&7Apply to target"));
```

**Files Modified**:
- `src/main/java/com/zenax/armorsets/gui/common/ItemBuilder.java`

**Benefits**:
- Clear visual feedback for selected options
- Uses vanilla enchantment glow (familiar to players)
- No enchantment tooltip clutter (uses ItemFlag.HIDE_ENCHANTS)

---

#### 3. Loading/Processing Indicators (Issue 5.3)

**Problem**: No feedback during operations that take time (file saves, reloads, exports).

**Solution**: Added action bar message support to `GUIManager.java`:

```java
/**
 * Send an action bar message to the player.
 * Useful for loading indicators and non-intrusive feedback.
 */
public void sendActionBar(Player player, String message) {
    player.sendActionBar(TextUtil.parseComponent(message));
}
```

**Usage Example**:
```java
sendActionBar(player, "&eExporting set configuration...");
// ... perform export operation ...
sendActionBar(player, "&aExport complete!");
```

**Files Modified**:
- `src/main/java/com/zenax/armorsets/gui/GUIManager.java`

**Benefits**:
- Non-intrusive feedback (doesn't clutter chat)
- Real-time status updates
- Clear indication of ongoing operations

---

#### 4. Additional Helper Methods

Added to `ItemBuilder.java`:

```java
/**
 * Create a help/instruction item with paper icon.
 */
public static ItemStack createHelpItem(String title, String... loreLines)

/**
 * Create a loading indicator item.
 */
public static ItemStack createLoadingItem(String message)
```

**Files Modified**:
- `src/main/java/com/zenax/armorsets/gui/common/ItemBuilder.java`

---

## Implementation Guide for Remaining Issues

### Issue 5.4: Improved Error Messages

**Current State**: Error messages are cryptic and don't provide actionable guidance.

**Recommended Changes**:

#### Before:
```java
player.sendMessage(TextUtil.colorize("&cError: Set not found in session"));
```

#### After:
```java
player.sendMessage(TextUtil.colorize("&cError: Set data not found"));
player.sendMessage(TextUtil.colorize("&7This usually means the set was deleted or unloaded."));
player.sendMessage(TextUtil.colorize("&7Try: &f/as reload &7then reopen the GUI"));
```

**Implementation Pattern**:
```java
private void sendErrorWithHelp(Player player, String error, String explanation, String solution) {
    player.sendMessage(TextUtil.colorize("&c" + error));
    if (explanation != null) {
        player.sendMessage(TextUtil.colorize("&7" + explanation));
    }
    if (solution != null) {
        player.sendMessage(TextUtil.colorize("&7Tip: &f" + solution));
    }
    sendActionBar(player, "&c" + error);
}
```

**Files to Update**:
- `src/main/java/com/zenax/armorsets/gui/handlers/ConfigHandler.java` (all error messages)
- `src/main/java/com/zenax/armorsets/gui/GUIManager.java` (export methods, input handlers)

---

### Issue 5.5: In-GUI Instructions (Not Just Chat)

**Current State**: Instructions are sent to chat after GUI opens, which can scroll away.

**Recommended Changes**:

Add help items to each GUI using the new `SLOT_HELP_INFO` constant (slot 4):

```java
// In openTriggerConfig method:
ItemStack helpItem = ItemBuilder.createHelpItem(
    "How to Configure",
    "&7Click the glass panes to adjust chance/cooldown",
    "&7Green increases, red decreases",
    "&7Click 'Conditions' to add requirements",
    "&7Click 'Confirm' when ready"
);
inv.setItem(GUIConstants.SLOT_HELP_INFO, helpItem);
```

**GUIs That Need Help Items**:
1. Trigger Config GUI - How to adjust chance/cooldown/conditions
2. Effect Value Config GUI - How to set effect parameters
3. Condition Category Selector - What each category means
4. Synergy Creator - Steps to create a synergy
5. Sigil Creator - Steps to create a sigil
6. Material Selector - How to select and confirm

**Files to Update**:
- `src/main/java/com/zenax/armorsets/gui/GUIManager.java` (all `open*` methods)

---

### Issue 5.6: Better Tooltips/Help System

**Current State**: Some buttons lack descriptive lore.

**Recommended Changes**:

Audit all `ItemBuilder.createGuiItem()` calls and ensure they have:
1. Clear, action-oriented display name
2. At least one lore line explaining what happens
3. Keyboard shortcuts if applicable (e.g., "Shift-click to...")

**Audit Checklist**:
```java
// Bad (no lore)
inv.setItem(10, ItemBuilder.createGuiItem(Material.NETHER_STAR, "&bView Synergies", ""));

// Good (descriptive lore)
inv.setItem(10, ItemBuilder.createGuiItem(Material.NETHER_STAR, "&bView Synergies",
    "&7View all set bonuses",
    "&7Shows triggers and effects",
    "&7Click to open viewer"));
```

**Files to Audit**:
- `src/main/java/com/zenax/armorsets/gui/GUIManager.java` (all GUI creation methods)
- Check every `ItemBuilder.createGuiItem()` call

---

## Usage Guide for Developers

### Adding a New GUI

```java
public void openMyNewGUI(Player player) {
    int size = 45; // or 27, 36, 54
    Inventory inv = Bukkit.createInventory(null, size, TextUtil.parseComponent("&8My GUI"));

    // Add help item at slot 4
    ItemStack helpItem = ItemBuilder.createHelpItem(
        "Instructions",
        "&7This GUI does XYZ",
        "&7Click buttons to...",
        "&7Press back to return"
    );
    inv.setItem(GUIConstants.SLOT_HELP_INFO, helpItem);

    // Use standardized positions
    int backSlot = GUIConstants.getBackSlot(size);
    int confirmSlot = GUIConstants.getConfirmSlot(size);
    int cancelSlot = GUIConstants.getCancelSlot(size);

    inv.setItem(backSlot, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack", "&7Return to previous menu"));
    inv.setItem(confirmSlot, ItemBuilder.createConfirmButton("Confirm"));
    inv.setItem(cancelSlot, ItemBuilder.createCancelButton("Cancel"));

    // Show selection with glow
    boolean isSelected = true;
    inv.setItem(20, ItemBuilder.createSelectableItem(Material.DIAMOND, "Option A", isSelected, "&7This is selected"));
    inv.setItem(21, ItemBuilder.createSelectableItem(Material.EMERALD, "Option B", !isSelected, "&7Not selected"));

    GUISession session = new GUISession(GUIType.MY_GUI);
    openGUI(player, inv, session);

    // Show action bar for context
    sendActionBar(player, "&eConfiguring new feature...");
}
```

### Handling Long Operations

```java
public void performLongOperation(Player player) {
    // Show start
    sendActionBar(player, "&eProcessing...");
    player.sendMessage(TextUtil.colorize("&7Starting operation..."));

    try {
        // Do work
        someLongOperation();

        // Show success
        sendActionBar(player, "&aComplete!");
        player.sendMessage(TextUtil.colorize("&aOperation successful!"));
        playSound(player, "socket");

    } catch (Exception e) {
        // Show error with helpful message
        sendActionBar(player, "&cFailed!");
        player.sendMessage(TextUtil.colorize("&cOperation failed: " + e.getMessage()));
        player.sendMessage(TextUtil.colorize("&7Please check logs and try again"));
        playSound(player, "error");
    }
}
```

---

## Testing Checklist

### Visual Feedback Testing
- [ ] Selected items show enchantment glow
- [ ] Glow disappears when item is deselected
- [ ] Glow is visible in all GUI backgrounds

### Slot Position Testing
For each GUI type:
- [ ] Back button is at standardized position
- [ ] Confirm/Cancel buttons are at standardized positions
- [ ] Buttons don't overlap with content
- [ ] Positions work in different inventory sizes (27, 36, 45, 54)

### Action Bar Testing
- [ ] Action bar messages appear during exports
- [ ] Action bar messages appear during saves
- [ ] Messages don't spam (proper duration)
- [ ] Messages use appropriate colors (yellow for processing, green for success, red for errors)

### Error Message Testing
- [ ] Error messages include explanation
- [ ] Error messages suggest solutions
- [ ] Action bar shows error summary
- [ ] Sound plays on error

### Help Item Testing
- [ ] Help items appear at slot 4 in all major GUIs
- [ ] Help text is clear and actionable
- [ ] Help items don't block important controls

---

## Architectural Notes

### Why These Positions?

**Bottom Row Layout (54-slot inventory)**:
```
[45] Back    [46]      [47] Confirm  [48]      [49] Info   [50]      [51] Cancel   [52]      [53] Close
```

- **Slot 45 (Back)**: Far left, natural "go back" position
- **Slot 47 (Confirm)**: Left of center, primary action
- **Slot 49 (Info)**: Dead center, status display
- **Slot 51 (Cancel)**: Right of center, secondary/destructive action
- **Slot 53 (Close)**: Far right, alternative to Escape key

### Why Enchantment Glow for Selection?

1. **Vanilla Familiarity**: Players recognize enchanted items as "special"
2. **No Tooltip Pollution**: Using ItemFlag.HIDE_ENCHANTS means no enchantment description
3. **Performance**: No particle effects or animations needed
4. **Universality**: Works in all GUI contexts

### Why Action Bar for Processing Messages?

1. **Non-Intrusive**: Doesn't push up chat history
2. **Always Visible**: Appears over hotbar, hard to miss
3. **Temporary**: Fades automatically, no manual clearing
4. **Contextual**: Player sees it while interacting with GUI

---

## Future Enhancements

### Possible Additions (Not in Scope)

1. **Progress Bars**: Use boss bars for long operations
2. **Confirmation Dialogs**: "Are you sure?" prompts for destructive actions
3. **Undo/Redo**: History stack for configuration changes
4. **Search/Filter**: GUI-based search for browsers
5. **Pagination**: Better handling of large lists

---

## Files Modified Summary

### Core Files
- `src/main/java/com/zenax/armorsets/constants/GUIConstants.java` - Added standardized slots and utility methods
- `src/main/java/com/zenax/armorsets/gui/common/ItemBuilder.java` - Added selection glow and helper methods
- `src/main/java/com/zenax/armorsets/gui/GUIManager.java` - Added action bar support

### Files Needing Updates (Recommended)
- `src/main/java/com/zenax/armorsets/gui/GUIManager.java` - Update all `open*` methods to use:
  - Standardized slot positions from GUIConstants
  - Help items at SLOT_HELP_INFO
  - Selection glow for options
  - Action bar messages for operations

- `src/main/java/com/zenax/armorsets/gui/handlers/ConfigHandler.java` - Update error messages and slot checks

---

## Migration Example

### Before (Inconsistent Positions):
```java
public void openTriggerConfig(...) {
    Inventory inv = Bukkit.createInventory(null, 45, ...);
    inv.setItem(39, confirmButton);  // Inconsistent
    inv.setItem(41, cancelButton);   // Inconsistent
    // ...
}

public void openEffectConfig(...) {
    Inventory inv = Bukkit.createInventory(null, 54, ...);
    inv.setItem(51, confirmButton);  // Different position!
    inv.setItem(53, cancelButton);   // Different position!
    // ...
}
```

### After (Standardized):
```java
public void openTriggerConfig(...) {
    int size = 45;
    Inventory inv = Bukkit.createInventory(null, size, ...);
    inv.setItem(GUIConstants.getConfirmSlot(size), confirmButton);
    inv.setItem(GUIConstants.getCancelSlot(size), cancelButton);
    // ...
}

public void openEffectConfig(...) {
    int size = 54;
    Inventory inv = Bukkit.createInventory(null, size, ...);
    inv.setItem(GUIConstants.getConfirmSlot(size), confirmButton);
    inv.setItem(GUIConstants.getCancelSlot(size), cancelButton);
    // ...
}
```

---

## Compilation Notes

After applying these changes, compile with:
```bash
# Windows
mvnw.bat clean compile

# Mac/Linux
./mvnw clean compile
```

If compilation fails, check:
1. All imports are correct (especially `GUIConstants`)
2. Method signatures match usage
3. No typos in constant names

---

## Documentation Generated
**Date**: 2024-11-24
**Implementer**: Claude (Sigil System Architect Agent)
**Related Issues**: Improvements.md Section 5 (UI/UX Issues)
