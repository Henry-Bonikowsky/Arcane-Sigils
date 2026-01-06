package com.miracle.arcanesigils.tier;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for sigil XP progression and tier advancement.
 */
public class TierXPConfig {

    /**
     * Type of XP curve for tier advancement.
     */
    public enum CurveType {
        /** Each tier needs the same XP */
        LINEAR,
        /** Each tier needs exponentially more XP */
        EXPONENTIAL,
        /** Custom XP values per tier */
        CUSTOM
    }

    private boolean enabled = true;
    private int gainPerActivation = 1;
    private CurveType curveType = CurveType.EXPONENTIAL;
    private int baseXP = 100;
    private double growthRate = 1.5;
    private Map<Integer, Integer> customXPTable = new HashMap<>();

    public TierXPConfig() {
    }

    // ===== Getters and Setters =====

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getGainPerActivation() {
        return gainPerActivation;
    }

    public void setGainPerActivation(int gainPerActivation) {
        this.gainPerActivation = Math.max(1, gainPerActivation);
    }

    public CurveType getCurveType() {
        return curveType;
    }

    public void setCurveType(CurveType curveType) {
        this.curveType = curveType;
    }

    public int getBaseXP() {
        return baseXP;
    }

    public void setBaseXP(int baseXP) {
        this.baseXP = Math.max(1, baseXP);
    }

    public double getGrowthRate() {
        return growthRate;
    }

    public void setGrowthRate(double growthRate) {
        this.growthRate = Math.max(1.0, growthRate);
    }

    public Map<Integer, Integer> getCustomXPTable() {
        return customXPTable;
    }

    public void setCustomXPTable(Map<Integer, Integer> customXPTable) {
        this.customXPTable = customXPTable;
    }

    /**
     * Get XP required to advance from (tier-1) to tier.
     * Tier 2 means XP to go from tier 1 to tier 2.
     *
     * @param tier Target tier (2 to maxTier)
     * @return XP required
     */
    public int getXPForTier(int tier) {
        if (tier <= 1) {
            return 0;
        }

        switch (curveType) {
            case LINEAR:
                return baseXP;

            case EXPONENTIAL:
                // tier 2 = baseXP, tier 3 = baseXP * growth, tier 4 = baseXP * growth^2, etc.
                return (int) Math.round(baseXP * Math.pow(growthRate, tier - 2));

            case CUSTOM:
                Integer customXP = customXPTable.get(tier);
                if (customXP != null) {
                    return customXP;
                }
                // Fallback to exponential if custom value not defined
                return (int) Math.round(baseXP * Math.pow(growthRate, tier - 2));

            default:
                return baseXP;
        }
    }

    /**
     * Get total XP required to reach a tier from tier 1.
     *
     * @param tier Target tier
     * @return Total cumulative XP
     */
    public int getTotalXPForTier(int tier) {
        int total = 0;
        for (int t = 2; t <= tier; t++) {
            total += getXPForTier(t);
        }
        return total;
    }

    /**
     * Calculate what tier a given total XP amount corresponds to.
     *
     * @param totalXP Total accumulated XP
     * @param maxTier Maximum tier ceiling
     * @return Current tier based on XP
     */
    public int getTierForXP(int totalXP, int maxTier) {
        int accumulated = 0;
        for (int tier = 2; tier <= maxTier; tier++) {
            accumulated += getXPForTier(tier);
            if (totalXP < accumulated) {
                return tier - 1;
            }
        }
        return maxTier;
    }

    /**
     * Get XP progress toward next tier.
     *
     * @param totalXP Total accumulated XP
     * @param currentTier Current tier
     * @return XP progress in current tier (0 to getXPForTier(currentTier+1))
     */
    public int getXPProgressInTier(int totalXP, int currentTier) {
        int xpAtCurrentTier = getTotalXPForTier(currentTier);
        return totalXP - xpAtCurrentTier;
    }

    /**
     * Get progress percentage toward next tier.
     *
     * @param totalXP Total accumulated XP
     * @param currentTier Current tier
     * @param maxTier Maximum tier
     * @return Progress 0.0 to 1.0 (returns 1.0 if at max tier)
     */
    public double getProgressPercent(int totalXP, int currentTier, int maxTier) {
        if (currentTier >= maxTier) {
            return 1.0;
        }
        int progressInTier = getXPProgressInTier(totalXP, currentTier);
        int requiredForNext = getXPForTier(currentTier + 1);
        if (requiredForNext <= 0) {
            return 1.0;
        }
        return Math.min(1.0, (double) progressInTier / requiredForNext);
    }

    /**
     * Load from YAML configuration section.
     *
     * @param section The 'xp' section from tier config
     * @return Parsed config, or default if section is null
     */
    public static TierXPConfig fromConfig(ConfigurationSection section) {
        TierXPConfig config = new TierXPConfig();

        if (section == null) {
            return config;
        }

        config.enabled = section.getBoolean("enabled", true);
        config.gainPerActivation = section.getInt("gain_per_activation", 1);
        config.baseXP = section.getInt("base_xp", 100);
        config.growthRate = section.getDouble("growth_rate", 1.5);

        // Parse curve type
        String curveStr = section.getString("curve", "EXPONENTIAL");
        try {
            config.curveType = CurveType.valueOf(curveStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            config.curveType = CurveType.EXPONENTIAL;
        }

        // Parse custom XP table
        ConfigurationSection customSection = section.getConfigurationSection("custom");
        if (customSection != null) {
            for (String tierStr : customSection.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(tierStr);
                    int xp = customSection.getInt(tierStr);
                    config.customXPTable.put(tier, xp);
                } catch (NumberFormatException ignored) {
                    // Skip invalid tier keys
                }
            }
        }

        return config;
    }

    /**
     * Save to YAML format map.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();

        if (!enabled) {
            map.put("enabled", false);
        }
        if (gainPerActivation != 1) {
            map.put("gain_per_activation", gainPerActivation);
        }

        map.put("curve", curveType.name());
        map.put("base_xp", baseXP);

        if (curveType == CurveType.EXPONENTIAL && growthRate != 1.5) {
            map.put("growth_rate", growthRate);
        }

        if (curveType == CurveType.CUSTOM && !customXPTable.isEmpty()) {
            map.put("custom", new HashMap<>(customXPTable));
        }

        return map;
    }

    /**
     * Create a deep copy of this config.
     */
    public TierXPConfig copy() {
        TierXPConfig copy = new TierXPConfig();
        copy.enabled = this.enabled;
        copy.gainPerActivation = this.gainPerActivation;
        copy.curveType = this.curveType;
        copy.baseXP = this.baseXP;
        copy.growthRate = this.growthRate;
        copy.customXPTable = new HashMap<>(this.customXPTable);
        return copy;
    }

    /**
     * Create default config with XP disabled.
     */
    public static TierXPConfig disabled() {
        TierXPConfig config = new TierXPConfig();
        config.enabled = false;
        return config;
    }
}
