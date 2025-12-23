package com.zenax.dungeons.dungeon;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Represents a dungeon template that can be instantiated.
 * This class holds all the configuration for a specific dungeon type.
 */
public class Dungeon {
    private final String id;
    private final String displayName;
    private final String description;
    private final DungeonDifficulty defaultDifficulty;
    private final int minPlayers;
    private final int maxPlayers;
    private final int timeLimit; // in seconds
    private final String bossId;
    private final String requiredKeyItemId;
    private final List<ObjectiveMode> availableObjectiveModes;

    // Generation settings
    private final int minRoomCount;
    private final int maxRoomCount;
    private final int minCaveCount;
    private final int maxCaveCount;
    private final Map<String, Material> themeMaterials;

    // Loot settings
    private final List<String> lootTableIds;

    /**
     * Creates a new dungeon template.
     *
     * @param id Unique identifier for this dungeon
     * @param displayName Display name shown to players
     * @param description Description of the dungeon
     * @param defaultDifficulty Default difficulty level
     * @param minPlayers Minimum number of players required
     * @param maxPlayers Maximum number of players allowed
     * @param timeLimit Time limit in seconds (0 for unlimited)
     * @param bossId ID of the boss mob for this dungeon
     * @param requiredKeyItemId ID of the key item required to enter (null if none)
     * @param availableObjectiveModes List of available objective modes
     * @param minRoomCount Minimum number of rooms to generate
     * @param maxRoomCount Maximum number of rooms to generate
     * @param minCaveCount Minimum number of caves to generate
     * @param maxCaveCount Maximum number of caves to generate
     * @param themeMaterials Map of theme material types to their materials
     * @param lootTableIds List of loot table IDs for this dungeon
     */
    public Dungeon(String id, String displayName, String description, DungeonDifficulty defaultDifficulty,
                   int minPlayers, int maxPlayers, int timeLimit, String bossId, String requiredKeyItemId,
                   List<ObjectiveMode> availableObjectiveModes, int minRoomCount, int maxRoomCount,
                   int minCaveCount, int maxCaveCount, Map<String, Material> themeMaterials,
                   List<String> lootTableIds) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.defaultDifficulty = defaultDifficulty;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.timeLimit = timeLimit;
        this.bossId = bossId;
        this.requiredKeyItemId = requiredKeyItemId;
        this.availableObjectiveModes = new ArrayList<>(availableObjectiveModes);
        this.minRoomCount = minRoomCount;
        this.maxRoomCount = maxRoomCount;
        this.minCaveCount = minCaveCount;
        this.maxCaveCount = maxCaveCount;
        this.themeMaterials = new HashMap<>(themeMaterials);
        this.lootTableIds = new ArrayList<>(lootTableIds);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public DungeonDifficulty getDefaultDifficulty() {
        return defaultDifficulty;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getTimeLimit() {
        return timeLimit;
    }

    public String getBossId() {
        return bossId;
    }

    public String getRequiredKeyItemId() {
        return requiredKeyItemId;
    }

    public List<ObjectiveMode> getAvailableObjectiveModes() {
        return new ArrayList<>(availableObjectiveModes);
    }

    public int getMinRoomCount() {
        return minRoomCount;
    }

    public int getMaxRoomCount() {
        return maxRoomCount;
    }

    public int getMinCaveCount() {
        return minCaveCount;
    }

    public int getMaxCaveCount() {
        return maxCaveCount;
    }

    public Map<String, Material> getThemeMaterials() {
        return new HashMap<>(themeMaterials);
    }

    public List<String> getLootTableIds() {
        return new ArrayList<>(lootTableIds);
    }

    /**
     * Gets the loot table ID used for completion rewards.
     *
     * @return The completion loot table ID, or the first loot table if none specified
     */
    public String getCompletionLootTable() {
        return lootTableIds.isEmpty() ? "basic" : lootTableIds.get(0);
    }

    /**
     * Checks if this dungeon requires a key item to enter.
     *
     * @return true if a key item is required
     */
    public boolean requiresKey() {
        return requiredKeyItemId != null && !requiredKeyItemId.isEmpty();
    }

    /**
     * Checks if this dungeon has a time limit.
     *
     * @return true if there is a time limit
     */
    public boolean hasTimeLimit() {
        return timeLimit > 0;
    }

    /**
     * Checks if the given objective mode is available for this dungeon.
     *
     * @param mode The objective mode to check
     * @return true if the mode is available
     */
    public boolean hasObjectiveMode(ObjectiveMode mode) {
        return availableObjectiveModes.contains(mode);
    }

    /**
     * Loads a dungeon from a configuration section.
     *
     * @param config The configuration section to load from
     * @return The loaded Dungeon, or null if loading failed
     */
    public static Dungeon fromConfig(ConfigurationSection config) {
        try {
            String id = config.getName();
            String displayName = config.getString("display-name", id);
            String description = config.getString("description", "");

            DungeonDifficulty defaultDifficulty = DungeonDifficulty.fromString(
                config.getString("default-difficulty", "NORMAL")
            );
            if (defaultDifficulty == null) {
                defaultDifficulty = DungeonDifficulty.NORMAL;
            }

            int minPlayers = config.getInt("min-players", 1);
            int maxPlayers = config.getInt("max-players", 5);
            int timeLimit = config.getInt("time-limit", 0);

            String bossId = config.getString("boss-id", "");
            String requiredKeyItemId = config.getString("required-key-item", null);

            // Load objective modes
            List<ObjectiveMode> objectiveModes = new ArrayList<>();
            List<String> modeStrings = config.getStringList("objective-modes");
            if (modeStrings.isEmpty()) {
                objectiveModes.add(ObjectiveMode.BOSS_KILL);
            } else {
                for (String modeStr : modeStrings) {
                    ObjectiveMode mode = ObjectiveMode.fromString(modeStr);
                    if (mode != null) {
                        objectiveModes.add(mode);
                    }
                }
            }

            // Load generation settings
            ConfigurationSection genSection = config.getConfigurationSection("generation");
            int minRoomCount = 5;
            int maxRoomCount = 10;
            int minCaveCount = 2;
            int maxCaveCount = 5;
            Map<String, Material> themeMaterials = new HashMap<>();

            if (genSection != null) {
                minRoomCount = genSection.getInt("min-room-count", 5);
                maxRoomCount = genSection.getInt("max-room-count", 10);
                minCaveCount = genSection.getInt("min-cave-count", 2);
                maxCaveCount = genSection.getInt("max-cave-count", 5);

                ConfigurationSection themeSection = genSection.getConfigurationSection("theme-materials");
                if (themeSection != null) {
                    for (String key : themeSection.getKeys(false)) {
                        try {
                            Material material = Material.valueOf(themeSection.getString(key).toUpperCase());
                            themeMaterials.put(key, material);
                        } catch (IllegalArgumentException e) {
                            // Invalid material, skip
                        }
                    }
                }
            }

            // Set default theme materials if none specified
            if (themeMaterials.isEmpty()) {
                themeMaterials.put("wall", Material.STONE_BRICKS);
                themeMaterials.put("floor", Material.STONE);
                themeMaterials.put("ceiling", Material.STONE);
            }

            // Load loot tables
            List<String> lootTableIds = config.getStringList("loot-tables");
            if (lootTableIds.isEmpty()) {
                lootTableIds.add("default_dungeon_loot");
            }

            return new Dungeon(id, displayName, description, defaultDifficulty, minPlayers, maxPlayers,
                             timeLimit, bossId, requiredKeyItemId, objectiveModes, minRoomCount, maxRoomCount,
                             minCaveCount, maxCaveCount, themeMaterials, lootTableIds);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String toString() {
        return "Dungeon{" +
               "id='" + id + '\'' +
               ", displayName='" + displayName + '\'' +
               ", difficulty=" + defaultDifficulty +
               ", players=" + minPlayers + "-" + maxPlayers +
               '}';
    }
}
