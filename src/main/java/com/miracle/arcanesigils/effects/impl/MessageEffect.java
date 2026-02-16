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
        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] ========== EXECUTE START ==========");
        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] context=%s", context != null ? "EXISTS" : "NULL");

        EffectParams params = context.getParams();
        if (params == null) {
            com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Params are NULL - ABORTING");
            return false;
        }

        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Params toString: %s", params);

        String message = params.getString("message", "");
        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Retrieved message from params: '%s'", message);

        if (message.isEmpty()) {
            com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Message is EMPTY - ABORTING");
            return false;
        }

        String type = params.getString("type", "CHAT").toUpperCase();
        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Message type: %s", type);

        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Calling getTarget(context)...");
        LivingEntity target = getTarget(context);
        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] getTarget() returned: %s (class: %s)",
            target, target != null ? target.getClass().getSimpleName() : "null");
        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] context.getPlayer(): %s", context.getPlayer() != null ? context.getPlayer().getName() : "NULL");
        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] params.getTarget(): %s", params.getTarget());

        if (target instanceof Player player) {
            com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Target IS a Player: %s", player.getName());
            com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Parsing message component from: '%s'", message);
            Component component = TextUtil.parseComponent(message);
            com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Component created successfully");

            switch (type) {
                case "TITLE" -> {
                    com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Showing TITLE to %s", player.getName());
                    player.showTitle(Title.title(
                        component,
                        Component.empty(),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
                    ));
                    com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] TITLE sent successfully");
                }
                case "SUBTITLE" -> {
                    com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Showing SUBTITLE to %s", player.getName());
                    player.showTitle(Title.title(
                        Component.empty(),
                        component,
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
                    ));
                    com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] SUBTITLE sent successfully");
                }
                default -> {
                    com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Sending CHAT message to %s (type: %s)", player.getName(), type);
                    player.sendMessage(component);
                    com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] CHAT message sent successfully");
                }
            }
            com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] ========== EXECUTE SUCCESS - RETURNING TRUE ==========");
            return true;
        }

        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] Target is NOT a Player - target class: %s",
            target != null ? target.getClass().getSimpleName() : "NULL");
        com.miracle.arcanesigils.utils.LogHelper.debug("[MessageEffect] ========== EXECUTE FAILED - RETURNING FALSE ==========");
        return false;
    }
}
