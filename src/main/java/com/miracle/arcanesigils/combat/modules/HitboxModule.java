package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Extends player reach to match 1.8 hitbox margins.
 * In 1.8, there was a 0.1 block hitbox margin giving effectively 3.1 block reach.
 * In 1.9+, players must hit inside the hitbox with only 3.0 block reach.
 */
public class HitboxModule extends AbstractCombatModule {

    private static final double DEFAULT_REACH = 3.0;

    public HitboxModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "hitbox";
    }

    @Override
    public String getDisplayName() {
        return "Hitbox Extend";
    }

    @Override
    public void applyToPlayer(Player player) {
        if (!isEnabled()) return;

        // Extend player entity interaction range
        AttributeInstance attr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr != null) {
            attr.setBaseValue(DEFAULT_REACH + config.getReachExtension());
        }
    }

    @Override
    public void removeFromPlayer(Player player) {
        // Restore default reach
        AttributeInstance attr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr != null) {
            attr.setBaseValue(DEFAULT_REACH);
        }
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("reach-extension")
                .displayName("Reach Extension")
                .description("Extra reach in blocks (1.8 had 0.1)")
                .doubleValue(config::getReachExtension, config::setReachExtension)
                .range(0, 0.5)
                .step(0.025)
                .build()
        );
    }
}
