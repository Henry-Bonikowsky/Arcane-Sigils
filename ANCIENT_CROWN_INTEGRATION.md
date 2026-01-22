# Ancient Crown Interception System Integration

## Overview
The Ancient Crown sigil has been successfully integrated with the interception system to provide passive immunity to negative effects and attribute modifiers.

## Implementation Details

### Files Created
1. **AncientCrownInterceptor.java** (`src/main/java/com/miracle/arcanesigils/interception/AncientCrownInterceptor.java`)
   - Implements `EffectInterceptor` interface
   - Handles both potion effects and attribute modifiers
   - Tier-based immunity: 20% (T1) → 40% (T2) → 60% (T3) → 80% (T4) → 100% (T5)
   - Priority 100 (runs before blockers like Cleopatra)

### Files Modified
1. **ArmorChangeListener.java** (`src/main/java/com/miracle/arcanesigils/listeners/ArmorChangeListener.java`)
   - Added Ancient Crown interceptor registration/unregistration
   - Detects when helmet with Ancient Crown is equipped/unequipped
   - Handles armor changes via inventory clicks, drops, and player join/quit events
   - Tracks active interceptors per player with `ConcurrentHashMap`

## How It Works

### Interception Flow
1. **Player equips helmet with Ancient Crown socketed**
   - `ArmorChangeListener.onInventoryClick()` detects the change
   - Calls `updateAncientCrownInterceptor(player)`
   - Creates `AncientCrownInterceptor(tier)` based on sigil tier
   - Registers interceptor with `InterceptionManager`

2. **Negative effect/modifier applied to player**
   - `PotionEffectInterceptionListener` or attribute system fires `InterceptionEvent`
   - `InterceptionManager` calls all registered interceptors in priority order
   - `AncientCrownInterceptor.intercept()` processes the event:
     - Checks if effect/modifier is negative
     - If tier 5 (100% immunity): cancels completely
     - Otherwise: reduces potency by immunity percent
   - Modified/cancelled event returned to caller

3. **Player unequips Ancient Crown**
   - `ArmorChangeListener` detects the change
   - Calls `unregisterAncientCrownInterceptor(player)`
   - Removes interceptor from `InterceptionManager`
   - Player now receives full negative effects

### Immunity Calculations

**Potion Effects:**
- Reduces amplifier by immunity percent
- Formula: `newAmplifier = (int)(originalAmplifier * (1 - immunity/100))`
- Example: Slowness III (amp 2) with 60% immunity → 2 * 0.4 = 0.8 → amp 0 (Slowness I)
- Duration is NOT affected (only potency)

**Attribute Modifiers:**
- Reduces negative modifier value by immunity percent
- Formula: `newValue = originalValue * (1 - immunity/100)`
- Example: -0.25 speed with 60% immunity → -0.25 * 0.4 = -0.10 speed
- Precision maintained (no integer truncation)

### Negative Effect Detection

**Potion Effects:**
- POISON, WITHER, SLOWNESS, WEAKNESS
- MINING_FATIGUE, NAUSEA, BLINDNESS
- HUNGER, INSTANT_DAMAGE, LEVITATION
- UNLUCK, DARKNESS
- INFESTED, OOZING, WEAVING, WIND_CHARGED (1.21+ effects)

**Attribute Modifiers:**
- Any modifier with value < 0 is considered negative
- Examples: movement speed reduction, attack damage reduction

## Debug Logging

When `settings.debug: true` in config.yml, the following messages are logged:

### ArmorChangeListener
- `[DEBUG] [ArmorChange] Registered Ancient Crown interceptor for <player> (tier X, Y% immunity)`
- `[DEBUG] [ArmorChange] Unregistered Ancient Crown interceptor for <player>`

### AncientCrownInterceptor
- `[DEBUG] [AncientCrown] Blocked <effect> (amp X) - 100% immunity`
- `[DEBUG] [AncientCrown] Reduced <effect>: amp X → Y (Z% immunity)`
- `[DEBUG] [AncientCrown] Blocked attribute modifier <attribute> (value) - 100% immunity`
- `[DEBUG] [AncientCrown] Reduced <attribute> modifier: X → Y (Z% immunity)`

## Testing Instructions

### 1. Enable Debug Mode
Edit `config.yml`:
```yaml
settings:
  debug: true
```

### 2. Give Ancient Crown
```
/as give sigil ancient_crown 1  # Tier 1 (20% immunity)
/as give sigil ancient_crown 3  # Tier 3 (60% immunity)
/as give sigil ancient_crown 5  # Tier 5 (100% immunity)
```

### 3. Socket to Helmet
1. Hold helmet in hand
2. Run: `/as socket ancient_crown <tier>`
3. Equip the helmet

### 4. Test Potion Effects
Apply negative effects and observe reduction:
```
/effect give @s minecraft:poison 30 2      # Poison III for 30 seconds
/effect give @s minecraft:slowness 30 1    # Slowness II for 30 seconds
/effect give @s minecraft:weakness 30 0    # Weakness I for 30 seconds
```

**Expected Results:**
- Tier 1 (20%): Slightly reduced potency
- Tier 3 (60%): Significantly reduced potency
- Tier 5 (100%): Effect completely blocked

### 5. Test Attribute Modifiers
Create a test sigil that applies negative attribute modifiers:
```yaml
test_slow:
  name: "Test Slowness"
  slot: CHESTPLATE
  signals:
    - type: PASSIVE
      effects:
        - type: MODIFY_ATTRIBUTE
          attribute: GENERIC_MOVEMENT_SPEED
          value: -0.5  # 50% slower
          operation: ADD_SCALAR
```

**Expected Results:**
- Without Ancient Crown: -0.5 speed
- With T1 Ancient Crown: -0.4 speed (20% reduction)
- With T3 Ancient Crown: -0.2 speed (60% reduction)
- With T5 Ancient Crown: No modifier applied (100% blocked)

### 6. Verify Registration/Unregistration
1. Watch console logs when equipping/unequipping helmet
2. Should see registration message when equipped
3. Should see unregistration message when unequipped
4. Verify interceptor only active when Ancient Crown is on helmet

## Integration Points

### Existing Systems
- **InterceptionManager**: Manages all interceptors
- **PotionEffectInterceptionListener**: Fires events for potion effects
- **ArmorChangeListener**: Detects armor changes and manages variables

### Future Extensions
The interception system can be extended for:
- Cleopatra suppression (blocks defensive buffs on targets)
- Other sigils that need to modify/cancel effects
- Damage interception (modify incoming/outgoing damage)
- Healing interception
- Teleportation interception

## Architecture Benefits
1. **No core code changes**: Ancient Crown works through listeners only
2. **Priority-based**: Reducers (Ancient Crown) run before blockers (Cleopatra)
3. **Tier-aware**: Automatically scales with sigil tier
4. **Event-driven**: Registers/unregisters on equipment changes
5. **Debug-friendly**: Comprehensive logging for testing

## Known Limitations
1. Only intercepts effects applied through Bukkit's `EntityPotionEffectEvent`
2. Attribute modifiers must be applied through the interception system
3. Does not affect environmental damage (fall damage, fire, etc.) - only effects/modifiers
4. Armor changes via `/item replace` or other commands may not trigger immediately (will update on next inventory change or rejoin)

## Performance
- **Memory**: Negligible (one interceptor instance per player with Ancient Crown)
- **CPU**: O(1) lookup per effect application, minimal overhead
- **Registration**: Happens on armor change only, not every tick
- **Thread-safe**: Uses `ConcurrentHashMap` for interceptor tracking
