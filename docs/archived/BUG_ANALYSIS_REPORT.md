# PHARAOH SET BUG ANALYSIS REPORT
**Date:** 2026-01-12  
**Server:** GravelHost Production  
**Plugin Version:** 1.0.556-WORKING

---

## 🔍 EXECUTIVE SUMMARY

After deep analysis of both local code and deployed server configuration, I've identified the root causes of all reported bugs. Most issues stem from **configuration problems** and **code logic gaps**, not fundamental architecture flaws.

**Critical Finding:** Debug is enabled on server (`config.yml:7`) but you may not be seeing the logs. This suggests either:
1. Server console not showing plugin messages
2. WorldGuard PVP protection blocking effects
3. Effects ARE working but visual feedback is missing

---

## 🐛 BUG #1: Ruler's Hand Not Proccing on Players

**Status:** ⚠️ **LIKELY WORLDGUARD OR MARK NOT APPLYING**

### Server Configuration (pharaoh-set.yml)
```yaml
# Line 813: Condition check
conditions:
  logic: AND
  list:
  - HAS_MARK:PHARAOH_MARK
```

### Root Cause Analysis
Ruler's Hand **requires** the victim to have `PHARAOH_MARK` first. This creates a dependency chain:
1. ✅ Sandstorm must proc first → apply mark to players
2. ✅ Then Ruler's Hand can proc → deal bonus damage

**Code Review:**
- ✅ `ConditionManager.checkHasMark()` (line 401) correctly checks players
- ✅ `MarkManager.applyMark()` (line 72) stores marks in concurrent map
- ✅ `MarkManager.hasMark()` (line 118) retrieves marks correctly

