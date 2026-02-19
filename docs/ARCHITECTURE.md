# Arcane Sigils Architecture

This document is the permanent rulebook for the codebase. All new code must follow these patterns. When editing existing code, migrate it toward these targets incrementally.

---

## A. Core Patterns

### Effect Pattern

Effects live in `effects/impl/` and extend `AbstractEffect`.

**Self-describing params**: Effects declare their parameters via `getParamDefinitions()`:

```java
public class DashEffect extends AbstractEffect {
    public DashEffect() {
        super("DASH", "Dashes the player forward");
    }

    @Override
    public List<ParamDefinition> getParamDefinitions() {
        return List.of(
            ParamDefinition.number("distance", "Distance", 5.0, 1, 50),
            ParamDefinition.target("target", "Target", "@Self"),
            ParamDefinition.cycle("direction", "Direction", "FORWARD",
                "FORWARD", "BACKWARD", "TOWARD_TARGET")
        );
    }

    @Override
    public boolean execute(EffectContext context) { ... }
}
```

This replaces the hardcoded switch in `EffectParamHandler.getParamConfig()`. New effects are fully self-contained: one class, one file, done.

**Registration**: Register in `EffectManager.registerEffects()`. Once effects self-describe, no edit to `EffectParamHandler` is needed.

**Re-entry guards**: Effects that call `entity.damage()` or fire signals MUST use:

```java
private static final ThreadLocal<Boolean> EXECUTING = ThreadLocal.withInitial(() -> false);

@Override
public boolean execute(EffectContext context) {
    if (EXECUTING.get()) return false;
    try {
        EXECUTING.set(true);
        // ... damage/signal logic ...
    } finally {
        EXECUTING.set(false);
    }
}
```

**Dependencies**: Effects access the plugin via `getPlugin()` (inherited from `AbstractEffect`). Never call `ArmorSetsPlugin.getInstance()` directly.

### Manager Pattern

Managers are singletons owned by `ArmorSetsPlugin`. New managers should implement `Lifecycle`:

```java
public class MyManager implements Lifecycle {
    @Override
    public void onDisable() { /* cleanup */ }

    @Override
    public void onReload() { /* refresh config */ }
}
```

Register with `plugin.registerComponent(this)` to get automatic shutdown/reload. This avoids editing `ArmorSetsPlugin.onDisable()` for every new manager.

### GUI Handler Pattern

Every handler extends `AbstractHandler` and is registered in `GUIManager.registerHandlers()` with a `GUIType` enum value.

**Session management**: Use `parentSession.deriveChild(GUIType.CHILD)` for child GUIs. Never manually copy flow context keys.

**Item building**: Use `ItemBuilder` factories. Never construct `ItemStack` + `ItemMeta` directly.

**Config editing**: Use `promptConfigEdit()` / `toggleConfigBool()` from `AbstractHandler`.

**Destructive actions**: Use `requireConfirmation()`:

```java
if (!requireConfirmation(player, session,
    "deleteConfirm_" + itemId,
    "Click again to confirm deletion of " + itemName)) return;
```

**Flow saving**: Always use `FlowSaveUtil`:

```java
FlowConfig flowConfig = FlowSaveUtil.resolveFlowConfig(flowConfig, sigil, signalKey);
session.put("flowConfig", flowConfig);
flowConfig.setGraph(graph);
FlowSaveUtil.syncStartNodeToConfig(graph, flowConfig);
FlowSaveUtil.saveFlowToSigil(sigil, flowConfig);
```

**Navigation**: Direct `openGUI()` calls only. No navigation stack.

**Layout conventions**:
- 27-slot (3 rows): Simple selection (palette, picker)
- 54-slot (6 rows): Full editor (config, flow builder, browser)
  - Row 0: Toolbar/header
  - Rows 1-4: Content area
  - Row 5: Navigation (back=45, prev=46, page=49, next=52)

**Sounds**: `playSound(player, "click"|"close"|"success"|"error"|"page")`

**Listener rules**: Use `NORMAL` priority for most handlers, `HIGH` for damage modification (SignalHandler), `MONITOR` only for read-only logging. Check `SignalHandler`/`ArmorChangeListener` before adding new listeners.

### Condition Pattern (Target)

