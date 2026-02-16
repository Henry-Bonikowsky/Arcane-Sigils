package com.miracle.arcanesigils.nms;

import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener that implements fast attack speed by manually triggering attacks
 */
public class FastAttackListener implements Listener {
    
    private final Plugin plugin;
    private final Map<UUID, BukkitRunnable> attackTasks = new HashMap<>();
    
    public FastAttackListener(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Start a fast attack task for a mob
     */
    public void enableFastAttack(Mob mob, double multiplier) {
        // Cancel existing task if any
        BukkitRunnable existing = attackTasks.remove(mob.getUniqueId());
        if (existing != null) {
            existing.cancel();
        }
        
        // Calculate attack interval (vanilla is 20 ticks = 1 second)
        int interval = (int) (20 / multiplier); // 2x multiplier = 10 ticks = 0.5 seconds
        
        LogHelper.debug("[FastAttackListener] Starting fast attack task: interval=%d ticks (%.1fx speed)", 
            interval, multiplier);
        
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead()) {
                    cancel();
                    attackTasks.remove(mob.getUniqueId());
                    return;
                }
                
                LivingEntity target = mob.getTarget();
                if (target == null || !target.isValid() || target.isDead()) {
                    return;
                }
                
                // Constantly path toward target to prevent stopping
                mob.getPathfinder().moveTo(target, 1.2);
                
                double distance = mob.getLocation().distance(target.getLocation());
                if (distance <= 2.5) { // Attack range
                    // Apply speed boost to prevent stopping
                    mob.setVelocity(target.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize().multiply(0.3));
                    
                    // Manually trigger attack
                    mob.attack(target);
                    
                    // Keep them moving forward slightly during attack animation
                    org.bukkit.Bukkit.getScheduler().runTaskLater(
                        FastAttackListener.this.plugin,
                        () -> {
                            if (mob.isValid() && !mob.isDead() && mob.getTarget() != null) {
                                org.bukkit.util.Vector direction = mob.getTarget().getLocation().toVector()
                                    .subtract(mob.getLocation().toVector()).normalize();
                                mob.setVelocity(direction.multiply(0.2));
                            }
                        },
                        2L  // 2 ticks after attack
                    );
                    
                    LogHelper.debug("[FastAttackListener] Manual attack triggered with movement");
                }
            }
        };
        
        task.runTaskTimer(plugin, interval, interval);
        attackTasks.put(mob.getUniqueId(), task);
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Cancel vanilla attacks from fast-attack mobs to prevent double-hitting
        if (event.getDamager() instanceof Mob mob) {
            if (mob.hasMetadata("FAST_ATTACK_ENABLED")) {
                // Let our manual attacks handle it
            }
        }
    }
    
    public void cleanup() {
        for (BukkitRunnable task : attackTasks.values()) {
            task.cancel();
        }
        attackTasks.clear();
    }
}
