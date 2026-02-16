# AttributeModifierManager - Usage Guide

## Overview

The `AttributeModifierManager` prevents the attribute modifier stacking bug by using named modifiers with consistent UUIDs. This ensures that updating a modifier replaces the old one instead of adding a duplicate.

---

## The Problem

**Before AttributeModifierManager:**
```java
// This code STACKS modifiers infinitely!
AttributeInstance armor = player.getAttribute(Attribute.ARMOR);
AttributeModifier mod = new AttributeModifier(
    new NamespacedKey(plugin, "dr"),
    4.0,
    AttributeModifier.Operation.ADD_NUMBER
);
armor.addModifier(mod); // First hit: 4 armor
armor.addModifier(mod); // Second hit: 8 armor (BUG!)
armor.addModifier(mod); // Third hit: 12 armor (BUG!)
// Result: Player ends up with 20+ armor modifiers
```

**After AttributeModifierManager:**
```java
// This code UPDATES the same modifier
manager.setNamedModifier(player, Attribute.ARMOR, "kings_brace_dr", 4.0, ADD_NUMBER, 60);
manager.setNamedModifier(player, Attribute.ARMOR, "kings_brace_dr", 6.0, ADD_NUMBER, 60);
manager.setNamedModifier(player, Attribute.ARMOR, "kings_brace_dr", 8.0, ADD_NUMBER, 60);
// Result: Player has exactly 1 modifier with value 8.0
```

---

## API Methods

### Set Named Modifier
Adds or updates a named attribute modifier. If a modifier with the same name exists, it's replaced.

```java
AttributeModifierManager manager = plugin.getAttributeModifierManager();

manager.setNamedModifier(
    entity,           // LivingEntity - who to modify
    attribute,        // Attribute - which attribute (e.g., Attribute.ARMOR)
    name,             // String - unique name (e.g., "kings_brace_dr")
    value,            // double - modifier value (e.g., 4.0)
    operation,        // Operation - ADD_NUMBER, ADD_SCALAR, MULTIPLY_SCALAR_1
    durationSeconds   // int - duration in seconds (0 = never expires)
);
```

### Remove Named Modifier
Removes a specific named modifier.

```java
manager.removeNamedModifier(player, Attribute.ARMOR, "kings_brace_dr");
```

### Check if Modifier Exists
Check if an entity has a specific named modifier.

```java
boolean hasModifier = manager.hasNamedModifier(player, Attribute.ARMOR, "kings_brace_dr");
```

### Get Modifier Value
Get the current value of a named modifier.

```java
double value = manager.getModifierValue(player, Attribute.ARMOR, "kings_brace_dr");
```

### Remove All Modifiers
Remove all tracked modifiers for an entity (useful for cleanup).

```java
manager.removeAllModifiers(player.getUniqueId());
```

---

## Common Use Cases

### King's Brace - Damage Reduction Based on Charges

The King's Brace sigil needs damage reduction that scales with charge count. Each hit updates the DR%, but must NOT stack.

```java
// In your effect code (ON_DEFEND signal)
AttributeModifierManager manager = plugin.getAttributeModifierManager();

// Calculate damage reduction based on charges
int charges = getCharges(player);
double drPercent = charges * 0.05; // 5% DR per charge
double armorValue = drPercent * 20; // Convert to armor points

// Set/update the named modifier
manager.setNamedModifier(
    player,
    Attribute.ARMOR,
    "kings_brace_dr",
    armorValue,
    AttributeModifier.Operation.ADD_NUMBER,
    60  // Lasts 60 seconds
);

// Result: Player always has EXACTLY ONE "kings_brace_dr" modifier
// Hit 1: 4 armor (5 charges)
// Hit 2: 6 armor (7 charges) - REPLACES the 4 armor modifier
// Hit 3: 8 armor (9 charges) - REPLACES the 6 armor modifier
```

### Movement Speed Slow (Quicksand Effect)

Apply a 50% movement speed reduction that can be refreshed.

```java
AttributeModifierManager manager = plugin.getAttributeModifierManager();

manager.setNamedModifier(
    target,
    Attribute.MOVEMENT_SPEED,
    "quicksand_slow",
    -0.5,  // -50% speed
    AttributeModifier.Operation.MULTIPLY_SCALAR_1,
    8  // Lasts 8 seconds
);

// If applied again within 8 seconds, it extends the duration (doesn't stack)
```

### Permanent Damage Boost (Equipment Effect)

Add a permanent damage boost while armor is equipped.

