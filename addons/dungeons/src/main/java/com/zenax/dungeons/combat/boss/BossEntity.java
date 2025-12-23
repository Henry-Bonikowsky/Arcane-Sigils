package com.zenax.dungeons.combat.boss;

import com.zenax.dungeons.combat.boss.ability.BossAbility;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Represents an active instance of a boss in the world.
 * Manages the boss entity, health, phases, abilities, and boss bar.
 */
public class BossEntity {
    private final String bossId;
    private final Entity entity;
    private final BossTemplate template;
    private int currentPhaseIndex;
    private BossBar bossBar;
    private boolean invulnerable;
    private int invulnerabilityTicks;
    private final Map<String, Long> abilityCooldowns;
    private boolean isDead;

    /**
     * Creates a new boss entity instance.
     *
     * @param entity The Bukkit entity
     * @param template The boss template
     */
    public BossEntity(Entity entity, BossTemplate template) {
        this.bossId = template.getBossId();
        this.entity = entity;
        this.template = template;
        this.currentPhaseIndex = 0;
        this.invulnerable = false;
        this.invulnerabilityTicks = 0;
        this.abilityCooldowns = new HashMap<>();
        this.isDead = false;
    }

    /**
     * Initializes the boss with stats and creates the boss bar.
     *
     * @param difficulty Difficulty multiplier for stats
     */
    public void initialize(double difficulty) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }

        LivingEntity living = (LivingEntity) entity;

        // Apply base stats with difficulty scaling
        double scaledHealth = template.getBaseHealth() * difficulty;
        living.setMaxHealth(scaledHealth);
        living.setHealth(scaledHealth);

        // Set custom name
        living.setCustomName(template.getDisplayName());
        living.setCustomNameVisible(true);

        // Create boss bar
        createBossBar();

        // Apply speed modifier if needed
        double speedModifier = template.getBaseSpeed();
        if (speedModifier != 1.0) {
            // Could apply speed attribute here if needed
        }
    }

    /**
     * Gets the current boss phase based on health.
     *
     * @return The current phase, or null if no phases exist
     */
    public BossPhase getCurrentPhase() {
        if (template.getPhases().isEmpty()) {
            return null;
        }

        double healthPercent = getHealthPercentage();
        BossPhase newPhase = template.getPhaseForHealth(healthPercent);

        // Check if we need to transition to a new phase
        int newPhaseIndex = template.getPhases().indexOf(newPhase);
        if (newPhaseIndex != currentPhaseIndex && newPhaseIndex >= 0) {
            // Phase transition
            currentPhaseIndex = newPhaseIndex;
            onPhaseEnter(newPhase);
        }

        return newPhase;
    }

    /**
     * Advances to the next phase if available.
     */
    public void advancePhase() {
        List<BossPhase> phases = template.getPhases();
        if (currentPhaseIndex < phases.size() - 1) {
            currentPhaseIndex++;
            BossPhase newPhase = phases.get(currentPhaseIndex);
            onPhaseEnter(newPhase);
        }
    }

    /**
     * Called when entering a new phase.
     *
     * @param phase The phase being entered
     */
    private void onPhaseEnter(BossPhase phase) {
        if (phase == null) {
            return;
        }

        // Broadcast enter message if present
        if (phase.hasEnterMessage()) {
            broadcastMessage(phase.getOnEnterMessage());
        }

        // Apply invulnerability
        if (phase.isInvulnerabilityEnabled()) {
            invulnerable = true;
            invulnerabilityTicks = phase.getInvulnerabilityDuration();
        }

        // Handle flying state
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            if (phase.isFlying()) {
                // Enable flying/levitation if the entity type supports it
                // This would need specific implementation based on entity type
            }
        }

        // Update boss bar
        updateBossBar(getHealthPercentage());
    }

    /**
     * Creates the boss bar for this boss.
     */
    public void createBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
        }

        BarColor color = template.getBossBarColor();
        BarStyle style = template.getBossBarStyle();
        String title = template.getDisplayName();

        bossBar = Bukkit.createBossBar(title, color, style);
        bossBar.setVisible(true);
        bossBar.setProgress(1.0);
    }

    /**
     * Updates the boss bar with the current health percentage.
     *
     * @param healthPercent Health percentage (0.0-1.0)
     */
    public void updateBossBar(double healthPercent) {
        if (bossBar != null) {
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, healthPercent)));
        }
    }

    /**
     * Adds a player to see the boss bar.
     *
     * @param player The player to add
     */
    public void addBossBarPlayer(Player player) {
        if (bossBar != null && player != null) {
            bossBar.addPlayer(player);
        }
    }

    /**
     * Removes a player from seeing the boss bar.
     *
     * @param player The player to remove
     */
    public void removeBossBarPlayer(Player player) {
        if (bossBar != null && player != null) {
            bossBar.removePlayer(player);
        }
    }

    /**
     * Uses a boss ability on a target.
     *
     * @param ability The ability to use
     * @param target The target player
     * @return true if the ability was used successfully
     */
    public boolean useAbility(BossAbility ability, Player target) {
        if (ability == null || isDead || invulnerable) {
            return false;
        }

        // Check if ability is on cooldown
        String abilityId = ability.getId();
        long currentTime = System.currentTimeMillis();
        if (abilityCooldowns.containsKey(abilityId)) {
            long cooldownEnd = abilityCooldowns.get(abilityId);
            if (currentTime < cooldownEnd) {
                return false; // Still on cooldown
            }
        }

        // Check if ability can be used
        if (!ability.canUse(this, target)) {
            return false;
        }

        // Execute the ability
        boolean success = ability.execute(this, target);

        if (success) {
            // Set cooldown
            long cooldownDuration = ability.getCooldownTicks() * 50L; // Convert ticks to milliseconds
            abilityCooldowns.put(abilityId, currentTime + cooldownDuration);
        }

        return success;
    }

    /**
     * Ticks the boss entity, updating cooldowns and invulnerability.
     */
    public void tick() {
        if (isDead) {
            return;
        }

        // Update invulnerability
        if (invulnerable && invulnerabilityTicks > 0) {
            invulnerabilityTicks--;
            if (invulnerabilityTicks <= 0) {
                invulnerable = false;
            }
        }

        // Update boss bar
        updateBossBar(getHealthPercentage());

        // Check current phase
        getCurrentPhase();
    }

    /**
     * Gets the health percentage of the boss.
     *
     * @return Health percentage (0.0-1.0)
     */
    public double getHealthPercentage() {
        if (!(entity instanceof LivingEntity)) {
            return 0.0;
        }

        LivingEntity living = (LivingEntity) entity;
        double maxHealth = living.getMaxHealth();
        if (maxHealth <= 0) {
            return 0.0;
        }

        return living.getHealth() / maxHealth;
    }

    /**
     * Marks this boss as dead and cleans up resources.
     */
    public void setDead() {
        this.isDead = true;
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
    }

    /**
     * Checks if this boss is dead.
     *
     * @return true if the boss is dead or the entity is invalid
     */
    public boolean isDead() {
        if (isDead) {
            return true;
        }
        if (entity == null || !entity.isValid()) {
            isDead = true;
            return true;
        }
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            if (living.isDead() || living.getHealth() <= 0) {
                isDead = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Broadcasts a message to all players viewing the boss bar.
     *
     * @param message The message to broadcast
     */
    private void broadcastMessage(String message) {
        if (bossBar != null && message != null && !message.isEmpty()) {
            for (Player player : bossBar.getPlayers()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Gets the location of the boss entity.
     *
     * @return The boss location, or null if invalid
     */
    public Location getLocation() {
        return entity != null && entity.isValid() ? entity.getLocation() : null;
    }

    public String getBossId() {
        return bossId;
    }

    public Entity getEntity() {
        return entity;
    }

    public BossTemplate getTemplate() {
        return template;
    }

    public int getCurrentPhaseIndex() {
        return currentPhaseIndex;
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public boolean isInvulnerable() {
        return invulnerable;
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public Map<String, Long> getAbilityCooldowns() {
        return new HashMap<>(abilityCooldowns);
    }

    @Override
    public String toString() {
        return "BossEntity{" +
               "bossId='" + bossId + '\'' +
               ", phase=" + currentPhaseIndex +
               ", health=" + getHealthPercentage() +
               ", invulnerable=" + invulnerable +
               ", isDead=" + isDead +
               '}';
    }
}
