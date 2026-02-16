# Arcane Sigils Plugin

Paper plugin - magical abilities (sigils) socketed into armor.

**Version**: 1.0.531 | Paper 1.21 | Java 21

---

## Build

```bash
export JAVA_HOME="/c/Users/henry/AppData/Local/Programs/Eclipse Adoptium/jdk-25.0.1.8-hotspot"
"/c/Users/henry/.m2/wrapper/dists/apache-maven-3.9.6-bin/3311e1d4/apache-maven-3.9.6/bin/mvn" -f "/c/Users/henry/Projects/Arcane Sigils/pom.xml" clean package -DskipTests -q
```

**Important**: Increment version in pom.xml with each build.

---

## Deployment

**Server**: GravelHost (dedicatedny.gravelhost.com:2022)

Deploy to server:
```bash
python deploy.py deploy          # Upload JAR (auto-deletes old JARs)
python deploy.py push <local> <remote>   # Upload specific file
python deploy.py ls [path]       # List remote directory
python deploy.py rm <path>       # Delete remote file
python deploy.py pull <remote> [local]  # Download from server
```

**SFTP password** is in `.env` file (not committed to git).

**Manual restart required** after deployment.

---

## YAML Files

Sigils, behaviors, and marks are in `src/main/resources/`:
- `sigils/*.yml` - Sigil definitions
- `behaviors/*.yml` - Entity AI behaviors
- `marks/*.yml` - Mark configurations (PHARAOH_MARK, etc.)

**Note**: `weapons/` folder removed in v1.0.531 (no longer used)

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

## Sigil Design Rules

### Message Types
**CRITICAL**: ALL player-facing messages MUST use `type: CHAT`, never `type: ACTIONBAR`.

Action bars are reserved for transient UI elements only. Chat messages provide better readability and are persistent in chat history.

**Example:**
```yaml
- id: msg
  type: EFFECT
  effect: MESSAGE
  params:
    type: CHAT  # ✓ Always use CHAT
    message: "&5&lCleopatra! &7Buffs stolen!"
```

### Target Resolution for ABILITY Flows
ABILITY-type flows use the bind menu target system:
- Use `target: "@Target"` to reference the player's selected target from `/binds`
- Do NOT use `@Victim` in ABILITY flows (that's for SIGNAL/ATTACK flows only)

**Example:**
```yaml
flows:
  - type: ABILITY
    id: my_ability
    nodes:
      - effect: STEAL_BUFFS
        params:
          target: "@Target"  # ✓ Correct for abilities
```

---

## Debugging

**Enable debug mode** in `config.yml`:
```yaml
settings:
  debug: true
```

Debug logs cover:
- **SpawnEntityEffect**: Entity spawning, targeting, BehaviorManager registration
- **BehaviorManager**: Entity registration, signal firing, flow execution
- **MarkManager**: Mark application, stacking, duration, EFFECT_STATIC execution

All debug messages prefixed with `[DEBUG]` and tagged by component (e.g., `[SpawnEntity]`, `[MarkManager]`).

---

## Recent Fixes (v1.0.531)

### Royal Guard Ability (Pharaoh Axe)
- ✓ Fixed cooldown: 45s → 120s (2 minutes)
- ✓ Fixed target mode: NEARBY → OWNER_TARGET (now uses bind menu selection)
- ✓ **CRITICAL BUG FIX**: Zombies now attack selected target from bind menu, not random nearby enemies
  - Bug was in `SpawnEntityEffect.setupForceTargeting()` - was calling `findNearestEnemy()` instead of `TargetGlowManager.getTarget()`
- ✓ Added PHARAOH_MARK to `marks/marks.yml` configuration
- ✓ Added comprehensive debugging to all spawn/targeting/behavior logic

### File Structure
- ✓ Removed `weapons/` directory (obsolete)
- ✓ Moved `marks.yml` → `marks/marks.yml` (organized into folder)
- ✓ Updated ConfigManager to load marks from new location

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
