# 2026-01-16 | Arcane Sigils - Ancient Crown Potion Suppression Redesign

DONE:
- Redesigned Ancient Crown potion suppression to use counter-modifiers instead of amplifier reduction
- Visual potion levels now stay unchanged (Slowness I stays Slowness I)
- Potency reduced by percentage while keeping visual appearance consistent
- Built and deployed v1.0.566

FILES CREATED:
- PotionEffectTracker.java - Tracks active potion effects and counter-modifiers for cleanup
- ReducePotionPotencyEffect.java - Applies counter-modifiers to offset attribute changes
- PotionDamageReductionListener.java - Reduces Poison/Wither damage by percentage

FILES MODIFIED:
- seasonal-pass.yml - Changed REDUCE_POTION_AMPLIFIER → REDUCE_POTION_POTENCY
- EffectManager.java - Registered REDUCE_POTION_POTENCY effect
- ArmorSetsPlugin.java - Added PotionEffectTracker field, initialization, getter, and listener registration
- pom.xml - Version 1.0.565 → 1.0.566

DEPLOYED: ArcaneSigils-1.0.566.jar

NEXT:
- Test on server with actual potion effects (Slowness, Poison, Strength, Weakness)
- Verify counter-modifiers are applied correctly
- Verify cleanup works when potions expire, players die, or log out
- Check F3 debug screen to confirm counter-modifiers are present

CONTEXT:
**How the System Works:**

1. **Attribute-Based Effects (Slowness, Speed, Strength, Weakness):**
   - PotionEffectInterceptionListener fires POTION_EFFECT_APPLY signal
   - ReducePotionPotencyEffect calculates counter-modifier value
   - Example: Slowness I (-15% speed) + 60% reduction (T3 Crown)
     - Full effect: -15% speed
     - Target effect: -6% speed (40% of original)
     - Counter-modifier: +9% speed
   - Counter-modifier applied via AttributeModifierManager
   - PotionEffectTracker tracks the modifier for cleanup
   - Visual: Potion level unchanged in inventory

2. **Damage Effects (Poison, Wither):**
   - PotionDamageReductionListener intercepts EntityDamageEvent
   - Checks if player has Ancient Crown equipped
   - Reduces damage by immunity_chance percentage
   - Example: Poison I damage reduced by 60% with T3 Crown

3. **Cleanup:**
   - PotionEffectTracker listens to EntityPotionEffectEvent
   - When potion expires/removed, counter-modifier is removed
   - Also cleans up on player death and logout

**Tier Reduction Percentages:**
- T1: 20%
- T2: 40%
- T3: 60%
- T4: 80%
- T5: 100% (complete immunity)

**Potion Type Mappings:**
- SLOWNESS → Attribute.MOVEMENT_SPEED (-15% per level, ADD_SCALAR)
- SPEED → Attribute.MOVEMENT_SPEED (+20% per level, ADD_SCALAR)
- STRENGTH → Attribute.ATTACK_DAMAGE (+3 per level, ADD_NUMBER)
- WEAKNESS → Attribute.ATTACK_DAMAGE (-4 flat, ADD_NUMBER)
- POISON → EntityDamageEvent interception
- WITHER → EntityDamageEvent interception

**Testing Checklist:**
1. Equip T3 Ancient Crown (60% reduction)
2. Apply Slowness I → Check F3 screen for counter-modifier, verify movement speed
3. Apply Poison I → Verify damage reduced by 60%
4. Wait for potion to expire → Verify counter-modifier removed
5. Unequip crown during effect → Verify counter-modifier removed immediately
6. Player death → Verify all counter-modifiers cleaned up
7. Player logout → Verify tracking cleared
