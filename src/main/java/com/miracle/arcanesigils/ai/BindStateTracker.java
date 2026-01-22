package com.miracle.arcanesigils.ai;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.binds.BindPreset;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.flow.FlowConfig;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Computes bind state for AI training scoreboard objectives.
 * Handles multi-sigil slot aggregation and bind type classification.
 */
public class BindStateTracker {
    
    private final ArmorSetsPlugin plugin;
    
    public BindStateTracker(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Compute the state of a bind slot for a player.
     * Multi-sigil aggregation: ALL sigils must be ready, cooldown % is average.
     */
    public BindState computeBindState(Player player, int slot) {
        BindPreset binds = plugin.getBindsManager().getPlayerData(player).getCurrentBinds();
        List<String> sigilIds = binds.getBind(slot);
        
        // Empty slot
        if (sigilIds.isEmpty()) {
            return new BindState(true, 0, 0);
        }
        
        boolean allReady = true;
        double totalCooldownPct = 0;
        int validFlows = 0;
        int highestType = 0;
        
        for (String sigilId : sigilIds) {
            Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
            if (sigil == null) continue;
            
            // Check all flows for this sigil
            for (FlowConfig flow : sigil.getFlows()) {
                validFlows++;
                
                // Check cooldown for this flow
                String flowId = flow.getGraph() != null ? flow.getGraph().getId() : "unknown";
                String cooldownKey = "sigil_" + sigilId + "_" + flowId;
                boolean onCooldown = plugin.getCooldownManager().isOnCooldown(player, cooldownKey);
                
                if (onCooldown) {
                    allReady = false;
                    double remaining = plugin.getCooldownManager().getRemainingCooldown(player, cooldownKey);
                    double total = flow.getCooldown();
                    if (total > 0) {
                        double pct = (remaining / total) * 100.0;
                        totalCooldownPct += pct;
                    }
                }
                
                // Classify bind type based on trigger signal
                int type = classifyFlowType(flow);
                highestType = Math.max(highestType, type);
            }
        }
        
        // Average cooldown percentage across all flows
        int avgCooldownPct = validFlows > 0 ? (int)(totalCooldownPct / validFlows) : 0;
        
        return new BindState(allReady, avgCooldownPct, highestType);
    }
    
    /**
     * Classify flow type based on trigger signal.
     * ATTACK signals = 100 (offensive)
     * DEFENSE signals = 50 (defensive)
     * Other signals = 25 (utility)
     */
    private int classifyFlowType(FlowConfig flow) {
        String trigger = flow.getTrigger();
        if (trigger == null) {
            return 25; // Default to utility for ABILITY flows
        }
        
        // Normalize to uppercase for comparison
        trigger = trigger.toUpperCase();
        
        // Offensive: ATTACK signals
        if (trigger.contains("ATTACK") || trigger.contains("BOW_SHOOT") || trigger.contains("BOW_HIT")) {
            return 100;
        }
        
        // Defensive: DEFENSE signals
        if (trigger.contains("DEFENSE") || trigger.contains("DEFEND")) {
            return 50;
        }
        
        // Everything else is utility (SHIFT, INTERACT, PASSIVE, etc.)
        return 25;
    }
}
