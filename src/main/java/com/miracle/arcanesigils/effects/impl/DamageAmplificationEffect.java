package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Causes target to take amplified damage for a duration.
 * Uses a static map approach (like DamageReductionBuffEffect) to track
 * targets who should take increased damage.
 *
 * Format: DAMAGE_AMPLIFICATION @Target
 *
 * Params (YAML):
 *   amplification_percent: 10.0  # Percentage increase (2.5-100%)
 *   duration: 5                   # Duration in seconds
 *   target: @Victim              # Who to affect
 *
 * Example:
 *   DAMAGE_AMPLIFICATION @Victim with amplification_percent=15 means target takes 15% more damage
 *
 * Implementation:
 *   Tracks affected targets in a static map. SignalHandler checks this map
 *   when processing DEFENSE signals and increases damage accordingly.
 */
public class DamageAmplificationEffect extends AbstractEffect {

    // Track active damage amplification debuffs per entity
    // UUID -> DamageAmplificationDebuff (expiry time and amplification percent)
    private static final Map<UUID, DamageAmplificationDebuff> activeDebuffs = new ConcurrentHashMap<>();

    public DamageAmplificationEffect() {
        super("DAMAGE_AMPLIFICATION", "Target takes increased damage for a duration");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        // Defaults
        params.set("amplification_percent", "10.0"); // 10% damage increase
        params.setDuration(5); // 5 seconds

        // Default to targeting victim
        if (params.getTarget() == null) {
            params.setTarget("@Victim");
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) {
            debug("DAMAGE_AMPLIFICATION effect requires params");
            return false;
        }

        LivingEntity target = getTarget(context);
        if (target == null) {
            debug("DAMAGE_AMPLIFICATION requires a target");
            return false;
        }

        // Get amplification percentage (2.5-100%)
        double amplificationPercent = params.getDouble("amplification_percent", 10.0);
        amplificationPercent = Math.max(2.5, Math.min(100.0, amplificationPercent));

        // Get duration
        int durationSeconds = params.getDuration() > 0 ? params.getDuration() : 5;

        // Apply the debuff via static map (same pattern as DamageReductionBuffEffect)
        long expiryTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        activeDebuffs.put(target.getUniqueId(), new DamageAmplificationDebuff(expiryTime, amplificationPercent));

        // Visual feedback - red skull particles
        target.getWorld().spawnParticle(
            Particle.DUST,
            target.getLocation().add(0, 1, 0),
            35,
            0.5, 0.8, 0.5,
            0.1,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 1.5f)
        );

        // Small red hearts falling
        target.getWorld().spawnParticle(
            Particle.HEART,
            target.getLocation().add(0, 1.5, 0),
            8,
            0.3, 0.3, 0.3,
            0.1
        );

        // Sound effect - ominous debuff
        target.getWorld().playSound(
            target.getLocation(),
            Sound.ENTITY_WITHER_HURT,
            0.6f,
            1.2f
        );

        // Notification for player targets
        if (target instanceof Player player) {
            player.sendMessage("§c§lVULNERABLE! §7+" + String.format("%.1f", amplificationPercent) + "% damage taken");
        }

        debug(String.format("Applied %.1f%% damage amplification to %s for %d seconds",
            amplificationPercent, target.getName(), durationSeconds));

        return true;
    }

    /**
     * Get the damage amplification percentage for an entity (0 if no active debuff).
     * Called by SignalHandler when processing DEFENSE signals.
     *
     * @param entityUUID The entity's UUID
     * @return The damage amplification percentage (0-100)
     */
    public static double getDamageAmplification(UUID entityUUID) {
        DamageAmplificationDebuff debuff = activeDebuffs.get(entityUUID);
        if (debuff == null) return 0;

        // Check if expired
        if (System.currentTimeMillis() >= debuff.expiryTime) {
            activeDebuffs.remove(entityUUID);
            return 0;
        }

        return debuff.amplificationPercent;
    }

    /**
     * Check if an entity has an active damage amplification debuff.
     */
    public static boolean hasDebuff(UUID entityUUID) {
        return getDamageAmplification(entityUUID) > 0;
    }

    /**
     * Remove an entity's damage amplification debuff.
     */
    public static void removeDebuff(UUID entityUUID) {
        activeDebuffs.remove(entityUUID);
    }

    /**
     * Clear all debuffs (used on plugin disable).
     */
    public static void clearAllDebuffs() {
        activeDebuffs.clear();
    }

    /**
     * Data class for tracking a damage amplification debuff.
     */
    private static class DamageAmplificationDebuff {
        final long expiryTime;
        final double amplificationPercent;

        DamageAmplificationDebuff(long expiryTime, double amplificationPercent) {
            this.expiryTime = expiryTime;
            this.amplificationPercent = amplificationPercent;
        }
    }
}
