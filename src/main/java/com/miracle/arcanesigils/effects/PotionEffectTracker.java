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

import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a suppressed potion effect that should be restored after suppression ends.
 */
record SuppressedEffect(PotionEffectType type, int amplifier, int remainingDuration, long removalTime) {}

/**
 * Tracks active potion effects and their associated counter-modifiers for Ancient Crown suppression.
 * Manages cleanup when effects expire, players die, or players log out.
 */
public class PotionEffectTracker implements Listener {

    private final ArmorSetsPlugin plugin;

    // Map: Player UUID -> (Potion Type -> Counter Modifier Name)
    private final Map<UUID, Map<PotionEffectType, String>> trackedEffects = new ConcurrentHashMap<>();

    // Map: Player UUID -> List of suppressed effects awaiting restoration
    private final Map<UUID, List<SuppressedEffect>> suppressedEffects = new ConcurrentHashMap<>();

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
                plugin.getModifierRegistry().removeNamedModifier(player, attribute, modifierName);
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
        // Clear suppressed effects (no restoration on death/quit)
        suppressedEffects.remove(player.getUniqueId());

        Map<PotionEffectType, String> playerEffects = trackedEffects.remove(player.getUniqueId());
        if (playerEffects == null) return;

        // Remove all counter-modifiers
        for (Map.Entry<PotionEffectType, String> entry : playerEffects.entrySet()) {
            Attribute attribute = getAttributeForEffect(entry.getKey());
            if (attribute != null) {
                plugin.getModifierRegistry().removeNamedModifier(player, attribute, entry.getValue());
            }
        }
    }

    // ===== Suppressed Effect Storage (for Cleopatra restoration) =====

    /**
     * Stores a potion effect that was suppressed, to be restored later.
     *
     * @param player The affected player
     * @param effect The potion effect that was removed
     */
    public void storeSuppressedEffect(Player player, PotionEffect effect) {
        SuppressedEffect suppressed = new SuppressedEffect(
            effect.getType(),
            effect.getAmplifier(),
            effect.getDuration(),
            System.currentTimeMillis()
        );
        suppressedEffects.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>())
                        .add(suppressed);
    }

    /**
     * Retrieves and clears all suppressed effects for a player.
     * Calculates remaining duration based on how long they were suppressed.
     *
     * @param player The player
     * @param suppressionDurationTicks How long the suppression lasted (in ticks)
     * @return List of PotionEffects to restore with adjusted durations, or empty list
     */
    public List<PotionEffect> popSuppressedEffects(Player player, int suppressionDurationTicks) {
        List<SuppressedEffect> stored = suppressedEffects.remove(player.getUniqueId());
        if (stored == null || stored.isEmpty()) {
            return new ArrayList<>();
        }

        List<PotionEffect> toRestore = new ArrayList<>();
        for (SuppressedEffect suppressed : stored) {
            // Calculate remaining duration
            int remainingDuration = suppressed.remainingDuration() - suppressionDurationTicks;

            // Only restore if there's meaningful duration left (at least 1 second)
            if (remainingDuration >= 20) {
                PotionEffect restored = new PotionEffect(
                    suppressed.type(),
                    remainingDuration,
                    suppressed.amplifier(),
                    false,  // ambient
                    true,   // particles
                    true    // icon
                );
                toRestore.add(restored);
            }
        }

        return toRestore;
    }

    /**
     * Clears suppressed effects for a player without restoring them.
     *
     * @param player The player to clear
     */
    public void clearSuppressedEffects(Player player) {
        suppressedEffects.remove(player.getUniqueId());
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
