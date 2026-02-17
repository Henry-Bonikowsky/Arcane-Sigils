package com.miracle.arcanesigils.api;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.binds.BindsManager;
import com.miracle.arcanesigils.binds.LastVictimManager;
import com.miracle.arcanesigils.binds.PlayerBindData;
import com.miracle.arcanesigils.binds.TargetGlowManager;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.core.SocketManager;
import com.miracle.arcanesigils.effects.MarkManager;
import com.miracle.arcanesigils.effects.impl.DamageAmplificationEffect;
import com.miracle.arcanesigils.effects.impl.DamageReductionBuffEffect;
import com.miracle.arcanesigils.effects.impl.InvulnerabilityHitsEffect;
import com.miracle.arcanesigils.events.CooldownManager;
import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.variables.SigilVariableManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArcaneSigilsAPIImpl implements ArcaneSigilsAPI {

    private final ArmorSetsPlugin plugin;

    public ArcaneSigilsAPIImpl(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
    }

    private String resolveSigilId(Player player, int bindSlot) {
        BindsManager bindsManager = plugin.getBindsManager();
        if (bindsManager == null) return null;
        PlayerBindData data = bindsManager.getPlayerData(player);
        if (data == null) return null;
        List<String> ids = data.getCurrentBinds().getBind(bindSlot);
        if (ids == null || ids.isEmpty()) return null;
        return ids.get(0);
    }

    private Sigil resolveSigil(Player player, int bindSlot) {
        String sigilId = resolveSigilId(player, bindSlot);
        if (sigilId == null) return null;
        return plugin.getSigilManager().getSigil(sigilId);
    }

    private ItemStack findEquippedItem(Player player, String sigilId) {
        SocketManager sm = plugin.getSocketManager();
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack item : armor) {
            if (item == null || item.getType().isAir()) continue;
            for (String entry : sm.getSocketedSigilData(item)) {
                String id = entry.contains(":") ? entry.split(":")[0] : entry;
                if (id.equalsIgnoreCase(sigilId)) return item;
            }
        }
        for (ItemStack item : new ItemStack[]{
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand()}) {
            if (item == null || item.getType().isAir()) continue;
            for (String entry : sm.getSocketedSigilData(item)) {
                String id = entry.contains(":") ? entry.split(":")[0] : entry;
                if (id.equalsIgnoreCase(sigilId)) return item;
            }
        }
        return null;
    }

    private int getEquippedTier(Player player, String sigilId) {
        ItemStack item = findEquippedItem(player, sigilId);
        if (item == null) return 0;
        for (String entry : plugin.getSocketManager().getSocketedSigilData(item)) {
            String[] parts = entry.split(":");
            if (parts[0].equalsIgnoreCase(sigilId) && parts.length > 1) {
                try { return Integer.parseInt(parts[1]); }
                catch (NumberFormatException e) { return 1; }
            }
        }
        return 1;
    }

    private String getCooldownKey(Sigil sigil) {
        FlowConfig abilityFlow = sigil.getAbilityFlow();
        if (abilityFlow == null) return null;
        String flowId = abilityFlow.getGraph() != null ? abilityFlow.getGraph().getId() : "unknown";
        return "sigil_" + sigil.getId() + "_" + flowId;
    }

    private String determineActivationType(Sigil sigil) {
        if (sigil.hasAbilityFlow()) return "ability";
        List<FlowConfig> flows = sigil.getFlows();
        if (flows == null || flows.isEmpty()) return "passive";
        for (FlowConfig flow : flows) {
            if (flow.isSignal()) {
                String trigger = flow.getTrigger();
                if (trigger == null) continue;
                trigger = trigger.toUpperCase();
                if (trigger.contains("ATTACK") || trigger.equals("HIT")) return "auto_attack";
                if (trigger.contains("DEFEND") || trigger.contains("DEFENSE")) return "auto_defense";
                if (trigger.equals("PASSIVE") || trigger.equals("EFFECT_STATIC")) return "passive";
            }
        }
        return "passive";
    }

    private double getMaxCooldownSeconds(Sigil sigil, int tier) {
        FlowConfig abilityFlow = sigil.getAbilityFlow();
        if (abilityFlow == null) return 0;
        double cooldown = abilityFlow.getCooldown();
        if (sigil.getTierScalingConfig() != null && sigil.getTierScalingConfig().hasParam("cooldown")) {
            cooldown = sigil.getTierScalingConfig().getParamValue("cooldown", tier);
        }
        return cooldown;
    }

    @Override
    public List<SigilInfo> getEquippedSigils(Player player) {
        if (player == null) return Collections.emptyList();
        SocketManager sm = plugin.getSocketManager();
        List<SigilInfo> result = new ArrayList<>();

        ItemStack[] toCheck = new ItemStack[]{
            player.getInventory().getHelmet(),
            player.getInventory().getChestplate(),
            player.getInventory().getLeggings(),
            player.getInventory().getBoots(),
            player.getInventory().getItemInMainHand(),
            player.getInventory().getItemInOffHand()
        };

        for (ItemStack item : toCheck) {
            if (item == null || item.getType().isAir()) continue;
            for (String entry : sm.getSocketedSigilData(item)) {
                String[] parts = entry.split(":");
                String sigilId = parts[0];
                int tier = 1;
                if (parts.length > 1) {
                    try { tier = Integer.parseInt(parts[1]); }
                    catch (NumberFormatException ignored) {}
                }
                Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
                if (sigil == null) continue;
                result.add(new SigilInfo(
                    sigilId,
                    sigil.getName(),
                    tier,
                    sigil.getSlot() != null ? sigil.getSlot() : "UNKNOWN",
                    determineActivationType(sigil)
                ));
            }
        }
        return result;
    }

    @Override
    public boolean isSigilReady(Player player, int bindSlot) {
        Sigil sigil = resolveSigil(player, bindSlot);
        if (sigil == null) return false;
        if (!sigil.hasAbilityFlow()) return false;
        if (findEquippedItem(player, sigil.getId()) == null) return false;
        String cooldownKey = getCooldownKey(sigil);
        if (cooldownKey == null) return false;
        return !plugin.getCooldownManager().isOnCooldown(player, cooldownKey);
    }

    @Override
    public double getCooldownProgress(Player player, int bindSlot) {
        Sigil sigil = resolveSigil(player, bindSlot);
        if (sigil == null) return 0.0;
        String cooldownKey = getCooldownKey(sigil);
        if (cooldownKey == null) return 0.0;
        CooldownManager cm = plugin.getCooldownManager();
        if (!cm.isOnCooldown(player, cooldownKey)) return 0.0;
        int tier = getEquippedTier(player, sigil.getId());
        double max = getMaxCooldownSeconds(sigil, tier);
        if (max <= 0) return 0.0;
        double remaining = cm.getRemainingCooldown(player, cooldownKey);
        return Math.min(1.0, remaining / max);
    }

    @Override
    public double getCooldownRemaining(Player player, int bindSlot) {
        Sigil sigil = resolveSigil(player, bindSlot);
        if (sigil == null) return 0.0;
        String cooldownKey = getCooldownKey(sigil);
        if (cooldownKey == null) return 0.0;
        return plugin.getCooldownManager().getRemainingCooldown(player, cooldownKey);
    }

    @Override
    public double getMaxCooldown(Player player, int bindSlot) {
        Sigil sigil = resolveSigil(player, bindSlot);
        if (sigil == null) return 0.0;
        int tier = getEquippedTier(player, sigil.getId());
        return getMaxCooldownSeconds(sigil, tier);
    }

    @Override
    public int getTier(Player player, int bindSlot) {
        String sigilId = resolveSigilId(player, bindSlot);
        if (sigilId == null) return 0;
        return getEquippedTier(player, sigilId);
    }

    @Override
    public String getSigilType(Player player, int bindSlot) {
        return resolveSigilId(player, bindSlot);
    }

    @Override
    public String getActivationType(Player player, int bindSlot) {
        Sigil sigil = resolveSigil(player, bindSlot);
        if (sigil == null) return null;
        return determineActivationType(sigil);
    }

    @Override
    public boolean activateAbility(Player player, int bindSlot) {
        return plugin.getBindsListener().activateBindSlot(player, bindSlot);
    }

    @Override
    public double getDamageAmplifier(Player player) {
        if (player == null) return 1.0;
        double amp = DamageAmplificationEffect.getDamageAmplification(player.getUniqueId());
        return 1.0 + (amp / 100.0);
    }

    @Override
    public double getDamageReduction(Player player) {
        if (player == null) return 1.0;
        double red = DamageReductionBuffEffect.getDamageReduction(player.getUniqueId());
        return 1.0 - (red / 100.0);
    }

    @Override
    public int getKingsBraceCharges(Player player) {
        if (player == null) return 0;
        SigilVariableManager svm = plugin.getSigilVariableManager();
        Object val = svm.getSigilVariable(player, "kings_brace", "CHESTPLATE", "charge");
        if (val instanceof Number num) return num.intValue();
        return 0;
    }

    @Override
    public int getInvulnHits(Player player) {
        if (player == null) return 0;
        return InvulnerabilityHitsEffect.getRemainingHits(player.getUniqueId());
    }

    @Override
    public boolean isMarked(Player target, Player attacker) {
        if (target == null || attacker == null) return false;
        return plugin.getMarkManager().isMarkedBy(target, attacker);
    }

    @Override
    public boolean hasMark(LivingEntity entity, String markName) {
        if (entity == null || markName == null) return false;
        return plugin.getMarkManager().hasMark(entity, markName);
    }

    @Override
    public List<MarkInfo> getActiveMarks(LivingEntity entity) {
        if (entity == null) return Collections.emptyList();
        return plugin.getMarkManager().getActiveMarkInfo(entity);
    }

    @Override
    public double getMarkDamageMultiplier(LivingEntity entity) {
        if (entity == null) return 1.0;
        return plugin.getMarkManager().getDamageMultiplier(entity);
    }

    @Override
    public LivingEntity getSelectedTarget(Player player) {
        if (player == null) return null;
        TargetGlowManager tgm = plugin.getTargetGlowManager();
        if (tgm == null) return null;
        return tgm.getTarget(player);
    }

    @Override
    public LivingEntity getLastVictim(Player player) {
        if (player == null) return null;
        LastVictimManager lvm = plugin.getLastVictimManager();
        if (lvm == null) return null;
        return lvm.getLastVictim(player);
    }
}
