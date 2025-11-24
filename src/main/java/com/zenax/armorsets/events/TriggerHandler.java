package com.zenax.armorsets.events;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.effects.EffectContext;
import com.zenax.armorsets.sets.ArmorSet;
import com.zenax.armorsets.sets.SetSynergy;
import com.zenax.armorsets.sets.TriggerConfig;
import com.zenax.armorsets.weapons.CustomWeapon;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles all trigger events for armor sets, sigils, and weapons.
 */
public class TriggerHandler implements Listener {

    private final ArmorSetsPlugin plugin;
    private final ConditionManager conditionManager;
    // Track applied effects per player to remove them when armor is unequipped
    private final Map<UUID, Set<PotionEffectType>> appliedEffects = new HashMap<>();
    // Track previous armor state to detect changes
    private final Map<UUID, ItemStack[]> previousArmor = new HashMap<>();
    // Track last equipped set to prevent duplicate unequipped messages
    private final Map<UUID, String> lastEquippedSet = new HashMap<>();

    public TriggerHandler(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.conditionManager = new ConditionManager();
        startStaticEffectTask();
        startArmorCheckTask();
        startTickTriggerTask();
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

        // Track set changes for equipped/unequipped messages
        ArmorSet previousSet = null;
        ArmorSet currentSet = null;

        // Get previous set
        for (int i = 0; i < 4; i++) {
            if (previous[i] != null && !previous[i].getType().isAir()) {
                String setId = plugin.getSetManager().getArmorSetId(previous[i]);
                if (setId != null) {
                    previousSet = plugin.getSetManager().getSet(setId);
                    if (previousSet != null && plugin.getSetManager().hasFullSet(player, previousSet)) {
                        break;
                    }
                }
            }
        }

        boolean armorRemoved = false;
        for (int i = 0; i < 4; i++) {
            ItemStack prev = previous[i];
            ItemStack curr = current[i];

            // Check if armor was removed (had armor before, now empty or different)
            if (prev != null && !prev.getType().isAir()) {
                // Compare only type and set ID, ignore durability changes
                if (curr == null || curr.getType().isAir() || !isSameArmorPiece(prev, curr)) {
                    // Armor piece was removed or changed
                    Sigil prevSigil = plugin.getSocketManager().getSocketedSigil(prev);
                    Sigil currSigil = curr != null ? plugin.getSocketManager().getSocketedSigil(curr) : null;

                    // If previous had a sigil and current doesn't have the same one
                    if (prevSigil != null && (currSigil == null || !prevSigil.getId().equals(currSigil.getId()))) {
                        armorRemoved = true;
                    }

                    // Also check if set armor was removed
                    if (plugin.getSetManager().isSetArmor(prev)) {
                        armorRemoved = true;
                    }
                }
            }
        }

        if (armorRemoved) {
            removeAppliedEffects(player);
            // Send unequipped message only if they previously had a full set and now don't
            String lastSet = lastEquippedSet.get(uuid);
            if (lastSet != null) {
                ArmorSet oldSet = plugin.getSetManager().getSet(lastSet);
                if (oldSet != null && !plugin.getSetManager().hasFullSet(player, oldSet)) {
                    plugin.getSetManager().sendSetMessage(player, oldSet, false);
                    lastEquippedSet.remove(uuid); // Clear so we don't spam messages
                }
            }
        }

        // Check for newly equipped set armor and update lore
        for (int i = 0; i < 4; i++) {
            ItemStack curr = current[i];
            ItemStack prev = previous[i];
            if (curr != null && !curr.getType().isAir()) {
                if (prev == null || prev.getType().isAir() || !isSameArmorPiece(prev, curr)) {
                    updateArmorSetLore(player, curr, i);
                }
            }
        }

        // Check for equipped set message - track when full sets are equipped
        currentSet = plugin.getSetManager().getActiveSet(player);
        String currentSetId = currentSet != null ? currentSet.getId() : null;
        String previousSetId = lastEquippedSet.get(uuid);

        if (currentSet != null && plugin.getSetManager().hasFullSet(player, currentSet)) {
            // Full set just equipped
            if (!currentSetId.equals(previousSetId)) {
                plugin.getSetManager().sendSetMessage(player, currentSet, true);
                lastEquippedSet.put(uuid, currentSetId); // Track as equipped
            }
        } else if (previousSetId != null && currentSetId == null) {
            // No longer wearing any full set - will be handled by armorRemoved block
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
     * Returns true if they're the same armor piece (type + set ID match).
     */
    private boolean isSameArmorPiece(ItemStack prev, ItemStack curr) {
        // Different types = different pieces
        if (prev.getType() != curr.getType()) {
            return false;
        }

        // Same type - check if both are the same set armor
        String prevSetId = plugin.getSetManager().getArmorSetId(prev);
        String currSetId = plugin.getSetManager().getArmorSetId(curr);

        // Both have same set ID (ignores durability)
        if (prevSetId != null && currSetId != null) {
            return prevSetId.equals(currSetId);
        }

        // If neither has a set ID, compare using similar (for non-set armor)
        return prev.isSimilar(curr);
    }

    private void updateArmorSetLore(Player player, ItemStack armor, int slotIndex) {
        String slot = switch (slotIndex) {
            case 0 -> "boots";
            case 1 -> "leggings";
            case 2 -> "chestplate";
            case 3 -> "helmet";
            default -> null;
        };
        if (slot == null) return;

        // Check if armor is from a set via PDC
        String setId = plugin.getSetManager().getArmorSetId(armor);
        if (setId == null) return;

        ArmorSet set = plugin.getSetManager().getSet(setId);
        if (set == null) return;

        // Build and apply lore
        List<String> loreLines = plugin.getSetManager().buildArmorLore(set, slot);
        ItemMeta meta = armor.getItemMeta();
        if (meta == null) return;

        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(TextUtil.parseComponent(line));
        }

        // Only add socket prompt if no sigil is socketed
        boolean hasSocketedSigil = false;
        if (meta.hasLore()) {
            List<Component> existingLore = meta.lore();
            if (existingLore != null) {
                for (Component line : existingLore) {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
                    if (plain.contains("[Sigil]")) {
                        hasSocketedSigil = true;
                        break;
                    }
                }
            }
        }

        if (!hasSocketedSigil) {
            lore.add(Component.empty());
            lore.add(TextUtil.parseComponent("&8Right-click with sigil shard to socket"));
        }

        // Preserve any socketed function lore
        if (meta.hasLore()) {
            List<Component> existingLore = meta.lore();
            if (existingLore != null) {
                boolean inSigilSection = false;
                for (Component line : existingLore) {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);

                    // Look for the sigil section marker
                    if (plain.contains("[Sigil]")) {
                        inSigilSection = true;
                        lore.add(Component.empty());
                        lore.add(line);
                        continue;
                    }

                    // Add all lines after the sigil marker
                    if (inSigilSection) {
                        lore.add(line);
                    }
                }
            }
        }

        meta.lore(lore);
        armor.setItemMeta(meta);
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

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        EffectContext context = EffectContext.builder(player, TriggerType.ATTACK)
                .event(event)
                .victim(victim)
                .damage(event.getDamage())
                .build();

        // Process armor set effects
        processArmorEffects(player, TriggerType.ATTACK, context);

        // Process weapon effects
        processWeaponEffects(player, TriggerType.ATTACK, context);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDefend(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        TriggerType triggerType = event.getCause() == EntityDamageEvent.DamageCause.FALL
                ? TriggerType.FALL_DAMAGE
                : TriggerType.DEFENSE;

        LivingEntity attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof LivingEntity living) {
                attacker = living;
            }
        }

