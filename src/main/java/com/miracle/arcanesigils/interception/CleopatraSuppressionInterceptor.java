package com.miracle.arcanesigils.interception;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Interceptor that suppresses defensive buffs from being applied to a target.
 * Used by Cleopatra ability to prevent targets from re-gaining protection.
 * 
 * Blocks:
 * - RESISTANCE potion effects
 * - REGENERATION potion effects
 * - Damage reduction attribute modifiers (ARMOR, ARMOR_TOUGHNESS)
 * 
 * Auto-expires after duration.
 */
public class CleopatraSuppressionInterceptor implements EffectInterceptor {
    
    private final Player target;
    private boolean active;
    
    /**
     * Create a suppression interceptor for a target player.
     * 
     * @param target The player who is suppressed
     */
    public CleopatraSuppressionInterceptor(Player target) {
        this.target = target;
        this.active = true;
    }
    
    @Override
    public InterceptionResult intercept(InterceptionEvent event) {
        // Only intercept effects on the suppressed target
        if (!event.getTarget().equals(target)) {
            return InterceptionResult.PASS;
        }
        
        if (event.getType() == InterceptionEvent.Type.POTION_EFFECT) {
            PotionEffectType type = event.getPotionType();
            
            // Block RESISTANCE and REGENERATION
            if (type == PotionEffectType.RESISTANCE || type == PotionEffectType.REGENERATION) {
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
    
    /**
     * Check if an attribute modifier provides damage reduction.
     * 
     * @param event The interception event
     * @return true if the modifier is protective
     */
    private boolean isDamageReductionModifier(InterceptionEvent event) {
        Attribute attr = event.getAttributeType();
        double value = event.getValue();
        
        // ARMOR and ARMOR_TOUGHNESS with positive values provide protection
        if (attr == Attribute.ARMOR && value > 0) {
            return true;
        }
        if (attr == Attribute.ARMOR_TOUGHNESS && value > 0) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public int getPriority() {
        return 1; // Standard priority
    }
    
    @Override
    public boolean isActive() {
        return active && target.isOnline() && target.isValid();
    }
    
    /**
     * Deactivate this interceptor.
     * Called when the suppression duration expires.
     */
    public void deactivate() {
        this.active = false;
    }
    
    /**
     * Get the suppressed target.
     * 
     * @return The target player
     */
    public Player getTarget() {
        return target;
    }
}
