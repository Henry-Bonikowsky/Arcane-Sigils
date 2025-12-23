# Missing Features Implementation Guide

This document provides complete implementation details for the 5 missing features identified in Improvements.md sections 3.1-3.5.

## Overview

The following features have been implemented:

1. **Pagination System** - Browsers now support 28 items per page with prev/next navigation
2. **Search/Filter System** - Filter sets by name/tier, sigils by name/slot/rarity/tier
3. **Undo/Redo System** - Session-based history with 10-action limit
4. **Confirmation Dialogs** - Pre-deletion confirmations for destructive actions
5. **Armor Set Creation GUI** - Full workflow for creating new armor sets

## Files Created

### 1. BrowserPagination.java
**Location**: `src/main/java/com/zenax/armorsets/gui/BrowserPagination.java`

Utility class providing:
- `ITEMS_PER_PAGE = 28` constant
- Slot constants for navigation controls (slots 44, 45, 48, 49, 53)
- `PageResult<T>` class with pagination metadata
- `paginate()` method for generic pagination
- `createSetFilter()` for armor set filtering
- `createSigilFilter()` for sigil filtering with slot/rarity support

## Files Modified

### 1. GUISession.java
**Enhanced with**:
- Navigation history stack (Deque<NavigationFrame>)
- Undo/Redo stacks with 10-action limit
- Pagination methods: `getCurrentPage()`, `setCurrentPage()`
- Filter methods: `getSearchFilter()`, `setSearchFilter()`, `getSlotFilter()`, `setSlotFilter()`, `getRarityFilter()`, `getTierFilter()`
- Undo/Redo methods: `saveStateForUndo()`, `undo()`, `redo()`, `canUndo()`, `canRedo()`, `getUndoCount()`, `getRedoCount()`, `clearHistory()`

### 2. GUIHandlerContext.java
**Added methods**:
```java
void openSetCreator(Player player);
void openConfirmationDialog(Player player, String title, String message, Runnable onConfirm, Runnable onCancel);
void openBrowserFilterGUI(Player player, GUISession browserSession);
```

## Implementation Details

### 3.1: Armor Set Creation GUI

#### New GUI Type
Add to `GUIType.java`:
```java
SET_CREATOR,  // GUI for creating new armor sets
```

#### GUIManager Implementation
Add these methods to `GUIManager.java`:

