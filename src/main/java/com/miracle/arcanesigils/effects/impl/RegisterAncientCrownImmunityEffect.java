package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.interception.AncientCrownImmunityInterceptor;
import com.miracle.arcanesigils.interception.EffectInterceptor;
import com.miracle.arcanesigils.interception.InterceptionManager;
import org.bukkit.entity.Player;

/**
 * Registers the Ancient Crown immunity interceptor for a player.
 * Used with EFFECT_STATIC signal to maintain interceptor while helmet is equipped.
 * Prevents duplicate registration by checking if interceptor already exists.
 */
public class RegisterAncientCrownImmunityEffect extends AbstractEffect {

    public RegisterAncientCrownImmunityEffect() {
        super("REGISTER_ANCIENT_CROWN_IMMUNITY", "Registers Ancient Crown passive immunity");
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            debug("RegisterAncientCrownImmunity requires a player");
            return false;
        }

        // Get tier scaling config and tier from context metadata
        com.miracle.arcanesigils.tier.TierScalingConfig tierConfig = context.getMetadata("tierScalingConfig", null);
        Integer sigilTier = context.getMetadata("sourceSigilTier", null);

        // Resolve immunity_percent from tier config
        double immunityPercent = 20.0; // default for T1

        // Check if param is a placeholder (contains { })
        Object paramValue = context.getParams().get("immunity_percent");
        if (paramValue != null && paramValue.toString().contains("{")) {
            // Extract placeholder name
            String placeholder = paramValue.toString().replace("{", "").replace("}", "");

            // Resolve from tier config if available
            if (tierConfig != null && tierConfig.hasParam(placeholder) && sigilTier != null) {
                immunityPercent = tierConfig.getParamValue(placeholder, sigilTier);
                debug(String.format("Resolved %s from tier config: %.1f%% for tier %d",
                      placeholder, immunityPercent, sigilTier));
            } else {
                debug(String.format("Failed to resolve %s: tierConfig=%s, sigilTier=%d",
                      placeholder, tierConfig != null, sigilTier != null ? sigilTier : -1));
            }
        } else if (paramValue != null) {
            // Direct value (non-placeholder)
            immunityPercent = context.getParams().getDouble("immunity_percent", 20.0);
        }

        ArmorSetsPlugin plugin = getPlugin();
        InterceptionManager interceptionManager = plugin.getInterceptionManager();

        if (interceptionManager == null) {
            debug("InterceptionManager not available");
            return false;
        }

        // Check if Ancient Crown interceptor already registered (prevent duplication)
        boolean alreadyRegistered = false;
        for (EffectInterceptor interceptor : interceptionManager.getInterceptors(player)) {
            if (interceptor instanceof AncientCrownImmunityInterceptor existing) {
                alreadyRegistered = true;

                // Update immunity percent if tier changed
                double currentPercent = existing.getImmunityPercent() * 100.0;
                if (Math.abs(currentPercent - immunityPercent) > 0.01) {
                    existing.setImmunityPercent(immunityPercent);
                    debug("Updated Ancient Crown immunity for " + player.getName() +
                          " from " + currentPercent + "% to " + immunityPercent + "%");
                }
                break;
            }
        }

        if (!alreadyRegistered) {
            AncientCrownImmunityInterceptor interceptor =
                new AncientCrownImmunityInterceptor(player, immunityPercent);
            interceptionManager.registerInterceptor(player, interceptor);

            debug("Registered Ancient Crown immunity for " + player.getName() +
                  " (" + immunityPercent + "% immunity)");
        }

        return true;
    }
}
