# TRUE 1.8 KNOCKBACK MECHANICS (From NMS Decompiled Code)

## EntityLiving.damageEntity() - When entity is hit:
```java
// Step 1: Apply FRICTION to victim's current velocity
this.motX /= 2.0;
this.motZ /= 2.0;
// This is the "kbFriction" - velocity is halved BEFORE knockback is added
```

## EntityHuman.attack() - When player attacks:
```java
// Step 2: Calculate knockback strength
double strength = 0.4;  // Base horizontal KB
if (attacker.isSprinting()) {
    strength = 0.4 + 0.85;  // Total 1.25 on sprint hit
}

// Step 3: Calculate direction and ADD knockback to victim velocity
double dx = victim.posX - attacker.posX;
double dz = victim.posZ - attacker.posZ;
// Normalize direction...
victim.motX += dx * strength;  // ADD to existing (post-friction) velocity
victim.motZ += dz * strength;

// Step 4: Vertical knockback (KOHI FORMULA)
if (victim.onGround()) {
    // On ground: Add KB to half of current Y, capped at 0.4
    victim.motY = Math.min(0.4, victim.motY / 2.0 + 0.4);
} else {
    // In air: DON'T change Y velocity at all
    // (This is what makes combos work - you don't get boosted up again)
}

// Step 5: Attacker velocity reduction and sprint cancel
attacker.motX *= 0.6;
attacker.motZ *= 0.6;
attacker.setSprinting(false);
```

## KEY INSIGHTS:
1. Friction is applied FIRST (velocity /= 2.0)
2. Knockback is ADDED to the friction-reduced velocity
3. Vertical KB uses Kohi formula - preserves Y when airborne
4. Attacker DOES get velocity reduced (this is 1.8 behavior)
5. Sprint KB is 0.4 base + 0.85 bonus = 1.25 total

## CURRENT CODE BUGS:
1. ❌ Never applies friction (kbFriction field unused)
2. ❌ Creates NEW velocity vector instead of modifying current one
3. ❌ Vertical KB is flat value, not Kohi formula
4. ❌ Attacker velocity reduction might interfere with movement
5. ❌ Sprint bonus calculation is overly complex
