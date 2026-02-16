package com.miracle.arcanesigils.interception;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.events.SignalHandler;
import com.miracle.arcanesigils.events.SignalType;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;

/**
 * Listens for potion effects being applied and fires POTION_EFFECT_APPLY signal.
 * Allows sigils like Ancient Crown to intercept and modify effects through flows.
 */
public class PotionEffectInterceptionListener implements Listener {

    private final ArmorSetsPlugin plugin;
    private final SignalHandler signalHandler;
    private final InterceptionManager interceptionManager;

    // Prevent infinite recursion when re-applying modified effects
    private static final ThreadLocal<Boolean> APPLYING_MODIFIED_EFFECT = ThreadLocal.withInitial(() -> false);

    public PotionEffectInterceptionListener(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.signalHandler = plugin.getSignalHandler();
        this.interceptionManager = plugin.getInterceptionManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPotionEffectApply(EntityPotionEffectEvent event) {
        // Skip if we're currently re-applying a modified effect (prevent infinite loop)
        if (APPLYING_MODIFIED_EFFECT.get()) {
            return;
        }

        // Only intercept effects being applied to players
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Intercept when effect is being added OR changed (reapplied/modified)
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED &&
            event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }

        // Get the effect being applied
        PotionEffect effect = event.getNewEffect();
        if (effect == null) {
            return;
        }

        // Create interception event
        InterceptionEvent intercept = new InterceptionEvent(
            InterceptionEvent.Type.POTION_EFFECT,
            player,
            null, // Could track source if needed
            effect.getType(),
            effect.getAmplifier(),
            effect.getDuration()
        );

        // CRITICAL: Fire interceptors FIRST (e.g., Ancient Crown immunity)
        // This allows registered interceptors to cancel/modify the effect before flows run
        if (interceptionManager != null) {
            interceptionManager.fireIntercept(intercept);

            // Check if interceptor cancelled the effect
            if (intercept.isCancelled()) {
                event.setCancelled(true);
                return;
            }
        }

        // Create context and pass interception event
        EffectContext context = EffectContext.builder(player, SignalType.POTION_EFFECT_APPLY)
            .event(event)
            .build();
        context.setInterceptionEvent(intercept);
        context.setCurrentPotionEffect(effect.getType());

        // Fire signal to execute flows
        signalHandler.processArmorEffects(player, SignalType.POTION_EFFECT_APPLY, context);

        // Check if flows modified the event
        if (intercept.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        // If modified, cancel original and apply modified version
        if (intercept.wasModified()) {
            event.setCancelled(true);

            // Set flag to prevent re-interception
            APPLYING_MODIFIED_EFFECT.set(true);
            try {
                // Apply the modified effect
                player.addPotionEffect(new PotionEffect(
                    intercept.getPotionType(),
                    intercept.getDuration(),
                    intercept.getAmplifier(),
                    effect.isAmbient(),
                    effect.hasParticles(),
                    effect.hasIcon()
                ));
            } finally {
                // Always clear flag
                APPLYING_MODIFIED_EFFECT.set(false);
            }
        }
    }
}
