package com.zenax.dungeons.generation.room;

import org.bukkit.Location;

/**
 * Represents a connection between two rooms in a dungeon.
 * Connections define how rooms are linked together.
 */
public class RoomConnection {
    private final Room fromRoom;
    private final Room toRoom;
    private final Location connectionPoint;
    private final Location doorLocation;

    /**
     * Creates a new room connection.
     *
     * @param fromRoom The source room
     * @param toRoom The destination room
     * @param connectionPoint The point where the connection starts
     * @param doorLocation The location of the door/entrance
     */
    public RoomConnection(Room fromRoom, Room toRoom, Location connectionPoint, Location doorLocation) {
        this.fromRoom = fromRoom;
        this.toRoom = toRoom;
        this.connectionPoint = connectionPoint.clone();
        this.doorLocation = doorLocation != null ? doorLocation.clone() : null;
    }

    /**
     * Gets the source room.
     *
     * @return The room this connection starts from
     */
    public Room getFromRoom() {
        return fromRoom;
    }

    /**
     * Gets the destination room.
     *
     * @return The room this connection leads to
     */
    public Room getToRoom() {
        return toRoom;
    }

    /**
     * Gets the connection point location.
     *
     * @return A clone of the connection point
     */
    public Location getConnectionPoint() {
        return connectionPoint.clone();
    }

    /**
     * Gets the door location.
     *
     * @return A clone of the door location, or null if no door
     */
    public Location getDoorLocation() {
        return doorLocation != null ? doorLocation.clone() : null;
    }

    /**
     * Checks if this connection has a door.
     *
     * @return true if a door location is set
     */
    public boolean hasDoor() {
        return doorLocation != null;
    }

    /**
     * Gets the other room in this connection.
     *
     * @param room One of the rooms in the connection
     * @return The other room, or null if the given room is not in this connection
     */
    public Room getOtherRoom(Room room) {
        if (room == fromRoom) {
            return toRoom;
        } else if (room == toRoom) {
            return fromRoom;
        }
        return null;
    }

    /**
     * Checks if this connection connects to the given room.
     *
     * @param room The room to check
     * @return true if the room is part of this connection
     */
    public boolean connects(Room room) {
        return room == fromRoom || room == toRoom;
    }

    /**
     * Checks if this connection links the two given rooms.
     *
     * @param room1 First room
     * @param room2 Second room
     * @return true if this connection links these rooms
     */
    public boolean connectsRooms(Room room1, Room room2) {
        return (fromRoom == room1 && toRoom == room2) ||
               (fromRoom == room2 && toRoom == room1);
    }

    @Override
    public String toString() {
        return "RoomConnection{" +
               "from='" + fromRoom.getId() + '\'' +
               ", to='" + toRoom.getId() + '\'' +
               ", hasDoor=" + hasDoor() +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RoomConnection that = (RoomConnection) o;
        return connectsRooms(that.fromRoom, that.toRoom);
    }

    @Override
    public int hashCode() {
        // Hash is order-independent for the rooms
        int result = fromRoom.hashCode();
        result = 31 * result + toRoom.hashCode();
        return result;
    }
}
