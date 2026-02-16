package com.miracle.arcanesigils.variables;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages sigil-scoped variables that persist while a sigil is equipped.
 * Variables are scoped to: Player UUID + Sigil ID + Armor Slot
 * 
 * Use cases:
 * - Track per-sigil charge counters (e.g., King's Brace charge: 0-100)
 * - Store state for individual sigil instances
 * - Handle multiple instances of the same sigil with separate state
 */
public class SigilVariableManager implements Listener {
    private final ArmorSetsPlugin plugin;
    
    // Map of composite key -> Map of variable name -> SigilVariable
    // Composite key format: "playerUUID|sigilId|slot"
    private final Map<String, Map<String, SigilVariable>> sigilVariables = new ConcurrentHashMap<>();
    
    /**
     * Data stored for each sigil variable.
     */
    private static class SigilVariable {
        Object value;
        long expiryTime; // System.currentTimeMillis() + duration, or Long.MAX_VALUE for permanent
        
        SigilVariable(Object value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }
    
    public SigilVariableManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        
        // Start tick task to auto-expire variables
        // Runs every 20 ticks (1 second)
        Bukkit.getScheduler().runTaskTimer(plugin, this::processTick, 20L, 20L);
        
        // Register listener for player quit cleanup
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Create composite key for variable lookup.
     * Format: "playerUUID|sigilId|slot"
     */
    private String makeKey(UUID playerId, String sigilId, String slot) {
        return playerId.toString() + "|" + sigilId.toLowerCase() + "|" + slot.toUpperCase();
    }
    
    /**
     * Set a sigil-scoped variable with a duration.
     * 
     * @param player The player
     * @param sigilId The sigil ID
     * @param slot The armor slot (HELMET, CHESTPLATE, LEGGINGS, BOOTS)
     * @param varName Variable name (case-insensitive)
     * @param value Variable value (any object)
     * @param durationSeconds Duration in seconds (0 or negative for permanent until cleared)
     */
    public void setSigilVariable(Player player, String sigilId, String slot, String varName, Object value, int durationSeconds) {
        if (player == null || sigilId == null || slot == null || varName == null) return;
        
        String key = makeKey(player.getUniqueId(), sigilId, slot);
        String normalizedVarName = varName.toLowerCase();
        
        sigilVariables.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        
        long expiryTime;
        if (durationSeconds <= 0) {
            expiryTime = Long.MAX_VALUE; // Permanent
        } else {
            expiryTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        }
        
        SigilVariable var = new SigilVariable(value, expiryTime);
        sigilVariables.get(key).put(normalizedVarName, var);
        
        LogHelper.debug("[SigilVariableManager] Set variable '%s' for %s (sigil=%s, slot=%s): value=%s, duration=%ds",
            normalizedVarName, player.getName(), sigilId, slot, value, durationSeconds);
    }
    
    /**
     * Get a sigil-scoped variable value.
     * 
     * @param player The player
     * @param sigilId The sigil ID
     * @param slot The armor slot (HELMET, CHESTPLATE, LEGGINGS, BOOTS)
     * @param varName Variable name (case-insensitive)
     * @return The variable value, or null if not set or expired
     */
    public Object getSigilVariable(Player player, String sigilId, String slot, String varName) {
        if (player == null || sigilId == null || slot == null || varName == null) return null;
        
        String key = makeKey(player.getUniqueId(), sigilId, slot);
        String normalizedVarName = varName.toLowerCase();
        
        Map<String, SigilVariable> vars = sigilVariables.get(key);
        if (vars == null) return null;
        
        SigilVariable var = vars.get(normalizedVarName);
        if (var == null) return null;
        
        // Check if expired
        if (System.currentTimeMillis() > var.expiryTime) {
            vars.remove(normalizedVarName);
            if (vars.isEmpty()) {
                sigilVariables.remove(key);
            }
            return null;
        }
        
        return var.value;
    }
    
    /**
     * Get a sigil variable as an integer, with a default value if not set or not an integer.
     * 
     * @param player The player
     * @param sigilId The sigil ID
     * @param slot The armor slot
     * @param varName Variable name
     * @param defaultValue Default value if not set
     * @return The integer value, or defaultValue if not set/invalid
     */
    public int getSigilVariableInt(Player player, String sigilId, String slot, String varName, int defaultValue) {
        Object value = getSigilVariable(player, sigilId, slot, varName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * Check if a sigil has a variable set (and not expired).
     * 
     * @param player The player
     * @param sigilId The sigil ID
     * @param slot The armor slot
     * @param varName Variable name (case-insensitive)
     * @return true if the variable exists and hasn't expired
     */
    public boolean hasSigilVariable(Player player, String sigilId, String slot, String varName) {
        return getSigilVariable(player, sigilId, slot, varName) != null;
    }
    
    /**
     * Clear a specific variable for a sigil.
     * 
     * @param player The player
     * @param sigilId The sigil ID
     * @param slot The armor slot
     * @param varName Variable name (case-insensitive)
     */
    public void clearSigilVariable(Player player, String sigilId, String slot, String varName) {
        if (player == null || sigilId == null || slot == null || varName == null) return;
        
        String key = makeKey(player.getUniqueId(), sigilId, slot);
        String normalizedVarName = varName.toLowerCase();
        
        Map<String, SigilVariable> vars = sigilVariables.get(key);
        if (vars != null) {
            vars.remove(normalizedVarName);
            if (vars.isEmpty()) {
                sigilVariables.remove(key);
            }
            
            LogHelper.debug("[SigilVariableManager] Cleared variable '%s' for %s (sigil=%s, slot=%s)",
                normalizedVarName, player.getName(), sigilId, slot);
        }
    }
    
    /**
     * Clear all variables for a specific sigil instance (player + sigil + slot).
     * 
     * @param player The player
     * @param sigilId The sigil ID
     * @param slot The armor slot
     */
    public void clearAllSigilVariables(Player player, String sigilId, String slot) {
        if (player == null || sigilId == null || slot == null) return;
        
        String key = makeKey(player.getUniqueId(), sigilId, slot);
        Map<String, SigilVariable> removed = sigilVariables.remove(key);
        
        if (removed != null && !removed.isEmpty()) {
            LogHelper.debug("[SigilVariableManager] Cleared %d variables for %s (sigil=%s, slot=%s)",
                removed.size(), player.getName(), sigilId, slot);
        }
    }
    
    /**
     * Clear all variables for a player (all sigils, all slots).
     * Called when player quits.
     * 
     * @param playerId The player's UUID
     */
    public void clearAllPlayerVariables(UUID playerId) {
        if (playerId == null) return;
        
        String playerPrefix = playerId.toString() + "|";
        sigilVariables.entrySet().removeIf(entry -> entry.getKey().startsWith(playerPrefix));
        
        Player player = Bukkit.getPlayer(playerId);
        String playerName = player != null ? player.getName() : playerId.toString();
        LogHelper.debug("[SigilVariableManager] Cleared all variables for %s", playerName);
    }
    
    /**
     * Clear all variables for a specific player's armor slot.
     * Called when armor is unequipped.
     * 
     * @param playerId The player's UUID
     * @param slot The armor slot
     */
    public void clearSlotVariables(UUID playerId, String slot) {
        if (playerId == null || slot == null) return;
        
        String slotSuffix = "|" + slot.toUpperCase();
        String playerPrefix = playerId.toString() + "|";
        
        sigilVariables.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            return key.startsWith(playerPrefix) && key.endsWith(slotSuffix);
        });
        
        Player player = Bukkit.getPlayer(playerId);
        String playerName = player != null ? player.getName() : playerId.toString();
        LogHelper.debug("[SigilVariableManager] Cleared all variables for %s slot=%s", playerName, slot);
    }
    
    /**
     * Process tick - expire old variables.
     */
    private void processTick() {
        long now = System.currentTimeMillis();
        
        sigilVariables.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            Map<String, SigilVariable> vars = entry.getValue();
            
            // Remove expired variables
            vars.entrySet().removeIf(varEntry -> {
                if (now > varEntry.getValue().expiryTime) {
                    LogHelper.debug("[SigilVariableManager] Variable '%s' expired for key=%s",
                        varEntry.getKey(), key);
                    return true;
                }
                return false;
            });
            
            // Remove key if no variables left
            return vars.isEmpty();
        });
    }
    
    /**
     * Clean up variables when player quits.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearAllPlayerVariables(event.getPlayer().getUniqueId());
    }
    
    /**
     * Shutdown cleanup.
     */
    public void shutdown() {
        sigilVariables.clear();
    }
}
