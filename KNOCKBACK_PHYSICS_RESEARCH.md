# Minecraft Knockback Physics: 1.8 vs 1.21 Comprehensive Analysis

**Last Updated:** 2026-01-11  
**Research Sources:** Decompiled 1.8 NMS, Minecraft Wiki, Community Testing, Paper API Documentation

---

## Executive Summary

**Critical Finding:** Vertical knockback behavior changed DRAMATICALLY in 1.9+, which explains why 1.8 configs don't work on modern versions.

**Key Differences:**
- **1.8**: Vertical KB = 0.2 base, capped at 0.4
- **1.9+**: Vertical KB = 0.5 base (2.5x higher!)
- **1.9+**: Airborne entities receive NO vertical knockback
- **1.21**: Removed 60% knockback resistance cap

---

## Part 1: Exact 1.8 Knockback Implementation

### Source: Spigot 1.8 R3 EntityLiving.java (Decompiled)

**Method:** `EntityLiving.a(Entity entity, float f, double d0, double d1)` (lines 885-902)

```java
// 1.8 KNOCKBACK IMPLEMENTATION
public void a(Entity entity, float f, double d0, double d1) {
    // f = knockback strength (enchantment level)
    // d0 = X direction component
    // d1 = Z direction component
    
    // Step 1: HALVE current velocity (friction = 2.0)
    this.motX /= 2.0D;
    this.motY /= 2.0D;
    this.motZ /= 2.0D;
    
    // Step 2: Calculate direction
    double distance = Math.sqrt(d0 * d0 + d1 * d1);
    
    // Step 3: Apply base knockback (0.4 horizontal)
    this.motX -= d0 / distance * 0.4D;
    this.motZ -= d1 / distance * 0.4D;
    
    // Step 4: Apply vertical knockback (FIXED 0.2)
    this.motY += 0.2D;  // ADDED, not set
    
    // Step 5: Cap vertical velocity
    if (this.motY > 0.4000000059604645D) {
        this.motY = 0.4000000059604645D;
    }
}
```

### 1.8 Constants (Confirmed):

| Constant | Value | Purpose |
|----------|-------|---------|
| **Friction Divisor** | 2.0 | Halves current velocity |
| **Horizontal Multiplier** | 0.4 | Base horizontal knockback |
| **Vertical Addition** | 0.2 | Base upward impulse (ADDED to motY) |
| **Vertical Cap** | 0.4 | Maximum Y velocity |
| **Sprint Horizontal Bonus** | 0.425 | Added when sprinting |
| **Sprint Vertical Bonus** | 0.085 | Added when sprinting |

### 1.8 Sprint Knockback (EntityHuman.attack())

Applied AFTER base knockback:
```java
// If attacker is sprinting:
victim.motX += direction.x * 0.425;
victim.motY += 0.085;  // ADDED
victim.motZ += direction.z * 0.425;

// Attacker loses horizontal velocity
attacker.motX *= 0.6;
attacker.motZ *= 0.6;
attacker.setSprinting(false);  // W-tap mechanic
```

### Tested 1.8 Knockback Values (Community Data)

