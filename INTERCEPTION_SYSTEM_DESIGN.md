# Effect Interception System Design

## Purpose
A unified event system that allows sigils to intercept and modify effects/modifiers before they're applied to players.

## Use Cases
1. **Ancient Crown** - Intercepts incoming negative potion effects and attribute modifiers, reduces their potency
2. **Cleopatra** - Intercepts outgoing defensive effects (resistance, regen, damage reduction) and blocks them during suppression
3. **Future sigils** - Any effect modification without touching core code

## Architecture

### 1. InterceptionEvent
Fired before an effect/modifier is applied. Contains:
- **Type** - What's being intercepted (POTION_EFFECT, ATTRIBUTE_MODIFIER, etc.)
- **Target** - Player receiving the effect
- **Source** - What's applying the effect (optional)
- **Data** - Effect-specific data (potion type, amplifier, attribute type, modifier value, etc.)
- **Cancelled** - Can be set to block the effect entirely
- **Modifications** - Interceptors can modify the data

```java
public class InterceptionEvent {
    public enum Type {
        POTION_EFFECT,
        ATTRIBUTE_MODIFIER
    }
    
    private final Type type;
    private final Player target;
    private final Object source;
    private boolean cancelled;
    
    // For POTION_EFFECT
    private PotionEffectType potionType;
    private int amplifier;
    private int duration;
    
    // For ATTRIBUTE_MODIFIER
    private Attribute attributeType;
    private AttributeModifier.Operation operation;
    private double value;
    
    // Methods
    public void cancel();
    public void modifyAmplifier(double multiplier); // Reduce by X%
    public void modifyValue(double multiplier); // Reduce by X%
}
```

### 2. EffectInterceptor Interface
Sigils implement this to intercept effects:

```java
public interface EffectInterceptor {
    /**
     * Called before an effect is applied.
     * @param event The interception event
     * @return Priority (higher = runs first)
     */
    InterceptionResult intercept(InterceptionEvent event);
    
    /**
     * Priority for ordering interceptors.
     * Ancient Crown (reduce) = 100 (runs first)
     * Cleopatra (block) = 50 (runs after reduction)
     */
    int getPriority();
}

public class InterceptionResult {
    public static final InterceptionResult PASS = new InterceptionResult(false);
    public static final InterceptionResult CANCEL = new InterceptionResult(true);
    
    private final boolean modified;
    
    public InterceptionResult(boolean modified) {
        this.modified = modified;
    }
    
    public boolean wasModified() {
        return modified;
    }
}
```

### 3. InterceptionManager
Manages interceptors and fires events:

```java
public class InterceptionManager {
    private final Map<UUID, List<EffectInterceptor>> playerInterceptors = new HashMap<>();
    
    /**
     * Register an interceptor for a player.
     * Called when Ancient Crown/Cleopatra is equipped.
     */
    public void registerInterceptor(Player player, EffectInterceptor interceptor);
    
    /**
     * Unregister an interceptor.
     * Called when sigil is unequipped.
     */
    public void unregisterInterceptor(Player player, EffectInterceptor interceptor);
    
    /**
     * Fire interception event before applying effect.
     * @return The modified event (or cancelled)
     */
    public InterceptionEvent fireIntercept(InterceptionEvent event) {
        List<EffectInterceptor> interceptors = playerInterceptors.get(event.getTarget().getUniqueId());
        if (interceptors == null || interceptors.isEmpty()) {
            return event;
        }
        
        // Sort by priority (higher first)
        interceptors.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        for (EffectInterceptor interceptor : interceptors) {
            InterceptionResult result = interceptor.intercept(event);
            if (event.isCancelled()) {
                break; // Stop processing if cancelled
            }
        }
        
        return event;
    }
}
```

### 4. Integration Points

#### PotionEffectApplyListener
```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onPotionEffectApply(EntityPotionEffectEvent event) {
    if (!(event.getEntity() instanceof Player player)) return;
    if (event.getAction() != EntityPotionEffectEvent.Action.ADDED) return;
    
    PotionEffect effect = event.getNewEffect();
    
    // Create interception event
    InterceptionEvent intercept = new InterceptionEvent(
        InterceptionEvent.Type.POTION_EFFECT,
        player,
        null, // Could track source if needed
        effect.getType(),
        effect.getAmplifier(),
        effect.getDuration()
    );
    
    // Fire to interceptors
    intercept = interceptionManager.fireIntercept(intercept);
    
    if (intercept.isCancelled()) {
        event.setCancelled(true);
        return;
    }
    
    // Apply modified effect if changed
    if (intercept.wasModified()) {
        event.setCancelled(true);
        player.addPotionEffect(new PotionEffect(
            intercept.getPotionType(),
            intercept.getDuration(),
            intercept.getAmplifier()
        ));
    }
}
```

