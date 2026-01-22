package com.miracle.arcanesigils.ai;

/**
 * Data class representing a reward signal sent to the AI for reinforcement learning.
 * These signals are sent via chat to provide feedback on sigil bind outcomes.
 */
public class RewardSignal {
    
    public enum Type {
        HIT,              // Sigil dealt damage
        MISS,             // Sigil failed (no effects executed)
        KILL,             // Killed entity with sigil
        COOLDOWN,         // Attempted to use while on cooldown
        HEAL,             // Healed with sigil
        BUFF,             // Applied buff/debuff
        CONDITION_FAIL,   // Condition check failed
        CC                // Crowd control applied (stun/slow/knockback)
    }
    
    private final Type type;
    private final int bindSlot;      // 1-4
    private final double value;      // damage, heal amount, duration, etc.
    private final String extra;      // entity type, buff name, condition fail reason, etc.
    
    public RewardSignal(Type type, int bindSlot, double value, String extra) {
        this.type = type;
        this.bindSlot = bindSlot;
        this.value = value;
        this.extra = extra;
    }
    
    // Factory methods for cleaner signal creation
    
    public static RewardSignal hit(int bindSlot, double damage) {
        return new RewardSignal(Type.HIT, bindSlot, damage, null);
    }
    
    public static RewardSignal miss(int bindSlot) {
        return new RewardSignal(Type.MISS, bindSlot, 0, null);
    }
    
    public static RewardSignal kill(int bindSlot, String entityType) {
        return new RewardSignal(Type.KILL, bindSlot, 0, entityType);
    }
    
    public static RewardSignal cooldown(int bindSlot, double remainingSeconds) {
        return new RewardSignal(Type.COOLDOWN, bindSlot, remainingSeconds, null);
    }
    
    public static RewardSignal heal(int bindSlot, double healAmount) {
        return new RewardSignal(Type.HEAL, bindSlot, healAmount, null);
    }
    
    public static RewardSignal buff(int bindSlot, String buffName, double durationSeconds) {
        return new RewardSignal(Type.BUFF, bindSlot, durationSeconds, buffName);
    }
    
    public static RewardSignal conditionFail(int bindSlot, String reason) {
        return new RewardSignal(Type.CONDITION_FAIL, bindSlot, 0, reason);
    }
    
    public static RewardSignal cc(int bindSlot, String ccType, double value) {
        return new RewardSignal(Type.CC, bindSlot, value, ccType);
    }
    
    /**
     * Formats the signal as a chat message.
     * Format: [SIGIL_{TYPE}:bind{slot}:{value}]
     */
    public String format() {
        StringBuilder sb = new StringBuilder("§7[§a");
        sb.append("SIGIL_").append(type.name());
        sb.append("§7:§ebind").append(bindSlot);
        
        // Add value/extra based on type
        switch (type) {
            case HIT:
                if (value > 0) {
                    sb.append("§7:§c").append(String.format("%.1f", value));
                }
                break;
            case KILL:
                if (extra != null) {
                    sb.append("§7:§c").append(extra);
                }
                break;
            case COOLDOWN:
                sb.append("§7:§6").append(String.format("%.1f", value));
                break;
            case HEAL:
                sb.append("§7:§a").append(String.format("%.1f", value));
                break;
            case BUFF:
                if (extra != null) {
                    sb.append("§7:§d").append(extra);
                    sb.append("§7:§d").append(String.format("%.1f", value));
                }
                break;
            case CONDITION_FAIL:
                if (extra != null) {
                    sb.append("§7:§c").append(extra);
                }
                break;
            case CC:
                if (extra != null) {
                    sb.append("§7:§5").append(extra);
                    sb.append("§7:§5").append(String.format("%.1f", value));
                }
                break;
        }
        
        sb.append("§7]");
        return sb.toString();
    }
    
    // Getters
    public Type getType() { return type; }
    public int getBindSlot() { return bindSlot; }
    public double getValue() { return value; }
    public String getExtra() { return extra; }
}
