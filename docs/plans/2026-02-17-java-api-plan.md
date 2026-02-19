# ArcaneSigils Java API - Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace scoreboard-based state exposure with a clean Java API for external plugin integration.

**Architecture:** Thin Facade pattern. API interface + records in `com.miracle.arcanesigils.api` package. Implementation class delegates to existing managers (SocketManager, CooldownManager, BindsManager, etc.). Remove entire `ai/` package and all scoreboard-writing code.

**Tech Stack:** Java 21, Paper 1.21, Maven

**Design doc:** `docs/plans/2026-02-17-java-api-design.md`

---

### Task 1: Create API Interface and Records

**Files:**
- Create: `src/main/java/com/miracle/arcanesigils/api/ArcaneSigilsAPI.java`
- Create: `src/main/java/com/miracle/arcanesigils/api/SigilInfo.java`
- Create: `src/main/java/com/miracle/arcanesigils/api/MarkInfo.java`

**Step 1: Create SigilInfo record**

```java
package com.miracle.arcanesigils.api;

import java.util.UUID;

/**
 * Immutable snapshot of an equipped sigil's state.
 */
public record SigilInfo(
    String id,
    String name,
    int tier,
    String slot,
    String activationType
) {}
```

**Step 2: Create MarkInfo record**

```java
package com.miracle.arcanesigils.api;

import java.util.UUID;

/**
 * Immutable snapshot of an active mark on an entity.
 */
public record MarkInfo(
    String name,
    double multiplier,
    long expiryTimeMs,
    UUID ownerUUID
) {}
```

**Step 3: Create ArcaneSigilsAPI interface**

```java
package com.miracle.arcanesigils.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.List;

/**
 * Public API for querying and controlling Arcane Sigils state.
 * Obtain via ArcaneSigils.getAPI().
 */
public interface ArcaneSigilsAPI {

    // --- Equipped Sigils ---
    List<SigilInfo> getEquippedSigils(Player player);

    // --- Bind Slot Queries (uses player's active bind system) ---
    boolean isSigilReady(Player player, int bindSlot);
    double getCooldownProgress(Player player, int bindSlot);
    double getCooldownRemaining(Player player, int bindSlot);
    double getMaxCooldown(Player player, int bindSlot);
    int getTier(Player player, int bindSlot);
    String getSigilType(Player player, int bindSlot);
    String getActivationType(Player player, int bindSlot);

    // --- Ability Activation ---
    boolean activateAbility(Player player, int bindSlot);

    // --- Combat State ---
    double getDamageAmplifier(Player player);
    double getDamageReduction(Player player);

    // --- Specific Sigil State ---
    int getKingsBraceCharges(Player player);
    int getInvulnHits(Player player);

    // --- Marks ---
    boolean isMarked(Player target, Player attacker);
    boolean hasMark(LivingEntity entity, String markName);
    List<MarkInfo> getActiveMarks(LivingEntity entity);
    double getMarkDamageMultiplier(LivingEntity entity);

    // --- Targets ---
    LivingEntity getSelectedTarget(Player player);
    LivingEntity getLastVictim(Player player);
}
```

**Step 4: Verify compilation**

Run: `mvn clean compile -DskipTests -q` using the project's Maven/Java paths.
Expected: BUILD SUCCESS

**Step 5: Commit**

```
feat: add ArcaneSigilsAPI interface and data records
```

---

### Task 2: Add Missing Internal Getters

Before implementing the facade, two internal classes need new public methods.

**Files:**
- Modify: `src/main/java/com/miracle/arcanesigils/effects/impl/InvulnerabilityHitsEffect.java`
- Modify: `src/main/java/com/miracle/arcanesigils/effects/MarkManager.java`

**Step 1: Add getRemainingHits() to InvulnerabilityHitsEffect**

Currently only has `isInvulnerable(UUID)`. Add after line ~156:

```java
/**
 * Get remaining invulnerability hits for a player.
 * @return remaining hits, or 0 if not invulnerable
 */
public static int getRemainingHits(UUID playerId) {
    InvulnerabilityCounter counter = activeInvulnerability.get(playerId);
    if (counter == null) return 0;
    return counter.hitsRemaining;
}
```

