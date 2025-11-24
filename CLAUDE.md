# ArmorSets Plugin - Claude.md

## Project Overview

**ArmorSets** (also known as Custom ArmorWeapon Plugin) is a Minecraft Paper/Spigot plugin that implements a sophisticated armor set bonus system with hot-swappable "Sigils" (modular enchantment-like abilities). The plugin enables server administrators to create custom armor sets with unique effects triggered by various in-game events.

### Core Concepts

- **Armor Sets**: Collections of armor pieces that provide bonuses when worn together
- **Sigils**: Modular abilities that can be socketed into armor pieces, providing effects based on triggers
- **Custom Weapons**: Weapons that can have set requirements and unique triggered effects
- **Effects**: Over 40 different effect types (damage, healing, movement, utility, visual)
- **Triggers**: Event-based activation (attack, defense, kill, sneak, etc.)

### Technology Stack

- **Platform**: Paper/Spigot 1.21.1+
- **Language**: Java 21
- **Build System**: Maven 3.x
- **Dependencies**:
  - Paper API 1.21.1-R0.1-SNAPSHOT
  - PlaceholderAPI 2.11.6 (optional)
  - Adventure MiniMessage 4.17.0
- **Soft Dependencies**: ItemsAdder, PlaceholderAPI, WorldGuard

---

## Directory Structure

```
Custom ArmorWeapon Plugin/
├── .claude/                          # Claude Code configuration
│   ├── agents/                       # Custom agent configurations
│   │   └── sigil-system-architect.md
│   └── settings.local.json
│
├── .git/                             # Git repository
├── .idea/                            # IntelliJ IDEA project files
│
├── docs/                             # Documentation
│   ├── INDEX.md                      # Documentation index
│   ├── ADMIN_GUIDE.md               # Server administrator manual
│   ├── BUILDING.md                  # Build instructions
│   ├── DEVELOPER_GUIDE.md           # Developer guide
│   └── examples/                    # Example configuration files
│       ├── armor-set-basic.yml
│       ├── armor-set-advanced.yml
│       ├── core-function-basic.yml
│       └── config-recommended.yml
│
├── src/
│   └── main/
│       ├── java/com/zenax/armorsets/  # Source code
│       │   ├── ArmorSetsPlugin.java   # Main plugin entry point
│       │   ├── commands/              # Command handlers
│       │   ├── config/                # Configuration management
│       │   ├── constants/             # Magic number constants
│       │   ├── core/                  # Core data models (Sigil, SocketManager)
│       │   ├── effects/               # Effect system
│       │   │   ├── impl/              # 40+ effect implementations
│       │   │   └── [interfaces]
│       │   ├── events/                # Event handling and triggers
│       │   ├── gui/                   # GUI management (GUIManager, GUISession, GUIType)
│       │   ├── menu/                  # UI menu components (MenuItem, MenuState, BuildMainMenuComponent)
│       │   ├── sets/                  # Armor set system
│       │   ├── utils/                 # Utility classes (TextUtil, ColorUtil, RomanNumerals, LogHelper)
│       │   └── weapons/               # Custom weapon system
│       │
│       └── resources/                 # Plugin resources
│           ├── plugin.yml             # Plugin metadata
│           ├── config.yml             # Default configuration
│           ├── messages.yml           # Configurable messages
│           ├── sets/                  # Armor set definitions
│           ├── sigils/                # Sigil definitions
│           └── weapons/               # Weapon definitions
│
├── target/                            # Maven build output
├── pom.xml                            # Maven build configuration
├── ArmorSets.iml                      # IntelliJ module file
├── REORGANIZATION_PLAN.md             # Planned structural improvements
└── codebase-roast.md                  # Code review notes
```

---

## Key Components

### Entry Point

**`ArmorSetsPlugin.java`** - Main plugin class extending JavaPlugin
- Initializes all managers in dependency order
- Registers commands and event listeners
- Provides getInstance() singleton access
- Handles plugin reload functionality

### Manager Architecture

