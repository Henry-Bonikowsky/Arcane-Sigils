package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Adds knockback to projectiles like snowballs and eggs.
 *
 * In 1.8 PvP, snowballs and eggs were commonly used for:
 * - Knocking players off edges
 * - Combo extension
 * - Rod replacement
 */
public class ProjectileKnockbackModule extends AbstractCombatModule implements Listener {

    public ProjectileKnockbackModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "projectile-kb";
    }

    @Override
    public String getDisplayName() {
        return "Projectile Knockback";
    }

    @Override
    public void onEnable() {
        // Nothing to initialize
    }

    @Override
    public void onDisable() {
        // Nothing to clean up
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!isEnabled()) return;

        Entity hitEntity = event.getHitEntity();
        if (!(hitEntity instanceof LivingEntity victim)) return;

        Entity projectile = event.getEntity();
        double horizontalKb;
        double verticalKb;

        if (projectile instanceof Snowball) {
            horizontalKb = config.getSnowballKb();
            verticalKb = config.getSnowballKbVertical();
        } else if (projectile instanceof Egg) {
            horizontalKb = config.getEggKb();
            verticalKb = config.getEggKbVertical();
        } else {
            return; // Not a projectile we care about
        }

        // Calculate knockback direction
        Vector direction = victim.getLocation().toVector()
            .subtract(projectile.getLocation().toVector());
        direction.setY(0);

        if (direction.lengthSquared() > 0) {
            direction.normalize();
        } else {
            // Fallback direction
            direction = projectile.getVelocity().normalize();
            direction.setY(0);
        }

        // Apply knockback
        Vector knockback = direction.multiply(horizontalKb);
        knockback.setY(verticalKb);

        // Add to existing velocity for combo potential
        Vector currentVel = victim.getVelocity();
        knockback.add(new Vector(0, Math.max(0, currentVel.getY()), 0));

        victim.setVelocity(knockback);
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
            ModuleParam.builder("snowball-kb")
                .displayName("Snowball KB")
                .description("Horizontal knockback for snowballs")
                .doubleValue(config::getSnowballKb, config::setSnowballKb)
                .range(0.1, 1.0)
                .step(0.05)
                .build(),
            ModuleParam.builder("snowball-vertical")
                .displayName("Snowball Vertical")
                .description("Vertical knockback for snowballs")
                .doubleValue(config::getSnowballKbVertical, config::setSnowballKbVertical)
                .range(0.05, 0.5)
                .step(0.025)
                .build(),
            ModuleParam.builder("egg-kb")
                .displayName("Egg KB")
                .description("Horizontal knockback for eggs")
                .doubleValue(config::getEggKb, config::setEggKb)
                .range(0.1, 1.0)
                .step(0.05)
                .build(),
            ModuleParam.builder("egg-vertical")
                .displayName("Egg Vertical")
                .description("Vertical knockback for eggs")
                .doubleValue(config::getEggKbVertical, config::setEggKbVertical)
                .range(0.05, 0.5)
                .step(0.025)
                .build()
        );
    }
}
