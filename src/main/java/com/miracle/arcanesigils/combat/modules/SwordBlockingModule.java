package com.miracle.arcanesigils.combat.modules;

import com.miracle.arcanesigils.combat.LegacyCombatManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restores 1.8-style sword blocking.
 *
 * Features:
 * - Swords get consumable/food component for blocking
 * - Right-click to block, reduces damage by 50%
 * - Movement slowed while blocking
 * - Shields are disabled
 */
public class SwordBlockingModule extends AbstractCombatModule implements Listener {

    private static final Set<Material> SWORD_MATERIALS = Set.of(
        Material.WOODEN_SWORD,
        Material.STONE_SWORD,
        Material.IRON_SWORD,
        Material.GOLDEN_SWORD,
        Material.DIAMOND_SWORD,
        Material.NETHERITE_SWORD
    );

    private final Map<UUID, Long> blockingPlayers = new ConcurrentHashMap<>();
    private BukkitTask blockingTask;
    private BukkitTask swordCheckTask;

    public SwordBlockingModule(LegacyCombatManager manager) {
        super(manager);
    }

    @Override
    public String getId() {
        return "sword-blocking";
    }

    @Override
    public String getDisplayName() {
        return "Sword Blocking";
    }

    @Override
    public void onEnable() {
        // Apply to all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyToAllSwords(player);
        }

        blockingTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickBlocking, 1L, 1L);
        swordCheckTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyToAllSwords(player);
            }
        }, 100L, 100L);

        plugin.getLogger().info("Sword Blocking: Enabled with Paper data component API");
    }

    @Override
    public void onDisable() {
        if (blockingTask != null) {
            blockingTask.cancel();
            blockingTask = null;
        }
        if (swordCheckTask != null) {
            swordCheckTask.cancel();
            swordCheckTask = null;
        }
        blockingPlayers.clear();
    }

    private void tickBlocking() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean isBlocking = isPlayerBlocking(player);

            if (isBlocking) {
                blockingPlayers.put(uuid, System.currentTimeMillis());
            } else {
                blockingPlayers.remove(uuid);
            }
        }
    }

    private boolean isPlayerBlocking(Player player) {
        if (!player.isHandRaised()) return false;
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        return isSword(mainHand);
    }

    private boolean isSword(ItemStack item) {
        return item != null && SWORD_MATERIALS.contains(item.getType());
    }

    private boolean isShield(ItemStack item) {
        return item != null && item.getType() == Material.SHIELD;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyToAllSwords(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!isEnabled()) return;
        ItemStack newItem = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (newItem != null && isSword(newItem)) {
            applySwordBlocking(newItem);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (current != null && isSword(current)) applySwordBlocking(current);
        if (cursor != null && isSword(cursor)) applySwordBlocking(cursor);

        Bukkit.getScheduler().runTaskLater(plugin, () -> applyToAllSwords(player), 1L);
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        ItemStack item = event.getItem().getItemStack();
        if (isSword(item)) {
            applySwordBlocking(item);
            event.getItem().setItemStack(item);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!isEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (isSword(event.getRecipe().getResult())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyToAllSwords(player), 1L);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!isEnabled()) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyToAllSwords(player), 1L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;

        ItemStack item = event.getItem();
        if (item != null && isSword(item)) {
            applySwordBlocking(item);
        }

        // Block shield usage
        if (item != null && isShield(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (!blockingPlayers.containsKey(player.getUniqueId())) return;
        if (!isPlayerBlocking(player)) return;

        double reduction = config.getBlockDamageReduction() / 100.0;
        event.setDamage(event.getDamage() * (1.0 - reduction));
    }

    private void applyToAllSwords(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isSword(item)) {
                applySwordBlocking(item);
            }
        }
    }

    /**
     * Apply sword blocking consumable component to a sword.
     * Uses Paper's DataComponentTypes API to add blocking animation.
     *
     * Note: First-person block animation requires client 1.21.4+
     * Third-person works on 1.21.2+
     */
    public void applySwordBlocking(ItemStack sword) {
        if (!isSword(sword)) return;

        try {
            // Check if already has consumable with blocking
            if (sword.hasData(DataComponentTypes.CONSUMABLE)) {
                Consumable existing = sword.getData(DataComponentTypes.CONSUMABLE);
                if (existing != null && existing.animation() == ItemUseAnimation.BLOCK) {
                    return; // Already has blocking
                }
            }

            // Build consumable with BLOCK animation (effectively infinite duration)
            Consumable blocking = Consumable.consumable()
                .consumeSeconds(3600f)  // 1 hour - effectively infinite
                .animation(ItemUseAnimation.BLOCK)
                .hasConsumeParticles(false)
                .build();

            sword.setData(DataComponentTypes.CONSUMABLE, blocking);
            plugin.getLogger().info("[SwordBlocking] Applied BLOCK animation to " + sword.getType());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply sword blocking: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void applyToPlayer(Player player) {
        if (!isEnabled()) return;
        applyToAllSwords(player);
    }

    @Override
    public void removeFromPlayer(Player player) {
        blockingPlayers.remove(player.getUniqueId());
    }

    @Override
    public List<ModuleParam> getConfigParams() {
        return List.of(
            ModuleParam.builder("damage-reduction")
                .displayName("Damage Reduction")
                .description("% of damage blocked when holding sword")
                .percentValue(config::getBlockDamageReduction, config::setBlockDamageReduction)
                .range(0, 100)
                .step(5)
                .build(),
            ModuleParam.builder("slowdown")
                .displayName("Block Slowdown")
                .description("Movement speed while blocking (0.2 = 20%)")
                .doubleValue(config::getSlowdownMultiplier, config::setSlowdownMultiplier)
                .range(0, 1)
                .step(0.05)
                .build()
        );
    }
}
