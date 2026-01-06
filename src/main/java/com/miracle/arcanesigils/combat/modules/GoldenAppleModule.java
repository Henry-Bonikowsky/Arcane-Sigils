package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Restores 1.8 golden apple and potion consumption speed.
 *
 * In 1.8: Golden apples ate in ~1 second (20 ticks)
 * In 1.9+: Golden apples eat in 1.6 seconds (32 ticks)
 *
 * Also applies to potions for faster drinking in PvP.
 *
 * Uses Paper's DataComponentTypes API (1.21.2+) to modify consume time.
 */
public class GoldenAppleModule extends AbstractCombatModule implements Listener {

    // 1.8 style fast consumption (1.0 second instead of 1.6)
    private static final float FAST_EAT_SECONDS = 1.0f;

    public GoldenAppleModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "golden-apple";
    }

    @Override
    public String getDisplayName() {
        return "Golden Apples";
    }

    @Override
    public void onEnable() {
        // Apply to all online players' inventories
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyToPlayer(player);
        }
    }

    @Override
    public void onDisable() {
        // Nothing to clean up
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;
        // Delay slightly to ensure inventory is loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyToPlayer(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        ItemStack item = event.getItem().getItemStack();
        if (isFastConsumable(item)) {
            applyFastEating(item);
            event.getItem().setItemStack(item);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (current != null && isFastConsumable(current)) {
            applyFastEating(current);
        }
        if (cursor != null && isFastConsumable(cursor)) {
            applyFastEating(cursor);
        }
    }

    /**
     * Apply fast eating when player right-clicks with golden apple.
     * This catches cases where the item wasn't modified yet.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;
        if (!event.getAction().isRightClick()) return;

        ItemStack item = event.getItem();
        if (item != null && isFastConsumable(item)) {
            applyFastEating(item);
        }
    }

    /**
     * Check if this item should have fast consumption applied.
     * Includes golden apples and potions.
     */
    private boolean isFastConsumable(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.GOLDEN_APPLE
            || type == Material.ENCHANTED_GOLDEN_APPLE
            || type == Material.POTION
            || type == Material.SPLASH_POTION
            || type == Material.LINGERING_POTION;
    }


    @Override
    public void applyToPlayer(Player player) {
        if (!isEnabled()) return;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isFastConsumable(item)) {
                applyFastEating(item);
            }
        }
    }

    @Override
    public void removeFromPlayer(Player player) {
        // No cleanup needed - items will have modified consume time but that's fine
    }

    /**
     * Apply fast consumption to golden apples and potions using Paper's Data Component API.
     */
    private void applyFastEating(ItemStack item) {
        if (!isFastConsumable(item)) return;

        try {
            // Get the existing consumable component
            Consumable existing = item.getData(DataComponentTypes.CONSUMABLE);
            if (existing == null) return;

            float eatTime = (float) config.getEatTimeSeconds();

            // Check if already modified (avoid repeated modifications)
            if (Math.abs(existing.consumeSeconds() - eatTime) < 0.01) return;

            // Build new consumable with faster eat time
            Consumable faster = existing.toBuilder()
                .consumeSeconds(eatTime)
                .build();

            // Apply to the item
            item.setData(DataComponentTypes.CONSUMABLE, faster);
        } catch (Exception e) {
            // Log error for debugging
            plugin.getLogger().warning("Failed to apply fast eating to golden apple: " + e.getMessage());
        }
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("eat-time")
                .displayName("Eat Time")
                .description("Time to consume in seconds")
                .secondValue(config::getEatTimeSeconds, config::setEatTimeSeconds)
                .range(0.5, 2.5)
                .step(0.1)
                .build()
        );
    }
}
