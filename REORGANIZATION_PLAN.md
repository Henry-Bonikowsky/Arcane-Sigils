# ArmorSets Plugin - Codebase Reorganization Plan

## Executive Summary

This document outlines a comprehensive reorganization plan for the ArmorSets Minecraft plugin codebase. The goal is to improve maintainability, reduce cognitive load, and establish clear organizational standards without modifying core logic.

---

## Current State Analysis

### Current Package Structure
```
com.zenax.armorsets/
├── ArmorSetsPlugin.java          [Entry point]
├── commands/
│   └── ArmorSetsCommand.java     [Single command handler]
├── components/                    [MISNAMED - contains UI components]
│   ├── BuildMainMenuComponent.java
│   ├── ColorUtil.java            [Should be in utils/]
│   ├── MenuItem.java
│   └── MenuState.java
├── config/
│   └── ConfigManager.java
├── core/
│   ├── Sigil.java
│   ├── SigilManager.java
│   └── SocketManager.java
├── effects/
│   ├── Effect.java
│   ├── EffectContext.java
│   ├── EffectManager.java
│   ├── EffectParams.java
│   └── impl/                     [40+ effect implementations - flat structure]
│       ├── AbstractEffect.java
│       ├── DealDamageEffect.java
│       ├── HealEffect.java
│       └── ... (37 more files)
├── events/
│   ├── ConditionManager.java
│   ├── CooldownManager.java
│   ├── TriggerHandler.java
│   └── TriggerType.java
├── gui/
│   └── GUIManager.java           [CRITICAL: 2000+ lines, handles 24+ GUI types]
├── sets/
│   ├── ArmorSet.java
│   ├── SetManager.java
│   ├── SetSynergy.java
│   └── TriggerConfig.java
├── utils/
│   └── TextUtil.java
└── weapons/
    ├── CustomWeapon.java
    └── WeaponManager.java
```

### Identified Issues

#### 1. GUIManager is Unmaintainable (Critical)
- **Location**: `gui/GUIManager.java`
- **Problem**: 2000+ lines handling 24+ GUI types with a giant switch statement
- **Impact**: Adding new GUI types requires modifying a monolithic class

#### 2. Misplaced Files
- `ColorUtil.java` in `components/` should be in `utils/`
- `components/` package name is ambiguous (contains UI menu components)

#### 3. Flat Effect Implementation Structure
- 40+ effect files in a single `effects/impl/` directory
- No categorization by effect type (combat, movement, healing, utility)

#### 4. Magic Numbers Scattered Throughout
- `5L`, `10L`, `20L` task intervals in TriggerHandler
- `250ms` click cooldown in GUIManager
- `5 * 60 * 1000` timeout in MenuState
- Various slot numbers in GUI classes

#### 5. Inconsistent Logging
- `System.err.println()` in ConditionManager instead of proper logging
- Mix of debug logging approaches

#### 6. Duplicate Patterns
- Session management exists in both GUIManager and BuildMainMenuComponent
- Similar item creation methods duplicated across GUI classes

---

## Proposed New Structure

