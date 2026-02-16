package com.miracle.arcanesigils.core;

import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.flow.FlowNode;
import com.miracle.arcanesigils.flow.FlowSerializer;
import com.miracle.arcanesigils.flow.FlowType;
import com.miracle.arcanesigils.tier.TierScalingConfig;
import com.miracle.arcanesigils.tier.TierXPConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a Sigil that can be socketed into armor.
 */
public class Sigil {

    /**
     * The type of sigil - determines how it can be used.
     */
    public enum SigilType {
        /** Standard sigil that can be socketed into equipment */
        STANDARD,
        /** Behavior definition for spawned entities/blocks - not socketable */
        BEHAVIOR,
        /** Exclusive sigil - socketable but cannot be removed */
        EXCLUSIVE
    }

    private final String id;
    private String name;
    private List<String> description;
    private String slot; // HELMET, CHESTPLATE, LEGGINGS, BOOTS
    private int tier;
    private int maxTier;
    private String rarity; // COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
    private ItemForm itemForm;
    private boolean exclusive; // If true, cannot be unsocketed
    private String crate; // Optional crate name for exclusive sigils
    private String lorePrefix; // Custom prefix symbol for exclusive sigils in lore (e.g., "⚖")
    private String sourceFile; // The file this sigil was loaded from
    private Set<String> socketables; // Items this sigil can socket to (helmet, chestplate, sword, etc.)
    private TierScalingConfig tierScalingConfig; // How this sigil scales with tier
    private TierXPConfig tierXPConfig; // XP progression configuration
    private SigilType sigilType = SigilType.STANDARD; // Type of sigil

    // UNIFIED FLOWS - replaces both signals and activation
    // A sigil has ONE flow (either SIGNAL or ABILITY type)
    // Rules: ABILITY type only allowed on exclusive sigils
    private List<FlowConfig> flows = new ArrayList<>();

