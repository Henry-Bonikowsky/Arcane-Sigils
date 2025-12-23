package com.zenax.dungeons.objective.impl;

import com.zenax.dungeons.dungeon.DungeonInstance;
import com.zenax.dungeons.objective.AbstractObjective;
import com.zenax.dungeons.objective.ObjectiveType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Objective that requires clearing all monsters from specific rooms.
 * Progress is calculated as clearedCount / requiredCount.
 */
public class ClearRoomsObjective extends AbstractObjective {
    private final List<String> requiredRooms;
    private final Set<String> clearedRooms;

    /**
     * Creates a new clear rooms objective.
     *
     * @param id The unique identifier for this objective
     * @param description The description of the objective
     * @param requiredRooms The list of room IDs that must be cleared
     */
    public ClearRoomsObjective(String id, String description, List<String> requiredRooms) {
        super(id, ObjectiveType.CLEAR_ROOMS, description);
        this.requiredRooms = requiredRooms != null && !requiredRooms.isEmpty()
            ? List.copyOf(requiredRooms)
            : List.of();
        this.clearedRooms = new HashSet<>();
    }

    @Override
    public double getProgress() {
        if (requiredRooms.isEmpty()) {
            return 0.0;
        }
        return Math.min(1.0, (double) clearedRooms.size() / (double) requiredRooms.size());
    }

    @Override
    public void update(DungeonInstance instance, Object... context) {
        // Context[0] should be the room ID that was cleared
        if (context.length == 0) {
            return;
        }

        if (context[0] instanceof String) {
            String roomId = (String) context[0];

            // Check if this is a required room and hasn't been cleared yet
            if (requiredRooms.contains(roomId) && !clearedRooms.contains(roomId)) {
                clearedRooms.add(roomId);

                // Check if objective is complete
                if (clearedRooms.size() >= requiredRooms.size()) {
                    setComplete(true);
                }
            }
        }
    }

    /**
     * Manually marks a room as cleared.
     *
     * @param roomId The room ID
     */
    public void clearRoom(String roomId) {
        if (requiredRooms.contains(roomId) && !clearedRooms.contains(roomId)) {
            clearedRooms.add(roomId);
            if (clearedRooms.size() >= requiredRooms.size()) {
                setComplete(true);
            }
        }
    }

    /**
     * Gets the list of required room IDs.
     *
     * @return The required room IDs
     */
    public List<String> getRequiredRooms() {
        return requiredRooms;
    }

    /**
     * Gets the set of cleared room IDs.
     *
     * @return The cleared room IDs
     */
    public Set<String> getClearedRooms() {
        return new HashSet<>(clearedRooms);
    }

    /**
     * Gets the number of cleared rooms.
     *
     * @return The cleared room count
     */
    public int getClearedCount() {
        return clearedRooms.size();
    }

    /**
     * Gets the number of required rooms.
     *
     * @return The required room count
     */
    public int getRequiredCount() {
        return requiredRooms.size();
    }

    /**
     * Checks if a specific room has been cleared.
     *
     * @param roomId The room ID
     * @return true if the room is cleared
     */
    public boolean isRoomCleared(String roomId) {
        return clearedRooms.contains(roomId);
    }

    @Override
    public String getDescription() {
        return String.format("%s (%d/%d)", description, clearedRooms.size(), requiredRooms.size());
    }
}
