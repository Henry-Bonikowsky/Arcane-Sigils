# Logging System Research

## Goal
Understand current logging architecture before refactoring to industry best practices (proper log levels, async writes, compression, etc.)

## Current Architecture

### LogHelper.java (Central Logging Facade)
- **Location**: `src/main/java/com/miracle/arcanesigils/utils/LogHelper.java`
- **Pattern**: Static utility wrapping Bukkit's plugin logger
- **Binary Debug Mode**: Single `debugEnabled` flag (true/false) controls ALL debug output
- **Routing**: `debugOutput` setting routes to FILE/CONSOLE/BOTH
- **Methods**: `debug()`, `info()`, `warning()`, `severe()` + domain helpers

**Key Issue**: No granular log levels - all debug messages treated equally.

### DebugLogger.java (File Writer)
- **Location**: `src/main/java/com/miracle/arcanesigils/utils/DebugLogger.java`
- **Rotation**: Size-based only (10MB default), startup rotation
- **Writes**: Synchronous with immediate flush (blocking I/O)
- **Archives**: Keeps 7 files, auto-cleanup
- **Missing**: Time-based rotation, compression, async writes

### Configuration
```yaml
settings:
  debug: true  # Binary on/off
  debug_logging:
    output: BOTH  # FILE/CONSOLE/BOTH
    max_file_size_mb: 10
    max_files_to_keep: 7
```

**Issue**: No per-component filtering, no runtime reload.

## Usage Patterns

### Debug Call Volume: 225 calls across 31 files

**Top Components** (by volume):
1. BindsListener (24 calls) - Ability activation tracking
2. EffectNode (23 calls) - Flow execution
3. MessageEffect (23 calls) - Chat/action bar
4. SpawnEntityEffect (22 calls) - Entity spawning
5. FlowExecutor (18 calls) - Main flow engine

**Component Groups**:
- Flow System: 60+ calls (EffectNode, FlowExecutor, VariableNode, etc.)
- Binds/Abilities: 35+ calls
- Entity Management: 30+ calls
- Effects: 25+ calls
- Game Logic: 20+ calls

**Message Format**: All use `[ComponentName]` tags consistently
```java
LogHelper.debug("[SpawnEntity] Starting spawn: type=%s, count=%d", ...);
LogHelper.debug("[Binds] Activated bind at slot " + slotOrId);
```

## Pain Points Identified

### 1. Production Spam
With `debug: true`, the plugin generates **333KB in minutes** (observed on server):
- Flow system fires on every tick for passive sigils
- Bind activation logs every keypress
- Entity behaviors log every signal

**Root Cause**: No INFO/WARN/ERROR separation - everything is DEBUG.

### 2. Blocking I/O
- Immediate flush on every write (line 95 in DebugLogger)
- Size check + potential rotation on every call (lines 86-88)
- Under load: hundreds of writes/sec → disk I/O bottleneck

### 3. No Granular Control
- Can't enable MarkManager debug without enabling EVERYTHING
- No per-component log levels
- All-or-nothing debug mode

### 4. Missing Industry Practices
- ❌ No TRACE/DEBUG/INFO/WARN/ERROR levels
- ❌ No async writes
- ❌ No compression of archived logs
- ❌ No time-based rotation (daily)
- ❌ No runtime log level changes

## Initialization Flow

```
ArmorSetsPlugin.onEnable():
1. LogHelper.init(this)                    [line 87]
2. DebugLogger.initialize(dataFolder)      [line 90]
3. ConfigManager.loadAll()
   └─ loadDebugLoggingConfig()             [line 72-78]
4. LogHelper.refreshDebugSetting(this)     [line 193]
```

**Shutdown**:
```
ArmorSetsPlugin.onDisable():
1. DebugLogger.shutdown()                  [line 180]
```

## Integration Points

### Files Using LogHelper (31 total)
- **Flow Execution**: All flow nodes, FlowExecutor
- **Abilities**: BindsListener, ActivateBindCommand, BindsManager
- **Entity Management**: SpawnEntityEffect, BehaviorManager, AuraManager
- **Effects**: MessageEffect, ParticleEffect, etc.
- **Game Mechanics**: MarkManager, PotionDamageReductionListener

### Direct Logger Bypasses (51 calls)
- LogHelper.java itself (8 calls - implementation)
- PluginDebugger.java (40 calls - intentional for plugin detection)
- AbstractAddon.java (3 calls - addon lifecycle)

**Verdict**: 98% centralized through LogHelper ✓

## Testing Approach in Codebase

**Current State**: No unit tests found for logging system
- Manual testing via server deployment
- Log observation via fetch_logs.py script

**Convention**: Integration testing on live/test server

## Key Decisions for Refactor

### 1. Backwards Compatibility
**Question**: Do we maintain `debug: true/false` or force migration to log levels?

**Recommendation**: Maintain compatibility:
```yaml
# Legacy (still works)
settings:
  debug: true

# New (preferred)
settings:
  log_level: INFO
  log_levels:
    MarkManager: DEBUG
```

### 2. Message Routing
**Current**: LogHelper routes to console/file/both

**Keep**: This pattern works well, just add async buffer for file writes

### 3. Component Tags
**Current**: Manual tags like `[MarkManager]` in every message

**Decision**: Keep manual tags (changing would break 225+ call sites)
- Alternative would be automatic detection via stack trace (expensive)

### 4. Configuration Reload
**Current**: No reload support (requires restart)

**Add**: Runtime log level changes without restart (industry standard)

## Files Requiring Modification

### Core Logging System
1. **LogHelper.java** - Add log levels (TRACE/DEBUG/INFO/WARN/ERROR)
2. **DebugLogger.java** - Add async writes, compression, time-based rotation
3. **ConfigManager.java** - Load per-component log levels
4. **config.yml** - Add log_level and log_levels section

### Plugin Lifecycle
5. **ArmorSetsPlugin.java** - Update initialization/shutdown for async logger

### Optional (Phase 2)
6. Add reload command for runtime log level changes
7. Add compression for archived logs
8. Add per-component filtering

## Open Questions

1. **Async Buffer Size**: Default to 1000 messages or configurable?
2. **Compression Format**: Use GZIP (.gz) for archived logs?
3. **Time-Based Rotation**: Daily at midnight, or configurable time?
4. **Migration Path**: Auto-migrate old config or require manual update?
5. **Performance Impact**: Is async queue necessary for <1000 msgs/sec workload?

## Next Steps

Move to **Planning Phase** with this research as context.
