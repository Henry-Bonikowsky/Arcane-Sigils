package com.miracle.arcanesigils.events;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.binds.BindsListener;
import com.miracle.arcanesigils.binds.LastVictimManager;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.flow.FlowExecutor;
import com.miracle.arcanesigils.flow.FlowGraph;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
    private final LastVictimManager lastVictimManager;
    // Track applied effects per player to remove them when armor is unequipped
    private final Map<UUID, Set<PotionEffectType>> appliedEffects = new HashMap<>();
    // Track previous armor state to detect changes
    private final Map<UUID, ItemStack[]> previousArmor = new HashMap<>();
    // Track last equipped set to prevent duplicate unequipped messages
    private final Map<UUID, String> lastEquippedSet = new HashMap<>();

    public SignalHandler(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.conditionManager = new ConditionManager(plugin);
        this.lastVictimManager = plugin.getLastVictimManager();
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

        // Update set bonuses when armor changes
        if (plugin.getSetBonusManager() != null) {
            plugin.getSetBonusManager().updatePlayerSetBonuses(player);
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        
        // Ignore sweep attacks to prevent multiple sigil activations
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }

        double originalDamage = event.getDamage();

        EffectContext context = EffectContext.builder(player, SignalType.ATTACK)
                .event(event)
                .victim(victim)
                .damage(originalDamage)
                .build();

        // Record last hit for @Victim tracking (only if event not cancelled by other plugins)
        if (!event.isCancelled() && lastVictimManager != null) {
            lastVictimManager.recordHit(player, victim);
        }

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

        // Check invulnerability hits (Divine Intervention)
        if (com.miracle.arcanesigils.effects.impl.InvulnerabilityHitsEffect.isInvulnerable(player.getUniqueId())) {
            event.setCancelled(true);
            com.miracle.arcanesigils.effects.impl.InvulnerabilityHitsEffect.decrementHits(player.getUniqueId());

            // Visual/sound feedback
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
                player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);

            return;
        }

        SignalType signalType = event.getCause() == EntityDamageEvent.DamageCause.FALL
                ? SignalType.FALL_DAMAGE
                : SignalType.DEFENSE;

        // Log poison/wither damage for debugging Ancient Crown
        if (event.getCause() == EntityDamageEvent.DamageCause.POISON ||
            event.getCause() == EntityDamageEvent.DamageCause.WITHER) {
            com.miracle.arcanesigils.utils.LogHelper.info(
                "[AncientCrown] DEFENSE signal firing for %s damage: %.2f",
                event.getCause(), event.getDamage());
        }

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

        // Apply damage modifiers BEFORE processing sigil effects
        double currentDamage = event.getDamage();

        // 1. Apply damage amplification (Cleopatra debuff - target takes MORE damage)
        double amplificationPercent = com.miracle.arcanesigils.effects.impl.DamageAmplificationEffect.getDamageAmplification(player.getUniqueId());
        if (amplificationPercent > 0) {
            currentDamage = currentDamage * (1 + amplificationPercent / 100.0);
        }

        // 2. Apply damage reduction buff (temporary buff effect)
        double reductionPercent = com.miracle.arcanesigils.effects.impl.DamageReductionBuffEffect.getDamageReduction(player.getUniqueId());
        if (reductionPercent > 0) {
            currentDamage = currentDamage * (1 - reductionPercent / 100.0);
        }

        // 3. Apply charge-based DR (King's Brace passive)
        double chargeDR = com.miracle.arcanesigils.effects.impl.UpdateChargeDREffect.getChargeDR(player.getUniqueId());
        if (chargeDR > 0) {
            currentDamage = currentDamage * (1 - chargeDR / 100.0);
        }

        // 4. Apply mark-based damage multipliers (Cleopatra debuff, King's Brace marks, etc.)
        double markMultiplier = plugin.getMarkManager().getDamageMultiplier(player);
        if (markMultiplier != 1.0) {
            currentDamage = currentDamage * markMultiplier;
            com.miracle.arcanesigils.utils.LogHelper.debug(
                "[SignalHandler] Applied mark multiplier %.3f to %s: %.2f -> %.2f",
                markMultiplier, player.getName(), event.getDamage(), currentDamage);
        }

        event.setDamage(Math.max(0, currentDamage));

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

        // Record last hit for @Victim tracking (only if victim exists and event not cancelled)
        if (victim != null && !event.isCancelled() && lastVictimManager != null) {
            lastVictimManager.recordHit(player, victim);
        }

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
            // Use custom max damage if set (for ItemsAdder/custom items), else material default
            int maxDurability = damageable.hasMaxDamage()
                ? damageable.getMaxDamage()
                : item.getType().getMaxDurability();
            int newDamage = currentDamage + event.getDamage();

            // Skip if item has no durability concept (maxDurability 0 = non-damageable)
            if (maxDurability <= 0) {
                return;
            }

            // Only fire ITEM_BREAK signal if this damage would destroy the item
            if (newDamage < maxDurability) {
                return; // Item won't break from this damage, skip
            }

            // Item WOULD break - fire the signal (cancellable!)
            com.miracle.arcanesigils.utils.LogHelper.info(
                "[ITEM_BREAK] %s: %s damage=%d/%d (+%d), customMax=%b - firing signal",
                player.getName(), item.getType(), currentDamage, maxDurability,
                event.getDamage(), damageable.hasMaxDamage());
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
     * Public so it can be called from interception system to fire signals.
     */
    public void processArmorEffects(Player player, SignalType signalType, EffectContext context) {
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
                collectFlowsForSignal(sigil, signalType, armor, allFlows, player);
            }
        }

        // Check main hand item for socketed sigils
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!mainHand.getType().isAir()
                && plugin.getSocketManager().isSocketable(mainHand)
                && !plugin.getSocketManager().isArmor(mainHand)) {
            List<Sigil> heldSigils = plugin.getSocketManager().getSocketedSigils(mainHand);
            for (Sigil sigil : heldSigils) {
                collectFlowsForSignal(sigil, signalType, mainHand, allFlows, player);
            }
        }

        // Check off-hand item
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (!offHand.getType().isAir()
                && plugin.getSocketManager().isSocketable(offHand)
                && !plugin.getSocketManager().isArmor(offHand)) {
            List<Sigil> offHandSigils = plugin.getSocketManager().getSocketedSigils(offHand);
            for (Sigil sigil : offHandSigils) {
                collectFlowsForSignal(sigil, signalType, offHand, allFlows, player);
            }
        }

        if (allFlows.isEmpty()) return;

        // For STATIC/passive signals, ALL flows should execute (no priority competition)
        // For active signals (ATTACK, DEFENSE, SHIFT, etc.), use priority tiers:
        //   - All flows at the highest priority level execute together
        //   - If any activated at that level, lower priority flows are skipped
        //   - If none activated, try the next priority level
        boolean isPassiveSignal = signalType == SignalType.EFFECT_STATIC ||
                                   signalType == SignalType.TICK;

        if (!isPassiveSignal) {
            // Sort by priority for active signals (highest first)
            allFlows.sort((a, b) -> Integer.compare(b.flow.getPriority(), a.flow.getPriority()));
        }

        // Execute flows
        int activatedPriority = Integer.MIN_VALUE;
        for (FlowEntry entry : allFlows) {
            // For active signals, skip lower-priority flows once a higher priority activated
            if (!isPassiveSignal && activatedPriority != Integer.MIN_VALUE
                    && entry.flow.getPriority() < activatedPriority) {
                break;
            }

            context.setMetadata("sourceSigilId", entry.sigil.getId());
            context.setMetadata("sourceSigilTier", entry.sigil.getTier());
            context.setMetadata("sourceItem", entry.sourceItem);
            if (entry.sigil.getTierScalingConfig() != null) {
                context.setMetadata("tierScalingConfig", entry.sigil.getTierScalingConfig());

                // Inject tier params as variables (for behavior_params and other param usage)
                com.miracle.arcanesigils.tier.TierParameterConfig tierParams = entry.sigil.getTierScalingConfig().getParams();
                if (tierParams != null) {
                    for (String paramName : tierParams.getParameterNames()) {
                        double value = tierParams.getValue(paramName, entry.sigil.getTier());
                        context.setVariable(paramName, value);
                    }
                }
            }

            String flowId = entry.flow.getGraph() != null ? entry.flow.getGraph().getId() : "unknown";
            String cooldownKey = "sigil_" + entry.sigil.getId() + "_" + signalType.getConfigKey() + "_" + flowId;

            boolean activated = executeFlow(player, entry.flow.getGraph(), entry.flow, context, cooldownKey);

            if (activated) {
                // Award XP for activation
                if (entry.sourceItem != null) {
                    plugin.getTierProgressionManager().awardXP(player, entry.sourceItem, entry.sigil.getId(), entry.sigil.getTier());
                }
                // Track the priority level that activated
                if (!isPassiveSignal) {
                    activatedPriority = entry.flow.getPriority();
                }
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
     * Filters out flows that are on cooldown before adding to the list.
     */
    private void collectFlowsForSignal(Sigil sigil, SignalType signalType, ItemStack sourceItem, List<FlowEntry> allFlows, Player player) {
        List<FlowConfig> flows = sigil.getFlowsForTrigger(signalType.getConfigKey());
        if (flows.isEmpty()) {
            flows = sigil.getFlowsForTrigger(signalType.name());
        }

        for (FlowConfig flow : flows) {
            // Check cooldown BEFORE adding to list
            String flowId = flow.getGraph() != null ? flow.getGraph().getId() : "unknown";
            String cooldownKey = "sigil_" + sigil.getId() + "_" + signalType.getConfigKey() + "_" + flowId;

            // Get cooldown value from flow
            double cooldown = flow.getCooldown();
            if (flow.getGraph() != null && flow.getGraph().getStartNode() != null) {
                cooldown = flow.getGraph().getStartNode().getDoubleParam("cooldown", cooldown);
            }

            // Skip if on cooldown
            if (cooldown > 0 && plugin.getCooldownManager().isOnCooldown(player, cooldownKey)) {
                continue;
            }

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
                    } else {
                        // Also check context variables (for set bonuses and other param sources)
                        Object varValue = context.getVariable(placeholder);
                        if (varValue instanceof Number) {
                            cooldown = ((Number) varValue).doubleValue();
                        }
                    }
                } else if (cooldownParam != null) {
                    cooldown = startNode.getDoubleParam("cooldown", cooldown);
                }

                // Resolve chance - check for {chance} placeholder first (unified system)
                Object chanceParam = startNode.getParam("chance");
                if (chanceParam != null && chanceParam.toString().contains("{")) {
                    // Unified tier system - resolve from TierScalingConfig
                    String placeholder = chanceParam.toString().replace("{", "").replace("}", "");
                    com.miracle.arcanesigils.utils.LogHelper.debug("[executeFlow] Resolving chance placeholder: %s", placeholder);
                    if (tierConfig != null && tierConfig.hasParam(placeholder)) {
                        chance = tierConfig.getParamValue(placeholder, tier);
                    } else {
                        // Also check context variables (for set bonuses and other param sources)
                        Object varValue = context.getVariable(placeholder);
                        if (varValue instanceof Number) {
                            chance = ((Number) varValue).doubleValue();
                        }
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
            com.miracle.arcanesigils.utils.LogHelper.debug("[executeFlow] Chance roll: %.1f vs %.1f (pass=%s)", roll, chance, roll <= chance);
            if (roll > chance) {
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

        // Process regular sigil effects
        processArmorEffects(player, SignalType.EFFECT_STATIC, context);

        // Process set bonus effects
        processSetBonusEffects(player, context);
    }

    /**
     * Process set bonus effects for player.
     * Set bonuses act like global behaviors - they trigger independently of sigils.
     */
    private void processSetBonusEffects(Player player, EffectContext context) {
        var setBonusManager = plugin.getSetBonusManager();
        if (setBonusManager == null) return;

        java.util.Map<String, Integer> activeBonuses = setBonusManager.getActiveBonuses(player);

        com.miracle.arcanesigils.utils.LogHelper.debug("[SetBonus] Processing for player %s, active bonuses: %d",
            player.getName(), activeBonuses.size());

        for (java.util.Map.Entry<String, Integer> entry : activeBonuses.entrySet()) {
            String setName = entry.getKey();
            int tier = entry.getValue();

            com.miracle.arcanesigils.sets.SetBonus setBonus = setBonusManager.getSetBonus(setName);
            if (setBonus == null) {
                com.miracle.arcanesigils.utils.LogHelper.debug("[SetBonus] Set '%s' not found in manager!", setName);
                continue;
            }

            com.miracle.arcanesigils.utils.LogHelper.debug("[SetBonus] Executing %s (tier %d) for %s",
                setName, tier, player.getName());

            // Inject set bonus tier into context as metadata
            context.setMetadata("setBonus_" + setName, tier);
            context.setMetadata("currentSetName", setName);
            context.setMetadata("currentSetTier", tier);

            // Resolve tier params from set config and inject into context as variables
            for (java.util.Map.Entry<String, java.util.Map<Integer, Double>> paramEntry : setBonus.getTierParams().entrySet()) {
                String paramName = paramEntry.getKey();
                Double value = paramEntry.getValue().get(tier);
                if (value != null) {
                    context.setVariable(paramName, value);
                    com.miracle.arcanesigils.utils.LogHelper.debug("[SetBonus]   Param: %s = %.1f", paramName, value);
                }
            }

            // Execute set bonus flow (use executeFlow to handle chance/cooldown)
            String cooldownKey = "setbonus_" + setName + "_EFFECT_STATIC";
            com.miracle.arcanesigils.utils.LogHelper.debug("[SetBonus] About to execute flow for %s", setName);
            boolean activated = executeFlow(player, setBonus.getFlow().getGraph(), setBonus.getFlow(), context, cooldownKey);
            com.miracle.arcanesigils.utils.LogHelper.debug("[SetBonus] Finished executing flow for %s (activated=%s)", setName, activated);
        }
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
