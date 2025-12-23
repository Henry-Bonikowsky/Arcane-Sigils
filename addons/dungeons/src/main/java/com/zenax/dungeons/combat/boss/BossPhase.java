package com.zenax.dungeons.combat.boss;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a phase in a boss fight.
 * Each phase is triggered when the boss reaches a certain health threshold.
 */
public class BossPhase {
    private final int phaseNumber;
    private final double healthThreshold;
    private final List<String> abilityIds;
    private final String onEnterMessage;
    private final int invulnerabilityDuration;
    private final boolean isFlying;

    /**
     * Creates a new boss phase.
     *
     * @param phaseNumber The phase number (1-indexed)
     * @param healthThreshold Health percentage threshold (0.0-1.0) to trigger this phase
     * @param abilityIds List of ability IDs available in this phase
     * @param onEnterMessage Optional message to broadcast when entering this phase (can be null)
     * @param invulnerabilityDuration Duration in ticks the boss is invulnerable when entering this phase
     * @param isFlying Whether the boss should fly during this phase
     */
    public BossPhase(int phaseNumber, double healthThreshold, List<String> abilityIds,
                    String onEnterMessage, int invulnerabilityDuration, boolean isFlying) {
        this.phaseNumber = phaseNumber;
        this.healthThreshold = Math.max(0.0, Math.min(1.0, healthThreshold));
        this.abilityIds = new ArrayList<>(abilityIds);
        this.onEnterMessage = onEnterMessage;
        this.invulnerabilityDuration = Math.max(0, invulnerabilityDuration);
        this.isFlying = isFlying;
    }

    /**
     * Loads a boss phase from a configuration section.
     *
     * @param config The configuration section containing phase data
     * @return The loaded BossPhase, or null if invalid
     */
    public static BossPhase fromConfig(ConfigurationSection config) {
        if (config == null) {
            return null;
        }

        try {
            int phaseNumber = config.getInt("phaseNumber", 1);
            double healthThreshold = config.getDouble("healthThreshold", 1.0);

            List<String> abilityIds = config.getStringList("abilities");
            if (abilityIds == null) {
                abilityIds = new ArrayList<>();
            }

            String onEnterMessage = config.getString("onEnterMessage", null);
            int invulnerabilityDuration = config.getInt("invulnerabilityDuration", 0);
            boolean isFlying = config.getBoolean("isFlying", false);

            return new BossPhase(phaseNumber, healthThreshold, abilityIds,
                               onEnterMessage, invulnerabilityDuration, isFlying);
        } catch (Exception e) {
            System.err.println("Error loading boss phase from config: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Checks if this phase should be activated at the given health percentage.
     *
     * @param currentHealthPercent Current health percentage (0.0-1.0)
     * @return true if this phase should be active
     */
    public boolean shouldActivate(double currentHealthPercent) {
        return currentHealthPercent <= healthThreshold;
    }

    public int getPhaseNumber() {
        return phaseNumber;
    }

    public double getHealthThreshold() {
        return healthThreshold;
    }

    public List<String> getAbilityIds() {
        return new ArrayList<>(abilityIds);
    }

    public String getOnEnterMessage() {
        return onEnterMessage;
    }

    public boolean hasEnterMessage() {
        return onEnterMessage != null && !onEnterMessage.isEmpty();
    }

    public int getInvulnerabilityDuration() {
        return invulnerabilityDuration;
    }

    public boolean isInvulnerabilityEnabled() {
        return invulnerabilityDuration > 0;
    }

    public boolean isFlying() {
        return isFlying;
    }

    @Override
    public String toString() {
        return "BossPhase{" +
               "phaseNumber=" + phaseNumber +
               ", healthThreshold=" + healthThreshold +
               ", abilities=" + abilityIds.size() +
               ", isFlying=" + isFlying +
               '}';
    }
}
