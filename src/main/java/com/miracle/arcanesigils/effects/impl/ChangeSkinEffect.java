package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.effects.SkinChangeManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Changes a player's skin temporarily using ProtocolLib packets.
 * Perfect for curse effects like Pharaoh's Curse (sand/mummy skin).
 *
 * Format: CHANGE_SKIN:skin_name:duration @Target
 *
 * Examples:
 *   CHANGE_SKIN:Notch:10 @Victim         - Change victim's skin to Notch for 10 seconds
 *   CHANGE_SKIN:skin=SandMummy:duration=15 @Self  - Key-value format
 *   CHANGE_SKIN:SandMummy @Victim        - Default 10 second duration
 *
 * Parameters:
 *   - skin (required): Username or UUID to copy skin from
 *   - duration (optional): Duration in seconds (default: 10)
 */
public class ChangeSkinEffect extends AbstractEffect {

    private static final int DEFAULT_DURATION = 10;

    public ChangeSkinEffect() {
        super("CHANGE_SKIN", "Temporarily change a player's skin appearance");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = new EffectParams(id);

        // Remove target selector from string
        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();

        // Parse target selector
        if (effectString.contains("@")) {
            String[] spaceParts = effectString.split("\\s+");
            for (String part : spaceParts) {
                if (part.startsWith("@")) {
                    params.setTarget(part);
                    break;
                }
            }
        }

        // Parse CHANGE_SKIN:skin:duration format
        String[] parts = cleanedString.split(":");
        params.setDuration(DEFAULT_DURATION);

        int positionalIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];

            if (part.contains("=")) {
                // Key=value format
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];

                    switch (key) {
                        case "skin", "name", "username", "uuid" -> params.set("skin", value);
                        case "duration", "time" -> params.setDuration(parseInt(value, DEFAULT_DURATION));
                    }
                }
            } else {
                // Positional format
                positionalIndex++;
                if (positionalIndex == 1) {
                    // First positional param is skin name
                    params.set("skin", part);
                } else if (positionalIndex == 2) {
                    // Second is duration
                    params.setDuration(parseInt(part, DEFAULT_DURATION));
                }
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        LivingEntity target = getTarget(context);

        // Only works on players
        if (!(target instanceof Player targetPlayer)) {
            debug("CHANGE_SKIN effect requires a player target, got: " +
                (target != null ? target.getClass().getSimpleName() : "null"));
            return false;
        }

        EffectParams params = context.getParams();
        if (params == null) {
            debug("CHANGE_SKIN: No params provided");
            return false;
        }

        // Get skin source (required)
        String skinSource = params.getString("skin", null);
        if (skinSource == null || skinSource.isEmpty()) {
            debug("CHANGE_SKIN: No skin source specified");
            return false;
        }

        // Get duration
        int duration = params.getDuration() > 0 ? params.getDuration() : DEFAULT_DURATION;

        // Get the skin change manager
        ArmorSetsPlugin plugin = getPlugin();
        SkinChangeManager skinManager = plugin.getSkinChangeManager();

        if (skinManager == null) {
            debug("SkinChangeManager not available");
            return false;
        }

        // Apply the skin change
        skinManager.changeSkin(targetPlayer, skinSource, duration);

        debug("Changed " + targetPlayer.getName() + "'s skin to " + skinSource + " for " + duration + " seconds");
        return true;
    }
}
