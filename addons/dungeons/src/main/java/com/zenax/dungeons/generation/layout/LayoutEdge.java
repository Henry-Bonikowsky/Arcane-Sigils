package com.zenax.dungeons.generation.layout;

/**
 * Represents a connection (corridor) between two rooms in the dungeon graph.
 */
public class LayoutEdge {
    private final LayoutNode nodeA;
    private final LayoutNode nodeB;
    private final boolean isLocked;    // Requires key to pass
    private final String keyId;        // Which key unlocks this

    public LayoutEdge(LayoutNode nodeA, LayoutNode nodeB) {
        this(nodeA, nodeB, false, null);
    }

    public LayoutEdge(LayoutNode nodeA, LayoutNode nodeB, boolean isLocked, String keyId) {
        this.nodeA = nodeA;
        this.nodeB = nodeB;
        this.isLocked = isLocked;
        this.keyId = keyId;

        // Add this edge to both nodes
        nodeA.addEdge(this);
        nodeB.addEdge(this);
    }

    public LayoutNode getNodeA() {
        return nodeA;
    }

    public LayoutNode getNodeB() {
        return nodeB;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public String getKeyId() {
        return keyId;
    }

    /**
     * Gets the other node connected by this edge.
     */
    public LayoutNode getOther(LayoutNode node) {
        if (node == nodeA) return nodeB;
        if (node == nodeB) return nodeA;
        return null;
    }

    /**
     * Checks if this edge connects the given nodes.
     */
    public boolean connects(LayoutNode a, LayoutNode b) {
        return (nodeA == a && nodeB == b) || (nodeA == b && nodeB == a);
    }

    @Override
    public String toString() {
        return "LayoutEdge{" + nodeA.getId() + " <-> " + nodeB.getId() +
               (isLocked ? " [LOCKED:" + keyId + "]" : "") + "}";
    }
}
