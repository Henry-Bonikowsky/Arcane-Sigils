# Sigil Variable System - Usage Guide

## Overview

The `SigilVariableManager` provides per-sigil-instance variable storage for tracking state like charge counters, cooldowns, or temporary buffs. Variables are scoped to: **Player UUID + Sigil ID + Armor Slot**.

This ensures that:
- Each player has their own variables
- Multiple instances of the same sigil have separate state
- Variables auto-cleanup when armor is unequipped or player logs out

## Example: King's Brace Charge Tracking

King's Brace needs to track a "charge" counter (0-100) that:
1. Increments when player takes damage (ON_DEFEND signal)
2. Enables special ability at 100 charges
3. Resets to 0 when ability is used
4. Persists while the chestplate is equipped

### Implementation in Flow

```yaml
# King's Brace sigil definition
KINGS_BRACE:
  name: "King's Brace"
  socketables: [CHESTPLATE]
  
  # ON_DEFEND: Increment charge on damage taken
  flows:
    DEFENSE:
      - type: VARIABLE_GET
        params:
          player: "@caster"
          sigilId: "KINGS_BRACE"
          slot: "CHESTPLATE"
          varName: "charge"
          defaultValue: 0
          outputVar: "current_charge"
      
      - type: VARIABLE_SET
        params:
          player: "@caster"
          sigilId: "KINGS_BRACE"
          slot: "CHESTPLATE"
          varName: "charge"
          value: "@{current_charge + 5}"  # Increase by 5 per hit
          duration: -1  # Permanent until cleared
      
      - type: VARIABLE_SET
        conditions:
          - type: EXPRESSION
            value: "@current_charge >= 100"
        params:
          player: "@caster"
          sigilId: "KINGS_BRACE"
          slot: "CHESTPLATE"
          varName: "charge"
          value: 100  # Cap at 100
          duration: -1
      
      # Visual feedback at 100 charges
      - type: PLAY_SOUND
        conditions:
          - type: EXPRESSION
            value: "@current_charge >= 100"
        params:
          sound: ENTITY_PLAYER_LEVELUP
          volume: 1.0
          pitch: 2.0
    
    # ON_INTERACT: Use ability when at 100 charges
    INTERACT:
      - type: VARIABLE_GET
        params:
          player: "@caster"
          sigilId: "KINGS_BRACE"
          slot: "CHESTPLATE"
          varName: "charge"
          defaultValue: 0
          outputVar: "charge"
      
      - type: STOP_FLOW
        conditions:
          - type: EXPRESSION
            value: "@charge < 100"
      
      # Ability active! Trigger effects
      - type: DEAL_DAMAGE
        params:
          target: TARGET
          damage: 20
      
      - type: KNOCKBACK
        params:
          target: TARGET
          strength: 3.0
      
      # Reset charge to 0
      - type: VARIABLE_SET
        params:
          player: "@caster"
          sigilId: "KINGS_BRACE"
          slot: "CHESTPLATE"
          varName: "charge"
          value: 0
          duration: -1
```

## Java API Usage

### Setting a Variable

```java
SigilVariableManager svm = plugin.getSigilVariableManager();

// Set charge to 50 (permanent until cleared)
svm.setSigilVariable(player, "KINGS_BRACE", "CHESTPLATE", "charge", 50, -1);

// Set temporary buff (expires in 10 seconds)
svm.setSigilVariable(player, "PHARAOH_HELMET", "HELMET", "damage_buff", 1.5, 10);
```

### Getting a Variable

```java
SigilVariableManager svm = plugin.getSigilVariableManager();

// Get charge value (returns null if not set)
Object chargeObj = svm.getSigilVariable(player, "KINGS_BRACE", "CHESTPLATE", "charge");
int charge = chargeObj != null ? ((Number) chargeObj).intValue() : 0;

// Or use the convenience method
int charge = svm.getSigilVariableInt(player, "KINGS_BRACE", "CHESTPLATE", "charge", 0);
```

### Checking if Variable Exists

```java
if (svm.hasSigilVariable(player, "KINGS_BRACE", "CHESTPLATE", "charge")) {
    // Variable is set and not expired
}
```

### Clearing Variables

```java
// Clear specific variable
svm.clearSigilVariable(player, "KINGS_BRACE", "CHESTPLATE", "charge");

// Clear all variables for a sigil instance
svm.clearAllSigilVariables(player, "KINGS_BRACE", "CHESTPLATE");

// Clear all variables for a slot (all sigils in that slot)
svm.clearSlotVariables(player.getUniqueId(), "CHESTPLATE");

// Clear all variables for a player (all sigils, all slots)
svm.clearAllPlayerVariables(player.getUniqueId());
```

## Auto-Cleanup

Variables are automatically cleaned up in these scenarios:

1. **Armor Unequipped**: When player removes armor, all variables for that slot are cleared
2. **Player Quit**: When player logs out, all their variables are cleared
3. **Expiration**: Variables with a duration automatically expire after the specified time

## Integration with Flow System

To use sigil variables in the flow system, create two new flow node types:

### VARIABLE_GET Node

```java
public class VariableGetNode extends FlowNode {
    @Override
    public void execute(FlowExecutionContext context) {
        Player player = context.getPlayer("player");
        String sigilId = context.getString("sigilId");
        String slot = context.getString("slot");
        String varName = context.getString("varName");
        Object defaultValue = context.get("defaultValue");
        String outputVar = context.getString("outputVar");
        
        SigilVariableManager svm = plugin.getSigilVariableManager();
        Object value = svm.getSigilVariable(player, sigilId, slot, varName);
        
        if (value == null) {
            value = defaultValue;
        }
        
        context.set(outputVar, value);
    }
}
```

### VARIABLE_SET Node

```java
public class VariableSetNode extends FlowNode {
    @Override
    public void execute(FlowExecutionContext context) {
        Player player = context.getPlayer("player");
        String sigilId = context.getString("sigilId");
        String slot = context.getString("slot");
        String varName = context.getString("varName");
        Object value = context.get("value");
        int duration = context.getInt("duration", -1);
        
        SigilVariableManager svm = plugin.getSigilVariableManager();
        svm.setSigilVariable(player, sigilId, slot, varName, value, duration);
    }
}
```

## Best Practices

1. **Use descriptive variable names**: `charge`, `damage_buff`, `combo_count`, etc.
2. **Set appropriate durations**: Use -1 for permanent (cleared on unequip), positive values for temporary effects
3. **Default values**: Always provide defaults when getting variables to handle first-time access
4. **Integer operations**: Use `getSigilVariableInt()` for numeric variables to avoid type casting
5. **Cleanup**: Trust the auto-cleanup system - no need to manually clear on unequip

## Performance Notes

- Variables are stored in `ConcurrentHashMap` for thread-safety
- Expiration check runs every 20 ticks (1 second)
- Composite keys format: `"playerUUID|sigilId|slot"`
- Case-insensitive variable names and sigil IDs
