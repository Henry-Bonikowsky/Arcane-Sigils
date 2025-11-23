# Feature Specification: Visual Effect Builder GUI with YAML Export

## Overview
A visual GUI-based tool for creating and editing armor sets and core functions without manual YAML editing. Admins can create complete configurations through an interactive inventory interface and automatically export them as properly-formatted YAML files.

---

## User Stories

### Story 1: Create New Armor Set
**As an** admin/server owner
**I want to** create a new armor set through a GUI
**So that** I don't have to manually write YAML and deal with formatting errors

**Acceptance Criteria:**
- Open GUI with command `/armorsets build set`
- Enter set name, base tier, description
- Assign individual piece effects (helmet, chest, legs, boots)
- Assign set synergies (full set bonuses)
- Preview the set in real-time
- Export as `<set-name>.yml` to `plugins/ArmorSets/sets/`
- YAML is properly formatted and ready to use

### Story 2: Create Core Function
**As an** admin
**I want to** design core functions with socket slots
**So that** I can customize what abilities players can use

**Acceptance Criteria:**
- Open GUI with `/armorsets build function`
- Select slot type (HELMET, CHESTPLATE, LEGGINGS, BOOTS)
- Add effects using effect picker
- Set item form (shard appearance, glow, enchants)
- Configure tier level
- Export as `<slot>-functions.yml` appended properly
- Function is immediately loadable

### Story 3: Edit Existing Configuration
**As an** admin
**I want to** edit an existing set without rewriting YAML
**So that** I can balance sets quickly

**Acceptance Criteria:**
- `/armorsets edit set arcanist` opens existing config in GUI
- All fields pre-populated
- Can modify any value
- Save overwrites original YAML
- Supports hot-reload

---

## Feature Requirements

### 1. Main Menu GUI
**Command:** `/armorsets build`

```
┌─────────────────────────────────────────┐
│    ⚔️ ARMOR SET BUILDER MAIN MENU ⚔️    │
├─────────────────────────────────────────┤
│                                         │
│  [📦] Create New Set                   │
│  [✨] Create New Function               │
│  [🔧] Edit Existing Set                │
│  [📝] Edit Existing Function            │
│  [📊] View All Sets                     │
│  [⚙️] Settings & Validation             │
│                                         │
│  Status: ✅ Plugin Ready               │
│  Sets: 42 | Functions: 156             │
│                                         │
└─────────────────────────────────────────┘
```

---

### 2. Set Creation Wizard

#### Step 1: Basic Info
```
┌──────────────────────────────────────────┐
│     SET BUILDER - STEP 1: BASIC INFO     │
├──────────────────────────────────────────┤
│                                          │
│  Set Name (ID):  [_________________]    │
│                  e.g., "arcanist"       │
│                                          │
│  Display Name:   [_________________]    │
│                  e.g., "Arcanist Set"   │
│                                          │
│  Base Tier:      [1] [2] [3] [4] [5]   │
│  (Can have multiple tiers)              │
│                                          │
│  Description:    [_________________]    │
│                  [_________________]    │
│                  (optional multi-line)  │
│                                          │
│  Material Type:  [NETHERITE ▼]         │
│                                          │
│         [◄ Back] [Next ►]               │
│                                          │
└──────────────────────────────────────────┘
```

**Data Collected:**
- `set_id`: String (auto-lowercase)
- `display_name`: String with color support
- `tier`: Integer (1-9)
- `description`: List of strings
- `material`: Material type

---

#### Step 2: Individual Piece Effects
```
┌──────────────────────────────────────────┐
│ SET BUILDER - STEP 2: PIECE EFFECTS      │
├──────────────────────────────────────────┤
│                                          │
│  Select Armor Piece:                    │
│  [🪖 HELMET] [🫀 CHESTPLATE]            │
│  [🦴 LEGGINGS] [👢 BOOTS]               │
│                                          │
│  HELMET Effects:                         │
│  ┌──────────────────────────────────┐   │
│  │ [+] Add Effect                   │   │
│  │                                  │   │
│  │ • EFFECT_STATIC                  │   │
│  │   └─ POTION:NIGHT_VISION:1      │   │
│  │      POTION:WATER_BREATHING:1   │   │
│  │   └─ Cooldown: 0                │   │
│  │   └─ Chance: 100%               │   │
│  │                                  │   │
│  │ [Remove] [Edit]                  │   │
│  └──────────────────────────────────┘   │
│                                          │
│         [◄ Back] [Next ►]               │
│                                          │
└──────────────────────────────────────────┘
```

