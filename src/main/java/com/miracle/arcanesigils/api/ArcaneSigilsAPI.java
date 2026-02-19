package com.miracle.arcanesigils.api;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.List;

/**
 * Public API for querying and controlling Arcane Sigils state.
 * Obtain via ArcaneSigils.getAPI().
 */
public interface ArcaneSigilsAPI {

    // --- Equipped Sigils ---
    List<SigilInfo> getEquippedSigils(Player player);

    // --- Bind Slot Queries (uses player's active bind system) ---
    boolean isSigilReady(Player player, int bindSlot);
    double getCooldownProgress(Player player, int bindSlot);
    double getCooldownRemaining(Player player, int bindSlot);
    double getMaxCooldown(Player player, int bindSlot);
    int getTier(Player player, int bindSlot);
    String getSigilType(Player player, int bindSlot);
    String getActivationType(Player player, int bindSlot);

    // --- Ability Activation ---
    boolean activateAbility(Player player, int bindSlot);

    // --- Combat State ---
    double getDamageAmplifier(Player player);
    double getDamageReduction(Player player);

    // --- Specific Sigil State ---
    int getKingsBraceCharges(Player player);
    int getInvulnHits(Player player);

    // --- Marks ---
    boolean isMarked(Player target, Player attacker);
    boolean hasMark(LivingEntity entity, String markName);
    List<MarkInfo> getActiveMarks(LivingEntity entity);
    double getMarkDamageMultiplier(LivingEntity entity);

    // --- Targets ---
    LivingEntity getSelectedTarget(Player player);
    LivingEntity getLastVictim(Player player);

    // --- Bot Sigil Registration ---
    void registerBotSigils(Player player, List<String> sigilIds);
    void unregisterBotSigils(Player player);
}
