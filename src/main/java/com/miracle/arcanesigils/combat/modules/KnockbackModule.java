package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import com.miracle.arcanesigils.combat.sync.KnockbackCalculator;
import com.miracle.arcanesigils.combat.sync.PositionTracker;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Implements 1.8-style knockback with rollback netcode for ping compensation.
 *
 * This is the most critical module for authentic 1.8 PvP feel.
 *
 * Key features:
 * - Overrides vanilla knockback completely
 * - Uses position history for ping compensation (rollback netcode)
 * - Proper sprint knockback and W-tap detection
 * - Forward movement reduction
 */
public class KnockbackModule extends AbstractCombatModule implements Listener {

    private static final NamespacedKey KB_NULLIFIER_KEY = new NamespacedKey("arcanesigils", "legacy_kb_nullifier");

    private KnockbackCalculator calculator;

    public KnockbackModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "knockback";
    }

    @Override
    public String getDisplayName() {
        return "Knockback";
    }

    @Override
    public void onEnable() {
        PositionTracker tracker = manager.getPositionTracker();
        this.calculator = new KnockbackCalculator(config, tracker);

        // Remove netherite KB resistance from all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            nullifyKnockbackResistance(player);
        }
    }

    @Override
    public void onDisable() {
        this.calculator = null;

        // Restore KB resistance for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreKnockbackResistance(player);
        }
    }

    /**
     * Apply KB resistance nullifier when player joins.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;

        // Delay slightly to ensure armor is loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            nullifyKnockbackResistance(event.getPlayer());
        }, 5L);
    }

    /**
     * Nullify knockback resistance by adding a negative modifier.
     * This counteracts netherite's KB resistance (0.1 per piece).
     */
    private void nullifyKnockbackResistance(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (attr == null) return;

        // Remove existing nullifier if present
        attr.removeModifier(KB_NULLIFIER_KEY);

        // Get current KB resistance (from netherite, etc.)
        double currentResistance = attr.getValue();

        if (currentResistance > 0) {
            // Add a modifier to negate the resistance
            AttributeModifier nullifier = new AttributeModifier(
                KB_NULLIFIER_KEY,
                -currentResistance,
                AttributeModifier.Operation.ADD_NUMBER
            );
            attr.addModifier(nullifier);
        }
    }

    /**
     * Restore natural knockback resistance by removing our nullifier.
     */
    private void restoreKnockbackResistance(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (attr == null) return;

        attr.removeModifier(KB_NULLIFIER_KEY);
    }

    /**
     * Intercept all damage events and apply custom knockback.
     * Priority HIGHEST to run after other plugins but before final processing.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (calculator == null) return;

        // Get the actual damager (handle projectiles, etc.)
        Player attacker = getAttacker(event);
        if (attacker == null) return;

        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        // Cancel vanilla knockback - we'll apply our own
        // Note: This uses Paper's API. For Spigot, we'd need a different approach.
        try {
            // Paper 1.20.4+ has this method
            // event.setKnockback(false);
            // For older versions or Spigot, we cancel and reapply

            // Store the original damage
            double damage = event.getFinalDamage();

            // Schedule our custom knockback to apply after the event
            // This ensures it overrides vanilla knockback
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isDead()) return;

                // Calculate 1.8-style knockback with rollback if applicable
                Vector knockback = calculator.calculateWithRollback(attacker, victim);

                // Apply any modifiers (enchantments, etc.)
                knockback = calculator.applyModifiers(knockback, attacker, victim);

                // Apply the knockback
                // We add to existing velocity to allow combos
                Vector currentVel = victim.getVelocity();

                // For horizontal, we override; for vertical, we take the max
                Vector finalKb = new Vector(
                    knockback.getX(),
                    Math.max(currentVel.getY(), knockback.getY()),
                    knockback.getZ()
                );

                victim.setVelocity(finalKb);

            }, 0L); // Run on next tick

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply custom knockback: " + e.getMessage());
        }
    }

    /**
     * Get the attacking player from a damage event.
     * Handles direct attacks and projectiles.
     */
    private Player getAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }

        // Handle projectiles
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }

        return null;
    }

    @Override
    public void reload() {
        super.reload();
        if (isEnabled() && calculator != null) {
            // Recreate calculator with new config values
            PositionTracker tracker = manager.getPositionTracker();
            this.calculator = new KnockbackCalculator(config, tracker);
        }
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("horizontal-base")
                .displayName("Horizontal KB")
                .description("Base horizontal knockback force")
                .doubleValue(config::getKbHorizontalBase, config::setKbHorizontalBase)
                .range(0.1, 1.5)
                .step(0.05)
                .build(),
            ModuleParam.builder("vertical-base")
                .displayName("Vertical KB")
                .description("Base vertical knockback force")
                .doubleValue(config::getKbVerticalBase, config::setKbVerticalBase)
                .range(0.1, 1.0)
                .step(0.05)
                .build(),
            ModuleParam.builder("vertical-cap")
                .displayName("Vertical Cap")
                .description("Maximum vertical knockback")
                .doubleValue(config::getKbVerticalCap, config::setKbVerticalCap)
                .range(0.1, 1.0)
                .step(0.05)
                .build(),
            ModuleParam.builder("sprint-multiplier")
                .displayName("Sprint Bonus")
                .description("Extra KB when sprinting")
                .doubleValue(config::getKbSprintMultiplier, config::setKbSprintMultiplier)
                .range(0.5, 2.0)
                .step(0.1)
                .build(),
            ModuleParam.builder("w-tap-window")
                .displayName("W-Tap Window")
                .description("Time window to detect W-tap (ms)")
                .msValue(config::getKbWTapWindowMs, config::setKbWTapWindowMs)
                .range(50, 300)
                .step(10)
                .build(),
            ModuleParam.builder("forward-reduction")
                .displayName("Forward Reduction")
                .description("KB reduction when moving forward")
                .doubleValue(config::getKbForwardReduction, config::setKbForwardReduction)
                .range(0.1, 1.0)
                .step(0.1)
                .build(),
            ModuleParam.builder("sync-enabled")
                .displayName("Ping Compensation")
                .description("Enable rollback netcode for KB")
                .boolValue(config::isKbSyncEnabled, config::setKbSyncEnabled)
                .build(),
            ModuleParam.builder("sync-ping-offset")
                .displayName("Ping Offset")
                .description("Base ping offset for compensation (ms)")
                .msValue(config::getKbSyncPingOffsetMs, config::setKbSyncPingOffsetMs)
                .range(0, 100)
                .step(5)
                .build(),
            ModuleParam.builder("sync-max-compensation")
                .displayName("Max Compensation")
                .description("Maximum ping compensation (ms)")
                .msValue(config::getKbSyncMaxCompensationMs, config::setKbSyncMaxCompensationMs)
                .range(50, 500)
                .step(25)
                .build()
        );
    }
}
