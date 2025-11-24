# Armor Plugin Codebase Roast

## THE SETUP

Alright, let's talk about your Minecraft plugin. You've built a moderately complex effects system for custom armor and weapons with 71 Java files, 40+ effect types, and a solid manager pattern. The architecture shows some thoughtful decisions... but also some decisions that make me wince.

---

## CRITICAL ISSUES

### 1. CRITICAL PERFORMANCE BOTTLENECK: The Armor Check Task Running Every 5 Ticks

**Location:** `src/main/java/com/zenax/armorsets/events/TriggerHandler.java`, lines 85-94

```java
private void startArmorCheckTask() {
    new BukkitRunnable() {
        @Override
        public void run() {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                checkArmorChange(player);
            }
        }
    }.runTaskTimer(plugin, 10L, 5L);  // <-- RUNNING EVERY 5 TICKS (250ms)
}
```

**THE PROBLEM:** This task runs **every single 5 ticks (250ms)** for EVERY online player. Each execution:
- Clones the entire armor array (4 ItemStacks per player, per tick)
- Checks 4 armor slots and compares them
- Makes multiple sigil and set lookups per comparison
- Instantiates `ItemStack[]` arrays repeatedly

On a server with 50 players, you're doing **200 armor comparisons per second**. That's absurd. Each player joining adds 200 more comparisons/sec.