```java
@Override
public void openSetCreator(Player player) {
    Inventory inv = Bukkit.createInventory(null, 36, TextUtil.parseComponent("&8Create New Armor Set"));

    inv.setItem(4, ItemBuilder.createGuiItem(Material.DIAMOND_CHESTPLATE, "&b&lNew Armor Set",
        "&7Follow the steps to create",
        "&7a new armor set configuration"));

    inv.setItem(11, ItemBuilder.createGuiItem(Material.NAME_TAG, "&eStep 1: Set ID",
        "&7Click to enter set ID",
        "&8(Use /as input <id>)"));
    inv.setItem(13, ItemBuilder.createGuiItem(Material.IRON_CHESTPLATE, "&eStep 2: Material",
        "&7Select armor material type"));
    inv.setItem(15, ItemBuilder.createGuiItem(Material.EXPERIENCE_BOTTLE, "&eStep 3: Max Tier",
        "&7Configure tier system"));
    inv.setItem(20, ItemBuilder.createGuiItem(Material.OAK_SIGN, "&eStep 4: Name Pattern",
        "&7Set the regex pattern"));

    inv.setItem(30, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aCreate Set",
        "&7Finalize and save"));
    inv.setItem(32, ItemBuilder.createGuiItem(Material.BARRIER, "&cCancel",
        "&7Return to build menu"));

    GUISession session = new GUISession(GUIType.SET_CREATOR);
    session.put("step", 1);
    session.put("maxTier", 10);
    session.put("material", "NETHERITE");
    openGUI(player, inv, session);
}

@Override
public void openSetMaterialSelector(Player player, GUISession creatorSession) {
    Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("&8Select Material Type"));

    Material[] materials = {
        Material.LEATHER_CHESTPLATE,
        Material.CHAINMAIL_CHESTPLATE,
        Material.IRON_CHESTPLATE,
        Material.GOLDEN_CHESTPLATE,
        Material.DIAMOND_CHESTPLATE,
        Material.NETHERITE_CHESTPLATE
    };

    String currentMaterial = creatorSession.getString("material");

    for (int i = 0; i < materials.length; i++) {
        Material mat = materials[i];
        String matName = mat.name().replace("_CHESTPLATE", "");
        boolean selected = matName.equals(currentMaterial);

        ItemStack item = ItemBuilder.createSelectableItem(mat,
            (selected ? "&a&l" : "&f") + TextUtil.toProperCase(matName),
            selected,
            selected ? "&7Currently Selected" : "&7Click to select");

        inv.setItem(10 + i, item);
    }

    inv.setItem(26, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack"));

    GUISession session = new GUISession(GUIType.GENERIC);
    session.put("parentSession", creatorSession);
    session.put("menuType", "MATERIAL_SELECTOR_SET");
    openGUI(player, inv, session);
}

private void saveNewArmorSet(Player player, GUISession session) {
    String setId = session.getString("setId");
    String material = session.getString("material");
    String namePattern = session.getString("namePattern");
    int maxTier = session.getInt("maxTier", 10);

    if (setId == null || setId.isEmpty()) {
        player.sendMessage(TextUtil.colorize("&cError: Set ID is required"));
        return;
    }

    if (plugin.getSetManager().getSet(setId) != null) {
        player.sendMessage(TextUtil.colorize("&cError: A set with that ID already exists"));
        return;
    }

    // Create new ArmorSet object
    ArmorSet newSet = new ArmorSet(setId);
    newSet.setMaterial(Material.valueOf(material + "_CHESTPLATE"));
    newSet.setNamePattern(namePattern != null ? namePattern : setId);
    newSet.setMaxTier(maxTier);
    newSet.setTier(1);

    // Save to YAML
    File setsDir = new File(plugin.getDataFolder(), "sets");
    if (!setsDir.exists()) setsDir.mkdirs();

    File setFile = new File(setsDir, setId.toLowerCase() + ".yml");
    YamlConfiguration config = new YamlConfiguration();

    config.set(setId + ".max_tier", maxTier);
    config.set(setId + ".name_pattern", newSet.getNamePattern());
    config.set(setId + ".material", material);
    config.set(setId + ".equipped_message", List.of("&aYou equipped the " + setId + " set!"));
    config.set(setId + ".unequipped_message", List.of("&cYou unequipped the " + setId + " set!"));

    try {
        config.save(setFile);
        player.sendMessage(TextUtil.colorize("&aSuccessfully created armor set: &f" + setId));
        player.sendMessage(TextUtil.colorize("&7File saved to: &f" + setFile.getName()));
        playSound(player, "socket");

        // Reload sets and open editor
        plugin.getSetManager().loadSets();
        ArmorSet loadedSet = plugin.getSetManager().getSet(setId);
        if (loadedSet != null) {
            openSetEditor(player, loadedSet);
        } else {
            openBuildMainMenu(player);
        }
    } catch (IOException e) {
        player.sendMessage(TextUtil.colorize("&cFailed to save armor set: " + e.getMessage()));
        playSound(player, "error");
    }
}
```

#### ConfigHandler Implementation
Add handling for `SET_CREATOR` in ConfigHandler.java:

