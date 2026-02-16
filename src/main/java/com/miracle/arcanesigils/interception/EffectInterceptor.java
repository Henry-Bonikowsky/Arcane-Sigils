package com.miracle.arcanesigils.interception;

/**
 * Interface for sigils that want to intercept and modify effects before they're applied.
 * Examples: Ancient Crown (reduce negative effects), Cleopatra (suppress defensive buffs)
 */
public interface EffectInterceptor {
    
    /**
     * Called before an effect is applied to a player.
     * Interceptor can modify the event or cancel it entirely.
     * 
     * @param event The interception event containing effect details
     * @return Result indicating if the event was modified
     */
    InterceptionResult intercept(InterceptionEvent event);
    
    /**
     * Priority for ordering multiple interceptors.
     * Higher priority runs first.
     * 
     * Recommended values:
     * - Reducers (Ancient Crown): 100
     * - Blockers (Cleopatra): 50
     * - Other: 0
     * 
     * @return Priority value
     */
    int getPriority();
    
    /**
     * Check if this interceptor is currently active.
     * Inactive interceptors are skipped.
     * 
     * @return true if active
     */
    default boolean isActive() {
        return true;
    }
}
