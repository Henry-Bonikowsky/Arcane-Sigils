package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * REPAIR_ITEM - Prevents item from breaking and optionally restores durability.
 *
 * Parameters:
 * - percent: How much durability to restore (0-100). Default 10.
 *            Use 0 to just prevent the break without repairing.
 *
 * Usage with ITEM_BREAK signal:
 * - Signal fires when item is ABOUT to break
 * - This effect cancels the damage and restores durability
 * - Item survives!
 */
public class RepairItemEffect extends AbstractEffect {

    public RepairItemEffect() {
        super("REPAIR_ITEM", "Prevents item breaking and restores durability");
    }

    @Override
    public boolean execute(EffectContext context) {
        // Get the item that's about to break
        ItemStack item = context.getMetadata("breakingItem", null);
        if (item == null) {
            debug("No breaking item in context - REPAIR_ITEM only works with ITEM_BREAK signal");
            return false;
        }

        // Get parameters
        EffectParams params = context.getParams();
        double percent = params != null ?
            ((Number) params.get("percent", 10.0)).doubleValue() : 10.0;

        // Prevent the break by cancelling the event
        context.cancelEvent();
        debug("Prevented item break for " + context.getPlayer().getName());

        // Restore durability
        if (percent > 0 && item.getItemMeta() instanceof Damageable damageable) {
            int maxDurability = item.getType().getMaxDurability();
            int currentDamage = damageable.getDamage();

            // Calculate repair amount (percent of max durability)
            int repairAmount = (int) Math.ceil(maxDurability * (percent / 100.0));
            int newDamage = Math.max(0, currentDamage - repairAmount);

            damageable.setDamage(newDamage);
            item.setItemMeta(damageable);

            debug("Repaired item by " + percent + "% (" + repairAmount + " durability) for " + context.getPlayer().getName());
        }

        return true;
    }
}
