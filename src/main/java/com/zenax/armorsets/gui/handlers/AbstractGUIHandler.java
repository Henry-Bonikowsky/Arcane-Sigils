package com.zenax.armorsets.gui.handlers;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Abstract base class for GUI handlers providing common functionality.
 * All concrete handlers should extend this class.
 */
public abstract class AbstractGUIHandler implements GUIHandler {

    protected final ArmorSetsPlugin plugin;
    protected final GUIHandlerContext context;

    /**
     * Create a new handler with plugin access.
     *
     * @param plugin  The plugin instance
     * @param context The handler context for accessing GUIManager methods
     */
    public AbstractGUIHandler(ArmorSetsPlugin plugin, GUIHandlerContext context) {
        this.plugin = plugin;
        this.context = context;
    }

    @Override
    public abstract Set<GUIType> getSupportedTypes();

    // ===== SOUND HELPERS =====

    /**
     * Play a GUI sound for the player.
     *
     * @param player    The player to play the sound for
     * @param soundType The type of sound (open, close, click, socket, unsocket, error)
     */
    protected void playSound(Player player, String soundType) {
        String soundName = plugin.getConfigManager().getMainConfig()
                .getString("gui.sounds." + soundType, "BLOCK_NOTE_BLOCK_PLING");
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    // ===== ITEM CREATION HELPERS =====

    /**
     * Create a GUI item with name and lore.
     *
     * @param material  The item material
     * @param name      The display name (color codes supported)
     * @param loreLines The lore lines (color codes supported)
     * @return The created ItemStack
     */
    protected ItemStack createGuiItem(Material material, String name, String... loreLines) {
        return ItemBuilder.createGuiItem(material, name, loreLines);
    }

    /**
     * Create a border/filler item.
     *
     * @return Black stained glass pane with empty name
     */
    protected ItemStack createBorderItem() {
        return ItemBuilder.createBorderItem();
    }

    // ===== RARITY HELPERS =====

    /**
     * Get the color code for a rarity level.
     *
     * @param rarity The rarity string
     * @return The color code
     */
    protected String getRarityColor(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> "&7";
            case "UNCOMMON" -> "&a";
            case "RARE" -> "&9";
            case "EPIC" -> "&5";
            case "LEGENDARY" -> "&6";
            case "MYTHIC" -> "&d";
            default -> "&7";
        };
    }

    // ===== ROMAN NUMERAL HELPERS =====

    /**
     * Convert an integer to a Roman numeral.
     *
     * @param num The number to convert (1-10)
     * @return The Roman numeral string
     */
    protected String toRomanNumeral(int num) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (num >= 1 && num <= 10) return numerals[num - 1];
        return String.valueOf(num);
    }

    // ===== ARMOR SLOT HELPERS =====

    /**
     * Update armor in player's inventory at the specified slot.
     *
     * @param player The player
     * @param slot   The inventory slot
     * @param armor  The armor item
     */
    protected void updateArmorInInventory(Player player, int slot, ItemStack armor) {
        switch (slot) {
            case 39 -> player.getInventory().setHelmet(armor);
            case 38 -> player.getInventory().setChestplate(armor);
            case 37 -> player.getInventory().setLeggings(armor);
            case 36 -> player.getInventory().setBoots(armor);
            default -> {
                if (slot >= 0 && slot <= 8) {
                    player.getInventory().setItem(slot, armor);
                } else {
                    player.getInventory().setItemInMainHand(armor);
                }
            }
        }
    }

    // ===== TRIGGER HELPERS =====

    /**
     * Get appropriate material for a trigger type.
     *
     * @param triggerKey The trigger key
     * @return The material to represent this trigger
     */
    protected Material getTriggerMaterial(String triggerKey) {
        String key = triggerKey.toLowerCase().replace("on_", "");
        return switch (key) {
            case "attack" -> Material.DIAMOND_SWORD;
            case "defense" -> Material.SHIELD;
            case "kill_mob" -> Material.ZOMBIE_HEAD;
            case "kill_player" -> Material.PLAYER_HEAD;
            case "shift" -> Material.LEATHER_BOOTS;
            case "fall_damage" -> Material.FEATHER;
            case "effect_static" -> Material.BEACON;
            case "bow_hit", "bow_shoot" -> Material.BOW;
            case "block_break" -> Material.IRON_PICKAXE;
            case "block_place" -> Material.GRASS_BLOCK;
            case "interact" -> Material.STICK;
            case "trident_throw" -> Material.TRIDENT;
            default -> Material.REDSTONE_TORCH;
        };
    }
}
