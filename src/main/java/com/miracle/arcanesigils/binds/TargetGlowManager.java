package com.miracle.arcanesigils.binds;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.utils.TargetFinder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages client-side glow effects for target highlighting.
 * When ability UI is toggled ON, highlights the entity the player is looking at.
 * Uses ProtocolLib to send glow packets only to the specific player.
 */
public class TargetGlowManager implements Listener {

    private final ArmorSetsPlugin plugin;
    private final ProtocolManager protocolManager;

    // Track active glow tasks per player
    private final Map<UUID, BukkitTask> glowTasks = new ConcurrentHashMap<>();

    // Track currently glowing entity per player
    private final Map<UUID, Entity> currentGlowTargets = new ConcurrentHashMap<>();

    // Configuration
    private static final double GLOW_RANGE = 15.0;
    private static final int UPDATE_INTERVAL_TICKS = 2; // 100ms

    public TargetGlowManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();

        // Register event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Start the glow task for a player when ability UI is toggled ON.
     */
    public void startGlowTask(Player player) {
        UUID uuid = player.getUniqueId();

        // Stop existing task if any
        stopGlowTask(player);

        // Start new task
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            updateGlow(player);
        }, 0L, UPDATE_INTERVAL_TICKS);

        glowTasks.put(uuid, task);
    }

    /**
     * Stop the glow task for a player when ability UI is toggled OFF.
     */
    public void stopGlowTask(Player player) {
        UUID uuid = player.getUniqueId();

        // Cancel the task
        BukkitTask task = glowTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        // Remove glow from current target
        Entity currentTarget = currentGlowTargets.remove(uuid);
        if (currentTarget != null && currentTarget.isValid()) {
            sendGlowPacket(player, currentTarget, false);
        }
    }

    /**
     * Update the glow effect based on what the player is looking at.
     */
    private void updateGlow(Player player) {
        if (!player.isOnline()) {
            stopGlowTask(player);
            return;
        }

        UUID uuid = player.getUniqueId();
        Entity currentTarget = currentGlowTargets.get(uuid);

        // Find entity player is looking at
        LivingEntity newTarget = TargetFinder.findLookTarget(player, GLOW_RANGE);

        // Same target - no change needed
        if (newTarget == currentTarget) {
            return;
        }

        // Remove glow from old target
        if (currentTarget != null && currentTarget.isValid()) {
            sendGlowPacket(player, currentTarget, false);
        }

        // Add glow to new target
        if (newTarget != null) {
            sendGlowPacket(player, newTarget, true);
            currentGlowTargets.put(uuid, newTarget);
        } else {
            currentGlowTargets.remove(uuid);
        }
    }

    /**
     * Send a glow packet to make an entity appear glowing (or not) to a specific player.
     * Uses ProtocolLib to send client-side only packets.
     */
    private void sendGlowPacket(Player player, Entity entity, boolean glowing) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);

            // Set entity ID
            packet.getIntegers().write(0, entity.getEntityId());

            // Build metadata
            List<WrappedDataValue> dataValues = new ArrayList<>();

            // Index 0 is the entity flags byte
            // Bit 0x40 (64) is the glowing flag
            byte flags = 0;

            // Preserve existing flags from the entity
            if (entity.isGlowing()) flags |= 0x40;
            if (entity.isInvisible()) flags |= 0x20;
            if (entity.getFireTicks() > 0) flags |= 0x01;
            if (entity instanceof Player && ((Player) entity).isSprinting()) flags |= 0x08;
            if (entity instanceof Player && ((Player) entity).isSneaking()) flags |= 0x02;

            // Set or clear the glow flag
            if (glowing) {
                flags |= 0x40;
            } else {
                flags &= ~0x40;
            }

            // Create the data value for index 0 (entity flags)
            WrappedDataValue flagsValue = new WrappedDataValue(
                0, // Index
                WrappedDataWatcher.Registry.get(Byte.class), // Serializer
                flags // Value
            );
            dataValues.add(flagsValue);

            // Write the data values to the packet
            packet.getDataValueCollectionModifier().write(0, dataValues);

            // Send packet only to the specific player
            protocolManager.sendServerPacket(player, packet);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send glow packet: " + e.getMessage());
        }
    }

    /**
     * Clean up when a player quits.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopGlowTask(event.getPlayer());
    }

    /**
     * Handle world changes - stop and restart glow task.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // If player had active glow task, restart it
        if (glowTasks.containsKey(uuid)) {
            // Clear current target (entity is in different world now)
            currentGlowTargets.remove(uuid);
        }
    }

    /**
     * Handle entity death - remove from tracking if it was a glow target.
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity deadEntity = event.getEntity();

        // Check if any player was targeting this entity
        currentGlowTargets.entrySet().removeIf(entry -> entry.getValue().equals(deadEntity));
    }

    /**
     * Clean up all tasks on plugin disable.
     */
    public void cleanup() {
        // Stop all glow tasks
        for (Map.Entry<UUID, BukkitTask> entry : glowTasks.entrySet()) {
            entry.getValue().cancel();

            // Try to remove glow from current targets
            Player player = plugin.getServer().getPlayer(entry.getKey());
            Entity target = currentGlowTargets.get(entry.getKey());
            if (player != null && target != null && target.isValid()) {
                sendGlowPacket(player, target, false);
            }
        }

        glowTasks.clear();
        currentGlowTargets.clear();
    }

    /**
     * Check if a player has an active glow task.
     */
    public boolean hasActiveGlow(Player player) {
        return glowTasks.containsKey(player.getUniqueId());
    }

    /**
     * Get the current target entity that the player is looking at (glowing).
     *
     * @param player The player
     * @return The target entity, or null if none
     */
    public LivingEntity getTarget(Player player) {
        Entity target = currentGlowTargets.get(player.getUniqueId());
        if (target instanceof LivingEntity living && living.isValid()) {
            return living;
        }
        return null;
    }

    /**
     * Get the current target entity by player UUID.
     *
     * @param uuid The player's UUID
     * @return The target entity, or null if none
     */
    public LivingEntity getTarget(UUID uuid) {
        Entity target = currentGlowTargets.get(uuid);
        if (target instanceof LivingEntity living && living.isValid()) {
            return living;
        }
        return null;
    }
}
