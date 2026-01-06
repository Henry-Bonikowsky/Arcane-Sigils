# Test Results - 2026-01-02

**Total: 444 tests | 0 failures | 0 errors | 0 skipped**

## Summary by Test Class

| Test Class | Tests | Status |
|------------|-------|--------|
| SigilLoadingTest | 16 | PASS |
| ConditionTypeTest | 99 | PASS |
| CooldownManagerTest | 10 | PASS |
| SignalActivationTest | 67 | PASS |
| FlowContextTest | 48 | PASS |
| FlowExecutorTest | 24 | PASS |
| FlowGraphTest | 46 | PASS |
| FlowSerializerTest | 34 | PASS |
| NodeTypesTest | 77 | PASS |
| **SigilFlowIntegrationTest** | **23** | **PASS** |

---

## Integration Tests (NEW)

These tests verify the **entire flow system works end-to-end** with actual YAML configs.

### SigilFlowIntegrationTest (23 tests)

#### YAML Loading Tests (3 tests)
Verifies all sigil YAML files load correctly.

| Test | Input | Output |
|------|-------|--------|
| `testDummySigilsLoad` | `/sigils/test-dummy.yml` | Contains: `test_dummy`, `test_damage_boost`, `test_lifesteal`, `test_stun`, `test_mark`, `test_mark_explode` |
| `testDefaultSigilsLoad` | `/sigils/default-sigils.yml` | Contains: `iron_forged`, `lifeforce`, `refurbish`, `extra_padding`, `iron_fist`, `rocket_boots`, `meal_planning`, `spring_shoes`, `dasher`, `sky_stepper` |
| `testPharaohSigilsLoad` | `/sigils/pharaoh-set.yml` | Contains: `pharaoh_curse`, `sandstorm`, `royal_bolster`, `quick_sand`, `rulers_hand`, `royal_guard` |

#### FlowConfig Parsing Tests (4 tests)
Verifies YAML flow configs parse into correct FlowConfig objects.

| Test | Input YAML | Expected Output |
|------|------------|-----------------|
| `testDamageBoostFlowParsing` | `test_damage_boost.flow` | type=SIGNAL, trigger="ATTACK", chance=100.0, nodeCount=5 |
| `testDummyAbilityFlowParsing` | `test_dummy.flow` | type=ABILITY, trigger=null, cooldown=5.0 |
| `testDasherConditions` | `dasher.flow` | trigger="SHIFT", conditions contains "IN_AIR" |
| `testRulersHandConditions` | `rulers_hand.flows[0]` | trigger="ATTACK", conditions match "HAS_MARK:*" |

#### Effect Node Parsing Tests (3 tests)
Verifies effect nodes have correct types and parameters.

| Test | Input Node | Expected Output |
|------|------------|-----------------|
| `testSpawnEntityEffect` | `test_dummy.spawn` | effectType="SPAWN_ENTITY", params: entity_type="IRON_GOLEM", hp=500, target="@Target" |
| `testDamageBoostEffect` | `test_damage_boost.damage` | effectType="DAMAGE_BOOST", params: percent=50 |
| `testParticleEffectWithShape` | `pharaoh_curse.*` | effectType="PARTICLE", shape in [spiral, helix, circle, sphere, disc, pulse] |

#### Flow Graph Structure Tests (3 tests)
Verifies all flow graphs have valid structure.

| Test | What It Checks |
|------|----------------|
| `testAllFlowsHaveValidStructure` | All 27 sigils across 3 files have valid flow graphs |
| `testFlowsHaveStartAndEnd` | `test_damage_boost` has both START and END nodes |
| `testNodeConnectionsValid` | `test_stun` - all node connections point to existing nodes |

#### Flow Execution Tests (4 tests)
Verifies nodes execute correctly with expected outputs.

| Test | Input | Expected Output |
|------|-------|-----------------|
| `testSimpleFlowWalkthrough` | START -> VARIABLE(testValue=42) -> END | context.getVariable("testValue") = 42.0 |
| `testConditionBranching` | ConditionNode("5 > 3") | returns "yes" (condition is true) |
| `testLoopExecution` | LoopNode(count=3) executed 4 times | "body", "body", "body", "done" |
| `testMathNodeInFlow` | MathNode(6 * 7) | context.getVariableAsDouble("answer") = 42.0 |

#### Tier Scaling Tests (3 tests)
Verifies tier parameter resolution works correctly.

| Test | Input | Expected Output |
|------|-------|-----------------|
| `testTierValuesFromConfig` | `dasher.tier` | Contains keys: "mode", "params" |
| `testTierParameterResolution` | tier=3, values=[10,15,20,25,30] | tierValue = 20.0 (index 2) |
| `testContextTierPlaceholder` | tier=5, expression="{tier}" | resolves to "5" |

#### Signal Type Verification Tests (1 test)
Verifies all triggers in YAML files are valid SignalTypes.

| Test | Input Files | Valid Triggers Found |
|------|-------------|---------------------|
| `testAllTriggersAreValidSignalTypes` | all 3 sigil files | ATTACK, DEFENSE, SHIFT, TICK, EFFECT_STATIC, ITEM_BREAK, BLOCK_BREAK, FISH_CATCH, BOW_HIT, KILL_PLAYER |