**Interface Features:**
- Click piece icon to switch displayed piece
- `[+] Add Effect` button opens effect picker
- Each effect shows: trigger type, effects list, cooldown, chance
- `[Edit]` to modify existing effect
- `[Remove]` to delete effect
- Visual color coding: Red=ATTACK, Blue=DEFENSE, Green=KILL, etc.

---

#### Step 3: Add/Edit Effect Dialog
```
┌──────────────────────────────────────────┐
│      EFFECT EDITOR - POTION EFFECT       │
├──────────────────────────────────────────┤
│                                          │
│  Trigger Type: [EFFECT_STATIC ▼]         │
│  • ATTACK, DEFENSE, KILL_MOB             │
│  • KILL_PLAYER, SHIFT, FALL_DAMAGE       │
│  • EFFECT_STATIC                         │
│                                          │
│  Chance:        [100] %                  │
│  Cooldown:      [0] seconds              │
│                                          │
│  Effects to Apply:                       │
│  ┌──────────────────────────────────┐   │
│  │ [+] Add Effect Sub-Type          │   │
│  │                                  │   │
│  │ POTION:NIGHT_VISION:1            │   │
│  │  ├─ Type: [NIGHT_VISION ▼]       │   │
│  │  ├─ Duration: [1] (level)        │   │
│  │  └─ [Remove]                     │   │
│  │                                  │   │
│  │ POTION:WATER_BREATHING:1         │   │
│  │  ├─ Type: [WATER_BREATHING ▼]    │   │
│  │  ├─ Duration: [1] (level)        │   │
│  │  └─ [Remove]                     │   │
│  │                                  │   │
│  └──────────────────────────────────┘   │
│                                          │
│  Target: [@SELF ▼]                      │
│  • @SELF, @VICTIM, @NEARBY              │
│                                          │
│  Preview YAML:                           │
│  POTION:NIGHT_VISION:1                  │
│  POTION:WATER_BREATHING:1               │
│                                          │
│      [Cancel] [Save Effect]             │
│                                          │
└──────────────────────────────────────────┘
```

**Effect Picker Dropdown:**
```
Effect Type [POTION ▼]
├─ POTION
│  ├─ NIGHT_VISION
│  ├─ WATER_BREATHING
│  ├─ REGENERATION
│  ├─ STRENGTH
│  ├─ SPEED
│  ├─ INVISIBILITY
│  ├─ ... (all MC potion effects)
├─ PARTICLE
│  ├─ SOUL_FIRE_FLAME
│  ├─ SOUL
│  ├─ PORTAL
│  ├─ ... (all particle types)
├─ DAMAGE
│  ├─ INCREASE_DAMAGE
│  ├─ DISINTEGRATE
│  ├─ AEGIS
├─ MOVEMENT
│  ├─ TELEPORT_RANDOM
│  ├─ SMOKEBOMB
├─ HEALING
│  ├─ HEAL
│  ├─ DEVOUR
│  ├─ PATCH
├─ ... (complete effect list)
```

---

