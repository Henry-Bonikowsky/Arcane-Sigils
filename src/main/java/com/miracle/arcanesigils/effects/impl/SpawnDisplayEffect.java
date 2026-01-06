package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.effects.BehaviorManager;
import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import com.miracle.arcanesigils.effects.ProjectileManager;
import com.miracle.arcanesigils.particles.ShapeDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns Display Entities (Block, Item, or Text) at a location with optional behavior support.
 * Uses Minecraft 1.19.4+ Display Entities for purely visual effects without world modification.
 *
 * Format: SPAWN_DISPLAY:TYPE:VALUE:DURATION @Target
 * Extended: SPAWN_DISPLAY:TYPE:VALUE:DURATION:param=value:param=value @Target
 *
 * Types:
 * - BLOCK - BlockDisplay showing a block (value = material name)
 * - ITEM - ItemDisplay showing an item (value = material name)
 * - TEXT - TextDisplay showing text (value = text with color codes)
 *
 * Parameters:
 * ===========
 *
 * SHAPE (uses ShapeEngine):
 * - shape=circle        Shape: point, circle, ring, spiral, helix, sphere, line, beam, cone
 * - radius=3.0          Radius for shapes
 * - points=20           Number of points for shape
 * - height=3.0          Height for spiral/helix/cone
 * - turns=1.0           Turns for spiral/helix
 *
 * TRANSFORM:
 * - scale=1.0           Uniform scale
 * - offset_x=0          X offset from target
 * - offset_y=0          Y offset from target
 * - offset_z=0          Z offset from target
 *
 * APPEARANCE:
 * - glow=false          Enable glowing
 * - billboard=FIXED     Billboard mode: FIXED, VERTICAL, HORIZONTAL, CENTER
 * - brightness=15       Light level (0-15)
 *
 * TEXT SPECIFIC:
 * - background=64000000 Background color (ARGB hex)
 * - see_through=false   Visible through blocks
 * - shadow=true         Text shadow
 *
 * BEHAVIOR (stationary displays):
 * - behavior=sigil_id   Behavior sigil for PLAYER_NEAR, TICK, EXPIRE signals
 * - detect_radius=3.0   Detection radius for PLAYER_NEAR signal
 *
 * PROJECTILE (moving displays):
 * - movement=STATIONARY|HOMING|DIRECTIONAL  Movement type (default: STATIONARY)
 * - speed=5.0           Speed in blocks per second
 * - on_hit=sigil_id     Behavior sigil to trigger on collision (PROJECTILE_HIT signal)
 * - hit_radius=0.5      Collision detection radius
 * - pierce=false        Continue after hit or despawn
 * - max_distance=30     Max blocks from spawn before despawn
 * - max_time=5          Max seconds before despawn
 *
 * Examples (Stationary):
 * - SPAWN_DISPLAY:BLOCK:RED_SAND:10:shape=circle:radius=3 @Self
 * - SPAWN_DISPLAY:ITEM:DIAMOND_SWORD:5:scale=2:glow=true:billboard=CENTER @Victim
 * - SPAWN_DISPLAY:TEXT:&cWarning!:10:glow=true:billboard=CENTER @Self
 * - SPAWN_DISPLAY:BLOCK:FIRE:15:behavior=fire_aura:detect_radius=5 @Self
 *
 * Examples (Projectile):
 * - SPAWN_DISPLAY:ITEM:FIRE_CHARGE:5:movement=HOMING:speed=8:on_hit=fire_explosion:glow=true @Victim
 * - SPAWN_DISPLAY:BLOCK:PACKED_ICE:3:movement=DIRECTIONAL:speed=15:on_hit=freeze_effect @Self
 * - SPAWN_DISPLAY:BLOCK:FIRE:5:shape=spiral:points=20:height=3:movement=HOMING:speed=6:on_hit=burn @Victim
 */
public class SpawnDisplayEffect extends AbstractEffect {

    // Track spawned displays for cleanup
    private final Map<UUID, Set<UUID>> ownerToDisplays = new ConcurrentHashMap<>();
    private final Set<UUID> allDisplays = ConcurrentHashMap.newKeySet();

    // Track active animations to prevent TICK spam (same display effect re-triggering)
    // Key: "playerUUID:sigilId:signal:shape:material:radius:points" -> expiration timestamp
    private static final Map<String, Long> activeAnimations = new ConcurrentHashMap<>();

    public enum DisplayType {
        BLOCK,
        ITEM,
        TEXT
    }

    public SpawnDisplayEffect() {
        super("SPAWN_DISPLAY", "Spawns display entities (Block/Item/Text) at location");
    }

    /**
     * Generate a unique key for tracking active animations.
     * Key is specific to the exact effect configuration so different effects don't block each other.
     */
    private String getAnimationKey(EffectContext context, EffectParams params) {
        Player player = context.getPlayer();
        String sigilId = context.getMetadata("sourceSigilId", "unknown");
        String signalKey = context.getSignalType() != null ? context.getSignalType().name() : "unknown";

        String shape = params.getString("shape", "point");
        String material = params.getString("material", params.getString("value", "STONE"));
        double radius = params.getDouble("radius", 3.0);
        int points = params.getInt("points", 20);

        return player.getUniqueId() + ":" + sigilId + ":" + signalKey + ":" +
               shape + ":" + material + ":" + radius + ":" + points;
    }

