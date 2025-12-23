package com.zenax.dungeons.combat.boss.ability.impl;

import com.zenax.dungeons.combat.boss.BossEntity;
import com.zenax.dungeons.combat.boss.ability.AbilityType;
import com.zenax.dungeons.combat.boss.ability.BossAbility;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Soul Bolt ability - fires a dark projectile at the target player.
 * The projectile deals damage and has a slight homing effect.
 */
public class SoulBoltAbility extends BossAbility {

    public SoulBoltAbility() {
        super(
            "soul_bolt",
            "Soul Bolt",
            60, // 3 second cooldown
            15.0, // damage
            30.0, // range
            AbilityType.PROJECTILE,
            createDefaultParams()
        );
    }

    private static Map<String, Object> createDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("projectileSpeed", 1.5);
        params.put("particle", Particle.SOUL_FIRE_FLAME.name());
        params.put("sound", Sound.ENTITY_WITHER_SHOOT.name());
        return params;
    }

    @Override
    public boolean execute(BossEntity boss, Player target) {
        if (!canUse(boss, target)) {
            return false;
        }

        LivingEntity bossEntity = (LivingEntity) boss.getEntity();
        if (bossEntity == null) {
            return false;
        }

        Location bossLoc = bossEntity.getEyeLocation();
        Location targetLoc = target.getEyeLocation();

        // Calculate direction
        Vector direction = targetLoc.toVector().subtract(bossLoc.toVector()).normalize();

        // Spawn projectile
        SmallFireball fireball = bossLoc.getWorld().spawn(
            bossLoc.add(direction.clone().multiply(0.5)),
            SmallFireball.class
        );

        // Set projectile properties
        double speed = (double) getParam("projectileSpeed", 1.5);
        fireball.setVelocity(direction.multiply(speed));
        fireball.setShooter(bossEntity);
        fireball.setYield(0); // No explosion damage

        // Play sound
        String soundName = (String) getParam("sound", Sound.ENTITY_WITHER_SHOOT.name());
        try {
            Sound sound = Sound.valueOf(soundName);
            bossLoc.getWorld().playSound(bossLoc, sound, 1.0f, 0.8f);
        } catch (IllegalArgumentException e) {
            // Invalid sound, use default
            bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.8f);
        }

        // Spawn particles
        String particleName = (String) getParam("particle", Particle.SOUL_FIRE_FLAME.name());
        try {
            Particle particle = Particle.valueOf(particleName);
            bossLoc.getWorld().spawnParticle(particle, bossLoc, 10, 0.2, 0.2, 0.2, 0.1);
        } catch (IllegalArgumentException e) {
            // Invalid particle, use default
            bossLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bossLoc, 10, 0.2, 0.2, 0.2, 0.1);
        }

        return true;
    }

    @Override
    public boolean canUse(BossEntity boss, Player target) {
        if (!super.canUse(boss, target)) {
            return false;
        }

        // Require a target for this ability
        if (target == null || !target.isOnline() || target.isDead()) {
            return false;
        }

        return true;
    }
}
