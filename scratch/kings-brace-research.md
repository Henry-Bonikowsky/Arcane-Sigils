# King's Brace Charge System - Research Findings

## Goal
Understand the systems needed to implement King's Brace (charge-based DR), Ancient Crown (passive immunity), and Cleopatra (suppression) seasonal pass sigils.

## Context
Three seasonal pass exclusive sigils need implementation:
- **King's Brace** (Chestplate): Charge-based passive DR + burst ability
- **Ancient Crown** (Helmet): Passive immunity to negative effects
- **Cleopatra** (Leggings): Suppression ability that blocks buff reapplication

The implementation plan identified a critical gap: Ancient Crown registration logic not specified.

---

## 1. Interceptor System

### Architecture

**InterceptionManager** (`src/main/java/com/miracle/arcanesigils/interception/InterceptionManager.java`)
- Central registry for effect interceptors
- Storage: `ConcurrentHashMap<UUID, List<EffectInterceptor>>`
- Auto-sorts interceptors by priority (higher first)
- Key methods:
  - `registerInterceptor(Player, EffectInterceptor)` - Add interceptor to player
  - `unregisterInterceptor(Player, EffectInterceptor)` - Remove specific interceptor
  - `fireIntercept(InterceptionEvent)` - Execute interceptors on event (lines 68-93)
  - `unregisterAll(Player)` - Cleanup on logout

**EffectInterceptor Interface** (`src/main/java/com/miracle/arcanesigils/interception/EffectInterceptor.java`)
```java
public interface EffectInterceptor {
    InterceptionResult intercept(InterceptionEvent event);
    int getPriority();  // Higher runs first
    default boolean isActive();  // Can be disabled
}
```

**Priority Convention:**
- 100: Reducers (Ancient Crown)
- 50: Blockers (Cleopatra)
- 0: Other

### Existing Implementation: CleopatraSuppressionInterceptor

**File:** `src/main/java/com/miracle/arcanesigils/interception/CleopatraSuppressionInterceptor.java`

**Blocks:**
- RESISTANCE potion effects
- REGENERATION potion effects
- Positive ARMOR/ARMOR_TOUGHNESS attribute modifiers

**Registration Pattern** (from `ApplySuppressionEffect.java` lines 88-91):
```java
CleopatraSuppressionInterceptor interceptor = new CleopatraSuppressionInterceptor(player);
interceptionManager.registerInterceptor(player, interceptor);

// Scheduled removal after duration
Bukkit.getScheduler().runTaskLater(plugin, () -> {
    interceptor.deactivate();
    interceptionManager.unregisterInterceptor(player, interceptor);
}, durationSeconds * 20L);
```

**Key Insight:** Effects register interceptors themselves. There's no automatic "when sigil equips → register interceptor" system.

### Critical Gap: Interception Not Integrated

**PROBLEM:** The plan assumes `fireIntercept()` is called when effects are applied. IT'S NOT.

**Files that need integration:**
1. `PotionEffectEffect.java` - Line 101: `target.addPotionEffect()` (NO interception call)
2. `ModifyAttributeEffect.java` - Line 205: `attrInstance.addModifier()` (NO interception call)
3. `AttributeModifierManager.java` - Line 97: `attrInstance.addModifier()` (NO interception call)

**Current Flow:**
- `PotionEffectInterceptionListener` listens to Bukkit `EntityPotionEffectEvent`
- Fires `POTION_EFFECT_APPLY` signal through flows
- Does NOT use InterceptionManager

**Required Fix:** Add `fireIntercept()` calls BEFORE applying effects in all three files.

---

## 2. SIGIL Variable System

### Storage Model

**SigilVariableManager** (`src/main/java/com/miracle/arcanesigils/variables/SigilVariableManager.java`)

**Composite Key:** `"playerUUID|sigilId|slot"`
Example: `"550e8400-e29b-41d4-a716-446655440000|kings_brace|CHESTPLATE"`

**Storage:**
```java
Map<String, Map<String, SigilVariable>>

class SigilVariable {
    Object value;
    long expiryTime;  // Long.MAX_VALUE for permanent
}
```

**Scoping:**
- Per-instance: Same sigil in different slots = separate variables
- Per-player: Different players = separate variables
- Cleared on armor unequip via `ArmorChangeListener`

### API Methods