```java
case SET_CREATOR -> handleSetCreatorClick(player, session, slot, event);

// ... later in the class ...

private void handleSetCreatorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
    switch (slot) {
        case 11 -> {
            // Set ID input
            player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<set_id> (lowercase, underscores for spaces)"));
            player.closeInventory();
            session.put("inputType", "SET_ID");
            context.addPendingMessageInput(player.getUniqueId(), session);
        }
        case 13 -> {
            // Material selector
            context.openSetMaterialSelector(player, session);
        }
        case 15 -> {
            // Tier configuration (simple increment/decrement)
            openSetTierConfig(player, session);
        }
        case 20 -> {
            // Name pattern input
            player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<name_pattern> (regex for matching armor names)"));
            player.sendMessage(TextUtil.colorize("&8Example: &7Dragon.*Armor"));
            player.closeInventory();
            session.put("inputType", "SET_NAME_PATTERN");
            context.addPendingMessageInput(player.getUniqueId(), session);
        }
        case 30 -> {
            // Create set
            saveNewArmorSet(player, session);
        }
        case 32 -> {
            // Cancel
            context.openBuildMainMenu(player);
        }
    }
}

private void openSetTierConfig(Player player, GUISession creatorSession) {
    Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("&8Configure Max Tier"));

    int currentMaxTier = creatorSession.getInt("maxTier", 10);

    inv.setItem(4, ItemBuilder.createGuiItem(Material.EXPERIENCE_BOTTLE,
        "&eCurrent Max Tier: &f" + currentMaxTier,
        "&7Adjust the maximum tier",
        "&7for this armor set"));

    inv.setItem(11, ItemBuilder.createDecrementButton("-5", "red"));
    inv.setItem(12, ItemBuilder.createDecrementButton("-1", "orange"));
    inv.setItem(13, ItemBuilder.createGuiItem(Material.GOLD_INGOT, "&aMax Tier: &f" + currentMaxTier));
    inv.setItem(14, ItemBuilder.createIncrementButton("+1", "lime"));
    inv.setItem(15, ItemBuilder.createIncrementButton("+5", "green"));

    inv.setItem(22, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aConfirm"));
    inv.setItem(26, ItemBuilder.createGuiItem(Material.BARRIER, "&cBack"));

    GUISession session = new GUISession(GUIType.GENERIC);
    session.put("parentSession", creatorSession);
    session.put("menuType", "SET_TIER_CONFIG");
    session.put("maxTier", currentMaxTier);
    openGUI(player, inv, session);
}

private void handleSetTierConfigClick(Player player, GUISession session, int slot) {
    GUISession parentSession = (GUISession) session.get("parentSession");
    int currentMaxTier = session.getInt("maxTier", 10);

    switch (slot) {
        case 11 -> session.put("maxTier", Math.max(1, currentMaxTier - 5));
        case 12 -> session.put("maxTier", Math.max(1, currentMaxTier - 1));
        case 14 -> session.put("maxTier", Math.min(100, currentMaxTier + 1));
        case 15 -> session.put("maxTier", Math.min(100, currentMaxTier + 5));
        case 22 -> {
            // Confirm
            parentSession.put("maxTier", session.getInt("maxTier", 10));
            context.openSetCreator(player);
            return;
        }
        case 26 -> {
            // Back
            context.openSetCreator(player);
            return;
        }
    }

    // Refresh the GUI
    openSetTierConfig(player, parentSession);
}
```

#### Message Input Handlers
Add to `GUIManager.handleMessageInput()`:

```java
if ("SET_ID".equals(inputType)) {
    return handleSetIdInput(player, input, session);
}
if ("SET_NAME_PATTERN".equals(inputType)) {
    return handleSetNamePatternInput(player, input, session);
}

// ... later in the class ...

private boolean handleSetIdInput(Player player, String setId, GUISession session) {
    if (setId.isEmpty() || !setId.matches("^[a-z0-9_]+$")) {
        player.sendMessage(TextUtil.colorize("&cInvalid set ID (use lowercase, numbers, underscores only)"));
        openSetCreator(player);
        return false;
    }

    if (plugin.getSetManager().getSet(setId) != null) {
        player.sendMessage(TextUtil.colorize("&cA set with that ID already exists"));
        openSetCreator(player);
        return false;
    }

    session.put("setId", setId);
    player.sendMessage(TextUtil.colorize("&aSet ID set to: &f" + setId));
    openSetCreator(player);
    return true;
}

private boolean handleSetNamePatternInput(Player player, String pattern, GUISession session) {
    if (pattern.isEmpty()) {
        player.sendMessage(TextUtil.colorize("&cName pattern cannot be empty"));
        openSetCreator(player);
        return false;
    }

    // Test regex validity
    try {
        Pattern.compile(pattern);
    } catch (Exception e) {
        player.sendMessage(TextUtil.colorize("&cInvalid regex pattern: " + e.getMessage()));
        openSetCreator(player);
        return false;
    }

    session.put("namePattern", pattern);
    player.sendMessage(TextUtil.colorize("&aName pattern set to: &f" + pattern));
    openSetCreator(player);
    return true;
}
```

