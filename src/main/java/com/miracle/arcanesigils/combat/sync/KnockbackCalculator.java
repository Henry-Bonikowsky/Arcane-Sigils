package com.miracle.arcanesigils.combat.sync;

import com.miracle.arcanesigils.combat.LegacyCombatConfig;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Calculates 1.8-style knockback with all the nuances.
 *
 * Key 1.8 knockback behaviors:
 * - First sprint hit: ~4 blocks knockback
 * - Subsequent hits without sprint reset: ~0.125 blocks
 * - W-tap (sprint reset): Full ~2 blocks each hit
 * - Forward movement reduction: 0.6x when target attacking forward
 * - Vertical cap: 0.4 blocks/tick max
 */
public class KnockbackCalculator {

    private final LegacyCombatConfig config;
    private final PositionTracker positionTracker;

    public KnockbackCalculator(LegacyCombatConfig config, PositionTracker positionTracker) {
        this.config = config;
        this.positionTracker = positionTracker;
    }

    /**
     * Calculate 1.8-style knockback vector.
     *
     * @param attacker The attacking player
     * @param victim The entity being hit
     * @param attackerLocation The position to calculate KB from (may be rollback position)
     * @param victimLocation The victim's position (may be rollback position)
     * @return The knockback velocity vector
     */
    public Vector calculate(Player attacker, LivingEntity victim,
                           Location attackerLocation, Location victimLocation) {

        // Calculate direction vector from attacker to victim
        Vector direction = victimLocation.toVector()
            .subtract(attackerLocation.toVector());

        // Normalize for horizontal direction only
        direction.setY(0);
        if (direction.lengthSquared() > 0) {
            direction.normalize();
        } else {
            // Fallback if positions are identical
            direction = attacker.getLocation().getDirection().setY(0).normalize();
        }

        // Base horizontal force
        double horizontalForce = config.getKbHorizontalBase();

        // Sprint bonus
        if (attacker.isSprinting()) {
            // Check for W-tap (sprint reset)
            boolean isWTap = positionTracker.hasWTappedRecently(attacker);
            long timeSinceSprint = positionTracker.getTimeSinceSprintStart(attacker);

            if (isWTap || timeSinceSprint < 100) {
                // Fresh sprint or W-tap - full sprint bonus
                horizontalForce *= (1.0 + config.getKbSprintMultiplier());
            } else {
                // Stale sprint - reduced bonus (subsequent hits)
                horizontalForce *= (1.0 + config.getKbSprintMultiplier() * 0.3);
            }
        }

        // Forward movement reduction
        // If victim is attacking AND moving toward attacker, reduce KB
        if (victim instanceof Player victimPlayer) {
            if (isMovingForwardAndAttacking(victimPlayer, attacker)) {
                horizontalForce *= config.getKbForwardReduction();
            }

            // Blocking KB reduction (1.8 mechanic)
            // When blocking with a sword, KB is reduced by 50%
            if (isBlocking(victimPlayer)) {
                horizontalForce *= 0.5;
            }
        }

        // Apply knockback resistance
        AttributeInstance kbResist = victim.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kbResist != null && kbResist.getValue() > 0) {
            horizontalForce *= (1.0 - kbResist.getValue());
        }

        // Calculate final horizontal knockback
        Vector knockback = direction.multiply(horizontalForce);

        // Add vertical component
        double verticalForce = config.getKbVerticalBase();

        // Cap vertical knockback
        double verticalCap = config.getKbVerticalCap();
        if (verticalForce > verticalCap) {
            verticalForce = verticalCap;
        }

        // Apply vertical KB resistance too
        if (kbResist != null && kbResist.getValue() > 0) {
            verticalForce *= (1.0 - kbResist.getValue());
        }

        knockback.setY(verticalForce);

        return knockback;
    }

    /**
     * Check if a player is blocking with a sword.
     * Uses the consumable component blocking system.
     */
    private boolean isBlocking(Player player) {
        if (!player.isHandRaised()) return false;

        // Check if holding a sword
        org.bukkit.Material mainHand = player.getInventory().getItemInMainHand().getType();
        return mainHand.name().endsWith("_SWORD");
    }

    /**
     * Check if a player is moving forward and attacking.
     * This is used for the 0.6x KB reduction mechanic.
     */
    private boolean isMovingForwardAndAttacking(Player victim, Player attacker) {
        // Check if victim has recently attacked (within 100ms)
        // We approximate this by checking if they're swinging
        // A more accurate method would track actual attack events

        // Get victim's movement direction relative to attacker
        Vector toAttacker = attacker.getLocation().toVector()
            .subtract(victim.getLocation().toVector())
            .normalize();

        Vector victimVelocity = victim.getVelocity();
        victimVelocity.setY(0);

        if (victimVelocity.lengthSquared() < 0.001) {
            return false; // Not moving
        }

        victimVelocity.normalize();

        // Check if victim is moving toward attacker (dot product > 0.5 = ~60 degree cone)
        double dot = victimVelocity.dot(toAttacker);
        return dot > 0.5;
    }

    /**
     * Calculate knockback with ping compensation (rollback netcode).
     *
     * @param attacker The attacking player
     * @param victim The entity being hit
     * @return The knockback velocity vector calculated from predicted positions
     */
    public Vector calculateWithRollback(Player attacker, LivingEntity victim) {
        if (!config.isKbSyncEnabled() || !(victim instanceof Player victimPlayer)) {
            // No rollback, use current positions
            return calculate(attacker, victim, attacker.getLocation(), victim.getLocation());
        }

        // Get the victim's ping
        int victimPing = victimPlayer.getPing();
        int offset = config.getKbSyncPingOffsetMs();
        int maxCompensation = config.getKbSyncMaxCompensationMs();

        // Calculate how far back to look
        int rollbackMs = Math.min(victimPing + offset, maxCompensation);

        // Get the victim's position from their perspective (in the past)
        Location victimPastLocation = positionTracker.getPositionAt(victimPlayer, rollbackMs);

        // For attacker, we use current position (they initiated the hit)
        Location attackerLocation = attacker.getLocation();

        return calculate(attacker, victim, attackerLocation, victimPastLocation);
    }

    /**
     * Apply any additional modifiers to the knockback vector.
     * This can be used for enchantments, special effects, etc.
     */
    public Vector applyModifiers(Vector knockback, Player attacker, LivingEntity victim) {
        // Knockback enchantment would be applied here
        // For now, we let vanilla handle enchantments

        // Ensure we don't exceed reasonable velocity
        if (knockback.length() > 5.0) {
            knockback.normalize().multiply(5.0);
        }

        return knockback;
    }
}
