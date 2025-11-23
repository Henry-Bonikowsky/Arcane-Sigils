package com.zenax.armorsets.sets;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Manages all Armor Sets in the plugin.
 */
public class SetManager {

    private final ArmorSetsPlugin plugin;
    private final Map<String, ArmorSet> sets = new HashMap<>();
    private final Map<UUID, ArmorSet> activeSetCache = new HashMap<>();

    private final NamespacedKey SET_ID_KEY;
    private final NamespacedKey ARMOR_SLOT_KEY;

    public SetManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.SET_ID_KEY = new NamespacedKey(plugin, "set_id");
        this.ARMOR_SLOT_KEY = new NamespacedKey(plugin, "armor_slot");
    }

    /**
     * Load all armor sets from configuration.
     * Each set config creates ONE set. Tier is a property, not separate items.
     */
    public void loadSets() {
        sets.clear();
        activeSetCache.clear();

        for (Map.Entry<String, FileConfiguration> entry : plugin.getConfigManager().getSetConfigs().entrySet()) {
            FileConfiguration config = entry.getValue();

            for (String setKey : config.getKeys(false)) {
                ConfigurationSection setSection = config.getConfigurationSection(setKey);
                if (setSection == null) continue;

                // Load the set (tier is a property, starts at 1)
                ArmorSet set = ArmorSet.fromConfig(setKey, setSection);
                if (set != null) {
                    sets.put(set.getId().toLowerCase(), set);
                }
            }
        }

        plugin.getLogger().info("Loaded " + sets.size() + " armor sets");
    }

    /**
     * Get an armor set by ID.
     */
    public ArmorSet getSet(String id) {
        return sets.get(id.toLowerCase());
    }

    /**
     * Get all armor sets.
     */
    public Collection<ArmorSet> getAllSets() {
        return sets.values();
    }

    /**
     * Get the number of loaded sets.
     */
    public int getSetCount() {
        return sets.size();
    }

    /**
     * Get the active armor set for a player.
     * This checks what set pieces the player is wearing and returns the matching set.
     */
    public ArmorSet getActiveSet(Player player) {
        // Check cache first
        ArmorSet cached = activeSetCache.get(player.getUniqueId());
        if (cached != null) {
            // Validate cache is still accurate
            if (isWearingSet(player, cached)) {
                return cached;
            }
            activeSetCache.remove(player.getUniqueId());
        }

        // Detect current set
        ArmorSet detectedSet = detectSet(player);
        if (detectedSet != null) {
            activeSetCache.put(player.getUniqueId(), detectedSet);
        }

        return detectedSet;
    }

    /**
     * Check if player has a full set equipped.
     */
    public boolean hasFullSet(Player player, ArmorSet set) {
        if (set == null) return false;

        ItemStack[] armor = player.getInventory().getArmorContents();
        int matchingPieces = 0;

        for (ItemStack piece : armor) {
            if (piece != null && !piece.getType().isAir()) {
                String setId = getArmorSetId(piece);
                if (set.getId().equalsIgnoreCase(setId)) {
                    matchingPieces++;
                }
            }
        }

        return matchingPieces >= 4;
    }

    /**
     * Check if player is wearing any pieces from a set.
     */
    public boolean isWearingSet(Player player, ArmorSet set) {
        if (set == null) return false;

        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack piece : armor) {
            if (piece != null && !piece.getType().isAir()) {
                String setId = getArmorSetId(piece);
                if (set.getId().equalsIgnoreCase(setId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Detect which set the player is wearing.
     */
    private ArmorSet detectSet(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();

        // Count pieces per set
        Map<String, Integer> setCounts = new HashMap<>();

        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir()) continue;

            String setId = getArmorSetId(piece);
            if (setId != null) {
                setCounts.merge(setId, 1, Integer::sum);
            }
        }

        // Return set with most pieces (prioritize full sets)
        String bestSetId = null;
        int bestCount = 0;

        for (Map.Entry<String, Integer> entry : setCounts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestSetId = entry.getKey();
            }
        }

        return bestSetId != null ? getSet(bestSetId) : null;
    }

    /**
     * Get the display name of an item.
     */
    private String getItemName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta.hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }

        return item.getType().name();
    }

    /**
     * Count matching set pieces.
     */
    public int countSetPieces(Player player, ArmorSet set) {
        if (set == null) return 0;

        ItemStack[] armor = player.getInventory().getArmorContents();
        int count = 0;

        for (ItemStack piece : armor) {
            if (piece != null && !piece.getType().isAir()) {
                String setId = getArmorSetId(piece);
                if (set.getId().equalsIgnoreCase(setId)) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Clear the active set cache for a player.
     */
    public void clearCache(Player player) {
        activeSetCache.remove(player.getUniqueId());
    }

    /**
     * Clear all caches.
     */
    public void clearAllCaches() {
        activeSetCache.clear();
    }

    /**
     * Send set equipped/unequipped messages.
     */
    public void sendSetMessage(Player player, ArmorSet set, boolean equipped) {
        List<String> messages = equipped ? set.getEquippedMessage() : set.getUnequippedMessage();
        for (String message : messages) {
            player.sendMessage(TextUtil.colorize(message));
        }
    }

    /**
     * Get sets by base name (ignoring tier).
     */
    public List<ArmorSet> getSetsByBaseName(String baseName) {
        List<ArmorSet> result = new ArrayList<>();
        String prefix = baseName.toLowerCase() + "_t";

        for (Map.Entry<String, ArmorSet> entry : sets.entrySet()) {
            if (entry.getKey().startsWith(prefix) || entry.getKey().equals(baseName.toLowerCase())) {
                result.add(entry.getValue());
            }
        }

        return result;
    }

    /**
     * Build lore lines for armor piece showing set abilities.
     */
    public List<String> buildArmorLore(ArmorSet set, String slot) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Part of the &d" + set.getId().replace("_t", " Tier ") + " &7set");
        lore.add("&8Tier " + set.getTier());

        var slotEffects = set.getIndividualEffects().get(slot.toLowerCase());
        if (slotEffects != null && !slotEffects.isEmpty()) {
            lore.add("");
            lore.add("&b&lPiece Abilities:");
            for (var entry : slotEffects.entrySet()) {
                String triggerKey = entry.getKey().replace("on_", "");
                String trigger = triggerKey.replace("_", " ");
                String cap = TextUtil.toProperCase(trigger);
                String triggerDesc = TextUtil.getTriggerDescription(triggerKey);
                lore.add("&b• &3" + cap);
                lore.add("&7  " + TextUtil.toProperCase(triggerDesc));
                for (String effect : entry.getValue().getEffects()) {
                    String effectDesc = TextUtil.getEffectDescription(effect);
                    lore.add("&8    →&7 " + TextUtil.toProperCase(effectDesc));
                }
            }
        }

        if (!set.getSynergies().isEmpty()) {
            lore.add("");
            lore.add("&b&lSet Synergies &8(Full Set):");
            for (var synergy : set.getSynergies()) {
                String triggerKey = synergy.getTrigger().getConfigKey();
                String trigger = triggerKey.replace("_", " ").toLowerCase();
                String cap = TextUtil.toProperCase(trigger);
                String triggerDesc = TextUtil.getTriggerDescription(triggerKey);
                lore.add("&b• &3" + synergy.getId());
                lore.add("&7  " + TextUtil.toProperCase(triggerDesc));
                for (String effect : synergy.getTriggerConfig().getEffects()) {
                    String effectDesc = TextUtil.getEffectDescription(effect);
                    lore.add("&8    →&7 " + TextUtil.toProperCase(effectDesc));
                }
            }
        }

        return lore;
    }

    /**
     * Set armor set data on an item using PDC.
     */
    public void setArmorSetData(ItemStack item, ArmorSet set, String slot) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(SET_ID_KEY, PersistentDataType.STRING, set.getId());
        meta.getPersistentDataContainer().set(ARMOR_SLOT_KEY, PersistentDataType.STRING, slot);
        item.setItemMeta(meta);
    }

    /**
     * Get set ID from armor item.
     */
    public String getArmorSetId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(SET_ID_KEY, PersistentDataType.STRING);
    }

    /**
     * Get armor slot from item.
     */
    public String getArmorSlot(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(ARMOR_SLOT_KEY, PersistentDataType.STRING);
    }

    /**
     * Check if item is armor from a set (via PDC).
     */
    public boolean isSetArmor(ItemStack item) {
        return getArmorSetId(item) != null;
    }

    public NamespacedKey getSetIdKey() {
        return SET_ID_KEY;
    }

    public NamespacedKey getArmorSlotKey() {
        return ARMOR_SLOT_KEY;
    }
}
