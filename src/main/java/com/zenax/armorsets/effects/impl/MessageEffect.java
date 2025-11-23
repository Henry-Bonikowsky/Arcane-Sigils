package com.zenax.armorsets.effects.impl;

import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.effects.EffectParams;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class MessageEffect extends AbstractEffect {

    public MessageEffect() {
        super("MESSAGE", "Sends a message to target player");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        // Extract message after MESSAGE:
        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        if (cleanedString.startsWith("MESSAGE:")) {
            String message = cleanedString.substring(8);
            params.set("message", message);
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) return false;

        String message = params.getString("message", "");
        if (message.isEmpty()) return false;

        LivingEntity target = getTarget(context);
        if (target instanceof Player player) {
            player.sendMessage(TextUtil.colorize(message));
            return true;
        }

        return false;
    }
}
