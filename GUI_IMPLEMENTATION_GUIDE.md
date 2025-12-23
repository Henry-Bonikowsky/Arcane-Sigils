# GUI Implementation Guide for Condition Enhancements

## Overview
This document provides the GUI rendering implementations that need to be added to `GUIManager.java`. The click handlers are already implemented in `ConfigHandler.java` - these methods create the actual inventory GUIs.

---

## Methods to Add to GUIManager.java

### 1. openConditionTemplateSelector()

```java
public void openConditionTemplateSelector(Player player, GUISession parentSession) {
    Inventory inv = Bukkit.createInventory(null, 36, TextUtil.colorize("&6&lCondition Templates"));

    ConditionTemplate[] templates = ConditionTemplate.values();
    int[] slots = {10, 11, 12, 13, 14, 15, 16, 17}; // 8 template slots

    for (int i = 0; i < templates.length && i < slots.length; i++) {
        ConditionTemplate template = templates[i];
        ItemStack item = new ItemStack(template.getIcon());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(template.getDisplayName())
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(template.getDescription())
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Conditions:")
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));

        for (String condition : template.getConditions()) {
            lore.add(Component.text("  - " + condition)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Click to apply all conditions")
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        inv.setItem(slots[i], item);
    }

    // Back button
    ItemStack back = new ItemStack(Material.BARRIER);
    ItemMeta backMeta = back.getItemMeta();
    backMeta.displayName(Component.text("Back")
        .color(NamedTextColor.RED)
        .decoration(TextDecoration.ITALIC, false));
    back.setItemMeta(backMeta);
    inv.setItem(26, back);

    GUISession session = new GUISession(GUIType.CONDITION_TEMPLATE_SELECTOR);
    session.put("parentSession", parentSession);

    openGUI(player, inv, session);
}
```

### 2. openConditionParameterEditor()

