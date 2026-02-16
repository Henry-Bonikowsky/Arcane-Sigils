package com.miracle.arcanesigils.sets;

import com.miracle.arcanesigils.flow.FlowConfig;

import java.util.List;
import java.util.Map;

/**
 * Represents a set bonus loaded from sets/ YAML files.
 */
public class SetBonus {
    private final String name;
    private final String description;
    private final List<String> crates;
    private final int minPieces;
    private final FlowConfig flow;
    private final Map<String, Map<Integer, Double>> tierParams; // paramName -> (tier -> value)

    public SetBonus(String name, String description, List<String> crates, int minPieces,
                    FlowConfig flow, Map<String, Map<Integer, Double>> tierParams) {
        this.name = name;
        this.description = description;
        this.crates = crates;
        this.minPieces = minPieces;
        this.flow = flow;
        this.tierParams = tierParams;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getCrates() {
        return crates;
    }

    public int getMinPieces() {
        return minPieces;
    }

    public FlowConfig getFlow() {
        return flow;
    }

    public Map<String, Map<Integer, Double>> getTierParams() {
        return tierParams;
    }
}
