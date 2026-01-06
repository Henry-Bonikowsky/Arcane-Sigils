# Arcane Sigils

A Minecraft plugin that adds a custom enchantment/ability system to armor and weapons. Think of it like enchantments on steroids - you can socket magical abilities (sigils) into your gear that activate based on different triggers.

**Version 1.0.498+** | **Paper 1.21** | **Java 21**

## What it does

Players can socket "sigils" into their armor and weapons. Each sigil has an effect (like dash, grapple, lightning strike, healing, etc.) that triggers based on events - attacking, defending, killing someone, dying, or just passively.

It's basically a way to make custom gear sets with unique abilities without needing separate plugins for each one.

## Main Features

### Sigil System
- 44 different effects (dash, grapple, lightning, phoenix, teleport, explosion, healing, lifesteal, etc.)
- Tier progression - sigils level up with XP and get stronger
- Socket sigils into any armor piece or weapon
- Multiple trigger types: ON_ATTACK, ON_DEFEND, ON_KILL, ON_DEATH, PASSIVE, etc.
- Conditional activation (only trigger at low health, in certain biomes, at night, etc.)

### Flow-Based Programming
- Visual node graph system for chaining effects together
- YAML-based configuration - no code needed to create new abilities
- Built for server owners who don't know Java

### Technical Features
- Custom particle engine with 3D shapes and effects
- Resource pack integration for custom fonts and visuals
- 1.8 PvP combat system (backported to 1.21)
- Addon system - other plugins can extend functionality
- Behavior AI system for custom mob control
- ProtocolLib integration for packet-level stuff

## Project Stats

- 213 Java files
- 55,923 lines of code
- 320+ classes/interfaces/enums
- 44 unique effect types
- 498+ build iterations

## Tech Stack

- Java 21
- Paper 1.21 API
- Maven
- JUnit + Mockito + AssertJ for testing
- PlaceholderAPI, ProtocolLib, WorldGuard integration

## Design Philosophy

Every feature is designed for "Alex" - a 16 year old server owner who uses GUIs but can't code. If Alex can't create a fire-punch ability without reading documentation, the UX failed.

## Commands

- `/as` - Main GUI
- `/as give sigil <id> [tier]` - Give a sigil
- `/as socket <id> [tier]` - Socket into held item
- `/binds` - Hotkey abilities

## License

Proprietary - All Rights Reserved
