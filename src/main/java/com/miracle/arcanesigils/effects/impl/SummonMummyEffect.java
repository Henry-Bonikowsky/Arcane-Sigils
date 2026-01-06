package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.effects.StunManager;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Summons mummified zombie guards (husks) that attack a target player.
 * Zombies have high health, fast movement, and can apply Pharaoh's Mark or Curse.
 *
 * Format: SUMMON_MUMMY:duration:count:markChance:curseChance:curseTier @Target
 * Example: SUMMON_MUMMY:10:2:25:5:5 @Victim
 * - 10 second duration
 * - 2 mummies
 * - 25% chance to apply Pharaoh's Mark (slowness)
 * - 5% chance to apply Pharaoh's Curse (stun) at tier 5
 */
public class SummonMummyEffect extends AbstractEffect implements Listener {

    private static final String MUMMY_METADATA_KEY = "pharaoh_mummy";
    private static final String OWNER_METADATA_KEY = "mummy_owner";
    private static final String TARGET_METADATA_KEY = "mummy_target";
    private static final String MARK_CHANCE_KEY = "mummy_mark_chance";
    private static final String CURSE_CHANCE_KEY = "mummy_curse_chance";
    private static final String CURSE_TIER_KEY = "mummy_curse_tier";

    private final Set<UUID> activeMummies = new HashSet<>();
    private final Random random = new Random();
    private boolean listenerRegistered = false;

