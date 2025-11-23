# ArmorSets Plugin

A comprehensive Minecraft Spigot/Paper plugin for custom armor sets with hot-swappable core functions, set synergies, and extensive effect systems.

## Features

- **Core Functions**: Socketable abilities that can be added to armor pieces
- **Armor Sets**: Full armor sets with individual piece effects and set synergy bonuses
- **45+ Effect Types**: Damage, healing, movement, potions, particles, and more
- **7 Trigger Types**: ATTACK, DEFENSE, KILL_MOB, KILL_PLAYER, SHIFT, FALL_DAMAGE, EFFECT_STATIC
- **Hot-Swap System**: Socket and unsocket functions without losing them
- **Tiered Progression**: Support for multiple tiers per set/function
- **Color Support**: Legacy `&` codes, hex colors `&#RRGGBB`, and MiniMessage gradients

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/as help` | Show help menu | - |
| `/as reload` | Reload all configurations | `armorsets.reload` |
| `/as give function <player> <id>` | Give a function shard | `armorsets.give` |
| `/as give armor <player> <slot> [name]` | Give an armor piece | `armorsets.give` |
| `/as give set <player> <set_id>` | Give a full armor set | `armorsets.give` |
| `/as list functions` | List all core functions | - |
| `/as list sets` | List all armor sets | - |
| `/as info` | Info about held item | - |
| `/as unsocket` | Remove function from held armor | - |

---

## Configuration Files

### Main Config (`config.yml`)

```yaml
settings:
  debug: false                    # Enable debug messages
  effect-check-interval: 20       # Ticks between static effect checks (20 = 1 second)

effects:
  max-lifesteal: 50              # Maximum lifesteal percentage cap

sets:
  require-same-tier: true        # Require all armor pieces to be same tier for set bonus
```

---

## Core Functions Configuration

Location: `plugins/ArmorSets/core-functions/`

Each YAML file can contain multiple core functions. Functions are abilities that can be socketed into armor.

### Basic Structure

```yaml
function_id:
  name: "&dFunction Name"        # Display name (supports color codes)
  slot: HELMET                   # Valid: HELMET, CHESTPLATE, LEGGINGS, BOOTS
  tier: 1                        # Function tier level
  description:
    - "&7First line of description"
    - "&7Second line"

  # Trigger configurations
  on_attack:                     # Triggers when player attacks
    chance: 100                  # Percentage chance to activate
    cooldown: 0                  # Cooldown in seconds
    effects:
      - "EFFECT_STRING"

  on_defense:                    # Triggers when player is damaged
    chance: 50
    cooldown: 5
    effects:
      - "EFFECT_STRING"
```

### Available Triggers

| Trigger | Description |
|---------|-------------|
| `on_attack` | Player attacks an entity |
| `on_defense` | Player takes damage |
| `on_kill_mob` | Player kills a mob |
| `on_kill_player` | Player kills another player |
| `on_shift` | Player starts sneaking |
| `on_fall_damage` | Player takes fall damage |
| `effect_static` | Passive effect (checked periodically) |

---

## Effects System

Effects are the core of what functions do. They use a string format:

```
EFFECT_TYPE:PARAM1:PARAM2:... @TARGET
```

### Target Specifiers

| Target | Description |
|--------|-------------|
| `@Self` | The player with the armor (default) |
| `@Victim` | The entity being attacked/damaged |
| `@Nearby:R` | All entities within R blocks |

### Effect Types Reference

#### Damage Effects

```yaml
effects:
  - "DAMAGE:10 @Victim"              # Deal 10 damage to victim
  - "DAMAGE_PERCENT:25 @Victim"      # Deal 25% of max health as damage
  - "TRUE_DAMAGE:5 @Victim"          # Bypass armor damage
  - "ARMOR_PIERCE:50:8 @Victim"      # 50% armor penetration, 8 damage
```

#### Healing Effects

```yaml
effects:
  - "HEAL:4 @Self"                   # Heal 4 HP (2 hearts)
  - "HEAL_PERCENT:20 @Self"          # Heal 20% of max health
  - "LIFESTEAL:15"                   # Heal 15% of damage dealt
  - "REGEN:5:2 @Self"                # Regenerate 5 HP over 2 seconds
```

#### Potion Effects

```yaml
effects:
  - "POTION:SPEED:10:1 @Self"        # Speed II for 10 seconds
  - "POTION:STRENGTH:30:0 @Self"     # Strength I for 30 seconds
  - "POTION:SLOWNESS:5:2 @Victim"    # Slowness III on victim for 5s
  - "POTION:POISON:8:1 @Victim"      # Poison II on victim for 8s