### Why It Might Fail
1. **Sandstorm not marking players** (see Bug #2)
2. **Mark expires before Ruler's Hand procs** (5s duration vs 8s cooldown)
3. **WorldGuard `respect-pvp-flags: true`** blocking mark application in protected regions

### Debug Steps
```bash
# On server, attack a player with Sandstorm equipped and check console for:
[MARK DEBUG] Applying mark to: <PlayerName> (PLAYER, isPlayer=true)
[HAS_MARK DEBUG] Checking PHARAOH_MARK on <PlayerName> (PLAYER, isPlayer=true): true
```

---

## 🐛 BUG #2: Sandstorm Not Proccing on Players

**Status:** ⚠️ **CODE IS CORRECT - LIKELY WORLDGUARD**

### Server Configuration (pharaoh-set.yml)
```yaml
# Line 254-256: Mark effect
effect: MARK
params:
  mark_name: PHARAOH_MARK
  duration: 5
  target: '@NearbyEntities:{radius}'

# Line 264-268: Speed reduction
effect: MODIFY_ATTRIBUTE
params:
  attribute: GENERIC_MOVEMENT_SPEED
  operation: MULTIPLY_SCALAR_1
  value: -0.25
  duration: 5
  target: '@NearbyEntities:{radius}'
```

### Code Review
- ✅ `AbstractEffect.getNearbyEntities()` (line 225) **excludes only the owner**, includes all other players
- ✅ `MarkEffect.execute()` (line 86) has debug logging for each entity affected
- ✅ `ModifyAttributeEffect.execute()` (line 92) has debug logging for each entity

### Confirmed Debug Output Should Show
```
[MARK DEBUG] Found X nearby entities within radius Y
[MARK DEBUG] Applying mark to: PlayerName (PLAYER, isPlayer=true)
[MODIFY_ATTRIBUTE DEBUG] Found X nearby entities within radius Y
[MODIFY_ATTRIBUTE DEBUG] Applying to: PlayerName (PLAYER, isPlayer=true)
```

### If Debug Shows Apply But No Effect
**WorldGuard is blocking it.** Check with `/rg info` in the combat area.

### If Debug Shows Nothing
The sigil isn't proccing at all. Check:
1. Cooldown not expired (8s)
2. Player is wearing the chestplate with Sandstorm socketed
3. Successfully hitting an entity (not sweep attack)

---

## 🐛 BUG #3: Pharaoh's Curse Cannot Hit When Stunned

**Status:** ❓ **NEED CLARIFICATION**

### Current Behavior
`StunManager` **only blocks movement** (`PlayerMoveEvent`), NOT attacks (`EntityDamageByEntityEvent`).

**Stunned players CAN:**
- Still attack
- Take damage
- Trigger DEFENSE sigils

**Stunned players CANNOT:**
- Move position
- Look around (camera locked)

### Questions
1. **Should stunned attackers be unable to deal damage?**
   - If YES → Need to cancel `EntityDamageByEntityEvent` when attacker is stunned
   
2. **Should Pharaoh's Curse not trigger if victim is already stunned?**
   - If YES → Add condition: `NOT:STUNNED` to pharaoh_curse flow

3. **Is the issue that stunned players take the curse damage but the visual stun doesn't re-apply?**
   - This could be a UX issue, not a bug

### Recommendation
Tell me which behavior you want and I'll implement it.

---

## 🐛 BUG #4: Quicksand Pull Collision Issue

**Status:** ✅ **FOUND - PULL THRESHOLD TOO SMALL**

### Server Configuration (pharaoh-set.yml)
```yaml
# Line 580-581
pull_on_attack: true  # Pulls when YOU attack
pull_strength: [0.8, 1, 1.2, 1.4, 1.6]
```

### Code Issue (AuraManager.java)
```java
// Line 260 & 312
if (distance > 0.3) { // PROBLEM: 0.3 blocks too close!
```

**Root Cause:** 
- `CollisionDisabler` IS working (players have collision off)
- Pull stops at 0.3 blocks distance
- With collision disabled, players phase into each other at ~0.5 blocks
- Result: Player gets pulled TO you, then phases inside you for 1 second before bouncing out

### Fix
```java
// Change pull threshold from 0.3 to 1.5
if (distance > 1.5) { // Don't pull if already close
```

This keeps enemies at sword's reach instead of inside your hitbox.

### Additional Note
Configuration uses `pull_on_attack: true` not `pull_on_hit: true`. This means:
- ✅ Pull triggers when YOU attack an enemy in the aura
- ❌ Pull does NOT trigger when enemies hit YOU

If you want pull on defense, change to `pull_on_hit: true` in YAML.

---

## 🐛 BUG #5: Royal Guard Duration Scaling

**Status:** ✅ **FOUND - DURATION INCREASES INSTEAD OF DECREASES**

### Server Configuration (pharaoh-set.yml)
```yaml
# Line 950-955: Current (WRONG)
duration:
- 10   # Tier 1
- 15   # Tier 2
- 20   # Tier 3
- 25   # Tier 4
- 30   # Tier 5
```

### Expected Behavior (from bug report)
> "make the duration of the zombies 10 seconds going down 2 seconds per level tier scaled"

### Fix
```yaml
duration:
- 10   # Tier 1: 10s
- 8    # Tier 2: 8s (-2s)
- 6    # Tier 3: 6s (-2s)
- 4    # Tier 4: 4s (-2s)
- 2    # Tier 5: 2s (-2s)
```

**Rationale:** Higher tier = shorter duration but zombies are stronger/faster, creating burst damage windows instead of sustained damage.

---

## 🐛 BUG #6: Royal Guard Speed/Damage Scaling

**Status:** ✅ **ALREADY SCALES UP - MAY NEED BOOST**

### Current Scaling
```yaml
# Speed (0.23 = default zombie speed)
speed: [0.32, 0.34, 0.36, 0.38, 0.4]   # 39-74% faster than zombies

# Damage (2.5 = default zombie damage)
damage: [4, 5, 6, 7, 8]                # 60-220% more damage
```

### Assessment
Speed and damage ALREADY scale up significantly. If they feel slow/weak, increase aggressively:

```yaml
speed: [0.35, 0.38, 0.42, 0.46, 0.50]  # Much faster
damage: [6, 8, 10, 12, 15]              # Much stronger
```

---

## 🐛 BUG #7: Bind Menu Targeting (Green Glow + Actionbar)

**Status:** ✅ **FOUND - MISSING FEATURES**

### Current Behavior (TargetGlowManager.java)
- ✅ White glow applied via metadata flag
- ❌ No team coloring (stays white)
- ❌ No actionbar message

### Required Changes

**1. Green Glow (needs scoreboard team):**
```java
// TargetGlowManager.java - add to sendGlowPacket()
Scoreboard scoreboard = player.getScoreboard();
Team glowTeam = scoreboard.getTeam("sigil_target");
if (glowTeam == null) {
    glowTeam = scoreboard.registerNewTeam("sigil_target");
    glowTeam.setColor(ChatColor.GREEN);
}
glowTeam.addEntity(entity);
```

**2. Actionbar (add to updateGlow() method):**
```java
// Line 119 - when newTarget != null
String targetName = newTarget instanceof Player p ? p.getName() : newTarget.getType().name();
player.sendActionBar(Component.text("Target: ", NamedTextColor.GRAY)
    .append(Component.text(targetName, NamedTextColor.GREEN)));
```

---

## 🐛 BUG #8: Royal Guard Should Only Target Ability UI Target

**Status:** ✅ **FOUND - USES NEARBY INSTEAD OF TARGET**

### Server Configuration (pharaoh-set.yml)
```yaml
# Line 1073: Current (WRONG)
target_mode: NEARBY  # Attacks any nearby enemy

# Line 1011: Missing condition
# No check if player has selected a target
```

### Fix Required

**1. Change target mode:**
```yaml
target_mode: TARGET  # Use ability UI highlighted target
```

**2. Add condition to only spawn if target exists:**
```yaml
flows:
- type: ABILITY
  cooldown: 45.0
  conditions:
    logic: AND
    list:
    - HAS_TARGET  # Only spawn if targeting someone
  id: royal_guard_ability
```

**3. Update description:**
```yaml
description:
- '&7Target a player, then activate.'
- '&7Summon mummified guards that'
- '&7attack ONLY your target!'
```

---

## 📊 PRIORITY MATRIX

| Bug | Severity | Effort | Priority | Status |
|-----|----------|--------|----------|--------|
| #4 Quicksand Collision | High | Low | **CRITICAL** | Code fix ready |
| #5 Royal Guard Duration | High | Low | **CRITICAL** | YAML fix ready |
| #8 Royal Guard Targeting | High | Low | **CRITICAL** | YAML fix ready |
| #7 Green Glow + Actionbar | Medium | Medium | **HIGH** | Code fix needed |
| #1 Ruler's Hand | Medium | Low | **MEDIUM** | Need debug logs |
| #2 Sandstorm | Medium | Low | **MEDIUM** | Need debug logs |
| #6 Royal Guard Stats | Low | Low | LOW | Optional tweak |
| #3 Pharaoh's Curse Stun | Unknown | Low | LOW | Need clarification |

---

## 🛠️ RECOMMENDED FIXES

### Immediate (Can Do Right Now)

**1. AuraManager.java - Fix quicksand collision**
```java
// Line 260 and 312
if (distance > 1.5) { // Changed from 0.3
```

**2. pharaoh-set.yml - Fix Royal Guard**
```yaml
royal_guard:
  tier:
    params:
      duration: [10, 8, 6, 4, 2]  # Decreases with tier
  
  flows:
  - type: ABILITY
    conditions:
      logic: AND
      list:
      - HAS_TARGET  # Add this
    nodes:
    - id: spawn
      effect: SPAWN_ENTITY
      params:
        target_mode: TARGET  # Changed from NEARBY
```

### Next Steps (Needs Testing)

**3. TargetGlowManager.java - Add green glow + actionbar**
- Implement scoreboard team coloring
- Add actionbar message in `updateGlow()`

**4. Debug Investigation**
- Enable debug (already on)
- Test Sandstorm/Ruler's Hand on players
- Check console for `[MARK DEBUG]` messages
- Check WorldGuard regions with `/rg info`

---

## 🧪 TESTING CHECKLIST

After fixes are deployed:

### Quicksand
- [ ] Activate quicksand near player
- [ ] Attack them while in aura
- [ ] Verify they stop ~1.5 blocks away (not phasing into you)
- [ ] Verify collision is disabled (walk through players)

### Royal Guard
- [ ] Target a player with ability UI (F + hotbar slot)
- [ ] Verify "Target: PlayerName" shows in actionbar
- [ ] Activate Royal Guard
- [ ] Verify zombies ONLY attack the targeted player
- [ ] Verify duration: Tier 1 = 10s, Tier 5 = 2s

### Sandstorm
- [ ] Attack a player with Sandstorm equipped
- [ ] Check console for: `[MARK DEBUG] Applying mark to: PlayerName`
- [ ] Verify player gets glowing effect and slowness
- [ ] If nothing happens, check WorldGuard `/rg info`

### Ruler's Hand
- [ ] Use Sandstorm first to mark a player
- [ ] Attack marked player with Ruler's Hand
- [ ] Verify explosion particles + bonus damage
- [ ] Check console for: `[HAS_MARK DEBUG] Checking PHARAOH_MARK on PlayerName: true`

---

## 📝 NOTES

### WorldGuard PVP Protection
Config shows `respect-pvp-flags: true`. If testing in spawn/protected regions:
1. Marks won't apply
2. Attribute modifications won't apply
3. This is EXPECTED behavior

**Test in PVP-enabled regions only.**

### Debug Logging
Server config shows `debug: true` already. If you're not seeing logs:
1. Check server console output level
2. Ensure Paper/Spigot isn't filtering plugin messages
3. Try `/arcane debug true` if command exists

### Pull Mechanics
Quick Sand uses `pull_on_attack` which pulls when YOU attack them, not when they hit you.
This is intentional for an offensive ability, but can be changed to `pull_on_hit` for defensive.

---

## ✅ CONCLUSION

**Bugs #4, #5, #8** are definitively confirmed and have ready fixes.  
**Bugs #1, #2** likely stem from WorldGuard or need debug log verification.  
**Bug #7** needs code implementation.  
**Bug #3** needs clarification on expected behavior.  
**Bug #6** is optional tuning.

**Ready to fix 3 critical bugs immediately. Awaiting your approval to proceed.**
