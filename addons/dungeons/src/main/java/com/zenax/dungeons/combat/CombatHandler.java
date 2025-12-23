package com.zenax.dungeons.combat;

import com.zenax.dungeons.dungeon.DungeonInstance;
import com.zenax.dungeons.dungeon.DungeonManager;
import com.zenax.dungeons.stats.StatManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;

/**
 * Handles combat events for dungeon mobs.
 * Intercepts damage events, applies custom calculations, and manages mob deaths.
 */
public class CombatHandler implements Listener {
    private final MobManager mobManager;
    private final StatManager statManager;
    private final DungeonManager dungeonManager;

    /**
     * Creates a new combat handler.
     *
     * @param mobManager The mob manager
     * @param statManager The stat manager
     * @param dungeonManager The dungeon manager
     */
    public CombatHandler(MobManager mobManager, StatManager statManager, DungeonManager dungeonManager) {
        this.mobManager = mobManager;
        this.statManager = statManager;
        this.dungeonManager = dungeonManager;
    }

    /**
     * Handles damage events between players and mobs.
     * Applies custom damage calculations based on stats.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        // Handle projectiles
        if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            if (projectile.getShooter() instanceof Entity) {
                damager = (Entity) projectile.getShooter();
            }
        }

        // Case 1: Player attacks dungeon mob
        if (damager instanceof Player && mobManager.isDungeonMob(victim)) {
            handlePlayerAttackMob(event, (Player) damager, victim);
        }
        // Case 2: Dungeon mob attacks player
        else if (mobManager.isDungeonMob(damager) && victim instanceof Player) {
            handleMobAttackPlayer(event, damager, (Player) victim);
        }
    }

    /**
     * Handles a player attacking a dungeon mob.
     *
     * @param event The damage event
     * @param player The attacking player
     * @param victimEntity The victim mob entity
     */
    private void handlePlayerAttackMob(EntityDamageByEntityEvent event, Player player, Entity victimEntity) {
        DungeonMob mob = mobManager.getMob(victimEntity);
        if (mob == null) {
            return;
        }

        // Get the dungeon instance
        DungeonInstance instance = dungeonManager.getInstance(mob.getDungeonInstanceId());
        if (instance == null) {
            return;
        }

        // Verify player is in the dungeon
        if (!instance.hasPlayer(player)) {
            event.setCancelled(true);
            return;
        }

        // Calculate custom damage
        double baseDamage = event.getDamage();
        double calculatedDamage = DamageCalculator.calculatePlayerDamage(
            player, mob, baseDamage, statManager
        );

        // Apply the new damage
        event.setDamage(calculatedDamage);

        // Send feedback if critical hit
        double critChance = statManager.calculateCritChance(player);
        if (DamageCalculator.rollCritical(critChance)) {
            player.sendMessage(ChatColor.GOLD + "CRITICAL HIT! " +
                             ChatColor.YELLOW + DamageCalculator.formatDamage(calculatedDamage));
        }
    }

    /**
     * Handles a dungeon mob attacking a player.
     *
     * @param event The damage event
     * @param damagerEntity The attacking mob entity
     * @param player The victim player
     */
    private void handleMobAttackPlayer(EntityDamageByEntityEvent event, Entity damagerEntity, Player player) {
        DungeonMob mob = mobManager.getMob(damagerEntity);
        if (mob == null) {
            return;
        }

        // Get the dungeon instance
        DungeonInstance instance = dungeonManager.getInstance(mob.getDungeonInstanceId());
        if (instance == null) {
            return;
        }

        // Verify player is in the dungeon
        if (!instance.hasPlayer(player)) {
            event.setCancelled(true);
            return;
        }

        // Calculate custom damage with defense
        double baseDamage = event.getDamage();
        double calculatedDamage = DamageCalculator.calculateMobDamage(
            mob, player, baseDamage, statManager
        );

        // Apply the new damage
        event.setDamage(calculatedDamage);
    }

    /**
     * Handles entity death events for dungeon mobs.
     * Manages loot drops and checks for room/dungeon completion.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Check if this is a dungeon mob
        if (!mobManager.isDungeonMob(entity)) {
            return;
        }

        DungeonMob mob = mobManager.getMob(entity);
        if (mob == null) {
            return;
        }

        // Mark mob as dead
        mob.setDead();

        // Get dungeon instance
        DungeonInstance instance = dungeonManager.getInstance(mob.getDungeonInstanceId());
        if (instance == null) {
            mobManager.removeMob(entity);
            return;
        }

        // Remove from instance active mobs
        instance.removeActiveMob(entity.getUniqueId());

        // Handle loot (would integrate with loot system here)
        // For now, clear default drops as we'll use custom loot tables
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Broadcast death message to dungeon players
        String deathMessage = mob.getTier().getColor() + mob.getTemplate().getDisplayName() +
                            ChatColor.GRAY + " has been slain!";
        broadcastToDungeon(instance, deathMessage);

        // Check if this was the boss
        if (entity.getUniqueId().equals(instance.getBossUuid())) {
            broadcastToDungeon(instance, ChatColor.GOLD + "BOSS DEFEATED!");
            // Boss completion logic would go here
        }

        // Check for room clear
        checkRoomClear(instance, mob.getRoomId());

        // Remove from manager
        mobManager.removeMob(entity);
    }

    /**
     * Handles entity targeting events.
     * Ensures dungeon mobs only target players in their dungeon instance.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Entity entity = event.getEntity();

        // Check if this is a dungeon mob
        if (!mobManager.isDungeonMob(entity)) {
            return;
        }

        DungeonMob mob = mobManager.getMob(entity);
        if (mob == null) {
            return;
        }

        // Get target
        Entity target = event.getTarget();
        if (!(target instanceof Player)) {
            return;
        }

        Player targetPlayer = (Player) target;

        // Get dungeon instance
        DungeonInstance instance = dungeonManager.getInstance(mob.getDungeonInstanceId());
        if (instance == null) {
            event.setCancelled(true);
            return;
        }

        // Check if target is in the dungeon
        if (!instance.hasPlayer(targetPlayer)) {
            event.setCancelled(true);
            return;
        }
    }

    /**
     * Checks if a room has been cleared of all mobs.
     *
     * @param instance The dungeon instance
     * @param roomId The room ID to check
     */
    private void checkRoomClear(DungeonInstance instance, String roomId) {
        if (instance == null || roomId == null) {
            return;
        }

        // Count remaining mobs in this room
        long remainingMobs = instance.getActiveMobs().stream()
                .map(mobManager::getMob)
                .filter(mob -> mob != null && roomId.equals(mob.getRoomId()))
                .count();

        if (remainingMobs == 0) {
            broadcastToDungeon(instance, ChatColor.GREEN + "Room cleared!");
            // Room clear logic would go here (unlock doors, spawn chest, etc.)
        }
    }

    /**
     * Broadcasts a message to all players in a dungeon instance.
     *
     * @param instance The dungeon instance
     * @param message The message to broadcast
     */
    private void broadcastToDungeon(DungeonInstance instance, String message) {
        if (instance == null || message == null) {
            return;
        }

        for (java.util.UUID playerUuid : instance.getPlayerUuids()) {
            Player player = org.bukkit.Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Gets the mob manager.
     *
     * @return The mob manager
     */
    public MobManager getMobManager() {
        return mobManager;
    }

    /**
     * Gets the stat manager.
     *
     * @return The stat manager
     */
    public StatManager getStatManager() {
        return statManager;
    }

    /**
     * Gets the dungeon manager.
     *
     * @return The dungeon manager
     */
    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }
}
