# Arcane Sigils Plugin

Paper plugin for creating magical abilities (sigils) that can be socketed into armor.

## Build & Deploy

```bash
export JAVA_HOME="/c/Users/henry/AppData/Local/Programs/Eclipse Adoptium/jdk-25.0.1.8-hotspot"
"/c/Users/henry/.m2/wrapper/dists/apache-maven-3.9.6-bin/3311e1d4/apache-maven-3.9.6/bin/mvn" -f "/c/Users/henry/Programs(self)/Arcane Sigils/pom.xml" clean package -DskipTests -q

rm -f "/c/Users/henry/Minecraft Server/plugins/"ArcaneSigils*.jar
cp "/c/Users/henry/Programs(self)/Arcane Sigils/target/"ArcaneSigils-*.jar "/c/Users/henry/Minecraft Server/plugins/"
```

**Important**: Increment version in pom.xml with each build.

## Config File Locations

**IMPORTANT**: The server uses its own config files, NOT the project source files!

| Type | Server Location (EDIT THIS) | Project Source (reference only) |
|------|----------------------------|--------------------------------|
| Sigils | `/c/Users/henry/Minecraft Server/plugins/ArcaneSigils/sigils/` | `src/main/resources/sigils/` |
| Behaviors | `/c/Users/henry/Minecraft Server/plugins/ArcaneSigils/behaviors/` | `src/main/resources/behaviors/` |
| Config | `/c/Users/henry/Minecraft Server/plugins/ArcaneSigils/config.yml` | `src/main/resources/config.yml` |

When editing sigil YAML, behavior YAML, or config files, **always edit the server files** - they override the JAR defaults.

## Core Concepts

- **Sigil** - A magical ability that can be socketed into gear
- **Signal** - Event trigger (ON_ATTACK, ON_DEFEND, ON_KILL, ON_DEATH, ON_INTERACT, ON_SNEAK, ON_JUMP, PASSIVE)
- **Effect** - Action when signal fires (44 types: damage, healing, teleport, particles, etc.)
- **Condition** - Activation requirement (health %, biome, time, etc.)
- **Tier** - Sigil level with scaling parameters

## Package Structure

| Package | Purpose |
|---------|---------|
| `core/` | Sigil, SigilManager, SocketManager |
| `effects/` | EffectManager + 44 effects in `impl/` |
| `events/` | SignalHandler, SignalType, ConditionManager, CooldownManager |
| `gui/` | GUIManager, GUISession, all handlers |
| `gui/common/` | AbstractHandler, AbstractBrowserHandler, ItemBuilder |
| `tier/` | TierScalingCalculator, TierProgressionManager |

## Commands

| Command | Description |
|---------|-------------|
| `/as` | Open sigils menu |
| `/as give sigil <id> [tier]` | Give sigil item |
| `/as socket <id> [tier]` | Socket sigil to held item |
| `/binds` | Open binds GUI |

## Design Philosophy

**Design for Alex** - a 16-year-old server owner who can use Minecraft GUIs but cannot program.

When designing features, ask:
- "Would Alex understand this button just by looking at it?"
- "Can Alex create a fire-punch ability without reading docs?"
- "If Alex makes a mistake, will they know what went wrong?"

**Red flags:**
- "This might be useful someday" → Remove it
- "Advanced users could..." → Design for Alex, not programmers
- Requires reading docs to understand → Redesign the UI
- Tooltip longer than 1 sentence → Simplify

**When proposing solutions**, include an "Alex Check" in your response:
> **How would Alex like this?** [Answer the question - explain why the solution works for a non-programmer using the GUI, or flag concerns if it doesn't]

**Version**: 1.0.311 | Paper 1.21 | Java 21
