package com.miracle.arcanesigils.variables;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player-scoped variables that persist beyond single flow executions.
 * Variables auto-expire after a specified duration.
 * 
 * Use cases:
 * - Track ability active states (e.g., "quicksand_active")
 * - Store temporary player data accessible across multiple flows
 * - Any sigil state that needs to persist for a duration
 */
public class PlayerVariableManager implements Listener {
    private final ArmorSetsPlugin plugin;
    
    // Map of player UUID -> Map of variable name -> PlayerVariable
    private final Map<UUID, Map<String, PlayerVariable>> playerVariables = new ConcurrentHashMap<>();
    
    /**
     * Data stored for each player variable.
     */
    private static class PlayerVariable {
        Object value;
        long expiryTime; // System.currentTimeMillis() + duration, or Long.MAX_VALUE for permanent
        
        PlayerVariable(Object value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }
    
    public PlayerVariableManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        
        // Start tick task to auto-expire variables
        // Runs every 20 ticks (1 second) - sufficient for ability durations
        Bukkit.getScheduler().runTaskTimer(plugin, this::processTick, 20L, 20L);
        
        // Register listener for player quit cleanup
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Set a player variable with a duration.
     * 
     * @param playerId The player's UUID
     * @param name Variable name (case-insensitive)
     * @param value Variable value (any object)
     * @param durationSeconds Duration in seconds (0 or negative for permanent until cleared)
     */
    public void setVariable(UUID playerId, String name, Object value, int durationSeconds) {
        if (playerId == null || name == null || name.isEmpty()) return;
        
        String normalizedName = name.toLowerCase();
        
        playerVariables.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        
        long expiryTime;
        if (durationSeconds <= 0) {
            expiryTime = Long.MAX_VALUE; // Permanent
        } else {
            expiryTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        }
        
        PlayerVariable var = new PlayerVariable(value, expiryTime);
        playerVariables.get(playerId).put(normalizedName, var);
        
        Player player = Bukkit.getPlayer(playerId);
        String playerName = player != null ? player.getName() : playerId.toString();
        
        LogHelper.debug("[PlayerVariableManager] Set variable '%s' for %s: value=%s, duration=%ds",
            normalizedName, playerName, value, durationSeconds);
    }
    
    /**
     * Get a player variable value.
     * 
     * @param playerId The player's UUID
     * @param name Variable name (case-insensitive)
     * @return The variable value, or null if not set or expired
     */
    public Object getVariable(UUID playerId, String name) {
        if (playerId == null || name == null) return null;
        
        String normalizedName = name.toLowerCase();
        Map<String, PlayerVariable> vars = playerVariables.get(playerId);
        if (vars == null) return null;
        
        PlayerVariable var = vars.get(normalizedName);
        if (var == null) return null;
        
        // Check if expired
        if (System.currentTimeMillis() > var.expiryTime) {
            vars.remove(normalizedName);
            if (vars.isEmpty()) {
                playerVariables.remove(playerId);
            }
            return null;
        }
        
        return var.value;
    }
    
    /**
     * Check if a player has a variable set (and not expired).
     * 
     * @param playerId The player's UUID
     * @param name Variable name (case-insensitive)
     * @return true if the variable exists and hasn't expired
     */
    public boolean hasVariable(UUID playerId, String name) {
        return getVariable(playerId, name) != null;
    }
    
    /**
     * Clear a specific variable for a player.
     * 
     * @param playerId The player's UUID
     * @param name Variable name (case-insensitive)
     */
    public void clearVariable(UUID playerId, String name) {
        if (playerId == null || name == null) return;
        
        String normalizedName = name.toLowerCase();
        Map<String, PlayerVariable> vars = playerVariables.get(playerId);
        if (vars != null) {
            vars.remove(normalizedName);
            if (vars.isEmpty()) {
                playerVariables.remove(playerId);
            }
        }
    }
    
    /**
     * Clear all variables for a player.
     * 
     * @param playerId The player's UUID
     */
    public void clearAllVariables(UUID playerId) {
        if (playerId == null) return;
        playerVariables.remove(playerId);
    }
    
    /**
     * Process tick - expire old variables.
     */
    private void processTick() {
        long now = System.currentTimeMillis();
        
        playerVariables.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            Map<String, PlayerVariable> vars = entry.getValue();
            
            // Remove expired variables
            vars.entrySet().removeIf(varEntry -> {
                if (now > varEntry.getValue().expiryTime) {
                    Player player = Bukkit.getPlayer(playerId);
                    String playerName = player != null ? player.getName() : playerId.toString();
                    LogHelper.debug("[PlayerVariableManager] Variable '%s' expired for %s",
                        varEntry.getKey(), playerName);
                    return true;
                }
                return false;
            });
            
            // Remove player entry if no variables left
            return vars.isEmpty();
        });
    }
    
    /**
     * Clean up variables when player quits.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearAllVariables(event.getPlayer().getUniqueId());
    }
    
    /**
     * Shutdown cleanup.
     */
    public void shutdown() {
        playerVariables.clear();
    }
}