```java
// Set with expiration
void setSigilVariable(Player, String sigilId, String slot, String varName, Object value, int durationSeconds)

// Get (auto-removes expired)
Object getSigilVariable(Player, String sigilId, String slot, String varName)
int getSigilVariableInt(Player, String sigilId, String slot, String varName, int defaultValue)

// Cleanup
void clearSlotVariables(UUID playerId, String slot)  // Called by ArmorChangeListener
```

### VariableNode Integration

**File:** `src/main/java/com/miracle/arcanesigils/flow/nodes/VariableNode.java` (lines 155-209)

**YAML Parameters for SIGIL Scope:**
```yaml
- type: VARIABLE
  params:
    name: charge
    operation: ADD
    value: 1
    scope: SIGIL
    sigilId: kings_brace
    slot: CHESTPLATE
    duration: -1  # Permanent
```

**Operations:** SET, ADD, SUBTRACT, MULTIPLY, DIVIDE

**Implementation:**
1. Gets current value from SigilVariableManager (if operation != SET)
2. Performs operation
3. Stores result back to SigilVariableManager

### FlowContext Resolution - CRITICAL GAP

**File:** `src/main/java/com/miracle/arcanesigils/flow/FlowContext.java` (lines 195-281)

**Current `resolveValue()` supports:**
- User variables: `$varName` → PlayerVariableManager
- Tier parameters: `{damage}` → TierScalingConfig
- Event data: `{player.health}`, `{victim.distance}`, etc.

**DOES NOT SUPPORT:**
- SIGIL variables: `{sigil.charge}` ❌
- Calculated variables: `{charges_needed}` ❌
- Dynamic calculations: `{current_dr}` ❌

**Required Implementation:**
```java
// In resolveValue() method, add BEFORE final switch:

if (placeholder.startsWith("sigil.")) {
    String varName = placeholder.substring(6);
    if (effectContext != null) {
        String sigilId = effectContext.getMetadata("sourceSigilId", null);
        Player player = effectContext.getPlayer();
        ItemStack sourceItem = effectContext.getMetadata("sourceItem", null);

        if (sigilId != null && player != null && sourceItem != null) {
            String slot = getSlotFromItem(sourceItem);
            if (slot != null) {
                Object value = plugin.getSigilVariableManager()
                    .getSigilVariable(player, sigilId, slot, varName);
                return value != null ? value : 0;
            }
        }
    }
    return 0;
}
```

**Helper method needed:**
```java
private String getSlotFromItem(ItemStack item) {
    String type = item.getType().name();
    if (type.contains("HELMET")) return "HELMET";
    if (type.contains("CHESTPLATE")) return "CHESTPLATE";
    if (type.contains("LEGGINGS")) return "LEGGINGS";
    if (type.contains("BOOTS")) return "BOOTS";
    return "UNKNOWN";
}
```

---

## 3. Armor Equip/Unequip Detection

### Detection Mechanism

**File:** `src/main/java/com/miracle/arcanesigils/events/SignalHandler.java`

**Method:** Polling task (every 5 ticks) via `startArmorCheckTask()` (lines 96-162)

**Process:**
1. Store previous armor state in `Map<UUID, Map<EquipmentSlot, ItemStack>>`
2. Every 5 ticks: compare current vs previous armor
3. Detect changes → remove attribute modifiers for removed sigils

**Cleanup on Unequip:**
```java
private void removeSigilAttributeModifiers(Player player, Sigil sigil, String slot) {
    // Removes modifiers with pattern: "arcane_sigils_persist_<sigilId>_*"
    // Example: "arcane_sigils_persist_kings_brace_armor"
}
```

**ArmorChangeListener** (`src/main/java/com/miracle/arcanesigils/listeners/ArmorChangeListener.java`)
- Listens for `InventoryClickEvent` on armor slots (5-8)
- Calls `clearSlotVariables()` on SigilVariableManager (lines 119-123)

**Critical Insight:** No "onEquip" hook exists. Passive effects use EFFECT_STATIC signal (runs every 20 ticks).

---

## 4. UpdateChargeDREffect Implementation

**File:** `src/main/java/com/miracle/arcanesigils/effects/impl/UpdateChargeDREffect.java`

**Already implemented and working correctly.**

**Workflow:**
1. Gets charge from SIGIL variable
2. Caps at 100 (lines 47-51)
3. Calculates DR: `charge * charge_dr_percent`
4. Updates named attribute modifier via AttributeModifierManager

