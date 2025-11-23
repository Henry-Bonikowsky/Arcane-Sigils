# Minecraft Custom Armor/Weapon Plugin - Design Document
## Project Overview & Context

**Client:** Zenax
**Target Server Version:** 1.21.X
**Purpose:** Create a configurable plugin system for custom armor sets and weapons with hot-swappable "core functions"
**Business Model:** Client intends to resell the plugin after development

---

## Executive Summary

Based on analysis of 145 armor set configurations and 656 custom weapon configurations, this plugin requires building a sophisticated system that allows hot-swappable "core functions" (abilities) that can be attached/detached from armor pieces dynamically. The client needs the **SYSTEM**, not pre-built content - they will configure the specific abilities themselves.

### Key Distinction
**Zenax does NOT need:**
- All abilities pre-programmed
- Custom asset creation
- Enchantment system (using existing Advanced Enchantments plugin)

**Zenax DOES need:**
- A framework to create configurable "Core Functions"
- Hot-swap system (like MMO ability swapping)
- Ability to turn Core Functions into items (shards/gems)
- Configuration flexibility for ALL ability parameters

---

## Current Configuration Analysis

### File Inventory
- **Armor Sets:** 145 configuration files
- **Custom Weapons:** 656 configuration files
- **Total Items:** 801 unique item configurations

### Identified Armor Set Families (with tier progressions)
1. Arcanist (Tiers 1-5)
2. Azure (Tiers 1-5)
3. Beats (Tiers 1-5)
4. Damned (Tiers 1-5)
5. Darkwither (Tiers 1-5)
6. Discord (Tiers 1-5)
7. Fairy (Tiers 1-2)
8. Fiendskull (Tiers 1-4)
9. Inferno (Tiers 2-5)
10. Killburn (Tiers 1-5)
11. Lightning Power (Tiers 1-3)
12. Lunar Dragon (Tiers 2-4)
13. Shogun (Tiers 3-9) **- Highest tier system**
14. Valentine (Tiers 1-3)
15. Valentine 2025 (Tiers 1-5)
16. Uranium (Tiers 2-5)
17. And 15+ more families...

---

## Current System Architecture (What They're Using Now)

### Dependencies Identified
1. **ItemsAdder** - Custom items/models/textures
2. **Advanced Enchantments** - Custom enchantment system
3. **Unknown "Advanced Sets" plugin** - This appears to be what needs replacing/building

### Current Configuration Structure
```yaml
name: <gradient:#8A2BE2:#4169E1>Arcanist Set - Tier I</gradient>
material: NETHERITE

settings:
  equipped: [messages]
  unequipped: [messages]

items:
  helmet/chestplate/leggings/boots:
    name: <item name>
    itemsadder: <reference>
    lore: [flavor text with "Core Function" descriptions]
    enchants: [list of enchantments]

individual_effects:
  helmet/chestplate/leggings/boots:
    EFFECT_STATIC/DEFENSE/FALL_DAMAGE:
      chance: 100
      effects: [list of effects]
      cooldown: X

events:
  ATTACK/DEFENSE/KILL_MOB/KILL_PLAYER/SHIFT:
    chance: X
    effects: [complex effect chains]
    cooldown: X
```

---

## Plugin Requirements

### 1. Core Function System (PRIMARY REQUIREMENT)

**What It Is:**
"Core Functions" are the primary abilities/powers described in armor lore. Currently they're just flavor text - Zenax wants them to be actual, swappable, modular abilities.

**Example from Arcanist Set Tier I Helmet:**
```
[Core Function]: A conduit for cosmic insight, granting
clarity and piercing the veil of darkness.
```
**Current Effect:** Static night vision + water breathing
**Desired:** This "function" should be extractable and swappable

#### Requirements:
- [ ] Each armor piece can have ONE "Core Function" slotted
- [ ] Core Functions can be removed and turned into physical items (shards/gems/orbs/etc.)
- [ ] Core Functions have configurable power levels/tiers
- [ ] Core Functions can be swapped between armor pieces of the same type
- [ ] Configuration file for creating new Core Functions

