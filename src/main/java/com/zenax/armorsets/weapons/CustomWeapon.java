package com.zenax.armorsets.weapons;

import com.zenax.armorsets.sets.TriggerConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a custom weapon with set requirements and effects.
 */
public class CustomWeapon {

    private final String id;
    private String name;
    private String requiredSet;
    private Material material;
    private Map<String, TriggerConfig> events;

    public CustomWeapon(String id) {
        this.id = id;
        this.name = id;
        this.requiredSet = null;
        this.material = Material.DIAMOND_SWORD;
        this.events = new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRequiredSet() {
        return requiredSet;
    }

    public void setRequiredSet(String requiredSet) {
        this.requiredSet = requiredSet;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public Map<String, TriggerConfig> getEvents() {
        return events;
    }

    public void setEvents(Map<String, TriggerConfig> events) {
        this.events = events;
    }

    public static CustomWeapon fromConfig(String id, ConfigurationSection section) {
        if (section == null) return null;

        CustomWeapon weapon = new CustomWeapon(id);
        weapon.setName(section.getString("name", id));
        weapon.setRequiredSet(section.getString("requireSet", section.getString("required_set")));

        String materialStr = section.getString("material", "DIAMOND_SWORD");
        try {
            weapon.setMaterial(Material.valueOf(materialStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            weapon.setMaterial(Material.DIAMOND_SWORD);
        }

        Map<String, TriggerConfig> events = new HashMap<>();
        ConfigurationSection eventsSection = section.getConfigurationSection("events");
        if (eventsSection != null) {
            for (String triggerKey : eventsSection.getKeys(false)) {
                TriggerConfig config = TriggerConfig.fromConfig(eventsSection.getConfigurationSection(triggerKey));
                if (config != null) {
                    events.put(triggerKey, config);
                }
            }
        }
        weapon.setEvents(events);

        return weapon;
    }
}
