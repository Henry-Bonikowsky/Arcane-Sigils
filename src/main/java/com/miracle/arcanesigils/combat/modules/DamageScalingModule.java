package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies global damage scaling and resistance de-scaling.
 *
 * Damage Scalar: Multiplies all outgoing damage from players (applied before armor)
 * Resistance De-scalar: Reduces armor/protection effectiveness (applied after armor)
 *
 * Example: With damage scalar 2.0 and resistance descalar 2.0:
 * - Sharpness 30 deals 2x damage before armor
 * - Armor protection is half as effective (2x descalar)
 */
public class DamageScalingModule extends AbstractCombatModule implements Listener {

    // Track raw damage before armor calculation
    private final Map<UUID, Double> rawDamageMap = new HashMap<>();

    public DamageScalingModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "global-damage-scaling";
    }

    @Override
    public String getDisplayName() {
        return "Global Damage Scaling";
    }

    /**
     * LOWEST priority - apply damage scalar before any other modifications
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageLowest(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        // Check if enchantment scaling is enabled
        if (!config.isEnchantmentScalingEnabled()) return;

        // Read Sharpness level from weapon
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;

        int sharpnessLevel = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharpnessLevel == 0) return;

        // Get scalar for this Sharpness level
        double scalar = config.getSharpnessScalar(sharpnessLevel);
        if (scalar == 1.0) return;

        // Apply damage scaling
        double damage = event.getDamage();
        event.setDamage(damage * scalar);
    }

    /**
     * HIGHEST priority - apply resistance descalar after armor calculations
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageHighest(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        // Check if enchantment scaling is enabled
        if (!config.isEnchantmentScalingEnabled()) return;

        // Calculate scaled Protection from victim's armor
        ItemStack[] armor = victim.getInventory().getArmorContents();
        double vanillaReduction = 0.0;
        double scaledReduction = 0.0;

        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir()) continue;

            int protectionLevel = piece.getEnchantmentLevel(Enchantment.PROTECTION);
            if (protectionLevel > 0) {
                // Vanilla: each Protection level = 4% damage reduction per piece
                double vanillaPieceReduction = protectionLevel * 0.04;
                vanillaReduction += vanillaPieceReduction;

                // Scaled: apply our scalar to this piece's contribution
                double scalar = config.getProtectionScalar(protectionLevel);
                double scaledPieceReduction = vanillaPieceReduction * scalar;
                scaledReduction += scaledPieceReduction;
            }
        }

        if (vanillaReduction == 0.0) return; // No Protection enchants

        // Calculate additional reduction (difference between scaled and vanilla)
        // Vanilla already applied its reduction, we add the extra from scaling
        double additionalReduction = scaledReduction - vanillaReduction;
        double currentDamage = event.getDamage();
        event.setDamage(currentDamage * (1.0 - additionalReduction));
    }

    /**
     * Cleanup map on non-PvP damage to prevent memory leaks
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageCleanup(EntityDamageEvent event) {
        rawDamageMap.remove(event.getEntity().getUniqueId());
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("damage-scalar")
                .displayName("Damage Scalar")
                .description("Multiplies all outgoing player damage (before armor)")
                .doubleValue(config::getDamageScalar, config::setDamageScalar)
                .range(0.1, 5.0)
                .step(0.1)
                .build(),
            ModuleParam.builder("resistance-descalar")
                .displayName("Resistance De-scalar")
                .description("Reduces armor effectiveness (2.0 = half armor protection)")
                .doubleValue(config::getResistanceDescalar, config::setResistanceDescalar)
                .range(0.1, 5.0)
                .step(0.1)
                .build()
        );
    }
}
