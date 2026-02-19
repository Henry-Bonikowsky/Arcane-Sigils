package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class DamageArmorEffect extends AbstractEffect {

    public DamageArmorEffect() {
        super("DAMAGE_ARMOR", "Shred enemy armor durability");
    }

    @Override
    public boolean execute(EffectContext context) {
        double power = context.getParams() != null ? context.getParams().getValue() : 1;

        LivingEntity target = getTarget(context);
        if (target == null) return false;

        // If target is player, damage their armor
        if (target instanceof Player targetPlayer) {
            PlayerInventory inv = targetPlayer.getInventory();
            int damageAmount = (int) power;

            // Damage each slot and write back to inventory
            inv.setHelmet(damageArmor(inv.getHelmet(), damageAmount));
            inv.setChestplate(damageArmor(inv.getChestplate(), damageAmount));
            inv.setLeggings(damageArmor(inv.getLeggings(), damageAmount));
            inv.setBoots(damageArmor(inv.getBoots(), damageAmount));
        }

        debug("Damaged armor of " + target.getName() + " by " + power + " durability");
        return true;
    }

    private ItemStack damageArmor(ItemStack item, int amount) {
        if (item == null || item.getType().isAir()) return item;
        if (!isArmorMaterial(item.getType())) return item;

        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
            int maxDurability = damageable.hasMaxDamage()
                ? damageable.getMaxDamage()
                : item.getType().getMaxDurability();
            if (maxDurability > 0) {
                int newDamage = damageable.getDamage() + amount;
                damageable.setDamage(Math.min(newDamage, maxDurability));
                item.setItemMeta(damageable);
            }
        }
        return item;
    }

    private boolean isArmorMaterial(Material mat) {
        String name = mat.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
            || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }
}
