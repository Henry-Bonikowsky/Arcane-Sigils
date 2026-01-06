package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Adjusts potion mechanics for 1.8-style combat.
 *
 * Features:
 * - Configurable throw velocity (arc/speed)
 * - Configurable splash radius
 */
public class PotionModule extends AbstractCombatModule implements Listener {

    public PotionModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "potions";
    }

    @Override
    public String getDisplayName() {
        return "Potion Mechanics";
    }

    @Override
    public void onEnable() {
        // Nothing to initialize
    }

    @Override
    public void onDisable() {
        // Nothing to clean up
    }

    /**
     * Modify potion throw velocity when launched.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionThrow(ProjectileLaunchEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof ThrownPotion potion)) return;

        // Modify velocity based on config
        double velocityMult = config.getPotionThrowVelocity();
        if (velocityMult != 1.0) {
            Vector velocity = potion.getVelocity();
            potion.setVelocity(velocity.multiply(velocityMult));
        }
    }

    /**
     * Modify splash radius when potion lands.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!isEnabled()) return;

        double configRadius = config.getSplashRadius();
        double defaultRadius = 4.0; // Vanilla splash radius

        // If radius is different from vanilla, adjust intensity based on distance
        if (Math.abs(configRadius - defaultRadius) > 0.01) {
            ThrownPotion potion = event.getPotion();

            for (LivingEntity entity : event.getAffectedEntities()) {
                double distance = entity.getLocation().distance(potion.getLocation());

                // Calculate new intensity based on configured radius
                // Entities within configRadius get effect, intensity decreases with distance
                if (distance > configRadius) {
                    // Outside configured radius - no effect
                    event.setIntensity(entity, 0.0);
                } else {
                    // Scale intensity: full at center, zero at edge
                    double intensity = 1.0 - (distance / configRadius);
                    intensity = Math.max(0, Math.min(1.0, intensity));
                    event.setIntensity(entity, intensity);
                }
            }
        }
    }

    @Override
    public void applyToPlayer(Player player) {
        // Nothing to apply per-player
    }

    @Override
    public void removeFromPlayer(Player player) {
        // Nothing to remove per-player
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("throw-velocity")
                .displayName("Throw Velocity")
                .description("Potion throw speed multiplier (1.0 = vanilla)")
                .doubleValue(config::getPotionThrowVelocity, config::setPotionThrowVelocity)
                .range(0.3, 2.0)
                .step(0.1)
                .build(),
            ModuleParam.builder("splash-radius")
                .displayName("Splash Radius")
                .description("Splash potion effect radius in blocks")
                .doubleValue(config::getSplashRadius, config::setSplashRadius)
                .range(1.0, 8.0)
                .step(0.5)
                .build()
        );
    }
}
