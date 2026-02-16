package com.miracle.arcanesigils.nms;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.entity.Mob;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * Helper to apply fast attack speed to mobs
 */
public class FastAttackHelper {
    
    private static FastAttackListener listener;
    
    public static void initialize(ArmorSetsPlugin plugin) {
        if (listener == null) {
            listener = new FastAttackListener(plugin);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            LogHelper.debug("[FastAttackHelper] Initialized fast attack system");
        }
    }
    
    /**
     * Apply faster attack speed to a mob
     */
    public static void setFastAttackSpeed(Mob mob, double multiplier, ArmorSetsPlugin plugin) {
        if (listener == null) {
            initialize(plugin);
        }
        
        // Mark mob with metadata
        mob.setMetadata("FAST_ATTACK_ENABLED", new FixedMetadataValue(plugin, true));
        mob.setMetadata("FAST_ATTACK_MULTIPLIER", new FixedMetadataValue(plugin, multiplier));
        
        // Start the fast attack task
        listener.enableFastAttack(mob, multiplier);
        
        LogHelper.debug("[FastAttackHelper] Enabled fast attack with %.1fx multiplier", multiplier);
    }
    
    public static void cleanup() {
        if (listener != null) {
            listener.cleanup();
        }
    }
}