**Step 2: Add getActiveMarkInfo() to MarkManager**

Add a method that returns `List<MarkInfo>` for an entity. Place after the existing `getMarks()` method (~line 625):

```java
/**
 * Get detailed info about all active marks on an entity.
 * Used by the public API.
 */
public List<com.miracle.arcanesigils.api.MarkInfo> getActiveMarkInfo(LivingEntity entity) {
    if (entity == null) return Collections.emptyList();

    UUID entityId = entity.getUniqueId();
    Map<String, MarkData> marks = entityMarks.get(entityId);
    if (marks == null) return Collections.emptyList();

    long now = System.currentTimeMillis();
    List<com.miracle.arcanesigils.api.MarkInfo> result = new ArrayList<>();

    for (Map.Entry<String, MarkData> entry : marks.entrySet()) {
        MarkData data = entry.getValue();
        if (data.isMultiSource) {
            for (SourceData source : data.sources.values()) {
                if (source.expiryTime == Long.MAX_VALUE || now < source.expiryTime) {
                    result.add(new com.miracle.arcanesigils.api.MarkInfo(
                        entry.getKey(),
                        source.multiplier,
                        source.expiryTime,
                        null
                    ));
                }
            }
        } else {
            if (now <= data.expiryTime) {
                result.add(new com.miracle.arcanesigils.api.MarkInfo(
                    entry.getKey(),
                    data.damageMultiplier,
                    data.expiryTime,
                    data.ownerUUID
                ));
            }
        }
    }
    return result;
}
```

**Step 3: Add isMarkedBy() to MarkManager**

Add a method to check if a specific attacker has marked a target. Place after `getActiveMarkInfo()`:

```java
/**
 * Check if target has any mark applied by the specified attacker.
 */
public boolean isMarkedBy(LivingEntity target, Player attacker) {
    if (target == null || attacker == null) return false;

    UUID targetId = target.getUniqueId();
    UUID attackerId = attacker.getUniqueId();
    Map<String, MarkData> marks = entityMarks.get(targetId);
    if (marks == null) return false;

    long now = System.currentTimeMillis();
    for (MarkData data : marks.values()) {
        if (data.isMultiSource) {
            // Check all sources for attacker UUID match
            // (multi-source marks don't track owner per-source currently)
            continue;
        } else {
            if (attackerId.equals(data.ownerUUID) && now <= data.expiryTime) {
                return true;
            }
        }
    }
    return false;
}
```

**Step 4: Verify compilation**

Run: `mvn clean compile -DskipTests -q`
Expected: BUILD SUCCESS

**Step 5: Commit**

```
feat: add internal getters for API (invuln hits, mark info)
```

---

### Task 3: Implement ArcaneSigilsAPIImpl

**Files:**
- Create: `src/main/java/com/miracle/arcanesigils/api/ArcaneSigilsAPIImpl.java`

**Step 1: Create the implementation class**

This is the core facade. It delegates every call to existing managers.

