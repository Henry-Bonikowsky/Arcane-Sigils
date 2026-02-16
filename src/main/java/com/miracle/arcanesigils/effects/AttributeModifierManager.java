package com.miracle.arcanesigils.effects;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages named attribute modifiers to prevent stacking bugs.
 * 
 * Problem: Adding attribute modifiers without proper tracking causes infinite stacking
 * (e.g., King's Brace adding 20+ damage reduction modifiers instead of updating 1).
 * 
 * Solution: Use consistent UUIDs generated from modifier names to identify and replace
 * existing modifiers instead of stacking them.
 * 
 * Features:
 * - Named modifiers with consistent UUIDs (no stacking)
 * - Automatic duration-based removal
 * - Entity cleanup when entity dies/is removed
 * - Thread-safe concurrent access
 * 
 * Usage:
 *   // Add/update a named modifier (replaces if exists)
 *   manager.setNamedModifier(player, Attribute.ARMOR, "kings_brace_dr", 4.0, ADD_NUMBER, 60);
 *   
 *   // Remove a specific named modifier
 *   manager.removeNamedModifier(player, Attribute.ARMOR, "kings_brace_dr");
 *   
 *   // Check if modifier exists
 *   boolean has = manager.hasNamedModifier(player, Attribute.ARMOR, "kings_brace_dr");
 */
public class AttributeModifierManager implements Listener {

    private final ArmorSetsPlugin plugin;
    
    // Track active modifiers: EntityUUID -> Attribute -> ModifierName -> RemovalTask
    private final Map<UUID, Map<Attribute, Map<String, BukkitTask>>> activeModifiers;
    
    // Cleanup task that runs periodically
    private BukkitTask cleanupTask;
    
    public AttributeModifierManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.activeModifiers = new ConcurrentHashMap<>();
        
        // Start periodic cleanup task (runs every 5 seconds)
        startCleanupTask();
    }
    
    /**
     * Add or update a named attribute modifier.
     * 
     * If a modifier with the same name already exists on this entity+attribute,
     * it will be removed before adding the new one (prevents stacking).
     * 
     * @param entity The entity to modify
     * @param attribute The attribute to modify (e.g., Attribute.ARMOR)
     * @param name Unique name for this modifier (e.g., "kings_brace_dr")
     * @param value The modifier value
     * @param operation How to apply the value (ADD_NUMBER, ADD_SCALAR, MULTIPLY_SCALAR_1)
     * @param durationSeconds How long before auto-removal (0 = never expires)
     * @return true if successfully applied
     */
    public boolean setNamedModifier(LivingEntity entity, Attribute attribute, String name, 
                                     double value, AttributeModifier.Operation operation, 
                                     int durationSeconds) {
        if (entity == null || attribute == null || name == null || name.isEmpty()) {
            return false;
        }
        
        AttributeInstance attrInstance = entity.getAttribute(attribute);
        if (attrInstance == null) {
            plugin.getLogger().warning("[AttributeModifierManager] Entity " + entity.getType() + 
                                      " does not have attribute " + attribute.name());
            return false;
        }
        
        // Generate consistent UUID from name
        UUID modifierUUID = generateUUIDFromName(name);
        
        // Create namespaced key for the modifier
        NamespacedKey key = new NamespacedKey(plugin, "attr_" + sanitizeName(name));
        
        // Remove existing modifier with this UUID/key if it exists
        removeExistingModifier(entity, attrInstance, modifierUUID, key);
        
        // Create the new modifier
        AttributeModifier modifier = new AttributeModifier(key, value, operation);

        // CHECK INTERCEPTION BEFORE APPLYING
        com.miracle.arcanesigils.interception.InterceptionManager interceptionManager = plugin.getInterceptionManager();

        if (interceptionManager != null && entity instanceof org.bukkit.entity.Player player) {
            com.miracle.arcanesigils.interception.InterceptionEvent interceptionEvent =
                new com.miracle.arcanesigils.interception.InterceptionEvent(
                    com.miracle.arcanesigils.interception.InterceptionEvent.Type.ATTRIBUTE_MODIFIER,
                    player,
                    null,
                    attribute,
                    operation,
                    value,
                    name
                );

            com.miracle.arcanesigils.interception.InterceptionEvent result =
                interceptionManager.fireIntercept(interceptionEvent);

            if (result.isCancelled()) {
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
        
        // Cancel any existing removal task for this modifier
        cancelRemovalTask(entity.getUniqueId(), attribute, name);
        
        // Schedule automatic removal if duration > 0
        if (durationSeconds > 0) {
            BukkitTask removalTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                removeNamedModifier(entity, attribute, name);
            }, durationSeconds * 20L);
            
            // Track this removal task
            trackRemovalTask(entity.getUniqueId(), attribute, name, removalTask);
        }
        
        if (plugin.getConfig().getBoolean("settings.debug", false)) {
            plugin.getLogger().info(String.format(
                "[AttributeModifierManager] Set modifier '%s' on %s's %s: %+.2f (%s) for %ds",
                name, entity.getName(), attribute.name(), value, operation, durationSeconds
            ));
        }
        
        return true;
    }
    
    /**
     * Remove a named attribute modifier from an entity.
     * 
     * @param entity The entity
     * @param attribute The attribute
     * @param name The modifier name
     * @return true if a modifier was found and removed
     */
    public boolean removeNamedModifier(LivingEntity entity, Attribute attribute, String name) {
        if (entity == null || attribute == null || name == null) {
            return false;
        }
        
        AttributeInstance attrInstance = entity.getAttribute(attribute);
        if (attrInstance == null) {
            return false;
        }
        
        // Generate UUID and key for this modifier
        UUID modifierUUID = generateUUIDFromName(name);
        NamespacedKey key = new NamespacedKey(plugin, "attr_" + sanitizeName(name));
        
        // Remove the modifier
        boolean removed = removeExistingModifier(entity, attrInstance, modifierUUID, key);
        
        // Cancel removal task
        cancelRemovalTask(entity.getUniqueId(), attribute, name);
        
        if (removed && plugin.getConfig().getBoolean("settings.debug", false)) {
            plugin.getLogger().info(String.format(
                "[AttributeModifierManager] Removed modifier '%s' from %s's %s",
                name, entity.getName(), attribute.name()
            ));
        }
        
        return removed;
    }
    
    /**
     * Check if an entity has a specific named modifier.
     * 
     * @param entity The entity
     * @param attribute The attribute
     * @param name The modifier name
     * @return true if the modifier exists
     */
    public boolean hasNamedModifier(LivingEntity entity, Attribute attribute, String name) {
        if (entity == null || attribute == null || name == null) {
            return false;
        }
        
        AttributeInstance attrInstance = entity.getAttribute(attribute);
        if (attrInstance == null) {
            return false;
        }
        
        UUID modifierUUID = generateUUIDFromName(name);
        String keyString = "attr_" + sanitizeName(name);
        
        for (AttributeModifier modifier : attrInstance.getModifiers()) {
            // Check by key string OR by UUID (for compatibility)
            if (modifier.getKey().getKey().equals(keyString)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Remove all named modifiers from an entity.
     * Useful for cleanup when entity dies or is removed.
     * 
     * @param entityId The entity UUID
     */
    public void removeAllModifiers(UUID entityId) {
        Map<Attribute, Map<String, BukkitTask>> entityModifiers = activeModifiers.remove(entityId);
        if (entityModifiers != null) {
            // Cancel all removal tasks for this entity
            entityModifiers.values().forEach(attributeMap -> 
                attributeMap.values().forEach(task -> {
                    if (task != null && !task.isCancelled()) {
                        task.cancel();
                    }
                })
            );
        }
    }
    
    /**
     * Get the current value of a named modifier.
     * 
     * @param entity The entity
     * @param attribute The attribute
     * @param name The modifier name
     * @return The modifier value, or 0.0 if not found
     */
    public double getModifierValue(LivingEntity entity, Attribute attribute, String name) {
        if (entity == null || attribute == null || name == null) {
            return 0.0;
        }
        
        AttributeInstance attrInstance = entity.getAttribute(attribute);
        if (attrInstance == null) {
            return 0.0;
        }
        
        String keyString = "attr_" + sanitizeName(name);
        
        for (AttributeModifier modifier : attrInstance.getModifiers()) {
            if (modifier.getKey().getKey().equals(keyString)) {
                return modifier.getAmount();
            }
        }
        
        return 0.0;
    }
    
    // ========== Internal Methods ==========
    
    /**
     * Generate a consistent UUID from a modifier name.
     * Same name always produces the same UUID.
     */
    private UUID generateUUIDFromName(String name) {
        return UUID.nameUUIDFromBytes(("arcane_sigils_" + name).getBytes());
    }
    
    /**
     * Sanitize name to valid NamespacedKey format.
     * Only lowercase alphanumeric and underscores allowed.
     */
    private String sanitizeName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }
    
    /**
     * Remove an existing modifier from an attribute instance.
     * Checks both by UUID and by NamespacedKey for compatibility.
     */
    private boolean removeExistingModifier(LivingEntity entity, AttributeInstance attrInstance, 
                                           UUID modifierUUID, NamespacedKey key) {
        AttributeModifier toRemove = null;
        String keyString = key.getKey();
        
        // Find modifier by key string (most reliable)
        for (AttributeModifier existing : attrInstance.getModifiers()) {
            if (existing.getKey().getKey().equals(keyString)) {
                toRemove = existing;
                break;
            }
        }
        
        if (toRemove != null) {
            attrInstance.removeModifier(toRemove);
            return true;
        }
        
        return false;
    }
    
    /**
     * Track a removal task for later cancellation.
     */
    private void trackRemovalTask(UUID entityId, Attribute attribute, String name, BukkitTask task) {
        activeModifiers
            .computeIfAbsent(entityId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(attribute, k -> new ConcurrentHashMap<>())
            .put(name, task);
    }
    
    /**
     * Cancel a removal task if it exists.
     */
    private void cancelRemovalTask(UUID entityId, Attribute attribute, String name) {
        Map<Attribute, Map<String, BukkitTask>> entityModifiers = activeModifiers.get(entityId);
        if (entityModifiers != null) {
            Map<String, BukkitTask> attributeMap = entityModifiers.get(attribute);
            if (attributeMap != null) {
                BukkitTask task = attributeMap.remove(name);
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
                
                // Cleanup empty maps
                if (attributeMap.isEmpty()) {
                    entityModifiers.remove(attribute);
                }
            }
            if (entityModifiers.isEmpty()) {
                activeModifiers.remove(entityId);
            }
        }
    }
    
    /**
     * Start periodic cleanup task to remove dead entities from tracking.
     */
    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Remove entries for entities that no longer exist
            activeModifiers.entrySet().removeIf(entry -> {
                UUID entityId = entry.getKey();
                // Check if entity still exists in any world
                boolean exists = Bukkit.getWorlds().stream()
                    .anyMatch(world -> world.getEntity(entityId) != null);
                
                if (!exists) {
                    // Cancel all tasks for this entity
                    entry.getValue().values().forEach(attributeMap ->
                        attributeMap.values().forEach(task -> {
                            if (task != null && !task.isCancelled()) {
                                task.cancel();
                            }
                        })
                    );
                    return true; // Remove this entry
                }
                return false; // Keep this entry
            });
        }, 100L, 100L); // Run every 5 seconds (100 ticks)
    }

    /**
     * Clean up attribute modifiers when player dies.
     * Prevents orphaned modifiers from persisting on respawn.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        removeAllModifiers(event.getEntity().getUniqueId());
    }

    /**
     * Clean up attribute modifiers when player disconnects.
     * Prevents memory leaks and stale data on reconnect.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeAllModifiers(event.getPlayer().getUniqueId());
    }

    /**
     * Clean up attribute modifiers when entity dies.
     * Handles non-player entities (mobs, pets, etc).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        removeAllModifiers(event.getEntity().getUniqueId());
    }

    /**
     * Shutdown the manager and cancel all tasks.
     */
    public void shutdown() {
        // Cancel cleanup task
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        
        // Cancel all removal tasks
        activeModifiers.values().forEach(entityMap ->
            entityMap.values().forEach(attributeMap ->
                attributeMap.values().forEach(task -> {
                    if (task != null && !task.isCancelled()) {
                        task.cancel();
                    }
                })
            )
        );
        
        // Clear all tracking
        activeModifiers.clear();
        
        plugin.getLogger().info("[AttributeModifierManager] Shutdown complete");
    }
    
    /**
     * Get statistics about active modifiers (for debugging).
     */
    public Map<String, Integer> getStatistics() {
        int totalEntities = activeModifiers.size();
        int totalModifiers = activeModifiers.values().stream()
            .mapToInt(entityMap -> entityMap.values().stream()
                .mapToInt(Map::size)
                .sum())
            .sum();
        
        Map<String, Integer> stats = new HashMap<>();
        stats.put("tracked_entities", totalEntities);
        stats.put("active_modifiers", totalModifiers);
        return stats;
    }
}
