# Message System Research

## Executive Summary
**CRITICAL BUG FOUND**: LogHelper is never initialized during plugin startup, causing ALL debug logs to fail silently. This prevents debugging of MESSAGE effects and all other systems. The debug logging system exists but is non-functional due to missing initialization call.

## Message Effect Flow

### YAML to Execution Path
```
1. YAML File (pharaoh-set.yml)
   ├─ params:
   │   ├─ type: CHAT
   │   ├─ message: '&6&lPharaoh's Curse! &7You have been stunned!'
   │   └─ target: '@Attacker'

2. FlowSerializer.nodeFromMap() [line 141-146]
   ├─ Reads params: map from YAML
   └─ Calls node.setParam() for each entry

3. EffectNode.execute() [line 72-100]
   ├─ Calls buildResolvedParams(context)
   ├─ Creates EffectParams via EffectParams.fromMap(effectType, resolvedParams)
   └─ Creates EffectContext with params

4. EffectParams.fromMap() [line 35-81]
   ├─ Handles special params: target, damage, duration, etc.
   ├─ "message" falls through to default case (line 76)
   └─ Calls result.set(key, value) to store in params map

5. MessageEffect.execute() [line 75-127]
   ├─ Gets params from context (line 76)
   ├─ Calls params.getString("message", "") (line 84)
   └─ Should work IF params were populated correctly
```

## Key Findings

### Finding 1: LogHelper Never Initialized
**File**: C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\ArmorSetsPlugin.java:177-227
**Issue**: initializeManagers() method never calls LogHelper.init(this)
**Evidence**:
```java
private void initializeManagers() {
    try {
        // Config must be first
        configManager = new ConfigManager(this);
        configManager.loadAll();

        // Effect manager (needed by others)
        effectManager = new EffectManager(this);

        // ... 50 more lines of initialization
        // LogHelper.init(this) is NEVER called
```

**Impact**:
- LogHelper.debug() calls check `if (debugEnabled && logger != null)` (LogHelper.java:143)
- logger is null because init() was never called
- debugEnabled is false because refreshDebugSetting() was never called
- ALL debug logs fail silently across entire plugin

**Search Results**:
```bash
$ grep -r "LogHelper.init" src/
# NO RESULTS
```

### Finding 2: Debug Config Correctly Set
**File**: C:\Users\henry\Projects\Arcane Sigils\src\main\resources\config.yml:7
**Status**: CORRECT
```yaml
settings:
  debug: true
```

