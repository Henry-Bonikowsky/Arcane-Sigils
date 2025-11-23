# Tier System Guide (Tiers 1-10)

## Overview

The Custom ArmorWeapon Plugin supports **Tiers 1-10** for both armor sets and core functions. Each tier up provides **scaling improvements** to effect values while maintaining the exact same structure and triggers.

### Tier Scaling Formula

**For Core Functions:**
```
multiplier = 0.5 + (tier * 0.1)

T1:  0.6x  (60% of base values)
T2:  0.7x  (70% of base values)
T3:  0.8x  (80% of base values)
T4:  0.9x  (90% of base values)
T5:  1.0x  (100% - baseline)
T6:  1.1x  (110% of base values)
T7:  1.2x  (120% of base values)
T8:  1.3x  (130% of base values)
T9:  1.4x  (140% of base values)
T10: 1.5x  (150% of base values)
```

**For Armor Sets:**
- Automatically inherited from effects
- Same structure for all tiers
- Numeric values scale with the same multiplier

## Designing Tiers

### Key Principle: Same Structure, Improved Values

When designing tiered sets/functions, **do not change the mechanics between tiers**. Only the numeric values should increase.

### Bad Example (Mechanics Change)
```yaml
# DON'T do this - mechanics change between tiers
function:
  name: "Fire Blast"
  T1:
    effects:
      - INCREASE_DAMAGE:10
  T5:
    effects:
      - INCREASE_DAMAGE:50
      - POTION:FIRE_RESISTANCE:999  # NEW effect added!
```

### Good Example (Values Scale)
```yaml
# DO this - same mechanics, scaled values
fireblast:
  name: "Fire Blast"
  slot: "chestplate"
  description:
    - "&cHarness the power of fire"
  effects:
    ATTACK:
      chance: 40
      effects:
        - INCREASE_DAMAGE:50      # Scales: T1=30, T5=50, T10=75
        - PARTICLE:FLAME:20 @Victim
      cooldown: 1
```

When loaded:
- **T1 version:** INCREASE_DAMAGE:30, cooldown:0.6s
- **T5 version:** INCREASE_DAMAGE:50, cooldown:1.0s
- **T10 version:** INCREASE_DAMAGE:75, cooldown:1.5s

## What Gets Scaled

### Numeric Values (Scaled)
- Damage amounts: `INCREASE_DAMAGE:50`
- Healing amounts: `HEAL:10`, `DEVOUR:5`
- Potion durations: `POTION:STRENGTH:999:2`
- Potion amplifiers: `POTION:STRENGTH:999:2` (second number)
- Particle counts: `PARTICLE:FLAME:20` → scales to 30 at T10
- Teleport distances: `TELEPORT_RANDOM:5` → scales to 7.5 at T10
- Effect values: `AEGIS:5`, `DISINTEGRATE:1`

### Non-Numeric Values (NOT Scaled)
- Trigger names: `ATTACK`, `DEFENSE`, etc.
- Effect names: `INCREASE_DAMAGE`, `HEAL`, etc.
- Target selectors: `@Self`, `@Victim`, `@Nearby:10`
- Chance percentages: `chance: 40`
- Cooldowns: `cooldown: 1`
- Conditions: All condition types
- Text/Messages: `MESSAGE:` text

## Creating a Tiered Set

### Step 1: Define Tier 1 (Base)

```yaml
my_set:
  tiers:
    1:
      name_pattern: "My Set.*Tier I"
      material: DIAMOND
      equipped_message:
        - "&b&lSET EQUIPPED"
      individual_effects:
        helmet:
          EFFECT_STATIC:
            chance: 100
            effects:
              - POTION:NIGHT_VISION:999
            cooldown: 0
      synergies:
        my_ability:
          trigger: ATTACK
          chance: 50
          effects:
            - INCREASE_DAMAGE:20
            - PARTICLE:CRIT_MAGIC:15 @Victim
          cooldown: 2
```

### Step 2: Add Tier 10 (Same Structure)