Note: "flow" trigger is internal (for flows triggered by other flows).

#### Condition Verification Tests (1 test)
Verifies all conditions use valid ConditionType enums.

| Test | Input Files | Valid Conditions Found |
|------|-------------|----------------------|
| `testAllConditionsAreValid` | all 3 sigil files | IN_AIR, HAS_MARK, etc. (all parse to valid ConditionType) |

#### Effect Type Verification Tests (1 test)
Verifies all effects in YAML files are valid effect types.

| Test | Input Files | Valid Effects Found |
|------|-------------|---------------------|
| `testAllEffectsAreValid` | all 3 sigil files | SPAWN_ENTITY, DAMAGE_BOOST, HEAL, PARTICLE, SOUND, MESSAGE, STUN, MARK, LIFESTEAL, POTION, MODIFY_ATTRIBUTE, etc. |

---

## Bugs Found and Fixed

### 1. FISH_CATCH trigger alias missing
**Issue:** pharaoh-set.yml uses `trigger: FISH_CATCH` but SignalType enum only had `FISH`
**Fix:** Added alias in `SignalType.fromConfigKey()`:
```java
if (upperKey.equals("FISH_CATCH")) {
    return FISH;
}
```

### 2. Test assertions didn't match actual YAML structure
**Issues Found:**
- `testDamageBoostFlowParsing` expected 4 nodes, actual YAML has 5 (start, damage, particle, msg, end)
- `testDamageBoostEffect` looked for node ID "damage_boost", but actual ID is "damage"
- `testRulersHandConditions` used `flow:` but rulers_hand uses `flows:` (list format)

**All fixed by correcting test assertions to match actual YAML.**

---

## Unit Test Details

### SigilLoadingTest (16 tests)
- FlowValidationTests: 3 tests
- PharaohStyleLoadingTests: 2 tests
- MultipleFlowsTests: 1 test
- TierScalingTests: 3 tests
- ComplexFlowLoadingTests: 3 tests
- FlowConfigLoadingTests: 4 tests

### ConditionTypeTest (99 tests)
- EnumVerificationTests: 38 tests (verifies all 35 enum values exist)
- KnownConditionTypesTests: 26 tests
- ComparisonOperatorTests: 6 tests
- SignalConditionTests: 5 tests
- ConditionParsingTests: 4 tests
- TimeConditionTests: 4 tests
- EdgeCaseTests: 4 tests
- WeatherConditionTests: 3 tests
- TargetSpecifierTests: 3 tests
- PotionConditionTests: 3 tests
- MarkConditionTests: 2 tests
- VictimConditionTests: 1 test

### CooldownManagerTest (10 tests)
- NullBehaviorTests: 4 tests
- MethodContractTests: 4 tests
- InstantiationTests: 2 tests

### SignalActivationTest (67 tests)
- SignalTypeEnumVerificationTests: 29 tests (verifies all 23 enum values exist)
- SignalTypeParsingTests: 16 tests
- TriggerNormalizationTests: 7 tests
- FlowMatchingTests: 3 tests
- ChanceTests: 3 tests
- ConditionLogicTests: 3 tests
- PriorityTests: 2 tests
- ActivationFlowTests: 2 tests
- AbilityFlowTests: 2 tests

### FlowContextTest (48 tests)
- ConditionEvaluationTests: 19 tests
- VariableManagementTests: 9 tests
- ExpressionResolutionTests: 9 tests
- EffectsTrackingTests: 4 tests
- EdgeCaseTests: 3 tests
- TierScalingTests: 2 tests
- ContextStateTests: 2 tests

### FlowExecutorTest (24 tests)
- GraphStructureTests: 4 tests
- ValidationTests: 4 tests
- MathOperationsTests: 3 tests
- LoopExecutionTests: 3 tests
- VariableOperationsTests: 3 tests
- ConditionBranchingTests: 3 tests
- ComplexFlowTests: 2 tests
- SkipCooldownTests: 2 tests

### FlowGraphTest (46 tests)
- NodeManagementTests: 12 tests
- ConnectionTests: 8 tests
- ValidationTests: 7 tests
- BasicPropertiesTests: 5 tests
- StartNodeTests: 4 tests
- PositionTests: 4 tests
- ComplexFlowTests: 3 tests
- DeepCopyTests: 3 tests

### FlowSerializerTest (34 tests)
- FlowGraphFromMapTests: 11 tests
- FlowConfigTests: 8 tests
- FlowConfigToMapTests: 5 tests
- NodeTypeSpecificTests: 4 tests
- MultipleFlowsTests: 4 tests
- RoundTripTests: 2 tests

### NodeTypesTest (77 tests)
- MathNodeTests: 18 tests (includes edge cases: div/mod by zero, sqrt negative)
- VariableNodeTests: 14 tests
- ConditionNodeTests: 9 tests
- FlowNodeBaseTests: 8 tests
- LoopNodeTests: 8 tests
- RandomNodeTests: 4 tests
- SkipCooldownNodeTests: 4 tests
- DelayNodeTests: 4 tests
- StartNodeTests: 4 tests
- EndNodeTests: 4 tests

---

## Raw Maven Output

```
Tests run: 444, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 1.668 s
```
