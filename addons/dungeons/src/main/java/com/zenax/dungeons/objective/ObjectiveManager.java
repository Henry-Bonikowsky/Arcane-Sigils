package com.zenax.dungeons.objective;

import com.zenax.dungeons.dungeon.DungeonInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manager class for handling dungeon objectives.
 * Tracks objectives per dungeon instance and provides methods to update and check completion.
 */
public class ObjectiveManager {
    private final Map<UUID, List<DungeonObjective>> instanceObjectives;

    /**
     * Creates a new objective manager.
     */
    public ObjectiveManager() {
        this.instanceObjectives = new ConcurrentHashMap<>();
    }

    /**
     * Registers an objective for a dungeon instance.
     *
     * @param instance The dungeon instance
     * @param objective The objective to register
     */
    public void registerObjective(DungeonInstance instance, DungeonObjective objective) {
        UUID instanceId = instance.getInstanceId();
        instanceObjectives.computeIfAbsent(instanceId, k -> new ArrayList<>()).add(objective);
    }

    /**
     * Registers multiple objectives for a dungeon instance.
     *
     * @param instance The dungeon instance
     * @param objectives The objectives to register
     */
    public void registerObjectives(DungeonInstance instance, List<DungeonObjective> objectives) {
        UUID instanceId = instance.getInstanceId();
        instanceObjectives.computeIfAbsent(instanceId, k -> new ArrayList<>()).addAll(objectives);
    }

    /**
     * Updates objectives of a specific type for a dungeon instance.
     *
     * @param instance The dungeon instance
     * @param type The type of objectives to update
     * @param context Additional context for the update
     */
    public void updateObjectives(DungeonInstance instance, ObjectiveType type, Object... context) {
        List<DungeonObjective> objectives = getObjectives(instance);
        if (objectives.isEmpty()) {
            return;
        }

        // Filter and update objectives of the specified type
        objectives.stream()
            .filter(obj -> obj.getType() == type && !obj.isComplete())
            .forEach(obj -> obj.update(instance, context));
    }

    /**
     * Updates all objectives for a dungeon instance, regardless of type.
     *
     * @param instance The dungeon instance
     * @param context Additional context for the update
     */
    public void updateAllObjectives(DungeonInstance instance, Object... context) {
        List<DungeonObjective> objectives = getObjectives(instance);
        if (objectives.isEmpty()) {
            return;
        }

        objectives.stream()
            .filter(obj -> !obj.isComplete())
            .forEach(obj -> obj.update(instance, context));
    }

    /**
     * Gets all objectives for a dungeon instance.
     *
     * @param instance The dungeon instance
     * @return The list of objectives
     */
    public List<DungeonObjective> getObjectives(DungeonInstance instance) {
        return new ArrayList<>(
            instanceObjectives.getOrDefault(instance.getInstanceId(), Collections.emptyList())
        );
    }

    /**
     * Gets objectives of a specific type for a dungeon instance.
     *
     * @param instance The dungeon instance
     * @param type The objective type
     * @return The list of objectives of the specified type
     */
    public List<DungeonObjective> getObjectivesByType(DungeonInstance instance, ObjectiveType type) {
        return getObjectives(instance).stream()
            .filter(obj -> obj.getType() == type)
            .collect(Collectors.toList());
    }