#### Step 4: Set Synergies (Full Set Bonuses)
```
┌──────────────────────────────────────────┐
│   SET BUILDER - STEP 4: SYNERGIES        │
├──────────────────────────────────────────┤
│                                          │
│  Set Synergies (when wearing 4/4):      │
│  ┌──────────────────────────────────┐   │
│  │ [+] Add Synergy                  │   │
│  │                                  │   │
│  │ 1️⃣  ARCANE_DISINTEGRATION       │   │
│  │     └─ Trigger: ATTACK           │   │
│  │     └─ Chance: 30%               │   │
│  │     └─ Cooldown: 3s              │   │
│  │     └─ Effects: 3                │   │
│  │     [Edit] [Remove] [Preview]    │   │
│  │                                  │   │
│  │ 2️⃣  AETHER_STEP                 │   │
│  │     └─ Trigger: DEFENSE          │   │
│  │     └─ Chance: 20%               │   │
│  │     └─ Cooldown: 8s              │   │
│  │     └─ Effects: 4                │   │
│  │     [Edit] [Remove] [Preview]    │   │
│  │                                  │   │
│  │ 3️⃣  SOUL_SIPHON                 │   │
│  │     └─ Trigger: KILL_MOB         │   │
│  │     └─ Chance: 100%              │   │
│  │     └─ Cooldown: 2s              │   │
│  │     └─ Effects: 4                │   │
│  │     [Edit] [Remove] [Preview]    │   │
│  │                                  │   │
│  └──────────────────────────────────┘   │
│                                          │
│         [◄ Back] [Next ►]               │
│                                          │
└──────────────────────────────────────────┘
```

---

#### Step 5: Review & Export
```
┌──────────────────────────────────────────┐
│    SET BUILDER - STEP 5: REVIEW          │
├──────────────────────────────────────────┤
│                                          │
│  📋 CONFIGURATION SUMMARY:               │
│                                          │
│  Set ID: arcanist_t1                    │
│  Display: Arcanist Set - Tier I         │
│  Tier: 1                                 │
│                                          │
│  Pieces with Effects:                    │
│  ✅ Helmet (1 trigger)                  │
│  ✅ Chestplate (1 trigger)              │
│  ✅ Leggings (1 trigger)                │
│  ✅ Boots (1 trigger)                   │
│                                          │
│  Synergies: 4                            │
│  ✅ Arcane Disintegration (ATTACK)      │
│  ✅ Aether Step (DEFENSE)               │
│  ✅ Soul Siphon (KILL_MOB)              │
│  ✅ Rift Walk (SHIFT)                   │
│                                          │
│  ⚠️ VALIDATION:                          │
│  ✅ All effects valid                   │
│  ✅ No cooldown conflicts               │
│  ✅ All pieces equipped                 │
│                                          │
│  Export Location:                        │
│  📁 plugins/ArmorSets/sets/arcanist.yml │
│                                          │
│         [◄ Back] [EXPORT & SAVE]        │
│                                          │
└──────────────────────────────────────────┘
```

---

### 3. YAML Export Format

**Output Location:** `plugins/ArmorSets/sets/arcanist.yml`

```yaml
# Auto-generated by Visual Effect Builder
# Set ID: arcanist_t1
# Created: 2025-11-22 14:30:00
# Last Modified: 2025-11-22 14:30:00

arcanist:
  tiers:
    1:
      name_pattern: "Arcanist.*Tier I"
      material: NETHERITE

      equipped_message:
        - "&d&l(!) &dYou don the Arcanist Set."
        - "&5✨ &7Whispers from the aether fill your mind..."

      unequipped_message:
        - "&c&l(!) &cThe Arcanist Set has been removed."
        - "&7The cosmic connection fades into silence..."

      individual_effects:
        helmet:
          EFFECT_STATIC:
            chance: 100
            effects:
              - "POTION:NIGHT_VISION:1"
              - "POTION:WATER_BREATHING:1"
            cooldown: 0

        chestplate:
          DEFENSE:
            chance: 15
            effects:
              - "AEGIS:3"
              - "PARTICLE:PORTAL:30 @Self"
            cooldown: 10

        leggings:
          EFFECT_STATIC:
            chance: 100
            effects:
              - "POTION:SPEED:1"
            cooldown: 0

        boots:
          FALL_DAMAGE:
            chance: 100
            effects:
              - "CANCEL_EVENT"
            cooldown: 0

      synergies:
        arcane_disintegration:
          trigger: ATTACK
          chance: 30
          effects:
            - "INCREASE_DAMAGE:30"
            - "DISINTEGRATE:1 @Victim"
            - "POTION:SLOW:4 @Victim"
            - "PARTICLE:SOUL_FIRE_FLAME:25 @Victim"
            - "SOUND:ENTITY_ILLUSIONER_CAST_SPELL @Victim"
          cooldown: 3

        aether_step:
          trigger: DEFENSE
          chance: 20
          effects:
            - "CANCEL_EVENT"
            - "SMOKEBOMB:1 @Self"
            - "TELEPORT_RANDOM:8 @Self"
            - "MESSAGE:&d&oYou phase through the aether... @Self"
          cooldown: 8

        soul_siphon:
          trigger: KILL_MOB
          chance: 100
          effects:
            - "HEAL:4"
            - "DEVOUR:10"
            - "POTION:REGENERATION:4 @Self"
            - "PARTICLE:SOUL:40 @Victim"
          cooldown: 2

        rift_walk:
          trigger: SHIFT
          chance: 100
          effects:
            - "POTION:INVISIBILITY:4 @Self"
            - "POTION:SPEED:2 @Self"
            - "REPLENISH:5"
            - "PARTICLE:REVERSE_PORTAL:30 @Self"
            - "SOUND:ENTITY_ENDERMAN_TELEPORT @Self"
          cooldown: 12
```

