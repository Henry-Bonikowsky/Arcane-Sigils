package com.zenax.armorsets.core;

import com.zenax.armorsets.sets.TriggerConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a Sigil that can be socketed into armor.
 */
public class Sigil {

    private final String id;
    private String name;
    private List<String> description;
    private String slot; // HELMET, CHESTPLATE, LEGGINGS, BOOTS
    private int tier;
    private int maxTier;
    private String rarity; // COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
    private Map<String, TriggerConfig> effects;
    private ItemForm itemForm;
    private boolean exclusive; // If true, cannot be unsocketed
    private String crate; // Optional crate name for exclusive sigils

    public Sigil(String id) {
        this.id = id;
        this.name = id;
        this.description = new ArrayList<>();
        this.slot = "HELMET";
        this.tier = 1;
        this.maxTier = 10;
        this.rarity = "COMMON";
        this.effects = new HashMap<>();
        this.itemForm = new ItemForm();
        this.exclusive = false;
        this.crate = null;
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

    public Map<String, TriggerConfig> getEffects() {
        return effects;
    }

    public void setEffects(Map<String, TriggerConfig> effects) {
        this.effects = effects;
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
        sigil.setTier(section.getInt("tier", 1));
        sigil.setMaxTier(section.getInt("max_tier", 10));
        sigil.setRarity(section.getString("rarity", "COMMON").toUpperCase());
        sigil.setExclusive(section.getBoolean("exclusive", false));
        sigil.setCrate(section.getString("crate", null));

        // Load effects - check both root level on_* keys and effects section
        Map<String, TriggerConfig> effects = new HashMap<>();

        // Check root level on_* keys first
        for (String key : section.getKeys(false)) {
            if (key.startsWith("on_") || key.equals("effect_static")) {
                TriggerConfig config = TriggerConfig.fromConfig(section.getConfigurationSection(key));
                if (config != null) {
                    effects.put(key, config);
                }
            }
        }

        // Also check effects section for backwards compatibility
        ConfigurationSection effectsSection = section.getConfigurationSection("effects");
        if (effectsSection != null) {
            for (String triggerKey : effectsSection.getKeys(false)) {
                TriggerConfig config = TriggerConfig.fromConfig(effectsSection.getConfigurationSection(triggerKey));
                if (config != null) {
                    effects.put(triggerKey, config);
                }
            }
        }
        sigil.setEffects(effects);

        // Load item form
        ConfigurationSection itemFormSection = section.getConfigurationSection("item_form");
        if (itemFormSection != null) {
            sigil.setItemForm(ItemForm.fromConfig(itemFormSection));
        }

        return sigil;
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

            form.setModelData(section.getInt("model_data", 0));
            form.setName(section.getString("name", "Sigil Shard"));
            form.setLore(section.getStringList("lore"));
            form.setGlow(section.getBoolean("glow", false) || section.getBoolean("enchant_glow", false));

            return form;
        }
    }
}
