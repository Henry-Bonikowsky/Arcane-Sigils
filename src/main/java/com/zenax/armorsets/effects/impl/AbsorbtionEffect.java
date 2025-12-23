package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Grants exact absorption hearts to a player for a duration.
 * Uses setAbsorptionAmount() for precise control over heart values.
 *
 * Format: ABSORBTION:duration:hearts @Target
 * Example: ABSORBTION:5:3 (5 seconds, 3 hearts = 6 HP)
 * Example: ABSORBTION:5:1.5 (5 seconds, 1.5 hearts = 3 HP)
 *
 * Note: "ABSORBTION" spelling maintained for backwards compatibility
 */
public class AbsorbtionEffect extends AbstractEffect {

    public AbsorbtionEffect() {
        super("ABSORBTION", "Grant exact absorption hearts for a duration");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // ABSORBTION:duration:hearts - supports both positional and key=value
        params.setDuration(5);
        params.setValue(2.0);

        int positionalIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    switch (key) {
                        case "duration" -> params.setDuration((int) parseDouble(value, 5));
                        case "hearts", "value", "amount" -> params.setValue(parseDouble(value, 2.0));
                    }
                }
            } else {
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.setDuration(parseInt(part, 5));
                    case 2 -> params.setValue(parseDouble(part, 2.0));
                }
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        LivingEntity target = getTarget(context);

        if (!(target instanceof Player player)) {
            debug("ABSORBTION effect requires a player target");
            return false;
        }

        EffectParams params = context.getParams();

        // Read duration - use getDuration() which is set by parseParams
        int durationSeconds = params != null && params.getDuration() > 0 ? params.getDuration() : 5;

        // Read hearts - use getValue() which is set by parseParams, or check named param
        double hearts = 2.0; // default
        if (params != null) {
            if (params.getValue() > 0) {
                hearts = params.getValue();
            } else if (params.getDouble("value", 0) > 0) {
                // Fallback to named param if setValue wasn't called
                hearts = params.getDouble("value", 2.0);
            }
        }

        // Convert hearts to HP (1 heart = 2 HP)
        double absorptionHP = hearts * 2.0;

        // Must set MAX_ABSORPTION attribute first (defaults to 0 in 1.17+)
        org.bukkit.attribute.AttributeInstance maxAbsorption = player.getAttribute(
            org.bukkit.attribute.Attribute.GENERIC_MAX_ABSORPTION);

        double oldMax = 0;
        if (maxAbsorption != null) {
            oldMax = maxAbsorption.getBaseValue();
            // Increase max if needed
            if (oldMax < absorptionHP) {
                maxAbsorption.setBaseValue(absorptionHP);
            }
        }

        // Now set absorption amount
        player.setAbsorptionAmount((float) absorptionHP);

        // Schedule removal after duration
        ArmorSetsPlugin plugin = getPlugin();
        final double finalOldMax = oldMax;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                double current = player.getAbsorptionAmount();
                if (current > 0) {
                    player.setAbsorptionAmount((float) Math.max(0, current - absorptionHP));
                }
                // Reset max absorption to what it was
                if (maxAbsorption != null) {
                    maxAbsorption.setBaseValue(finalOldMax);
                }
            }
        }, durationSeconds * 20L);

        // Visual effects
        player.getWorld().spawnParticle(
            Particle.HEART,
            player.getLocation().add(0, 2, 0),
            (int) Math.ceil(hearts),
            0.5, 0.3, 0.5,
            0.1
        );

        // Golden particles for absorption
        player.getWorld().spawnParticle(
            Particle.DUST,
            player.getLocation().add(0, 1.5, 0),
            15,
            0.4, 0.6, 0.4,
            0.05,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 223, 0), 1.2f)
        );

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        debug("Granted " + hearts + " absorption hearts (" + absorptionHP + " HP) to " +
              player.getName() + " for " + durationSeconds + " seconds");
        return true;
    }
}
