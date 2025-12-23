package com.zenax.armorsets.flow;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.utils.LogHelper;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Executes flow graphs, handling node traversal, delays, and error handling.
 */
public class FlowExecutor {

    private final ArmorSetsPlugin plugin;

    /**
     * Maximum number of nodes to execute in a single run (prevents infinite loops).
     */
    private static final int MAX_NODES = 1000;

    /**
     * Maximum depth for nested flows.
     */
    private static final int MAX_DEPTH = 50;

    public FlowExecutor(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Execute a flow graph.
     *
     * @param graph The flow graph to execute
     * @param effectContext The effect context from the signal
     * @return true if flow executed successfully
     */
    public boolean execute(FlowGraph graph, EffectContext effectContext) {
        FlowContext context = executeWithContext(graph, effectContext);
        return context != null && !context.isCancelled();
    }

    /**
     * Execute a flow graph and return the full context.
     * Use this when you need to check effectsExecuted for cooldown logic.
     *
     * @param graph The flow graph to execute
     * @param effectContext The effect context from the signal
     * @return The FlowContext after execution, or null if flow couldn't start
     */
    public FlowContext executeWithContext(FlowGraph graph, EffectContext effectContext) {
        if (graph == null) {
            LogHelper.debug("[Flow] Cannot execute null flow graph");
            return null;
        }

        FlowNode startNode = graph.getStartNode();
        if (startNode == null) {
            LogHelper.debug("[Flow] Flow '%s' has no start node", graph.getId());
            return null;
        }

        // Create flow context
        FlowContext context = new FlowContext(effectContext);

        // Execute the flow
        executeFromNode(graph, startNode, context, 0);

        return context;
    }

    /**
     * Execute a flow starting from a specific node.
     *
     * @param graph The flow graph
     * @param startNode The node to start from
     * @param context The flow context
     * @param depth Current recursion depth
     * @return true if execution completed without errors
     */
    private boolean executeFromNode(FlowGraph graph, FlowNode startNode, FlowContext context, int depth) {
        if (depth > MAX_DEPTH) {
            handleError(context, "Maximum flow depth exceeded (possible infinite recursion)");
            return false;
        }

        FlowNode currentNode = startNode;
        int nodesExecuted = 0;
        Set<String> visitedInCycle = new HashSet<>();

        while (currentNode != null && !context.isCancelled()) {
            // Safety check for infinite loops
            nodesExecuted++;
            if (nodesExecuted > MAX_NODES) {
                handleError(context, "Maximum node execution limit reached (possible infinite loop)");
                return false;
            }

            // Detect cycles (same node visited twice without a delay/async break)
            String nodeKey = currentNode.getId() + "_" + nodesExecuted;
            if (currentNode.getType() != NodeType.DELAY && currentNode.getType() != NodeType.LOOP) {
                if (visitedInCycle.contains(currentNode.getId())) {
                    handleError(context, "Cycle detected at node: " + currentNode.getDisplayName());
                    return false;
                }
                visitedInCycle.add(currentNode.getId());
            }

            LogHelper.debug("[Flow] Executing node: %s (%s)", currentNode.getDisplayName(), currentNode.getId());

            try {
                // Execute the node and get the output port to follow
                String outputPort = currentNode.execute(context);

                // Check if flow was cancelled during execution
                if (context.isCancelled()) {
                    LogHelper.debug("[Flow] Flow cancelled at node: %s", currentNode.getId());
                    break;
                }

                // Handle delay nodes specially (async continuation)
                if (currentNode.getType() == NodeType.DELAY && outputPort != null) {
                    double delaySeconds = currentNode.getDoubleParam("duration", 1.0);
                    long delayTicks = (long) (delaySeconds * 20);

                    String nextNodeId = currentNode.getConnection(outputPort);
                    FlowNode nextNode = nextNodeId != null ? graph.getNode(nextNodeId) : null;

                    if (nextNode != null) {
                        // Schedule continuation after delay
                        final FlowNode finalNextNode = nextNode;
                        final int newDepth = depth + 1;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                executeFromNode(graph, finalNextNode, context, newDepth);
                            }
                        }.runTaskLater(plugin, delayTicks);
                    }

                    return true; // Async continuation, current execution complete
                }

                // Find next node based on output port
                if (outputPort == null) {
                    // Node returned null, end this branch
                    LogHelper.debug("[Flow] Node %s returned null, ending branch", currentNode.getId());
                    currentNode = null;
                } else {
                    String nextNodeId = currentNode.getConnection(outputPort);
                    currentNode = nextNodeId != null ? graph.getNode(nextNodeId) : null;

                    if (nextNodeId != null && currentNode == null) {
                        LogHelper.debug("[Flow] Warning: Node %s connects to non-existent node %s",
                                currentNode != null ? currentNode.getId() : "unknown", nextNodeId);
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[Flow] Error executing node " + currentNode.getId() + ": " + e.getMessage(), e);
                handleError(context, "Error in node '" + currentNode.getDisplayName() + "': " + e.getMessage());
                return false;
            }
        }

        return !context.isCancelled();
    }

    /**
     * Handle a flow execution error.
     * Shows actionbar notification to player and logs to console.
     */
    private void handleError(FlowContext context, String message) {
        context.setError(message);

        // Notify player via actionbar
        Player player = context.getPlayer();
        if (player != null && player.isOnline()) {
            player.sendActionBar(Component.text("\u00a7c[Sigil] " + message));
        }

        // Log to console
        plugin.getLogger().warning("[Flow] " + message);
    }

    /**
     * Validate a flow before execution.
     *
     * @param graph The flow graph to validate
     * @return true if valid
     */
    public boolean validate(FlowGraph graph) {
        return graph != null && graph.isValid();
    }
}
