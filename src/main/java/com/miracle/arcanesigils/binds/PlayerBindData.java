package com.miracle.arcanesigils.binds;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

/**
 * Stores all bind-related data for a single player.
 * Includes current binds, presets, settings, and toggle state.
 */
public class PlayerBindData {
    private final UUID playerId;

    // Current active binds for each system
    private final BindPreset hotbarBinds;
    private final BindPreset commandBinds;

    // Saved presets (4 per system, not shared)
    private final BindPreset[] hotbarPresets;
    private final BindPreset[] commandPresets;

    // Settings
    private BindSystem activeSystem;
    private ToggleHotkey toggleHotkey;
    private HeldSlotHotkey heldSlotHotkey;

    // Runtime state (not persisted)
    private boolean toggled;
    private int highestCommandBindId;

    public PlayerBindData(UUID playerId) {
        this.playerId = playerId;
        this.hotbarBinds = new BindPreset();
        this.commandBinds = new BindPreset();
        this.hotbarPresets = new BindPreset[4];
        this.commandPresets = new BindPreset[4];
        this.activeSystem = BindSystem.HOTBAR;
        this.toggleHotkey = ToggleHotkey.SNEAK_SWAP;
        this.heldSlotHotkey = HeldSlotHotkey.SWAP_HAND;
        this.toggled = false;
        this.highestCommandBindId = 0;
    }

    // Getters and setters
    public UUID getPlayerId() {
        return playerId;
    }

    public BindPreset getHotbarBinds() {
        return hotbarBinds;
    }

    public BindPreset getCommandBinds() {
        return commandBinds;
    }

    public BindPreset getCurrentBinds() {
        return activeSystem == BindSystem.HOTBAR ? hotbarBinds : commandBinds;
    }

    public BindPreset[] getHotbarPresets() {
        return hotbarPresets;
    }

    public BindPreset[] getCommandPresets() {
        return commandPresets;
    }

    public BindPreset[] getCurrentPresets() {
        return activeSystem == BindSystem.HOTBAR ? hotbarPresets : commandPresets;
    }

    public BindSystem getActiveSystem() {
        return activeSystem;
    }

    public void setActiveSystem(BindSystem activeSystem) {
        this.activeSystem = activeSystem;
    }

    public ToggleHotkey getToggleHotkey() {
        return toggleHotkey;
    }

    public void setToggleHotkey(ToggleHotkey toggleHotkey) {
        this.toggleHotkey = toggleHotkey;
    }

    public HeldSlotHotkey getHeldSlotHotkey() {
        return heldSlotHotkey;
    }

    public void setHeldSlotHotkey(HeldSlotHotkey heldSlotHotkey) {
        this.heldSlotHotkey = heldSlotHotkey;
    }

    public boolean isToggled() {
        return toggled;
    }

    public void setToggled(boolean toggled) {
        this.toggled = toggled;
    }

    public void toggle() {
        this.toggled = !this.toggled;
    }

    public int getHighestCommandBindId() {
        return highestCommandBindId;
    }

    public void setHighestCommandBindId(int id) {
        this.highestCommandBindId = id;
    }

    // Preset management
    public void saveToPreset(int index) {
        if (index < 0 || index >= 4) return;
        BindPreset[] presets = getCurrentPresets();
        presets[index] = getCurrentBinds().copy();
    }

    public void loadFromPreset(int index) {
        if (index < 0 || index >= 4) return;
        BindPreset[] presets = getCurrentPresets();
        BindPreset preset = presets[index];
        if (preset != null) {
            BindPreset current = getCurrentBinds();
            current.clearAll();
            for (Map.Entry<Integer, List<String>> entry : preset.getBinds().entrySet()) {
                current.setBind(entry.getKey(), entry.getValue());
            }
        }
    }

    public void deletePreset(int index) {
        if (index < 0 || index >= 4) return;
        BindPreset[] presets = getCurrentPresets();
        presets[index] = null;
    }

    public boolean hasPreset(int index) {
        if (index < 0 || index >= 4) return false;
        BindPreset[] presets = getCurrentPresets();
        return presets[index] != null && !presets[index].isEmpty();
    }

    // Command bind ID management
    public int addCommandBind() {
        highestCommandBindId++;
        return highestCommandBindId;
    }

    public void deleteHighestCommandBind() {
        if (highestCommandBindId > 0) {
            commandBinds.clearBind(highestCommandBindId);
            highestCommandBindId--;
        }
    }

    public void recalculateHighestCommandBindId() {
        highestCommandBindId = 0;
        for (int slot : commandBinds.getBinds().keySet()) {
            if (slot > highestCommandBindId) {
                highestCommandBindId = slot;
            }
        }
    }

