package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.utils.ScreenShakeUtil;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * Explosion effect - creates an explosion at target location.
 * Format: EXPLOSION:POWER:FIRE:BREAK_BLOCKS @Target
 *
 * - power: Explosion strength (default 2.0, max 6.0)
 * - fire: Whether to set fire (true/false, default false)
 * - break_blocks: Whether to break blocks (true/false, default false)
 */
public class ExplosionEffect extends AbstractEffect {

    public ExplosionEffect() {
        super("EXPLOSION", "Create explosion at target");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // EXPLOSION:power:fire:break_blocks - supports both positional and key=value
        params.setValue(2.0);
        params.set("fire", false);
        params.set("breakBlocks", false);

        int positionalIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    switch (key) {
                        case "power", "value" -> params.setValue(parseDouble(value, 2.0));
                        case "fire" -> params.set("fire", Boolean.parseBoolean(value));
                        case "break_blocks", "breakblocks" -> params.set("breakBlocks", Boolean.parseBoolean(value));
                    }
                }
            } else {
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.setValue(parseDouble(part, 2.0));
                    case 2 -> params.set("fire", Boolean.parseBoolean(part));
                    case 3 -> params.set("breakBlocks", Boolean.parseBoolean(part));
                }
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        double power = context.getParams() != null ? context.getParams().getValue() : 2.0;
        boolean fire = context.getParams() != null && (Boolean) context.getParams().get("fire", false);
        boolean breakBlocks = context.getParams() != null && (Boolean) context.getParams().get("breakBlocks", false);

        // Cap power for safety
        power = Math.min(power, 6.0);

        // Determine target location
        Location targetLoc;
        LivingEntity target = getTarget(context);

        if (target != null && target != context.getPlayer()) {
            targetLoc = target.getLocation();
        } else if (context.getVictim() != null) {
            targetLoc = context.getVictim().getLocation();
        } else {
            targetLoc = context.getPlayer().getLocation();
        }

        // Create explosion
        targetLoc.getWorld().createExplosion(targetLoc, (float) power, fire, breakBlocks, context.getPlayer());

        // Screen shake for players near explosion
        double shakeRadius = power * 3;
        ScreenShakeUtil.shakeArea(targetLoc, shakeRadius, 0.8, (int) (power * 5));

        debug("Created explosion at " + targetLoc + " power=" + power + " fire=" + fire + " breakBlocks=" + breakBlocks);
        return true;
    }
}