    /**
     * Gets a specific objective by ID.
     *
     * @param instance The dungeon instance
     * @param objectiveId The objective ID
     * @return The objective, or null if not found
     */
    public DungeonObjective getObjective(DungeonInstance instance, String objectiveId) {
        return getObjectives(instance).stream()
            .filter(obj -> obj.getId().equals(objectiveId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Checks if all objectives are complete for a dungeon instance.
     *
     * @param instance The dungeon instance
     * @return true if all objectives are complete
     */
    public boolean checkCompletion(DungeonInstance instance) {
        List<DungeonObjective> objectives = getObjectives(instance);
        if (objectives.isEmpty()) {
            return false;
        }

        return objectives.stream().allMatch(DungeonObjective::isComplete);
    }

    /**
     * Gets the total progress of all objectives (average).
     *
     * @param instance The dungeon instance
     * @return The average progress value (0.0-1.0)
     */
    public double getTotalProgress(DungeonInstance instance) {
        List<DungeonObjective> objectives = getObjectives(instance);
        if (objectives.isEmpty()) {
            return 0.0;
        }

        double totalProgress = objectives.stream()
            .mapToDouble(DungeonObjective::getProgress)
            .sum();

        return totalProgress / objectives.size();
    }

    /**
     * Gets the number of completed objectives.
     *
     * @param instance The dungeon instance
     * @return The number of completed objectives
     */
    public int getCompletedCount(DungeonInstance instance) {
        return (int) getObjectives(instance).stream()
            .filter(DungeonObjective::isComplete)
            .count();
    }

    /**
     * Gets the total number of objectives.
     *
     * @param instance The dungeon instance
     * @return The total number of objectives
     */
    public int getTotalCount(DungeonInstance instance) {
        return getObjectives(instance).size();
    }

    /**
     * Broadcasts progress to all players in the dungeon instance.
     *
     * @param instance The dungeon instance
     */
    public void broadcastProgress(DungeonInstance instance) {
        List<DungeonObjective> objectives = getObjectives(instance);
        if (objectives.isEmpty()) {
            return;
        }

        // Build the progress message
        Component header = Component.text()
            .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH))
            .append(Component.newline())
            .append(Component.text("Dungeon Objectives", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.newline())
            .build();

        net.kyori.adventure.text.TextComponent.Builder messageBuilder = Component.text()
            .append(header);

        // Add each objective
        for (DungeonObjective objective : objectives) {
            messageBuilder.append(objective.getDisplayComponent())
                .append(Component.newline());
        }

        // Add footer with overall progress
        int completed = getCompletedCount(instance);
        int total = getTotalCount(instance);
        double progress = getTotalProgress(instance);

        Component footer = Component.text()
            .append(Component.text("Overall Progress: ", NamedTextColor.GRAY))
            .append(Component.text(
                String.format("%d/%d (%.0f%%)", completed, total, progress * 100),
                completed == total ? NamedTextColor.GREEN : NamedTextColor.YELLOW
            ))
            .append(Component.newline())
            .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH))
            .build();

        messageBuilder.append(footer);

        Component finalMessage = messageBuilder.build();

        // Send to all players in the instance
        for (UUID playerUuid : instance.getPlayerUuids()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(finalMessage);
            }
        }
    }

    /**
     * Broadcasts a specific objective update to all players.
     *
     * @param instance The dungeon instance
     * @param objective The objective that was updated
     */
    public void broadcastObjectiveUpdate(DungeonInstance instance, DungeonObjective objective) {
        Component message = Component.text()
            .append(Component.text("[Objective] ", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(objective.getDisplayComponent())
            .build();

        // Send to all players in the instance
        for (UUID playerUuid : instance.getPlayerUuids()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Broadcasts objective completion to all players.
     *
     * @param instance The dungeon instance
     * @param objective The completed objective
     */
    public void broadcastObjectiveComplete(DungeonInstance instance, DungeonObjective objective) {
        Component message = Component.text()
            .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(Component.text("Objective Complete: ", NamedTextColor.GREEN))
            .append(Component.text(objective.getDescription(), NamedTextColor.WHITE))
            .build();

        // Send to all players in the instance
        for (UUID playerUuid : instance.getPlayerUuids()) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Clears all objectives for a dungeon instance.
     *
     * @param instance The dungeon instance
     */
    public void clearObjectives(DungeonInstance instance) {
        instanceObjectives.remove(instance.getInstanceId());
    }

    /**
     * Removes a specific objective from a dungeon instance.
     *
     * @param instance The dungeon instance
     * @param objectiveId The objective ID to remove
     * @return true if the objective was removed
     */
    public boolean removeObjective(DungeonInstance instance, String objectiveId) {
        List<DungeonObjective> objectives = instanceObjectives.get(instance.getInstanceId());
        if (objectives == null) {
            return false;
        }

        return objectives.removeIf(obj -> obj.getId().equals(objectiveId));
    }

    /**
     * Clears all objectives from all instances.
     * Should be called on plugin disable.
     */
    public void clearAll() {
        instanceObjectives.clear();
    }
}
