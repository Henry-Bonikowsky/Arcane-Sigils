package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.events.SignalType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Phoenix effect - prevents death and revives player with restored health.
 * Format: PHOENIX:HEALTH
 *
 * Recommended signal: DEFENSE (checks for lethal damage)
 *
 * Behavior:
 * - On DEFENSE/FALL_DAMAGE with lethal damage: Full totem revival (cancel damage, restore health, add buffs)
 * - Non-lethal damage or other signals: Grants absorption hearts and regeneration as protective buff
 */
public class PhoenixEffect extends AbstractEffect {

    public PhoenixEffect() {
        super("PHOENIX", "Revive on death with restored health");
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();
        double health = context.getParams() != null ? context.getParams().getValue() : 10;

        SignalType signal = context.getSignalType();

        // Check if player would die from current damage (DEFENSE signal with lethal damage)
        if (signal == SignalType.DEFENSE || signal == SignalType.FALL_DAMAGE) {
            double incomingDamage = context.getDamage();
            double currentHealth = player.getHealth();

            // If damage would be lethal, trigger totem revival
            if (incomingDamage >= currentHealth) {
                context.cancelEvent(); // Cancel the fatal damage
                return executeTotemRevival(player, health);
            }
        }

        // Non-lethal situation or other signals: Apply protective buffs
        return executeProtectiveBuff(player);
    }

    /**
     * Full totem revival - cancels death, restores health, applies buffs.
     */
    private boolean executeTotemRevival(Player player, double health) {
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();

        // Restore health
        player.setHealth(Math.min(health, maxHealth));

        // Apply totem buffs
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));

        // Visual/audio effects
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation(), 100);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);

        debug("Phoenix revival: " + player.getName() + " restored to " + health + " health");
        return true;
    }

    /**
     * Protective buff - grants absorption and regeneration.
     * Used when Phoenix is on a non-death signal.
     */
    private boolean executeProtectiveBuff(Player player) {
        // Apply protective buffs
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));

        // Visual effect
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 10);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        debug("Phoenix protective buff applied to " + player.getName());
        return true;
    }
}