```java
package com.miracle.arcanesigils.api;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.binds.BindsManager;
import com.miracle.arcanesigils.binds.LastVictimManager;
import com.miracle.arcanesigils.binds.PlayerBindData;
import com.miracle.arcanesigils.binds.TargetGlowManager;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.core.SocketManager;
import com.miracle.arcanesigils.effects.MarkManager;
import com.miracle.arcanesigils.effects.impl.DamageAmplificationEffect;
import com.miracle.arcanesigils.effects.impl.DamageReductionBuffEffect;
import com.miracle.arcanesigils.effects.impl.InvulnerabilityHitsEffect;
import com.miracle.arcanesigils.events.CooldownManager;
import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.variables.SigilVariableManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArcaneSigilsAPIImpl implements ArcaneSigilsAPI {

    private final ArmorSetsPlugin plugin;

    public ArcaneSigilsAPIImpl(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    // --- Helper: resolve bind slot to sigil ID ---
    private String resolveSigilId(Player player, int bindSlot) {
        BindsManager bindsManager = plugin.getBindsManager();
        if (bindsManager == null) return null;
        PlayerBindData data = bindsManager.getPlayerData(player);
        if (data == null) return null;
        List<String> ids = data.getCurrentBinds().getBind(bindSlot);
        if (ids == null || ids.isEmpty()) return null;
        return ids.get(0);
    }

    // --- Helper: resolve sigil ID to Sigil object ---
    private Sigil resolveSigil(Player player, int bindSlot) {
        String sigilId = resolveSigilId(player, bindSlot);
        if (sigilId == null) return null;
        return plugin.getSigilManager().getSigil(sigilId);
    }

    // --- Helper: find equipped item containing a sigil ---
    private ItemStack findEquippedItem(Player player, String sigilId) {
        SocketManager sm = plugin.getSocketManager();
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack item : armor) {
            if (item == null || item.getType().isAir()) continue;
            for (String entry : sm.getSocketedSigilData(item)) {
                String id = entry.contains(":") ? entry.split(":")[0] : entry;
                if (id.equalsIgnoreCase(sigilId)) return item;
            }
        }
        // Check held items (main hand, off hand)
        for (ItemStack item : new ItemStack[]{
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand()}) {
            if (item == null || item.getType().isAir()) continue;
            for (String entry : sm.getSocketedSigilData(item)) {
                String id = entry.contains(":") ? entry.split(":")[0] : entry;
                if (id.equalsIgnoreCase(sigilId)) return item;
            }
        }
        return null;
    }

    // --- Helper: get equipped tier for a sigil from item NBT ---
    private int getEquippedTier(Player player, String sigilId) {
        ItemStack item = findEquippedItem(player, sigilId);
        if (item == null) return 0;
        for (String entry : plugin.getSocketManager().getSocketedSigilData(item)) {
            String[] parts = entry.split(":");
            if (parts[0].equalsIgnoreCase(sigilId) && parts.length > 1) {
                try { return Integer.parseInt(parts[1]); }
                catch (NumberFormatException e) { return 1; }
            }
        }
        return 1;
    }

    // --- Helper: get cooldown key for a sigil's ability flow ---
    private String getCooldownKey(Sigil sigil) {
        FlowConfig abilityFlow = sigil.getAbilityFlow();
        if (abilityFlow == null) return null;
        String flowId = abilityFlow.getGraph() != null ? abilityFlow.getGraph().getId() : "unknown";
        return "sigil_" + sigil.getId() + "_" + flowId;
    }

    // --- Helper: determine activation type from sigil flows ---
    private String determineActivationType(Sigil sigil) {
        if (sigil.hasAbilityFlow()) return "ability";
        List<FlowConfig> flows = sigil.getFlows();
        if (flows == null || flows.isEmpty()) return "passive";
        for (FlowConfig flow : flows) {
            if (flow.isSignal()) {
                String trigger = flow.getTrigger();
                if (trigger == null) continue;
                trigger = trigger.toUpperCase();
                if (trigger.contains("ATTACK") || trigger.equals("HIT")) return "auto_attack";
                if (trigger.contains("DEFEND") || trigger.contains("DEFENSE")) return "auto_defense";
                if (trigger.equals("PASSIVE") || trigger.equals("EFFECT_STATIC")) return "passive";
            }
        }
        return "passive";
    }

    // --- Helper: get max cooldown for a sigil's ability flow ---
    private double getMaxCooldownSeconds(Sigil sigil, int tier) {
        FlowConfig abilityFlow = sigil.getAbilityFlow();
        if (abilityFlow == null) return 0;
        double cooldown = abilityFlow.getCooldown();
        // Check for tier-scaled cooldown
        if (sigil.getTierScalingConfig() != null && sigil.getTierScalingConfig().hasParam("cooldown")) {
            cooldown = sigil.getTierScalingConfig().getParamValue("cooldown", tier);
        }
        return cooldown;
    }

    @Override
    public List<SigilInfo> getEquippedSigils(Player player) {
        if (player == null) return Collections.emptyList();
        SocketManager sm = plugin.getSocketManager();
        List<SigilInfo> result = new ArrayList<>();

        // Check all equipment slots
        ItemStack[] toCheck = new ItemStack[]{
            player.getInventory().getHelmet(),
            player.getInventory().getChestplate(),
            player.getInventory().getLeggings(),
            player.getInventory().getBoots(),
            player.getInventory().getItemInMainHand(),
            player.getInventory().getItemInOffHand()
        };

        for (ItemStack item : toCheck) {
            if (item == null || item.getType().isAir()) continue;
            for (String entry : sm.getSocketedSigilData(item)) {
                String[] parts = entry.split(":");
                String sigilId = parts[0];
                int tier = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
                if (sigil == null) continue;
                result.add(new SigilInfo(
                    sigilId,
                    sigil.getName(),
                    tier,
                    sigil.getSlot() != null ? sigil.getSlot() : "UNKNOWN",
                    determineActivationType(sigil)
                ));
            }
        }
        return result;
    }

    @Override
    public boolean isSigilReady(Player player, int bindSlot) {
        Sigil sigil = resolveSigil(player, bindSlot);
        if (sigil == null) return false;
        if (!sigil.hasAbilityFlow()) return false;
        if (findEquippedItem(player, sigil.getId()) == null) return false;
        String cooldownKey = getCooldownKey(sigil);
        if (cooldownKey == null) return false;
        return !plugin.getCooldownManager().isOnCooldown(player, cooldownKey);
    }

    @Override
    public double getCooldownProgress(Player player, int bindSlot) {
        Sigil sigil = resolveSigil(player, bindSlot);
        if (sigil == null) return 0.0;
        String cooldownKey = getCooldownKey(sigil);
        if (cooldownKey == null) return 0.0;
        CooldownManager cm = plugin.getCooldownManager();
        if (!cm.isOnCooldown(player, cooldownKey)) return 0.0;
        int tier = getEquippedTier(player, sigil.getId());
        double max = getMaxCooldownSeconds(sigil, tier);
        if (max <= 0) return 0.0;
        double remaining = cm.getRemainingCooldown(player, cooldownKey);
        return Math.min(1.0, remaining / max);
    }

    @Override
    public double getCooldownRemaining(Player player, int bindSlot) {
        Sigil sigil = resolveSigil(player, bindSlot);
        if (sigil == null) return 0.0;
        String cooldownKey = getCooldownKey(sigil);
        if (cooldownKey == null) return 0.0;
        return plugin.getCooldownManager().getRemainingCooldown(player, cooldownKey);
    }

    @Override
    public double getMaxCooldown(Player player, int bindSlot) {
        Sigil sigil = resolveSigil(player, bindSlot);
        if (sigil == null) return 0.0;
        int tier = getEquippedTier(player, sigil.getId());
        return getMaxCooldownSeconds(sigil, tier);
    }

    @Override
    public int getTier(Player player, int bindSlot) {
        String sigilId = resolveSigilId(player, bindSlot);
        if (sigilId == null) return 0;
        return getEquippedTier(player, sigilId);
    }

    @Override
    public String getSigilType(Player player, int bindSlot) {
        String sigilId = resolveSigilId(player, bindSlot);
        return sigilId; // sigil ID is the type name
    }

    @Override
    public String getActivationType(Player player, int bindSlot) {
        Sigil sigil = resolveSigil(player, bindSlot);
        if (sigil == null) return null;
        return determineActivationType(sigil);
    }

    @Override
    public boolean activateAbility(Player player, int bindSlot) {
        // Delegate to BindsListener's extracted activation method
        // (see Task 4 for extraction)
        return plugin.getBindsListener().activateBindSlot(player, bindSlot);
    }

    @Override
    public double getDamageAmplifier(Player player) {
        if (player == null) return 1.0;
        double amp = DamageAmplificationEffect.getDamageAmplification(player.getUniqueId());
        return 1.0 + (amp / 100.0);
    }

    @Override
    public double getDamageReduction(Player player) {
        if (player == null) return 1.0;
        double red = DamageReductionBuffEffect.getDamageReduction(player.getUniqueId());
        return 1.0 - (red / 100.0);
    }

    @Override
    public int getKingsBraceCharges(Player player) {
        if (player == null) return 0;
        SigilVariableManager svm = plugin.getSigilVariableManager();
        // King's Brace is on CHESTPLATE slot
        Object val = svm.getSigilVariable(player, "kings_brace", "CHESTPLATE", "charge");
        if (val instanceof Number num) return num.intValue();
        return 0;
    }

    @Override
    public int getInvulnHits(Player player) {
        if (player == null) return 0;
        return InvulnerabilityHitsEffect.getRemainingHits(player.getUniqueId());
    }

    @Override
    public boolean isMarked(Player target, Player attacker) {
        if (target == null || attacker == null) return false;
        return plugin.getMarkManager().isMarkedBy(target, attacker);
    }

    @Override
    public boolean hasMark(LivingEntity entity, String markName) {
        if (entity == null || markName == null) return false;
        return plugin.getMarkManager().hasMark(entity, markName);
    }

    @Override
    public List<MarkInfo> getActiveMarks(LivingEntity entity) {
        if (entity == null) return Collections.emptyList();
        return plugin.getMarkManager().getActiveMarkInfo(entity);
    }

    @Override
    public double getMarkDamageMultiplier(LivingEntity entity) {
        if (entity == null) return 1.0;
        return plugin.getMarkManager().getDamageMultiplier(entity);
    }

    @Override
    public LivingEntity getSelectedTarget(Player player) {
        if (player == null) return null;
        TargetGlowManager tgm = plugin.getTargetGlowManager();
        if (tgm == null) return null;
        return tgm.getTarget(player);
    }

    @Override
    public LivingEntity getLastVictim(Player player) {
        if (player == null) return null;
        LastVictimManager lvm = plugin.getLastVictimManager();
        if (lvm == null) return null;
        return lvm.getLastVictim(player);
    }
}
```