The plugin uses a manager pattern where each subsystem has a dedicated manager:

| Manager | Responsibility |
|---------|---------------|
| `ConfigManager` | Load/save YAML configuration files |
| `EffectManager` | Registry and execution of all 40+ effects |
| `CooldownManager` | Per-player cooldown tracking |
| `SigilManager` | Load and manage sigil definitions |
| `SocketManager` | Socket/unsocket sigil operations |
| `SetManager` | Armor set detection and management |
| `WeaponManager` | Custom weapon management |
| `TriggerHandler` | Listen for Bukkit events and trigger effects |
| `GUIManager` | Handle all inventory GUI operations |

### Effect System

**Location**: `src/main/java/com/zenax/armorsets/effects/`

- **`Effect.java`** - Interface defining effect contract
- **`EffectContext.java`** - Context object passed to effects (player, trigger, victim, etc.)
- **`EffectParams.java`** - Parsed parameters from effect strings
- **`EffectManager.java`** - Effect registry and executor
- **`impl/AbstractEffect.java`** - Base class with common functionality
- **`impl/*.java`** - 40+ concrete effect implementations

**Effect Categories**:
- **Combat**: DealDamage, DamageBoost, Aegis, Ward, Lifesteal, Bleeding, Freezing
- **Movement**: Teleport, Dodge, Blink, Smokebomb, Wings, Springs, Momentum
- **Healing**: Heal, Devour, Replenish, Patch, Restore, Phoenix, Angelic, Immortal
- **Utility**: PotionEffect, Soulbound, Unbreakable, Aqua, Nightowl, Guardians
- **Visual**: Particle, Sound, Message, SpawnEntity

### Trigger System

**Location**: `src/main/java/com/zenax/armorsets/events/`

**`TriggerType.java`** defines all available triggers:
- `ATTACK` - When attacking an entity
- `DEFENSE` - When taking damage
- `KILL_MOB` - When killing a mob
- `KILL_PLAYER` - When killing another player
- `SHIFT` - When sneaking
- `FALL_DAMAGE` - When taking fall damage
- `EFFECT_STATIC` - Passive always-on effects
- `BOW_SHOOT`, `BOW_HIT` - Bow events
- `BLOCK_BREAK`, `BLOCK_PLACE` - Block events
- `INTERACT` - Right-click interaction
- `TICK` - Periodic effects

### Data Models

**Sigil** (`core/Sigil.java`):
- ID, name, description
- Slot restriction (HELMET, CHESTPLATE, LEGGINGS, BOOTS)
- Tier and max tier
- Rarity (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY)
- Effects mapped to trigger types
- Item form for extracted sigils

**ArmorSet** (`sets/ArmorSet.java`):
- ID and name pattern for detection
- Tier system
- Material type
- Equipped/unequipped messages
- Set synergies (full set bonuses)

**CustomWeapon** (`weapons/CustomWeapon.java`):
- ID and display name
- Required armor set
- Material type
- Event-triggered effects

---

## File Naming Conventions

### Java Files
- **Classes**: PascalCase (`ArmorSetsPlugin.java`, `EffectManager.java`)
- **Interfaces**: PascalCase (`Effect.java`)
- **Enums**: PascalCase (`TriggerType.java`, `GUIType.java`)
- **Constants files**: PascalCase with "Constants" suffix (`GUIConstants.java`)

### Configuration Files (YAML)
- **kebab-case**: `config.yml`, `messages.yml`, `plugin.yml`
- **Sigil files**: `{slot}-sigils.yml` (e.g., `helmet-sigils.yml`)
- **Example files**: `{type}-examples.yml` (e.g., `trigger-examples.yml`)

### Package Naming
- All lowercase: `com.zenax.armorsets.effects.impl`
- Domain-based organization

### Code Style
- Constants: `UPPER_SNAKE_CASE`
- Methods: `camelCase`
- Variables: `camelCase`
- Private fields: No prefix (pure `camelCase`)

---

## Configuration Format

