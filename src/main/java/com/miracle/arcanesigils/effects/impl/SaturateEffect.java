package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import org.bukkit.entity.Player;

public class SaturateEffect extends AbstractEffect {

    public SaturateEffect() {
        super("SATURATE", "Restore hunger");
    }

    @Override
    public boolean execute(EffectContext context) {
        double amount = context.getParams() != null ? context.getParams().getValue() : 4;

        Player player = context.getPlayer();
        int newFood = Math.min(player.getFoodLevel() + (int) amount, 20);
        player.setFoodLevel(newFood);
        // Add saturation (capped at food level and max 20.0)
        float newSaturation = Math.min(player.getSaturation() + (float) amount, Math.min(newFood, 20.0f));
        player.setSaturation(newSaturation);
        return true;
    }
}