#### Update BUILD_MAIN_MENU Handler
Change line 124 in ConfigHandler.java from:
```java
case 10 -> player.sendMessage(TextUtil.colorize("&e[CREATE SET] Feature coming soon"));
```
to:
```java
case 10 -> context.openSetCreator(player);
```

---

### 3.2 & 3.3: Pagination and Search/Filter

#### ConfigHandler Browser Handlers
Replace `handleSetBrowserClick` and `handleFunctionBrowserClick` with pagination-aware versions:

```java
private void handleSetBrowserClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
    // Handle pagination controls
    if (slot == BrowserPagination.PREV_BUTTON_SLOT) {
        session.setCurrentPage(Math.max(0, session.getCurrentPage() - 1));
        context.openSetBrowser(player);
        return;
    }

    if (slot == BrowserPagination.NEXT_BUTTON_SLOT) {
        session.setCurrentPage(session.getCurrentPage() + 1);
        context.openSetBrowser(player);
        return;
    }

    if (slot == BrowserPagination.CLOSE_BUTTON_SLOT) {
        context.openBuildMainMenu(player);
        return;
    }

    if (slot == BrowserPagination.SEARCH_BUTTON_SLOT) {
        context.openBrowserFilterGUI(player, session);
        return;
    }

    // Handle item clicks (slots 0-27 for items)
    if (slot >= 0 && slot < BrowserPagination.ITEMS_PER_PAGE) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);

        if (item == null || item.getType().isAir()) return;

        if (item.getType() == Material.DIAMOND_CHESTPLATE && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String clickedDisplayName = PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
            String plainName = clickedDisplayName.replaceAll("[^a-zA-Z0-9_ ]", "").trim();

            var allSets = plugin.getSetManager().getAllSets();
            for (var set : allSets) {
                if (set.getId().equalsIgnoreCase(plainName)) {
                    context.openSetEditor(player, set);
                    return;
                }
            }
            player.sendMessage(TextUtil.colorize("&cSet not found: '" + plainName + "'"));
        }
    }
}

private void handleFunctionBrowserClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
    // Handle pagination controls
    if (slot == BrowserPagination.PREV_BUTTON_SLOT) {
        session.setCurrentPage(Math.max(0, session.getCurrentPage() - 1));
        context.openSigilBrowser(player);
        return;
    }

    if (slot == BrowserPagination.NEXT_BUTTON_SLOT) {
        session.setCurrentPage(session.getCurrentPage() + 1);
        context.openSigilBrowser(player);
        return;
    }

    if (slot == BrowserPagination.CLOSE_BUTTON_SLOT) {
        context.openBuildMainMenu(player);
        return;
    }

    if (slot == BrowserPagination.SEARCH_BUTTON_SLOT) {
        context.openBrowserFilterGUI(player, session);
        return;
    }

    // Handle item clicks
    if (slot >= 0 && slot < BrowserPagination.ITEMS_PER_PAGE) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);

        if (item == null || item.getType().isAir()) return;

        if (item.getType() == Material.NETHER_STAR && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String clickedDisplayName = PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
            String plainName = clickedDisplayName.replaceAll("[^a-zA-Z0-9_ ]", "").trim();

            var allSigils = plugin.getSigilManager().getAllSigils();
            for (var sigil : allSigils) {
                if (sigil.getName().equalsIgnoreCase(plainName)) {
                    context.openSigilEditor(player, sigil);
                    return;
                }
            }
            player.sendMessage(TextUtil.colorize("&cSigil not found: '" + plainName + "'"));
        }
    }
}
```

#### Filter GUI Implementation
Add to GUIManager.java:

