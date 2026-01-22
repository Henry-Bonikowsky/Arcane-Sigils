package com.miracle.arcanesigils.effects;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.events.SignalType;
import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.flow.FlowExecutor;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages marks (string tags) on entities.
 * Marks can be used to track states like "PHARAOH_MARK" for ability synergies.
 *
 * Marks can have behaviors attached - when applied:
 * - EFFECT_STATIC flows run once on application
 * - TICK flows run every second while mark is active
 * - EXPIRE flows run when mark expires
 *
 * Re-applying a mark refreshes the timer without re-running EFFECT_STATIC.
 */
public class MarkManager implements Listener {
    private final ArmorSetsPlugin plugin;

    // Map of entity UUID -> Map of mark name -> MarkData
    private final Map<UUID, Map<String, MarkData>> entityMarks = new ConcurrentHashMap<>();

    /**
     * Data stored for each mark on an entity.
     */
    private static class MarkData {
        long expiryTime;
        String behaviorId;      // Optional behavior sigil ID
        UUID ownerUUID;         // Who applied the mark (for context)
        boolean staticApplied;  // Whether EFFECT_STATIC has been run

        MarkData(long expiryTime, String behaviorId, UUID ownerUUID) {
            this.expiryTime = expiryTime;
            this.behaviorId = behaviorId;
            this.ownerUUID = ownerUUID;
            this.staticApplied = false;
        }
    }

    public MarkManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;

        // Start task to handle mark expiration and TICK signals
        // Ticks every 2 game ticks (0.1 seconds) for responsive behaviors like quicksand pull
        Bukkit.getScheduler().runTaskTimer(plugin, this::processTick, 2L, 2L);

        // Register event listener for player quit cleanup
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Apply a mark to an entity with a duration (no behavior).
     */
    public void applyMark(LivingEntity entity, String markName, double durationSeconds) {
        applyMark(entity, markName, durationSeconds, null, null);
    }

    /**
     * Apply a mark to an entity with a duration and optional behavior.
     *
     * @param entity The entity to mark
     * @param markName The mark identifier (e.g., "PHARAOH_MARK")
     * @param durationSeconds Duration in seconds (0 or negative for permanent until cleared)
     * @param behaviorId Optional behavior sigil ID to run while marked
     * @param owner The player who applied the mark (for context)
     */
    public void applyMark(LivingEntity entity, String markName, double durationSeconds,
                          String behaviorId, Player owner) {
        if (entity == null || markName == null || markName.isEmpty()) return;

        UUID entityId = entity.getUniqueId();
        String normalizedMark = markName.toUpperCase();

        LogHelper.debug("[MarkManager] Applying mark '%s' to %s: duration=%.1fs, behavior=%s, owner=%s",
            normalizedMark,
            entity instanceof Player p ? p.getName() : entity.getType().name(),
            durationSeconds,
            behaviorId != null ? behaviorId : "none",
            owner != null ? owner.getName() : "none");

        entityMarks.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>());

        long expiryTime;
        if (durationSeconds <= 0) {
            expiryTime = Long.MAX_VALUE; // Permanent
        } else {
            expiryTime = System.currentTimeMillis() + (long)(durationSeconds * 1000);
        }

        Map<String, MarkData> marks = entityMarks.get(entityId);
        MarkData existingData = marks.get(normalizedMark);

