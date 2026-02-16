package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Updates King's Brace charge-based damage reduction.
 * Gets current charge from SIGIL variables, caps at 100,
 * calculates DR percentage, and stores in static map for SignalHandler to apply.
 *
 * Uses static map pattern (like DamageReductionBuffEffect) instead of attribute
 * modifiers, since attribute modifiers add flat armor which doesn't equate to
 * percentage damage reduction.
 */
public class UpdateChargeDREffect extends AbstractEffect {

    // Track active charge-based DR per player
    // UUID -> DR percentage (calculated from charge * charge_dr_percent)
    private static final Map<UUID, Double> activeChargeDR = new ConcurrentHashMap<>();

    public UpdateChargeDREffect() {
        super("UPDATE_CHARGE_DR", "Updates King's Brace charge-based DR");
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            LogHelper.debug("[UpdateChargeDR] No player in context");
            return false;
        }

        // Get params
        String sigilId = context.getParams().getString("sigilId", "kings_brace");
        String slot = context.getParams().getString("slot", "CHESTPLATE");
        double chargeDrPercent = context.getParams().getDouble("charge_dr_percent", 0.001);

        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();

        // Get current charge
        Object chargeObj = plugin.getSigilVariableManager()
            .getSigilVariable(player, sigilId, slot, "charge");

        double charge = 0;
        if (chargeObj instanceof Number num) {
            charge = num.doubleValue();
        }

        // Cap at 100
        if (charge > 100) {
            charge = 100;
            plugin.getSigilVariableManager()
                .setSigilVariable(player, sigilId, slot, "charge", (int)charge, -1);
        }

        // Calculate DR percentage: charge * tier_percent
        // Example: 100 charges * 0.002 (T5) = 0.2 = 20% DR
        double drPercent = charge * chargeDrPercent * 100; // Convert to percentage

        LogHelper.debug("[UpdateChargeDR] Player=%s, charge=%.0f, drPercent=%.3f, DR=%.2f%%",
            player.getName(), charge, chargeDrPercent, drPercent);

        // Store in static map for SignalHandler to apply
        if (drPercent > 0) {
            activeChargeDR.put(player.getUniqueId(), drPercent);
        } else {
            activeChargeDR.remove(player.getUniqueId());
        }

        return true;
    }

    /**
     * Get the charge-based damage reduction percentage for a player.
     * Called by SignalHandler when processing DEFENSE signals.
     *
     * @param playerUUID The player's UUID
     * @return The damage reduction percentage (0-20 typically)
     */
    public static double getChargeDR(UUID playerUUID) {
        return activeChargeDR.getOrDefault(playerUUID, 0.0);
    }

    /**
     * Check if a player has charge-based DR.
     */
    public static boolean hasChargeDR(UUID playerUUID) {
        return activeChargeDR.containsKey(playerUUID) && activeChargeDR.get(playerUUID) > 0;
    }

    /**
     * Remove a player's charge-based DR (e.g., when unequipping King's Brace).
     */
    public static void removeChargeDR(UUID playerUUID) {
        activeChargeDR.remove(playerUUID);
    }

    /**
     * Clear all charge DR (used on plugin disable).
     */
    public static void clearAllChargeDR() {
        activeChargeDR.clear();
    }
}