    public Sigil(String id) {
        this.id = id;
        this.name = id;
        this.description = new ArrayList<>();
        this.slot = "HELMET";
        this.tier = 1;
        this.maxTier = 10;
        this.rarity = "COMMON";
        this.itemForm = null;
        this.exclusive = false;
        this.crate = null;
        this.lorePrefix = null;
        this.sourceFile = null;
        this.socketables = new HashSet<>();
        // Default to all item types
        this.socketables.add("helmet");
        this.socketables.add("chestplate");
        this.socketables.add("leggings");
        this.socketables.add("boots");
        this.socketables.add("tool");
        this.socketables.add("weapon");
        this.socketables.add("bow");
        this.socketables.add("axe");
        this.tierScalingConfig = null; // Uses defaults if null
        this.tierXPConfig = new TierXPConfig(); // Default XP config
        this.flows = new ArrayList<>(); // Flows are added when sigil is configured
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getDescription() {
        return description;
    }

    public void setDescription(List<String> description) {
        this.description = description;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public int getMaxTier() {
        return maxTier;
    }

    public void setMaxTier(int maxTier) {
        this.maxTier = maxTier;
    }

    public String getRarity() {
        return rarity;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity;
    }

    // ============ UNIFIED FLOWS ============

    /**
     * Get all flows for this sigil.
     */
    public List<FlowConfig> getFlows() {
        return flows;
    }

    /**
     * Set all flows for this sigil.
     */
    public void setFlows(List<FlowConfig> flows) {
        this.flows = flows != null ? flows : new ArrayList<>();
    }

    /**
     * Add a flow to this sigil.
     * Validates: only one ABILITY flow allowed, ABILITY only on exclusive sigils.
     *
     * @param flow The flow to add
     * @return true if added successfully, false if validation failed
     */
    public boolean addFlow(FlowConfig flow) {
        if (flow == null) return false;

        // Validation: ABILITY flows only allowed on exclusive sigils
        if (flow.isAbility() && !this.exclusive) {
            com.miracle.arcanesigils.utils.LogHelper.warning(
                "[Sigil] Cannot add ABILITY flow to non-exclusive sigil '%s'", id);
            return false;
        }

        // Validation: Only one ABILITY flow per sigil
        if (flow.isAbility() && hasAbilityFlow()) {
            com.miracle.arcanesigils.utils.LogHelper.warning(
                "[Sigil] Sigil '%s' already has an ABILITY flow", id);
            return false;
        }

        // Validation: No duplicate signal triggers for STANDARD sigils
        // BEHAVIOR sigils CAN have multiple flows with the same trigger (e.g., Royal Guards: mark + curse on ATTACK)
        if (sigilType != SigilType.BEHAVIOR && flow.isSignal() && flow.getTrigger() != null) {
            String normalizedTrigger = normalizeTrigger(flow.getTrigger());
            for (FlowConfig existing : flows) {
                if (existing.isSignal() && normalizedTrigger.equals(normalizeTrigger(existing.getTrigger()))) {
                    com.miracle.arcanesigils.utils.LogHelper.warning(
                        "[Sigil] Sigil '%s' already has a flow for trigger '%s' (only behaviors allow multiple)",
                        id, flow.getTrigger());
                    return false;
                }
            }
        }

        flows.add(flow);
        return true;
    }

    /**
     * Remove a flow from this sigil.
     */
    public boolean removeFlow(FlowConfig flow) {
        return flows.remove(flow);
    }

    /**
     * Remove a flow by trigger name.
     */
    public boolean removeFlowByTrigger(String trigger) {
        if (trigger == null) return false;
        String normalized = normalizeTrigger(trigger);
        if (normalized == null) return false;
        return flows.removeIf(f -> f.isSignal() && normalized.equals(normalizeTrigger(f.getTrigger())));
    }

    /**
     * Get a flow for a specific signal trigger.
     * NOTE: Only returns the FIRST match. Use getFlowsForTrigger() to get ALL matching flows.
     *
     * @param trigger The trigger name (e.g., "ATTACK", "ON_ATTACK")
     * @return The matching FlowConfig, or null if not found
     */
    public FlowConfig getFlowForTrigger(String trigger) {
        if (trigger == null) return null;

        String normalized = normalizeTrigger(trigger);
        for (FlowConfig flow : flows) {
            if (flow.isSignal() && normalized.equals(normalizeTrigger(flow.getTrigger()))) {
                return flow;
            }
        }
        return null;
    }

    /**
     * Get ALL flows for a specific signal trigger.
     * A sigil can have multiple flows with the same trigger (e.g., two ON_SNEAK abilities).
     *
     * @param trigger The trigger name (e.g., "ATTACK", "ON_ATTACK")
     * @return List of all matching FlowConfigs (may be empty, never null)
     */
    public List<FlowConfig> getFlowsForTrigger(String trigger) {
        List<FlowConfig> matching = new java.util.ArrayList<>();
        if (trigger == null) return matching;

        String normalized = normalizeTrigger(trigger);
        for (FlowConfig flow : flows) {
            if (flow.isSignal() && normalized.equals(normalizeTrigger(flow.getTrigger()))) {
                matching.add(flow);
            }
        }
        return matching;
    }

    /**
     * Get the ability flow for this sigil (if any).
     * Only exclusive sigils can have ability flows.
     */
    public FlowConfig getAbilityFlow() {
        for (FlowConfig flow : flows) {
            if (flow.isAbility()) {
                return flow;
            }
        }
        return null;
    }

    /**
     * Check if this sigil has any configured flows.
     */
    public boolean hasFlows() {
        return !flows.isEmpty() && flows.stream().anyMatch(FlowConfig::hasNodes);
    }

    /**
     * Check if this sigil has an ability flow.
     */
    public boolean hasAbilityFlow() {
        return flows.stream().anyMatch(FlowConfig::isAbility);
    }

    /**
     * Check if this sigil has any signal flows.
     */
    public boolean hasSignalFlows() {
        return flows.stream().anyMatch(FlowConfig::isSignal);
    }

    /**
     * Get all signal flows (excludes ability flow).
     */
    public List<FlowConfig> getSignalFlowsList() {
        return flows.stream()
            .filter(FlowConfig::isSignal)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Check if this sigil is an ability (bind-activated, not signal-triggered).
     * A sigil is considered an ability if it HAS an ability flow.
     */
    public boolean isAbility() {
        return hasAbilityFlow();
    }

    /**
     * Check if this sigil is signal-triggered (not a bind-activated ability).
     */
    public boolean isSignalTriggered() {
        return hasSignalFlows();
    }

    /**
     * Normalize a trigger name to standard format.
     */
    private static String normalizeTrigger(String trigger) {
        if (trigger == null) return null;
        String normalized = trigger.toUpperCase().trim();
        if (normalized.startsWith("ON_")) {
            normalized = normalized.substring(3);
        }
        // Handle common aliases
        return switch (normalized) {
            case "SNEAK" -> "SHIFT";
            case "DEFEND" -> "DEFENSE";
            case "HIT" -> "ATTACK";
            case "PASSIVE" -> "EFFECT_STATIC";
            case "KILL" -> "KILL_MOB";
            case "FISH_CATCH" -> "FISH";
            default -> normalized;
        };
    }

    public ItemForm getItemForm() {
        return itemForm;
    }

    public void setItemForm(ItemForm itemForm) {
        this.itemForm = itemForm;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public String getCrate() {
        return crate;
    }

    public void setCrate(String crate) {
        this.crate = crate;
    }

    public String getLorePrefix() {
        return lorePrefix;
    }

    public void setLorePrefix(String lorePrefix) {
        this.lorePrefix = lorePrefix;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Set<String> getSocketables() {
        return socketables;
    }

    public void setSocketables(Set<String> socketables) {
        this.socketables = socketables;
    }

    public TierScalingConfig getTierScalingConfig() {
        return tierScalingConfig;
    }

    public void setTierScalingConfig(TierScalingConfig tierScalingConfig) {
        this.tierScalingConfig = tierScalingConfig;
    }

    public TierXPConfig getTierXPConfig() {
        return tierXPConfig;
    }

    public void setTierXPConfig(TierXPConfig tierXPConfig) {
        this.tierXPConfig = tierXPConfig;
    }

    public SigilType getSigilType() {
        return sigilType;
    }

    public void setSigilType(SigilType sigilType) {
        this.sigilType = sigilType;
    }

    /**
     * Check if this sigil is a behavior (for spawned entities/blocks).
     */
    public boolean isBehavior() {
        return sigilType == SigilType.BEHAVIOR;
    }

    /**
     * Check if this sigil can be socketed into equipment.
     */
    public boolean isSocketable() {
        return sigilType != SigilType.BEHAVIOR;
    }

    /**
     * Check if XP progression is enabled for this sigil.
     */
    public boolean isXPEnabled() {
        return tierXPConfig != null && tierXPConfig.isEnabled();
    }

    /**
     * Check if this sigil can be socketed into the given slot.
     */
    public boolean canSocketInto(String armorSlot) {
        return slot.equalsIgnoreCase(armorSlot);
    }

    /**
     * Load Sigil from configuration section.
     */
    public static Sigil fromConfig(String id, ConfigurationSection section) {
        if (section == null) return null;

        Sigil sigil = new Sigil(id);
        sigil.setName(section.getString("name", id));
        sigil.setDescription(section.getStringList("description"));
        sigil.setSlot(section.getString("slot", "HELMET").toUpperCase());
        // tier is a runtime property (stored on item instance), not a template property
        // sigil instances always start at tier 1, max_tier defines the ceiling
        sigil.setMaxTier(section.getInt("max_tier", 10));
        sigil.setRarity(section.getString("rarity", "COMMON").toUpperCase());
        sigil.setExclusive(section.getBoolean("exclusive", false));
        sigil.setCrate(section.getString("crate", null));
        sigil.setLorePrefix(section.getString("lore_prefix", null));

        // Load sigil type (STANDARD, BEHAVIOR, EXCLUSIVE)
        String typeStr = section.getString("type", "STANDARD").toUpperCase();
        try {
            sigil.setSigilType(SigilType.valueOf(typeStr));
        } catch (IllegalArgumentException e) {
            sigil.setSigilType(SigilType.STANDARD);
        }
        // Override if exclusive flag is set
        if (sigil.isExclusive() && sigil.getSigilType() == SigilType.STANDARD) {
            sigil.setSigilType(SigilType.EXCLUSIVE);
        }

        // Load tier configuration
        ConfigurationSection tierSection = section.getConfigurationSection("tier");
        if (tierSection != null) {
            sigil.setTierScalingConfig(TierScalingConfig.fromConfig(tierSection));

            // Check for xp_enabled at tier level or in xp subsection
            if (tierSection.contains("xp_enabled") && !tierSection.getBoolean("xp_enabled", true)) {
                sigil.setTierXPConfig(TierXPConfig.disabled());
            } else {
                sigil.setTierXPConfig(TierXPConfig.fromConfig(tierSection.getConfigurationSection("xp")));
            }
        }

        // Load socketables
        if (section.contains("socketables")) {
            Set<String> socketables = new HashSet<>(section.getStringList("socketables"));
            sigil.setSocketables(socketables);
        }

        // Load item form
        ConfigurationSection itemFormSection = section.getConfigurationSection("item");
        if (itemFormSection != null) {
            sigil.setItemForm(ItemForm.fromConfig(itemFormSection));
        }

        // Load flows - only "flows" (plural) is supported
        List<?> flowsList = section.getList("flows");
        if (flowsList != null && !flowsList.isEmpty()) {
            List<FlowConfig> loadedFlows = FlowSerializer.deserializeFlowConfigs(flowsList);
            com.miracle.arcanesigils.utils.LogHelper.info("[Sigil] %s: Deserialized %d flows from YAML", id, loadedFlows.size());
            
            int addedCount = 0;
            for (FlowConfig flow : loadedFlows) {
                // Sync flow-level cooldown/chance to START node params
                syncFlowSettingsToStartNode(flow);
                
                // Log flow details BEFORE adding
                String flowId = flow.getGraph() != null ? flow.getGraph().getId() : "unknown";
                String trigger = flow.getTrigger() != null ? flow.getTrigger() : "none";
                com.miracle.arcanesigils.utils.LogHelper.info("[FlowAdd] %s: Attempting to add flow: type=%s, trigger=%s, id=%s", 
                    id, flow.getType(), trigger, flowId);
                
                boolean added = sigil.addFlow(flow);
                if (added) {
                    addedCount++;
                    com.miracle.arcanesigils.utils.LogHelper.info("[FlowAdd] %s: ✓ Successfully added flow %s", id, flowId);
                } else {
                    com.miracle.arcanesigils.utils.LogHelper.warning("[FlowAdd] %s: ✗ FAILED to add flow %s (rejected by validation)", id, flowId);
                }
            }
            com.miracle.arcanesigils.utils.LogHelper.info("[Sigil] %s: Final flow count = %d (added %d of %d)", 
                id, sigil.getFlows().size(), addedCount, loadedFlows.size());
        } else if (section.contains("flow")) {
            // Backward compatibility: load single flow from "flow" key
            Object flowObj = section.get("flow");
            if (flowObj instanceof ConfigurationSection) {
                // Deserialize single flow
                ConfigurationSection flowSection = (ConfigurationSection) flowObj;
                FlowConfig flow = FlowSerializer.deserializeFlowConfig(flowSection);
                if (flow != null) {
                    syncFlowSettingsToStartNode(flow);
                    sigil.addFlow(flow);
                    com.miracle.arcanesigils.utils.LogHelper.info("[Sigil] %s: Loaded 1 flow (legacy format)",
                        id);
                }
            }
        }

        return sigil;
    }

    /**
     * Sync flow-level cooldown and chance to the START node params.
     * This ensures the START node is the single source of truth for these values.
     * Called during sigil loading to migrate flow-level settings into START node.
     *
     * @param flow The flow config to sync
     */
    private static void syncFlowSettingsToStartNode(FlowConfig flow) {
        if (flow == null || flow.getGraph() == null) return;

        FlowNode startNode = flow.getGraph().getStartNode();
        if (startNode == null) return;

        // Sync cooldown: only if flow has a non-zero cooldown and START node doesn't already have one
        double flowCooldown = flow.getCooldown();
        if (flowCooldown > 0 && startNode.getParam("cooldown") == null) {
            startNode.setParam("cooldown", flowCooldown);
            com.miracle.arcanesigils.utils.LogHelper.debug("[FlowSync] Synced cooldown %.1f to START node", flowCooldown);
        }

        // Sync chance: only if flow has non-100% chance and START node doesn't already have one
        double flowChance = flow.getChance();
        if (flowChance < 100 && startNode.getParam("chance") == null) {
            startNode.setParam("chance", flowChance);
            com.miracle.arcanesigils.utils.LogHelper.debug("[FlowSync] Synced chance %.1f to START node", flowChance);
        }

        // Sync trigger/signal_type for SIGNAL flows
        if (flow.getType() == FlowType.SIGNAL && flow.getTrigger() != null) {
            if (startNode.getParam("signal_type") == null) {
                startNode.setParam("signal_type", flow.getTrigger());
                com.miracle.arcanesigils.utils.LogHelper.debug("[FlowSync] Synced signal_type %s to START node", flow.getTrigger());
            }
        }

        // Set flow_type so GUI knows how to render START node params
        startNode.setParam("flow_type", flow.getType().name());
    }

    /**
     * Represents the item form of a sigil (when extracted as a shard/gem).
     */
    public static class ItemForm {
        private Material material = Material.ECHO_SHARD;
        private int modelData = 0;
        private String name = "Sigil Shard";
        private List<String> lore = new ArrayList<>();
        private boolean glow = false;

        public Material getMaterial() {
            return material;
        }

        public void setMaterial(Material material) {
            this.material = material;
        }

        public int getModelData() {
            return modelData;
        }

        public void setModelData(int modelData) {
            this.modelData = modelData;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getLore() {
            return lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public boolean isGlow() {
            return glow;
        }

        public void setGlow(boolean glow) {
            this.glow = glow;
        }

        public static ItemForm fromConfig(ConfigurationSection section) {
            ItemForm form = new ItemForm();
            if (section == null) return form;

            String materialName = section.getString("material", "ECHO_SHARD");
            try {
                form.setMaterial(Material.valueOf(materialName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                form.setMaterial(Material.ECHO_SHARD);
            }

            // Prefer custom_model_data (Minecraft convention), fall back to model_data
            form.setModelData(section.getInt("custom_model_data", section.getInt("model_data", 0)));
            form.setName(section.getString("name", "Sigil Shard"));
            form.setLore(section.getStringList("lore"));
            form.setGlow(section.getBoolean("glow", false) || section.getBoolean("enchant_glow", false));

            return form;
        }
    }
}