```
com.zenax.armorsets/
├── ArmorSetsPlugin.java
│
├── api/                                    [NEW - Public API for extensions]
│   └── ArmorSetsAPI.java
│
├── commands/
│   └── ArmorSetsCommand.java
│
├── config/
│   ├── ConfigManager.java
│   └── ConfigConstants.java                [NEW - Configuration key constants]
│
├── constants/                              [NEW - All magic numbers]
│   ├── GUIConstants.java
│   ├── TaskIntervals.java
│   └── SlotPositions.java
│
├── core/
│   ├── data/                               [NEW - Data models]
│   │   ├── Sigil.java
│   │   ├── ArmorSet.java
│   │   ├── SetSynergy.java
│   │   └── CustomWeapon.java
│   ├── managers/                           [NEW - Core managers grouped]
│   │   ├── SigilManager.java
│   │   ├── SetManager.java
│   │   ├── SocketManager.java
│   │   ├── WeaponManager.java
│   │   └── CooldownManager.java
│   └── TriggerConfig.java
│
├── effects/
│   ├── Effect.java
│   ├── EffectContext.java
│   ├── EffectManager.java
│   ├── EffectParams.java
│   ├── AbstractEffect.java                 [Promoted from impl/]
│   │
│   ├── combat/                             [NEW - Categorized effects]
│   │   ├── DealDamageEffect.java
│   │   ├── DamageBoostEffect.java
│   │   ├── AegisEffect.java
│   │   ├── WardEffect.java
│   │   ├── LifestealEffect.java
│   │   ├── BleedingEffect.java
│   │   ├── FreezingEffect.java
│   │   └── DisintegrateEffect.java
│   │
│   ├── movement/                           [NEW]
│   │   ├── TeleportEffect.java
│   │   ├── TeleportRandomEffect.java
│   │   ├── DodgeEffect.java
│   │   ├── BlinkEffect.java
│   │   ├── SmokebombEffect.java
│   │   ├── WingsEffect.java
│   │   ├── SpringsEffect.java
│   │   ├── MomentumEffect.java
│   │   ├── GearsEffect.java
│   │   ├── FeatherweightEffect.java
│   │   └── JellylegsEffect.java
│   │
│   ├── healing/                            [NEW]
│   │   ├── HealEffect.java
│   │   ├── DevourEffect.java
│   │   ├── ReplenishEffect.java
│   │   ├── PatchEffect.java
│   │   ├── RestoreEffect.java
│   │   ├── PhoenixEffect.java
│   │   ├── AngelicEffect.java
│   │   └── ImmortalEffect.java
│   │
│   ├── utility/                            [NEW]
│   │   ├── PotionEffectEffect.java
│   │   ├── CancelEventEffect.java
│   │   ├── SoulboundEffect.java
│   │   ├── UnbreakableEffect.java
│   │   ├── AquaEffect.java
│   │   ├── NightowlEffect.java
│   │   ├── LucidEffect.java
│   │   ├── InquisitiveEffect.java
│   │   ├── EnlightenedEffect.java
│   │   ├── ImplantsEffect.java
│   │   ├── GuardiansEffect.java
│   │   ├── AllureEffect.java
│   │   └── RushEffect.java
│   │
│   └── visual/                             [NEW]
│       ├── ParticleEffect.java
│       ├── SoundEffect.java
│       ├── MessageEffect.java
│       └── SpawnEntityEffect.java
│
├── events/
│   ├── TriggerHandler.java
│   ├── TriggerType.java
│   └── ConditionManager.java
│
├── gui/                                    [MAJOR REFACTOR]
│   ├── GUIManager.java                     [Reduced to coordinator role]
│   ├── GUISession.java                     [Extracted from GUIManager]
│   ├── GUIType.java                        [Extracted enum]
│   │
│   ├── common/                             [NEW - Shared GUI utilities]
│   │   ├── InventoryBuilder.java
│   │   ├── ItemBuilder.java
│   │   └── GUIConstants.java
│   │
│   ├── handlers/                           [NEW - Click handlers by GUI type]
│   │   ├── SocketGUIHandler.java
│   │   ├── UnsocketGUIHandler.java
│   │   ├── BuildMenuHandler.java
│   │   ├── BrowserHandler.java
│   │   ├── EditorHandler.java
│   │   └── ConfigHandler.java
│   │
│   └── screens/                            [NEW - Screen renderers]
│       ├── SocketScreen.java
│       ├── UnsocketScreen.java
│       ├── BuildMainMenuScreen.java
│       ├── SetBrowserScreen.java
│       ├── SigilBrowserScreen.java
│       ├── SetEditorScreen.java
│       ├── SigilEditorScreen.java
│       ├── TriggerConfigScreen.java
│       └── EffectSelectorScreen.java
│
├── menu/                                   [RENAMED from components/]
│   ├── BuildMainMenuComponent.java
│   ├── MenuItem.java
│   └── MenuState.java
│
└── utils/
    ├── TextUtil.java
    ├── ColorUtil.java                      [Moved from components/]
    ├── RomanNumerals.java                  [NEW - Extracted utility]
    └── LogHelper.java                      [NEW - Consistent logging]
```

