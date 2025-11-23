# ArmorSets Plugin - Developer Guide

Guide for developers extending or contributing to the plugin.

---

## Table of Contents

1. [Project Structure](#project-structure)
2. [Architecture Overview](#architecture-overview)
3. [Building & Setup](#building--setup)
4. [Creating New Effect Types](#creating-new-effect-types)
5. [Adding Event Triggers](#adding-event-triggers)
6. [Integration Points](#integration-points)
7. [Code Style & Conventions](#code-style--conventions)
8. [Testing](#testing)

---

## Project Structure

```
src/main/
├── java/com/zenax/armorsets/
│   ├── ArmorSetsPlugin.java                    # Main plugin class
│   ├── config/
│   │   ├── ConfigManager.java                  # Load/save configs
│   │   ├── CoreFunctionConfig.java
│   │   ├── SetConfig.java
│   │   └── WeaponConfig.java
│   ├── core/
│   │   ├── CoreFunction.java                   # Function data model
│   │   ├── CoreFunctionManager.java            # Manager for functions
│   │   ├── SocketManager.java                  # Socket/unsocket logic
│   │   └── SocketListener.java                 # Handle socket events
│   ├── effects/
│   │   ├── Effect.java                         # Interface for all effects
│   │   ├── EffectContext.java                  # Context passed to effects
│   │   ├── EffectManager.java                  # Registry & executor
│   │   ├── EffectParams.java                   # Effect parameters
│   │   └── impl/
│   │       ├── AbstractEffect.java             # Base effect class
│   │       ├── DamageEffect.java               # Damage increase
│   │       ├── HealEffect.java                 # Healing
│   │       ├── ParticleEffect.java             # Particles
│   │       ├── SoundEffect.java                # Sound effects
│   │       └── (40+ more effect implementations)
│   ├── events/
│   │   ├── TriggerType.java                    # Enum of triggers
│   │   ├── TriggerHandler.java                 # Listens to Bukkit events
│   │   └── CooldownManager.java                # Per-player cooldown tracking
│   ├── sets/
│   │   ├── ArmorSet.java                       # Set data model
│   │   ├── SetManager.java                     # Manager for sets
│   │   ├── SetSynergy.java                     # Synergy data model
│   │   └── SetSynergyHandler.java              # Synergy trigger logic
│   ├── weapons/
│   │   ├── CustomWeapon.java                   # Weapon data model
│   │   └── WeaponManager.java                  # Manager for weapons
│   ├── gui/
│   │   ├── GUIManager.java                     # GUI system manager
│   │   ├── SocketGUI.java                      # Socket inventory GUI
│   │   └── GUIClickHandler.java                # Handle clicks
│   ├── commands/
│   │   └── ArmorSetsCommand.java               # Command handler
│   └── utils/
│       ├── TextUtil.java                       # Text/color utilities
│       ├── NBTUtil.java                        # NBT data helpers
│       └── TargetUtil.java                     # Target selection
├── resources/
│   ├── plugin.yml                              # Plugin metadata
│   ├── config.yml                              # Default config
│   ├── messages.yml                            # Default messages
│   ├── core-functions/
│   │   └── *.yml
│   ├── sets/
│   │   └── *.yml
│   └── weapons/
│       └── *.yml
└── test/
    └── java/com/zenax/armorsets/
        └── (Unit tests)
```

---

## Architecture Overview

### Manager Pattern

The plugin uses a manager pattern for each subsystem:

```
ArmorSetsPlugin
├── ConfigManager         (Load/save configs)
├── EffectManager         (Manage effects)
├── CooldownManager       (Track cooldowns)
├── CoreFunctionManager   (Manage functions)
├── SetManager            (Manage armor sets)
├── WeaponManager         (Manage weapons)
├── TriggerHandler        (Listen for events)
└── GUIManager            (Handle GUIs)
```

Each manager:
- Has one responsibility
- Provides public API methods
- Manages data for that subsystem
- Accessible via `ArmorSetsPlugin.getInstance().get<Manager>()`

### Effect Execution Flow

```
Event Fires (PlayerAttackEntityEvent)
    ↓
TriggerHandler detects event
    ↓
Find player's armor set & weapon
    ↓
Check if effects registered for ATTACK trigger
    ↓
For each effect:
    - Check cooldown (is it ready?)
    - Check chance (does 30% trigger?)
    - Parse effect string
    - Get Effect impl from EffectManager
    - Create EffectContext
    - Call Effect.execute(context)
    ↓
Effect applies to target/player
```

### Data Flow

```
Config File (YAML)
    ↓
ConfigManager.load()
    ↓
ArmorSet / CoreFunction / CustomWeapon object
    ↓
Manager (SetManager, CoreFunctionManager, etc)
    ↓
TriggerHandler checks conditions
    ↓
EffectManager.executeEffects()
    ↓
Individual Effect implementations
```

---

## Building & Setup

### Development Environment

1. **Install Java 21 SDK**
   ```bash
   # Windows
   choco install openjdk21

   # macOS
   brew install openjdk@21
   ```

2. **Install Maven**
   ```bash
   # Windows
   choco install maven

   # macOS
   brew install maven
   ```

3. **Clone/Download Project**
   ```bash
   git clone <repo>
   cd Custom\ ArmorWeapon\ Plugin
   ```

4. **Open in IDE**

   **IntelliJ IDEA:**
   - File → Open → Select plugin folder
   - Maven panel auto-appears on right
   - Wait for indexing to finish

   **Eclipse:**
   - File → Import → Maven → Existing Maven Projects
   - Select plugin folder

   **VS Code:**
   - Install "Extension Pack for Java"
   - Open folder
   - Maven auto-detected

5. **Build**
   ```bash
   mvn clean package -DskipTests
   ```

### Run Tests

```bash
mvn test
```

### Debug in IDE

1. Set breakpoint in code (click line number)
2. Right-click project → Debug As → Maven build
3. Debug with breakpoints

---

## Creating New Effect Types

### 1. Create Effect Class

```java
package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.AbstractEffect;
import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import org.bukkit.entity.Player;

public class CustomEffect extends AbstractEffect {

    @Override
    public String getId() {
        return "CUSTOM_EFFECT";
    }

    @Override
    public String getDescription() {
        return "My custom effect does something cool";
    }

    @Override
    public EffectParams parseParams(String effectString) {
        // Parse "CUSTOM_EFFECT:param1:param2" format
        String[] parts = effectString.split(":");
        EffectParams params = new EffectParams(getId());

        if (parts.length > 1) {
            params.setParameter("param1", parts[1]);
        }
        if (parts.length > 2) {
            params.setParameter("param2", parts[2]);
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();

        if (player == null) {
            return false;
        }

        // Your effect logic here
        player.sendMessage("Custom effect triggered!");

        return true;
    }
}
```

### 2. Register in EffectManager

In `EffectManager.registerDefaultEffects()`:

```java
registerEffect(new CustomEffect());
```

### 3. Usage in YAML

```yaml
effects:
  - "CUSTOM_EFFECT:value1:value2"
```

### Effect Class Pattern

All effects inherit from `AbstractEffect`:

```java
public abstract class AbstractEffect implements Effect {

    // Required methods
    public abstract String getId();
    public abstract String getDescription();
    public abstract EffectParams parseParams(String effectString);
    public abstract boolean execute(EffectContext context);

    // Optional helper methods
    protected void sendDebugMessage(String message) { ... }
    protected boolean hasCooldown(Player player) { ... }
}
```

### EffectContext

Contains all context about where effect is happening:

```java
public class EffectContext {
    private Player player;              // The player triggering effect
    private TriggerType triggerType;    // What triggered it
    private Event bukkitEvent;          // Original Bukkit event
    private LivingEntity victim;        // Target entity (if attack)
    private Location location;          // Where effect happens
    private double damage;              // Damage amount (if damage event)
    private EffectParams params;        // Parsed effect parameters
    private Map<String, Object> metadata; // Custom data
}
```

### Common Effect Patterns

```java
// Damage target
context.getVictim().damage(amount);

// Heal player
context.getPlayer().setHealth(
    Math.min(20, context.getPlayer().getHealth() + amount)
);

// Apply potion effect
context.getPlayer().addPotionEffect(
    new PotionEffect(PotionEffectType.SPEED, duration * 20, level)
);

// Spawn particles
context.getPlayer().getWorld().spawnParticle(
    Particle.SOUL,
    context.getPlayer().getLocation(),
    count
);

// Play sound
context.getPlayer().playSound(
    context.getPlayer().getLocation(),
    Sound.ENTITY_ENDERMAN_TELEPORT,
    1.0f, 1.0f
);

// Get effect parameter
String param = context.getParams().getParameter("param1");
```

---

## Adding Event Triggers

### 1. Add Trigger Type

In `TriggerType.java`:

```java
public enum TriggerType {
    ATTACK("on_attack", "Player attacks"),
    DEFENSE("on_defense", "Player takes damage"),
    // ... existing ones
    NEW_TRIGGER("on_new_trigger", "When new thing happens");

    private final String configKey;
    private final String description;

    TriggerType(String configKey, String description) {
        this.configKey = configKey;
        this.description = description;
    }
}
```

### 2. Add Listener in TriggerHandler

```java
public class TriggerHandler implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNewEvent(SomeNewEvent event) {
        Player player = event.getPlayer();

        // Get active set
        ArmorSet set = setManager.getActiveSet(player);
        if (set == null) return;

        // Check if set has this trigger
        Map<String, TriggerData> triggers = set.getTriggers();
        TriggerData trigger = triggers.get(TriggerType.NEW_TRIGGER.getConfigKey());
        if (trigger == null) return;

        // Execute trigger
        executeTrigger(player, TriggerType.NEW_TRIGGER, trigger, event);
    }

    private void executeTrigger(Player player, TriggerType type,
                                TriggerData trigger, Event event) {
        // Check cooldown
        if (!cooldownManager.canTrigger(player, type)) {
            return;
        }

        // Check chance
        if (Math.random() * 100 > trigger.getChance()) {
            return;
        }

        // Create context
        EffectContext context = EffectContext.builder(player, type)
                .event(event)
                .build();

        // Execute effects
        effectManager.executeEffects(trigger.getEffects(), context);

        // Set cooldown
        cooldownManager.setCooldown(player, type, trigger.getCooldown());
    }
}
```

---

## Integration Points

### PlaceholderAPI Integration

If PlaceholderAPI is installed, custom placeholders can be used in messages:

```yaml
equipped_message:
  - "Welcome %player_name%!"
  - "You have %player_health% health"
```

**To add custom placeholders:** Implement `me.clip.placeholderapi.expansion.PlaceholderExpansion`

### WorldGuard Integration

Effects respect WorldGuard PvP flags:

```java
if (config.isWorldGuardEnabled()) {
    RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
    ApplicableRegionSet regions = container.getApplicableRegions(location);

    if (!regions.testState(null, Flags.PVP)) {
        // PvP disabled in this region, don't apply effect
        return false;
    }
}
```

### Vault Integration (Economy)

Can integrate with Vault for economy:

```java
Plugin vaultPlugin = Bukkit.getPluginManager().getPlugin("Vault");
RegisteredServiceProvider<Economy> economyProvider =
    Bukkit.getServicesManager().getRegistration(Economy.class);

if (economyProvider != null) {
    Economy economy = economyProvider.getProvider();
    economy.withdrawPlayer(player, amount);
}
```

---

## Code Style & Conventions

### Naming

```java
// Classes: PascalCase
public class ArmorSetManager { }

// Methods: camelCase
public void loadConfigs() { }

// Constants: UPPER_CASE_WITH_UNDERSCORES
public static final int DEFAULT_COOLDOWN = 0;

// Variables: camelCase
int playerCount = 0;
String itemName = "sword";
```

### Code Organization

```java
public class ExampleClass {
    // 1. Constants
    private static final int CONSTANT = 5;

    // 2. Static variables
    private static Map<String, ExampleClass> cache;

    // 3. Instance variables
    private final Plugin plugin;
    private String data;

    // 4. Constructor
    public ExampleClass(Plugin plugin) { }

    // 5. Public methods
    public void publicMethod() { }

    // 6. Private methods
    private void privateMethod() { }
}
```

### Javadoc Comments

```java
/**
 * Load all armor set configurations from disk.
 * Creates set cache and validates all entries.
 *
 * @throws IOException if config files cannot be read
 */
public void loadSets() throws IOException { }
```

### Logging

```java
// Debug
plugin.getLogger().fine("Debug message");

// Info
plugin.getLogger().info("Important info");

// Warning
plugin.getLogger().warning("Something might be wrong");

// Error
plugin.getLogger().log(Level.SEVERE, "Error message", exception);
```

---

## Testing

### Running Tests

```bash
mvn test
```

### Writing Unit Tests

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ArmorSetTests {

    @Test
    public void testSetDetection() {
        // Arrange
        Player player = createMockPlayer();
        ArmorSet set = createMockSet();

        // Act
        boolean detected = setManager.isWearingSet(player, set);

        // Assert
        assertTrue(detected);
    }
}
```

### Integration Tests

Test with actual Bukkit events:

```java
public class EffectIntegrationTests {

    @Test
    public void testDamageEffect() {
        // Create mock world and player
        World world = server.createWorld("test", WorldType.FLAT);
        Player attacker = server.addPlayer(world, "Attacker");
        Player victim = server.addPlayer(world, "Victim");

        // Simulate attack
        PlayerAttackEntityEvent event =
            new PlayerAttackEntityEvent(attacker, victim);
        Bukkit.getPluginManager().callEvent(event);

        // Verify effect applied
        assertEquals(expectedHealth, victim.getHealth());
    }
}
```

---

## Contributing

1. **Fork** the repository
2. **Create branch** for feature: `git checkout -b feature/new-effect`
3. **Make changes** following code style
4. **Write tests** for new functionality
5. **Run tests** locally: `mvn test`
6. **Submit PR** with description

### PR Checklist

- [ ] Code follows style guide
- [ ] Tests added/updated
- [ ] Javadoc added for public methods
- [ ] No breaking changes to public API
- [ ] Builds without warnings: `mvn clean compile`

---

## Debugging Tips

### Enable Debug Logging

```yaml
# In config.yml
settings:
  debug: true
```

Check console for detailed logs.

### Check Effect Execution

```bash
/as perf
```

Shows which effects are slow.

### Inspect Item Data

```bash
/as info
```

Shows NBT data of held item (for debugging config matching).

### Maven Debug Build

```bash
mvn clean package -X
```

Very verbose output for build issues.

---

## Useful Resources

- [Bukkit/Paper API Javadocs](https://papermc.io/javadocs/)
- [Spigot Plugin Development Tutorial](https://www.spigotmc.org/wiki/spigot-plugin-development/)
- [Maven Documentation](https://maven.apache.org/guides/)
- [YAML Specification](https://yaml.org/spec/1.2/spec.html)