    public SummonMummyEffect() {
        super("SUMMON_MUMMY", "Summon mummified zombie guards that attack a target");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // SUMMON_MUMMY:duration:count:markChance:curseChance:curseTier - supports both positional and key=value
        params.setDuration(10);
        params.set("count", 2);
        params.set("markChance", 20);
        params.set("curseChance", 5);
        params.set("curseTier", 1);

        int positionalIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    switch (key) {
                        case "duration" -> params.setDuration(parseInt(value, 10));
                        case "count" -> params.set("count", parseInt(value, 2));
                        case "mark_chance", "markchance" -> params.set("markChance", parseInt(value, 20));
                        case "curse_chance", "cursechance" -> params.set("curseChance", parseInt(value, 5));
                        case "curse_tier", "cursetier" -> params.set("curseTier", parseInt(value, 1));
                    }
                }
            } else {
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.setDuration(parseInt(part, 10));
                    case 2 -> params.set("count", parseInt(part, 2));
                    case 3 -> params.set("markChance", parseInt(part, 20));
                    case 4 -> params.set("curseChance", parseInt(part, 5));
                    case 5 -> params.set("curseTier", parseInt(part, 1));
                }
            }
        }

        return params;
    }

    @Override
    public boolean execute(EffectContext context) {
        Player owner = context.getPlayer();
        LivingEntity target = getTarget(context, 30.0); // 30 block range

        // This ability requires a valid target (not self)
        if (target == null || target == owner) {
            // No valid target found - show action bar message and don't execute
            owner.sendActionBar(net.kyori.adventure.text.Component.text(
                TextUtil.colorize("&c&lNo target found! &7Look at an enemy to summon mummies.")
            ));
            return false;
        }

        EffectParams params = context.getParams();
        int duration = params != null ? params.getDuration() : 10;
        int count = params != null ? params.getInt("count", 2) : 2;
        int markChance = params != null ? params.getInt("markChance", 20) : 20;
        int curseChance = params != null ? params.getInt("curseChance", 5) : 5;
        int curseTier = params != null ? params.getInt("curseTier", 1) : 1;

        // Register listener if not already registered
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, getPlugin());
            listenerRegistered = true;
        }

        // Spawn mummies
        Location spawnLoc = owner.getLocation();
        final LivingEntity finalTarget = target;

        for (int i = 0; i < count; i++) {
            // Offset spawn location slightly
            double angle = (2 * Math.PI / count) * i;
            double offsetX = Math.cos(angle) * 1.5;
            double offsetZ = Math.sin(angle) * 1.5;
            Location mummyLoc = spawnLoc.clone().add(offsetX, 0, offsetZ);

            // Spawn husk (desert zombie = mummy)
            Husk mummy = owner.getWorld().spawn(mummyLoc, Husk.class, husk -> {
                // Make it a baby for faster speed and smaller hitbox
                husk.setBaby();

                // Set name
                husk.setCustomName(TextUtil.colorize("§6&lMummified Guard"));
                husk.setCustomNameVisible(true);

                // Set high health (50 HP = 25 hearts)
                AttributeInstance maxHealth = husk.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null) {
                    maxHealth.setBaseValue(50.0);
                    husk.setHealth(50.0);
                }

                // Set fast movement speed (baby zombie speed is ~0.35, default is ~0.23)
                AttributeInstance speed = husk.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speed != null) {
                    speed.setBaseValue(0.35); // Fast like baby zombie
                }

                // Set attack damage
                AttributeInstance damage = husk.getAttribute(Attribute.ATTACK_DAMAGE);
                if (damage != null) {
                    damage.setBaseValue(6.0); // 3 hearts
                }

                // Make it not burn in sunlight (it's undead but magical)
                husk.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 999999, 0, false, false));

                // Apply glowing for visibility
                husk.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration * 20, 0, false, false));

                // Set target
                if (finalTarget != null) {
                    husk.setTarget(finalTarget);
                }

                // Set metadata for tracking
                husk.setMetadata(MUMMY_METADATA_KEY, new FixedMetadataValue(getPlugin(), true));
                husk.setMetadata(OWNER_METADATA_KEY, new FixedMetadataValue(getPlugin(), owner.getUniqueId().toString()));
                if (finalTarget != null) {
                    husk.setMetadata(TARGET_METADATA_KEY, new FixedMetadataValue(getPlugin(), finalTarget.getUniqueId().toString()));
                }
                husk.setMetadata(MARK_CHANCE_KEY, new FixedMetadataValue(getPlugin(), markChance));
                husk.setMetadata(CURSE_CHANCE_KEY, new FixedMetadataValue(getPlugin(), curseChance));
                husk.setMetadata(CURSE_TIER_KEY, new FixedMetadataValue(getPlugin(), curseTier));

                // Prevent item pickup
                husk.setCanPickupItems(false);

                // No equipment drops
                husk.getEquipment().setHelmetDropChance(0);
                husk.getEquipment().setChestplateDropChance(0);
                husk.getEquipment().setLeggingsDropChance(0);
                husk.getEquipment().setBootsDropChance(0);
                husk.getEquipment().setItemInMainHandDropChance(0);
                husk.getEquipment().setItemInOffHandDropChance(0);
            });

            activeMummies.add(mummy.getUniqueId());

            // Spawn particles
            mummy.getWorld().spawnParticle(
                Particle.SOUL,
                mummy.getLocation().add(0, 1, 0),
                20,
                0.3, 0.5, 0.3,
                0.05
            );
        }

        // Start a repeating task to force mummies to target the specified entity
        // This overrides the zombie AI that would otherwise ignore non-player targets
        if (finalTarget != null) {
            final UUID targetUUID = finalTarget.getUniqueId();
            Bukkit.getScheduler().runTaskTimer(getPlugin(), task -> {
                // Stop if target is dead or gone
                Entity targetEntity = Bukkit.getEntity(targetUUID);
                if (targetEntity == null || targetEntity.isDead() || !(targetEntity instanceof LivingEntity)) {
                    task.cancel();
                    return;
                }

                // Stop if no mummies left
                if (activeMummies.isEmpty()) {
                    task.cancel();
                    return;
                }

                // Force each mummy to target the entity
                for (UUID mummyId : new HashSet<>(activeMummies)) {
                    Entity entity = Bukkit.getEntity(mummyId);
                    if (entity instanceof Husk husk && entity.isValid()) {
                        husk.setTarget((LivingEntity) targetEntity);
                    }
                }
            }, 5L, 10L); // Start after 5 ticks, repeat every 10 ticks (0.5 sec)
        }

        // Sound effects
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_HUSK_AMBIENT, 1.0f, 0.8f);
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.8f, 0.5f);
        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_SAND_BREAK, 1.0f, 0.6f);

        // Sand particles
        owner.getWorld().spawnParticle(
            Particle.FALLING_DUST,
            owner.getLocation().add(0, 1, 0),
            60,
            1.5, 1.0, 1.5,
            0.1,
            org.bukkit.Material.SAND.createBlockData()
        );

        // Schedule removal after duration
        BukkitTask removeTask = Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            for (UUID mummyId : new HashSet<>(activeMummies)) {
                Entity entity = Bukkit.getEntity(mummyId);
                if (entity != null && entity.isValid()) {
                    // Death particles
                    entity.getWorld().spawnParticle(
                        Particle.SOUL,
                        entity.getLocation().add(0, 1, 0),
                        30,
                        0.5, 0.5, 0.5,
                        0.1
                    );
                    entity.getWorld().spawnParticle(
                        Particle.FALLING_DUST,
                        entity.getLocation().add(0, 1, 0),
                        40,
                        0.5, 1.0, 0.5,
                        0.1,
                        org.bukkit.Material.SAND.createBlockData()
                    );
                    entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_HUSK_DEATH, 0.8f, 0.8f);
                    entity.remove();
                }
                activeMummies.remove(mummyId);
            }
        }, duration * 20L);

        debug("Summoned " + count + " mummies for " + duration + " seconds targeting " +
              (target != null ? target.getName() : "no one"));
        return true;
    }

    /**
     * Handle mummy attacks - apply Pharaoh's Mark or Curse on hit.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onMummyAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Husk husk)) return;
        if (!husk.hasMetadata(MUMMY_METADATA_KEY)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        // Get effect chances from metadata
        int markChance = 20;
        int curseChance = 5;
        int curseTier = 1;

        if (husk.hasMetadata(MARK_CHANCE_KEY)) {
            markChance = (int) husk.getMetadata(MARK_CHANCE_KEY).get(0).value();
        }
        if (husk.hasMetadata(CURSE_CHANCE_KEY)) {
            curseChance = (int) husk.getMetadata(CURSE_CHANCE_KEY).get(0).value();
        }
        if (husk.hasMetadata(CURSE_TIER_KEY)) {
            curseTier = (int) husk.getMetadata(CURSE_TIER_KEY).get(0).value();
        }

        // Check for Pharaoh's Curse (stun)
        if (random.nextInt(100) < curseChance) {
            applyCurse(victim, curseTier);
            return; // Curse takes priority
        }

        // Check for Pharaoh's Mark (slowness)
        if (random.nextInt(100) < markChance) {
            applyMark(victim);
        }
    }

    /**
     * Apply Pharaoh's Mark (slowness + glowing).
     */
    private void applyMark(Player victim) {
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, true)); // 5 sec
        victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, true));

        victim.getWorld().spawnParticle(
            Particle.DUST,
            victim.getLocation().add(0, 1, 0),
            20,
            0.5, 0.5, 0.5,
            0.1,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 215, 0), 1.0f)
        );

        victim.sendMessage(TextUtil.colorize("§6&lPharaoh's Mark! §7You have been marked!"));
    }

    /**
     * Apply Pharaoh's Curse (stun).
     */
    private void applyCurse(Player victim, int tier) {
        StunManager stunManager = getPlugin().getStunManager();
        if (stunManager != null) {
            // Duration based on tier (0.5 + tier * 0.5)
            double duration = 0.5 + (tier * 0.5);
            stunManager.stunPlayer(victim, duration);
        } else {
            // Fallback to potion effects if stun manager not available
            int ticks = (int) (20 + (tier * 10)); // 1-3 seconds
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 254, false, true));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, true));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, false, true));
        }

        victim.getWorld().spawnParticle(
            Particle.FALLING_DUST,
            victim.getLocation().add(0, 1, 0),
            40,
            0.5, 1.0, 0.5,
            0.1,
            org.bukkit.Material.SAND.createBlockData()
        );

        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_SAND_BREAK, 1.0f, 0.5f);
        victim.sendMessage(TextUtil.colorize("§6&lPharaoh's Curse! §7You have been stunned!"));
    }

    /**
     * Clean up when mummy dies.
     */
    @EventHandler
    public void onMummyDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Husk husk)) return;
        if (!husk.hasMetadata(MUMMY_METADATA_KEY)) return;

        activeMummies.remove(husk.getUniqueId());

        // Clear drops (mummies don't drop loot)
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Death particles
        husk.getWorld().spawnParticle(
            Particle.SOUL,
            husk.getLocation().add(0, 1, 0),
            15,
            0.3, 0.5, 0.3,
            0.05
        );
    }
}
