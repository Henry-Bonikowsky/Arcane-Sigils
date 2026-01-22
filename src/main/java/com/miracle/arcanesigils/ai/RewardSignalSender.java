package com.miracle.arcanesigils.ai;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * Sends reward signals to players via chat for AI training.
 * Feedback mechanism for AI training signals.
 */
public class RewardSignalSender {
    
    /**
     * Send a reward signal to a player via chat.
     */
    public void sendSignal(Player player, RewardSignal signal) {
        String formatted = signal.format();
        sendMessage(player, formatted);
    }

    /**
     * Send a combo signal to a player.
     * Format: [SIGIL_COMBO:bind1+bind2:150]
     */
    public void sendComboSignal(Player player, String comboString, double totalDamage) {
        String message = String.format("§7[§aSIGIL_COMBO§7:§e%s§7:§c%.1f§7]",
            comboString, totalDamage);
        sendMessage(player, message);
    }

    /**
     * Send chat message to player.
     */
    private void sendMessage(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.CHAT, new TextComponent(message));
    }
}
