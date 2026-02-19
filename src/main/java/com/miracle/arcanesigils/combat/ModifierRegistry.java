package com.miracle.arcanesigils.combat;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.config.MarkConfig;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.events.SignalType;
import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.flow.FlowExecutor;
import com.miracle.arcanesigils.utils.LogHelper;
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
 * Unified modifier and mark registry. Replaces both the old static damage maps
 * (DamageAmplificationEffect, DamageReductionBuffEffect, UpdateChargeDREffect)
 * and the old MarkManager.
 *
 * Two subsystems in one registry:
 * 1. Typed modifiers (damage amp, damage reduction, charge DR) — numeric values with aggregation
 * 2. String marks (PHARAOH_MARK, QUICKSAND_PULL, etc.) — tags with optional behavior execution
 *
 * Performance: O(1) cached reads, lazy expiry, background purge every 200 ticks (10s).
 */
public class ModifierRegistry implements Listener {

    private final ArmorSetsPlugin plugin;

    // ============================================================
    // Typed Modifiers (damage calculation)
    // ============================================================

    private final ConcurrentHashMap<UUID, EnumMap<ModifierType, SourceMap>> modifiers = new ConcurrentHashMap<>();

    // Players who have suppressed modifier/attribute notifications
    private static final Set<UUID> suppressedNotifications = ConcurrentHashMap.newKeySet();

    public static boolean isNotificationsSuppressed(UUID playerId) {
        return suppressedNotifications.contains(playerId);
    }

    public static boolean toggleNotifications(UUID playerId) {
        if (suppressedNotifications.contains(playerId)) {
            suppressedNotifications.remove(playerId);
            return true; // notifications now ON
        } else {
            suppressedNotifications.add(playerId);
            return false; // notifications now OFF
        }
    }

    /**
     * Per-type source map with cached aggregate value.
     */
    private static class SourceMap {
        final Map<String, Modifier> sources = new ConcurrentHashMap<>();
        volatile double cachedAggregate = Double.NaN;

        void invalidateCache() {
            cachedAggregate = Double.NaN;
        }
    }

    /**
     * A single modifier source entry.
     */
    record Modifier(String source, double value, long expiryTimeMs) {
        boolean isExpired(long now) {
            return expiryTimeMs != Long.MAX_VALUE && now >= expiryTimeMs;
        }
    }

    // ============================================================
    // String Marks (tags with optional behaviors)
    // ============================================================

    private final ConcurrentHashMap<UUID, Map<String, MarkEntry>> marks = new ConcurrentHashMap<>();

    // ============================================================
    // Bukkit Attribute Modifiers (replaces AttributeModifierManager)
    // ============================================================

    // Track active named attribute modifiers: EntityUUID -> Attribute -> ModifierName -> RemovalTask
    private final Map<UUID, Map<Attribute, Map<String, BukkitTask>>> attributeModifiers = new ConcurrentHashMap<>();

    /**
     * Data for a mark on an entity.
     */
    private static class MarkEntry {
        long expiryTimeMs;
        final String behaviorId;
        UUID ownerUUID;
        String sigilId;
        boolean staticApplied;

        MarkEntry(long expiryTimeMs, String behaviorId, UUID ownerUUID, String sigilId) {
            this.expiryTimeMs = expiryTimeMs;
            this.behaviorId = behaviorId;
            this.ownerUUID = ownerUUID;
            this.sigilId = sigilId;
            this.staticApplied = false;
        }

        boolean isExpired(long now) {
            return expiryTimeMs != Long.MAX_VALUE && now >= expiryTimeMs;
        }
    }

    // ============================================================
    // Constructor / Lifecycle
    // ============================================================

