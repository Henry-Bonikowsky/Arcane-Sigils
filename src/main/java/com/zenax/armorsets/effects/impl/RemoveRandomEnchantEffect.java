package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Removes a random enchantment from the broken item.
 * Format: REMOVE_RANDOM_ENCHANT[:COUNT]
 *
 * Used by Refurbish sigil when an item breaks.
 */
public class RemoveRandomEnchantEffect extends AbstractEffect {

    public RemoveRandomEnchantEffect() {
        super("REMOVE_RANDOM_ENCHANT", "Remove random enchantment from item");
    }

    @Override
    public boolean execute(EffectContext context) {
        int count = (int) (context.getParams() != null ? context.getParams().getValue() : 1);

        // Get the broken item from metadata
        ItemStack item = context.getMetadata("brokenItem", null);
        if (item == null) {
            debug("REMOVE_RANDOM_ENCHANT failed - no broken item in context");
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            debug("REMOVE_RANDOM_ENCHANT failed - item has no meta");
            return false;
        }

        Map<Enchantment, Integer> enchants = meta.getEnchants();
        if (enchants.isEmpty()) {
            debug("REMOVE_RANDOM_ENCHANT - item has no enchantments to remove");
            return true; // Not a failure, just nothing to remove
        }

        // Convert to list for random selection
        List<Enchantment> enchantList = new ArrayList<>(enchants.keySet());
        int removed = 0;

        for (int i = 0; i < count && !enchantList.isEmpty(); i++) {
            int randomIndex = ThreadLocalRandom.current().nextInt(enchantList.size());
            Enchantment toRemove = enchantList.remove(randomIndex);
            meta.removeEnchant(toRemove);
            removed++;
            debug("Removed enchantment: " + toRemove.getKey().getKey());
        }

        item.setItemMeta(meta);
        debug("Removed " + removed + " random enchantment(s)");
        return true;
    }
}
