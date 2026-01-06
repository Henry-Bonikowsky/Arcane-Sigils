# Test Inputs & Outputs - Literal Values

## FlowContextTest - Variable Management

```java
// INPUT: Set variable "test" to 42
flowContext.setVariable("test", 42);
// OUTPUT: Get variable returns 42
assertThat(flowContext.getVariable("test")).isEqualTo(42);

// INPUT: Get non-existent variable
flowContext.getVariable("nonexistent")
// OUTPUT: Returns null
assertThat(...).isNull();

// INPUT: Get non-existent with default "default"
flowContext.getVariable("nonexistent", "default")
// OUTPUT: Returns "default"
assertThat(...).isEqualTo("default");

// INPUT: Set variable "num" to 42, get as double
flowContext.setVariable("num", 42);
flowContext.getVariableAsDouble("num", 0.0)
// OUTPUT: Returns 42.0
assertThat(...).isEqualTo(42.0);

// INPUT: Set variable "num" to "3.14" (string), get as double
flowContext.setVariable("num", "3.14");
flowContext.getVariableAsDouble("num", 0.0)
// OUTPUT: Returns 3.14
assertThat(...).isEqualTo(3.14);

// INPUT: Set variable "num" to "not a number", get as double with default -1.0
flowContext.setVariable("num", "not a number");
flowContext.getVariableAsDouble("num", -1.0)
// OUTPUT: Returns -1.0 (the default)
assertThat(...).isEqualTo(-1.0);
```

## FlowContextTest - Expression Resolution

```java
// INPUT: Resolve null
flowContext.resolveExpression(null)
// OUTPUT: null
assertThat(...).isNull();

// INPUT: Resolve "hello world" (no placeholders)
flowContext.resolveExpression("hello world")
// OUTPUT: "hello world" unchanged
assertThat(...).isEqualTo("hello world");

// INPUT: Set tier to 5, resolve "{tier}"
flowContext.setTier(5);
flowContext.resolveExpression("{tier}")
// OUTPUT: "5"
assertThat(...).isEqualTo("5");

// INPUT: Set variable "customValue" to 999, resolve "{customValue}"
flowContext.setVariable("customValue", 999);
flowContext.resolveExpression("{customValue}")
// OUTPUT: "999"
assertThat(...).isEqualTo("999");

// INPUT: Resolve "{unknown_placeholder}" (variable not set)
flowContext.resolveExpression("{unknown_placeholder}")
// OUTPUT: "{unknown_placeholder}" unchanged
assertThat(...).isEqualTo("{unknown_placeholder}");
```

## FlowContextTest - Nested Braces (FIXED)

```java
// INPUT: Resolve "{{value}}" when variable NOT set
flowContext.resolveExpression("{{value}}")
// OUTPUT: "{{value}}" - unchanged because {value} does not resolve
assertThat(result).isEqualTo("{{value}}");

// INPUT: Set variable "value" to 42, resolve "{{value}}"
flowContext.setVariable("value", 42);
flowContext.resolveExpression("{{value}}")
// OUTPUT: "{42}" - inner {value} resolved to 42, outer braces remain
assertThat(result).isEqualTo("{42}");
```

## FlowContextTest - Condition Evaluation

```java
// INPUT: Evaluate null condition
flowContext.evaluateCondition(null)
// OUTPUT: true
assertThat(...).isTrue();

// INPUT: Evaluate empty string
flowContext.evaluateCondition("")
// OUTPUT: true
assertThat(...).isTrue();

// Comparison tests (parameterized):
// INPUT -> OUTPUT
"5 < 10"   -> true
"10 < 5"   -> false
"5 > 3"    -> true
"3 > 5"    -> false
"5 <= 5"   -> true
"6 <= 5"   -> false
"5 >= 5"   -> true
"4 >= 5"   -> false
"5 == 5"   -> true
"5 == 6"   -> false
"5 != 6"   -> true
"5 != 5"   -> false

// Boolean literals:
"true"  -> true
"TRUE"  -> true
"1"     -> true
"false" -> false
"FALSE" -> false
"0"     -> false

// String equality:
"hello == hello" -> true
"hello == world" -> false
"hello != world" -> true

// Random:
"random(100%)" -> always true (tested 10 times)
"random(0%)"   -> always false (tested 10 times)

// With variables:
// INPUT: Set counter=8, evaluate "{counter} < 10"
flowContext.setVariable("counter", 8);
flowContext.evaluateCondition("{counter} < 10")
// OUTPUT: true (8 < 10)
assertThat(result).isTrue();
```