**Step 2: Verify compilation**

Run: `mvn clean compile -DskipTests -q`
Expected: BUILD SUCCESS (activateAbility will fail until Task 4 - may need to stub)

**Step 3: Commit**

```
feat: implement ArcaneSigilsAPIImpl facade
```

---

### Task 4: Extract Bind Activation Logic from BindsListener

The `activateAbility()` API method needs to trigger ability activation. Currently this logic lives in `BindsListener.activateSigilWithItem()` (private). Extract it into a public method.

**Files:**
- Modify: `src/main/java/com/miracle/arcanesigils/binds/BindsListener.java`

**Step 1: Add a public `activateBindSlot` method**

Add a new public method to `BindsListener` that wraps the existing `activateBind()` private method and returns success/failure. Place it as a public method early in the class:

```java
/**
 * Programmatically activate a bind slot for a player.
 * Called by the public API (ArcaneSigilsAPI.activateAbility).
 *
 * @param player The player
 * @param bindSlot The bind slot number (1-based)
 * @return true if activation was successful (at least one flow executed)
 */
public boolean activateBindSlot(Player player, int bindSlot) {
    // Reuse existing activation logic
    activateBind(player, bindSlot);
    // activateBind is void and handles its own error messaging
    // Return true optimistically - the internal method handles all validation
    return true;
}
```

