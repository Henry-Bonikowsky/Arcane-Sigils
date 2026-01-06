# Arcane Sigils Plugin

Paper plugin - magical abilities (sigils) socketed into armor.

**Version**: 1.0.489 | Paper 1.21 | Java 21

---

## Build

```bash
export JAVA_HOME="/c/Users/henry/AppData/Local/Programs/Eclipse Adoptium/jdk-25.0.1.8-hotspot"
"/c/Users/henry/.m2/wrapper/dists/apache-maven-3.9.6-bin/3311e1d4/apache-maven-3.9.6/bin/mvn" -f "/c/Users/henry/Projects/Arcane Sigils/pom.xml" clean package -DskipTests -q
```

**Important**: Increment version in pom.xml with each build.

---

## YAML Files

Sigils and behaviors are in `src/main/resources/`:
- `sigils/*.yml` - Sigil definitions
- `behaviors/*.yml` - Entity AI behaviors

---

## Resource Pack Workflow

**Always do ALL steps:**

1. Edit files in `resourcepack/`
2. Rezip (Python, not PowerShell - backslash paths break MC):
```python
cd "/c/Users/henry/Projects/Arcane Sigils/resourcepack" && python << 'EOF'
import zipfile, os
base = r"C:\Users\henry\Projects\Arcane Sigils\resourcepack"
with zipfile.ZipFile(os.path.join(base, "ArcaneSigils-RP.zip"), 'w', zipfile.ZIP_DEFLATED) as zf:
    zf.write(os.path.join(base, "pack.mcmeta"), "pack.mcmeta")
    for root, dirs, files in os.walk(os.path.join(base, "assets")):
        for f in files:
            full = os.path.join(root, f)
            zf.write(full, os.path.relpath(full, base).replace("\\", "/"))
print("Done:", len(zf.namelist()), "files")
EOF
```
3. Get hash: `sha1sum resourcepack/ArcaneSigils-RP.zip`
4. Upload to GitHub releases
5. Give user new hash for server config.yml

---

## Obfuscation (Release Builds)

Tool: `tools/Bozar-1.7.0.exe`

**Enable**: String Encryption, Line Number Obfuscation, Source File Remover, Local Variable Obfuscation, Crasher

**Avoid**: Renamer (breaks reflection), Control Flow Heavy (breaks logic)

Add Paper API jar to Libraries. Test obfuscated JAR before distributing.

---

## Package Structure

| Package | Purpose |
|---------|---------|
| `core/` | Sigil, SigilManager, SocketManager |
| `effects/` | EffectManager + 44 effects in `impl/` |
| `events/` | SignalHandler, SignalType, ConditionManager, CooldownManager |
| `gui/` | GUIManager, GUISession, all handlers |
| `tier/` | TierScalingCalculator, TierProgressionManager |

---

## Core Concepts

- **Sigil** - Magical ability socketed into gear
- **Signal** - Event trigger (ON_ATTACK, ON_DEFEND, ON_KILL, ON_DEATH, ON_INTERACT, ON_SNEAK, ON_JUMP, PASSIVE)
- **Effect** - 44 types: damage, healing, teleport, particles, etc.
- **Condition** - Activation requirement (health %, biome, time, etc.)
- **Tier** - Sigil level with scaling parameters

**Commands**: `/as` (menu), `/as give sigil <id> [tier]`, `/as socket <id> [tier]`, `/binds`

---

## Design Philosophy: Alex Check

**Design for Alex** - a 16-year-old server owner who uses GUIs but cannot program.

Ask:
- "Would Alex understand this button just by looking at it?"
- "Can Alex create a fire-punch ability without reading docs?"
- "If Alex makes a mistake, will they know what went wrong?"

**Red flags**:
- "This might be useful someday" → Remove it
- "Advanced users could..." → Design for Alex, not programmers
- Requires docs to understand → Redesign the UI
- Tooltip > 1 sentence → Simplify

**Include in proposals**:
> **Alex Check**: [Explain why solution works for a non-programmer]
