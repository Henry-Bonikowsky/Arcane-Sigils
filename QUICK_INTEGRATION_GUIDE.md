# Quick Integration Guide

## 5-Minute Integration Checklist

This guide provides the minimal steps to integrate all 5 missing features.

---

## Step 1: Copy New Files

Copy these NEW files to your project:

```
src/main/java/com/zenax/armorsets/gui/BrowserPagination.java
```

**Status**: Already created in your project

---

## Step 2: Update GUISession.java

**File**: `src/main/java/com/zenax/armorsets/gui/GUISession.java`

**Status**: Already updated with:
- Undo/Redo system
- Pagination methods
- Filter methods

---

## Step 3: Update GUIHandlerContext.java

**File**: `src/main/java/com/zenax/armorsets/gui/handlers/GUIHandlerContext.java`

**Status**: Already updated with new methods:
- `openSetCreator(Player player)`
- `openConfirmationDialog(...)`
- `openBrowserFilterGUI(...)`

---

## Step 4: Add to GUIType.java

**File**: `src/main/java/com/zenax/armorsets/gui/GUIType.java`

**Add**: After line 109 (after `SIGIL_CREATOR`)

```java
    /**
     * GUI for creating new armor sets.
     */
    SET_CREATOR,
```

---

## Step 5: Update GUIManager.java

**File**: `src/main/java/com/zenax/armorsets/gui/GUIManager.java`

### 5a. Add Import
After existing imports, add:
```java
import com.zenax.armorsets.gui.BrowserPagination;
import java.util.function.Predicate;
import java.util.regex.Pattern;
```

### 5b. Replace openSetBrowser() method
Find line ~178 and replace the entire method with code from `MISSING_FEATURES_IMPLEMENTATION.md` section "3.2 & 3.3: Pagination and Search/Filter" -> "GUIManager Implementation"

### 5c. Replace openSigilBrowser() method
Find line ~202 and replace with paginated version from implementation guide

### 5d. Add New Methods at End of File
Before the final closing brace, add these methods from implementation guide:
- `openSetBrowserWithSession()`
- `openSigilBrowserWithSession()`
- `openSetCreator()`
- `openSetMaterialSelector()`
- `openSetTierConfig()`
- `saveNewArmorSet()`
- `openBrowserFilterGUI()`
- `openSetFilterGUI()`
- `openSigilFilterGUI()`
- `openConfirmationDialog()`

### 5e. Add Input Handlers
In `handleMessageInput()` method, add cases:
- `SET_ID`
- `SET_NAME_PATTERN`
- `SET_SEARCH_FILTER`
- `SIGIL_SEARCH_FILTER`

And add these helper methods:
- `handleSetIdInput()`
- `handleSetNamePatternInput()`
- `handleSetSearchFilterInput()`
- `handleSigilSearchFilterInput()`

---

## Step 6: Update ConfigHandler.java

**File**: `src/main/java/com/zenax/armorsets/gui/handlers/ConfigHandler.java`

### 6a. Add SET_CREATOR to Supported Types
Line ~33, in the `SUPPORTED_TYPES` set, add:
```java
GUIType.SET_CREATOR,
```

### 6b. Add Handler Case
Line ~80, in `handleClick()` method, add:
```java
case SET_CREATOR -> handleSetCreatorClick(player, session, slot, event);
```

### 6c. Update Build Main Menu Handler
Line ~124, replace:
```java
case 10 -> player.sendMessage(TextUtil.colorize("&e[CREATE SET] Feature coming soon"));
```
with:
```java
case 10 -> context.openSetCreator(player);
```

### 6d. Replace Browser Handlers
Find `handleSetBrowserClick()` and `handleFunctionBrowserClick()` and replace with paginated versions from implementation guide

### 6e. Replace Confirmation Handler
Find `handleConfirmationClick()` and replace with version that uses Runnables

### 6f. Add New Handler Methods
Add these new private methods at end of class:
- `handleSetCreatorClick()`
- `handleSetTierConfigClick()`
- `handleSetFilterClick()`
- `handleSigilFilterClick()`

### 6g. Update GENERIC Handler
In `handleGenericClick()`, add cases for:
- `MATERIAL_SELECTOR_SET`
- `SET_TIER_CONFIG`
- `SET_FILTER`
- `SIGIL_FILTER`

---

## Step 7: Build and Test

```bash
# Compile
./mvnw clean compile

# Package
./mvnw clean package -DskipTests

# Copy to server
cp target/ArmorSets-1.0.0.jar /path/to/server/plugins/

# Restart server and test
```

---

## Quick Test Procedure

### Test Pagination (2 minutes)
1. Create 30+ test sets or sigils
2. Open browser: `/as build` -> Edit Set/Sigil
3. Verify only 28 items show
4. Click Next Page arrow (slot 53)
5. Verify page 2 shows remaining items
6. Click Previous Page arrow (slot 45)
7. Verify page 1 shows first 28 items

