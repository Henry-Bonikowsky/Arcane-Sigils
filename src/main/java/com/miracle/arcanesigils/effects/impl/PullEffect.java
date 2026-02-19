package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Pull effect - pulls target toward player.
 * Format: PULL:FORCE @Target
 */
public class PullEffect extends AbstractEffect {

    public PullEffect() {
        super("PULL", "Pull target toward you");
    }

    @Override
    public boolean execute(EffectContext context) {
        double force = context.getParams() != null ? context.getParams().getValue() : 1.5;
        force = Math.min(force, 5.0); // Cap force

        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 20.0);
        if (target == null || target == context.getPlayer()) {
            debug("Pull failed - no valid target found");
            return false;
        }

        // Calculate pull direction (toward player)
        Vector direction = context.getPlayer().getLocation().toVector()
            .subtract(target.getLocation().toVector())
            .normalize();

        // Add slight upward component
        direction.setY(0.2);
        direction.multiply(force);

        target.setVelocity(direction);

        // Apply cooldown if specified (for Maelstrom crossbow)
        if (context.getParams() != null) {
            Object cooldownObj = context.getParams().get("cooldown");
            if (cooldownObj != null) {
                double cooldown = cooldownObj instanceof Number
                    ? ((Number) cooldownObj).doubleValue()
                    : 0.0;

                if (cooldown > 0 && context.getPlayer() != null) {
                    String flowId = context.getMetadata("flowId", "pull_effect");
                    getPlugin().getCooldownManager().setCooldown(
                        context.getPlayer(),
                        flowId,
                        "Maelstrom",
                        cooldown
                    );
                }
            }
        }

        debug("Pulled " + target.getName() + " with force " + force);
        return true;
    }
}
