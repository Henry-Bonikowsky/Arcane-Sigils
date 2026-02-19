package com.miracle.arcanesigils.listeners;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.combat.CombatUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Sets configured max durability on armor when first equipped.
 * Skips ItemsAdder items (detected by custom model data).
 * Uses a PDC marker to avoid re-processing.
 */
public class DurabilityListener implements Listener {

    private final CombatUtil combatUtil;
    private final NamespacedKey durabilitySetKey;

    public DurabilityListener(ArmorSetsPlugin plugin, CombatUtil combatUtil) {
        this.combatUtil = combatUtil;
        this.durabilitySetKey = new NamespacedKey(plugin, "durability_set");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!combatUtil.isDurabilityEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Process armor after the click resolves
        player.getServer().getScheduler().runTaskLater(
                ArmorSetsPlugin.getInstance(), () -> processArmor(player), 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!combatUtil.isDurabilityEnabled()) return;

        // Process armor on join (1 tick delay for inventory to load)
        event.getPlayer().getServer().getScheduler().runTaskLater(
                ArmorSetsPlugin.getInstance(), () -> processArmor(event.getPlayer()), 1L);
    }

    private void processArmor(Player player) {
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || piece.getType().isAir()) continue;
            applyDurability(piece);
        }
    }

    private void applyDurability(ItemStack item) {
        if (!(item.getItemMeta() instanceof Damageable damageable)) return;

        // Skip ItemsAdder items (have custom model data)
        if (damageable.hasCustomModelData()) return;

        // Skip if already processed
        PersistentDataContainer pdc = damageable.getPersistentDataContainer();
        if (pdc.has(durabilitySetKey, PersistentDataType.BYTE)) return;

        // Determine material category
        String material = getMaterialCategory(item.getType().name());
        if (material == null) return;

        int configuredMax = combatUtil.getDurability(material);
        if (configuredMax <= 0) return;

        // Get current vanilla max durability
        int vanillaMax = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
        if (vanillaMax <= 0) return;

        // Scale current damage proportionally if max is changing
        int currentDamage = damageable.getDamage();
        if (vanillaMax != configuredMax && currentDamage > 0) {
            int scaledDamage = (int) Math.round((double) currentDamage * configuredMax / vanillaMax);
            damageable.setDamage(Math.min(scaledDamage, configuredMax - 1));
        }

        // Set configured max durability
        damageable.setMaxDamage(configuredMax);

        // Mark as processed
        pdc.set(durabilitySetKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(damageable);
    }

    private String getMaterialCategory(String materialName) {
        if (materialName.contains("LEATHER")) return "LEATHER";
        if (materialName.contains("CHAINMAIL")) return "CHAINMAIL";
        if (materialName.contains("IRON")) return "IRON";
        if (materialName.contains("GOLDEN")) return "GOLD";
        if (materialName.contains("DIAMOND")) return "DIAMOND";
        if (materialName.contains("NETHERITE")) return "NETHERITE";
        return null;
    }
}