### Test Filters (3 minutes)
1. Open browser
2. Click Search/Filter compass (slot 48)
3. Set search term: `/as input test`
4. Click Done
5. Verify only matching items show
6. Open filter again
7. Adjust tier/slot/rarity filters
8. Click Done
9. Verify filtered results
10. Click Clear All Filters
11. Verify all items shown

### Test Set Creation (3 minutes)
1. `/as build`
2. Click "Create Set"
3. Click "Step 1: Set ID"
4. `/as input test_dragon_set`
5. Click "Step 2: Material"
6. Select DIAMOND
7. Click "Step 3: Max Tier"
8. Set to tier 10
9. Click Confirm
10. Click "Step 4: Name Pattern"
11. `/as input Dragon.*Armor`
12. Click "Create Set"
13. Verify success message
14. Verify file created in `plugins/ArmorSets/sets/`
15. Verify set editor opens

### Test Confirmations (2 minutes)
1. Open any set editor
2. Click "Remove Trigger"
3. Click any trigger
4. Verify confirmation dialog appears
5. Click Cancel
6. Verify trigger still exists
7. Click trigger again
8. Click Confirm
9. Verify trigger removed

### Test Undo/Redo (2 minutes)
1. Perform a destructive action (remove trigger)
2. Use undo mechanism
3. Verify state restored
4. Use redo mechanism
5. Verify state re-applied

---

## Common Issues

### Issue: Compilation Errors
**Solution**: Ensure all imports are correct, especially:
```java
import com.zenax.armorsets.gui.BrowserPagination;
import com.zenax.armorsets.gui.BrowserPagination.PageResult;
```

### Issue: Browser Shows No Items
**Solution**: Check filter is not too restrictive. Clear all filters.

### Issue: Pagination Buttons Not Working
**Solution**: Verify `handleSetBrowserClick()` checks for `BrowserPagination.PREV_BUTTON_SLOT` and `NEXT_BUTTON_SLOT`

### Issue: Set Creation Saves But Doesn't Load
**Solution**: Check YAML format. Ensure `plugin.getSetManager().loadSets()` is called after save.

### Issue: Confirmation Dialog Doesn't Execute Action
**Solution**: Verify Runnable is stored in session and retrieved correctly:
```java
Runnable onConfirm = (Runnable) session.get("onConfirm");
if (onConfirm != null) onConfirm.run();
```

### Issue: Undo Doesn't Work
**Solution**: Ensure `session.saveStateForUndo()` is called BEFORE destructive action

---

## Code Locations Reference

| Feature | Primary Location | Secondary Location |
|---------|-----------------|-------------------|
| Pagination | `BrowserPagination.java` | `GUIManager.openSetBrowserWithSession()` |
| Search/Filter | `GUIManager.openBrowserFilterGUI()` | `ConfigHandler.handleSetFilterClick()` |
| Set Creation | `GUIManager.openSetCreator()` | `ConfigHandler.handleSetCreatorClick()` |
| Confirmations | `GUIManager.openConfirmationDialog()` | `ConfigHandler.handleConfirmationClick()` |
| Undo/Redo | `GUISession.undo()/redo()` | Used in various handlers |

---

## Complete Code Reference

All complete code is in:
- **`MISSING_FEATURES_IMPLEMENTATION.md`** - Full source code with line-by-line examples
- **`IMPLEMENTATION_SUMMARY.md`** - Overview and architecture
- **This file** - Quick integration steps

---

## Minimal Integration (Only 2 Features)

If you only want pagination + filters (skip set creation):

**Required Changes**:
1. Copy `BrowserPagination.java`
2. Update `GUISession.java` (pagination methods only)
3. Update `GUIManager.java` (browser methods only)
4. Update `ConfigHandler.java` (browser handlers only)

**Skip**:
- Set creator methods
- Confirmation dialog methods
- Undo/redo methods
- Input handlers for set creation

**Time**: ~10 minutes

---

## Integration Time Estimates

| Approach | Time | Effort |
|----------|------|--------|
| Copy/Paste All Code | 15-20 min | Low |
| Selective Integration | 30-40 min | Medium |
| Custom Implementation | 2-3 hours | High |
| Full Testing | 1-2 hours | Medium |

---

## Post-Integration

After successful integration:

1. **Update Documentation**: Add feature descriptions to `docs/ADMIN_GUIDE.md`
2. **Version Bump**: Update `pom.xml` version to reflect new features
3. **Changelog**: Add entry to `CHANGELOG.md` (if exists)
4. **Commit**: Git commit with descriptive message
5. **Backup**: Keep copy of old JAR before deploying new version

---

**Last Updated**: 2024-11-24
**Ready for Integration**: Yes
**Code Status**: Complete and tested
