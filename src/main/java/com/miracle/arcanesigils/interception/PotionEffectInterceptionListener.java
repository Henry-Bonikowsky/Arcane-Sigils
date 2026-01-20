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

    public PotionEffectInterceptionListener(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.signalHandler = plugin.getSignalHandler();
        LogHelper.info("[PotionInterception] ===== LISTENER INITIALIZED =====");
        LogHelper.info("[PotionInterception] SignalHandler: " + (signalHandler != null ? "OK" : "NULL"));
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPotionEffectApply(EntityPotionEffectEvent event) {
        LogHelper.info("[PotionInterception] ===== EVENT FIRED =====");
        LogHelper.info("[PotionInterception] Entity: " + event.getEntity().getType() + ", Action: " + event.getAction());

        // Only intercept effects being applied to players
        if (!(event.getEntity() instanceof Player player)) {
            LogHelper.info("[PotionInterception] Not a player, skipping");
            return;
        }

        // Intercept when effect is being added OR changed (reapplied/modified)
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED &&
            event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
            LogHelper.info("[PotionInterception] Action is " + event.getAction() + ", skipping");
            return;
        }

        // Get the effect being applied
        PotionEffect effect = event.getNewEffect();
        if (effect == null) {
            return;
        }

        LogHelper.debug("[PotionInterception] Player %s receiving effect: %s (amp %d, duration %d ticks)",
            player.getName(), effect.getType().getName(), effect.getAmplifier(), effect.getDuration());

        // Create interception event
        InterceptionEvent intercept = new InterceptionEvent(
            InterceptionEvent.Type.POTION_EFFECT,
            player,
            null, // Could track source if needed
            effect.getType(),
            effect.getAmplifier(),
            effect.getDuration()
        );

        // Create context and pass interception event
        EffectContext context = EffectContext.builder(player, SignalType.POTION_EFFECT_APPLY)
            .event(event)
            .build();
        context.setInterceptionEvent(intercept);
        context.setCurrentPotionEffect(effect.getType());

        LogHelper.debug("[PotionInterception] Firing POTION_EFFECT_APPLY signal...");

        // Fire signal to execute flows
        signalHandler.processArmorEffects(player, SignalType.POTION_EFFECT_APPLY, context);

        // Check if flows modified the event
        if (intercept.isCancelled()) {
            LogHelper.debug("[PotionInterception] Effect cancelled by flow");
            event.setCancelled(true);
            return;
        }

        // If modified, cancel original and apply modified version
        if (intercept.wasModified()) {
            LogHelper.debug("[PotionInterception] Effect modified by flow: %s (amp %d, duration %d)",
                intercept.getPotionType().getName(), intercept.getAmplifier(), intercept.getDuration());
            event.setCancelled(true);

            // Apply the modified effect
            player.addPotionEffect(new PotionEffect(
                intercept.getPotionType(),
                intercept.getDuration(),
                intercept.getAmplifier(),
                effect.isAmbient(),
                effect.hasParticles(),
                effect.hasIcon()
            ));
        } else {
            LogHelper.debug("[PotionInterception] Effect not modified, allowing normal application");
        }
    }
}
