# Targeting Validation Fix - Implementation Plan v2

**Goal**: Add validation to prevent ABILITY flows from activating without a valid player target

**Approach**: Use existing `FlowContext.setError()` mechanism to stop flow and show error message

---

## Architecture

**Validation point**: `AbstractEffect.getTarget()` method

**Flow when @Target is null**:
1. Effect calls `getTarget("@Target")`
2. Target resolution returns null (no bind target selected)
3. **NEW: Call `flowContext.setError("message")`**
4. This sets `cancelled = true` and `errorMessage`
5. FlowExecutor checks `cancelled` flag and stops execution
6. FlowExecutor shows error message to player
7. BindsListener sees flow was cancelled, doesn't set cooldown

**Key insight**: FlowContext already has error handling infrastructure - we just need to use it.

---

## Problem to Solve First

**Issue**: `AbstractEffect.execute(EffectContext context)` receives `EffectContext`, not `FlowContext`

**Need to check**: Does EffectContext have a reference to FlowContext?

**If NO**: We need to add FlowContext reference to EffectContext

**Research needed**:
- Check `EffectContext.java` for FlowContext reference
- Check how EffectContext is created in EffectNode
- Determine if we need to pass FlowContext through

---

## Phase 1: Add FlowContext Reference to EffectContext (if needed)

**File**: `src/main/java/com/miracle/arcanesigils/effects/EffectContext.java`

**Check if exists**: Does EffectContext already have a FlowContext field?

**If NO, add**:
```java
private FlowContext flowContext;

public FlowContext getFlowContext() {
    return flowContext;
}

// In Builder class
public Builder flowContext(FlowContext flowContext) {
    this.flowContext = flowContext;
    return this;
}
```

**Update EffectNode.java** to pass FlowContext when building EffectContext:
```java
EffectContext execContext = EffectContext.builder()
    .player(ownerPlayer)
    .sigil(sigil)
    .item(item)
    .flowContext(context)  // NEW: Pass FlowContext
    .build();
```

---

## Phase 2: Add Target Validation in AbstractEffect.getTarget()

**File**: `src/main/java/com/miracle/arcanesigils/effects/impl/AbstractEffect.java`

**Method**: `getTarget(String target, EffectContext context, double range)` (lines 188-205)

**Current code** (lines 192-198 for @Target):
```java
} else if (target.equalsIgnoreCase("@Target")) {
    TargetGlowManager targetManager = ArmorSetsPlugin.getInstance().getTargetGlowManager();
    if (targetManager != null) {
        LivingEntity bindTarget = targetManager.getTarget(context.getPlayer());
        if (bindTarget != null) {
            return bindTarget;
        }
    }
    return null;  // <-- Returns null if no target
}
```

**New code**:
```java
} else if (target.equalsIgnoreCase("@Target")) {
    TargetGlowManager targetManager = ArmorSetsPlugin.getInstance().getTargetGlowManager();
    if (targetManager != null) {
        LivingEntity bindTarget = targetManager.getTarget(context.getPlayer());
        if (bindTarget != null && !bindTarget.isDead()) {
            return bindTarget;
        }
    }

    // No valid target - stop flow with error
    FlowContext flowContext = context.getFlowContext();
    if (flowContext != null) {
        String sigilName = context.getSigil() != null
            ? TextUtil.stripColors(context.getSigil().getName())
            : "Ability";
        flowContext.setError(sigilName + " requires a target!");
    }

    return null;
}
```

**Changes**:
1. Added `!bindTarget.isDead()` check (dead targets are invalid)
2. Get FlowContext from EffectContext
3. Call `setError()` with user-friendly message
4. Return null (effect will fail, but flow already stopped)

---

## Phase 3: Verify FlowExecutor Error Handling

**File**: `src/main/java/com/miracle/arcanesigils/flow/FlowExecutor.java`

**Verify**: FlowExecutor.executeWithContext() checks `context.isCancelled()` at each step

**Expected behavior** (from research):
- Line 155, 208: Checks `context.isCancelled()` and stops execution
- Line 297-304: `handleError()` sends chat message to player

**No changes needed** if this already exists.

---

## Phase 4: Verify BindsListener Cooldown Logic

**File**: `src/main/java/com/miracle/arcanesigils/binds/BindsListener.java`

**Verify**: Cooldown is only set if flow wasn't cancelled

**Current code** (lines 647-650):
```java
// Set cooldown only if flow executed successfully
if (!flowContext.isCancelled()) {
    cooldownManager.setCooldown(player, flow.getId(), sigilName, cooldownSeconds);
}
```

**Expected**: If this check already exists, no changes needed.

**If NOT**: Add the `!flowContext.isCancelled()` check before setting cooldown.

---

## Testing Plan

### Test 1: @Target with No Target Selected

**Setup**: Don't select any target in binds menu

**Steps**:
1. Activate Cleopatra ability
2. Check console for errors
3. Check chat for error message

**Expected**:
- Chat: "Cleopatra requires a target!" (from FlowExecutor.handleError)
- Console: No errors/exceptions
- Cooldown: NOT on cooldown (verify with `/as cooldowns` or retry)

### Test 2: @Target with Valid Target

**Setup**: Select a player as target in binds menu

**Steps**:
1. Activate Cleopatra
2. Verify suppression applied to target

**Expected**:
- Cleopatra executes normally
- Target gets suppressed, damage amp applied
- Cooldown applied (180 seconds)

### Test 3: @Target with Dead Target

**Setup**: Select target, kill them, immediately activate

**Steps**:
1. Select player as target
2. Kill that player
3. Activate Cleopatra before target respawns

**Expected**:
- Chat: "Cleopatra requires a target!"
- No cooldown consumed

### Test 4: @Self Abilities Still Work

**Setup**: Test abilities using `target: "@Self"` (like Royal Bolster)

**Steps**:
1. Don't select any target
2. Activate Royal Bolster

**Expected**:
- Royal Bolster works normally (self-buffs don't need bind target)
- No error message

### Test 5: @Victim in Combat

**Setup**: Hit an enemy player, trigger ON_ATTACK sigil with @Victim

**Steps**:
1. Don't select bind target
2. Hit an enemy player
3. ON_ATTACK sigil fires

**Expected**:
- Sigil works normally (@Victim comes from combat context, not bind target)
- No error message

---

## Rollback Plan

If validation causes issues:

1. **Phase 2 rollback**: Remove `setError()` block from AbstractEffect.getTarget()
2. **Phase 1 rollback**: Remove FlowContext reference from EffectContext (if added)
3. Behavior returns to current (silent fail with null target)

---

## Files to Modify

1. **EffectContext.java** (if FlowContext reference doesn't exist)
   - Add FlowContext field and getter
   - Add flowContext() method to Builder

2. **EffectNode.java** (if Phase 1 needed)
   - Pass FlowContext when building EffectContext

3. **AbstractEffect.java** (lines 192-198)
   - Add target validation with setError() call

4. **BindsListener.java** (lines 647-650, verify only)
   - Confirm cooldown check includes `!isCancelled()`

---

## Success Criteria

✅ @Target with no target shows error message
✅ Error message does not consume cooldown
✅ @Target with valid target works normally
✅ Dead targets trigger error message
✅ @Self abilities unaffected (no target needed)
✅ @Victim in combat unaffected (uses combat context)
✅ No console errors or exceptions
✅ Build compiles successfully

---

## Estimated Complexity

**Low-Medium**:
- 1-3 files depending on EffectContext reference
- ~20-30 lines of code total
- Uses existing error handling infrastructure
