package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.effects.BehaviorManager;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.utils.TextUtil;
import com.miracle.arcanesigils.utils.LogHelper;
import com.miracle.arcanesigils.binds.TargetGlowManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highly configurable entity spawning effect.
 *
 * Basic Format: SPAWN_ENTITY:TYPE:COUNT:DURATION @Target
 *
 * Extended Format with parameters:
 * SPAWN_ENTITY:TYPE:COUNT:DURATION:PARAM1=VALUE:PARAM2=VALUE @Target
 *
 * Available Parameters:
 * ======================
 *
 * STATS:
 * - hp=50              Max health (default: entity default)
 * - speed=0.35         Movement speed (default: entity default)
 * - damage=6           Attack damage (default: entity default)
 * - attack_speed=8.0   Attack speed - higher = faster attacks (default: 4.0)
 * - knockback_resist=0.5  Knockback resistance 0-1 (default: 0)
 *
 * EQUIPMENT:
 * - helmet=DIAMOND_HELMET       Helmet material
 * - chestplate=IRON_CHESTPLATE  Chestplate material
 * - leggings=LEATHER_LEGGINGS   Leggings material
 * - boots=GOLDEN_BOOTS          Boots material
 * - mainhand=DIAMOND_SWORD      Main hand item
 * - offhand=SHIELD              Off hand item
 * - drop_equipment=false        Whether equipment drops (default: false)
 *
 * TARGETING:
 * - target=VICTIM       Target mode: VICTIM, NEARBY, OWNER_TARGET, NONE
 * - target_range=10     Range for NEARBY targeting (default: 10)
 * - exclude_owner=true  Don't target the summoner (default: true)
 * - force_target=true   Keep re-targeting every 0.5s (default: true)
 *
 * APPEARANCE:
 * - name=&6Mummy        Custom name (supports color codes)
 * - name_visible=true   Show name above head (default: true if name set)
 * - baby=false          Make baby version (default: false)
 * - glowing=true        Apply glowing effect (default: false)
 * - silent=false        No sounds (default: false)
 * - fire_immune=true    Fire resistance (default: false)
 * - no_ai=false         Disable AI (default: false)
 *
 * POTION EFFECTS:
 * - effects=SPEED:100:1,STRENGTH:100:0   Comma-separated TYPE:DURATION:AMP
 *
 * ON-HIT BEHAVIOR:
 * NOTE: Use ~ instead of : for sub-params, and / instead of , for lists
 * - on_hit_mark=PHARAOH_MARK~5   Mark to apply on hit (NAME~DURATION)
 * - on_hit_mark_chance=25        Chance to apply mark (default: 100)
 * - on_hit_effects=SLOWNESS~60~1/GLOWING~60~0  Potion effects on hit
 * - on_hit_effect_chance=50      Chance to apply on-hit effects (default: 100)
 * - on_hit_stun=2                Stun duration on hit (0 = disabled)
 * - on_hit_stun_chance=5         Chance to stun (default: 100)
 *
 * DEATH BEHAVIOR:
 * NOTE: Use ~ instead of : for sub-params
 * - no_drops=true                Clear drops (default: true)
 * - death_particle=SOUL~20       Particle on death (TYPE~COUNT)
 * - death_sound=ENTITY_HUSK_DEATH~0.8~1.0  Sound on death (SOUND~VOL~PITCH)
 *
 * Examples:
 * - SPAWN_ENTITY:ZOMBIE:3:10 @Self
 *   Spawns 3 zombies for 10 seconds at player location
 *
 * - SPAWN_ENTITY:HUSK:2:10:hp=50:speed=0.35:name=&6Mummy:target=VICTIM @Victim
 *   Spawns 2 custom mummies that chase the victim
 *
 * - SPAWN_ENTITY:WOLF:1:30:target=OWNER_TARGET:name=&bSpirit Wolf:glowing=true @Self
 *   Spawns a glowing wolf that attacks whatever you attack
 */
public class SpawnEntityEffect extends AbstractEffect implements Listener {

    // Metadata keys
    private static final String SPAWNED_ENTITY_KEY = "arcanesigils_spawned";
    private static final String OWNER_KEY = "spawned_owner";
    private static final String TARGET_MODE_KEY = "spawned_target_mode";
    private static final String EXCLUDE_OWNER_KEY = "spawned_exclude_owner";
    private static final String ON_HIT_MARK_KEY = "spawned_on_hit_mark";
    private static final String ON_HIT_MARK_DURATION_KEY = "spawned_on_hit_mark_duration";
    private static final String ON_HIT_MARK_CHANCE_KEY = "spawned_on_hit_mark_chance";
    private static final String ON_HIT_EFFECTS_KEY = "spawned_on_hit_effects";
    private static final String ON_HIT_EFFECT_CHANCE_KEY = "spawned_on_hit_effect_chance";
    private static final String ON_HIT_STUN_KEY = "spawned_on_hit_stun";
    private static final String ON_HIT_STUN_CHANCE_KEY = "spawned_on_hit_stun_chance";
    private static final String NO_DROPS_KEY = "spawned_no_drops";
    private static final String DEATH_PARTICLE_KEY = "spawned_death_particle";
    private static final String DEATH_PARTICLE_COUNT_KEY = "spawned_death_particle_count";
    private static final String DEATH_SOUND_KEY = "spawned_death_sound";

    // Active entities tracking
    private final Map<UUID, Set<UUID>> ownerToEntities = new ConcurrentHashMap<>();
    private final Set<UUID> allSpawnedEntities = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();
    private boolean listenerRegistered = false;

    public SpawnEntityEffect() {
        super("SPAWN_ENTITY", "Spawns highly configurable entities");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = new EffectParams(id);

        // Remove target selector
        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();

        // Parse target selector
        if (effectString.contains("@")) {
            String[] spaceParts = effectString.split("\\s+");
            for (String part : spaceParts) {
                if (part.startsWith("@")) {
                    params.setTarget(part);
                    break;
                }
            }
        }

        // Split by colons
        String[] parts = cleanedString.split(":");

        // Parse all parts - support both positional and key=value format
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];

            if (part.contains("=")) {
                // Key=value format
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];

