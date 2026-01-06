package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Repair armor effect - fully repairs all equipped armor pieces.
 * Format: REPAIR_ARMOR
 *
 * No parameters - repairs all armor to full durability.
 */
public class RepairArmorEffect extends AbstractEffect {

    public RepairArmorEffect() {
        super("REPAIR_ARMOR", "Fully repair all equipped armor");
    }

    @Override
    public boolean execute(EffectContext context) {
        Player player = context.getPlayer();
        PlayerInventory inv = player.getInventory();

        int repairedCount = 0;

        repairedCount += repairItem(inv.getHelmet());
        repairedCount += repairItem(inv.getChestplate());
        repairedCount += repairItem(inv.getLeggings());
        repairedCount += repairItem(inv.getBoots());

        if (repairedCount > 0) {
            // Visual and sound effects
            player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.5f);

            debug("Repaired " + repairedCount + " armor pieces for " + player.getName());
            return true;
        }

        debug("No armor to repair for " + player.getName());
        return false;
    }

    private int repairItem(ItemStack item) {
        if (item == null) return 0;

        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
            if (damageable.getDamage() > 0) {
                damageable.setDamage(0);
                item.setItemMeta(damageable);
                return 1;
            }
        }
        return 0;
    }
}
