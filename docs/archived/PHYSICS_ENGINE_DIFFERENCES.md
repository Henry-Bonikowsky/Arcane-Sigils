# Minecraft Physics Engine: 1.8 vs 1.21 - WHY IT FEELS WRONG

**Research Date:** 2026-01-11  
**Purpose:** Explain why 1.8 PvP pros say modern combat "doesn't feel right"

---

## TL;DR - The Core Problems

1. **Ground friction is 5x STRONGER** than air friction (0.546 vs 0.91 per tick)
2. **Momentum threshold changed** from 0.005 to 0.003 (you stop moving differently)
3. **Collision order changed** in 1.14 (ground detection timing different)
4. **Slipperiness application changed** in 1.15 (only affects blocks ≤0.5 tall)
5. **"Blips" patched** in 1.14 (step mechanics no longer give artificial height)
6. **Airborne knockback behavior** changed in 1.9 (horizontal only, no vertical)

**The ground IS fucking with knockback** - it decays velocity 5x faster than being airborne.

---

## Part 1: The Velocity Decay Problem

### How Velocity Decays (EVERY TICK)

Source: [Minecraft Parkour Wiki - Horizontal Movement](https://www.mcpk.wiki/wiki/Horizontal_Movement_Formulas)

**In the Air:**
```
velocity = velocity × 0.91
```
Decay rate: **9% per tick** (keeps 91%)

**On the Ground:**
```
velocity = velocity × slipperiness × 0.91
velocity = velocity × 0.6 × 0.91
velocity = velocity × 0.546
```
Decay rate: **45.4% per tick** (keeps only 54.6%!)

### Why This Destroys Knockback Feel

When you get hit on the ground:
1. Knockback applies velocity (let's say 3.0 blocks/tick)
2. **NEXT TICK:** velocity = 3.0 × 0.546 = **1.638** (lost 45% instantly!)
3. **TICK 2:** velocity = 1.638 × 0.546 = **0.894** (lost another 45%)
4. **TICK 3:** velocity = 0.894 × 0.546 = **0.488** (stopped almost completely)

**In 3 ticks (0.15 seconds), ground friction kills 84% of knockback velocity.**

Compare to being airborne:
1. Knockback applies velocity (3.0 blocks/tick)
2. **NEXT TICK:** velocity = 3.0 × 0.91 = **2.73** (lost only 9%)
3. **TICK 2:** velocity = 2.73 × 0.91 = **2.48** (still strong)
4. **TICK 3:** velocity = 2.48 × 0.91 = **2.26** (barely slowed)

**In 3 ticks airborne, only lost 25% of velocity vs 84% on ground.**

### The 1.8 Feel Problem

In 1.8 PvP, combos work because:
- First hit launches target into AIR
- While airborne, knockback decays slowly (0.91)
- You can hit them again before they touch ground
- **Ground contact KILLS the combo** (velocity drops to 54.6%)

**Modern Minecraft amplifies this problem** because the ground detection is more aggressive (1.14 collision changes).

---

## Part 2: Momentum Threshold (Why You "Stop" Differently)

Source: [Minecraft Parkour Wiki - Momentum Threshold](https://www.mcpk.wiki/wiki/Momentum_Threshold)

### The Threshold Rule

**At the end of EVERY tick:**
```
if (abs(velocityX) < threshold) velocityX = 0;
if (abs(velocityZ) < threshold) velocityZ = 0;
```

**1.8 and earlier:**
```
threshold = 0.005
```

**1.9+ (Modern):**
```
threshold = 0.003
```

### Why This Matters for Combat

**Scenario:** Player gets hit and is sliding backward with velocity 0.004

**1.8 Behavior:**
- 0.004 < 0.005 → **STOP INSTANTLY** (velocity set to 0)
- Player "locks" into position after knockback ends

**1.9+ Behavior:**
- 0.004 > 0.003 → **KEEPS SLIDING** for 1-2 more ticks
- Player continues drifting slightly after knockback ends

**Impact:** Movement feels "floatier" and less "snappy" in 1.9+ because small velocities persist longer.

### The Physics Formula

After applying friction:
```
newVelocity = oldVelocity × friction

if (newVelocity < momentum_threshold) {
    newVelocity = 0;  // INSTANT STOP
}
```

This creates a "hard stop" when velocity gets small enough.

**1.8:** Harder stop (threshold 0.005)  
**1.9+:** Softer stop (threshold 0.003)

---

## Part 3: Ground Detection Timing (1.14 Collision Changes)

Source: [Minecraft Parkour Wiki - Version Differences](https://www.mcpk.wiki/wiki/Version_Differences)

### Collision Order Change

**Pre-1.14 (includes 1.8):**
- Collision order: **Y-axis, X-axis, Z-axis** (always)
- Predictable, consistent

**1.14+ (Modern):**
- Collision order: **DYNAMIC** based on velocity
- If `abs(motZ) > abs(motX)`: Order is Y-X-Z
- Otherwise: Order is Y-Z-X
- **Unpredictable, varies per situation**

### Why This Breaks 1.8 Feel

**1.8 Behavior:**
1. Y collision (check if landing on ground)
2. X collision (check horizontal walls)
3. Z collision (check horizontal walls)
- Always in this order, muscle memory works

**1.14+ Behavior:**
1. Y collision (check ground)
2. **X or Z collision depending on your movement direction**
- Order changes dynamically
- **Ground detection can happen "earlier" depending on velocity direction**

### Impact on Knockback

When knocked back diagonally (most PvP hits):
- **1.8:** Y collision happens first, then horizontal
- **1.14+:** If moving more in Z than X, order is Y-X-Z; otherwise Y-Z-X
- This means **you "hit the ground" at different times** depending on knockback angle

**Result:** Unpredictable ground contact = unpredictable velocity loss

---

## Part 4: Slipperiness Change (1.15 - The "Slab Problem")

Source: [Minecraft Parkour Wiki - Version Differences](https://www.mcpk.wiki/wiki/Version_Differences)

### What Changed in 1.15

**Pre-1.15 (includes 1.8):**
- **ALL blocks** apply their slipperiness value to entities standing on them
- Normal blocks: 0.6 friction
- Ice: 0.98 friction

**1.15+ (Modern):**
- **ONLY blocks ≤0.5 blocks tall** apply slipperiness
- Standing on SLABS: No slipperiness applied!
- Standing on STAIRS: No slipperiness applied!
- Standing on FULL BLOCKS: Slipperiness applied normally

### Why This Matters

If your PvP arena has:
- Slabs
- Stairs
- Any partial blocks

**The ground friction behaves completely differently than 1.8.**

**Example:**
- Standing on slab in 1.8: velocity × 0.6 × 0.91 = **0.546 decay**
- Standing on slab in 1.15+: velocity × ??? (inconsistent behavior)

This means **knockback distance varies depending on what block you're standing on**.

### Test This

1. Hit someone on a FULL block → measure knockback distance
2. Hit someone on a SLAB → measure knockback distance
3. **Compare to 1.8** - they should be the same, but in 1.15+ they're different

---

## Part 5: "Blips" Patched (1.14 Step Mechanics)

Source: [Minecraft Parkour Wiki - Version Differences](https://www.mcpk.wiki/wiki/Version_Differences)

### What Were "Blips"?

In pre-1.14 versions (includes 1.8):
- **Step height is 0.6 blocks**
- When stepping up, player gains small **artificial upward velocity**
- This created "blips" - tiny upward pops when walking over edges

**1.14+ (Modern):**
- **"Blips have been patched"**
- Step mechanics no longer give artificial height
- Stepping up is "smoother" but feels different

### Why This Affects Combat

**1.8 Behavior:**
- Player walks forward during combat
- Steps up tiny elevation changes (0.1-0.5 blocks)
- Gets micro-boosts upward from step mechanics
- **Stays slightly more airborne** during combat

**1.14+ Behavior:**
- Player walks forward during combat
- Steps up elevation changes
- **NO micro-boosts**
- **Stays more grounded** during combat

**Impact:** In 1.14+, players "stick to the ground" more, triggering ground friction (0.546) instead of air friction (0.91).

This amplifies the velocity decay problem.

---

## Part 6: Sneaking Height Changes

Source: [Minecraft Parkour Wiki - Version Differences](https://www.mcpk.wiki/wiki/Version_Differences)

### Height Values

**1.8:**
- Standing: 1.8m
- Sneaking: **1.8m** (no change!)

**1.9-1.13:**
- Standing: 1.8m
- Sneaking: **1.65m** (reduced)

**1.14+ (Modern):**
- Standing: 1.8m
- Sneaking: **1.5m** (further reduced)

### Why This Matters

**Ceiling Collision:**
- In 1.8, sneaking under 2-block ceilings worked normally
- In 1.14+, player can be **forced under ceilings between 1.5-1.8 blocks**
- Changes hitbox behavior during combat

**Hitbox Shape:**
- Smaller hitbox = different collision detection
- Affects when you're considered "on ground"
- **Could cause faster ground contact during knockback**

---

## Part 7: Airborne Knockback (1.9 Change)

Source: [Minecraft Wiki - Knockback Mechanics](https://minecraft.wiki/w/Knockback_(mechanic))

### The Critical Change

**1.8:**
- Airborne entities receive **FULL knockback** (horizontal + vertical)
- You can "juggle" players indefinitely
- Combos work by keeping target airborne

**1.9+ (Java Edition):**
- Airborne entities receive **HORIZONTAL knockback ONLY**
- **NO vertical knockback when airborne**
- Can't juggle players - first hit launches, subsequent hits only push horizontally

**Bedrock Edition (Modern):**
- Still uses 1.8 behavior (airborne gets full KB)
- This is why Bedrock combat feels closer to 1.8

### Our System

**Current Implementation:**
- We apply vertical knockback regardless of ground state
- **This matches 1.8 behavior** ✓
- But modern Minecraft players expect 1.9+ behavior

**Possible Issue:**
- 1.8 pros testing might be used to **1.8 behavior on actual 1.8 clients**
- Modern clients might handle velocity differently even if server sends same values

---

## Part 8: Complete Velocity Decay Math

### Example: 3.0 Blocks/Tick Knockback

**Grounded Entity (Normal Block):**
```
Tick 0: 3.000 (knockback applied)
Tick 1: 3.000 × 0.6 × 0.91 = 1.638 (45.4% loss)
Tick 2: 1.638 × 0.6 × 0.91 = 0.894 (45.4% loss)
Tick 3: 0.894 × 0.6 × 0.91 = 0.488 (45.4% loss)
Tick 4: 0.488 × 0.6 × 0.91 = 0.266 (45.4% loss)
Tick 5: 0.266 × 0.6 × 0.91 = 0.145 (45.4% loss)
```

**After 5 ticks (0.25 seconds): 3.0 → 0.145 (95.2% loss)**

**Airborne Entity:**
```
Tick 0: 3.000 (knockback applied)
Tick 1: 3.000 × 0.91 = 2.730 (9% loss)
Tick 2: 2.730 × 0.91 = 2.484 (9% loss)
Tick 3: 2.484 × 0.91 = 2.261 (9% loss)
Tick 4: 2.261 × 0.91 = 2.057 (9% loss)
Tick 5: 2.057 × 0.91 = 1.872 (9% loss)
```

**After 5 ticks (0.25 seconds): 3.0 → 1.872 (37.6% loss)**

### The Implication

**If target lands on ground during knockback:**
- Velocity instantly drops from 0.91 decay to 0.546 decay
- **Feels like hitting a wall**
- This is what pros mean by "ground is fucking with knockback"

---

## Part 9: What We Can Actually Fix

### Problem #1: Ground Friction Too Strong

**Can we change block friction?**
- NO - Block slipperiness is client-side (hardcoded in vanilla)
- NO - We can't modify how blocks apply friction

**What we CAN do:**
- Detect when victim lands on ground after knockback
- **Reapply velocity boost** to compensate for ground friction
- Essentially "refresh" knockback when ground contact detected

```java
@EventHandler
public void onPlayerMove(PlayerMoveEvent event) {
    if (recentlyKnockedBack.contains(event.getPlayer().getUniqueId())) {
        if (event.getPlayer().isOnGround()) {
            // Just landed - velocity about to die from ground friction
            // Reapply partial velocity to compensate
            Vector vel = event.getPlayer().getVelocity();
            vel.multiply(1.8);  // Counter the 0.546 decay (1.8 ≈ 1/0.546)
            event.getPlayer().setVelocity(vel);
        }
    }
}
```

### Problem #2: Momentum Threshold Different

**Can we change the threshold?**
- NO - Momentum threshold is client-side physics

**What we CAN do:**
- When velocity gets small (< 0.01), set it to 0 ourselves
- Simulate 1.8's harder stop behavior

```java
Vector vel = victim.getVelocity();
if (Math.abs(vel.getX()) < 0.01) vel.setX(0);
if (Math.abs(vel.getZ()) < 0.01) vel.setZ(0);
victim.setVelocity(vel);
```

### Problem #3: Collision Order Changed

**Can we change collision processing?**
- NO - Collision order is client-side physics engine

**What we CAN do:**
- Nothing. This is baked into the client.

### Problem #4: Slipperiness Change (1.15)

**Can we fix slab behavior?**
- NO - Slipperiness application is client-side

**Workaround:**
- Tell users to avoid slabs/stairs in PvP arenas
- Use full blocks only

### Problem #5: Blips Patched

**Can we re-add blips?**
- MAYBE - We could detect stepping and add tiny upward velocity
- But this might feel even MORE wrong

### Problem #6: Airborne Knockback

**Can we change to 1.9+ behavior?**
- YES - We control this!

```java
if (!victim.isOnGround()) {
    // 1.9+ behavior: no vertical KB for airborne
    finalVelocity.setY(Math.min(currentVelocity.getY(), 0));
}
```

**Config option:**
```yaml
apply-vertical-to-airborne: true  # true = 1.8 feel, false = 1.9+ feel
```

---

## Part 10: Recommendations

### For "True 1.8 Feel" on 1.21 Client:

1. **Compensate for ground friction**
   - Detect ground contact during knockback window
   - Multiply velocity by ~1.8x to counter 0.546 decay

2. **Simulate harder momentum threshold**
   - Set velocities < 0.01 to zero manually
   - Creates 1.8's "snappier" stop behavior

3. **Keep current settings**
   - Vertical KB: 0.4 (SET, not add) ✓
   - Apply vertical to airborne: YES (1.8 behavior) ✓
   - Friction: 2.0 ✓

4. **Test on full blocks only**
   - Avoid slabs, stairs, partial blocks
   - 1.15+ slipperiness change makes these inconsistent

### For "Modern Feel" (1.9+ Accurate):

1. **Don't compensate ground friction**
   - Let it decay naturally (modern behavior)

2. **Use 1.9+ airborne behavior**
   - No vertical KB for airborne targets
   - `apply-vertical-to-airborne: false`

3. **Increase vertical base**
   - Vertical: 0.5 (matches modern vanilla)
   - Vertical limit: 0.6

---

## Sources

### Primary Research:
- [Minecraft Parkour Wiki - Horizontal Movement Formulas](https://www.mcpk.wiki/wiki/Horizontal_Movement_Formulas)
- [Minecraft Parkour Wiki - Momentum Threshold](https://www.mcpk.wiki/wiki/Momentum_Threshold)
- [Minecraft Parkour Wiki - Version Differences](https://www.mcpk.wiki/wiki/Version_Differences)
- [TrueCraft Wiki - Entity Movement And Physics](https://github.com/ddevault/TrueCraft/wiki/Entity-Movement-And-Physics)

### Secondary Research:
- [Minecraft Wiki - Knockback Mechanics](https://minecraft.wiki/w/Knockback_(mechanic))
- [Minecraft Wiki - Player](https://minecraft.wiki/w/Player)
- [Minecraft Wiki - Hitbox](https://minecraft.wiki/w/Hitbox)
- [Hypixel Forums - Laws of Minecraft Physics](https://hypixel.net/threads/the-laws-of-minecraft-physics.5640116/)

---

**End of Document**
