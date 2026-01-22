package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KnockbackModule extends AbstractCombatModule implements Listener {

    private final Map<Player, Vector> queuedKnockback = new HashMap<>();

    public KnockbackModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "knockback";
    }

    @Override
    public String getDisplayName() {
        return "Knockback";
    }

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskTimer(plugin, queuedKnockback::clear, 1L, 1L);
    }

    @Override
    public void onDisable() {
        queuedKnockback.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        
        Player attacker = getAttacker(event);
        if (attacker == null) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Vector velocity = victim.getVelocity().clone();

        // Friction
        velocity.setX(velocity.getX() / 2.0);
        velocity.setY(velocity.getY() / 2.0);
        velocity.setZ(velocity.getZ() / 2.0);

        // Direction (attacker -> victim)
        double dx = victim.getLocation().getX() - attacker.getLocation().getX();
        double dz = victim.getLocation().getZ() - attacker.getLocation().getZ();
        double magnitude = Math.sqrt(dx * dx + dz * dz);

        if (magnitude > 0) {
            double horizontal = config.getKbHorizontalBase();
            velocity.setX(velocity.getX() + (dx / magnitude * horizontal));
            velocity.setZ(velocity.getZ() + (dz / magnitude * horizontal));
        }

        // Vertical knockback
        velocity.setY(velocity.getY() + config.getKbVerticalBase());
        double cap = config.getKbVerticalCap();
        if (velocity.getY() > cap) {
            velocity.setY(cap);
        }

        // Calculate knockback "level" (sprint + enchant)
        int level = 0;
        boolean wasSprinting = attacker.isSprinting();
        if (wasSprinting) {
            level++;
        }
        
        // Check for Knockback enchantment
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon != null && weapon.containsEnchantment(Enchantment.KNOCKBACK)) {
            level += weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
        }

        // Apply sprint/enchant bonus
        if (level > 0) {
            float yaw = attacker.getLocation().getYaw();
            double extraH = config.getKbExtraHorizontal() * level;
            double extraV = config.getKbExtraVertical() * level;
            
            velocity.add(new Vector(
                -Math.sin(yaw * Math.PI / 180.0F) * extraH,
                extraV,
                Math.cos(yaw * Math.PI / 180.0F) * extraH
            ));
        }

        // Cap horizontal velocity to prevent excessive knockback
        double hCap = config.getKbHorizontalCap();
        double horizontalMag = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
        if (horizontalMag > hCap) {
            double scale = hCap / horizontalMag;
            velocity.setX(velocity.getX() * scale);
            velocity.setZ(velocity.getZ() * scale);
        }

        queuedKnockback.put(victim, velocity);
        
        // Cancel sprint AFTER knockback is calculated (1.8 behavior)
        if (wasSprinting) {
            attacker.setSprinting(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVelocity(PlayerVelocityEvent event) {
        if (!isEnabled()) return;
        
        Vector queued = queuedKnockback.get(event.getPlayer());
        if (queued != null) {
            event.setVelocity(queued);
            queuedKnockback.remove(event.getPlayer());
        }
    }

    private Player getAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof org.bukkit.entity.Projectile proj) {
            if (proj.getShooter() instanceof Player shooter) return shooter;
        }
        return null;
    }

    @Override
    public void applyToPlayer(Player player) {}

    @Override
    public void removeFromPlayer(Player player) {
        queuedKnockback.remove(player);
    }

    @Override
    public void reload() {
        super.reload();
        queuedKnockback.clear();
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("horizontal-base")
                .displayName("Horizontal Base")
                .description("Base horizontal KB (0.35 = Kohi)")
                .doubleValue(config::getKbHorizontalBase, config::setKbHorizontalBase)
                .range(0.1, 1.0)
                .step(0.05)
                .build(),
            ModuleParam.builder("vertical-base")
                .displayName("Vertical Base")
                .description("Base vertical KB (0.35 = Kohi)")
                .doubleValue(config::getKbVerticalBase, config::setKbVerticalBase)
                .range(0.1, 1.0)
                .step(0.05)
                .build(),
            ModuleParam.builder("vertical-cap")
                .displayName("Vertical Cap")
                .description("Max Y velocity (0.4 = Kohi)")
                .doubleValue(config::getKbVerticalCap, config::setKbVerticalCap)
                .range(0.2, 1.0)
                .step(0.05)
                .build(),
            ModuleParam.builder("horizontal-cap")
                .displayName("Horizontal Cap")
                .description("Max horizontal velocity (0.5 = balanced)")
                .doubleValue(config::getKbHorizontalCap, config::setKbHorizontalCap)
                .range(0.3, 1.0)
                .step(0.05)
                .build(),
            ModuleParam.builder("extra-horizontal")
                .displayName("Sprint/Enchant Horizontal")
                .description("Per level bonus (0.425 = Kohi)")
                .doubleValue(config::getKbExtraHorizontal, config::setKbExtraHorizontal)
                .range(0.0, 1.0)
                .step(0.025)
                .build(),
            ModuleParam.builder("extra-vertical")
                .displayName("Sprint/Enchant Vertical")
                .description("Per level Y bonus (0.085 = Kohi)")
                .doubleValue(config::getKbExtraVertical, config::setKbExtraVertical)
                .range(0.0, 0.2)
                .step(0.005)
                .build()
        );
    }
}
