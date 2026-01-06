package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Applies a mark (string tag) to the target entity.
 * Marks can be checked with the HAS_MARK condition.
 *
 * Format: MARK:MARK_NAME:DURATION @Target
 * Example: MARK:PHARAOH_MARK:5 @Victim
 *
 * Parameters:
 * - mark_name: The identifier for the mark (case-insensitive, stored uppercase)
 * - duration: Duration in seconds (optional, default 10s, 0 = permanent)
 * - behavior: Optional behavior sigil ID to run while marked
 *             The behavior's EFFECT_STATIC runs on apply, TICK runs each second,
 *             and EXPIRE runs when the mark ends.
 *             Re-applying refreshes duration without re-running EFFECT_STATIC.
 */
public class MarkEffect extends AbstractEffect {

    public MarkEffect() {
        super("MARK", "Applies a mark to the target");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        // Parse MARK:NAME:DURATION format (supports both positional and key=value)
        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        params.setDuration(10); // Default 10 seconds
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
                        case "mark_name", "name", "mark" -> params.set("mark_name", value.toUpperCase());
                        case "duration" -> params.setDuration((int) parseDouble(value, 10));
                        case "behavior" -> params.set("behavior", value);
                    }
                }
            } else {
                // Positional format
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.set("mark_name", part.toUpperCase());
                    case 2 -> {
                        try {
                            params.setDuration((int) Double.parseDouble(part));
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

        String markName = params.getString("mark_name", "MARKED");
        int duration = params.getDuration() > 0 ? params.getDuration() : 10;
        String behaviorId = params.getString("behavior", null);

        LivingEntity target = getTarget(context);
        if (target == null) return false;

        // Get owner for behavior context
        Player owner = context.getPlayer();

        // Handle @Nearby targets
        String targetStr = params.getTarget();
        if (targetStr != null && (targetStr.startsWith("@Nearby") || targetStr.startsWith("@NearbyEntities"))) {
            double radius = parseNearbyRadius(targetStr, 5);
            for (LivingEntity entity : getNearbyEntities(context, radius)) {
                getPlugin().getMarkManager().applyMark(entity, markName, duration, behaviorId, owner);
            }
            debug("Applied mark " + markName + " to nearby entities for " + duration + "s" +
                  (behaviorId != null ? " with behavior " + behaviorId : ""));
            return true;
        }

        getPlugin().getMarkManager().applyMark(target, markName, duration, behaviorId, owner);

        debug("Applied mark " + markName + " to " +
              (target instanceof Player p ? p.getName() : target.getType().name()) +
              " for " + duration + "s" +
              (behaviorId != null ? " with behavior " + behaviorId : ""));

        String displayName = formatMarkName(markName);

        // Send one-time notification to attacker (only if attacker is a player and not the victim)
        if (owner != null && owner instanceof Player attacker && owner != target) {
            Component msg = Component.text("Inflicted ", NamedTextColor.GREEN)
                .append(Component.text(displayName, NamedTextColor.WHITE))
                .append(Component.text(" for " + duration + "s", NamedTextColor.GREEN));
            attacker.sendActionBar(msg);
        }

        return true;
    }

    /**
     * Format mark name for display (PHARAOH_MARK -> Pharaoh's Mark)
     */
    private String formatMarkName(String markName) {
        // Special case for common marks
        if (markName.equalsIgnoreCase("PHARAOH_MARK")) {
            return "Pharaoh's Mark";
        }

        // Generic formatting: replace underscores, title case
        String[] words = markName.toLowerCase().replace("_", " ").split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1))
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
}
