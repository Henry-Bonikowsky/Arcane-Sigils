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
 * Blink ability - teleports the boss behind the target player.
 * Creates a surprise attack opportunity and disorients players.
 */
public class BlinkAbility extends BossAbility {

    public BlinkAbility() {
        super(
            "blink",
            "Blink",
            120, // 6 second cooldown
            0.0, // no direct damage
            25.0, // maximum teleport range
            AbilityType.TELEPORT,
            createDefaultParams()
        );
    }

    private static Map<String, Object> createDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("behindDistance", 3.0);
        params.put("particle", Particle.PORTAL.name());
        params.put("sound", Sound.ENTITY_ENDERMAN_TELEPORT.name());
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

        Location originalLoc = bossEntity.getLocation();
        Location targetLoc = target.getLocation();

        // Calculate position behind the player
        double behindDistance = (double) getParam("behindDistance", 3.0);
        Vector direction = targetLoc.getDirection().normalize();
        Vector behind = direction.multiply(-1).multiply(behindDistance);

        Location teleportLoc = targetLoc.clone().add(behind);
        teleportLoc.setYaw(targetLoc.getYaw() + 180); // Face the player
        teleportLoc.setPitch(0);

        // Ensure the location is safe
        teleportLoc = findSafeTeleportLocation(teleportLoc);
        if (teleportLoc == null) {
            return false;
        }

        // Spawn particles at original location
        String particleName = (String) getParam("particle", Particle.PORTAL.name());
        try {
            Particle particle = Particle.valueOf(particleName);
            originalLoc.getWorld().spawnParticle(particle, originalLoc, 50, 0.5, 1.0, 0.5, 0.1);
        } catch (IllegalArgumentException e) {
            originalLoc.getWorld().spawnParticle(Particle.PORTAL, originalLoc, 50, 0.5, 1.0, 0.5, 0.1);
        }

        // Play sound at original location
        String soundName = (String) getParam("sound", Sound.ENTITY_ENDERMAN_TELEPORT.name());
        try {
            Sound sound = Sound.valueOf(soundName);
            originalLoc.getWorld().playSound(originalLoc, sound, 1.0f, 0.8f);
        } catch (IllegalArgumentException e) {
            originalLoc.getWorld().playSound(originalLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        }

        // Teleport the boss
        boolean success = bossEntity.teleport(teleportLoc);

        if (success) {
            // Spawn particles at new location
            try {
                Particle particle = Particle.valueOf(particleName);
                teleportLoc.getWorld().spawnParticle(particle, teleportLoc, 50, 0.5, 1.0, 0.5, 0.1);
            } catch (IllegalArgumentException e) {
                teleportLoc.getWorld().spawnParticle(Particle.PORTAL, teleportLoc, 50, 0.5, 1.0, 0.5, 0.1);
            }

            // Play sound at new location
            try {
                Sound sound = Sound.valueOf(soundName);
                teleportLoc.getWorld().playSound(teleportLoc, sound, 1.0f, 1.2f);
            } catch (IllegalArgumentException e) {
                teleportLoc.getWorld().playSound(teleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            }

            // Optional: Send message to player
            target.sendMessage("§5The boss has blinked behind you!");
        }

        return success;
    }

    /**
     * Finds a safe location to teleport to.
     * Ensures the location has solid ground and enough space.
     *
     * @param location The initial teleport location
     * @return A safe location, or null if none found
     */
    private Location findSafeTeleportLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        // Check if the location is already safe
        Location checkLoc = location.clone();

        // Try to find ground within a few blocks up or down
        for (int yOffset = 0; yOffset >= -5; yOffset--) {
            Location testLoc = checkLoc.clone().add(0, yOffset, 0);

            // Check if there's solid ground and space above
            if (testLoc.getBlock().getType().isSolid()) {
                Location spawnLoc = testLoc.add(0, 1, 0);
                if (!spawnLoc.getBlock().getType().isSolid() &&
                    !spawnLoc.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                    return spawnLoc;
                }
            }
        }

        // If no safe location found, return original
        return location;
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

        // Check if boss is invulnerable (don't teleport during invulnerability)
        if (boss.isInvulnerable()) {
            return false;
        }

        return true;
    }
}
