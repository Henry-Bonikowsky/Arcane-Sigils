package com.zenax.armorsets.particles;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import com.zenax.armorsets.effects.impl.AbstractEffect;
import com.zenax.armorsets.utils.TargetFinder;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Effect that plays shape-based visual effects using the ShapeEngine.
 *
 * Format: SHAPE:preset_id @Target
 * Example: SHAPE:fire_spiral @Self
 * Example: SHAPE:expanding_ring @Victim
 * Example: SHAPE:sand_summon @Target  (uses TargetFinder to get looked-at entity)
 */
public class ShapeEffect extends AbstractEffect {

    public ShapeEffect() {
        super("SHAPE", "Plays a shape-based visual effect preset");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // SHAPE:preset_id
        if (parts.length >= 2) {
            params.set("preset", parts[1].toLowerCase());
        }

        // Optional: SHAPE:preset_id:duration
        if (parts.length >= 3) {
            try {
                params.setDuration((int) Double.parseDouble(parts[2]));
            } catch (NumberFormatException ignored) {}
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) {
            debug("No params for SHAPE effect");
            return false;
        }

        String presetId = params.getString("preset", "");
        if (presetId.isEmpty()) {
            debug("No preset specified for SHAPE effect");
            return false;
        }

        // Get target location
        Location targetLocation = getTargetLocation(context);
        if (targetLocation == null) {
            debug("No target location for SHAPE effect");
            return false;
        }

        // Get owner for attachment
        Player player = context.getPlayer();
        LivingEntity owner = null;

        String targetStr = params.getTarget();
        if (targetStr != null) {
            if (targetStr.equalsIgnoreCase("@Self") && player != null) {
                owner = player;
            } else if (targetStr.equalsIgnoreCase("@Victim")) {
                owner = context.getVictim();
            } else if (targetStr.equalsIgnoreCase("@Target") && player != null) {
                // @Target uses TargetFinder to find what the player is looking at
                owner = TargetFinder.findLookTarget(player, 30.0);
            }
        }

        // Get the ShapeEngine
        ArmorSetsPlugin plugin = getPlugin();
        ShapeEngine engine = plugin.getShapeEngine();

        if (engine == null) {
            debug("ShapeEngine not initialized");
            return false;
        }

        // Check if preset exists
        PresetDefinition preset = engine.getPreset(presetId);
        if (preset == null) {
            debug("Preset not found: " + presetId);
            return false;
        }

        // Play the preset
        engine.playPreset(presetId, targetLocation, owner);

        debug("Playing preset: " + presetId + " at " + targetLocation);
        return true;
    }

    /**
     * Get the target location based on context and target selector.
     */
    @Override
    protected Location getTargetLocation(EffectContext context) {
        EffectParams params = context.getParams();
        String targetStr = params != null ? params.getTarget() : null;

        if (targetStr == null || targetStr.isEmpty()) {
            targetStr = "@Self";
        }

        if (targetStr.equalsIgnoreCase("@Self")) {
            Player player = context.getPlayer();
            return player != null ? player.getLocation() : null;
        }

        if (targetStr.equalsIgnoreCase("@Victim")) {
            LivingEntity victim = context.getVictim();
            return victim != null ? victim.getLocation() : null;
        }

        if (targetStr.equalsIgnoreCase("@Target")) {
            // @Target uses TargetFinder to find what the player is looking at
            Player player = context.getPlayer();
            if (player != null) {
                LivingEntity target = TargetFinder.findLookTarget(player, 30.0);
                if (target != null) {
                    return target.getLocation();
                }
            }
            // Fallback to player location if no target found
            return player != null ? player.getLocation() : null;
        }

        if (targetStr.toLowerCase().startsWith("@nearby")) {
            Player player = context.getPlayer();
            return player != null ? player.getLocation() : null;
        }

        // Default to context location or player location
        if (context.getLocation() != null) {
            return context.getLocation();
        }

        Player player = context.getPlayer();
        return player != null ? player.getLocation() : null;
    }
}
