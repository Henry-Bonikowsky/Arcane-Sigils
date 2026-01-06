package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.events.SignalType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Dodge effect - evades incoming damage.
 * Format: DODGE:CHANCE
 *
 * Recommended signal: ON_DEFEND
 *
 * Behavior depends on signal type:
 * - ON_DEFEND: Chance to completely negate incoming damage and dash away
 * - Other signals: Performs a quick dash backward to reposition
 */
public class DodgeEffect extends AbstractEffect {

    public DodgeEffect() {
        super("DODGE", "Chance to evade incoming damage");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // DODGE:chance - supports both positional and key=value
        params.setValue(15.0);

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    if (key.equals("chance") || key.equals("value")) {
                        params.setValue(parseDouble(value, 15.0));
                    }
                }
            } else if (i == 1) {
                params.setValue(parseDouble(part, 15.0));
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();
        double chance = context.getParams() != null ? context.getParams().getValue() : 15;

        SignalType signal = context.getSignalType();

        if (signal == SignalType.DEFENSE || signal == SignalType.FALL_DAMAGE) {
            // DEFENSE signal: Chance-based damage negation
            return executeDodge(player, chance, context);
        } else {
            // Other signals: Quick dash backward
            return executeDash(player);
        }
    }

    /**
     * Dodge incoming damage - rolls chance, cancels event, and dashes away.
     */
    private boolean executeDodge(Player player, double chance, EffectContext context) {
        // Roll for dodge chance
        double roll = ThreadLocalRandom.current().nextDouble() * 100;
        if (roll > chance) {
            debug("Dodge failed: rolled " + String.format("%.1f", roll) + "% > " + chance + "%");
            return false;
        }

        // Cancel the damage
        context.cancelEvent();

        // Quick sidestep movement
        Vector direction = player.getLocation().getDirection();
        Vector sideways = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

        // Random left or right
        if (ThreadLocalRandom.current().nextBoolean()) {
            sideways.multiply(-1);
        }

        player.setVelocity(sideways.multiply(0.5).add(new Vector(0, 0.2, 0)));

        // Visual/audio effects
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 10, 0.3, 0.1, 0.3, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.5f, 1.5f);

        debug("Dodge succeeded: " + player.getName() + " evaded damage");
        return true;
    }

    /**
     * Quick backward dash - used when on non-defend signal.
     */
    private boolean executeDash(Player player) {
        // Dash backward
        Vector direction = player.getLocation().getDirection().multiply(-0.7);
        direction.setY(0.3);
        player.setVelocity(direction);

        // Visual/audio effects
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 8, 0.2, 0.1, 0.2, 0.03);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.4f, 1.3f);

        debug("Dodge dash: " + player.getName() + " repositioned");
        return true;
    }
}
