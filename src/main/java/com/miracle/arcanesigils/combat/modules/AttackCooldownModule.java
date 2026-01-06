package com.miracle.arcanesigils.combat.modules;

import com.destroystokyo.paper.event.player.PlayerAttackEntityCooldownResetEvent;
import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Disables 1.9+ attack cooldown by setting attack speed to a very high value.
 * This allows players to swing as fast as they click, like in 1.8.
 */
public class AttackCooldownModule extends AbstractCombatModule implements Listener {

    private static final double DEFAULT_ATTACK_SPEED = 4.0;

    public AttackCooldownModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "attack-cooldown";
    }

    @Override
    public String getDisplayName() {
        return "Attack Cooldown";
    }

    @Override
    public void applyToPlayer(Player player) {
        if (!isEnabled()) return;

        AttributeInstance attr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attr != null) {
            attr.setBaseValue(config.getAttackSpeed());
        }

        // Also reset the cooldown so they start fresh
        player.resetCooldown();
    }

    @Override
    public void removeFromPlayer(Player player) {
        // Restore default attack speed
        AttributeInstance attr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attr != null) {
            attr.setBaseValue(DEFAULT_ATTACK_SPEED);
        }
    }

    /**
     * Cancel the attack cooldown reset event to prevent vanilla mechanics.
     * This is a Paper-specific event.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCooldownReset(PlayerAttackEntityCooldownResetEvent event) {
        if (!isEnabled()) return;

        // Cancel the cooldown reset - player's cooldown stays ready
        event.setCancelled(true);
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("attack-speed")
                .displayName("Attack Speed")
                .description("Higher = faster attacks (1024+ = no cooldown)")
                .doubleValue(config::getAttackSpeed, config::setAttackSpeed)
                .range(4, 2048)
                .step(128)
                .build()
        );
    }
}
