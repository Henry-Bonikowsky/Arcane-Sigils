package com.miracle.arcanesigils.core;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.flow.FlowSerializer;
import com.miracle.arcanesigils.tier.TierScalingConfig;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.miracle.arcanesigils.utils.LogHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages all Sigils in the plugin.
 */
public class SigilManager {

    private final ArmorSetsPlugin plugin;
    private final Map<String, Sigil> sigils = new HashMap<>();
    private final Map<String, Sigil> behaviors = new HashMap<>();

    // PDC keys for sigil data on sigil shard items
    private final NamespacedKey SIGIL_ID_KEY;
    private final NamespacedKey SIGIL_TIER_KEY;

    public SigilManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.SIGIL_ID_KEY = new NamespacedKey(plugin, "sigil_id");
        this.SIGIL_TIER_KEY = new NamespacedKey(plugin, "sigil_tier");
    }

    /**
     * Load all sigils from configuration.
     * Each config entry is ONE sigil. Tier is a property that can be set when giving/socketing.
     */
    public void loadSigils() {
        sigils.clear();

        for (Map.Entry<String, FileConfiguration> entry : plugin.getConfigManager().getSigilConfigs().entrySet()) {
            String fileNameWithoutExt = entry.getKey();
            String fileName = fileNameWithoutExt + ".yml"; // Add extension back
            FileConfiguration config = entry.getValue();

            for (String key : config.getKeys(false)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) continue;

                Sigil sigil = Sigil.fromConfig(key, section);
                if (sigil != null) {
                    sigil.setSourceFile(fileName);
                    sigils.put(key.toLowerCase(), sigil);

                    // Debug: log flow details for ancient_crown
                    if (key.equalsIgnoreCase("ancient_crown")) {
                        int flowCount = sigil.hasFlows() ? sigil.getFlows().size() : 0;
                        plugin.getLogger().info("[ANCIENT_CROWN] Loaded with " + flowCount + " flows");
                        if (sigil.hasFlows()) {
                            for (com.miracle.arcanesigils.flow.FlowConfig flow : sigil.getFlows()) {
                                String flowId = flow.getGraph() != null ? flow.getGraph().getId() : "unknown";
                                plugin.getLogger().info("[ANCIENT_CROWN] - Flow ID: " + flowId);
                            }
                        }
                    }
                }
            }
        }

        plugin.getLogger().info("Loaded " + sigils.size() + " base sigils");
    }

    /**
     * Load all behaviors from the behaviors folder.
     * Behaviors are sigils with type=BEHAVIOR, used for entity/display/block behaviors.
     */
    public void loadBehaviors() {
        behaviors.clear();

        File behaviorsDir = new File(plugin.getDataFolder(), "behaviors");
        if (!behaviorsDir.exists()) {
            behaviorsDir.mkdirs();
            plugin.getLogger().info("Created behaviors folder");
            return;
        }

        File[] files = behaviorsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("No behavior files found");
            return;
        }

        for (File file : files) {
            try {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                for (String key : config.getKeys(false)) {
                    ConfigurationSection section = config.getConfigurationSection(key);
                    if (section == null) continue;

                    Sigil behavior = Sigil.fromConfig(key, section);
                    if (behavior != null) {
                        // Force type to BEHAVIOR
                        behavior.setSigilType(Sigil.SigilType.BEHAVIOR);
                        behavior.setSourceFile(file.getName());
                        behaviors.put(key.toLowerCase(), behavior);

                        // Debug: log flow count for behaviors
                        int flowCount = behavior.hasFlows() ? behavior.getFlows().size() : 0;
                        plugin.getLogger().info("  - " + key + ": " + flowCount + " flows");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load behavior file " + file.getName() + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + behaviors.size() + " behaviors");
    }

    /**
     * Get a sigil by ID, optionally with a specific tier applied.
     * The tier is set on the sigil instance; {param} placeholders are
     * resolved at effect execution time using the TierScalingConfig.
     */
    public Sigil getSigilWithTier(String id, int tier) {
        Sigil base = sigils.get(id.toLowerCase());
        if (base == null) return null;

        // Clone the sigil and apply tier
        Sigil tiered = cloneSigil(base);
        tier = Math.max(1, Math.min(tier, tiered.getMaxTier()));
        tiered.setTier(tier);
        return tiered;
    }

    /**
     * Clone a sigil (deep copy for tier application).
     */
    private Sigil cloneSigil(Sigil original) {
        Sigil clone = new Sigil(original.getId());
        clone.setName(original.getName());
        clone.setDescription(new java.util.ArrayList<>(original.getDescription()));
        clone.setSlot(original.getSlot());
        clone.setTier(original.getTier());
        clone.setMaxTier(original.getMaxTier());
        clone.setRarity(original.getRarity());
        clone.setExclusive(original.isExclusive());
        clone.setCrate(original.getCrate());
        clone.setItemForm(original.getItemForm());
        clone.setSourceFile(original.getSourceFile());

        // Copy socketables (CRITICAL - without this, clones allow all item types!)
        if (original.getSocketables() != null) {
            clone.setSocketables(new java.util.HashSet<>(original.getSocketables()));
        }

        // Copy tier configs (make copies to avoid shared state)
        if (original.getTierScalingConfig() != null) {
            clone.setTierScalingConfig(original.getTierScalingConfig().copy());
        }
        if (original.getTierXPConfig() != null) {
            clone.setTierXPConfig(original.getTierXPConfig().copy());
        }

        // Copy flows (new unified system) - MUST use deepCopy() to avoid shared state!
        for (FlowConfig flow : original.getFlows()) {
            clone.addFlow(flow.deepCopy()); // Deep copy to avoid sharing conditions/state between instances
        }

        return clone;
    }

    /**
     * Get a sigil by ID.
     */
    public Sigil getSigil(String id) {
        return sigils.get(id.toLowerCase());
    }

    /**
     * Get all sigils (socketable sigils only, not behaviors).
     */
    public Collection<Sigil> getAllSigils() {
        return sigils.values();
    }

    /**
     * Get all behaviors.
     */
    public Collection<Sigil> getAllBehaviors() {
        return behaviors.values();
    }

    /**
     * Get a behavior by ID.
     */
    public Sigil getBehavior(String id) {
        return behaviors.get(id.toLowerCase());
    }

    /**
     * Get the number of loaded sigils.
     */
    public int getSigilCount() {
        return sigils.size();
    }

    /**
     * Get the number of loaded behaviors.
     */
    public int getBehaviorCount() {
        return behaviors.size();
    }

    /**
     * Create an ItemStack for a sigil (shard/gem form).
     */
    public ItemStack createSigilItem(Sigil sigil) {
        Sigil.ItemForm itemForm = sigil.getItemForm();

        // Use default ItemForm if none is set
        if (itemForm == null) {
            itemForm = new Sigil.ItemForm();
        }

        ItemStack item = new ItemStack(itemForm.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set display name: <sigil-name> <roman numeral>
        String baseName = sigil.getName().replaceAll("\\s*&8\\[T\\d+\\]", "").trim();
        String romanNumeral = toRomanNumeral(sigil.getTier());
        String rarity = sigil.getRarity();
        String rarityColor = getRarityColor(rarity);
        // Only prepend rarity color if name doesn't already have formatting (gradient or color code)
        boolean hasFormatting = baseName.startsWith("<") || baseName.startsWith("&") || baseName.startsWith("§");
        String displayName = hasFormatting ? baseName + " " + romanNumeral : rarityColor + baseName + " " + romanNumeral;
        meta.displayName(TextUtil.parseComponent(displayName));

        // Build lore following enchantment format
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();

        // Line 1: Rarity
        lore.add(TextUtil.parseComponent(rarityColor + "§l" + rarity));

        // Line 2: Empty
        lore.add(net.kyori.adventure.text.Component.empty());

        // Build socketable types display from socketables set
        String socketableDisplay = formatSocketableItems(sigil.getSocketables());

        // Track longest line for bottom border
        int maxLength = socketableDisplay.length() + 2; // "─ " + display

        // Line 3: What items it can be applied to
        lore.add(TextUtil.parseComponent("§8┌─ §a" + socketableDisplay));

        // Line 4+: Description (from sigil description or flow effects)
        if (!sigil.getDescription().isEmpty()) {
            for (String desc : sigil.getDescription()) {
                lore.add(TextUtil.parseComponent("§8│ §f" + desc));
                maxLength = Math.max(maxLength, desc.length() + 2); // "│ " prefix
            }
        } else if (sigil.hasFlows()) {
            // Generate description from first flow
            FlowConfig flow = sigil.getFlows().get(0);
            String triggerDesc = flow.getTrigger() != null ? flow.getTrigger() : "Unknown";
            lore.add(TextUtil.parseComponent("§8│ §7Trigger: §f" + triggerDesc));
            maxLength = Math.max(maxLength, triggerDesc.length() + 12);
        }

        // Build bottom border to match longest line
        // The ─ character is narrower than text, so divide by ~2 for visual match
        int borderLength = (int) Math.ceil(maxLength / 1.8);
        StringBuilder border = new StringBuilder("§8└");
        for (int i = 0; i < borderLength - 1; i++) {
            border.append("─");
        }
        lore.add(TextUtil.parseComponent(border.toString()));

        lore.add(net.kyori.adventure.text.Component.empty());

        lore.add(TextUtil.parseComponent("§8Drag and drop to socket sigil"));

        meta.lore(lore);

        // Set custom model data
        if (itemForm.getModelData() > 0) {
            meta.setCustomModelData(itemForm.getModelData());
        }
        // Disable enchantment glint on sigil items
        meta.setEnchantmentGlintOverride(false);

        // Store sigil ID and tier in PDC
        meta.getPersistentDataContainer().set(SIGIL_ID_KEY, PersistentDataType.STRING, sigil.getId());
        meta.getPersistentDataContainer().set(SIGIL_TIER_KEY, PersistentDataType.INTEGER, sigil.getTier());

        // Hide attributes
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Get the sigil ID from a sigil item.
     */
    public String getSigilIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(SIGIL_ID_KEY, PersistentDataType.STRING);
    }

    /**
     * Get the tier from a sigil item.
     */
    public int getSigilTierFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;

        ItemMeta meta = item.getItemMeta();
        Integer tier = meta.getPersistentDataContainer().get(SIGIL_TIER_KEY, PersistentDataType.INTEGER);
        return tier != null ? tier : 1;
    }

    /**
     * Get the sigil from a sigil item (with correct tier).
     */
    public Sigil getSigilFromItem(ItemStack item) {
        String id = getSigilIdFromItem(item);
        if (id == null) return null;
        int tier = getSigilTierFromItem(item);
        return getSigilWithTier(id, tier);
    }

    /**
     * Check if an item is a sigil shard/gem.
     */
    public boolean isSigilItem(ItemStack item) {
        return getSigilIdFromItem(item) != null;
    }

    /**
     * Get sigils by slot type.
     */
    public Collection<Sigil> getSigilsBySlot(String slot) {
        return sigils.values().stream()
                .filter(f -> f.getSlot().equalsIgnoreCase(slot))
                .toList();
    }

    /**
     * Get sigils by tier.
     */
    public Collection<Sigil> getSigilsByTier(int tier) {
        return sigils.values().stream()
                .filter(f -> f.getTier() == tier)
                .toList();
    }

    public NamespacedKey getSigilIdKey() {
        return SIGIL_ID_KEY;
    }

    /**
     * Convert tier number to roman numeral.
     */
    private String toRomanNumeral(int tier) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (tier >= 1 && tier <= 10) {
            return numerals[tier - 1];
        }
        return String.valueOf(tier);
    }

    /**
     * Format socketable items set into a display string.
     * Groups armor types together and handles special cases.
     */
    private String formatSocketableItems(Set<String> items) {
        if (items == null || items.isEmpty()) {
            return "None";
        }

        // Check if all armor types are selected
        boolean hasAllArmor = items.contains("helmet") && items.contains("chestplate")
                && items.contains("leggings") && items.contains("boots");

        List<String> displayParts = new ArrayList<>();

        if (hasAllArmor) {
            displayParts.add("Armor");
        } else {
            // Add individual armor pieces
            if (items.contains("helmet")) displayParts.add("Helmet");
            if (items.contains("chestplate")) displayParts.add("Chestplate");
            if (items.contains("leggings")) displayParts.add("Leggings");
            if (items.contains("boots")) displayParts.add("Boots");
        }

        // Add tool/weapon types (general categories)
        if (items.contains("tool")) displayParts.add("Tools");
        if (items.contains("weapon")) displayParts.add("Weapons");
        if (items.contains("bow")) displayParts.add("Bows");
        if (items.contains("axe")) displayParts.add("Axes");
        if (items.contains("offhand")) displayParts.add("Offhand");

        // Add specific item types
        if (items.contains("sword")) displayParts.add("Swords");
        if (items.contains("pickaxe")) displayParts.add("Pickaxes");
        if (items.contains("shovel")) displayParts.add("Shovels");
        if (items.contains("hoe")) displayParts.add("Hoes");
        if (items.contains("fishing_rod")) displayParts.add("Fishing Rods");
        if (items.contains("crossbow")) displayParts.add("Crossbows");
        if (items.contains("trident")) displayParts.add("Tridents");

        if (displayParts.isEmpty()) {
            return "None";
        }

        return String.join(", ", displayParts);
    }

    /**
     * Get rarity color code based on rarity name.
     */
    private String getRarityColor(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> "§7";      // Gray
            case "UNCOMMON" -> "§a";    // Green
            case "RARE" -> "§9";        // Blue
            case "EPIC" -> "§5";        // Purple
            case "LEGENDARY" -> "§6";   // Gold
            case "MYTHIC" -> "§d";      // Pink
            default -> "§7";
        };
    }

    /**
     * Save a sigil back to its configuration file.
     * This method saves changes made to a sigil through the GUI.
     */
    public void saveSigil(Sigil sigil) {
        saveSigil(sigil, null);
    }

    /**
     * Save a sigil to its YAML file.
     * @param sigil The sigil to save
     * @param oldSourceFile If the filename changed, provide the old filename to remove from
     */
    public void saveSigil(Sigil sigil, String oldSourceFile) {
        // Update in-memory map
        sigils.put(sigil.getId().toLowerCase(), sigil);

        // Save to file
        String sourceFile = sigil.getSourceFile();
        if (sourceFile == null) {
            sourceFile = "sigils.yml"; // Default filename
            sigil.setSourceFile(sourceFile);
        }

        // Get the file path
        File sigilsDir = new File(plugin.getDataFolder(), "sigils");

        // Ensure directory exists
        if (!sigilsDir.exists()) {
            sigilsDir.mkdirs();
        }

        File file = new File(sigilsDir, sourceFile);

        // If filename changed, remove from old file first
        if (oldSourceFile != null && !oldSourceFile.equals(sourceFile)) {
            removeFromFile(sigil.getId(), oldSourceFile);
        }

        try {
            // Ensure file exists
            if (!file.exists()) {
                file.createNewFile();
            }

            // Load the config file
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            // Update the sigil's section
            String sigilId = sigil.getId();

            // Create or update the sigil section
            config.set(sigilId + ".name", sigil.getName());
            config.set(sigilId + ".description", sigil.getDescription());
            config.set(sigilId + ".slot", sigil.getSlot());
            config.set(sigilId + ".max_tier", sigil.getMaxTier());
            config.set(sigilId + ".rarity", sigil.getRarity());
            config.set(sigilId + ".exclusive", sigil.isExclusive());

            if (sigil.getCrate() != null) {
                config.set(sigilId + ".crate", sigil.getCrate());
            }

            if (sigil.getLorePrefix() != null) {
                config.set(sigilId + ".lore_prefix", sigil.getLorePrefix());
            }

            // Save socketables (new key name)
            if (sigil.getSocketables() != null && !sigil.getSocketables().isEmpty()) {
                config.set(sigilId + ".socketables", new java.util.ArrayList<>(sigil.getSocketables()));
            }

            // Save item form if present (use "item" key, custom_model_data for consistency)
            if (sigil.getItemForm() != null) {
                Sigil.ItemForm itemForm = sigil.getItemForm();
                config.set(sigilId + ".item.material", itemForm.getMaterial().name());
                config.set(sigilId + ".item.custom_model_data", itemForm.getModelData());
                config.set(sigilId + ".item.name", itemForm.getName());
                config.set(sigilId + ".item.lore", itemForm.getLore());
                config.set(sigilId + ".item.glow", itemForm.isGlow());
            }

            // Save tier scaling config if present
            if (sigil.getTierScalingConfig() != null) {
                TierScalingConfig tierConfig = sigil.getTierScalingConfig();
                config.set(sigilId + ".tier.mode", tierConfig.getMode().name());

                // Save parameter arrays
                if (tierConfig.hasParams()) {
                    java.util.Map<String, Object> paramsMap = tierConfig.getParams().toMap();
                    for (java.util.Map.Entry<String, Object> param : paramsMap.entrySet()) {
                        config.set(sigilId + ".tier.params." + param.getKey(), param.getValue());
                        LogHelper.debug("[TierSave] %s.tier.params.%s = %s", sigilId, param.getKey(), param.getValue());
                    }
                }
            }

            // Save tier XP config if present
            if (sigil.getTierXPConfig() != null) {
                var xpConfig = sigil.getTierXPConfig();
                config.set(sigilId + ".tier.xp_enabled", xpConfig.isEnabled());
                if (xpConfig.isEnabled()) {
                    config.set(sigilId + ".tier.xp.gain_per_activation", xpConfig.getGainPerActivation());
                    config.set(sigilId + ".tier.xp.curve_type", xpConfig.getCurveType().name());
                    config.set(sigilId + ".tier.xp.base_xp", xpConfig.getBaseXP());
                    config.set(sigilId + ".tier.xp.growth_rate", xpConfig.getGrowthRate());
                }
            }

            // Save unified flows (replaces old signals and activation)
            // Uses the new list format: flows: [{type, trigger, nodes...}, ...]
            LogHelper.debug("[SigilSave] %s hasFlows=%s, flowCount=%d",
                sigilId, sigil.hasFlows(), sigil.getFlows().size());

            if (sigil.hasFlows()) {
                // Save as flows list
                java.util.List<java.util.Map<String, Object>> flowsList =
                    FlowSerializer.flowConfigsToMapList(sigil.getFlows());
                LogHelper.debug("[SigilSave] Serialized %d flows for %s", flowsList.size(), sigilId);
                
                // AGGRESSIVE DEBUG - log serialized conditions
                for (java.util.Map<String, Object> flowMap : flowsList) {
                    Object conditions = flowMap.get("conditions");
                    plugin.getLogger().severe(String.format(
                        "[SigilSave] POST-SERIALIZE: flowId=%s, conditions=%s",
                        flowMap.get("id"), conditions
                    ));
                }
                
                config.set(sigilId + ".flows", flowsList);
            } else {
                LogHelper.warning("[SigilSave] Sigil %s has NO flows to save! Flows list: %s",
                    sigilId, sigil.getFlows());
            }

            // Save to file
            config.save(file);

            plugin.getLogger().info("Saved sigil: " + sigil.getId() + " to " + sourceFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save sigil " + sigil.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Remove a sigil from a specific YAML file.
     * Used when the sigil's filename changes to clean up the old file.
     */
    private void removeFromFile(String sigilId, String fileName) {
        File sigilsDir = new File(plugin.getDataFolder(), "sigils");
        File file = new File(sigilsDir, fileName);

        if (!file.exists()) {
            return; // File doesn't exist, nothing to remove
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            // Remove the sigil section
            if (config.contains(sigilId)) {
                config.set(sigilId, null);
                config.save(file);
                plugin.getLogger().info("Removed sigil " + sigilId + " from old file: " + fileName);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to remove sigil " + sigilId + " from " + fileName + ": " + e.getMessage());
        }
    }

    /**
     * Register a new sigil (for GUI-created sigils).
     */
    public void registerSigil(Sigil sigil) {
        sigils.put(sigil.getId().toLowerCase(), sigil);
    }

    /**
     * Delete a sigil completely (remove from memory and file).
     */
    public void deleteSigil(String sigilId) {
        Sigil sigil = sigils.remove(sigilId.toLowerCase());
        if (sigil != null && sigil.getSourceFile() != null) {
            removeFromFile(sigilId, sigil.getSourceFile());
        }
    }

    /**
     * Save a behavior to the behaviors folder.
     * @param behavior The behavior sigil to save
     */
    public void saveBehavior(Sigil behavior) {
        saveBehavior(behavior, null);
    }

    /**
     * Save a behavior to the behaviors folder.
     * @param behavior The behavior sigil to save
     * @param oldSourceFile If filename changed, the old filename to remove from
     */
    public void saveBehavior(Sigil behavior, String oldSourceFile) {
        // Ensure it's marked as a behavior
        behavior.setSigilType(Sigil.SigilType.BEHAVIOR);

        // Update in-memory map
        behaviors.put(behavior.getId().toLowerCase(), behavior);

        // Save to file
        String sourceFile = behavior.getSourceFile();
        if (sourceFile == null) {
            sourceFile = behavior.getId().toLowerCase() + ".yml";
            behavior.setSourceFile(sourceFile);
        }

        // Get the file path - behaviors go in behaviors/ folder
        File behaviorsDir = new File(plugin.getDataFolder(), "behaviors");

        // Ensure directory exists
        if (!behaviorsDir.exists()) {
            behaviorsDir.mkdirs();
        }

        File file = new File(behaviorsDir, sourceFile);

        // If filename changed, remove from old file first
        if (oldSourceFile != null && !oldSourceFile.equals(sourceFile)) {
            removeFromBehaviorFile(behavior.getId(), oldSourceFile);
        }

        try {
            // Ensure file exists
            if (!file.exists()) {
                file.createNewFile();
            }

            // Load the config file
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            // Update the behavior's section
            String behaviorId = behavior.getId();

            // Create or update the behavior section
            config.set(behaviorId + ".name", behavior.getName());
            config.set(behaviorId + ".type", "BEHAVIOR");
            config.set(behaviorId + ".description", behavior.getDescription());

            // Save unified flows
            if (behavior.hasFlows()) {
                java.util.List<java.util.Map<String, Object>> flowsList =
                    FlowSerializer.flowConfigsToMapList(behavior.getFlows());
                config.set(behaviorId + ".flows", flowsList);
            }

            // Save to file
            config.save(file);

            plugin.getLogger().info("Saved behavior: " + behavior.getId() + " to behaviors/" + sourceFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save behavior " + behavior.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Remove a behavior from a specific YAML file in behaviors folder.
     */
    private void removeFromBehaviorFile(String behaviorId, String fileName) {
        File behaviorsDir = new File(plugin.getDataFolder(), "behaviors");
        File file = new File(behaviorsDir, fileName);

        if (!file.exists()) {
            return;
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            if (config.contains(behaviorId)) {
                config.set(behaviorId, null);
                config.save(file);
                plugin.getLogger().info("Removed behavior " + behaviorId + " from old file: " + fileName);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to remove behavior " + behaviorId + " from " + fileName + ": " + e.getMessage());
        }
    }

    /**
     * Register a new behavior (for GUI-created behaviors).
     */
    public void registerBehavior(Sigil behavior) {
        behavior.setSigilType(Sigil.SigilType.BEHAVIOR);
        behaviors.put(behavior.getId().toLowerCase(), behavior);
    }

    /**
     * Delete a behavior completely (remove from memory and file).
     */
    public void deleteBehavior(String behaviorId) {
        Sigil behavior = behaviors.remove(behaviorId.toLowerCase());
        if (behavior != null && behavior.getSourceFile() != null) {
            removeFromBehaviorFile(behaviorId, behavior.getSourceFile());
        }
    }
}
