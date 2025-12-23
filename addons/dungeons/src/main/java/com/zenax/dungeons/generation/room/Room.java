package com.zenax.dungeons.generation.room;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a room in a dungeon with all its associated data.
 * Rooms are the building blocks of dungeon generation.
 */
public class Room {
    private final String id;
    private final RoomType type;
    private final BoundingBox bounds;
    private final Location center;

    private final List<RoomConnection> connections;
    private final List<Location> spawnPoints;
    private final List<Location> chestLocations;

    private boolean cleared;
    private final List<UUID> mobIds;

    /**
     * Creates a new room.
     *
     * @param id Unique identifier for this room
     * @param type The type of room
     * @param bounds The bounding box defining the room's space
     * @param center The center location of the room
     */
    public Room(String id, RoomType type, BoundingBox bounds, Location center) {
        this.id = id;
        this.type = type;
        this.bounds = bounds.clone();
        this.center = center.clone();
        this.connections = new ArrayList<>();
        this.spawnPoints = new ArrayList<>();
        this.chestLocations = new ArrayList<>();
        this.cleared = false;
        this.mobIds = new ArrayList<>();
    }

    /**
     * Gets the room's unique identifier.
     *
     * @return The room ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the room type.
     *
     * @return The room type
     */
    public RoomType getType() {
        return type;
    }

    /**
     * Gets the room's bounding box.
     *
     * @return A clone of the room's bounds
     */
    public BoundingBox getBounds() {
        return bounds.clone();
    }

    /**
     * Gets the room's center location.
     *
     * @return A clone of the center location
     */
    public Location getCenter() {
        return center.clone();
    }

    /**
     * Gets the list of connections to other rooms.
     *
     * @return The list of connections
     */
    public List<RoomConnection> getConnections() {
        return connections;
    }

    /**
     * Adds a connection to another room.
     *
     * @param connection The connection to add
     */
    public void addConnection(RoomConnection connection) {
        connections.add(connection);
    }

    /**
     * Gets the list of mob spawn points in this room.
     *
     * @return The list of spawn points
     */
    public List<Location> getSpawnPoints() {
        return spawnPoints;
    }

    /**
     * Adds a spawn point to this room.
     *
     * @param location The spawn point location
     */
    public void addSpawnPoint(Location location) {
        spawnPoints.add(location.clone());
    }

    /**
     * Gets the list of chest locations in this room.
     *
     * @return The list of chest locations
     */
    public List<Location> getChestLocations() {
        return chestLocations;
    }

    /**
     * Adds a chest location to this room.
     *
     * @param location The chest location
     */
    public void addChestLocation(Location location) {
        chestLocations.add(location.clone());
    }

    /**
     * Checks if the room has been cleared of all mobs.
     *
     * @return true if the room is cleared
     */
    public boolean isCleared() {
        return cleared;
    }

    /**
     * Sets whether the room is cleared.
     *
     * @param cleared true to mark as cleared
     */
    public void setCleared(boolean cleared) {
        this.cleared = cleared;
    }

    /**
     * Gets the list of mob UUIDs spawned in this room.
     *
     * @return The list of mob IDs
     */
    public List<UUID> getMobIds() {
        return mobIds;
    }

    /**
     * Adds a mob ID to this room.
     *
     * @param mobId The mob's UUID
     */
    public void addMobId(UUID mobId) {
        mobIds.add(mobId);
    }

    /**
     * Removes a mob ID from this room.
     *
     * @param mobId The mob's UUID
     */
    public void removeMobId(UUID mobId) {
        mobIds.remove(mobId);
    }

    /**
     * Checks if the location is within this room's bounds.
     *
     * @param location The location to check
     * @return true if the location is inside the room
     */
    public boolean contains(Location location) {
        return bounds.contains(location.toVector());
    }

    /**
     * Gets the width of the room (X axis).
     *
     * @return The room width
     */
    public double getWidth() {
        return bounds.getWidthX();
    }

    /**
     * Gets the height of the room (Y axis).
     *
     * @return The room height
     */
    public double getHeight() {
        return bounds.getHeight();
    }

    /**
     * Gets the depth of the room (Z axis).
     *
     * @return The room depth
     */
    public double getDepth() {
        return bounds.getWidthZ();
    }

    @Override
    public String toString() {
        return "Room{" +
               "id='" + id + '\'' +
               ", type=" + type +
               ", bounds=" + bounds +
               ", cleared=" + cleared +
               ", mobs=" + mobIds.size() +
               '}';
    }
}