---

### 4. Core Function Builder

**Command:** `/armorsets build function`

Similar wizard but for creating core functions:

```
┌──────────────────────────────────────────┐
│  CORE FUNCTION BUILDER - BASIC INFO      │
├──────────────────────────────────────────┤
│                                          │
│  Function ID:    [_________________]    │
│  Function Name:  [_________________]    │
│  Slot Type:      [HELMET ▼]             │
│  Tier Level:     [1]                    │
│                                          │
│  Description:                            │
│  [_________________________________]    │
│  [_________________________________]    │
│                                          │
│  Effects:                                │
│  [+] Add Effect                         │
│                                          │
│  Item Form (Shard):                      │
│  Material: [ECHO_SHARD ▼]               │
│  Color: [PURPLE ▼]                      │
│  Glow: [✓] Enchantment Glow             │
│  Model Data: [100]                      │
│                                          │
│         [Save] [Cancel]                 │
│                                          │
└──────────────────────────────────────────┘
```

**Export to:** `plugins/ArmorSets/core-functions/helmet-functions.yml`

---

### 5. Edit Existing Configurations

**Command:** `/armorsets edit set <set-name>`

Opens existing YAML in GUI (pre-populated fields), allows modifications, saves back to file.

**Features:**
- Validates changes before saving
- Creates backup of old YAML as `.bak`
- Supports hot-reload without server restart
- Shows diff of changes made

---

### 6. Validation System

Before exporting, validate:

```
✅ Checks:
├─ All effect types exist (no typos)
├─ All potion effect types valid
├─ Cooldowns are positive numbers
├─ Chances are 0-100%
├─ Teleport distances within max
├─ Entity spawn counts within max
├─ No duplicate synergy IDs
├─ All required fields present
├─ YAML syntax valid
└─ Set ID lowercase with underscores

⚠️ Warnings:
├─ Effect not registered (may be missing implementation)
├─ Synergy cooldown very low (< 0.5s)
├─ Damage increase very high (> 300%)
├─ Many effects on one trigger (performance concern)
└─ Set has no synergies (unusual)
```

If any check fails, show modal:
```
┌────────────────────────────────────┐
│     ❌ VALIDATION ERRORS           │
├────────────────────────────────────┤
│                                    │
│ Error 1: Invalid effect type       │
│ Location: helmet > ATTACK > effects│
│ Value: "POTION:INVALID_TYPE:1"     │
│ Suggestion: Did you mean           │
│            "POTION:SPEED:1"?       │
│                                    │
│ Error 2: Missing tier              │
│ Location: set > tiers              │
│ The set has no tiers defined       │
│ Suggestion: Add at least Tier 1    │
│                                    │
│         [Fix Issues] [Export Anyway]
│                                    │
└────────────────────────────────────┘
```

---

### 7. Commands

```bash
# Main builder menu
/armorsets build

# Create new set
/armorsets build set

# Create new function
/armorsets build function

# Edit existing set
/armorsets edit set <name>

# Edit existing function
/armorsets edit function <name>

# View all sets in GUI
/armorsets catalog

# Preview YAML of a set
/armorsets preview-yaml <set>

# Validate all configs
/armorsets validate

# Export specific set to YAML (if already configured)
/armorsets export set <name>
```

