package com.miracle.arcanesigils.ai;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration wrapper for AI training settings.
 * Loads and caches values from the ai_training section in config.yml.
 */
public class AITrainingConfig {
    
    private final ArmorSetsPlugin plugin;
    private boolean enabled;
    private int updateInterval;
    private long comboWindowMs;
    
    public AITrainingConfig(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }
    
    /**
     * Reloads configuration values from config.yml.
     */
    public void reload() {
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        
        this.enabled = config.getBoolean("ai_training.enabled", false);
        this.updateInterval = config.getInt("ai_training.update_interval", 5);
        int comboWindowSeconds = config.getInt("ai_training.combo_window_seconds", 3);
        this.comboWindowMs = comboWindowSeconds * 1000L;
        
        // Validate values
        if (updateInterval < 1) {
            plugin.getLogger().warning("ai_training.update_interval must be >= 1, using default 5");
            updateInterval = 5;
        }
        if (comboWindowMs < 1000) {
            plugin.getLogger().warning("ai_training.combo_window_seconds must be >= 1, using default 3");
            comboWindowMs = 3000;
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public int getUpdateInterval() {
        return updateInterval;
    }
    
    public long getComboWindowMs() {
        return comboWindowMs;
    }
}