```java
AttributeModifierManager manager = plugin.getAttributeModifierManager();

// ON_EQUIP signal
manager.setNamedModifier(
    player,
    Attribute.ATTACK_DAMAGE,
    "berserker_damage",
    5.0,  // +5 attack damage
    AttributeModifier.Operation.ADD_NUMBER,
    0  // 0 = never expires (must be manually removed)
);

// ON_UNEQUIP signal
manager.removeNamedModifier(player, Attribute.ATTACK_DAMAGE, "berserker_damage");
```

### Max Health Boost with Dynamic Scaling

Boost max health based on nearby enemies.

```java
AttributeModifierManager manager = plugin.getAttributeModifierManager();

int nearbyEnemies = getNearbyEnemyCount(player, 10.0);
double healthBoost = nearbyEnemies * 4.0; // +2 hearts per enemy

manager.setNamedModifier(
    player,
    Attribute.MAX_HEALTH,
    "combat_vitality",
    healthBoost,
    AttributeModifier.Operation.ADD_NUMBER,
    10  // Refreshes every 10 seconds via EFFECT_STATIC
);

// Each time this runs, it UPDATES the existing modifier instead of adding a new one
```

---

## How It Works Internally

1. **Consistent UUID Generation**: The manager uses `UUID.nameUUIDFromBytes(name)` to generate the same UUID for the same name every time.

2. **Key-Based Identification**: Modifiers are identified by their NamespacedKey (`attr_<name>`), making them easy to find and replace.

3. **Automatic Cleanup**: 
   - Duration-based removal: Tasks scheduled via BukkitScheduler
   - Entity cleanup: Periodic task removes dead entities from tracking
   - Shutdown cleanup: All tasks cancelled on plugin disable

4. **Thread-Safe**: Uses ConcurrentHashMap for safe concurrent access.

---

## Best Practices

### Naming Convention
Use descriptive, unique names that indicate both the sigil and the effect:
- ✅ `kings_brace_dr` (sigil + effect type)
- ✅ `berserker_damage` (clear purpose)
- ✅ `quicksand_slow` (effect + type)
- ❌ `modifier1` (too generic)
- ❌ `temp` (unclear purpose)

### Duration Strategy
- **0 seconds**: Permanent modifiers (must be manually removed, e.g., equipment effects)
- **Short duration (1-10s)**: Combat effects that refresh frequently
- **Medium duration (30-60s)**: Temporary buffs/debuffs
- **Long duration (120+ s)**: Persistent effects that rarely refresh

### Cleanup
Always remove permanent modifiers when they're no longer needed:
```java
// ON_UNEQUIP signal
manager.removeNamedModifier(player, Attribute.ARMOR, "kings_brace_dr");
```

### Operation Types
- `ADD_NUMBER`: Flat bonus (e.g., +5 armor, +2 hearts)
- `ADD_SCALAR`: Percentage of base value (e.g., +20% of base speed)
- `MULTIPLY_SCALAR_1`: Final multiplier (e.g., -50% final speed = 0.5x speed)

---

## Integration with Existing Effects

The AttributeModifierManager is initialized in `ArmorSetsPlugin` and available via:

```java
ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
AttributeModifierManager manager = plugin.getAttributeModifierManager();
```

You can use it from any effect, listener, or manager in the plugin.

---

## Debugging

Enable debug mode in `config.yml`:
```yaml
settings:
  debug: true
```

Debug output includes:
- When modifiers are set: `[AttributeModifierManager] Set modifier 'kings_brace_dr' on Player's ARMOR: +4.00 (ADD_NUMBER) for 60s`
- When modifiers are removed: `[AttributeModifierManager] Removed modifier 'kings_brace_dr' from Player's ARMOR`

---

## Statistics

Get statistics about active modifiers:

```java
Map<String, Integer> stats = manager.getStatistics();
int trackedEntities = stats.get("tracked_entities");
int activeModifiers = stats.get("active_modifiers");

plugin.getLogger().info("Tracking " + trackedEntities + " entities with " + activeModifiers + " active modifiers");
```

---

## Alex Check

**Would Alex understand how to use this?**

No - this is a programmer API, not a GUI feature. Alex doesn't need to know about this. Effects that use the AttributeModifierManager will "just work" without stacking bugs, and Alex will configure them through the existing effect parameters in the GUI.

**For Alex**: The sigil effect parameters work the same as before. If a sigil modifies attributes (like King's Brace damage reduction), it will now correctly update instead of stacking infinitely.
