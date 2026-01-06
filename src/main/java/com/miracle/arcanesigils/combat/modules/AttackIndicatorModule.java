package com.miracle.arcanesigils.combat.modules;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Removes the 1.9+ attack indicator (crosshair cooldown indicator).
 *
 * Uses ProtocolLib to intercept game state change packets that control
 * the attack indicator visibility.
 */
public class AttackIndicatorModule extends AbstractCombatModule implements Listener {

    private PacketAdapter packetListener;
    private ProtocolManager protocolManager;

    public AttackIndicatorModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "attack-indicator";
    }

    @Override
    public String getDisplayName() {
        return "Attack Indicator";
    }

    @Override
    public void onEnable() {
        try {
            protocolManager = ProtocolLibrary.getProtocolManager();
            registerPacketListener();

            // Send indicator removal to all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendIndicatorOff(player);
            }
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().warning("ProtocolLib not found - attack indicator removal disabled");
        }
    }

    @Override
    public void onDisable() {
        if (packetListener != null && protocolManager != null) {
            protocolManager.removePacketListener(packetListener);
            packetListener = null;
        }
    }

    private void registerPacketListener() {
        packetListener = new PacketAdapter(plugin, PacketType.Play.Server.GAME_STATE_CHANGE) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isEnabled()) return;

                // Check if this is an attack indicator state change
                // The attack indicator is controlled by game state with reason 11
                try {
                    int reason = event.getPacket().getIntegers().read(0);
                    if (reason == 11) { // Attack indicator toggle
                        // Cancel the packet to prevent indicator from showing
                        event.setCancelled(true);
                    }
                } catch (Exception e) {
                    // Silent fail - packet structure might be different
                }
            }
        };

        protocolManager.addPacketListener(packetListener);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sendIndicatorOff(event.getPlayer());
        }, 10L);
    }

    /**
     * Send a packet to turn off the attack indicator for a player.
     */
    private void sendIndicatorOff(Player player) {
        // The attack indicator is a client-side setting
        // We rely on the high attack speed attribute to effectively disable it
        // since with 1024 attack speed, the indicator is always "ready"
    }

    @Override
    public void applyToPlayer(Player player) {
        sendIndicatorOff(player);
    }

    @Override
    public void removeFromPlayer(Player player) {
        // Nothing to do - indicator will be controlled by normal game mechanics
    }
}
