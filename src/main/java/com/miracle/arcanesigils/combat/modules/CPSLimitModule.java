package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits player clicks per second (CPS) to prevent autoclickers.
 * After exceeding the CPS cap, additional hits are ignored.
 */
public class CPSLimitModule extends AbstractCombatModule implements Listener {

    private static final int DEFAULT_CPS_LIMIT = 12;
    private static final long ONE_SECOND_MS = 1000L;

    private final Map<UUID, long[]> clickHistory = new ConcurrentHashMap<>();
    private int cpsLimit = DEFAULT_CPS_LIMIT;

    public CPSLimitModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "cps-limit";
    }

    @Override
    public String getDisplayName() {
        return "CPS Limit";
    }

    @Override
    public void applyToPlayer(Player player) {
        // No attribute changes needed
    }

    @Override
    public void removeFromPlayer(Player player) {
        clickHistory.remove(player.getUniqueId());
    }

    @Override
    public void reload() {
        super.reload();
        cpsLimit = manager.getConfig().getMaxCps();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;

        if (!(event.getDamager() instanceof Player attacker)) return;

        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();

        // Get or create click history array (circular buffer)
        long[] clicks = clickHistory.computeIfAbsent(uuid, k -> new long[cpsLimit + 1]);

        // Count clicks in the last second
        int recentClicks = 0;
        for (long clickTime : clicks) {
            if (clickTime > 0 && now - clickTime < ONE_SECOND_MS) {
                recentClicks++;
            }
        }

        // If over CPS limit, cancel the attack
        if (recentClicks >= cpsLimit) {
            event.setCancelled(true);
            return;
        }

        // Record this click (find oldest slot)
        int oldestIndex = 0;
        long oldestTime = Long.MAX_VALUE;
        for (int i = 0; i < clicks.length; i++) {
            if (clicks[i] < oldestTime) {
                oldestTime = clicks[i];
                oldestIndex = i;
            }
        }
        clicks[oldestIndex] = now;
    }

    public int getCpsLimit() {
        return cpsLimit;
    }

    public void setCpsLimit(int limit) {
        this.cpsLimit = limit;
    }
}
