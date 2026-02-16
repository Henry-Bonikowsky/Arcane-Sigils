package com.miracle.arcanesigils.listeners;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.core.SigilManager;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

/**
 * Reduces damage from Poison and Wither effects based on Ancient Crown tier.
 * Complements the ReducePotionPotencyEffect which handles attribute-based effects.
 *
 * This listener intercepts damage-over-time effects and reduces them by the
 * reduction percentage specified in the Ancient Crown's immunity_chance parameter.
 */
public class PotionDamageReductionListener implements Listener {

    private final ArmorSetsPlugin plugin;
    private final SigilManager sigilManager;

    public PotionDamageReductionListener(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.sigilManager = plugin.getSigilManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionDamage(EntityDamageEvent event) {
        // Only apply to players
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        DamageCause cause = event.getCause();

        // Log ALL damage types for debugging
        LogHelper.info(String.format("[PotionDamageListener] Player %s took %s damage: %.2f",
            player.getName(), cause, event.getDamage()));

        // Only apply to Poison and Wither damage
        if (cause != DamageCause.POISON && cause != DamageCause.WITHER) {
            LogHelper.info("[PotionDamageListener] Not poison/wither, skipping");
            return;
        }

        LogHelper.debug(String.format("[PotionDamage] Player %s taking %s damage: %.2f",
            player.getName(), cause, event.getDamage()));

        // Get Ancient Crown reduction percentage
        double reductionPercent = getAncientCrownReduction(player);

        LogHelper.debug(String.format("[PotionDamage] Ancient Crown reduction: %.1f%%", reductionPercent));

        if (reductionPercent <= 0) {
            LogHelper.debug("[PotionDamage] No Ancient Crown found or reduction is 0%, allowing full damage");
            return;
        }

        // Calculate reduced damage
        double originalDamage = event.getDamage();
        double reducedDamage = originalDamage * (1.0 - reductionPercent / 100.0);

        // Apply reduced damage
        event.setDamage(reducedDamage);

        LogHelper.info(String.format("[AncientCrown] Reduced %s damage: %.2f → %.2f (-%.0f%%)",
            cause, originalDamage, reducedDamage, reductionPercent));
        LogHelper.debug(String.format("[PotionDamage] Reduced %s damage: %.2f → %.2f (-%.0f%%)",
            cause, originalDamage, reducedDamage, reductionPercent));
    }

    /**
     * Gets the Ancient Crown reduction percentage for a player.
     *
     * @param player The player to check
     * @return Reduction percentage (0-100), or 0 if no Ancient Crown equipped
     */
    private double getAncientCrownReduction(Player player) {
        // Check helmet slot for Ancient Crown (exclusive helmet sigil)
        ItemStack helmet = player.getEquipment().getHelmet();
        if (helmet == null) {
            LogHelper.debug("[PotionDamage] No helmet equipped");
            return 0;
        }

        LogHelper.debug(String.format("[PotionDamage] Helmet: %s", helmet.getType()));

        // Get sigil from helmet
        Sigil sigil = sigilManager.getSigilFromItem(helmet);
        if (sigil == null) {
            LogHelper.debug("[PotionDamage] Helmet has no sigil");
            return 0;
        }

        LogHelper.debug(String.format("[PotionDamage] Sigil found: %s", sigil.getId()));

        // Check if this is the Ancient Crown sigil
        if (!sigil.getId().equals("ancient_crown")) {
            LogHelper.debug(String.format("[PotionDamage] Sigil is not ancient_crown (found: %s)", sigil.getId()));
            return 0;
        }

        // Get tier from item
        int tier = sigilManager.getSigilTierFromItem(helmet);
        LogHelper.debug(String.format("[PotionDamage] Ancient Crown tier: %d", tier));

        if (tier <= 0) {
            LogHelper.debug("[PotionDamage] Invalid tier (<= 0)");
            return 0;
        }

        // Get immunity_chance parameter for this tier
        // Tiers: T1=20%, T2=40%, T3=60%, T4=80%, T5=100%
        if (sigil.getTierScalingConfig() == null) {
            LogHelper.debug("[PotionDamage] No tier scaling config");
            return 0;
        }

        Object immunityChance = sigil.getTierScalingConfig().getParamValue("immunity_chance", tier);
        LogHelper.debug(String.format("[PotionDamage] immunity_chance value: %s", immunityChance));

        if (immunityChance == null) {
            LogHelper.debug("[PotionDamage] immunity_chance is null");
            return 0;
        }

        // Parse immunity chance (could be Integer or Double from YAML)
        if (immunityChance instanceof Number) {
            double value = ((Number) immunityChance).doubleValue();
            LogHelper.debug(String.format("[PotionDamage] Returning immunity: %.1f%%", value));
            return value;
        }

        LogHelper.debug(String.format("[PotionDamage] immunity_chance is not a number (type: %s)",
            immunityChance.getClass().getSimpleName()));
        return 0;
    }
}
