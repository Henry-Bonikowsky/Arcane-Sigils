package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import org.bukkit.entity.Player;

/**
 * Registers the player as having "no knockback mode" for a duration.
 * While active, the player's attacks will deal no knockback.
 * 
 * Format: REGISTER_NO_KNOCKBACK:duration
 * Example: REGISTER_NO_KNOCKBACK:6
 * 
 * Parameters:
 * - duration: Duration in seconds (default 6)
 */
public class RegisterNoKnockbackEffect extends AbstractEffect {

    public RegisterNoKnockbackEffect() {
        super("REGISTER_NO_KNOCKBACK", "Register player in no-knockback mode");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // Default duration: 6 seconds
        params.setValue(6.0);

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];

            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    if (key.equals("duration")) {
                        params.setValue(parseDouble(value, 6.0));
                    }
                }
            } else if (i == 1) {
                // Positional format - first param is duration
                try {
                    params.setValue(parseDouble(part, 6.0));
                } catch (NumberFormatException ignored) {}
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            debug("REGISTER_NO_KNOCKBACK requires a player");
            return false;
        }

        EffectParams params = context.getParams();
        int duration = 6; // Default
        
        if (params != null) {
            // Try getDuration() first (set from flow node params)
            if (params.getDuration() > 0) {
                duration = params.getDuration();
            } else {
                // Try params map
                duration = params.getInt("duration", 6);
            }
        }

        ArmorSetsPlugin plugin = (ArmorSetsPlugin) getPlugin();
        plugin.registerQuicksandActive(player.getUniqueId(), duration);

        debug(String.format("Registered %s for no-knockback mode for %d seconds",
            player.getName(), duration));

        return true;
    }
}
