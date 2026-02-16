package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements TRUE Kohi/Minemen client-side knockback using ProtocolLib.
 * 
 * This replicates the SpigotX NMS approach:
 * 1. Save victim's server-side velocity before damage
 * 2. Let knockback calculation run (adds to velocity)
 * 3. Send velocity packet to CLIENT with new velocity
 * 4. RESTORE original velocity on SERVER (no server-side knockback)
 * 
 * Result: Client sees knockback, but server physics unchanged = smooth, no lag
 */
public class ClientSideKnockbackModule extends AbstractCombatModule implements Listener {

    private final Map<UUID, Vector> savedVelocities = new ConcurrentHashMap<>();
    private PacketAdapter velocityInterceptor;

    public ClientSideKnockbackModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "client-side-knockback";
    }

    @Override
    public String getDisplayName() {
        return "Client-Side Knockback";
    }

    @Override
    public void onEnable() {
        // Intercept velocity packets and restore server velocity after sending to client
        velocityInterceptor = new PacketAdapter(plugin, PacketType.Play.Server.ENTITY_VELOCITY) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isEnabled()) return;
                
                try {
                    PacketContainer packet = event.getPacket();
                    int entityId = packet.getIntegers().read(0);
                    
                    // Check if this is a player we're tracking
                    Player receiver = event.getPlayer();
                    for (Player player : receiver.getWorld().getPlayers()) {
                        if (player.getEntityId() == entityId) {
                            Vector saved = savedVelocities.remove(player.getUniqueId());
                            if (saved != null) {
                                // Let packet send to client with knockback velocity
                                // But immediately restore server-side velocity after
                                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                                    player.setVelocity(saved);
                                });
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    // Silently fail - packet structure might differ
                }
            }
        };
        
        ProtocolLibrary.getProtocolManager().addPacketListener(velocityInterceptor);
        plugin.getLogger().info("[ClientSideKnockback] Enabled - Kohi/Minemen client-side knockback active");
    }

    @Override
    public void onDisable() {
        if (velocityInterceptor != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(velocityInterceptor);
            velocityInterceptor = null;
        }
        savedVelocities.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageEarliest(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        
        // Save victim velocity BEFORE knockback is applied
        if (event.getEntity() instanceof Player victim) {
            savedVelocities.put(victim.getUniqueId(), victim.getVelocity().clone());
        }
    }

    @Override
    public void applyToPlayer(Player player) {}

    @Override
    public void removeFromPlayer(Player player) {
        savedVelocities.remove(player.getUniqueId());
    }
}