---

## Detailed Migration Plan

### Phase 1: Constants Extraction (Non-Breaking)

#### Create `constants/TaskIntervals.java`
```java
package com.zenax.armorsets.constants;

public final class TaskIntervals {
    private TaskIntervals() {}

    public static final long ARMOR_CHECK_TICKS = 5L;
    public static final long STATIC_EFFECT_INTERVAL_TICKS = 20L;
    public static final long TICK_TRIGGER_INTERVAL = 1L;
    public static final long INITIAL_DELAY_TICKS = 10L;
}
```

#### Create `constants/GUIConstants.java`
```java
package com.zenax.armorsets.constants;

public final class GUIConstants {
    private GUIConstants() {}

    public static final long CLICK_COOLDOWN_MS = 250L;
    public static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000L;
    public static final int INVENTORY_ROW_SIZE = 9;
    public static final int MAX_INVENTORY_SIZE = 54;

    // Navigation slots
    public static final int SLOT_BACK = 45;
    public static final int SLOT_PREV_PAGE = 48;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT_PAGE = 50;
    public static final int SLOT_CLOSE = 53;
}
```

#### Create `constants/SlotPositions.java`
```java
package com.zenax.armorsets.constants;

public final class SlotPositions {
    private SlotPositions() {}

    // Armor slot indices in player inventory
    public static final int HELMET_SLOT = 39;
    public static final int CHESTPLATE_SLOT = 38;
    public static final int LEGGINGS_SLOT = 37;
    public static final int BOOTS_SLOT = 36;

    // GUI content slots (4 rows of 7)
    public static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
}
```

### Phase 2: Utility Extraction

#### Move `ColorUtil.java` to `utils/`
- Current: `com.zenax.armorsets.components.ColorUtil`
- Target: `com.zenax.armorsets.utils.ColorUtil`

#### Create `utils/RomanNumerals.java`
Extract the `toRomanNumeral()` method duplicated in multiple GUI classes.

#### Create `utils/LogHelper.java`
Replace `System.err.println()` calls with proper logging.

### Phase 3: GUIManager Decomposition (Major)

This is the most critical refactoring. The current GUIManager handles:
- Session management (700+ lines)
- 24+ GUI types with individual handlers
- Item creation utilities
- Sound playing
- Event handling

#### Step 3.1: Extract `GUISession.java`
Move the inner `GUISession` class to its own file.

#### Step 3.2: Extract `GUIType.java`
Move the `GUIType` enum to its own file.

#### Step 3.3: Create Handler Interface
```java
package com.zenax.armorsets.gui.handlers;

public interface GUIHandler {
    void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event);
    boolean canHandle(GUIType type);
}
```

#### Step 3.4: Extract Individual Handlers
Split the giant switch statement into focused handler classes:

| Current Method | New Handler Class |
|----------------|-------------------|
| `handleSocketGUIClick()` | `SocketGUIHandler` |
| `handleUnsocketGUIClick()` | `UnsocketGUIHandler` |
| `handleBuildMainMenuClick()` | `BuildMenuHandler` |
| `handleSetBrowserClick()` | `BrowserHandler` |
| `handleFunctionBrowserClick()` | `BrowserHandler` |
| `handleTriggerConfigClick()` | `ConfigHandler` |
| `handleSetEditorClick()` | `EditorHandler` |
| `handleFunctionEditorClick()` | `EditorHandler` |

#### Step 3.5: Create Screen Renderers
Extract inventory creation methods into focused screen classes.

### Phase 4: Effect Categorization

