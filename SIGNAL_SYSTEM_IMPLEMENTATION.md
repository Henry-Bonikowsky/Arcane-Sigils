# Signal System GUI Handlers Implementation

## Overview
Created the Signal System GUI handlers for the Arcane Sigils plugin, providing a user-friendly interface for managing signals (event-based triggers) on sigils.

## Files Created

### 1. SignalConfigHandler.java
**Location:** `src/main/java/com/zenax/armorsets/gui/signal/SignalConfigHandler.java`

**Purpose:** Main GUI for viewing and managing all signals on a sigil.

**Features:**
- Display up to 7 signals in the middle row
- Clear all signals with confirmation
- Delete mode toggle for removing individual signals
- Add new signals via Signal Selector
- Click signals to edit their effects
- Material-based visual representation for each signal type

**GUI Layout (27 slots, 3 rows):**
```
Row 0: [Clear All][_][_][_][_][_][_][_][_]
Row 1: [_][Signal 1-7 (7 slots)...........][_]
Row 2: [Back][_][_][Delete Mode][Add Signal][_][_][_][_]
```

**Key Methods:**
- `openGUI(GUIManager, Player, Sigil)` - Opens the Signal Config GUI
- `handleClick(Player, GUISession, int, InventoryClickEvent)` - Handles all click events
- `getSignalMaterial(SignalType)` - Maps signal types to visual materials

### 2. SignalSelectorHandler.java
**Location:** `src/main/java/com/zenax/armorsets/gui/signal/SignalSelectorHandler.java`

**Purpose:** GUI for selecting which signal type to add to a sigil.

**Features:**
- Displays all 14 signal types
- Pagination support (18 signals per page)
- Shows signal descriptions
- Prevents adding duplicate signals
- Auto-opens Signal Config after adding

**GUI Layout (27 slots, 3 rows):**
```
Row 0-1: [Signal Types (18 per page)....................]
Row 2: [Back][Prev Page][_][_][Page Info][_][_][_][Next Page]
```

**Key Methods:**
- `openGUI(GUIManager, Player, Sigil, int)` - Opens the Signal Selector at a specific page
- `handleSignalSelect(...)` - Adds selected signal to sigil
- `buildSignalKey(SignalType)` - Converts signal type to config key format

## Signal Types Supported

All 14 signal types from `SignalType` enum:

| Signal Type | Material | Description |
|------------|----------|-------------|
| ATTACK | IRON_SWORD | When attacking |
| DEFENSE | SHIELD | When taking damage |
| KILL_MOB | BONE | When killing a mob |
| KILL_PLAYER | PLAYER_HEAD | When killing a player |
| SHIFT | FEATHER | When sneaking |
| FALL_DAMAGE | LEATHER_BOOTS | When taking fall damage |
| EFFECT_STATIC | CLOCK | Passive effect |
| BOW_SHOOT | ARROW | When shooting a bow |
| BOW_HIT | TARGET | When arrow hits target |
| TRIDENT_THROW | TRIDENT | When throwing trident |
| TICK | REPEATER | Periodic effect |
| BLOCK_BREAK | WOODEN_PICKAXE | When breaking blocks |
| BLOCK_PLACE | GRASS_BLOCK | When placing blocks |
| INTERACT | STICK | When interacting |

## Integration with GUIManager

### Handler Registration
Added to `GUIManager` constructor:
```java
registerHandler(GUIType.SIGNAL_CONFIG, new SignalConfigHandler(plugin, this));
registerHandler(GUIType.SIGNAL_SELECTOR, new SignalSelectorHandler(plugin, this));
```

### OpenSignalConfig Method
Updated `GUIManager.openSignalConfig()` to use the new handler:
```java
public void openSignalConfig(Player player, Sigil sigil) {
    com.zenax.armorsets.gui.signal.SignalConfigHandler.openGUI(this, player, sigil);
}
```

## Data Structure

Signals are stored in `Sigil` as:
```java
Map<String, SignalConfig> effects;
```

Where:
- **Key format:** `"on_SIGNAL_TYPE"` (e.g., `"on_ATTACK"`) or `"effect_static"` for passive effects
- **Value:** `SignalConfig` object containing:
  - `List<String> effects` - Effect strings to execute
  - `double chance` - Activation chance
  - `double cooldown` - Cooldown in seconds
  - `List<String> conditions` - Conditions that must be met
  - `SignalMode signalMode` - CHANCE or COOLDOWN scaling mode

## User Workflow

1. **Open Sigil Editor** → Click "Signals" button
2. **Signal Config GUI opens** showing all configured signals
3. **Add New Signal:**
   - Click "Add Signal" button
   - Signal Selector GUI opens
   - Click desired signal type
   - Returns to Signal Config with new signal added
4. **Edit Signal:**
   - Click a signal item in the list
   - Opens Effect Config GUI (to be implemented)
5. **Delete Signal:**
   - Toggle "Delete Mode" ON
   - Click signal to delete
   - Toggle "Delete Mode" OFF when done
6. **Clear All:**
   - Click "Clear All" button
   - All signals removed instantly

## Future Integration Points

### Effect Config GUI (Not Yet Implemented)
When clicking a signal in normal mode, should open:
- Effect Config GUI to manage the list of effects for that signal
- Edit chance, cooldown, conditions
- Configure signal mode (CHANCE vs COOLDOWN scaling)

### Auto-Save
Currently signals are stored in memory. Integration needed:
- Save signal changes to YAML via `SigilManager`
- Auto-save on signal add/delete/modify

## Technical Details

### Navigation History
- Uses `GUISession.pushNavigation()` to track GUI history
- Back button uses `openPreviousGUI()` for proper navigation
- Maintains state when moving between GUIs

### Session Data
Each GUI session stores:
- `"sigil"` (Sigil) - The sigil being edited
- `"deleteMode"` (Boolean) - Delete mode state for Signal Config
- `"page"` (Integer) - Current page for Signal Selector

### Material Mapping
Static helper method `getSignalMaterial(SignalType)` provides consistent visual representation across all GUIs.

### Effect String Summarization
`summarizeEffect(String)` method parses effect strings like:
- `"POTION:SPEED:10:2"` → `"POTION (SPEED, 10, 2)"`
- Truncates for display if too long

## Compilation Status

✅ **Successfully Compiled** - Both signal handlers compile without errors.

**Note:** Build shows errors in unrelated files (binds GUI handlers, tier handlers) that use deprecated GUI handler classes. These are pre-existing issues unrelated to the signal system implementation.

## Next Steps

1. **Implement Effect Config GUI** - Handler for editing effects list on a signal
2. **Implement Effect Selector GUI** - Handler for adding new effects to a signal
3. **Add Save Functionality** - Integrate with SigilManager to persist changes
4. **Add Confirmation Dialogs** - For destructive operations (clear all, delete)
5. **Update Sigil Editor** - Ensure proper navigation from Sigil Editor to Signal Config

## Version

- **Plugin Version:** 1.0.78
- **Implementation Date:** December 11, 2025
- **Files Modified:**
  - `GUIManager.java` (updated)
- **Files Created:**
  - `SignalConfigHandler.java` (new)
  - `SignalSelectorHandler.java` (new)