```yaml
    10:
      name_pattern: "My Set.*Tier X"
      material: DIAMOND
      equipped_message:
        - "&b&lULTIMATE SET EQUIPPED"
      individual_effects:
        helmet:
          EFFECT_STATIC:
            chance: 100
            effects:
              - POTION:NIGHT_VISION:999
            cooldown: 0
      synergies:
        my_ability:
          trigger: ATTACK
          chance: 50
          effects:
            - INCREASE_DAMAGE:20      # Will scale to 30 at T10
            - PARTICLE:CRIT_MAGIC:15 @Victim
          cooldown: 2
```

**Result:**
- T1: INCREASE_DAMAGE:12 (0.6x multiplier)
- T5: INCREASE_DAMAGE:20 (1.0x multiplier)
- T10: INCREASE_DAMAGE:30 (1.5x multiplier)

## Creating a Tiered Function

Functions automatically scale from T1-T10 if defined in YAML.

### Structure
```yaml
my_function:
  name: "My Function"
  slot: "chestplate"        # Must match slot for socketing
  description:
    - "&7Description line 1"
    - "&7Description line 2"
  item_form:
    material: AMETHYST_SHARD
    name: "&dFunction Name"
    lore:
      - "&7Function lore"
    glow: true
  effects:
    ATTACK:
      chance: 50
      effects:
        - INCREASE_DAMAGE:40
        - PARTICLE:CRIT_MAGIC:20 @Victim
      cooldown: 2
    BOW_HIT:
      chance: 70
      effects:
        - INCREASE_DAMAGE:30
      cooldown: 1
```

**Note:** You only need to define the function ONCE in the YAML. The system automatically creates T1-T10 versions with scaled values.

### What Changes vs What Doesn't

| Aspect | T1 | T5 | T10 | Note |
|--------|-----|-----|------|------|
| Name | "My Function [T1]" | "My Function [T5]" | "My Function [T10]" | Tier appended automatically |
| Effects | Same triggers | Same triggers | Same triggers | Structure identical |
| INCREASE_DAMAGE:40 | 24 | 40 | 60 | Scales by multiplier |
| PARTICLE:CRIT_MAGIC:20 | 12 | 20 | 30 | Scales particle count |
| cooldown: 2 | 1.2s | 2.0s | 3.0s | Scales cooldown |
| chance: 50 | 50% | 50% | 50% | Chance NOT scaled |

## Advanced: Conditional Tier Scaling

### Using Conditions to Gate Tiers

You can use different conditions at different tiers:

```yaml
healing_spell:
  name: "Healing Spell"
  slot: "helmet"
  effects:
    TICK:
      chance: 20
      effects:
        - HEAL:3
      cooldown: 0
      conditions:
        - HEALTH_BELOW:15
```

At higher tiers, this becomes more powerful naturally because `HEAL:3` becomes `HEAL:4.5` at T10.

### Per-Tier Message Updates

```yaml
my_synergy:
  trigger: ATTACK
  effects:
    - INCREASE_DAMAGE:50
    - MESSAGE:&cPowered up! @Self
  cooldown: 3
```

The message is the same, but the damage increase scales:
- T1: INCREASE_DAMAGE:30
- T10: INCREASE_DAMAGE:75

## Practical Examples

### Example 1: Simple Defensive Buff

```yaml
iron_skin:
  name: "Iron Skin"
  slot: "chestplate"
  description:
    - "&7Hardens your skin"
  item_form:
    material: IRON_INGOT
    name: "&7Iron Skin"
    lore:
      - "&7Defensive function"
    glow: true
  effects:
    DEFENSE:
      chance: 60
      effects:
        - AEGIS:5          # T1:3, T5:5, T10:7.5
      cooldown: 3
    EFFECT_STATIC:
      chance: 100
      effects:
        - POTION:RESISTANCE:999:0
      cooldown: 0
```

### Example 2: Damage-Based Ability

```yaml
execution:
  name: "Execution"
  slot: "chestplate"
  description:
    - "&cStrikes with fatal force"
  item_form:
    material: DIAMOND
    name: "&cExecution Strike"
    lore:
      - "&cDeal massive damage to weak enemies"
    glow: true
  effects:
    ATTACK:
      chance: 40
      effects:
        - INCREASE_DAMAGE:100   # T1:60, T5:100, T10:150
        - PARTICLE:SOUL_FIRE_FLAME:30 @Victim
      cooldown: 5
      conditions:
        - VICTIM_HEALTH_BELOW:5
```

