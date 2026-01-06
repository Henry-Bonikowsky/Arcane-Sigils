# Arcane Sigils

A production-grade Minecraft plugin featuring a sophisticated magical ability system with procedural dungeon generation, boss AI, and flow-based programming for ability creation.

<img src="https://img.shields.io/badge/Minecraft-1.21-brightgreen" alt="MC 1.21"/> <img src="https://img.shields.io/badge/Paper-API-blue" alt="Paper"/> <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21"/> <img src="https://img.shields.io/badge/Version-1.0.498+-red" alt="Version"/>

## Overview

Arcane Sigils is a comprehensive magical ability system where players can socket powerful abilities ("sigils") into their armor and weapons. With 44 unique effect types, a tier progression system, and visual polish through custom particles and resource packs, it provides a complete RPG-style ability framework for Minecraft servers.

## Key Features

### 🔮 Sigil System
- **44 unique effects**: Dash, Grapple, Lightning, Phoenix, Teleport, Explosion, Healing, Lifesteal, and more
- **Tier progression**: Sigils level up with XP, unlocking scaling parameters and enhanced power
- **Socket system**: Attach sigils to armor/weapons with customizable slot counts
- **Signal-based activation**: ON_ATTACK, ON_DEFEND, ON_KILL, ON_DEATH, PASSIVE, and more
- **Conditional logic**: Activate based on health %, biome, time of day, weather, etc.

### ⚡ Flow-Based Programming
- Visual node graph system for creating complex ability combinations
- Non-programmer friendly ability design
- YAML-based configuration for easy content creation

### 🏰 Procedural Dungeon System
- **BSP algorithm** for room/corridor generation
- **Multi-phase boss battles** with custom AI (Soul Bolt, Blink, Death Grip, Summon Minions)
- **Party system** for multiplayer coordination
- **Loot management** with weighted rarity and tier-based rewards
- **Ambient audio** with music and sound effects

### 🎨 Visual Polish
- **Custom particle engine**: 3D shapes, presets, layers, modifiers
- **Resource pack integration**: Custom fonts and visual effects
- **Aura system**: Persistent area-of-effect visuals

### ⚙️ Technical Features
- **1.8 PvP combat** backported to modern Minecraft
- **Addon system** with custom class loading for extensibility
- **Behavior AI system** for mob control (YAML-driven)
- **Extensive GUI framework** with 30+ handler types
- **ProtocolLib integration** for advanced packet manipulation

## Architecture

```
com.zenax.arcanesigils/
├── core/         # Sigil, SigilManager, SocketManager
├── effects/      # EffectManager + 44 effect implementations
│   └── impl/     # Individual effect classes
├── events/       # SignalHandler, SignalType, ConditionManager, CooldownManager
├── gui/          # GUIManager, GUISession, handlers
├── tier/         # TierScalingCalculator, TierProgressionManager
├── particles/    # ParticleEngine, shape effects, presets
├── combat/       # 1.8 PvP system
├── flow/         # Flow-based programming system
└── binds/        # Hotkey-activated abilities
```

## Technology Stack

- **Java 21** with modern language features
- **Paper 1.21** API for optimal server performance
- **Maven** for dependency management and builds
- **JUnit + Mockito + AssertJ** for comprehensive testing
- **Custom obfuscation** with Bozar for distribution
- **PlaceholderAPI**, **ProtocolLib**, **WorldGuard** integration

## Design Philosophy: "Alex Check"

Every feature is designed for "Alex" - a 16-year-old server owner who uses GUIs but cannot program. If Alex can't create a fire-punch ability without reading docs, the UX needs improvement.

**Questions we ask:**
- Would Alex understand this button just by looking at it?
- Can Alex create abilities without technical knowledge?
- If Alex makes a mistake, will they know what went wrong?

## Development Practices

- **498+ build iterations** demonstrating continuous refinement
- **Comprehensive test suite** for stability
- **Session state tracking** for development workflow
- **Automated deployment pipeline** with Python/shell scripts
- **Documentation-driven** with 9+ research reports in `/docs`

## Commands

- `/as` - Open main GUI menu
- `/as give sigil <id> [tier]` - Give a sigil to player
- `/as socket <id> [tier]` - Socket a sigil into held item
- `/binds` - Manage hotkey-activated abilities

## License

Proprietary - All Rights Reserved

## Stats

- **213 Java files**
- **55,923 lines of code**
- **320+ classes/interfaces/enums**
- **44 unique effect types**
- **Version 1.0.498+**

---

*Built with passion for creating intuitive, powerful game systems.*
