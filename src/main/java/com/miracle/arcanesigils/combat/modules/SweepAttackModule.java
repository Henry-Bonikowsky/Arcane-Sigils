package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Disables 1.9+ sweep attack by setting the sweep damage ratio to 0
 * AND cancelling sweep damage events entirely.
 * In 1.8, swords did not have an AOE sweep attack.
 */
public class SweepAttackModule extends AbstractCombatModule implements Listener {

    private static final double DEFAULT_SWEEP_RATIO = 1.0;
    private static final boolean DEBUG = true; // Set to false in production

    public SweepAttackModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "sweep-attack";
    }

    @Override
    public String getDisplayName() {
        return "Sweep Attack";
    }

    @Override
    public void onEnable() {
        // Periodically enforce sweep ratio = 0 for all online players (every 5 seconds)
        // This prevents other plugins/systems from resetting it
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (isEnabled()) {
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    setSweepRatioZero(p);
                }
            }
        }, 100L, 100L);
        
        if (DEBUG) {
            plugin.getLogger().info("[SweepAttack] Enabled with periodic enforcement");
        }
    }

    @Override
    public void applyToPlayer(Player player) {
        if (!isEnabled()) return;
        setSweepRatioZero(player);
        if (DEBUG) {
            plugin.getLogger().info("[SweepAttack] Applied to " + player.getName());
        }
    }

    @Override
    public void removeFromPlayer(Player player) {
        // Restore default sweep ratio
        AttributeInstance attr = player.getAttribute(Attribute.SWEEPING_DAMAGE_RATIO);
        if (attr != null) {
            attr.setBaseValue(DEFAULT_SWEEP_RATIO);
            if (DEBUG) {
                plugin.getLogger().info("[SweepAttack] Removed from " + player.getName());
            }
        }
    }

    private void setSweepRatioZero(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.SWEEPING_DAMAGE_RATIO);
        if (attr != null) {
            double oldValue = attr.getBaseValue();
            attr.setBaseValue(0.0);
            if (DEBUG && oldValue != 0.0) {
                plugin.getLogger().info("[SweepAttack] Set " + player.getName() +
                    " sweep ratio: " + oldValue + " -> 0.0");
            }
        } else if (DEBUG) {
            plugin.getLogger().warning("[SweepAttack] Could not get sweep attribute for " +
                player.getName());
        }
    }

    private boolean isHoldingSword(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        String materialName = item.getType().name();
        return materialName.endsWith("_SWORD");
    }

    // Cancel at LOWEST priority
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSweepAttackLowest(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;

        // AGGRESSIVE: Cancel ANY damage from player holding sword if cause is sweep
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            if (DEBUG) {
                plugin.getLogger().warning("[SweepAttack] LOWEST: BLOCKING SWEEP! " +
                    "Damager: " + event.getDamager().getName() +
                    ", Victim: " + event.getEntity().getName() +
                    ", Damage: " + event.getDamage());
            }

            event.setCancelled(true);
            event.setDamage(0);
            return; // Don't process further
        }

        // AGGRESSIVE: If player is holding sword, ensure sweep ratio is 0
        if (event.getDamager() instanceof Player attacker) {
            if (isHoldingSword(attacker)) {
                setSweepRatioZero(attacker);
                
                // EXTRA CHECK: If damage is suspiciously low (< 1), might be a sweep
                if (event.getDamage() < 1.0 && event.getDamage() > 0) {
                    if (DEBUG) {
                        plugin.getLogger().warning("[SweepAttack] Suspicious low damage (" + 
                            event.getDamage() + ") - might be sweep! Blocking.");
                    }
                    event.setCancelled(true);
                    event.setDamage(0);
                }
            }
        }
    }

    // Cancel at LOW priority
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onSweepAttackLow(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
            event.setDamage(0);
        }
    }

    // Cancel at NORMAL priority
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onSweepAttackNormal(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
            event.setDamage(0);
        }
    }

    // Cancel at HIGH priority
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onSweepAttackHigh(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
            event.setDamage(0);
        }
    }

    // Cancel at HIGHEST priority - FINAL ATTEMPT
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSweepAttackHighest(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            if (DEBUG) {
                plugin.getLogger().warning("[SweepAttack] HIGHEST: FINAL BLOCK! " +
                    "Cancelled status: " + event.isCancelled());
            }

            event.setCancelled(true);
            event.setDamage(0);
        }
    }

    // MONITOR priority - LOG if anything gets through
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSweepMonitor(EntityDamageByEntityEvent event) {
        if (!isEnabled() || !DEBUG) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            if (!event.isCancelled()) {
                plugin.getLogger().severe("[SweepAttack] CRITICAL: SWEEP ATTACK GOT THROUGH! " +
                    "Damage: " + event.getDamage() + ", Cancelled: " + event.isCancelled());
            }
        }
    }

    // Diagnostic handler to catch ALL damage events for testing
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onAnyDamage(EntityDamageByEntityEvent event) {
        if (!DEBUG || !isEnabled()) return;

        if (event.getDamager() instanceof Player attacker && isHoldingSword(attacker)) {
            AttributeInstance attr = attacker.getAttribute(Attribute.SWEEPING_DAMAGE_RATIO);
            double ratio = attr != null ? attr.getBaseValue() : -1.0;

            plugin.getLogger().info("[SweepAttack] MONITOR: Damage event - " +
                "Cause: " + event.getCause() +
                ", Damage: " + event.getDamage() +
                ", Sweep Ratio: " + ratio +
                ", Cancelled: " + event.isCancelled());
        }
    }
}
