package com.zenax.armorsets.flow;

import com.zenax.armorsets.flow.nodes.*;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serializes and deserializes FlowGraph objects to/from YAML configuration.
 *
 * PRIMARY API (use these):
 * - deserializeFlowConfigs(List) - loads flows: list format
 * - deserializeFlowConfig(ConfigurationSection) - loads flow: single format
 * - flowConfigsToMapList(List) - saves flows to YAML
 * - flowConfigToMap(FlowConfig) - saves single flow to YAML
 *
 * INTERNAL (used by primary API):
 * - fromMap(Map), nodeFromMap(Map) - parsing helpers
 * - nodeToMap(FlowNode) - serialization helper for saving
 *
 * NOTE: For cloning FlowGraph/FlowConfig, use the deepCopy() methods instead.
 */
public class FlowSerializer {

    /**
     * Deserialize a FlowGraph from a configuration section.
     * @deprecated Use deserializeFlowConfig() instead for flow: format
     */
    @Deprecated
    public static FlowGraph deserialize(ConfigurationSection section) {
        if (section == null) return null;

        String id = section.getString("id", "unnamed");
        FlowGraph graph = new FlowGraph(id);

        graph.setName(section.getString("name", id));
        graph.setDescription(section.getString("description", ""));

        // Deserialize nodes
        ConfigurationSection nodesSection = section.getConfigurationSection("nodes");
        if (nodesSection != null) {
            for (String nodeId : nodesSection.getKeys(false)) {
                FlowNode node = deserializeNode(nodeId, nodesSection.getConfigurationSection(nodeId));
                if (node != null) {
                    graph.addNode(node);
                }
            }
        }

        // Set start node after all nodes are loaded
        graph.setStartNodeId(section.getString("startNodeId"));

        return graph;
    }

    /**
     * Deserialize a single node.
     */
    private static FlowNode deserializeNode(String nodeId, ConfigurationSection section) {
        if (section == null) return null;

        String typeStr = section.getString("type", "EFFECT");
        NodeType type;
        try {
            type = NodeType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        // Create appropriate node type
        FlowNode node = createNode(type, nodeId, section);
        if (node == null) return null;

        // Load common properties
        node.setDisplayName(section.getString("displayName", type.getDisplayName()));
        node.setGridX(section.getInt("gridX", 0));
        node.setGridY(section.getInt("gridY", 0));

        // Load parameters
        ConfigurationSection paramsSection = section.getConfigurationSection("params");
        if (paramsSection != null) {
            for (String key : paramsSection.getKeys(false)) {
                node.setParam(key, paramsSection.get(key));
            }
        }

        // Load connections
        ConfigurationSection connectionsSection = section.getConfigurationSection("connections");
        if (connectionsSection != null) {
            for (String port : connectionsSection.getKeys(false)) {
                node.setConnection(port, connectionsSection.getString(port));
            }
        }

        // Load tier values (deprecated - will be auto-migrated to sigil TierScalingConfig)
        ConfigurationSection tierSection = section.getConfigurationSection("tierValues");
        if (tierSection != null && !tierSection.getKeys(false).isEmpty()) {
            for (String paramName : tierSection.getKeys(false)) {
                List<Double> values = tierSection.getDoubleList(paramName);
                if (!values.isEmpty()) {
                    node.setTierValues(paramName, values);
                }
            }
        }

        return node;
    }

    /**
     * Create a node of the appropriate type.
     */
    private static FlowNode createNode(NodeType type, String nodeId, ConfigurationSection section) {
        return switch (type) {
            case START -> new StartNode(nodeId);
            case END -> new EndNode(nodeId);
            case EFFECT -> {
                EffectNode node = new EffectNode(nodeId);
                node.setEffectType(section.getString("effectType", ""));
                yield node;
            }
            case CONDITION -> {
                ConditionNode node = new ConditionNode(nodeId);
                node.setCondition(section.getString("condition", ""));
                yield node;
            }
            case DELAY -> new DelayNode(nodeId);
            case LOOP -> new LoopNode(nodeId);
            case RANDOM -> new RandomNode(nodeId);
            case VARIABLE -> new VariableNode(nodeId);
            case TARGET -> new TargetNode(nodeId);
            case MATH -> new MathNode(nodeId);
            case SKIP_COOLDOWN -> new SkipCooldownNode(nodeId);
        };
    }

    /**
     * Serialize a node to a Map (used by flowConfigToMap for saving).
     */
    private static Map<String, Object> nodeToMap(FlowNode node) {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("id", node.getId());
        map.put("type", node.getType().name());

        // Position
        map.put("x", node.getGridX());
        map.put("y", node.getGridY());

        // Type-specific
        if (node instanceof EffectNode effectNode && effectNode.getEffectType() != null) {
            map.put("effect", effectNode.getEffectType());
        }
        if (node instanceof ConditionNode conditionNode && conditionNode.getCondition() != null) {
            map.put("condition", conditionNode.getCondition());
        }

        // Parameters
        if (!node.getParams().isEmpty()) {
            map.put("params", new LinkedHashMap<>(node.getParams()));
        }

        // Connections (compact format)
        if (!node.getConnections().isEmpty()) {
            if (node.getConnections().size() == 1 && node.getConnections().containsKey("next")) {
                map.put("next", node.getConnection("next"));
            } else {
                Map<String, String> connections = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : node.getConnections().entrySet()) {
                    if (entry.getValue() != null) {
                        connections.put(entry.getKey(), entry.getValue());
                    }
                }
                if (!connections.isEmpty()) {
                    map.put("connections", connections);
                }
            }
        }

        // Tier values are now stored in TierScalingConfig (unified system)
        // Per-node tierValues are deprecated - don't serialize them

        return map;
    }

