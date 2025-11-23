package com.zenax.armorsets.sets;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Represents an Armor Set with tiers and effects.
 */
public class ArmorSet {

    private final String id;
    private int tier;
    private int maxTier;
    private String namePattern;
    private Pattern compiledPattern;
    private Material material;
    private List<String> equippedMessage;
    private List<String> unequippedMessage;

    // Slot -> TriggerType -> TriggerConfig
    private Map<String, Map<String, TriggerConfig>> individualEffects;

    // Set synergies (full set bonuses)
    private List<SetSynergy> synergies;

    public ArmorSet(String id) {
        this.id = id;
        this.tier = 1;
        this.maxTier = 10;
        this.namePattern = id;
        this.material = Material.NETHERITE_CHESTPLATE;
        this.equippedMessage = new ArrayList<>();
        this.unequippedMessage = new ArrayList<>();
        this.individualEffects = new HashMap<>();
        this.synergies = new ArrayList<>();
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = Math.min(tier, maxTier);
    }

    public int getMaxTier() {
        return maxTier;
    }

    public void setMaxTier(int maxTier) {
        this.maxTier = maxTier;
    }

    public String getNamePattern() {
        return namePattern;
    }

    public void setNamePattern(String namePattern) {
        this.namePattern = namePattern;
        try {
            this.compiledPattern = Pattern.compile(namePattern, Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            this.compiledPattern = null;
        }
    }

    public Pattern getCompiledPattern() {
        return compiledPattern;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public List<String> getEquippedMessage() {
        return equippedMessage;
    }

    public void setEquippedMessage(List<String> equippedMessage) {
        this.equippedMessage = equippedMessage;
    }

    public List<String> getUnequippedMessage() {
        return unequippedMessage;
    }

    public void setUnequippedMessage(List<String> unequippedMessage) {
        this.unequippedMessage = unequippedMessage;
    }

    public Map<String, Map<String, TriggerConfig>> getIndividualEffects() {
        return individualEffects;
    }

    public void setIndividualEffects(Map<String, Map<String, TriggerConfig>> individualEffects) {
        this.individualEffects = individualEffects;
    }

    public List<SetSynergy> getSynergies() {
        return synergies;
    }

    public void setSynergies(List<SetSynergy> synergies) {
        this.synergies = synergies;
    }

    /**
     * Check if an item name matches this set's pattern.
     */
    public boolean matchesName(String itemName) {
        if (itemName == null) return false;

        // Strip color codes for matching (legacy &, hex §x, and standard §)
        String stripped = itemName.replaceAll("§x(§[0-9a-f]){6}", "") // Remove hex colors (§x§R§R§G§G§B§B)
                                  .replaceAll("§[0-9a-fk-or]", "")     // Remove legacy colors
                                  .replaceAll("&[0-9a-fk-or]", "");    // Remove & format colors

        if (compiledPattern != null) {
            return compiledPattern.matcher(stripped).find();
        }

        return stripped.toLowerCase().contains(namePattern.toLowerCase());
    }

    /**
     * Load ArmorSet from configuration.
     * Tier is a property, not part of the ID.
     */
    public static ArmorSet fromConfig(String id, ConfigurationSection section) {
        if (section == null) return null;

        ArmorSet set = new ArmorSet(id);
        set.setMaxTier(section.getInt("max_tier", 10));
        set.setTier(section.getInt("tier", 1));

        set.setNamePattern(section.getString("name_pattern", id));

        String materialStr = section.getString("material", "NETHERITE_CHESTPLATE");
        try {
            set.setMaterial(Material.valueOf(materialStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            set.setMaterial(Material.NETHERITE_CHESTPLATE);
        }

        set.setEquippedMessage(section.getStringList("equipped_message"));
        set.setUnequippedMessage(section.getStringList("unequipped_message"));

        // Load individual effects
        Map<String, Map<String, TriggerConfig>> individualEffects = new HashMap<>();
        ConfigurationSection effectsSection = section.getConfigurationSection("individual_effects");
        if (effectsSection != null) {
            for (String slot : effectsSection.getKeys(false)) {
                ConfigurationSection slotSection = effectsSection.getConfigurationSection(slot);
                if (slotSection == null) continue;

                Map<String, TriggerConfig> slotEffects = new HashMap<>();
                for (String triggerKey : slotSection.getKeys(false)) {
                    TriggerConfig config = TriggerConfig.fromConfig(slotSection.getConfigurationSection(triggerKey));
                    if (config != null) {
                        slotEffects.put(triggerKey, config);
                    }
                }
                individualEffects.put(slot, slotEffects);
            }
        }
        set.setIndividualEffects(individualEffects);

        // Load synergies
        List<SetSynergy> synergies = new ArrayList<>();
        ConfigurationSection synergiesSection = section.getConfigurationSection("synergies");
        if (synergiesSection != null) {
            for (String synergyId : synergiesSection.getKeys(false)) {
                SetSynergy synergy = SetSynergy.fromConfig(synergyId,
                        synergiesSection.getConfigurationSection(synergyId));
                if (synergy != null) {
                    synergies.add(synergy);
                }
            }
        }
        set.setSynergies(synergies);

        return set;
    }

    /**
     * Convert number to Roman numeral.
     */
    private static String toRoman(int num) {
        String[] thousands = {"", "M", "MM", "MMM"};
        String[] hundreds = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

        return thousands[num / 1000] +
               hundreds[(num % 1000) / 100] +
               tens[(num % 100) / 10] +
               ones[num % 10];
    }
}