        if (existingData != null) {
            // Check for per-mark config, fall back to global config
            com.miracle.arcanesigils.config.MarkConfig markConfig = 
                plugin.getConfigManager().getMarkConfig(normalizedMark);
            
            boolean stackingEnabled;
            double stackIncrement;
            double maxDuration;
            
            if (markConfig != null) {
                // Use per-mark configuration
                stackingEnabled = markConfig.isStackingEnabled();
                stackIncrement = markConfig.getStackIncrement();
                maxDuration = markConfig.getMaxDuration();
            } else {
                // Fall back to global configuration
                stackingEnabled = plugin.getConfig().getBoolean("marks.enable-stacking", true);
                stackIncrement = plugin.getConfig().getDouble("marks.stack-increment", 1.0);
                maxDuration = plugin.getConfig().getDouble("marks.max-duration", 1.0);
            }
            
            if (stackingEnabled && durationSeconds > 0) {
                // STACK DURATION: Get remaining time and add stack increment
                double remainingSeconds = (existingData.expiryTime - System.currentTimeMillis()) / 1000.0;
                double newDuration = Math.min(remainingSeconds + stackIncrement, maxDuration);
                existingData.expiryTime = System.currentTimeMillis() + (long)(newDuration * 1000);
                LogHelper.debug("[MarkManager] Stacking mark '%s': remaining=%.1fs, increment=%.1fs, new=%.1fs (max=%.1fs)",
                    normalizedMark, remainingSeconds, stackIncrement, newDuration, maxDuration);
            } else {
                // Just refresh timer (old behavior for permanent marks or stacking disabled)
                existingData.expiryTime = expiryTime;
                LogHelper.debug("[MarkManager] Refreshing mark '%s': stacking=%s, new duration=%.1fs",
                    normalizedMark, stackingEnabled, durationSeconds);
            }
            
            // Update owner if provided
            if (owner != null) {
                existingData.ownerUUID = owner.getUniqueId();
            }
        } else {
            // New mark - create data and run EFFECT_STATIC if behavior exists
            MarkData newData = new MarkData(expiryTime, behaviorId, owner != null ? owner.getUniqueId() : null);
            marks.put(normalizedMark, newData);

            LogHelper.debug("[MarkManager] New mark '%s' applied to %s",
                normalizedMark,
                entity instanceof Player p ? p.getName() : entity.getType().name());

            // Run EFFECT_STATIC flows from behavior
            if (behaviorId != null && !behaviorId.isEmpty()) {
                LogHelper.debug("[MarkManager] Running EFFECT_STATIC for mark '%s' behavior '%s'",
                    normalizedMark, behaviorId);
                runBehaviorSignal(entity, newData, SignalType.EFFECT_STATIC);
                newData.staticApplied = true;
            }
        }
    }

    /**
     * Check if an entity has a specific mark.
     *
     * @param entity The entity to check
     * @param markName The mark to look for
     * @return true if the entity has the mark and it hasn't expired
     */
    public boolean hasMark(LivingEntity entity, String markName) {
        if (entity == null || markName == null) return false;

        UUID entityId = entity.getUniqueId();
        String normalizedMark = markName.toUpperCase();

        Map<String, MarkData> marks = entityMarks.get(entityId);
        if (marks == null) return false;

        MarkData data = marks.get(normalizedMark);
        if (data == null) return false;

        // Check if expired
        if (System.currentTimeMillis() > data.expiryTime) {
            expireMark(entity, normalizedMark, data);
            marks.remove(normalizedMark);
            return false;
        }

        return true;
    }

    /**
     * Remove a specific mark from an entity (runs EXPIRE behavior).
     *
     * @param entity The entity
     * @param markName The mark to remove
     */
    public void removeMark(LivingEntity entity, String markName) {
        if (entity == null || markName == null) return;

        UUID entityId = entity.getUniqueId();
        String normalizedMark = markName.toUpperCase();

        Map<String, MarkData> marks = entityMarks.get(entityId);
        if (marks != null) {
            MarkData data = marks.remove(normalizedMark);
            if (data != null) {
                expireMark(entity, normalizedMark, data);
            }
        }
    }

    /**
     * Remove all marks from an entity (runs EXPIRE behaviors).
     *
     * @param entity The entity
     */
    public void clearAllMarks(LivingEntity entity) {
        if (entity == null) return;

        UUID entityId = entity.getUniqueId();
        Map<String, MarkData> marks = entityMarks.remove(entityId);
        if (marks != null) {
            for (Map.Entry<String, MarkData> entry : marks.entrySet()) {
                expireMark(entity, entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Get all active marks on an entity.
     *
     * @param entity The entity
     * @return Set of active mark names
     */
    public Set<String> getMarks(LivingEntity entity) {
        if (entity == null) return Collections.emptySet();

        UUID entityId = entity.getUniqueId();
        Map<String, MarkData> marks = entityMarks.get(entityId);
        if (marks == null) return Collections.emptySet();

        // Filter out expired marks
        long now = System.currentTimeMillis();
        Set<String> activeMarks = new HashSet<>();
        Iterator<Map.Entry<String, MarkData>> it = marks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, MarkData> entry = it.next();
            if (now > entry.getValue().expiryTime) {
                LivingEntity ent = (LivingEntity) Bukkit.getEntity(entityId);
                if (ent != null) {
                    expireMark(ent, entry.getKey(), entry.getValue());
                }
                it.remove();
            } else {
                activeMarks.add(entry.getKey());
            }
        }

        return activeMarks;
    }

    /**
     * Get remaining duration of a mark in seconds.
     *
     * @param entity The entity
     * @param markName The mark name
     * @return Remaining seconds, or -1 if permanent, or 0 if not marked
     */
    public double getRemainingDuration(LivingEntity entity, String markName) {
        if (entity == null || markName == null) return 0;

        UUID entityId = entity.getUniqueId();
        String normalizedMark = markName.toUpperCase();

        Map<String, MarkData> marks = entityMarks.get(entityId);
        if (marks == null) return 0;

        MarkData data = marks.get(normalizedMark);
        if (data == null) return 0;

        if (data.expiryTime == Long.MAX_VALUE) return -1; // Permanent

        long remaining = data.expiryTime - System.currentTimeMillis();
        if (remaining <= 0) {
            expireMark(entity, normalizedMark, data);
            marks.remove(normalizedMark);
            return 0;
        }

        return remaining / 1000.0;
    }

    /**
     * Process tick - run TICK behaviors and expire marks.
     */
    private void processTick() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Map<String, MarkData>> entityEntry : entityMarks.entrySet()) {
            UUID entityId = entityEntry.getKey();
            LivingEntity entity = (LivingEntity) Bukkit.getEntity(entityId);

            if (entity == null || entity.isDead()) {
                // Entity gone - clean up
                entityMarks.remove(entityId);
                continue;
            }

            Map<String, MarkData> marks = entityEntry.getValue();
            Iterator<Map.Entry<String, MarkData>> it = marks.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<String, MarkData> markEntry = it.next();
                MarkData data = markEntry.getValue();

                // Check expiry
                if (now > data.expiryTime) {
                    expireMark(entity, markEntry.getKey(), data);
                    it.remove();
                    continue;
                }

                // Run TICK behavior if exists
                if (data.behaviorId != null && !data.behaviorId.isEmpty()) {
                    runBehaviorSignal(entity, data, SignalType.TICK);
                }
            }

            // Clean up empty maps
            if (marks.isEmpty()) {
                entityMarks.remove(entityId);
            }
        }
    }

    /**
     * Handle mark expiration - run EXPIRE behavior.
     */
    private void expireMark(LivingEntity entity, String markName, MarkData data) {
        if (data.behaviorId != null && !data.behaviorId.isEmpty()) {
            runBehaviorSignal(entity, data, SignalType.EXPIRE);
        }
    }

    /**
     * Run a behavior signal on the marked entity.
     */
    private void runBehaviorSignal(LivingEntity target, MarkData data, SignalType signalType) {
        if (data.behaviorId == null) return;

        Sigil behavior = plugin.getSigilManager().getBehavior(data.behaviorId);
        if (behavior == null) {
            // Try loading as regular sigil if not found as behavior
            behavior = plugin.getSigilManager().getSigil(data.behaviorId);
        }
        if (behavior == null) {
            return;
        }

        // Get owner player for context
        Player owner = data.ownerUUID != null ? Bukkit.getPlayer(data.ownerUUID) : null;

        // Build context - target is the marked entity
        EffectContext context = EffectContext.builder(
                owner != null ? owner : (target instanceof Player p ? p : null),
                signalType
            )
            .victim(target)
            .location(target.getLocation())
            .build();

        // Set metadata
        context.setMetadata("markedEntity", target);
        context.setMetadata("behaviorId", data.behaviorId);

        // Execute flows for this signal type
        FlowExecutor executor = new FlowExecutor(plugin);
        for (FlowConfig flow : behavior.getFlows()) {
            if (flow.getGraph() == null) continue;

            // Check if this flow matches the signal type
            String trigger = flow.getTrigger();
            
            if (trigger != null && trigger.equalsIgnoreCase(signalType.getConfigKey())) {
                executor.execute(flow.getGraph(), context);
            }
        }
    }

    /**
     * Clean up marks when player quits to prevent persistence across logins.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearAllMarks(event.getPlayer());
    }

    /**
     * Clean up marks for a specific entity (call on entity death/removal).
     */
    public void onEntityRemove(UUID entityId) {
        Map<String, MarkData> marks = entityMarks.remove(entityId);
        if (marks != null) {
            LivingEntity entity = (LivingEntity) Bukkit.getEntity(entityId);
            if (entity != null) {
                for (Map.Entry<String, MarkData> entry : marks.entrySet()) {
                    expireMark(entity, entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * Shutdown cleanup.
     */
    public void shutdown() {
        entityMarks.clear();
    }
}