### Finding 3: MessageEffect Has Comprehensive Debug Logging
**File**: C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\effects\impl\MessageEffect.java:78-120
**Status**: CORRECT (but never executes due to Finding #1)
```java
LogHelper.debug("[MessageEffect] Params: %s", params);
LogHelper.debug("[MessageEffect] Raw message string: '%s'", message);
if (message.isEmpty()) {
    LogHelper.debug("[MessageEffect] Message is EMPTY - aborting");
    return false;
}
LogHelper.debug("[MessageEffect] Type: %s", type);
LogHelper.debug("[MessageEffect] Component created: %s", component);
LogHelper.debug("[MessageEffect] Sending to player: %s", player.getName());
```

All these logs would work perfectly if LogHelper were initialized.

### Finding 4: EffectParams Handles "message" Correctly
**File**: C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\effects\EffectParams.java:35-81
**Status**: CORRECT
```java
public static EffectParams fromMap(String effectType, Map<String, Object> params) {
    // ... special cases for target, damage, duration, etc.
    default -> result.set(key, value); // Line 76 - stores "message" param
}
```

The "message" parameter is correctly stored via the default case. No bug here.

### Finding 5: YAML Structure is Correct
**File**: C:\Users\henry\Projects\Arcane Sigils\src\main\resources\sigils\pharaoh-set.yml:100-116
**Status**: CORRECT
```yaml
- id: msg_attacker
  type: EFFECT
  effect: MESSAGE
  params:
    type: CHAT
    message: '&6&lPharaoh''s Curse! &7You have been stunned!'
    target: '@Attacker'
```

Standard YAML flow node format with params map.

### Finding 6: No Empty sendMessage() Calls
**Search Results**:
```bash
$ grep -r 'sendMessage("")' src/
# NO RESULTS

$ grep -r 'sendMessage(null)' src/
# NO RESULTS
```

No code is sending empty messages directly.

## Root Cause Hypothesis

**Primary Issue**: LogHelper is never initialized, making debugging impossible.

**Secondary Mystery**: Why are empty messages appearing in chat?

**Theory**: Without debug logs, we cannot determine:
1. Whether MessageEffect.execute() is actually being called
2. Whether params is null when execute() runs
3. Whether message string is empty or contains whitespace
4. Whether the empty messages come from MESSAGE effects or another source

**Possible Causes for Empty Messages**:
- MESSAGE effect's params.getString("message", "") returns empty string
- Message contains only color codes (e.g., "&7") which render as empty
- Target resolution fails (e.g., @Attacker is null) causing early return
- Another system (NotificationManager, CooldownNotifier) sends empty messages

## Files Involved

### Core System Files
- C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\ArmorSetsPlugin.java - Plugin initialization (MISSING LogHelper.init())
- C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\utils\LogHelper.java - Debug logging system (not initialized)
- C:\Users\henry\Projects\Arcane Sigils\src\main\resources\config.yml - Config with debug: true

### Message Effect Chain
- C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\effects\impl\MessageEffect.java - Message effect implementation
- C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\effects\EffectParams.java - Parameter handling
- C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\flow\nodes\EffectNode.java - Flow node execution
- C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\flow\FlowSerializer.java - YAML deserialization

### YAML Data Files
- C:\Users\henry\Projects\Arcane Sigils\src\main\resources\sigils\pharaoh-set.yml - Contains MESSAGE effects
- Found 185 MESSAGE effects across 29 files (via grep)

## Debug Logs That Would Appear (Once Fixed)

With LogHelper.init() called, we would see:
```
[DEBUG] [Flow] Executing node: MESSAGE (msg_attacker)
[DEBUG] [EffectNode] === Effect execution: MESSAGE ===
[DEBUG] [EffectNode] Raw node params: {type=CHAT, message=&6&lPharaoh's Curse! &7You have been stunned!, target=@Attacker}
[DEBUG] [EffectNode] Resolved params: {type=CHAT, message=&6&lPharaoh's Curse! &7You have been stunned!, target=@Attacker}
[DEBUG] [MessageEffect] Params: EffectParams{effectType='MESSAGE', target='@Attacker', params={type=CHAT, message=&6&lPharaoh's Curse! &7You have been stunned!}}
[DEBUG] [MessageEffect] Raw message string: '&6&lPharaoh's Curse! &7You have been stunned!'
[DEBUG] [MessageEffect] Type: CHAT
[DEBUG] [MessageEffect] Component created: ...
[DEBUG] [MessageEffect] Sending to player: PlayerName
[DEBUG] [MessageEffect] Sending CHAT message (type: CHAT)
```

## Additional Research Needed

### Other Message Sources (65 files use player.sendMessage)
These could be alternative sources of empty messages:
- C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\effects\CooldownNotifier.java
- C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\notifications\NotificationManager.java
- C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\flow\FlowExecutor.java (error messages)
- C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\tier\TierProgressionManager.java
- C:\Users\henry\Projects\Arcane Sigils\src\main\java\com\miracle\arcanesigils\sets\SetBonusManager.java

## Next Steps

### Immediate Fix (Required)
1. **Add LogHelper.init(this) to ArmorSetsPlugin.initializeManagers()**
   - Location: After configManager initialization (line 181)
   - Call: `com.miracle.arcanesigils.utils.LogHelper.init(this);`
   - This will enable ALL debug logging across the entire plugin

2. **Rebuild and deploy**
   - Build JAR with fix
   - Deploy to server
   - Test MESSAGE effects
   - Check console for debug logs

### Investigation (Once Logs Work)
3. **Trigger a MESSAGE effect and capture logs**
   - Use Pharaoh's Curse sigil (has MESSAGE effects)
   - Review debug output to see actual param values
   - Identify why message string is empty

4. **Check for target resolution issues**
   - Verify @Attacker, @Victim, @Self targets work correctly
   - Confirm getTarget() returns valid entities

5. **Test message content variations**
   - Simple text: "Test"
   - With color codes: "&6Test"
   - Empty string: ""
   - Only color codes: "&7"

### Code Analysis (If Still Broken)
6. **Add breakpoint debugging**
   - MessageEffect.execute() line 84 (message retrieval)
   - EffectParams.getString() line 164 (param lookup)
   - Check if params.params map contains "message" key

7. **Verify EffectParams.fromMap() preserves message**
   - Add temporary log before line 76 (default case)
   - Confirm "message" key/value reach the set() call

8. **Check TextUtil.parseComponent()**
   - Verify it doesn't return empty Component for valid input
   - Test with various color code formats

## Conclusion

The debugging system is completely broken due to missing LogHelper initialization. **This must be fixed first** before any other investigation can proceed. Once debug logs work, the actual cause of empty messages will be immediately visible in the console output.

The MESSAGE effect code itself appears correct. The issue is either:
- A runtime problem visible only through debug logs (params being null, message being empty)
- Or coming from a different system entirely (CooldownNotifier, NotificationManager, etc.)

**Priority**: Fix LogHelper.init() → Deploy → Observe debug output → Diagnose actual issue
