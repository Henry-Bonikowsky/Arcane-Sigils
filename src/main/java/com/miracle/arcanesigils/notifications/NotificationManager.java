package com.miracle.arcanesigils.notifications;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class NotificationManager {
    private final ArmorSetsPlugin plugin;

    public NotificationManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Send ability proc notification.
     * Format: "Your Pharaoh's Curse V stunned PlayerName for 2.5s"
     */
    public void sendAbilityProc(Player attacker, String abilityName, int tier, String effect, String target, double duration) {
        if (!isEnabled(NotificationType.ABILITY_PROC)) return;

        String roman = getRomanNumeral(tier);
        String durationStr = formatDuration(duration);

        String message = String.format("§a%s %s §f%s §e%s §ffor §b%s",
            abilityName, roman, effect, target, durationStr);
        attacker.sendMessage(message);
    }

    /**
     * Send ability proc to victim.
     * Format: "PlayerName's Pharaoh's Curse V stunned you for 2.5s"
     */
    public void sendAbilityProcVictim(Player victim, String attackerName, String abilityName, int tier, String effect, double duration) {
        if (!isEnabled(NotificationType.ABILITY_PROC)) return;

        String roman = getRomanNumeral(tier);
        String durationStr = formatDuration(duration);

        String message = String.format("§c%s's %s %s §f%s §fyou for §b%s",
            attackerName, abilityName, roman, effect, durationStr);
        victim.sendMessage(message);
    }

    /**
     * Send multi-target ability proc.
     * Format: "Your Sandstorm hit 3 enemies"
     */
    public void sendAbilityProcMulti(Player attacker, String abilityName, int tier, String effect, int count) {
        if (!isEnabled(NotificationType.ABILITY_PROC)) return;

        String roman = getRomanNumeral(tier);
        String message = String.format("§a%s %s §f%s §e%d enemies",
            abilityName, roman, effect, count);
        attacker.sendMessage(message);
    }

    /**
     * Send cooldown active message.
     * Format: "§7[Cooldown] §cRoyal Bolster §f- §e15s remaining"
     */
    public void sendCooldownActive(Player player, String abilityName, double remainingSeconds) {
        if (!isEnabled(NotificationType.COOLDOWN)) return;

        String timeStr = formatDuration(remainingSeconds);
        String message = String.format("§7[Cooldown] §c%s §f- §e%s remaining",
            abilityName, timeStr);
        player.sendMessage(message);
    }

    /**
     * Send cooldown ready message.
     * Format: "§a[Ready] §fRoyal Bolster §7is ready to use!"
     */
    public void sendCooldownReady(Player player, String abilityName) {
        if (!isEnabled(NotificationType.COOLDOWN)) return;

        String message = String.format("§a[Ready] §f%s §7is ready to use!", abilityName);
        player.sendMessage(message);

        // Play sound
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
    }

    /**
     * Send tier up message.
     * Format: "§a§l↑ §fSigilName §7upgraded to §eV"
     */
    public void sendTierUp(Player player, String sigilName, int newTier) {
        if (!isEnabled(NotificationType.TIER_UP)) return;

        String roman = getRomanNumeral(newTier);
        String message = String.format("§a§l↑ §f%s §7upgraded to §e%s", sigilName, roman);
        player.sendMessage(message);
    }

    /**
     * Send set bonus proc message.
     * Format: "§6[Ancient Set IV] §aactivated §7- Resistance II for 4s"
     */
    public void sendSetBonusProc(Player player, String setName, int tier, String effect, double duration) {
        if (!isEnabled(NotificationType.SET_BONUS)) return;

        String roman = getRomanNumeral(tier);
        String durationStr = formatDuration(duration);
        String message = String.format("§6[%s %s] §aactivated §7- %s for %s",
            setName, roman, effect, durationStr);
        player.sendMessage(message);
    }

    /**
     * Format duration: 2s, 2.5s, 15s
     */
    private String formatDuration(double seconds) {
        if (seconds == (int) seconds) {
            return (int) seconds + "s";
        } else {
            return String.format("%.1fs", seconds);
        }
    }

    private boolean isEnabled(NotificationType type) {
        // Check config.yml notifications section
        return plugin.getConfig().getBoolean("notifications." + type.getConfigKey(), true);
    }

    private String getRomanNumeral(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(tier);
        };
    }
}