#### AttributeModifierListener
```java
// Note: No direct Bukkit event for attribute modifiers
// Need to hook into our own MODIFY_ATTRIBUTE effect and any other places attributes are modified
```

### 5. Sigil Implementations

#### Ancient Crown Interceptor
```java
public class AncientCrownInterceptor implements EffectInterceptor {
    private final int tier;
    private final double immunityPercent; // 20-100%
    
    @Override
    public InterceptionResult intercept(InterceptionEvent event) {
        if (event.getType() == InterceptionEvent.Type.POTION_EFFECT) {
            if (isNegativeEffect(event.getPotionType())) {
                // Reduce amplifier by immunity %
                event.modifyAmplifier(1.0 - (immunityPercent / 100.0));
                return new InterceptionResult(true);
            }
        } else if (event.getType() == InterceptionEvent.Type.ATTRIBUTE_MODIFIER) {
            if (isNegativeModifier(event.getAttributeType(), event.getValue())) {
                // Reduce value by immunity %
                event.modifyValue(1.0 - (immunityPercent / 100.0));
                return new InterceptionResult(true);
            }
        }
        return InterceptionResult.PASS;
    }
    
    @Override
    public int getPriority() {
        return 100; // Run first (before suppressors)
    }
    
    private boolean isNegativeEffect(PotionEffectType type) {
        return type == PotionEffectType.POISON ||
               type == PotionEffectType.WITHER ||
               type == PotionEffectType.SLOW ||
               type == PotionEffectType.WEAKNESS ||
               // ... etc
    }
    
    private boolean isNegativeModifier(Attribute attr, double value) {
        // Negative values for movement speed, attack damage, etc.
        return value < 0;
    }
}
```

#### Cleopatra Suppression Interceptor
```java
public class CleopatraSuppressionInterceptor implements EffectInterceptor {
    private final Player target; // Who is suppressed
    
    @Override
    public InterceptionResult intercept(InterceptionEvent event) {
        // Only intercept effects on the suppressed target
        if (!event.getTarget().equals(target)) {
            return InterceptionResult.PASS;
        }
        
        if (event.getType() == InterceptionEvent.Type.POTION_EFFECT) {
            PotionEffectType type = event.getPotionType();
            if (type == PotionEffectType.RESISTANCE || 
                type == PotionEffectType.REGENERATION) {
                event.cancel();
                return new InterceptionResult(true);
            }
        } else if (event.getType() == InterceptionEvent.Type.ATTRIBUTE_MODIFIER) {
            // Block damage reduction modifiers
            if (isDamageReductionModifier(event)) {
                event.cancel();
                return new InterceptionResult(true);
            }
        }
        
        return InterceptionResult.PASS;
    }
    
    @Override
    public int getPriority() {
        return 50; // Run after reducers
    }
}
```

## Lifecycle

### Ancient Crown Equipped
1. Player equips Ancient Crown helmet
2. Create AncientCrownInterceptor(tier)
3. Register interceptor with InterceptionManager
4. Any negative effects/modifiers → intercepted → reduced

### Ancient Crown Unequipped
1. Player removes Ancient Crown
2. Unregister AncientCrownInterceptor
3. Effects apply normally again

### Cleopatra Activated
1. Player uses Cleopatra ability on target
2. Strip target's defensive buffs
3. Create CleopatraSuppressionInterceptor(target, duration)
4. Register interceptor
5. Target cannot receive resistance/regen/DR for duration
6. After duration: Unregister interceptor automatically

## Benefits
1. **Centralized** - All effect interception in one place
2. **Extensible** - New sigils can easily add interceptors
3. **Priority-based** - Reducers run before blockers
4. **Clean** - No flag checks scattered in effect code
5. **Testable** - Can unit test interceptors independently

## Future Expansion
- Intercept damage events (modify incoming/outgoing damage)
- Intercept healing events
- Intercept teleportation
- Intercept any game mechanic with similar pattern