### Effect String Format
```
EFFECT_TYPE:PARAM1:PARAM2:PARAM3 @Target
```

**Examples**:
- `POTION:SPEED:10:2` - Speed II for 10 seconds
- `DEAL_DAMAGE:5 @Victim` - Deal 5 damage to victim
- `PARTICLE:SOUL:20 @Self` - Spawn 20 soul particles on self
- `HEAL:4` - Heal 4 health (2 hearts)

### Target Selectors
- `@Self` - The player triggering the effect
- `@Victim` - The target of an attack
- `@Nearby:X` - All entities within X blocks

### Sigil Configuration Structure
```yaml
sigil_id:
  name: Display Name
  slot: HELMET|CHESTPLATE|LEGGINGS|BOOTS
  rarity: COMMON|UNCOMMON|RARE|EPIC|LEGENDARY
  max_tier: 10
  description:
    - "Line 1"
    - "Line 2"
  effect_static:           # Passive effects
    effects:
      - "EFFECT_STRING"
  on_attack:               # Attack trigger
    trigger_mode: CHANCE
    base_chance: 30
    cooldown: 5
    effects:
      - "EFFECT_STRING"
```

### Armor Set Configuration Structure
```yaml
set_id:
  name_pattern: "Regex Pattern"
  material: NETHERITE
  max_tier: 10
  equipped_message:
    - "Message when equipped"
  unequipped_message:
    - "Message when removed"
  synergies:
    synergy_id:
      on_attack:
        trigger_mode: CHANCE
        base_chance: 25
        effects:
          - "EFFECT_STRING"
```

---

## Organizational Issues and Recommendations

### Resolved Issues (Completed)

1. **[RESOLVED] Misplaced Files**
   - `ColorUtil.java` moved from `components/` to `utils/ColorUtil.java`

2. **[RESOLVED] Ambiguous Package Name**
   - `components/` package renamed to `menu/`
   - Now contains: `MenuItem.java`, `MenuState.java`, `BuildMainMenuComponent.java`

### Remaining Critical Issues

1. **GUIManager Monolith** (`gui/GUIManager.java`)
   - Currently 2000+ lines handling 24+ GUI types
   - Recommendation: Decompose into separate handler classes per GUI type
   - See `REORGANIZATION_PLAN.md` for detailed migration plan

### Moderate Issues

2. **Flat Effect Structure**
   - 40+ effect files in a single `effects/impl/` directory
   - Recommendation: Categorize into subpackages (combat/, movement/, healing/, utility/, visual/)

3. **Magic Numbers**
   - Task intervals (5L, 20L), click cooldowns (250ms), slot positions scattered throughout
   - Recommendation: Centralize in `constants/` package (partially done)

4. **Inconsistent Logging**
   - Mix of `System.err.println()` and proper logging
   - Recommendation: Use consistent logging via plugin logger

### Minor Issues

5. **Duplicate Code**
   - Roman numeral conversion duplicated in multiple files
   - Item builder patterns repeated
   - Recommendation: Extract to utility classes (RomanNumerals.java exists, ensure usage)

8. **Import Organization**
   - After reorganization, EffectManager will need updated imports for categorized effects

---

## Quick Start Guide

### For Developers

1. **Prerequisites**
   ```bash
   # Install Java 21 and Maven
   java -version  # Should show 21+
   mvn -version   # Should show 3.x+
   ```

2. **Clone and Build**
   ```bash
   cd "C:/Users/henry/Programs(self)/Custom ArmorWeapon Plugin"
   mvn clean package -DskipTests
   ```

3. **Output Location**
   ```
   target/ArmorSets-1.0.0.jar
   ```

4. **Development Workflow**
   - Make changes to source files
   - Run `mvn compile` to check for errors
   - Run `mvn package` to build JAR
   - Copy JAR to server's `plugins/` folder
   - Use `/as reload` for hot-reload (where possible)

### For Server Administrators

1. **Installation**
   - Place `ArmorSets-1.0.0.jar` in `plugins/` folder
   - Restart server
   - Plugin creates `plugins/ArmorSets/` with default configs

