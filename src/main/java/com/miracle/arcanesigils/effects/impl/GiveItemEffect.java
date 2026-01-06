package com.miracle.arcanesigils.effects.impl;

import com.miracle.arcanesigils.effects.EffectContext;
import com.miracle.arcanesigils.effects.EffectParams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Gives items to the target player.
 * Supports standard materials, custom items by ID, and random amount ranges.
 *
 * Format: GIVE_ITEM:MATERIAL:AMOUNT @Target
 * Format: GIVE_ITEM:MATERIAL:MIN-MAX @Target (random amount)
 * Format: GIVE_ITEM:CUSTOM:item_id:AMOUNT @Target (custom registered item)
 *
 * Examples:
 *   GIVE_ITEM:DIAMOND:1 @Self
 *   GIVE_ITEM:GOLDEN_APPLE:1-3 @Self
 *   GIVE_ITEM:CUSTOM:sigil_fragment:1-5 @Self
 */
public class GiveItemEffect extends AbstractEffect {

    public GiveItemEffect() {
        super("GIVE_ITEM", "Gives items to target player");
    }

    @Override
    public EffectParams parseParams(String effectString) {
        EffectParams params = super.parseParams(effectString);

        String cleanedString = effectString.replaceAll("\\s+@\\w+(?::\\d+)?$", "").trim();
        String[] parts = cleanedString.split(":");

        // GIVE_ITEM:MATERIAL:AMOUNT - supports both positional and key=value
        params.set("amount", 1);

        int positionalIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].toLowerCase();
                    String value = kv[1];
                    switch (key) {
                        case "material", "item" -> params.set("material", value.toUpperCase());
                        case "amount", "count" -> parseAmount(params, value);
                        case "custom" -> params.set("custom", Boolean.parseBoolean(value));
                        case "custom_id", "id" -> params.set("custom_id", value);
                    }
                }
            } else {
                positionalIndex++;
                // GIVE_ITEM:MATERIAL:AMOUNT or GIVE_ITEM:CUSTOM:item_id:AMOUNT
                if (positionalIndex == 1) {
                    String typeOrMaterial = part.toUpperCase();
                    if (typeOrMaterial.equals("CUSTOM")) {
                        params.set("custom", true);
                    } else {
                        params.set("material", typeOrMaterial);
                    }
                } else if (positionalIndex == 2) {
                    if (params.getBoolean("custom", false)) {
                        params.set("custom_id", part);
                    } else {
                        parseAmount(params, part);
                    }
                } else if (positionalIndex == 3 && params.getBoolean("custom", false)) {
                    parseAmount(params, part);
                }
            }
        }

        return params;
    }

    private void parseAmount(EffectParams params, String amountStr) {
        if (amountStr.contains("-")) {
            // Random range format: MIN-MAX
            String[] range = amountStr.split("-");
            try {
                params.set("amount_min", Integer.parseInt(range[0]));
                params.set("amount_max", Integer.parseInt(range[1]));
            } catch (NumberFormatException e) {
                params.set("amount", 1);
            }
        } else {
            try {
                params.set("amount", Integer.parseInt(amountStr));
            } catch (NumberFormatException e) {
                params.set("amount", 1);
            }
        }
    }

    @Override
    public boolean execute(EffectContext context) {
        EffectParams params = context.getParams();
        if (params == null) return false;

        // Get target - must be a player
        LivingEntity targetEntity = getTarget(context);
        if (!(targetEntity instanceof Player target)) {
            debug("GIVE_ITEM target is not a player");
            return false;
        }

        // Calculate amount
        int amount;
        if (params.has("amount_min") && params.has("amount_max")) {
            int min = params.getInt("amount_min", 1);
            int max = params.getInt("amount_max", 1);
            amount = ThreadLocalRandom.current().nextInt(min, max + 1);
        } else {
            amount = params.getInt("amount", 1);
        }

        if (amount <= 0) {
            debug("GIVE_ITEM amount is 0 or negative");
            return false;
        }

        ItemStack item;

        // Check if custom item
        if (params.getBoolean("custom", false)) {
            String customId = params.getString("custom_id", "");
            item = createCustomItem(customId, amount);
            if (item == null) {
                debug("Unknown custom item ID: " + customId);
                return false;
            }
        } else {
            // Standard material
            String materialName = params.getString("material", "DIAMOND");
            Material material;
            try {
                material = Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                debug("Unknown material: " + materialName);
                return false;
            }

            item = new ItemStack(material, amount);
        }

        // Give item to player
        var leftover = target.getInventory().addItem(item);

        // Drop any items that didn't fit
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
            debug("Gave " + amount + " items, " + leftover.values().stream().mapToInt(ItemStack::getAmount).sum() + " dropped");
        } else {
            debug("Gave " + amount + " " + item.getType() + " to " + target.getName());
        }

        return true;
    }

    /**
     * Creates a custom item by ID.
     * This can be extended to look up items from a registry.
     */
    private ItemStack createCustomItem(String customId, int amount) {
        // Built-in custom items
        return switch (customId.toLowerCase()) {
            case "sigil_fragment", "fragment" -> {
                ItemStack item = new ItemStack(Material.PRISMARINE_SHARD, amount);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&d&lSigil Fragment"));
                    meta.lore(java.util.List.of(
                        LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&7A mysterious fragment of arcane power."),
                        LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&7Can be used to upgrade sigils.")
                    ));
                    item.setItemMeta(meta);
                }
                yield item;
            }
            case "arcane_dust", "dust" -> {
                ItemStack item = new ItemStack(Material.GLOWSTONE_DUST, amount);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&b&lArcane Dust"));
                    meta.lore(java.util.List.of(
                        LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&7Shimmering magical dust."),
                        LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&7Used in sigil crafting.")
                    ));
                    item.setItemMeta(meta);
                }
                yield item;
            }
            case "ancient_coin", "coin" -> {
                ItemStack item = new ItemStack(Material.GOLD_NUGGET, amount);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&6&lAncient Coin"));
                    meta.lore(java.util.List.of(
                        LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&7A coin from a forgotten age."),
                        LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&7Trade with mysterious vendors.")
                    ));
                    item.setItemMeta(meta);
                }
                yield item;
            }
            default -> null;
        };
    }
}
