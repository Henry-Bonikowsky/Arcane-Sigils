package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SmokebombEffect extends AbstractEffect {

    public SmokebombEffect() {
        super("SMOKEBOMB", "Creates a smoke cloud that blinds nearby enemies");
    }

    @Override
    public boolean execute(EffectContext context) {
        double radius = context.getParams() != null ? context.getParams().getValue() : 5;
        if (radius <= 0) radius = 5;

        Location loc = context.getPlayer().getLocation();

        // Spawn particles
        for (int i = 0; i < 100; i++) {
            double x = (Math.random() - 0.5) * radius * 2;
            double y = Math.random() * 2;
            double z = (Math.random() - 0.5) * radius * 2;
            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(x, y, z), 1);
        }

        // Play sound
        loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.5f);

        // Apply blindness to nearby entities
        for (LivingEntity entity : getNearbyEntities(context, radius)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
        }

        debug("Smokebomb deployed with radius " + radius);
        return true;
    }
}
