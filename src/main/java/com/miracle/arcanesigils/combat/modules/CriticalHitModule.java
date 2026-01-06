package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;

/**
 * Allows critical hits without the 1.9+ attack cooldown requirement.
 * In 1.8, crits only required the player to be falling and not on ground.
 * In 1.9+, crits also require 84.8%+ attack cooldown charge.
 */
public class CriticalHitModule extends AbstractCombatModule implements Listener {

    public CriticalHitModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "critical-hits";
    }

    @Override
    public String getDisplayName() {
        return "Critical Hits";
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        // Check if this would be a crit in 1.8 (falling + not on ground)
        if (!is18CriticalHit(attacker)) return;

        // Check if the attack cooldown prevented vanilla crit
        float cooldown = attacker.getAttackCooldown();
        if (cooldown >= 0.848f) {
            // Vanilla already applied crit, no need to modify
            return;
        }

        // Apply 1.8-style crit damage manually
        double originalDamage = event.getDamage();
        double critDamage = originalDamage * config.getCritDamageMultiplier();
        event.setDamage(critDamage);

        // Play crit effects
        playCritEffects(event.getEntity());
    }

    /**
     * Check if the player meets 1.8 critical hit requirements:
     * - Falling (negative Y velocity)
     * - Not on ground
     * - Not in water/climbing/flying
     */
    private boolean is18CriticalHit(Player player) {
        // Must be falling
        double yVelocity = player.getVelocity().getY();
        if (yVelocity >= config.getCritFallingThreshold()) {
            return false;
        }

        // Must not be on ground
        if (player.isOnGround()) {
            return false;
        }

        // Must not be in water, climbing, or flying
        if (player.isInWater() || player.isClimbing() || player.isFlying() || player.isGliding()) {
            return false;
        }

        // Must not be in a vehicle
        if (player.isInsideVehicle()) {
            return false;
        }

        return true;
    }

    private void playCritEffects(Entity target) {
        // Spawn crit particles
        target.getWorld().spawnParticle(
            Particle.CRIT,
            target.getLocation().add(0, 1, 0),
            10, 0.5, 0.5, 0.5, 0.1
        );

        // Play crit sound
        target.getWorld().playSound(
            target.getLocation(),
            Sound.ENTITY_PLAYER_ATTACK_CRIT,
            1.0f, 1.0f
        );
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("damage-multiplier")
                .displayName("Crit Multiplier")
                .description("Damage multiplier for critical hits")
                .doubleValue(config::getCritDamageMultiplier, config::setCritDamageMultiplier)
                .range(1.0, 3.0)
                .step(0.1)
                .build(),
            ModuleParam.builder("falling-threshold")
                .displayName("Fall Threshold")
                .description("Y velocity to count as falling")
                .doubleValue(config::getCritFallingThreshold, config::setCritFallingThreshold)
                .range(-0.2, 0.0)
                .step(0.01)
                .build()
        );
    }
}
