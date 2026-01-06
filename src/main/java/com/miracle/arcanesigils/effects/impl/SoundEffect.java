package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import org.bukkit.Location;
import org.bukkit.Sound;

public class SoundEffect extends AbstractEffect {

    public SoundEffect() {
        super("SOUND", "Plays a sound at target location");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // SOUND:name:volume:pitch - supports both positional and key=value
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
                        case "sound", "name", "type" -> params.set("sound", value.toUpperCase());
                        case "volume", "vol" -> params.setValue(parseDouble(value, 1.0));
                        case "pitch" -> params.set("pitch", parseDouble(value, 1.0));
                    }
                }
            } else {
                // Positional format
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.set("sound", part.toUpperCase());
                    case 2 -> {
                        try { params.setValue(Float.parseFloat(part)); } catch (NumberFormatException ignored) {}
                    }
                    case 3 -> {
                        try { params.set("pitch", Float.parseFloat(part)); } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) return false;

        String soundName = params.getString("sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        // Check both "volume" param (from YAML) and getValue() (from legacy string format)
        float volume = params.getFloat("volume", (float) params.getValue());
        if (volume <= 0) volume = 1.0f;
        float pitch = params.getFloat("pitch", 1.0f);

        Sound sound = getSound(soundName);
        if (sound == null) {
            debug("Unknown sound: " + soundName);
            return false;
        }

        Location loc = getTargetLocation(context);
        if (loc != null) {
            loc.getWorld().playSound(loc, sound, volume, pitch);
            return true;
        }

        return false;
    }

    private Sound getSound(String name) {
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
