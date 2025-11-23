package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import org.bukkit.entity.Player;

public class ReplenishEffect extends AbstractEffect {

    public ReplenishEffect() {
        super("REPLENISH", "Restores hunger");
    }

    @Override
    public boolean execute(EffectContext context) {
        double amount = context.getParams() != null ? context.getParams().getValue() : 4;

        Player player = context.getPlayer();
        int newFood = Math.min(player.getFoodLevel() + (int) amount, 20);
        player.setFoodLevel(newFood);
        player.setSaturation(Math.min(player.getSaturation() + (float) amount / 2, newFood));

        debug("Replenished " + amount + " hunger for " + player.getName());
        return true;
    }
}
