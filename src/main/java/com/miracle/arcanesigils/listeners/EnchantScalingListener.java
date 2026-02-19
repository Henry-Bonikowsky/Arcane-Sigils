package com.miracle.arcanesigils.listeners;

import com.miracle.arcanesigils.combat.CombatUtil;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Overrides vanilla Sharpness, Protection, and Unbreaking enchant scaling with configurable values.
 * Also handles crit multiplier override.
 * Listens at LOW priority so adjustments happen before SignalHandler (NORMAL/HIGH/HIGHEST).
 */
public class EnchantScalingListener implements Listener {

    private final CombatUtil combatUtil;

    public EnchantScalingListener(CombatUtil combatUtil) {
        this.combatUtil = combatUtil;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Sharpness override
        if (combatUtil.isSharpnessEnabled()) {
            applySharpnessOverride(event);
        }

        // Crit multiplier override
        applyCritOverride(event);

        // Protection override (melee damage)
        if (combatUtil.isProtectionEnabled()) {
            applyProtectionOverride(event);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        // Skip EntityDamageByEntityEvent (handled above)
        if (event instanceof EntityDamageByEntityEvent) return;

        // Protection override for non-melee damage (fire, fall, etc.)
        if (combatUtil.isProtectionEnabled()) {
            applyProtectionOverrideGeneric(event);
        }
    }

    /**
     * Unbreaking override: intercepts durability damage and applies configurable ignore chance.
     * Works universally on all items (armor, weapons, tools) — no ItemsAdder detection needed.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        if (!combatUtil.isUnbreakingEnabled()) return;

        ItemStack item = event.getItem();
        int unbreakingLevel = item.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreakingLevel <= 0) return;

        double ignoreChance = combatUtil.getUnbreakingIgnoreChance(unbreakingLevel);
        if (ignoreChance <= 0) return;

        if (Math.random() < ignoreChance) {
            event.setCancelled(true);
        }
    }

    private void applySharpnessOverride(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof LivingEntity attacker)) return;
        if (attacker.getEquipment() == null) return;

        ItemStack weapon = attacker.getEquipment().getItemInMainHand();
        int sharpLevel = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharpLevel <= 0) return;

        // Vanilla sharpness formula: 0.5 * level + 0.5
        double vanillaBonus = 0.5 * sharpLevel + 0.5;
        double configuredBonus = combatUtil.getSharpnessBonus(sharpLevel);

        // Adjust: remove vanilla bonus, add configured bonus
        double adjustment = configuredBonus - vanillaBonus;
        if (Math.abs(adjustment) > 0.001) {
            event.setDamage(event.getDamage() + adjustment);
        }
    }

    /**
     * Override crit damage multiplier. Vanilla uses 1.5x for crits.
     * Detects crits the same way vanilla does: falling, not on ground, not in water.
     */
    private void applyCritOverride(EntityDamageByEntityEvent event) {
        double configuredMultiplier = combatUtil.getCritMultiplier();
        if (Math.abs(configuredMultiplier - 1.5) < 0.001) return; // Vanilla default, no change

        Entity damager = event.getDamager();
        if (!(damager instanceof Player attacker)) return;

        // Check if this was a critical hit (same conditions as vanilla)
        boolean isCrit = attacker.getFallDistance() > 0.0f
                && !attacker.isOnGround()
                && !attacker.isInsideVehicle()
                && !attacker.isInWater()
                && !attacker.isSprinting();

        if (!isCrit) return;

        // Vanilla already applied 1.5x. Adjust to our configured multiplier.
        // current = base * 1.5; desired = base * configured
        // adjustment = base * (configured - 1.5) = current / 1.5 * (configured - 1.5)
        double currentDamage = event.getDamage();
        double baseDamage = currentDamage / 1.5;
        double adjustment = baseDamage * (configuredMultiplier - 1.5);
        if (Math.abs(adjustment) > 0.001) {
            event.setDamage(currentDamage + adjustment);
        }
    }

    private void applyProtectionOverride(EntityDamageByEntityEvent event) {
        applyProtectionOverrideGeneric(event);
    }

    @SuppressWarnings("deprecation")
    private void applyProtectionOverrideGeneric(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (victim.getEquipment() == null) return;

        // Check if MAGIC damage modifier is applicable (used by Protection enchant)
        if (!event.isApplicable(EntityDamageEvent.DamageModifier.MAGIC)) return;

        // Sum Protection levels from all armor
        int totalProtLevel = 0;
        for (ItemStack piece : victim.getEquipment().getArmorContents()) {
            if (piece == null || piece.getType().isAir()) continue;
            totalProtLevel += piece.getEnchantmentLevel(Enchantment.PROTECTION);
        }
        if (totalProtLevel <= 0) return;

        // Calculate vanilla EPF reduction: min(totalEPF, 20) / 25.0
        int vanillaEPF = Math.min(totalProtLevel, 20);
        double vanillaReduction = vanillaEPF / 25.0;

        // Calculate configured reduction: sum of DR% per piece
        double configuredReduction = 0;
        for (ItemStack piece : victim.getEquipment().getArmorContents()) {
            if (piece == null || piece.getType().isAir()) continue;
            int protLevel = piece.getEnchantmentLevel(Enchantment.PROTECTION);
            if (protLevel > 0) {
                configuredReduction += combatUtil.getProtectionDR(protLevel);
            }
        }
        configuredReduction = Math.min(configuredReduction, 100.0) / 100.0;

        // Calculate post-armor damage (base + armor modifier)
        double baseDamage = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
        double armorReduction = event.getDamage(EntityDamageEvent.DamageModifier.ARMOR);
        double postArmorDamage = baseDamage + armorReduction;
        if (postArmorDamage <= 0) return;

        // Override the MAGIC modifier to apply our configured reduction instead of vanilla
        double newMagicModifier = -(postArmorDamage * configuredReduction);
        event.setDamage(EntityDamageEvent.DamageModifier.MAGIC, newMagicModifier);
    }
}