#### Create Category Subpackages
```
effects/
├── combat/      [8 files]
├── movement/    [11 files]
├── healing/     [8 files]
├── utility/     [13 files]
└── visual/      [4 files]
```

#### Effect Categorization Mapping

| Effect | Category | Reasoning |
|--------|----------|-----------|
| DealDamageEffect | combat | Direct damage dealing |
| DamageBoostEffect | combat | Damage modification |
| AegisEffect | combat | Defensive combat |
| WardEffect | combat | Damage protection |
| LifestealEffect | combat | Combat sustain |
| BleedingEffect | combat | DoT damage |
| FreezingEffect | combat | CC + damage |
| DisintegrateEffect | combat | Equipment damage |
| TeleportEffect | movement | Position change |
| TeleportRandomEffect | movement | Random movement |
| DodgeEffect | movement | Evasion |
| BlinkEffect | movement | Short teleport |
| SmokebombEffect | movement | Escape utility |
| WingsEffect | movement | Flight |
| SpringsEffect | movement | Jump boost |
| MomentumEffect | movement | Speed |
| GearsEffect | movement | Movement modifier |
| FeatherweightEffect | movement | Fall reduction |
| JellylegsEffect | movement | Fall immunity |
| HealEffect | healing | Health restore |
| DevourEffect | healing | Health on kill |
| ReplenishEffect | healing | Resource restore |
| PatchEffect | healing | Armor repair |
| RestoreEffect | healing | General restore |
| PhoenixEffect | healing | Death prevention |
| AngelicEffect | healing | Revival |
| ImmortalEffect | healing | Death immunity |
| PotionEffectEffect | utility | Buff application |
| CancelEventEffect | utility | Event cancellation |
| SoulboundEffect | utility | Item binding |
| UnbreakableEffect | utility | Durability |
| AquaEffect | utility | Water breathing |
| NightowlEffect | utility | Night vision |
| LucidEffect | utility | Mental buff |
| InquisitiveEffect | utility | Experience |
| EnlightenedEffect | utility | Knowledge buff |
| ImplantsEffect | utility | Stat modifier |
| GuardiansEffect | utility | Protection |
| AllureEffect | utility | Entity attraction |
| RushEffect | utility | Speed buff |
| ParticleEffect | visual | Visual effect |
| SoundEffect | visual | Audio effect |
| MessageEffect | visual | Text display |
| SpawnEntityEffect | visual | Entity spawning |

### Phase 5: Package Renaming

#### Rename `components/` to `menu/`
The current `components` package is ambiguous. It contains menu-related classes, so renaming to `menu` is more descriptive.

### Phase 6: Core Package Restructuring

#### Create `core/data/` for Data Models
Move pure data classes:
- `Sigil.java`
- Move `ArmorSet.java` from `sets/`
- Move `SetSynergy.java` from `sets/`
- Move `CustomWeapon.java` from `weapons/`

#### Create `core/managers/` for Manager Classes
Consolidate managers:
- `SigilManager.java`
- `SocketManager.java`
- Move `SetManager.java` from `sets/`
- Move `WeaponManager.java` from `weapons/`
- Move `CooldownManager.java` from `events/`

---

## File Path Migration Reference

### Constants (New Files)
| New Path | Content |
|----------|---------|
| `constants/TaskIntervals.java` | Task timing constants |
| `constants/GUIConstants.java` | GUI timing and slot constants |
| `constants/SlotPositions.java` | Inventory slot positions |

### Utils
| Old Path | New Path |
|----------|----------|
| `components/ColorUtil.java` | `utils/ColorUtil.java` |
| (new) | `utils/RomanNumerals.java` |
| (new) | `utils/LogHelper.java` |

