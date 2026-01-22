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

        // Get immunity percentage from params
        double immunityPercent = context.getParams().getDouble("immunity_percent", 20.0);

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

                // Optional: Update immunity percent if tier changed
                // (Would require setter in AncientCrownImmunityInterceptor)
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