## NodeTypesTest - MathNode Operations

```java
// ADD
// INPUT: left=10.0, right=5.0, operation=ADD
// OUTPUT: result variable = 15.0
assertThat(flowContext.getVariableAsDouble("result", 0.0)).isEqualTo(15.0);

// SUBTRACT
// INPUT: left=10.0, right=3.0, operation=SUBTRACT
// OUTPUT: result = 7.0
assertThat(...).isEqualTo(7.0);

// MULTIPLY
// INPUT: left=6.0, right=7.0, operation=MULTIPLY
// OUTPUT: result = 42.0
assertThat(...).isEqualTo(42.0);

// DIVIDE
// INPUT: left=20.0, right=4.0, operation=DIVIDE
// OUTPUT: result = 5.0
assertThat(...).isEqualTo(5.0);

// DIVIDE BY ZERO (edge case)
// INPUT: left=10.0, right=0.0, operation=DIVIDE
// OUTPUT: result = 0.0
assertThat(flowContext.getVariableAsDouble("output", -999.0)).isEqualTo(0.0);

// MODULO
// INPUT: left=17.0, right=5.0, operation=MODULO
// OUTPUT: result = 2.0
assertThat(...).isEqualTo(2.0);

// MODULO BY ZERO (edge case)
// INPUT: left=17.0, right=0.0, operation=MODULO
// OUTPUT: result = 0.0
assertThat(flowContext.getVariableAsDouble("output", -999.0)).isEqualTo(0.0);

// POWER
// INPUT: left=2.0, right=10.0, operation=POWER
// OUTPUT: result = 1024.0
assertThat(...).isEqualTo(1024.0);

// SQRT
// INPUT: left=16.0, operation=SQRT
// OUTPUT: result = 4.0
assertThat(...).isEqualTo(4.0);

// SQRT NEGATIVE (edge case)
// INPUT: left=-16.0, operation=SQRT
// OUTPUT: result = NaN
assertThat(Double.isNaN(result)).isTrue();

// MIN
// INPUT: left=10.0, right=5.0, operation=MIN
// OUTPUT: result = 5.0
assertThat(...).isEqualTo(5.0);

// MAX
// INPUT: left=10.0, right=5.0, operation=MAX
// OUTPUT: result = 10.0
assertThat(...).isEqualTo(10.0);

// ABS
// INPUT: left=-42.0, operation=ABS
// OUTPUT: result = 42.0
assertThat(...).isEqualTo(42.0);

// FLOOR
// INPUT: left=3.7, operation=FLOOR
// OUTPUT: result = 3.0
assertThat(...).isEqualTo(3.0);

// CEIL
// INPUT: left=3.2, operation=CEIL
// OUTPUT: result = 4.0
assertThat(...).isEqualTo(4.0);

// ROUND
// INPUT: left=3.5, operation=ROUND
// OUTPUT: result = 4.0
assertThat(...).isEqualTo(4.0);

// RANDOM (range)
// INPUT: left=10.0, right=20.0, operation=RANDOM
// OUTPUT: result >= 10.0 AND result < 20.0
assertThat(result).isGreaterThanOrEqualTo(10.0);
assertThat(result).isLessThan(20.0);
```

## NodeTypesTest - VariableNode Operations

