package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * Lightning effect - strikes lightning at target location.
 * Format: LIGHTNING:DAMAGE @Target
 *
 * If damage is 0, strikes visual-only lightning (no damage).
 */
public class LightningEffect extends AbstractEffect {

    public LightningEffect() {
        super("LIGHTNING", "Strike lightning at target");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // LIGHTNING:damage - supports both positional and key=value
        params.setValue(5.0);

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    if (key.equals("damage") || key.equals("value")) {
                        params.setValue(parseDouble(value, 5.0));
                    }
                }
            } else if (i == 1) {
                params.setValue(parseDouble(part, 5.0));
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        double damage = context.getParams() != null ? context.getParams().getValue() : 5;

        // Get target - uses TargetFinder if no victim (ability-style)
        LivingEntity target = getTarget(context, 20.0);
        Location targetLoc;

        if (target != null && target != context.getPlayer()) {
            targetLoc = target.getLocation();
        } else {
            // No target found, strike where player is looking
            targetLoc = context.getPlayer().getTargetBlockExact(30) != null
                ? context.getPlayer().getTargetBlockExact(30).getLocation()
                : context.getPlayer().getLocation().add(
                    context.getPlayer().getLocation().getDirection().multiply(10));
            target = null;
        }

        if (damage <= 0) {
            // Visual-only lightning (no damage)
            targetLoc.getWorld().strikeLightningEffect(targetLoc);
        } else {
            // Real lightning (causes fire and damage)
            targetLoc.getWorld().strikeLightning(targetLoc);

            // Apply additional damage if specified beyond default lightning
            if (target != null && damage > 5) {
                target.damage(damage - 5, context.getPlayer());
            }
        }

        debug("Lightning struck at " + targetLoc + " with damage " + damage);
        return true;
    }
}
