package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
 * Completely disables 1.9+ sweep attacks when using swords.
 * In 1.8, swords did not have an AOE sweep attack.
 * This prevents sweep damage, particles, sounds, and knockback issues.
 */
public class SweepAttackModule extends AbstractCombatModule implements Listener {

    private static final double DEFAULT_SWEEP_RATIO = 1.0;
    private PacketAdapter particleCanceller;
    private PacketAdapter soundCanceller;

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
        
        // Cancel sweep particle effects
        particleCanceller = new PacketAdapter(plugin, PacketType.Play.Server.WORLD_PARTICLES) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isEnabled()) return;
                try {
                    org.bukkit.entity.Player viewer = event.getPlayer();
                    if (viewer == null) return;
                    
                    // Get the particle wrapper
                    com.comphenix.protocol.wrappers.WrappedParticle<?> wrappedParticle = 
                        event.getPacket().getNewParticles().read(0);
                    
                    if (wrappedParticle != null) {
                        // Get the actual Bukkit particle type
                        org.bukkit.Particle particleType = wrappedParticle.getParticle();
                        
                        // Cancel SWEEP_ATTACK particle
                        if (particleType == org.bukkit.Particle.SWEEP_ATTACK) {
                            event.setCancelled(true);
                        }
                    }
                } catch (Exception ignored) {
                    // Silently ignore packet handling errors
                }
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(particleCanceller);
        
        // Cancel sweep attack sounds
        soundCanceller = new PacketAdapter(plugin, PacketType.Play.Server.NAMED_SOUND_EFFECT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isEnabled()) return;
                try {
                    Sound sound = event.getPacket().getSoundEffects().read(0);
                    if (sound == Sound.ENTITY_PLAYER_ATTACK_SWEEP) {
                        event.setCancelled(true);
                    }
                } catch (Exception ignored) {}
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(soundCanceller);
    }
    
    @Override
    public void onDisable() {
        if (particleCanceller != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(particleCanceller);
            particleCanceller = null;
        }
        if (soundCanceller != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(soundCanceller);
            soundCanceller = null;
        }
    }

    @Override
    public void applyToPlayer(Player player) {
        if (!isEnabled()) return;
        setSweepRatioZero(player);
    }

    @Override
    public void removeFromPlayer(Player player) {
        // Restore default sweep ratio
        AttributeInstance attr = player.getAttribute(Attribute.SWEEPING_DAMAGE_RATIO);
        if (attr != null) {
            attr.setBaseValue(DEFAULT_SWEEP_RATIO);
        }
    }

    private void setSweepRatioZero(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.SWEEPING_DAMAGE_RATIO);
        if (attr != null) {
            attr.setBaseValue(0.0);
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

    // Cancel at LOWEST priority - catch sweep attacks immediately
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSweepAttackLowest(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;

        // If this is a sweep attack, cancel it completely
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
            return;
        }

        // If attacker is holding sword, ensure sweep ratio stays at 0
        if (event.getDamager() instanceof Player attacker) {
            if (isHoldingSword(attacker)) {
                setSweepRatioZero(attacker);
            }
        }
    }

    // Backup cancellation at HIGHEST priority
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSweepAttackHighest(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        
        // Final sweep cancellation check
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
        }
    }
}
