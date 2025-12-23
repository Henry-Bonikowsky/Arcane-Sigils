package com.zenax.dungeons.dungeon;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an active instance of a dungeon.
 * This class tracks the state and progress of a specific dungeon run.
 */
public class DungeonInstance {
    private final UUID instanceId;
    private final Dungeon template;
    private DungeonState state;
    private final DungeonDifficulty difficulty;
    private final ObjectiveMode objectiveMode;

    // Player management
    private final Set<UUID> playerUuids;
    private final Map<UUID, Integer> playerDeaths;

    // World and location data
    private final World world;
    private final Location spawnPoint;

    // Room and mob tracking
    private final Map<String, Location> rooms;
    private final List<UUID> activeMobs;

    // Timing
    private final long startTime;
    private final int timeLimit; // in seconds, 0 for unlimited

    // Objectives tracking
    private final Map<String, Boolean> objectives;
    private UUID bossUuid;

    /**
     * Creates a new dungeon instance.
     *
     * @param template The dungeon template this instance is based on
     * @param difficulty The difficulty level for this instance
     * @param objectiveMode The objective mode for this instance
     * @param world The world this instance is in
     * @param spawnPoint The spawn point for players
     */
    public DungeonInstance(Dungeon template, DungeonDifficulty difficulty, ObjectiveMode objectiveMode,
                          World world, Location spawnPoint) {
        this.instanceId = UUID.randomUUID();
        this.template = template;
        this.state = DungeonState.GENERATING;
        this.difficulty = difficulty;
        this.objectiveMode = objectiveMode;
        this.playerUuids = ConcurrentHashMap.newKeySet();
        this.playerDeaths = new ConcurrentHashMap<>();
        this.world = world;
        this.spawnPoint = spawnPoint.clone();
        this.rooms = new ConcurrentHashMap<>();
        this.activeMobs = Collections.synchronizedList(new ArrayList<>());
        this.startTime = System.currentTimeMillis();
        this.timeLimit = template.getTimeLimit();
        this.objectives = new ConcurrentHashMap<>();
        this.bossUuid = null;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public Dungeon getTemplate() {
        return template;
    }

    public DungeonState getState() {
        return state;
    }

    public void setState(DungeonState state) {
        this.state = state;
    }

    public DungeonDifficulty getDifficulty() {
        return difficulty;
    }

    public ObjectiveMode getObjectiveMode() {
        return objectiveMode;
    }

    public Set<UUID> getPlayerUuids() {
        return new HashSet<>(playerUuids);
    }

    public World getWorld() {
        return world;
    }

    public Location getSpawnPoint() {
        return spawnPoint.clone();
    }

    public Map<String, Location> getRooms() {
        return new HashMap<>(rooms);
    }

    public List<UUID> getActiveMobs() {
        return new ArrayList<>(activeMobs);
    }

    public long getStartTime() {
        return startTime;
    }

    public int getTimeLimit() {
        return timeLimit;
    }

    public Map<String, Boolean> getObjectives() {
        return new HashMap<>(objectives);
    }

    public UUID getBossUuid() {
        return bossUuid;
    }

    public void setBossUuid(UUID bossUuid) {
        this.bossUuid = bossUuid;
    }

    /**
     * Adds a player to this dungeon instance.
     *
     * @param player The player to add
     * @return true if the player was added successfully
     */
    public boolean addPlayer(Player player) {
        if (playerUuids.size() >= template.getMaxPlayers()) {
            return false;
        }
        if (state != DungeonState.LOBBY && state != DungeonState.ACTIVE) {
            return false;
        }
        boolean added = playerUuids.add(player.getUniqueId());
        if (added) {
            playerDeaths.put(player.getUniqueId(), 0);
        }
        return added;
    }

    /**
     * Removes a player from this dungeon instance.
     *
     * @param player The player to remove
     * @return true if the player was removed successfully
     */
    public boolean removePlayer(Player player) {
        playerDeaths.remove(player.getUniqueId());
        return playerUuids.remove(player.getUniqueId());
    }

    /**
     * Removes a player by UUID from this dungeon instance.
     *
     * @param uuid The UUID of the player to remove
     * @return true if the player was removed successfully
     */
    public boolean removePlayer(UUID uuid) {
        playerDeaths.remove(uuid);
        return playerUuids.remove(uuid);
    }

    /**
     * Records a death for a player.
     *
     * @param player The player who died
     */
    public void recordDeath(Player player) {
        playerDeaths.merge(player.getUniqueId(), 1, Integer::sum);
    }

    /**
     * Gets the number of deaths for a player.
     *
     * @param player The player
     * @return The number of deaths
     */
    public int getDeaths(Player player) {
        return playerDeaths.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Checks if the dungeon is complete based on the objective mode.
     *
     * @return true if the dungeon is complete
     */
    public boolean isComplete() {
        if (state.isTerminal()) {
            return state == DungeonState.COMPLETED;
        }

        switch (objectiveMode) {
            case BOSS_KILL:
                return isBossDefeated();
            case OBJECTIVES:
                return areAllObjectivesComplete();
            default:
                return false;
        }
    }

    /**
     * Checks if the boss has been defeated.
     *
     * @return true if the boss is dead or null
     */
    public boolean isBossDefeated() {
        if (bossUuid == null) {
            return false;
        }
        Entity boss = world.getEntity(bossUuid);
        return boss == null || boss.isDead();
    }

    /**
     * Checks if all objectives are complete.
     *
     * @return true if all objectives are complete
     */
    public boolean areAllObjectivesComplete() {
        if (objectives.isEmpty()) {
            return false;
        }
        for (Boolean completed : objectives.values()) {
            if (!completed) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the time remaining in seconds.
     *
     * @return The time remaining, or -1 if there is no time limit
     */
    public long getTimeRemaining() {
        if (timeLimit <= 0) {
            return -1;
        }
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        return Math.max(0, timeLimit - elapsed);
    }

    /**
     * Checks if the time limit has been exceeded.
     *
     * @return true if the time limit has been exceeded
     */
    public boolean isTimeExpired() {
        if (timeLimit <= 0) {
            return false;
        }
        return getTimeRemaining() <= 0;
    }

    /**
     * Adds a room to this dungeon instance.
     *
     * @param roomId The room identifier
     * @param location The location of the room
     */
    public void addRoom(String roomId, Location location) {
        rooms.put(roomId, location.clone());
    }

    /**
     * Gets a room location by its ID.
     *
     * @param roomId The room identifier
     * @return The room location, or null if not found
     */
    public Location getRoom(String roomId) {
        Location loc = rooms.get(roomId);
        return loc != null ? loc.clone() : null;
    }

    /**
     * Adds an active mob to this dungeon instance.
     *
     * @param mobUuid The UUID of the mob
     */
    public void addActiveMob(UUID mobUuid) {
        activeMobs.add(mobUuid);
    }

    /**
     * Removes an active mob from this dungeon instance.
     *
     * @param mobUuid The UUID of the mob
     */
    public void removeActiveMob(UUID mobUuid) {
        activeMobs.remove(mobUuid);
    }

    /**
     * Adds an objective to track.
     *
     * @param objectiveId The objective identifier
     * @param completed Whether the objective is initially completed
     */
    public void addObjective(String objectiveId, boolean completed) {
        objectives.put(objectiveId, completed);
    }

    /**
     * Marks an objective as completed.
     *
     * @param objectiveId The objective identifier
     */
    public void completeObjective(String objectiveId) {
        objectives.put(objectiveId, true);
    }

    /**
     * Checks if an objective is completed.
     *
     * @param objectiveId The objective identifier
     * @return true if the objective is completed
     */
    public boolean isObjectiveComplete(String objectiveId) {
        return objectives.getOrDefault(objectiveId, false);
    }

    /**
     * Gets the number of completed objectives.
     *
     * @return The number of completed objectives
     */
    public int getCompletedObjectivesCount() {
        return (int) objectives.values().stream().filter(Boolean::booleanValue).count();
    }

    /**
     * Gets the total number of objectives.
     *
     * @return The total number of objectives
     */
    public int getTotalObjectivesCount() {
        return objectives.size();
    }

    /**
     * Checks if the dungeon instance is empty (no players).
     *
     * @return true if there are no players
     */
    public boolean isEmpty() {
        return playerUuids.isEmpty();
    }

    /**
     * Checks if the player is in this dungeon instance.
     *
     * @param player The player to check
     * @return true if the player is in this instance
     */
    public boolean hasPlayer(Player player) {
        return playerUuids.contains(player.getUniqueId());
    }

    /**
     * Checks if the player UUID is in this dungeon instance.
     *
     * @param uuid The UUID to check
     * @return true if the player is in this instance
     */
    public boolean hasPlayer(UUID uuid) {
        return playerUuids.contains(uuid);
    }

    /**
     * Gets the number of players in this instance.
     *
     * @return The player count
     */
    public int getPlayerCount() {
        return playerUuids.size();
    }

    @Override
    public String toString() {
        return "DungeonInstance{" +
               "id=" + instanceId +
               ", template=" + template.getId() +
               ", state=" + state +
               ", difficulty=" + difficulty +
               ", players=" + playerUuids.size() +
               '}';
    }
}
