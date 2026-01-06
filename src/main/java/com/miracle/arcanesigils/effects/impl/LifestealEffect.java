package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class LifestealEffect extends AbstractEffect {

    public LifestealEffect() {
        super("LIFESTEAL", "Steal health on hit");
    }

    @Override
    public boolean execute(EffectContext context) {
        double percentage = context.getParams() != null ? context.getParams().getValue() : 10;

        // Cap lifesteal
        double maxLifesteal = getPlugin().getConfigManager().getMainConfig()
                .getDouble("effects.max-lifesteal", 50);
        percentage = Math.min(percentage, maxLifesteal);

        double damage = context.getDamage();
        if (damage <= 0 && context.getBukkitEvent() instanceof EntityDamageByEntityEvent event) {
            damage = event.getFinalDamage();
        }

        double healAmount = damage * (percentage / 100.0);
        if (healAmount <= 0) return false;

        Player player = context.getPlayer();
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double newHealth = Math.min(player.getHealth() + healAmount, maxHealth);
        player.setHealth(newHealth);

        // Visual
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0), 5);

        debug("Lifesteal healed " + player.getName() + " for " + healAmount);
        return true;
    }
}
