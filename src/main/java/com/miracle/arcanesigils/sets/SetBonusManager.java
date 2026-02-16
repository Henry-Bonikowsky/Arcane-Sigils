package com.miracle.arcanesigils.sets;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.flow.FlowNode;
import com.miracle.arcanesigils.flow.FlowSerializer;
import com.miracle.arcanesigils.utils.LogHelper;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages set bonuses for players wearing matching armor pieces.
 * Set bonuses are independent of sigils and trigger based on equipped armor crates.
 */
public class SetBonusManager {
    private final ArmorSetsPlugin plugin;
    private final Map<String, SetBonus> loadedSets; // setName -> SetBonus
    private final Map<UUID, Map<String, SetBonusState>> playerSetBonuses; // playerId -> (setName -> state)

    public SetBonusManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.loadedSets = new HashMap<>();
        this.playerSetBonuses = new HashMap<>();
        loadSetBonuses();
    }

    /**
     * Load all set bonuses from sets/ directory.
     */
    private void loadSetBonuses() {
        File setsDir = new File(plugin.getDataFolder(), "sets");
        if (!setsDir.exists()) {
            setsDir.mkdirs();
            plugin.getLogger().info("Created sets/ directory");
            return;
        }

        File[] setFiles = setsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (setFiles == null || setFiles.length == 0) {
            plugin.getLogger().warning("No set bonus files found in sets/ directory");
            return;
        }

        for (File setFile : setFiles) {
            try {
                loadSetBonusFile(setFile);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load set bonus file: " + setFile.getName(), e);
            }
        }

        plugin.getLogger().info("Loaded " + loadedSets.size() + " set bonuses");
    }

    /**
     * Load a single set bonus YAML file.
     */
    private void loadSetBonusFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String setKey : config.getKeys(false)) {
            ConfigurationSection setSection = config.getConfigurationSection(setKey);
            if (setSection == null) continue;

            String name = setSection.getString("name", setKey);
            String description = setSection.getString("description", "");
            List<String> crates = setSection.getStringList("crates");
            int minPieces = setSection.getInt("min_pieces", 2);

            // Load flow
            ConfigurationSection flowSection = setSection.getConfigurationSection("flow");
            FlowConfig flow = null;
            if (flowSection != null) {
                flow = FlowSerializer.deserializeFlowConfig(flowSection);
                if (flow != null && flow.getGraph() != null) {
                    FlowNode startNode = flow.getGraph().getStartNode();
                    if (startNode != null) {
                        String nextConn = startNode.getConnection("next");
                        LogHelper.info("[SetBonus] Loaded flow for %s: START node 'next' -> %s",
                            setKey, nextConn);
                        LogHelper.info("[SetBonus] Flow has %d nodes",
                            flow.getGraph().getNodes().size());
                    }
                }
            }

            // Load tier params
            Map<String, Map<Integer, Double>> tierParams = new HashMap<>();
            ConfigurationSection tierParamsSection = setSection.getConfigurationSection("tier_params");
            if (tierParamsSection != null) {
                for (String paramName : tierParamsSection.getKeys(false)) {
                    ConfigurationSection paramSection = tierParamsSection.getConfigurationSection(paramName);
                    if (paramSection != null) {
                        Map<Integer, Double> tierValues = new HashMap<>();
                        for (String tierKey : paramSection.getKeys(false)) {
                            int tier = Integer.parseInt(tierKey);
                            double value = paramSection.getDouble(tierKey);
                            tierValues.put(tier, value);
                        }
                        tierParams.put(paramName, tierValues);
                    }
                }
            }

            SetBonus setBonus = new SetBonus(name, description, crates, minPieces, flow, tierParams);
            loadedSets.put(setKey, setBonus);

            plugin.getLogger().info("Loaded set bonus: " + name + " (min pieces: " + minPieces + ")");
        }
    }

    /**
     * Calculate active set bonuses for a player based on equipped armor.
     * Returns map of setName -> tier (based on 2 HIGHEST equipped pieces, rounded down)
     */
    public Map<String, Integer> calculateSetBonuses(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        Map<String, List<Integer>> setTiers = new HashMap<>(); // setName -> list of tiers

        for (ItemStack armorPiece : armor) {
            if (armorPiece == null || armorPiece.getType().isAir()) continue;

            List<Sigil> sigils = plugin.getSocketManager().getSocketedSigils(armorPiece);
            for (Sigil sigil : sigils) {
                String setName = getSetNameFromCrate(sigil.getCrate());
                if (setName != null) {
                    setTiers.computeIfAbsent(setName, k -> new ArrayList<>()).add(sigil.getTier());
                }
            }
        }

        Map<String, Integer> activeBonuses = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : setTiers.entrySet()) {
            String setName = entry.getKey();
            List<Integer> tiers = entry.getValue();

            SetBonus setBonus = loadedSets.get(setName);
            if (setBonus == null) continue;

            // Need at least min_pieces for set bonus
            if (tiers.size() < setBonus.getMinPieces()) continue;

            // Get 2 highest tiers, round down to lower of the two
            tiers.sort(Collections.reverseOrder());
            int tier1 = tiers.get(0);
            int tier2 = tiers.size() > 1 ? tiers.get(1) : tier1;
            int bonusTier = Math.min(tier1, tier2);

            activeBonuses.put(setName, bonusTier);
        }

        return activeBonuses;
    }

    /**
     * Extract set name from crate field.
     * Examples:
     * - "Pharaoh Crate Exclusive" -> "ancient_set"
     * - "Seasonal Pass Exclusive" -> "ancient_set"
     * - "<gradient:#9400D3:#4B0082>Seasonal Pass Exclusive</gradient>" -> "ancient_set"
     */
    private String getSetNameFromCrate(String crate) {
        if (crate == null || crate.isEmpty()) return null;

        // Strip color codes/gradients
        String cleaned = crate.replaceAll("<[^>]+>", "").replaceAll("§.", "");

        // Check each loaded set's crates list
        for (Map.Entry<String, SetBonus> entry : loadedSets.entrySet()) {
            for (String setCrate : entry.getValue().getCrates()) {
                String cleanedSetCrate = setCrate.replaceAll("<[^>]+>", "").replaceAll("§.", "");
                if (cleaned.contains(cleanedSetCrate) || cleanedSetCrate.contains(cleaned)) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }

    /**
     * Update player's active set bonuses. Called by armor change listener.
     */
    public void updatePlayerSetBonuses(Player player) {
        Map<String, Integer> newBonuses = calculateSetBonuses(player);
        Map<String, SetBonusState> oldBonuses = playerSetBonuses.getOrDefault(player.getUniqueId(), new HashMap<>());

        // Check for newly activated sets
        for (Map.Entry<String, Integer> entry : newBonuses.entrySet()) {
            String setName = entry.getKey();
            int tier = entry.getValue();

            if (!oldBonuses.containsKey(setName)) {
                // Set bonus activated
                sendActivationMessage(player, setName, tier);
            }
        }

        // Check for deactivated sets
        for (String setName : oldBonuses.keySet()) {
            if (!newBonuses.containsKey(setName)) {
                // Set bonus deactivated
                sendDeactivationMessage(player, setName);
            }
        }

        // Update stored state
        if (newBonuses.isEmpty()) {
            playerSetBonuses.remove(player.getUniqueId());
        } else {
            Map<String, SetBonusState> stateMap = new HashMap<>();
            for (Map.Entry<String, Integer> entry : newBonuses.entrySet()) {
                stateMap.put(entry.getKey(), new SetBonusState(entry.getKey(), entry.getValue()));
            }
            playerSetBonuses.put(player.getUniqueId(), stateMap);
        }
    }

    /**
     * Get active set bonus tier for a player.
     * @return tier (1-5) or 0 if not active
     */
    public int getSetBonusTier(Player player, String setName) {
        Map<String, SetBonusState> bonuses = playerSetBonuses.get(player.getUniqueId());
        if (bonuses == null) return 0;
        SetBonusState state = bonuses.get(setName);
        return state != null ? state.getTier() : 0;
    }

    /**
     * Get all active bonuses for a player.
     */
    public Map<String, Integer> getActiveBonuses(Player player) {
        Map<String, SetBonusState> bonuses = playerSetBonuses.get(player.getUniqueId());
        if (bonuses == null) return new HashMap<>();

        Map<String, Integer> activeBonuses = new HashMap<>();
        for (Map.Entry<String, SetBonusState> entry : bonuses.entrySet()) {
            activeBonuses.put(entry.getKey(), entry.getValue().getTier());
        }
        return activeBonuses;
    }

    /**
     * Get a loaded set bonus by name.
     */
    public SetBonus getSetBonus(String setName) {
        return loadedSets.get(setName);
    }

    private void sendActivationMessage(Player player, String setName, int tier) {
        String roman = getRomanNumeral(tier);
        String displayName = getSetDisplayName(setName);
        player.sendMessage("§6[" + displayName + " " + roman + "] §aactivated");
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
    }

    private void sendDeactivationMessage(Player player, String setName) {
        String displayName = getSetDisplayName(setName);
        player.sendMessage("§6[" + displayName + "] §cdeactivated");
    }

    private String getSetDisplayName(String setName) {
        SetBonus setBonus = loadedSets.get(setName);
        return setBonus != null ? setBonus.getName() : setName;
    }

    private String getRomanNumeral(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(tier);
        };
    }

    /**
     * Reload all set bonuses from disk.
     */
    public void reload() {
        loadedSets.clear();
        loadSetBonuses();
    }

    /**
     * Cleanup on player quit.
     */
    public void removePlayer(UUID playerId) {
        playerSetBonuses.remove(playerId);
    }
}