    public ModifierRegistry(ArmorSetsPlugin plugin) {
        this.plugin = plugin;

        // Background purge: every 200 ticks (10s) for modifier cleanup
        // Mark behavior tick: every 2 ticks (0.1s) for responsive behaviors like quicksand
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickMarks, 2L, 2L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::purgeExpiredModifiers, 200L, 200L);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        modifiers.clear();
        marks.clear();
        // Cancel all attribute modifier removal tasks
        attributeModifiers.values().forEach(entityMap ->
            entityMap.values().forEach(attrMap ->
                attrMap.values().forEach(task -> {
                    if (task != null && !task.isCancelled()) task.cancel();
                })
            )
        );
        attributeModifiers.clear();
    }

    // ============================================================
    // Modifier API
    // ============================================================

    /**
     * Apply a typed modifier to an entity.
     *
     * @param entityId   Entity UUID
     * @param type       Modifier type (determines aggregation)
     * @param source     Unique source ID (e.g., sigil name). Same source overwrites previous.
     * @param value      Percentage value (e.g., 0.10 for 10% amplification, 0.25 for 25% reduction)
     * @param durationMs Duration in milliseconds (0 or negative = permanent)
     */
    public void applyModifier(UUID entityId, ModifierType type, String source, double value, long durationMs) {
        LogHelper.debug("[ModifierRegistry] applyModifier: entity=%s, type=%s, source=%s, value=%.4f, durationMs=%d",
                entityId, type, source, value, durationMs);

        EnumMap<ModifierType, SourceMap> entityMods = modifiers.computeIfAbsent(entityId,
                k -> new EnumMap<>(ModifierType.class));
        SourceMap sourceMap = entityMods.computeIfAbsent(type, k -> new SourceMap());

        long expiry = durationMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + durationMs;

        // Check if this is a refresh of the same source with the same value (skip message)
        Modifier existing = sourceMap.sources.get(source);
        boolean isRefresh = existing != null
                && Math.abs(existing.value() - value) < 0.001
                && !existing.isExpired(System.currentTimeMillis());

        sourceMap.sources.put(source, new Modifier(source, value, expiry));
        sourceMap.invalidateCache();

        LogHelper.debug("[ModifierRegistry] Stored OK. isRefresh=%s, totalSources=%d for type %s",
                isRefresh, sourceMap.sources.size(), type);

        // Notify the target player (skip refresh spam, respect notification toggle)
        if (!isRefresh && Bukkit.getEntity(entityId) instanceof Player target
                && !suppressedNotifications.contains(entityId)) {
            String pct = String.format("%.0f", value * 100);
            String durationStr = durationMs > 0 ? String.format(", %.0fs", durationMs / 1000.0) : "";
            switch (type) {
                case DAMAGE_AMPLIFICATION ->
                    target.sendMessage("§c§lVULNERABLE! §7+" + pct + "% damage taken §8(" + source + durationStr + ")");
                case DAMAGE_REDUCTION ->
                    target.sendMessage("§9§lPROTECTED! §7-" + pct + "% damage taken §8(" + source + durationStr + ")");
                case CHARGE_DR -> {} // Too frequent, skip
            }
        }
    }

    /**
     * Get the aggregated multiplier for a modifier type on an entity.
     * Returns cached value if available, recalculates on cache miss (lazy expiry).
     *
     * @return Multiplier (1.0 = no effect)
     */
    public double getMultiplier(UUID entityId, ModifierType type) {
        EnumMap<ModifierType, SourceMap> entityMods = modifiers.get(entityId);
        if (entityMods == null) return 1.0;

        SourceMap sourceMap = entityMods.get(type);
        if (sourceMap == null || sourceMap.sources.isEmpty()) return 1.0;

        // Check cache
        double cached = sourceMap.cachedAggregate;
        if (!Double.isNaN(cached)) return cached;

        // Recalculate (lazy expiry: remove expired during aggregation)
        long now = System.currentTimeMillis();
        double result;

        switch (type) {
            case DAMAGE_AMPLIFICATION -> {
                double totalPercent = 0.0;
                var it = sourceMap.sources.values().iterator();
                while (it.hasNext()) {
                    Modifier mod = it.next();
                    if (mod.isExpired(now)) {
                        it.remove();
                        continue;
                    }
                    totalPercent += mod.value();
                }
                result = 1.0 + totalPercent;
                // Floor: amplification can't go below 1.0 (no accidental reduction)
                result = Math.max(1.0, result);
            }
            case DAMAGE_REDUCTION, CHARGE_DR -> {
                double totalPercent = 0.0;
                var it = sourceMap.sources.values().iterator();
                while (it.hasNext()) {
                    Modifier mod = it.next();
                    if (mod.isExpired(now)) {
                        it.remove();
                        continue;
                    }
                    totalPercent += mod.value();
                }
                result = 1.0 - totalPercent;
                // Floor: reduction multiplier can't go negative (would heal instead of damage)
                result = Math.max(0.0, result);
            }
            default -> result = 1.0;
        }

        sourceMap.cachedAggregate = result;
        return result;
    }

    /**
     * Check if entity has any active modifier of given type.
     */
    public boolean hasModifier(UUID entityId, ModifierType type) {
        return getMultiplier(entityId, type) != 1.0;
    }

    /**
     * Remove all modifiers from a specific source across all types.
     */
    public void removeModifierSource(UUID entityId, String source) {
        EnumMap<ModifierType, SourceMap> entityMods = modifiers.get(entityId);
        if (entityMods == null) return;

        for (SourceMap sourceMap : entityMods.values()) {
            if (sourceMap.sources.remove(source) != null) {
                sourceMap.invalidateCache();
            }
        }
    }

    /**
     * Remove a specific modifier type+source.
     */
    public void removeModifier(UUID entityId, ModifierType type, String source) {
        EnumMap<ModifierType, SourceMap> entityMods = modifiers.get(entityId);
        if (entityMods == null) return;

        SourceMap sourceMap = entityMods.get(type);
        if (sourceMap != null && sourceMap.sources.remove(source) != null) {
            sourceMap.invalidateCache();
        }
    }

    /**
     * Get all active typed modifier sources for an entity.
     * Used by the modifier viewer GUI.
     *
     * @return Map of ModifierType -> Map of source name -> {value, remaining seconds}
     */
    public Map<ModifierType, Map<String, double[]>> getActiveModifiers(UUID entityId) {
        Map<ModifierType, Map<String, double[]>> result = new LinkedHashMap<>();
        EnumMap<ModifierType, SourceMap> entityMods = modifiers.get(entityId);
        if (entityMods == null) return result;

        long now = System.currentTimeMillis();
        for (var entry : entityMods.entrySet()) {
            Map<String, double[]> sources = new LinkedHashMap<>();
            for (var src : entry.getValue().sources.entrySet()) {
                Modifier mod = src.getValue();
                if (!mod.isExpired(now)) {
                    double remaining = mod.expiryTimeMs() == Long.MAX_VALUE ? -1.0
                            : (mod.expiryTimeMs() - now) / 1000.0;
                    sources.put(src.getKey(), new double[]{mod.value(), remaining});
                }
            }
            if (!sources.isEmpty()) {
                result.put(entry.getKey(), sources);
            }
        }
        return result;
    }

    /**
     * Get all active Bukkit attribute modifier names for an entity.
     * Used by the modifier viewer GUI.
     *
     * @return Map of Attribute -> Set of modifier names
     */
    public Map<Attribute, Set<String>> getActiveAttributeModifiers(UUID entityId) {
        Map<Attribute, Set<String>> result = new LinkedHashMap<>();
        Map<Attribute, Map<String, BukkitTask>> entityAttrs = attributeModifiers.get(entityId);
        if (entityAttrs == null) return result;

        for (var entry : entityAttrs.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.put(entry.getKey(), new LinkedHashSet<>(entry.getValue().keySet()));
            }
        }
        return result;
    }

    // ============================================================
    // Mark API
    // ============================================================

    /**
     * Apply a mark (tag) to an entity. No behavior, no owner.
     */
    public void applyMark(LivingEntity entity, String markName, double durationSeconds) {
        applyMark(entity, markName, durationSeconds, null, null, null);
    }

    /**
     * Apply a mark with optional behavior and owner.
     */
    public void applyMark(LivingEntity entity, String markName, double durationSeconds,
                           String behaviorId, Player owner) {
        applyMark(entity, markName, durationSeconds, behaviorId, owner, null);
    }

    /**
     * Apply a mark with full parameters.
     *
     * @param entity          Target entity
     * @param markName        Mark ID (stored uppercase)
     * @param durationSeconds Duration (0 or negative = permanent)
     * @param behaviorId      Optional behavior sigil ID for EFFECT_STATIC/TICK/EXPIRE
     * @param owner           Player who applied the mark
     * @param sigilId         Sigil ID for stacking logic
     */
    public void applyMark(LivingEntity entity, String markName, double durationSeconds,
                           String behaviorId, Player owner, String sigilId) {
        if (entity == null || markName == null || markName.isEmpty()) return;

        UUID entityId = entity.getUniqueId();
        String normalized = markName.toUpperCase();

        long expiryTime = durationSeconds <= 0
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + (long) (durationSeconds * 1000);

        Map<String, MarkEntry> entityMarks = marks.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>());
        MarkEntry existing = entityMarks.get(normalized);

        if (existing != null) {
            // Existing mark: apply stacking/refresh logic
            MarkConfig markConfig = plugin.getConfigManager().getMarkConfig(normalized);

            boolean stackingEnabled;
            double stackIncrement;
            double maxDuration;

            if (markConfig != null) {
                stackingEnabled = markConfig.isStackingEnabled();
                stackIncrement = markConfig.getStackIncrement();
                maxDuration = markConfig.getMaxDuration();
            } else {
                stackingEnabled = plugin.getConfig().getBoolean("marks.enable-stacking", true);
                stackIncrement = plugin.getConfig().getDouble("marks.stack-increment", 1.0);
                maxDuration = plugin.getConfig().getDouble("marks.max-duration", 1.0);
            }

            if (stackingEnabled && durationSeconds > 0) {
                boolean isSameAbility = sigilId != null && sigilId.equals(existing.sigilId);
                if (isSameAbility) {
                    existing.expiryTimeMs = System.currentTimeMillis() +
                            (long) (Math.min(durationSeconds, maxDuration) * 1000);
                } else {
                    double remainingSeconds = (existing.expiryTimeMs - System.currentTimeMillis()) / 1000.0;
                    double newDuration = Math.min(remainingSeconds + stackIncrement, maxDuration);
                    existing.expiryTimeMs = System.currentTimeMillis() + (long) (newDuration * 1000);
                }
                if (sigilId != null) existing.sigilId = sigilId;
            } else {
                existing.expiryTimeMs = expiryTime;
            }

            if (owner != null) existing.ownerUUID = owner.getUniqueId();

        } else {
            // New mark
            MarkEntry entry = new MarkEntry(expiryTime, behaviorId,
                    owner != null ? owner.getUniqueId() : null, sigilId);
            entityMarks.put(normalized, entry);

            // Run EFFECT_STATIC on first application
            if (behaviorId != null && !behaviorId.isEmpty()) {
                runBehaviorSignal(entity, entry, SignalType.EFFECT_STATIC);
                entry.staticApplied = true;
            }
        }
    }

    /**
     * Check if entity has a specific mark (lazy-expires if needed).
     */
    public boolean hasMark(LivingEntity entity, String markName) {
        if (entity == null || markName == null) return false;

        UUID entityId = entity.getUniqueId();
        String normalized = markName.toUpperCase();
        Map<String, MarkEntry> entityMarks = marks.get(entityId);
        if (entityMarks == null) return false;

        MarkEntry entry = entityMarks.get(normalized);
        if (entry == null) return false;

        if (entry.isExpired(System.currentTimeMillis())) {
            expireMark(entity, normalized, entry);
            entityMarks.remove(normalized);
            return false;
        }
        return true;
    }

    /**
     * Remove a specific mark, running EXPIRE behavior.
     */
    public void removeMark(LivingEntity entity, String markName) {
        if (entity == null || markName == null) return;

        UUID entityId = entity.getUniqueId();
        String normalized = markName.toUpperCase();
        Map<String, MarkEntry> entityMarks = marks.get(entityId);
        if (entityMarks != null) {
            MarkEntry entry = entityMarks.remove(normalized);
            if (entry != null) {
                expireMark(entity, normalized, entry);
            }
        }
    }

    /**
     * Get all active mark names on an entity.
     */
    public Set<String> getMarks(LivingEntity entity) {
        if (entity == null) return Collections.emptySet();

        Map<String, MarkEntry> entityMarks = marks.get(entity.getUniqueId());
        if (entityMarks == null) return Collections.emptySet();

        long now = System.currentTimeMillis();
        Set<String> active = new HashSet<>();
        var it = entityMarks.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getValue().isExpired(now)) {
                expireMark(entity, e.getKey(), e.getValue());
                it.remove();
            } else {
                active.add(e.getKey());
            }
        }
        return active;
    }

    /**
     * Get API-facing mark info for all active marks.
     */
    public List<com.miracle.arcanesigils.api.MarkInfo> getActiveMarkInfo(LivingEntity entity) {
        if (entity == null) return Collections.emptyList();

        Map<String, MarkEntry> entityMarks = marks.get(entity.getUniqueId());
        if (entityMarks == null) return Collections.emptyList();

        long now = System.currentTimeMillis();
        List<com.miracle.arcanesigils.api.MarkInfo> result = new ArrayList<>();

        for (var e : entityMarks.entrySet()) {
            MarkEntry entry = e.getValue();
            if (!entry.isExpired(now)) {
                result.add(new com.miracle.arcanesigils.api.MarkInfo(
                        e.getKey(), 1.0, entry.expiryTimeMs, entry.ownerUUID));
            }
        }
        return result;
    }

    /**
     * Check if target has any mark applied by the specified attacker.
     */
    public boolean isMarkedBy(LivingEntity target, Player attacker) {
        if (target == null || attacker == null) return false;

        Map<String, MarkEntry> entityMarks = marks.get(target.getUniqueId());
        if (entityMarks == null) return false;

        UUID attackerId = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        for (MarkEntry entry : entityMarks.values()) {
            if (attackerId.equals(entry.ownerUUID) && !entry.isExpired(now)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get remaining duration of a mark in seconds.
     * @return Seconds remaining, -1 if permanent, 0 if not marked/expired
     */
    public double getRemainingDuration(LivingEntity entity, String markName) {
        if (entity == null || markName == null) return 0;

        Map<String, MarkEntry> entityMarks = marks.get(entity.getUniqueId());
        if (entityMarks == null) return 0;

        MarkEntry entry = entityMarks.get(markName.toUpperCase());
        if (entry == null) return 0;

        if (entry.expiryTimeMs == Long.MAX_VALUE) return -1;

        long remaining = entry.expiryTimeMs - System.currentTimeMillis();
        if (remaining <= 0) {
            expireMark(entity, markName.toUpperCase(), entry);
            entityMarks.remove(markName.toUpperCase());
            return 0;
        }
        return remaining / 1000.0;
    }

    // ============================================================
    // Bukkit Attribute Modifier API (replaces AttributeModifierManager)
    // ============================================================

    /**
     * Add or update a named Bukkit attribute modifier on an entity.
     * Same name on same entity+attribute will replace the existing modifier (prevents stacking).
     *
     * @param entity          The entity to modify
     * @param attribute       The attribute (e.g., Attribute.ARMOR)
     * @param name            Unique name for this modifier
     * @param value           The modifier value
     * @param operation       How to apply the value
     * @param durationSeconds Duration before auto-removal (0 = permanent)
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
            return false;
        }

        NamespacedKey key = new NamespacedKey(plugin, "attr_" + sanitizeAttrName(name));

        // Remove existing modifier with this key (prevents stacking)
        removeExistingAttrModifier(attrInstance, key);

        AttributeModifier modifier = new AttributeModifier(key, value, operation);

        // Check interception before applying
        com.miracle.arcanesigils.interception.InterceptionManager interceptionManager = plugin.getInterceptionManager();
        if (interceptionManager != null && entity instanceof Player player) {
            com.miracle.arcanesigils.interception.InterceptionEvent interceptionEvent =
                new com.miracle.arcanesigils.interception.InterceptionEvent(
                    com.miracle.arcanesigils.interception.InterceptionEvent.Type.ATTRIBUTE_MODIFIER,
                    player, null, attribute, operation, value, name
                );
            com.miracle.arcanesigils.interception.InterceptionEvent result =
                interceptionManager.fireIntercept(interceptionEvent);
            if (result.isCancelled()) {
                return false;
            }
        }

        attrInstance.addModifier(modifier);

        // Notify the target player of attribute changes (respect notification toggle)
        if (entity instanceof Player target && value != 0
                && !suppressedNotifications.contains(entity.getUniqueId())) {
            String attrName = formatAttributeName(attribute);
            if (value < 0) {
                String pct = String.format("%.0f", Math.abs(value) * 100);
                target.sendMessage("§c§lDEBUFF! §7-" + pct + "% " + attrName);
            } else {
                String pct = String.format("%.0f", value * 100);
                target.sendMessage("§a§lBUFF! §7+" + pct + "% " + attrName);
            }
        }

        // Cancel any existing removal task
        cancelAttrRemovalTask(entity.getUniqueId(), attribute, name);

        // Schedule auto-removal if duration > 0
        if (durationSeconds > 0) {
            BukkitTask removalTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                removeNamedModifier(entity, attribute, name);
            }, durationSeconds * 20L);
            trackAttrRemovalTask(entity.getUniqueId(), attribute, name, removalTask);
        }

        return true;
    }

    /**
     * Remove a named Bukkit attribute modifier from an entity.
     */
    public boolean removeNamedModifier(LivingEntity entity, Attribute attribute, String name) {
        if (entity == null || attribute == null || name == null) return false;

        AttributeInstance attrInstance = entity.getAttribute(attribute);
        if (attrInstance == null) return false;

        NamespacedKey key = new NamespacedKey(plugin, "attr_" + sanitizeAttrName(name));
        boolean removed = removeExistingAttrModifier(attrInstance, key);

        cancelAttrRemovalTask(entity.getUniqueId(), attribute, name);
        return removed;
    }

    /**
     * Check if an entity has a specific named attribute modifier.
     */
    public boolean hasNamedModifier(LivingEntity entity, Attribute attribute, String name) {
        if (entity == null || attribute == null || name == null) return false;

        AttributeInstance attrInstance = entity.getAttribute(attribute);
        if (attrInstance == null) return false;

        String keyString = "attr_" + sanitizeAttrName(name);
        for (AttributeModifier modifier : attrInstance.getModifiers()) {
            if (modifier.getKey().getKey().equals(keyString)) return true;
        }
        return false;
    }

    /**
     * Remove ALL arcane_sigils attribute modifiers from an entity's attributes.
     * Safety net for death/quit cleanup.
     */
    public void scrubAllPluginModifiers(LivingEntity entity) {
        if (entity == null) return;

        for (Attribute attribute : new Attribute[] {
                Attribute.MOVEMENT_SPEED, Attribute.MAX_HEALTH,
                Attribute.ATTACK_DAMAGE, Attribute.ATTACK_SPEED,
                Attribute.ARMOR, Attribute.ARMOR_TOUGHNESS,
                Attribute.KNOCKBACK_RESISTANCE
        }) {
            AttributeInstance attrInstance = entity.getAttribute(attribute);
            if (attrInstance == null) continue;

            List<AttributeModifier> toRemove = new ArrayList<>();
            for (AttributeModifier modifier : attrInstance.getModifiers()) {
                String keyStr = modifier.getKey().getKey();
                if (keyStr.startsWith("arcane_sigils_attr") || keyStr.startsWith("arcane_sigils_persist")) {
                    toRemove.add(modifier);
                }
            }
            for (AttributeModifier modifier : toRemove) {
                attrInstance.removeModifier(modifier);
            }
        }
    }

    /**
     * Remove all tracked attribute modifier removal tasks for an entity.
     */
    private void removeAllAttributeModifiers(UUID entityId) {
        Map<Attribute, Map<String, BukkitTask>> entityMods = attributeModifiers.remove(entityId);
        if (entityMods != null) {
            entityMods.values().forEach(attrMap ->
                attrMap.values().forEach(task -> {
                    if (task != null && !task.isCancelled()) task.cancel();
                })
            );
        }
    }

    private static String formatAttributeName(Attribute attribute) {
        String name = attribute.name();
        if (name.contains("MOVEMENT_SPEED")) return "Movement Speed";
        if (name.contains("MAX_HEALTH")) return "Max Health";
        if (name.contains("ATTACK_DAMAGE")) return "Attack Damage";
        if (name.contains("ATTACK_SPEED")) return "Attack Speed";
        if (name.contains("ARMOR_TOUGHNESS")) return "Armor Toughness";
        if (name.contains("ARMOR")) return "Armor";
        if (name.contains("KNOCKBACK")) return "Knockback Resistance";
        return name.replace("_", " ").toLowerCase();
    }

    private String sanitizeAttrName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    private boolean removeExistingAttrModifier(AttributeInstance attrInstance, NamespacedKey key) {
        String keyString = key.getKey();
        AttributeModifier toRemove = null;
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

    private void trackAttrRemovalTask(UUID entityId, Attribute attribute, String name, BukkitTask task) {
        attributeModifiers
            .computeIfAbsent(entityId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(attribute, k -> new ConcurrentHashMap<>())
            .put(name, task);
    }

    private void cancelAttrRemovalTask(UUID entityId, Attribute attribute, String name) {
        Map<Attribute, Map<String, BukkitTask>> entityMods = attributeModifiers.get(entityId);
        if (entityMods != null) {
            Map<String, BukkitTask> attrMap = entityMods.get(attribute);
            if (attrMap != null) {
                BukkitTask task = attrMap.remove(name);
                if (task != null && !task.isCancelled()) task.cancel();
                if (attrMap.isEmpty()) entityMods.remove(attribute);
            }
            if (entityMods.isEmpty()) attributeModifiers.remove(entityId);
        }
    }

    // ============================================================
    // Combined cleanup
    // ============================================================

    /**
     * Remove ALL data (modifiers + marks) for an entity.
     */
    public void removeEntity(UUID entityId) {
        modifiers.remove(entityId);
        removeEntityMarks(entityId);
        removeAllAttributeModifiers(entityId);
    }

    private void removeEntityMarks(UUID entityId) {
        Map<String, MarkEntry> entityMarks = marks.remove(entityId);
        if (entityMarks != null) {
            LivingEntity entity = (LivingEntity) Bukkit.getEntity(entityId);
            if (entity != null) {
                for (var e : entityMarks.entrySet()) {
                    expireMark(entity, e.getKey(), e.getValue());
                }
            }
        }
    }

    /**
     * Clear all marks on an entity, running EXPIRE behaviors.
     */
    public void clearAllMarks(LivingEntity entity) {
        if (entity == null) return;
        removeEntityMarks(entity.getUniqueId());
    }

    // ============================================================
    // Background tasks
    // ============================================================

    /**
     * Tick marks: run TICK behaviors, clean up expired marks and dead entities.
     * Runs every 2 game ticks (0.1s) for responsive behaviors.
     */
    private void tickMarks() {
        long now = System.currentTimeMillis();

        for (var entityEntry : marks.entrySet()) {
            UUID entityId = entityEntry.getKey();
            LivingEntity entity = (LivingEntity) Bukkit.getEntity(entityId);

            if (entity == null || entity.isDead()) {
                marks.remove(entityId);
                continue;
            }

            Map<String, MarkEntry> entityMarks = entityEntry.getValue();
            var it = entityMarks.entrySet().iterator();

            while (it.hasNext()) {
                var markEntry = it.next();
                MarkEntry entry = markEntry.getValue();

                if (entry.isExpired(now)) {
                    expireMark(entity, markEntry.getKey(), entry);
                    it.remove();
                    continue;
                }

                // Run TICK behavior
                if (entry.behaviorId != null && !entry.behaviorId.isEmpty()) {
                    runBehaviorSignal(entity, entry, SignalType.TICK);
                }
            }

            if (entityMarks.isEmpty()) {
                marks.remove(entityId);
            }
        }
    }

    /**
     * Purge expired modifiers from all entities.
     * Runs every 200 ticks (10s).
     */
    private void purgeExpiredModifiers() {
        long now = System.currentTimeMillis();

        var entityIt = modifiers.entrySet().iterator();
        while (entityIt.hasNext()) {
            var entityEntry = entityIt.next();
            EnumMap<ModifierType, SourceMap> entityMods = entityEntry.getValue();

            for (SourceMap sourceMap : entityMods.values()) {
                boolean removed = false;
                var sourceIt = sourceMap.sources.values().iterator();
                while (sourceIt.hasNext()) {
                    if (sourceIt.next().isExpired(now)) {
                        sourceIt.remove();
                        removed = true;
                    }
                }
                if (removed) sourceMap.invalidateCache();
            }

            // Clean up empty entity entries
            entityMods.values().removeIf(sm -> sm.sources.isEmpty());
            if (entityMods.isEmpty()) entityIt.remove();
        }

        // Also purge attribute modifier tracking for dead/removed entities
        attributeModifiers.entrySet().removeIf(entry -> {
            UUID entityId = entry.getKey();
            boolean exists = Bukkit.getEntity(entityId) != null;
            if (!exists) {
                entry.getValue().values().forEach(attrMap ->
                    attrMap.values().forEach(task -> {
                        if (task != null && !task.isCancelled()) task.cancel();
                    })
                );
                return true;
            }
            return false;
        });
    }

    // ============================================================
    // Behavior execution (replaces MarkManager's runBehaviorSignal)
    // ============================================================

    private void expireMark(LivingEntity entity, String markName, MarkEntry entry) {
        if (entry.behaviorId != null && !entry.behaviorId.isEmpty()) {
            runBehaviorSignal(entity, entry, SignalType.EXPIRE);
        }
    }

    private void runBehaviorSignal(LivingEntity target, MarkEntry entry, SignalType signalType) {
        if (entry.behaviorId == null) return;

        Sigil behavior = plugin.getSigilManager().getBehavior(entry.behaviorId);
        if (behavior == null) {
            behavior = plugin.getSigilManager().getSigil(entry.behaviorId);
        }
        if (behavior == null) return;

        Player owner = entry.ownerUUID != null ? Bukkit.getPlayer(entry.ownerUUID) : null;

        EffectContext context = EffectContext.builder(
                        owner != null ? owner : (target instanceof Player p ? p : null),
                        signalType
                )
                .victim(target)
                .location(target.getLocation())
                .build();

        context.setMetadata("markedEntity", target);
        context.setMetadata("behaviorId", entry.behaviorId);

        FlowExecutor executor = new FlowExecutor(plugin);
        for (FlowConfig flow : behavior.getFlows()) {
            if (flow.getGraph() == null) continue;
            String trigger = flow.getTrigger();
            if (trigger != null && trigger.equalsIgnoreCase(signalType.getConfigKey())) {
                executor.execute(flow.getGraph(), context);
            }
        }
    }

    // ============================================================
    // Event listeners
    // ============================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        removeEntity(player.getUniqueId());
        scrubAllPluginModifiers(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        removeEntity(player.getUniqueId());
        scrubAllPluginModifiers(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        removeEntity(entity.getUniqueId());
        scrubAllPluginModifiers(entity);
    }
}
