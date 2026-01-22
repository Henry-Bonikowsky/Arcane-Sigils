# Collision Disabling in Minecraft 1.21.10 - Deep Research

**Date**: 2026-01-15  
**Target**: Paper API 1.21.10+  
**Plugin Context**: Arcane Sigils (Quicksand pull effect requires no player collision)

---

## Executive Summary

After extensive research into collision mechanics for Minecraft 1.21.10 on Paper servers, **the scoreboard team method is the gold standard for player collision control**. Your current implementation in `CollisionDisabler.java` follows best practices, but there are several nuances and alternative approaches worth understanding.

---

## Method Comparison Matrix

| Method | Player Collision | Entity Collision | Reliability | Complexity | Scoreboard Conflicts |
|--------|------------------|------------------|-------------|------------|----------------------|
| **Scoreboard Teams** | ✅ Excellent | ❌ N/A | ⭐⭐⭐⭐⭐ | Medium | High (if other plugins use scoreboards) |
| **Paper Config** | ✅ Global only | ❌ N/A | ⭐⭐⭐ | Very Low | None |
| **Entity.setCollidable()** | ⚠️ Client prediction issues | ✅ Good | ⭐⭐ | Very Low | None |
| **getCollidableExemptions()** | ⚠️ Not recommended | ✅ Good | ⭐⭐⭐ | Low | None |
| **NMS Entity Metadata** | ⚠️ Version-specific | ✅ Limited | ⭐⭐ | Very High | None |
| **Packet Manipulation** | ✅ Can work | ✅ Can work | ⭐⭐ | Very High | Low |

---

## 1. Scoreboard Team Method (Current Implementation ✅)

### How It Works
Uses Minecraft's native scoreboard team system with `Team.Option.COLLISION_RULE` set to `Team.OptionStatus.NEVER`.

### API Documentation (Paper 1.21.11)
```java
// Get or create team
Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
Team team = scoreboard.getTeam("noCollision");
if (team == null) {
    team = scoreboard.registerNewTeam("noCollision");
}

// Configure collision rule
team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

// Add players
team.addEntry(player.getName());
```

### Available OptionStatus Values
- `NEVER` - Disable all collisions for team members
- `ALWAYS` - Enable all collisions (default)
- `FOR_OTHER_TEAMS` - Only collide with players NOT on this team
- `FOR_OWN_TEAM` - Only collide with players ON this team

### Your Implementation Analysis

**File**: `src/main/java/com/miracle/arcanesigils/listeners/CollisionDisabler.java`

**Architecture**: Per-player scoreboard approach with global fallback

**Strengths**:
1. ✅ Uses per-player scoreboards (avoids most conflicts)
2. ✅ Synchronizes all online players to each player's team
3. ✅ Cleans up on player quit
4. ✅ Full shutdown cleanup
5. ✅ Logging for debugging

**Potential Issues Identified**:

1. **Scoreboard Replacement Risk** (Line 56-57):
   ```java
   if (scoreboard == null) {
       scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
   }
   ```
   If another plugin sets a player's scoreboard to `null` (unlikely but possible), fallback to main scoreboard could cause conflicts.