```java
// SET operation
// INPUT: operation=SET, name="myVar", value=42.0
// OUTPUT: variable "myVar" = 42.0
assertThat(flowContext.getVariableAsDouble("myVar", 0.0)).isEqualTo(42.0);

// ADD operation
// INPUT: Set counter=10, then operation=ADD, name="counter", value=5.0
// OUTPUT: counter = 15.0
assertThat(flowContext.getVariableAsDouble("counter", 0.0)).isEqualTo(15.0);

// SUBTRACT operation
// INPUT: Set counter=10, then operation=SUBTRACT, name="counter", value=3.0
// OUTPUT: counter = 7.0
assertThat(...).isEqualTo(7.0);

// MULTIPLY operation
// INPUT: Set counter=10, then operation=MULTIPLY, name="counter", value=2.0
// OUTPUT: counter = 20.0
assertThat(...).isEqualTo(20.0);

// DIVIDE operation
// INPUT: Set counter=20, then operation=DIVIDE, name="counter", value=4.0
// OUTPUT: counter = 5.0
assertThat(...).isEqualTo(5.0);

// INCREMENT operation
// INPUT: Set counter=5, then operation=INCREMENT, name="counter"
// OUTPUT: counter = 6.0
assertThat(...).isEqualTo(6.0);

// DECREMENT operation
// INPUT: Set counter=5, then operation=DECREMENT, name="counter"
// OUTPUT: counter = 4.0
assertThat(...).isEqualTo(4.0);
```

## NodeTypesTest - ConditionNode

```java
// True condition branches to "yes"
// INPUT: condition="true"
// OUTPUT: next node = "yes_node"
assertThat(node.execute(flowContext)).isEqualTo("yes_node");

// False condition branches to "no"
// INPUT: condition="false"
// OUTPUT: next node = "no_node"
assertThat(node.execute(flowContext)).isEqualTo("no_node");

// Numeric comparison true
// INPUT: condition="5 > 3"
// OUTPUT: next node = "yes_node"
assertThat(...).isEqualTo("yes_node");

// Numeric comparison false
// INPUT: condition="3 > 5"
// OUTPUT: next node = "no_node"
assertThat(...).isEqualTo("no_node");

// Variable resolution in condition
// INPUT: Set health=75, condition="{health} > 50"
// OUTPUT: next node = "yes_node" (75 > 50 is true)
flowContext.setVariable("health", 75);
assertThat(node.execute(flowContext)).isEqualTo("yes_node");
```

## NodeTypesTest - LoopNode

```java
// Loop 3 iterations
// INPUT: iterations=3, body connection="body", done connection="done"
// First 3 calls return "body", 4th call returns "done"
assertThat(node.execute(flowContext)).isEqualTo("body");  // i=0
assertThat(node.execute(flowContext)).isEqualTo("body");  // i=1
assertThat(node.execute(flowContext)).isEqualTo("body");  // i=2
assertThat(node.execute(flowContext)).isEqualTo("done");  // finished

// Loop sets index variable
// INPUT: iterations=3, indexVar="i"
// OUTPUT: i=0 on first iteration, i=1 on second, i=2 on third
node.execute(flowContext);
assertThat(flowContext.getVariableAsDouble("i", -1.0)).isEqualTo(0.0);
node.execute(flowContext);
assertThat(flowContext.getVariableAsDouble("i", -1.0)).isEqualTo(1.0);

// Zero iterations skips directly to done
// INPUT: iterations=0
// OUTPUT: returns "done" immediately
assertThat(node.execute(flowContext)).isEqualTo("done");
```

## ConditionTypeTest - Enum Verification

