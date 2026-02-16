package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies a temporary flat damage reduction buff to the player.
 * This is NOT the same as Resistance potion - it's a direct percentage reduction
 * applied before armor calculations.
 *
 * Format: DAMAGE_REDUCTION_BUFF:duration:percent @Target
 * Example: DAMAGE_REDUCTION_BUFF:5:25 (5 seconds, 25% damage reduction)
 *
 * The buff stacks with other damage reduction effects additively (capped at 80%).
 */
public class DamageReductionBuffEffect extends AbstractEffect {

    // Track active damage reduction buffs per player
    // UUID -> DamageReductionBuff (expiry time and reduction percent)
    private static final Map<UUID, DamageReductionBuff> activeBuffs = new ConcurrentHashMap<>();

    public DamageReductionBuffEffect() {
        super("DAMAGE_REDUCTION_BUFF", "Apply temporary flat damage reduction");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // DAMAGE_REDUCTION_BUFF:duration:percent - supports both positional and key=value
        int positionalIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    switch (key) {
                        case "duration" -> params.setDuration((int) parseDouble(value, 5));
                        case "percent", "value", "reduction" -> params.setValue(parseDouble(value, 25.0));
                    }
                }
            } else {
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.setDuration((int) parseDouble(part, 5));
                    case 2 -> params.setValue(parseDouble(part, 25.0));
                }
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        EffectParams params = context.getParams();

        // Read duration - use getDuration() which is set by parseParams
        int durationSeconds = params != null && params.getDuration() > 0 ? params.getDuration() : 5;

        // Read percent - use getValue() which is set by parseParams
        double percent = params != null && params.getValue() > 0 ? params.getValue() : 25.0;

        // Cap at 80% max reduction
        percent = Math.min(percent, 80.0);

        // Apply the buff
        long expiryTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        activeBuffs.put(player.getUniqueId(), new DamageReductionBuff(expiryTime, percent));

        debug("Applied " + percent + "% damage reduction to " + player.getName() + " for " + durationSeconds + "s");
        return true;
    }

    /**
     * Get the damage reduction percentage for a player (0 if no active buff).
     * Called by SignalHandler when processing DEFENSE signals.
     *
     * @param playerUUID The player's UUID
     * @return The damage reduction percentage (0-80)
     */
    public static double getDamageReduction(UUID playerUUID) {
        DamageReductionBuff buff = activeBuffs.get(playerUUID);
        if (buff == null) return 0;

        // Check if expired
        if (System.currentTimeMillis() >= buff.expiryTime) {
            activeBuffs.remove(playerUUID);
            return 0;
        }

        return buff.reductionPercent;
    }

    /**
     * Check if a player has an active damage reduction buff.
     */
    public static boolean hasBuff(UUID playerUUID) {
        return getDamageReduction(playerUUID) > 0;
    }

    /**
     * Remove a player's damage reduction buff.
     */
    public static void removeBuff(UUID playerUUID) {
        activeBuffs.remove(playerUUID);
    }

    /**
     * Clear all buffs (used on plugin disable).
     */
    public static void clearAllBuffs() {
        activeBuffs.clear();
    }

    /**
     * Data class for tracking a damage reduction buff.
     */
    private static class DamageReductionBuff {
        final long expiryTime;
        final double reductionPercent;

        DamageReductionBuff(long expiryTime, double reductionPercent) {
            this.expiryTime = expiryTime;
            this.reductionPercent = reductionPercent;
        }
    }
}
