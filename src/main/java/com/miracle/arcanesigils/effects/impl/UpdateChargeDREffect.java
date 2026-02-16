package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

/**
 * Updates King's Brace charge-based damage reduction.
 * Gets current charge from SIGIL variables, caps at 100,
 * calculates DR, and updates named attribute modifier.
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

        // Get params
        String modifierName = context.getParams().getString("modifier_name", "kings_brace_dr");
        String sigilId = context.getParams().getString("sigilId", "kings_brace");
        String slot = context.getParams().getString("slot", "CHESTPLATE");
        double chargeDrPercent = context.getParams().getDouble("charge_dr_percent", 0.1);

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
        
        // Calculate DR: charge * tier_percent
        double drAmount = charge * chargeDrPercent;
        
        LogHelper.debug("[UpdateChargeDR] Player=%s, charge=%.0f, drPercent=%.3f, DR=%.2f",
            player.getName(), charge, chargeDrPercent, drAmount);
        
        // Update named attribute modifier
        plugin.getAttributeModifierManager().setNamedModifier(
            player,
            Attribute.ARMOR,
            modifierName,
            drAmount,
            AttributeModifier.Operation.ADD_NUMBER,
            -1 // Permanent until manually removed
        );
        
        return true;
    }
}
