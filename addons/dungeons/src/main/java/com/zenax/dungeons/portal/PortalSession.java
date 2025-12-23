package com.zenax.dungeons.portal;

import org.bukkit.entity.Player;

import java.util.*;

/**
 * Represents an active portal entry session.
 * When the first player enters a portal, a session is created with a 30-second timer.
 * All players who enter during this window become the party for the dungeon run.
 */
public class PortalSession {
    private final UUID sessionId;
    private final UUID portalId;
    private final String dungeonId;
    private final long startTime;
    private final long expirationTime;
    private final Set<UUID> enteredPlayers;
    private final int maxPlayers;

    private static final long SESSION_DURATION_MS = 30_000; // 30 seconds

    /**
     * Creates a new portal session.
     *
     * @param portalId The portal that started this session
     * @param dungeonId The dungeon this portal leads to
     * @param firstPlayer The first player who entered
     * @param maxPlayers Maximum players allowed for this dungeon
     */
    public PortalSession(UUID portalId, String dungeonId, Player firstPlayer, int maxPlayers) {
        this.sessionId = UUID.randomUUID();
        this.portalId = portalId;
        this.dungeonId = dungeonId;
        this.startTime = System.currentTimeMillis();
        this.expirationTime = startTime + SESSION_DURATION_MS;
        this.enteredPlayers = new LinkedHashSet<>(); // Preserve entry order
        this.maxPlayers = maxPlayers;

        // Add the first player
        this.enteredPlayers.add(firstPlayer.getUniqueId());
    }

    /**
     * Gets the session's unique ID.
     *
     * @return The session ID
     */
    public UUID getSessionId() {
        return sessionId;
    }

    /**
     * Gets the portal ID this session is for.
     *
     * @return The portal ID
     */
    public UUID getPortalId() {
        return portalId;
    }

    /**
     * Gets the dungeon ID this session is for.
     *
     * @return The dungeon ID
     */
    public String getDungeonId() {
        return dungeonId;
    }

    /**
     * Gets the session start time.
     *
     * @return The start timestamp in milliseconds
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Gets the session expiration time.
     *
     * @return The expiration timestamp in milliseconds
     */
    public long getExpirationTime() {
        return expirationTime;
    }

    /**
     * Checks if the session has expired.
     *
     * @return true if the session has expired
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expirationTime;
    }

    /**
     * Gets the remaining time in seconds.
     *
     * @return The remaining time, or 0 if expired
     */
    public int getRemainingSeconds() {
        long remaining = expirationTime - System.currentTimeMillis();
        return remaining > 0 ? (int) (remaining / 1000) : 0;
    }

    /**
     * Gets all players who have entered during this session.
     *
     * @return Set of player UUIDs in entry order
     */
    public Set<UUID> getEnteredPlayers() {
        return new LinkedHashSet<>(enteredPlayers);
    }

    /**
     * Gets the number of players who have entered.
     *
     * @return The player count
     */
    public int getPlayerCount() {
        return enteredPlayers.size();
    }

    /**
     * Gets the first player who entered (the party leader).
     *
     * @return The first player's UUID
     */
    public UUID getFirstPlayer() {
        return enteredPlayers.iterator().next();
    }

    /**
     * Checks if the session is full (max players reached).
     *
     * @return true if no more players can join
     */
    public boolean isFull() {
        return enteredPlayers.size() >= maxPlayers;
    }

    /**
     * Checks if a player has already entered during this session.
     *
     * @param playerUuid The player's UUID
     * @return true if the player has entered
     */
    public boolean hasEntered(UUID playerUuid) {
        return enteredPlayers.contains(playerUuid);
    }

    /**
     * Checks if a player has already entered during this session.
     *
     * @param player The player
     * @return true if the player has entered
     */
    public boolean hasEntered(Player player) {
        return hasEntered(player.getUniqueId());
    }

    /**
     * Adds a player to this session.
     *
     * @param player The player to add
     * @return true if the player was added, false if session is full or expired
     */
    public boolean addPlayer(Player player) {
        if (isExpired()) {
            return false;
        }

        if (isFull()) {
            return false;
        }

        if (hasEntered(player)) {
            return false;
        }

        enteredPlayers.add(player.getUniqueId());
        return true;
    }

    /**
     * Removes a player from this session.
     *
     * @param playerUuid The player's UUID
     * @return true if the player was removed
     */
    public boolean removePlayer(UUID playerUuid) {
        return enteredPlayers.remove(playerUuid);
    }

    /**
     * Gets the maximum number of players.
     *
     * @return The max player count
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Checks if the session is empty.
     *
     * @return true if no players have entered
     */
    public boolean isEmpty() {
        return enteredPlayers.isEmpty();
    }

    /**
     * Gets the session duration in milliseconds.
     *
     * @return The session duration (30 seconds)
     */
    public static long getSessionDurationMs() {
        return SESSION_DURATION_MS;
    }
}
