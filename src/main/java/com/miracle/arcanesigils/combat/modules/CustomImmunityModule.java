package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import com.miracle.arcanesigils.utils.LogHelper;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomImmunityModule extends AbstractCombatModule implements Listener {

    private final Map<UUID, Long> lastHitTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentFailedAttackers = new ConcurrentHashMap<>();
    private PacketAdapter soundCanceller;

    public CustomImmunityModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "custom-immunity";
    }

    @Override
    public String getDisplayName() {
        return "Custom Immunity";
    }

    @Override
    public void onEnable() {
        // Cancel hit sounds for immune victims AND weak attack sounds for failed attackers
        soundCanceller = new PacketAdapter(plugin, 
            PacketType.Play.Server.NAMED_SOUND_EFFECT,
            PacketType.Play.Server.ENTITY_STATUS) {
            
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isEnabled()) return;
                
                try {
                    Player receiver = event.getPlayer();
                    
                    if (event.getPacketType() == PacketType.Play.Server.NAMED_SOUND_EFFECT) {
                        Sound sound = event.getPacket().getSoundEffects().read(0);
                        
                        // Cancel weak attack sounds for players who recently hit an immune target
                        if (sound == Sound.ENTITY_PLAYER_ATTACK_WEAK || sound == Sound.ENTITY_PLAYER_ATTACK_NODAMAGE) {
                            Long lastFailed = recentFailedAttackers.get(receiver.getUniqueId());
                            if (lastFailed != null && (System.currentTimeMillis() - lastFailed) < 100) {
                                event.setCancelled(true);
                                return;
                            }
                        }
                        
                        // Cancel hurt sounds for immune victims
                        if (isImmune(receiver)) {
                            if (sound == Sound.ENTITY_PLAYER_HURT || 
                                sound == Sound.ENTITY_PLAYER_HURT_DROWN ||
                                sound == Sound.ENTITY_PLAYER_HURT_ON_FIRE ||
                                sound == Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH ||
                                sound == Sound.ENTITY_PLAYER_HURT_FREEZE) {
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                    
                    if (event.getPacketType() == PacketType.Play.Server.ENTITY_STATUS) {
                        byte status = event.getPacket().getBytes().read(0);
                        
                        // Cancel weak attack animation for recent failed attackers
                        if (status == 33) {
                            Long lastFailed = recentFailedAttackers.get(receiver.getUniqueId());
                            if (lastFailed != null && (System.currentTimeMillis() - lastFailed) < 100) {
                                event.setCancelled(true);
                                return;
                            }
                        }
                        
                        // Cancel hurt animation for immune victims
                        if (status == 2 && isImmune(receiver)) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                } catch (Exception ignored) {}
            }
        };
        ProtocolLibrary.getProtocolManager().addPacketListener(soundCanceller);
        
        // Clean up old entries every second
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            recentFailedAttackers.entrySet().removeIf(e -> (now - e.getValue()) > 200);
        }, 20L, 20L);
        
        plugin.getLogger().info("[CustomImmunity] Enabled - tracking failed hits for sound cancellation");
    }

    @Override
    public void onDisable() {
        if (soundCanceller != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(soundCanceller);
            soundCanceller = null;
        }
        lastHitTime.clear();
        recentFailedAttackers.clear();
    }

    public boolean isImmune(Player player) {
        Long lastHit = lastHitTime.get(player.getUniqueId());
        if (lastHit == null) return false;
        
        int immunityTicks = config.getDamageImmunityTicks();
        long immunityMs = immunityTicks * 50;
        
        return (System.currentTimeMillis() - lastHit) < immunityMs;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        
        if (event.getEntity() instanceof Player victim) {
            // Track who attacked (for weak sound cancellation)
            Player attacker = null;
            if (event.getDamager() instanceof Player p) {
                attacker = p;
            } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj) {
                if (proj.getShooter() instanceof Player shooter) {
                    attacker = shooter;
                }
            }
            
            if (isImmune(victim)) {
                event.setCancelled(true);
                LogHelper.info("[Immunity] Cancelled damage to %s (immune)", victim.getName());

                // Mark this attacker as having recently failed a hit
                if (attacker != null) {
                    recentFailedAttackers.put(attacker.getUniqueId(), System.currentTimeMillis());
                }
                return;
            }
            
            lastHitTime.put(victim.getUniqueId(), System.currentTimeMillis());
        }
    }

    @Override
    public void applyToPlayer(Player player) {}

    @Override
    public void removeFromPlayer(Player player) {
        lastHitTime.remove(player.getUniqueId());
    }

    @Override
    public void reload() {
        super.reload();
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("immunity-ticks")
                .displayName("Immunity Ticks")
                .description("Hit immunity duration (10 = 0.5s)")
                .intValue(config::getDamageImmunityTicks, config::setDamageImmunityTicks)
                .range(0, 20)
                .step(1)
                .build()
        );
    }
}
