package com.zenax.dungeons.portal;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a dungeon portal structure.
 * Portals are made from crying obsidian frames and teleport players to dungeon lobbies.
 */
public class DungeonPortal {
    private final UUID id;
    private final String dungeonId;
    private final Location location;
    private final Material frameBlock;
    private final String requiredKeyItemId;
    private final Axis axis; // The orientation of the portal (X = east-west portal, Z = north-south portal)
    private boolean active;

    // Portal frame dimensions (Nether portal style: 4 blocks wide, 5 blocks tall)
    private static final int PORTAL_WIDTH = 4;
    private static final int PORTAL_HEIGHT = 5;

    /**
     * Creates a new dungeon portal.
     *
     * @param id Unique identifier for this portal
     * @param dungeonId ID of the dungeon this portal leads to
     * @param location Location of the bottom-left corner of the portal frame
     * @param frameBlock Material used for the portal frame (default: CRYING_OBSIDIAN)
     * @param requiredKeyItemId ID of the key item required to use this portal (null if none)
     * @param axis The axis orientation of the portal (X or Z)
     * @param active Whether the portal is currently active
     */
    public DungeonPortal(UUID id, String dungeonId, Location location, Material frameBlock,
                        String requiredKeyItemId, Axis axis, boolean active) {
        this.id = id;
        this.dungeonId = dungeonId;
        this.location = location.clone();
        this.frameBlock = frameBlock != null ? frameBlock : Material.CRYING_OBSIDIAN;
        this.requiredKeyItemId = requiredKeyItemId;
        this.axis = axis != null ? axis : Axis.X;
        this.active = active;
    }

    /**
     * Creates a new dungeon portal with default frame block (CRYING_OBSIDIAN).
     *
     * @param id Unique identifier for this portal
     * @param dungeonId ID of the dungeon this portal leads to
     * @param location Location of the bottom-left corner of the portal frame
     * @param requiredKeyItemId ID of the key item required to use this portal (null if none)
     * @param axis The axis orientation of the portal (X or Z)
     * @param active Whether the portal is currently active
     */
    public DungeonPortal(UUID id, String dungeonId, Location location, String requiredKeyItemId, Axis axis, boolean active) {
        this(id, dungeonId, location, Material.CRYING_OBSIDIAN, requiredKeyItemId, axis, active);
    }

    public UUID getId() {
        return id;
    }

    public String getDungeonId() {
        return dungeonId;
    }

    public Location getLocation() {
        return location.clone();
    }

    public Material getFrameBlock() {
        return frameBlock;
    }

    public String getRequiredKeyItemId() {
        return requiredKeyItemId;
    }

    public Axis getAxis() {
        return axis;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Checks if this portal requires a key item to use.
     *
     * @return true if a key item is required
     */
    public boolean requiresKey() {
        return requiredKeyItemId != null && !requiredKeyItemId.isEmpty();
    }

    /**
     * Validates that the portal frame structure is intact.
     * Checks for a proper crying obsidian portal frame (similar to Nether portal).
     *
     * @return true if the portal frame is valid
     */
    public boolean isValidFrame() {
        if (location.getWorld() == null) {
            return false;
        }

        Block baseBlock = location.getBlock();
        BlockFace direction = getPortalDirection(baseBlock);

        if (direction == null) {
            // Try all four cardinal directions
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                if (checkFrameStructure(baseBlock, face)) {
                    return true;
                }
            }
            return false;
        }

        return checkFrameStructure(baseBlock, direction);
    }

