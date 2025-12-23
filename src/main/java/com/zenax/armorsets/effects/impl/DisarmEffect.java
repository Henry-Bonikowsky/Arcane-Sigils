package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Disarm effect - forces target to drop their held item.
 * Format: DISARM @Target
 */
public class DisarmEffect extends AbstractEffect {

    public DisarmEffect() {
        super("DISARM", "Force target to drop held item");
    }

    @Override
    public boolean execute(EffectContext context) {
        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 10.0);
        if (target == null || target == context.getPlayer()) {
            debug("Disarm failed - no valid target found");
            return false;
        }

        if (target instanceof Player targetPlayer) {
            ItemStack heldItem = targetPlayer.getInventory().getItemInMainHand();

            if (heldItem.getType().isAir()) {
                debug("Disarm failed - " + target.getName() + " not holding anything");
                return false;
            }

            // Drop the item
            Item droppedItem = targetPlayer.getWorld().dropItemNaturally(
                targetPlayer.getLocation().add(0, 1, 0), heldItem.clone());

            // Clear the main hand
            targetPlayer.getInventory().setItemInMainHand(null);

            // Give the dropped item some velocity away from the player
            droppedItem.setVelocity(targetPlayer.getLocation().getDirection().multiply(-0.3).setY(0.3));

            // Prevent immediate pickup
            droppedItem.setPickupDelay(40); // 2 seconds

            // Effects
            targetPlayer.getWorld().spawnParticle(Particle.CRIT,
                targetPlayer.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
            targetPlayer.getWorld().playSound(targetPlayer.getLocation(),
                Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

            debug("Disarmed " + target.getName());
            return true;
        }

        debug("Disarm failed - target is not a player");
        return false;
    }
}
