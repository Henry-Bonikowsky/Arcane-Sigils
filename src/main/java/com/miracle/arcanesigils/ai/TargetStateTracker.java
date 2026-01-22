package com.miracle.arcanesigils.ai;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Boss;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Tracks player's selected target and updates scoreboard objectives with target information.
 * Provides 9 objectives: exists, type, health%, distance, hostile, player, boss, armor, line-of-sight.
 */
public class TargetStateTracker {
    
    private final ArmorSetsPlugin plugin;
    
    public TargetStateTracker(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Update all target-related scoreboard objectives for a player.
     */
    public void updateTargetObjectives(Player player) {
        LivingEntity target = plugin.getTargetGlowManager().getTarget(player);
        Scoreboard sb = player.getScoreboard();
        
        // No target or target is dead/invalid
        if (target == null || !target.isValid() || target.isDead()) {
            setObjectiveScore(sb, player, "sigil_target_exists", 0);
            setObjectiveScore(sb, player, "sigil_target_type", 0);
            setObjectiveScore(sb, player, "sigil_target_health", 0);
            setObjectiveScore(sb, player, "sigil_target_distance", 0);
            setObjectiveScore(sb, player, "sigil_target_hostile", 0);
            setObjectiveScore(sb, player, "sigil_target_player", 0);
            setObjectiveScore(sb, player, "sigil_target_boss", 0);
            setObjectiveScore(sb, player, "sigil_target_armor", 0);
            setObjectiveScore(sb, player, "sigil_target_los", 0);
            return;
        }
        
        // Target exists
        setObjectiveScore(sb, player, "sigil_target_exists", 1);
        
        // Target type (10=passive, 50=hostile, 100=player)
        int type = classifyTargetType(target);
        setObjectiveScore(sb, player, "sigil_target_type", type);
        
        // Health percentage (0-100)
        int healthPct = (int)((target.getHealth() / target.getMaxHealth()) * 100);
        setObjectiveScore(sb, player, "sigil_target_health", Math.max(0, Math.min(100, healthPct)));
        
        // Distance (blocks * 10, e.g., 53 = 5.3 blocks)
        int distance = (int)(player.getLocation().distance(target.getLocation()) * 10);
        setObjectiveScore(sb, player, "sigil_target_distance", distance);
        
        // Boolean flags
        setObjectiveScore(sb, player, "sigil_target_hostile", isHostile(target) ? 1 : 0);
        setObjectiveScore(sb, player, "sigil_target_player", target instanceof Player ? 1 : 0);
        setObjectiveScore(sb, player, "sigil_target_boss", target instanceof Boss ? 1 : 0);
        
        // Armor points (0-100)
        double armor = target.getAttribute(Attribute.ARMOR) != null 
            ? target.getAttribute(Attribute.ARMOR).getValue() 
            : 0;
        setObjectiveScore(sb, player, "sigil_target_armor", (int)Math.min(100, armor));
        
        // Line of sight (1=clear, 0=blocked)
        boolean los = player.hasLineOfSight(target);
        setObjectiveScore(sb, player, "sigil_target_los", los ? 1 : 0);
    }
    
    /**
     * Classify target type for AI learning.
     * 100 = player (highest priority target)
     * 50 = hostile mob
     * 10 = passive mob
     */
    private int classifyTargetType(LivingEntity target) {
        if (target instanceof Player) {
            return 100;
        }
        if (isHostile(target)) {
            return 50;
        }
        return 10; // Passive
    }
    
    /**
     * Check if entity is hostile.
     */
    private boolean isHostile(LivingEntity entity) {
        return entity instanceof Monster;
    }
    
    /**
     * Helper to set scoreboard objective score safely.
     */
    private void setObjectiveScore(Scoreboard sb, Player player, String objectiveName, int score) {
        Objective obj = sb.getObjective(objectiveName);
        if (obj != null) {
            obj.getScore(player.getName()).setScore(score);
        }
    }
}
