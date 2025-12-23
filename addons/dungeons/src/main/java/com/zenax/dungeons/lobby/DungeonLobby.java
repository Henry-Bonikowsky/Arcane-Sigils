package com.zenax.dungeons.lobby;

import com.zenax.dungeons.dungeon.Dungeon;
import com.zenax.dungeons.dungeon.DungeonDifficulty;
import com.zenax.dungeons.dungeon.ObjectiveMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a dungeon lobby instance where players can configure settings
 * and prepare before entering the dungeon.
 */
public class DungeonLobby {
    private final String dungeonId;
    private final UUID instanceId;
    private final Dungeon dungeonTemplate;

    // Location data
    private final Location spawnPoint;
    private final Location readyPlatformCenter;
    private final Location gateLocation;
    private final Location dungeonEntranceLocation;
    private Location leavePortalLocation;
    private Location returnLocation; // Where players go when using the exit portal
    private UUID entryPortalId; // The dungeon portal that was used to enter this lobby
    private Location backPortalCorner; // Back wall portal corner location
    private UUID hologramEntityId; // TextDisplay hologram entity UUID

    // Store original locations for each player (for restoring on disconnect/shutdown)
    private final Map<UUID, Location> playerOriginalLocations = new java.util.concurrent.ConcurrentHashMap<>();

    // Interactive element locations
    private Location objectiveSelectorLocation;
    private Location difficultySelectorLocation;
    private Location infoBoardLocation;
    private Location lootPreviewLocation;

    // Player management
    private final Set<UUID> playersInLobby;
    private final Set<UUID> readyPlayers;

    // Dungeon settings
    private ObjectiveMode selectedObjectiveMode;
    private DungeonDifficulty selectedDifficulty;

    // Timing
    private final long createdTime;
    private long countdownStartTime;
    private boolean countdownActive;

    /**
     * Creates a new dungeon lobby instance.
     *
     * @param dungeonTemplate The dungeon template this lobby is for
     * @param spawnPoint The spawn point for players entering the lobby
     * @param readyPlatformCenter The center of the ready platform
     * @param gateLocation The location of the gate/barrier blocks
     * @param dungeonEntranceLocation The location players will be teleported to when entering
     */
    public DungeonLobby(Dungeon dungeonTemplate, Location spawnPoint, Location readyPlatformCenter,
                       Location gateLocation, Location dungeonEntranceLocation) {
        this.dungeonTemplate = dungeonTemplate;
        this.dungeonId = dungeonTemplate.getId();
        this.instanceId = UUID.randomUUID();

        this.spawnPoint = spawnPoint.clone();
        this.readyPlatformCenter = readyPlatformCenter.clone();
        this.gateLocation = gateLocation.clone();
        this.dungeonEntranceLocation = dungeonEntranceLocation.clone();

        // Initialize interactive locations as null - these should be set after creation
        this.objectiveSelectorLocation = null;
        this.difficultySelectorLocation = null;
        this.infoBoardLocation = null;
        this.lootPreviewLocation = null;

        this.playersInLobby = ConcurrentHashMap.newKeySet();
        this.readyPlayers = ConcurrentHashMap.newKeySet();

        // Set default settings
        this.selectedObjectiveMode = dungeonTemplate.getAvailableObjectiveModes().isEmpty()
            ? ObjectiveMode.BOSS_KILL
            : dungeonTemplate.getAvailableObjectiveModes().get(0);
        this.selectedDifficulty = dungeonTemplate.getDefaultDifficulty();

        this.createdTime = System.currentTimeMillis();
        this.countdownStartTime = 0;
        this.countdownActive = false;
    }

    /**
     * Full constructor with all locations.
     */
    public DungeonLobby(Dungeon dungeonTemplate, Location spawnPoint, Location readyPlatformCenter,
                       Location gateLocation, Location dungeonEntranceLocation,
                       Location objectiveSelectorLocation, Location difficultySelectorLocation,
                       Location infoBoardLocation, Location lootPreviewLocation) {
        this(dungeonTemplate, spawnPoint, readyPlatformCenter, gateLocation, dungeonEntranceLocation);

        this.objectiveSelectorLocation = objectiveSelectorLocation != null ? objectiveSelectorLocation.clone() : null;
        this.difficultySelectorLocation = difficultySelectorLocation != null ? difficultySelectorLocation.clone() : null;
        this.infoBoardLocation = infoBoardLocation != null ? infoBoardLocation.clone() : null;
        this.lootPreviewLocation = lootPreviewLocation != null ? lootPreviewLocation.clone() : null;
    }

    // Getters

