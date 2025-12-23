package com.zenax.dungeons.combat.boss.ability.impl;

import com.zenax.dungeons.combat.boss.BossEntity;
import com.zenax.dungeons.combat.boss.ability.AbilityType;
import com.zenax.dungeons.combat.boss.ability.BossAbility;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Death Grip ability - pulls the target player towards the boss.
 * Creates a powerful pulling effect with visual and sound effects.
 */
public class DeathGripAbility extends BossAbility {

    public DeathGripAbility() {
        super(
            "death_grip",
            "Death Grip",
            140, // 7 second cooldown
            10.0, // damage on pull
            20.0, // maximum pull range
            AbilityType.GRAB,
            createDefaultParams()
        );
    }

    private static Map<String, Object> createDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("pullStrength", 2.5);
        params.put("stopDistance", 3.0);
        params.put("particle", Particle.WITCH.name());
        params.put("sound", Sound.ENTITY_WITHER_SHOOT.name());
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
        Location targetLoc = target.getLocation();

        // Calculate distance
        double distance = bossLoc.distance(targetLoc);
        double stopDistance = (double) getParam("stopDistance", 3.0);

        // Don't pull if already close
        if (distance <= stopDistance) {
            return false;
        }

        // Calculate pull direction (from player to boss)
        Vector direction = bossLoc.toVector().subtract(targetLoc.toVector()).normalize();

        // Apply pull velocity
        double pullStrength = (double) getParam("pullStrength", 2.5);
        Vector pullVelocity = direction.multiply(pullStrength);

        // Add slight upward component to prevent ground collision
        pullVelocity.setY(pullVelocity.getY() + 0.3);

        target.setVelocity(pullVelocity);

        // Deal damage
        target.damage(getDamage(), bossEntity);

        // Create particle trail from player to boss
        createParticleTrail(targetLoc, bossLoc);

        // Play sound at boss location
        String soundName = (String) getParam("sound", Sound.ENTITY_WITHER_SHOOT.name());
        try {
            Sound sound = Sound.valueOf(soundName);
            bossLoc.getWorld().playSound(bossLoc, sound, 1.5f, 0.5f);
        } catch (IllegalArgumentException e) {
            bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WITHER_SHOOT, 1.5f, 0.5f);
        }

        // Play sound at player location
        try {
            Sound sound = Sound.valueOf(soundName);
            targetLoc.getWorld().playSound(targetLoc, sound, 1.0f, 0.7f);
        } catch (IllegalArgumentException e) {
            targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.7f);
        }

        // Send message to player
        target.sendMessage("§cYou are pulled towards the boss!");

        return true;
    }

    /**
     * Creates a particle trail from the player to the boss.
     *
     * @param from Starting location (player)
     * @param to Ending location (boss)
     */
    private void createParticleTrail(Location from, Location to) {
        if (from.getWorld() != to.getWorld()) {
            return;
        }

        String particleName = (String) getParam("particle", Particle.WITCH.name());
        Particle particle;
        try {
            particle = Particle.valueOf(particleName);
        } catch (IllegalArgumentException e) {
            particle = Particle.WITCH;
        }

        // Calculate number of particles based on distance
        double distance = from.distance(to);
        int particleCount = (int) (distance * 2);

        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        direction.normalize();

        // Spawn particles along the line
        for (int i = 0; i < particleCount; i++) {
            double progress = (double) i / particleCount;
            Vector offset = direction.clone().multiply(progress * length);
            Location particleLoc = from.clone().add(offset);

            from.getWorld().spawnParticle(particle, particleLoc, 2, 0.1, 0.1, 0.1, 0);
        }
    }

    @Override
    public boolean canUse(BossEntity boss, Player target) {
        if (!super.canUse(boss, target)) {
            return false;
        }

        // Require a valid target
        if (target == null || !target.isOnline() || target.isDead()) {
            return false;
        }

        // Check if player is flying (creative/spectator mode)
        if (target.isFlying() || target.getAllowFlight()) {
            return false;
        }

        // Check minimum distance
        Entity bossEntity = boss.getEntity();
        if (bossEntity != null) {
            double stopDistance = (double) getParam("stopDistance", 3.0);
            double distance = bossEntity.getLocation().distance(target.getLocation());

            // Don't use if player is too close
            if (distance <= stopDistance) {
                return false;
            }
        }

        return true;
    }
}
