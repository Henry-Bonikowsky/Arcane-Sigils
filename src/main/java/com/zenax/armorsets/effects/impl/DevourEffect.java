package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class DevourEffect extends AbstractEffect {

    public DevourEffect() {
        super("DEVOUR", "Grants absorption hearts (temporary extra health)");
    }

    @Override
    public boolean execute(EffectContext context) {
        double amount = context.getParams() != null ? context.getParams().getValue() : 4;

        Player player = context.getPlayer();

        // Calculate absorption level (1 level = 4 absorption hearts)
        int level = (int) Math.ceil(amount / 4.0) - 1;
        int duration = 200; // 10 seconds

        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, level, false, true));

        // Visual
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 5);

        debug("Devour granted " + amount + " absorption to " + player.getName());
        return true;
    }
}