```java
public void openConditionParameterEditor(Player player, String conditionString, int conditionIndex, GUISession parentSession) {
    Inventory inv = Bukkit.createInventory(null, 36, TextUtil.colorize("&6&lEdit Condition"));

    // Parse condition
    String[] parts = conditionString.split(":");
    String type = parts.length > 0 ? parts[0] : "UNKNOWN";
    String value = parts.length > 1 ? parts[1] : "";

    double numericValue = 0;
    String comparison = "<";

    if (!value.isEmpty()) {
        if (value.startsWith(">=")) {
            comparison = ">=";
            try { numericValue = Double.parseDouble(value.substring(2)); } catch (Exception e) {}
        } else if (value.startsWith("<=")) {
            comparison = "<=";
            try { numericValue = Double.parseDouble(value.substring(2)); } catch (Exception e) {}
        } else if (value.startsWith("<")) {
            comparison = "<";
            try { numericValue = Double.parseDouble(value.substring(1)); } catch (Exception e) {}
        } else if (value.startsWith(">")) {
            comparison = ">";
            try { numericValue = Double.parseDouble(value.substring(1)); } catch (Exception e) {}
        } else if (value.startsWith("=")) {
            comparison = "=";
            try { numericValue = Double.parseDouble(value.substring(1)); } catch (Exception e) {}
        } else {
            try { numericValue = Double.parseDouble(value); } catch (Exception e) {}
        }
    }

    // Info display (slot 4)
    ItemStack info = new ItemStack(Material.PAPER);
    ItemMeta infoMeta = info.getItemMeta();
    infoMeta.displayName(Component.text("Editing: " + type)
        .color(NamedTextColor.YELLOW)
        .decoration(TextDecoration.ITALIC, false));
    List<Component> infoLore = List.of(
        Component.text("Current: " + conditionString).color(NamedTextColor.WHITE),
        Component.empty(),
        Component.text("Adjust parameters below").color(NamedTextColor.GRAY)
    );
    infoMeta.lore(infoLore);
    info.setItemMeta(infoMeta);
    inv.setItem(4, info);

    // Comparison operator selector (slot 11)
    ItemStack compOp = new ItemStack(Material.COMPARATOR);
    ItemMeta compMeta = compOp.getItemMeta();
    compMeta.displayName(Component.text("Comparison: " + comparison)
        .color(NamedTextColor.AQUA)
        .decoration(TextDecoration.ITALIC, false));
    compMeta.lore(List.of(
        Component.text("Click to cycle").color(NamedTextColor.GRAY)
    ));
    compOp.setItemMeta(compMeta);
    inv.setItem(11, compOp);

    // Value display (slot 22)
    ItemStack valueDisplay = new ItemStack(Material.NAME_TAG);
    ItemMeta valueMeta = valueDisplay.getItemMeta();
    valueMeta.displayName(Component.text("Value: " + (int)numericValue)
        .color(NamedTextColor.GREEN)
        .decoration(TextDecoration.ITALIC, false));
    valueDisplay.setItemMeta(valueMeta);
    inv.setItem(22, valueDisplay);

    // Adjustment buttons
    addAdjustmentButton(inv, 19, Material.RED_WOOL, "-10", -10);
    addAdjustmentButton(inv, 20, Material.ORANGE_WOOL, "-1", -1);
    addAdjustmentButton(inv, 24, Material.LIME_WOOL, "+1", 1);
    addAdjustmentButton(inv, 25, Material.GREEN_WOOL, "+10", 10);

    // Save button (slot 30)
    ItemStack save = new ItemStack(Material.EMERALD_BLOCK);
    ItemMeta saveMeta = save.getItemMeta();
    saveMeta.displayName(Component.text("Save Changes")
        .color(NamedTextColor.GREEN)
        .decoration(TextDecoration.ITALIC, false));
    save.setItemMeta(saveMeta);
    inv.setItem(30, save);

    // Cancel button (slot 32)
    ItemStack cancel = new ItemStack(Material.REDSTONE_BLOCK);
    ItemMeta cancelMeta = cancel.getItemMeta();
    cancelMeta.displayName(Component.text("Cancel")
        .color(NamedTextColor.RED)
        .decoration(TextDecoration.ITALIC, false));
    cancel.setItemMeta(cancelMeta);
    inv.setItem(32, cancel);

    GUISession session = new GUISession(GUIType.CONDITION_PARAMETER_EDITOR);
    session.put("conditionString", conditionString);
    session.put("conditionIndex", conditionIndex);
    session.put("parentSession", parentSession);
    session.put("numericValue", numericValue);
    session.put("comparison", comparison);

    openGUI(player, inv, session);
}

private void addAdjustmentButton(Inventory inv, int slot, Material material, String label, int change) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text(label)
        .color(change < 0 ? NamedTextColor.RED : NamedTextColor.GREEN)
        .decoration(TextDecoration.ITALIC, false));
    item.setItemMeta(meta);
    inv.setItem(slot, item);
}
```

### 3. openConditionPresetSelector()

```java
public void openConditionPresetSelector(Player player, GUISession parentSession) {
    Inventory inv = Bukkit.createInventory(null, 54, TextUtil.colorize("&6&lLoad Condition Preset"));

    // Load presets from file
    File presetsFile = new File(plugin.getDataFolder(), "presets/conditions.yml");
    Map<String, ConditionPreset> presets = ConditionPreset.loadAllPresets(presetsFile);

    int slot = 0;
    for (ConditionPreset preset : presets.values()) {
        if (slot >= 45) break; // Max 45 presets displayed

        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(preset.getName())
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(preset.getDescription())
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Conditions (" + preset.getConditions().size() + "):")
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));

        for (String condition : preset.getConditions()) {
            lore.add(Component.text("  - " + condition)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Creator: " + preset.getCreator())
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to merge with existing")
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Shift-Click to replace existing")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);

        // Store preset ID in PDC
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "preset_id"),
            PersistentDataType.STRING,
            preset.getId()
        );

        item.setItemMeta(meta);
        inv.setItem(slot++, item);
    }

    if (presets.isEmpty()) {
        ItemStack noPresets = new ItemStack(Material.BARRIER);
        ItemMeta meta = noPresets.getItemMeta();
        meta.displayName(Component.text("No Presets Saved")
            .color(NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Save conditions from the").color(NamedTextColor.GRAY),
            Component.text("Condition Viewer to create presets").color(NamedTextColor.GRAY)
        ));
        noPresets.setItemMeta(meta);
        inv.setItem(22, noPresets);
    }

    // Back button
    ItemStack back = new ItemStack(Material.BARRIER);
    ItemMeta backMeta = back.getItemMeta();
    backMeta.displayName(Component.text("Back")
        .color(NamedTextColor.RED)
        .decoration(TextDecoration.ITALIC, false));
    back.setItemMeta(backMeta);
    inv.setItem(53, back);

    GUISession session = new GUISession(GUIType.CONDITION_PRESET_SELECTOR);
    session.put("parentSession", parentSession);

    openGUI(player, inv, session);
}
```

