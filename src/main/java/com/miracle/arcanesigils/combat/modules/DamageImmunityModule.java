package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements 1.8-style damage immunity (hit delay).
 * 
 * In 1.8, after taking damage, entities are immune for 10 ticks (0.5 seconds).
 * During this time:
 * - Equal or lower damage is completely ignored
 * - Higher damage only applies the DIFFERENCE
 * 
 * This prevents insta-kills from rapid clicking and creates the timing aspect
 * of 1.8 PvP combat.
 */
public class DamageImmunityModule extends AbstractCombatModule implements Listener {

    private static final int DEFAULT_IMMUNITY_TICKS = 10; // 0.5 seconds
    private static final long TICK_MS = 50L; // 1 tick = 50ms

    // Track immunity state per entity
    private final Map<UUID, ImmunityState> immunityStates = new ConcurrentHashMap<>();
    // Cache entity ID -> UUID mapping for async packet listener (avoid Bukkit API calls)
    private int taskId = -1;
    private final Map<Integer, UUID> entityIdCache = new ConcurrentHashMap<>();

    private int immunityTicks = DEFAULT_IMMUNITY_TICKS;
    private ProtocolManager protocolManager;
    private PacketAdapter packetListener;

    public DamageImmunityModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "damage-immunity";
    }

    @Override
    public String getDisplayName() {
        return "Damage Immunity";
    }

    @Override
    public void onEnable() {
        // Populate entity ID cache every 2 seconds (lightweight)
        taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            entityIdCache.clear();
            for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                for (org.bukkit.entity.Entity e : p.getNearbyEntities(32, 32, 32)) {
                    entityIdCache.put(e.getEntityId(), e.getUniqueId());
                }
            }
        }, 0L, 40L);  // Every 2 seconds
        
        // Enable packet interception to cancel attacks BEFORE client plays sound
        if (plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            protocolManager = ProtocolLibrary.getProtocolManager();
            registerPacketInterceptor();
        }
    }

    @Override
    public void onDisable() {
        // Cancel the scheduled task
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        
        // Remove packet listener
        if (packetListener != null && protocolManager != null) {
            protocolManager.removePacketListener(packetListener);
            packetListener = null;
        }
    }
    
    private void registerPacketInterceptor() {
        packetListener = new PacketAdapter(
            plugin,
            com.comphenix.protocol.events.ListenerPriority.HIGHEST,
            PacketType.Play.Client.USE_ENTITY
        ) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (!isEnabled()) return;
                
                try {
                    // Get the entity ID being attacked
                    int entityId = event.getPacket().getIntegers().read(0);
                    
                    // Look up UUID from cache (no Bukkit API calls - safe in async context)
                    UUID targetUuid = entityIdCache.get(entityId);
                    if (targetUuid != null) {
                        ImmunityState state = immunityStates.get(targetUuid);
                        if (state != null && isImmune(state, System.currentTimeMillis())) {
                            // Entity is immune - CANCEL the packet so hit never registers
                            event.setCancelled(true);
                        }
                    }
                } catch (Exception e) {
                    // Ignore packet errors
                }
            }
        };
        protocolManager.addPacketListener(packetListener);
    }

    @Override
    public void applyToPlayer(Player player) {
        // No attribute changes needed
    }

    @Override
    public void removeFromPlayer(Player player) {
        immunityStates.remove(player.getUniqueId());
    }

    @Override
    public void reload() {
        super.reload();
        immunityTicks = config.getDamageImmunityTicks();
    }

    /**
     * Intercept damage events to implement 1.8-style immunity.
     * Priority LOWEST to run before other damage modifications.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        UUID uuid = victim.getUniqueId();
        long now = System.currentTimeMillis();
        // CRITICAL: Use BASE damage, not final damage (matches 1.8 behavior)
        // In 1.8, immunity is checked BEFORE armor/protection/resistance calculations
        double incomingDamage = event.getDamage();

        // DEBUG: Log all damage events
        if (victim instanceof Player p) {
            plugin.getLogger().info(String.format("[DamageImmunity] %s taking %.2f base damage (%.2f final)",
                p.getName(), incomingDamage, event.getFinalDamage()));
        }

        ImmunityState state = immunityStates.get(uuid);

        // Check if entity is still immune from previous hit
        if (state != null && isImmune(state, now)) {
            // Entity is immune - check damage comparison
            if (incomingDamage <= state.lastDamage) {
                // Equal or lower damage - CANCEL completely (no KB, nothing)
                // 1.8 behavior: immune = hit doesn't register at all
                
                if (victim instanceof Player p) {
                    plugin.getLogger().info(String.format("[DamageImmunity] %s BLOCKED %.2f damage (immune to %.2f)",
                        p.getName(), incomingDamage, state.lastDamage));
                }
                
                // IMPORTANT: Set noDamageTicks HIGH so client knows entity is immune
                victim.setNoDamageTicks(immunityTicks);
                victim.setMaximumNoDamageTicks(immunityTicks);
                
                
                // Notify sound filter to block hit sound
                if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntityEvent) {
                    org.bukkit.entity.Player attacker = getAttacker(byEntityEvent);
                    if (attacker != null) {
                        notifyHitCancelled(attacker, victim);
                    }
                }
                event.setCancelled(true);
                return;
            } else {
                // Higher damage - apply only the DIFFERENCE
                double difference = incomingDamage - state.lastDamage;
                event.setDamage(difference);
                
                if (victim instanceof Player p) {
                    plugin.getLogger().info(String.format("[DamageImmunity] %s REDUCED %.2f to %.2f (diff from %.2f)",
                        p.getName(), incomingDamage, difference, state.lastDamage));
                }
                
                // Keep noDamageTicks high during immunity window
                victim.setNoDamageTicks(immunityTicks);
                victim.setMaximumNoDamageTicks(immunityTicks);
                
                // Update immunity state with new higher damage
                state.lastDamage = incomingDamage;
        
        // Cache entity ID for packet interception
        entityIdCache.put(victim.getEntityId(), uuid);
                state.lastHitTime = now;
                return;
            }
        }

        // Not immune or no previous state - record this hit and set vanilla immunity
        immunityStates.put(uuid, new ImmunityState(incomingDamage, now));
        
        if (victim instanceof Player p) {
            plugin.getLogger().info(String.format("[DamageImmunity] %s NEW IMMUNITY STATE: %.2f damage",
                p.getName(), incomingDamage));
        }
        
        // Set noDamageTicks so the CLIENT knows the entity is immune
        victim.setNoDamageTicks(immunityTicks);
        victim.setMaximumNoDamageTicks(immunityTicks);
        
        // Cache entity ID -> UUID for packet interception
        entityIdCache.put(victim.getEntityId(), uuid);
    }

    /**
     * Check if an entity is currently immune based on their immunity state.
     */
    private boolean isImmune(ImmunityState state, long currentTime) {
        long immunityDurationMs = immunityTicks * TICK_MS;
        return (currentTime - state.lastHitTime) < immunityDurationMs;
    }


    /**
     * Get the attacking player from a damage event.
     * Handles direct attacks and projectiles.
     */
    private org.bukkit.entity.Player getAttacker(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof org.bukkit.entity.Player player) {
            return player;
        }

        // Handle projectiles
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof org.bukkit.entity.Player shooter) {
                return shooter;
            }
        }

        return null;
    }
    /**
     * Clean up old immunity states periodically.
     * Called by the manager or on player quit.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        long immunityDurationMs = immunityTicks * TICK_MS;
        
        immunityStates.entrySet().removeIf(entry -> 
            (now - entry.getValue().lastHitTime) > immunityDurationMs * 2
        );
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("immunity-ticks")
                .displayName("Immunity Duration")
                .description("Ticks of immunity after taking damage (10 = 0.5s)")
                .intValue(config::getDamageImmunityTicks, config::setDamageImmunityTicks)
                .range(0, 20)
                .step(1)
                .build()
        );
    }

    /**
     * Tracks the immunity state of an entity.
     */
    private static class ImmunityState {
        double lastDamage;
        long lastHitTime;

        ImmunityState(double damage, long time) {
            this.lastDamage = damage;
            this.lastHitTime = time;
        }
    }
}
