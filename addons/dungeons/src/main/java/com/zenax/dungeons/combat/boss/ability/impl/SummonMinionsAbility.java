package com.zenax.dungeons.combat.boss.ability.impl;

import com.zenax.dungeons.combat.boss.BossEntity;
import com.zenax.dungeons.combat.boss.ability.AbilityType;
import com.zenax.dungeons.combat.boss.ability.BossAbility;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Summon Minions ability - spawns hostile mobs to aid the boss.
 * The number and type of mobs can be configured.
 */
public class SummonMinionsAbility extends BossAbility {

    public SummonMinionsAbility() {
        super(
            "summon_minions",
            "Summon Minions",
            200, // 10 second cooldown
            0.0, // no direct damage
            15.0, // spawn radius
            AbilityType.SUMMON,
            createDefaultParams()
        );
    }

    private static Map<String, Object> createDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("minionType", EntityType.ZOMBIE.name());
        params.put("minionCount", 3);
        params.put("spawnRadius", 5.0);
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

        Location bossLoc = bossEntity.getLocation();

        // Get parameters
        String minionTypeName = (String) getParam("minionType", EntityType.ZOMBIE.name());
        int minionCount = ((Number) getParam("minionCount", 3)).intValue();
        double spawnRadius = (double) getParam("spawnRadius", 5.0);

        // Parse minion type
        EntityType minionType;
        try {
            minionType = EntityType.valueOf(minionTypeName);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid minion type: " + minionTypeName);
            minionType = EntityType.ZOMBIE;
        }

        // Spawn minions
        int spawned = 0;
        for (int i = 0; i < minionCount; i++) {
            // Calculate random spawn position around boss
            double angle = Math.random() * Math.PI * 2;
            double distance = Math.random() * spawnRadius;
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;

            Location spawnLoc = bossLoc.clone().add(x, 0, z);

            // Find safe ground location
            spawnLoc = findSafeLocation(spawnLoc);
            if (spawnLoc == null) {
                continue;
            }

            try {
                // Spawn minion
                Entity minion = bossLoc.getWorld().spawnEntity(spawnLoc, minionType);

                // Configure minion if it's a mob
                if (minion instanceof Mob) {
                    Mob mob = (Mob) minion;
                    mob.setRemoveWhenFarAway(true);

                    // Set target if available
                    if (target != null && target.isOnline()) {
                        mob.setTarget(target);
                    }
                }

                // Spawn particles
                String particleName = (String) getParam("particle", Particle.PORTAL.name());
                try {
                    Particle particle = Particle.valueOf(particleName);
                    spawnLoc.getWorld().spawnParticle(particle, spawnLoc, 30, 0.5, 1.0, 0.5, 0.1);
                } catch (IllegalArgumentException e) {
                    spawnLoc.getWorld().spawnParticle(Particle.PORTAL, spawnLoc, 30, 0.5, 1.0, 0.5, 0.1);
                }

                spawned++;
            } catch (Exception e) {
                System.err.println("Error spawning minion: " + e.getMessage());
            }
        }

        if (spawned > 0) {
            // Play sound at boss location
            String soundName = (String) getParam("sound", Sound.ENTITY_ENDERMAN_TELEPORT.name());
            try {
                Sound sound = Sound.valueOf(soundName);
                bossLoc.getWorld().playSound(bossLoc, sound, 1.5f, 0.7f);
            } catch (IllegalArgumentException e) {
                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.7f);
            }
        }

        return spawned > 0;
    }

    /**
     * Finds a safe location to spawn a minion.
     * Adjusts the Y coordinate to find solid ground.
     *
     * @param location The initial location
     * @return A safe location, or null if none found
     */
    private Location findSafeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        // Start from the initial Y and search down
        Location checkLoc = location.clone();
        for (int i = 0; i < 10; i++) {
            if (checkLoc.getBlock().getType().isSolid()) {
                // Found solid ground, spawn above it
                return checkLoc.add(0, 1, 0);
            }
            checkLoc.subtract(0, 1, 0);
        }

        // If no ground found nearby, use original location
        return location;
    }

    @Override
    public boolean canUse(BossEntity boss, Player target) {
        if (!super.canUse(boss, target)) {
            return false;
        }

        // Check if there are already too many nearby mobs
        Entity bossEntity = boss.getEntity();
        if (bossEntity != null) {
            long nearbyMobCount = bossEntity.getNearbyEntities(15, 15, 15).stream()
                .filter(e -> e instanceof Monster)
                .count();

            // Limit total mobs to prevent overwhelming players
            if (nearbyMobCount > 20) {
                return false;
            }
        }

        return true;
    }
}
