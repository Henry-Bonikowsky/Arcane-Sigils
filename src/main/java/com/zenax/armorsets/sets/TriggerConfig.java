package com.zenax.armorsets.sets;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a trigger (event) effect.
 */
public class TriggerConfig {

    public enum TriggerMode {
        CHANCE,   // Higher tiers increase activation chance
        COOLDOWN  // Higher tiers decrease cooldown
    }

    private TriggerMode triggerMode = TriggerMode.CHANCE;
    private double chance = 100;
    private double baseChance = 20;    // Starting chance at tier 1
    private List<String> effects = new ArrayList<>();
    private double cooldown = 0;
    private double baseCooldown = 10;  // Starting cooldown at tier 1
    private List<String> conditions = new ArrayList<>();

    public TriggerConfig() {}

    public TriggerConfig(double chance, List<String> effects, double cooldown) {
        this.chance = chance;
        this.effects = effects != null ? effects : new ArrayList<>();
        this.cooldown = cooldown;
        this.conditions = new ArrayList<>();
    }

    public TriggerConfig(double chance, List<String> effects, double cooldown, List<String> conditions) {
        this.chance = chance;
        this.effects = effects != null ? effects : new ArrayList<>();
        this.cooldown = cooldown;
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = chance;
    }

    public List<String> getEffects() {
        return effects;
    }

    public void setEffects(List<String> effects) {
        this.effects = effects != null ? effects : new ArrayList<>();
    }

    public double getCooldown() {
        return cooldown;
    }

    public void setCooldown(double cooldown) {
        this.cooldown = cooldown;
    }

    public List<String> getConditions() {
        return conditions;
    }

    public void setConditions(List<String> conditions) {
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }

    public TriggerMode getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(TriggerMode triggerMode) {
        this.triggerMode = triggerMode;
    }

    public double getBaseChance() {
        return baseChance;
    }

    public void setBaseChance(double baseChance) {
        this.baseChance = baseChance;
    }

    public double getBaseCooldown() {
        return baseCooldown;
    }

    public void setBaseCooldown(double baseCooldown) {
        this.baseCooldown = baseCooldown;
    }

    /**
     * Parse a TriggerConfig from a configuration section.
     */
    public static TriggerConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) return null;

        TriggerConfig config = new TriggerConfig();

        // Parse trigger mode
        String modeStr = section.getString("trigger_mode", "CHANCE").toUpperCase();
        try {
            config.setTriggerMode(TriggerMode.valueOf(modeStr));
        } catch (IllegalArgumentException e) {
            config.setTriggerMode(TriggerMode.CHANCE);
        }

        config.setChance(section.getDouble("chance", 100));
        config.setBaseChance(section.getDouble("base_chance", section.getDouble("chance", 20)));
        config.setEffects(section.getStringList("effects"));
        config.setCooldown(section.getDouble("cooldown", 0));
        config.setBaseCooldown(section.getDouble("base_cooldown", section.getDouble("cooldown", 10)));
        config.setConditions(section.getStringList("conditions"));

        return config;
    }
}