    // Check if any data worth saving
    public boolean hasAnyData() {
        // Check presets
        for (BindPreset preset : hotbarPresets) {
            if (preset != null && !preset.isEmpty()) return true;
        }
        for (BindPreset preset : commandPresets) {
            if (preset != null && !preset.isEmpty()) return true;
        }
        // Check current binds
        if (!hotbarBinds.isEmpty()) return true;
        if (!commandBinds.isEmpty()) return true;
        // Check non-default settings
        if (activeSystem != BindSystem.HOTBAR) return true;
        if (toggleHotkey != ToggleHotkey.SNEAK_SWAP) return true;
        if (heldSlotHotkey != HeldSlotHotkey.SWAP_HAND) return true;
        return false;
    }

    // YAML persistence
    public void saveToConfig(YamlConfiguration config) {
        config.set("active-system", activeSystem.name());
        config.set("toggle-hotkey", toggleHotkey.name());
        config.set("held-slot-hotkey", heldSlotHotkey.name());
        config.set("highest-command-bind-id", highestCommandBindId);

        // Save hotbar binds
        ConfigurationSection hotbarSection = config.createSection("hotbar-binds");
        hotbarBinds.saveToConfig(hotbarSection);

        // Save command binds
        ConfigurationSection commandSection = config.createSection("command-binds");
        commandBinds.saveToConfig(commandSection);

        // Save hotbar presets
        for (int i = 0; i < 4; i++) {
            if (hotbarPresets[i] != null && !hotbarPresets[i].isEmpty()) {
                ConfigurationSection presetSection = config.createSection("hotbar-presets.preset-" + i);
                hotbarPresets[i].saveToConfig(presetSection);
            }
        }

        // Save command presets
        for (int i = 0; i < 4; i++) {
            if (commandPresets[i] != null && !commandPresets[i].isEmpty()) {
                ConfigurationSection presetSection = config.createSection("command-presets.preset-" + i);
                commandPresets[i].saveToConfig(presetSection);
            }
        }
    }

    public static PlayerBindData loadFromConfig(UUID playerId, YamlConfiguration config) {
        PlayerBindData data = new PlayerBindData(playerId);

        // Load settings
        String systemStr = config.getString("active-system", "HOTBAR");
        try {
            data.activeSystem = BindSystem.valueOf(systemStr);
        } catch (IllegalArgumentException ignored) {
        }

        String toggleStr = config.getString("toggle-hotkey", "SNEAK_SWAP");
        try {
            data.toggleHotkey = ToggleHotkey.valueOf(toggleStr);
        } catch (IllegalArgumentException ignored) {
        }

        String heldStr = config.getString("held-slot-hotkey", "SWAP_HAND");
        try {
            data.heldSlotHotkey = HeldSlotHotkey.valueOf(heldStr);
        } catch (IllegalArgumentException ignored) {
        }

        data.highestCommandBindId = config.getInt("highest-command-bind-id", 0);

        // Load hotbar binds
        ConfigurationSection hotbarSection = config.getConfigurationSection("hotbar-binds");
        if (hotbarSection != null) {
            BindPreset loaded = BindPreset.loadFromConfig(hotbarSection);
            for (Map.Entry<Integer, List<String>> entry : loaded.getBinds().entrySet()) {
                data.hotbarBinds.setBind(entry.getKey(), entry.getValue());
            }
        }

        // Load command binds
        ConfigurationSection commandSection = config.getConfigurationSection("command-binds");
        if (commandSection != null) {
            BindPreset loaded = BindPreset.loadFromConfig(commandSection);
            for (Map.Entry<Integer, List<String>> entry : loaded.getBinds().entrySet()) {
                data.commandBinds.setBind(entry.getKey(), entry.getValue());
            }
        }

        // Load hotbar presets
        ConfigurationSection hotbarPresetsSection = config.getConfigurationSection("hotbar-presets");
        if (hotbarPresetsSection != null) {
            for (int i = 0; i < 4; i++) {
                ConfigurationSection presetSection = hotbarPresetsSection.getConfigurationSection("preset-" + i);
                if (presetSection != null) {
                    data.hotbarPresets[i] = BindPreset.loadFromConfig(presetSection);
                }
            }
        }

        // Load command presets
        ConfigurationSection commandPresetsSection = config.getConfigurationSection("command-presets");
        if (commandPresetsSection != null) {
            for (int i = 0; i < 4; i++) {
                ConfigurationSection presetSection = commandPresetsSection.getConfigurationSection("preset-" + i);
                if (presetSection != null) {
                    data.commandPresets[i] = BindPreset.loadFromConfig(presetSection);
                }
            }
        }

        // Recalculate highest command bind ID in case of inconsistency
        data.recalculateHighestCommandBindId();

        return data;
    }
}