                    // Handle basic params that can also come as key=value
                    switch (key) {
                        case "entity_type" -> params.set("entity_type", value.toUpperCase());
                        case "count" -> params.set("count", parseInt(value, 1));
                        case "duration" -> params.setDuration(parseInt(value, 10));
                        default -> parseKeyValue(params, key, value);
                    }
                }
            } else {
                // Positional format: SPAWN_ENTITY:TYPE:COUNT:DURATION
                switch (i) {
                    case 1 -> params.set("entity_type", part.toUpperCase());
                    case 2 -> params.set("count", parseInt(part, 1));
                    case 3 -> params.setDuration(parseInt(part, 10));
                }
            }
        }

        return params;
    }

    private void parseKeyValue(EffectParams params, String key, String value) {
        switch (key) {
            // Stats
            case "hp" -> params.set("hp", parseDouble(value, 20));
            case "speed" -> params.set("speed", parseDouble(value, 0.23));
            case "damage" -> params.set("damage", parseDouble(value, 2));
            case "knockback_resist" -> params.set("knockback_resist", parseDouble(value, 0));

            // Equipment
            case "helmet" -> params.set("helmet", value.toUpperCase());
            case "chestplate" -> params.set("chestplate", value.toUpperCase());
            case "leggings" -> params.set("leggings", value.toUpperCase());
            case "boots" -> params.set("boots", value.toUpperCase());
            case "mainhand" -> params.set("mainhand", value.toUpperCase());
            case "offhand" -> params.set("offhand", value.toUpperCase());
            case "drop_equipment" -> params.set("drop_equipment", Boolean.parseBoolean(value));

            // Targeting
            case "target" -> params.set("target_mode", value.toUpperCase());
            case "target_range" -> params.set("target_range", parseDouble(value, 10));
            case "exclude_owner" -> params.set("exclude_owner", Boolean.parseBoolean(value));
            case "force_target" -> params.set("force_target", Boolean.parseBoolean(value));

            // Appearance
            case "name" -> params.set("custom_name", value);
            case "name_visible" -> params.set("name_visible", Boolean.parseBoolean(value));
            case "baby" -> params.set("baby", Boolean.parseBoolean(value));
            case "glowing" -> params.set("glowing", Boolean.parseBoolean(value));
            case "silent" -> params.set("silent", Boolean.parseBoolean(value));
            case "fire_immune" -> params.set("fire_immune", Boolean.parseBoolean(value));
            case "no_ai" -> params.set("no_ai", Boolean.parseBoolean(value));

            // Potion effects (format: TYPE:DURATION:AMP,TYPE:DURATION:AMP)
            case "effects" -> params.set("potion_effects", value);

            // On-hit behavior (use ~ instead of : for sub-params, / instead of ,)
            // e.g., on_hit_mark=PHARAOH_MARK~5 means PHARAOH_MARK with 5s duration
            case "on_hit_mark" -> params.set("on_hit_mark", value.replace('~', ':'));
            case "on_hit_mark_chance" -> params.set("on_hit_mark_chance", parseInt(value, 100));
            // e.g., on_hit_effects=SLOWNESS~100~1/GLOWING~100~0
            case "on_hit_effects" -> params.set("on_hit_effects", value.replace('~', ':').replace('/', ','));
            case "on_hit_effect_chance" -> params.set("on_hit_effect_chance", parseInt(value, 100));
            case "on_hit_stun" -> params.set("on_hit_stun", parseDouble(value, 0));
            case "on_hit_stun_chance" -> params.set("on_hit_stun_chance", parseInt(value, 100));

            // Death behavior (use ~ instead of : for sub-params)
            // e.g., death_particle=SOUL~30, death_sound=ENTITY_HUSK_DEATH~0.8~0.8
            case "no_drops" -> params.set("no_drops", Boolean.parseBoolean(value));
            case "death_particle" -> params.set("death_particle", value.replace('~', ':'));
            case "death_sound" -> params.set("death_sound", value.replace('~', ':'));

            // Behavior sigil (replaces on-hit handling with full sigil system)
            case "behavior" -> params.set("behavior", value);
            
            // Fast attack speed multiplier
            case "fast_attack_speed" -> params.set("fast_attack_speed", parseDouble(value, 2.0));
        }
    }


    @Override
    public boolean execute(EffectContext context) {
        Player owner = context.getPlayer();
        EffectParams params = context.getParams();
        if (params == null) params = new EffectParams(id);

        // Basic params
        String entityTypeName = params.getString("entity_type", "ZOMBIE");
        int count = params.getInt("count", 1);
        int duration = params.getDuration() > 0 ? params.getDuration() : 10;

        // Cap count from config
        int maxCount = getPlugin().getConfigManager().getMainConfig()
                .getInt("effects.max-spawned-entities", 10);
        count = Math.min(count, maxCount);

        LogHelper.debug("[SpawnEntity] Starting spawn: type=%s, count=%d, duration=%d, target_mode=%s", 
            entityTypeName, count, duration, params.getString("target_mode", "VICTIM"));

        // Get entity type
        EntityType entityType = getEntityType(entityTypeName);
        if (entityType == null) {
            debug("Unknown entity type: " + entityTypeName);
            return false;
        }

        // Get spawn location
        Location spawnLoc = getTargetLocation(context);
        if (spawnLoc == null) spawnLoc = owner.getLocation();

        // Register listener if needed
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, getPlugin());
            listenerRegistered = true;
        }

        // Determine target
        LivingEntity targetEntity = determineTarget(context, params);

        // If target mode is OWNER_TARGET, only spawn if target is a Player
        String targetMode = params.getString("target_mode", "VICTIM");
        if (targetMode.equalsIgnoreCase("OWNER_TARGET") || targetMode.equalsIgnoreCase("TARGET")) {
            if (!(targetEntity instanceof Player)) {
                LogHelper.debug("[SpawnEntity] OWNER_TARGET mode requires Player target. Target is %s. Not spawning.",
                    targetEntity != null ? targetEntity.getClass().getSimpleName() : "null");
                // Set error to stop flow and show message to player
                if (context.getFlowContext() != null) {
                    String abilityName = context.getSigilId() != null ? context.getSigilId() : "Ability";
                    context.getFlowContext().setError(abilityName + " requires a target!");
                }
                return false;
            }
            LogHelper.debug("[SpawnEntity] OWNER_TARGET mode: Valid player target '%s' found. Proceeding with spawn.",
                ((Player) targetEntity).getName());
        }

        // Get behavior sigil if specified
        String behaviorId = params.getString("behavior", null);
        Sigil behaviorSigil = null;
        if (behaviorId != null && !behaviorId.isEmpty()) {
            behaviorSigil = getPlugin().getSigilManager().getBehavior(behaviorId);
            if (behaviorSigil == null) {
                debug("Behavior not found: " + behaviorId + ", using hardcoded effects");
            }
        }

        // Spawn entities
        List<Entity> spawned = new ArrayList<>();
        BehaviorManager behaviorManager = getPlugin().getBehaviorManager();
        for (int i = 0; i < count; i++) {
            // Offset spawn slightly for multiple entities
            double angle = (2 * Math.PI / count) * i;
            double offsetX = count > 1 ? Math.cos(angle) * 1.5 : 0;
            double offsetZ = count > 1 ? Math.sin(angle) * 1.5 : 0;
            Location entityLoc = spawnLoc.clone().add(offsetX, 0, offsetZ);

            Entity entity = spawnAndConfigure(entityLoc, entityType, owner, targetEntity, params, duration, behaviorSigil != null);
            if (entity != null) {
                spawned.add(entity);
                allSpawnedEntities.add(entity.getUniqueId());
                ownerToEntities.computeIfAbsent(owner.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                        .add(entity.getUniqueId());

                LogHelper.debug("[SpawnEntity] Spawned entity #%d: type=%s, UUID=%s", 
                    i+1, entity.getType(), entity.getUniqueId());

                // Register with BehaviorManager if behavior sigil is specified
                if (behaviorSigil != null && behaviorManager != null) {
                    // Parse and resolve behavior_params
                    Map<String, Object> behaviorParams = parseBehaviorParams(params, context);

                    LogHelper.debug("[SpawnEntity] Registering entity with BehaviorManager: behavior=%s, duration=%d, params=%s",
                        behaviorId, duration, behaviorParams);
                    behaviorManager.registerEntity(entity, behaviorSigil, owner.getUniqueId(), duration, behaviorParams);
                }
            }
        }

        if (spawned.isEmpty()) return false;

        LogHelper.debug("[SpawnEntity] Successfully spawned %d entities. Initial target=%s (UUID=%s)", 
            spawned.size(),
            targetEntity != null ? targetEntity.getName() : "none",
            targetEntity != null ? targetEntity.getUniqueId() : "none");

        // Set up force targeting if enabled
        boolean forceTarget = params.getBoolean("force_target", true);
        // targetMode already declared earlier
        if (forceTarget && targetEntity != null && !targetMode.equals("NONE")) {
            LogHelper.debug("[SpawnEntity] Setting up force targeting: mode=%s, force=%s", 
                targetMode, forceTarget);
            setupForceTargeting(spawned, targetEntity, owner, params);
        } else {
            LogHelper.debug("[SpawnEntity] Force targeting disabled or no target: force=%s, target=%s, mode=%s", 
                forceTarget, targetEntity != null ? "exists" : "none", targetMode);
        }

        // Schedule removal after duration
        if (duration > 0) {
            scheduleRemoval(spawned, params, duration);
        }

        debug("Spawned " + spawned.size() + " " + entityTypeName + " for " + duration + "s");
        return true;
    }

    private Entity spawnAndConfigure(Location loc, EntityType type, Player owner,
                                     LivingEntity target, EffectParams params, int duration, boolean useBehavior) {
        try {
            Entity entity = loc.getWorld().spawnEntity(loc, type);

            // Set metadata
            entity.setMetadata(SPAWNED_ENTITY_KEY, new FixedMetadataValue(getPlugin(), true));
            entity.setMetadata(OWNER_KEY, new FixedMetadataValue(getPlugin(), owner.getUniqueId().toString()));
            entity.setMetadata(TARGET_MODE_KEY, new FixedMetadataValue(getPlugin(),
                    params.getString("target_mode", "VICTIM")));
            entity.setMetadata(EXCLUDE_OWNER_KEY, new FixedMetadataValue(getPlugin(),
                    params.getBoolean("exclude_owner", true)));
            entity.setMetadata(NO_DROPS_KEY, new FixedMetadataValue(getPlugin(),
                    params.getBoolean("no_drops", true)));

            // On-hit configs only if not using behavior sigil (behavior handles this via BehaviorManager)
            if (!useBehavior) {
                // On-hit mark config
                String onHitMark = params.getString("on_hit_mark", null);
                if (onHitMark != null && !onHitMark.isEmpty()) {
                    String[] markParts = onHitMark.split(":");
                    entity.setMetadata(ON_HIT_MARK_KEY, new FixedMetadataValue(getPlugin(), markParts[0]));
                    if (markParts.length > 1) {
                        entity.setMetadata(ON_HIT_MARK_DURATION_KEY, new FixedMetadataValue(getPlugin(),
                                parseDouble(markParts[1], 5)));
                    }
                }
                entity.setMetadata(ON_HIT_MARK_CHANCE_KEY, new FixedMetadataValue(getPlugin(),
                        params.getInt("on_hit_mark_chance", 100)));

                // On-hit effects config
                String onHitEffects = params.getString("on_hit_effects", null);
                if (onHitEffects != null && !onHitEffects.isEmpty()) {
                    entity.setMetadata(ON_HIT_EFFECTS_KEY, new FixedMetadataValue(getPlugin(), onHitEffects));
                }
                entity.setMetadata(ON_HIT_EFFECT_CHANCE_KEY, new FixedMetadataValue(getPlugin(),
                        params.getInt("on_hit_effect_chance", 100)));

                // On-hit stun config
                double onHitStun = params.getDouble("on_hit_stun", 0);
                if (onHitStun > 0) {
                    entity.setMetadata(ON_HIT_STUN_KEY, new FixedMetadataValue(getPlugin(), onHitStun));
                    entity.setMetadata(ON_HIT_STUN_CHANCE_KEY, new FixedMetadataValue(getPlugin(),
                            params.getInt("on_hit_stun_chance", 100)));
                }
            }

            // Death particle config
            String deathParticle = params.getString("death_particle", null);
            if (deathParticle != null) {
                String[] particleParts = deathParticle.split(":");
                entity.setMetadata(DEATH_PARTICLE_KEY, new FixedMetadataValue(getPlugin(), particleParts[0]));
                if (particleParts.length > 1) {
                    entity.setMetadata(DEATH_PARTICLE_COUNT_KEY, new FixedMetadataValue(getPlugin(),
                            parseInt(particleParts[1], 20)));
                }
            }

            // Death sound config
            String deathSound = params.getString("death_sound", null);
            if (deathSound != null) {
                entity.setMetadata(DEATH_SOUND_KEY, new FixedMetadataValue(getPlugin(), deathSound));
            }

            if (entity instanceof LivingEntity living) {
                configureLivingEntity(living, owner, target, params, duration);
            }

            if (entity instanceof Mob mob && target != null) {
                mob.setTarget(target);
            }

            return entity;
        } catch (Exception e) {
            debug("Failed to spawn entity: " + e.getMessage());
            return null;
        }
    }

    private void configureLivingEntity(LivingEntity living, Player owner, LivingEntity target,
                                       EffectParams params, int duration) {
        // === DEBUG: Dump all params for troubleshooting ===
        debug("=== SpawnEntityEffect params debug ===");
        debug("Entity type: " + living.getType());
        debug("Params: " + params);
        debug("Has 'damage' key: " + params.has("damage") + ", raw value: " + params.get("damage"));
        debug("Has 'speed' key: " + params.has("speed") + ", raw value: " + params.get("speed"));
        debug("Has 'hp' key: " + params.has("hp") + ", raw value: " + params.get("hp"));

        // === STATS (always apply - GUI may use defaults) ===

        // Max Health
        AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            double hp = params.getDouble("hp", 20);
            debug(String.format("Setting max health to %.1f (has param: %b)", hp, params.has("hp")));
            maxHealth.setBaseValue(hp);
            living.setHealth(hp);
        }

        // Movement Speed - clear modifiers first to prevent equipment from overriding
        AttributeInstance speedAttr = living.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            double speedValue = params.getDouble("speed", 0.23);
            debug(String.format("Setting movement speed to %.3f (has param: %b)", speedValue, params.has("speed")));
            // Clear any existing modifiers that could override our speed
            speedAttr.getModifiers().forEach(speedAttr::removeModifier);
            speedAttr.setBaseValue(speedValue);
        } else {
            debug("WARNING: Entity " + living.getType() + " has no MOVEMENT_SPEED attribute!");
        }

        // Attack Damage - clear modifiers first to prevent equipment from overriding
        AttributeInstance damage = living.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            double damageValue = params.getDouble("damage", 3); // Default to 3 (Husk default) instead of 2
            debug(String.format("Setting attack damage to %.1f (has param: %b)", damageValue, params.has("damage")));
            // Clear any existing modifiers that could override our damage
            damage.getModifiers().forEach(damage::removeModifier);
            damage.setBaseValue(damageValue);
        } else {
            debug("WARNING: Entity " + living.getType() + " has no ATTACK_DAMAGE attribute!");
        }

        // Attack Speed - clear modifiers first to prevent equipment from overriding
        AttributeInstance attackSpeed = living.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeed != null) {
            double attackSpeedValue = params.getDouble("attack_speed", 4.0); // Default zombie attack speed is 4.0
            debug(String.format("Setting attack speed to %.1f (has param: %b)", attackSpeedValue, params.has("attack_speed")));
            // Clear any existing modifiers that could override our attack speed
            attackSpeed.getModifiers().forEach(attackSpeed::removeModifier);
            attackSpeed.setBaseValue(attackSpeedValue);
        } else {
            debug("WARNING: Entity " + living.getType() + " has no ATTACK_SPEED attribute!");
        }

        // Knockback Resistance
        AttributeInstance kbResist = living.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kbResist != null) {
            kbResist.setBaseValue(params.getDouble("knockback_resist", 0));
        }

        // === EQUIPMENT ===
        EntityEquipment equipment = living.getEquipment();
        if (equipment != null) {
            boolean dropEquipment = params.getBoolean("drop_equipment", false);

            if (params.has("helmet")) {
                equipment.setHelmet(createItem(params.getString("helmet", null)));
                equipment.setHelmetDropChance(dropEquipment ? 1.0f : 0);
            } else {
                equipment.setHelmetDropChance(0);
            }

            if (params.has("chestplate")) {
                equipment.setChestplate(createItem(params.getString("chestplate", null)));
                equipment.setChestplateDropChance(dropEquipment ? 1.0f : 0);
            } else {
                equipment.setChestplateDropChance(0);
            }

            if (params.has("leggings")) {
                equipment.setLeggings(createItem(params.getString("leggings", null)));
                equipment.setLeggingsDropChance(dropEquipment ? 1.0f : 0);
            } else {
                equipment.setLeggingsDropChance(0);
            }

            if (params.has("boots")) {
                equipment.setBoots(createItem(params.getString("boots", null)));
                equipment.setBootsDropChance(dropEquipment ? 1.0f : 0);
            } else {
                equipment.setBootsDropChance(0);
            }

            if (params.has("mainhand")) {
                equipment.setItemInMainHand(createItem(params.getString("mainhand", null)));
                equipment.setItemInMainHandDropChance(dropEquipment ? 1.0f : 0);
            } else {
                equipment.setItemInMainHandDropChance(0);
            }

            if (params.has("offhand")) {
                equipment.setItemInOffHand(createItem(params.getString("offhand", null)));
                equipment.setItemInOffHandDropChance(dropEquipment ? 1.0f : 0);
            } else {
                equipment.setItemInOffHandDropChance(0);
            }
        }

        // === APPEARANCE ===

        // Custom Name
        String customName = params.getString("custom_name", null);
        if (customName != null && !customName.isEmpty()) {
            living.setCustomName(TextUtil.colorize(customName));
            living.setCustomNameVisible(params.getBoolean("name_visible", true));
        }

        // Baby
        if (params.getBoolean("baby", false)) {
            if (living instanceof Ageable ageable) {
                ageable.setBaby();
            } else if (living instanceof org.bukkit.entity.Zombie zombie) {
                zombie.setBaby(true);
            }
        }

        // Silent
        if (params.getBoolean("silent", false)) {
            living.setSilent(true);
        }

        // No AI
        if (params.getBoolean("no_ai", false) && living instanceof Mob mob) {
            mob.setAI(false);
        }

        // Prevent item pickup
        if (living instanceof Mob mob) {
            mob.setCanPickupItems(false);
        }

        // Re-apply attributes with a delay to ensure entity is fully initialized
        // Minecraft's entity initialization can override our values
        final double finalSpeedValue = params.getDouble("speed", 0.23);
        final double finalDamageValue = params.getDouble("damage", 3);
        final double finalAttackSpeedValue = params.getDouble("attack_speed", 4.0);

        // First pass: 1 tick delay
        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            if (living.isValid() && !living.isDead()) {
                // Re-apply movement speed
                AttributeInstance finalSpeed = living.getAttribute(Attribute.MOVEMENT_SPEED);
                if (finalSpeed != null) {
                    finalSpeed.getModifiers().forEach(finalSpeed::removeModifier);
                    finalSpeed.setBaseValue(finalSpeedValue);
                    debug(String.format("Applied movement speed %.3f (1-tick delay)", finalSpeedValue));
                }

                // Re-apply attack damage
                AttributeInstance finalDamage = living.getAttribute(Attribute.ATTACK_DAMAGE);
                if (finalDamage != null) {
                    finalDamage.getModifiers().forEach(finalDamage::removeModifier);
                    finalDamage.setBaseValue(finalDamageValue);
                    debug(String.format("Applied attack damage %.1f (1-tick delay)", finalDamageValue));
                }

                // Re-apply attack speed
                AttributeInstance finalAttackSpeed = living.getAttribute(Attribute.ATTACK_SPEED);
                if (finalAttackSpeed != null) {
                    finalAttackSpeed.getModifiers().forEach(finalAttackSpeed::removeModifier);
                    finalAttackSpeed.setBaseValue(finalAttackSpeedValue);
                    debug(String.format("Applied attack speed %.1f (1-tick delay)", finalAttackSpeedValue));
                }
            }
        }, 1L);

        // Second pass: 5 tick delay (some entities need more time to fully initialize)
        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            if (living.isValid() && !living.isDead()) {
                // Re-apply movement speed
                AttributeInstance finalSpeed = living.getAttribute(Attribute.MOVEMENT_SPEED);
                if (finalSpeed != null) {
                    double currentSpeed = finalSpeed.getBaseValue();
                    if (Math.abs(currentSpeed - finalSpeedValue) > 0.001) {
                        debug(String.format("Speed was reset to %.3f, re-applying %.3f", currentSpeed, finalSpeedValue));
                        finalSpeed.getModifiers().forEach(finalSpeed::removeModifier);
                        finalSpeed.setBaseValue(finalSpeedValue);
                    }
                }

                // Re-apply attack damage
                AttributeInstance finalDamage = living.getAttribute(Attribute.ATTACK_DAMAGE);
                if (finalDamage != null) {
                    double currentDamage = finalDamage.getBaseValue();
                    if (Math.abs(currentDamage - finalDamageValue) > 0.1) {
                        debug(String.format("Damage was reset to %.1f, re-applying %.1f", currentDamage, finalDamageValue));
                        finalDamage.getModifiers().forEach(finalDamage::removeModifier);
                        finalDamage.setBaseValue(finalDamageValue);
                    }
                }

                // Re-apply attack speed
                AttributeInstance finalAttackSpeed = living.getAttribute(Attribute.ATTACK_SPEED);
                if (finalAttackSpeed != null) {
                    double currentAttackSpeed = finalAttackSpeed.getBaseValue();
                    if (Math.abs(currentAttackSpeed - finalAttackSpeedValue) > 0.1) {
                        debug(String.format("Attack speed was reset to %.1f, re-applying %.1f", currentAttackSpeed, finalAttackSpeedValue));
                        finalAttackSpeed.getModifiers().forEach(finalAttackSpeed::removeModifier);
                        finalAttackSpeed.setBaseValue(finalAttackSpeedValue);
                    }
                }

                // Debug: log final attribute values
                debug("=== Final entity attributes (5-tick) ===");
                AttributeInstance spd = living.getAttribute(Attribute.MOVEMENT_SPEED);
                AttributeInstance dmg = living.getAttribute(Attribute.ATTACK_DAMAGE);
                AttributeInstance atkSpd = living.getAttribute(Attribute.ATTACK_SPEED);
                if (spd != null) debug("Final movement speed: " + spd.getBaseValue());
                if (dmg != null) debug("Final attack damage: " + dmg.getBaseValue());
                if (atkSpd != null) debug("Final attack speed: " + atkSpd.getBaseValue());
                
                // === FAST ATTACK SPEED (NMS) ===
                if (params.has("fast_attack_speed") && living instanceof Mob mob) {
                    double multiplier = params.getDouble("fast_attack_speed", 2.0);
                    com.miracle.arcanesigils.nms.FastAttackHelper.setFastAttackSpeed(mob, multiplier, getPlugin());
                    debug(String.format("Applied fast attack speed: %.1fx multiplier", multiplier));
                }
            }
        }, 5L);

        // === POTION EFFECTS ===

        // Fire Immune
        if (params.getBoolean("fire_immune", false)) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                    duration > 0 ? duration * 20 : 999999, 0, false, false));
        }

        // Glowing
        if (params.getBoolean("glowing", false)) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                    duration > 0 ? duration * 20 : 999999, 0, false, false));
        }

        // Custom potion effects
        String effectsStr = params.getString("potion_effects", null);
        if (effectsStr != null && !effectsStr.isEmpty()) {
            applyPotionEffects(living, effectsStr, duration);
        }
    }

    private ItemStack createItem(String materialName) {
        if (materialName == null || materialName.isEmpty()) return null;
        try {
            Material material = Material.valueOf(materialName.toUpperCase());
            return new ItemStack(material);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void applyPotionEffects(LivingEntity entity, String effectsStr, int duration) {
        // Format: TYPE:DURATION:AMP,TYPE:DURATION:AMP
        String[] effects = effectsStr.split(",");
        for (String effect : effects) {
            String[] parts = effect.trim().split(":");
            if (parts.length >= 1) {
                try {
                    PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
                    if (type == null) continue;

                    int effectDuration = parts.length > 1 ? parseInt(parts[1], duration * 20) : duration * 20;
                    int amplifier = parts.length > 2 ? parseInt(parts[2], 0) : 0;

                    entity.addPotionEffect(new PotionEffect(type, effectDuration, amplifier, false, true));
                } catch (Exception ignored) {}
            }
        }
    }

    private LivingEntity determineTarget(EffectContext context, EffectParams params) {
        String targetMode = params.getString("target_mode", "VICTIM");
        Player owner = context.getPlayer();
        double range = params.getDouble("target_range", 10);
        boolean excludeOwner = params.getBoolean("exclude_owner", true);

        LogHelper.debug("[SpawnEntity] Determining initial target: mode=%s, range=%.1f, excludeOwner=%s", 
            targetMode, range, excludeOwner);

        return switch (targetMode.toUpperCase()) {
            case "VICTIM" -> {
                // Priority 1: Direct event victim
                LivingEntity victim = context.getVictim();
                if (victim != null) {
                    LogHelper.debug("[SpawnEntity] VICTIM mode: Using event victim=%s", victim.getName());
                    yield victim;
                }

                // Priority 2: Cached last hit entity
                com.miracle.arcanesigils.binds.LastVictimManager victimManager =
                    ((com.miracle.arcanesigils.ArmorSetsPlugin) getPlugin()).getLastVictimManager();
                if (victimManager != null) {
                    LivingEntity lastVictim = victimManager.getLastVictim(owner);
                    if (lastVictim != null) {
                        LogHelper.debug("[SpawnEntity] VICTIM mode: Using last victim=%s from LastVictimManager",
                            lastVictim.getName());
                        yield lastVictim;
                    }
                }

                // Priority 3: Look-ahead fallback
                LivingEntity lookTarget = getTarget(context, range);
                LogHelper.debug("[SpawnEntity] VICTIM mode: Using look-ahead target=%s",
                    lookTarget != null ? lookTarget.getName() : "none");
                yield lookTarget;
            }
            case "NEARBY" -> {
                LivingEntity result = findNearestEnemy(owner, range, excludeOwner);
                LogHelper.debug("[SpawnEntity] NEARBY mode: found=%s", 
                    result != null ? result.getName() : "none");
                yield result;
            }
            case "TARGET", "OWNER_TARGET" -> {
                // Get bound target from TargetGlowManager (set in binds UI)
                LivingEntity target = null;
                if (owner != null && owner.isOnline()) {
                    com.miracle.arcanesigils.binds.TargetGlowManager glowManager = 
                        ((com.miracle.arcanesigils.ArmorSetsPlugin) getPlugin()).getTargetGlowManager();
                    if (glowManager != null) {
                        target = glowManager.getTarget(owner);
                    }
                }
                
                // Fallback to look target if no bound target
                if (target == null) {
                    target = getTarget(context, range);
                }
                
                LivingEntity result = (target != owner) ? target : null;
                LogHelper.debug("[SpawnEntity] OWNER_TARGET mode: boundTarget=%s, final=%s", 
                    target != null ? target.getName() : "none",
                    result != null ? result.getName() : "none");
                yield result;
            }
            case "NONE" -> {
                LogHelper.debug("[SpawnEntity] NONE mode: no target");
                yield null;
            }
            default -> {
                LivingEntity victim = context.getVictim();
                LivingEntity result = victim != null ? victim : findNearestEnemy(owner, range, excludeOwner);
                LogHelper.debug("[SpawnEntity] DEFAULT mode: victim=%s, final=%s", 
                    victim != null ? victim.getName() : "none",
                    result != null ? result.getName() : "none");
                yield result;
            }
        };
    }

    private LivingEntity findNearestEnemy(Player owner, double range, boolean excludeOwner) {
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity entity : owner.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (excludeOwner && entity.equals(owner)) continue;
            if (entity instanceof ArmorStand) continue;
            // Don't target other spawned entities!
            if (entity.hasMetadata(SPAWNED_ENTITY_KEY)) continue;

            double dist = entity.getLocation().distanceSquared(owner.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = living;
            }
        }

        return nearest;
    }

    private void setupForceTargeting(List<Entity> entities, LivingEntity target,
                                     Player owner, EffectParams params) {
        final UUID targetUUID = target.getUniqueId();
        final UUID ownerUUID = owner.getUniqueId();
        final Set<UUID> entityIds = new HashSet<>();
        entities.forEach(e -> entityIds.add(e.getUniqueId()));
        final String targetMode = params.getString("target_mode", "VICTIM");
        final double targetRange = params.getDouble("target_range", 10);
        final boolean excludeOwner = params.getBoolean("exclude_owner", true);

        Bukkit.getScheduler().runTaskTimer(getPlugin(), task -> {
            // Check if any entities still exist
            boolean anyAlive = false;
            for (UUID id : entityIds) {
                Entity e = Bukkit.getEntity(id);
                if (e != null && e.isValid() && !e.isDead()) {
                    anyAlive = true;
                    break;
                }
            }
            if (!anyAlive) {
                task.cancel();
                return;
            }

            // Determine current target
            LivingEntity currentTarget = null;
            if (targetMode.equals("VICTIM")) {
                Entity t = Bukkit.getEntity(targetUUID);
                if (t instanceof LivingEntity living && t.isValid() && !t.isDead()) {
                    currentTarget = living;
                    LogHelper.debug("[SpawnEntity] Force-targeting VICTIM mode: target=%s (valid=%s)", 
                        living.getName(), living.isValid());
                } else {
                    LogHelper.debug("[SpawnEntity] Force-targeting VICTIM mode: target UUID not found or dead");
                }
            } else if (targetMode.equals("OWNER_TARGET") || targetMode.equals("TARGET")) {
                Player ownerPlayer = Bukkit.getPlayer(ownerUUID);
                if (ownerPlayer != null && ownerPlayer.isOnline()) {
                    // Try to get bound target from TargetGlowManager first
                    TargetGlowManager glowManager = ((ArmorSetsPlugin) getPlugin()).getTargetGlowManager();
                    if (glowManager != null) {
                        LivingEntity boundTarget = glowManager.getTarget(ownerPlayer);
                        if (boundTarget != null && boundTarget.isValid() && !boundTarget.isDead()) {
                            currentTarget = boundTarget;
                            LogHelper.debug("[SpawnEntity] Force-targeting OWNER_TARGET mode: Using bound target=%s from TargetGlowManager",
                                boundTarget.getName());
                        } else {
                            LogHelper.debug("[SpawnEntity] Force-targeting OWNER_TARGET mode: No valid bound target, using initial target UUID");
                            // Fall back to initial target if bound target is gone
                            Entity t = Bukkit.getEntity(targetUUID);
                            if (t instanceof LivingEntity living && t.isValid() && !t.isDead()) {
                                currentTarget = living;
                            }
                        }
                    } else {
                        LogHelper.debug("[SpawnEntity] Force-targeting OWNER_TARGET mode: TargetGlowManager not available, using initial target UUID");
                        // TargetGlowManager not available, use initial target
                        Entity t = Bukkit.getEntity(targetUUID);
                        if (t instanceof LivingEntity living && t.isValid() && !t.isDead()) {
                            currentTarget = living;
                        }
                    }
                } else {
                    LogHelper.debug("[SpawnEntity] Force-targeting OWNER_TARGET mode: Owner offline");
                }
            } else if (targetMode.equals("NEARBY")) {
                Player ownerPlayer = Bukkit.getPlayer(ownerUUID);
                if (ownerPlayer != null && ownerPlayer.isOnline()) {
                    currentTarget = findNearestEnemy(ownerPlayer, targetRange, excludeOwner);
                    LogHelper.debug("[SpawnEntity] Force-targeting NEARBY mode: Found nearest enemy=%s", 
                        currentTarget != null ? currentTarget.getName() : "none");
                }
            }

            if (currentTarget == null) {
                LogHelper.debug("[SpawnEntity] Force-targeting: No valid target found, skipping this cycle");
                return;
            }

            // Force each entity to target
            for (UUID id : entityIds) {
                Entity e = Bukkit.getEntity(id);
                if (e instanceof Mob mob && e.isValid()) {
                    mob.setTarget(currentTarget);
                }
            }
        }, 5L, 10L);
    }

    private void scheduleRemoval(List<Entity> entities, EffectParams params, int duration) {
        Set<UUID> entityIds = new HashSet<>();
        entities.forEach(e -> entityIds.add(e.getUniqueId()));

        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            for (UUID id : entityIds) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.isValid()) {
                    playDeathEffects(entity, params);
                    entity.remove();
                }
                allSpawnedEntities.remove(id);
                // Clean up owner mapping
                ownerToEntities.values().forEach(set -> set.remove(id));
            }
        }, duration * 20L);
    }

    private void playDeathEffects(Entity entity, EffectParams params) {
        Location loc = entity.getLocation().add(0, 1, 0);

        // Death particle
        if (entity.hasMetadata(DEATH_PARTICLE_KEY)) {
            try {
                String particleName = (String) entity.getMetadata(DEATH_PARTICLE_KEY).get(0).value();
                Particle particle = Particle.valueOf(particleName.toUpperCase());
                int count = entity.hasMetadata(DEATH_PARTICLE_COUNT_KEY)
                        ? (int) entity.getMetadata(DEATH_PARTICLE_COUNT_KEY).get(0).value()
                        : 20;

                if (particle == Particle.DUST) {
                    entity.getWorld().spawnParticle(particle, loc, count, 0.5, 0.5, 0.5, 0.1,
                            new Particle.DustOptions(Color.WHITE, 1.0f));
                } else if (particle == Particle.FALLING_DUST || particle == Particle.BLOCK) {
                    entity.getWorld().spawnParticle(particle, loc, count, 0.5, 0.5, 0.5, 0.1,
                            Material.SAND.createBlockData());
                } else {
                    entity.getWorld().spawnParticle(particle, loc, count, 0.5, 0.5, 0.5, 0.1);
                }
            } catch (Exception ignored) {}
        }

        // Death sound
        if (entity.hasMetadata(DEATH_SOUND_KEY)) {
            try {
                String soundStr = (String) entity.getMetadata(DEATH_SOUND_KEY).get(0).value();
                String[] parts = soundStr.split(":");
                Sound sound = Sound.valueOf(parts[0].toUpperCase());
                float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                entity.getWorld().playSound(loc, sound, volume, pitch);
            } catch (Exception ignored) {}
        }
    }

    private EntityType getEntityType(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // === EVENT HANDLERS ===

    /**
     * Prevent spawned entities from targeting their owner OR other spawned entities.
     * This runs before the entity even starts chasing.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnedEntityTarget(EntityTargetLivingEntityEvent event) {
        Entity entity = event.getEntity();
        if (!entity.hasMetadata(SPAWNED_ENTITY_KEY)) return;

        LivingEntity target = event.getTarget();
        if (target == null) return;

        // Prevent targeting other spawned entities (they fight each other otherwise!)
        if (target.hasMetadata(SPAWNED_ENTITY_KEY)) {
            event.setCancelled(true);
            event.setTarget(null);
            return;
        }

        // Check if trying to target owner
        if (entity.hasMetadata(EXCLUDE_OWNER_KEY) && entity.hasMetadata(OWNER_KEY)) {
            boolean excludeOwner = (boolean) entity.getMetadata(EXCLUDE_OWNER_KEY).get(0).value();
            if (!excludeOwner) return;

            String ownerUUID = (String) entity.getMetadata(OWNER_KEY).get(0).value();

            if (target.getUniqueId().toString().equals(ownerUUID)) {
                // Cancel targeting the owner
                event.setCancelled(true);
                event.setTarget(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSpawnedEntityAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (!attacker.hasMetadata(SPAWNED_ENTITY_KEY)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        // Debug: Log attack information
        debug("=== Spawned Entity Attack Event ===");
        debug("Attacker: " + attacker.getType() + " (" + attacker.getCustomName() + ")");
        debug("Victim: " + victim.getType() + (victim instanceof org.bukkit.entity.Player p ? " (" + p.getName() + ")" : ""));
        debug("Event damage: " + event.getDamage());
        debug("Event cancelled: " + event.isCancelled());

        // Log attacker's attack damage attribute
        AttributeInstance damageAttr = attacker.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damageAttr != null) {
            debug("Attacker ATTACK_DAMAGE base: " + damageAttr.getBaseValue());
            debug("Attacker ATTACK_DAMAGE final: " + damageAttr.getValue());
        } else {
            debug("WARNING: Attacker has no ATTACK_DAMAGE attribute!");
        }

        // Check if attacking owner (backup check - targeting should already be prevented)
        if (attacker.hasMetadata(EXCLUDE_OWNER_KEY) && attacker.hasMetadata(OWNER_KEY)) {
            boolean excludeOwner = (boolean) attacker.getMetadata(EXCLUDE_OWNER_KEY).get(0).value();
            String ownerUUID = (String) attacker.getMetadata(OWNER_KEY).get(0).value();
            if (excludeOwner && victim.getUniqueId().toString().equals(ownerUUID)) {
                debug("Cancelling attack - victim is owner!");
                event.setCancelled(true);
                // Also clear the mob's target so it stops chasing
                if (attacker instanceof Mob mob) {
                    mob.setTarget(null);
                }
                return;
            }
        }

        // On-hit mark
        if (attacker.hasMetadata(ON_HIT_MARK_KEY)) {
            int chance = attacker.hasMetadata(ON_HIT_MARK_CHANCE_KEY)
                    ? (int) attacker.getMetadata(ON_HIT_MARK_CHANCE_KEY).get(0).value()
                    : 100;
            if (random.nextInt(100) < chance) {
                String markName = (String) attacker.getMetadata(ON_HIT_MARK_KEY).get(0).value();
                double markDuration = attacker.hasMetadata(ON_HIT_MARK_DURATION_KEY)
                        ? (double) attacker.getMetadata(ON_HIT_MARK_DURATION_KEY).get(0).value()
                        : 5;
                getPlugin().getMarkManager().applyMark(victim, markName, markDuration);
            }
        }

        // On-hit potion effects
        if (attacker.hasMetadata(ON_HIT_EFFECTS_KEY)) {
            int chance = attacker.hasMetadata(ON_HIT_EFFECT_CHANCE_KEY)
                    ? (int) attacker.getMetadata(ON_HIT_EFFECT_CHANCE_KEY).get(0).value()
                    : 100;
            if (random.nextInt(100) < chance) {
                String effectsStr = (String) attacker.getMetadata(ON_HIT_EFFECTS_KEY).get(0).value();
                applyPotionEffects(victim, effectsStr, 0);
            }
        }

        // On-hit stun
        if (attacker.hasMetadata(ON_HIT_STUN_KEY) && victim instanceof Player playerVictim) {
            int chance = attacker.hasMetadata(ON_HIT_STUN_CHANCE_KEY)
                    ? (int) attacker.getMetadata(ON_HIT_STUN_CHANCE_KEY).get(0).value()
                    : 100;
            if (random.nextInt(100) < chance) {
                double stunDuration = (double) attacker.getMetadata(ON_HIT_STUN_KEY).get(0).value();
                if (getPlugin().getStunManager() != null) {
                    getPlugin().getStunManager().stunPlayer(playerVictim, stunDuration);
                }
            }
        }
    }

    @EventHandler
    public void onSpawnedEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.hasMetadata(SPAWNED_ENTITY_KEY)) return;

        // Clear from tracking
        allSpawnedEntities.remove(entity.getUniqueId());
        ownerToEntities.values().forEach(set -> set.remove(entity.getUniqueId()));

        // Clear drops if configured
        if (entity.hasMetadata(NO_DROPS_KEY)) {
            boolean noDrops = (boolean) entity.getMetadata(NO_DROPS_KEY).get(0).value();
            if (noDrops) {
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
        }

        // Play death effects
        playDeathEffects(entity, new EffectParams(id));
    }

    /**
     * Remove all entities spawned by a specific owner.
     */
    public void removeOwnerEntities(UUID ownerUUID) {
        Set<UUID> entities = ownerToEntities.remove(ownerUUID);
        if (entities != null) {
            for (UUID id : entities) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
                allSpawnedEntities.remove(id);
            }
        }
    }

    /**
     * Remove all spawned entities.
     */
    public void removeAllSpawnedEntities() {
        for (UUID id : new HashSet<>(allSpawnedEntities)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        allSpawnedEntities.clear();
        ownerToEntities.clear();
    }

    /**
     * Parse behavior_params from params and resolve variable placeholders.
     * Format: "key1={var1},key2={var2},key3=literal"
     * Example: "mark_chance={mark_chance},curse_chance={curse_chance},curse_duration={curse_duration}"
     *
     * @param params  Effect params containing behavior_params
     * @param context Effect context for variable resolution
     * @return Map of resolved parameter key-value pairs
     */
    private Map<String, Object> parseBehaviorParams(EffectParams params, EffectContext context) {
        Map<String, Object> behaviorParams = new HashMap<>();

        String behaviorParamsStr = params.getString("behavior_params", null);
        if (behaviorParamsStr == null || behaviorParamsStr.isEmpty()) {
            return behaviorParams; // No params specified
        }

        // Split by comma
        String[] pairs = behaviorParamsStr.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.trim().split("=", 2);
            if (keyValue.length != 2) continue;

            String key = keyValue[0].trim();
            String value = keyValue[1].trim();

            // Resolve variable placeholder if present (e.g., {mark_chance})
            if (value.startsWith("{") && value.endsWith("}")) {
                String varName = value.substring(1, value.length() - 1);
                Object resolvedValue = context.getVariable(varName);

                if (resolvedValue != null) {
                    behaviorParams.put(key, resolvedValue);
                    LogHelper.debug("[SpawnEntity] Resolved behavior param: %s = {%s} → %s", key, varName, resolvedValue);
                } else {
                    LogHelper.debug("[SpawnEntity] WARNING: Variable {%s} not found in context, skipping param %s", varName, key);
                }
            } else {
                // Literal value - try to parse as number
                try {
                    if (value.contains(".")) {
                        behaviorParams.put(key, Double.parseDouble(value));
                    } else {
                        behaviorParams.put(key, Integer.parseInt(value));
                    }
                } catch (NumberFormatException e) {
                    // Not a number, store as string
                    behaviorParams.put(key, value);
                }
                LogHelper.debug("[SpawnEntity] Literal behavior param: %s = %s", key, value);
            }
        }

        return behaviorParams;
    }
}
