# Targeting Validation Fix - Implementation Plan

**Goal**: Add validation to prevent ABILITY flows from activating without a valid player target

**Based on**: `scratch/targeting-fix-research.md`

**Affects**: Cleopatra (legs), Royal Guard (axe), and any other ABILITY-type sigils using `@Target`

---

## Architecture

**Single validation point in `BindsListener.activateSigilWithItem()`**:
- After target is resolved (line 545)
- Before EffectContext is built (line 550)
- Before flow execution (line 613)

**Flow**:
1. Resolve target from capturedTarget or TargetGlowManager
2. **NEW: Check if target is null or dead**
3. **NEW: If invalid, show error and return early (no cooldown)**
4. If valid, build context and execute flow normally

---

## Phase 1: Add Target Validation

**File**: `src/main/java/com/miracle/arcanesigils/binds/BindsListener.java`

**Modify**: Method `activateSigilWithItem()`, lines 538-552

**Current Code** (lines 538-552):
```java
// Get target - prefer captured target, but fall back to TargetGlowManager
org.bukkit.entity.LivingEntity target = capturedTarget;
if (target == null) {
    TargetGlowManager glowManager = plugin.getTargetGlowManager();
    if (glowManager != null) {
        target = glowManager.getTarget(player);
    }
}

// Victim is set here
org.bukkit.entity.LivingEntity victim = target;

// Build context
EffectContext.Builder contextBuilder = EffectContext.builder()
        .player(player)
        .sigil(sigil);

if (victim != null && !victim.isDead()) {
    contextBuilder.victim(victim);
}
```

**New Code** (replace lines 538-560):
```java
// Get target - prefer captured target, but fall back to TargetGlowManager
org.bukkit.entity.LivingEntity target = capturedTarget;
if (target == null) {
    TargetGlowManager glowManager = plugin.getTargetGlowManager();
    if (glowManager != null) {
        target = glowManager.getTarget(player);
    }
}

// VALIDATION: Check if target is required but missing/dead
if (target == null || target.isDead()) {
    // Show error message to player
    String sigilName = TextUtil.stripColors(sigil.getName());
    Component feedback = Component.text(sigilName + " ", NamedTextColor.RED)
            .append(Component.text("requires a target!", NamedTextColor.GRAY));
    player.sendMessage(feedback);
    return; // Skip flow execution - no cooldown consumed
}

// Victim is set here
org.bukkit.entity.LivingEntity victim = target;

// Build context
EffectContext.Builder contextBuilder = EffectContext.builder()
        .player(player)
        .sigil(sigil)
        .victim(victim);  // Always set victim since we validated target exists
```

**Changes**:
1. Added validation block after target resolution
2. Show error message using existing Component pattern
3. Early return skips flow execution (no cooldown consumed)
4. Removed conditional `if (victim != null)` since we validated it exists

**Expected Behavior**:
- Without target: Error message shown, no cooldown, no flow execution
- With valid target: Normal behavior, flow executes, cooldown applies
- With dead target: Error message shown, no cooldown, no flow execution

---

## Phase 2: Testing

### Test 1: No Target Selected

**Steps**:
1. Ensure no target in binds menu (no entity glowing)
2. Activate Cleopatra ability
3. Activate Royal Guard ability

**Expected**:
- Console: No errors
- Chat: "Cleopatra requires a target!" (red + gray)
- Chat: "Royal Guard requires a target!" (red + gray)
- Cooldowns: NOT on cooldown (check with `/as cooldowns` or try activating again)

### Test 2: Valid Target Selected

**Steps**:
1. Open binds menu, select a player as target
2. Activate Cleopatra
3. Verify suppression applied to target
4. Select different target
5. Activate Royal Guard
6. Verify zombie spawns and attacks target

**Expected**:
- Cleopatra: Target gets suppressed, damage amp applied
- Royal Guard: Zombie spawns, immediately targets selected player
- Both abilities go on cooldown normally

### Test 3: Target Dies Between Selection and Activation

**Steps**:
1. Select a player as target
2. Kill that player (or have them die)
3. Immediately activate Cleopatra

**Expected**:
- Chat: "Cleopatra requires a target!" (dead entity counts as no target)
- No cooldown consumed

### Test 4: Multiple Sigils in Same Bind

**Steps**:
1. Bind multiple ABILITY sigils to same slot
2. Don't select a target
3. Activate the bind

**Expected**:
- Each sigil shows "requires a target!" message
- None of them go on cooldown
- No flow executions happen

---

## Verification Commands

**Check cooldowns**:
```
/as cooldowns
```

**Check active effects**:
```
/effect @s
```

**Check bind target**:
- Target should glow when binds UI is open
- Use TargetGlowManager debug if available

---

## Rollback Plan

If validation causes issues:

1. Remove lines added in Phase 1
2. Restore original code from git
3. Flow behavior returns to current (silent fail with null target)

---

## Success Criteria

✅ Activating ABILITY without target shows error message
✅ Error message does not consume cooldown
✅ Activating ABILITY with valid target works normally
✅ Dead targets are treated as invalid
✅ Multiple sigils in same bind all validate correctly
✅ No console errors or exceptions
✅ Build compiles successfully

---

## Files Modified

- `src/main/java/com/miracle/arcanesigils/binds/BindsListener.java` (lines 538-560)

---

## Estimated Complexity

**Low** - Single file, single method, ~10 lines of code, straightforward validation logic