        EffectContext context = EffectContext.builder(player, triggerType)
                .event(event)
                .victim(attacker)
                .damage(event.getDamage())
                .build();

        processArmorEffects(player, triggerType, context);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) return;

        TriggerType triggerType = dead instanceof Player
                ? TriggerType.KILL_PLAYER
                : TriggerType.KILL_MOB;

        EffectContext context = EffectContext.builder(killer, triggerType)
                .victim(dead)
                .build();

        processArmorEffects(killer, triggerType, context);
        processWeaponEffects(killer, triggerType, context);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        EffectContext context = EffectContext.builder(player, TriggerType.SHIFT)
                .event(event)
                .build();

        processArmorEffects(player, TriggerType.SHIFT, context);
        processWeaponEffects(player, TriggerType.SHIFT, context);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileSource source = event.getEntity().getShooter();
        if (!(source instanceof Player player)) return;

        // Determine if this is a bow or trident shot
        String projectileName = event.getEntity().getType().name();
        TriggerType triggerType = projectileName.contains("ARROW") ? TriggerType.BOW_SHOOT :
                                   projectileName.contains("TRIDENT") ? TriggerType.TRIDENT_THROW :
                                   null;
        if (triggerType == null) return;

        EffectContext context = EffectContext.builder(player, triggerType)
                .event(event)
                .build();

        processArmorEffects(player, triggerType, context);
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

        EffectContext context = EffectContext.builder(player, TriggerType.BOW_HIT)
                .event(event)
                .victim(victim)
                .location(event.getHitBlock() != null ? event.getHitBlock().getLocation() :
                         (victim != null ? victim.getLocation() : player.getLocation()))
                .build();

        processArmorEffects(player, TriggerType.BOW_HIT, context);
        processWeaponEffects(player, TriggerType.BOW_HIT, context);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        EffectContext context = EffectContext.builder(player, TriggerType.BLOCK_BREAK)
                .event(event)
                .location(event.getBlock().getLocation())
                .build();

        processArmorEffects(player, TriggerType.BLOCK_BREAK, context);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        EffectContext context = EffectContext.builder(player, TriggerType.BLOCK_PLACE)
                .event(event)
                .location(event.getBlock().getLocation())
                .build();

        processArmorEffects(player, TriggerType.BLOCK_PLACE, context);
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

        EffectContext context = EffectContext.builder(player, TriggerType.INTERACT)
                .event(event)
                .location(event.getClickedBlock() != null ? event.getClickedBlock().getLocation() :
                         player.getLocation())
                .build();

        processArmorEffects(player, TriggerType.INTERACT, context);
        processWeaponEffects(player, TriggerType.INTERACT, context);
    }

    // ==================== TICK TRIGGER ====================

    /**
     * Start task for periodic TICK trigger.
     */
    private void startTickTriggerTask() {
        int interval = plugin.getConfigManager().getMainConfig()
                .getInt("settings.tick-interval", 1); // Default 1 tick

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    EffectContext context = EffectContext.builder(player, TriggerType.TICK).build();
                    processArmorEffects(player, TriggerType.TICK, context);
                }
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    // ==================== PROCESSING METHODS ====================

    /**
     * Process armor set effects for a trigger.
     */
    private void processArmorEffects(Player player, TriggerType triggerType, EffectContext context) {
        // Check for active set
        ArmorSet activeSet = plugin.getSetManager().getActiveSet(player);

        if (activeSet != null) {
            // Process set synergies (only when full set is equipped)
            if (plugin.getSetManager().hasFullSet(player, activeSet)) {
                processSetSynergies(player, activeSet, triggerType, context);
            }
        }

        // Process sigil effects
        processSigilEffects(player, triggerType, context);
    }

    /**
     * Process set synergies (full set bonuses, with cooldowns like functions).
     */
    private void processSetSynergies(Player player, ArmorSet set, TriggerType triggerType, EffectContext context) {
        for (SetSynergy synergy : set.getSynergies()) {
            if (synergy.getTrigger() != triggerType) continue;

            TriggerConfig config = synergy.getTriggerConfig();
            processTriggerConfig(player, config, context, set.getId() + "_synergy_" + synergy.getId());
        }
    }

    /**
     * Process sigil effects - now supports multiple sigils per armor piece.
     */
    private void processSigilEffects(Player player, TriggerType triggerType, EffectContext context) {
        // Check each armor slot for socketed sigils
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;

            // Get ALL socketed sigils on this armor piece
            List<Sigil> sigils = plugin.getSocketManager().getSocketedSigils(armor);
            for (Sigil sigil : sigils) {
                Map<String, TriggerConfig> effects = sigil.getEffects();
                TriggerConfig config = effects.get("on_" + triggerType.getConfigKey().toLowerCase());
                if (config == null) config = effects.get(triggerType.getConfigKey());
                if (config == null) config = effects.get(triggerType.getConfigKey().toLowerCase());
                if (config == null) continue;

                processTriggerConfig(player, config, context, "sigil_" + sigil.getId() + "_" + triggerType.getConfigKey());
            }
        }
    }

    /**
     * Process weapon effects.
     */
    private void processWeaponEffects(Player player, TriggerType triggerType, EffectContext context) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType().isAir()) return;

        CustomWeapon weapon = plugin.getWeaponManager().getWeapon(heldItem);
        if (weapon == null) return;

        // Check set requirement
        if (weapon.getRequiredSet() != null) {
            ArmorSet activeSet = plugin.getSetManager().getActiveSet(player);
            if (activeSet == null) {
                if (triggerType == TriggerType.ATTACK) {
                    player.sendMessage(TextUtil.colorize("&c✗ &cWeapon requires: &d" + weapon.getRequiredSet().replace("_t", " Tier ")));
                }
                return;
            }

            // Check if set matches (handle both exact match and base name match for tiers)
            String requiredBase = weapon.getRequiredSet().toLowerCase().replaceAll("_t\\d+$", "");
            String activeBase = activeSet.getId().toLowerCase().replaceAll("_t\\d+$", "");

            if (!activeSet.getId().equalsIgnoreCase(weapon.getRequiredSet()) && !requiredBase.equals(activeBase)) {
                if (triggerType == TriggerType.ATTACK) {
                    player.sendMessage(TextUtil.colorize("&c✗ &cWeapon requires: &d" + weapon.getRequiredSet().replace("_t", " Tier ")));
                }
                return;
            }
        }

        Map<String, TriggerConfig> events = weapon.getEvents();
        TriggerConfig config = events.get("on_" + triggerType.getConfigKey().toLowerCase());
        if (config == null) config = events.get(triggerType.getConfigKey());
        if (config == null) return;

        processTriggerConfig(player, config, context, "weapon_" + weapon.getId() + "_" + triggerType.getConfigKey());
    }

    /**
     * Process static/passive effects.
     */
    private void processStaticEffects(Player player) {
        EffectContext context = EffectContext.builder(player, TriggerType.EFFECT_STATIC).build();
        processArmorEffects(player, TriggerType.EFFECT_STATIC, context);
    }

    /**
     * Process a trigger config (chance, cooldown, effects).
     */
    private void processTriggerConfig(Player player, TriggerConfig config, EffectContext context, String cooldownKey) {
        // Check cooldown
        if (plugin.getCooldownManager().isOnCooldown(player, cooldownKey)) {
            return;
        }

        // Check chance
        double chance = config.getChance();
        if (chance < 100 && ThreadLocalRandom.current().nextDouble() * 100 > chance) {
            return;
        }

        // Check conditions
        if (!conditionManager.checkConditions(config.getConditions(), context)) {
            return;
        }

        // Execute effects
        List<String> effects = config.getEffects();
        if (effects != null && !effects.isEmpty()) {
            plugin.getEffectManager().executeEffects(effects, context);
        }

        // Set cooldown
        double cooldown = config.getCooldown();
        if (cooldown > 0) {
            plugin.getCooldownManager().setCooldown(player, cooldownKey, cooldown);
        }
    }

}