**Usage in YAML:**
```yaml
- type: EFFECT
  effect: UPDATE_CHARGE_DR
  params:
    modifier_name: kings_brace_dr
    sigilId: kings_brace
    slot: CHESTPLATE
    charge_dr_percent: "{charge_dr_percent}"
```

**Verification:** Effect exists and uses AttributeModifierManager correctly. No changes needed.

---

## 5. AttributeModifierManager

**File:** `src/main/java/com/miracle/arcanesigils/effects/AttributeModifierManager.java`

**Purpose:** Prevents attribute modifier stacking bugs by using consistent UUIDs.

**Key Methods:**
```java
public boolean setNamedModifier(LivingEntity entity, Attribute attribute, String name,
                                 double value, AttributeModifier.Operation operation,
                                 int durationSeconds)
```

**Implementation:**
1. Generates UUID from name: `UUID.nameUUIDFromBytes("arcane_sigils_" + name)`
2. Removes existing modifier with same UUID
3. Adds new modifier
4. Schedules removal after duration (if durationSeconds > 0)

**Critical Gap - No Interception:**
Line 97: `attrInstance.addModifier(modifier)` is called WITHOUT checking InterceptionManager.

**Required Fix:**
```java
// BEFORE line 97:
InterceptionManager interceptionManager = plugin.getInterceptionManager();
if (interceptionManager != null) {
    InterceptionEvent event = new InterceptionEvent(
        entity,
        InterceptionEvent.Type.ATTRIBUTE_MODIFIER,
        null,
        attribute,
        value
    );
    InterceptionResult result = interceptionManager.fireIntercept(event);
    if (result.isBlocked()) {
        return false;  // Modifier was blocked
    }
}

// THEN add modifier:
attrInstance.addModifier(modifier);
```

---

## 6. Effect Interception Integration Requirements

### PotionEffectEffect.java (Line 101)

**Current:**
```java
target.addPotionEffect(new PotionEffect(potionType, duration, amplifier, false, true), true);
```

**Required:**
```java
// Create event
InterceptionEvent event = new InterceptionEvent(
    target,
    InterceptionEvent.Type.POTION_EFFECT,
    potionType,
    null,
    0
);

// Fire interception
InterceptionResult result = interceptionManager.fireIntercept(event);
if (result.isBlocked()) {
    return false;  // Effect blocked by interceptor
}

// Apply effect
target.addPotionEffect(new PotionEffect(potionType, duration, amplifier, false, true), true);
```

### ModifyAttributeEffect.java (Line 205)

**Current:**
```java
AttributeModifier modifier = new AttributeModifier(key, value, operation);
attrInstance.addModifier(modifier);
```

**Required:**
```java
// Create event
InterceptionEvent event = new InterceptionEvent(
    target,
    InterceptionEvent.Type.ATTRIBUTE_MODIFIER,
    null,
    attribute,
    value
);

// Fire interception
InterceptionResult result = interceptionManager.fireIntercept(event);
if (result.isBlocked()) {
    return false;  // Modifier blocked
}

// Apply modifier
AttributeModifier modifier = new AttributeModifier(key, value, operation);
attrInstance.addModifier(modifier);
```

---

## 7. Ancient Crown Registration Pattern

### Problem
Ancient Crown needs to register `AncientCrownImmunityInterceptor` when equipped, but there's no automatic "onEquip" hook in the system.

### Solution: EFFECT_STATIC Flow

**EFFECT_STATIC signal:**
- Runs every 20 ticks (1 second) on all equipped sigils
- Fired by `SignalHandler.processStaticEffects()` (lines 857-865)

**Registration Pattern:**
```yaml
ancient_crown:
  flows:
    - type: SIGNAL
      trigger: EFFECT_STATIC
      nodes:
        - type: EFFECT
          effect: REGISTER_ANCIENT_CROWN_IMMUNITY
          params:
            immunity_percent: "{immunity_percent}"
```

**New Effect Required:** `RegisterAncientCrownImmunityEffect.java`

**Implementation:**
```java
public boolean execute(EffectContext context) {
    Player player = context.getPlayer();
    double immunityPercent = context.getParams().getDouble("immunity_percent", 20);

    // Check if interceptor already registered (prevent duplication)
    InterceptionManager im = plugin.getInterceptionManager();
    boolean alreadyRegistered = im.getInterceptors(player).stream()
        .anyMatch(i -> i instanceof AncientCrownImmunityInterceptor);

    if (!alreadyRegistered) {
        AncientCrownImmunityInterceptor interceptor =
            new AncientCrownImmunityInterceptor(player, immunityPercent);
        im.registerInterceptor(player, interceptor);
    }

    return true;
}
```

