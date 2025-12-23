package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
            int damageAmount = (int) power; // Direct durability points

            damageArmor(inv.getHelmet(), damageAmount);
            damageArmor(inv.getChestplate(), damageAmount);
            damageArmor(inv.getLeggings(), damageAmount);
            damageArmor(inv.getBoots(), damageAmount);
        }

        // Visual effects
        target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation().add(0, 1, 0), 20);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);

        debug("Damaged armor of " + target.getName() + " by " + power + " durability");
        return true;
    }

    private void damageArmor(ItemStack item, int amount) {
        if (item == null || !item.getType().name().contains("_")) return;

        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
            int maxDurability = item.getType().getMaxDurability();
            if (maxDurability > 0) {
                int newDamage = damageable.getDamage() + amount;
                damageable.setDamage(Math.min(newDamage, maxDurability));
                item.setItemMeta(damageable);
            }
        }
    }
}