    public String getDungeonId() {
        return dungeonId;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public Dungeon getDungeonTemplate() {
        return dungeonTemplate;
    }

    public Location getSpawnPoint() {
        return spawnPoint.clone();
    }

    public Location getReadyPlatformCenter() {
        return readyPlatformCenter.clone();
    }

    public Location getGateLocation() {
        return gateLocation.clone();
    }

    public Location getDungeonEntranceLocation() {
        return dungeonEntranceLocation.clone();
    }

    public Location getObjectiveSelectorLocation() {
        return objectiveSelectorLocation != null ? objectiveSelectorLocation.clone() : null;
    }

    public Location getDifficultySelectorLocation() {
        return difficultySelectorLocation != null ? difficultySelectorLocation.clone() : null;
    }

    public Location getInfoBoardLocation() {
        return infoBoardLocation != null ? infoBoardLocation.clone() : null;
    }

    public Location getLootPreviewLocation() {
        return lootPreviewLocation != null ? lootPreviewLocation.clone() : null;
    }

    public Location getLeavePortalLocation() {
        return leavePortalLocation != null ? leavePortalLocation.clone() : null;
    }

    public void setLeavePortalLocation(Location location) {
        this.leavePortalLocation = location != null ? location.clone() : null;
    }

    public Location getReturnLocation() {
        return returnLocation != null ? returnLocation.clone() : null;
    }

    public void setReturnLocation(Location location) {
        this.returnLocation = location != null ? location.clone() : null;
    }

    public UUID getEntryPortalId() {
        return entryPortalId;
    }

    public void setEntryPortalId(UUID portalId) {
        this.entryPortalId = portalId;
    }

    public Location getBackPortalCorner() {
        return backPortalCorner != null ? backPortalCorner.clone() : null;
    }

    public void setBackPortalCorner(Location location) {
        this.backPortalCorner = location != null ? location.clone() : null;
    }

    public UUID getHologramEntityId() {
        return hologramEntityId;
    }

    public void setHologramEntityId(UUID hologramEntityId) {
        this.hologramEntityId = hologramEntityId;
    }

    /**
     * Saves a player's original location before entering the lobby.
     */
    public void savePlayerOriginalLocation(UUID playerUuid, Location location) {
        if (location != null) {
            playerOriginalLocations.put(playerUuid, location.clone());
        }
    }

    /**
     * Gets a player's original location.
     */
    public Location getPlayerOriginalLocation(UUID playerUuid) {
        Location loc = playerOriginalLocations.get(playerUuid);
        return loc != null ? loc.clone() : null;
    }

    /**
     * Gets all stored original locations.
     */
    public Map<UUID, Location> getAllOriginalLocations() {
        return new java.util.HashMap<>(playerOriginalLocations);
    }

    public Set<UUID> getPlayersInLobby() {
        return new HashSet<>(playersInLobby);
    }

    public Set<UUID> getReadyPlayers() {
        return new HashSet<>(readyPlayers);
    }

    public ObjectiveMode getSelectedObjectiveMode() {
        return selectedObjectiveMode;
    }

    public DungeonDifficulty getSelectedDifficulty() {
        return selectedDifficulty;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public long getCountdownStartTime() {
        return countdownStartTime;
    }

    public boolean isCountdownActive() {
        return countdownActive;
    }

    // Player management methods

    /**
     * Adds a player to the lobby.
     *
     * @param player The player to add
     * @return true if the player was added successfully
     */
    public boolean addPlayer(Player player) {
        if (playersInLobby.size() >= dungeonTemplate.getMaxPlayers()) {
            return false;
        }
        return playersInLobby.add(player.getUniqueId());
    }

    /**
     * Adds a player by UUID to the lobby.
     *
     * @param playerUuid The UUID of the player to add
     * @return true if the player was added successfully
     */
    public boolean addPlayer(UUID playerUuid) {
        if (playersInLobby.size() >= dungeonTemplate.getMaxPlayers()) {
            return false;
        }
        return playersInLobby.add(playerUuid);
    }

    /**
     * Removes a player from the lobby.
     *
     * @param player The player to remove
     * @return true if the player was removed successfully
     */
    public boolean removePlayer(Player player) {
        readyPlayers.remove(player.getUniqueId());
        return playersInLobby.remove(player.getUniqueId());
    }

    /**
     * Removes a player by UUID from the lobby.
     *
     * @param playerUuid The UUID of the player to remove
     * @return true if the player was removed successfully
     */
    public boolean removePlayer(UUID playerUuid) {
        readyPlayers.remove(playerUuid);
        return playersInLobby.remove(playerUuid);
    }

    /**
     * Sets a player as ready or not ready.
     *
     * @param playerUuid The UUID of the player
     * @param ready true to mark as ready, false to unmark
     * @return true if the ready state was changed successfully
     */
    public boolean setReady(UUID playerUuid, boolean ready) {
        if (!playersInLobby.contains(playerUuid)) {
            return false;
        }

        if (ready) {
            return readyPlayers.add(playerUuid);
        } else {
            return readyPlayers.remove(playerUuid);
        }
    }

    /**
     * Sets a player as ready or not ready.
     *
     * @param player The player
     * @param ready true to mark as ready, false to unmark
     * @return true if the ready state was changed successfully
     */
    public boolean setReady(Player player, boolean ready) {
        return setReady(player.getUniqueId(), ready);
    }

    /**
     * Checks if a player is ready.
     *
     * @param playerUuid The UUID of the player
     * @return true if the player is ready
     */
    public boolean isReady(UUID playerUuid) {
        return readyPlayers.contains(playerUuid);
    }

    /**
     * Checks if a player is ready.
     *
     * @param player The player
     * @return true if the player is ready
     */
    public boolean isReady(Player player) {
        return isReady(player.getUniqueId());
    }

    /**
     * Checks if all players in the lobby are ready.
     * Note: No minimum player requirement - even a solo player can start.
     *
     * @return true if all players are ready
     */
    public boolean areAllReady() {
        if (playersInLobby.isEmpty()) {
            return false;
        }

        return readyPlayers.size() == playersInLobby.size();
    }

    /**
     * Checks if the lobby has a player.
     *
     * @param player The player to check
     * @return true if the player is in the lobby
     */
    public boolean hasPlayer(Player player) {
        return playersInLobby.contains(player.getUniqueId());
    }

    /**
     * Checks if the lobby has a player by UUID.
     *
     * @param playerUuid The UUID of the player to check
     * @return true if the player is in the lobby
     */
    public boolean hasPlayer(UUID playerUuid) {
        return playersInLobby.contains(playerUuid);
    }

    /**
     * Gets the number of players in the lobby.
     *
     * @return The player count
     */
    public int getPlayerCount() {
        return playersInLobby.size();
    }

    /**
     * Gets the number of ready players.
     *
     * @return The ready player count
     */
    public int getReadyPlayerCount() {
        return readyPlayers.size();
    }

    /**
     * Checks if the lobby is empty.
     *
     * @return true if there are no players
     */
    public boolean isEmpty() {
        return playersInLobby.isEmpty();
    }

    /**
     * Checks if the lobby is full.
     *
     * @return true if the lobby has reached max players
     */
    public boolean isFull() {
        return playersInLobby.size() >= dungeonTemplate.getMaxPlayers();
    }

    /**
     * Checks if the lobby meets the minimum player requirement.
     *
     * @return true if there are enough players
     */
    public boolean meetsMinimumPlayers() {
        return playersInLobby.size() >= dungeonTemplate.getMinPlayers();
    }

    // Setting management methods

    /**
     * Sets the selected objective mode.
     *
     * @param mode The objective mode to set
     * @return true if the mode was set successfully
     */
    public boolean setObjectiveMode(ObjectiveMode mode) {
        if (!dungeonTemplate.hasObjectiveMode(mode)) {
            return false;
        }
        this.selectedObjectiveMode = mode;
        return true;
    }

    /**
     * Sets the selected difficulty.
     *
     * @param difficulty The difficulty to set
     */
    public void setDifficulty(DungeonDifficulty difficulty) {
        this.selectedDifficulty = difficulty;
    }

    // Countdown management

    /**
     * Starts the countdown timer.
     */
    public void startCountdown() {
        this.countdownStartTime = System.currentTimeMillis();
        this.countdownActive = true;
    }

    /**
     * Cancels the countdown timer.
     */
    public void cancelCountdown() {
        this.countdownActive = false;
        this.countdownStartTime = 0;
    }

    /**
     * Gets the countdown time remaining in seconds.
     *
     * @param countdownDuration The total countdown duration in seconds
     * @return The time remaining in seconds, or 0 if countdown is not active
     */
    public long getCountdownRemaining(int countdownDuration) {
        if (!countdownActive) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - countdownStartTime) / 1000;
        return Math.max(0, countdownDuration - elapsed);
    }

    /**
     * Checks if the countdown has finished.
     *
     * @param countdownDuration The total countdown duration in seconds
     * @return true if the countdown has finished
     */
    public boolean isCountdownFinished(int countdownDuration) {
        if (!countdownActive) {
            return false;
        }
        return getCountdownRemaining(countdownDuration) <= 0;
    }

    /**
     * Clears all ready statuses.
     */
    public void clearReadyPlayers() {
        readyPlayers.clear();
    }

    @Override
    public String toString() {
        return "DungeonLobby{" +
               "dungeonId='" + dungeonId + '\'' +
               ", instanceId=" + instanceId +
               ", players=" + playersInLobby.size() + "/" + dungeonTemplate.getMaxPlayers() +
               ", ready=" + readyPlayers.size() +
               ", mode=" + selectedObjectiveMode +
               ", difficulty=" + selectedDifficulty +
               '}';
    }
}
