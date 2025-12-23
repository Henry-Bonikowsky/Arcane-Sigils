package com.zenax.dungeons.dungeon;

/**
 * Represents the various states a dungeon instance can be in.
 */
public enum DungeonState {
    /**
     * The dungeon is currently being generated.
     */
    GENERATING,

    /**
     * The dungeon has been generated and players are in the lobby waiting to start.
     */
    LOBBY,

    /**
     * The dungeon is active and players are currently attempting it.
     */
    ACTIVE,

    /**
     * The dungeon has been successfully completed by the party.
     */
    COMPLETED,

    /**
     * The dungeon attempt has failed (all players died, time expired, etc.).
     */
    FAILED;

    /**
     * Checks if this state represents a terminal state (completed or failed).
     *
     * @return true if the dungeon is in a terminal state
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    /**
     * Checks if this state allows player interactions.
     *
     * @return true if players can interact with the dungeon
     */
    public boolean isPlayable() {
        return this == LOBBY || this == ACTIVE;
    }

    /**
     * Checks if the dungeon is currently in progress.
     *
     * @return true if the dungeon is active
     */
    public boolean isInProgress() {
        return this == ACTIVE;
    }
}
