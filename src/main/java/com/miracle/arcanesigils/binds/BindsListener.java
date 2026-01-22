package com.miracle.arcanesigils.binds;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.ai.AITrainingManager;
import com.miracle.arcanesigils.ai.RewardSignal;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.events.SignalType;
import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.flow.FlowContext;
import com.miracle.arcanesigils.flow.FlowExecutor;
import com.miracle.arcanesigils.utils.LogHelper;
import com.miracle.arcanesigils.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Listener that handles hotkey detection for activating abilities via the binds system.
 *
 * Detects various hotkey combinations:
 * - Toggle UI: SNEAK+SWAP, Double-Tap SWAP, ATTACK+USE, SNEAK+USE, SNEAK+ATTACK
 * - Hotbar slot selection: Number keys 1-9
 * - Held slot activation: SWAP_HAND, ATTACK, USE_ITEM (depending on player setting)
 *
 * When a bind is activated, executes all sigils in that bind with 1-second delay between each.
 */
public class BindsListener implements Listener {
    private final ArmorSetsPlugin plugin;
    private final BindsManager bindsManager;
    private final BindsBossBarManager bossBarManager;
    private final TargetGlowManager targetGlowManager;
    private final com.miracle.arcanesigils.events.ConditionManager conditionManager;

    // Track sneak state for combo detection
    private final Map<UUID, Boolean> sneaking = new HashMap<>();

    // Track last swap time for double-tap detection
    private final Map<UUID, Long> lastSwapTime = new HashMap<>();

    // Track pending swap tasks for delayed execution (allows double-tap detection)
    private final Map<UUID, Integer> pendingSwapTasks = new HashMap<>();

    // Track last attack/use time for simultaneous detection
    private final Map<UUID, Long> lastAttackTime = new HashMap<>();
    private final Map<UUID, Long> lastUseTime = new HashMap<>();

    // Track last held slot to detect when player is already on a slot
    private final Map<UUID, Integer> lastHeldSlot = new HashMap<>();

    // Track last activated bind for AI training kill attribution
    private final Map<UUID, Integer> lastActivatedBindSlot = new HashMap<>();
    private final Map<UUID, Long> lastActivationTime = new HashMap<>();

    // Activation delays
    private static final long DOUBLE_TAP_WINDOW = 300; // ms - quick double-tap detection
    private static final long SIMULTANEOUS_WINDOW = 200; // ms for ATTACK+USE detection
    private static final long SIGIL_ACTIVATION_DELAY = 20L; // ticks (1 second)

    public BindsListener(ArmorSetsPlugin plugin, BindsManager bindsManager, BindsBossBarManager bossBarManager, TargetGlowManager targetGlowManager) {
        this.plugin = plugin;
        this.bindsManager = bindsManager;
        this.bossBarManager = bossBarManager;
        this.targetGlowManager = targetGlowManager;
        this.conditionManager = new com.miracle.arcanesigils.events.ConditionManager(plugin);
    }

    // ==================== TOGGLE DETECTION ====================