    /**
     * Check if an animation is already running for this context.
     */
    private boolean isAnimationActive(String key) {
        Long expiration = activeAnimations.get(key);
        if (expiration == null) return false;
        if (System.currentTimeMillis() > expiration) {
            activeAnimations.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Mark an animation as active for the given duration.
     */
    private void markAnimationActive(String key, double durationSeconds) {
        long expirationTime = System.currentTimeMillis() + (long)(durationSeconds * 1000);
        activeAnimations.put(key, expirationTime);
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // SPAWN_DISPLAY:TYPE:VALUE:DURATION - supports both positional and key=value
        int positionalIndex = 0;

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];

            if (part.contains("=")) {
                // Key=value format
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    // Handle basic params as key=value
                    switch (key) {
                        case "display_type", "type" -> params.set("display_type", value.toUpperCase());
                        case "value", "material", "block", "item", "text" -> params.set("value", value);
                        case "duration" -> params.setDuration(parseInt(value, 10));
                        default -> parseKeyValue(params, key, value);
                    }
                }
            } else {
                // Positional format
                positionalIndex++;
                switch (positionalIndex) {
                    case 1 -> params.set("display_type", part.toUpperCase());
                    case 2 -> params.set("value", part);
                    case 3 -> {
                        try { params.setDuration(Integer.parseInt(part)); }
                        catch (NumberFormatException ignored) { params.setDuration(10); }
                    }
                }
            }
        }

