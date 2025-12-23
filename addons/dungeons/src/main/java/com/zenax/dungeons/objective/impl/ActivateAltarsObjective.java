package com.zenax.dungeons.objective.impl;

import com.zenax.dungeons.dungeon.DungeonInstance;
import com.zenax.dungeons.objective.AbstractObjective;
import com.zenax.dungeons.objective.ObjectiveType;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;

/**
 * Objective that requires activating a certain number of altars.
 * Progress is calculated as activatedCount / requiredCount.
 */
public class ActivateAltarsObjective extends AbstractObjective {
    private final int requiredAltars;
    private final Set<String> activatedAltars;

    /**
     * Creates a new activate altars objective.
     *
     * @param id The unique identifier for this objective
     * @param description The description of the objective
     * @param requiredAltars The number of altars that must be activated
     */
    public ActivateAltarsObjective(String id, String description, int requiredAltars) {
        super(id, ObjectiveType.ACTIVATE_ALTARS, description);
        this.requiredAltars = Math.max(1, requiredAltars);
        this.activatedAltars = new HashSet<>();
    }

    @Override
    public double getProgress() {
        return Math.min(1.0, (double) activatedAltars.size() / (double) requiredAltars);
    }

    @Override
    public void update(DungeonInstance instance, Object... context) {
        // Context[0] should be the altar ID or location identifier
        if (context.length == 0) {
            return;
        }

        String altarId = null;

        // Accept either String ID or Location
        if (context[0] instanceof String) {
            altarId = (String) context[0];
        } else if (context[0] instanceof Location) {
            Location location = (Location) context[0];
            altarId = locationToId(location);
        }

        if (altarId != null && !activatedAltars.contains(altarId)) {
            activatedAltars.add(altarId);

            // Check if objective is complete
            if (activatedAltars.size() >= requiredAltars) {
                setComplete(true);
            }
        }
    }

    /**
     * Converts a location to a unique identifier string.
     *
     * @param location The location
     * @return A unique identifier for the location
     */
    private String locationToId(Location location) {
        return String.format("%d,%d,%d",
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }

    /**
     * Manually activates an altar by ID.
     *
     * @param altarId The altar ID
     */
    public void activateAltar(String altarId) {
        if (!activatedAltars.contains(altarId)) {
            activatedAltars.add(altarId);
            if (activatedAltars.size() >= requiredAltars) {
                setComplete(true);
            }
        }
    }

    /**
     * Gets the required number of altars.
     *
     * @return The required altar count
     */
    public int getRequiredAltars() {
        return requiredAltars;
    }

    /**
     * Gets the number of activated altars.
     *
     * @return The activated altar count
     */
    public int getActivatedCount() {
        return activatedAltars.size();
    }

    /**
     * Gets the set of activated altar IDs.
     *
     * @return The activated altar IDs
     */
    public Set<String> getActivatedAltars() {
        return new HashSet<>(activatedAltars);
    }

    /**
     * Checks if a specific altar has been activated.
     *
     * @param altarId The altar ID
     * @return true if the altar is activated
     */
    public boolean isAltarActivated(String altarId) {
        return activatedAltars.contains(altarId);
    }

    @Override
    public String getDescription() {
        return String.format("%s (%d/%d)", description, activatedAltars.size(), requiredAltars);
    }
}
