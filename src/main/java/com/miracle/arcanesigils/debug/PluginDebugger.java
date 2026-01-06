package com.miracle.arcanesigils.debug;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.util.logging.Logger;

/**
 * Debug utility to identify which external plugins are:
 * 1. Cancelling damage events
 * 2. Modifying saturation/food levels
 *
 * Enable via /as debug damage or /as debug saturation
 */
public class PluginDebugger implements Listener {

    private final Plugin plugin;
    private final Logger logger;

    private boolean debugDamage = false;
    private boolean debugSaturation = false;

    public PluginDebugger(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Lists all plugins listening to EntityDamageByEntityEvent
     */
    public void listDamageListeners() {
        logger.info("========== DAMAGE EVENT LISTENERS ==========");
        HandlerList handlers = EntityDamageByEntityEvent.getHandlerList();

        for (RegisteredListener listener : handlers.getRegisteredListeners()) {
            Plugin owningPlugin = listener.getPlugin();
            String priority = listener.getPriority().name();
            String listenerClass = listener.getListener().getClass().getName();

            logger.info(String.format("[%s] %s - %s (ignoreCancelled=%s)",
                priority,
                owningPlugin.getName(),
                listenerClass,
                listener.isIgnoringCancelled()
            ));
        }
        logger.info("==============================================");
    }

    /**
     * Lists all plugins listening to FoodLevelChangeEvent
     */
    public void listSaturationListeners() {
        logger.info("========== FOOD/SATURATION EVENT LISTENERS ==========");
        HandlerList handlers = FoodLevelChangeEvent.getHandlerList();

        for (RegisteredListener listener : handlers.getRegisteredListeners()) {
            Plugin owningPlugin = listener.getPlugin();
            String priority = listener.getPriority().name();
            String listenerClass = listener.getListener().getClass().getName();

            logger.info(String.format("[%s] %s - %s (ignoreCancelled=%s)",
                priority,
                owningPlugin.getName(),
                listenerClass,
                listener.isIgnoringCancelled()
            ));
        }
        logger.info("======================================================");
    }

    /**
     * LOW priority - runs after LOWEST
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onDamageLow(EntityDamageByEntityEvent event) {
        if (!debugDamage) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (event.isCancelled()) {
            logger.warning("[DAMAGE DEBUG] Event CANCELLED at LOW priority!");
            logger.warning("  Attacker: " + attacker.getName() + " -> Victim: " + victim.getName());
            logger.warning("  >>> Cancelled by something at LOWEST or LOW priority <<<");
            logger.warning("  Suspects: Essentials, AdvancedChat SpawnArrowsEffect, ArcaneSigils SweepAttack/CPS");
        }
    }

    /**
     * NORMAL priority - runs after LOW
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onDamageNormal(EntityDamageByEntityEvent event) {
        if (!debugDamage) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (event.isCancelled()) {
            // Check if it was already cancelled at LOW
            // If this is the first time we see it cancelled, it was cancelled between LOW and NORMAL
            logger.warning("[DAMAGE DEBUG] Event CANCELLED at NORMAL priority!");
            logger.warning("  Attacker: " + attacker.getName() + " -> Victim: " + victim.getName());
        }
    }

    /**
     * HIGH priority - runs after NORMAL
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageHigh(EntityDamageByEntityEvent event) {
        if (!debugDamage) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (event.isCancelled()) {
            logger.warning("[DAMAGE DEBUG] Event CANCELLED at HIGH priority!");
            logger.warning("  Attacker: " + attacker.getName() + " -> Victim: " + victim.getName());
        }
    }

    /**
     * MONITOR priority - runs LAST, after all other handlers
     * If event is cancelled here, we know some earlier handler cancelled it
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageMonitor(EntityDamageByEntityEvent event) {
        if (!debugDamage) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (event.isCancelled()) {
            logger.warning("========== DAMAGE CANCELLED - INVESTIGATING ==========");
            logger.warning("Attacker: " + attacker.getName() + " -> Victim: " + victim.getName());
            logger.warning("Damage: " + event.getDamage() + " | Cause: " + event.getCause());
            logger.warning("");
            logger.warning("Registered listeners (in priority order):");

            HandlerList handlers = EntityDamageByEntityEvent.getHandlerList();
            for (RegisteredListener listener : handlers.getRegisteredListeners()) {
                Plugin owningPlugin = listener.getPlugin();
                String priority = listener.getPriority().name();
                String listenerClass = listener.getListener().getClass().getSimpleName();

                // Highlight non-ArcaneSigils plugins
                String marker = owningPlugin.getName().equals("ArcaneSigils") ? "" : " <-- SUSPECT";

                logger.warning(String.format("  [%s] %s.%s%s",
                    priority,
                    owningPlugin.getName(),
                    listenerClass,
                    marker
                ));
            }
            logger.warning("");
            logger.warning("TIP: The cancelling plugin is likely one marked SUSPECT above,");
            logger.warning("     running at LOW, NORMAL, or HIGH priority (before MONITOR).");
            logger.warning("=====================================================");
        }
    }

    /**
     * Track saturation changes - runs at multiple priorities to catch the modifier
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodChangeLowest(FoodLevelChangeEvent event) {
        if (!debugSaturation) return;
        if (!(event.getEntity() instanceof Player player)) return;

        int oldFood = player.getFoodLevel();
        int newFood = event.getFoodLevel();
        float saturation = player.getSaturation();

        // Only log if saturation is suspiciously high or food isn't decreasing
        if (saturation >= 20 || (oldFood > newFood && event.isCancelled())) {
            logger.info("[SATURATION DEBUG - LOWEST] Player: " + player.getName());
            logger.info("  Old Food: " + oldFood + " -> New Food: " + newFood);
            logger.info("  Saturation: " + saturation);
            logger.info("  Cancelled: " + event.isCancelled());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFoodChangeMonitor(FoodLevelChangeEvent event) {
        if (!debugSaturation) return;
        if (!(event.getEntity() instanceof Player player)) return;

        float saturation = player.getSaturation();

        // Log if saturation is max (20) - something is keeping it full
        if (saturation >= 20) {
            logger.warning("========== INFINITE SATURATION DETECTED ==========");
            logger.warning("Player: " + player.getName());
            logger.warning("Saturation: " + saturation + " (MAX)");
            logger.warning("Food Level: " + player.getFoodLevel());
            logger.warning("Event cancelled: " + event.isCancelled());
            logger.warning("");
            logger.warning("Registered FoodLevelChangeEvent listeners:");

            HandlerList handlers = FoodLevelChangeEvent.getHandlerList();
            for (RegisteredListener listener : handlers.getRegisteredListeners()) {
                Plugin owningPlugin = listener.getPlugin();
                String priority = listener.getPriority().name();
                String listenerClass = listener.getListener().getClass().getSimpleName();

                String marker = owningPlugin.getName().equals("ArcaneSigils") ? "" : " <-- SUSPECT";

                logger.warning(String.format("  [%s] %s.%s%s",
                    priority,
                    owningPlugin.getName(),
                    listenerClass,
                    marker
                ));
            }
            logger.warning("==================================================");
        }
    }

    /**
     * Periodic task to check player saturation levels directly
     */
    public void startSaturationMonitor() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!debugSaturation) return;

            for (Player player : Bukkit.getOnlinePlayers()) {
                float saturation = player.getSaturation();
                if (saturation >= 19) {
                    logger.info("[SATURATION CHECK] " + player.getName() +
                        " has saturation " + saturation + " (suspicious if not recently eaten)");
                }
            }
        }, 200L, 200L); // Every 10 seconds
    }

    public void setDebugDamage(boolean enabled) {
        this.debugDamage = enabled;
        logger.info("Damage debugging " + (enabled ? "ENABLED" : "DISABLED"));
        if (enabled) {
            listDamageListeners();
        }
    }

    public void setDebugSaturation(boolean enabled) {
        this.debugSaturation = enabled;
        logger.info("Saturation debugging " + (enabled ? "ENABLED" : "DISABLED"));
        if (enabled) {
            listSaturationListeners();
        }
    }

    public boolean isDebugDamage() {
        return debugDamage;
    }

    public boolean isDebugSaturation() {
        return debugSaturation;
    }
}