        return params;
    }

    private void parseKeyValue(EffectParams params, String key, String value) {
        switch (key) {
            // Shape (uses ShapeEngine)
            case "shape" -> params.set("shape", value.toLowerCase());
            case "pattern" -> params.set("shape", value.toLowerCase()); // Legacy support
            case "radius" -> params.set("radius", parseDouble(value, 3.0));
            case "points" -> params.set("points", parseInt(value, 20));
            case "spacing" -> params.set("spacing", parseDouble(value, 0.5));
            case "height" -> params.set("height", parseDouble(value, 3.0));
            case "turns" -> params.set("turns", parseDouble(value, 1.0));

            // Transform
            case "scale" -> params.set("scale", parseFloat(value, 1.0f));
            case "scale_x" -> params.set("scale_x", parseFloat(value, -1.0f)); // -1 means use uniform scale
            case "scale_y" -> params.set("scale_y", parseFloat(value, -1.0f));
            case "scale_z" -> params.set("scale_z", parseFloat(value, -1.0f));
            case "offset_x" -> params.set("offset_x", parseDouble(value, 0));
            case "offset_y" -> params.set("offset_y", parseDouble(value, 0));
            case "offset_z" -> params.set("offset_z", parseDouble(value, 0));
            case "ground_offset" -> params.set("ground_offset", parseDouble(value, 0)); // Y offset after ground snap

            // Appearance
            case "glow" -> params.set("glow", Boolean.parseBoolean(value));
            case "billboard" -> params.set("billboard", value.toUpperCase());
            case "brightness" -> params.set("brightness", parseInt(value, 15));

            // Text specific
            case "background" -> params.set("background", value);
            case "see_through" -> params.set("see_through", Boolean.parseBoolean(value));
            case "shadow" -> params.set("shadow", Boolean.parseBoolean(value));

            // Behavior
            case "behavior" -> params.set("behavior", value);
            case "detect_radius" -> params.set("detect_radius", parseDouble(value, 3.0));

            // Projectile movement (new)
            case "movement" -> params.set("movement", value.toUpperCase());
            case "speed" -> params.set("speed", parseDouble(value, 5.0));
            case "on_hit" -> params.set("on_hit", value);
            case "hit_radius" -> params.set("hit_radius", parseDouble(value, 0.5));
            case "pierce" -> params.set("pierce", Boolean.parseBoolean(value));
            case "max_distance" -> params.set("max_distance", parseDouble(value, 30.0));
            case "max_time" -> params.set("max_time", parseInt(value, 5));

            // Following owner
            case "follow_owner" -> params.set("follow_owner", Boolean.parseBoolean(value));
            case "snap_to_ground" -> params.set("snap_to_ground", Boolean.parseBoolean(value));

            // Animation (shapes animate by default)
            case "rotate_speed", "rotate" -> params.set("rotate_speed", parseDouble(value, 1.0));
            case "y_offset" -> params.set("y_offset", parseDouble(value, 0.0));
        }
    }

    private float parseFloat(String s, float defaultVal) {
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return defaultVal; }
    }

    /**
     * Expand short aliases for materials that exceed sign input limit (~15 chars).
     * Allows users to type "totem" instead of "totem_of_undying".
     */
    private String expandMaterialAlias(String input) {
        if (input == null) return input;
        String upper = input.toUpperCase();
        return switch (upper) {
            case "TOTEM" -> "TOTEM_OF_UNDYING";
            case "ENCHANT_TABLE", "ENCHANT" -> "ENCHANTING_TABLE";
            case "COMMAND", "CMD" -> "COMMAND_BLOCK";
            case "BEACON_BEAM" -> "BEACON"; // Common mistake
            case "BREWING" -> "BREWING_STAND";
            case "NETHER_STAR" -> "NETHER_STAR";
            case "ENDER_CRYSTAL" -> "END_CRYSTAL";
            case "XPBOTTLE", "XP_BOTTLE" -> "EXPERIENCE_BOTTLE";
            case "GAPPLE" -> "GOLDEN_APPLE";
            case "EGAPPLE", "ENCHANTED_APPLE" -> "ENCHANTED_GOLDEN_APPLE";
            case "ELYTRA_WINGS" -> "ELYTRA";
            case "TRIDENT_WEAPON" -> "TRIDENT";
            case "CROSSBOW_WEAPON" -> "CROSSBOW";
            default -> upper;
        };
    }

    @Override
    public boolean execute(EffectContext context) {
        Player owner = context.getPlayer();
        EffectParams params = context.getParams();
        if (params == null) return false;

        // Check if this exact effect is already running (prevents TICK spam)
        String animKey = getAnimationKey(context, params);
        if (isAnimationActive(animKey)) {
            return true; // Animation already running, skip silently
        }

        // Parse parameters - check multiple possible param names for flexibility
        String typeStr = params.getString("display_type", null);
        if (typeStr == null) typeStr = params.getString("type", "BLOCK");

        // Try multiple param names for the material/content value
        String value = params.getString("value", null);
        if (value == null || value.isEmpty()) value = params.getString("material", null);
        if (value == null || value.isEmpty()) value = params.getString("block", null);
        if (value == null || value.isEmpty()) value = params.getString("item", null);
        if (value == null || value.isEmpty()) value = params.getString("text", null);
        if (value == null || value.isEmpty()) value = "STONE";

        debug("SpawnDisplay - type: " + typeStr + ", value: " + value);

        int duration = params.getDuration() > 0 ? params.getDuration() : 10;

        // Mark this animation as active for its duration
        markAnimationActive(animKey, duration);

        String shape = params.getString("shape", "point");
        double radius = params.getDouble("radius", 3.0);
        int points = params.getInt("points", 20);
        double spacing = params.getDouble("spacing", 0.0);
        double height = params.getDouble("height", 3.0);

        debug(String.format("SPAWN_DISPLAY: shape=%s, radius=%.2f (has param: %b), points=%d, height=%.2f",
            shape, radius, params.has("radius"), points, height));
        double turns = params.getDouble("turns", 1.0);
        float scale = params.getFloat("scale", 1.0f);
        boolean glow = params.getBoolean("glow", false);
        String billboardStr = params.getString("billboard", "FIXED");

        DisplayType displayType;
        try {
            displayType = DisplayType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            displayType = DisplayType.BLOCK;
        }

        // Check for custom billboard modes first
        boolean outwardFacing = billboardStr.equalsIgnoreCase("OUTWARD") || billboardStr.equalsIgnoreCase("RADIAL");
        boolean inwardFacing = billboardStr.equalsIgnoreCase("INWARD") || billboardStr.equalsIgnoreCase("CENTER_FACING");
        Display.Billboard billboard;
        if (outwardFacing || inwardFacing) {
            billboard = Display.Billboard.FIXED; // We'll handle rotation manually
        } else {
            try {
                billboard = Display.Billboard.valueOf(billboardStr);
            } catch (IllegalArgumentException e) {
                billboard = Display.Billboard.FIXED;
            }
        }

        Location baseLoc = getTargetLocation(context);
        if (baseLoc == null || baseLoc.getWorld() == null) {
            debug("No valid location for SPAWN_DISPLAY");
            return false;
        }

        // Apply offset
        double offsetX = params.getDouble("offset_x", 0);
        double offsetY = params.getDouble("offset_y", 0);
        double offsetZ = params.getDouble("offset_z", 0);
        baseLoc = baseLoc.clone().add(offsetX, offsetY, offsetZ);

        // Get spawn locations using ShapeEngine
        List<Location> spawnLocations = getShapeLocations(baseLoc, shape, radius, points, spacing, height, turns);
        Location playerLoc = owner.getLocation();
        getPlugin().getLogger().info("[SPAWN_DISPLAY] Shape=" + shape + ", locations=" + spawnLocations.size() +
            ", radius=" + radius + ", spacing=" + spacing +
            ", center=" + String.format("%.1f,%.1f,%.1f", baseLoc.getX(), baseLoc.getY(), baseLoc.getZ()) +
            ", player=" + String.format("%.1f,%.1f,%.1f", playerLoc.getX(), playerLoc.getY(), playerLoc.getZ()));

        // Get behavior sigil if specified
        String behaviorId = params.getString("behavior", null);
        Sigil behaviorSigil = null;
        BehaviorManager behaviorManager = getPlugin().getBehaviorManager();
        if (behaviorId != null && !behaviorId.isEmpty()) {
            behaviorSigil = getPlugin().getSigilManager().getBehavior(behaviorId);
            if (behaviorSigil == null) {
                debug("Behavior not found: " + behaviorId);
            }
        }

        // Get per-axis scale (or use uniform scale if not specified)
        float scaleX = params.getFloat("scale_x", -1.0f);
        float scaleY = params.getFloat("scale_y", -1.0f);
        float scaleZ = params.getFloat("scale_z", -1.0f);
        if (scaleX < 0) scaleX = scale;
        if (scaleY < 0) scaleY = scale;
        if (scaleZ < 0) scaleZ = scale;

        // Spawn displays at each location
        List<Display> spawnedDisplays = new ArrayList<>();
        for (Location loc : spawnLocations) {
            // Calculate yaw based on billboard mode
            if (outwardFacing) {
                // OUTWARD mode: face away from center
                double dx = loc.getX() - baseLoc.getX();
                double dz = loc.getZ() - baseLoc.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                loc.setYaw(yaw);
            } else if (inwardFacing) {
                // INWARD mode: face toward center
                double dx = baseLoc.getX() - loc.getX();
                double dz = baseLoc.getZ() - loc.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                loc.setYaw(yaw);
            } else {
                // For VERTICAL/CENTER/FIXED: don't set rotation, let billboard handle it
                loc.setYaw(0);
                loc.setPitch(0);
            }

            Display display = spawnDisplay(loc, displayType, value, scaleX, scaleY, scaleZ, glow, billboard, params, outwardFacing || inwardFacing);
            if (display != null) {
                spawnedDisplays.add(display);

                // Track display
                allDisplays.add(display.getUniqueId());
                ownerToDisplays.computeIfAbsent(owner.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                        .add(display.getUniqueId());

                // Register with BehaviorManager if behavior sigil is specified
                if (behaviorSigil != null && behaviorManager != null) {
                    behaviorManager.registerEntity(display, behaviorSigil, owner.getUniqueId(), duration);
                }
            }
        }

        if (spawnedDisplays.isEmpty()) {
            debug("Failed to spawn any displays");
            return false;
        }

        // Check for projectile movement
        String movementStr = params.getString("movement", "STATIONARY");
        boolean isProjectile = !movementStr.equals("STATIONARY");

        if (isProjectile) {
            // Register with ProjectileManager for movement
            ProjectileManager projectileManager = getPlugin().getProjectileManager();
            if (projectileManager != null) {
                ProjectileManager.MovementType movementType;
                try {
                    movementType = ProjectileManager.MovementType.valueOf(movementStr);
                } catch (IllegalArgumentException e) {
                    movementType = ProjectileManager.MovementType.DIRECTIONAL;
                }

                double speed = params.getDouble("speed", 5.0);
                String onHitBehavior = params.getString("on_hit", null);
                double hitRadius = params.getDouble("hit_radius", 0.5);
                boolean pierce = params.getBoolean("pierce", false);
                double maxDistance = params.getDouble("max_distance", 30.0);
                int maxTime = params.getInt("max_time", 5);

                // Get target for homing, direction for directional
                LivingEntity homingTarget = null;
                org.bukkit.util.Vector direction = null;

                if (movementType == ProjectileManager.MovementType.HOMING) {
                    // Homing targets the victim
                    if (context.getVictim() != null) {
                        homingTarget = context.getVictim();
                    }
                } else {
                    // Directional uses player's facing direction
                    direction = owner.getLocation().getDirection();
                }

                // Register each display as a projectile
                for (Display display : spawnedDisplays) {
                    projectileManager.registerProjectile(
                            display, owner, homingTarget, direction,
                            speed, hitRadius, pierce, onHitBehavior,
                            maxDistance, maxTime, movementType
                    );
                }

                debug("Registered " + spawnedDisplays.size() + " projectile(s) with movement=" + movementType);
            }
        } else {
            // Check animation/movement params
            boolean isShape = !shape.equals("point");
            // Default follow_owner to TRUE for shapes so effects follow the player
            boolean followOwner = params.getBoolean("follow_owner", isShape);
            boolean snapToGround = params.getBoolean("snap_to_ground", false);
            double groundOffset = params.getDouble("ground_offset", 0.0);
            double rotateSpeed = params.getDouble("rotate_speed", 1.0);
            double yOffsetParam = params.getDouble("y_offset", 0.0);

            // Animation mode: "rotate" (all spin around center), "orbit" (move through points), "none"
            String animateMode = params.getString("animate", "rotate").toLowerCase();

            if (isShape && !animateMode.equals("none")) {
                if (animateMode.equals("orbit") || animateMode.equals("cycle")) {
                    // Orbit mode: displays move through shape points sequentially (orbiting effect)
                    startCycleAnimation(spawnedDisplays, spawnLocations, baseLoc, owner, duration, followOwner, yOffsetParam);
                } else {
                    // Rotate mode: all displays rotate around center together
                    startAnimatedRotation(spawnedDisplays, baseLoc, owner, rotateSpeed, duration, followOwner, yOffsetParam, params);
                }
            } else if (isShape && followOwner) {
                // Shape with no animation but following owner - use animated rotation with speed 0
                startAnimatedRotation(spawnedDisplays, baseLoc, owner, 0, duration, followOwner, yOffsetParam, params);
            } else if (followOwner) {
                // Single display following owner
                scheduleFollowing(spawnedDisplays, owner, baseLoc, duration, snapToGround, groundOffset);
            } else {
                // Stationary single display
                scheduleRemoval(spawnedDisplays, duration);
            }
        }

        debug("Spawned " + spawnedDisplays.size() + " " + displayType + " display(s) for " + duration + "s");
        return true;
    }

    /**
     * Generate locations using ShapeEngine.
     * Supports all shapes: point, circle, ring, spiral, helix, sphere, line, beam, cone, grid
     */
    private List<Location> getShapeLocations(Location center, String shapeName, double radius,
                                              int points, double spacing, double height, double turns) {
        // Create a ShapeDefinition for the requested shape
        ShapeDefinition shape = new ShapeDefinition(shapeName);

        // Build params map for shape generation
        Map<String, Object> shapeParams = new HashMap<>();
        shapeParams.put("radius", radius);
        shapeParams.put("points", points);
        shapeParams.put("spacing", spacing);  // If > 0, overrides points calculation
        shapeParams.put("height", height);
        shapeParams.put("turns", turns);

        // Generate points using ShapeEngine
        return shape.generatePoints(center, shapeParams, 0.0);
    }

    /**
     * Start animated rotation for display entities.
     * Rotates displays around the center point over the duration.
     */
    private void startAnimatedRotation(List<Display> displays, Location center, Player owner,
                                        double rotateSpeed, int duration, boolean followOwner,
                                        double yOffset, EffectParams params) {
        if (displays.isEmpty()) return;

        UUID ownerUUID = owner.getUniqueId();

        // Get snap_to_ground settings from params
        boolean snapToGround = params.getBoolean("snap_to_ground", false);
        double groundOffset = params.getDouble("ground_offset", 0.0);

        // Use the passed-in center (where displays were spawned around)
        // Store each display's base offset from center (for rotation calculation)
        List<double[]> baseOffsets = new ArrayList<>();
        for (Display display : displays) {
            Location displayLoc = display.getLocation();
            double dx = displayLoc.getX() - center.getX();
            double dy = displayLoc.getY() - center.getY();
            double dz = displayLoc.getZ() - center.getZ();
            baseOffsets.add(new double[]{dx, dy, dz});
        }

        int totalTicks = duration * 20;

        BukkitRunnable animationTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    // Remove all displays
                    for (Display display : displays) {
                        if (display.isValid()) display.remove();
                        allDisplays.remove(display.getUniqueId());
                    }
                    ownerToDisplays.values().forEach(set -> displays.forEach(d -> set.remove(d.getUniqueId())));
                    this.cancel();
                    return;
                }

                // Get current center (may follow owner)
                Location currentCenter;
                Player player = null;
                if (followOwner) {
                    player = Bukkit.getPlayer(ownerUUID);
                    if (player == null || !player.isOnline()) {
                        // Owner offline, remove displays
                        for (Display display : displays) {
                            if (display.isValid()) display.remove();
                            allDisplays.remove(display.getUniqueId());
                        }
                        this.cancel();
                        return;
                    }
                    currentCenter = player.getLocation().add(0, yOffset, 0);
                } else {
                    currentCenter = center.clone();
                }

                // Calculate rotation angle
                double progress = (double) tick / totalTicks;
                double rotation = progress * rotateSpeed * 2 * Math.PI;
                double cos = Math.cos(rotation);
                double sin = Math.sin(rotation);

                // Rotate and teleport each display
                for (int i = 0; i < displays.size(); i++) {
                    Display display = displays.get(i);
                    if (!display.isValid()) continue;
                    if (i >= baseOffsets.size()) continue;

                    double[] offset = baseOffsets.get(i);

                    // Rotate offset around Y axis
                    double newX = offset[0] * cos - offset[2] * sin;
                    double newZ = offset[0] * sin + offset[2] * cos;

                    Location newLoc;
                    if (snapToGround && player != null) {
                        // Snap to ground: find ground Y for this X/Z position
                        double targetX = currentCenter.getX() + newX;
                        double targetZ = currentCenter.getZ() + newZ;
                        double groundY = findGroundBelowPlayer(player.getWorld(), targetX, player.getY(), targetZ);
                        groundY += groundOffset;
                        newLoc = new Location(player.getWorld(), targetX, groundY, targetZ, 0, 0);
                    } else {
                        // Normal: use calculated Y offset
                        newLoc = currentCenter.clone().add(newX, offset[1], newZ);
                    }
                    // Reset yaw/pitch so billboard mode can work properly
                    newLoc.setYaw(0);
                    newLoc.setPitch(0);
                    display.teleport(newLoc);
                }

                tick++;
            }
        };
        animationTask.runTaskTimer(getPlugin(), 0L, 1L);
    }

    /**
     * Cycle animation: displays move through shape points sequentially.
     * Creates an orbiting/chasing effect where each display visits each point in order.
     *
     * @param displays The spawned display entities
     * @param shapePoints All the points in the shape (displays cycle through these)
     * @param center The center of the shape
     * @param owner The player who owns this effect
     * @param duration Duration in seconds
     * @param followOwner Whether to follow the owner's position
     * @param yOffset Y offset from center/owner
     */
    private void startCycleAnimation(List<Display> displays, List<Location> shapePoints,
                                     Location center, Player owner, int duration,
                                     boolean followOwner, double yOffset) {
        if (displays.isEmpty() || shapePoints.isEmpty()) return;

        UUID ownerUUID = owner.getUniqueId();
        int numPoints = shapePoints.size();
        int numDisplays = displays.size();

        // Store base offsets of shape points from center (for follow_owner recalculation)
        List<double[]> pointOffsets = new ArrayList<>();
        for (Location point : shapePoints) {
            pointOffsets.add(new double[]{
                point.getX() - center.getX(),
                point.getY() - center.getY(),
                point.getZ() - center.getZ()
            });
        }

        int totalTicks = duration * 20;
        // Each display moves to next point every N ticks (smooth cycling through all points)
        int ticksPerMove = Math.max(1, totalTicks / (numPoints * 2)); // Cycle through twice during duration

        BukkitRunnable cycleTask = new BukkitRunnable() {
            int tick = 0;
            int currentPointIndex = 0;

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    // Remove all displays
                    for (Display display : displays) {
                        if (display.isValid()) display.remove();
                        allDisplays.remove(display.getUniqueId());
                    }
                    ownerToDisplays.values().forEach(set -> displays.forEach(d -> set.remove(d.getUniqueId())));
                    this.cancel();
                    return;
                }

                // Get current center (may follow owner)
                Location currentCenter;
                if (followOwner) {
                    Player player = Bukkit.getPlayer(ownerUUID);
                    if (player == null || !player.isOnline()) {
                        for (Display display : displays) {
                            if (display.isValid()) display.remove();
                            allDisplays.remove(display.getUniqueId());
                        }
                        this.cancel();
                        return;
                    }
                    currentCenter = player.getLocation().add(0, yOffset, 0);
                } else {
                    currentCenter = center.clone();
                }

                // Move to next point every ticksPerMove ticks
                if (tick > 0 && tick % ticksPerMove == 0) {
                    currentPointIndex = (currentPointIndex + 1) % numPoints;
                }

                // Teleport each display to its current point in the cycle
                // Displays are spaced evenly across the shape points
                for (int i = 0; i < numDisplays; i++) {
                    Display display = displays.get(i);
                    if (!display.isValid()) continue;

                    // Each display is offset by (i * numPoints/numDisplays) positions
                    int displayPointIndex = (currentPointIndex + (i * numPoints / numDisplays)) % numPoints;
                    double[] offset = pointOffsets.get(displayPointIndex);

                    Location newLoc = currentCenter.clone().add(offset[0], offset[1], offset[2]);
                    display.teleport(newLoc);
                }

                tick++;
            }
        };
        cycleTask.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private Display spawnDisplay(Location loc, DisplayType type, String value, float scaleX, float scaleY, float scaleZ,
                                  boolean glow, Display.Billboard billboard, EffectParams params, boolean needsRotation) {
        if (loc.getWorld() == null) return null;

        Display display;

        // Get rotation from location (used for INWARD/OUTWARD billboard modes)
        float yaw = loc.getYaw();

        // KEY INSIGHT: BlockDisplay has origin at corner (0,0,0), ItemDisplay has origin at center (0.5,0.5,0.5)
        // When rotation is needed, ItemDisplay works correctly because it rotates around its center.
        // For INWARD/OUTWARD modes with BLOCK type, we use ItemDisplay to get proper center-based rotation.
        boolean useItemDisplayForBlock = needsRotation && type == DisplayType.BLOCK;

        switch (type) {
            case BLOCK:
                Material blockMat;
                try {
                    blockMat = Material.valueOf(expandMaterialAlias(value));
                } catch (IllegalArgumentException e) {
                    blockMat = Material.STONE;
                }
                if (!blockMat.isBlock()) {
                    blockMat = Material.STONE;
                }

                Material finalBlockMat = blockMat;

                if (useItemDisplayForBlock) {
                    // Use ItemDisplay for center-based rotation (origin at center, not corner)
                    ItemStack blockItem = new ItemStack(finalBlockMat);
                    display = loc.getWorld().spawn(loc, ItemDisplay.class, id -> {
                        id.setItemStack(blockItem);
                        // FIXED transform gives block-like appearance (full size, no perspective)
                        id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    });
                } else {
                    // Standard BlockDisplay for non-rotated blocks
                    display = loc.getWorld().spawn(loc, BlockDisplay.class, bd -> {
                        bd.setBlock(finalBlockMat.createBlockData());
                    });
                }
                break;

            case ITEM:
                Material itemMat;
                try {
                    itemMat = Material.valueOf(expandMaterialAlias(value));
                } catch (IllegalArgumentException e) {
                    itemMat = Material.DIAMOND;
                }

                ItemStack item = new ItemStack(itemMat);
                display = loc.getWorld().spawn(loc, ItemDisplay.class, id -> {
                    id.setItemStack(item);
                    id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                });
                break;

            case TEXT:
                // Parse color codes in the text (replace underscores with spaces)
                String textValue = value.replace('_', ' ');
                Component textComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(textValue);
                display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
                    td.text(textComponent);

                    // Background color
                    String bgHex = params.getString("background", null);
                    if (bgHex != null) {
                        try {
                            int argb = (int) Long.parseLong(bgHex, 16);
                            td.setBackgroundColor(org.bukkit.Color.fromARGB(argb));
                        } catch (NumberFormatException ignored) {
                            td.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 0, 0));
                        }
                    } else {
                        td.setBackgroundColor(org.bukkit.Color.fromARGB(100, 0, 0, 0));
                    }

                    td.setAlignment(TextDisplay.TextAlignment.CENTER);
                    td.setSeeThrough(params.getBoolean("see_through", false));
                    td.setShadowed(params.getBoolean("shadow", true));
                });
                break;

            default:
                return null;
        }

        // Apply common settings
        if (display != null) {
            // Calculate rotation for OUTWARD/INWARD mode or scale transform
            boolean hasRotation = yaw != 0;
            boolean hasScale = Math.abs(scaleX - 1.0f) > 0.01f ||
                               Math.abs(scaleY - 1.0f) > 0.01f ||
                               Math.abs(scaleZ - 1.0f) > 0.01f;
            boolean needsTransform = hasRotation || hasScale;

            if (needsTransform) {
                // NOTE: Minecraft yaw is clockwise, JOML rotationY is counter-clockwise, so negate
                float rotRadians = (float) Math.toRadians(-yaw);

                Quaternionf rightRot = new Quaternionf().rotationY(rotRadians);
                Quaternionf noRot = new Quaternionf();

                // ItemDisplay has origin at CENTER - rotation works correctly without compensation
                // BlockDisplay has origin at CORNER - needs translation compensation
                boolean isItemDisplay = (display instanceof ItemDisplay);

                float tx, ty, tz;
                if (isItemDisplay) {
                    // ItemDisplay: origin at center, no compensation needed
                    // Just offset for scale (scale happens around origin, which is already center)
                    tx = 0;
                    ty = 0;
                    tz = 0;
                } else {
                    // BlockDisplay: origin at corner (0,0,0), need to compensate for rotation
                    float cos = (float) Math.cos(rotRadians);
                    float sin = (float) Math.sin(rotRadians);

                    // After RightRotation, model center (0.5,0.5,0.5) moves to new position
                    // Compensate to keep visual center in place
                    tx = -0.5f * scaleX * (cos - sin);
                    ty = -0.5f * scaleY;
                    tz = -0.5f * scaleZ * (sin + cos);
                }

                Transformation transform = new Transformation(
                    new Vector3f(tx, ty, tz),
                    noRot,
                    new Vector3f(scaleX, scaleY, scaleZ),
                    rightRot
                );
                display.setTransformation(transform);
            }

            // Set glow
            display.setGlowing(glow);

            // Set billboard mode (FIXED for outward, otherwise as specified)
            display.setBillboard(billboard);

            // Brightness
            int brightness = params.getInt("brightness", -1);
            if (brightness >= 0 && brightness <= 15) {
                display.setBrightness(new Display.Brightness(brightness, brightness));
            }

            // Make it not persist
            display.setPersistent(false);

            // Enable smooth interpolation for butter-smooth movement at client framerate
            // Duration of 2-3 ticks means client smoothly animates position changes
            display.setInterpolationDuration(3);
            display.setInterpolationDelay(0);
        }

        return display;
    }

    private void scheduleRemoval(List<Display> displays, int duration) {
        Set<UUID> displayIds = new HashSet<>();
        displays.forEach(d -> displayIds.add(d.getUniqueId()));

        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID id : displayIds) {
                    Entity entity = Bukkit.getEntity(id);
                    if (entity != null && entity.isValid()) {
                        entity.remove();
                    }
                    allDisplays.remove(id);
                    ownerToDisplays.values().forEach(set -> set.remove(id));
                }
            }
        }.runTaskLater(getPlugin(), duration * 20L);
    }

    /**
     * Schedule displays to follow their owner and be removed after duration.
     * Each display maintains its relative offset from the owner's position.
     *
     * @param snapToGround If true, displays snap to the highest solid block below the player's Y position
     *                     instead of maintaining a fixed Y offset (useful for ground-based effects)
     * @param groundOffset Y offset to apply after ground snapping (e.g., -0.9 to sink into ground)
     */
    private void scheduleFollowing(List<Display> displays, Player owner, Location spawnBase, int duration, boolean snapToGround, double groundOffset) {
        if (displays.isEmpty()) return;

        UUID ownerUUID = owner.getUniqueId();
        Location ownerLoc = owner.getLocation();

        // Store each display's offset from owner (X/Z only for ground snapping)
        Map<UUID, org.bukkit.util.Vector> displayOffsets = new HashMap<>();
        for (Display display : displays) {
            Location displayLoc = display.getLocation();
            // Calculate offset from owner position
            double offsetX = displayLoc.getX() - ownerLoc.getX();
            double offsetY = displayLoc.getY() - ownerLoc.getY();
            double offsetZ = displayLoc.getZ() - ownerLoc.getZ();
            displayOffsets.put(display.getUniqueId(), new org.bukkit.util.Vector(offsetX, offsetY, offsetZ));
        }

        // Store base locations for each display (for transformation-based movement)
        final Map<UUID, Location> baseLocations = new HashMap<>();
        for (Display display : displays) {
            baseLocations.put(display.getUniqueId(), display.getLocation().clone());
        }

        // Start a repeating task to move displays using transformation interpolation
        // This gives butter-smooth movement at client framerate instead of jerky teleports
        BukkitRunnable followTask = new BukkitRunnable() {
            @Override
            public void run() {
                Player player = Bukkit.getPlayer(ownerUUID);
                if (player == null || !player.isOnline()) {
                    // Owner offline, remove displays
                    for (Display display : displays) {
                        if (display.isValid()) display.remove();
                        allDisplays.remove(display.getUniqueId());
                    }
                    this.cancel();
                    return;
                }

                Location playerLoc = player.getLocation();

                // Move each display using transformation for smooth interpolation
                for (Display display : displays) {
                    if (!display.isValid()) continue;

                    org.bukkit.util.Vector offset = displayOffsets.get(display.getUniqueId());
                    Location baseLoc = baseLocations.get(display.getUniqueId());
                    if (offset == null || baseLoc == null) continue;

                    // Calculate target world position
                    double targetX, targetY, targetZ;
                    if (snapToGround) {
                        // Snap to ground: each display finds its own ground Y for terrain conforming
                        targetX = playerLoc.getX() + offset.getX();
                        targetZ = playerLoc.getZ() + offset.getZ();
                        targetY = findGroundBelowPlayer(playerLoc.getWorld(), targetX, playerLoc.getY(), targetZ);
                        targetY += groundOffset; // Apply ground offset
                    } else {
                        // Normal following: maintain fixed offset from player
                        targetX = playerLoc.getX() + offset.getX();
                        targetY = playerLoc.getY() + offset.getY();
                        targetZ = playerLoc.getZ() + offset.getZ();
                    }

                    // Calculate translation from base position to target position
                    float transX = (float) (targetX - baseLoc.getX());
                    float transY = (float) (targetY - baseLoc.getY());
                    float transZ = (float) (targetZ - baseLoc.getZ());

                    // Get current transformation and update translation
                    Transformation currentTrans = display.getTransformation();
                    Transformation newTrans = new Transformation(
                            new Vector3f(transX, transY, transZ), // New translation
                            currentTrans.getLeftRotation(),
                            currentTrans.getScale(),
                            currentTrans.getRightRotation()
                    );
                    display.setTransformation(newTrans);
                }
            }
        };
        followTask.runTaskTimer(getPlugin(), 2L, 2L); // Update every 2 ticks, interpolation smooths the movement

        // Schedule removal after duration
        new BukkitRunnable() {
            @Override
            public void run() {
                followTask.cancel();
                for (Display display : displays) {
                    if (display.isValid()) display.remove();
                    allDisplays.remove(display.getUniqueId());
                }
                ownerToDisplays.values().forEach(set -> displays.forEach(d -> set.remove(d.getUniqueId())));
            }
        }.runTaskLater(getPlugin(), duration * 20L);
    }

    /**
     * Remove all displays spawned by a specific owner.
     */
    public void removeOwnerDisplays(UUID ownerUUID) {
        Set<UUID> displays = ownerToDisplays.remove(ownerUUID);
        if (displays != null) {
            for (UUID id : displays) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
                allDisplays.remove(id);
            }
        }
    }

    /**
     * Remove all spawned displays.
     */
    public void removeAllDisplays() {
        for (UUID id : new HashSet<>(allDisplays)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        allDisplays.clear();
        ownerToDisplays.clear();
    }

    /**
     * Find the highest solid block near the player's Y position.
     * Used for snap_to_ground feature to keep displays at ground level.
     *
     * Can snap UP to blocks higher than player, but ONLY if there's a continuous
     * solid path (no air gaps). This prevents clipping through trees where leaves
     * are above with air in between.
     *
     * @param world The world to search in
     * @param x The X coordinate
     * @param playerY The player's Y position
     * @param z The Z coordinate
     * @return The Y coordinate of the top of the highest solid block
     */
    private double findGroundBelowPlayer(org.bukkit.World world, double x, double playerY, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int playerBlockY = (int) Math.floor(playerY);

        // First, find ground directly below player's feet
        int baseGroundY = world.getMinHeight();
        for (int y = playerBlockY - 1; y >= world.getMinHeight(); y--) {
            if (world.getBlockAt(blockX, y, blockZ).getType().isSolid()) {
                baseGroundY = y;
                break;
            }
        }

        // Now check if we can snap UP (max 2 blocks higher than player)
        // Only if there's a continuous solid path (no air gaps)
        int maxSnapUp = playerBlockY + 2;
        int highestValidGround = baseGroundY;

        // Search upward from base ground, looking for continuous solid blocks
        for (int y = baseGroundY + 1; y <= maxSnapUp; y++) {
            org.bukkit.block.Block block = world.getBlockAt(blockX, y, blockZ);
            if (block.getType().isSolid()) {
                // This block is solid and continuous from below - valid snap point
                highestValidGround = y;
            } else {
                // Air gap found - stop here, don't snap through
                break;
            }
        }

        // Return top of highest valid ground block
        return highestValidGround + 1.0;
    }
}
