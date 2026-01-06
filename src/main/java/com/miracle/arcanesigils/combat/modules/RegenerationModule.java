package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * Implements 1.8-style slow regeneration instead of 1.9+ saturation-based fast regen.
 *
 * 1.8 mechanics:
 * - Regenerate 1 HP every 4 seconds (80 ticks) when hunger >= 18 (9 drumsticks)
 * - No "saturation boost" fast regeneration
 *
 * 1.9+ mechanics (disabled by this module):
 * - Fast regen (2 HP/second) when saturation > 0 and hunger is full
 */
public class RegenerationModule extends AbstractCombatModule implements Listener {

    private BukkitTask regenTask;

    public RegenerationModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "regeneration";
    }

    @Override
    public String getDisplayName() {
        return "Regeneration";
    }

    @Override
    public void onEnable() {
        // Start custom regeneration task
        startRegenTask();
    }

    @Override
    public void onDisable() {
        if (regenTask != null) {
            regenTask.cancel();
            regenTask = null;
        }
    }

    @Override
    public void reload() {
        super.reload();
        // Restart task with new interval
        if (isEnabled()) {
            if (regenTask != null) {
                regenTask.cancel();
            }
            startRegenTask();
        }
    }

    private void startRegenTask() {
        int interval = config.getRegenIntervalTicks();
        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRegen, interval, interval);
    }

    private void tickRegen() {
        if (!isEnabled()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Check if player should regenerate
            if (!shouldRegenerate(player)) continue;

            // Calculate max health
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            double currentHealth = player.getHealth();

            // Already at max health
            if (currentHealth >= maxHealth) continue;

            // Apply 1.8-style regeneration
            double newHealth = Math.min(currentHealth + config.getRegenAmount(), maxHealth);
            player.setHealth(newHealth);

            // Drain exhaustion like vanilla (optional, for authenticity)
            float exhaustion = player.getExhaustion();
            player.setExhaustion(Math.min(exhaustion + 3.0f, 40.0f));
        }
    }

    private boolean shouldRegenerate(Player player) {
        // Must have enough hunger (18 = 9 drumsticks in 1.8)
        if (player.getFoodLevel() < config.getRegenMinHunger()) {
            return false;
        }

        // Must not be dead or in spectator
        if (player.isDead() || player.getGameMode().name().equals("SPECTATOR")) {
            return false;
        }

        return true;
    }

    /**
     * Block the vanilla saturation-based fast regeneration.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHealthRegain(EntityRegainHealthEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        // Block saturation-based regen
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            if (config.isDisableSaturationBoost()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Optionally prevent saturation from accumulating to avoid confusion.
     * Players might wonder why their hunger bar isn't behaving as expected.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!config.isDisableSaturationBoost()) return;

        // Cap saturation at max vanilla value (20.0) to prevent infinite buildup
        // Raised from 5.0 to allow sigils to work properly while still preventing exploits
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.getSaturation() > 20.0f) {
                player.setSaturation(20.0f);
            }
        }, 1L);
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("regen-amount")
                .displayName("Regen Amount")
                .description("Health restored per tick")
                .doubleValue(config::getRegenAmount, config::setRegenAmount)
                .range(0.5, 4.0)
                .step(0.5)
                .build(),
            ModuleParam.builder("regen-interval")
                .displayName("Regen Interval")
                .description("Ticks between regen (80 = 4 seconds)")
                .tickValue(config::getRegenIntervalTicks, config::setRegenIntervalTicks)
                .range(20, 200)
                .step(10)
                .build(),
            ModuleParam.builder("min-hunger")
                .displayName("Min Hunger")
                .description("Minimum hunger to regenerate (18 = 9 bars)")
                .intValue(config::getRegenMinHunger, config::setRegenMinHunger)
                .range(1, 20)
                .step(1)
                .build(),
            ModuleParam.builder("disable-saturation")
                .displayName("Block Fast Regen")
                .description("Disable 1.9+ saturation-based fast regen")
                .boolValue(config::isDisableSaturationBoost, config::setDisableSaturationBoost)
                .build()
        );
    }
}
