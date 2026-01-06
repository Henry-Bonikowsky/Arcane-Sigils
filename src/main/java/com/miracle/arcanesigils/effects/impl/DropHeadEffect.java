package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Drops the head of the victim when killed.
 * Format: DROP_HEAD
 *
 * Only works when victim is a player.
 */
public class DropHeadEffect extends AbstractEffect {

    public DropHeadEffect() {
        super("DROP_HEAD", "Drop victim's head on kill");
    }

    @Override
    public boolean execute(EffectContext context) {
        // Only works on player victims
        if (!(context.getVictim() instanceof Player victim)) {
            debug("DROP_HEAD failed - victim is not a player");
            return false;
        }

        // Create player head item
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(victim);
            head.setItemMeta(meta);
        }

        // Drop at victim's location
        victim.getWorld().dropItemNaturally(victim.getLocation(), head);

        debug("Dropped head of " + victim.getName());
        return true;
    }
}
