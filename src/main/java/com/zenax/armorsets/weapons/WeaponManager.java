package com.zenax.armorsets.weapons;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
 * Manages all custom weapons in the plugin.
 */
public class WeaponManager {

    private final ArmorSetsPlugin plugin;
    private final Map<String, CustomWeapon> weapons = new HashMap<>();
    private final NamespacedKey WEAPON_ID_KEY;

    public WeaponManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.WEAPON_ID_KEY = new NamespacedKey(plugin, "weapon_id");
    }

    public void loadWeapons() {
        weapons.clear();

        for (Map.Entry<String, FileConfiguration> entry : plugin.getConfigManager().getWeaponConfigs().entrySet()) {
            FileConfiguration config = entry.getValue();

            for (String key : config.getKeys(false)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) continue;

                CustomWeapon weapon = CustomWeapon.fromConfig(key, section);
                if (weapon != null) {
                    weapons.put(key.toLowerCase(), weapon);
                }
            }
        }

        plugin.getLogger().info("Loaded " + weapons.size() + " custom weapons");
    }

    public CustomWeapon getWeapon(String id) {
        return weapons.get(id.toLowerCase());
    }

    public CustomWeapon getWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();

        // Check PDC first
        String weaponId = meta.getPersistentDataContainer().get(WEAPON_ID_KEY, PersistentDataType.STRING);
        if (weaponId != null) {
            return getWeapon(weaponId);
        }

        // Fallback to name matching
        if (meta.hasDisplayName()) {
            String name = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            for (CustomWeapon weapon : weapons.values()) {
                if (weapon.getName().equalsIgnoreCase(name) ||
                    name.toLowerCase().contains(weapon.getId().toLowerCase())) {
                    return weapon;
                }
            }
        }

        return null;
    }

    public Collection<CustomWeapon> getAllWeapons() {
        return weapons.values();
    }

    public int getWeaponCount() {
        return weapons.size();
    }

    public NamespacedKey getWeaponIdKey() {
        return WEAPON_ID_KEY;
    }

    public ItemStack createWeaponItem(CustomWeapon weapon) {
        ItemStack item = new ItemStack(weapon.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(TextUtil.parseComponent(weapon.getName()));

        List<Component> lore = new ArrayList<>();
        if (weapon.getRequiredSet() != null) {
            lore.add(TextUtil.parseComponent("&8Requires: &d" + weapon.getRequiredSet()));
        }

        if (!weapon.getEvents().isEmpty()) {
            lore.add(Component.empty());
            lore.add(TextUtil.parseComponent("&b&lAbilities:"));
            for (var entry : weapon.getEvents().entrySet()) {
                String trigger = entry.getKey().replace("on_", "").replace("_", " ");
                String cap = TextUtil.toProperCase(trigger);
                lore.add(TextUtil.parseComponent("&b• &3" + cap + " &8- &a" + entry.getValue().getEffects().size() + " effect(s)"));
            }
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(WEAPON_ID_KEY, PersistentDataType.STRING, weapon.getId());
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