2. **Configuration**
   - Edit `plugins/ArmorSets/config.yml` for general settings
   - Add armor sets in `plugins/ArmorSets/sets/`
   - Add sigils in `plugins/ArmorSets/sigils/`
   - Add weapons in `plugins/ArmorSets/weapons/`

3. **Commands**
   - `/armorsets reload` - Reload all configurations
   - `/armorsets give <player> <item>` - Give items
   - `/armorsets socket` - Open socket GUI

4. **Permissions**
   - `armorsets.admin` - Full admin access
   - `armorsets.reload` - Reload permission
   - `armorsets.give` - Give items permission
   - `armorsets.socket` - Socket/unsocket sigils
   - `armorsets.use` - Use armor set effects

### Creating a New Effect

1. Create class extending `AbstractEffect`:
   ```java
   package com.zenax.armorsets.effects.impl;

   public class MyEffect extends AbstractEffect {
       public MyEffect() {
           super("MY_EFFECT", "Description of effect");
       }

       @Override
       public boolean execute(EffectContext context) {
           // Implementation
           return true;
       }
   }
   ```

2. Register in `EffectManager.registerDefaultEffects()`:
   ```java
   registerEffect(new MyEffect());
   ```

3. Use in YAML:
   ```yaml
   effects:
     - "MY_EFFECT:param1:param2"
   ```

---

## Architecture Overview

```
                    ┌─────────────────┐
                    │ ArmorSetsPlugin │
                    │  (Entry Point)  │
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│ ConfigManager │   │ TriggerHandler│   │  GUIManager   │
│  (YAML I/O)   │   │   (Events)    │   │  (Inventory)  │
└───────┬───────┘   └───────┬───────┘   └───────────────┘
        │                   │
        ▼                   ▼
┌───────────────┐   ┌───────────────┐
│  SetManager   │   │ EffectManager │◄──── 40+ Effects
│ SigilManager  │   │  (Execution)  │
│ WeaponManager │   └───────┬───────┘
└───────────────┘           │
                            ▼
                   ┌───────────────┐
                   │CooldownManager│
                   │  (Tracking)   │
                   └───────────────┘
```

### Effect Execution Flow

```
1. Bukkit Event Fires (e.g., EntityDamageByEntityEvent)
           │
2. TriggerHandler detects relevant event
           │
3. Check player's equipped armor set and sigils
           │
4. Find matching trigger configs (on_attack, on_defense, etc.)
           │
5. For each trigger:
   ├── Check cooldown (CooldownManager)
   ├── Roll chance (trigger_mode: CHANCE)
   ├── Parse effect strings
   └── Call EffectManager.executeEffects()
           │
6. EffectManager:
   ├── Parse effect string
   ├── Look up Effect implementation
   ├── Create EffectContext
   └── Call Effect.execute(context)
           │
7. Effect applies to player/victim/area
```

---

## Testing

```bash
# Compile only (fast check)
mvn compile

# Full build with JAR
mvn clean package -DskipTests

# Run tests (when available)
mvn test
```

### Manual Testing Checklist

- [ ] Plugin loads without errors
- [ ] `/as reload` completes successfully
- [ ] Armor sets detected when equipped
- [ ] Sigils can be socketed/unsocketed
- [ ] Effects trigger on correct events
- [ ] Cooldowns work correctly
- [ ] GUI navigation functions properly
- [ ] Custom weapons work with set requirements

---

## Related Documentation

- `docs/INDEX.md` - Documentation index
- `docs/ADMIN_GUIDE.md` - Server administrator guide
- `docs/DEVELOPER_GUIDE.md` - Developer documentation
- `docs/BUILDING.md` - Build instructions
- `REORGANIZATION_PLAN.md` - Planned structural improvements

---

## Version Information

- **Plugin Version**: 1.0.0
- **API Version**: Paper 1.21
- **Java Version**: 21
- **Last Updated**: November 2024
- Don't try compiling, just code and ill compile