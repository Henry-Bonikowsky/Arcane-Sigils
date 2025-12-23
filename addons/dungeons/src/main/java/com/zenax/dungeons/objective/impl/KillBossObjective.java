package com.zenax.dungeons.objective.impl;

import com.zenax.dungeons.dungeon.DungeonInstance;
import com.zenax.dungeons.objective.AbstractObjective;
import com.zenax.dungeons.objective.ObjectiveType;
import org.bukkit.entity.Entity;

import java.util.UUID;

/**
 * Objective that requires killing a specific boss.
 * Progress is binary: 0 (not killed) or 1 (killed).
 */
public class KillBossObjective extends AbstractObjective {
    private final String targetBossId;
    private boolean killed;

    /**
     * Creates a new kill boss objective.
     *
     * @param id The unique identifier for this objective
     * @param description The description of the objective
     * @param targetBossId The ID of the boss that must be killed
     */
    public KillBossObjective(String id, String description, String targetBossId) {
        super(id, ObjectiveType.KILL_BOSS, description);
        this.targetBossId = targetBossId;
        this.killed = false;
    }

    @Override
    public double getProgress() {
        return killed ? 1.0 : 0.0;
    }

    @Override
    public void update(DungeonInstance instance, Object... context) {
        // Check if boss is defeated via instance method
        if (instance.isBossDefeated()) {
            killed = true;
            setComplete(true);
            return;
        }

        // Alternatively, check via context if a boss UUID is provided
        if (context.length > 0 && context[0] instanceof UUID) {
            UUID killedBossUuid = (UUID) context[0];

            // Check if the killed boss matches the instance boss
            if (killedBossUuid.equals(instance.getBossUuid())) {
                killed = true;
                setComplete(true);
            }
        }

        // Additional check: if context contains boss ID string
        if (context.length > 0 && context[0] instanceof String) {
            String killedBossId = (String) context[0];
            if (targetBossId.equals(killedBossId)) {
                killed = true;
                setComplete(true);
            }
        }
    }

    /**
     * Gets the target boss ID.
     *
     * @return The boss ID
     */
    public String getTargetBossId() {
        return targetBossId;
    }

    /**
     * Checks if the boss has been killed.
     *
     * @return true if the boss is killed
     */
    public boolean isKilled() {
        return killed;
    }
}
