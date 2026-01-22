# Targeting Validation Fix - Research

**Date**: 2026-01-21
**Goal**: Add validation to prevent ABILITY flows from activating without a valid player target

---

## Problem Statement

- **Issue**: Cleopatra (legs) and Royal Guard (axe) abilities can activate without a target
- **Result**: Flow executes with null target, effects fail silently, cooldown is consumed
- **Expected**: Show error message, don't activate flow, don't consume cooldown

---

## Key Findings

### 1. Bind Activation Flow

**Entry Point**: `BindsListener.activateBind()` (lines 388-459)

1. Captures target from TargetGlowManager (lines 411-416)
2. Schedules delayed activation for each sigil (lines 438-446)
3. Calls `activateSigilWithItem()` after delay

**Critical**: Target is captured ONCE before delays, then passed to all sigils.

### 2. Target Resolution in activateSigilWithItem()

**File**: `BindsListener.java:496-691`

**Flow**:
1. Gets ABILITY flows from sigil (lines 506-508)
2. For each flow:
   - **Checks cooldown** (line 596-609)
   - **Resolves target** (lines 538-545) - prefers capturedTarget, falls back to TargetGlowManager
   - **Builds EffectContext** with victim=target (line 550)
   - **Executes flow** (lines 613-614)
   - **Sets cooldown** (lines 647-650)

**Key Code** (lines 538-545):
```java
org.bukkit.entity.LivingEntity target = capturedTarget;
if (target == null) {
    TargetGlowManager glowManager = plugin.getTargetGlowManager();
    if (glowManager != null) {
        target = glowManager.getTarget(player);
    }
}
```

### 3. Where @Target Gets Resolved

**AbstractEffect.getTarget()** (lines 160-205):

```java
else if (target.equalsIgnoreCase("@Target")) {
    TargetGlowManager targetManager = ArmorSetsPlugin.getInstance().getTargetGlowManager();
    if (targetManager != null) {
        LivingEntity bindTarget = targetManager.getTarget(context.getPlayer());
        if (bindTarget != null) {
            return bindTarget;
        }
    }
    return null;  // <-- Returns null if no target!
}
```

Effects using `@Target` will get null if TargetGlowManager has no target for the player.

### 4. Cooldown Timeline

**Current Order**:
1. Check if on cooldown (line 596-609)
2. Execute flow (line 613-614)
3. Set cooldown (line 647-650)

**Important**: Cooldown is set AFTER execution, so early return prevents cooldown waste.

### 5. TargetGlowManager

**File**: `src/main/java/com/miracle/arcanesigils/binds/TargetGlowManager.java`

**Key Methods**:
- `getTarget(Player player)` (line 340-346): Returns glowing entity or null
- Only tracks entities while glow task is active (when binds UI toggled ON)

---

## Implementation Recommendation

### Validation Point

**File**: `BindsListener.java`
**Method**: `activateSigilWithItem()`
**Line**: After 545 (after target resolution, before flow execution)

**Logic**:
```java
// After resolving target (line 538-545)
org.bukkit.entity.LivingEntity target = capturedTarget;
if (target == null) {
    TargetGlowManager glowManager = plugin.getTargetGlowManager();
    if (glowManager != null) {
        target = glowManager.getTarget(player);
    }
}

// VALIDATION: Check if target required but null
if (target == null || target.isDead()) {
    // Show error message
    player.sendMessage(Component.text(sigilName + " ", NamedTextColor.RED)
        .append(Component.text("requires a target!", NamedTextColor.GRAY)));
    return; // Skip flow execution
}

// Continue with context building (line 550+)
```

### Error Message Pattern

Use existing pattern from lines 666-690:
```java
Component feedback = Component.text(sigilName + " ", NamedTextColor.RED)
    .append(Component.text("requires a target!", NamedTextColor.GRAY));
player.sendMessage(feedback);
```

---

## Files to Modify

1. **BindsListener.java** (lines 538-552)
   - Add target validation after resolution
   - Show error message if null/dead
   - Early return to skip flow execution

---

## Testing Approach

1. Test without target selected:
   - Activate Cleopatra → should see "Cleopatra requires a target!" message
   - Activate Royal Guard → should see "Royal Guard requires a target!" message
   - Check cooldowns → should NOT be on cooldown

2. Test with valid target:
   - Select target from binds menu
   - Activate abilities → should work normally
   - Effects should apply to target

3. Test with dead target:
   - Select target, kill them
   - Activate ability → should show "requires a target!" message
   - Should not consume cooldown
