package com.miracle.arcanesigils.tier;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Manages sigil XP progression and tier advancement.
 * XP is stored on sigil items (in armor PDC) and advances tiers automatically.
 */
public class TierProgressionManager {

    private final ArmorSetsPlugin plugin;
    private final NamespacedKey SIGIL_XP_KEY;

    public TierProgressionManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.SIGIL_XP_KEY = new NamespacedKey(plugin, "sigil_xp");
    }

    /**
     * Award XP to a sigil socketed in armor.
     * NOTE: Auto tier-up disabled - players must use Enchanter GUI for manual upgrades.
     *
     * @param player The player wearing the armor
     * @param armorItem The armor item containing the sigil
     * @param sigilId The sigil ID that activated
     * @param currentTier Current tier of the sigil
     * @return always false (no auto tier-up)
     */
    public boolean awardXP(Player player, ItemStack armorItem, String sigilId, int currentTier) {
        if (armorItem == null || !armorItem.hasItemMeta()) {
            return false;
        }

        // Get the sigil to check XP config
        Sigil baseSigil = plugin.getSigilManager().getSigil(sigilId);
        if (baseSigil == null) {
            return false;
        }

        // Check if XP is enabled for this sigil
        TierXPConfig xpConfig = baseSigil.getTierXPConfig();
        if (xpConfig == null || !xpConfig.isEnabled()) {
            return false;
        }

        // Check if already at max tier
        if (currentTier >= baseSigil.getMaxTier()) {
            return false;
        }

        // Get current XP for this sigil on this item
        int currentXP = getSigilXP(armorItem, sigilId);

        // Add XP
        int xpGain = xpConfig.getGainPerActivation();
        int newXP = currentXP + xpGain;

        // DISABLED: Auto tier-up
        // Players must manually upgrade via Enchanter GUI using vanilla XP + materials
        /*
        // Check for tier up
        int xpRequired = xpConfig.getXPForTier(currentTier + 1);
        boolean tieredUp = false;

        if (newXP >= xpRequired) {
            // Tier up!
            int newTier = currentTier + 1;
            newXP -= xpRequired; // Carry over excess XP

            // Update the sigil tier on the armor
            updateSigilTier(armorItem, sigilId, newTier);

            // Refresh the armor lore to show new tier
            plugin.getSocketManager().refreshArmorLore(armorItem);

            // Send feedback
            sendTierUpFeedback(player, baseSigil, newTier);

            tieredUp = true;
        }
        */

        // Save new XP (kept for compatibility, but no longer used for auto tier-up)
        setSigilXP(armorItem, sigilId, newXP);

        return false; // No auto tier-up
    }

    /**
     * Get the current XP for a sigil on an armor item.
     */
    public int getSigilXP(ItemStack armorItem, String sigilId) {
        if (armorItem == null || !armorItem.hasItemMeta()) {
            return 0;
        }

        ItemMeta meta = armorItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // XP is stored as "sigilId:xp" format in a compound key
        String xpKey = sigilId + "_xp";
        NamespacedKey key = new NamespacedKey(plugin, xpKey);

        Integer xp = pdc.get(key, PersistentDataType.INTEGER);
        return xp != null ? xp : 0;
    }

    /**
     * Set the XP for a sigil on an armor item.
     */
    public void setSigilXP(ItemStack armorItem, String sigilId, int xp) {
        if (armorItem == null || !armorItem.hasItemMeta()) {
            return;
        }

        ItemMeta meta = armorItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String xpKey = sigilId + "_xp";
        NamespacedKey key = new NamespacedKey(plugin, xpKey);

        pdc.set(key, PersistentDataType.INTEGER, xp);
        armorItem.setItemMeta(meta);
    }

    /**
     * Update the tier of a sigil socketed in armor.
     */
    private void updateSigilTier(ItemStack armorItem, String sigilId, int newTier) {
        // The SocketManager stores sigils as "id:tier" pairs
        // We need to update the stored tier value
        plugin.getSocketManager().updateSocketedSigilTier(armorItem, sigilId, newTier);
    }

    /**
     * Send subtle tier-up feedback to the player.
     */
    private void sendTierUpFeedback(Player player, Sigil sigil, int newTier) {
        String romanTier = toRomanNumeral(newTier);
        String sigilName = sigil.getName();

        // Chat message
        String message = "§a&l\u2191 §f" + sigilName + " §7upgraded to §e" + romanTier;
        player.sendMessage(TextUtil.parseComponent(message));

        // Quiet sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.5f);
    }

    /**
     * Get XP progress info for display.
     */
    public XPProgressInfo getProgressInfo(ItemStack armorItem, String sigilId, int currentTier) {
        Sigil baseSigil = plugin.getSigilManager().getSigil(sigilId);
        if (baseSigil == null) {
            return null;
        }

        TierXPConfig xpConfig = baseSigil.getTierXPConfig();
        if (xpConfig == null || !xpConfig.isEnabled()) {
            return new XPProgressInfo(0, 0, 1.0, true, currentTier >= baseSigil.getMaxTier());
        }

        int currentXP = getSigilXP(armorItem, sigilId);
        int maxTier = baseSigil.getMaxTier();

        if (currentTier >= maxTier) {
            return new XPProgressInfo(currentXP, 0, 1.0, true, true);
        }

        int requiredXP = xpConfig.getXPForTier(currentTier + 1);
        double progress = requiredXP > 0 ? (double) currentXP / requiredXP : 1.0;

        return new XPProgressInfo(currentXP, requiredXP, progress, xpConfig.isEnabled(), false);
    }

    /**
     * Convert tier number to roman numeral.
     */
    private String toRomanNumeral(int tier) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
                "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
        if (tier >= 1 && tier <= 20) {
            return numerals[tier - 1];
        }
        return String.valueOf(tier);
    }

    /**
     * Data class for XP progress information.
     */
    public static class XPProgressInfo {
        public final int currentXP;
        public final int requiredXP;
        public final double progress;
        public final boolean xpEnabled;
        public final boolean maxTierReached;

        public XPProgressInfo(int currentXP, int requiredXP, double progress,
                              boolean xpEnabled, boolean maxTierReached) {
            this.currentXP = currentXP;
            this.requiredXP = requiredXP;
            this.progress = Math.min(1.0, Math.max(0.0, progress));
            this.xpEnabled = xpEnabled;
            this.maxTierReached = maxTierReached;
        }

        /**
         * Get a progress bar string for display.
         */
        public String getProgressBar(int length) {
            int filled = (int) (progress * length);
            StringBuilder bar = new StringBuilder("§8[");
            for (int i = 0; i < length; i++) {
                if (i < filled) {
                    bar.append("§a|");
                } else {
                    bar.append("§7|");
                }
            }
            bar.append("§8]");
            return bar.toString();
        }
    }
}