    /**
     * Checks the frame structure in a specific direction.
     *
     * @param baseBlock The base block of the portal
     * @param direction The direction the portal is facing
     * @return true if the frame structure is valid
     */
    private boolean checkFrameStructure(Block baseBlock, BlockFace direction) {
        // Calculate perpendicular direction for width
        BlockFace perpendicular = getPerpendicularFace(direction);

        // Check bottom row (4 blocks wide)
        for (int x = 0; x < PORTAL_WIDTH; x++) {
            Block block = baseBlock.getRelative(perpendicular, x);
            if (block.getType() != frameBlock) {
                return false;
            }
        }

        // Check left and right sides (3 blocks tall each, excluding corners)
        for (int y = 1; y < PORTAL_HEIGHT - 1; y++) {
            Block leftBlock = baseBlock.getRelative(BlockFace.UP, y);
            Block rightBlock = baseBlock.getRelative(perpendicular, PORTAL_WIDTH - 1).getRelative(BlockFace.UP, y);

            if (leftBlock.getType() != frameBlock || rightBlock.getType() != frameBlock) {
                return false;
            }
        }

        // Check top row (4 blocks wide)
        for (int x = 0; x < PORTAL_WIDTH; x++) {
            Block block = baseBlock.getRelative(BlockFace.UP, PORTAL_HEIGHT - 1).getRelative(perpendicular, x);
            if (block.getType() != frameBlock) {
                return false;
            }
        }

        // Check that interior is air or nether portal (filled portal)
        for (int y = 1; y < PORTAL_HEIGHT - 1; y++) {
            for (int x = 1; x < PORTAL_WIDTH - 1; x++) {
                Block block = baseBlock.getRelative(BlockFace.UP, y).getRelative(perpendicular, x);
                Material type = block.getType();
                if (!type.isAir() && type != Material.NETHER_PORTAL) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Gets the direction the portal is facing based on the frame structure.
     *
     * @param baseBlock The base block of the portal
     * @return The direction the portal faces, or null if undetermined
     */
    private BlockFace getPortalDirection(Block baseBlock) {
        // Check if there's a frame block to the east (portal facing north-south)
        if (baseBlock.getRelative(BlockFace.EAST).getType() == frameBlock) {
            return BlockFace.NORTH;
        }
        // Check if there's a frame block to the south (portal facing east-west)
        if (baseBlock.getRelative(BlockFace.SOUTH).getType() == frameBlock) {
            return BlockFace.EAST;
        }
        return null;
    }

    /**
     * Gets the perpendicular face for a given cardinal direction.
     *
     * @param face The cardinal direction
     * @return The perpendicular direction
     */
    private BlockFace getPerpendicularFace(BlockFace face) {
        switch (face) {
            case NORTH:
            case SOUTH:
                return BlockFace.EAST;
            case EAST:
            case WEST:
                return BlockFace.SOUTH;
            default:
                return BlockFace.EAST;
        }
    }

    /**
     * Gets the BlockFace direction to iterate along based on stored axis.
     * axis=X means frame extends EAST, axis=Z means frame extends SOUTH
     * @return EAST for X axis, SOUTH for Z axis
     */
    private BlockFace getWidthDirection() {
        return axis == Axis.X ? BlockFace.EAST : BlockFace.SOUTH;
    }

    /**
     * Gets all blocks that are part of this portal's frame.
     *
     * @return Set of all frame block locations
     */
    public Set<Location> getFrameBlocks() {
        Set<Location> blocks = new HashSet<>();

        if (location.getWorld() == null) {
            return blocks;
        }

        Block baseBlock = location.getBlock();
        BlockFace widthDir = getWidthDirection();

        // Add bottom row
        for (int x = 0; x < PORTAL_WIDTH; x++) {
            blocks.add(baseBlock.getRelative(widthDir, x).getLocation());
        }

        // Add left and right sides
        for (int y = 1; y < PORTAL_HEIGHT - 1; y++) {
            blocks.add(baseBlock.getRelative(BlockFace.UP, y).getLocation());
            blocks.add(baseBlock.getRelative(widthDir, PORTAL_WIDTH - 1).getRelative(BlockFace.UP, y).getLocation());
        }

        // Add top row
        for (int x = 0; x < PORTAL_WIDTH; x++) {
            blocks.add(baseBlock.getRelative(BlockFace.UP, PORTAL_HEIGHT - 1).getRelative(widthDir, x).getLocation());
        }

        return blocks;
    }

    /**
     * Gets all interior blocks of this portal (where players stand).
     *
     * @return Set of all interior block locations
     */
    public Set<Location> getInteriorBlocks() {
        Set<Location> blocks = new HashSet<>();

        if (location.getWorld() == null) {
            return blocks;
        }

        Block baseBlock = location.getBlock();
        BlockFace widthDir = getWidthDirection();

        // Add interior blocks (2 wide x 3 tall)
        for (int y = 1; y < PORTAL_HEIGHT - 1; y++) {
            for (int x = 1; x < PORTAL_WIDTH - 1; x++) {
                blocks.add(baseBlock.getRelative(BlockFace.UP, y).getRelative(widthDir, x).getLocation());
            }
        }

        return blocks;
    }

    /**
     * Spawns particle effects at the portal location.
     * Creates a swirling effect with PORTAL and REVERSE_PORTAL particles.
     */
    public void spawnParticleEffects() {
        if (!active || location.getWorld() == null) {
            return;
        }

        Set<Location> interiorBlocks = getInteriorBlocks();

        // Spawn particles at random interior locations
        for (Location loc : interiorBlocks) {
            Location particleLoc = loc.clone().add(0.5, 0.5, 0.5);

            // Spawn PORTAL particles (purple swirl)
            location.getWorld().spawnParticle(
                Particle.PORTAL,
                particleLoc,
                3, // count
                0.3, // offsetX
                0.3, // offsetY
                0.3, // offsetZ
                0.1  // extra (speed)
            );

            // Occasionally spawn REVERSE_PORTAL particles
            if (Math.random() < 0.3) {
                location.getWorld().spawnParticle(
                    Particle.REVERSE_PORTAL,
                    particleLoc,
                    1,
                    0.2,
                    0.2,
                    0.2,
                    0.05
                );
            }
        }
    }

    /**
     * Checks if a location is within this portal's interior.
     *
     * @param loc The location to check
     * @return true if the location is inside the portal
     */
    public boolean isLocationInPortal(Location loc) {
        if (loc == null || loc.getWorld() == null || location.getWorld() == null) {
            return false;
        }

        if (!loc.getWorld().equals(location.getWorld())) {
            return false;
        }

        Set<Location> interiorBlocks = getInteriorBlocks();

        // Check if the location is within any of the interior blocks
        for (Location interior : interiorBlocks) {
            if (interior.getBlockX() == loc.getBlockX() &&
                interior.getBlockY() == loc.getBlockY() &&
                interior.getBlockZ() == loc.getBlockZ()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Saves this portal to a configuration section.
     *
     * @param config The configuration section to save to
     */
    public void saveToConfig(ConfigurationSection config) {
        config.set("id", id.toString());
        config.set("dungeon-id", dungeonId);
        config.set("location.world", location.getWorld().getName());
        config.set("location.x", location.getX());
        config.set("location.y", location.getY());
        config.set("location.z", location.getZ());
        config.set("frame-block", frameBlock.name());
        config.set("axis", axis.name());
        config.set("required-key-item", requiredKeyItemId);
        config.set("active", active);
    }

    /**
     * Loads a portal from a configuration section.
     *
     * @param config The configuration section to load from
     * @return The loaded DungeonPortal, or null if loading failed
     */
    public static DungeonPortal fromConfig(ConfigurationSection config) {
        try {
            UUID id = UUID.fromString(config.getString("id"));
            String dungeonId = config.getString("dungeon-id");

            String worldName = config.getString("location.world");
            double x = config.getDouble("location.x");
            double y = config.getDouble("location.y");
            double z = config.getDouble("location.z");

            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world == null) {
                org.bukkit.Bukkit.getLogger().warning("[DungeonPortal] Failed to load portal - world '" + worldName + "' not found!");
                return null;
            }

            Location location = new Location(world, x, y, z);

            Material frameBlock = Material.CRYING_OBSIDIAN;
            try {
                frameBlock = Material.valueOf(config.getString("frame-block", "CRYING_OBSIDIAN"));
            } catch (IllegalArgumentException e) {
                // Use default
            }

            Axis axis = Axis.X;
            try {
                axis = Axis.valueOf(config.getString("axis", "X"));
            } catch (IllegalArgumentException e) {
                // Use default
            }

            String requiredKeyItemId = config.getString("required-key-item");
            boolean active = config.getBoolean("active", true);

            return new DungeonPortal(id, dungeonId, location, frameBlock, requiredKeyItemId, axis, active);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String toString() {
        return "DungeonPortal{" +
               "id=" + id +
               ", dungeonId='" + dungeonId + '\'' +
               ", location=" + location +
               ", active=" + active +
               '}';
    }
}
