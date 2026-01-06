package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.effects.StunManager;
import com.miracle.arcanesigils.utils.ScreenShakeUtil;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * True stun effect that completely freezes a player.
 * Prevents all movement AND camera rotation for the duration.
 *
 * Format: STUN:duration @Target
 * Example: STUN:3 @Victim (stun for 3 seconds)
 */
public class StunEffect extends AbstractEffect {

    public StunEffect() {
        super("STUN", "Completely freeze a player, preventing movement and camera rotation");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = new EffectParams(id);

        // Remove target selector from string
        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();

        // Parse target selector
        if (effectString.contains("@")) {
            String[] spaceParts = effectString.split("\\s+");
            for (String part : spaceParts) {
                if (part.startsWith("@")) {
                    params.setTarget(part);
                    break;
                }
            }
        }

        // Parse STUN:duration format (supports both positional and key=value)
        String[] parts = cleanedString.split(":");
        params.setValue(2.0); // Default 2 seconds

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];

            if (part.contains("=")) {
                // Key=value format
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    if (key.equals("duration")) {
                        params.setValue(parseDouble(value, 2.0));
                    }
                }
            } else if (i == 1) {
                // Positional format - first param is duration
                try {
                    params.setValue(Double.parseDouble(part));
                } catch (NumberFormatException ignored) {}
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        LivingEntity target = getTarget(context);

        // Debug logging for target resolution
        EffectParams params = context.getParams();
        String targetSelector = params != null ? params.getTarget() : null;
        debug(String.format("STUN target selector: %s, attacker in context: %s, resolved target: %s",
            targetSelector,
            context.getAttacker() != null ? context.getAttacker().getName() : "null",
            target != null ? target.getName() : "null"));

        if (!(target instanceof Player targetPlayer)) {
            debug(String.format("STUN effect requires a player target, got: %s",
                target != null ? target.getClass().getSimpleName() : "null"));
            return false;
        }

        // Check getDuration() (from YAML "duration" key), then getValue() (from legacy string format)
        double duration = 2.0;
        if (params != null) {
            if (params.getDuration() > 0) {
                duration = params.getDuration();
            } else if (params.getValue() > 0) {
                duration = params.getValue();
            }
        }

        // Get the stun manager from the plugin
        ArmorSetsPlugin plugin = getPlugin();
        StunManager stunManager = plugin.getStunManager();

        if (stunManager == null) {
            debug("StunManager not available");
            return false;
        }

        // Apply the stun
        stunManager.stunPlayer(targetPlayer, duration);

        // Screen shake on stun impact
        ScreenShakeUtil.shake(targetPlayer, 0.4, 8);

        // Visual effects - sand particles covering player
        targetPlayer.getWorld().spawnParticle(
            Particle.FALLING_DUST,
            targetPlayer.getLocation().add(0, 1, 0),
            50,
            0.5, 1.0, 0.5,
            0.1,
            org.bukkit.Material.SAND.createBlockData()
        );

        // Golden dust particles
        targetPlayer.getWorld().spawnParticle(
            Particle.DUST,
            targetPlayer.getLocation().add(0, 1, 0),
            30,
            0.5, 1.0, 0.5,
            0.1,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 215, 0), 1.5f)
        );

        // Sound effect
        targetPlayer.playSound(targetPlayer.getLocation(), Sound.BLOCK_SAND_BREAK, 1.0f, 0.5f);
        targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_HUSK_AMBIENT, 0.8f, 0.8f);

        debug("Stunned " + targetPlayer.getName() + " for " + duration + " seconds");
        return true;
    }
}
