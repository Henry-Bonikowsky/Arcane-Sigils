package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;

/**
 * Rebalances tool damage to 1.8 levels.
 *
 * In 1.9+, axes do significantly more damage than swords.
 * In 1.8, swords were the primary weapon and axes did less damage.
 *
 * 1.8 damage values:
 * - Diamond Sword: 7 damage
 * - Diamond Axe: 6 damage
 * - Iron Sword: 6 damage
 * - Iron Axe: 5 damage
 *
 * 1.9+ damage values (what we're reverting):
 * - Diamond Sword: 7 damage
 * - Diamond Axe: 9 damage (too high!)
 * - Iron Sword: 6 damage
 * - Iron Axe: 9 damage (too high!)
 */
public class ToolDamageModule extends AbstractCombatModule implements Listener {

    private static final NamespacedKey DAMAGE_NERF_KEY = new NamespacedKey("arcanesigils", "legacy_damage_nerf");

    // Axes that need damage reduction
    private static final Set<Material> AXES = Set.of(
        Material.WOODEN_AXE,
        Material.STONE_AXE,
        Material.IRON_AXE,
        Material.GOLDEN_AXE,
        Material.DIAMOND_AXE,
        Material.NETHERITE_AXE
    );

    public ToolDamageModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "tool-damage";
    }

    @Override
    public String getDisplayName() {
        return "Tool Damage";
    }

    @Override
    public void onEnable() {
        // Apply to all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyToPlayer(player);
        }
    }

    @Override
    public void onDisable() {
        // Revert for all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeFromPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            applyToPlayer(event.getPlayer());
        }, 5L);
    }

    /**
     * Intercept axe damage and reduce it to 1.8 levels.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!AXES.contains(weapon.getType())) return;

        // Reduce axe damage to 1.8 levels
        // 1.9+ axes do 9 damage, 1.8 axes did about 5-6
        // Reduce by approximately 33%
        double currentDamage = event.getDamage();
        double reducedDamage = currentDamage * 0.67; // ~2/3 of current damage
        event.setDamage(reducedDamage);
    }

    @Override
    public void applyToPlayer(Player player) {
        // Apply damage modifiers to axes in inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && AXES.contains(item.getType())) {
                applyDamageNerf(item);
            }
        }
    }

    @Override
    public void removeFromPlayer(Player player) {
        // Remove damage modifiers from axes
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && AXES.contains(item.getType())) {
                removeDamageNerf(item);
            }
        }
    }

    /**
     * Apply damage nerf attribute modifier to an axe.
     */
    private void applyDamageNerf(ItemStack axe) {
        if (!AXES.contains(axe.getType())) return;

        ItemMeta meta = axe.getItemMeta();
        if (meta == null) return;

        // Remove existing modifier if present
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE,
            new AttributeModifier(DAMAGE_NERF_KEY, -3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));

        // Add damage reduction (-3 damage, roughly brings axes in line with 1.8)
        AttributeModifier nerf = new AttributeModifier(
            DAMAGE_NERF_KEY,
            -3.0, // Reduce damage by 3
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlotGroup.MAINHAND
        );

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, nerf);
        axe.setItemMeta(meta);
    }

    /**
     * Remove damage nerf from an axe.
     */
    private void removeDamageNerf(ItemStack axe) {
        if (!AXES.contains(axe.getType())) return;

        ItemMeta meta = axe.getItemMeta();
        if (meta == null) return;

        // Remove our modifier
        try {
            meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(DAMAGE_NERF_KEY, -3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            axe.setItemMeta(meta);
        } catch (Exception e) {
            // Silent fail
        }
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("axe-damage-reduction")
                .displayName("Axe Damage Nerf")
                .description("Damage reduction for axes")
                .doubleValue(config::getAxeDamageReduction, config::setAxeDamageReduction)
                .range(0, 6)
                .step(0.5)
                .build()
        );
    }
}