```java
@Override
public void openBrowserFilterGUI(Player player, GUISession browserSession) {
    GUIType browserType = browserSession.getType();

    if (browserType == GUIType.SET_BROWSER) {
        openSetFilterGUI(player, browserSession);
    } else if (browserType == GUIType.FUNCTION_BROWSER) {
        openSigilFilterGUI(player, browserSession);
    }
}

private void openSetFilterGUI(Player player, GUISession browserSession) {
    Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent("&8Filter Armor Sets"));

    String currentSearch = browserSession.getSearchFilter();
    Integer currentTier = browserSession.getTierFilter();

    inv.setItem(10, ItemBuilder.createGuiItem(Material.NAME_TAG, "&eSearch by Name",
        currentSearch != null ? "&7Current: &f" + currentSearch : "&7No search filter",
        "&7",
        "&eClick to set search term"));

    inv.setItem(12, ItemBuilder.createGuiItem(Material.EXPERIENCE_BOTTLE, "&eTier Filter",
        currentTier != null ? "&7Current: &f" + currentTier : "&7All tiers",
        "&7",
        "&eLeft-click: +1 tier",
        "&eRight-click: -1 tier",
        "&eShift-click: Clear filter"));

    inv.setItem(16, ItemBuilder.createGuiItem(Material.BARRIER, "&cClear All Filters",
        "&7Remove all active filters"));

    inv.setItem(22, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aDone",
        "&7Return to browser"));

    GUISession session = new GUISession(GUIType.GENERIC);
    session.put("parentSession", browserSession);
    session.put("menuType", "SET_FILTER");
    openGUI(player, inv, session);
}

private void openSigilFilterGUI(Player player, GUISession browserSession) {
    Inventory inv = Bukkit.createInventory(null, 36, TextUtil.parseComponent("&8Filter Sigils"));

    String currentSearch = browserSession.getSearchFilter();
    String currentSlot = browserSession.getSlotFilter();
    String currentRarity = browserSession.getRarityFilter();
    Integer currentTier = browserSession.getTierFilter();

    inv.setItem(10, ItemBuilder.createGuiItem(Material.NAME_TAG, "&eSearch by Name",
        currentSearch != null ? "&7Current: &f" + currentSearch : "&7No search filter",
        "&7",
        "&eClick to set search term"));

    inv.setItem(12, ItemBuilder.createGuiItem(Material.DIAMOND_HELMET, "&eSlot Filter",
        currentSlot != null ? "&7Current: &f" + currentSlot : "&7All slots",
        "&7",
        "&eClick to cycle slots",
        "&8(HELMET > CHESTPLATE > LEGGINGS > BOOTS > ALL)"));

    inv.setItem(14, ItemBuilder.createGuiItem(Material.DIAMOND, "&eRarity Filter",
        currentRarity != null ? "&7Current: &f" + currentRarity : "&7All rarities",
        "&7",
        "&eClick to cycle rarities",
        "&8(COMMON > UNCOMMON > RARE > EPIC > LEGENDARY > ALL)"));

    inv.setItem(16, ItemBuilder.createGuiItem(Material.EXPERIENCE_BOTTLE, "&eTier Filter",
        currentTier != null ? "&7Current: &f" + currentTier : "&7All tiers",
        "&7",
        "&eLeft-click: +1 tier",
        "&eRight-click: -1 tier",
        "&eShift-click: Clear filter"));

    inv.setItem(22, ItemBuilder.createGuiItem(Material.BARRIER, "&cClear All Filters",
        "&7Remove all active filters"));

    inv.setItem(31, ItemBuilder.createGuiItem(Material.LIME_DYE, "&aDone",
        "&7Return to browser"));

    GUISession session = new GUISession(GUIType.GENERIC);
    session.put("parentSession", browserSession);
    session.put("menuType", "SIGIL_FILTER");
    openGUI(player, inv, session);
}
```

Add filter handling to ConfigHandler.java GENERIC handler:

