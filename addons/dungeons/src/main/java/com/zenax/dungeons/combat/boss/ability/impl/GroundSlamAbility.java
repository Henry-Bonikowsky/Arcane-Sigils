package com.zenax.dungeons.combat.boss.ability.impl;

import com.zenax.dungeons.combat.boss.BossEntity;
import com.zenax.dungeons.combat.boss.ability.AbilityType;
import com.zenax.dungeons.combat.boss.ability.BossAbility;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Ground Slam ability - creates an AOE knockback effect around the boss.
 * Deals damage and knocks back all nearby players.
 */
public class GroundSlamAbility extends BossAbility {

    public GroundSlamAbility() {
        super(
            "ground_slam",
            "Ground Slam",
            100, // 5 second cooldown
            20.0, // damage
            8.0, // AOE radius
            AbilityType.AOE,
            createDefaultParams()
        );
    }

    private static Map<String, Object> createDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("knockbackStrength", 2.0);
        params.put("verticalKnockback", 0.5);
        params.put("particle", Particle.EXPLOSION.name());
        params.put("sound", Sound.ENTITY_GENERIC_EXPLODE.name());
        return params;
    }

    @Override
    public boolean execute(BossEntity boss, Player target) {
        if (!canUse(boss, target)) {
            return false;
        }

        Entity bossEntity = boss.getEntity();
        if (bossEntity == null) {
            return false;
        }

        Location bossLoc = bossEntity.getLocation();
        double radius = getRange();
        double damage = getDamage();
        double knockbackStrength = (double) getParam("knockbackStrength", 2.0);
        double verticalKnockback = (double) getParam("verticalKnockback", 0.5);

        // Find all nearby players
        for (Entity entity : bossEntity.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player) {
                Player player = (Player) entity;
                if (player.isDead()) {
                    continue;
                }

                // Calculate direction from boss to player
                Vector direction = player.getLocation().toVector()
                    .subtract(bossLoc.toVector())
                    .normalize();

                // Apply knockback
                Vector knockback = direction.multiply(knockbackStrength);
                knockback.setY(verticalKnockback);
                player.setVelocity(knockback);

                // Deal damage
                player.damage(damage, bossEntity);

                // Spawn particles at player location
                String particleName = (String) getParam("particle", Particle.EXPLOSION.name());
                try {
                    Particle particle = Particle.valueOf(particleName);
                    player.getWorld().spawnParticle(particle, player.getLocation(), 5, 0.5, 0.5, 0.5, 0.1);
                } catch (IllegalArgumentException e) {
                    player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 5, 0.5, 0.5, 0.5, 0.1);
                }
            }
        }

        // Play sound at boss location
        String soundName = (String) getParam("sound", Sound.ENTITY_GENERIC_EXPLODE.name());
        try {
            Sound sound = Sound.valueOf(soundName);
            bossLoc.getWorld().playSound(bossLoc, sound, 2.0f, 0.5f);
        } catch (IllegalArgumentException e) {
            bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        }

        // Spawn ground particles
        for (int i = 0; i < 360; i += 15) {
            double angle = Math.toRadians(i);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particleLoc = bossLoc.clone().add(x, 0.1, z);
            bossLoc.getWorld().spawnParticle(Particle.BLOCK, particleLoc, 3, 0.1, 0.1, 0.1, 0.1);
        }

        return true;
    }

    @Override
    public boolean canUse(BossEntity boss, Player target) {
        if (!super.canUse(boss, target)) {
            return false;
        }

        // Check if boss is on the ground
        Entity bossEntity = boss.getEntity();
        if (bossEntity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) bossEntity;
            // Can only use if on ground (not flying)
            return living.isOnGround();
        }

        return true;
    }
}
