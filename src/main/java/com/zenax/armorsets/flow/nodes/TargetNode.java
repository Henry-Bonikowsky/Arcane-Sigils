package com.zenax.armorsets.flow.nodes;

import com.zenax.armorsets.flow.FlowContext;
import com.zenax.armorsets.flow.FlowNode;
import com.zenax.armorsets.flow.NodeType;
import com.zenax.armorsets.utils.LogHelper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Target node - changes the current target for subsequent effects.
 * Allows dynamic target selection during flow execution.
 */
public class TargetNode extends FlowNode {

    /**
     * Target selection type.
     */
    public enum TargetType {
        /**
         * The player executing the flow.
         */
        SELF,

        /**
         * The original victim from the event.
         */
        VICTIM,

        /**
         * Nearest player within range.
         */
        NEAREST_PLAYER,

        /**
         * Nearest hostile mob within range.
         */
        NEAREST_HOSTILE,

        /**
         * Nearest any living entity within range.
         */
        NEAREST_ENTITY,

        /**
         * Random player within range.
         */
        RANDOM_PLAYER,

        /**
         * Entity with a specific mark.
         */
        MARKED
    }

    public TargetNode(String id) {
        super(id);
        setDisplayName("Target");
        setParam("targetType", TargetType.VICTIM.name());
        setParam("range", 10.0);
    }

    @Override
    public NodeType getType() {
        return NodeType.TARGET;
    }

    @Override
    public String execute(FlowContext context) {
        String typeStr = getStringParam("targetType", "VICTIM");
        TargetType targetType;
        try {
            targetType = TargetType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            targetType = TargetType.VICTIM;
        }

        double range = getDoubleParam("range", 10.0);
        Player player = context.getPlayer();

        LivingEntity newTarget = switch (targetType) {
            case SELF -> player;

            case VICTIM -> context.getEffectContext().getVictim();

            case NEAREST_PLAYER -> findNearestPlayer(player, range);

            case NEAREST_HOSTILE -> findNearestHostile(player, range);

            case NEAREST_ENTITY -> findNearestEntity(player, range);

            case RANDOM_PLAYER -> findRandomPlayer(player, range);

            case MARKED -> {
                String markName = getStringParam("markName", "");
                yield findMarkedEntity(player, range, markName);
            }
        };

        if (newTarget != null) {
            context.setCurrentTarget(newTarget);
            context.setCurrentLocation(newTarget.getLocation());
            LogHelper.debug("[TargetNode] Set target to: %s (%s)",
                    newTarget.getName(), targetType);
        } else {
            LogHelper.debug("[TargetNode] No valid target found for type: %s", targetType);
        }

        return "next";
    }

    private Player findNearestPlayer(Player source, double range) {
        Player nearest = null;
        double nearestDist = range * range;

        for (Entity entity : source.getNearbyEntities(range, range, range)) {
            if (entity instanceof Player other && other != source) {
                double dist = other.getLocation().distanceSquared(source.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = other;
                }
            }
        }

        return nearest;
    }

    private LivingEntity findNearestHostile(Player source, double range) {
        LivingEntity nearest = null;
        double nearestDist = range * range;

        for (Entity entity : source.getNearbyEntities(range, range, range)) {
            if (entity instanceof LivingEntity living && isHostile(entity)) {
                double dist = living.getLocation().distanceSquared(source.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = living;
                }
            }
        }

        return nearest;
    }

    private LivingEntity findNearestEntity(Player source, double range) {
        LivingEntity nearest = null;
        double nearestDist = range * range;

        for (Entity entity : source.getNearbyEntities(range, range, range)) {
            if (entity instanceof LivingEntity living && entity != source) {
                double dist = living.getLocation().distanceSquared(source.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = living;
                }
            }
        }

        return nearest;
    }

    private Player findRandomPlayer(Player source, double range) {
        List<Player> nearby = new ArrayList<>();
        for (Entity entity : source.getNearbyEntities(range, range, range)) {
            if (entity instanceof Player other && other != source) {
                nearby.add(other);
            }
        }

        if (nearby.isEmpty()) return null;

        return nearby.get((int) (Math.random() * nearby.size()));
    }

    private LivingEntity findMarkedEntity(Player source, double range, String markName) {
        // Would need MarkManager access - for now return nearest entity with the mark
        // This is a placeholder that can be enhanced later
        for (Entity entity : source.getNearbyEntities(range, range, range)) {
            if (entity instanceof LivingEntity living) {
                // Check for mark using scoreboard or metadata
                // For now, just return first living entity
                // TODO: Integrate with MarkManager
                return living;
            }
        }
        return null;
    }

    private boolean isHostile(Entity entity) {
        // Check if entity is a hostile mob
        return switch (entity.getType()) {
            case ZOMBIE, SKELETON, CREEPER, SPIDER, CAVE_SPIDER, ENDERMAN, WITCH, SLIME,
                    MAGMA_CUBE, GHAST, BLAZE, WITHER_SKELETON, WARDEN, WITHER, ENDER_DRAGON,
                    PHANTOM, DROWNED, HUSK, STRAY, PILLAGER, RAVAGER, VEX, EVOKER, VINDICATOR,
                    PIGLIN_BRUTE, ZOGLIN, HOGLIN, GUARDIAN, ELDER_GUARDIAN, SHULKER, SILVERFISH,
                    ENDERMITE -> true;
            default -> false;
        };
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        String typeStr = getStringParam("targetType", "");
        if (typeStr.isEmpty()) {
            errors.add("Target type is required");
        }

        if ("MARKED".equalsIgnoreCase(typeStr)) {
            String markName = getStringParam("markName", "");
            if (markName.isEmpty()) {
                errors.add("Mark name is required for MARKED target type");
            }
        }

        if (getConnection("next") == null) {
            errors.add("Output 'next' is not connected");
        }

        return errors;
    }

    @Override
    public FlowNode deepCopy() {
        TargetNode copy = new TargetNode(getId());
        copyBaseTo(copy);
        return copy;
    }
}