---

## Technical Implementation Details

### 1. File Structure
```
src/main/java/com/zenax/armorsets/
├── gui/
│   ├── EffectBuilderGUI.java          (Main GUI controller)
│   ├── pages/
│   │   ├── SetBuilderStep1.java       (Basic Info)
│   │   ├── SetBuilderStep2.java       (Piece Effects)
│   │   ├── SetBuilderStep3.java       (Synergies)
│   │   ├── SetBuilderStep4.java       (Review & Export)
│   │   ├── FunctionBuilderGUI.java    (Core Function builder)
│   │   └── EffectPickerGUI.java       (Effect selection modal)
│   └── builder/
│       ├── SetBuilderData.java        (Data model)
│       ├── FunctionBuilderData.java   (Data model)
│       ├── EffectBuilder.java         (Effect construction)
│       └── YAMLExporter.java          (YAML generation)
└── config/
    └── validation/
        ├── ConfigValidator.java       (Validation logic)
        └── ValidationReport.java      (Error/warning container)
```

### 2. Data Models

```java
public class SetBuilderData {
    private String setId;
    private String displayName;
    private int tier;
    private String description;
    private Material material;

    private Map<String, List<EffectData>> individualEffects;
    // Key: "helmet", "chestplate", "leggings", "boots"

    private List<SynergyData> synergies;
    private List<String> equippedMessages;
    private List<String> unequippedMessages;

    // Methods to convert to/from YAML
    public FileConfiguration toYAML();
    public static SetBuilderData fromYAML(FileConfiguration config);
}

public class EffectData {
    private String triggerType;      // ATTACK, DEFENSE, etc.
    private int chance;              // 0-100
    private List<String> effects;    // ["POTION:SPEED:1", "PARTICLE:..."]
    private long cooldown;           // In seconds
    private String target;           // @SELF, @VICTIM, @NEARBY
}

public class SynergyData {
    private String id;               // "arcane_disintegration"
    private TriggerType trigger;
    private int chance;
    private List<String> effects;
    private long cooldown;
}
```

### 3. YAML Export Logic

```java
public class YAMLExporter {

    public static void exportSet(SetBuilderData data, File outputFile) {
        YamlConfiguration yaml = new YamlConfiguration();

        // Build nested structure matching expected format
        ConfigurationSection setSection = yaml.createSection(data.getSetId());
        ConfigurationSection tiersSection = setSection.createSection("tiers");
        ConfigurationSection tierSection = tiersSection.createSection(String.valueOf(data.getTier()));

        // Add all data recursively
        tierSection.set("name_pattern", data.getNamePattern());
        tierSection.set("material", data.getMaterial().name());
        tierSection.set("equipped_message", data.getEquippedMessages());
        tierSection.set("unequipped_message", data.getUnequippedMessages());

        // Add individual effects
        ConfigurationSection effectsSection = tierSection.createSection("individual_effects");
        for (String slot : data.getIndividualEffects().keySet()) {
            ConfigurationSection slotSection = effectsSection.createSection(slot);
            // Add effects for this slot...
        }

        // Add synergies
        ConfigurationSection synergiesSection = tierSection.createSection("synergies");
        for (SynergyData synergy : data.getSynergies()) {
            ConfigurationSection synSection = synergiesSection.createSection(synergy.getId());
            synSection.set("trigger", synergy.getTrigger().getConfigKey());
            synSection.set("chance", synergy.getChance());
            synSection.set("effects", synergy.getEffects());
            synSection.set("cooldown", synergy.getCooldown());
        }

        // Save to file with proper formatting
        try {
            yaml.save(outputFile);
            // Pretty-print (add comments, formatting)
            prettifyYAML(outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to export set", e);
        }
    }

    private static void prettifyYAML(File file) {
        // Add YAML header comments, proper indentation, etc.
    }
}
```

### 4. GUI Navigation

