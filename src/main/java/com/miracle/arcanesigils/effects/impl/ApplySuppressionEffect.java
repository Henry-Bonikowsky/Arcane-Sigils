package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.interception.CleopatraSuppressionInterceptor;
import com.miracle.arcanesigils.interception.InterceptionManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Apply a suppression mark that blocks defensive buffs from being reapplied.
 * Used by Cleopatra ability to prevent targets from regaining protection.
 * 
 * Format: APPLY_SUPPRESSION @Target
 * 
 * Params (YAML):
 *   duration: 3              # Duration in seconds (2-5s based on tier)
 *   target: @Victim          # Who to suppress
 * 
 * Blocks during duration:
 * - RESISTANCE potion effects
 * - REGENERATION potion effects
 * - Damage reduction attribute modifiers
 * 
 * Example:
 *   APPLY_SUPPRESSION @Victim with duration=4 means suppress for 4 seconds
 */
public class ApplySuppressionEffect extends AbstractEffect {
    
    public ApplySuppressionEffect() {
        super("APPLY_SUPPRESSION", "Apply suppression that blocks defensive buffs");
    }
    
    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);
        
        // Defaults
        params.setDuration(3); // 3 seconds default
        
        // Default to targeting victim
        if (params.getTarget() == null) {
            params.setTarget("@Victim");
        }
        
        return params;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) {
            debug("APPLY_SUPPRESSION effect requires params");
            return false;
        }
        
        LivingEntity target = getTarget(context);
        if (target == null) {
            debug("APPLY_SUPPRESSION requires a target");
            return false;
        }
        
        // Only works on players
        if (!(target instanceof Player player)) {
            debug("APPLY_SUPPRESSION only works on players");
            return false;
        }
        
        // Get duration
        int durationSeconds = params.getDuration() > 0 ? params.getDuration() : 3;
        durationSeconds = Math.max(2, Math.min(5, durationSeconds)); // Clamp 2-5 seconds
        
        // Get the interception manager
        ArmorSetsPlugin plugin = getPlugin();
        InterceptionManager interceptionManager = plugin.getInterceptionManager();
        
        if (interceptionManager == null) {
            debug("InterceptionManager not available");
            return false;
        }
        
        // Create the suppression interceptor
        CleopatraSuppressionInterceptor interceptor = new CleopatraSuppressionInterceptor(player);
        
        // Register it
        interceptionManager.registerInterceptor(player, interceptor);
        
        // Schedule removal after duration
        new BukkitRunnable() {
            @Override
            public void run() {
                interceptor.deactivate();
                interceptionManager.unregisterInterceptor(player, interceptor);
                
                if (player.isOnline() && player.isValid()) {
                    // Visual feedback on expiration
                    player.getWorld().spawnParticle(
                        Particle.CLOUD,
                        player.getLocation().add(0, 1, 0),
                        15,
                        0.4, 0.5, 0.4,
                        0.03
                    );
                    
                    // Sound - relief
                    player.getWorld().playSound(
                        player.getLocation(),
                        Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                        0.6f,
                        1.0f
                    );
                    
                    // Notification
                    player.sendMessage("§a§lSuppression Lifted!");
                }
                
                debug("Suppression expired on " + player.getName());
            }
        }.runTaskLater(plugin, durationSeconds * 20L);
        
        // Visual feedback - dark purple/black particles (oppressive)
        player.getWorld().spawnParticle(
            Particle.DUST,
            player.getLocation().add(0, 1, 0),
            40,
            0.5, 0.8, 0.5,
            0.1,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(25, 0, 51), 1.8f)
        );
        
        // Smoke particles - swirling around
        player.getWorld().spawnParticle(
            Particle.SQUID_INK,
            player.getLocation().add(0, 1, 0),
            20,
            0.4, 0.6, 0.4,
            0.05
        );
        
        // Sound effect - ominous suppression
        player.getWorld().playSound(
            player.getLocation(),
            Sound.ENTITY_WITHER_AMBIENT,
            0.8f,
            0.8f
        );
        
        // Notification
        player.sendMessage("§5§lSUPPRESSED! §7Defensive buffs blocked for " + durationSeconds + "s");
        
        debug(String.format("Applied suppression to %s for %d seconds", 
            player.getName(), durationSeconds));
        
        return true;
    }
}