### Effects Reorganization
| Old Path | New Path |
|----------|----------|
| `effects/impl/AbstractEffect.java` | `effects/AbstractEffect.java` |
| `effects/impl/DealDamageEffect.java` | `effects/combat/DealDamageEffect.java` |
| `effects/impl/DamageBoostEffect.java` | `effects/combat/DamageBoostEffect.java` |
| `effects/impl/AegisEffect.java` | `effects/combat/AegisEffect.java` |
| `effects/impl/WardEffect.java` | `effects/combat/WardEffect.java` |
| `effects/impl/LifestealEffect.java` | `effects/combat/LifestealEffect.java` |
| `effects/impl/BleedingEffect.java` | `effects/combat/BleedingEffect.java` |
| `effects/impl/FreezingEffect.java` | `effects/combat/FreezingEffect.java` |
| `effects/impl/DisintegrateEffect.java` | `effects/combat/DisintegrateEffect.java` |
| `effects/impl/TeleportEffect.java` | `effects/movement/TeleportEffect.java` |
| `effects/impl/TeleportRandomEffect.java` | `effects/movement/TeleportRandomEffect.java` |
| `effects/impl/DodgeEffect.java` | `effects/movement/DodgeEffect.java` |
| `effects/impl/BlinkEffect.java` | `effects/movement/BlinkEffect.java` |
| `effects/impl/SmokebombEffect.java` | `effects/movement/SmokebombEffect.java` |
| `effects/impl/WingsEffect.java` | `effects/movement/WingsEffect.java` |
| `effects/impl/SpringsEffect.java` | `effects/movement/SpringsEffect.java` |
| `effects/impl/MomentumEffect.java` | `effects/movement/MomentumEffect.java` |
| `effects/impl/GearsEffect.java` | `effects/movement/GearsEffect.java` |
| `effects/impl/FeatherweightEffect.java` | `effects/movement/FeatherweightEffect.java` |
| `effects/impl/JellylegsEffect.java` | `effects/movement/JellylegsEffect.java` |
| `effects/impl/HealEffect.java` | `effects/healing/HealEffect.java` |
| `effects/impl/DevourEffect.java` | `effects/healing/DevourEffect.java` |
| `effects/impl/ReplenishEffect.java` | `effects/healing/ReplenishEffect.java` |
| `effects/impl/PatchEffect.java` | `effects/healing/PatchEffect.java` |
| `effects/impl/RestoreEffect.java` | `effects/healing/RestoreEffect.java` |
| `effects/impl/PhoenixEffect.java` | `effects/healing/PhoenixEffect.java` |
| `effects/impl/AngelicEffect.java` | `effects/healing/AngelicEffect.java` |
| `effects/impl/ImmortalEffect.java` | `effects/healing/ImmortalEffect.java` |
| `effects/impl/PotionEffectEffect.java` | `effects/utility/PotionEffectEffect.java` |
| `effects/impl/CancelEventEffect.java` | `effects/utility/CancelEventEffect.java` |
| `effects/impl/SoulboundEffect.java` | `effects/utility/SoulboundEffect.java` |
| `effects/impl/UnbreakableEffect.java` | `effects/utility/UnbreakableEffect.java` |
| `effects/impl/AquaEffect.java` | `effects/utility/AquaEffect.java` |
| `effects/impl/NightowlEffect.java` | `effects/utility/NightowlEffect.java` |
| `effects/impl/LucidEffect.java` | `effects/utility/LucidEffect.java` |
| `effects/impl/InquisitiveEffect.java` | `effects/utility/InquisitiveEffect.java` |
| `effects/impl/EnlightenedEffect.java` | `effects/utility/EnlightenedEffect.java` |
| `effects/impl/ImplantsEffect.java` | `effects/utility/ImplantsEffect.java` |
| `effects/impl/GuardiansEffect.java` | `effects/utility/GuardiansEffect.java` |
| `effects/impl/AllureEffect.java` | `effects/utility/AllureEffect.java` |
| `effects/impl/RushEffect.java` | `effects/utility/RushEffect.java` |
| `effects/impl/ParticleEffect.java` | `effects/visual/ParticleEffect.java` |
| `effects/impl/SoundEffect.java` | `effects/visual/SoundEffect.java` |
| `effects/impl/MessageEffect.java` | `effects/visual/MessageEffect.java` |
| `effects/impl/SpawnEntityEffect.java` | `effects/visual/SpawnEntityEffect.java` |