Individual evaluator classes registered in `ConditionManager`, following the same pattern as effects in `EffectManager`. Each condition is self-contained.

---

## B. Dependency Rules

1. **No `getInstance()` in new code.** Effects use `getPlugin()`. Managers receive the plugin via constructor. GUI handlers receive it via `super(plugin, guiManager)`.
2. **No circular package imports.** If package A imports from B, B must not import from A.
3. **Effects don't import from** `gui/`, `binds/`, or `commands/`.
4. **GUI handlers don't contain business logic** — delegate to managers.
5. **Utilities are stateless.** `RarityUtil`, `RomanNumerals`, `TextUtil`, `TargetFinder` — pure functions, no instance state.

---

## C. Target Package Layout

Where classes should migrate to when we touch them:

| Target Package | Classes |
|----------------|---------|
| `behavior/` | BehaviorManager, AuraManager, ProjectileManager |
| `player/` | StunManager, PotionEffectTracker, SkinChangeManager, LastVictimManager |
| `combat/` | CombatUtil, ModifierRegistry, TargetFinder, TargetGlowManager |
| `core/` | Sigil, SigilManager, SocketManager, RarityUtil |
| `effects/` | Effect, EffectManager, EffectContext, ParamDefinition |
| `effects/impl/` | All 44+ effect classes |
| `events/` | SignalHandler, SignalType, ConditionManager, CooldownManager |
| `flow/` | FlowExecutor, FlowSerializer, FlowSaveUtil, FlowConfig |
| `gui/` | GUIManager, GUISession, all handlers (not `binds/gui/` or `enchanter/gui/`) |
| `tier/` | TierScalingCalculator, TierProgressionManager |
| `utils/` | TextUtil, RomanNumerals, LogHelper, ScreenShakeUtil |

---

## D. God Class Decomposition Map

When touching these files, extract the identified component:

| God Class | Extract | Into |
|-----------|---------|------|
| `SignalHandler` | Armor tracking/caching logic | `ArmorTracker` in `events/` |
| `SocketManager` | Lore rendering (buildSigilLore, gradient parsing) | `SigilLoreRenderer` in `core/` |
| `ModifierRegistry` | Mark-specific registry logic | `MarkRegistry` in `combat/` |
| `EffectParamHandler` | 2000-line `getParamConfig()` switch | Replaced by `getParamDefinitions()` on each effect |
| `ArmorSetsPlugin` | 27 null-checked shutdown calls | `Lifecycle` interface + component list |

---

## E. Naming Conventions

| Kind | Pattern | Location | Example |
|------|---------|----------|---------|
| Manager | `XManager` | Package root or domain package | `SigilManager`, `StunManager` |
| Handler | `XHandler` | `gui/` subpackage | `SigilEditorHandler` |
| Effect | `XEffect` | `effects/impl/` | `DashEffect`, `StunEffect` |
| Utility | `XUtil` | `utils/` or `core/` | `RarityUtil`, `TextUtil` |
| Data | Record | Near its consumer | `ParamDefinition`, `FlowConfig` |
| Interface | Descriptive noun | Package root | `Lifecycle`, `Effect` |

---

## F. Incremental Migration Protocol

Every change follows these rules:

1. **New file rule**: Must follow target package layout and naming conventions.
2. **Touch rule**: When editing a file, migrate one thing toward the target (replace a `getInstance()`, use `RarityUtil`, etc.).
3. **Import rule**: When adding imports, fix any forbidden cross-package dependency.
4. **Every commit compiles.** No intermediate broken states.
5. **No Big Bang refactors.** Move files one at a time, always leaving the build green.

### Migration Checklist (when touching a file)

- [ ] Replace `getRarityColor()` with `RarityUtil.getColor()`
- [ ] Replace `toRomanNumeral()` with `RomanNumerals.toRoman()`
- [ ] Replace `getInstance()` with `getPlugin()` or constructor injection
- [ ] Add `getParamDefinitions()` override if it's an effect class
- [ ] Add `implements Lifecycle` if it's a manager with cleanup logic

---

## G. Messages

All player-facing messages MUST use `type: CHAT`, never `type: ACTIONBAR`. Action bars are reserved for transient UI elements only.
