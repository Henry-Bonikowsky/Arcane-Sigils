package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
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

        if (parts.length >= 2) {
            params.set("sound", parts[1].toUpperCase());
        }
        if (parts.length >= 3) {
            try {
                params.setValue(Float.parseFloat(parts[2])); // volume
            } catch (NumberFormatException ignored) {}
        }
        if (parts.length >= 4) {
            try {
                params.set("pitch", Float.parseFloat(parts[3]));
            } catch (NumberFormatException ignored) {}
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) return false;

        String soundName = params.getString("sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        float volume = (float) params.getValue();
        if (volume <= 0) volume = 1.0f;
        float pitch;
        pitch = (float) params.getDouble("pitch", 1.0);

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
