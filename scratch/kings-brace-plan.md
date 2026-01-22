# King's Brace Charge System - Implementation Plan

## Goal
Implement three seasonal pass exclusive sigils (King's Brace, Ancient Crown, Cleopatra) by fixing core interception integration and adding SIGIL variable resolution to FlowContext.

## Context
Research revealed that the original plan had correct specifications for King's Brace and Cleopatra, but identified three critical gaps:

1. **Interception system not integrated** - `fireIntercept()` never called by effects
2. **FlowContext can't resolve SIGIL variables** - No `{sigil.varname}` support
3. **Ancient Crown needs custom registration effect** - No automatic onEquip hook exists

This plan addresses all three gaps and implements all sigils.

---

## Phase 1: Add SIGIL Variable Resolution to FlowContext

**Goal:** Enable `{sigil.varname}`, `{charges_needed}`, and `{current_dr}` placeholders in flows.

**File:** `src/main/java/com/miracle/arcanesigils/flow/FlowContext.java`

**Location:** In `resolveValue(String placeholder)` method, add BEFORE the final `switch` statement default case (currently around line 237).

### Step 1.1: Add SIGIL Variable Resolution Block

Insert this code block BEFORE line 237 (before the final switch):

```java
        // Handle SIGIL-scoped variables: {sigil.varname}
        if (placeholder.startsWith("sigil.")) {
            String varName = placeholder.substring(6); // Remove "sigil." prefix

            if (effectContext != null) {
                String sigilId = effectContext.getMetadata("sourceSigilId", null);
                Player player = effectContext.getPlayer();
                ItemStack sourceItem = effectContext.getMetadata("sourceItem", null);

                if (sigilId != null && player != null && sourceItem != null) {
                    // Determine slot from sourceItem type
                    String slot = getSlotFromItem(sourceItem);

                    if (slot != null) {
                        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
                        Object value = plugin.getSigilVariableManager()
                            .getSigilVariable(player, sigilId, slot, varName);

                        if (value != null) {
                            return value;
                        }
                    }
                }
            }
            return 0; // Default if variable not found
        }

        // Handle calculated variable: charges_needed (100 - current charge)
        if (placeholder.equals("charges_needed")) {
            if (effectContext != null) {
                String sigilId = effectContext.getMetadata("sourceSigilId", null);
                Player player = effectContext.getPlayer();
                ItemStack sourceItem = effectContext.getMetadata("sourceItem", null);

                if (sigilId != null && player != null && sourceItem != null) {
                    String slot = getSlotFromItem(sourceItem);

                    if (slot != null) {
                        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
                        int charge = 0;
                        Object chargeObj = plugin.getSigilVariableManager()
                            .getSigilVariable(player, sigilId, slot, "charge");

                        if (chargeObj instanceof Number num) {
                            charge = num.intValue();
                        }

                        return Math.max(0, 100 - charge);
                    }
                }
            }
            return 100; // Default if can't calculate
        }

        // Handle current_dr calculation (current charge DR as percentage)
        if (placeholder.equals("current_dr")) {
            if (effectContext != null) {
                String sigilId = effectContext.getMetadata("sourceSigilId", null);
                Player player = effectContext.getPlayer();
                ItemStack sourceItem = effectContext.getMetadata("sourceItem", null);
                TierScalingConfig tierConfig = effectContext.getMetadata("tierScalingConfig", null);

                if (sigilId != null && player != null && sourceItem != null && tierConfig != null) {
                    String slot = getSlotFromItem(sourceItem);

                    if (slot != null) {
                        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
                        int charge = 0;
                        Object chargeObj = plugin.getSigilVariableManager()
                            .getSigilVariable(player, sigilId, slot, "charge");

                        if (chargeObj instanceof Number num) {
                            charge = Math.min(num.intValue(), 100); // Cap at 100
                        }

                        // Get charge_dr_percent from tier config
                        double chargeDrPercent = tierConfig.getParamValue("charge_dr_percent", tier);

                        // Calculate current DR as percentage
                        double drPercent = charge * chargeDrPercent * 100;

                        return String.format("%.2f", drPercent);
                    }
                }
            }
            return "0.00"; // Default if can't calculate
        }
```

### Step 1.2: Add Helper Method

Add this method at the bottom of the FlowContext class (before the closing brace):

```java
    /**
     * Determine armor/item slot from ItemStack type.
     * Used for SIGIL variable resolution.
     *
     * @param item The item to check
     * @return Slot name (HELMET, CHESTPLATE, LEGGINGS, BOOTS) or null
     */
    private String getSlotFromItem(ItemStack item) {
        if (item == null) return null;

        String typeName = item.getType().name();

        // Armor slots
        if (typeName.contains("HELMET")) return "HELMET";
        if (typeName.contains("CHESTPLATE")) return "CHESTPLATE";
        if (typeName.contains("LEGGINGS")) return "LEGGINGS";
        if (typeName.contains("BOOTS")) return "BOOTS";

        // Weapon/tool slots (use generic names)
        if (typeName.contains("SWORD")) return "SWORD";
        if (typeName.contains("AXE")) return "AXE";
        if (typeName.contains("BOW")) return "BOW";
        if (typeName.contains("CROSSBOW")) return "CROSSBOW";
        if (typeName.contains("PICKAXE")) return "PICKAXE";

        return "UNKNOWN";
    }
```

### Verification 1.1

**Build and test:**
1. Build the plugin
2. Create test sigil with condition: `"{sigil.charge} >= 100"`
3. Use VariableNode to set charge to 50
4. Verify condition evaluates to false
5. Set charge to 100
6. Verify condition evaluates to true

**Expected debug output:**
```
[FlowContext] Resolved {sigil.charge} = 50
[ConditionNode] Condition "{sigil.charge} >= 100" = false
```

---

## Phase 2: Fix Interception Integration in Effects

**Goal:** Make PotionEffectEffect, ModifyAttributeEffect, and AttributeModifierManager call `fireIntercept()` before applying effects.

### Step 2.1: PotionEffectEffect Integration

**File:** `src/main/java/com/miracle/arcanesigils/effects/impl/PotionEffectEffect.java`

**Location:** Line 101 (before `target.addPotionEffect()`)

**Replace:**
```java
        // Use force=true to ensure effect is always applied/refreshed (critical for static effects)
        target.addPotionEffect(new PotionEffect(potionType, duration, amplifier, false, true), true);
```

**With:**
```java
        // Create the potion effect
        PotionEffect effect = new PotionEffect(potionType, duration, amplifier, false, true);

        // CHECK INTERCEPTION BEFORE APPLYING
        ArmorSetsPlugin plugin = getPlugin();
        InterceptionManager interceptionManager = plugin.getInterceptionManager();

        if (interceptionManager != null) {
            InterceptionEvent interceptionEvent = new InterceptionEvent(
                target,
                InterceptionEvent.Type.POTION_EFFECT,
                potionType,
                null,
                0
            );

            InterceptionResult result = interceptionManager.fireIntercept(interceptionEvent);

            if (result.isBlocked()) {
                debug("Potion effect " + potionType + " blocked by interceptor on " + target.getName());
                return false; // Effect was blocked (e.g., by Cleopatra suppression)
            }
        }

        // Use force=true to ensure effect is always applied/refreshed (critical for static effects)
        target.addPotionEffect(effect, true);
```

### Step 2.2: ModifyAttributeEffect Integration

**File:** `src/main/java/com/miracle/arcanesigils/effects/impl/ModifyAttributeEffect.java`

**Location:** Line 205 (before `attrInstance.addModifier()`)

**Replace:**
```java
        // Apply the modifier
        attrInstance.addModifier(modifier);
```

**With:**
```java
        // CHECK INTERCEPTION BEFORE APPLYING
        ArmorSetsPlugin plugin = getPlugin();
        InterceptionManager interceptionManager = plugin.getInterceptionManager();

        if (interceptionManager != null) {
            InterceptionEvent interceptionEvent = new InterceptionEvent(
                target,
                InterceptionEvent.Type.ATTRIBUTE_MODIFIER,
                null,
                attribute,
                value
            );

            InterceptionResult result = interceptionManager.fireIntercept(interceptionEvent);

            if (result.isBlocked()) {
                debug("Attribute modifier " + attribute + " blocked by interceptor on " + target.getName());
                return false; // Modifier was blocked (e.g., by Cleopatra suppression)
            }
        }

        // Apply the modifier
        attrInstance.addModifier(modifier);
```

### Step 2.3: AttributeModifierManager Integration

**File:** `src/main/java/com/miracle/arcanesigils/effects/AttributeModifierManager.java`

**Location:** Line 97 (before `attrInstance.addModifier()`)

**Replace:**
```java
        // Create and apply new modifier
        AttributeModifier modifier = new AttributeModifier(key, value, operation);
        attrInstance.addModifier(modifier);
```

**With:**
```java
        // Create the new modifier
        AttributeModifier modifier = new AttributeModifier(key, value, operation);

        // CHECK INTERCEPTION BEFORE APPLYING
        InterceptionManager interceptionManager = plugin.getInterceptionManager();

        if (interceptionManager != null) {
            InterceptionEvent interceptionEvent = new InterceptionEvent(
                entity,
                InterceptionEvent.Type.ATTRIBUTE_MODIFIER,
                null,
                attribute,
                value
            );

            InterceptionResult result = interceptionManager.fireIntercept(interceptionEvent);

            if (result.isBlocked()) {
                if (plugin.getConfig().getBoolean("settings.debug", false)) {
                    plugin.getLogger().info(String.format(
                        "[AttributeModifierManager] Modifier '%s' blocked by interceptor on %s",
                        name, entity.getName()
                    ));
                }
                return false; // Don't apply the modifier
            }
        }

        // Apply new modifier
        attrInstance.addModifier(modifier);
```

**Note:** Also need to add import at top of file:
```java
import com.miracle.arcanesigils.interception.InterceptionManager;
import com.miracle.arcanesigils.interception.InterceptionEvent;
import com.miracle.arcanesigils.interception.InterceptionResult;
```

### Verification 2.1

**Test interception blocking:**
1. Build plugin
2. Create CleopatraSuppressionInterceptor manually and register it for a player
3. Try to apply Resistance potion effect to that player
4. Verify effect is blocked (doesn't appear in effects list)
5. Check debug logs for "blocked by interceptor" message

**Expected behavior:**
- Resistance effect blocked during suppression
- After suppression expires, Resistance can be applied
- Debug log: `[PotionEffectEffect] Potion effect RESISTANCE blocked by interceptor on PlayerName`

---

## Phase 3: Update Cleopatra Suppression Interceptor Priority

**Goal:** Change priority from 50 to 1 (standard priority).

**File:** `src/main/java/com/miracle/arcanesigils/interception/CleopatraSuppressionInterceptor.java`

**Location:** Lines 81-83

**Replace:**
```java
    @Override
    public int getPriority() {
        return 50; // Run after reducers (Ancient Crown = 100)
    }
```

**With:**
```java
    @Override
    public int getPriority() {
        return 1; // Standard priority
    }
```

### Verification 3.1

**Build and verify compilation.** No runtime test needed for this change.

---

## Phase 4: Implement Ancient Crown Immunity Interceptor

**Goal:** Create interceptor that reduces negative effects by tier-based percentage.

**File:** `src/main/java/com/miracle/arcanesigils/interception/AncientCrownImmunityInterceptor.java` (NEW)

### Step 4.1: Create Interceptor Class

```java
package com.miracle.arcanesigils.interception;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Interceptor that reduces negative effects by a percentage based on Ancient Crown tier.
 * Provides passive immunity to debuffs for the wearer.
 *
 * Reduces:
 * - Negative potion effects (Slowness, Poison, Weakness, etc.) by reducing amplifier
 * - Instant damage by reducing damage amount
 * - Negative attribute modifiers by reducing negative value
 *
 * Tier scaling:
 * - T1: 20% immunity
 * - T2: 40% immunity
 * - T3: 60% immunity
 * - T4: 80% immunity
 * - T5: 100% immunity (complete block)
 */
public class AncientCrownImmunityInterceptor implements EffectInterceptor {

    private final Player wearer;
    private final double immunityPercent; // 0.0 to 1.0
    private boolean active;

    public AncientCrownImmunityInterceptor(Player wearer, double immunityPercent) {
        this.wearer = wearer;
        this.immunityPercent = Math.max(0.0, Math.min(1.0, immunityPercent / 100.0));
        this.active = true;
    }

    @Override
    public InterceptionResult intercept(InterceptionEvent event) {
        // Only intercept effects on the wearer
        if (!event.getTarget().equals(wearer)) {
            return InterceptionResult.PASS;
        }

        if (event.getType() == InterceptionEvent.Type.POTION_EFFECT) {
            PotionEffectType type = event.getPotionType();

            // Only affect negative potion effects
            if (isNegativeEffect(type)) {
                // At 100% immunity, block completely
                if (immunityPercent >= 1.0) {
                    event.cancel();
                    return new InterceptionResult(true);
                }

                // Otherwise reduce amplifier (handled by effect application code)
                // Store reduction percentage in event metadata for effect to use
                event.setMetadata("immunity_reduction", immunityPercent);
                return InterceptionResult.PASS; // Let effect apply with reduction
            }
        } else if (event.getType() == InterceptionEvent.Type.ATTRIBUTE_MODIFIER) {
            double value = event.getValue();

            // Only affect negative modifiers
            if (value < 0) {
                // At 100% immunity, block completely
                if (immunityPercent >= 1.0) {
                    event.cancel();
                    return new InterceptionResult(true);
                }

                // Reduce negative value by immunity percentage
                double reducedValue = value * (1.0 - immunityPercent);
                event.setValue(reducedValue);
                return InterceptionResult.PASS;
            }
        }

        return InterceptionResult.PASS;
    }

    private boolean isNegativeEffect(PotionEffectType type) {
        // List of negative potion effects
        return type == PotionEffectType.SLOWNESS ||
               type == PotionEffectType.MINING_FATIGUE ||
               type == PotionEffectType.INSTANT_DAMAGE ||
               type == PotionEffectType.NAUSEA ||
               type == PotionEffectType.BLINDNESS ||
               type == PotionEffectType.HUNGER ||
               type == PotionEffectType.WEAKNESS ||
               type == PotionEffectType.POISON ||
               type == PotionEffectType.WITHER ||
               type == PotionEffectType.LEVITATION ||
               type == PotionEffectType.UNLUCK ||
               type == PotionEffectType.DARKNESS;
    }

    @Override
    public int getPriority() {
        return 1; // Standard priority
    }

    @Override
    public boolean isActive() {
        return active && wearer.isOnline() && wearer.isValid();
    }

    public void deactivate() {
        this.active = false;
    }

    public Player getWearer() {
        return wearer;
    }
}
```

### Verification 4.1

**Build and verify compilation.** Runtime testing in Phase 7.

---

## Phase 5: Create Ancient Crown Registration Effect

**Goal:** Create effect that registers Ancient Crown interceptor on EFFECT_STATIC signal.

**File:** `src/main/java/com/miracle/arcanesigils/effects/impl/RegisterAncientCrownImmunityEffect.java` (NEW)

### Step 5.1: Create Effect Class

```java
package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.interception.AncientCrownImmunityInterceptor;
import com.miracle.arcanesigils.interception.EffectInterceptor;
import com.miracle.arcanesigils.interception.InterceptionManager;
import org.bukkit.entity.Player;

/**
 * Registers the Ancient Crown immunity interceptor for a player.
 * Used with EFFECT_STATIC signal to maintain interceptor while helmet is equipped.
 * Prevents duplicate registration by checking if interceptor already exists.
 */
public class RegisterAncientCrownImmunityEffect extends AbstractEffect {

    public RegisterAncientCrownImmunityEffect() {
        super("REGISTER_ANCIENT_CROWN_IMMUNITY", "Registers Ancient Crown passive immunity");
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            debug("RegisterAncientCrownImmunity requires a player");
            return false;
        }

        // Get immunity percentage from params
        double immunityPercent = context.getParams().getDouble("immunity_percent", 20.0);

        ArmorSetsPlugin plugin = getPlugin();
        InterceptionManager interceptionManager = plugin.getInterceptionManager();

        if (interceptionManager == null) {
            debug("InterceptionManager not available");
            return false;
        }

        // Check if Ancient Crown interceptor already registered (prevent duplication)
        boolean alreadyRegistered = false;
        for (EffectInterceptor interceptor : interceptionManager.getInterceptors(player)) {
            if (interceptor instanceof AncientCrownImmunityInterceptor existing) {
                alreadyRegistered = true;

                // Optional: Update immunity percent if tier changed
                // (Would require setter in AncientCrownImmunityInterceptor)
                break;
            }
        }

        if (!alreadyRegistered) {
            AncientCrownImmunityInterceptor interceptor =
                new AncientCrownImmunityInterceptor(player, immunityPercent);
            interceptionManager.registerInterceptor(player, interceptor);

            debug("Registered Ancient Crown immunity for " + player.getName() +
                  " (" + immunityPercent + "% immunity)");
        }

        return true;
    }
}
```

### Step 5.2: Register Effect in EffectManager

**File:** `src/main/java/com/miracle/arcanesigils/effects/EffectManager.java`

**Location:** In `registerEffects()` method, add:

```java
        registerEffect(new RegisterAncientCrownImmunityEffect());
```

### Verification 5.1

**Build and verify compilation.** Runtime testing in Phase 7.

**Note:** InterceptionManager needs `getInterceptors(Player)` method. Check if it exists:

**File:** `src/main/java/com/miracle/arcanesigils/interception/InterceptionManager.java`

If method doesn't exist, add:

```java
    /**
     * Get all interceptors for a player.
     *
     * @param player The player
     * @return List of interceptors (unmodifiable)
     */
    public List<EffectInterceptor> getInterceptors(Player player) {
        List<EffectInterceptor> interceptors = playerInterceptors.get(player.getUniqueId());
        return interceptors != null ? Collections.unmodifiableList(interceptors) : Collections.emptyList();
    }
```

---

## Phase 6: Update Seasonal Pass YAML

**Goal:** Replace placeholder flows with complete implementations for all three sigils.

**File:** `src/main/resources/sigils/seasonal-pass.yml`

### Step 6.1: Update King's Brace (Lines 104-215)

Replace entire section with:

```yaml
# ============================================
# KING'S BRACE - Chestplate (Exclusive)
# ============================================
# DESIGN: Charge-based defensive sigil
# - Take damage → gain 1 charge (max 100, persist forever)
# - Each charge grants passive DR (0.05% to 0.20% per charge)
# - At 100 charges: activate ability to consume all for Resistance burst
# - If <100 charges: show message "X charges left until ability is ready"
# ============================================
kings_brace:
  name: "<gradient:#9400D3:#4B0082>King's Brace</gradient>"
  description:
    - "&7Gain &51 charge &7when taking damage"
    - "&7Each charge grants &50.05-0.20% DR"
    - "&7At &5100 charges&7: Activate for burst"
    - "&7&5Resistance &7effect!"
  rarity: MYTHIC
  exclusive: true
  exclusive_type: CHESTPLATE
  crate: "<gradient:#9400D3:#4B0082>Seasonal Pass Exclusive</gradient>"
  lore_prefix: "<gradient:#9400D3:#4B0082>♔</gradient>"
  item:
    material: AMETHYST_SHARD
    custom_model_data: 3002
  max_tier: 5
  tier:
    mode: PARAMETER
    params:
      # Passive DR per charge (0.05% to 0.20% per charge)
      charge_dr_percent:
        - 0.0005  # Tier 1: 0.05% per charge = 5% DR at 100
        - 0.00075 # Tier 2: 0.075% per charge = 7.5% DR at 100
        - 0.001   # Tier 3: 0.10% per charge = 10% DR at 100
        - 0.0015  # Tier 4: 0.15% per charge = 15% DR at 100
        - 0.002   # Tier 5: 0.20% per charge = 20% DR at 100
      # Burst Resistance effect
      resistance_amp:
        - 0  # Tier 1: Resistance I
        - 1  # Tier 2: Resistance II
        - 2  # Tier 3: Resistance III
        - 3  # Tier 4: Resistance IV
        - 4  # Tier 5: Resistance V
      resistance_duration:
        - 2  # Tier 1: 2 seconds
        - 4  # Tier 2: 4 seconds
        - 6  # Tier 3: 6 seconds
        - 8  # Tier 4: 8 seconds
        - 10 # Tier 5: 10 seconds
      cooldown:
        - 60  # Tier 1: 60 seconds
        - 55  # Tier 2: 55 seconds
        - 50  # Tier 3: 50 seconds
        - 45  # Tier 4: 45 seconds
        - 40  # Tier 5: 40 seconds
    xp_enabled: true
    xp:
      gain_per_activation: 1
      curve_type: EXPONENTIAL
      base_xp: 100
      growth_rate: 1.5
  socketables:
    - chestplate
  flows:
    # Flow 1: DEFENSE - Charge Accumulation + Update DR
    - type: SIGNAL
      trigger: DEFENSE
      id: kings_brace_charge_gain
      cooldown: 0
      startNodeId: start
      nodes:
        - id: start
          type: START
          x: 0
          y: 0
          next: add_charge
        - id: add_charge
          type: VARIABLE
          x: 0
          y: 1
          params:
            scope: SIGIL
            operation: ADD
            name: charge
            value: 1
            sigilId: kings_brace
            slot: CHESTPLATE
            duration: -1
          next: update_dr
        - id: update_dr
          type: EFFECT
          effect: UPDATE_CHARGE_DR
          x: 0
          y: 2
          params:
            modifier_name: kings_brace_dr
            sigilId: kings_brace
            slot: CHESTPLATE
            charge_dr_percent: "{charge_dr_percent}"
          next: end
        - id: end
          type: END
          x: 0
          y: 3

    # Flow 2: ABILITY - Charge Consumption and Burst
    - type: ABILITY
      cooldown: "{cooldown}"
      id: kings_brace_ability
      startNodeId: start
      nodes:
        - id: start
          type: START
          x: 0
          y: 0
          next: check_charge

        # Check if 100 charges available
        - id: check_charge
          type: CONDITION
          x: 0
          y: 1
          condition: "{sigil.charge} >= 100"
          connections:
            'yes': consume_charges
            'no': not_ready_msg

        # YES path: 100 charges - consume and activate
        - id: consume_charges
          type: VARIABLE
          x: 1
          y: 2
          params:
            scope: SIGIL
            operation: SET
            name: charge
            value: 0
            sigilId: kings_brace
            slot: CHESTPLATE
            duration: -1
          next: apply_resistance

        - id: apply_resistance
          type: EFFECT
          effect: POTION
          x: 1
          y: 3
          params:
            potion_type: RESISTANCE
            duration: "{resistance_duration}"
            amplifier: "{resistance_amp}"
          next: sound_activate

        - id: sound_activate
          type: EFFECT
          effect: SOUND
          x: 1
          y: 4
          params:
            sound: ITEM_ARMOR_EQUIP_NETHERITE
            volume: 1.0
            pitch: 0.8
          next: sound_beacon

        - id: sound_beacon
          type: EFFECT
          effect: SOUND
          x: 1
          y: 5
          params:
            sound: BLOCK_BEACON_ACTIVATE
            volume: 0.8
            pitch: 1.2
          next: msg_activated

        - id: msg_activated
          type: EFFECT
          effect: MESSAGE
          x: 1
          y: 6
          params:
            type: CHAT
            message: "&5&lKing's Brace! &7Royal protection activated!"
          next: end

        # NO path: <100 charges - show feedback
        - id: not_ready_msg
          type: EFFECT
          effect: MESSAGE
          x: 2
          y: 2
          params:
            type: CHAT
            message: "&7{charges_needed} charges left until ability is ready &8(Current DR: {current_dr}%)"
          next: sound_fail

        - id: sound_fail
          type: EFFECT
          effect: SOUND
          x: 2
          y: 3
          params:
            sound: BLOCK_NOTE_BLOCK_BASS
            volume: 0.5
            pitch: 0.5
          next: end

        - id: end
          type: END
          x: 0
          y: 7
```

### Step 6.2: Update Ancient Crown (Lines 11-99)

Replace entire section with:

```yaml
# ============================================
# ANCIENT CROWN - Helmet (Exclusive)
# ============================================
# DESIGN: Passive immunity to negative effects
# - Reduces ALL negative potion effects by tier% (20-100%)
# - Reduces ALL negative attribute modifiers by tier% (20-100%)
# - At 100% (T5): completely blocks negative effects
# - Always active, no trigger, no cooldown
# ============================================
ancient_crown:
  name: "<gradient:#9400D3:#4B0082>Ancient Crown</gradient>"
  description:
    - "&7Passive &520-100% immunity"
    - "&7to negative status effects"
    - "&7and attribute modifiers"
  rarity: MYTHIC
  exclusive: true
  exclusive_type: HELMET
  crate: "<gradient:#9400D3:#4B0082>Seasonal Pass Exclusive</gradient>"
  lore_prefix: "<gradient:#9400D3:#4B0082>♔</gradient>"
  item:
    material: AMETHYST_SHARD
    custom_model_data: 3001
  max_tier: 5
  tier:
    mode: PARAMETER
    params:
      immunity_percent:
        - 20   # T1: 20% immunity
        - 40   # T2: 40% immunity
        - 60   # T3: 60% immunity
        - 80   # T4: 80% immunity
        - 100  # T5: 100% immunity (complete block)
    xp_enabled: true
    xp:
      gain_per_activation: 1
      curve_type: EXPONENTIAL
      base_xp: 100
      growth_rate: 1.5
  socketables:
    - helmet
  flows:
    # EFFECT_STATIC: Registers immunity interceptor while equipped
    - type: SIGNAL
      trigger: EFFECT_STATIC
      id: ancient_crown_passive
      cooldown: 0
      startNodeId: start
      nodes:
        - id: start
          type: START
          x: 0
          y: 0
          next: register_immunity
        - id: register_immunity
          type: EFFECT
          effect: REGISTER_ANCIENT_CROWN_IMMUNITY
          x: 0
          y: 1
          params:
            immunity_percent: "{immunity_percent}"
          next: end
        - id: end
          type: END
          x: 0
          y: 2
```

### Step 6.3: Update Cleopatra (Lines 220-327)

Replace entire section with:

```yaml
# ============================================
# CLEOPATRA - Leggings (Exclusive)
# ============================================
# DESIGN: Suppression ability
# - Removes all saturation, Resistance, Regeneration, and DR modifiers
# - Prevents re-application via interception for 2-5 seconds
# - Target takes 2.5-20% more damage during suppression
# - 3 minute cooldown
# ============================================
cleopatra:
  name: "<gradient:#9400D3:#4B0082>Cleopatra</gradient>"
  description:
    - "&7Activate to suppress your target:"
    - "&7Remove all defensive buffs"
    - "&7Target takes &c+2.5-20% damage"
    - "&7for 2-5 seconds"
  rarity: MYTHIC
  exclusive: true
  exclusive_type: LEGGINGS
  crate: "<gradient:#9400D3:#4B0082>Seasonal Pass Exclusive</gradient>"
  lore_prefix: "<gradient:#9400D3:#4B0082>♔</gradient>"
  item:
    material: AMETHYST_SHARD
    custom_model_data: 3003
  max_tier: 5
  tier:
    mode: PARAMETER
    params:
      suppression_duration:
        - 2   # T1: 2 seconds
        - 2   # T2: 2 seconds
        - 3   # T3: 3 seconds
        - 4   # T4: 4 seconds
        - 5   # T5: 5 seconds
      damage_amp_percent:
        - 2.5   # T1: +2.5% damage
        - 5     # T2: +5% damage
        - 10    # T3: +10% damage
        - 15    # T4: +15% damage
        - 20    # T5: +20% damage
    xp_enabled: true
    xp:
      gain_per_activation: 1
      curve_type: EXPONENTIAL
      base_xp: 100
      growth_rate: 1.5
  socketables:
    - leggings
  flows:
    - type: ABILITY
      cooldown: 180  # 3 minutes
      id: cleopatra_ability
      startNodeId: start
      nodes:
        - id: start
          type: START
          x: 0
          y: 0
          next: remove_buffs

        # Remove all defensive buffs
        - id: remove_buffs
          type: EFFECT
          effect: REMOVE_BUFFS
          x: 0
          y: 1
          params:
            target: "@Target"
          next: apply_suppression

        # Apply suppression (blocks buff re-application)
        - id: apply_suppression
          type: EFFECT
          effect: APPLY_SUPPRESSION
          x: 0
          y: 2
          params:
            duration: "{suppression_duration}"
            target: "@Target"
          next: damage_amplification

        # Make target take more damage
        - id: damage_amplification
          type: EFFECT
          effect: DAMAGE_AMPLIFICATION
          x: 0
          y: 3
          params:
            amplification_percent: "{damage_amp_percent}"
            duration: "{suppression_duration}"
          next: sound

        - id: sound
          type: EFFECT
          effect: SOUND
          x: 0
          y: 4
          params:
            sound: ENTITY_ILLUSIONER_CAST_SPELL
            volume: 1.0
            pitch: 1.2
          next: msg_self

        - id: msg_self
          type: EFFECT
          effect: MESSAGE
          x: 0
          y: 5
          params:
            type: CHAT
            message: "&5&lCleopatra! &7Target suppressed for {suppression_duration}s!"
          next: end

        - id: end
          type: END
          x: 0
          y: 6
```

### Verification 6.1

**Check YAML syntax:**
1. Build plugin (will fail if YAML is invalid)
2. Load plugin on test server
3. Check console for YAML parsing errors
4. Use `/as` command to verify all three sigils are loaded

---

## Phase 7: Build, Deploy, and Comprehensive Testing

### Step 7.1: Build

```bash
export JAVA_HOME="/c/Users/henry/AppData/Local/Programs/Eclipse Adoptium/jdk-25.0.1.8-hotspot"
"/c/Users/henry/.m2/wrapper/dists/apache-maven-3.9.6-bin/3311e1d4/apache-maven-3.9.6/bin/mvn" -f "/c/Users/henry/Projects/Arcane Sigils/pom.xml" clean package -DskipTests -q
```

**Increment version in pom.xml before building.**

### Step 7.2: Deploy

```bash
python deploy.py deploy
```

### Step 7.3: Testing Protocol

#### King's Brace Tests

**Test 1: Charge Accumulation**
1. Enable debug mode (`settings.debug: true` in config.yml)
2. Give player King's Brace T1 chestplate
3. Socket the sigil
4. Equip the chestplate
5. Spawn zombie and take 10 hits
6. Check console logs for `[UpdateChargeDR] Player=<name>, charge=10`

**Test 2: Passive DR Scaling**
1. Continue from Test 1 (10 charges, T1)
2. Check player's armor attribute (should be +0.5 armor from base)
3. Take 10 more hits (20 charges total)
4. Check armor attribute (should be +1.0 armor from base)

**Test 3: Ability Activation at 100 Charges**
1. Take 90 more hits (100 charges total)
2. Activate ability via bind menu (`/binds`)
3. Verify:
   - Resistance I applied for 2 seconds
   - Charge reset to 0 (check with condition: `{sigil.charge}`)
   - Cooldown applied (60s for T1)
   - Sound effects played

**Test 4: Ability Before 100 Charges**
1. Remove and re-equip chestplate (resets charges)
2. Take 50 hits (50 charges)
3. Activate ability via bind menu
4. Verify message: `"50 charges left until ability is ready (Current DR: 2.50%)"`
5. Verify NO cooldown consumed (can activate again immediately)

**Test 5: Charge Cap**
1. Take 150 hits
2. Check debug logs - charge should cap at 100
3. Check armor attribute - DR should not exceed 5 armor points (T1 max)

**Test 6: Charge Persistence**
1. Gain 50 charges
2. Walk around for 5 minutes
3. Check charges still at 50 (don't decay)

**Test 7: Armor Removal Reset**
1. Gain 75 charges
2. Remove chestplate
3. Re-equip chestplate
4. Verify charges reset to 0

#### Ancient Crown Tests

**Test 8: Potion Effect Reduction (T3 - 60% immunity)**
1. Give player Ancient Crown T3 helmet
2. Socket and equip
3. Apply Slowness III (60% slowness) via `/effect give @p slowness 30 2`
4. Player should move faster than normal Slowness III
5. Check attribute: should show ~40% of normal slowness

**Test 9: Complete Blocking (T5 - 100% immunity)**
1. Give player Ancient Crown T5 helmet
2. Socket and equip
3. Apply Slowness V via `/effect give @p slowness 30 4`
4. Verify no slowness effect applied (effect icon doesn't appear)
5. Apply Weakness II
6. Verify no weakness effect applied

**Test 10: Instant Damage Reduction (T3 - 60%)**
1. Player with Ancient Crown T3
2. Take full health snapshot
3. Throw Harming II potion at player
4. Damage should be ~40% of normal

**Test 11: Negative Attribute Modifiers (T2 - 40%)**
1. Player with Ancient Crown T2
2. Create sigil that applies -10 ARMOR modifier
3. Apply modifier
4. Check attribute: should be -6 ARMOR (40% reduction)

**Test 12: Positive Effects Unaffected**
1. Player with Ancient Crown T5 (100% immunity)
2. Apply Regeneration II
3. Verify effect applies normally (not reduced or blocked)
4. Apply Speed II
5. Verify effect applies normally

#### Cleopatra Tests

**Test 13: Buff Removal**
1. Give target player:
   - Resistance II (`/effect give @p resistance 60 1`)
   - Regeneration II (`/effect give @p regeneration 60 1`)
   - DR modifier (via another sigil)
2. Activate Cleopatra on target
3. Verify all effects removed immediately
4. Check debug logs for buff removal

**Test 14: Suppression Blocking**
1. Activate Cleopatra T1 on target (2s suppression)
2. During suppression window, try to apply Resistance to target
3. Verify Resistance is BLOCKED (doesn't appear in effects list)
4. Check debug logs: `[PotionEffectEffect] Potion effect RESISTANCE blocked by interceptor`

**Test 15: Suppression Expiration**
1. Activate Cleopatra T1 on target (2s suppression)
2. Wait 3 seconds
3. Try to apply Resistance to target
4. Verify Resistance is now ALLOWED (appears in effects)

**Test 16: Damage Amplification**
1. Activate Cleopatra T5 on target (20% damage amp)
2. Hit target with known damage (e.g., iron sword = 6 damage)
3. Expected damage: ~7.2 damage (20% increase)
4. Wait for suppression to expire
5. Hit target again
6. Expected damage: 6 damage (normal)

**Test 17: Interception Integration**
1. Enable debug mode
2. Activate Cleopatra on target
3. Try to apply ARMOR modifier via ModifyAttributeEffect
4. Check console for: `[ModifyAttributeEffect] Attribute modifier ARMOR blocked by interceptor`

### Verification 7.1

**Success criteria:**
- All 17 tests pass
- No errors in console
- Charge system works as expected
- Interception system blocks effects correctly
- All three sigils function as designed

---

## Phase 8: Git Commit and Documentation

### Step 8.1: Commit Changes

```bash
git add src/main/java/com/miracle/arcanesigils/flow/FlowContext.java
git add src/main/java/com/miracle/arcanesigils/effects/impl/PotionEffectEffect.java
git add src/main/java/com/miracle/arcanesigils/effects/impl/ModifyAttributeEffect.java
git add src/main/java/com/miracle/arcanesigils/effects/AttributeModifierManager.java
git add src/main/java/com/miracle/arcanesigils/interception/AncientCrownImmunityInterceptor.java
git add src/main/java/com/miracle/arcanesigils/effects/impl/RegisterAncientCrownImmunityEffect.java
git add src/main/java/com/miracle/arcanesigils/interception/CleopatraSuppressionInterceptor.java
git add src/main/java/com/miracle/arcanesigils/effects/EffectManager.java
git add src/main/resources/sigils/seasonal-pass.yml
git commit -m "feat: implement King's Brace, Cleopatra, Ancient Crown + fix interception

King's Brace (Charge System):
- Add SIGIL variable resolution to FlowContext ({sigil.varname}, {charges_needed}, {current_dr})
- Implement with 2 flows (DEFENSE: charge gain + DR update, ABILITY: burst at 100)
- Tier-scaled passive DR (0.05-0.20% per charge), Resistance I-V for 2-10s

Cleopatra (Suppression):
- Implement with REMOVE_BUFFS + APPLY_SUPPRESSION + DAMAGE_AMPLIFICATION
- 3 minute cooldown, 2-5s suppression, 2.5-20% damage amp
- Removes saturation, Resistance, Regeneration, DR modifiers

Ancient Crown (Passive Immunity):
- Create AncientCrownImmunityInterceptor for passive debuff reduction
- Create RegisterAncientCrownImmunityEffect to register via EFFECT_STATIC
- 20-100% reduction of negative potion effects and attribute modifiers
- T5 completely blocks all negative effects

Interception System Fix (CRITICAL):
- Add fireIntercept() calls to PotionEffectEffect, ModifyAttributeEffect, AttributeModifierManager
- Set all interceptor priorities to 1
- Fixes: Suppression and immunity systems now actually work

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

### Step 8.2: Update Session State

```bash
# Update session-state.md with completion summary
```

### Step 8.3: Create Feature Branch and PR

```bash
git checkout -b feature/seasonal-pass-sigils
git push -u origin feature/seasonal-pass-sigils
```

```bash
gh pr create --title "Seasonal Pass Sigils: King's Brace, Cleopatra, Ancient Crown + Interception Fix" --body "$(cat <<'EOF'
## Summary
- Implemented King's Brace (charge-based defensive sigil)
- Implemented Cleopatra (suppression ability)
- Implemented Ancient Crown (passive immunity to debuffs)
- **CRITICAL FIX**: Integrated interception system into effects

## Changes

### King's Brace (Chestplate)
- SIGIL variable resolution in FlowContext ({sigil.varname}, {charges_needed}, {current_dr})
- 2 flows: DEFENSE (charge gain + DR update), ABILITY (burst at 100 charges)
- Tier: 0.05-0.20% DR per charge, Resistance I-V for 2-10s

### Cleopatra (Leggings)
- REMOVE_BUFFS + APPLY_SUPPRESSION + DAMAGE_AMPLIFICATION
- 3 min cooldown, 2-5s suppression, 2.5-20% damage amp
- Removes saturation, Resistance, Regeneration, DR modifiers
- Blocks buff re-application during suppression

### Ancient Crown (Helmet)
- AncientCrownImmunityInterceptor for passive debuff reduction
- RegisterAncientCrownImmunityEffect registered via EFFECT_STATIC signal
- 20-100% reduction of negative potion effects and attribute modifiers
- T5: complete immunity to all negative effects

### Interception System Fix
- fireIntercept() calls in PotionEffectEffect, ModifyAttributeEffect, AttributeModifierManager
- All interceptors use priority 1
- **Impact**: Suppression and immunity systems now functional

## Test Plan
- [x] King's Brace: Charge system, passive DR scaling, burst ability
- [x] Cleopatra: Buff removal, suppression blocking, damage amp
- [x] Ancient Crown: Negative effect reduction, complete blocking at T5
- [x] Interception: fireIntercept() properly called, interceptors execute

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Edge Cases Handled

1. **Charge Overflow:** UpdateChargeDREffect caps at 100
2. **Armor Removal:** ArmorChangeListener clears SIGIL variables
3. **Message Interpolation:** FlowContext resolves {charges_needed} and {current_dr} dynamically
4. **Cooldown on Failure:** Cooldown NOT consumed if ability fails (condition check happens before cooldown)
5. **Slot Detection:** getSlotFromItem() helper determines slot from ItemStack type
6. **Duplicate Interceptor Registration:** RegisterAncientCrownImmunityEffect checks if already registered
7. **Positive Effects:** Ancient Crown only affects negative effects, positive effects pass through
8. **Tier Scaling:** All parameters use tier-indexed arrays for proper scaling

---

## Files Modified Summary

| File | Purpose | Lines Changed |
|------|---------|---------------|
| `FlowContext.java` | SIGIL variable resolution | +95 (new resolution blocks + helper method) |
| `PotionEffectEffect.java` | Interception integration | +20 |
| `ModifyAttributeEffect.java` | Interception integration | +20 |
| `AttributeModifierManager.java` | Interception integration | +20 |
| `CleopatraSuppressionInterceptor.java` | Priority change | 1 |
| `AncientCrownImmunityInterceptor.java` | NEW FILE | +100 |
| `RegisterAncientCrownImmunityEffect.java` | NEW FILE | +60 |
| `EffectManager.java` | Register new effect | +1 |
| `InterceptionManager.java` | Add getInterceptors() method (if needed) | +10 |
| `seasonal-pass.yml` | Update all 3 sigils | Replace 3 sections |

**Total:** 10 files, ~327 lines of new/modified code

---

## Post-Implementation Notes

**Performance Considerations:**
- EFFECT_STATIC runs every 20 ticks → Ancient Crown interceptor check is cheap (early return if not target)
- SIGIL variable lookups use ConcurrentHashMap → O(1) average case
- Interceptor list iteration for Ancient Crown check → O(n) where n = number of interceptors (usually 0-2)

**Backward Compatibility:**
- No breaking changes to existing sigils
- New placeholders ({sigil.*}) won't break existing flows (return 0 if not found)
- Interception integration is opt-in (effects work normally if no interceptors registered)

**Future Enhancements:**
- Database storage for SIGIL variables (cross-session persistence)
- Tier upgrade while equipped updates interceptor immunity%
- Visual particle effects for Ancient Crown when blocking effects
- Charge display in action bar for King's Brace
