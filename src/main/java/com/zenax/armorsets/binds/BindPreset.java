package com.zenax.armorsets.binds;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Represents a saved bind preset containing bind configurations.
 * Can be saved/loaded from YAML configuration.
 */
public class BindPreset {
    private final Map<Integer, List<String>> binds; // bind slot -> list of sigil IDs

    public BindPreset() {
        this.binds = new LinkedHashMap<>();
    }

    public BindPreset(Map<Integer, List<String>> binds) {
        this.binds = new LinkedHashMap<>(binds);
    }

    public Map<Integer, List<String>> getBinds() {
        return Collections.unmodifiableMap(binds);
    }

    public void setBind(int slot, List<String> sigilIds) {
        if (sigilIds == null) {
            binds.remove(slot);
        } else {
            binds.put(slot, new ArrayList<>(sigilIds));
        }
    }

    public List<String> getBind(int slot) {
        return binds.getOrDefault(slot, Collections.emptyList());
    }

    public void clearBind(int slot) {
        binds.remove(slot);
    }

    public void clearAll() {
        binds.clear();
    }

    public int getTotalAbilitiesBound() {
        return binds.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    public boolean isEmpty() {
        return binds.isEmpty();
    }

    public BindPreset copy() {
        Map<Integer, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<String>> entry : binds.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return new BindPreset(copy);
    }

    public void saveToConfig(ConfigurationSection section) {
        for (Map.Entry<Integer, List<String>> entry : binds.entrySet()) {
            section.set("bind-" + entry.getKey(), entry.getValue());
        }
    }

    public static BindPreset loadFromConfig(ConfigurationSection section) {
        BindPreset preset = new BindPreset();
        if (section == null) return preset;

        for (String key : section.getKeys(false)) {
            if (key.startsWith("bind-")) {
                try {
                    int slot = Integer.parseInt(key.substring(5));
                    List<String> sigilIds = section.getStringList(key);
                    // Allow empty binds to be loaded (they represent bind slots with no sigils assigned yet)
                    preset.setBind(slot, sigilIds);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return preset;
    }
}
