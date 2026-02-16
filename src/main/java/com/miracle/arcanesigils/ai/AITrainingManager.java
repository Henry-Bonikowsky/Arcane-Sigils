package com.miracle.arcanesigils.ai;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Main coordinator for AI training features.
 * Manages scoreboard objectives, reward signals, and combo tracking.
 */
public class AITrainingManager {
    
    private final ArmorSetsPlugin plugin;
    private final AITrainingConfig config;
    private final BindStateTracker bindStateTracker;
    private final TargetStateTracker targetStateTracker;
    private final ComboTracker comboTracker;
    private final RewardSignalSender signalSender;
    private ScoreboardUpdateTask updateTask;
    private final Set<UUID> enabledPlayers = new HashSet<>();
    
    public AITrainingManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.config = new AITrainingConfig(plugin);
        this.bindStateTracker = new BindStateTracker(plugin);
        this.targetStateTracker = new TargetStateTracker(plugin);
        this.comboTracker = new ComboTracker(config.getComboWindowMs());
        this.signalSender = new RewardSignalSender();
        
        if (config.isEnabled()) {
            initialize();
        }
    }
    
    /**
     * Initialize AI training system.
     */
    private void initialize() {
        plugin.getLogger().info("AI Training mode enabled");
        
        // Create scoreboard objectives for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            initializePlayerObjectives(player);
        }
        
        // Start scoreboard update task
        int interval = config.getUpdateInterval();
        updateTask = new ScoreboardUpdateTask(plugin, bindStateTracker, targetStateTracker);
        updateTask.runTaskTimer(plugin, interval, interval);
        
        plugin.getLogger().info("AI Training scoreboard updating every " + interval + " ticks");
    }
    
    /**
     * Initialize scoreboard objectives for a player.
     * Creates 21 objectives: 12 for bind state + 9 for target info.
     */
    public void initializePlayerObjectives(Player player) {
        Scoreboard sb = player.getScoreboard();
        if (sb == Bukkit.getScoreboardManager().getMainScoreboard()) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(sb);
        }
        
        // Bind state objectives (12 total)
        for (int slot = 1; slot <= 4; slot++) {
            String prefix = "sigil_bind" + slot;
            createObjective(sb, prefix + "_ready", "Bind " + slot + " Ready");
            createObjective(sb, prefix + "_cd", "Bind " + slot + " Cooldown");
            createObjective(sb, prefix + "_type", "Bind " + slot + " Type");
        }
        
        // Target info objectives (9 total)
        createObjective(sb, "sigil_target_exists", "Target Exists");
        createObjective(sb, "sigil_target_type", "Target Type");
        createObjective(sb, "sigil_target_health", "Target Health %");
        createObjective(sb, "sigil_target_distance", "Target Distance");
        createObjective(sb, "sigil_target_hostile", "Target Hostile");
        createObjective(sb, "sigil_target_player", "Target Player");
        createObjective(sb, "sigil_target_boss", "Target Boss");
        createObjective(sb, "sigil_target_armor", "Target Armor");
        createObjective(sb, "sigil_target_los", "Target LOS");
    }
    
    /**
     * Create a scoreboard objective if it doesn't exist.
     */
    private void createObjective(Scoreboard sb, String name, String displayName) {
        if (sb.getObjective(name) == null) {
            sb.registerNewObjective(name, "dummy", displayName);
        }
    }
    
    /**
     * Check if AI training is enabled for a player.
     */
    public boolean isEnabledForPlayer(Player player) {
        return config.isEnabled() && enabledPlayers.contains(player.getUniqueId());
    }
    
    /**
     * Enable or disable AI training for a specific player.
     */
    public void setEnabledForPlayer(Player player, boolean enabled) {
        if (enabled) {
            enabledPlayers.add(player.getUniqueId());
        } else {
            enabledPlayers.remove(player.getUniqueId());
        }
    }
    
    /**
     * Send a hit signal.
     */
    public void sendHitSignal(Player player, int bindSlot, double damage) {
        if (!isEnabledForPlayer(player)) return;
        
        RewardSignal signal = RewardSignal.hit(bindSlot, damage);
        signalSender.sendSignal(player, signal);
        
        // Check for combo
        ComboTracker.ComboInfo combo = comboTracker.recordActivation(player, bindSlot, damage);
        if (combo != null) {
            signalSender.sendComboSignal(player, combo.getComboString(), combo.totalDamage);
        }
    }
    
    /**
     * Send a miss signal.
     */
    public void sendMissSignal(Player player, int bindSlot) {
        if (!isEnabledForPlayer(player)) return;
        RewardSignal signal = RewardSignal.miss(bindSlot);
        signalSender.sendSignal(player, signal);
    }
    
    /**
     * Send a kill signal.
     */
    public void sendKillSignal(Player player, int bindSlot, String entityType) {
        if (!isEnabledForPlayer(player)) return;
        RewardSignal signal = RewardSignal.kill(bindSlot, entityType);
        signalSender.sendSignal(player, signal);
    }
    
    /**
     * Send a cooldown signal.
     */
    public void sendCooldownSignal(Player player, int bindSlot, double remainingSeconds) {
        if (!isEnabledForPlayer(player)) return;
        RewardSignal signal = RewardSignal.cooldown(bindSlot, remainingSeconds);
        signalSender.sendSignal(player, signal);
    }
    
    /**
     * Send a heal signal.
     */
    public void sendHealSignal(Player player, int bindSlot, double healAmount) {
        if (!isEnabledForPlayer(player)) return;
        
        RewardSignal signal = RewardSignal.heal(bindSlot, healAmount);
        signalSender.sendSignal(player, signal);
        
        // Check for combo (healing counts too)
        ComboTracker.ComboInfo combo = comboTracker.recordActivation(player, bindSlot, 0);
        if (combo != null) {
            signalSender.sendComboSignal(player, combo.getComboString(), combo.totalDamage);
        }
    }
    
    /**
     * Send a buff signal.
     */
    public void sendBuffSignal(Player player, int bindSlot, String buffName, double durationSeconds) {
        if (!isEnabledForPlayer(player)) return;
        RewardSignal signal = RewardSignal.buff(bindSlot, buffName, durationSeconds);
        signalSender.sendSignal(player, signal);
    }
    
    /**
     * Send a condition failure signal.
     */
    public void sendConditionFailSignal(Player player, int bindSlot, String reason) {
        if (!isEnabledForPlayer(player)) return;
        RewardSignal signal = RewardSignal.conditionFail(bindSlot, reason);
        signalSender.sendSignal(player, signal);
    }
    
    /**
     * Send a crowd control signal.
     */
    public void sendCCSignal(Player player, int bindSlot, String ccType, double value) {
        if (!isEnabledForPlayer(player)) return;
        RewardSignal signal = RewardSignal.cc(bindSlot, ccType, value);
        signalSender.sendSignal(player, signal);
    }
    
    /**
     * Get the combo tracker.
     */
    public ComboTracker getComboTracker() {
        return comboTracker;
    }
    
    /**
     * Shutdown AI training system.
     */
    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        plugin.getLogger().info("AI Training system shut down");
    }
}
