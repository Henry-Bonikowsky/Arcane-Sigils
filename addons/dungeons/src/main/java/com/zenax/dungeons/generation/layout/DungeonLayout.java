package com.zenax.dungeons.generation.layout;

import com.zenax.dungeons.generation.room.RoomType;

import java.util.*;

/**
 * Represents the abstract graph structure of a dungeon before physical generation.
 * This is the "virtual map" that defines room connectivity, types, and relationships.
 */
public class DungeonLayout {
    private final List<LayoutNode> nodes;
    private final List<LayoutEdge> edges;
    private final long seed;

    private LayoutNode spawnNode;
    private LayoutNode bossNode;
    private List<LayoutNode> mainPath;

    public DungeonLayout(long seed) {
        this.seed = seed;
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.mainPath = new ArrayList<>();
    }

    public long getSeed() {
        return seed;
    }

    public List<LayoutNode> getNodes() {
        return nodes;
    }

    public List<LayoutEdge> getEdges() {
        return edges;
    }

    public LayoutNode getSpawnNode() {
        return spawnNode;
    }

    public void setSpawnNode(LayoutNode spawnNode) {
        this.spawnNode = spawnNode;
    }

    public LayoutNode getBossNode() {
        return bossNode;
    }

    public void setBossNode(LayoutNode bossNode) {
        this.bossNode = bossNode;
    }

    public List<LayoutNode> getMainPath() {
        return mainPath;
    }

    public void setMainPath(List<LayoutNode> mainPath) {
        this.mainPath = mainPath;
        for (LayoutNode node : mainPath) {
            node.setMainPath(true);
        }
    }

    /**
     * Adds a node to the layout.
     */
    public LayoutNode addNode(RoomType type) {
        LayoutNode node = new LayoutNode(nodes.size(), type);
        node.setDimensionsForType();
        nodes.add(node);
        return node;
    }

    /**
     * Connects two nodes with an edge.
     */
    public LayoutEdge connect(LayoutNode a, LayoutNode b) {
        return connect(a, b, false, null);
    }

    /**
     * Connects two nodes with a potentially locked edge.
     */
    public LayoutEdge connect(LayoutNode a, LayoutNode b, boolean locked, String keyId) {
        LayoutEdge edge = new LayoutEdge(a, b, locked, keyId);
        edges.add(edge);
        return edge;
    }

    /**
     * Gets a node by its ID.
     */
    public LayoutNode getNode(int id) {
        if (id >= 0 && id < nodes.size()) {
            return nodes.get(id);
        }
        return null;
    }

    /**
     * Gets all nodes of a specific type.
     */
    public List<LayoutNode> getNodesByType(RoomType type) {
        List<LayoutNode> result = new ArrayList<>();
        for (LayoutNode node : nodes) {
            if (node.getType() == type) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Calculates depth (distance from spawn) for all nodes using BFS.
     */
    public void calculateDepths() {
        if (spawnNode == null) return;

        // Reset all depths
        for (LayoutNode node : nodes) {
            node.setDepth(Integer.MAX_VALUE);
        }

        // BFS from spawn
        Queue<LayoutNode> queue = new LinkedList<>();
        spawnNode.setDepth(0);
        queue.add(spawnNode);

        while (!queue.isEmpty()) {
            LayoutNode current = queue.poll();
            int nextDepth = current.getDepth() + 1;

            for (LayoutNode neighbor : current.getNeighbors()) {
                if (neighbor.getDepth() > nextDepth) {
                    neighbor.setDepth(nextDepth);
                    queue.add(neighbor);
                }
            }
        }
    }

    /**
     * Gets the maximum depth in the dungeon.
     */
    public int getMaxDepth() {
        int max = 0;
        for (LayoutNode node : nodes) {
            if (node.getDepth() != Integer.MAX_VALUE && node.getDepth() > max) {
                max = node.getDepth();
            }
        }
        return max;
    }

    /**
     * Validates that all nodes are reachable from spawn.
     */
    public boolean isFullyConnected() {
        if (spawnNode == null || nodes.isEmpty()) return false;

        Set<LayoutNode> visited = new HashSet<>();
        Queue<LayoutNode> queue = new LinkedList<>();
        queue.add(spawnNode);
        visited.add(spawnNode);

        while (!queue.isEmpty()) {
            LayoutNode current = queue.poll();
            for (LayoutNode neighbor : current.getNeighbors()) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return visited.size() == nodes.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DungeonLayout{nodes=").append(nodes.size());
        sb.append(", edges=").append(edges.size());
        sb.append(", maxDepth=").append(getMaxDepth());
        sb.append(", connected=").append(isFullyConnected());
        sb.append("}\n");

        for (LayoutNode node : nodes) {
            sb.append("  ").append(node).append("\n");
        }

        return sb.toString();
    }
}
