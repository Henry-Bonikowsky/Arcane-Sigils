package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Message effect supporting multiple display types:
 * - MESSAGE:text - Chat message (default)
 * - MESSAGE:ACTIONBAR:text - Chat message (ACTIONBAR deprecated, now uses CHAT)
 * - MESSAGE:TITLE:text - Title (big center text)
 * - MESSAGE:SUBTITLE:text - Subtitle (smaller center text)
 */
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
            String content = cleanedString.substring(8);

            // Check for key=value format first
            if (content.contains("=")) {
                String[] parts = content.split(":");
                for (String part : parts) {
                    if (part.contains("=")) {
                        String[] kv = part.split("=", 2);
                        if (kv.length == 2) {
                            String key = kv[0].toLowerCase();
                            String value = kv[1];
                            switch (key) {
                                case "type" -> params.set("type", value.toUpperCase());
                                case "message", "msg", "text" -> params.set("message", value);
                            }
                        }
                    }
                }
            }
            // Check for message type prefix (legacy format)
            else if (content.toUpperCase().startsWith("ACTIONBAR:")) {
                params.set("type", "ACTIONBAR");
                params.set("message", content.substring(10));
            } else if (content.toUpperCase().startsWith("TITLE:")) {
                params.set("type", "TITLE");
                params.set("message", content.substring(6));
            } else if (content.toUpperCase().startsWith("SUBTITLE:")) {
                params.set("type", "SUBTITLE");
                params.set("message", content.substring(9));
            } else if (content.toUpperCase().startsWith("CHAT:")) {
                params.set("type", "CHAT");
                params.set("message", content.substring(5));
            } else {
                params.set("type", "CHAT");
                params.set("message", content);
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) {
            com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Params are NULL");
            return false;
        }

        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Params: %s", params);

        String message = params.getString("message", "");
        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Raw message string: '%s'", message);

        if (message.isEmpty()) {
            com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Message is EMPTY - aborting");
            return false;
        }

        String type = params.getString("type", "CHAT").toUpperCase();
        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Type: %s", type);

        LivingEntity target = getTarget(context);
        if (target instanceof Player player) {
            Component component = TextUtil.parseComponent(message);
            com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Component created: %s", component);
            com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Sending to player: %s", player.getName());

            switch (type) {
                case "TITLE" -> {
                    com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Showing TITLE");
                    player.showTitle(Title.title(
                        component,
                        Component.empty(),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
                    ));
                }
                case "SUBTITLE" -> {
                    com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Showing SUBTITLE");
                    player.showTitle(Title.title(
                        Component.empty(),
                        component,
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
                    ));
                }
                default -> {
                    com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Sending CHAT message (type: %s)", type);
                    player.sendMessage(component);
                }
            }
            return true;
        }

        return false;
    }
}