    /**
     * Deserialize a FlowGraph from a Map.
     */
    @SuppressWarnings("unchecked")
    public static FlowGraph fromMap(Map<String, Object> map) {
        if (map == null) return null;

        String id = (String) map.getOrDefault("id", "unnamed");
        FlowGraph graph = new FlowGraph(id);

        graph.setName((String) map.getOrDefault("name", id));
        graph.setDescription((String) map.get("description"));
        graph.setStartNodeId((String) map.get("startNodeId"));

        // Nodes
        Object nodesObj = map.get("nodes");
        if (nodesObj instanceof List<?> nodesList) {
            for (Object nodeObj : nodesList) {
                if (nodeObj instanceof Map<?, ?> nodeMap) {
                    FlowNode node = nodeFromMap((Map<String, Object>) nodeMap);
                    if (node != null) {
                        graph.addNode(node);
                    }
                }
            }
        }

        return graph;
    }

    /**
     * Deserialize a node from a Map.
     */
    @SuppressWarnings("unchecked")
    private static FlowNode nodeFromMap(Map<String, Object> map) {
        String nodeId = (String) map.get("id");
        String typeStr = (String) map.getOrDefault("type", "EFFECT");

        NodeType type;
        try {
            type = NodeType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        FlowNode node = switch (type) {
            case START -> new StartNode(nodeId);
            case END -> new EndNode(nodeId);
            case EFFECT -> {
                EffectNode n = new EffectNode(nodeId);
                n.setEffectType((String) map.get("effect"));
                yield n;
            }
            case CONDITION -> {
                ConditionNode n = new ConditionNode(nodeId);
                n.setCondition((String) map.get("condition"));
                yield n;
            }
            case DELAY -> new DelayNode(nodeId);
            case LOOP -> new LoopNode(nodeId);
            case RANDOM -> new RandomNode(nodeId);
            case VARIABLE -> new VariableNode(nodeId);
            case TARGET -> new TargetNode(nodeId);
            case MATH -> new MathNode(nodeId);
            case SKIP_COOLDOWN -> new SkipCooldownNode(nodeId);
        };

        // Position
        node.setGridX(((Number) map.getOrDefault("x", 0)).intValue());
        node.setGridY(((Number) map.getOrDefault("y", 0)).intValue());

        // Parameters
        Object paramsObj = map.get("params");
        if (paramsObj instanceof Map<?, ?> paramsMap) {
            for (Map.Entry<?, ?> entry : paramsMap.entrySet()) {
                node.setParam(entry.getKey().toString(), entry.getValue());
            }
        }

        // Connections
        if (map.containsKey("next")) {
            node.setConnection("next", (String) map.get("next"));
        }
        Object connObj = map.get("connections");
        if (connObj instanceof Map<?, ?> connMap) {
            for (Map.Entry<?, ?> entry : connMap.entrySet()) {
                node.setConnection(entry.getKey().toString(), (String) entry.getValue());
            }
        }

        // Tier values (deprecated - will be auto-migrated to sigil TierScalingConfig)
        Object tierObj = map.get("tierValues");
        if (tierObj instanceof Map<?, ?> tierMap && !tierMap.isEmpty()) {
            for (Map.Entry<?, ?> entry : tierMap.entrySet()) {
                if (entry.getValue() instanceof List<?> valueList) {
                    List<Double> values = new ArrayList<>();
                    for (Object v : valueList) {
                        if (v instanceof Number num) {
                            values.add(num.doubleValue());
                        }
                    }
                    node.setTierValues(entry.getKey().toString(), values);
                }
            }
        }

        return node;
    }

    // ============ FlowConfig Deserialization ============

    /**
     * Deserialize a FlowConfig from a configuration section (flow: format).
     */
    public static FlowConfig deserializeFlowConfig(ConfigurationSection section) {
        if (section == null) return null;

        FlowConfig config = new FlowConfig();

        // Parse type
        String typeStr = section.getString("type", "SIGNAL").toUpperCase();
        try {
            config.setType(FlowType.valueOf(typeStr));
        } catch (IllegalArgumentException e) {
            config.setType(FlowType.SIGNAL);
        }

        // Parse trigger (only for SIGNAL type)
        config.setTrigger(section.getString("trigger"));

        // Parse cooldown, chance, and priority
        config.setCooldown(section.getDouble("cooldown", 0.0));
        config.setChance(section.getDouble("chance", 100.0));
        config.setPriority(section.getInt("priority", 1));

        // Parse conditions
        ConfigurationSection condSection = section.getConfigurationSection("conditions");
        if (condSection != null) {
            List<String> condList = new ArrayList<>(condSection.getStringList("list"));
            config.setConditions(condList);
            String logicStr = condSection.getString("logic", "AND").toUpperCase();
            try {
                config.setConditionLogic(FlowConfig.ConditionLogic.valueOf(logicStr));
            } catch (IllegalArgumentException e) {
                config.setConditionLogic(FlowConfig.ConditionLogic.AND);
            }
        } else if (section.contains("conditions")) {
            // Flat format - just a list
            List<String> condList = new ArrayList<>(section.getStringList("conditions"));
            config.setConditions(condList);
        }

        // Parse flow graph
        String graphId = section.getString("id", "flow");
        FlowGraph graph = new FlowGraph(graphId);
        graph.setStartNodeId(section.getString("startNodeId"));

        // Parse nodes - list format (primary) or map format (legacy fallback)
        List<?> nodesList = section.getList("nodes");
        if (nodesList != null && !nodesList.isEmpty()) {
            for (Object nodeObj : nodesList) {
                if (nodeObj instanceof Map<?, ?> nodeMap) {
                    @SuppressWarnings("unchecked")
                    FlowNode node = nodeFromMap((Map<String, Object>) nodeMap);
                    if (node != null) {
                        graph.addNode(node);
                    }
                }
            }
        } else {
            // Fallback to map format
            ConfigurationSection nodesSection = section.getConfigurationSection("nodes");
            if (nodesSection != null) {
                for (String nodeId : nodesSection.getKeys(false)) {
                    FlowNode node = deserializeNode(nodeId, nodesSection.getConfigurationSection(nodeId));
                    if (node != null) {
                        graph.addNode(node);
                    }
                }
            }
        }

        config.setGraph(graph);
        return config;
    }

    /**
     * Serialize a FlowConfig to a Map (for embedding in sigil YAML).
     */
    public static Map<String, Object> flowConfigToMap(FlowConfig config) {
        if (config == null) return null;

        Map<String, Object> map = new LinkedHashMap<>();

        // Flow metadata
        map.put("type", config.getType().name());

        if (config.getTrigger() != null && !config.getTrigger().isEmpty()) {
            map.put("trigger", config.getTrigger());
        }

        if (config.getCooldown() > 0) {
            map.put("cooldown", config.getCooldown());
        }

        if (config.getChance() < 100) {
            map.put("chance", config.getChance());
        }

        if (config.getPriority() != 1) {
            map.put("priority", config.getPriority());
        }

        // Conditions
        if (!config.getConditions().isEmpty()) {
            Map<String, Object> condMap = new LinkedHashMap<>();
            condMap.put("logic", config.getConditionLogic().name());
            condMap.put("list", config.getConditions());
            map.put("conditions", condMap);
        }

        // Flow graph
        FlowGraph graph = config.getGraph();
        if (graph != null && graph.getNodeCount() > 0) {
            map.put("id", graph.getId());
            map.put("startNodeId", graph.getStartNodeId());

            List<Map<String, Object>> nodesList = new ArrayList<>();
            for (FlowNode node : graph.getNodes()) {
                nodesList.add(nodeToMap(node));
            }
            map.put("nodes", nodesList);
        }

        return map;
    }

    /**
     * Deserialize a FlowConfig from a Map.
     */
    @SuppressWarnings("unchecked")
    public static FlowConfig flowConfigFromMap(Map<String, Object> map) {
        if (map == null) return null;

        FlowConfig config = new FlowConfig();

        // Parse type
        String typeStr = (String) map.getOrDefault("type", "SIGNAL");
        try {
            config.setType(FlowType.valueOf(typeStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            config.setType(FlowType.SIGNAL);
        }

        // Parse trigger
        config.setTrigger((String) map.get("trigger"));

        // Parse cooldown and chance
        Object cooldownObj = map.get("cooldown");
        if (cooldownObj instanceof Number num) {
            config.setCooldown(num.doubleValue());
        }

        Object chanceObj = map.get("chance");
        if (chanceObj instanceof Number num) {
            config.setChance(num.doubleValue());
        }

        Object priorityObj = map.get("priority");
        if (priorityObj instanceof Number num) {
            config.setPriority(num.intValue());
        }

        // Parse conditions
        Object condObj = map.get("conditions");
        if (condObj instanceof Map<?, ?> condMap) {
            Object listObj = condMap.get("list");
            if (listObj instanceof List<?> list) {
                List<String> condList = list.stream()
                        .map(Object::toString)
                        .collect(Collectors.toCollection(ArrayList::new));
                config.setConditions(condList);
            }
            Object logicObj = condMap.get("logic");
            String logicStr = logicObj != null ? logicObj.toString() : "AND";
            try {
                config.setConditionLogic(FlowConfig.ConditionLogic.valueOf(logicStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                config.setConditionLogic(FlowConfig.ConditionLogic.AND);
            }
        } else if (condObj instanceof List<?> list) {
            // Flat format
            List<String> condList = list.stream()
                    .map(Object::toString)
                    .collect(Collectors.toCollection(ArrayList::new));
            config.setConditions(condList);
        }

        // Parse flow graph
        String graphId = (String) map.getOrDefault("id", "flow");
        FlowGraph graph = new FlowGraph(graphId);
        graph.setStartNodeId((String) map.get("startNodeId"));

        Object nodesObj = map.get("nodes");
        if (nodesObj instanceof List<?> nodesList) {
            for (Object nodeObj : nodesList) {
                if (nodeObj instanceof Map<?, ?> nodeMap) {
                    FlowNode node = nodeFromMap((Map<String, Object>) nodeMap);
                    if (node != null) {
                        graph.addNode(node);
                    }
                }
            }
        }

        config.setGraph(graph);
        return config;
    }

    // ============ Multiple FlowConfigs (flows: list format) ============

    /**
     * Deserialize a list of FlowConfigs from a YAML list.
     *
     * @param flowsList The list from YAML (List of Maps)
     * @return List of deserialized FlowConfigs
     */
    @SuppressWarnings("unchecked")
    public static List<FlowConfig> deserializeFlowConfigs(List<?> flowsList) {
        List<FlowConfig> flows = new ArrayList<>();

        if (flowsList == null) return flows;

        for (Object flowObj : flowsList) {
            if (flowObj instanceof Map<?, ?> flowMap) {
                FlowConfig flow = flowConfigFromMap((Map<String, Object>) flowMap);
                if (flow != null) {
                    flows.add(flow);
                }
            }
        }

        return flows;
    }

    /**
     * Serialize a list of FlowConfigs to a List of Maps (for YAML embedding).
     */
    public static List<Map<String, Object>> flowConfigsToMapList(List<FlowConfig> flows) {
        List<Map<String, Object>> list = new ArrayList<>();

        if (flows == null) return list;

        for (FlowConfig flow : flows) {
            Map<String, Object> map = flowConfigToMap(flow);
            if (map != null) {
                list.add(map);
            }
        }

        return list;
    }
}