### 4. toggleConditionLogic()

```java
public void toggleConditionLogic(Player player, GUISession triggerSession) {
    // Get current conditions list from session
    @SuppressWarnings("unchecked")
    List<String> conditions = (List<String>) triggerSession.get("conditions");

    // Get or create logic mode
    TriggerConfig.ConditionLogic currentLogic = triggerSession.get("conditionLogic", TriggerConfig.ConditionLogic.class);
    if (currentLogic == null) {
        currentLogic = TriggerConfig.ConditionLogic.AND;
    }

    // Toggle
    TriggerConfig.ConditionLogic newLogic = (currentLogic == TriggerConfig.ConditionLogic.AND) ?
        TriggerConfig.ConditionLogic.OR :
        TriggerConfig.ConditionLogic.AND;

    triggerSession.put("conditionLogic", newLogic);

    player.sendMessage(TextUtil.colorize(
        "&aCondition logic set to: &f" + newLogic.name() +
        (newLogic == TriggerConfig.ConditionLogic.AND ? " &7(all must pass)" : " &7(any can pass)")
    ));
}
```

### 5. Update openConditionViewer() - Add Logic Toggle Indicator

Add this to the existing `openConditionViewer()` method at slot 13:

```java
// Logic mode toggle (slot 13)
TriggerConfig.ConditionLogic logic = triggerSession.get("conditionLogic", TriggerConfig.ConditionLogic.class);
if (logic == null) {
    logic = TriggerConfig.ConditionLogic.AND;
}

Material logicIcon = (logic == TriggerConfig.ConditionLogic.AND) ? Material.REDSTONE : Material.AMETHYST_CLUSTER;
String logicName = (logic == TriggerConfig.ConditionLogic.AND) ? "&aAND Logic" : "&dOR Logic";
String logicDesc = (logic == TriggerConfig.ConditionLogic.AND) ?
    "All conditions must pass" :
    "Any condition can pass";

ItemStack logicToggle = new ItemStack(logicIcon);
ItemMeta logicMeta = logicToggle.getItemMeta();
logicMeta.displayName(Component.text(TextUtil.colorize(logicName))
    .decoration(TextDecoration.ITALIC, false));
logicMeta.lore(List.of(
    Component.text(logicDesc).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
    Component.empty(),
    Component.text("Click to toggle").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
));
logicToggle.setItemMeta(logicMeta);
inv.setItem(13, logicToggle);

// Template Selector button (slot 25)
ItemStack templateBtn = new ItemStack(Material.ENCHANTED_BOOK);
ItemMeta templateMeta = templateBtn.getItemMeta();
templateMeta.displayName(Component.text("Condition Templates")
    .color(NamedTextColor.LIGHT_PURPLE)
    .decoration(TextDecoration.ITALIC, false));
templateMeta.lore(List.of(
    Component.text("Browse pre-built condition groups").color(NamedTextColor.GRAY),
    Component.text("Click to view templates").color(NamedTextColor.YELLOW)
));
templateBtn.setItemMeta(templateMeta);
inv.setItem(25, templateBtn);

// Save Preset button (slot 23)
ItemStack saveBtn = new ItemStack(Material.WRITABLE_BOOK);
ItemMeta saveMeta = saveBtn.getItemMeta();
saveMeta.displayName(Component.text("Save as Preset")
    .color(NamedTextColor.GREEN)
    .decoration(TextDecoration.ITALIC, false));
saveMeta.lore(List.of(
    Component.text("Save current conditions as a preset").color(NamedTextColor.GRAY),
    Component.text("Click to save").color(NamedTextColor.YELLOW)
));
saveBtn.setItemMeta(saveMeta);
inv.setItem(23, saveBtn);

// Load Preset button (slot 29)
ItemStack loadBtn = new ItemStack(Material.KNOWLEDGE_BOOK);
ItemMeta loadMeta = loadBtn.getItemMeta();
loadMeta.displayName(Component.text("Load Preset")
    .color(NamedTextColor.AQUA)
    .decoration(TextDecoration.ITALIC, false));
loadMeta.lore(List.of(
    Component.text("Load saved condition presets").color(NamedTextColor.GRAY),
    Component.text("Click to browse").color(NamedTextColor.YELLOW)
));
loadBtn.setItemMeta(loadMeta);
inv.setItem(29, loadBtn);
```