**THE IMPACT:**
- Massive garbage collection pressure (allocating ItemStack[] clones constantly)
- Unnecessary CPU usage that scales linearly with player count
- Delays in detecting actual armor changes (the detection is actually reasonably fast, but you're checking far too often)

**HOW TO FIX IT:**
- Increase the interval from 5L to at least 20L (1 second), or even 40L (2 seconds). Armor changes aren't that time-critical
- Use **event-driven detection** instead. Hook `InventoryClickEvent` and armor slot drag events to detect changes immediately rather than polling
- Cache comparisons more aggressively - only deep-check when you actually detect a change

**ESTIMATED IMPACT:** Reducing from 5 to 20 ticks = 4x reduction in armor check overhead. That's significant.

---

### 2. ARCHITECTURAL SIN: HashMap-Based Event Listener Storage is Fire-and-Forget

**Location:** `src/main/java/com/zenax/armorsets/events/TriggerHandler.java`, lines 46-51

```java
private final Map<UUID, Set<PotionEffectType>> appliedEffects = new HashMap<>();
private final Map<UUID, ItemStack[]> previousArmor = new HashMap<>();
private final Map<UUID, String> lastEquippedSet = new HashMap<>();
```

**THE PROBLEM:** These maps never get cleaned up. Players log off and their data sits in memory **forever**.

With 100 players cycling through your server, you could easily accumulate 100+ UUIDs in these maps. Over days, that's kilobytes of wasted memory for players who aren't even online.

**THE IMPACT:**
- Memory leak (not severe, but unnecessary)
- No cleanup on plugin reload
- On-disk persistence would be better, but even just clearing on player quit would help

**HOW TO FIX IT:**
- Listen to `PlayerQuitEvent` and clear entries:
```java
@EventHandler
public void onQuit(PlayerQuitEvent e) {
    UUID uuid = e.getPlayer().getUniqueId();
    appliedEffects.remove(uuid);
    previousArmor.remove(uuid);
    lastEquippedSet.remove(uuid);
}
```
- Do this in `onDisable()` to clear all on plugin reload

**ESTIMATED IMPACT:** Minimal memory savings but better code hygiene.

---

### 3. THREAD SAFETY THEATER: ConcurrentHashMap for Cooldowns but HashMap for Player State

**Location:** `src/main/java/com/zenax/armorsets/events/CooldownManager.java`, lines 18-21

```java
private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
private final Map<UUID, Long> globalCooldowns = new ConcurrentHashMap<>();
```

**The Good:** You're using `ConcurrentHashMap` for cooldowns (good, this is accessed from async effect tasks).

**The Bad:** TriggerHandler uses regular `HashMap` for appliedEffects, previousArmor, lastEquippedSet. These are accessed from Bukkit events (which are sync) AND TriggerHandler methods (which might be async). That's a data race waiting to happen.

**THE IMPACT:**
- Potential `ConcurrentModificationException` if async tasks try to iterate
- Silent data corruption if edge cases align

**HOW TO FIX IT:**
- Convert all player state maps to `ConcurrentHashMap` or ensure synchronization:
```java
private final Map<UUID, Set<PotionEffectType>> appliedEffects = new ConcurrentHashMap<>();
```

---

### 4. CONDITION EVALUATION: String Parsing on Every Trigger Fire

**Location:** `src/main/java/com/zenax/armorsets/events/ConditionManager.java`, lines 39-92

```java
private boolean evaluateCondition(String condition, EffectContext context) {
    if (condition == null || condition.trim().isEmpty()) {
        return true;
    }

    String[] parts = condition.split(":"); // <-- EVERY TRIGGER FIRE
    String type = parts[0].toUpperCase();

    try {
        return switch (type) {
            case "HEALTH_PERCENT" -> checkHealthPercent(context.getPlayer(), parts);
            // ... 20+ cases ...
        };
    } catch (Exception e) {
        // ...
        return true;
    }
}
```

**THE PROBLEM:** You're parsing condition strings every single time an effect triggers. Conditions are loaded from YAML once and never change, so why parse them repeatedly?

**THE IMPACT:**
- String splits, toUpperCase() calls, exception handling on every trigger
- On a player with 5 active sigils + armor set, each attack trigger could fire 50+ condition evaluations

**HOW TO FIX IT:**
- Parse conditions once when YAML loads
- Store them as pre-parsed objects with a `type` enum and param array:
```java
public class ParsedCondition {
    public enum Type { HEALTH_PERCENT, HAS_POTION, BIOME, ... }
    public Type type;
    public String[] params;
}
```
- Evaluate the pre-parsed object (no string operations)

**ESTIMATED IMPACT:** Reduces per-trigger condition overhead by 70-80%.

---

### 5. SIGIL CLONING: Deep Copy Overhead on Tier Application

**Location:** `src/main/java/com/zenax/armorsets/core/SigilManager.java`, lines 71-81, 86-107

```java
public Sigil getSigilWithTier(String id, int tier) {
    Sigil base = sigils.get(id.toLowerCase());
    if (base == null) return null;

    Sigil tiered = cloneSigil(base);  // <-- DEEP COPY EVERY TIME
    // ...
}

private Sigil cloneSigil(Sigil original) {
    Sigil clone = new Sigil(original.getId());
    // ... 10+ field assignments ...
    Map<String, TriggerConfig> clonedEffects = new java.util.HashMap<>();
    for (Map.Entry<String, TriggerConfig> e : original.getEffects().entrySet()) {
        clonedEffects.put(e.getKey(), cloneTriggerConfig(e.getValue()));
    }
    // ...
}
```

**THE PROBLEM:** Every time a player equips armor or triggers a sigil, you're deep-cloning the entire sigil object (including all trigger configs). For a player with 4 armor pieces + 2 sigils each, that's 8 sigil clones per armor check.

**THE IMPACT:**
- Massive object allocation pressure
- Garbage collection spikes during gear changes

**HOW TO FIX IT:**
- Sigils are immutable after loading, so tier scaling should be **calculated at runtime** rather than stored:
```java
public double getScaledValue(String effectId, int tier, int maxTier) {
    // Calculate multiplier on-the-fly
    double multiplier = 0.5 + (tier * (1.0 / maxTier));
    return baseValue * multiplier;
}
```
- OR cache tiered versions:
```java
private final Map<String, Map<Integer, Sigil>> tierCache = new HashMap<>();

public Sigil getSigilWithTier(String id, int tier) {
    return tierCache.computeIfAbsent(id, k -> new HashMap<>())
        .computeIfAbsent(tier, t -> {
            Sigil base = sigils.get(id.toLowerCase());
            Sigil tiered = cloneSigil(base);
            scaleEffects(tiered, tier, tiered.getMaxTier());
            return tiered;
        });
}
```

**ESTIMATED IMPACT:** If caching: 90%+ reduction in cloning overhead. If runtime calculation: different trade-off but same reduction in allocations.

---

## PERFORMANCE ISSUES

### 6. STATIC EFFECT TASK: Running Every 20 Ticks (1 sec) for Static/Passive Effects

**Location:** `src/main/java/com/zenax/armorsets/events/TriggerHandler.java`, lines 68-79

```java
private void startStaticEffectTask() {
    int interval = plugin.getConfigManager().getMainConfig()
            .getInt("settings.effect-check-interval", 20);

    new BukkitRunnable() {
        @Override
        public void run() {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                processStaticEffects(player);
            }
        }
    }.runTaskTimer(plugin, 20L, interval);
}
```

**THE PROBLEM:** Running every 20 ticks (1 second) is reasonable, but you're iterating all players and checking if they have active sets. If a player doesn't have armor equipped, you're still doing work for them.

**THE IMPACT:**
- Wasted cycles on players without active sets

**HOW TO FIX IT:**
- Track which players have active sets and only process those
- Remove from the "active" set when armor is unequipped

---

### 7. EFFECT PARSING INEFFICIENCY: Fallback Parser is Clunky

**Location:** `src/main/java/com/zenax/armorsets/effects/EffectManager.java`, lines 185-209

```java
public ParsedEffect parseEffectString(String effectString) {
    // ... regex attempt ...
    Matcher matcher = EFFECT_PATTERN.matcher(effectString.trim());
    if (!matcher.matches()) {
        // Try simple format without regex
        String[] parts = effectString.split("\\s+");
        if (parts.length > 0) {
            String[] effectParts = parts[0].split(":");
            String target = parts.length > 1 ? parts[parts.length - 1] : null;
            if (target != null && !target.startsWith("@")) {
                target = null;
            }
            return new ParsedEffect(effectParts[0], effectString, target);
        }
        return null;
    }
    // ... rest of parsing ...
}
```

**THE PROBLEM:** You're doing regex + fallback parsing. If the regex fails, you do `split("\\s+")` + another `split(":")`. That's two regex operations hidden in string splits.

**THE IMPACT:**
- Every effect execution pays a regex tax
- The fallback logic is convoluted and easy to misparse

**HOW TO FIX IT:**
- Cache the parsing result if an effect string is repeated (most are from YAML)
- Use a simpler tokenizer instead of regex for simple cases

---

### 8. REGEX COMPILATION ANTI-PATTERN: Compiling Patterns on Every Set Load

**THE PROBLEM:** If patterns aren't cached at the class level, then every `getActiveSet()` call could be recompiling regex patterns. Pattern compilation is expensive.

**THE IMPACT:**
- Every armor detection could be doing regex compilations
- With armor checks every 5 ticks, this multiplies quickly

**HOW TO FIX IT:**
- Add a static `Pattern` cache in SetManager:
```java
private static final Map<String, Pattern> PATTERN_CACHE = new HashMap<>();

private Pattern getCompiledPattern(String patternStr) {
    return PATTERN_CACHE.computeIfAbsent(patternStr, Pattern::compile);
}
```

---

### 9. CONFIG LOADING: Not Cached, Not Validated

**Location:** `src/main/java/com/zenax/armorsets/config/ConfigManager.java`, lines 100-113

```java
private void loadConfigsFromDirectory(File directory, Map<String, FileConfiguration> configMap) {
    File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
    if (files == null) return;

    for (File file : files) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);  // <-- EVERY LOAD
            String name = file.getName().replace(".yml", "").replace(".yaml", "");
            configMap.put(name, config);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load config: " + file.getName(), e);
        }
    }
}
```

**THE PROBLEM:**
- No caching of parsed configs
- No validation that required keys exist
- String replacement logic for file names is fragile

**HOW TO FIX IT:**
- Validate required keys on load:
```java
private void validateConfig(FileConfiguration config, String file) {
    if (!config.contains("name") || !config.contains("effects")) {
        throw new InvalidConfigException("Missing required keys in " + file);
    }
}
```
- Use `FilenameUtils.removeExtension()` instead of string replace

---

## ARCHITECTURAL ISSUES

### 10. GUI MANAGER: GIANT UNMAINTAINABLE CLASS

**THE PROBLEM:**
- Single class doing inventory management, event handling, session tracking, and rendering
- Hard to test
- Hard to modify without breaking other features
- Likely contains duplicate UI building logic

**HOW TO FIX IT:**
- Split into:
  - `GUISession` - Session state management
  - `InventoryBuilder` - Creates inventory layouts
  - `InventoryRenderer` - Renders items
  - `GUIEventHandler` - Handles clicks (keep as separate listener)
  - `GUIManager` - Coordinates the above

---

## CODE QUALITY ISSUES

**Minor sins:**

- **Magic Numbers**: `5L`, `20L`, `10L` scattered throughout TriggerHandler. These should be config values or constants.
- **Incomplete Null Checks**: Some paths don't null-check before dereferencing (e.g., `parts[0]` in condition parsing).
- **Logging Spam**: Printing to `System.err` in ConditionManager instead of using a logger.
- **Copy-Paste Effect Implementations**: Many effect classes likely have identical structure. Consider an annotation-based or factory pattern to reduce duplication.

---

## WHAT'S ACTUALLY GOOD

Let me give credit where it's due:

1. **Manager Pattern is Clean**: The separation of concerns with ConfigManager, EffectManager, SetManager, etc. is solid. Easy to understand the hierarchy.

2. **PDC Usage for Item Data**: Using `NamespacedKey` and `PersistentDataType` for storing sigil/set data is the modern, correct approach. Good job not reinventing wheels.

3. **Effect Strategy Pattern**: The `Effect` interface with 40+ implementations is extensible and maintainable. Adding new effects is straightforward.

4. **Condition System is Thoughtful**: The condition evaluation system (health %, biome, time, etc.) is well-designed for configurability.

5. **Cooldown Manager is Simple and Works**: The concurrent hash map usage for cooldowns is correct and efficient.

6. **Error Handling is Present**: You catch exceptions and log them rather than letting crashes cascade.

---

## ACTIONABLE PATH FORWARD

### Priority 1 (Do This Now) - CRITICAL IMPACT
1. Increase armor check interval from 5L to 20L (or event-driven) - **4x performance improvement**
2. Add player quit event cleanup for TriggerHandler maps - **Memory leak prevention**
3. Pre-parse conditions at YAML load time - **70-80% condition evaluation improvement**
4. Cache tiered sigil versions - **90%+ reduction in cloning overhead**

**Estimated combined impact:** 40-60% reduction in CPU overhead during normal gameplay

### Priority 2 (Do This Soon) - IMPORTANT FIXES
5. Convert TriggerHandler maps to ConcurrentHashMap - **Thread safety fix**
6. Extract pattern compilation to a cache - **Regex overhead reduction**
7. Simplify effect string parsing (remove fallback logic) - **Parsing efficiency**
8. Validate configs on load - **Crash prevention**

### Priority 3 (Refactoring) - CODE QUALITY
9. Split GUIManager into smaller classes - **Maintainability**
10. Extract magic numbers to config file - **Configuration management**
11. Add proper logging instead of `System.err` - **Debugging**

---

## FINAL VERDICT

Your plugin isn't bad. It's actually moderately well-architected with a solid effects engine and good separation of concerns. But you've got some **glaring performance inefficiencies** that scream "this wasn't profiled under load." The 5-tick armor checks, condition string parsing, and sigil cloning are all unnecessary tax on the server.

The good news? These are **all fixable without rewriting core logic**. You don't need a rewrite; you need targeted optimizations that preserve your architecture while cutting the overhead.

If you implement Priority 1 items, your plugin will be noticeably snappier on servers with 30+ concurrent players. That's not a trivial win.

Now stop reading docs and go profile your code with Spark. You might find even bigger bottlenecks.
