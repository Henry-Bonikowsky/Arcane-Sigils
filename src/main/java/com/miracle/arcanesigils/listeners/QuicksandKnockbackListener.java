package com.miracle.arcanesigils.listeners;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cancels knockback from attacks when attacker has active Quicksand.
 */
public class QuicksandKnockbackListener implements Listener {

    private final ArmorSetsPlugin plugin;
    // Track which players should receive zero knockback (victim UUID -> expiry time)
    private final Map<UUID, Long> noKnockbackVictims = new HashMap<>();

    public QuicksandKnockbackListener(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // Get attacker
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player shooter) {
                attacker = shooter;
            }
        }

        if (attacker == null) return;

        // Check if attacker has active Quicksand
        if (plugin.hasActiveQuicksand(attacker.getUniqueId())) {
            // Mark victim for zero knockback (expires in 50ms)
            noKnockbackVictims.put(victim.getUniqueId(), System.currentTimeMillis() + 50);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVelocity(PlayerVelocityEvent event) {
        UUID victimId = event.getPlayer().getUniqueId();
        Long expiryTime = noKnockbackVictims.get(victimId);
        
        if (expiryTime != null) {
            // Check if still valid
            if (System.currentTimeMillis() < expiryTime) {
                // Cancel all knockback - set velocity to zero
                event.setVelocity(new Vector(0, 0, 0));
            }
            
            // Clean up expired entry
            if (System.currentTimeMillis() >= expiryTime) {
                noKnockbackVictims.remove(victimId);
            }
        }
    }
}
