package com.miracle.arcanesigils.events;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.flow.FlowExecutor;
import com.miracle.arcanesigils.flow.FlowGraph;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles all signal events for sigils.
 */
public class SignalHandler implements Listener {

    private final ArmorSetsPlugin plugin;
    private final ConditionManager conditionManager;
    // Track applied effects per player to remove them when armor is unequipped
    private final Map<UUID, Set<PotionEffectType>> appliedEffects = new HashMap<>();
    // Track previous armor state to detect changes
    private final Map<UUID, ItemStack[]> previousArmor = new HashMap<>();
    // Track last equipped set to prevent duplicate unequipped messages
    private final Map<UUID, String> lastEquippedSet = new HashMap<>();

    public SignalHandler(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.conditionManager = new ConditionManager();
        startStaticEffectTask();
        startArmorCheckTask();
        startTickSignalTask();
    }

    public void trackAppliedEffect(Player player, PotionEffectType type) {
        appliedEffects.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(type);
    }

    /**
     * Start task to apply static/passive effects periodically.
     */
    private void startStaticEffectTask() {
        int interval = plugin.getConfigManager().getMainConfig()
                .getInt("settings.effect-check-interval", 20);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    processStaticEffects(player);
                }
            }
        }.runTaskTimer(plugin, 20L, interval);
    }

    /**
     * Start task to check for armor changes and remove effects when armor is unequipped.
     */
    private void startArmorCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    checkArmorChange(player);
                }
            }
        }.runTaskTimer(plugin, 10L, 5L);
    }

    private void checkArmorChange(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack[] current = player.getInventory().getArmorContents();
        ItemStack[] previous = previousArmor.get(uuid);

        if (previous == null) {
            previousArmor.put(uuid, cloneArmor(current));
            return;
        }

        java.util.List<String> removedSigilIds = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ItemStack prev = previous[i];
            ItemStack curr = current[i];

            boolean prevHasItem = prev != null && !prev.getType().isAir();
            boolean currHasItem = curr != null && !curr.getType().isAir();

            // Check if armor was removed (had armor before, now empty or different)
            if (prevHasItem) {
                boolean armorChanged = !currHasItem || !isSameArmorPiece(prev, curr);
                if (armorChanged) {
                    // Armor piece was removed or changed - get all sigils from old piece
                    java.util.List<com.miracle.arcanesigils.core.Sigil> prevSigils = plugin.getSocketManager().getSocketedSigils(prev);
                    java.util.List<com.miracle.arcanesigils.core.Sigil> currSigils = currHasItem ?
                        plugin.getSocketManager().getSocketedSigils(curr) : java.util.Collections.emptyList();

                    // Find sigils that were on old armor but not on new
                    for (com.miracle.arcanesigils.core.Sigil prevSigil : prevSigils) {
                        boolean stillEquipped = currSigils.stream()
                            .anyMatch(s -> s.getId().equals(prevSigil.getId()));
                        if (!stillEquipped) {
                            removedSigilIds.add(prevSigil.getId());
                        }
                    }
                }
            }
        }

        if (!removedSigilIds.isEmpty()) {
            removeAppliedEffects(player);

            // Get all sigils still equipped on remaining armor
            java.util.Set<String> stillEquippedSigils = new java.util.HashSet<>();
            for (ItemStack armor : current) {
                if (armor != null && !armor.getType().isAir()) {
                    for (com.miracle.arcanesigils.core.Sigil sigil : plugin.getSocketManager().getSocketedSigils(armor)) {
                        stillEquippedSigils.add(sigil.getId());
                    }
                }
            }

            // Only remove modifiers for sigils that are NO LONGER on ANY armor piece
            for (String sigilId : removedSigilIds) {
                if (!stillEquippedSigils.contains(sigilId)) {
                    removeSigilAttributeModifiers(player, sigilId);
                }
            }
        }

        previousArmor.put(uuid, cloneArmor(current));
    }

    private ItemStack[] cloneArmor(ItemStack[] armor) {
        ItemStack[] cloned = new ItemStack[armor.length];
        for (int i = 0; i < armor.length; i++) {
            cloned[i] = armor[i] != null ? armor[i].clone() : null;
        }
        return cloned;
    }

    /**
     * Compares two armor pieces ignoring durability changes.
     * Returns true if they're the same armor piece (same type and similar).
     */
    private boolean isSameArmorPiece(ItemStack prev, ItemStack curr) {
        // Different types = different pieces
        if (prev.getType() != curr.getType()) {
            return false;
        }

        // Same type - compare using similar (ignores durability)
        return prev.isSimilar(curr);
    }

    private void removeAppliedEffects(Player player) {
        Set<PotionEffectType> effects = appliedEffects.remove(player.getUniqueId());
        if (effects != null) {
            for (PotionEffectType type : effects) {
                player.removePotionEffect(type);
            }
        }
    }

    /**
     * Remove attribute modifiers for a specific sigil from a player.
     * Called when armor with that sigil is unequipped.
     *
     * Modifier keys follow the pattern: arcane_sigils_persist_<sigilId>_<attributeName>
     */
    private void removeSigilAttributeModifiers(Player player, String sigilId) {
        String sigilPrefix = "arcane_sigils_persist_" + sigilId.toLowerCase();

        // Clean up max health modifiers for this sigil
        org.bukkit.attribute.AttributeInstance maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            org.bukkit.attribute.AttributeModifier toRemove = null;
            for (org.bukkit.attribute.AttributeModifier modifier : maxHealth.getModifiers()) {
                String keyName = modifier.getKey().getKey();
                if (keyName.startsWith(sigilPrefix)) {
                    toRemove = modifier;
                    break;
                }
            }
            if (toRemove != null) {
                maxHealth.removeModifier(toRemove);
                // Clamp health if needed
                if (player.getHealth() > maxHealth.getValue()) {
                    player.setHealth(maxHealth.getValue());
                }
            }
        }

        // Clean up other common attributes for this sigil
        cleanupAttributeForSigil(player, org.bukkit.attribute.Attribute.ATTACK_DAMAGE, sigilPrefix);
        cleanupAttributeForSigil(player, org.bukkit.attribute.Attribute.MOVEMENT_SPEED, sigilPrefix);
        cleanupAttributeForSigil(player, org.bukkit.attribute.Attribute.ARMOR, sigilPrefix);
        cleanupAttributeForSigil(player, org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS, sigilPrefix);
        cleanupAttributeForSigil(player, org.bukkit.attribute.Attribute.ATTACK_SPEED, sigilPrefix);
    }

    /**
     * Clean up modifiers for a specific sigil from a specific attribute.
     */
    private void cleanupAttributeForSigil(Player player, org.bukkit.attribute.Attribute attribute, String sigilPrefix) {
        org.bukkit.attribute.AttributeInstance attrInstance = player.getAttribute(attribute);
        if (attrInstance != null) {
            org.bukkit.attribute.AttributeModifier toRemove = null;
            for (org.bukkit.attribute.AttributeModifier modifier : attrInstance.getModifiers()) {
                String keyName = modifier.getKey().getKey();
                if (keyName.startsWith(sigilPrefix)) {
                    toRemove = modifier;
                    break;
                }
            }
            if (toRemove != null) {
                attrInstance.removeModifier(toRemove);
            }
        }
    }

    public void cleanupPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        removeAppliedEffects(player);
        previousArmor.remove(uuid);
    }

    // ==================== EVENT HANDLERS ====================

    /**
     * LOWEST priority handler to track when event gets cancelled.
     * This runs BEFORE all other handlers and logs initial event state.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageEventLowest(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        // Check if either player has RECENT skin change (within 5 seconds)
        boolean shouldLog = false;
        String reason = "";

        if (plugin.getSkinChangeManager() != null) {
            long attackerMs = plugin.getSkinChangeManager().getTimeSinceSkinChange(attacker);
            long victimMs = plugin.getSkinChangeManager().getTimeSinceSkinChange(victim);

            if (attackerMs >= 0 && attackerMs < 5000) {
                shouldLog = true;
                reason += "Attacker had skin change " + attackerMs + "ms ago. ";
            }
            if (victimMs >= 0 && victimMs < 5000) {
                shouldLog = true;
                reason += "Victim had skin change " + victimMs + "ms ago. ";
            }
        }

        if (plugin.getStunManager() != null) {
            if (plugin.getStunManager().isStunned(attacker)) {
                shouldLog = true;
                reason += "Attacker is stunned. ";
            }
            if (plugin.getStunManager().isStunned(victim)) {
                shouldLog = true;
                reason += "Victim is stunned. ";
            }
        }

        if (!shouldLog) return;

        // Log the INITIAL state at LOWEST priority
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- LOWEST PRIORITY (first handler) ---\n");
        sb.append("Reason: ").append(reason).append("\n");
        sb.append("Event cancelled at entry: ").append(event.isCancelled()).append("\n");
        sb.append("Attacker: ").append(attacker.getName())
          .append(" noDamageTicks=").append(attacker.getNoDamageTicks())
          .append(" invulnerable=").append(attacker.isInvulnerable()).append("\n");
        sb.append("Victim: ").append(victim.getName())
          .append(" noDamageTicks=").append(victim.getNoDamageTicks())
          .append(" invulnerable=").append(victim.isInvulnerable()).append("\n");
        sb.append("Damage: ").append(event.getDamage()).append("\n");

        plugin.getLogger().info(sb.toString());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        
        // Ignore sweep attacks to prevent multiple sigil activations
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }

        double originalDamage = event.getDamage();

        // DEBUG: Log combat info when either party has skin change or stun
        logCombatDebug("ATTACK", player, victim, event);

        EffectContext context = EffectContext.builder(player, SignalType.ATTACK)
                .event(event)
                .victim(victim)
                .damage(originalDamage)
                .build();

        // Process armor set effects
        processArmorEffects(player, SignalType.ATTACK, context);

        // Notify AuraManager of owner attack (triggers pull-on-attack auras)
        if (plugin.getAuraManager() != null) {
            plugin.getAuraManager().onOwnerAttack(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDefend(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        SignalType signalType = event.getCause() == EntityDamageEvent.DamageCause.FALL
                ? SignalType.FALL_DAMAGE
                : SignalType.DEFENSE;

        LivingEntity attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            org.bukkit.entity.Entity damager = byEntity.getDamager();

            if (damager instanceof LivingEntity living) {
                // Direct hit from a mob or player
                attacker = living;
            } else if (damager instanceof org.bukkit.entity.Projectile projectile) {
                // Hit by projectile (arrow, trident, etc.) - get the shooter
                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof LivingEntity livingShooter) {
                    attacker = livingShooter;
                }
            }
        }

        // Apply damage reduction buff BEFORE processing sigil effects
        double originalDamage = event.getDamage();
        double reductionPercent = com.miracle.arcanesigils.effects.impl.DamageReductionBuffEffect.getDamageReduction(player.getUniqueId());
        if (reductionPercent > 0) {
            double reducedDamage = originalDamage * (1 - reductionPercent / 100.0);
            event.setDamage(Math.max(0, reducedDamage));
        }

        // Notify AuraManager of owner hit (triggers pull-on-hit auras)
        if (plugin.getAuraManager() != null) {
            plugin.getAuraManager().onOwnerHit(player.getUniqueId());
        }

        EffectContext context = EffectContext.builder(player, signalType)
                .event(event)
                .attacker(attacker)
                .damage(event.getDamage())
                .build();

        processArmorEffects(player, signalType, context);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) return;

        SignalType signalType = dead instanceof Player
                ? SignalType.KILL_PLAYER
                : SignalType.KILL_MOB;

        EffectContext context = EffectContext.builder(killer, signalType)
                .victim(dead)
                .build();

        processArmorEffects(killer, signalType, context);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        EffectContext context = EffectContext.builder(player, SignalType.SHIFT)
                .event(event)
                .build();

        processArmorEffects(player, SignalType.SHIFT, context);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileSource source = event.getEntity().getShooter();
        if (!(source instanceof Player player)) return;

        // Determine if this is a bow or trident shot
        String projectileName = event.getEntity().getType().name();
        SignalType signalType = projectileName.contains("ARROW") ? SignalType.BOW_SHOOT :
                                   projectileName.contains("TRIDENT") ? SignalType.TRIDENT_THROW :
                                   null;
        if (signalType == null) return;

        EffectContext context = EffectContext.builder(player, signalType)
                .event(event)
                .build();

        processArmorEffects(player, signalType, context);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileHit(ProjectileHitEvent event) {
        ProjectileSource source = event.getEntity().getShooter();
        if (!(source instanceof Player player)) return;

        // Only handle arrows and tridents
        String projectileName = event.getEntity().getType().name();
        if (!projectileName.contains("ARROW") && !projectileName.contains("TRIDENT")) return;

        // Get hit entity if it exists
        LivingEntity victim = null;
        if (event.getHitEntity() instanceof LivingEntity living) {
            victim = living;
        }

        EffectContext context = EffectContext.builder(player, SignalType.BOW_HIT)
                .event(event)
                .victim(victim)
                .location(event.getHitBlock() != null ? event.getHitBlock().getLocation() :
                         (victim != null ? victim.getLocation() : player.getLocation()))
                .build();

        processArmorEffects(player, SignalType.BOW_HIT, context);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        EffectContext context = EffectContext.builder(player, SignalType.BLOCK_BREAK)
                .event(event)
                .location(event.getBlock().getLocation())
                .build();

        processArmorEffects(player, SignalType.BLOCK_BREAK, context);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        EffectContext context = EffectContext.builder(player, SignalType.BLOCK_PLACE)
                .event(event)
                .location(event.getBlock().getLocation())
                .build();

        processArmorEffects(player, SignalType.BLOCK_PLACE, context);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Handle all click actions (left and right, air and block)
        Action action = event.getAction();
        if (action == Action.PHYSICAL) return; // Skip pressure plates, tripwires, etc.

        // Skip socket interactions (handled elsewhere)
        ItemStack item = event.getItem();
        if (item != null && plugin.getSigilManager().isSigilItem(item)) return;

        Player player = event.getPlayer();

        EffectContext context = EffectContext.builder(player, SignalType.INTERACT)
                .event(event)
                .location(event.getClickedBlock() != null ? event.getClickedBlock().getLocation() :
                         player.getLocation())
                .build();

        processArmorEffects(player, SignalType.INTERACT, context);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerFish(PlayerFishEvent event) {
        // Only trigger on successful catch
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();

        EffectContext context = EffectContext.builder(player, SignalType.FISH)
                .event(event)
                .location(event.getHook().getLocation())
                .build();

        // Store catch type for conditions/effects
        context.setMetadata("catchEntity", event.getCaught());

        processArmorEffects(player, SignalType.FISH, context);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if the item has sigils socketed
        if (!plugin.getSocketManager().hasSocketedSigil(item)) {
            return;
        }

        // Check if this damage would break the item
        // ItemStack durability: 0 = full, max = broken
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
            int currentDamage = damageable.getDamage();
            int maxDurability = item.getType().getMaxDurability();
            int newDamage = currentDamage + event.getDamage();

            // Only fire ITEM_BREAK signal if this damage would destroy the item
            if (newDamage < maxDurability) {
                return; // Item won't break from this damage, skip
            }

            // Item WOULD break - fire the signal (cancellable!)
            EffectContext context = EffectContext.builder(player, SignalType.ITEM_BREAK)
                    .event(event)
                    .location(player.getLocation())
                    .build();
            context.setMetadata("breakingItem", item);
            context.setMetadata("damageAmount", event.getDamage());

            // Process effects from the item's sigils
            for (Sigil sigil : plugin.getSocketManager().getSocketedSigils(item)) {
                processSigilForSignal(player, sigil, SignalType.ITEM_BREAK, context, item);
            }
        }
    }

    // ==================== COMBAT DEBUG ====================

    /**
     * Log comprehensive debug info for combat involving stunned/skin-changed players.
     * This helps diagnose hit detection issues after packet-based skin changes.
     */
    private void logCombatDebug(String eventType, Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        boolean attackerHasSkinChange = plugin.getSkinChangeManager() != null &&
                plugin.getSkinChangeManager().hasSkinChange(attacker);
        boolean attackerIsStunned = plugin.getStunManager() != null &&
                plugin.getStunManager().isStunned(attacker);
        // Also check for RECENT skin change (within 5s) even if restored
        boolean attackerHadRecentSkinChange = plugin.getSkinChangeManager() != null &&
                plugin.getSkinChangeManager().getTimeSinceSkinChange(attacker) >= 0 &&
                plugin.getSkinChangeManager().getTimeSinceSkinChange(attacker) < 5000;

        boolean victimIsPlayer = victim instanceof Player;
        boolean victimHasSkinChange = false;
        boolean victimIsStunned = false;
        boolean victimHadRecentSkinChange = false;

        if (victimIsPlayer) {
            Player victimPlayer = (Player) victim;
            victimHasSkinChange = plugin.getSkinChangeManager() != null &&
                    plugin.getSkinChangeManager().hasSkinChange(victimPlayer);
            victimIsStunned = plugin.getStunManager() != null &&
                    plugin.getStunManager().isStunned(victimPlayer);
            victimHadRecentSkinChange = plugin.getSkinChangeManager() != null &&
                    plugin.getSkinChangeManager().getTimeSinceSkinChange(victimPlayer) >= 0 &&
                    plugin.getSkinChangeManager().getTimeSinceSkinChange(victimPlayer) < 5000;
        }

        // Only log if either party has skin change, stun, or RECENT skin change
        if (!attackerHasSkinChange && !attackerIsStunned && !victimHasSkinChange && !victimIsStunned
                && !attackerHadRecentSkinChange && !victimHadRecentSkinChange) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n========== COMBAT DEBUG (").append(eventType).append(") ==========\n");

        // Attacker info
        sb.append("ATTACKER: ").append(attacker.getName()).append("\n");
        sb.append("  entityId=").append(attacker.getEntityId()).append("\n");
        sb.append("  noDamageTicks=").append(attacker.getNoDamageTicks()).append("\n");
        sb.append("  maxNoDamageTicks=").append(attacker.getMaximumNoDamageTicks()).append("\n");
        sb.append("  invulnerable=").append(attacker.isInvulnerable()).append("\n");
        sb.append("  isStunned=").append(attackerIsStunned).append("\n");
        if (plugin.getSkinChangeManager() != null) {
            sb.append("  skinChange: ").append(plugin.getSkinChangeManager().getDebugInfo(attacker)).append("\n");
        }

        // Victim info
        sb.append("VICTIM: ").append(victim.getName()).append(" (").append(victim.getType()).append(")\n");
        sb.append("  entityId=").append(victim.getEntityId()).append("\n");
        sb.append("  noDamageTicks=").append(victim.getNoDamageTicks()).append("\n");
        sb.append("  maxNoDamageTicks=").append(victim.getMaximumNoDamageTicks()).append("\n");
        sb.append("  invulnerable=").append(victim.isInvulnerable()).append("\n");
        if (victimIsPlayer) {
            Player victimPlayer = (Player) victim;
            sb.append("  isStunned=").append(victimIsStunned).append("\n");
            if (plugin.getSkinChangeManager() != null) {
                sb.append("  skinChange: ").append(plugin.getSkinChangeManager().getDebugInfo(victimPlayer)).append("\n");
            }
        }

        // Event info
        sb.append("EVENT:\n");
        sb.append("  cause=").append(event.getCause()).append("\n");
        sb.append("  damage=").append(event.getDamage()).append("\n");
        sb.append("  finalDamage=").append(event.getFinalDamage()).append("\n");
        sb.append("  cancelled=").append(event.isCancelled()).append("\n");

        // Distance between entities
        double distance = attacker.getLocation().distance(victim.getLocation());
        sb.append("  distance=").append(String.format("%.2f", distance)).append(" blocks\n");

        // Key insight: spawn invulnerability is 60 ticks (3 seconds)
        // Check if we're within that window
        if (plugin.getSkinChangeManager() != null) {
            long attackerMs = plugin.getSkinChangeManager().getTimeSinceSkinChange(attacker);
            long victimMs = victimIsPlayer ?
                    plugin.getSkinChangeManager().getTimeSinceSkinChange((Player) victim) : -1;

            if (attackerMs >= 0 && attackerMs < 3000) {
                sb.append("  WARNING: Attacker skin changed ").append(attackerMs).append("ms ago (within 3s spawn invuln window)\n");
            }
            if (victimMs >= 0 && victimMs < 3000) {
                sb.append("  WARNING: Victim skin changed ").append(victimMs).append("ms ago (within 3s spawn invuln window)\n");
            }
        }

        sb.append("==============================================");

        plugin.getLogger().info(sb.toString());

        // Also send to both players involved (if victim is player)
        attacker.sendMessage("§c[Debug] Combat event logged - check console for details");
        if (victimIsPlayer) {
            ((Player) victim).sendMessage("§c[Debug] Combat event logged - check console for details");
        }
    }

    // ==================== TICK SIGNAL ====================

    /**
     * Start task for periodic TICK signal.
     */
    private void startTickSignalTask() {
        int interval = plugin.getConfigManager().getMainConfig()
                .getInt("settings.tick-interval", 1); // Default 1 tick

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    EffectContext context = EffectContext.builder(player, SignalType.TICK).build();
                    processArmorEffects(player, SignalType.TICK, context);
                }
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    // ==================== PROCESSING METHODS ====================

    /**
     * Process armor effects for a signal.
     * Since sets have been removed, this only processes sigil effects.
     */
    private void processArmorEffects(Player player, SignalType signalType, EffectContext context) {
        // Process sigil effects
        processSigilEffects(player, signalType, context);
    }

    /**
     * Process sigil effects - supports multiple sigils per armor piece and held items.
     * Collects ALL flows from ALL sigils and sorts by priority globally.
     */
    private void processSigilEffects(Player player, SignalType signalType, EffectContext context) {
        // Collect all flows from all equipped sigils with their metadata
        List<FlowEntry> allFlows = new java.util.ArrayList<>();

        // Check each armor slot for socketed sigils
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;
            List<Sigil> sigils = plugin.getSocketManager().getSocketedSigils(armor);
            for (Sigil sigil : sigils) {
                collectFlowsForSignal(sigil, signalType, armor, allFlows);
            }
        }

        // Check main hand item for socketed sigils
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!mainHand.getType().isAir()
                && plugin.getSocketManager().isSocketable(mainHand.getType())
                && !plugin.getSocketManager().isArmor(mainHand.getType())) {
            List<Sigil> heldSigils = plugin.getSocketManager().getSocketedSigils(mainHand);
            for (Sigil sigil : heldSigils) {
                collectFlowsForSignal(sigil, signalType, mainHand, allFlows);
            }
        }

        // Check off-hand item
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (!offHand.getType().isAir()
                && plugin.getSocketManager().isSocketable(offHand.getType())
                && !plugin.getSocketManager().isArmor(offHand.getType())) {
            List<Sigil> offHandSigils = plugin.getSocketManager().getSocketedSigils(offHand);
            for (Sigil sigil : offHandSigils) {
                collectFlowsForSignal(sigil, signalType, offHand, allFlows);
            }
        }

        if (allFlows.isEmpty()) return;

        // For STATIC/passive signals, ALL flows should execute (no priority competition)
        // For active signals (ATTACK, DEFENSE, SHIFT, etc.), use priority - only ONE activates
        boolean isPassiveSignal = signalType == SignalType.EFFECT_STATIC ||
                                   signalType == SignalType.TICK;

        if (!isPassiveSignal) {
            // Sort by priority for active signals
            allFlows.sort((a, b) -> Integer.compare(b.flow.getPriority(), a.flow.getPriority()));
        }

        // Execute flows
        for (FlowEntry entry : allFlows) {
            context.setMetadata("sourceSigilId", entry.sigil.getId());
            context.setMetadata("sourceSigilTier", entry.sigil.getTier());
            context.setMetadata("sourceItem", entry.sourceItem);
            if (entry.sigil.getTierScalingConfig() != null) {
                context.setMetadata("tierScalingConfig", entry.sigil.getTierScalingConfig());
            }

            String flowId = entry.flow.getGraph() != null ? entry.flow.getGraph().getId() : "unknown";
            String cooldownKey = "sigil_" + entry.sigil.getId() + "_" + signalType.getConfigKey() + "_" + flowId;

            boolean activated = executeFlow(player, entry.flow.getGraph(), entry.flow, context, cooldownKey);

            if (activated && !isPassiveSignal) {
                // For active signals, stop after first activation (priority system)
                if (entry.sourceItem != null) {
                    plugin.getTierProgressionManager().awardXP(player, entry.sourceItem, entry.sigil.getId(), entry.sigil.getTier());
                }
                break;
            } else if (activated && isPassiveSignal) {
                // For passive signals, award XP but continue to next flow
                if (entry.sourceItem != null) {
                    plugin.getTierProgressionManager().awardXP(player, entry.sourceItem, entry.sigil.getId(), entry.sigil.getTier());
                }
                // Don't break - continue executing all passive flows
            }
        }
    }

    /**
     * Helper class to hold flow with its sigil and source item.
     */
    private static class FlowEntry {
        final FlowConfig flow;
        final Sigil sigil;
        final ItemStack sourceItem;

        FlowEntry(FlowConfig flow, Sigil sigil, ItemStack sourceItem) {
            this.flow = flow;
            this.sigil = sigil;
            this.sourceItem = sourceItem;
        }
    }

    /**
     * Collect flows from a sigil for a signal type.
     */
    private void collectFlowsForSignal(Sigil sigil, SignalType signalType, ItemStack sourceItem, List<FlowEntry> allFlows) {
        List<FlowConfig> flows = sigil.getFlowsForTrigger(signalType.getConfigKey());
        if (flows.isEmpty()) {
            flows = sigil.getFlowsForTrigger(signalType.name());
        }
        for (FlowConfig flow : flows) {
            allFlows.add(new FlowEntry(flow, sigil, sourceItem));
        }
    }

    /**
     * Process a single sigil for a specific signal type.
     * Awards XP on successful activation.
     *
     * Uses the unified flows system - sigil.getFlowForTrigger() handles
     * trigger normalization (ON_ prefix, aliases like SNEAK->SHIFT).
     *
     * @param player The player
     * @param sigil The sigil being processed
     * @param signalType The signal type
     * @param context The effect context
     * @param sourceItem The item containing the sigil (for XP tracking)
     */
    private void processSigilForSignal(Player player, Sigil sigil, SignalType signalType,
            EffectContext context, ItemStack sourceItem) {

        // Get ALL flows for this signal - a sigil can have multiple flows with the same trigger
        // e.g., "sky_stepper" (dash up, priority 2) and "dasher" (dash forward, priority 1)
        List<FlowConfig> flowConfigs = sigil.getFlowsForTrigger(signalType.getConfigKey());

        // Also check by signal type name if config key didn't match
        if (flowConfigs.isEmpty()) {
            flowConfigs = sigil.getFlowsForTrigger(signalType.name());
        }

        // No flows for this signal
        if (flowConfigs.isEmpty()) {
            return;
        }

        // Sort by priority (higher priority first) - only ONE flow will activate
        flowConfigs.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        // Add sigil metadata for effects that need to know their source (e.g., DECREASE_SIGIL_TIER)
        context.setMetadata("sourceSigilId", sigil.getId());
        context.setMetadata("sourceSigilTier", sigil.getTier());
        context.setMetadata("sourceItem", sourceItem);

        // Add tier scaling config for {param} placeholder replacement
        if (sigil.getTierScalingConfig() != null) {
            context.setMetadata("tierScalingConfig", sigil.getTierScalingConfig());
        }

        // Try flows in priority order - first one that activates wins, others are skipped
        for (int i = 0; i < flowConfigs.size(); i++) {
            FlowConfig flowConfig = flowConfigs.get(i);
            if (flowConfig == null || !flowConfig.hasNodes()) {
                continue;
            }

            // Each flow gets its own cooldown key (using flow ID or index to differentiate)
            String flowId = flowConfig.getGraph() != null ? flowConfig.getGraph().getId() : String.valueOf(i);
            String cooldownKey = "sigil_" + sigil.getId() + "_" + signalType.getConfigKey() + "_" + flowId;

            // Execute the flow - returns true if it actually activated (conditions passed, effects ran)
            boolean activated = executeFlow(player, flowConfig.getGraph(), flowConfig, context, cooldownKey);

            // If this flow activated, stop checking lower priority flows
            if (activated) {
                // Award XP on successful activation
                if (sourceItem != null) {
                    plugin.getTierProgressionManager().awardXP(player, sourceItem, sigil.getId(), sigil.getTier());
                }
                break; // Only ONE flow activates per signal
            }
        }
    }

    /**
     * Execute a flow graph with cooldown and chance checks.
     *
     * @param player The player
     * @param flow The flow graph to execute
     * @param flowConfig The flow configuration (may be null for legacy flows)
     * @param context The effect context
     * @param cooldownKey The cooldown key
     * @return true if flow was executed, false if blocked by cooldown/chance
     */
    private boolean executeFlow(Player player, FlowGraph flow, FlowConfig flowConfig, EffectContext context, String cooldownKey) {
        // Get cooldown and chance - prefer START node tier-scaled values, fall back to FlowConfig
        double cooldown = flowConfig != null ? flowConfig.getCooldown() : 0;
        double chance = flowConfig != null ? flowConfig.getChance() : 100;

        // Try to get tier-scaled values from START node
        if (flow != null) {
            com.miracle.arcanesigils.flow.FlowNode startNode = flow.getStartNode();
            if (startNode != null) {
                Integer sigilTier = context.getMetadata("sourceSigilTier", null);
                int tier = sigilTier != null ? sigilTier : 1;

                // Get the sigil's tier scaling config for unified tier system
                com.miracle.arcanesigils.tier.TierScalingConfig tierConfig = context.getMetadata("tierScalingConfig", null);

                // Resolve cooldown - check for {cooldown} placeholder first (unified system)
                Object cooldownParam = startNode.getParam("cooldown");
                if (cooldownParam != null && cooldownParam.toString().contains("{")) {
                    // Unified tier system - resolve from TierScalingConfig
                    String placeholder = cooldownParam.toString().replace("{", "").replace("}", "");
                    if (tierConfig != null && tierConfig.hasParam(placeholder)) {
                        cooldown = tierConfig.getParamValue(placeholder, tier);
                    }
                } else if (cooldownParam != null) {
                    cooldown = startNode.getDoubleParam("cooldown", cooldown);
                }

                // Resolve chance - check for {chance} placeholder first (unified system)
                Object chanceParam = startNode.getParam("chance");
                if (chanceParam != null && chanceParam.toString().contains("{")) {
                    // Unified tier system - resolve from TierScalingConfig
                    String placeholder = chanceParam.toString().replace("{", "").replace("}", "");
                    if (tierConfig != null && tierConfig.hasParam(placeholder)) {
                        chance = tierConfig.getParamValue(placeholder, tier);
                    }
                } else if (chanceParam != null) {
                    chance = startNode.getDoubleParam("chance", chance);
                }
            }
        }

        // Check cooldown for flow
        if (cooldown > 0 && plugin.getCooldownManager().isOnCooldown(player, cooldownKey)) {
            return false;
        }

        // Check chance
        if (chance < 100) {
            double roll = ThreadLocalRandom.current().nextDouble() * 100;
            if (roll > chance) {
                return false;
            }
        }

        // Check conditions from FlowConfig
        if (flowConfig != null && flowConfig.getConditions() != null && !flowConfig.getConditions().isEmpty()) {
            if (!conditionManager.checkConditions(flowConfig.getConditions(), context)) {
                return false;
            }
        }

        // Execute the flow and get context
        FlowExecutor executor = new FlowExecutor(plugin);
        com.miracle.arcanesigils.flow.FlowContext flowContext = executor.executeWithContext(flow, context);

        if (flowContext == null) {
            return false;
        }

        // Check if a SKIP_COOLDOWN node was hit (sets skipCooldown flag)
        boolean skipCooldown = flowContext.shouldSkipCooldown();

        // Set cooldown unless explicitly skipped by a SKIP_COOLDOWN node
        if (cooldown > 0 && !skipCooldown) {
            // Get display name from sigil metadata
            String sigilId = context.getMetadata("sourceSigilId", null);
            String displayName = cooldownKey; // Fallback to key
            if (sigilId != null) {
                Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
                if (sigil != null) {
                    displayName = com.miracle.arcanesigils.utils.TextUtil.stripColors(sigil.getName());
                }
            }
            plugin.getCooldownManager().setCooldown(player, cooldownKey, displayName, cooldown);
        }

        // Return true if any effects executed (for XP purposes)
        return flowContext.hasEffectsExecuted();
    }

    /**
     * Process static/passive effects.
     */
    private void processStaticEffects(Player player) {
        EffectContext context = EffectContext.builder(player, SignalType.EFFECT_STATIC).build();
        processArmorEffects(player, SignalType.EFFECT_STATIC, context);
    }

    // ==================== PLAYER JOIN CLEANUP ====================

    /**
     * Clean up any lingering sigil health modifiers when a player joins.
     * This prevents stale modifiers from persisting across sessions.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Clean up all sigil-related max health modifiers
        org.bukkit.attribute.AttributeInstance maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            java.util.List<org.bukkit.attribute.AttributeModifier> toRemove = new java.util.ArrayList<>();
            for (org.bukkit.attribute.AttributeModifier modifier : maxHealth.getModifiers()) {
                String keyName = modifier.getKey().getKey();
                // Remove any modifiers from this plugin (persistent_ prefix or arcanesigils namespace)
                if (keyName.startsWith("persistent_") || keyName.startsWith("sigil_") ||
                    modifier.getKey().getNamespace().equals("arcanesigils")) {
                    toRemove.add(modifier);
                }
            }
            for (org.bukkit.attribute.AttributeModifier modifier : toRemove) {
                maxHealth.removeModifier(modifier);
            }
            if (!toRemove.isEmpty()) {
                // Clamp health if needed
                if (player.getHealth() > maxHealth.getValue()) {
                    player.setHealth(maxHealth.getValue());
                }
            }
        }

        // Clear previous armor tracking to ensure clean slate
        previousArmor.remove(player.getUniqueId());
        appliedEffects.remove(player.getUniqueId());
    }

}