#### Core Function Configuration Schema (PROPOSED):
```yaml
core_functions:
  cosmic_insight:
    id: cosmic_insight
    name: "<gradient:#8A2BE2:#4169E1>Cosmic Insight</gradient>"
    description: "A conduit for cosmic insight..."
    applicable_slot: HELMET
    tier: 1
    effects:
      EFFECT_STATIC:
        chance: 100
        effects:
          - POTION:NIGHT_VISION:1
          - POTION:WATER_BREATHING:1
        cooldown: 0
    item_form:
      material: ECHO_SHARD
      name: "Shard of Cosmic Insight"
      lore:
        - "Right-click on a helmet to socket this function"
```

### 2. Set Synergy System

**What It Is:**
When wearing a full set (4/4 pieces), unlock powerful set bonuses.

#### Current Implementation Examples:

**Arcanist Set Synergies (Tier I):**
1. Arcane Disintegration - Attacks may unravel enemy defenses
2. Aether Step - Taking damage triggers phase shift
3. Soul Siphon - Defeating foes replenishes vitality
4. Rift Walk - Shift to enter aether (invisibility)

**Shogun Set Synergies (Tier IX):**
1. Emperor's Edict - Will is law, erasing unworthy
2. Divine Right - Cannot be harmed

#### Requirements:
- [ ] Detect full set equipped
- [ ] Activate set-specific event handlers
- [ ] Different bonuses per tier (Tier I vs Tier V = different power levels)
- [ ] Configuration for set matching (by name pattern or ID)

#### Set Synergy Configuration Schema (PROPOSED):
```yaml
set_synergies:
  arcanist_t1:
    set_name_pattern: "Arcanist.*Tier I"
    required_pieces: 4
    synergies:
      arcane_disintegration:
        trigger: ATTACK
        chance: 30
        effects:
          - INCREASE_DAMAGE:30
          - DISINTEGRATE:1 @Victim
          - POTION:SLOW:4 @Victim
        cooldown: 3
      # ... more synergies
```

### 3. Event System

**Trigger Types Identified:**
1. `ATTACK` - When player attacks
2. `DEFENSE` - When player takes damage
3. `KILL_MOB` - When player kills a mob
4. `KILL_PLAYER` - When player kills another player
5. `SHIFT` - When player sneaks/shifts
6. `FALL_DAMAGE` - When player takes fall damage
7. `EFFECT_STATIC` - Permanent/passive effects while worn

**Effect Types Identified:**

#### Damage/Combat:
- `INCREASE_DAMAGE:X` - Boost attack damage by X%
- `DISINTEGRATE:X` - Armor shred effect
- `AEGIS:X` - Damage reduction

#### Movement:
- `TELEPORT_RANDOM:X` - Random teleport X blocks
- `SMOKEBOMB:X` - Create smoke cloud
- `CANCEL_EVENT` - Negate the triggering event

#### Healing/Buffs:
- `HEAL:X` - Restore X health
- `DEVOUR:X` - Lifesteal/absorption
- `REPLENISH:X` - Restore hunger

#### Potion Effects:
- `POTION:<TYPE>:<DURATION> @Target`
- Targets: `@Self`, `@Victim`, `@Nearby`

#### Visual/Audio:
- `PARTICLE:<TYPE>:<COUNT> @Target`
- `SOUND:<SOUND_TYPE> @Target`
- `MESSAGE:<text> @Target`

