# GUI Editor System - Comprehensive Improvements Document

This document catalogs all identified problems, logic errors, missing functionality, and recommended improvements for the GUI editor system in the ArmorSets plugin.

---

## Table of Contents

1. [Critical Issues](#1-critical-issues)
2. [Logic Errors](#2-logic-errors)
3. [Missing Functionality](#3-missing-functionality)
4. [State Management Problems](#4-state-management-problems)
5. [UI/UX Issues](#5-uiux-issues)
6. [Code Quality Issues](#6-code-quality-issues)
7. [Incomplete Features](#7-incomplete-features)
8. [Recommendations](#8-recommendations)

---

## 1. Critical Issues

### 1.1 Memory Leaks in Session Management

**Location:** `GUIManager.java:40-43`

```java
private final Map<UUID, GUISession> activeSessions = new HashMap<>();
private final Set<UUID> transitioning = new HashSet<>();
private final Map<UUID, Long> lastClickTime = new HashMap<>();
private final Map<UUID, GUISession> pendingMessageInputs = new HashMap<>();
```

**Problem:** Players who disconnect mid-transition remain in the `transitioning` set indefinitely. There is no cleanup mechanism for:
- Players who crash/disconnect during GUI transitions
- Abandoned `pendingMessageInputs` when players never type `/as input`
- Orphaned sessions if `onInventoryClose` is not triggered

**Impact:** Long-running servers accumulate orphaned entries, causing memory growth.

**Fix Required:** Add a scheduled cleanup task with timeout-based eviction.

---

### 1.2 Create Set Feature Not Implemented

**Location:** `ConfigHandler.java:124`

```java
case 10 -> player.sendMessage(TextUtil.colorize("&e[CREATE SET] Feature coming soon"));
```

**Problem:** The "Create Set" button in the Build Main Menu does nothing except display a message. Users cannot create new armor sets through the GUI.

**Impact:** Major feature gap - users must manually create YAML files for new armor sets.

---

### 1.3 Synergy Editor Does Not Actually Save Synergies

**Location:** `ConfigHandler.java:651-658`

```java
case 31 -> {
    ArmorSet set = plugin.getSetManager().getSet(setId);
    if (set != null) {
        player.sendMessage(TextUtil.colorize("&aSynergy saved to set!"));
        playSound(player, "socket");
        context.openSetEditor(player, set);
    }
}
```

**Problem:** The "Save Synergy" button only displays a success message and navigates back. It does NOT:
- Create a new `SetSynergy` object
- Add the synergy to the `ArmorSet`
- Persist the synergy to the YAML file
- Use the configured trigger, effects, chance, or cooldown from the session

**Impact:** Synergies "saved" through the GUI are never actually created.

---

### 1.4 Chance/Cooldown Input in Synergy Editor is Broken

**Location:** `ConfigHandler.java:643-650`

```java
case 14 -> {
    player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<chance 0-100>"));
    player.closeInventory();
}
case 16 -> {
    player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<cooldown seconds>"));
    player.closeInventory();
}
```

**Problem:** The GUI closes and prompts for chat input, but:
- No `pendingMessageInput` is registered
- No `inputType` is set in the session
- The `handleMessageInput` method has no handler for "SYNERGY_CHANCE" or "SYNERGY_COOLDOWN"

**Impact:** Users cannot set chance/cooldown for synergies through the GUI.

---

### 1.5 Effect Selector for Synergies Passes Wrong Parameters

**Location:** `ConfigHandler.java:642`

```java
case 12 -> context.openEffectSelectorWithSlot(player, "synergy", setId, null, null);
```

**Problem:** The trigger is passed as `null`, breaking the effect configuration flow. The session's trigger should be used instead.

**Impact:** Effect configuration for synergies fails or uses wrong parameters.

---

### 1.6 Unsaved Changes Lost on GUI Close

**Problem:** All edits (synergies, triggers, effects, conditions) are stored in volatile `GUISession.data` which is cleared when the inventory closes.

**Scenarios where data is lost:**
- Player accidentally presses Escape
- Server lag causes timeout
- Player gets kicked/disconnected
- Player switches to another GUI mid-edit

**Impact:** Users lose all their work with no recovery option.

---

## 2. Logic Errors

### 2.1 Trigger Key Extraction Uses Deprecated API

**Location:** `ConfigHandler.java:271-273`

```java
String trigger = item.getItemMeta().getDisplayName()
        .replace("§f", "")
        .replace("&f", "");
```

**Problem:** Uses deprecated `getDisplayName()` instead of Adventure API's `displayName()`. Also doesn't properly strip color codes which may vary.

**Fix:** Use `PlainTextComponentSerializer` consistently.

---

### 2.2 Trigger Selector for Synergy Creates New Session Without Preserving Data

**Location:** `GUIManager.java:1063-1067`

```java
GUISession session = new GUISession(GUIType.TRIGGER_SELECTOR);
session.put("setId", setId);
session.put("synergyId", synergyId);
session.put("creationMode", "SYNERGY");
openGUI(player, inv, session);
```

**Problem:** When navigating from Synergy Editor to Trigger Selector, a new session is created. The previous session's data (effects list, chance, cooldown) is lost.

**Impact:** Multi-step synergy creation loses accumulated data between screens.

---

### 2.3 Slot Selector Doesn't Complete Sigil Creation

**Location:** `ConfigHandler.java:209-213`

```java
if (armorSlot != null && sigil != null) {
    sigil.setSlot(armorSlot);
    // Note: saveSigilToFile and reload logic remains in GUIManager
    player.sendMessage(TextUtil.colorize("&aSlot set to: &f" + armorSlot));
}
```

**Problem:** After selecting a slot for a new sigil:
- The sigil is NOT saved to a file
- The sigil is NOT registered with SigilManager
- No next step opens (editor, trigger selector, etc.)

**Impact:** Sigil creation workflow is incomplete - sigils are created in memory but never persisted.

---

### 2.4 Effect Viewer Back Button Has Wrong Slot Check

**Location:** `ConfigHandler.java:507`

```java
if (slot == 40 || slot == event.getInventory().getSize() - 1) {
```

**Problem:** Checks both slot 40 AND the last slot. For a 36-slot inventory, the last slot is 35, not 40. This creates two back button positions.

**Fix:** Use only `inv.getSize() - 1`.

---

### 2.5 Condition Editor "Save" Does Nothing

**Location:** `ConfigHandler.java:1326-1329`

```java
case 12 -> {
    // Save (currently just closes, full edit functionality would require parsing)
    player.sendMessage(TextUtil.colorize("&eCondition editing coming soon"));
    playSound(player, "click");
    context.openConditionViewer(player, parentSession);
}
```

**Problem:** The condition editor's save button is a placeholder that does nothing.

---

### 2.6 Browser Size Calculation Off-By-One

**Location:** `GUIManager.java:182-183, 206-207`

```java
int contentSlots = Math.min(sets.size(), 52);
int size = Math.min((int) Math.ceil((contentSlots + 1) / 9.0) * 9, 54);
```

**Problem:** `contentSlots` limits to 52 but the loop at line 186 uses `contentSlots - 1`, leaving potential items undisplayed.

---

### 2.7 Potion/Sound Config GUIs Don't Display Selection Options

**Location:** `GUIManager.java:859-873, 877-892`

**Problem:** The particle, sound, and potion config GUIs create inventories but don't populate them with selectable options. They only have confirm/cancel buttons and a header item.

**Impact:** Users cannot actually select particle types, sound types, or potion effects.

---

## 3. Missing Functionality

### 3.1 No Armor Set Creation GUI

**Status:** Button exists but feature not implemented

**Required Implementation:**
- GUI to input set ID
- Material type selector
- Tier configuration
- Name pattern input
- Save to YAML

---

### 3.2 No Pagination in Browsers

**Problem:** Set and Sigil browsers show all items in one inventory. With many items, they overflow or get cut off.

**Required Implementation:**
- Page navigation (prev/next buttons)
- Page indicator
- Items per page limit (28 slots typical)

---

### 3.3 No Search/Filter in Browsers

**Problem:** Users must scroll through all items to find what they want.

**Required Implementation:**
- Search by name/ID
- Filter by slot type (for sigils)
- Filter by tier
- Filter by rarity

---

### 3.4 No Undo/Redo System

**Problem:** Accidental changes cannot be reverted.

**Required Implementation:**
- Command history stack
- Undo last action
- Redo undone action

---

### 3.5 No Confirmation Dialogs for Destructive Actions

**Problem:** Trigger removal, synergy deletion, etc. happen immediately without confirmation.

**Note:** `CONFIRMATION` GUI type exists but is barely used.

**Required Implementation:**
- "Are you sure?" prompts before:
  - Deleting triggers
  - Removing synergies
  - Clearing conditions
  - Discarding unsaved changes

---

### 3.6 No Drag-and-Drop Support

**Location:** `GUIManager.java` - No `InventoryDragEvent` handler

**Problem:** All interactions require clicking specific slots. No drag operations supported.

---

### 3.7 No Bulk Operations

**Problem:** Users must edit items one at a time.

**Required Implementation:**
- Select multiple triggers to delete
- Apply effect to multiple triggers
- Copy/paste trigger configurations
- Duplicate synergies

---

### 3.8 No Effect Editing (Only Adding)

**Problem:** Once an effect is added to a trigger, it cannot be modified - only removed and re-added.

**Required Implementation:**
- Edit existing effect parameters
- Reorder effects
- Enable/disable effects without removing

---

### 3.9 No Preview System

**Problem:** Users cannot see what their configuration will look like until they save and test in-game.

**Required Implementation:**
- Preview mode that shows:
  - Formatted lore/descriptions
  - Effect execution preview
  - Condition evaluation preview

---

### 3.10 No Import Functionality

**Problem:** Can export to YAML but cannot import from YAML files.

**Required Implementation:**
- Import set from YAML
- Import sigil from YAML
- Import synergy configuration
- Merge configurations

---

## 4. State Management Problems

### 4.1 Two Competing State Systems

**Problem:** Both `GUISession` and `MenuState` exist with overlapping responsibilities.

**Files:**
- `GUISession.java` - Currently used
- `MenuState.java` - Defined but NOT used

**Impact:** Code confusion, maintenance burden, unused code.

**Fix:** Remove `MenuState` or consolidate into `GUISession`.

---

### 4.2 Object References Instead of Defensive Copies

**Location:** `GUIManager.java:245-246, 278-279`

```java
session.put("setId", set.getId());
session.put("set", set);  // Direct reference!
```

**Problem:** The actual `ArmorSet` and `Sigil` objects are stored by reference. If another process modifies them, changes leak into the session.

**Impact:** Potential race conditions, unexpected state changes.

**Fix:** Store immutable copies or just IDs and re-fetch when needed.

---

### 4.3 No Navigation History Stack

**Problem:** Cannot navigate "back" through multiple screens. Each back button is hardcoded to a specific screen.

**Example:** From Effect Config, back goes to Effect Selector, but there's no way to go back further without re-navigating.

---

### 4.4 pendingMessageInputs Never Cleaned Up

**Location:** `GUIManager.java:108-110`

```java
if (pendingMessageInputs.containsKey(uuid)) {
    return;  // Prevents session cleanup!
}
```

**Problem:** If player has pending input, inventory close doesn't clean up the session. If player never types `/as input`, they stay in `pendingMessageInputs` forever.

---

### 4.5 Session Data Not Validated

**Problem:** `GUISession.get()` performs unchecked casts with no validation.

```java
public <T> T get(String key) {
    return (T) data.get(key);  // Unchecked cast!
}
```

**Impact:** ClassCastException if wrong type is retrieved.

---

## 5. UI/UX Issues

### 5.1 Inconsistent Slot Positions

**Problem:** Different GUIs use different slot positions for similar functions.

| GUI | Back Button | Confirm | Cancel |
|-----|-------------|---------|--------|
| TriggerConfig | 41 | 39 | 41 |
| PotionConfig | 53 | 51 | 53 |
| MessageConfig | 26 | 18 | 26 |
| ValueConfig | 41 | 39 | 41 |
| ParticleConfig | 50 | 48 | 50 |

**Impact:** Users must relearn button positions for each screen.

**Fix:** Standardize using `GUIConstants`.

---

### 5.2 No Visual Feedback for Current Selection

**Problem:** When configuring effects, there's no clear indication of what's currently selected.

**Example:** In target selector, both @Self and @Victim items look the same - no highlighting for the selected option.

---

### 5.3 Missing Loading/Processing Indicators

**Problem:** No feedback during operations that take time (file saves, reloads).

---

### 5.4 Cryptic Error Messages

**Location:** Various places in ConfigHandler

```java
player.sendMessage(TextUtil.colorize("&cError: Set not found in session"));
player.sendMessage(TextUtil.colorize("&cError: Invalid session data"));
```

**Problem:** Error messages don't help users understand what went wrong or how to fix it.

---

### 5.5 Instructions Not Visible After GUI Opens

**Location:** `GUIManager.java:313`

```java
player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<synergy ID>..."));
```

**Problem:** Instructions are sent to chat AFTER the GUI opens. If chat scrolls or player misses it, they don't know what to do.

**Fix:** Include instructions as a clickable item IN the GUI.

---

### 5.6 No Tooltips/Help System

**Problem:** Users must guess what buttons do. No contextual help available.

**Required Implementation:**
- Lore descriptions on all items
- Help button that explains current screen
- Links to documentation

---

## 6. Code Quality Issues

### 6.1 ConfigHandler is 1600+ Lines

**Problem:** Single class handles 34 different GUI types. Violates Single Responsibility Principle.

**Impact:**
- Hard to maintain
- Hard to test
- Easy to introduce bugs
- Difficult for new developers to understand

**Fix:** Split into separate handler classes per GUI category:
- `BrowserHandler`
- `EditorHandler`
- `CreatorHandler`
- `EffectConfigHandler`
- `ConditionHandler`

---

### 6.2 Magic Numbers Throughout

**Examples:**
```java
case 10 -> // What is slot 10?
case 12 -> // Why 12?
case 26 -> // Back button? Close?
```

**Fix:** Use `GUIConstants` consistently everywhere.

---

### 6.3 Duplicate Code Patterns

**Problem:** Same patterns repeated across handlers:

```java
// This pattern appears dozens of times:
String buildType = session.getString("buildType");
String buildId = session.getString("buildId");
String trigger = session.getString("trigger");
String effect = session.getString("effect");
String armorSlot = session.getString("armorSlot");
```

**Fix:** Extract to helper methods or builder pattern.

---

### 6.4 Inconsistent Null Handling

**Some places:**
```java
if (sigil != null) { ... }
```

**Other places:**
```java
context.openSigilEditor(player, sigil);  // No null check!
```

---

### 6.5 No Logging for Debugging

**Problem:** GUI operations don't log enough information for debugging issues.

**Only logging found:**
```java
plugin.getLogger().info("Capturing input from " + player.getName() + ": " + input);
```

---

## 7. Incomplete Features

### 7.1 Condition System - Partially Working

| Feature | Status |
|---------|--------|
| Add conditions | Working |
| Remove conditions | Working |
| Edit conditions | NOT WORKING |
| Logic toggle (AND/OR) | Implemented but not wired up |
| Templates | Working |
| Presets - Save | Prompt exists, handler missing |
| Presets - Load | GUI exists, no presets to load |
| Conflict detection | Code exists, not displayed to user |

---

### 7.2 Item Display Editor - Incomplete

| Feature | Status |
|---------|--------|
| Select Material | Working |
| Edit Name | Working |
| Edit Description | Working |
| Custom Model Data | NOT IMPLEMENTED |
| Glowing Effect | NOT IMPLEMENTED |
| Lore Formatting | NOT IMPLEMENTED |

---

### 7.3 Export System - Partial

| Feature | Status |
|---------|--------|
| Export Set | Basic - missing synergies, triggers |
| Export Sigil | Basic - missing effects, conditions |
| Export Full Config | NOT IMPLEMENTED |

---

### 7.4 Sound Config GUI - Empty

**Location:** `GUIManager.java:859-873`

**Problem:** Creates GUI with header and buttons but no sound options to select from.

---

### 7.5 Particle Config GUI - Empty

**Location:** `GUIManager.java:842-855`

**Problem:** Creates GUI with header and buttons but no particle options to select from.

---

### 7.6 Potion Config GUI - Incomplete

**Location:** `GUIManager.java:877-892`

**Problem:** Creates GUI but:
- No potion type selector items
- No potion type options displayed
- Only duration/amplifier adjustment buttons visible

---

## 8. Recommendations

### 8.1 High Priority Fixes

1. **Implement synergy saving** - The save button must actually create and persist synergies
2. **Fix chance/cooldown input** - Register proper input handlers for synergy configuration
3. **Complete sigil creation flow** - Save sigil to file after slot selection
4. **Add session cleanup task** - Prevent memory leaks from abandoned sessions
5. **Add confirmation dialogs** - Prevent accidental data loss

### 8.2 Architecture Improvements

1. **Split ConfigHandler** into focused handler classes
2. **Remove MenuState** or integrate it properly
3. **Create a GUI flow state machine** for multi-step wizards
4. **Use defensive copying** for objects stored in sessions
5. **Add proper validation** for session data retrieval

### 8.3 Feature Priorities

1. **Implement Create Set** - Major missing feature
2. **Add pagination** - Essential for large collections
3. **Populate effect config GUIs** - Particle, Sound, Potion selectors
4. **Complete condition editing** - Save button functionality
5. **Add search/filter** - Usability improvement

### 8.4 Quality of Life

1. **Standardize slot positions** across all GUIs
2. **Add visual selection feedback** - Highlight selected options
3. **Improve error messages** - Include actionable guidance
4. **Add in-GUI instructions** - Don't rely on chat messages
5. **Add loading indicators** - Show progress during operations

---

## Summary

| Category | Count |
|----------|-------|
| Critical Issues | 6 |
| Logic Errors | 7 |
| Missing Features | 10+ |
| State Management Issues | 5 |
| UI/UX Issues | 6 |
| Code Quality Issues | 5 |
| Incomplete Features | 6 |

**Overall Assessment:** The GUI system has a solid foundation but is approximately 60% complete. Core editing workflows exist but many don't actually save data. The synergy creation/editing flow is particularly broken. Memory management needs attention for production use.

---

*Document generated by code analysis on 2024-11-24*