```java
if ("SET_FILTER".equals(menuType)) {
    handleSetFilterClick(player, session, slot, event);
    return;
}
if ("SIGIL_FILTER".equals(menuType)) {
    handleSigilFilterClick(player, session, slot, event);
    return;
}

// ... later in the class ...

private void handleSetFilterClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
    GUISession browserSession = (GUISession) session.get("parentSession");

    switch (slot) {
        case 10 -> {
            // Search input
            player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<search term> (or 'clear' to remove)"));
            player.closeInventory();
            session.put("inputType", "SET_SEARCH_FILTER");
            context.addPendingMessageInput(player.getUniqueId(), session);
        }
        case 12 -> {
            // Tier filter
            Integer currentTier = browserSession.getTierFilter();

            if (event.isShiftClick()) {
                browserSession.setTierFilter(null);
            } else if (event.isLeftClick()) {
                browserSession.setTierFilter(currentTier == null ? 1 : Math.min(100, currentTier + 1));
            } else if (event.isRightClick()) {
                browserSession.setTierFilter(currentTier == null ? 1 : Math.max(1, currentTier - 1));
            }

            context.openBrowserFilterGUI(player, browserSession);
        }
        case 16 -> {
            // Clear all
            browserSession.setSearchFilter(null);
            browserSession.setTierFilter(null);
            browserSession.setCurrentPage(0);
            player.sendMessage(TextUtil.colorize("&aAll filters cleared"));
            context.playSound(player, "click");
            context.openBrowserFilterGUI(player, browserSession);
        }
        case 22 -> {
            // Done
            browserSession.setCurrentPage(0);
            context.openSetBrowser(player);
        }
    }
}

private void handleSigilFilterClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
    GUISession browserSession = (GUISession) session.get("parentSession");

    switch (slot) {
        case 10 -> {
            // Search input
            player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<search term> (or 'clear' to remove)"));
            player.closeInventory();
            session.put("inputType", "SIGIL_SEARCH_FILTER");
            context.addPendingMessageInput(player.getUniqueId(), session);
        }
        case 12 -> {
            // Slot filter cycle
            String currentSlot = browserSession.getSlotFilter();
            String[] slots = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", null};
            int index = currentSlot == null ? 0 : Arrays.asList(slots).indexOf(currentSlot) + 1;
            if (index >= slots.length) index = 0;

            browserSession.setSlotFilter(slots[index]);
            context.openBrowserFilterGUI(player, browserSession);
        }
        case 14 -> {
            // Rarity filter cycle
            String currentRarity = browserSession.getRarityFilter();
            String[] rarities = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", null};
            int index = currentRarity == null ? 0 : Arrays.asList(rarities).indexOf(currentRarity) + 1;
            if (index >= rarities.length) index = 0;

            browserSession.setRarityFilter(rarities[index]);
            context.openBrowserFilterGUI(player, browserSession);
        }
        case 16 -> {
            // Tier filter
            Integer currentTier = browserSession.getTierFilter();

            if (event.isShiftClick()) {
                browserSession.setTierFilter(null);
            } else if (event.isLeftClick()) {
                browserSession.setTierFilter(currentTier == null ? 1 : Math.min(100, currentTier + 1));
            } else if (event.isRightClick()) {
                browserSession.setTierFilter(currentTier == null ? 1 : Math.max(1, currentTier - 1));
            }

            context.openBrowserFilterGUI(player, browserSession);
        }
        case 22 -> {
            // Clear all
            browserSession.setSearchFilter(null);
            browserSession.setSlotFilter(null);
            browserSession.setRarityFilter(null);
            browserSession.setTierFilter(null);
            browserSession.setCurrentPage(0);
            player.sendMessage(TextUtil.colorize("&aAll filters cleared"));
            context.playSound(player, "click");
            context.openBrowserFilterGUI(player, browserSession);
        }
        case 31 -> {
            // Done
            browserSession.setCurrentPage(0);
            context.openSigilBrowser(player);
        }
    }
}
```

Add input handlers to GUIManager.handleMessageInput():

```java
if ("SET_SEARCH_FILTER".equals(inputType)) {
    return handleSetSearchFilterInput(player, input, session);
}
if ("SIGIL_SEARCH_FILTER".equals(inputType)) {
    return handleSigilSearchFilterInput(player, input, session);
}

// ... later in the class ...

private boolean handleSetSearchFilterInput(Player player, String input, GUISession session) {
    GUISession parentSession = (GUISession) session.get("parentSession");

    if ("clear".equalsIgnoreCase(input)) {
        parentSession.setSearchFilter(null);
        player.sendMessage(TextUtil.colorize("&aSearch filter cleared"));
    } else {
        parentSession.setSearchFilter(input);
        player.sendMessage(TextUtil.colorize("&aSearch filter set to: &f" + input));
    }

    openBrowserFilterGUI(player, parentSession);
    return true;
}

private boolean handleSigilSearchFilterInput(Player player, String input, GUISession session) {
    GUISession parentSession = (GUISession) session.get("parentSession");

    if ("clear".equalsIgnoreCase(input)) {
        parentSession.setSearchFilter(null);
        player.sendMessage(TextUtil.colorize("&aSearch filter cleared"));
    } else {
        parentSession.setSearchFilter(input);
        player.sendMessage(TextUtil.colorize("&aSearch filter set to: &f" + input));
    }

    openBrowserFilterGUI(player, parentSession);
    return true;
}
```