```java
public class EffectBuilderGUI implements InventoryHolder {

    private Player player;
    private SetBuilderData currentSet;
    private int currentStep = 1;

    public void openStep(int step) {
        Inventory inv = switch(step) {
            case 1 -> createStep1GUI();
            case 2 -> createStep2GUI();
            case 3 -> createStep3GUI();
            case 4 -> createStep4GUI();
            default -> null;
        };

        if (inv != null) {
            player.openInventory(inv);
            currentStep = step;
        }
    }

    private Inventory createStep1GUI() {
        Inventory inv = Bukkit.createInventory(this, 45,
            ChatColor.DARK_GRAY + "Set Builder - Step 1: Basic Info");

        // Add text input items (sign clicking pattern)
        // Add dropdown items (multiple items representing options)
        // Add validation feedback items

        return inv;
    }

    // ... createStep2GUI, createStep3GUI, createStep4GUI

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() != this.getInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        // Handle clicks based on current step

        if (clicked.getType() == Material.ARROW &&
            clicked.getItemMeta().getDisplayName().contains("Next")) {
            saveCurrentStep();
            openStep(currentStep + 1);
        }
    }
}
```

---

## User Flow Diagram

```
┌─────────────────────┐
│ /armorsets build    │
└──────────┬──────────┘
           │
           v
┌─────────────────────────────┐
│  Main Menu                  │
│  • Create New Set           │
│  • Create New Function      │
│  • Edit Existing Set        │
│  • Edit Existing Function   │
└──────────┬──────────────────┘
           │
           ├─────────────────────┬─────────────────────┐
           v                     v                     v
    ┌────────────────┐    ┌──────────────┐    ┌──────────────────┐
    │ Set Builder    │    │ Function     │    │ Edit Existing    │
    │ Step 1: Info   │    │ Builder      │    │ Set/Function     │
    └────────┬───────┘    └──────┬───────┘    └────────┬─────────┘
             │                   │                     │
             v                   v                     v
    ┌────────────────┐    ┌──────────────┐    ┌──────────────────┐
    │ Step 2:        │    │ Slot Type    │    │ Load from YAML   │
    │ Piece Effects  │    │ & Effects    │    └────────┬─────────┘
    └────────┬───────┘    └──────┬───────┘             │
             │                   │                     v
             v                   v            ┌──────────────────┐
    ┌────────────────┐    ┌──────────────┐   │ Populate GUI     │
    │ Step 3:        │    │ Item Form    │   │ with Existing    │
    │ Synergies      │    │ & Export     │   │ Data             │
    └────────┬───────┘    └──────┬───────┘   └────────┬─────────┘
             │                   │                     │
             v                   v                     v
    ┌────────────────┐    ┌──────────────┐    ┌──────────────────┐
    │ Step 4:        │    │ Validate &   │    │ Allow Edits      │
    │ Review         │    │ Export YAML  │    │ & Re-export      │
    └────────┬───────┘    └──────┬───────┘    └────────┬─────────┘
             │                   │                     │
             v                   v                     v
    ┌────────────────┐    ┌──────────────┐    ┌──────────────────┐
    │ Validate &     │    │ Save to File │    │ Save Changes     │
    │ Export YAML    │    │ SUCCESS! ✅  │    │ Reload Config    │
    └────────┬───────┘    └──────────────┘    │ SUCCESS! ✅      │
             │                                 └──────────────────┘
             v
    ┌────────────────┐
    │ Save to File   │
    │ SUCCESS! ✅    │
    └────────────────┘
```

---

## Benefits

✅ **For Admins:**
- No YAML editing experience needed
- Real-time validation prevents errors
- Visual preview of effects
- Quick set creation (5 min vs 30 min manual)
- Backup of original configs

✅ **For Server:**
- Consistent YAML format
- No configuration syntax errors
- Faster content iteration
- Lower barrier to entry

✅ **For Zenax (Resale):**
- Major selling point
- Justifies higher price
- Less tech-savvy admins can use plugin
- Recurring revenue: "Premium Builder"

---

## Estimated Implementation

- **Core GUI Framework:** 4 hours
- **Step 1-4 GUIs:** 6 hours
- **Effect Picker:** 2 hours
- **YAML Export:** 2 hours
- **Validation System:** 2 hours
- **Edit Existing:** 2 hours
- **Testing & Polish:** 3 hours

**Total: ~21 hours of development**