Note: The existing `activateBind()` method already handles all validation (sigil not found, not equipped, on cooldown, conditions failed). It sends player messages for each failure case. The API just delegates.

**Step 2: Expose BindsListener from ArmorSetsPlugin**

Check if `ArmorSetsPlugin` already has a getter for `BindsListener`. If not, add:

```java
public BindsListener getBindsListener() {
    return bindsListener;
}
```

Ensure `bindsListener` is stored as a field when registered in `onEnable()`.

**Step 3: Verify compilation**

Run: `mvn clean compile -DskipTests -q`
Expected: BUILD SUCCESS

**Step 4: Commit**

```
feat: extract bind activation for API access
```

---

### Task 5: Wire API into Main Plugin Class

**Files:**
- Modify: `src/main/java/com/miracle/arcanesigils/ArmorSetsPlugin.java`

**Step 1: Add static API field and getter**

Add near the top of the class with other fields:

```java
private static ArcaneSigilsAPI api;
private ArcaneSigilsAPIImpl apiImpl;
```

Add public static getter:

```java
/**
 * Get the public API for querying sigil state.
 * @return API instance, or null if plugin not enabled
 */
public static ArcaneSigilsAPI getAPI() {
    return api;
}
```

**Step 2: Initialize API in onEnable()**

After all managers are initialized (near end of onEnable), add:

