# ArcaneSigils Java API Design

## Goal
Replace the broken scoreboard-based state exposure with a clean Java API that external plugins (MinimalAI) can use to query and control sigils.

## Decisions
- **Slot model**: Bind slots (player's active bind system, 1-9 hotbar or 1-27 command)
- **Architecture**: Thin Facade - API impl delegates to existing managers, live reads, no caching
- **AI package**: Remove entire `ai/` package (AITrainingManager, ScoreboardUpdateTask, BindStateTracker, TargetStateTracker, AITrainingConfig)
- **Target info**: Expose entity references only (getSelectedTarget, getLastVictim), no convenience stats
- **API artifact**: Same JAR, `com.miracle.arcanesigils.api` package. External plugins use compileOnly dependency.

## API Interface

```java
package com.miracle.arcanesigils.api;

public interface ArcaneSigilsAPI {

    // --- Equipped Sigils ---
    List<SigilInfo> getEquippedSigils(Player player);

    // --- Bind Slot Queries ---
    boolean isSigilReady(Player player, int bindSlot);
    double getCooldownProgress(Player player, int bindSlot);   // 0.0=ready, 1.0=full
    double getCooldownRemaining(Player player, int bindSlot);  // seconds
    double getMaxCooldown(Player player, int bindSlot);        // seconds
    int getTier(Player player, int bindSlot);
    String getSigilType(Player player, int bindSlot);          // "pharaoh_curse", etc.
    String getActivationType(Player player, int bindSlot);     // "passive","ability","auto_attack","auto_defense"

    // --- Ability Activation ---
    boolean activateAbility(Player player, int bindSlot);

    // --- Combat State ---
    double getDamageAmplifier(Player player);    // multiplier (1.0 = none)
    double getDamageReduction(Player player);    // multiplier (1.0 = none)

    // --- Specific Sigil State ---
    int getKingsBraceCharges(Player player);
    int getInvulnHits(Player player);

    // --- Marks ---
    boolean isMarked(Player target, Player attacker);
    boolean hasMark(LivingEntity entity, String markName);
    List<MarkInfo> getActiveMarks(LivingEntity entity);
    double getMarkDamageMultiplier(LivingEntity entity);

    // --- Targets ---
    LivingEntity getSelectedTarget(Player player);  // @Target (bind menu)
    LivingEntity getLastVictim(Player player);       // @Victim (last punched)
}
```

## Data Records

```java
public record SigilInfo(
    String id,              // "pharaoh_curse"
    String name,            // display name with colors
    int tier,               // 1-5
    String slot,            // "HELMET", "CHESTPLATE", etc.
    String activationType   // "passive", "ability", "auto_attack", "auto_defense"
) {}

public record MarkInfo(
    String name,            // "PHARAOH_MARK", "DAMAGE_AMPLIFICATION", etc.
    double multiplier,      // damage multiplier (1.0 = neutral)
    long expiryTimeMs,      // System.currentTimeMillis() expiry (Long.MAX_VALUE = permanent)
    UUID ownerUUID          // who applied it (null for multi-source marks)
) {}
```

## Access Point
```java
ArcaneSigilsAPI api = ArcaneSigils.getAPI();
```

Static getter on main plugin class. Returns null if plugin not enabled.

## Implementation: Thin Facade

`ArcaneSigilsAPIImpl` delegates to:
- `SocketManager` - equipped sigils, tier lookup
- `CooldownManager` - cooldown state
- `BindsManager` / `PlayerBindData` - bind slot → sigil ID resolution
- `SigilManager` - sigil definitions (flows, activation types)
- `DamageAmplificationEffect` - static getDamageAmplification()
- `DamageReductionBuffEffect` - static getDamageReduction()
- `SigilVariableManager` - King's Brace charges
- `InvulnerabilityHitsEffect` - static isInvulnerable() + hits remaining
- `MarkManager` - mark queries
- `TargetGlowManager` - selected target
- `LastVictimManager` - last victim

`activateAbility()` - Extract core logic from `BindsListener.activateSigilWithItem()` into a shared method callable from both BindsListener and the API.

## Removal Scope

### Delete entirely:
- `ai/AITrainingManager.java`
- `ai/AITrainingConfig.java`
- `ai/ScoreboardUpdateTask.java`
- `ai/BindStateTracker.java`
- `ai/TargetStateTracker.java`

### Remove references to ai/ package from:
- `ArmorSetsPlugin.java` - field + initialization
- `BindsListener.java` - AI training signal calls
- `SignalHandler.java` - kill signal calls
- `StunManager.java` - crowd control signal calls
- `config.yml` - ai_training section
- `plugin.yml` - if referenced

### Keep:
- `CollisionDisabler.java` - uses scoreboard teams, unrelated to sigil state
