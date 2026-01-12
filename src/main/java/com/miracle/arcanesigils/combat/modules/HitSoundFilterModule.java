package com.miracle.arcanesigils.combat.modules;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filters hit sounds from attacks that were cancelled due to:
 * - Damage immunity (hit delay)
 * - CPS limit
 * - Attack cooldown not ready
 * 
 * The client plays hit sounds immediately when clicking, but if the
 * server cancels the damage event, the sound shouldn't play.
 * This module uses ProtocolLib to intercept and cancel those sound packets.
 */
public class HitSoundFilterModule extends AbstractCombatModule implements Listener {

    private ProtocolManager protocolManager;
    private PacketAdapter packetListener;

    // Track hits that were cancelled (attacker + victim pairs)
    private final Map<UUID, Long> cancelledAttackers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cancelledVictims = new ConcurrentHashMap<>();
    private static final long CANCEL_WINDOW_MS = 100; // 100ms window to catch the sound packet

    // Hit sounds to intercept - using partial matching now
    private static final Set<String> HIT_SOUND_PATTERNS = Set.of(
        "attack",
        "hurt",
        "hit"
    );

    public HitSoundFilterModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "hit-sound-filter";
    }

    @Override
    public String getDisplayName() {
        return "Hit Sound Filter";
    }

    @Override
    public void onEnable() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            plugin.getLogger().warning("[HitSoundFilter] ProtocolLib not found - hit sound filtering disabled");
            return;
        }

        protocolManager = ProtocolLibrary.getProtocolManager();
        registerPacketListener();
        plugin.getLogger().info("[HitSoundFilter] Enabled - filtering sounds from cancelled hits");
    }

    @Override
    public void onDisable() {
        if (packetListener != null && protocolManager != null) {
            protocolManager.removePacketListener(packetListener);
            packetListener = null;
        }
        cancelledAttackers.clear();
        cancelledVictims.clear();
    }

    /**
     * Register a hit as cancelled so we can filter ALL packets related to it.
     * Called by DamageImmunityModule when damage is cancelled due to immunity.
     */
    public void registerCancelledHit(UUID attackerUuid, UUID victimUuid) {
        long now = System.currentTimeMillis();
        cancelledAttackers.put(attackerUuid, now);
        cancelledVictims.put(victimUuid, now);
    }

    /**
     * Check if this entity was recently involved in a cancelled hit.
     */
    private boolean wasRecentlyCancelled(UUID uuid) {
        long now = System.currentTimeMillis();
        
        // Check if entity was victim
        Long victimTime = cancelledVictims.get(uuid);
        if (victimTime != null) {
            if (now - victimTime <= CANCEL_WINDOW_MS) {
                return true;
            } else {
                cancelledVictims.remove(uuid);
            }
        }
        
        // Check if entity was attacker
        Long attackerTime = cancelledAttackers.get(uuid);
        if (attackerTime != null) {
            if (now - attackerTime <= CANCEL_WINDOW_MS) {
                return true;
            } else {
                cancelledAttackers.remove(uuid);
            }
        }
        
        return false;
    }

    private void registerPacketListener() {
        packetListener = new PacketAdapter(
            plugin,
            ListenerPriority.NORMAL,
            PacketType.Play.Server.NAMED_SOUND_EFFECT,
            PacketType.Play.Server.ENTITY_STATUS,
            PacketType.Play.Server.HURT_ANIMATION
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isEnabled()) return;

                try {
                    PacketType type = event.getPacketType();

                    // Intercept entity damage animations and hurt status
                    if (type == PacketType.Play.Server.ENTITY_STATUS || type == PacketType.Play.Server.HURT_ANIMATION) {
                        // Get entity ID from packet
                        int entityId = event.getPacket().getIntegers().read(0);
                        
                        // Find entity by ID
                        Player player = event.getPlayer();
                        for (Entity nearby : player.getWorld().getEntities()) {
                            if (nearby.getEntityId() == entityId && wasRecentlyCancelled(nearby.getUniqueId())) {
                                event.setCancelled(true);
                                cancelledAttackers.remove(nearby.getUniqueId());
                                cancelledVictims.remove(nearby.getUniqueId());
                                return;
                            }
                        }
                    }

                    // Intercept hit sounds
                    if (type == PacketType.Play.Server.NAMED_SOUND_EFFECT) {
                        String soundKey = event.getPacket().getSoundEffects().read(0).getKey().toString();

                        // Check if this is a hit sound using pattern matching
                        boolean isHitSound = HIT_SOUND_PATTERNS.stream().anyMatch(soundKey::contains);
                        if (!isHitSound) return;

                        // Get the entity the sound is playing for
                        // Sound packets don't directly reference entities, but we can check
                        // if ANY entity near the player had a recent cancelled hit
                        Player player = event.getPlayer();
                        
                        // Check nearby entities for recently cancelled hits
                        // Check if the player (either as attacker or victim) was involved in a cancelled hit
                        if (wasRecentlyCancelled(player.getUniqueId())) {
                            event.setCancelled(true);
                            cancelledAttackers.remove(player.getUniqueId());
                            cancelledVictims.remove(player.getUniqueId());
                        }
                    }

                } catch (Exception e) {
                    // Silent fail - packet structure might differ
                }
            }
        };

        protocolManager.addPacketListener(packetListener);
    }

    @Override
    public void applyToPlayer(Player player) {
        // No per-player setup needed
    }

    @Override
    public void removeFromPlayer(Player player) {
        // No per-player cleanup needed
    }

    /**
     * Clean up old cancelled hit records periodically.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        cancelledAttackers.entrySet().removeIf(entry ->
            (now - entry.getValue()) > CANCEL_WINDOW_MS * 2
        );
        cancelledVictims.entrySet().removeIf(entry ->
            (now - entry.getValue()) > CANCEL_WINDOW_MS * 2
        );
    }
}