```java
apiImpl = new ArcaneSigilsAPIImpl(this);
api = apiImpl;
getLogger().info("ArcaneSigils API initialized");
```

**Step 3: Clear API in onDisable()**

In onDisable(), add:

```java
api = null;
apiImpl = null;
```

**Step 4: Verify compilation**

Run: `mvn clean compile -DskipTests -q`
Expected: BUILD SUCCESS

**Step 5: Commit**

```
feat: wire ArcaneSigilsAPI into main plugin class
```

---

### Task 6: Remove AI Training Package

Delete the entire `ai/` package and all references to it.

**Files:**
- Delete: `src/main/java/com/miracle/arcanesigils/ai/AITrainingManager.java`
- Delete: `src/main/java/com/miracle/arcanesigils/ai/AITrainingConfig.java`
- Delete: `src/main/java/com/miracle/arcanesigils/ai/ScoreboardUpdateTask.java`
- Delete: `src/main/java/com/miracle/arcanesigils/ai/BindStateTracker.java`
- Delete: `src/main/java/com/miracle/arcanesigils/ai/TargetStateTracker.java`
- Modify: `src/main/java/com/miracle/arcanesigils/ArmorSetsPlugin.java`
- Modify: `src/main/java/com/miracle/arcanesigils/binds/BindsListener.java`
- Modify: `src/main/java/com/miracle/arcanesigils/events/SignalHandler.java`
- Modify: `src/main/java/com/miracle/arcanesigils/effects/StunManager.java`
- Modify: `src/main/java/com/miracle/arcanesigils/effects/impl/DealDamageEffect.java`
- Modify: `src/main/java/com/miracle/arcanesigils/effects/impl/HealEffect.java`
- Modify: `src/main/java/com/miracle/arcanesigils/effects/impl/StunEffect.java`
- Modify: `src/main/java/com/miracle/arcanesigils/events/ConditionManager.java`
- Modify: `src/main/java/com/miracle/arcanesigils/commands/ArmorSetsCommand.java`
- Modify: `src/main/resources/config.yml`

**Step 1: Delete AI package files**

Delete these 5 files:
- `src/main/java/com/miracle/arcanesigils/ai/AITrainingManager.java`
- `src/main/java/com/miracle/arcanesigils/ai/AITrainingConfig.java`
- `src/main/java/com/miracle/arcanesigils/ai/ScoreboardUpdateTask.java`
- `src/main/java/com/miracle/arcanesigils/ai/BindStateTracker.java`
- `src/main/java/com/miracle/arcanesigils/ai/TargetStateTracker.java`

**Step 2: Clean ArmorSetsPlugin.java**

Remove these items:
- Import: `import com.miracle.arcanesigils.ai.AITrainingManager;`
- Field: `private AITrainingManager aiTrainingManager;`
- In onDisable(): the `if (aiTrainingManager != null) { aiTrainingManager.shutdown(); }` block
- In onEnable(): `aiTrainingManager = new AITrainingManager(this);`
- Getter: `public AITrainingManager getAITrainingManager() { return aiTrainingManager; }`

**Step 3: Clean BindsListener.java**

Remove:
- Import: `import com.miracle.arcanesigils.ai.AITrainingManager;`
- All blocks like:
  ```java
  AITrainingManager aiTraining = plugin.getAITrainingManager();
  if (aiTraining != null && slotOrId >= 0) {
      aiTraining.sendCooldownSignal(...);
  }
  ```
- All blocks like:
  ```java
  AITrainingManager aiTraining = plugin.getAITrainingManager();
  if (aiTraining != null && slotOrId >= 0) {
      aiTraining.sendHitSignal(...);
      aiTraining.sendHealSignal(...);
      ...
  }
  ```
