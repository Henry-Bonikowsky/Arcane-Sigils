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
     * Data stored for each source within a multi-source mark.
     */
    private static class SourceData {
        final String sigilId;
        final double multiplier;
        long expiryTime;
        UUID ownerUUID;

        SourceData(String sigilId, double multiplier, long expiryTime, UUID ownerUUID) {
            this.sigilId = sigilId;
            this.multiplier = multiplier;
            this.expiryTime = expiryTime;
            this.ownerUUID = ownerUUID;
        }
    }

    /**
     * Data stored for each mark on an entity.
     * Supports two modes:
     * - Single-source (legacy): One expiry time, one multiplier
     * - Multi-source (universal marks): Multiple sources with individual expiry times and multipliers
     */
    private static class MarkData {
        // Mode flag
        private final boolean isMultiSource;

        // Mark name (for aggregation mode determination)
        private final String markName;

        // Single-source mode (legacy behavior)
        long expiryTime;
        String behaviorId;      // Optional behavior sigil ID
        UUID ownerUUID;         // Who applied the mark (for context)
        boolean staticApplied;  // Whether EFFECT_STATIC has been run
        double damageMultiplier; // Damage multiplier (1.0 = no change, <1.0 = DR, >1.0 = amplification)
        String sigilId;         // Track which ability applied this mark

        // Multi-source mode (universal marks)
        private Map<String, SourceData> sources; // sigilId -> source data
        private String sharedBehaviorId; // Behavior shared by all sources

        // Single-source constructor (backward compatible)
        MarkData(long expiryTime, String behaviorId, UUID ownerUUID, double damageMultiplier, String sigilId) {
            this.isMultiSource = false;
            this.markName = null;
            this.expiryTime = expiryTime;
            this.behaviorId = behaviorId;
            this.ownerUUID = ownerUUID;
            this.staticApplied = false;
            this.damageMultiplier = damageMultiplier;
            this.sigilId = sigilId;
            this.sources = null;
            this.sharedBehaviorId = null;
        }

        // Multi-source constructor (new universal marks)
        MarkData(String markName, String behaviorId) {
            this.isMultiSource = true;
            this.markName = markName;
            this.sources = new ConcurrentHashMap<>();
            this.sharedBehaviorId = behaviorId;
            this.staticApplied = false;
            this.expiryTime = -1;
            this.damageMultiplier = 1.0;
            this.ownerUUID = null;
            this.behaviorId = null;
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
     * @param sigilId The ability/sigil ID that applied this mark (for stacking logic)
     */
    public void applyMark(LivingEntity entity, String markName, double durationSeconds,
                          String behaviorId, Player owner, String sigilId) {
        if (entity == null || markName == null || markName.isEmpty()) return;

        UUID entityId = entity.getUniqueId();
        String normalizedMark = markName.toUpperCase();

        LogHelper.info("[MarkManager] APPLY mark '%s' to %s (UUID=%s): duration=%.1fs, behavior=%s, owner=%s",
            normalizedMark,
            entity instanceof Player p ? p.getName() : entity.getType().name(),
            entityId.toString().substring(0, 8),
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
                // Check if same ability is re-applying
                boolean isSameAbility = sigilId != null && sigilId.equals(existingData.sigilId);

                if (isSameAbility) {
                    // REFRESH: Reset to max duration (same ability, different player)
                    existingData.expiryTime = System.currentTimeMillis() + (long)(Math.min(durationSeconds, maxDuration) * 1000);
                    LogHelper.debug("[MarkManager] Refreshing mark '%s' from same ability '%s': new duration=%.1fs",
                        normalizedMark, sigilId, Math.min(durationSeconds, maxDuration));
                } else {
                    // STACK: Add increment (different ability)
                    double remainingSeconds = (existingData.expiryTime - System.currentTimeMillis()) / 1000.0;
                    double newDuration = Math.min(remainingSeconds + stackIncrement, maxDuration);
                    existingData.expiryTime = System.currentTimeMillis() + (long)(newDuration * 1000);
                    LogHelper.debug("[MarkManager] Stacking mark '%s': different ability, remaining=%.1fs, increment=%.1fs, new=%.1fs (max=%.1fs)",
                        normalizedMark, remainingSeconds, stackIncrement, newDuration, maxDuration);
                }

                // Update sigilId to most recent
                if (sigilId != null) {
                    existingData.sigilId = sigilId;
                }
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
            MarkData newData = new MarkData(expiryTime, behaviorId, owner != null ? owner.getUniqueId() : null, 1.0, sigilId);
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
     * Apply a mark to an entity (backward compatibility overload).
     * Calls the main applyMark method with sigilId=null.
     *
     * @param entity The entity to mark
     * @param markName The mark identifier (e.g., "PHARAOH_MARK")
     * @param durationSeconds Duration in seconds (0 or negative for permanent until cleared)
     * @param behaviorId Optional behavior sigil ID to run while marked
     * @param owner The player who applied the mark (for context)
     */
    public void applyMark(LivingEntity entity, String markName, double durationSeconds,
                          String behaviorId, Player owner) {
        applyMark(entity, markName, durationSeconds, behaviorId, owner, null);
    }

    /**
     * Apply a mark with a damage multiplier.
     *
     * @param entity The entity to mark
     * @param markName The mark identifier (e.g., "CLEOPATRA_DEBUFF")
     * @param durationSeconds Duration in seconds (0 or negative for permanent)
     * @param behaviorId Optional behavior sigil ID to run while marked
     * @param owner The player who applied the mark (for context)
     * @param damageMultiplier Damage multiplier (1.0 = no change, <1.0 = DR, >1.0 = amplification)
     * @param sigilId The ability/sigil ID that applied this mark (for stacking logic)
     */
    public void applyMark(LivingEntity entity, String markName, double durationSeconds,
                          String behaviorId, Player owner, double damageMultiplier, String sigilId) {
        if (entity == null || markName == null || markName.isEmpty()) return;

        UUID entityId = entity.getUniqueId();
        String normalizedMark = markName.toUpperCase();

        LogHelper.debug("[MarkManager] Applying mark '%s' to %s: duration=%.1fs, behavior=%s, owner=%s, damageMultiplier=%.3f",
            normalizedMark,
            entity instanceof Player p ? p.getName() : entity.getType().name(),
            durationSeconds,
            behaviorId != null ? behaviorId : "none",
            owner != null ? owner.getName() : "none",
            damageMultiplier);

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
            // Update existing mark: refresh timer and update damage multiplier
            existingData.expiryTime = expiryTime;
            existingData.damageMultiplier = damageMultiplier;

            if (owner != null) {
                existingData.ownerUUID = owner.getUniqueId();
            }

            LogHelper.debug("[MarkManager] Updated mark '%s': multiplier=%.3f",
                normalizedMark, damageMultiplier);
        } else {
            // New mark - create data and run EFFECT_STATIC if behavior exists
            MarkData newData = new MarkData(expiryTime, behaviorId, owner != null ? owner.getUniqueId() : null, damageMultiplier, sigilId);
            marks.put(normalizedMark, newData);

            LogHelper.debug("[MarkManager] New mark '%s' applied to %s with multiplier %.3f",
                normalizedMark,
                entity instanceof Player p ? p.getName() : entity.getType().name(),
                damageMultiplier);

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
     * Apply a mark with a damage multiplier (backward compatibility overload).
     * Calls the main applyMark method with sigilId=null.
     *
     * @param entity The entity to mark
     * @param markName The mark identifier (e.g., "CLEOPATRA_DEBUFF")
     * @param durationSeconds Duration in seconds (0 or negative for permanent)
     * @param behaviorId Optional behavior sigil ID to run while marked
     * @param owner The player who applied the mark (for context)
     * @param damageMultiplier Damage multiplier (1.0 = no change, <1.0 = DR, >1.0 = amplification)
     */
    public void applyMark(LivingEntity entity, String markName, double durationSeconds,
                          String behaviorId, Player owner, double damageMultiplier) {
        applyMark(entity, markName, durationSeconds, behaviorId, owner, damageMultiplier, null);
    }

    /**
     * Apply a multi-source mark (universal damage tracking system).
     * Multiple sigils can contribute to the same mark with additive stacking within the mark.
     * Each sigil can only contribute once (re-applying refreshes duration and updates multiplier).
     *
     * @param entity The entity to mark
     * @param markName The mark identifier (e.g., "DAMAGE_AMPLIFICATION", "DAMAGE_REDUCTION")
     * @param sigilId Unique sigil identifier for per-sigil tracking
     * @param multiplier Damage multiplier contribution from this sigil
     * @param durationSeconds Duration in seconds (0 or negative for permanent)
     * @param owner The player who applied the mark (for context)
     */
    public void applyMultiSourceMark(LivingEntity entity, String markName, String sigilId,
                                     double multiplier, double durationSeconds, Player owner) {
        if (entity == null || markName == null || markName.isEmpty() || sigilId == null) return;

        UUID entityId = entity.getUniqueId();
        String normalizedMark = markName.toUpperCase();

        LogHelper.debug("[MarkManager] Applying multi-source mark '%s' to %s: sigilId=%s, multiplier=%.3f, duration=%.1fs, owner=%s",
            normalizedMark,
            entity instanceof Player p ? p.getName() : entity.getType().name(),
            sigilId,
            multiplier,
            durationSeconds,
            owner != null ? owner.getName() : "none");

        entityMarks.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>());
        Map<String, MarkData> marks = entityMarks.get(entityId);

        long expiryTime;
        if (durationSeconds <= 0) {
            expiryTime = Long.MAX_VALUE; // Permanent
        } else {
            expiryTime = System.currentTimeMillis() + (long)(durationSeconds * 1000);
        }

        MarkData existingData = marks.get(normalizedMark);

        if (existingData != null) {
            // Mark exists - verify it's multi-source mode
            if (!existingData.isMultiSource) {
                LogHelper.info("[MarkManager] WARNING: Attempting to add multi-source to single-source mark '%s'", normalizedMark);
                return;
            }

            // Add or update source
            SourceData existingSource = existingData.sources.get(sigilId);
            if (existingSource != null) {
                // Refresh existing source
                existingSource.expiryTime = expiryTime;
                existingSource.ownerUUID = owner != null ? owner.getUniqueId() : existingSource.ownerUUID;
                LogHelper.debug("[MarkManager] Refreshed source '%s' in mark '%s': multiplier=%.3f",
                    sigilId, normalizedMark, multiplier);
            } else {
                // New source
                existingData.sources.put(sigilId, new SourceData(
                    sigilId, multiplier, expiryTime, owner != null ? owner.getUniqueId() : null
                ));
                LogHelper.debug("[MarkManager] Added new source '%s' to mark '%s': multiplier=%.3f",
                    sigilId, normalizedMark, multiplier);
            }

            // Update owner to most recent
            if (owner != null) {
                existingData.ownerUUID = owner.getUniqueId();
            }

        } else {
            // New mark - create multi-source MarkData
            MarkData newData = new MarkData(normalizedMark, null);
            newData.sources.put(sigilId, new SourceData(
                sigilId, multiplier, expiryTime, owner != null ? owner.getUniqueId() : null
            ));
            if (owner != null) {
                newData.ownerUUID = owner.getUniqueId();
            }
            marks.put(normalizedMark, newData);

            LogHelper.debug("[MarkManager] Created new multi-source mark '%s' with source '%s'",
                normalizedMark, sigilId);

            // Run EFFECT_STATIC if behavior exists (once per mark, not per source)
            if (newData.sharedBehaviorId != null && !newData.sharedBehaviorId.isEmpty()) {
                LogHelper.debug("[MarkManager] Running EFFECT_STATIC for mark '%s' behavior '%s'",
                    normalizedMark, newData.sharedBehaviorId);
                runBehaviorSignal(entity, newData, SignalType.EFFECT_STATIC);
                newData.staticApplied = true;
            }
        }
    }

    /**
     * Calculate total damage multiplier from all active marks on entity.
     * Multiple marks multiply together: 0.8 * 0.9 = 0.72 (28% total DR)
     * Multi-source marks aggregate sources additively, then multiply with other marks.
     *
     * @param entity The entity to check
     * @return Combined damage multiplier (1.0 = no change, <1.0 = DR, >1.0 = amplification)
     */
    public double getDamageMultiplier(LivingEntity entity) {
        if (entity == null) return 1.0;

        Map<String, MarkData> marks = entityMarks.get(entity.getUniqueId());
        if (marks == null || marks.isEmpty()) return 1.0;

        double totalMultiplier = 1.0;
        long now = System.currentTimeMillis();

        for (MarkData data : marks.values()) {
            double markMultiplier;

            if (data.isMultiSource) {
                // Multi-source: calculate aggregate from all sources
                markMultiplier = calculateMultiSourceMultiplier(data, now);
            } else {
                // Single-source: use direct multiplier (legacy)
                if (data.expiryTime != Long.MAX_VALUE && now >= data.expiryTime) {
                    continue; // Expired
                }
                markMultiplier = data.damageMultiplier;
            }

            // Multiply all marks together (existing behavior)
            if (markMultiplier != 1.0) {
                totalMultiplier *= markMultiplier;
            }
        }

        return totalMultiplier;
    }

    /**
     * Calculate the aggregate multiplier for a multi-source mark.
     * DAMAGE_AMPLIFICATION: Additive stacking (sources add their percentages)
     * DAMAGE_REDUCTION: Additive stacking (sources add their percentages)
     *
     * @param data The multi-source mark data
     * @param now Current time for expiry checking
     * @return Aggregate multiplier for this mark
     */
    private double calculateMultiSourceMultiplier(MarkData data, long now) {
        if (data.markName == null) return 1.0;

        if (data.markName.equals("DAMAGE_AMPLIFICATION")) {
            // Additive: sum all sources, then convert to multiplier
            double totalPercent = 0.0;
            for (SourceData source : data.sources.values()) {
                if (source.expiryTime == Long.MAX_VALUE || now < source.expiryTime) {
                    // Convert multiplier to percent: 1.20 -> 20%
                    totalPercent += (source.multiplier - 1.0) * 100.0;
                }
            }
            return 1.0 + (totalPercent / 100.0); // Convert back to multiplier

        } else if (data.markName.equals("DAMAGE_REDUCTION")) {
            // Additive: sum all sources, then convert to multiplier
            double totalPercent = 0.0;
            for (SourceData source : data.sources.values()) {
                if (source.expiryTime == Long.MAX_VALUE || now < source.expiryTime) {
                    // Convert multiplier to percent: 0.80 -> 20%
                    totalPercent += (1.0 - source.multiplier) * 100.0;
                }
            }
            return 1.0 - (totalPercent / 100.0); // Convert back to multiplier
        }

        return 1.0; // Unknown mark type
    }

    /**
     * Check if an entity has a specific mark.
     * For multi-source marks, returns true if at least one source is active.
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

        // TEMP DEBUG: only log for PHARAOH_MARK
        if (normalizedMark.equals("PHARAOH_MARK")) {
            LogHelper.info("[hasMark] Checking %s (UUID=%s) for PHARAOH_MARK - marksMap=%s, allEntities=%d",
                entity.getName(), entityId.toString().substring(0, 8),
                marks != null ? marks.keySet().toString() : "NULL",
                entityMarks.size());
        }

        if (marks == null) return false;

        MarkData data = marks.get(normalizedMark);
        if (data == null) return false;

        if (data.isMultiSource) {
            // Multi-source: check if any source is active
            long now = System.currentTimeMillis();
            for (SourceData source : data.sources.values()) {
                if (source.expiryTime == Long.MAX_VALUE || now < source.expiryTime) {
                    return true; // At least one active source
                }
            }
            // All sources expired
            expireMark(entity, normalizedMark, data);
            marks.remove(normalizedMark);
            return false;
        } else {
            // Single-source: check expiry
            if (System.currentTimeMillis() > data.expiryTime) {
                expireMark(entity, normalizedMark, data);
                marks.remove(normalizedMark);
                return false;
            }
            return true;
        }
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
     * Get detailed info about all active marks on an entity.
     * Used by the public API.
     */
    public List<com.miracle.arcanesigils.api.MarkInfo> getActiveMarkInfo(LivingEntity entity) {
        if (entity == null) return Collections.emptyList();

        UUID entityId = entity.getUniqueId();
        Map<String, MarkData> marks = entityMarks.get(entityId);
        if (marks == null) return Collections.emptyList();

        long now = System.currentTimeMillis();
        List<com.miracle.arcanesigils.api.MarkInfo> result = new ArrayList<>();

        for (Map.Entry<String, MarkData> entry : marks.entrySet()) {
            MarkData data = entry.getValue();
            if (data.isMultiSource) {
                for (SourceData source : data.sources.values()) {
                    if (source.expiryTime == Long.MAX_VALUE || now < source.expiryTime) {
                        result.add(new com.miracle.arcanesigils.api.MarkInfo(
                            entry.getKey(),
                            source.multiplier,
                            source.expiryTime,
                            null
                        ));
                    }
                }
            } else {
                if (now <= data.expiryTime) {
                    result.add(new com.miracle.arcanesigils.api.MarkInfo(
                        entry.getKey(),
                        data.damageMultiplier,
                        data.expiryTime,
                        data.ownerUUID
                    ));
                }
            }
        }
        return result;
    }

    /**
     * Check if target has any mark applied by the specified attacker.
     */
    public boolean isMarkedBy(LivingEntity target, Player attacker) {
        if (target == null || attacker == null) return false;

        UUID targetId = target.getUniqueId();
        UUID attackerId = attacker.getUniqueId();
        Map<String, MarkData> marks = entityMarks.get(targetId);
        if (marks == null) return false;

        long now = System.currentTimeMillis();
        for (MarkData data : marks.values()) {
            if (data.isMultiSource) {
                continue;
            } else {
                if (attackerId.equals(data.ownerUUID) && now <= data.expiryTime) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get remaining duration of a mark in seconds.
     * For multi-source marks, returns the longest remaining duration among all sources.
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

        if (data.isMultiSource) {
            // Multi-source: find longest remaining duration
            long now = System.currentTimeMillis();
            long longestRemaining = 0;
            boolean hasPermanent = false;

            for (SourceData source : data.sources.values()) {
                if (source.expiryTime == Long.MAX_VALUE) {
                    hasPermanent = true;
                    break;
                } else {
                    long remaining = source.expiryTime - now;
                    if (remaining > longestRemaining) {
                        longestRemaining = remaining;
                    }
                }
            }

            if (hasPermanent) return -1; // At least one permanent source
            if (longestRemaining <= 0) {
                // All sources expired
                expireMark(entity, normalizedMark, data);
                marks.remove(normalizedMark);
                return 0;
            }
            return longestRemaining / 1000.0;

        } else {
            // Single-source: existing logic
            if (data.expiryTime == Long.MAX_VALUE) return -1; // Permanent

            long remaining = data.expiryTime - System.currentTimeMillis();
            if (remaining <= 0) {
                expireMark(entity, normalizedMark, data);
                marks.remove(normalizedMark);
                return 0;
            }

            return remaining / 1000.0;
        }
    }

    /**
     * Process tick - run TICK behaviors and expire marks.
     * Handles both single-source and multi-source marks.
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

                if (data.isMultiSource) {
                    // Multi-source: clean expired sources
                    Iterator<SourceData> sourceIt = data.sources.values().iterator();
                    while (sourceIt.hasNext()) {
                        SourceData source = sourceIt.next();
                        if (source.expiryTime != Long.MAX_VALUE && now > source.expiryTime) {
                            sourceIt.remove();
                            LogHelper.debug("[MarkManager] Source %s expired from mark %s",
                                source.sigilId, markEntry.getKey());
                        }
                    }

                    // If all sources expired, remove mark and run EXPIRE
                    if (data.sources.isEmpty()) {
                        expireMark(entity, markEntry.getKey(), data);
                        it.remove();
                        continue;
                    }

                    // Run TICK behavior if exists
                    if (data.sharedBehaviorId != null && !data.sharedBehaviorId.isEmpty()) {
                        runBehaviorSignal(entity, data, SignalType.TICK);
                    }

                } else {
                    // Single-source: existing logic
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

        LogHelper.debug("[MarkManager] runBehaviorSignal: signal=%s, target=%s, behaviorId=%s",
            signalType.name(), target.getName(), data.behaviorId);

        Sigil behavior = plugin.getSigilManager().getBehavior(data.behaviorId);
        if (behavior == null) {
            // Try loading as regular sigil if not found as behavior
            behavior = plugin.getSigilManager().getSigil(data.behaviorId);
        }
        if (behavior == null) {
            LogHelper.debug("[MarkManager] Behavior not found: %s", data.behaviorId);
            return;
        }

        // Get owner player for context
        Player owner = data.ownerUUID != null ? Bukkit.getPlayer(data.ownerUUID) : null;
        LogHelper.debug("[MarkManager] Owner lookup: UUID=%s, found=%s",
            data.ownerUUID, owner != null ? owner.getName() : "NULL");

        // Build context - target is the marked entity
        EffectContext context = EffectContext.builder(
                owner != null ? owner : (target instanceof Player p ? p : null),
                signalType
            )
            .victim(target)
            .location(target.getLocation())
            .build();

        LogHelper.debug("[MarkManager] EffectContext: player=%s, victim=%s",
            context.getPlayer() != null ? context.getPlayer().getName() : "NULL",
            context.getVictim() != null ? context.getVictim().getName() : "NULL");

        // Set metadata
        context.setMetadata("markedEntity", target);
        context.setMetadata("behaviorId", data.behaviorId);

        // Execute flows for this signal type
        FlowExecutor executor = new FlowExecutor(plugin);
        int flowsExecuted = 0;
        for (FlowConfig flow : behavior.getFlows()) {
            if (flow.getGraph() == null) {
                LogHelper.debug("[MarkManager] Flow has null graph, skipping");
                continue;
            }

            // Check if this flow matches the signal type
            String trigger = flow.getTrigger();
            LogHelper.debug("[MarkManager] Checking flow: trigger=%s, looking for=%s",
                trigger, signalType.getConfigKey());

            if (trigger != null && trigger.equalsIgnoreCase(signalType.getConfigKey())) {
                LogHelper.debug("[MarkManager] Executing flow for %s signal", signalType.name());
                boolean result = executor.execute(flow.getGraph(), context);
                LogHelper.debug("[MarkManager] Flow execution result: %s", result);
                flowsExecuted++;
            }
        }
        LogHelper.debug("[MarkManager] Total flows executed for %s: %d", signalType.name(), flowsExecuted);
    }

    /**
     * Clean up marks when player quits to prevent persistence across logins.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearAllMarks(event.getPlayer());
    }

    @EventHandler
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        clearAllMarks(event.getEntity());
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
