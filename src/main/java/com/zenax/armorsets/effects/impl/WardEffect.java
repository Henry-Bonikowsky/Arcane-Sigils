package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.Particle;
import org.bukkit.event.entity.EntityDamageEvent;

public class WardEffect extends AbstractEffect {

    public WardEffect() {
        super("WARD", "Magic damage resistance");
    }

    @Override
    public boolean execute(EffectContext context) {
        double reduction = context.getParams() != null ? context.getParams().getValue() : 15;

        if (context.getBukkitEvent() instanceof EntityDamageEvent event) {
            if (event.getCause() == EntityDamageEvent.DamageCause.MAGIC ||
                event.getCause() == EntityDamageEvent.DamageCause.WITHER ||
                event.getCause() == EntityDamageEvent.DamageCause.POISON) {

                double currentDamage = event.getDamage();
                double reducedDamage = currentDamage * (1 - reduction / 100.0);
                event.setDamage(Math.max(0, reducedDamage));

                context.getPlayer().getWorld().spawnParticle(
                        Particle.ENCHANT, context.getPlayer().getLocation().add(0, 1, 0), 20
                );

                debug("Ward reduced magic damage from " + currentDamage + " to " + reducedDamage);
                return true;
            }
        }
        return false;
    }
}