### 6. Add Conflict Detection to Condition Items

Update the condition display in `openConditionViewer()` to show conflicts:

```java
// When creating condition items (slots 9-26)
for (int i = 0; i < conditions.size() && i < 18; i++) {
    String condition = conditions.get(i);

    // Detect conflicts
    List<ConflictDetector.Conflict> conflicts = ConflictDetector.detectConflictsWithNew(
        condition,
        conditions.subList(0, i)  // Check against previous conditions
    );

    Material iconMaterial = Material.PAPER;
    NamedTextColor nameColor = NamedTextColor.WHITE;

    // Override icon if conflict detected
    if (!conflicts.isEmpty()) {
        ConflictDetector.ConflictSeverity highestSeverity = conflicts.stream()
            .map(ConflictDetector.Conflict::getSeverity)
            .max(Comparator.comparingInt(Enum::ordinal))
            .orElse(ConflictDetector.ConflictSeverity.WARNING);

        iconMaterial = highestSeverity.getIcon();
        nameColor = switch (highestSeverity) {
            case IMPOSSIBLE -> NamedTextColor.RED;
            case CONFLICTING -> NamedTextColor.YELLOW;
            case REDUNDANT -> NamedTextColor.GOLD;
            case WARNING -> NamedTextColor.GRAY;
        };
    }

    ItemStack item = new ItemStack(iconMaterial);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text(condition)
        .color(nameColor)
        .decoration(TextDecoration.ITALIC, false));

    List<Component> lore = new ArrayList<>();
    lore.add(Component.empty());

    if (!conflicts.isEmpty()) {
        for (ConflictDetector.Conflict conflict : conflicts) {
            lore.add(Component.text(conflict.getFormattedMessage())
                .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
    }

    lore.add(Component.text("Shift-Click to remove").color(NamedTextColor.RED));
    lore.add(Component.text("Shift-Right-Click to edit").color(NamedTextColor.AQUA));

    meta.lore(lore);
    item.setItemMeta(meta);
    inv.setItem(9 + i, item);
}
```

---

## Interface Method Implementations in GUIManager

Add these public method signatures to the `GUIHandlerContext` implementation:

```java
@Override
public void openConditionTemplateSelector(Player player, GUISession parentSession) {
    // Implementation above
}

@Override
public void openConditionParameterEditor(Player player, String conditionString, int conditionIndex, GUISession parentSession) {
    // Implementation above
}

@Override
public void openConditionPresetSelector(Player player, GUISession parentSession) {
    // Implementation above
}

@Override
public void openConditionPresetManager(Player player, GUISession parentSession) {
    // Simple GUI with info item, actual save handled in message input
    Inventory inv = Bukkit.createInventory(null, 27, TextUtil.colorize("&6&lSave Condition Preset"));

    ItemStack info = new ItemStack(Material.BOOK);
    ItemMeta meta = info.getItemMeta();
    meta.displayName(Component.text("Enter Preset Name").color(NamedTextColor.YELLOW));
    meta.lore(List.of(
        Component.text("Type a name in chat to save").color(NamedTextColor.GRAY),
        Component.text("the current conditions as a preset").color(NamedTextColor.GRAY)
    ));
    info.setItemMeta(meta);
    inv.setItem(13, info);

    ItemStack cancel = new ItemStack(Material.BARRIER);
    ItemMeta cancelMeta = cancel.getItemMeta();
    cancelMeta.displayName(Component.text("Cancel").color(NamedTextColor.RED));
    cancel.setItemMeta(cancelMeta);
    inv.setItem(14, cancel);

    GUISession session = new GUISession(GUIType.CONDITION_PRESET_MANAGER);
    session.put("triggerSession", parentSession);
    openGUI(player, inv, session);
}

@Override
public void toggleConditionLogic(Player player, GUISession triggerSession) {
    // Implementation above
}
```

