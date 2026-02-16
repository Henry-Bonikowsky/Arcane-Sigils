package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
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
        var log = ArmorSetsPlugin.getInstance().getLogger();

        log.info("[EnchantScale] onDamageLowest triggered - damager: " + event.getDamager().getType());

        if (!isEnabled()) {
            log.info("[EnchantScale] SKIP: Module not enabled (isEnabled=false)");
            return;
        }
        log.info("[EnchantScale] Module is enabled");

        if (!(event.getDamager() instanceof Player attacker)) {
            log.info("[EnchantScale] SKIP: Damager is not a player");
            return;
        }
        log.info("[EnchantScale] Attacker: " + attacker.getName());

        // Read Sharpness level from weapon
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) {
            log.info("[EnchantScale] SKIP: No weapon in hand");
            return;
        }
        log.info("[EnchantScale] Weapon: " + weapon.getType());

        int sharpnessLevel = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharpnessLevel == 0) {
            log.info("[EnchantScale] SKIP: Weapon has no Sharpness enchant");
            return;
        }
        log.info("[EnchantScale] Sharpness level: " + sharpnessLevel);

        // Get scalar for this Sharpness level
        double scalar = config.getSharpnessScalar(sharpnessLevel);
        log.info("[EnchantScale] Config scalar for Sharp " + sharpnessLevel + ": " + scalar);

        if (scalar == 1.0) {
            log.info("[EnchantScale] SKIP: Scalar is 1.0 (no change)");
            return;
        }

        // Apply damage scaling
        double damage = event.getDamage();
        double newDamage = damage * scalar;
        event.setDamage(newDamage);
        log.info("[EnchantScale] APPLIED: damage " + damage + " -> " + newDamage + " (x" + scalar + ")");
    }

    /**
     * HIGHEST priority - apply resistance descalar after armor calculations
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageHighest(EntityDamageByEntityEvent event) {
        var log = ArmorSetsPlugin.getInstance().getLogger();

        if (!isEnabled()) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        log.info("[EnchantScale] onDamageHighest - PvP detected, victim: " + victim.getName());

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

                log.info("[EnchantScale] Armor piece " + piece.getType() + " Prot " + protectionLevel +
                    " -> vanilla=" + vanillaPieceReduction + ", scalar=" + scalar + ", scaled=" + scaledPieceReduction);
            }
        }

        if (vanillaReduction == 0.0) {
            log.info("[EnchantScale] SKIP: No Protection enchants on victim");
            return;
        }

        // Calculate additional reduction (difference between scaled and vanilla)
        // Vanilla already applied its reduction, we add the extra from scaling
        double additionalReduction = scaledReduction - vanillaReduction;
        double currentDamage = event.getDamage();
        double newDamage = currentDamage * (1.0 - additionalReduction);
        event.setDamage(newDamage);

        log.info("[EnchantScale] Protection total: vanilla=" + vanillaReduction + ", scaled=" + scaledReduction +
            ", additionalReduction=" + additionalReduction);
        log.info("[EnchantScale] APPLIED: damage " + currentDamage + " -> " + newDamage);
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
        List<ModuleParam> params = new ArrayList<>();

        // Base damage scaling params
        params.add(ModuleParam.builder("damage-scalar")
            .displayName("Damage Scalar")
            .description("Multiplies all outgoing player damage (before armor)")
            .doubleValue(config::getDamageScalar, config::setDamageScalar)
            .range(0.1, 5.0)
            .step(0.1)
            .build());

        params.add(ModuleParam.builder("resistance-descalar")
            .displayName("Resistance De-scalar")
            .description("Reduces armor effectiveness (2.0 = half armor protection)")
            .doubleValue(config::getResistanceDescalar, config::setResistanceDescalar)
            .range(0.1, 5.0)
            .step(0.1)
            .build());

        // Enchantment scaling toggle
        params.add(ModuleParam.builder("enchant-scaling")
            .displayName("Enchant Scaling")
            .description("Enable per-level Sharpness/Protection scaling")
            .boolValue(config::isEnchantmentScalingEnabled, config::setEnchantmentScalingEnabled)
            .build());

        // Sharpness levels 5-10 (extended levels beyond vanilla)
        for (int level = 5; level <= 10; level++) {
            final int lvl = level;
            params.add(ModuleParam.builder("sharpness-" + level)
                .displayName("Sharp " + level)
                .description("Sharpness " + level + " damage multiplier")
                .doubleValue(() -> config.getSharpnessScalar(lvl), v -> config.setSharpnessScalar(lvl, v))
                .range(0.5, 3.0)
                .step(0.05)
                .build());
        }

        // Protection levels 5-10 (extended levels beyond vanilla)
        for (int level = 5; level <= 10; level++) {
            final int lvl = level;
            params.add(ModuleParam.builder("protection-" + level)
                .displayName("Prot " + level)
                .description("Protection " + level + " effectiveness multiplier")
                .doubleValue(() -> config.getProtectionScalar(lvl), v -> config.setProtectionScalar(lvl, v))
                .range(0.5, 3.0)
                .step(0.05)
                .build());
        }

        return params;
    }
}