### GUI Extraction
| Old Location | New Path |
|--------------|----------|
| `GUIManager.GUISession` (inner class) | `gui/GUISession.java` |
| `GUIManager.GUIType` (inner enum) | `gui/GUIType.java` |
| (new) | `gui/common/InventoryBuilder.java` |
| (new) | `gui/common/ItemBuilder.java` |
| (new) | `gui/handlers/SocketGUIHandler.java` |
| (new) | `gui/handlers/UnsocketGUIHandler.java` |
| (new) | `gui/handlers/BuildMenuHandler.java` |
| (new) | `gui/handlers/BrowserHandler.java` |
| (new) | `gui/handlers/EditorHandler.java` |
| (new) | `gui/handlers/ConfigHandler.java` |
| (new) | `gui/screens/SocketScreen.java` |
| (new) | `gui/screens/BuildMainMenuScreen.java` |

### Package Renames
| Old Package | New Package |
|-------------|-------------|
| `com.zenax.armorsets.components` | `com.zenax.armorsets.menu` |

---

## Import Updates Required

After reorganization, the following files will need import updates:

### EffectManager.java
```java
// Old
import com.zenax.armorsets.effects.impl.*;

// New
import com.zenax.armorsets.effects.combat.*;
import com.zenax.armorsets.effects.movement.*;
import com.zenax.armorsets.effects.healing.*;
import com.zenax.armorsets.effects.utility.*;
import com.zenax.armorsets.effects.visual.*;
```

### ArmorSetsPlugin.java
No changes to manager imports if we keep the same class names.

### GUIManager.java
```java
// Add
import com.zenax.armorsets.constants.GUIConstants;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
```

---

## Build Configuration Updates

### pom.xml
No changes required - Maven will compile from the new package structure automatically.

### IDE Configuration
IntelliJ `.idea/` files will auto-update on project reload.

---

## Testing Checklist

After reorganization:

1. [ ] `mvn clean compile` succeeds
2. [ ] All imports resolve correctly
3. [ ] Plugin loads without errors
4. [ ] `/as reload` works
5. [ ] All 40+ effects still register in EffectManager
6. [ ] GUI socket/unsocket flows work
7. [ ] Build menu opens correctly
8. [ ] Set detection works
9. [ ] Sigil socketing works
10. [ ] Cooldowns still track correctly

---

## Rollback Plan

If reorganization causes issues:
1. Git revert to pre-reorganization commit
2. Keep `REORGANIZATION_PLAN.md` for future reference
3. Apply changes incrementally in smaller PRs

---

## Future Considerations

### Phase 7 (Future): API Extraction
Create a clean public API for external plugins:
```java
package com.zenax.armorsets.api;

public interface ArmorSetsAPI {
    Optional<ArmorSet> getPlayerActiveSet(Player player);
    List<Sigil> getSocketedSigils(ItemStack item);
    boolean socketSigil(ItemStack item, Sigil sigil);
    void registerEffect(Effect effect);
}
```

### Phase 8 (Future): Event-Driven Architecture
Replace polling-based armor detection with event-driven:
- Hook `InventoryClickEvent` for armor changes
- Hook `PlayerItemHeldEvent` for weapon changes
- Eliminate the 5-tick polling task

---

## Summary

This reorganization plan addresses:
- **GUIManager decomposition** into handlers and screens
- **Effect categorization** into combat/movement/healing/utility/visual
- **Constants extraction** for all magic numbers
- **Utility consolidation** in the utils package
- **Package naming clarity** (components -> menu)

The changes preserve all existing functionality while dramatically improving code navigation and maintainability.

**Estimated Effort**: 4-6 hours for full implementation
**Risk Level**: Medium (many file moves, but no logic changes)
**Testing Required**: Full regression testing of all features