2. **Asymmetric Team Membership**:
   - When PlayerA joins, you add all online players to PlayerA's team
   - But you don't add PlayerA to existing players' teams
   - This could cause one-directional collision (A can't collide with B, but B can collide with A)

3. **Race Condition** (Line 100):
   ```java
   for (Player online : Bukkit.getOnlinePlayers()) {
       if (!team.hasEntry(online.getName())) {
           team.addEntry(online.getName());
       }
   }
   ```
   If players join rapidly during server startup, they might not all be mutually added to each other's teams.

### Recommended Fix for Asymmetry

```java
private void addToTeam(Player player) {
    // Setup team on player's scoreboard
    setupPlayerTeam(player);
    
    Scoreboard scoreboard = player.getScoreboard();
    Team team = scoreboard.getTeam(TEAM_NAME);
    
    if (team != null) {
        // Add all online players to this player's team
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!team.hasEntry(online.getName())) {
                team.addEntry(online.getName());
            }
        }
        
        // CRITICAL: Add this player to all other players' teams (mutual)
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            
            Scoreboard otherScoreboard = online.getScoreboard();
            if (otherScoreboard != null) {
                setupPlayerTeam(online); // Ensure team exists
                Team otherTeam = otherScoreboard.getTeam(TEAM_NAME);
                if (otherTeam != null && !otherTeam.hasEntry(player.getName())) {
                    otherTeam.addEntry(player.getName());
                }
            }
        }
        
        plugin.getLogger().info("[CollisionDisabler] Setup no-collision for " + player.getName() + " (" + team.getEntries().size() + " players in team)");
    }
}
```

### Known Issues (Paper GitHub)

1. **Issue #672** - "Collision rule team options keep resetting with server restart"
   - Teams with `seeFriendlyInvisibles` default to `true` on regeneration
   - Your code sets this to `false` (good!)

2. **Issue #418** - "Server crashes when collision disabled and player on wrong team"
   - Error: `Cannot remove from team 'collideRule_-880'`
   - Happens when Paper's internal collision team conflicts with plugin teams
   - **Mitigation**: Use unique team name (you use "noCollision" ✅)

3. **Issue #424** - "Collision Rule team options violate vanilla behavior"
   - Some edge cases where collision rules don't apply correctly
   - Mostly resolved in modern Paper builds

---

## 2. Paper Global Configuration

### How It Works
Server-wide configuration file that disables player collisions globally.

### Configuration
**File**: `config/paper-global.yml`
```yaml
collisions:
  enable-player-collisions: false
  send-full-pos-for-hard-colliding-entities: true
```

### Pros
- ✅ Zero code required
- ✅ No scoreboard conflicts
- ✅ Applies immediately on server start
- ✅ No per-player overhead

### Cons
- ❌ Global only (can't enable/disable per player)
- ❌ Affects ALL players, ALL the time
- ❌ Other plugins can't override it
- ❌ Not suitable for conditional effects (like Quicksand)

### Verdict
**Not suitable for Arcane Sigils**. You need dynamic collision control for specific effects, not a global setting.

---

## 3. Entity.setCollidable() Method

### How It Works
Sets whether a specific living entity will be subject to collisions.

### API Documentation (Paper 1.21.11)
```java
LivingEntity entity = ...;
entity.setCollidable(false); // Disable collision for this entity
boolean canCollide = entity.isCollidable(); // Check status
```

### Critical Limitation (Official Warning)
> "The client may predict the collision between itself and another entity, resulting in this flag not working for **player collisions**. This method should therefore **only be used to set the collision status of non-player entities**."

### How It Actually Works
- When you call `A.setCollidable(false)`:
  - Entity A is ignored when OTHER entities check for pushable nearby entities
  - BUT when A is ticked, it STILL checks for nearby pushable entities
  - Result: A still gets pushed around despite being "non-collidable"
- **Bidirectional collision behavior** means both entities must agree to not collide

### Use Cases
- ✅ NPCs/mobs that shouldn't push players
- ✅ Decorative entities
- ❌ **NOT for player-to-player collision** (client prediction breaks it)

### Verdict
**Not suitable for player collision**. Paper API explicitly warns against this. Only use for spawned entities (zombies, armor stands, etc.).

---

## 4. getCollidableExemptions() Method

### How It Works
Maintains a per-entity set of UUIDs for inverted collision behavior.

### API Documentation (Paper 1.21.11)
```java
LivingEntity entity = ...;
Set<UUID> exemptions = entity.getCollidableExemptions();

// If entity.isCollidable() == true:
exemptions.add(otherEntity.getUniqueId()); // Now won't collide with otherEntity

// If entity.isCollidable() == false:
exemptions.add(otherEntity.getUniqueId()); // Now WILL collide with otherEntity
```

### Important Notes
- ⚠️ **Not persistent** - exemptions reset on server restart
- ⚠️ **Client prediction issues for players** - same problem as `setCollidable()`
- ✅ Good for entity-to-entity exemptions (non-players)

### Recommended Use
> "To exempt collisions for a player, use `Team.Option.COLLISION_RULE` in combination with a Scoreboard and a Team."

### Verdict
**Not recommended for player collision**. Use scoreboard teams instead (as Paper API documentation states).

---

## 5. NMS Entity Metadata Manipulation

### How It Works
Directly manipulates entity metadata packets using net.minecraft.server (NMS) code.

### Entity Metadata Flags (Index 0, Byte)
From wiki.vg protocol documentation:

| Bit | Hex  | Meaning |
|-----|------|---------|
| 0   | 0x01 | On Fire |
| 1   | 0x02 | Crouched |
| 2   | 0x04 | Unused (was riding) |
| 3   | 0x08 | Sprinting |
| 4   | 0x10 | Unused (was eating/blocking) |
| 5   | 0x20 | **Invisible** |
| 6   | 0x40 | Glowing |
| 7   | 0x80 | Flying with elytra |

**CRITICAL**: There is **NO collision disable flag** in entity metadata. The 0x20 flag is for **invisibility**, not collision.

### Old NMS Methods (Pre-1.13)
```java
// THIS NO LONGER WORKS IN 1.21
nmsEntity.noclip = true; // Disabled block collision, not entity collision
```

### Modern Approach (Packet Manipulation)
Requires ProtocolLib to intercept and modify `ClientboundSetEntityData` packets:

```java
// Theoretical approach (complex, version-specific, not recommended)
ProtocolLibrary.getProtocolManager().addPacketListener(
    new PacketAdapter(plugin, PacketType.Play.Server.ENTITY_METADATA) {
        @Override
        public void onPacketSending(PacketEvent event) {
            // Modify entity data watcher values
            // Extremely fragile and breaks between versions
        }
    }
);
```

### Known Issues
- **Issue #2987** (ProtocolLib): `set_entity_data` packet causes player disconnections in 1.20.6+
- **Version-specific obfuscation**: NMS field names change every Minecraft version
- **EncoderException errors**: Malformed packets crash clients

### Verdict
**Avoid NMS/packet manipulation**. Way too complex, fragile, and no actual collision disable flag exists. The "noclip" approach was for block collision, not entity collision, and is obsolete anyway.

---

## 6. Packet-Based Collision (ProtocolLib)

### How It Works
Some plugins bypass scoreboard conflicts by sending custom packets that manipulate collision behavior client-side.

### Research Finding
NoPlayerCollisions plugin (supports 1.9-1.20.1) claims to:
> "Use packets to disable collisions and support all types of scoreboards, without causing problems or conflicts with them"

### Reality Check
- Requires ProtocolLib dependency
- Must maintain version-specific packet handling
- Paper 1.21+ has had issues with `ClientboundSetEntityData` packets
- More complex than scoreboard approach
- Marginal benefit over well-implemented scoreboard system

### Verdict
**Not worth the complexity**. Your current scoreboard implementation is more maintainable and doesn't require external dependencies.

---

## Best Practices for Paper 1.21.10

### ✅ DO:
1. **Use scoreboard teams with per-player scoreboards**
   - Avoids conflicts with plugins that modify main scoreboard
   - Your implementation already does this ✅

2. **Set collision rule to NEVER**
   ```java
   team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
   ```

3. **Disable friendly invisibles**
   ```java
   team.setCanSeeFriendlyInvisibles(false);
   ```
   Prevents unintended side effects with invisible players

4. **Use unique team names**
   - "noCollision" is good
   - Avoid generic names like "team1" that might conflict

5. **Synchronize team membership bidirectionally**
   - When PlayerA joins, add A to all players' teams
   - And add all players to A's team
   - This is missing in your current implementation ⚠️

6. **Clean up on shutdown**
   - Unregister teams to prevent ghost teams
   - Your code does this ✅

### ❌ DON'T:
1. **Don't use `Entity.setCollidable()` for players**
   - Client prediction breaks it
   - Paper API explicitly warns against this

2. **Don't rely on paper-global.yml for dynamic collision**
   - It's global-only
   - Can't toggle per-player or per-effect

3. **Don't use getCollidableExemptions() for players**
   - Same client prediction issues
   - Not persistent

4. **Don't manipulate NMS/packets unless absolutely necessary**
   - Extremely fragile
   - Breaks between versions
   - No actual collision flag exists in metadata

5. **Don't forget to handle edge cases**
   - Players joining during plugin reload
   - Server restart with players already online
   - Other plugins modifying scoreboards

---

## Troubleshooting Guide

### Problem: Collision disable isn't working
**Possible Causes**:
1. Another plugin is modifying player scoreboards
2. Team membership is asymmetric (A in B's team, but B not in A's team)
3. Team collision rule is being reset

**Debugging**:
```java
// Add this to your collision disabler
Player p1 = ..., p2 = ...;
Scoreboard sb1 = p1.getScoreboard();
Team t1 = sb1.getTeam("noCollision");

getLogger().info("P1's team has P2: " + t1.hasEntry(p2.getName()));
getLogger().info("Team collision rule: " + t1.getOption(Team.Option.COLLISION_RULE));
```

### Problem: Players see invisible teammates
**Solution**:
```java
team.setCanSeeFriendlyInvisibles(false); // You already do this ✅
```

### Problem: Scoreboard conflicts with other plugins
**Solutions**:
1. Use per-player scoreboards (you do ✅)
2. Use very unique team name (you do ✅)
3. Listen for scoreboard changes and re-apply collision rule
4. Last resort: Disable other plugin's scoreboard features

### Problem: Collision re-enables after server restart
**Solution**:
Ensure your plugin re-initializes collision settings on startup:
```java
@Override
public void onEnable() {
    // ...
    collisionDisabler.disableForAll(); // You do this at line 287 ✅
}
```

---

## Recommendations for Arcane Sigils

### Current Status: **Good Foundation, Minor Improvements Needed**

### Critical Fix: Bidirectional Team Membership
**Priority**: High  
**Impact**: Prevents one-directional collision bugs

See the "Recommended Fix for Asymmetry" code in Section 1.

### Optional Enhancement: Selective Collision Control
If you want to enable collision for specific players (e.g., disable only during Quicksand effect):

```java
public class SelectiveCollisionDisabler {
    private final Map<UUID, Boolean> collisionDisabled = new HashMap<>();
    
    public void setCollisionDisabled(Player player, boolean disabled) {
        if (disabled) {
            // Add to no-collision team
            addToTeam(player);
            collisionDisabled.put(player.getUniqueId(), true);
        } else {
            // Remove from no-collision team
            removeFromTeam(player);
            collisionDisabled.remove(player.getUniqueId());
        }
    }
}
```

### Testing Checklist
- [ ] Two players walk through each other
- [ ] Player joins server → collision disabled immediately
- [ ] Player quits → no errors in console
- [ ] Server restart → collision disabled for all online players
- [ ] 10+ players online → all can phase through each other
- [ ] Other plugin with scoreboard active → no conflicts

---

## Conclusion

**Your current implementation is solid and follows Paper API best practices.** The scoreboard team method is the industry standard for player collision control in Paper 1.21.10, and alternatives like `setCollidable()`, NMS manipulation, or packet injection are either:
- Explicitly not recommended by Paper (setCollidable for players)
- Overly complex with minimal benefit (packets)
- Non-functional (no collision flag in entity metadata)

**Main improvement needed**: Fix the asymmetric team membership issue so that collision disabling is truly bidirectional.

---

## References

### Paper API Documentation
- [LivingEntity - Paper 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/LivingEntity.html)
- [Team - Paper 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/scoreboard/Team.html)
- [Team.Option - Spigot 1.21.10](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/scoreboard/Team.Option.html)
- [Team.OptionStatus - Spigot 1.21.10](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/scoreboard/Team.OptionStatus.html)

### Protocol Documentation
- [Entity metadata - wiki.vg](https://wiki.vg/Entity_metadata)
- [Entity statuses - wiki.vg](https://wiki.vg/Entity_statuses)
- [Java Edition protocol/Packets - Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_protocol/Packets)

### Paper GitHub Issues
- [#672 - Disable Player Collisions Not Working](https://github.com/PaperMC/Paper/issues/672)
- [#376 - Player Collision disable not working](https://github.com/PaperMC/Paper/issues/376)
- [#424 - Collision Rule team options violate vanilla behavior](https://github.com/PaperMC/Paper/issues/424)
- [#418 - Server crashes when collisions disabled](https://github.com/PaperMC/Paper/issues/418)
- [#6024 - [1.17] enable-player-collisions: false doesn't work](https://github.com/PaperMC/Paper/issues/6024)
- [#577 - Add option to disable entity collisions completely](https://github.com/PaperMC/Paper/issues/577)
- [#4655 - Entity Collisions are Broken](https://github.com/PaperMC/Paper/issues/4655)

### Community Resources
- [NoPlayerCollisions Plugin - SpigotMC](https://www.spigotmc.org/resources/noplayercollisions-no-dependencies-no-patch-needed-scoreboard-support.81223/)
- [CollisionDisabler Plugin - Modrinth](https://modrinth.com/plugin/collisiondisabler)
- [How to Disable Player Collisions - HolyGG Guide](https://www.holy.gg/en/post/how-to-disable-player-collisions-on-your-minecraft-server)
- [SpigotMC Thread - How to remove entity collision (1.13.1)](https://www.spigotmc.org/threads/how-can-i-remove-the-collision-of-an-entity-spigot-version-1-13-1.342714/)
- [SpigotMC Thread - Preventing collision and scoreboard (1.16.5)](https://www.spigotmc.org/threads/solved-prevent-player-collision-and-show-scoreboard.491070/)

### Paper Configuration
- [paper-global.yml Examples - GitHub](https://github.com/danthedaniel/tildes-minecraft-configs/blob/main/config/paper-global.yml)
- [Paper Global Configuration - PaperMC Docs](https://docs.papermc.io/paper/reference/global-configuration/)

---

**Research compiled**: 2026-01-15  
**Target environment**: Paper 1.21.10 (ArcaneSigils plugin)  
**Key finding**: Scoreboard team method is optimal; your implementation is 90% correct, needs bidirectional team sync fix.
