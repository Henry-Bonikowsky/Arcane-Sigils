package com.miracle.arcanesigils.effects;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active potion effects and their associated counter-modifiers for Ancient Crown suppression.
 * Manages cleanup when effects expire, players die, or players log out.
 */
public class PotionEffectTracker implements Listener {

    private final ArmorSetsPlugin plugin;

    // Map: Player UUID -> (Potion Type -> Counter Modifier Name)
    private final Map<UUID, Map<PotionEffectType, String>> trackedEffects = new ConcurrentHashMap<>();

    public PotionEffectTracker(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a counter-modifier for a specific potion effect.
     *
     * @param player The affected player
     * @param effectType The potion effect type
     * @param modifierName The name of the counter-modifier applied
     */
    public void trackEffect(Player player, PotionEffectType effectType, String modifierName) {
        trackedEffects.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                     .put(effectType, modifierName);
    }

    /**
     * Removes tracking for a specific potion effect and cleans up the counter-modifier.
     *
     * @param player The affected player
     * @param effectType The potion effect type to untrack
     */
    public void untrackEffect(Player player, PotionEffectType effectType) {
        Map<PotionEffectType, String> playerEffects = trackedEffects.get(player.getUniqueId());
        if (playerEffects == null) return;

        String modifierName = playerEffects.remove(effectType);
        if (modifierName != null) {
            // Remove the counter-modifier
            Attribute attribute = getAttributeForEffect(effectType);
            if (attribute != null) {
                plugin.getAttributeModifierManager().removeNamedModifier(player, attribute, modifierName);
            }
        }

        // Clean up empty map
        if (playerEffects.isEmpty()) {
            trackedEffects.remove(player.getUniqueId());
        }
    }

    /**
     * Checks if a player has a tracked counter-modifier for a specific potion effect.
     *
     * @param player The player to check
     * @param effectType The potion effect type
     * @return true if a counter-modifier is currently tracked
     */
    public boolean isTracked(Player player, PotionEffectType effectType) {
        Map<PotionEffectType, String> playerEffects = trackedEffects.get(player.getUniqueId());
        return playerEffects != null && playerEffects.containsKey(effectType);
    }

    /**
     * Removes all tracked effects and counter-modifiers for a player.
     *
     * @param player The player to clear
     */
    public void clearPlayer(Player player) {
        Map<PotionEffectType, String> playerEffects = trackedEffects.remove(player.getUniqueId());
        if (playerEffects == null) return;

        // Remove all counter-modifiers
        for (Map.Entry<PotionEffectType, String> entry : playerEffects.entrySet()) {
            Attribute attribute = getAttributeForEffect(entry.getKey());
            if (attribute != null) {
                plugin.getAttributeModifierManager().removeNamedModifier(player, attribute, entry.getValue());
            }
        }
    }

    /**
     * Maps potion effect types to their corresponding attributes.
     *
     * @param effectType The potion effect type
     * @return The attribute modified by this potion, or null if none
     */
    private Attribute getAttributeForEffect(PotionEffectType effectType) {
        if (effectType.equals(PotionEffectType.SLOWNESS) || effectType.equals(PotionEffectType.SPEED)) {
            return Attribute.MOVEMENT_SPEED;
        } else if (effectType.equals(PotionEffectType.STRENGTH) || effectType.equals(PotionEffectType.WEAKNESS)) {
            return Attribute.ATTACK_DAMAGE;
        }
        return null; // No attribute for damage-based effects like POISON, WITHER
    }

    // ===== Event Listeners for Automatic Cleanup =====

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionEffectRemove(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        EntityPotionEffectEvent.Action action = event.getAction();
        if (action == EntityPotionEffectEvent.Action.REMOVED ||
            action == EntityPotionEffectEvent.Action.CLEARED) {

            Player player = (Player) event.getEntity();
            PotionEffectType effectType = event.getModifiedType();

            untrackEffect(player, effectType);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Bukkit clears potion effects on death, we clear counter-modifiers
        clearPlayer(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up on logout to prevent memory leaks
        clearPlayer(event.getPlayer());
    }
}