```

**Available Potion Types:**
SPEED, SLOWNESS, HASTE, MINING_FATIGUE, STRENGTH, INSTANT_HEALTH, INSTANT_DAMAGE, JUMP_BOOST, NAUSEA, REGENERATION, RESISTANCE, FIRE_RESISTANCE, WATER_BREATHING, INVISIBILITY, BLINDNESS, NIGHT_VISION, HUNGER, WEAKNESS, POISON, WITHER, HEALTH_BOOST, ABSORPTION, SATURATION, GLOWING, LEVITATION, LUCK, UNLUCK, SLOW_FALLING, CONDUIT_POWER, DOLPHINS_GRACE, DARKNESS

#### Movement Effects

```yaml
effects:
  - "LAUNCH:1.5 @Self"               # Launch upward with velocity 1.5
  - "PUSH:2.0 @Victim"               # Push victim away with velocity 2.0
  - "PULL:1.5 @Victim"               # Pull victim toward player
  - "TELEPORT_RANDOM:10 @Self"       # Teleport randomly within 10 blocks
  - "TELEPORT_BEHIND @Victim"        # Teleport behind victim
  - "DASH:1.5 @Self"                 # Dash forward
  - "FREEZE:3 @Victim"               # Freeze victim for 3 seconds
```

#### Particle Effects

```yaml
effects:
  - "PARTICLE:FLAME:50 @Self"        # Spawn 50 flame particles
  - "PARTICLE:EXPLOSION_LARGE:10 @Victim"
  - "PARTICLE:HEART:20 @Self"
```

#### Sound Effects

```yaml
effects:
  - "SOUND:ENTITY_EXPERIENCE_ORB_PICKUP:1:1 @Self"  # sound:volume:pitch
  - "SOUND:ENTITY_WITHER_SPAWN:1:0.5 @Self"
```

#### Combat Effects

```yaml
effects:
  - "IGNITE:5 @Victim"               # Set on fire for 5 seconds
  - "BLEED:2:5 @Victim"              # 2 damage every tick for 5 seconds
  - "EXECUTE:30 @Victim"             # Instant kill if below 30% HP
  - "DISARM:3 @Victim"               # Drop held item for 3 seconds
  - "STUN:2 @Victim"                 # Stun for 2 seconds (blindness+slow)
  - "LIGHTNING @Victim"              # Strike lightning
```

#### Utility Effects

```yaml
effects:
  - "MESSAGE:&aYou activated the ability!"  # Send message
  - "TITLE:&6Title:&7Subtitle:20:40:20"     # Title (fadeIn:stay:fadeOut in ticks)
  - "ACTIONBAR:&eYour actionbar message"    # Actionbar message
  - "COMMAND:say Hello World"               # Execute command as player
  - "CONSOLE_COMMAND:give %player% diamond 1"  # Execute as console
  - "CANCEL_EVENT"                          # Cancel the triggering event
```

#### Special Effects

```yaml
effects:
  - "PHOENIX:4"                      # Totem effect, revive with 4 HP
  - "VAMPIRIC:10 @Victim"           # Deal damage and heal same amount
  - "THORNS:50"                      # Reflect 50% damage back
  - "DOUBLE_DAMAGE"                  # Double attack damage
  - "CRITICAL_STRIKE:200"            # 200% critical damage
  - "COMBO:3"                        # Increase damage by combo count
  - "SHIELD:50:5 @Self"              # 50 HP shield for 5 seconds
  - "STEAL_EFFECTS @Victim"          # Steal positive potion effects
  - "CLEANSE @Self"                  # Remove negative potion effects
  - "EXPLOSION:3:false @Victim"      # Explosion power 3, no fire
```

---

## Armor Sets Configuration

Location: `plugins/ArmorSets/sets/`

### Basic Structure

```yaml
set_name:
  tiers:
    1:                               # Tier 1
      name_pattern: "Shadow.*Tier I" # Regex pattern to match item names
      material: NETHERITE_CHESTPLATE # Base material for the set

      equipped_message:
        - "&8[&dShadow Set&8] &7Equipped!"
      unequipped_message:
        - "&8[&dShadow Set&8] &7Unequipped."

      # Effects for individual armor pieces
      individual_effects:
        helmet:
          on_defense:
            chance: 100
            cooldown: 0
            effects:
              - "POTION:NIGHT_VISION:10:0 @Self"

        chestplate:
          on_attack:
            chance: 25
            cooldown: 5
            effects:
              - "LIFESTEAL:10"

        boots:
          on_shift:
            chance: 100
            cooldown: 10
            effects:
              - "POTION:SPEED:5:1 @Self"

      # Full set synergy bonuses
      synergies:
        shadow_strike:
          name: "&5Shadow Strike"
          description:
            - "&7Full set bonus"
            - "&7Attacks deal bonus shadow damage"
          trigger: ATTACK
          chance: 30
          cooldown: 3
          effects:
            - "DAMAGE:5 @Victim"
            - "PARTICLE:WITCH:30 @Victim"
            - "SOUND:ENTITY_WITHER_SHOOT:0.5:1.5"

    2:                               # Tier 2 (stronger version)
      name_pattern: "Shadow.*Tier II"
      material: NETHERITE_CHESTPLATE
      # ... more effects with higher values
