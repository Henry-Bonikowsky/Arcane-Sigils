package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Pulls an entity toward the mark owner (player who applied the mark).
 * Used for continuous pull effects like quicksand.
 *
 * Format: PULL_TO_OWNER:strength @Target
 * Example: PULL_TO_OWNER:0.05 @Victim
 *
 * Parameters:
 * - strength: Pull velocity per tick (default 0.05)
 */
public class PullToOwnerEffect extends AbstractEffect {

    public PullToOwnerEffect() {
        super("PULL_TO_OWNER", "Pulls entity toward mark owner");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        // Parse PULL_TO_OWNER:STRENGTH format
        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // Default strength
        params.setValue(0.05);

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];

            if (part.contains("=")) {
                // Key=value format
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    if (key.equals("strength")) {
                        params.setValue(parseDouble(value, 0.05));
                    }
                }
            } else if (i == 1) {
                // Positional format - first param is strength
                try {
                    params.setValue(parseDouble(part, 0.05));
                } catch (NumberFormatException ignored) {}
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        // For mark behaviors, the victim is the marked entity (stored in context.victim)
        // NOT the target from @Target parameter (which defaults to @Self = owner)
        LivingEntity victim = context.getVictim();
        
        if (victim == null) {
            debug("PULL_TO_OWNER requires a victim (marked entity)");
            return false;
        }

        // Get mark owner (player who applied the mark)
        Player owner = context.getPlayer();
        
        if (owner == null || !owner.isOnline()) {
            debug("PULL_TO_OWNER requires mark owner to be online");
            return false;
        }

        // Don't pull self
        if (victim.equals(owner)) {
            return false;
        }

        // Get pull strength from params
        EffectParams params = context.getParams();
        double strength = params != null ? params.getValue() : 0.05;
        if (strength == 0 && params != null) {
            strength = params.getDouble("strength", 0.05);
        }

        // Calculate direction vector from victim to owner
        Location ownerLoc = owner.getLocation();
        Location victimLoc = victim.getLocation();
        Vector direction = ownerLoc.toVector().subtract(victimLoc.toVector());
        double distance = direction.length();

        // Don't pull if already very close (within 1 block)
        if (distance < 1.0) {
            return false;
        }

        // Cap velocity to distance - never overshoot
        // If distance is 0.5 blocks, max velocity is 0.5
        // This ensures victim ends up inside owner hitbox, not flying past
        double cappedStrength = Math.min(strength, distance * 0.8);

        if (distance > 0.01) { // Avoid zero division
            direction.normalize().multiply(cappedStrength);
            victim.setVelocity(direction);
            return true;
        }

        return false;
    }
}