#### Spawning:
- `SPAWN_ENTITY:<TYPE>:<COUNT>:<ALLY?> @Target`
- Example: `SPAWN_ENTITY:IRON_GOLEM:1:true @Self` (Valentine Set's "Secret Admirer")

#### Requirements:
- [ ] Comprehensive effect handler for each effect type
- [ ] Target selector system (`@Self`, `@Victim`, `@Nearby:<radius>`)
- [ ] Percentage-based chance system
- [ ] Cooldown management per-player per-effect
- [ ] Effect stacking/chaining (one trigger can fire multiple effects)

### 4. Hot-Swap GUI System (NEW REQUIREMENT)

**What Zenax Wants:**
> "hot swappable or able to have there affect put into a hot key system like mmos or to be able to interchange effects between armor sets"

#### Requirements:
- [ ] GUI inventory for managing Core Functions
- [ ] Right-click armor piece → Opens socket GUI
- [ ] Drag-and-drop Core Function items to socket/unsocket
- [ ] Visual indication of socketed functions
- [ ] Hotkey system for activating socketed abilities (optional MMO-style)
- [ ] Restrictions: helmet functions only in helmets, etc.

#### GUI Mockup Concept:
```
[ Helmet Slot ] [ Current Function: Cosmic Insight ]
[ Empty Socket ] [ Drag function here to socket ]
[ Inventory: Shards/Gems ]
```

### 5. Tiered Progression System

**Pattern Identified:**
Sets have multiple tiers (1-9) with increasing power.

**Tier I Example (Arcanist):**
- ATTACK: 30% chance, +30% damage, 3s cooldown
- DEFENSE: 20% chance, teleport 8 blocks, 8s cooldown

**Tier V Example (Arcanist):**
- ATTACK: 75% chance, +200% damage, 1s cooldown
- DEFENSE: 50% chance, complete cancel + heal, 2s cooldown

#### Requirements:
- [ ] Power scaling configuration per tier
- [ ] Different effect magnitudes per tier
- [ ] Cooldown reduction at higher tiers
- [ ] Chance increase at higher tiers

### 6. Custom Weapons Integration

**656 weapon configurations** follow similar pattern:
- Require specific sets to activate (`requireSet: Phantom`)
- Have their own event triggers
- Simpler than armor (mostly ATTACK events)

#### Example (Phantom Scythe):
```yaml
requireSet: Phantom
material: DIAMOND_SWORD
events:
  ATTACK:
    chance: 100
    effects:
      - INCREASE_DAMAGE:10
    cooldown: 0
```

#### Requirements:
- [ ] Set requirement checking for weapons
- [ ] Weapon-specific events (bow shots, trident throws, etc.)
- [ ] Integration with armor set bonuses (weapon + full set = extra power)

### 7. Configuration Hot-Reload

#### Requirements:
- [ ] `/reload` or `/armorsets reload` command
- [ ] Hot-reload config files without server restart
- [ ] Validation and error checking on reload

### 8. Compatibility & Integration

#### Must Be Compatible With:
- **ItemsAdder** - Custom items/models/textures
- **Advanced Enchantments** - Custom enchantment integration
- **MiniMessage/Gradients** - For colored text in names/lore
- **Minecraft 1.21.X** - Latest version support

#### Should Support:
- PlaceholderAPI (for messages/lore)
- WorldGuard (region restrictions)
- Combat log plugins

---

## Technical Complexity Assessment

### HIGH COMPLEXITY SYSTEMS:

#### 1. Core Function Hot-Swap System (HARD)
**Why:**
- Dynamic NBT/PDC manipulation on items
- Item-to-function and function-to-item conversion
- State persistence across server restarts
- GUI drag-and-drop with validation

**Estimated Effort:** 15-20 hours

#### 2. Effect Handler System (MEDIUM-HARD)
**Why:**
- 30+ unique effect types to implement
- Target selection system
- Effect chaining/stacking
- Cooldown management per player

**Estimated Effort:** 20-25 hours

#### 3. Event Trigger System (MEDIUM)
**Why:**
- 7 different event types
- Percentage-based chance calculations
- Integration with Minecraft event system

**Estimated Effort:** 10-15 hours

### MEDIUM COMPLEXITY SYSTEMS:

#### 4. Set Detection & Synergy (MEDIUM)
**Why:**
- Pattern matching for set names
- Full set detection
- Tiered power scaling

**Estimated Effort:** 8-12 hours

#### 5. Configuration System (MEDIUM)
**Why:**
- Complex YAML parsing
- Validation and error handling
- Hot-reload system

**Estimated Effort:** 8-10 hours

### LOW-MEDIUM COMPLEXITY SYSTEMS:

#### 6. Weapon Integration (LOW-MEDIUM)
**Why:**
- Similar to armor but simpler
- Set requirement checking

**Estimated Effort:** 5-8 hours

#### 7. GUI System (MEDIUM)
**Why:**
- Inventory GUI framework
- Click handling
- Visual feedback

**Estimated Effort:** 10-12 hours

---

## Proposed Architecture

### Package Structure
```
com.zenax.armorsets/
├── ArmorSetsPlugin.java (Main)
├── config/
│   ├── ConfigManager.java
│   ├── CoreFunctionConfig.java
│   ├── SetConfig.java
│   └── WeaponConfig.java
├── core/
│   ├── CoreFunction.java
│   ├── CoreFunctionManager.java
│   └── SocketManager.java
├── sets/
│   ├── ArmorSet.java
│   ├── SetManager.java
│   └── SetSynergy.java
├── effects/
│   ├── Effect.java (interface)
│   ├── EffectManager.java
│   ├── effects/
│   │   ├── DamageEffect.java
│   │   ├── PotionEffect.java
│   │   ├── TeleportEffect.java
│   │   └── [30+ more effect types]
│   └── TargetSelector.java
├── events/
│   ├── TriggerType.java (enum)
│   ├── TriggerHandler.java
│   └── CooldownManager.java
├── weapons/
│   ├── CustomWeapon.java
│   └── WeaponManager.java
├── gui/
│   ├── SocketGUI.java
│   ├── CoreFunctionInventory.java
│   └── GUIClickHandler.java
├── items/
│   ├── CoreFunctionItem.java
│   └── ItemBuilder.java
└── utils/
    ├── NBTUtil.java
    ├── TargetUtil.java
    └── MessageUtil.java
```

### Data Storage
**PersistentDataContainer (PDC) for:**
- Socketed Core Functions on armor
- Player cooldown tracking
- Core Function item metadata

**Config Files:**
```
plugins/ArmorSets/
├── config.yml (global settings)
├── core-functions/
│   ├── helmet-functions.yml
│   ├── chestplate-functions.yml
│   ├── leggings-functions.yml
│   └── boots-functions.yml
├── sets/
│   ├── arcanist.yml
│   ├── shogun.yml
│   └── [143 more set configs]
├── weapons/
│   └── [656 weapon configs]
└── messages.yml
```

---

## Effect Types - Complete Implementation List

Based on analysis of all configuration files, here are ALL unique effects that need implementation:

### Damage Effects
1. `INCREASE_DAMAGE:X` - Increase attack damage by X%
2. `DISINTEGRATE:X` - Armor penetration/shred (X stacks/power)
3. `AEGIS:X` - Damage reduction shield
4. `WARD:X` - Magic resistance
5. `CANCEL_EVENT` - Completely negate damage/event

### Movement Effects
6. `TELEPORT_RANDOM:X` - Random teleport X blocks
7. `SMOKEBOMB:X` - Create smoke cloud (blindness)
8. `DODGE:X` - Evasion chance boost
9. `MOMENTUM:X` - Movement speed from combat
10. `WINGS:X` - Flight/elytra-like movement
11. `SPRINGS:X` - Jump boost
12. `GEARS:X` - Speed boost
13. `FEATHERWEIGHT:X` - Slow falling
14. `JELLYLEGS:X` - Fall damage reduction

### Healing/Sustain Effects
15. `HEAL:X` - Instant health restoration
16. `DEVOUR:X` - Lifesteal/absorption hearts
17. `REPLENISH:X` - Hunger restoration
18. `PATCH:X` - Healing over time
19. `RESTORE:X` - Health regeneration
20. `PHOENIX:X` - Totem-like resurrection
21. `ANGELIC:X` - Health boost
22. `IMMORTAL:X` - Prevents death

### Potion Effects
23. `POTION:<TYPE>:<DURATION> @Target` - Apply any MC potion effect
    - Supported: NIGHT_VISION, WATER_BREATHING, REGENERATION, STRENGTH, SPEED, JUMP, INVISIBILITY, ABSORPTION, DAMAGE_RESISTANCE, FIRE_RESISTANCE, SLOW, CONFUSION, WITHER, etc.

### Utility Effects
24. `LIFESTEAL:X` - Steal health on hit
25. `SOULBOUND:X` - Keep on death
26. `UNBREAKABLE:X` - Durability protection
27. `AQUA:X` - Water breathing/movement
28. `NIGHTOWL:X` - Night vision
29. `LUCID:X` - Mental clarity (likely remove negative effects)
30. `INQUISITIVE:X` - Experience gain boost
31. `ENLIGHTENED:X` - Knowledge/XP effects
32. `IMPLANTS:X` - Unknown (needs clarification)
33. `GUARDIANS:X` - Summon protection
34. `ALLURE:X` - Charm/attraction effect

### Offensive Enchantments (Advanced Enchantments Plugin)
35. Custom enchants used: sharpness, unbreaking, etc. (handled by external plugin)

### Visual/Audio Effects
36. `PARTICLE:<TYPE>:<COUNT> @Target` - Spawn particles
    - Types: SOUL_FIRE_FLAME, SOUL, PORTAL, REVERSE_PORTAL, HEART, etc.
37. `SOUND:<SOUND> @Target` - Play sound effect
    - Types: ENTITY_ILLUSIONER_CAST_SPELL, ENTITY_WITHER_DEATH, ENTITY_ENDERMAN_TELEPORT, etc.
38. `MESSAGE:<text> @Target` - Send message to player

### Spawning Effects
39. `SPAWN_ENTITY:<TYPE>:<COUNT>:<ALLY?> @Target` - Summon entities
    - Types: IRON_GOLEM, EXPERIENCE_ORB, etc.
    - Ally flag determines if entity is friendly

### Special Effects (Need Investigation)
40. `RUSH:X` - Combat movement boost
41. `ALLURE:X` - Unknown effect (charm?)
42. **Bleeding** - Mentioned in conversation, not in configs
43. **Freezing** - Mentioned in conversation (similar to MC powder snow effect)
44. **Blinking** - Mentioned in conversation (short teleport)
45. **X-Ray/Ore Vision** - Mentioned in conversation

---

## Configuration Examples for New System

### Core Function Configuration
```yaml
# plugins/ArmorSets/core-functions/helmet-functions.yml

cosmic_insight:
  name: "<gradient:#8A2BE2:#4169E1>Cosmic Insight</gradient>"
  description:
    - "A conduit for cosmic insight, granting"
    - "clarity and piercing the veil of darkness."
  slot: HELMET
  tier: 1
  effects:
    EFFECT_STATIC:
      chance: 100
      effects:
        - POTION:NIGHT_VISION:1
        - POTION:WATER_BREATHING:1
      cooldown: 0
  item_form:
    material: ECHO_SHARD
    model_data: 100
    name: "<gradient:#8A2BE2:#4169E1>Shard: Cosmic Insight</gradient>"
    lore:
      - "&7Tier I Helmet Function"
      - ""
      - "&fRight-click a helmet to socket"
    glow: true

absolute_knowing:
  name: "<gradient:#4B0082:#FF00FF>Absolute Knowing</gradient>"
  description:
    - "There is no thought."
    - "There is only knowing."
  slot: HELMET
  tier: 5
  effects:
    EFFECT_STATIC:
      chance: 100
      effects:
        - POTION:NIGHT_VISION:1
        - POTION:WATER_BREATHING:1
        - POTION:REGENERATION:3
        - POTION:SATURATION:1
      cooldown: 0
  item_form:
    material: ECHO_SHARD
    model_data: 105
    name: "<gradient:#4B0082:#FF00FF>Shard: Absolute Knowing</gradient>"
    lore:
      - "&5Tier V Helmet Function"
      - ""
      - "&fRight-click a helmet to socket"
    glow: true
    enchant_glow: true
```

### Set Configuration
```yaml
# plugins/ArmorSets/sets/arcanist.yml

arcanist:
  tiers:
    1:
      name_pattern: "Arcanist.*Tier I"
      material: NETHERITE
      equipped_message:
        - "&d&l(!) &dYou don the Arcanist Set."
        - "&5✨ &7Whispers from the aether fill your mind..."
      unequipped_message:
        - "&c&l(!) &cThe Arcanist Set has been removed."
        - "&7The cosmic connection fades into silence..."

      # Individual piece effects (before set synergy)
      individual_effects:
        helmet:
          EFFECT_STATIC:
            chance: 100
            effects:
              - POTION:NIGHT_VISION:1
              - POTION:WATER_BREATHING:1
            cooldown: 0
        chestplate:
          DEFENSE:
            chance: 15
            effects:
              - AEGIS:3
              - PARTICLE:PORTAL:30 @Self
            cooldown: 10
        leggings:
          EFFECT_STATIC:
            chance: 100
            effects:
              - POTION:SPEED:1
            cooldown: 0
        boots:
          FALL_DAMAGE:
            chance: 100
            effects:
              - CANCEL_EVENT
            cooldown: 0

      # Set synergies (when wearing 4/4 pieces)
      synergies:
        arcane_disintegration:
          trigger: ATTACK
          chance: 30
          effects:
            - INCREASE_DAMAGE:30
            - DISINTEGRATE:1 @Victim
            - POTION:SLOW:4 @Victim
            - PARTICLE:SOUL_FIRE_FLAME:25 @Victim
            - SOUND:ENTITY_ILLUSIONER_CAST_SPELL @Victim
          cooldown: 3

        aether_step:
          trigger: DEFENSE
          chance: 20
          effects:
            - CANCEL_EVENT
            - SMOKEBOMB:1 @Self
            - TELEPORT_RANDOM:8 @Self
            - MESSAGE:&d&oYou phase through the aether... @Self
          cooldown: 8

        soul_siphon:
          trigger: KILL_MOB
          chance: 100
          effects:
            - HEAL:4
            - DEVOUR:10
            - POTION:REGENERATION:4 @Self
            - PARTICLE:SOUL:40 @Victim
          cooldown: 2

        soul_consumption:
          trigger: KILL_PLAYER
          chance: 100
          effects:
            - HEAL:15
            - DEVOUR:20
            - POTION:STRENGTH:10 @Self
            - POTION:REGENERATION:8 @Self
            - SPAWN_ENTITY:EXPERIENCE_ORB:10 @Self
            - MESSAGE:&5&lYour arcane mastery is absolute... @Self
          cooldown: 10

        rift_walk:
          trigger: SHIFT
          chance: 100
          effects:
            - POTION:INVISIBILITY:4 @Self
            - POTION:SPEED:2 @Self
            - REPLENISH:5
            - PARTICLE:REVERSE_PORTAL:30 @Self
            - SOUND:ENTITY_ENDERMAN_TELEPORT @Self
          cooldown: 12

    5:
      name_pattern: "Arcanist.*Tier V"
      material: NETHERITE
      equipped_message:
        - "&d&l(!) &dYou are the Arcane."
        - "&5✨ &7Existence is your plaything."
      unequipped_message:
        - "&c&l(!) &cYou choose to forget."
        - "&7The universe sighs in relief."

      individual_effects:
        helmet:
          EFFECT_STATIC:
            chance: 100
            effects:
              - POTION:NIGHT_VISION:1
              - POTION:WATER_BREATHING:1
              - POTION:REGENERATION:3
              - POTION:SATURATION:1
            cooldown: 0
        chestplate:
          EFFECT_STATIC:
            chance: 100
            effects:
              - POTION:DAMAGE_RESISTANCE:2
              - POTION:FIRE_RESISTANCE:1
            cooldown: 0
        leggings:
          EFFECT_STATIC:
            chance: 100
            effects:
              - POTION:SPEED:4
              - POTION:JUMP:3
            cooldown: 0
        boots:
          FALL_DAMAGE:
            chance: 100
            effects:
              - CANCEL_EVENT
            cooldown: 0

      synergies:
        unmaking:
          trigger: ATTACK
          chance: 75
          effects:
            - INCREASE_DAMAGE:200
            - DISINTEGRATE:10 @Victim
            - POTION:WITHER:10 @Victim
            - POTION:SLOW:10 @Victim
            - SOUND:ENTITY_WITHER_DEATH @Victim
          cooldown: 1

        causality_failure:
          trigger: DEFENSE
          chance: 50
          effects:
            - CANCEL_EVENT
            - HEAL:20
            - MESSAGE:&d&oThe attack never happened. @Self
          cooldown: 2

        apotheosis:
          trigger: KILL_PLAYER
          chance: 100
          effects:
            - HEAL:20
            - DEVOUR:20
            - POTION:STRENGTH:30 @Self
            - POTION:REGENERATION:20 @Self
            - POTION:ABSORPTION:20 @Self
            - SPAWN_ENTITY:EXPERIENCE_ORB:100 @Self
            - MESSAGE:&5&lThey are unmade. @Self
          cooldown: 3

        phase_shift:
          trigger: SHIFT
          chance: 100
          effects:
            - POTION:INVISIBILITY:10 @Self
            - POTION:SPEED:7 @Self
            - REPLENISH:20
            - HEAL:5
          cooldown: 4
```

---

## User Stories / Use Cases

### Use Case 1: Creating a New Core Function
**Actor:** Server Admin (Zenax)
**Steps:**
1. Admin opens `core-functions/helmet-functions.yml`
2. Copies existing function template
3. Modifies: name, description, effects, tier, item appearance
4. Runs `/armorsets reload`
5. Uses `/armorsets give <player> function:cosmic_insight` to get the function item
6. Right-clicks helmet with function item to socket it

### Use Case 2: Swapping Core Functions
**Actor:** Player
**Steps:**
1. Player has socketed helmet with "Cosmic Insight"
2. Player holds new function shard "Absolute Knowing"
3. Player right-clicks helmet
4. GUI opens showing current function and socket options
5. Player drags "Absolute Knowing" into socket
6. "Cosmic Insight" pops out as item in inventory
7. Helmet now has new function

### Use Case 3: Set Synergy Activation
**Actor:** Player
**Steps:**
1. Player equips 4/4 Arcanist Tier I pieces
2. Message appears: "You don the Arcanist Set"
3. Player attacks enemy
4. 30% chance triggers "Arcane Disintegration"
5. Effects apply: +30% damage, armor shred, slow, particles
6. 3-second cooldown starts
7. Player cannot trigger this synergy again for 3 seconds

### Use Case 4: Tier Progression
**Actor:** Player
**Steps:**
1. Player starts with Arcanist Tier I set
2. ATTACK synergy: 30% chance, +30% damage, 3s cooldown
3. Player upgrades to Arcanist Tier V set
4. ATTACK synergy: 75% chance, +200% damage, 1s cooldown
5. Same synergy name, drastically more powerful

---

## Questions Requiring Clarification

### Critical Questions for Zenax:

1. **Core Function Swapping Rules:**
   - Can Core Functions be swapped between different armor sets?
   - Can a Tier V function go on a Tier I armor piece?
   - Should there be tier restrictions (T5 function requires T5+ armor)?

2. **Core Function Acquisition:**
   - How do players obtain function shards? (Drops, crafting, shop, admin-give only?)
   - Can functions be traded between players?
   - Should functions be consumable or permanent items?

3. **Set Detection:**
   - Must all 4 pieces be from same tier? (All Tier I, or can mix Tier I helmet + Tier V chest?)
   - Should mixing tiers give partial bonuses or require exact match?

4. **Effects Needing Clarification:**
   - **Freezing effect** - How should this work mechanically?
     - Slowness + mining fatigue?
     - Visual overlay (frost on screen)?
     - Increased damage taken while frozen?
   - **Bleeding effect** - How should this work?
     - Damage over time?
     - Visual particles (redstone dust)?
   - **Blinking** - Short-distance teleport or full invisibility?
   - **Ore Vision/X-Ray** - Show ores through walls? How far? Which ores?

5. **Hotkey System:**
   - Should Core Functions be activatable on keybind (like MMO abilities)?
   - Or purely passive/automatic on events?
   - If hotkeys: How many slots? (1-9 like MMO action bar?)

6. **GUI Details:**
   - Should there be a centralized "function storage" inventory?
   - Or functions only exist as physical items?
   - Can players preview function effects before socketing?

7. **Weapon Integration:**
   - Should weapons also have socketable functions?
   - Or only check for set requirements?

8. **Economy/Permissions:**
   - Should function extraction cost money/materials?
   - Permission nodes for using certain tiers?
   - Admin-only functions?

---

## Development Phases (Proposed)

### Phase 1: Core Framework (Week 1)
- [ ] Plugin skeleton & config system
- [ ] Event system (7 trigger types)
- [ ] Basic effect handlers (10 most common effects)
- [ ] Set detection system
- [ ] Cooldown management

**Deliverable:** Basic set bonuses working without hot-swap

### Phase 2: Effect System Completion (Week 1-2)
- [ ] Implement all 45+ effect types
- [ ] Target selection system
- [ ] Effect chaining
- [ ] Particle & sound effects

**Deliverable:** All effects from config files functional

### Phase 3: Core Function System (Week 2-3)
- [ ] Core Function data structure
- [ ] NBT/PDC storage on armor
- [ ] Function-to-item conversion
- [ ] Socket/unsocket mechanics

**Deliverable:** Functions can be socketed/extracted

### Phase 4: GUI System (Week 3)
- [ ] Socket GUI interface
- [ ] Drag-and-drop functionality
- [ ] Visual feedback
- [ ] Function preview

**Deliverable:** GUI for managing functions

### Phase 5: Weapon Integration (Week 3-4)
- [ ] Weapon event handlers
- [ ] Set requirement checking
- [ ] Weapon-specific effects

**Deliverable:** 656 weapon configs functional

### Phase 6: Polish & Testing (Week 4)
- [ ] Performance optimization
- [ ] Edge case handling
- [ ] Extensive testing
- [ ] Documentation for Zenax

**Deliverable:** Production-ready plugin

---

## Estimated Effort & Pricing

### Time Breakdown:
| Component                   | Hours | Complexity |
|-----------                  |-------|------------|
| Core Framework              | 15    | Medium |
| Effect System (45+ effects) | 25    | High |
| Event & Trigger System      | 12    | Medium |
| Set Detection & Synergies   | 10    | Medium |
| Core Function System        | 20    | High |
| GUI & Hot-Swap              | 12    | Medium |
| Weapon Integration          | 8     | Low-Med |
| Config System & Reload      | 10    | Medium |
| Testing & Polish            | 15    | - |
| Documentation               | 5     | - |
| **TOTAL** | **132 hours** | - |

### Pricing Models:

#### Option 1: Standard Timeline (30 days)
**Rate:** $15/hour
**Total:** $1,980

#### Option 2: Rushed Timeline (14 days)
**Rate:** $20/hour (rush premium)
**Total:** $2,640


**Notes:**
- These are estimates based on a senior developer with 10+ years experience
- Actual hours may vary based on clarifications needed
- Additional features (hotkey system, ore vision, etc.) would add 10-20 hours
- Does NOT include custom asset creation (using existing MC assets)
- Does NOT include enchantment system (using Advanced Enchantments)

---

## Risks & Assumptions

### Risks:
1. **Scope Creep** - "Freezing," "Bleeding," "Ore Vision" not in configs yet
2. **Performance** - 801 items checking events on every attack could lag
3. **Compatibility** - ItemsAdder API changes between versions
4. **Ambiguity** - Many effects (IMPLANTS, RUSH) need clarification

### Assumptions:
1. Zenax will provide complete Core Function specifications
2. No custom asset creation needed
3. Advanced Enchantments plugin handles all enchant logic
4. Using existing MC particles/sounds/effects only
5. Server is well-configured (adequate RAM, CPU for complex calculations)

### Mitigation Strategies:
1. **Clear Spec Document** - Get Zenax to fill in missing details
2. **Performance Optimization** - Cache set calculations, optimize event handlers
3. **Flexible Architecture** - Build modular system that can adapt to changes
4. **Iterative Development** - Deliver in phases, get feedback early

---

## Success Criteria

Plugin is considered complete when:

- [ ] All 45+ effect types implemented and tested
- [ ] All 7 trigger types functional
- [ ] Set detection working for all 145+ sets
- [ ] Core Functions can be socketed/unsocketed via GUI
- [ ] All 656 weapon configs load and function
- [ ] Hot-reload works without errors
- [ ] Zero critical bugs (no crashes, dupes, exploits)
- [ ] Performance: <5ms per event trigger
- [ ] Zenax can create new Core Functions via config alone
- [ ] Complete documentation provided

---

## Next Steps

### For Zenax:
1. **Review this document** - Confirm understanding is correct
2. **Answer clarification questions** (Section 9)
3. **Provide missing effect specifications** (Freezing, Bleeding, Ore Vision, etc.)
4. **Decide on tier** restrictions and swapping rules
5. **Confirm budget and timeline**

### For Gnuoy:
1. **Review technical feasibility** - Any concerns with architecture?
2. **Confirm pricing** - Does this align with expected scope?
3. **Clarify unknowns** - Flag any missing information
4. **Provide final quote** - Based on chosen timeline

### For Both:
1. **Formal specification** - Convert this to agreed-upon contract
2. **Milestone definition** - What constitutes each phase completion?
3. **Payment schedule** - Deposits, milestone payments, final delivery
4. **Communication plan** - Discord, Github, how to track progress?

---

## Appendix A: Complete Effect List

See **Section 6: Effect Types - Complete Implementation List**

## Appendix B: Configuration File Samples

See **Section 8: Configuration Examples for New System**

## Appendix C: Armor Set Family List

See **Section 2.3: Identified Armor Set Families**

---

**Document Version:** 1.0
**Date:** 2025-11-21
**Author:** Claude (via Crehop's request)
**Purpose:** Provide Gnuoy with comprehensive understanding of plugin scope for accurate quoting

---

**END OF DOCUMENT**