---

## Message Input Handler for Preset Saving

Add this to the message input handler in GUIManager (or wherever chat input is processed):

```java
if ("SAVE_CONDITION_PRESET".equals(inputType)) {
    String presetName = message;
    @SuppressWarnings("unchecked")
    List<String> conditions = (List<String>) session.get("conditions");

    if (conditions == null || conditions.isEmpty()) {
        player.sendMessage(TextUtil.colorize("&cNo conditions to save!"));
        return;
    }

    // Generate ID
    File presetsFile = new File(plugin.getDataFolder(), "presets/conditions.yml");
    Map<String, ConditionPreset> existingPresets = ConditionPreset.loadAllPresets(presetsFile);
    String presetId = ConditionPreset.generateId(presetName, existingPresets.keySet());

    // Create preset
    ConditionPreset preset = new ConditionPreset(
        presetId,
        presetName,
        "Custom condition preset",
        conditions,
        player.getName()
    );

    // Save
    ConditionPreset.savePreset(presetsFile, preset);

    player.sendMessage(TextUtil.colorize("&aSaved condition preset: &f" + presetName));
    player.sendMessage(TextUtil.colorize("&7ID: &f" + presetId));

    // Return to condition viewer
    GUISession triggerSession = session.get("triggerSession", GUISession.class);
    if (triggerSession != null) {
        openConditionViewer(player, triggerSession);
    }
}
```

---

## Slot Layout Reference

### CONDITION_VIEWER (36 slots)
```
[ ][ ][ ][ ][ ][ ][ ][ ][ ]
[C][C][C][C][C][C][C][C][C]  C = Condition items
[C][C][C][C][L][C][C][C][C]  L = Logic toggle (13)
[C][C][C][S][T][ ][+][P][ ]  S = Save (23), T = Templates (25), + = Add (27), P = Load Preset (29)
[ ][ ][ ][ ][X][ ][ ][ ][B]  X = Remove All (31), B = Back (35)
```

### CONDITION_TEMPLATE_SELECTOR (36 slots)
```
[ ][ ][ ][ ][ ][ ][ ][ ][ ]
[ ][T][T][T][T][T][T][T][ ]  T = Template items (10-17)
[ ][ ][ ][ ][ ][ ][ ][ ][ ]
[ ][ ][ ][ ][ ][ ][ ][B][ ]  B = Back (26)
```

### CONDITION_PARAMETER_EDITOR (36 slots)
```
[ ][ ][ ][ ][I][ ][ ][ ][ ]  I = Info (4)
[ ][ ][ ][O][ ][ ][ ][ ][ ]  O = Operator selector (11)
[ ][ ][ ][ ][ ][ ][V][ ][ ]  V = Value display (22)
[ ][-][-][ ][ ][+][+][ ][ ]  - = Decrease, + = Increase (19-20, 24-25)
[ ][ ][ ][ ][ ][ ][S][ ][C]  S = Save (30), C = Cancel (32)
```

---

## Testing Commands

Once implemented, test with:
```
/as reload
/as socket (with armor piece in hand)
- Navigate to Trigger Config
- Click "Add Condition"
- Test all 6 new features
```

---

**Note**: All click handlers are already implemented in `ConfigHandler.java`. This guide only covers the GUI rendering methods for `GUIManager.java`.