```java
// Each of these 35 enum values MUST exist (will throw if not):
ConditionType.valueOf("HEALTH_PERCENT")     -> ConditionType.HEALTH_PERCENT
ConditionType.valueOf("HEALTH")             -> ConditionType.HEALTH
ConditionType.valueOf("VICTIM_HEALTH_PERCENT") -> ConditionType.VICTIM_HEALTH_PERCENT
ConditionType.valueOf("HAS_POTION")         -> ConditionType.HAS_POTION
ConditionType.valueOf("NO_POTION")          -> ConditionType.NO_POTION
ConditionType.valueOf("BIOME")              -> ConditionType.BIOME
ConditionType.valueOf("BLOCK_BELOW")        -> ConditionType.BLOCK_BELOW
ConditionType.valueOf("LIGHT_LEVEL")        -> ConditionType.LIGHT_LEVEL
ConditionType.valueOf("IN_WATER")           -> ConditionType.IN_WATER
ConditionType.valueOf("ON_GROUND")          -> ConditionType.ON_GROUND
ConditionType.valueOf("IN_AIR")             -> ConditionType.IN_AIR
ConditionType.valueOf("HUNGER")             -> ConditionType.HUNGER
ConditionType.valueOf("WEATHER")            -> ConditionType.WEATHER
ConditionType.valueOf("TIME")               -> ConditionType.TIME
ConditionType.valueOf("HAS_VICTIM")         -> ConditionType.HAS_VICTIM
ConditionType.valueOf("VICTIM_IS_PLAYER")   -> ConditionType.VICTIM_IS_PLAYER
ConditionType.valueOf("VICTIM_IS_HOSTILE")  -> ConditionType.VICTIM_IS_HOSTILE
ConditionType.valueOf("HAS_TARGET")         -> ConditionType.HAS_TARGET
ConditionType.valueOf("SIGNAL")             -> ConditionType.SIGNAL
ConditionType.valueOf("WEARING_FULL_SET")   -> ConditionType.WEARING_FULL_SET
ConditionType.valueOf("SNEAKING")           -> ConditionType.SNEAKING
ConditionType.valueOf("SPRINTING")          -> ConditionType.SPRINTING
ConditionType.valueOf("FLYING")             -> ConditionType.FLYING
ConditionType.valueOf("SWIMMING")           -> ConditionType.SWIMMING
ConditionType.valueOf("MAIN_HAND")          -> ConditionType.MAIN_HAND
ConditionType.valueOf("HAS_ENCHANT")        -> ConditionType.HAS_ENCHANT
ConditionType.valueOf("HOLDING_SIGIL_ITEM") -> ConditionType.HOLDING_SIGIL_ITEM
ConditionType.valueOf("DURABILITY_PERCENT") -> ConditionType.DURABILITY_PERCENT
ConditionType.valueOf("HAS_MARK")           -> ConditionType.HAS_MARK
ConditionType.valueOf("CRITICAL_HIT")       -> ConditionType.CRITICAL_HIT
ConditionType.valueOf("VICTIM_IS_UNDEAD")   -> ConditionType.VICTIM_IS_UNDEAD
ConditionType.valueOf("ON_FIRE")            -> ConditionType.ON_FIRE
ConditionType.valueOf("DIMENSION")          -> ConditionType.DIMENSION
ConditionType.valueOf("Y_LEVEL")            -> ConditionType.Y_LEVEL
ConditionType.valueOf("EXPERIENCE_LEVEL")   -> ConditionType.EXPERIENCE_LEVEL

// Total count must be exactly 35
assertThat(ConditionType.values()).hasSize(35);

// Each enum has non-null, non-empty metadata:
for (ConditionType type : ConditionType.values()) {
    type.getDisplayName() != null && not empty
    type.getIcon() != null
    type.getDescription() != null && not empty
    type.getCategory() != null
}
```

## SignalActivationTest - Enum Verification

