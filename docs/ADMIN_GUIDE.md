# ArmorSets Plugin - Admin Guide

Complete guide for server administrators to configure and manage armor sets.

---

## Table of Contents

1. [Installation](#installation)
2. [Configuration Files](#configuration-files)
3. [Creating Armor Sets](#creating-armor-sets)
4. [Creating Core Functions](#creating-core-functions)
5. [Commands & Permissions](#commands--permissions)
6. [Troubleshooting](#troubleshooting)
7. [Performance Tips](#performance-tips)

---

## Installation

### 1. Download Plugin

Get the compiled JAR file (see BUILDING.md).

### 2. Install to Server

```
1. Copy ArmorSets.jar to <Server Root>/plugins/
2. Restart server
3. Plugin auto-creates folder structure in plugins/ArmorSets/
```

### 3. Verify Installation

Check server log for:

```
[ArmorSets] Initializing ArmorSets plugin...
[ArmorSets] Loaded 42 armor sets
[ArmorSets] Loaded 156 core functions
[ArmorSets] Loaded 656 weapons
[ArmorSets] ArmorSets has been enabled!
```

---

## Configuration Files

Plugin creates this folder structure:

```
plugins/ArmorSets/
├── config.yml                    ← Main settings
├── messages.yml                  ← Custom messages
├── core-functions/               ← Core function configs
│   ├── helmet-functions.yml
│   ├── chestplate-functions.yml
│   ├── leggings-functions.yml
│   └── boots-functions.yml
├── sets/                         ← Armor set configs
│   ├── arcanist.yml
│   ├── demon.yml
│   └── (145+ more set configs)
└── weapons/                      ← Weapon configs
    └── (656+ weapon configs)
```

### Main Config (`config.yml`)

Controls global plugin behavior:

```yaml
settings:
  debug: false                    # Enable debug logging
  effect-check-interval: 20       # Ticks between effect checks (20 = 1 second)
  use-itemsadder: true           # Enable ItemsAdder support

core-functions:
  allow-trading: true            # Can players trade functions?
  allow-extraction: true         # Can functions be removed from armor?
  extraction-cost: 0             # Cost to extract (0 = free)
  enforce-tier-restrictions: false  # Block low-tier armor accepting high-tier functions?
  enforce-slot-restrictions: true   # Can only slot helmet functions in helmets?

sets:
  detection-method: NAME_PATTERN # How to detect sets (NAME_PATTERN, LORE_TAG, NBT_TAG)
  require-same-tier: true        # All 4 pieces must be same tier?
  partial-bonuses:
    enabled: false               # 2-piece and 3-piece bonuses?
    2-piece-multiplier: 0.25
    3-piece-multiplier: 0.5

gui:
  socket-title: "&8Socket Core Function"
  sounds:
    open: BLOCK_CHEST_OPEN
    close: BLOCK_CHEST_CLOSE
    socket: BLOCK_ANVIL_USE
    unsocket: BLOCK_ANVIL_LAND
    error: ENTITY_VILLAGER_NO

effects:
  max-damage-increase: 500       # Cap damage boosts at +500%
  max-teleport-distance: 50      # Max teleport range
  max-spawned-entities: 5        # Max mobs spawned per effect
  max-lifesteal: 50              # Max lifesteal percentage

integrations:
  placeholderapi: true           # PlaceholderAPI support
  worldguard: true               # WorldGuard region checks
  respect-pvp-flags: true        # Block effects in non-PvP regions
```

---

## Creating Armor Sets

Armor sets go in `plugins/ArmorSets/sets/` as `.yml` files.

### Basic Structure

```yaml
set_name:
  tiers:
    1:                           # Tier number
      name_pattern: "Set.*Tier I"   # Regex to match item names
      material: NETHERITE        # Base material

      equipped_message:
        - "&d[Set Name] Equipped!"

      unequipped_message:
        - "&c[Set Name] Removed."

      # Effects for individual pieces
      individual_effects:
        helmet:
          EFFECT_STATIC:
            chance: 100
            effects:
              - "POTION:NIGHT_VISION:1"
            cooldown: 0

        chestplate:
          DEFENSE:
            chance: 25
            effects:
              - "AEGIS:3"
            cooldown: 10

        leggings:
          # ... more effects

        boots:
          # ... more effects

      # Full set bonuses (4/4 pieces)
      synergies:
        synergy_id:
          trigger: ATTACK        # ATTACK, DEFENSE, KILL_MOB, KILL_PLAYER, SHIFT, FALL_DAMAGE
          chance: 30             # 0-100%
          effects:
            - "INCREASE_DAMAGE:50"
            - "PARTICLE:SOUL:30 @Victim"
          cooldown: 3            # Seconds

    2:                           # Tier 2 (stronger version)
      # ... repeat structure with higher values
```

### Name Pattern Examples

```yaml
# Matches items with "Dragon" anywhere in name
name_pattern: "Dragon"

# Matches "Shadow Helmet Tier I", "Shadow Boots Tier I", etc.
name_pattern: "Shadow.*Tier I"

# Case-insensitive match for "Arcanist"
name_pattern: "(?i)arcanist.*"

# Exact match (must use anchors)
name_pattern: "^Exact Name$"
```

### Trigger Types

| Trigger | When It Fires |
|---------|---------------|
| `ATTACK` | Player attacks entity |
| `DEFENSE` | Player takes damage |
| `KILL_MOB` | Player kills a mob |
| `KILL_PLAYER` | Player kills player |
| `SHIFT` | Player sneaks/unsneaks |
| `FALL_DAMAGE` | Player takes fall damage |
| `EFFECT_STATIC` | Periodic passive effect |

### Tips for Creating Sets

1. **Name Pattern:** Make sure it matches your custom item names exactly
   - Get item in hand: `/as info`
   - Copy the exact display name
   - Use that in `name_pattern`

2. **Tier Progression:** Higher tiers should be stronger
   - Tier I: 30% chance, +50% damage
   - Tier II: 40% chance, +100% damage
   - Tier V: 75% chance, +300% damage

3. **Cooldowns:** Balance with frequency
   - Common effects: 0-3 seconds
   - Powerful effects: 5-10 seconds
   - Ultimate synergies: 10-30 seconds

4. **Messages:** Keep them short and thematic
   ```yaml
   equipped_message:
     - "&5✨ &dYou feel arcane power flow..."
   ```

---

## Creating Core Functions

Core functions are socketable abilities in `plugins/ArmorSets/core-functions/` folders.

### Basic Structure

```yaml
function_id:
  name: "&dFunction Name"
  slot: HELMET                   # HELMET, CHESTPLATE, LEGGINGS, BOOTS
  tier: 1
  description:
    - "&7First description line"
    - "&7Second description line"

  effects:
    EFFECT_STATIC:              # Passive effect
      chance: 100
      effects:
        - "POTION:NIGHT_VISION:1"
      cooldown: 0

    ATTACK:                      # On player attack
      chance: 50
      effects:
        - "INCREASE_DAMAGE:25"
      cooldown: 5

  # Item form when it's a shard/gem
  item_form:
    material: ECHO_SHARD
    model_data: 100
    name: "&5Shard: Function Name"
    lore:
      - "&7Tier 1 Helmet Function"
      - "&fRight-click to socket"
    glow: true
```

### Slot Types

- `HELMET` - Helmet armor slot
- `CHESTPLATE` - Chestplate armor slot
- `LEGGINGS` - Leggings armor slot
- `BOOTS` - Boots armor slot

### Item Form

The item form defines what the socketable shard looks like:

| Field | Values |
|-------|--------|
| `material` | Any Minecraft material (ECHO_SHARD, AMETHYST_SHARD, etc.) |
| `model_data` | 0-9999 (for custom models in resource packs) |
| `name` | Display name with color codes |
| `lore` | Description lines |
| `glow` | true/false (enchantment glow effect) |

---

## Commands & Permissions

### Player Commands

```bash
/as help                           # Show help menu

/as info                           # Info about held armor piece
/as unsocket                       # Remove function from armor

/as list functions                 # List all available functions
/as list sets                      # List all armor sets
```

### Admin Commands

```bash
# Give items
/as give function <player> <id>    # Give function shard
/as give armor <player> <slot> [name]  # Give armor piece
/as give set <player> <set_id>     # Give full set

# Management
/as reload                         # Reload all configs
/as preview <set>                  # Show set info
/as validate                       # Check all configs for errors

# Testing
/as test set <player> <set> <tier> # Give full set instantly
/as test function <player> <func>  # Trigger specific function
/as perf                           # Performance metrics
```

### Permissions

```yaml
armorsets.reload          # Reload plugin
armorsets.give            # Give items via command
armorsets.socket          # Socket functions into armor
armorsets.admin           # Admin commands
armorsets.test            # Testing commands
```

### Permission Setup (LuckPerms Example)

```bash
# Give all basic permissions to players
/luckperms group default permission set armorsets.socket true

# Give admin perms to staff
/luckperms group staff permission set armorsets.admin true
/luckperms group staff permission set armorsets.reload true
```

---

## Troubleshooting

### Plugin won't load

**Check logs for:**

```
[ERROR] Failed to initialize managers!
```

**Solutions:**
1. Verify Java 21+: `java -version`
2. Check for syntax errors in YAML files
3. Enable debug: `debug: true` in config.yml
4. Check console for specific error message

### Effects not triggering

**Debug checklist:**

1. Is the armor correct? `/as info` on the armor piece
2. Is set detection working? Check that name_pattern matches
3. Are cooldowns expired? Wait longer or check config
4. Enable debug mode in config.yml

```bash
# Check logs for activation attempts
debug: true
# Restart server
# Try triggering effect again
# Check console
```

### Set bonuses not activating

1. **Do you have full 4/4 set?** Check armor with `/as info`
2. **Does all armor match the name_pattern?**
3. **Are they same tier?** If `require-same-tier: true`, all pieces must be same tier
4. **Check name pattern:** Should be regex, not exact text

### YAML Syntax Errors

**Common issues:**

```yaml
# ❌ Wrong: Tabs instead of spaces
	name: "Bad"

# ✅ Correct: 2 spaces per indent level
  name: "Good"

# ❌ Wrong: Missing quotes
name: Some Name With Spaces

# ✅ Correct: Quotes for strings with spaces
name: "Some Name With Spaces"

# ❌ Wrong: Unbalanced quotes
name: "Unclosed quote

# ✅ Correct: Balanced quotes
name: "Closed quote"
```

**Test YAML syntax:** Use online YAML validator or IDE plugin

---

## Performance Tips

### 1. Reduce Effect Check Interval

In `config.yml`:

```yaml
settings:
  effect-check-interval: 20  # Ticks (20 = 1 second)
```

- Lower = more frequent checks = higher CPU
- Default (20) is usually fine
- Increase to 40 if lagging

### 2. Limit Concurrent Effects

```yaml
effects:
  max-spawned-entities: 5    # Max mobs per effect
  max-damage-increase: 500   # Cap bonuses at 500%
```

### 3. Use Cooldowns

Always use cooldowns on powerful effects:

```yaml
ATTACK:
  cooldown: 3    # Wait 3 seconds between triggers
  chance: 30     # Don't trigger every hit
```

### 4. Monitor Performance

```bash
/as perf
```

Shows CPU time spent on different operations.

### 5. Disable Unused Features

If you don't use WorldGuard:

```yaml
integrations:
  worldguard: false
```

---

## Configuration Examples

See `docs/examples/` for:

- `armor-set-basic.yml` - Simple armor set
- `armor-set-advanced.yml` - Complex set with synergies
- `core-function-basic.yml` - Basic socketable function
- `core-function-advanced.yml` - Complex function
- `config-recommended.yml` - Suggested config settings

---

## Next Steps

1. ✅ Copy example configs from `docs/examples/`
2. ✅ Modify names, effects, and stats for your server
3. ✅ Test with `/as give set @s <set_id>`
4. ✅ Adjust cooldowns and chances based on gameplay
5. ✅ Use `/as reload` to test changes without restart

See **README.md** for effect reference guide.

