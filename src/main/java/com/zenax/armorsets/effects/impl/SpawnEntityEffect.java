package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import org.bukkit.Location;
import org.bukkit.entity.*;

public class SpawnEntityEffect extends AbstractEffect {

    public SpawnEntityEffect() {
        super("SPAWN_ENTITY", "Spawns entities at target location");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        if (parts.length >= 2) {
            params.set("entity_type", parts[1].toUpperCase());
        }
        if (parts.length >= 3) {
            try {
                params.setValue(Integer.parseInt(parts[2])); // count
            } catch (NumberFormatException ignored) {}
        }
        if (parts.length >= 4) {
            params.set("ally", parts[3].equalsIgnoreCase("true"));
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) return false;

        String entityTypeName = params.getString("entity_type", "ZOMBIE");
        int count = (int) params.getValue();
        if (count <= 0) count = 1;

        // Cap count
        int maxCount = getPlugin().getConfigManager().getMainConfig()
                .getInt("effects.max-spawned-entities", 5);
        count = Math.min(count, maxCount);

        boolean ally = params.getBoolean("ally", false);

        EntityType entityType = getEntityType(entityTypeName);
        if (entityType == null) {
            debug("Unknown entity type: " + entityTypeName);
            return false;
        }

        Location loc = getTargetLocation(context);
        if (loc == null) return false;

        for (int i = 0; i < count; i++) {
            Entity entity = loc.getWorld().spawnEntity(loc, entityType);

            if (ally && entity instanceof Mob mob) {
                // Make ally not target owner
                mob.setTarget(context.getVictim() instanceof LivingEntity living ? living : null);
            }

            // For experience orbs, set experience
            if (entity instanceof ExperienceOrb orb) {
                orb.setExperience(10);
            }
        }

        debug("Spawned " + count + " " + entityTypeName + " at " + loc);
        return true;
    }

    private EntityType getEntityType(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