**Cleanup:** ArmorChangeListener → when helmet removed → `unregisterAll(player)` for interceptors of type `AncientCrownImmunityInterceptor`

---

## 8. Seasonal Pass YAML Files

**File:** `src/main/resources/sigils/seasonal-pass.yml`

**Line Ranges:**
- Ancient Crown: 11-99
- King's Brace: 104-215
- Cleopatra: 220-327

**Verification:** All three sigils exist with placeholder flows. Ready for replacement.

---

## Key Findings Summary

| System | Status | Action Required |
|--------|--------|----------------|
| **SigilVariableManager** | ✅ Working | None - already functional |
| **UpdateChargeDREffect** | ✅ Working | None - already implemented |
| **AttributeModifierManager** | ⚠️ Missing interception | Add `fireIntercept()` call before line 97 |
| **PotionEffectEffect** | ⚠️ Missing interception | Add `fireIntercept()` call before line 101 |
| **ModifyAttributeEffect** | ⚠️ Missing interception | Add `fireIntercept()` call before line 205 |
| **FlowContext** | ❌ Missing SIGIL variable resolution | Add `{sigil.varname}` support in `resolveValue()` |
| **Ancient Crown Registration** | ❌ No mechanism | Create `RegisterAncientCrownImmunityEffect.java` |
| **InterceptionManager** | ✅ Working | Set CleopatraSuppressionInterceptor priority to 1 (currently 50) |

---

## Recommended Implementation Order

1. **Phase 1:** Fix FlowContext to support SIGIL variable resolution
   - Required for King's Brace condition checks

2. **Phase 2:** Fix interception integration in all three effects
   - Required for Cleopatra and Ancient Crown to work

3. **Phase 3:** Implement King's Brace YAML with charge flows
   - Depends on Phase 1

4. **Phase 4:** Create `RegisterAncientCrownImmunityEffect` + YAML
   - Depends on Phase 2

5. **Phase 5:** Update Cleopatra YAML
   - Depends on Phase 2

6. **Phase 6:** Build, deploy, test all three sigils

---

## Open Questions

**Q1:** Should Ancient Crown interceptor tier update dynamically if sigil is upgraded while equipped?
**A:** No - interceptor registered with tier at equip time. Re-equip to update.

**Q2:** Should charge variables persist across sessions (database storage)?
**A:** No - in-memory only, reset on logout. Future enhancement.

**Q3:** What happens if King's Brace ability is activated at <100 charges?
**A:** Cooldown should NOT be consumed. Message shows charges needed.

---

## Testing Strategy

### King's Brace Tests
1. Charge accumulation (10 hits → verify charge = 10)
2. Passive DR scaling (verify ARMOR attribute increases)
3. Charge cap at 100
4. Ability at 100 charges (consume all, apply Resistance)
5. Ability at <100 charges (show message, no cooldown)
6. Armor removal resets charge

### Ancient Crown Tests
1. Negative potion effect reduction (Slowness III → reduced by tier%)
2. Complete blocking at T5 (100% immunity)
3. Positive effects unaffected (Regeneration applies normally)
4. Negative attribute modifier reduction

### Cleopatra Tests
1. Buff removal (Resistance + Regeneration removed)
2. Suppression blocking (buffs can't be reapplied during duration)
3. Suppression expiration (buffs can be applied after duration)
4. Damage amplification (target takes +X% damage)

---

## Files Changed Summary

| File | Purpose | Lines Changed |
|------|---------|---------------|
| `FlowContext.java` | Add SIGIL variable resolution | +80 (new code block + helper) |
| `PotionEffectEffect.java` | Add interception check | +15 |
| `ModifyAttributeEffect.java` | Add interception check | +15 |
| `AttributeModifierManager.java` | Add interception check | +15 |
| `CleopatraSuppressionInterceptor.java` | Change priority 50→1 | 1 line |
| `AncientCrownImmunityInterceptor.java` | NEW FILE | +150 |
| `RegisterAncientCrownImmunityEffect.java` | NEW FILE | +60 |
| `seasonal-pass.yml` | Update all 3 sigils | Replace 3 sections |

**Total:** 8 files, ~350 lines of new/modified code