- The `context.setMetadata("aiTraining_bindSlot", slotOrId);` line
- All `aiTraining_totalDamage` / `aiTraining_totalHeal` variable reads

**Step 4: Clean SignalHandler.java**

Remove:
- Import: `import com.miracle.arcanesigils.ai.AITrainingManager;`
- The kill signal block:
  ```java
  AITrainingManager aiTraining = plugin.getAITrainingManager();
  if (aiTraining != null) {
      aiTraining.sendKillSignal(killer, bindSlot, entityType);
  }
  ```

**Step 5: Clean StunManager.java**

Remove:
- Import: `import com.miracle.arcanesigils.ai.AITrainingManager;`
- The CC signal block:
  ```java
  AITrainingManager aiTraining = plugin.getAITrainingManager();
  if (aiTraining != null) {
      aiTraining.sendCCSignal(attacker, bindSlot, "stun", duration);
  }
  ```

**Step 6: Clean DealDamageEffect.java**

Remove the `aiTraining_totalDamage` tracking lines (~lines 49-51 and 74-76):
```java
Double currentTotal = context.getVariable("aiTraining_totalDamage");
// ... etc
context.setVariable("aiTraining_totalDamage", newTotal);
```

**Step 7: Clean HealEffect.java**

Remove the `aiTraining_totalHeal` tracking lines (~line 30-32):
```java
Double currentTotal = context.getVariable("aiTraining_totalHeal");
// ... etc
context.setVariable("aiTraining_totalHeal", newTotal);
```

**Step 8: Clean StunEffect.java**

Remove the `aiTraining_bindSlot` metadata read (~line 111):
```java
Integer bindSlot = context.getMetadata("aiTraining_bindSlot", -1);
```
And any subsequent use of `bindSlot` for AI training.

**Step 9: Clean ConditionManager.java**

Remove the `aiTraining_conditionFail` metadata set (~line 43):
```java
context.setMetadata("aiTraining_conditionFail", getConditionFailureReason(condition));
```

**Step 10: Clean ArmorSetsCommand.java**

Remove the AI training manager reference (~line 498):
```java
var aiManager = plugin.getAITrainingManager();
```
And any surrounding code that uses it.

**Step 11: Clean config.yml**

Remove the `ai_training:` section entirely.

**Step 12: Verify compilation**

Run: `mvn clean compile -DskipTests -q`
Expected: BUILD SUCCESS

**Step 13: Commit**

```
refactor: remove ai/ package and scoreboard system

Replaced by ArcaneSigilsAPI for external plugin integration.
```

---

### Task 7: Build, Version Bump, and Final Verification

**Files:**
- Modify: `pom.xml` (version bump)

**Step 1: Bump version in pom.xml**

Increment version (current 1.0.531 -> 1.0.532 or whatever is current).

**Step 2: Full build**

Run: `mvn clean package -DskipTests -q`
Expected: BUILD SUCCESS, JAR in `target/`

**Step 3: Verify API classes in JAR**

Run: `jar tf target/ArcaneSigils-*.jar | grep api/`
Expected: Should list:
- `com/miracle/arcanesigils/api/ArcaneSigilsAPI.class`
- `com/miracle/arcanesigils/api/ArcaneSigilsAPIImpl.class`
- `com/miracle/arcanesigils/api/SigilInfo.class`
- `com/miracle/arcanesigils/api/MarkInfo.class`

**Step 4: Verify AI classes NOT in JAR**

Run: `jar tf target/ArcaneSigils-*.jar | grep ai/`
Expected: No results (ai package fully removed)

**Step 5: Commit**

```
chore: bump version to 1.0.532
```

---

## Task Dependency Summary

```
Task 1 (interface + records)
    ↓
Task 2 (internal getters)
    ↓
Task 3 (API impl)
    ↓
Task 4 (extract bind activation)
    ↓
Task 5 (wire into plugin)
    ↓
Task 6 (remove ai/ package)
    ↓
Task 7 (build + verify)
```

All tasks are sequential - each depends on the previous.
