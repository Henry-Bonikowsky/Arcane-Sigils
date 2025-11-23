package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PotionEffectEffect extends AbstractEffect {

    public PotionEffectEffect() {
        super("POTION", "Applies a potion effect to target");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        // Parse POTION:TYPE:DURATION:AMPLIFIER format
        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        if (parts.length >= 2) {
            params.set("potion_type", parts[1].toUpperCase());
        }
        if (parts.length >= 3) {
            try {
                params.setDuration(Integer.parseInt(parts[2]));
            } catch (NumberFormatException ignored) {}
        }
        if (parts.length >= 4) {
            try {
                params.setAmplifier(Integer.parseInt(parts[3]));
            } catch (NumberFormatException ignored) {}
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) return false;

        String potionTypeName = params.getString("potion_type", "SPEED");
        int duration = params.getDuration() > 0 ? params.getDuration() * 20 : 200; // Convert to ticks
        int amplifier = params.getAmplifier();

        PotionEffectType potionType = getPotionType(potionTypeName);
        if (potionType == null) {
            debug("Unknown potion type: " + potionTypeName);
            return false;
        }

        LivingEntity target = getTarget(context);
        if (target == null) return false;

        // Handle @Nearby targets
        String targetStr = params.getTarget();
        if (targetStr != null && targetStr.startsWith("@Nearby")) {
            double radius = parseNearbyRadius(targetStr, 5);
            for (LivingEntity entity : getNearbyEntities(context, radius)) {
                entity.addPotionEffect(new PotionEffect(potionType, duration, amplifier, false, true));
            }
            return true;
        }

        target.addPotionEffect(new PotionEffect(potionType, duration, amplifier, false, true));

        // Track effect for removal when armor is unequipped
        if (target == context.getPlayer() && getPlugin().getTriggerHandler() != null) {
            getPlugin().getTriggerHandler().trackAppliedEffect(context.getPlayer(), potionType);
        }

        debug("Applied " + potionTypeName + " to " + target.getName() + " for " + (duration / 20) + "s");
        return true;
    }

    private PotionEffectType getPotionType(String name) {
        return switch (name.toUpperCase()) {
            case "SPEED" -> PotionEffectType.SPEED;
            case "SLOW", "SLOWNESS" -> PotionEffectType.SLOWNESS;
            case "HASTE" -> PotionEffectType.HASTE;
            case "MINING_FATIGUE" -> PotionEffectType.MINING_FATIGUE;
            case "STRENGTH" -> PotionEffectType.STRENGTH;
            case "INSTANT_HEALTH", "HEAL" -> PotionEffectType.INSTANT_HEALTH;
            case "INSTANT_DAMAGE", "HARM" -> PotionEffectType.INSTANT_DAMAGE;
            case "JUMP", "JUMP_BOOST" -> PotionEffectType.JUMP_BOOST;
            case "NAUSEA", "CONFUSION" -> PotionEffectType.NAUSEA;
            case "REGENERATION" -> PotionEffectType.REGENERATION;
            case "DAMAGE_RESISTANCE", "RESISTANCE" -> PotionEffectType.RESISTANCE;
            case "FIRE_RESISTANCE" -> PotionEffectType.FIRE_RESISTANCE;
            case "WATER_BREATHING" -> PotionEffectType.WATER_BREATHING;
            case "INVISIBILITY" -> PotionEffectType.INVISIBILITY;
            case "BLINDNESS" -> PotionEffectType.BLINDNESS;
            case "NIGHT_VISION" -> PotionEffectType.NIGHT_VISION;
            case "HUNGER" -> PotionEffectType.HUNGER;
            case "WEAKNESS" -> PotionEffectType.WEAKNESS;
            case "POISON" -> PotionEffectType.POISON;
            case "WITHER" -> PotionEffectType.WITHER;
            case "HEALTH_BOOST" -> PotionEffectType.HEALTH_BOOST;
            case "ABSORPTION" -> PotionEffectType.ABSORPTION;
            case "SATURATION" -> PotionEffectType.SATURATION;
            case "GLOWING" -> PotionEffectType.GLOWING;
            case "LEVITATION" -> PotionEffectType.LEVITATION;
            case "LUCK" -> PotionEffectType.LUCK;
            case "UNLUCK", "BAD_LUCK" -> PotionEffectType.UNLUCK;
            case "SLOW_FALLING" -> PotionEffectType.SLOW_FALLING;
            case "CONDUIT_POWER" -> PotionEffectType.CONDUIT_POWER;
            case "DOLPHINS_GRACE" -> PotionEffectType.DOLPHINS_GRACE;
            case "BAD_OMEN" -> PotionEffectType.BAD_OMEN;
            case "HERO_OF_THE_VILLAGE" -> PotionEffectType.HERO_OF_THE_VILLAGE;
            case "DARKNESS" -> PotionEffectType.DARKNESS;
            default -> null;
        };
    }
}
