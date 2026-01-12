package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Enforces vanilla immunity by cancelling damage events when entity is immune.
 * This is the SIMPLEST approach - just like vanilla 1.8.
 * 
 * By cancelling early, we prevent:
 * - Damage
 * - Knockback  
 * - Sounds
 * - Animations
 * 
 * Exactly like MinemenClub's SpigotX does it.
 */
public class VanillaImmunityModule extends AbstractCombatModule implements Listener {

    public VanillaImmunityModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "vanilla-immunity";
    }

    @Override
    public String getDisplayName() {
        return "Vanilla Immunity";
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("[VanillaImmunity] Enabled - using pure vanilla immunity");
    }

    @Override
    public void onDisable() {
        // Nothing to cleanup
    }

    /**
     * Cancel ALL damage when entity is immune.
     * Priority LOWEST to run before everything else.
     * 
     * This replicates what Minecraft 1.8 does in EntityLiving.damageEntity():
     *   if (noDamageTicks > maxNoDamageTicks / 2.0F) return false;
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        // Check vanilla immunity
        int noDamageTicks = victim.getNoDamageTicks();
        int maxNoDamageTicks = victim.getMaximumNoDamageTicks();

        // 1.8 immunity check: if more than half of max immunity has passed, block the hit
        if (noDamageTicks > maxNoDamageTicks / 2) {
            event.setCancelled(true);
            
            // Notify sound filter if this was a player attack
            if (event instanceof EntityDamageByEntityEvent byEntityEvent) {
                Player attacker = getAttacker(byEntityEvent);
                if (attacker != null) {
                    notifyHitCancelled(attacker, victim);
                }
            }
            
            // DEBUG
            if (victim instanceof Player p) {
                plugin.getLogger().info(String.format("[VanillaImmunity] Blocked hit on %s (immune: %d/%d)",
                    p.getName(), noDamageTicks, maxNoDamageTicks));
            }
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
    public void applyToPlayer(Player player) {
        // Nothing to apply
    }

    @Override
    public void removeFromPlayer(Player player) {
        // Nothing to remove
    }

    @Override
    public java.util.List<ModuleParam> getConfigParams() {
        return java.util.List.of(
            ModuleParam.builder("immunity-ticks")
                .displayName("Immunity Duration")
                .description("Ticks of immunity after taking damage (10 = 0.5s, 20 = 1s)")
                .intValue(config::getVanillaImmunityTicks, config::setVanillaImmunityTicks)
                .range(0, 20)
                .step(1)
                .build()
        );
    }
}
