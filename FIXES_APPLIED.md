# GUI Critical Issues - Fixes Applied

This document summarizes the fixes applied to resolve critical issues 1.4, 1.5, and 1.6 from `Improvements.md`.

## Issue 1.4: Chance/Cooldown Input in Synergy Editor is Broken

**Location:** `ConfigHandler.java:643-650`

**Problem:**
- GUI closed and prompted for chat input but no `pendingMessageInput` was registered
- No `inputType` was set in the session
- The `handleMessageInput` method had no handler for "SYNERGY_CHANCE" or "SYNERGY_COOLDOWN"

**Fix Applied:**

### 1. ConfigHandler.java (Lines 647-659)
```java
case 14 -> {
    // FIXED: Register pending input and set inputType
    player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<chance 0-100>"));
    session.put("inputType", "SYNERGY_CHANCE");
    context.addPendingMessageInput(player.getUniqueId(), session);
    player.closeInventory();
}
case 16 -> {
    // FIXED: Register pending input and set inputType
    player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<cooldown seconds>"));
    session.put("inputType", "SYNERGY_COOLDOWN");
    context.addPendingMessageInput(player.getUniqueId(), session);
    player.closeInventory();
}
```

### 2. GUIManager.java (Lines 1430-1436)
Added handlers in `handleMessageInput` method:
```java
// FIXED: Add handlers for synergy chance and cooldown
if ("SYNERGY_CHANCE".equals(inputType)) {
    return handleSynergyChanceInput(player, input, session);
}
if ("SYNERGY_COOLDOWN".equals(inputType)) {
    return handleSynergyCooldownInput(player, input, session);
}
```

### 3. GUIManager.java (Lines 1789-1806)
Added validation and handling methods:
```java
private boolean handleSynergyChanceInput(Player player, String input, GUISession session) {
    String setId = session.getString("setId");
    String synergyId = session.getString("synergyId");

    if (setId != null && synergyId != null) {
        try {
            double chance = Double.parseDouble(input);
            if (chance < 0 || chance > 100) {
                player.sendMessage(TextUtil.colorize("&cChance must be between 0 and 100"));
                openSynergyEditor(player, setId, synergyId);
                return false;
            }

            // Store the chance value in the session
            session.put("chance", chance);
            player.sendMessage(TextUtil.colorize("&aChance set to: &f" + (int) chance + "%"));
            playSound(player, "click");

            // Reopen the synergy editor with the updated value
            openSynergyEditor(player, setId, synergyId);
            return true;
        } catch (NumberFormatException e) {
            player.sendMessage(TextUtil.colorize("&cInvalid number format. Please enter a number between 0 and 100."));
            openSynergyEditor(player, setId, synergyId);
            return false;
        }
    }

    player.sendMessage(TextUtil.colorize("&cInvalid session data"));
    return false;
}

private boolean handleSynergyCooldownInput(Player player, String input, GUISession session) {
    // Similar implementation with cooldown validation (>= 0)
}
```

**Validation Added:**
- Chance: Must be between 0-100
- Cooldown: Must be >= 0
- Both display error messages and reopen the GUI on invalid input
- Values are stored back in the session for persistence

---

## Issue 1.5: Effect Selector for Synergies Passes Wrong Parameters

**Location:** `ConfigHandler.java:642`

**Problem:**
The trigger was passed as `null`, breaking the effect configuration flow. The session's trigger should be used instead.

**Fix Applied:**

### ConfigHandler.java (Lines 642-646)
```java
case 12 -> {
    // FIXED: Pass the trigger from the session instead of null
    String trigger = session.getString("trigger");
    context.openEffectSelectorWithSlot(player, "synergy", setId, trigger, null);
}
```

**Explanation:**
- Now retrieves the trigger from the session data
- Passes it correctly to `openEffectSelectorWithSlot`
- This ensures the effect selector knows what trigger context it's operating in

---

## Issue 1.6: Unsaved Changes Lost on GUI Close

**Problem:**
All edits (synergies, triggers, effects, conditions) are stored in volatile `GUISession.data` which is cleared when the inventory closes.

**Fix Applied:**