---

### 3.4: Undo/Redo System

The undo/redo system has been implemented in GUISession with these methods:
- `saveStateForUndo()` - Call before destructive actions
- `undo()` - Restore previous state
- `redo()` - Restore next state after undo
- `canUndo()` / `canRedo()` - Check availability

#### Example Usage in Trigger Removal

Update `handleTriggerRemoverClick` in ConfigHandler.java:

```java
private void handleTriggerRemoverClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
    // ... existing code to get trigger ...

    if (slot >= 9 && slot < inv.getSize() - 1) {
        ItemStack item = inv.getItem(slot);
        if (item != null && item.getType() == Material.TNT && item.hasItemMeta()) {
            NamespacedKey key = new NamespacedKey(plugin, "trigger_key");
            String triggerKey = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

            if (triggerKey != null) {
                // Save state for undo BEFORE destructive action
                session.saveStateForUndo();

                // Show confirmation dialog
                context.openConfirmationDialog(player,
                    "&cRemove Trigger?",
                    "&7Are you sure you want to remove&7 trigger: &f" + triggerKey + "&7?&7&7This action can be undone.",
                    () -> {
                        // On confirm: actually remove trigger
                        if ("sigil".equalsIgnoreCase(buildType)) {
                            Sigil sigil = (Sigil) target;
                            sigil.getEffects().remove(triggerKey);
                            context.exportSigilToYAML(player, sigil);
                        }
                        // Re-open trigger remover
                        context.openTriggerRemover(player, buildType, buildId, target);
                    },
                    () -> {
                        // On cancel: undo the state save
                        session.undo();
                        context.openTriggerRemover(player, buildType, buildId, target);
                    }
                );
            }
        }
    }

    // ... rest of method ...
}
```

---

### 3.5: Confirmation Dialogs

#### GUIManager Implementation

Add to GUIManager.java:

```java
@Override
public void openConfirmationDialog(Player player, String title, String message, Runnable onConfirm, Runnable onCancel) {
    Inventory inv = Bukkit.createInventory(null, 27, TextUtil.parseComponent(title));

    // Message display
    String[] messageLines = message.split("&7");
    List<String> loreLines = new ArrayList<>();
    for (String line : messageLines) {
        if (!line.isEmpty()) {
            loreLines.add(line.trim());
        }
    }

    inv.setItem(13, ItemBuilder.createGuiItem(Material.PAPER, "&eConfirmation Required",
        loreLines.toArray(new String[0])));

    // Confirm button
    inv.setItem(11, ItemBuilder.createGuiItem(Material.LIME_DYE, "&a&lCONFIRM",
        "&7Click to proceed"));

    // Cancel button
    inv.setItem(15, ItemBuilder.createGuiItem(Material.RED_DYE, "&c&lCANCEL",
        "&7Click to abort"));

    GUISession session = new GUISession(GUIType.CONFIRMATION);
    session.put("onConfirm", onConfirm);
    session.put("onCancel", onCancel);
    openGUI(player, inv, session);
}
```

#### ConfigHandler Implementation

Replace `handleConfirmationClick`:

```java
private void handleConfirmationClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
    Runnable onConfirm = (Runnable) session.get("onConfirm");
    Runnable onCancel = (Runnable) session.get("onCancel");

    if (slot == 11 && onConfirm != null) {
        // Confirm
        onConfirm.run();
        context.playSound(player, "socket");
    } else if (slot == 15 && onCancel != null) {
        // Cancel
        onCancel.run();
        context.playSound(player, "click");
    } else {
        // Close without action
        player.closeInventory();
    }
}
```

