package com.zenax.armorsets.flow;

import org.bukkit.Material;

/**
 * Enum of all node types in the visual flow system.
 */
public enum NodeType {

    /**
     * Entry point node - where the flow begins when a signal fires.
     * Uses LIME_CONCRETE for green "go" visual.
     */
    START("Start", "Entry point for the flow", Material.LIME_CONCRETE, NodeCategory.CONTROL),

    /**
     * Effect node - wraps any existing effect for execution.
     * Material is dynamically set by FlowBuilderHandler.getEffectMaterial() based on effect type.
     */
    EFFECT("Effect", "Execute an effect", Material.NETHER_STAR, NodeCategory.EFFECT),

    /**
     * Condition node - branches based on a condition.
     * Has two outputs: "yes" and "no".
     * Uses YELLOW_CONCRETE for warning/branch visual.
     */
    CONDITION("Condition", "Branch based on condition", Material.YELLOW_CONCRETE, NodeCategory.CONTROL),

    /**
     * End node - terminates a branch.
     * Uses RED_CONCRETE for stop visual.
     * @deprecated Flows now end naturally when there's no next connection. Kept for backward compatibility.
     */
    @Deprecated
    END("End", "End this branch", Material.RED_CONCRETE, NodeCategory.CONTROL),

    /**
     * Delay node - waits before continuing.
     * Keeps CLOCK - it's a perfect semantic match.
     */
    DELAY("Delay", "Wait before continuing", Material.CLOCK, NodeCategory.CONTROL),

    /**
     * Loop node - repeats a section.
     * Uses CYAN_CONCRETE for repeat/cycle visual.
     */
    LOOP("Loop", "Repeat N times or while condition", Material.CYAN_CONCRETE, NodeCategory.CONTROL),

    /**
     * Random node - picks a random weighted path.
     * Uses PURPLE_CONCRETE for randomness.
     * @deprecated No clear purpose for non-programmers. Kept for backward compatibility.
     */
    @Deprecated
    RANDOM("Random", "Random path selection", Material.PURPLE_CONCRETE, NodeCategory.CONTROL),

    /**
     * Variable node - sets or gets a variable.
     * Uses ORANGE_CONCRETE for data/storage visual.
     */
    VARIABLE("Variable", "Store or retrieve values", Material.ORANGE_CONCRETE, NodeCategory.DATA),

    /**
     * Math node - performs calculations.
     * Uses MAGENTA_CONCRETE.
     * @deprecated Too complex for non-programmers. Kept for backward compatibility.
     */
    @Deprecated
    MATH("Math", "Perform calculations", Material.MAGENTA_CONCRETE, NodeCategory.DATA),

    /**
     * Target node - changes current target.
     * Uses PINK_CONCRETE.
     * @deprecated Redundant - effects already specify target via @Victim, @Self. Kept for backward compatibility.
     */
    @Deprecated
    TARGET("Target", "Change current target", Material.PINK_CONCRETE, NodeCategory.DATA),

    /**
     * Skip Cooldown node - prevents cooldown from triggering.
     * Place on condition "no" branch to skip cooldown when condition fails.
     * Uses BARRIER to indicate "stop/block cooldown".
     */
    SKIP_COOLDOWN("Skip Cooldown", "Prevent cooldown from triggering", Material.BARRIER, NodeCategory.CONTROL);

    private final String displayName;
    private final String description;
    private final Material material;
    private final NodeCategory category;

    NodeType(String displayName, String description, Material material, NodeCategory category) {
        this.displayName = displayName;
        this.description = description;
        this.material = material;
        this.category = category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Material getMaterial() {
        return material;
    }

    public NodeCategory getCategory() {
        return category;
    }

    /**
     * Node categories for organization in the GUI.
     */
    public enum NodeCategory {
        CONTROL("Control Flow", Material.REDSTONE),
        EFFECT("Effects", Material.DIAMOND),
        DATA("Data", Material.CHEST);

        private final String displayName;
        private final Material material;

        NodeCategory(String displayName, Material material) {
            this.displayName = displayName;
            this.material = material;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getMaterial() {
            return material;
        }
    }
}