    /**
     * Handle swap hand events - used for multiple toggle combos and held slot activation.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerBindData data = bindsManager.getPlayerData(uuid);
        long now = System.currentTimeMillis();

        // Check for toggle hotkeys
        boolean toggled = false;
        boolean uiIsOn = data.isToggled();

        // SNEAK + SWAP_HAND
        if (data.getToggleHotkey() == ToggleHotkey.SNEAK_SWAP && sneaking.getOrDefault(uuid, false)) {
            event.setCancelled(true);
            toggled = true;
            LogHelper.debug("[Binds] SNEAK+SWAP detected for " + player.getName());
        }
        // DOUBLE_SWAP - delayed swap to allow double-tap detection
        else if (data.getToggleHotkey() == ToggleHotkey.DOUBLE_SWAP) {
            Long lastSwap = lastSwapTime.get(uuid);

            if (lastSwap != null && (now - lastSwap) <= DOUBLE_TAP_WINDOW) {
                // Second press within window - cancel pending swap and toggle UI
                event.setCancelled(true);
                Integer pendingTask = pendingSwapTasks.remove(uuid);
                if (pendingTask != null) {
                    Bukkit.getScheduler().cancelTask(pendingTask);
                }
                toggled = true;
                lastSwapTime.remove(uuid); // Reset so next press starts fresh
                LogHelper.debug("[Binds] Double-tap SWAP detected for " + player.getName());
            } else {
                // First press - cancel event and track timing
                event.setCancelled(true);
                lastSwapTime.put(uuid, now);

                // Cancel any existing pending task
                Integer existingTask = pendingSwapTasks.remove(uuid);
                if (existingTask != null) {
                    Bukkit.getScheduler().cancelTask(existingTask);
                }

                if (uiIsOn) {
                    // UI is ON - activate held slot if SWAP_HAND is the activation hotkey
                    if (data.getHeldSlotHotkey() == HeldSlotHotkey.SWAP_HAND) {
                        activateHeldSlotBind(player);
                    }
                    // Don't schedule delayed swap, just track timing for potential double-tap toggle OFF
                } else {
                    // UI is OFF - schedule delayed swap for normal swap behavior
                    // Capture CURRENT items from inventory (not event - event has post-swap items)
                    final ItemStack mainHand = player.getInventory().getItemInMainHand().clone();
                    final ItemStack offHand = player.getInventory().getItemInOffHand().clone();

                    // Schedule delayed swap (after double-tap window expires)
                    int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        pendingSwapTasks.remove(uuid);
                        // Perform the swap manually
                        player.getInventory().setItemInMainHand(offHand);
                        player.getInventory().setItemInOffHand(mainHand);
                        LogHelper.debug("[Binds] Delayed swap executed for " + player.getName());
                    }, (DOUBLE_TAP_WINDOW / 50) + 1).getTaskId(); // Convert ms to ticks + 1

                    pendingSwapTasks.put(uuid, taskId);
                }
            }
        }
        // Not a toggle hotkey - block swap if UI is on
        else if (uiIsOn) {
            event.setCancelled(true);

            // Check for held slot activation
            if (data.getHeldSlotHotkey() == HeldSlotHotkey.SWAP_HAND) {
                activateHeldSlotBind(player);
            }
            return;
        }

        if (toggled) {
            toggleBinds(player);
        }
    }

    /**
     * Handle player interact events - used for USE_ITEM detection and some ATTACK cases.
     * Note: For reliable left-click detection, we also use PlayerAnimationEvent.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerBindData data = bindsManager.getPlayerData(uuid);
        long now = System.currentTimeMillis();
        Action action = event.getAction();

        // PHYSICAL action (pressure plates) - ignore
        if (action == Action.PHYSICAL) return;

        boolean isAttack = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean isUse = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;

        // Track attack/use times
        if (isAttack) {
            lastAttackTime.put(uuid, now);
        }
        if (isUse) {
            lastUseTime.put(uuid, now);
        }

        // Check for toggle hotkeys
        boolean toggled = false;

        // SNEAK + USE_ITEM
        if (data.getToggleHotkey() == ToggleHotkey.SNEAK_USE && sneaking.getOrDefault(uuid, false) && isUse) {
            event.setCancelled(true);
            toggled = true;
            LogHelper.debug("[Binds] SNEAK+USE detected for " + player.getName());
        }
        // SNEAK + ATTACK
        else if (data.getToggleHotkey() == ToggleHotkey.SNEAK_ATTACK && sneaking.getOrDefault(uuid, false) && isAttack) {
            event.setCancelled(true);
            toggled = true;
            LogHelper.debug("[Binds] SNEAK+ATTACK detected for " + player.getName());
        }
        // ATTACK + USE_ITEM (simultaneous)
        else if (data.getToggleHotkey() == ToggleHotkey.ATTACK_USE) {
            Long lastAttack = lastAttackTime.get(uuid);
            Long lastUse = lastUseTime.get(uuid);

            if (lastAttack != null && lastUse != null) {
                long timeDiff = Math.abs(lastAttack - lastUse);
                if (timeDiff <= SIMULTANEOUS_WINDOW) {
                    event.setCancelled(true);
                    toggled = true;
                    LogHelper.debug("[Binds] ATTACK+USE detected for " + player.getName());
                }
            }
        }

        if (toggled) {
            toggleBinds(player);
            return;
        }

        // Check for held slot activation
        if (!data.isToggled()) return;

        if (data.getHeldSlotHotkey() == HeldSlotHotkey.ATTACK && isAttack) {
            event.setCancelled(true);
            activateHeldSlotBind(player);
        } else if (data.getHeldSlotHotkey() == HeldSlotHotkey.USE_ITEM && isUse) {
            event.setCancelled(true);
            activateHeldSlotBind(player);
        }
    }

    /**
     * Handle player arm swing (left click) - more reliable than PlayerInteractEvent for air/empty hand.
     * This fires whenever player swings arm, regardless of what they're holding or looking at.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onArmSwing(PlayerAnimationEvent event) {
        // Only handle arm swing animation
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerBindData data = bindsManager.getPlayerData(uuid);
        long now = System.currentTimeMillis();

        // Track attack time for combo detection
        lastAttackTime.put(uuid, now);

        // Check for SNEAK + ATTACK toggle
        if (data.getToggleHotkey() == ToggleHotkey.SNEAK_ATTACK && sneaking.getOrDefault(uuid, false)) {
            event.setCancelled(true);
            toggleBinds(player);
            LogHelper.debug("[Binds] SNEAK+ATTACK (arm swing) detected for " + player.getName());
            return;
        }

        // Check for ATTACK + USE_ITEM toggle (simultaneous)
        if (data.getToggleHotkey() == ToggleHotkey.ATTACK_USE) {
            Long lastUse = lastUseTime.get(uuid);
            if (lastUse != null && (now - lastUse) <= SIMULTANEOUS_WINDOW) {
                event.setCancelled(true);
                toggleBinds(player);
                LogHelper.debug("[Binds] ATTACK+USE (arm swing) detected for " + player.getName());
                return;
            }
        }

        // Check for held slot activation with ATTACK hotkey
        if (data.isToggled() && data.getHeldSlotHotkey() == HeldSlotHotkey.ATTACK) {
            event.setCancelled(true);
            activateHeldSlotBind(player);
        }
    }

    /**
     * Track sneak state for combo detection.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        sneaking.put(player.getUniqueId(), event.isSneaking());
    }

    /**
     * Track held slot changes for hotbar slot bind detection.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerBindData data = bindsManager.getPlayerData(uuid);

        // Debug logging to trace slot change events
        LogHelper.debug("[Binds] Slot change event: " + event.getPreviousSlot() + " -> " + event.getNewSlot() +
                       " for " + player.getName() + " (toggled=" + data.isToggled() +
                       ", system=" + data.getActiveSystem() + ")");

        // Skip if player has an inventory GUI open
        if (player.getOpenInventory().getTopInventory().getSize() > 5) {
            LogHelper.debug("[Binds] Skipping - inventory GUI open");
            return;
        }

        // Only process if binds are toggled ON
        if (!data.isToggled()) {
            lastHeldSlot.put(uuid, event.getNewSlot());
            return;
        }

        int newSlot = event.getNewSlot();
        lastHeldSlot.put(uuid, newSlot);

        // Activate bind for hotbar system (slots 0-8 map to bind slots 1-9)
        if (data.getActiveSystem() == BindSystem.HOTBAR) {
            LogHelper.debug("[Binds] Activating hotbar slot " + (newSlot + 1));
            // Cancel event to prevent hotbar slot from changing while binds are active
            event.setCancelled(true);
            // Convert 0-based hotbar slot to 1-based bind slot
            activateBind(player, newSlot + 1);
        } else {
            LogHelper.debug("[Binds] Not hotbar system, skipping activation");
        }
    }

    // ==================== ACTIVATION LOGIC ====================

    /**
     * Toggle the binds system ON/OFF for a player.
     */
    private void toggleBinds(Player player) {
        boolean newState = bindsManager.toggle(player.getUniqueId());

        if (newState) {
            // Toggled ON - show boss bars and start target glow
            LogHelper.debug("[Binds] Binds toggled ON for " + player.getName());

            // Show boss bars with bound abilities
            bossBarManager.showBossBars(player);

            // Start target glow highlighting
            targetGlowManager.startGlowTask(player);
        } else {
            // Toggled OFF - hide boss bars and stop target glow
            LogHelper.debug("[Binds] Binds toggled OFF for " + player.getName());

            // Hide boss bars
            bossBarManager.hideBossBars(player);

            // Stop target glow highlighting
            targetGlowManager.stopGlowTask(player);
        }
    }

