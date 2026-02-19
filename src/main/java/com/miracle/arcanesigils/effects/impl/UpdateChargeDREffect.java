package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.combat.ModifierType;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.entity.Player;

/**
 * Updates King's Brace charge-based damage reduction.
 * Gets current charge from SIGIL variables, caps at 100,
 * calculates DR percentage, and stores in ModifierRegistry.
 */
public class UpdateChargeDREffect extends AbstractEffect {

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

        String sigilId = context.getParams().getString("sigilId", "kings_brace");
        String slot = context.getParams().getString("slot", "CHESTPLATE");
        double chargeDrPercent = context.getParams().getDouble("charge_dr_percent", 0.001);

        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();

        Object chargeObj = plugin.getSigilVariableManager()
                .getSigilVariable(player, sigilId, slot, "charge");

        double charge = 0;
        if (chargeObj instanceof Number num) {
            charge = num.doubleValue();
        }

        if (charge > 100) {
            charge = 100;
            plugin.getSigilVariableManager()
                    .setSigilVariable(player, sigilId, slot, "charge", (int) charge, -1);
        }

        // Calculate DR: charge * tier_percent -> fraction
        // Example: 100 charges * 0.002 (T5) = 0.2 = 20% DR -> 0.20 fraction
        double drFraction = charge * chargeDrPercent;

        LogHelper.debug("[UpdateChargeDR] Player=%s, charge=%.0f, drPercent=%.3f, DR=%.2f%%",
                player.getName(), charge, chargeDrPercent, drFraction * 100);

        if (drFraction > 0) {
            // Permanent modifier (no expiry) — recalculated each PASSIVE tick
            plugin.getModifierRegistry().applyModifier(
                    player.getUniqueId(),
                    ModifierType.CHARGE_DR,
                    "charge_dr_" + sigilId,
                    drFraction,
                    0  // permanent, overwritten on next PASSIVE tick
            );
        } else {
            plugin.getModifierRegistry().removeModifier(
                    player.getUniqueId(),
                    ModifierType.CHARGE_DR,
                    "charge_dr_" + sigilId
            );
        }

        return true;
    }
}
