package com.zenax.armorsets.flow.nodes;

import com.zenax.armorsets.flow.FlowContext;
import com.zenax.armorsets.flow.FlowNode;
import com.zenax.armorsets.flow.NodeType;
import com.zenax.armorsets.utils.LogHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random node - picks a random path based on weighted probabilities.
 * Has 2-4 outputs: "path1", "path2", "path3", "path4".
 * Weights are normalized to percentages automatically.
 */
public class RandomNode extends FlowNode {

    public RandomNode(String id) {
        super(id);
        setDisplayName("Random");
        // Default: 2 paths with equal weight
        setParam("pathCount", 2);
        setParam("weight1", 50.0);
        setParam("weight2", 50.0);
    }

    @Override
    public NodeType getType() {
        return NodeType.RANDOM;
    }

    @Override
    public List<String> getOutputPorts() {
        int pathCount = getIntParam("pathCount", 2);
        pathCount = Math.max(2, Math.min(pathCount, 4));

        List<String> ports = new ArrayList<>();
        for (int i = 1; i <= pathCount; i++) {
            ports.add("path" + i);
        }
        return ports;
    }

    @Override
    public String execute(FlowContext context) {
        int pathCount = getIntParam("pathCount", 2);
        pathCount = Math.max(2, Math.min(pathCount, 4));

        // Get weights
        double[] weights = new double[pathCount];
        double totalWeight = 0;

        for (int i = 0; i < pathCount; i++) {
            weights[i] = getDoubleParam("weight" + (i + 1), 1.0);
            totalWeight += weights[i];
        }

        // Roll random number
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;

        // Find which path to take
        double cumulative = 0;
        for (int i = 0; i < pathCount; i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                String chosenPath = "path" + (i + 1);
                LogHelper.debug("[RandomNode] Rolled %.2f/%.2f, chose %s (weight %.1f%%)",
                        roll, totalWeight, chosenPath, (weights[i] / totalWeight) * 100);
                return chosenPath;
            }
        }

        // Fallback to last path
        return "path" + pathCount;
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        int pathCount = getIntParam("pathCount", 2);
        if (pathCount < 2 || pathCount > 4) {
            errors.add("Path count must be between 2 and 4");
        }

        // Check that at least some paths are connected
        boolean anyConnected = false;
        for (int i = 1; i <= pathCount; i++) {
            if (getConnection("path" + i) != null) {
                anyConnected = true;
            }
        }

        if (!anyConnected) {
            errors.add("At least one random path must be connected");
        }

        // Check weights
        double totalWeight = 0;
        for (int i = 1; i <= pathCount; i++) {
            double weight = getDoubleParam("weight" + i, 0);
            if (weight < 0) {
                errors.add("Weight " + i + " cannot be negative");
            }
            totalWeight += weight;
        }

        if (totalWeight <= 0) {
            errors.add("Total weight must be greater than 0");
        }

        return errors;
    }

    /**
     * Set equal weights for all paths.
     */
    public void setEqualWeights() {
        int pathCount = getIntParam("pathCount", 2);
        double weight = 100.0 / pathCount;
        for (int i = 1; i <= pathCount; i++) {
            setParam("weight" + i, weight);
        }
    }

    @Override
    public FlowNode deepCopy() {
        RandomNode copy = new RandomNode(getId());
        copyBaseTo(copy);
        return copy;
    }
}
