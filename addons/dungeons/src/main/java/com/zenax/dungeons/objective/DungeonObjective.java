package com.zenax.dungeons.objective;

import com.zenax.dungeons.dungeon.DungeonInstance;
import net.kyori.adventure.text.Component;

/**
 * Interface representing a dungeon objective that players must complete.
 * Objectives track progress and can be updated based on game events.
 */
public interface DungeonObjective {

    /**
     * Gets the type of this objective.
     *
     * @return The objective type
     */
    ObjectiveType getType();

    /**
     * Gets the unique identifier for this objective.
     *
     * @return The objective ID
     */
    String getId();

    /**
     * Gets the description of this objective.
     *
     * @return The description
     */
    String getDescription();

    /**
     * Checks if this objective is complete.
     *
     * @return true if the objective is complete
     */
    boolean isComplete();

    /**
     * Gets the progress of this objective as a value between 0.0 and 1.0.
     * 0.0 represents no progress, 1.0 represents completion.
     *
     * @return The progress value
     */
    double getProgress();

    /**
     * Updates the objective based on game events.
     * This method is called when relevant events occur in the dungeon.
     *
     * @param instance The dungeon instance
     * @param context Additional context data specific to the objective type
     */
    void update(DungeonInstance instance, Object... context);

    /**
     * Gets a formatted component for displaying this objective in the UI.
     *
     * @return The display component
     */
    Component getDisplayComponent();
}