```

### Name Patterns

The `name_pattern` uses regex to match armor pieces:

```yaml
name_pattern: "Shadow.*Tier I"     # Matches "Shadow Helmet Tier I", etc.
name_pattern: "Dragon"             # Matches any item with "Dragon" in name
name_pattern: "^Phoenix.*$"        # Matches items starting with "Phoenix"
```

---

## Weapons Configuration

Location: `plugins/ArmorSets/weapons/`

### Basic Structure

```yaml
weapon_id:
  name: "&c&lDragon Slayer"
  material: NETHERITE_SWORD
  required_set: dragon_t3           # Optional: requires wearing this set

  lore:
    - "&7A legendary dragon-slaying sword"
    - "&8Requires Dragon Set Tier 3"

  enchantments:
    SHARPNESS: 5
    FIRE_ASPECT: 2

  attributes:
    GENERIC_ATTACK_DAMAGE: 12
    GENERIC_ATTACK_SPEED: 1.6

  events:
    on_attack:
      chance: 20
      cooldown: 5
      effects:
        - "DAMAGE:10 @Victim"
        - "IGNITE:3 @Victim"

    on_kill_mob:
      chance: 100
      cooldown: 0
      effects:
        - "HEAL:2 @Self"
```

---

## Color Codes

### Legacy Color Codes

```
&0 Black       &8 Dark Gray
&1 Dark Blue   &9 Blue
&2 Dark Green  &a Green
&3 Dark Aqua   &b Aqua
&4 Dark Red    &c Red
&5 Dark Purple &d Light Purple
&6 Gold        &e Yellow
&7 Gray        &f White
&l Bold        &m Strikethrough
&n Underline   &o Italic
&k Obfuscated  &r Reset
```

### Hex Colors

```yaml
name: "&#FF5555Custom Red Name"
```

### Gradients (MiniMessage)

```yaml
name: "<gradient:#FF0000:#0000FF>Rainbow Text</gradient>"
description:
  - "<gradient:#FFD700:#FFA500>Golden gradient description</gradient>"
```

### Mixing Formats

You can mix legacy codes with MiniMessage:

```yaml
name: "&l<gradient:#FF0000:#00FF00>Bold Gradient</gradient>"
```

---

## Permissions

| Permission | Description |
|------------|-------------|
| `armorsets.reload` | Reload plugin configurations |
| `armorsets.give` | Give items via commands |
| `armorsets.socket` | Socket functions into armor |

---

## Example: Complete Core Function

```yaml
berserker_rage:
  name: "&c&lBerserker's Rage"
  slot: CHESTPLATE
  tier: 2
  description:
    - "&7When health is low, gain"
    - "&7immense power at a cost."
    - ""
    - "&8Tier 2 Chestplate Function"

  on_defense:
    chance: 100
    cooldown: 30
    effects:
      # Only triggers below 30% HP (handled via condition in your logic)
      - "POTION:STRENGTH:10:2 @Self"
      - "POTION:SPEED:10:1 @Self"
      - "POTION:RESISTANCE:10:0 @Self"
      - "PARTICLE:ANGRY_VILLAGER:50 @Self"
      - "SOUND:ENTITY_RAVAGER_ROAR:1:0.8"
      - "MESSAGE:&c&lBERSERKER RAGE ACTIVATED!"
      - "TITLE:&4&lRAGE:&cUnleashed:10:40:10"
```

---

## Tips

1. **Testing**: Use `/as give function <player> <id>` to test functions
2. **Debugging**: Set `debug: true` in config.yml for detailed console output
3. **Balancing**: Start with low values and adjust based on gameplay
4. **Cooldowns**: Use cooldowns to prevent ability spam
5. **Chances**: Lower chances for powerful effects to maintain balance
6. **Sets**: Use name patterns that match your custom item names exactly

---

## Troubleshooting

**Functions not activating?**
- Check if the function is socketed (use `/as info`)
- Verify the trigger type matches your action
- Check cooldowns haven't expired
- Ensure `debug: true` to see activation logs

**Set bonuses not working?**
- Verify all 4 pieces match the `name_pattern`
- Check if `require-same-tier` is blocking mixed sets
- Use `/as info` on each piece to verify

**Colors not displaying?**
- Ensure proper format: `&c` for legacy, `&#RRGGBB` for hex
- MiniMessage gradients need opening and closing tags
- Don't mix `<` tags with `&` codes in the same segment

---

## Support

Report issues at the plugin repository or contact the developer.