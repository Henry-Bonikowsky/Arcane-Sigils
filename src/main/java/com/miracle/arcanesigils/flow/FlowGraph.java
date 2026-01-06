package com.miracle.arcanesigils.flow;

import java.util.*;

/**
 * A flow graph containing all nodes and their connections.
 * Represents a complete visual script for a sigil ability.
 */
public class FlowGraph {

    /**
     * Unique identifier for this flow.
     */
    private String id;

    /**
     * Human-readable name for the flow.
     */
    private String name;

    /**
     * Description of what this flow does.
     */
    private String description;

    /**
     * All nodes in the flow, keyed by node ID.
     */
    private final Map<String, FlowNode> nodes = new LinkedHashMap<>();

    /**
     * The ID of the start node.
     */
    private String startNodeId;

    /**
     * Version number for tracking changes.
     */
    private int version = 1;

    public FlowGraph(String id) {
        this.id = id;
        this.name = id;
    }

    // ============ Node Management ============

    /**
     * Add a node to the flow.
     */
    public void addNode(FlowNode node) {
        nodes.put(node.getId(), node);
    }

    /**
     * Remove a node from the flow.
     * Also removes all connections to/from this node.
     */
    public void removeNode(String nodeId) {
        nodes.remove(nodeId);

        // Remove connections pointing to this node
        for (FlowNode node : nodes.values()) {
            node.getConnections().entrySet().removeIf(entry -> nodeId.equals(entry.getValue()));
        }

        // Clear start node if it was removed
        if (nodeId.equals(startNodeId)) {
            startNodeId = null;
        }
    }

    /**
     * Get a node by ID.
     */
    public FlowNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * Get all nodes in the flow.
     */
    public Collection<FlowNode> getNodes() {
        return nodes.values();
    }

    /**
     * Get all node IDs.
     */
    public Set<String> getNodeIds() {
        return nodes.keySet();
    }

    /**
     * Check if a node exists.
     */
    public boolean hasNode(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    /**
     * Get the start node.
     */
    public FlowNode getStartNode() {
        return startNodeId != null ? nodes.get(startNodeId) : null;
    }

    /**
     * Set the start node ID.
     */
    public void setStartNodeId(String nodeId) {
        this.startNodeId = nodeId;
    }

    /**
     * Generate a unique node ID.
     */
    public String generateNodeId() {
        int counter = 1;
        while (nodes.containsKey("node_" + counter)) {
            counter++;
        }
        return "node_" + counter;
    }

    // ============ Connection Management ============

    /**
     * Connect two nodes.
     *
     * @param sourceNodeId The source node ID
     * @param outputPort The output port name on the source node
     * @param targetNodeId The target node ID
     * @return true if connection was made
     */
    public boolean connect(String sourceNodeId, String outputPort, String targetNodeId) {
        FlowNode source = nodes.get(sourceNodeId);
        FlowNode target = nodes.get(targetNodeId);

        if (source == null || target == null) {
            return false;
        }

        // Check if port is valid for source node
        if (!source.getOutputPorts().contains(outputPort)) {
            return false;
        }

        source.setConnection(outputPort, targetNodeId);
        return true;
    }

    /**
     * Disconnect a node's output port.
     */
    public void disconnect(String sourceNodeId, String outputPort) {
        FlowNode source = nodes.get(sourceNodeId);
        if (source != null) {
            source.removeConnection(outputPort);
        }
    }

    /**
     * Get all nodes that connect to the given node.
     */
    public List<FlowNode> getIncomingNodes(String nodeId) {
        List<FlowNode> incoming = new ArrayList<>();
        for (FlowNode node : nodes.values()) {
            if (node.getConnections().containsValue(nodeId)) {
                incoming.add(node);
            }
        }
        return incoming;
    }

    // ============ Validation ============

    /**
     * Validate the entire flow.
     *
     * @return List of validation errors (empty if valid)
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        // Must have a start node
        if (startNodeId == null || !nodes.containsKey(startNodeId)) {
            errors.add("Flow has no start node");
        }

        // Validate each node
        for (FlowNode node : nodes.values()) {
            List<String> nodeErrors = node.validate();
            for (String error : nodeErrors) {
                errors.add(node.getDisplayName() + " (" + node.getId() + "): " + error);
            }
        }

        // Check for unreachable nodes
        Set<String> reachable = findReachableNodes();
        for (String nodeId : nodes.keySet()) {
            if (!reachable.contains(nodeId) && !nodeId.equals(startNodeId)) {
                FlowNode node = nodes.get(nodeId);
                errors.add(node.getDisplayName() + " (" + nodeId + "): Node is not reachable from start");
            }
        }

        return errors;
    }

    /**
     * Find all nodes reachable from the start node.
     */
    private Set<String> findReachableNodes() {
        Set<String> reachable = new HashSet<>();
        if (startNodeId == null) return reachable;

        Queue<String> queue = new LinkedList<>();
        queue.add(startNodeId);
        reachable.add(startNodeId);

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            FlowNode node = nodes.get(nodeId);
            if (node == null) continue;

            for (String targetId : node.getConnections().values()) {
                if (targetId != null && !reachable.contains(targetId)) {
                    reachable.add(targetId);
                    queue.add(targetId);
                }
            }
        }

        return reachable;
    }

    /**
     * Check if the flow is valid (no errors).
     */
    public boolean isValid() {
        return validate().isEmpty();
    }

    // ============ Utility ============

    /**
     * Get the node at a specific grid position.
     */
    public FlowNode getNodeAt(int gridX, int gridY) {
        for (FlowNode node : nodes.values()) {
            if (node.getGridX() == gridX && node.getGridY() == gridY) {
                return node;
            }
        }
        return null;
    }

    /**
     * Check if a grid position is occupied.
     */
    public boolean isPositionOccupied(int gridX, int gridY) {
        return getNodeAt(gridX, gridY) != null;
    }

    /**
     * Get total node count.
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * Clear all nodes from the flow.
     */
    public void clear() {
        nodes.clear();
        startNodeId = null;
    }

    // ============ Getters/Setters ============

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStartNodeId() {
        return startNodeId;
    }

    public int getVersion() {
        return version;
    }

    public void incrementVersion() {
        this.version++;
    }

    // ============ Cloning ============

    /**
     * Create a deep copy of this flow graph.
     * All nodes are cloned, preserving structure and connections.
     *
     * @return A new FlowGraph with copied data
     */
    public FlowGraph deepCopy() {
        FlowGraph copy = new FlowGraph(this.id);
        copy.name = this.name;
        copy.description = this.description;
        copy.startNodeId = this.startNodeId;
        copy.version = this.version;

        // Deep copy all nodes
        for (FlowNode node : this.nodes.values()) {
            copy.addNode(node.deepCopy());
        }

        return copy;
    }
}
