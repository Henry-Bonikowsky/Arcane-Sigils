package com.zenax.armorsets.events;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.flow.FlowConfig;
import com.zenax.armorsets.flow.FlowExecutor;
import com.zenax.armorsets.flow.FlowGraph;
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

        boolean armorRemoved = false;
        for (int i = 0; i < 4; i++) {
            ItemStack prev = previous[i];
            ItemStack curr = current[i];

            // Check if armor was removed (had armor before, now empty or different)
            if (prev != null && !prev.getType().isAir()) {
                // Compare only type, ignore durability changes
                if (curr == null || curr.getType().isAir() || !isSameArmorPiece(prev, curr)) {
                    // Armor piece was removed or changed
                    Sigil prevSigil = plugin.getSocketManager().getSocketedSigil(prev);
                    Sigil currSigil = curr != null ? plugin.getSocketManager().getSocketedSigil(curr) : null;

                    // If previous had a sigil and current doesn't have the same one
                    if (prevSigil != null && (currSigil == null || !prevSigil.getId().equals(currSigil.getId()))) {
                        armorRemoved = true;
                    }
                }
            }
        }

        if (armorRemoved) {
            removeAppliedEffects(player);
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

        double originalDamage = event.getDamage();
        com.zenax.armorsets.utils.LogHelper.debug("[Signals] ATTACK signal: player=%s, victim=%s, damage=%.2f",
            player.getName(), victim.getName(), originalDamage);

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

        // Always log final damage for debugging
        com.zenax.armorsets.utils.LogHelper.debug("[Signals] Final event damage: %.2f (original: %.2f, changed: %b)",
            event.getDamage(), originalDamage, Math.abs(event.getDamage() - originalDamage) > 0.01);
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
        double reductionPercent = com.zenax.armorsets.effects.impl.DamageReductionBuffEffect.getDamageReduction(player.getUniqueId());
        if (reductionPercent > 0) {
            double reducedDamage = originalDamage * (1 - reductionPercent / 100.0);
            event.setDamage(Math.max(0, reducedDamage));
            com.zenax.armorsets.utils.LogHelper.debug("[Signals] Applied %.1f%% damage reduction buff: %.2f -> %.2f",
                    reductionPercent, originalDamage, event.getDamage());
        }

        // Notify AuraManager of owner hit (triggers pull-on-hit auras)
        if (plugin.getAuraManager() != null) {
            plugin.getAuraManager().onOwnerHit(player.getUniqueId());
        }

        com.zenax.armorsets.utils.LogHelper.debug("[Signals] %s signal: player=%s, attacker=%s, damage=%.2f",
                signalType, player.getName(), attacker != null ? attacker.getName() : "null", event.getDamage());

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
            com.zenax.armorsets.flow.FlowNode startNode = flow.getStartNode();
            if (startNode != null) {
                Integer sigilTier = context.getMetadata("sourceSigilTier", null);
                int tier = sigilTier != null ? sigilTier : 1;

                // Get the sigil's tier scaling config for unified tier system
                com.zenax.armorsets.tier.TierScalingConfig tierConfig = context.getMetadata("tierScalingConfig", null);

                // Resolve cooldown - check for {cooldown} placeholder first (unified system)
                Object cooldownParam = startNode.getParam("cooldown");
                if (cooldownParam != null && cooldownParam.toString().contains("{")) {
                    // Unified tier system - resolve from TierScalingConfig
                    String placeholder = cooldownParam.toString().replace("{", "").replace("}", "");
                    if (tierConfig != null && tierConfig.hasParam(placeholder)) {
                        cooldown = tierConfig.getParamValue(placeholder, tier);
                        com.zenax.armorsets.utils.LogHelper.debug("[Signals] Resolved cooldown from tier config: %.1f (tier %d)", cooldown, tier);
                    }
                } else if (startNode.hasTierScaling("cooldown")) {
                    // Old per-node tier system
                    cooldown = startNode.getTierScaledValue("cooldown", tier, cooldown);
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
                        com.zenax.armorsets.utils.LogHelper.debug("[Signals] Resolved chance from tier config: %.1f%% (tier %d)", chance, tier);
                    }
                } else if (startNode.hasTierScaling("chance")) {
                    // Old per-node tier system
                    chance = startNode.getTierScaledValue("chance", tier, chance);
                } else if (chanceParam != null) {
                    chance = startNode.getDoubleParam("chance", chance);
                }
            }
        }

        // Check cooldown for flow
        if (cooldown > 0 && plugin.getCooldownManager().isOnCooldown(player, cooldownKey)) {
            com.zenax.armorsets.utils.LogHelper.debug("[Signals] Flow on cooldown: %s", cooldownKey);
            return false;
        }

        // Check chance
        if (chance < 100) {
            double roll = ThreadLocalRandom.current().nextDouble() * 100;
            if (roll > chance) {
                com.zenax.armorsets.utils.LogHelper.debug("[Signals] Flow chance failed: rolled %.2f%% > %.2f%%", roll, chance);
                return false;
            }
            com.zenax.armorsets.utils.LogHelper.debug("[Signals] Flow chance succeeded: rolled %.2f%% <= %.2f%%", roll, chance);
        }

        // Check conditions from FlowConfig
        if (flowConfig != null && flowConfig.getConditions() != null && !flowConfig.getConditions().isEmpty()) {
            if (!conditionManager.checkConditions(flowConfig.getConditions(), context)) {
                com.zenax.armorsets.utils.LogHelper.debug("[Signals] Conditions failed for %s", cooldownKey);
                return false;
            }
        }

        com.zenax.armorsets.utils.LogHelper.debug("[Signals] Executing flow: %s for player %s",
            flow.getId(), player.getName());

        // Execute the flow and get context
        FlowExecutor executor = new FlowExecutor(plugin);
        com.zenax.armorsets.flow.FlowContext flowContext = executor.executeWithContext(flow, context);

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
                    displayName = com.zenax.armorsets.utils.TextUtil.stripColors(sigil.getName());
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

    /**
     * Get a tier-scaled value from tier params, or fall back to default.
     */
    private double getScaledValue(EffectContext context, String paramName, double defaultValue) {
        com.zenax.armorsets.tier.TierScalingConfig tierConfig =
            context.getMetadata("tierScalingConfig", null);
        Integer tier = context.getMetadata("sourceSigilTier", null);

        if (tierConfig != null && tier != null && tierConfig.hasParam(paramName)) {
            return tierConfig.getParamValue(paramName, tier);
        }
        return defaultValue;
    }

}
