package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CustomImmunityModule extends AbstractCombatModule implements Listener {

    private final Map<UUID, Long> lastHitTime = new ConcurrentHashMap<>();

    public CustomImmunityModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "custom-immunity";
    }

    @Override
    public String getDisplayName() {
        return "Custom Immunity";
    }

    @Override
    public void onEnable() {
        plugin.getLogger().info("[CustomImmunity] Enabled - 1.8 style hit delay active");
    }

    @Override
    public void onDisable() {
        lastHitTime.clear();
    }

    public boolean isImmune(Player player) {
        Long lastHit = lastHitTime.get(player.getUniqueId());
        if (lastHit == null) return false;
        
        int immunityTicks = config.getDamageImmunityTicks();
        long immunityMs = immunityTicks * 50;
        
        return (System.currentTimeMillis() - lastHit) < immunityMs;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        
        if (event.getEntity() instanceof Player victim) {
            if (isImmune(victim)) {
                event.setCancelled(true);
                
                // Notify HitSoundFilterModule to cancel sounds/animations
                HitSoundFilterModule soundFilter = manager.getModule("hit-sound-filter");
                if (soundFilter != null && soundFilter.isEnabled()) {
                    UUID attackerUuid = null;
                    if (event.getDamager() instanceof Player attacker) {
                        attackerUuid = attacker.getUniqueId();
                    }
                    if (attackerUuid != null) {
                        soundFilter.registerCancelledHit(attackerUuid, victim.getUniqueId());
                    }
                }
                return;
            }
            
            lastHitTime.put(victim.getUniqueId(), System.currentTimeMillis());
        }
    }

    @Override
    public void applyToPlayer(Player player) {}

    @Override
    public void removeFromPlayer(Player player) {
        lastHitTime.remove(player.getUniqueId());
    }

    @Override
    public void reload() {
        super.reload();
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("immunity-ticks")
                .displayName("Immunity Ticks")
                .description("Hit immunity duration (10 = 0.5s)")
                .intValue(config::getDamageImmunityTicks, config::setDamageImmunityTicks)
                .range(0, 20)
                .step(1)
                .build()
        );
    }
}
