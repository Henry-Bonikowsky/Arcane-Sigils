package com.miracle.arcanesigils.interception;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages effect interceptors and fires interception events.
 * Central hub for the interception system.
 */
public class InterceptionManager {
    
    // Map of player UUID -> list of interceptors
    private final Map<UUID, List<EffectInterceptor>> playerInterceptors = new ConcurrentHashMap<>();
    
    /**
     * Register an interceptor for a player.
     * Called when a sigil with interception capability is equipped.
     * 
     * @param player The player to register for
     * @param interceptor The interceptor to register
     */
    public void registerInterceptor(Player player, EffectInterceptor interceptor) {
        UUID uuid = player.getUniqueId();
        playerInterceptors.computeIfAbsent(uuid, k -> new ArrayList<>()).add(interceptor);
        
        // Sort by priority (higher first)
        playerInterceptors.get(uuid).sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
    }
    
    /**
     * Unregister a specific interceptor for a player.
     * Called when a sigil is unequipped.
     * 
     * @param player The player
     * @param interceptor The interceptor to remove
     */
    public void unregisterInterceptor(Player player, EffectInterceptor interceptor) {
        UUID uuid = player.getUniqueId();
        List<EffectInterceptor> interceptors = playerInterceptors.get(uuid);
        if (interceptors != null) {
            interceptors.remove(interceptor);
            if (interceptors.isEmpty()) {
                playerInterceptors.remove(uuid);
            }
        }
    }
    
    /**
     * Unregister all interceptors for a player.
     * Called on player logout.
     * 
     * @param player The player
     */
    public void unregisterAll(Player player) {
        playerInterceptors.remove(player.getUniqueId());
    }
    
    /**
     * Fire an interception event to all registered interceptors for the target player.
     * Interceptors are called in priority order (highest first).
     * Processing stops if the event is cancelled.
     * 
     * @param event The interception event
     * @return The same event (potentially modified)
     */
    public InterceptionEvent fireIntercept(InterceptionEvent event) {
        List<EffectInterceptor> interceptors = playerInterceptors.get(event.getTarget().getUniqueId());
        
        // No interceptors registered for this player
        if (interceptors == null || interceptors.isEmpty()) {
            return event;
        }
        
        // Call each interceptor in priority order
        for (EffectInterceptor interceptor : interceptors) {
            // Skip inactive interceptors
            if (!interceptor.isActive()) {
                continue;
            }
            
            // Call the interceptor
            interceptor.intercept(event);
            
            // Stop if cancelled
            if (event.isCancelled()) {
                break;
            }
        }
        
        return event;
    }
    
    /**
     * Get the number of registered interceptors for a player.
     * Useful for debugging.
     * 
     * @param player The player
     * @return Number of interceptors
     */
    public int getInterceptorCount(Player player) {
        List<EffectInterceptor> interceptors = playerInterceptors.get(player.getUniqueId());
        return interceptors == null ? 0 : interceptors.size();
    }
    
    /**
     * Check if a player has any interceptors registered.
     *
     * @param player The player
     * @return true if player has interceptors
     */
    public boolean hasInterceptors(Player player) {
        return getInterceptorCount(player) > 0;
    }

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
}
