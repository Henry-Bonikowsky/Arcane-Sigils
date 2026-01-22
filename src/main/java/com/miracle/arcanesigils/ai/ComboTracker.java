package com.miracle.arcanesigils.ai;

import org.bukkit.entity.Player;

import java.util.*;

/**
 * Tracks bind activation sequences to detect combos for AI learning.
 * A combo is detected when multiple binds are activated within the configured time window.
 */
public class ComboTracker {
    
    private final long comboWindowMs;
    private final Map<UUID, List<ComboEntry>> recentActivations = new HashMap<>();
    
    public ComboTracker(long comboWindowMs) {
        this.comboWindowMs = comboWindowMs;
    }
    
    /**
     * Record a bind activation and check for combos.
     * @return ComboInfo if a combo was detected, null otherwise
     */
    public ComboInfo recordActivation(Player player, int bindSlot, double damageDealt) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        // Get or create activation history for this player
        List<ComboEntry> history = recentActivations.computeIfAbsent(
            playerId, k -> new ArrayList<>()
        );
        
        // Add current activation
        history.add(new ComboEntry(bindSlot, damageDealt, now));
        
        // Clean up old entries (outside combo window)
        history.removeIf(e -> now - e.timestamp > comboWindowMs);
        
        // Check for combo (2+ activations within window)
        if (history.size() >= 2) {
            ComboEntry prev = history.get(history.size() - 2);
            ComboEntry curr = history.get(history.size() - 1);
            
            // Combo detected if different binds used within window
            if (prev.bindSlot != curr.bindSlot && (curr.timestamp - prev.timestamp) <= comboWindowMs) {
                // Calculate total damage for this combo
                double totalDamage = history.stream()
                    .filter(e -> now - e.timestamp <= comboWindowMs)
                    .mapToDouble(e -> e.damage)
                    .sum();
                
                return new ComboInfo(prev.bindSlot, curr.bindSlot, totalDamage);
            }
        }
        
        return null; // No combo
    }
    
    /**
     * Clear activation history for a player (e.g., on logout).
     */
    public void clearPlayer(UUID playerId) {
        recentActivations.remove(playerId);
    }
    
    /**
     * Represents a single bind activation.
     */
    private static class ComboEntry {
        final int bindSlot;
        final double damage;
        final long timestamp;
        
        ComboEntry(int bindSlot, double damage, long timestamp) {
            this.bindSlot = bindSlot;
            this.damage = damage;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Information about a detected combo.
     */
    public static class ComboInfo {
        public final int firstBind;
        public final int secondBind;
        public final double totalDamage;
        
        ComboInfo(int firstBind, int secondBind, double totalDamage) {
            this.firstBind = firstBind;
            this.secondBind = secondBind;
            this.totalDamage = totalDamage;
        }
        
        public String getComboString() {
            return "bind" + firstBind + "+bind" + secondBind;
        }
    }
}
