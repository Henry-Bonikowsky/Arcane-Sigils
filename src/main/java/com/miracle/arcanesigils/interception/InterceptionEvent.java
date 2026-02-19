package com.miracle.arcanesigils.interception;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Event fired before an effect or attribute modifier is applied to a player.
 * Interceptors can modify or cancel this event.
 */
public class InterceptionEvent {
    
    public enum Type {
        /** Potion effect being applied */
        POTION_EFFECT,
        /** Attribute modifier being applied */
        ATTRIBUTE_MODIFIER
    }
    
    private final Type type;
    private final Player target;
    private final Object source;
    private boolean cancelled;
    private boolean modified;
    
    // For POTION_EFFECT
    private PotionEffectType potionType;
    private int amplifier;
    private int duration;
    
    // For ATTRIBUTE_MODIFIER
    private Attribute attributeType;
    private AttributeModifier.Operation operation;
    private double value;
    private String modifierName;
    
    /**
     * Create a potion effect interception event.
     */
    public InterceptionEvent(Type type, Player target, Object source,
                            PotionEffectType potionType, int amplifier, int duration) {
        this.type = type;
        this.target = target;
        this.source = source;
        this.potionType = potionType;
        this.amplifier = amplifier;
        this.duration = duration;
        this.cancelled = false;
        this.modified = false;
    }
    
    /**
     * Create an attribute modifier interception event.
     */
    public InterceptionEvent(Type type, Player target, Object source,
                            Attribute attributeType, AttributeModifier.Operation operation,
                            double value, String modifierName) {
        this.type = type;
        this.target = target;
        this.source = source;
        this.attributeType = attributeType;
        this.operation = operation;
        this.value = value;
        this.modifierName = modifierName;
        this.cancelled = false;
        this.modified = false;
    }
    
    public Type getType() {
        return type;
    }
    
    public Player getTarget() {
        return target;
    }
    
    public Object getSource() {
        return source;
    }
    
    public boolean isCancelled() {
        return cancelled;
    }
    
    public void cancel() {
        this.cancelled = true;
        this.modified = true;
    }
    
    public boolean wasModified() {
        return modified;
    }
    
    // ===== POTION EFFECT METHODS =====
    
    public PotionEffectType getPotionType() {
        return potionType;
    }
    
    public int getAmplifier() {
        return amplifier;
    }
    
    public int getDuration() {
        return duration;
    }
    
    /**
     * Modify the amplifier by a multiplier.
     * Example: modifyAmplifier(0.4) reduces amplifier to 40% of original (60% reduction)
     */
    public void modifyAmplifier(double multiplier) {
        if (type != Type.POTION_EFFECT) {
            throw new IllegalStateException("Cannot modify amplifier on non-potion event");
        }
        
        // Calculate new amplifier and round (not truncate) to integer
        int newAmplifier = (int) Math.round(amplifier * multiplier);
        
        // Clamp to 0 minimum (can't have negative amplifier)
        this.amplifier = Math.max(0, newAmplifier);
        this.modified = true;
    }
    
    /**
     * Modify the duration by a multiplier.
     * Example: modifyDuration(0.5) reduces duration to 50% of original
     */
    public void modifyDuration(double multiplier) {
        if (type != Type.POTION_EFFECT) {
            throw new IllegalStateException("Cannot modify duration on non-potion event");
        }
        
        int newDuration = (int) (duration * multiplier);
        this.duration = Math.max(0, newDuration);
        this.modified = true;
    }
    
    // ===== ATTRIBUTE MODIFIER METHODS =====
    
    public Attribute getAttributeType() {
        return attributeType;
    }
    
    public AttributeModifier.Operation getOperation() {
        return operation;
    }
    
    public double getValue() {
        return value;
    }
    
    public String getModifierName() {
        return modifierName;
    }
    
    /**
     * Modify the attribute modifier value by a multiplier.
     * Example: modifyValue(0.4) reduces a -0.25 speed modifier to -0.10 (60% reduction)
     */
    public void modifyValue(double multiplier) {
        if (type != Type.ATTRIBUTE_MODIFIER) {
            throw new IllegalStateException("Cannot modify value on non-attribute event");
        }
        
        this.value = value * multiplier;
        this.modified = true;
    }
}
