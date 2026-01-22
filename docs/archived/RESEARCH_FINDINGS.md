# 1.8 PvP Combat Research - Critical Missing Mechanics

## Summary
After analyzing the MinemenClub/ClubSpigot source code (spigotx-master), I've identified several critical mechanics missing from the current implementation that explain why combat doesn't "feel like 1.8 PvP" and why W-tapping doesn't work.

## Critical Findings

### 1. Sprint Cancellation (W-Tap Mechanic)
**Source:** EntityHuman.java:1030  
**Issue:** After applying sprint knockback, the attacker's sprint state is CANCELLED.

```java
if (i > 0) {  // i = knockback level (incremented when sprinting)
    // Apply sprint knockback...
    this.setSprinting(false);  // <-- THIS IS THE W-TAP MECHANIC
}
```

**Impact:** 
- First hit with sprint = big knockback
- Subsequent hits without re-sprinting = small knockback
- Player must W-tap (release W to cancel sprint, then sprint again) to get full knockback
- This is why "W tap dual" doesn't work - both players lose sprint after first hit

### 2. Attacker Velocity Reduction
**Source:** EntityHuman.java:1028-1029  
**Issue:** After applying sprint knockback, the attacker loses 40% of horizontal velocity.

```java
if (i > 0) {
    // Apply sprint knockback...
    this.motX *= 0.6D;
    this.motZ *= 0.6D;
    this.setSprinting(false);
}
```

**Impact:**
- Prevents infinite combos
- Attacker is slowed after sprint hit
- Creates the "knockback give-and-take" dynamic of 1.8

### 3. Sprint Knockback Application Method
**Source:** EntityHuman.java:1020-1027  
**Issue:** Sprint knockback is applied as a SEPARATE additive velocity boost, not integrated into the main KB formula.

```java
if (i > 0) {
    KnockbackProfile profile = getKnockbackProfile();
    
    // This adds DIRECTLY to entity velocity, AFTER damage is dealt
    entity.g(
        (-MathHelper.sin(yaw * PI / 180) * (float)i * profile.getExtraHorizontal()),
        profile.getExtraVertical(),
        (MathHelper.cos(yaw * PI / 180) * (float)i * profile.getExtraHorizontal())
    );
}
```

Where `entity.g(d0, d1, d2)` is:
```java
this.motX += d0;
this.motY += d1;
this.motZ += d2;
```

**Impact:**
- Sprint KB is added AFTER the main knockback from EntityLiving.a()
- Variable `i` starts at 0, increments to 1 when sprinting (line 984)
- ExtraHorizontal/ExtraVertical are MULTIPLIED by `i`, so non-sprint gets 0, sprint gets 1x

### 4. Config Values Missing
**Source:** SpigotXConfig.java:104-108, CraftKnockbackProfile.java:16-17  
**Issue:** Config only loads 4 values, but profile has 6.

**Current Config (SpigotXConfig.java):**
```java
profile.setFriction(this.getDouble(path + ".friction", 2.0D));
profile.setHorizontal(this.getDouble(path + ".horizontal", 0.35D));
profile.setVertical(this.getDouble(path + ".vertical", 0.35D));
profile.setVerticalLimit(this.getDouble(path + ".vertical-limit", 0.4D));
// MISSING: extra-horizontal and extra-vertical
```

**Profile Defaults (CraftKnockbackProfile.java):**
```java
private double extraHorizontal = 0.425D;
private double extraVertical = 0.085D;
```

**Impact:**
- extraHorizontal/extraVertical always use hardcoded defaults
- Should be loaded from config for proper customization

## Implementation Changes Required

### 1. Add Sprint Cancellation to KnockbackModule
After applying knockback, cancel attacker's sprint:
```java
if (attacker.isSprinting()) {
    attacker.setSprinting(false);
}
```

### 2. Add Attacker Velocity Reduction
After applying knockback:
```java
if (attacker.isSprinting()) {
    Vector attackerVel = attacker.getVelocity();
    attackerVel.setX(attackerVel.getX() * 0.6);
    attackerVel.setZ(attackerVel.getZ() * 0.6);
    attacker.setVelocity(attackerVel);
}
```

### 3. Fix Sprint Knockback Application
Current implementation applies sprint bonus IN the calculator (wrong).  
Should apply sprint bonus AFTER main KB, OUTSIDE calculator.

**Current (WRONG):**
```java
// In KnockbackCalculator
if (attacker.isSprinting()) {
    currentVelocity.setX(currentVelocity.getX() + direction.getX() * extraHorizontal);
    ...
}
```

**Should be (CORRECT):**
```java
// In KnockbackModule, AFTER applying base KB
Vector finalVelocity = calculator.calculateWithRollback(...);  // Base KB only
victim.setVelocity(finalVelocity);

// THEN apply sprint bonus separately
if (attacker.isSprinting()) {
    Vector direction = victim.getLocation().toVector()
        .subtract(attacker.getLocation().toVector()).setY(0).normalize();
    
    Vector sprintBonus = new Vector(
        direction.getX() * extraHorizontal,
        extraVertical,
        direction.getZ() * extraHorizontal
    );
    
    victim.setVelocity(victim.getVelocity().add(sprintBonus));
}
```

### 4. Add Config Loading for Extra Values
In LegacyCombatConfig.java:
```java
kbExtraHorizontal = section.getDouble("kb-extra-horizontal", 0.425);
kbExtraVertical = section.getDouble("kb-extra-vertical", 0.085);
```

## Why This Fixes the Issues

### "W-tap dual" Problem
**Before:** Both players keep sprinting, apply full KB every hit  
**After:** First hit cancels both sprints, subsequent hits are weaker until they re-sprint

### "Doesn't feel like 1.8 PvP" Problem
**Before:** Missing sprint cancellation and attacker slowdown  
**After:** Proper give-and-take dynamic where attacking costs velocity

### Hit Sounds (Still Unsolved)
This is a client-side prediction issue. The client plays sounds BEFORE the server can cancel them.  
**Possible solution:** Detect immunity BEFORE damage event using cooldown timers or custom tracking.

## Next Steps
1. Implement sprint cancellation
2. Implement attacker velocity reduction
3. Fix sprint knockback to apply AFTER base KB
4. Test thoroughly on server
5. Consider hit sound pre-detection system
