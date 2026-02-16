package com.miracle.arcanesigils.ai;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Background task that updates scoreboard objectives for AI training.
 * Runs every N ticks (configurable) to provide real-time bind state to AI clients.
 */
public class ScoreboardUpdateTask extends BukkitRunnable {
    
    private final ArmorSetsPlugin plugin;
    private final BindStateTracker bindStateTracker;
    private final TargetStateTracker targetStateTracker;
    
    public ScoreboardUpdateTask(ArmorSetsPlugin plugin, 
                               BindStateTracker bindStateTracker,
                               TargetStateTracker targetStateTracker) {
        this.plugin = plugin;
        this.bindStateTracker = bindStateTracker;
        this.targetStateTracker = targetStateTracker;
    }
    
    @Override
    public void run() {
        // Only process online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerObjectives(player);
        }
    }
    
    /**
     * Update all AI training objectives for a player.
     */
    private void updatePlayerObjectives(Player player) {
        Scoreboard sb = player.getScoreboard();
        
        // Update bind state objectives (12 total: 4 slots x 3 each)
        for (int slot = 1; slot <= 4; slot++) {
            BindState state = bindStateTracker.computeBindState(player, slot);
            updateBindObjectives(sb, player, slot, state);
        }
        
        // Update target info objectives (9 total)
        targetStateTracker.updateTargetObjectives(player);
    }
    
    /**
     * Update the 3 scoreboard objectives for a specific bind slot.
     */
    private void updateBindObjectives(Scoreboard sb, Player player, int slot, BindState state) {
        String prefix = "sigil_bind" + slot;
        
        setObjectiveScore(sb, player, prefix + "_ready", state.getReadyAsInt());
        setObjectiveScore(sb, player, prefix + "_cd", state.getCooldownPercent());
        setObjectiveScore(sb, player, prefix + "_type", state.getType());
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
