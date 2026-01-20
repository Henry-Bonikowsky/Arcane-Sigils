package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
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

        // Parse POTION:TYPE:DURATION:AMPLIFIER format (supports both positional and key=value)
        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

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
                        case "potion_type", "type" -> params.set("potion_type", value.toUpperCase());
                        case "duration" -> params.setDuration((int) parseDouble(value, 10));
                        case "amplifier", "amp" -> params.setAmplifier((int) parseDouble(value, 0));
                    }
                }
            } else {
                // Positional format
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.set("potion_type", part.toUpperCase());
                    case 2 -> {
                        try {
                            params.setDuration((int) Double.parseDouble(part));
                        } catch (NumberFormatException ignored) {}
                    }
                    case 3 -> {
                        try {
                            params.setAmplifier((int) Double.parseDouble(part));
                        } catch (NumberFormatException ignored) {}
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

        String potionTypeName = params.getString("potion_type", "SPEED");
        int duration = params.getDuration() > 0 ? params.getDuration() * 20 : 200; // Convert to ticks
        int amplifier = params.getAmplifier();

        // For EFFECT_STATIC (passive) signals targeting SELF, ensure minimum duration of 5 seconds
        // to guarantee overlap with cooldown and prevent flickering
        String targetStr = params.getTarget();
        boolean isSelfTarget = targetStr == null || targetStr.equals("@Self") || targetStr.isEmpty();
        if (context.getSignalType() != null &&
            context.getSignalType().name().equals("EFFECT_STATIC") &&
            isSelfTarget &&
            duration < 100) {
            duration = 100; // 5 seconds minimum for passive self-buffs
        }

        PotionEffectType potionType = getPotionType(potionTypeName);
        if (potionType == null) {
            debug("Unknown potion type: " + potionTypeName);
            return false;
        }

        LivingEntity target = getTarget(context);
        if (target == null) return false;

        // Handle @Nearby targets
        if (targetStr != null && targetStr.startsWith("@Nearby")) {
            double radius = parseNearbyRadius(targetStr, 5);
            for (LivingEntity entity : getNearbyEntities(context, radius)) {
                // Use force=true to ensure effect is always applied/refreshed
                entity.addPotionEffect(new PotionEffect(potionType, duration, amplifier, false, true), true);
            }
            return true;
        }

        // Create the potion effect
        PotionEffect effect = new PotionEffect(potionType, duration, amplifier, false, true);

        // CHECK INTERCEPTION BEFORE APPLYING
        com.miracle.arcanesigils.ArmorSetsPlugin plugin = getPlugin();
        com.miracle.arcanesigils.interception.InterceptionManager interceptionManager = plugin.getInterceptionManager();

        if (interceptionManager != null && target instanceof org.bukkit.entity.Player player) {
            com.miracle.arcanesigils.interception.InterceptionEvent interceptionEvent =
                new com.miracle.arcanesigils.interception.InterceptionEvent(
                    com.miracle.arcanesigils.interception.InterceptionEvent.Type.POTION_EFFECT,
                    player,
                    context.getPlayer(),
                    potionType,
                    amplifier,
                    duration
                );

            com.miracle.arcanesigils.interception.InterceptionEvent result =
                interceptionManager.fireIntercept(interceptionEvent);

            if (result.isCancelled()) {
                debug("Potion effect " + potionType + " blocked by interceptor on " + target.getName());
                return false; // Effect was blocked (e.g., by Cleopatra suppression)
            }
        }

        // Use force=true to ensure effect is always applied/refreshed (critical for static effects)
        target.addPotionEffect(effect, true);

        // Track effect for removal when armor is unequipped
        if (target == context.getPlayer() && getPlugin().getSignalHandler() != null) {
            getPlugin().getSignalHandler().trackAppliedEffect(context.getPlayer(), potionType);
        }

        return true;
    }

    private PotionEffectType getPotionType(String name) {
        // Trim and uppercase for consistent matching
        String normalized = name.trim().toUpperCase();

        return switch (normalized) {
            case "SPEED", "SWIFTNESS" -> PotionEffectType.SPEED;
            case "SLOW", "SLOWNESS" -> PotionEffectType.SLOWNESS;
            case "HASTE" -> PotionEffectType.HASTE;
            case "MINING_FATIGUE", "FATIGUE" -> PotionEffectType.MINING_FATIGUE;
            case "STRENGTH" -> PotionEffectType.STRENGTH;
            case "INSTANT_HEALTH", "HEAL", "HEALTH" -> PotionEffectType.INSTANT_HEALTH;
            case "INSTANT_DAMAGE", "HARM", "DAMAGE" -> PotionEffectType.INSTANT_DAMAGE;
            case "JUMP", "JUMP_BOOST" -> PotionEffectType.JUMP_BOOST;
            case "NAUSEA", "CONFUSION" -> PotionEffectType.NAUSEA;
            case "REGENERATION", "REGEN" -> PotionEffectType.REGENERATION;
            // RESISTANCE must come before FIRE_RESISTANCE check - exact match only
            case "RESISTANCE", "DAMAGE_RESISTANCE", "DMG_RESISTANCE" -> PotionEffectType.RESISTANCE;
            case "FIRE_RESISTANCE", "FIRERESISTANCE", "FIRE_RES" -> PotionEffectType.FIRE_RESISTANCE;
            case "WATER_BREATHING", "WATERBREATHING" -> PotionEffectType.WATER_BREATHING;
            case "INVISIBILITY", "INVIS" -> PotionEffectType.INVISIBILITY;
            case "BLINDNESS", "BLIND" -> PotionEffectType.BLINDNESS;
            case "NIGHT_VISION", "NIGHTVISION" -> PotionEffectType.NIGHT_VISION;
            case "HUNGER" -> PotionEffectType.HUNGER;
            case "WEAKNESS", "WEAK" -> PotionEffectType.WEAKNESS;
            case "POISON" -> PotionEffectType.POISON;
            case "WITHER" -> PotionEffectType.WITHER;
            case "HEALTH_BOOST", "HEALTHBOOST" -> PotionEffectType.HEALTH_BOOST;
            case "ABSORPTION", "ABSORB" -> PotionEffectType.ABSORPTION;
            case "SATURATION" -> PotionEffectType.SATURATION;
            case "GLOWING", "GLOW" -> PotionEffectType.GLOWING;
            case "LEVITATION", "LEVITATE" -> PotionEffectType.LEVITATION;
            case "LUCK" -> PotionEffectType.LUCK;
            case "UNLUCK", "BAD_LUCK", "BADLUCK" -> PotionEffectType.UNLUCK;
            case "SLOW_FALLING", "SLOWFALLING" -> PotionEffectType.SLOW_FALLING;
            case "CONDUIT_POWER", "CONDUITPOWER", "CONDUIT" -> PotionEffectType.CONDUIT_POWER;
            case "DOLPHINS_GRACE", "DOLPHINSGRACE" -> PotionEffectType.DOLPHINS_GRACE;
            case "BAD_OMEN", "BADOMEN" -> PotionEffectType.BAD_OMEN;
            case "HERO_OF_THE_VILLAGE", "HERO" -> PotionEffectType.HERO_OF_THE_VILLAGE;
            case "DARKNESS", "DARK" -> PotionEffectType.DARKNESS;
            default -> {
                // Try to match by Bukkit name as fallback
                try {
                    yield PotionEffectType.getByName(normalized);
                } catch (Exception e) {
                    yield null;
                }
            }
        };
    }
}