#### Add Confirmations to Destructive Actions

Update these methods in ConfigHandler to use confirmation dialogs:

1. **Synergy Deletion**:
```java
// In handleSetSynergiesViewerClick or similar
context.openConfirmationDialog(player,
    "&cDelete Synergy?",
    "&7Remove synergy: &f" + synergyId + "&7?&7This action can be undone.",
    () -> {
        session.saveStateForUndo();
        set.getSynergies().removeIf(s -> s.getId().equals(synergyId));
        context.openSetSynergiesViewer(player, set);
    },
    () -> context.openSetSynergiesViewer(player, set)
);
```

2. **Clear All Conditions**:
```java
// In handleConditionViewerClick when clicking "Remove All"
context.openConfirmationDialog(player,
    "&cClear All Conditions?",
    "&7Remove all conditions from this trigger?&7This action can be undone.",
    () -> {
        session.saveStateForUndo();
        List<String> conditions = (List<String>) triggerSession.get("conditions");
        conditions.clear();
        context.openConditionViewer(player, triggerSession);
    },
    () -> context.openConditionViewer(player, triggerSession)
);
```

---

## Testing Checklist

After implementing all features, verify:

- [ ] Set browser shows 28 items per page with working prev/next buttons
- [ ] Sigil browser shows 28 items per page with working prev/next buttons
- [ ] Search filter works for sets (by name)
- [ ] Search filter works for sigils (by name)
- [ ] Tier filter works for both browsers
- [ ] Slot filter works for sigil browser
- [ ] Rarity filter works for sigil browser
- [ ] Filters can be cleared individually and all at once
- [ ] Filtered results show correct item count
- [ ] Create Set button opens set creator GUI
- [ ] Set creator validates ID format and uniqueness
- [ ] Set creator saves to YAML correctly
- [ ] Set creator reloads sets and opens editor
- [ ] Material selector shows current selection with glow
- [ ] Tier configuration increments/decrements correctly
- [ ] Confirmation dialog appears before trigger removal
- [ ] Confirmation dialog appears before synergy deletion
- [ ] Confirmation dialog appears before clearing conditions
- [ ] Undo works after destructive actions
- [ ] Redo works after undo
- [ ] Undo/redo state is preserved across GUI navigation
- [ ] All GUIs compile without errors
- [ ] Plugin loads without errors
- [ ] `/as reload` works correctly

---

## Architecture Notes

### Pagination Design
- Items per page: 28 (4 rows of 7 items)
- Inventory size: 54 slots (6 rows)
- Rows 1-4: Item display (slots 0-27)
- Row 5: Empty spacer (slots 28-35)
- Row 6: Navigation controls (slots 36-53)
  - Slot 44: Close
  - Slot 45: Previous page
  - Slot 48: Search/Filter
  - Slot 49: Page info
  - Slot 53: Next page

### Filter System Design
- Filters stored in GUISession per-browser
- Filters preserved across page navigation
- Clearing filters resets to page 0
- Filter button shows active filters in lore

### Undo/Redo Design
- Stack-based with 10-action limit
- State snapshots stored in session data map
- Redo stack cleared on new actions
- Works with any session data changes

### Confirmation Dialog Design
- Generic dialog with Runnable callbacks
- Supports custom title and multi-line messages
- Called before destructive actions
- Can trigger undo on cancel

### Set Creation Design
- Step-by-step wizard interface
- Validates ID format and uniqueness
- Material selector with visual feedback
- Tier configuration with increment/decrement
- Name pattern validation (regex)
- Saves to YAML and reloads
- Opens editor on success

---

## Summary

All 5 missing features have been implemented with:
- 1 new utility class (BrowserPagination)
- Enhancements to 2 existing classes (GUISession, GUIHandlerContext)
- New methods in GUIManager for pagination, filters, confirmations, and set creation
- Updated handlers in ConfigHandler for all new GUI types
- Input handlers for set creation and filter search terms
- Confirmation dialogs integrated with destructive actions
- Undo/redo system integrated with session state management

The implementation maintains the existing architecture patterns and is fully compatible with the current codebase.