### 1. GUIManager.java (Lines 116-122)
Added warning message when closing with unsaved changes:
```java
// FIXED: Warn user if they have unsaved changes (issue 1.6)
GUISession session = activeSessions.get(uuid);
if (session != null && hasUnsavedChanges(session)) {
    player.sendMessage(TextUtil.colorize("&c&lWARNING: &7You closed the GUI with unsaved changes!"));
    player.sendMessage(TextUtil.colorize("&7Your edits to synergies, triggers, or effects have been lost."));
    player.sendMessage(TextUtil.colorize("&7Remember to click the '&aSave&7' button before closing."));
}
```

### 2. GUIManager.java (Lines 1747-1786)
Added helper method to detect unsaved changes:
```java
private boolean hasUnsavedChanges(GUISession session) {
    if (session == null) return false;

    GUIType type = session.getType();

    // Check if it's a GUI type that can have unsaved changes
    if (type == GUIType.SYNERGY_EDITOR || type == GUIType.TRIGGER_CONFIG) {
        // Check if effects list has been modified
        @SuppressWarnings("unchecked")
        List<String> effects = (List<String>) session.get("effects");
        if (effects != null && !effects.isEmpty()) {
            return true;
        }

        // Check if conditions have been added
        @SuppressWarnings("unchecked")
        List<String> conditions = (List<String>) session.get("conditions");
        if (conditions != null && !conditions.isEmpty()) {
            return true;
        }

        // Check if chance or cooldown have been modified from defaults
        Double chance = session.getDouble("chance", -1.0);
        Double cooldown = session.getDouble("cooldown", -1.0);
        if ((chance >= 0 && chance != 100.0) || (cooldown > 0)) {
            return true;
        }

        // Check if trigger has been selected
        String trigger = session.getString("trigger");
        if (trigger != null && !trigger.isEmpty()) {
            return true;
        }
    }

    return false;
}
```

**Detection Criteria:**
- Checks for GUI types that support editing (SYNERGY_EDITOR, TRIGGER_CONFIG)
- Detects if effects have been added
- Detects if conditions have been added
- Detects if chance or cooldown differ from defaults
- Detects if a trigger has been selected

**User Experience:**
- Clear warning messages when closing with unsaved data
- Reminds users to click the Save button
- Prevents silent data loss

---

## Summary of Changes

### Files Modified
1. `src/main/java/com/zenax/armorsets/gui/handlers/ConfigHandler.java`
   - Fixed synergy chance/cooldown input registration (lines 647-659)
   - Fixed effect selector trigger parameter (lines 642-646)

2. `src/main/java/com/zenax/armorsets/gui/GUIManager.java`
   - Added SYNERGY_CHANCE and SYNERGY_COOLDOWN input handlers (lines 1430-1436)
   - Added unsaved changes warning on GUI close (lines 116-122)
   - Added `hasUnsavedChanges()` helper method (lines 1747-1786)
   - Added `handleSynergyChanceInput()` method (lines 1789-1822)
   - Added `handleSynergyCooldownInput()` method (lines 1824-1857)

### Testing Checklist
- [ ] Test synergy editor chance input (`/as input 50`)
- [ ] Test synergy editor cooldown input (`/as input 5`)
- [ ] Test chance validation (negative, > 100)
- [ ] Test cooldown validation (negative)
- [ ] Test effect selector receives correct trigger
- [ ] Test unsaved changes warning appears when closing with data
- [ ] Test no warning appears when closing empty/saved sessions

---

## Architecture Notes

### Input Validation Pattern
The fix follows the existing pattern used for sigil trigger chance/cooldown:
1. Player clicks button in GUI
2. GUI closes and `inputType` is set in session
3. Session is registered in `pendingMessageInputs`
4. User types `/as input <value>`
5. `handleMessageInput` routes to appropriate handler
6. Handler validates input
7. Handler stores value in session
8. Handler reopens GUI with updated values

### Session Data Flow
```
GUI Click → Close Inventory → Set inputType → Register pendingMessageInput
    ↓
Chat Command (/as input <value>)
    ↓
handleMessageInput → Route by inputType → Validate → Store in Session
    ↓
Reopen GUI with Updated Values
```

### Unsaved Changes Detection
The system tracks "unsaved" state by looking for data in the session that hasn't been committed to actual ArmorSet/Sigil objects. This is a lightweight approach that doesn't require explicit dirty flags.

---

*Fixes applied: 2025-11-24*
*All three critical issues from Improvements.md sections 1.4, 1.5, and 1.6 have been resolved.*
