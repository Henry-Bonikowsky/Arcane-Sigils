# Arcane Sigils Unit Tests

Comprehensive unit tests for the Arcane Sigils plugin flow builder and sigil activation system.

## Test Directory Structure

```
src/test/java/com/zenax/armorsets/
├── flow/
│   ├── FlowContextTest.java      - Tests for expression resolution and variable management
│   ├── FlowExecutorTest.java     - Tests for flow graph execution
│   ├── FlowGraphTest.java        - Tests for graph structure and validation
│   ├── FlowSerializerTest.java   - Tests for YAML serialization/deserialization
│   └── nodes/
│       └── NodeTypesTest.java    - Tests for all 11 node type implementations
├── events/
│   ├── CooldownManagerTest.java  - Tests for ability cooldown tracking
│   ├── ConditionTypeTest.java    - Tests for condition string parsing
│   └── SignalActivationTest.java - Tests for sigil activation flow
└── core/
    └── SigilLoadingTest.java     - Tests for sigil YAML loading
```

## Test Files Overview

### Flow System Tests

#### `FlowContextTest.java`
Tests the FlowContext class which provides execution state for flow graphs.
- **Variable Management**: setVariable, getVariable, getVariableAsDouble, hasVariable
- **Expression Resolution**: resolveExpression with {placeholders}, resolveNumeric
- **Tier Scaling**: Integration with TierScalingConfig for {param} resolution
- **Condition Evaluation**: Comparison operators (<, >, <=, >=, ==, !=), boolean literals, random(X%)
- **Context State**: player, target, location, cancelled state, error handling
- **Effects Tracking**: effectsExecuted counter, skipCooldown flag

#### `FlowGraphTest.java`
Tests the FlowGraph class which is the container for flow nodes.
- **Basic Properties**: id, name, description, version
- **Node Management**: addNode, removeNode, getNode, hasNode, clear, generateNodeId
- **Start Node**: setStartNodeId, getStartNode
- **Connections**: connect, disconnect, getIncomingNodes
- **Validation**: detect missing start node, unreachable nodes, node-level errors
- **Position**: getNodeAt, isPositionOccupied
- **Deep Copy**: independent copy with preserved structure

#### `FlowExecutorTest.java`
Tests the FlowExecutor which runs flow graphs.
- **Basic Execution**: execute, executeWithContext, null/invalid graph handling
- **Condition Branching**: Following yes/no paths based on condition result
- **Variable Operations**: SET, ADD, SUBTRACT, MULTIPLY, DIVIDE through flows
- **Loop Execution**: COUNT and WHILE loop modes
- **Skip Cooldown**: SkipCooldownNode flag setting
- **Error Handling**: Missing nodes, infinite loop protection
- **Complex Flows**: Nested conditions, conditions in loops, converging paths

#### `FlowSerializerTest.java`
Tests serialization/deserialization of flows to/from YAML maps.
- **FlowGraph fromMap**: Basic properties, nodes list, positions, params, connections
- **FlowConfig**: SIGNAL vs ABILITY types, triggers, cooldown, chance, priority, conditions
- **Round Trip**: Verify data survives serialize->deserialize cycle
- **Node Types**: All 7 node types deserialize correctly
- **Connection Formats**: "next" shorthand vs "connections" map

#### `NodeTypesTest.java`
Tests all 7 flow node implementations.
- **StartNode**: Type, output ports, execute returns "next"
- **ConditionNode**: "yes"/"no" outputs, condition evaluation, validation
- **LoopNode**: COUNT and WHILE modes, iteration tracking, body/done outputs
- **VariableNode**: SET/ADD/SUBTRACT/MULTIPLY/DIVIDE operations
- **DelayNode**: Duration parameter, returns "next"
- **SkipCooldownNode**: Sets skipCooldown flag, returns null
- **EffectNode**: Effect execution wrapper
- **FlowNode Base**: Parameter getters, connections, tier values

### Event System Tests

#### `CooldownManagerTest.java`
Tests the cooldown tracking system.
- **Basic Cooldowns**: isOnCooldown, setCooldown, per-ability tracking
- **Remaining Time**: getRemainingCooldown, time-based expiry
- **Global Cooldown**: Short cooldown that affects all abilities
- **Clear Operations**: clearCooldowns (per player), clearAll
- **Override Behavior**: Newer cooldowns replace older ones
- **Name Formatting**: snake_case to Title Case conversion

#### `ConditionTypeTest.java`
Tests condition string parsing (doesn't require running server).
- **Parsing**: Simple conditions, parameters, multiple parameters
- **Comparison Operators**: <, >, <=, >=, extraction from strings
- **Known Types**: Boolean conditions, numeric comparisons, enum values
- **Target Specifiers**: @Self, @Victim, @Target parsing
- **Potion Conditions**: HAS_POTION, NO_POTION, amplifier checks
- **Mark Conditions**: HAS_MARK with optional target
- **Time/Weather**: TIME:NIGHT, WEATHER:RAINING parsing

#### `SignalActivationTest.java`
Tests the sigil activation flow from signal to effect execution.
- **Signal Types**: All 17 signal types recognized
- **Trigger Normalization**: ON_ prefix removal, alias handling
- **Flow Matching**: Finding flows by trigger, handling multiple matches
- **Activation Chance**: 0%, 50%, 100% chance testing
- **Priority Sorting**: Highest priority first
- **Condition Logic**: AND/OR evaluation
- **Ability Flows**: ABILITY vs SIGNAL type distinction

### Core System Tests

#### `SigilLoadingTest.java`
Tests YAML-based sigil configuration loading.
- **FlowConfig Parsing**: Complete flow configurations from maps
- **Tier Scaling**: Parameter-based scaling configuration
- **Condition Logic**: AND/OR logic for condition lists
- **Complex Flows**: Multi-node graphs with branches and loops

## Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=FlowContextTest

# Run tests with verbose output
mvn test -Dsurefire.useFile=false
```

## Dependencies

- JUnit Jupiter 5.10.2
- Mockito 5.11.0
- AssertJ 3.25.3
- MockBukkit-v1.21 3.93.2 (for Bukkit API mocking)

## Coverage

These tests cover:
- All 11 node types in the flow builder
- Complete expression resolution system
- Full serialization/deserialization cycle
- Cooldown tracking and expiry
- Condition parsing and evaluation
- Graph validation and connectivity

## Moving Tests

To move these tests to a different directory:
1. Copy the entire `src/test/` directory
2. Update package declarations if needed
3. Ensure pom.xml test dependencies are present
4. Run `mvn test` to verify

## Notes

- Tests use Mockito for mocking Bukkit API objects (Player, World, Location, etc.)
- FlowContext tests mock the EffectContext to avoid Bukkit dependency
- CooldownManager tests verify timing behavior with Thread.sleep()
- All tests are designed to run without a Minecraft server
