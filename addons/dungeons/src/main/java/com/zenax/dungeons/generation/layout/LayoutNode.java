package com.zenax.dungeons.generation.layout;

import com.zenax.dungeons.generation.room.RoomType;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a room in the abstract dungeon graph before physical generation.
 * Contains logical information about the room's role and connections.
 */
public class LayoutNode {
    private final int id;
    private final RoomType type;
    private final List<LayoutEdge> edges;

    // Graph properties
    private int depth;           // Distance from spawn (0 = spawn room)
    private boolean isMainPath;  // Part of critical path to boss
    private boolean isSecret;    // Hidden room

    // Physical properties (set during placement)
    private int gridX;
    private int gridZ;
    private int width;
    private int height;
    private int length;

    public LayoutNode(int id, RoomType type) {
        this.id = id;
        this.type = type;
        this.edges = new ArrayList<>();
        this.depth = 0;
        this.isMainPath = false;
        this.isSecret = false;
        this.width = 10;
        this.height = 6;
        this.length = 10;
    }

    public int getId() {
        return id;
    }

    public RoomType getType() {
        return type;
    }

    public List<LayoutEdge> getEdges() {
        return edges;
    }

    public void addEdge(LayoutEdge edge) {
        edges.add(edge);
    }

    public List<LayoutNode> getNeighbors() {
        List<LayoutNode> neighbors = new ArrayList<>();
        for (LayoutEdge edge : edges) {
            neighbors.add(edge.getOther(this));
        }
        return neighbors;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public boolean isMainPath() {
        return isMainPath;
    }

    public void setMainPath(boolean mainPath) {
        isMainPath = mainPath;
    }

    public boolean isSecret() {
        return isSecret;
    }

    public void setSecret(boolean secret) {
        isSecret = secret;
    }

    public int getGridX() {
        return gridX;
    }

    public void setGridX(int gridX) {
        this.gridX = gridX;
    }

    public int getGridZ() {
        return gridZ;
    }

    public void setGridZ(int gridZ) {
        this.gridZ = gridZ;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    /**
     * Sets room dimensions based on room type.
     */
    public void setDimensionsForType() {
        switch (type) {
            case SPAWN:
                width = 8;
                height = 5;
                length = 8;
                break;
            case COMBAT:
                width = 12 + (int)(Math.random() * 6);
                height = 6;
                length = 12 + (int)(Math.random() * 6);
                break;
            case TREASURE:
                width = 8;
                height = 5;
                length = 8;
                break;
            case PUZZLE:
                width = 10;
                height = 6;
                length = 10;
                break;
            case BOSS:
                width = 20;
                height = 10;
                length = 20;
                break;
            default:
                width = 10;
                height = 6;
                length = 10;
        }
    }

    @Override
    public String toString() {
        return "LayoutNode{id=" + id + ", type=" + type + ", depth=" + depth + ", mainPath=" + isMainPath + "}";
    }
}
