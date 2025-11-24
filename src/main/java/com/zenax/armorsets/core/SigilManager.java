package com.zenax.armorsets.core;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.sets.TriggerConfig;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages all Sigils in the plugin.
 */
public class SigilManager {

    private final ArmorSetsPlugin plugin;
    private final Map<String, Sigil> sigils = new HashMap<>();

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
                }
            }
        }

        plugin.getLogger().info("Loaded " + sigils.size() + " base sigils");
    }

    /**
     * Get a sigil by ID, optionally with a specific tier applied.
     */
    public Sigil getSigilWithTier(String id, int tier) {
        Sigil base = sigils.get(id.toLowerCase());
        if (base == null) return null;

        // Clone the sigil and apply tier
        Sigil tiered = cloneSigil(base);
        tier = Math.max(1, Math.min(tier, tiered.getMaxTier()));
        tiered.setTier(tier);
        scaleEffects(tiered, tier, tiered.getMaxTier());
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

        // Deep copy effects
        Map<String, TriggerConfig> clonedEffects = new java.util.HashMap<>();
        for (Map.Entry<String, TriggerConfig> e : original.getEffects().entrySet()) {
            clonedEffects.put(e.getKey(), cloneTriggerConfig(e.getValue()));
        }
        clone.setEffects(clonedEffects);

        return clone;
    }

    /**
     * Clone a trigger config.
     */
    private TriggerConfig cloneTriggerConfig(TriggerConfig original) {
        TriggerConfig clone = new TriggerConfig();
        clone.setChance(original.getChance());
        clone.setBaseChance(original.getBaseChance());
        clone.setCooldown(original.getCooldown());
        clone.setBaseCooldown(original.getBaseCooldown());
        clone.setTriggerMode(original.getTriggerMode());
        clone.setEffects(new java.util.ArrayList<>(original.getEffects()));
        clone.setConditions(new java.util.ArrayList<>(original.getConditions()));
        return clone;
    }

    private void scaleEffects(Sigil sigil, int tier, int maxTier) {
        // Scale so tier 1 = 60% and max tier = 150%
        double effectMultiplier = 0.5 + (tier * (1.0 / maxTier));

        for (TriggerConfig config : sigil.getEffects().values()) {
            // Scale effect values
            List<String> scaled = config.getEffects().stream()
                .map(e -> scaleEffectString(e, effectMultiplier))
                .toList();
            config.setEffects(new java.util.ArrayList<>(scaled));

            // Scale chance OR cooldown based on trigger mode (not both)
            if (config.getTriggerMode() == TriggerConfig.TriggerMode.CHANCE) {
                // CHANCE mode: each tier adds the base_chance percentage
                // e.g., 20% base -> T1:20%, T2:40%, T3:60%, T4:80%, T5:100%
                double baseChance = config.getBaseChance();
                double scaledChance = baseChance * tier;
                config.setChance(Math.min(100, scaledChance));
                config.setCooldown(0); // No cooldown for chance-based
            } else {
                // COOLDOWN mode: scale from baseCooldown at tier 1 to 20% of base at maxTier
                double baseCooldown = config.getBaseCooldown();
                double minCooldown = baseCooldown * 0.2; // Min is 20% of base
                double scaledCooldown = baseCooldown - ((baseCooldown - minCooldown) * (tier - 1) / Math.max(1, maxTier - 1));
                if (maxTier == 1) scaledCooldown = baseCooldown;
                config.setCooldown(Math.max(minCooldown, scaledCooldown));
                config.setChance(100); // Always 100% chance for cooldown-based
            }
        }
    }

    private String scaleEffectString(String effect, double multiplier) {
        // Scale numeric values in effects like "DAMAGE:10" -> "DAMAGE:16"
        String[] parts = effect.split(":");
        if (parts.length < 2) return effect;

        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            result.append(":");
            String part = parts[i];
            // Check if contains target specifier
            if (part.contains("@")) {
                String[] subParts = part.split(" ");
                try {
                    double val = Double.parseDouble(subParts[0]);
                    result.append((int) Math.round(val * multiplier));
                    for (int j = 1; j < subParts.length; j++) {
                        result.append(" ").append(subParts[j]);
                    }
                } catch (NumberFormatException e) {
                    result.append(part);
                }
            } else {
                try {
                    double val = Double.parseDouble(part);
                    result.append((int) Math.round(val * multiplier));
                } catch (NumberFormatException e) {
                    result.append(part);
                }
            }
        }
        return result.toString();
    }

    /**
     * Get a sigil by ID.
     */
    public Sigil getSigil(String id) {
        return sigils.get(id.toLowerCase());
    }

    /**
     * Get all sigils.
     */
    public Collection<Sigil> getAllSigils() {
        return sigils.values();
    }

    /**
     * Get the number of loaded sigils.
     */
    public int getSigilCount() {
        return sigils.size();
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
        meta.displayName(TextUtil.parseComponent(rarityColor + baseName + " " + romanNumeral));

        // Build lore following enchantment format
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();

        // Line 1: Rarity
        lore.add(TextUtil.parseComponent(rarityColor + "&l" + rarity));

        // Line 2: Empty
        lore.add(net.kyori.adventure.text.Component.empty());

        // Track longest line for bottom border
        int maxLength = TextUtil.toProperCase(sigil.getSlot()).length() + 2; // "─ " + slot name

        // Line 3: What armor it can be applied to
        lore.add(TextUtil.parseComponent("&8┌─ &a" + TextUtil.toProperCase(sigil.getSlot())));

        // Line 4+: Description (from sigil description or generated from effects)
        if (!sigil.getDescription().isEmpty()) {
            for (String desc : sigil.getDescription()) {
                lore.add(TextUtil.parseComponent("&8│ &f" + desc));
                maxLength = Math.max(maxLength, desc.length() + 2); // "│ " prefix
            }
        } else if (!sigil.getEffects().isEmpty()) {
            // Generate description from effects
            for (String triggerKey : sigil.getEffects().keySet()) {
                var triggerConfig = sigil.getEffects().get(triggerKey);
                for (String effect : triggerConfig.getEffects()) {
                    String effectDesc = TextUtil.getEffectDescription(effect);
                    lore.add(TextUtil.parseComponent("&8│ &f" + effectDesc));
                    maxLength = Math.max(maxLength, effectDesc.length() + 2);
                }
            }
        }

        // Build bottom border to match longest line
        // The ─ character is narrower than text, so divide by ~2 for visual match
        int borderLength = (int) Math.ceil(maxLength / 1.8);
        StringBuilder border = new StringBuilder("&8└");
        for (int i = 0; i < borderLength - 1; i++) {
            border.append("─");
        }
        lore.add(TextUtil.parseComponent(border.toString()));

        lore.add(net.kyori.adventure.text.Component.empty());

        lore.add(TextUtil.parseComponent("&8Drag and drop to socket sigil"));

        meta.lore(lore);

        // Set custom model data
        if (itemForm.getModelData() > 0) {
            meta.setCustomModelData(itemForm.getModelData());
        }

        // Add glow effect
        if (itemForm.isGlow()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

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
     * Get rarity color code based on rarity name.
     */
    private String getRarityColor(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> "&7";      // Gray
            case "UNCOMMON" -> "&a";    // Green
            case "RARE" -> "&9";        // Blue
            case "EPIC" -> "&5";        // Purple
            case "LEGENDARY" -> "&6";   // Gold
            case "MYTHIC" -> "&d";      // Pink
            default -> "&7";
        };
    }
}