```java
// Each of these 23 enum values MUST exist (will throw if not):
SignalType.valueOf("ATTACK")         -> SignalType.ATTACK
SignalType.valueOf("DEFENSE")        -> SignalType.DEFENSE
SignalType.valueOf("KILL_MOB")       -> SignalType.KILL_MOB
SignalType.valueOf("KILL_PLAYER")    -> SignalType.KILL_PLAYER
SignalType.valueOf("SHIFT")          -> SignalType.SHIFT
SignalType.valueOf("FALL_DAMAGE")    -> SignalType.FALL_DAMAGE
SignalType.valueOf("EFFECT_STATIC")  -> SignalType.EFFECT_STATIC
SignalType.valueOf("BOW_SHOOT")      -> SignalType.BOW_SHOOT
SignalType.valueOf("BOW_HIT")        -> SignalType.BOW_HIT
SignalType.valueOf("TRIDENT_THROW")  -> SignalType.TRIDENT_THROW
SignalType.valueOf("TICK")           -> SignalType.TICK
SignalType.valueOf("BLOCK_BREAK")    -> SignalType.BLOCK_BREAK
SignalType.valueOf("BLOCK_PLACE")    -> SignalType.BLOCK_PLACE
SignalType.valueOf("INTERACT")       -> SignalType.INTERACT
SignalType.valueOf("ITEM_BREAK")     -> SignalType.ITEM_BREAK
SignalType.valueOf("FISH")           -> SignalType.FISH
SignalType.valueOf("ENTITY_DEATH")   -> SignalType.ENTITY_DEATH
SignalType.valueOf("PLAYER_NEAR")    -> SignalType.PLAYER_NEAR
SignalType.valueOf("PLAYER_STAND")   -> SignalType.PLAYER_STAND
SignalType.valueOf("EXPIRE")         -> SignalType.EXPIRE
SignalType.valueOf("PROJECTILE_HIT") -> SignalType.PROJECTILE_HIT
SignalType.valueOf("OWNER_ATTACK")   -> SignalType.OWNER_ATTACK
SignalType.valueOf("OWNER_DEFEND")   -> SignalType.OWNER_DEFEND

// Total count must be exactly 23
assertThat(SignalType.values()).hasSize(23);

// fromConfigKey lookups:
SignalType.fromConfigKey("ATTACK")   -> SignalType.ATTACK
SignalType.fromConfigKey("DEFENSE")  -> SignalType.DEFENSE
SignalType.fromConfigKey("attack")   -> SignalType.ATTACK  // case insensitive
SignalType.fromConfigKey(null)       -> null
SignalType.fromConfigKey("INVALID")  -> null

// isCombatSignal:
SignalType.ATTACK.isCombatSignal()      -> true
SignalType.DEFENSE.isCombatSignal()     -> true
SignalType.KILL_MOB.isCombatSignal()    -> true
SignalType.KILL_PLAYER.isCombatSignal() -> true
SignalType.BOW_HIT.isCombatSignal()     -> true
SignalType.SHIFT.isCombatSignal()       -> false
SignalType.TICK.isCombatSignal()        -> false

// isPassive:
SignalType.EFFECT_STATIC.isPassive() -> true
SignalType.ATTACK.isPassive()        -> false

// isBehaviorSignal:
SignalType.ENTITY_DEATH.isBehaviorSignal()   -> true
SignalType.PLAYER_NEAR.isBehaviorSignal()    -> true
SignalType.PLAYER_STAND.isBehaviorSignal()   -> true
SignalType.EXPIRE.isBehaviorSignal()         -> true
SignalType.PROJECTILE_HIT.isBehaviorSignal() -> true
SignalType.OWNER_ATTACK.isBehaviorSignal()   -> true
SignalType.OWNER_DEFEND.isBehaviorSignal()   -> true
SignalType.ATTACK.isBehaviorSignal()         -> false
```

## FlowSerializerTest - Config Parsing

```java
// Parse flow config from map
// INPUT:
Map flowMap = {
    "type": "SIGNAL",
    "trigger": "ATTACK",
    "chance": 75.0,
    "cooldown": 5.0,
    "nodes": [...]
}

// OUTPUT:
config.getType()     -> FlowType.SIGNAL
config.getTrigger()  -> "ATTACK"
config.getChance()   -> 75.0
config.getCooldown() -> 5.0

// Parse ability flow (no trigger)
// INPUT:
Map flowMap = {
    "type": "ABILITY",
    "cooldown": 30.0,
    "nodes": [...]
}

// OUTPUT:
config.getType()    -> FlowType.ABILITY
config.getTrigger() -> null
config.getCooldown() -> 30.0
```

## FlowGraphTest - Validation

```java
// Valid graph (has start, end, all connected)
// INPUT: Graph with start->effect->end
// OUTPUT: graph.isValid() -> true

// Invalid: no start node
// INPUT: Graph without start node
// OUTPUT: graph.isValid() -> false

// Invalid: orphan node (not connected)
// INPUT: Graph with orphan node
// OUTPUT: graph.isValid() -> false

// Node count
// INPUT: Add 3 nodes
// OUTPUT: graph.getNodeCount() -> 3
```
