# Sweep Attack Disabling Investigation

## Executive Summary

**Issue**: Sweep attack disabling appears non-functional despite comprehensive implementation.

**Status**: Implementation is technically correct but may face Paper API quirks or version-specific behavior.

## Current Implementation Analysis

### ✅ What's Been Done Correctly

**File**: `SweepAttackModule.java`

1. **Dual-Layer Disabling**:
   - Attribute-based: Sets `SWEEPING_DAMAGE_RATIO` to 0.0
   - Event-based: Cancels `ENTITY_SWEEP_ATTACK` events at LOWEST and HIGHEST priorities

2. **Lifecycle Management**:
   - Applied on player join (LegacyCombatManager.java:94)
   - Removed on player quit with cleanup (LegacyCombatManager.java:114)
   - Module properly registered and event listeners active

3. **Configuration**:
   - Enabled in combat.yml (line 42: `enabled: true`)
   - Toggleable via in-game GUI

## Potential Issues Discovered

### 1. **Attribute Name Version Mismatch**

**Research Finding**: The attribute name changed between Paper versions:
- **1.21.1**: `Attribute.PLAYER_SWEEPING_DAMAGE_RATIO`
- **1.21.4+**: `Attribute.SWEEPING_DAMAGE_RATIO`

**Current Code**: Uses `Attribute.SWEEPING_DAMAGE_RATIO`
**Project Version**: Paper 1.21.10 (from pom.xml)

**Verdict**: ✅ Attribute name is correct for the target version.

### 2. **Missing Sword Validation**

**BukkitOldCombatMechanics Approach**: They verify the player is holding a sword before processing.

**Current Code**: Cancels ALL `ENTITY_SWEEP_ATTACK` events without checking weapon type.

**Potential Issue**: May not be reaching the event handler due to other plugins or timing issues.

### 3. **Sweep Attack Mechanics**

From [Minecraft Wiki](https://minecraft.wiki/w/Melee_attack):

**Trigger Conditions**:
- Attack cooldown >= 84.8%
- Player on ground
- Not sprinting in straight line
- Weapon is a sword

**Damage Formula**: `1 + Attack_Damage × (Sweeping_Edge_Level / (Sweeping_Edge_Level + 1))`

**Critical Finding**: Even with Sweeping Edge 0, sweep attacks deal **1 HP base damage**.

### 4. **Event vs. Particle Separation**

**Important**: Setting `SWEEPING_DAMAGE_RATIO` to 0 prevents damage calculation, but the sweep **particle effect still appears**.

Players may *think* sweep is active if they see particles, even if damage is prevented.

## Known Issues in Other Plugins

### BukkitOldCombatMechanics Issue #133

**Report**: [GitHub Issue](https://github.com/gvlfm78/BukkitOldCombatMechanics/issues/133)

**Symptoms**: "Sweeping Edge does not appear to disable; still hits multiple mobs"

**Impact**: Even established plugins struggle with this feature, suggesting:
- Paper API quirks exist
- Server version differences matter
- Client-side vs server-side mismatch possible

## Diagnostic Approach

### Recommended Testing Steps

1. **Enable Debug Logging**:
   - Add console output when events are caught
   - Log attribute values after setting
   - Verify module is actually enabled

2. **Test Scenarios**:
   ```
   Scenario A: Single target attack (should work normally)
   Scenario B: Multiple mobs nearby (sweep should NOT damage them)
   Scenario C: With Sweeping Edge enchantment (should still be disabled)
   Scenario D: Particles visible but no damage (SUCCESS condition)
   ```

3. **Verify Event Firing**:
   - Check if `ENTITY_SWEEP_ATTACK` events are actually being generated
   - Test with debug mode in both LOWEST and HIGHEST priority handlers

4. **Attribute Verification**:
   - Log the actual attribute value after setting
   - Check if other plugins are modifying the attribute
   - Verify attribute persists between attacks

## Potential Fixes

### Fix 1: Enhanced Event Handling (Most Likely Solution)

**Problem**: Events might not be captured due to Paper-specific behavior.

**Solution**: Add sword validation and damage analysis fallback (like BukkitOldCombatMechanics).

### Fix 2: Enchantment Removal

**Problem**: Sweeping Edge enchantment might override the attribute.

**Solution**: Strip Sweeping Edge enchantments from swords when the module is active.

### Fix 3: Client-Side Packet Interception

**Problem**: Sweep might be calculated client-side.

**Solution**: Use ProtocolLib to intercept and modify damage packets.

### Fix 4: Cooldown Manipulation

**Problem**: Sweep requires 84.8% cooldown.

**Solution**: Prevent cooldown from ever reaching 84.8% (may break other mechanics).

## Comparative Analysis: Other Plugins

### BukkitOldCombatMechanics Implementation

**Key Differences**:
1. They use `@EventHandler(priority = EventPriority.LOWEST)` (same as us)
2. They verify `isHoldingSword()` before processing
3. They have a fallback damage calculation method
4. They track sweep locations to detect indirect hits

**Source**: [ModuleSwordSweep.java](https://github.com/kernitus/BukkitOldCombatMechanics/blob/master/src/main/java/kernitus/plugin/OldCombatMechanics/module/ModuleSwordSweep.java)

## Recommendations

### Immediate Actions

1. **Add Debug Logging** to confirm:
   - Module is enabled
   - Events are being fired
   - Attributes are being set correctly

2. **Implement Sword Check** like BukkitOldCombatMechanics

3. **Test Systematically** with multiple mobs to verify if damage is actually being dealt

### Long-Term Solutions

If event cancellation continues to fail:

1. **Option A**: Implement damage calculation fallback (compare expected vs actual damage)
2. **Option B**: Use ProtocolLib for deeper packet control
3. **Option C**: Consider it a Paper API limitation and document workaround

## References

- [Paper API Attribute Documentation](https://jd.papermc.io/paper/1.21.10/org/bukkit/attribute/Attribute.html)
- [Minecraft Wiki: Melee Attack](https://minecraft.wiki/w/Melee_attack)
- [BukkitOldCombatMechanics Source](https://github.com/kernitus/BukkitOldCombatMechanics)
- [EntityDamageEvent.DamageCause Docs](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/entity/EntityDamageEvent.DamageCause.html)

---

**Next Steps**: Implement diagnostic improvements and test systematically.