    /**
     * Activate the bind for the currently held slot.
     * Used when player presses the held slot hotkey (SWAP_HAND, ATTACK, or USE_ITEM).
     */
    private void activateHeldSlotBind(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerBindData data = bindsManager.getPlayerData(uuid);

        if (!data.isToggled()) return;

        int heldSlot = player.getInventory().getHeldItemSlot();

        if (data.getActiveSystem() == BindSystem.HOTBAR) {
            // Convert 0-based hotbar slot to 1-based bind slot
            activateBind(player, heldSlot + 1);
        }
        // Command system doesn't use hotbar slots, so no activation here
    }

    /**
     * Activate a bind by slot or ID.
     * For hotbar system: slot is 1-9 (bind slot, matching keys 1-9)
     * For command system: slot is the command bind ID (1+)
     */
    private void activateBind(Player player, int slotOrId) {
        UUID uuid = player.getUniqueId();
        PlayerBindData data = bindsManager.getPlayerData(uuid);

        // Get the sigil IDs bound to this slot
        List<String> sigilIds = data.getCurrentBinds().getBind(slotOrId);

        if (sigilIds.isEmpty()) {
            LogHelper.debug("[Binds] No bind at slot " + slotOrId + " for " + player.getName());
            // Still close the UI even for unbound slots (with small delay for consistency)
            if (data.isToggled()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (bindsManager.getPlayerData(uuid).isToggled()) {
                        toggleBinds(player);
                    }
                }, 2L);
            }
            return;
        }

        LogHelper.debug("[Binds] Activating bind at slot " + slotOrId + " for " + player.getName() +
                       " (" + sigilIds.size() + " sigils)");

        // Capture target NOW before any delays - so abilities requiring target get the right one
        org.bukkit.entity.LivingEntity capturedTarget = null;
        TargetGlowManager glowManager = plugin.getTargetGlowManager();
        if (glowManager != null) {
            capturedTarget = glowManager.getTarget(player);
        }

        // Activate each sigil with delay - but SKIP unequipped sigils immediately (no delay)
        int actualDelayIndex = 0; // Only increment for equipped sigils
        for (int i = 0; i < sigilIds.size(); i++) {
            String sigilId = sigilIds.get(i);

            // Check if sigil is equipped BEFORE scheduling delay
            Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
            if (sigil == null) {
                LogHelper.debug("[Binds] Skipping unknown sigil (no delay): " + sigilId);
                continue; // Skip immediately, no delay
            }

            ItemStack equippedItem = findEquippedSigil(player, sigilId);
            if (equippedItem == null) {
                LogHelper.debug("[Binds] Skipping unequipped sigil (no delay): " + sigilId);
                player.sendMessage(Component.text(TextUtil.colorize(
                    "&c" + TextUtil.stripColors(sigil.getName()) + " &7is not equipped!")));
                continue; // Skip immediately, no delay
            }

            // Sigil is equipped - schedule with delay based on ACTUAL equipped sigils only
            long delay = actualDelayIndex * SIGIL_ACTIVATION_DELAY; // 0, 20, 40, 60... ticks
            actualDelayIndex++; // Only increment for equipped sigils

            final ItemStack finalEquippedItem = equippedItem;
            final org.bukkit.entity.LivingEntity finalTarget = capturedTarget; // Pass captured target to delayed execution
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                activateSigilWithItem(player, sigilId, finalEquippedItem, finalTarget, slotOrId);
            }, delay);
        }

        // Auto-close the ability UI after activating a bind (with small delay so abilities fire first)
        // User must re-open the UI to activate another ability
        if (data.isToggled()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Check again in case something changed
                if (bindsManager.getPlayerData(uuid).isToggled()) {
                    toggleBinds(player);
                }
            }, 2L); // 2 ticks delay
        }
    }

    /**
     * Activate a single sigil by ID.
     * Checks if the sigil is equipped, checks cooldown, and executes its flow.
     */
    private void activateSigil(Player player, String sigilId) {
        // Get the sigil template
        Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
        if (sigil == null) {
            LogHelper.debug("[Binds] Sigil not found: " + sigilId);
            return;
        }

        // Find the equipped instance of this sigil (required for activation)
        ItemStack equippedItem = findEquippedSigil(player, sigilId);
        if (equippedItem == null) {
            LogHelper.debug("[Binds] Sigil not equipped: " + sigilId);
            player.sendMessage(Component.text(TextUtil.colorize(
                "&c" + TextUtil.stripColors(sigil.getName()) + " &7is not equipped!")));
            return;
        }

        // Delegate to the version with pre-found item and no pre-captured target
        activateSigilWithItem(player, sigilId, equippedItem, null, -1);
    }

    /**
     * Activate a single sigil with a pre-found equipped item and optional pre-captured target.
     * Used when we've already validated the sigil is equipped (for efficient multi-bind activation).
     *
     * @param player The player activating
     * @param sigilId The sigil ID
     * @param equippedItem The item containing the sigil (pre-found)
     * @param capturedTarget Optional target captured at schedule time (for delayed abilities)
     * @param slotOrId The bind slot or ID (for AI training tracking)
     */
    private void activateSigilWithItem(Player player, String sigilId, ItemStack equippedItem, org.bukkit.entity.LivingEntity capturedTarget, int slotOrId) {
        // Get the sigil template
        Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
        if (sigil == null) {
            LogHelper.debug("[Binds] Sigil not found: " + sigilId);
            return;
        }

        // Get ABILITY flows from sigil and sort by priority (higher first)
        // Filter to ABILITY type only - SIGNAL flows should not execute on bind activation
        List<FlowConfig> flows = sigil.getFlows().stream()
                .filter(FlowConfig::isAbility)
                .collect(java.util.stream.Collectors.toList());
        if (flows.isEmpty()) {
            LogHelper.debug("[Binds] Sigil has no ability flows: " + sigilId);
            return;
        }

        // Sort by priority - higher priority checked first
        flows.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        plugin.getLogger().info("[Priority] Bind activation for " + sigilId + " has " + flows.size() + " flows");
        for (FlowConfig fc : flows) {
            String flowId = fc.getGraph() != null ? fc.getGraph().getId() : "unknown";
            plugin.getLogger().info("[Priority] -> Flow '" + flowId + "' priority=" + fc.getPriority());
        }

        // Get equipped tier from socket data (format: "sigilId:tier")
        // NOTE: getSigilTierFromItem only works for sigil shard items, not armor!
        // For armor, tier is stored in the socket data string.
        int equippedTier = 1;
        for (String entry : plugin.getSocketManager().getSocketedSigilData(equippedItem)) {
            String[] parts = entry.split(":");
            if (parts[0].equalsIgnoreCase(sigilId) && parts.length > 1) {
                try {
                    equippedTier = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
                break;
            }
        }
        plugin.getLogger().info("[Binds] Sigil " + sigilId + " equipped tier from socket data: " + equippedTier);

        // Get target - prefer captured target, fallback to current TargetGlowManager target
        org.bukkit.entity.LivingEntity target = capturedTarget;
        if (target == null) {
            TargetGlowManager glowManager = plugin.getTargetGlowManager();
            if (glowManager != null) {
                target = glowManager.getTarget(player);
            }
        }

        // Build effect context with target
        EffectContext.Builder contextBuilder = EffectContext.builder(player, SignalType.EFFECT_STATIC);
        if (target != null && !target.isDead()) {
            contextBuilder.victim(target);
        }
        EffectContext context = contextBuilder.build();
        context.setMetadata("sourceSigilId", sigil.getId());
        context.setMetadata("sourceSigilTier", equippedTier);
        context.setMetadata("sourceItem", equippedItem);
        context.setMetadata("aiTraining_bindSlot", slotOrId);

        // CRITICAL: Set tier scaling config for {param} placeholder replacement
        // Without this, tier params like {damage}, {speed}, {cooldown} won't resolve!
        if (sigil.getTierScalingConfig() != null) {
            context.setMetadata("tierScalingConfig", sigil.getTierScalingConfig());
            LogHelper.debug("[Binds] Set tierScalingConfig for " + sigilId + " at tier " + equippedTier);
        } else {
            LogHelper.debug("[Binds] WARNING: No tierScalingConfig for " + sigilId);
        }

        // Track why flows failed - for better user feedback
        double longestRemainingCooldown = 0;
        boolean anyOnCooldown = false;
        boolean anyConditionsFailed = false;

        // Try flows in priority order - first one that passes conditions wins
        for (FlowConfig flow : flows) {
            if (flow == null || flow.getGraph() == null) continue;

            String flowId = flow.getGraph().getId();
            String cooldownKey = "sigil_" + sigilId + "_" + flowId;

            // Get cooldown - prefer START node tier-scaled value, then check for {cooldown} placeholder
            double cooldown = flow.getCooldown();
            com.miracle.arcanesigils.flow.FlowNode startNode = flow.getGraph().getStartNode();
            if (startNode != null) {
                // Check if cooldown uses tier placeholder
                Object cooldownParam = startNode.getParam("cooldown");
                if (cooldownParam != null && cooldownParam.toString().contains("{")) {
                    // Resolve from tier config
                    if (sigil.getTierScalingConfig() != null && sigil.getTierScalingConfig().hasParam("cooldown")) {
                        cooldown = sigil.getTierScalingConfig().getParamValue("cooldown", equippedTier);
                    }
                } else if (cooldownParam != null) {
                    cooldown = startNode.getDoubleParam("cooldown", cooldown);
                }
            }

            // Check cooldown for this specific flow
            if (cooldown > 0 && plugin.getCooldownManager().isOnCooldown(player, cooldownKey)) {
                double remaining = plugin.getCooldownManager().getRemainingCooldown(player, cooldownKey);
                plugin.getLogger().info("[Priority] Flow '" + flowId + "' on cooldown (" + String.format("%.1f", remaining) + "s remaining), trying next");
                anyOnCooldown = true;
                if (remaining > longestRemainingCooldown) {
                    longestRemainingCooldown = remaining;
                }
                // Send cooldown signal for AI training
                AITrainingManager aiTraining = plugin.getAITrainingManager();
                if (aiTraining != null && slotOrId >= 0) {
                    aiTraining.sendCooldownSignal(player, slotOrId, remaining);
                }
                continue; // Try next flow
            }

            // This flow passes! Execute it
            plugin.getLogger().info("[Priority] Flow '" + flowId + "' ACTIVATED - stopping here");
            FlowExecutor executor = new FlowExecutor(plugin);
            FlowContext flowContext = executor.executeWithContext(flow.getGraph(), context);

            // AI Training: Send reward signals based on execution results
            AITrainingManager aiTraining = plugin.getAITrainingManager();
            if (aiTraining != null && slotOrId >= 0) {
                // Track this bind activation for kill attribution
                lastActivatedBindSlot.put(player.getUniqueId(), slotOrId);
                lastActivationTime.put(player.getUniqueId(), System.currentTimeMillis());

                // Check if any effects executed
                if (flowContext != null && flowContext.hasEffectsExecuted()) {
                    // Get accumulated damage/healing from context
                    Object damageObj = flowContext.getVariable("aiTraining_totalDamage");
                    Object healObj = flowContext.getVariable("aiTraining_totalHeal");
                    
                    Double totalDamage = (damageObj instanceof Double) ? (Double) damageObj : null;
                    Double totalHeal = (healObj instanceof Double) ? (Double) healObj : null;

                    if (totalDamage != null && totalDamage > 0) {
                        aiTraining.sendHitSignal(player, slotOrId, totalDamage);
                    } else if (totalHeal != null && totalHeal > 0) {
                        aiTraining.sendHealSignal(player, slotOrId, totalHeal);
                    } else {
                        // Effects executed but no damage/heal - generic success
                        aiTraining.sendHitSignal(player, slotOrId, 0.0);
                    }
                } else {
                    // No effects executed - MISS
                    aiTraining.sendMissSignal(player, slotOrId);
                }
            }

            // Set cooldown - use sigil's display name, not the ugly ID
            // Only set cooldown if flow wasn't cancelled (e.g., missing target)
            if (cooldown > 0 && flowContext != null && !flowContext.isCancelled()) {
                String displayName = TextUtil.stripColors(sigil.getName());
                plugin.getCooldownManager().setCooldown(player, cooldownKey, displayName, cooldown);
            }

            // Award XP
            int tier = equippedTier;
            List<Sigil> equippedSigils = plugin.getSocketManager().getSocketedSigils(equippedItem);
            for (Sigil equipped : equippedSigils) {
                if (equipped.getId().equals(sigilId)) {
                    tier = equipped.getTier();
                    break;
                }
            }
            plugin.getTierProgressionManager().awardXP(player, equippedItem, sigilId, tier);

            return; // IMPORTANT: Stop after first successful activation
        }

        // If we get here, no flow activated - give specific feedback via chat
        String sigilName = TextUtil.stripColors(sigil.getName());
        Component feedback;
        if (anyOnCooldown && !anyConditionsFailed) {
            // Only cooldown issue
            String time = String.format("%.1fs", longestRemainingCooldown);
            feedback = Component.text(sigilName + " ", NamedTextColor.RED)
                .append(Component.text("on cooldown: ", NamedTextColor.GRAY))
                .append(Component.text(time, NamedTextColor.WHITE));
        } else if (anyConditionsFailed && !anyOnCooldown) {
            // Only conditions issue
            feedback = Component.text("Conditions not met: ", NamedTextColor.RED)
                .append(Component.text(sigilName, NamedTextColor.GRAY));
        } else if (anyOnCooldown && anyConditionsFailed) {
            // Both issues - prioritize cooldown message (more actionable)
            String time = String.format("%.1fs", longestRemainingCooldown);
            feedback = Component.text(sigilName + " ", NamedTextColor.RED)
                .append(Component.text("on cooldown: ", NamedTextColor.GRAY))
                .append(Component.text(time, NamedTextColor.WHITE));
        } else {
            // No specific reason found
            feedback = Component.text("Cannot activate ", NamedTextColor.RED)
                .append(Component.text(sigilName, NamedTextColor.GRAY));
        }
        player.sendMessage(feedback);
    }

    /**
     * Find an equipped instance of a sigil (armor or hotbar).
     * Returns the ItemStack containing the sigil, or null if not found.
     */
    private ItemStack findEquippedSigil(Player player, String sigilId) {
        // Check armor slots
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;

            List<Sigil> sigils = plugin.getSocketManager().getSocketedSigils(armor);
            for (Sigil sigil : sigils) {
                if (sigil.getId().equalsIgnoreCase(sigilId)) {
                    return armor;
                }
            }
        }

        // Check hotbar (0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) continue;

            if (!plugin.getSocketManager().isSocketable(item.getType())) continue;

            List<Sigil> sigils = plugin.getSocketManager().getSocketedSigils(item);
            for (Sigil sigil : sigils) {
                if (sigil.getId().equalsIgnoreCase(sigilId)) {
                    return item;
                }
            }
        }

        // Also check off-hand
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && !offHand.getType().isAir() && plugin.getSocketManager().isSocketable(offHand.getType())) {
            List<Sigil> sigils = plugin.getSocketManager().getSocketedSigils(offHand);
            for (Sigil sigil : sigils) {
                if (sigil.getId().equalsIgnoreCase(sigilId)) {
                    return offHand;
                }
            }
        }

        return null;
    }

    // ==================== CLEANUP ====================

    /**
     * Clean up player data on quit.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sneaking.remove(uuid);
        lastSwapTime.remove(uuid);
        lastAttackTime.remove(uuid);
        lastUseTime.remove(uuid);
        lastHeldSlot.remove(uuid);
        lastActivatedBindSlot.remove(uuid);
        lastActivationTime.remove(uuid);

        // Cancel any pending swap task
        Integer pendingTask = pendingSwapTasks.remove(uuid);
        if (pendingTask != null) {
            Bukkit.getScheduler().cancelTask(pendingTask);
        }
    }

    // ==================== AI TRAINING ====================

    /**
     * Get the last activated bind slot for a player (for kill attribution).
     * Returns null if no bind was activated or if it was too long ago.
     *
     * @param uuid Player UUID
     * @param maxAgeMs Maximum age in milliseconds (typically 5000ms for kills)
     * @return The bind slot/ID, or null if not found or too old
     */
    public Integer getLastActivatedBindSlot(UUID uuid, long maxAgeMs) {
        Long activationTime = lastActivationTime.get(uuid);
        if (activationTime == null) return null;

        long age = System.currentTimeMillis() - activationTime;
        if (age > maxAgeMs) return null;

        return lastActivatedBindSlot.get(uuid);
    }
}
