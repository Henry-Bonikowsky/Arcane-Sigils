package com.zenax.dungeons.objective.impl;

import com.zenax.dungeons.dungeon.DungeonInstance;
import com.zenax.dungeons.objective.AbstractObjective;
import com.zenax.dungeons.objective.ObjectiveType;

/**
 * Objective that requires collecting a certain number of specific items.
 * Progress is calculated as collectedAmount / requiredAmount.
 */
public class CollectItemsObjective extends AbstractObjective {
    private final String itemId;
    private final int requiredAmount;
    private int collectedAmount;

    /**
     * Creates a new collect items objective.
     *
     * @param id The unique identifier for this objective
     * @param description The description of the objective
     * @param itemId The ID of the item to collect
     * @param requiredAmount The number of items required
     */
    public CollectItemsObjective(String id, String description, String itemId, int requiredAmount) {
        super(id, ObjectiveType.COLLECT_ITEMS, description);
        this.itemId = itemId;
        this.requiredAmount = Math.max(1, requiredAmount);
        this.collectedAmount = 0;
    }

    @Override
    public double getProgress() {
        return Math.min(1.0, (double) collectedAmount / (double) requiredAmount);
    }

    @Override
    public void update(DungeonInstance instance, Object... context) {
        // Context[0] should be the item ID that was picked up
        // Context[1] should be the amount (optional, defaults to 1)
        if (context.length == 0) {
            return;
        }

        if (context[0] instanceof String) {
            String pickedUpItemId = (String) context[0];

            // Check if this is the item we're looking for
            if (itemId.equals(pickedUpItemId)) {
                int amount = 1;
                if (context.length > 1 && context[1] instanceof Integer) {
                    amount = (Integer) context[1];
                }

                collectedAmount = Math.min(collectedAmount + amount, requiredAmount);

                // Check if objective is complete
                if (collectedAmount >= requiredAmount) {
                    setComplete(true);
                }
            }
        }
    }

    /**
     * Manually sets the collected amount.
     *
     * @param amount The amount to set
     */
    public void setCollectedAmount(int amount) {
        this.collectedAmount = Math.min(Math.max(0, amount), requiredAmount);
        if (collectedAmount >= requiredAmount) {
            setComplete(true);
        }
    }

    /**
     * Gets the item ID required for this objective.
     *
     * @return The item ID
     */
    public String getItemId() {
        return itemId;
    }

    /**
     * Gets the required amount of items.
     *
     * @return The required amount
     */
    public int getRequiredAmount() {
        return requiredAmount;
    }

    /**
     * Gets the current collected amount.
     *
     * @return The collected amount
     */
    public int getCollectedAmount() {
        return collectedAmount;
    }

    @Override
    public String getDescription() {
        return String.format("%s (%d/%d)", description, collectedAmount, requiredAmount);
    }
}