Source: [Hypixel Forums - Exact Horizontal Knockback Values](https://hypixel.net/threads/exact-horizontal-knockback-values-in-vanilla-1-8.2360655/)

**Grounded Players (<50ms ping, flat ground):**
- Base hit (no sprint): **1.984 blocks**
- Sprint hit: **~3.0-3.5 blocks** (estimated based on 0.425 bonus)
- Projectiles (rod/snowball): **1.984 blocks** (consistent)

---

## Part 2: Major Changes in 1.9+

### Source: [Minecraft Wiki - Knockback Mechanics](https://minecraft.wiki/w/Knockback_(mechanic))

### Critical Change #1: Vertical Knockback INCREASED

**1.8 Vertical:**
```java
motY += 0.2;  // Added to current Y velocity
if (motY > 0.4) motY = 0.4;
```

**1.9+ Vertical:**
```java
motY += 0.5;  // 2.5x HIGHER than 1.8!
// No cap mentioned in modern code
```

**Impact:** Using 1.8 configs (vertical = 0.4) on 1.9+ results in LOWER vertical KB than vanilla 1.9+.

### Critical Change #2: Airborne Entity Behavior

**1.8:** Airborne entities receive BOTH horizontal and vertical knockback (same as grounded)

**1.9+ (Java):** Airborne entities receive HORIZONTAL knockback ONLY (no vertical component)

**Bedrock:** Airborne entities receive same KB as grounded (like 1.8)

**Impact:** This explains why players "fly up" - modern Minecraft doesn't apply vertical KB to mid-air targets, but our 1.8 system does.

### Critical Change #3: Attack Cooldown Requirements

**1.8:** Sprint knockback requires attacker to be sprinting (no other conditions)

**1.9+:** Sprint knockback requires:
- Attacker is sprinting
- Attack cooldown charge ≥ 84.8%
- Overrides critical hits if both would apply

**Impact:** Spamming attacks in 1.9+ doesn't apply sprint KB. Our 1.8 system always applies it.

### Critical Change #4: Knockback Enchantment Scaling

**1.8 & 1.9:** Knockback I/II directly adds to horizontal KB

**Formula:** `horizontal_bonus = 0.4 * (1 + knockback_level)`

**Impact:** Same across versions (no change needed)

### Critical Change #5: Knockback Resistance Cap

**1.8 - 1.20:** 60% knockback resistance cap

**1.21+:** Cap REMOVED

**Impact:** Netherite armor (40% resistance) and Blast Protection IV now interact differently.

---

## Part 3: Ground Detection Differences

### Source: [TrueCraft Wiki - Entity Movement](https://github.com/ddevault/TrueCraft/wiki/Entity-Movement-And-Physics), Spigot Forums

### Ground Detection Logic

**All Versions:**
```java
onGround = (motY < 0) && (vertical collision detected while moving down);
```

**1.14 Collision Order Change:**

**Pre-1.14:** Always Y-X-Z collision order

**1.14+:** Dynamic collision order based on velocity:
- If `abs(motZ) > abs(motX)`: Order is Y-X-Z
- Otherwise: Order is Y-Z-X

**Impact:** Corner cutting and edge cases behave differently. May affect KB when near edges/walls.

### Ground Detection Timing Issue

**Bukkit API Problem:**
```java
victim.isOnGround()  // Checks CURRENT tick
// But knockback is applied BEFORE ground update
```

**Our Fix (v1.0.552):**
```java
boolean onGround = victim.isOnGround();
if (onGround || currentVelocity.getY() < vertical) {
    currentVelocity.setY(vertical);  // SET instead of ADD
}
```

This handles the timing issue by also checking if victim is falling (negative Y).

---

## Part 4: Friction and Velocity Decay

### Source: [TrueCraft Wiki - Entity Physics](https://github.com/ddevault/TrueCraft/wiki/Entity-Movement-And-Physics)

### Friction Constants (Unchanged Across Versions)

| Surface | Friction Multiplier |
|---------|-------------------|
| Normal Blocks | 0.6 per tick |
| Slime Blocks | 0.8 per tick |
| Ice | 0.98 per tick |
| Packed Ice | 0.98 per tick |
| Blue Ice | 0.989 per tick |

### Velocity Application Order (Every Tick)

1. **Apply Acceleration** (knockback, gravity, etc.)
2. **Apply Drag** (air resistance = 0.98 for Y, 0.91 for X/Z when airborne)
3. **Update Position** (move entity by velocity)
4. **Apply Friction** (if on ground, multiply velocity by surface friction)

**Critical:** Knockback "friction" (halving velocity) is NOT the same as surface friction.

---

## Part 5: Modern Minecraft (1.21) Specifics

### Gravity Constant (Unchanged)

```java
motY -= 0.08;  // Per tick (default entity gravity)
```

**Player Falling Velocity:**
- Stationary on ground: `motY = -0.0784`
- Free falling (terminal velocity): `motY ≈ -3.92`

### Entity Knockback Event (Paper API)

Source: [Paper API EntityKnockbackEvent](https://jd.papermc.io/paper/1.21.4/io/papermc/paper/event/entity/EntityKnockbackEvent.html)

**Event Types:**
- `EntityKnockbackEvent` (base class)
- `EntityPushedByEntityAttackEvent` (melee attacks)

**Critical API Notes:**
- Knockback value is READ-ONLY
- Must use `setKnockback(Vector)` to modify
- Event fires BEFORE vanilla KB is applied
- Cancelling prevents ALL knockback

### Known Paper Issues

**Issue #11055:** 0 damage sources don't apply knockback to players (bug)

**Issue #7168:** EntityKnockbackByEntityEvent called with wrong entities (fixed)

---

## Part 6: Why 1.8 Configs Don't Work on 1.21

### Problem #1: Vertical Velocity Is ADDED, Not SET

**1.8 Code:**
```java
motY += 0.2;  // If falling (-0.3 Y), result is -0.1 (goes down!)
```

**What Should Happen:**
```java
if (onGround || motY < vertical) {
    motY = vertical;  // SET to 0.4, always goes up
}
```

**Fix:** We implemented this in v1.0.552

### Problem #2: Modern Versions Use Higher Base Values

**1.8 Defaults:**
- Horizontal: 0.4
- Vertical: 0.2

**1.9+ Vanilla:**
- Horizontal: ~0.4 (similar)
- Vertical: 0.5 (2.5x higher!)

**Recommendation:** Increase default vertical to 0.5 for 1.9+ feel, or keep 0.2 for true 1.8 feel.

### Problem #3: Airborne Target Handling

**1.8:** Applies full KB to airborne targets

**1.9+:** No vertical KB for airborne targets

**Current System:** Applies vertical KB regardless (matches 1.8)

**Potential Issue:** Players launched into air continue getting vertical KB, stacking higher than vanilla.

**Possible Fix:**
```java
if (!victim.isOnGround()) {
    // 1.9+ behavior: horizontal only
    vertical = 0.0;
}
```

### Problem #4: Sprint Detection

**1.8:** Only checks if sprinting

**1.9+:** Also checks attack cooldown ≥ 84.8%

**Current System:** Matches 1.8 (no cooldown check)

**Impact:** Our system applies sprint KB more frequently than 1.9+.

---

## Part 7: Recommended Config Adjustments

### For "Authentic 1.8" Feel:

```yaml
knockback:
  friction: 2.0          # Exactly 1.8
  horizontal: 0.4        # Exactly 1.8
  vertical: 0.4          # Raised from 0.2 to account for SET behavior
  vertical-limit: 0.4    # Exactly 1.8
  extra-horizontal: 0.425  # Exactly 1.8
  extra-vertical: 0.085   # Exactly 1.8
```

**Note:** We use `vertical: 0.4` instead of 0.2 because we SET Y velocity (matching 1.8 cap) rather than adding 0.2.

### For "Reduced KB" (Less Floaty):

```yaml
knockback:
  friction: 2.5          # Faster decay
  horizontal: 0.3        # Less horizontal push
  vertical: 0.35         # Less bounce
  vertical-limit: 0.4    # Same cap
  extra-horizontal: 0.4   # Less sprint bonus
  extra-vertical: 0.08    # Less sprint lift
```

### For "Combo-Friendly" (Higher KB):

```yaml
knockback:
  friction: 2.0          # Standard decay
  horizontal: 0.45       # More horizontal push
  vertical: 0.45         # Higher bounce
  vertical-limit: 0.5    # Allow higher combos
  extra-horizontal: 0.5   # Stronger sprint hits
  extra-vertical: 0.1     # More sprint lift
```

---

## Part 8: Future Improvements

### Consideration #1: Airborne Detection

**Implement 1.9+ Behavior:**
```java
if (!victim.isOnGround()) {
    // No vertical KB for airborne targets
    finalVelocity.setY(Math.min(currentVelocity.getY(), 0));
}
```

**Config Option:** `apply-vertical-when-airborne: false` (default false for 1.9+ feel)

### Consideration #2: Attack Cooldown for Sprint KB

**1.9+ Accurate:**
```java
if (attacker.isSprinting() && attacker.getAttackCooldown() >= 0.848) {
    // Apply sprint bonus
}
```

**Config Option:** `require-cooldown-for-sprint: false` (default false for 1.8 feel)

### Consideration #3: Velocity Debugging

Add optional debug output:
```java
plugin.getLogger().info(String.format(
    "[KB] %s → %s: onGround=%b, oldY=%.3f, newY=%.3f",
    attacker.getName(), victim.getName(),
    victim.isOnGround(), oldVelocity.getY(), newVelocity.getY()
));
```

**Config Option:** `debug-knockback: false`

### Consideration #4: Per-World Knockback Profiles

Allow different KB configs per world (e.g., "practice" vs "survival")

---

## Part 9: Testing Methodology

### How to Verify Accurate 1.8 KB:

1. **Horizontal Distance Test**
   - Two players on flat ground
   - Attacker hits victim (no sprint)
   - Measure horizontal distance traveled
   - **Expected:** ~2.0 blocks

2. **Vertical Height Test**
   - Hit grounded player
   - Measure max Y height reached
   - **Expected:** ~0.6-0.8 blocks above ground

3. **Sprint Difference Test**
   - Compare sprint vs non-sprint hit distance
   - **Expected:** Sprint adds ~40-50% more distance

4. **Falling Target Test**
   - Hit player while falling
   - **1.8 Behavior:** Should bounce UP regardless
   - **1.9+ Behavior:** Should only push horizontally

5. **Combo Test**
   - Hit airborne target repeatedly
   - **1.8 Behavior:** Can juggle indefinitely
   - **1.9+ Behavior:** Only first hit applies vertical

---

## Sources

### Official Documentation:
- [Minecraft Wiki - Knockback Mechanics](https://minecraft.wiki/w/Knockback_(mechanic))
- [Minecraft Wiki - Knockback Enchantment](https://minecraft.fandom.com/wiki/Knockback)
- [Paper API - EntityKnockbackEvent](https://jd.papermc.io/paper/1.21.4/io/papermc/paper/event/entity/EntityKnockbackEvent.html)

### Decompiled Source Code:
- [Spigot 1.8 R3 EntityLiving.java](https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/EntityLiving.java)
- [Paper 1.7 Explosion Knockback Patch](https://github.com/PaperMC/Paper-1.7/blob/master/Spigot-Server-Patches/0070-Disable-explosion-knockback.patch)

### Community Research:
- [Hypixel Forums - 1.8 Knockback Values](https://hypixel.net/threads/exact-horizontal-knockback-values-in-vanilla-1-8.2360655/)
- [SpigotMC - Knockback Changes 1.8 vs 1.9](https://www.spigotmc.org/threads/knockback-changes-1-8-and-1-9.171234/)
- [OldCombatMechanics Plugin](https://github.com/kernitus/BukkitOldCombatMechanics)

### Technical Documentation:
- [TrueCraft Wiki - Entity Movement](https://github.com/ddevault/TrueCraft/wiki/Entity-Movement-And-Physics)
- [Minecraft Parkour Wiki - Collisions](https://www.mcpk.wiki/wiki/Collisions)
- [Minecraft Parkour Wiki - Version Differences](https://www.mcpk.wiki/wiki/Version_Differences)

---

**End of Research Document**