### Example 3: Complex Set with Multiple Tiers

```yaml
dragon_slayer:
  tiers:
    1:
      name_pattern: "Dragon Slayer.*Tier I"
      material: NETHERITE
      equipped_message:
        - "&c&lDragon Slayer I equipped"
      synergies:
        dragon_strike:
          trigger: ATTACK
          chance: 50
          effects:
            - INCREASE_DAMAGE:75      # Scales to 112.5 at T10
            - POTION:SLOW:3:1 @Victim
            - PARTICLE:EXPLOSION:20 @Victim
          cooldown: 4
        dragon_defense:
          trigger: DEFENSE
          chance: 75
          effects:
            - AEGIS:10                # Scales to 15 at T10
            - POTION:FIRE_RESISTANCE:5 @Self
          cooldown: 2

    5:
      name_pattern: "Dragon Slayer.*Tier V"
      material: NETHERITE
      equipped_message:
        - "&c&lDragon Slayer V equipped"
      synergies:
        dragon_strike:
          trigger: ATTACK
          chance: 50
          effects:
            - INCREASE_DAMAGE:75
            - POTION:SLOW:3:1 @Victim
            - PARTICLE:EXPLOSION:20 @Victim
          cooldown: 4
        dragon_defense:
          trigger: DEFENSE
          chance: 75
          effects:
            - AEGIS:10
            - POTION:FIRE_RESISTANCE:5 @Self
          cooldown: 2

    10:
      name_pattern: "Dragon Slayer.*Tier X"
      material: NETHERITE
      equipped_message:
        - "&c&lULTIMATE Dragon Slayer equipped"
      synergies:
        dragon_strike:
          trigger: ATTACK
          chance: 50
          effects:
            - INCREASE_DAMAGE:75
            - POTION:SLOW:3:1 @Victim
            - PARTICLE:EXPLOSION:20 @Victim
          cooldown: 4
        dragon_defense:
          trigger: DEFENSE
          chance: 75
          effects:
            - AEGIS:10
            - POTION:FIRE_RESISTANCE:5 @Self
          cooldown: 2
```

## Common Mistakes

### ❌ Mistake 1: Changing Triggers Between Tiers
```yaml
# WRONG - triggers change between tiers
T1:
  effects:
    ATTACK: [...]
T10:
  effects:
    ATTACK: [...]
    DEFENSE: [...]  # NEW trigger added - BAD!
```

### ✅ Correct Approach
Keep the same triggers, let values scale:
```yaml
# RIGHT - same structure for all tiers
effects:
  ATTACK: [...]
  DEFENSE: [...]
```

### ❌ Mistake 2: Adding New Effects at Higher Tiers
```yaml
# WRONG - adds new effect at T10
T1:
  effects:
    - INCREASE_DAMAGE:20
T10:
  effects:
    - INCREASE_DAMAGE:100
    - POTION:STRENGTH:999:1  # NEW - BAD!
```

### ✅ Correct Approach
Include all effects at all tiers:
```yaml
# RIGHT - same effects, values scale
effects:
  - INCREASE_DAMAGE:20    # T1:12, T10:30
  - POTION:STRENGTH:999:1
```

### ❌ Mistake 3: Duplicate Tier Definitions
```yaml
# WRONG - redundant copies
setname:
  tiers:
    1: {...copy of everything...}
    2: {...copy of everything...}
    3: {...copy of everything...}
```

### ✅ Correct Approach
Only define T1 and T10, or just T1:
```yaml
# RIGHT - only define base tier
setname:
  tiers:
    1: {...everything...}
    # System auto-creates T2-T9, T10 is optional
```

## Performance Considerations

- Each function T1-T10 creates 10 separate entries in memory
- Only define tiers you actually use
- If you only want T1-T5, define only those tiers

## Migration from Old System

If upgrading from the old 5-tier system (T1-T5):

**Old:**
```yaml
function:
  # Only created T1-T5 automatically
```

**New:**
```yaml
function:
  # Creates T1-T10 automatically
  # Old configs still work (just stop at T5)
```

No changes needed to existing configs! The system automatically scales to T10 now.
