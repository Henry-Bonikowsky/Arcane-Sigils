package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements 1.8-style fishing rod mechanics.
 *
 * In 1.8:
 * - Fishing rods push entities away with knockback when hooked
 * - "Rod tricking" - hitting with rod + melee at same time deals extra damage
 *
 * In 1.9+:
 * - Fishing rods don't apply knockback
 */
public class FishingRodModule extends AbstractCombatModule implements Listener {

    // Track recent rod hits for rod tricking
    // Maps attacker UUID -> (victim UUID, timestamp)
    private final Map<UUID, RodHitData> recentRodHits = new ConcurrentHashMap<>();

    // Cleanup task reference for proper cancellation
    private org.bukkit.scheduler.BukkitTask cleanupTask;

    public FishingRodModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "fishing-rod";
    }

    @Override
    public String getDisplayName() {
        return "Fishing Rod";
    }

    /**
     * Apply knockback when fishing rod bobber HITS an entity.
     * In 1.8 PvP, fishing rods push entities away when the bobber first contacts them.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!isEnabled()) return;

        // Only handle fishing hooks
        if (!(event.getEntity() instanceof FishHook hook)) return;

        // Must hit an entity
        Entity hitEntity = event.getHitEntity();
        if (hitEntity == null) return;
        if (!(hitEntity instanceof LivingEntity victim)) return;

        // Get the player who cast the rod
        if (!(hook.getShooter() instanceof Player player)) return;

        // Don't knockback yourself
        if (victim.equals(player)) return;

        // Calculate direction AWAY from the player (1.8 style push)
        Vector direction = victim.getLocation().toVector()
            .subtract(player.getLocation().toVector());

        // Normalize only if length > 0
        if (direction.lengthSquared() > 0) {
            direction.normalize();
        } else {
            // Fallback - push in player's look direction
            direction = player.getLocation().getDirection();
        }

        double force = config.getRodKnockbackForce();
        double vertical = config.getRodKnockbackVertical();

        // Ensure valid values
        if (force <= 0) force = 0.4;
        if (vertical <= 0) vertical = 0.2;

        Vector knockback = direction.multiply(force);
        knockback.setY(vertical);

        // Apply the knockback (push away from player)
        victim.setVelocity(victim.getVelocity().add(knockback));

        // Deal damage tick
        double damage = config.getRodHitDamage();
        if (damage > 0) {
            victim.damage(damage, player);
        }

        // Track for rod tricking
        if (config.isRodTrickingEnabled() && victim instanceof Player) {
            recentRodHits.put(player.getUniqueId(), new RodHitData(
                victim.getUniqueId(),
                System.currentTimeMillis()
            ));
        }
    }

    /**
     * Apply rod tricking bonus damage.
     * Rod trick: Hit with rod + melee at almost the same time = extra damage.
     * This replicates a vanilla 1.8 bug that became a skill mechanic.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (!config.isRodTrickingEnabled()) return;

        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        UUID attackerUuid = attacker.getUniqueId();
        RodHitData rodData = recentRodHits.get(attackerUuid);

        if (rodData == null) return;

        // Check if the rod hit was on this victim
        if (!rodData.victimUuid.equals(victim.getUniqueId())) return;

        // Check if within rod trick window
        long timeSinceRod = System.currentTimeMillis() - rodData.timestamp;
        if (timeSinceRod > config.getRodTrickWindowMs()) {
            // Expired, remove it
            recentRodHits.remove(attackerUuid);
            return;
        }

        // Apply rod trick damage bonus!
        double originalDamage = event.getDamage();
        double multiplier = config.getRodTrickMultiplier();
        event.setDamage(originalDamage * multiplier);

        // Remove the rod data so it can't be used again
        recentRodHits.remove(attackerUuid);
    }

    /**
     * Cancel the vanilla fishing rod pull effect.
     * In 1.9+, reeling in while hooked on an entity pulls them towards you.
     * In 1.8 PvP, we want rods to PUSH away, not pull.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!isEnabled()) return;

        // When catching an entity, cancel the pull by resetting their velocity
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            Entity caught = event.getCaught();
            if (caught != null) {
                // Store current velocity and restore it next tick to cancel the pull
                Vector currentVelocity = caught.getVelocity().clone();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (caught.isValid()) {
                        caught.setVelocity(currentVelocity);
                    }
                });
            }
        }
    }

    /**
     * Clean up expired rod hit data periodically.
     */
    @Override
    public void onEnable() {
        plugin.getLogger().info("[FishingRod] Module enabled - KB force: " +
            config.getRodKnockbackForce() + ", vertical: " + config.getRodKnockbackVertical());

        // Cleanup task every second
        cleanupTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!isEnabled()) return;

            long now = System.currentTimeMillis();
            int windowMs = config.getRodTrickWindowMs() + 1000; // Add buffer

            recentRodHits.entrySet().removeIf(entry ->
                (now - entry.getValue().timestamp) > windowMs
            );
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        recentRodHits.clear();
    }

    /**
     * Data class for tracking rod hits.
     */
    private record RodHitData(UUID victimUuid, long timestamp) {}

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("hit-damage")
                .displayName("Hit Damage")
                .description("Damage dealt when rod hits an entity")
                .doubleValue(config::getRodHitDamage, config::setRodHitDamage)
                .range(0.0, 4.0)
                .step(0.5)
                .build(),
            ModuleParam.builder("knockback-force")
                .displayName("KB Force")
                .description("Horizontal knockback when rod hooks")
                .doubleValue(config::getRodKnockbackForce, config::setRodKnockbackForce)
                .range(0.1, 1.0)
                .step(0.05)
                .build(),
            ModuleParam.builder("knockback-vertical")
                .displayName("Vertical KB")
                .description("Vertical knockback when rod hooks")
                .doubleValue(config::getRodKnockbackVertical, config::setRodKnockbackVertical)
                .range(0.05, 0.5)
                .step(0.025)
                .build(),
            ModuleParam.builder("rod-tricking")
                .displayName("Rod Tricking")
                .description("Enable rod + hit combo for extra damage")
                .boolValue(config::isRodTrickingEnabled, config::setRodTrickingEnabled)
                .build(),
            ModuleParam.builder("rod-trick-window")
                .displayName("Trick Window")
                .description("Time window for rod trick combo (ms)")
                .msValue(config::getRodTrickWindowMs, config::setRodTrickWindowMs)
                .range(50, 300)
                .step(10)
                .build(),
            ModuleParam.builder("rod-trick-multiplier")
                .displayName("Trick Multiplier")
                .description("Damage multiplier for rod trick")
                .doubleValue(config::getRodTrickMultiplier, config::setRodTrickMultiplier)
                .range(1.0, 4.0)
                .step(0.25)
                .build()
        );
    }
}
