package com.miracle.arcanesigils.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.utils.TextUtil;

import net.kyori.adventure.text.Component;

public class SocketManager implements Listener {

    private final ArmorSetsPlugin plugin;
    private final NamespacedKey SOCKETED_SIGILS_KEY;

    // Roman numerals for display
    private static final String[] ROMAN_NUMERALS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    // Enchantment display order per item type
    private static final Map<Enchantment, Integer> SWORD_ENCHANT_ORDER = Map.of(
        Enchantment.SHARPNESS, 0,
        Enchantment.FIRE_ASPECT, 1,
        Enchantment.LOOTING, 2,
        Enchantment.UNBREAKING, 3
    );

    private static final Map<Enchantment, Integer> ARMOR_ENCHANT_ORDER = Map.of(
        Enchantment.PROTECTION, 0,
        Enchantment.FIRE_PROTECTION, 1,
        Enchantment.PROJECTILE_PROTECTION, 2,
        Enchantment.UNBREAKING, 3
    );


    public SocketManager(ArmorSetsPlugin plugin) {
        this.plugin = plugin;
        this.SOCKETED_SIGILS_KEY = new NamespacedKey(plugin, "socketed_sigils");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // Check if holding a sigil item
        if (!plugin.getSigilManager().isSigilItem(mainHand)) return;

        Sigil sigil = plugin.getSigilManager().getSigilFromItem(mainHand);
        if (sigil == null) return;

        ItemStack targetItem = null;
        boolean isOffHandTarget = false;

        // Check if sigil can be socketed into tools/weapons/swords/axes (check off-hand first)
        Set<String> socketable = sigil.getSocketables();
        if (socketable.contains("tool") || socketable.contains("weapon") ||
            socketable.contains("axe") || socketable.contains("sword") || socketable.contains("bow")) {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand != null && !offHand.getType().isAir() && isSocketable(offHand.getType())) {
                // Use the unified canSigilSocketInto method for consistency
                if (canSigilSocketInto(sigil, offHand.getType())) {
                    targetItem = offHand;
                    isOffHandTarget = true;
                }
            }
        }

        // If no valid off-hand target, check for worn armor
        if (targetItem == null) {
            // Try to find matching worn armor piece for this sigil's slot
            for (String socketableType : sigil.getSocketables()) {
                ItemStack armorPiece = getWornArmorBySlot(player, socketableType.toUpperCase());
                if (armorPiece != null && !armorPiece.getType().isAir()) {
                    targetItem = armorPiece;
                    break;
                }
            }
        }

        if (targetItem == null) {
            String itemTypes = String.join(", ", sigil.getSocketables());
            player.sendMessage(TextUtil.colorize("§cYou must be holding or wearing a valid item (" + itemTypes + ") to socket this sigil!"));
            return;
        }

        event.setCancelled(true);

        // Try to socket
        SocketResult result = socketSigil(player, targetItem, sigil);
        handleSocketResult(player, result, sigil);

        if (result == SocketResult.SUCCESS) {
            mainHand.setAmount(mainHand.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.3f, 1f);

            // Update the item in its slot
            if (isOffHandTarget) {
                player.getInventory().setItemInOffHand(targetItem);
            } else {
                // Update armor slot
                for (String socketableType : sigil.getSocketables()) {
                    if (getWornArmorBySlot(player, socketableType.toUpperCase()) != null) {
                        setWornArmorBySlot(player, socketableType.toUpperCase(), targetItem);
                        // Force inventory update to client
                        player.getInventory().setArmorContents(player.getInventory().getArmorContents());
                        break;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;

        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        // Check if cursor has sigil and clicked is socketable item
        if (cursor != null && !cursor.getType().isAir() && plugin.getSigilManager().isSigilItem(cursor)) {
            if (clicked != null && !clicked.getType().isAir() && isSocketable(clicked.getType())) {
                Sigil sigil = plugin.getSigilManager().getSigilFromItem(cursor);
                if (sigil == null) return;

                event.setCancelled(true);
                SocketResult result = socketSigil(player, clicked, sigil);
                handleSocketResult(player, result, sigil);

                if (result == SocketResult.SUCCESS) {
                    cursor.setAmount(cursor.getAmount() - 1);
                    event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.3f, 1f);
                }
            }
        }
    }

    private ItemStack getWornArmorBySlot(Player player, String slot) {
        return switch (slot.toUpperCase()) {
            case "HELMET" -> player.getInventory().getHelmet();
            case "CHESTPLATE" -> player.getInventory().getChestplate();
            case "LEGGINGS" -> player.getInventory().getLeggings();
            case "BOOTS" -> player.getInventory().getBoots();
            default -> null;
        };
    }

    private void setWornArmorBySlot(Player player, String slot, ItemStack armor) {
        switch (slot.toUpperCase()) {
            case "HELMET" -> player.getInventory().setHelmet(armor);
            case "CHESTPLATE" -> player.getInventory().setChestplate(armor);
            case "LEGGINGS" -> player.getInventory().setLeggings(armor);
            case "BOOTS" -> player.getInventory().setBoots(armor);
        }
    }

    public SocketResult socketSigil(Player player, ItemStack item, Sigil sigil) {
        if (item == null || item.getType().isAir()) return SocketResult.INVALID_ITEM;
        if (!isSocketable(item.getType())) return SocketResult.INVALID_ITEM;

        // Check if sigil can be socketed into this item type
        if (!canSigilSocketInto(sigil, item.getType())) {
            return SocketResult.WRONG_SLOT;
        }

        if (!player.hasPermission("arcanesigils.socket")) return SocketResult.NO_PERMISSION;

        // Check if this sigil type is already socketed (ignore tier)
        List<String> currentSigils = getSocketedSigilData(item);
        String baseId = sigil.getId().toLowerCase();
        for (String entry : currentSigils) {
            String existingBase = entry.split(":")[0];
            if (existingBase.equals(baseId)) {
                return SocketResult.ALREADY_HAS_SIGIL;
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return SocketResult.INVALID_ITEM;

        // Add sigil to list with format "sigilId:tier"
        currentSigils.add(baseId + ":" + sigil.getTier());
        String sigilData = String.join(",", currentSigils);
        meta.getPersistentDataContainer().set(SOCKETED_SIGILS_KEY, PersistentDataType.STRING, sigilData);

        updateItemLore(meta, currentSigils, item.getType());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(false);
        item.setItemMeta(meta);
        return SocketResult.SUCCESS;
    }

    /**
     * Unsocket the last non-exclusive sigil from armor.
     * Exclusive sigils cannot be removed.
     */
    public Sigil unsocketSigil(Player player, ItemStack armor) {
        if (armor == null || armor.getType().isAir() || !armor.hasItemMeta()) return null;

        List<String> sigilData = getSocketedSigilData(armor);
        if (sigilData.isEmpty()) return null;

        // Find the last non-exclusive sigil to remove
        String entryToRemove = null;
        Sigil sigilToRemove = null;
        for (int i = sigilData.size() - 1; i >= 0; i--) {
            String[] parts = sigilData.get(i).split(":");
            String sigilId = parts[0];
            int tier = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            Sigil sigil = plugin.getSigilManager().getSigilWithTier(sigilId, tier);
            if (sigil != null && !sigil.isExclusive()) {
                entryToRemove = sigilData.get(i);
                sigilToRemove = sigil;
                break;
            }
        }

        if (entryToRemove == null) return null; // All sigils are exclusive

        sigilData.remove(entryToRemove);

        ItemMeta meta = armor.getItemMeta();
        if (sigilData.isEmpty()) {
            meta.getPersistentDataContainer().remove(SOCKETED_SIGILS_KEY);
        } else {
            meta.getPersistentDataContainer().set(SOCKETED_SIGILS_KEY, PersistentDataType.STRING, String.join(",", sigilData));
        }
        updateItemLore(meta, sigilData, armor.getType());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(false);
        armor.setItemMeta(meta);
        return sigilToRemove;
    }

    /**
     * Unsocket a specific sigil by ID from armor.
     * Returns null if the sigil is exclusive (cannot be removed).
     */
    public Sigil unsocketSigilById(Player player, ItemStack armor, String sigilId) {
        if (armor == null || armor.getType().isAir() || !armor.hasItemMeta()) return null;

        Sigil baseSigil = plugin.getSigilManager().getSigil(sigilId);
        if (baseSigil == null || baseSigil.isExclusive()) return null; // Cannot remove exclusive sigils

        List<String> sigilData = getSocketedSigilData(armor);
        String entryToRemove = null;
        Sigil sigilToRemove = null;

        for (String entry : sigilData) {
            String[] parts = entry.split(":");
            if (parts[0].equalsIgnoreCase(sigilId)) {
                entryToRemove = entry;
                int tier = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                sigilToRemove = plugin.getSigilManager().getSigilWithTier(sigilId, tier);
                break;
            }
        }

        if (entryToRemove == null) return null;
        sigilData.remove(entryToRemove);

        ItemMeta meta = armor.getItemMeta();
        if (sigilData.isEmpty()) {
            meta.getPersistentDataContainer().remove(SOCKETED_SIGILS_KEY);
        } else {
            meta.getPersistentDataContainer().set(SOCKETED_SIGILS_KEY, PersistentDataType.STRING, String.join(",", sigilData));
        }
        updateItemLore(meta, sigilData, armor.getType());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setEnchantmentGlintOverride(false);
        armor.setItemMeta(meta);
        return sigilToRemove;
    }

    /**
     * Get raw sigil data from armor (format: "sigilId:tier,sigilId:tier").
     */
    public List<String> getSocketedSigilData(ItemStack armor) {
        if (armor == null || !armor.hasItemMeta()) return new ArrayList<>();

        String data = armor.getItemMeta().getPersistentDataContainer().get(SOCKETED_SIGILS_KEY, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return new ArrayList<>();

        return new ArrayList<>(Arrays.asList(data.split(",")));
    }

    /**
     * Get all socketed sigils from armor with their tiers.
     */
    public List<Sigil> getSocketedSigils(ItemStack armor) {
        List<Sigil> sigils = new ArrayList<>();
        for (String entry : getSocketedSigilData(armor)) {
            String[] parts = entry.split(":");
            String sigilId = parts[0];
            int tier = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            Sigil sigil = plugin.getSigilManager().getSigilWithTier(sigilId, tier);
            if (sigil != null) sigils.add(sigil);
        }
        return sigils;
    }

    /**
     * Get the first socketed sigil (for backwards compatibility).
     */
    public Sigil getSocketedSigil(ItemStack armor) {
        List<Sigil> sigils = getSocketedSigils(armor);
        return sigils.isEmpty() ? null : sigils.get(0);
    }

    public boolean hasSocketedSigil(ItemStack armor) {
        return !getSocketedSigilData(armor).isEmpty();
    }

    private void updateItemLore(ItemMeta meta, List<String> sigilIds, Material material) {
        List<Component> sigilLore = new ArrayList<>();
        List<Component> enchantLore = new ArrayList<>();
        List<Component> otherLore = new ArrayList<>();
        Map<String, String> crateInfo = new java.util.LinkedHashMap<>(); // crateName -> lorePrefix

        // Get existing lore and filter out old sigil/enchant entries
        if (meta.hasLore()) {
            List<Component> existingLore = meta.lore();
            if (existingLore != null) {
                for (Component line : existingLore) {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);

                    // Skip old sigil lines (will be rebuilt)
                    if (plain.contains("➤") || plain.startsWith("▶") || plain.contains("☆") || plain.contains("⚖") ||
                        plain.contains("Sigils:") || plain.contains("[Sigil]") ||
                        plain.contains("Socketed:") || plain.contains("Exclusive") ||
                        plain.contains("Right-click with sigil shard")) {
                        continue;
                    }

                    // Skip old enchantment lines (will be rebuilt from meta)
                    if (plain.contains("⚔") || plain.contains("🛡") || plain.contains("✦")) {
                        continue;
                    }

                    otherLore.add(line);
                }
            }
        }

        // Collect sigils and separate exclusive from regular
        List<Sigil> exclusiveSigils = new ArrayList<>();
        List<Sigil> regularSigils = new ArrayList<>();

        for (String entry : sigilIds) {
            // Parse format "sigilId:tier"
            String[] parts = entry.split(":");
            String sigilId = parts[0];
            int tier = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            Sigil sigil = plugin.getSigilManager().getSigilWithTier(sigilId, tier);
            if (sigil != null) {
                if (sigil.isExclusive()) {
                    exclusiveSigils.add(sigil);
                    if (sigil.getCrate() != null) {
                        String prefix = sigil.getLorePrefix() != null ? sigil.getLorePrefix() : "<gradient:#FFD700:#CD853F>⚖</gradient>";
                        crateInfo.put(sigil.getCrate(), prefix);
                    }
                } else {
                    regularSigils.add(sigil);
                }
            }
        }

        // Sort both lists by rarity (highest first)
        exclusiveSigils.sort((a, b) -> getRarityOrder(b.getRarity()) - getRarityOrder(a.getRarity()));
        regularSigils.sort((a, b) -> getRarityOrder(b.getRarity()) - getRarityOrder(a.getRarity()));

        // Build sigil lore - exclusive sigils first with custom prefix (from YAML lore_prefix)
        for (Sigil sigil : exclusiveSigils) {
            String baseName = sigil.getName().replaceAll("\\s*&8\\[T\\d+\\]", "").trim();
            String roman = toRomanNumeral(sigil.getTier());
            // Use prefix from YAML with its own formatting (no rarity color override)
            String prefix = sigil.getLorePrefix() != null ? sigil.getLorePrefix() : "<gradient:#FFD700:#CD853F>⚖</gradient>";

            // Extract gradient end color for tier display
            String tierColor = "<color:#CD853F>"; // Default
            if (prefix.contains("<gradient:#")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("<gradient:#[A-Fa-f0-9]{6}:(#[A-Fa-f0-9]{6})>")
                    .matcher(prefix);
                if (matcher.find()) {
                    tierColor = "<color:" + matcher.group(1) + ">";
                }
            }

            // Make tier bold if at max tier
            boolean isMaxTier = sigil.getTier() >= sigil.getMaxTier();
            String tierFormat = isMaxTier ? tierColor + "<bold>" + roman + "</bold>" : tierColor + roman;

            sigilLore.add(TextUtil.parseComponent(prefix + " " + baseName + " " + tierFormat));
        }

        // Regular sigils with ➤ prefix (rarity color)
        for (Sigil sigil : regularSigils) {
            String baseName = sigil.getName().replaceAll("\\s*&8\\[T\\d+\\]", "").trim();
            String roman = toRomanNumeral(sigil.getTier());
            String rarityColor = getRarityColor(sigil.getRarity());

            // Make tier white and bold if at max tier, otherwise aqua
            boolean isMaxTier = sigil.getTier() >= sigil.getMaxTier();
            String tierFormat = isMaxTier ? "§f§l" + roman : "§b" + roman;

            sigilLore.add(TextUtil.parseComponent(rarityColor + "➤ " + baseName + " " + tierFormat));
        }

        // Build enchantment lore from item's actual enchantments (custom order)
        if (meta.hasEnchants()) {
            List<Map.Entry<Enchantment, Integer>> sortedEnchants = new ArrayList<>(meta.getEnchants().entrySet());
            Map<Enchantment, Integer> orderMap = isSword(material) ? SWORD_ENCHANT_ORDER : ARMOR_ENCHANT_ORDER;

            sortedEnchants.sort((a, b) -> {
                int orderA = orderMap.getOrDefault(a.getKey(), 100);
                int orderB = orderMap.getOrDefault(b.getKey(), 100);
                if (orderA != orderB) return Integer.compare(orderA, orderB);
                return a.getKey().getKey().getKey().compareTo(b.getKey().getKey().getKey());
            });

            for (var entry : sortedEnchants) {
                Enchantment enchant = entry.getKey();
                int level = entry.getValue();
                String enchantName = formatEnchantmentName(enchant);
                String roman = toRomanNumeral(level);
                enchantLore.add(TextUtil.parseComponent("§8➤ §7" + enchantName + " §b" + roman));
            }
            // Hide vanilla enchantment display
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // Build final lore order:
        // 1. Exclusive sigils (⚖)
        // 2. Regular sigils (➤)
        // 3. Enchantments (✦)
        // 4. Crate exclusive badge
        // 5. Other lore
        List<Component> finalLore = new ArrayList<>();

        // Add sigils at the top
        finalLore.addAll(sigilLore);

        // Add enchantments right after sigils
        finalLore.addAll(enchantLore);

        // Add crate exclusive badge after enchantments
        if (!crateInfo.isEmpty()) {
            for (Map.Entry<String, String> entry : crateInfo.entrySet()) {
                String crateName = entry.getKey();
                String prefix = entry.getValue();
                // Build crate line - extract color from prefix for trailing symbol
                // For gradients, use the SECOND color (end of gradient) for trailing symbol
                String trailingColor = "<color:#FFD700>"; // Default gold
                if (prefix != null) {
                    // Check for gradient: <gradient:#COLOR1:#COLOR2>
                    if (prefix.contains("<gradient:#")) {
                        // Extract the second color from gradient
                        java.util.regex.Matcher matcher = java.util.regex.Pattern
                            .compile("<gradient:#[A-Fa-f0-9]{6}:(#[A-Fa-f0-9]{6})>")
                            .matcher(prefix);
                        if (matcher.find()) {
                            trailingColor = "<color:" + matcher.group(1) + ">";
                        }
                    }
                    // Check for MiniMessage color tag: <color:#RRGGBB>
                    else if (prefix.contains("<color:#")) {
                        int start = prefix.indexOf("<color:#");
                        int end = prefix.indexOf(">", start);
                        if (end > start) {
                            trailingColor = prefix.substring(start, end + 1);
                        }
                    } else if (prefix.length() >= 2) {
                        // Legacy color codes
                        char firstChar = prefix.charAt(0);
                        if (firstChar == '&' || firstChar == '§') {
                            trailingColor = "&" + prefix.charAt(1);
                        }
                    }
                }
                finalLore.add(TextUtil.parseComponent(prefix + " " + crateName + " " + trailingColor + "⚖"));
            }
        }

        // Add separator and other lore if content exists
        if ((!sigilLore.isEmpty() || !enchantLore.isEmpty() || !crateInfo.isEmpty()) && !otherLore.isEmpty()) {
            finalLore.add(Component.empty());
        }

        // Add other lore (set info, descriptions, etc.)
        finalLore.addAll(otherLore);

        meta.lore(finalLore);
    }

    private String formatEnchantmentName(Enchantment enchant) {
        // Convert enchantment key to readable name
        String key = enchant.getKey().getKey();
        String[] words = key.split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (!name.isEmpty()) name.append(" ");
            name.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
        }
        return name.toString();
    }

    private String toRomanNumeral(int num) {
        if (num >= 1 && num <= 10) {
            return ROMAN_NUMERALS[num - 1];
        }
        return String.valueOf(num);
    }

    private String getRarityColor(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> "§7";
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            default -> "§7";
        };
    }

    private int getRarityOrder(String rarity) {
        return switch (rarity.toUpperCase()) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
    }

    public String getArmorSlot(Material material) {
        String name = material.name();
        if (name.contains("HELMET") || name.contains("CAP") || name.contains("SKULL") || name.contains("HEAD")) return "HELMET";
        if (name.contains("CHESTPLATE") || name.contains("TUNIC")) return "CHESTPLATE";
        if (name.contains("LEGGINGS") || name.contains("PANTS")) return "LEGGINGS";
        if (name.contains("BOOTS")) return "BOOTS";
        return "UNKNOWN";
    }

    public boolean isArmor(Material material) {
        String name = material.name();
        return name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS") || name.contains("BOOTS") || name.contains("CAP") || name.contains("TUNIC");
    }

    public boolean isTool(Material material) {
        String name = material.name();
        return name.contains("PICKAXE") || name.contains("SHOVEL") ||
               name.contains("HOE") || name.contains("SHEARS") || name.contains("FISHING_ROD") ||
               name.contains("FLINT_AND_STEEL") || name.contains("BRUSH") || isAxe(material);
    }

    public boolean isAxe(Material material) {
        String name = material.name();
        // Check for AXE but exclude PICKAXE
        return name.endsWith("_AXE");
    }

    public boolean isBow(Material material) {
        String name = material.name();
        // BOW and CROSSBOW are separate from melee weapons
        return name.equals("BOW") || name.equals("CROSSBOW");
    }

    public boolean isSword(Material material) {
        return material.name().contains("SWORD");
    }

    public boolean isWeapon(Material material) {
        String name = material.name();
        // Weapons exclude bows (bows are a separate category)
        // Includes swords, tridents, maces, and axes
        return name.contains("SWORD") || name.contains("TRIDENT") ||
               name.contains("MACE") || isAxe(material);
    }

    public boolean isOffhand(Material material) {
        String name = material.name();
        return name.equals("SHIELD") || name.contains("TOTEM") || name.contains("MAP") ||
               name.equals("COMPASS") || name.equals("CLOCK") || name.equals("SPYGLASS");
    }

    public boolean isSocketable(Material material) {
        return isArmor(material) || isTool(material) || isWeapon(material) || isBow(material) || isOffhand(material);
    }

    public String getItemType(Material material) {
        if (isArmor(material)) return getArmorSlot(material).toLowerCase();

        String name = material.name();

        // Specific tool types first
        if (name.contains("PICKAXE")) return "pickaxe";
        if (name.contains("SHOVEL")) return "shovel";
        if (name.contains("HOE")) return "hoe";
        if (name.equals("FISHING_ROD")) return "fishing_rod";
        if (isAxe(material)) return "axe";
        if (isSword(material)) return "sword";

        // Ranged weapons - specific first
        if (name.equals("CROSSBOW")) return "crossbow";
        if (name.equals("BOW")) return "bow";
        if (name.equals("TRIDENT")) return "trident";

        // General categories
        if (isTool(material)) return "tool";
        if (isWeapon(material)) return "weapon";
        if (isOffhand(material)) return "offhand";
        return "unknown";
    }

    /**
     * Check if a sigil can be socketed into an item of the given material.
     * Handles special cases like axes counting as both tools and weapons,
     * and specific tool types (pickaxe, shovel) also counting as "tool".
     */
    public boolean canSigilSocketInto(Sigil sigil, Material material) {
        if (sigil == null || material == null) return false;

        String itemType = getItemType(material);
        Set<String> socketable = sigil.getSocketables();

        // Direct match
        if (socketable.contains(itemType)) return true;

        // Axes count as both "tool" and "weapon"
        if (isAxe(material)) {
            return socketable.contains("tool") || socketable.contains("weapon") || socketable.contains("axe");
        }

        // Swords also count as "weapon"
        if (isSword(material)) {
            return socketable.contains("weapon");
        }

        // Specific tools also count as "tool"
        if (itemType.equals("pickaxe") || itemType.equals("shovel") ||
            itemType.equals("hoe") || itemType.equals("fishing_rod")) {
            return socketable.contains("tool");
        }

        // Crossbow also counts as "bow"
        if (itemType.equals("crossbow")) {
            return socketable.contains("bow");
        }

        // Trident also counts as "weapon"
        if (itemType.equals("trident")) {
            return socketable.contains("weapon");
        }

        return false;
    }

    private void handleSocketResult(Player player, SocketResult result, Sigil sigil) {
        String message = switch (result) {
            case SUCCESS -> "&a&lSocketed! &f" + sigil.getName() + " &aadded to your item!";
            case WRONG_SLOT -> "&cThis sigil can only be socketed into: " + String.join(", ", sigil.getSocketables()) + "!";
            case ALREADY_HAS_SIGIL -> "&cThis item already has this sigil!";
            case NO_PERMISSION -> "&cYou don't have permission to socket sigils!";
            case INVALID_ITEM -> "&cInvalid item!";
            case TIER_TOO_LOW -> "&cYour item tier is too low for this sigil!";
        };
        // Use parseComponent to handle gradients and hex colors properly
        player.sendMessage(TextUtil.parseComponent(message));
    }

    public enum SocketResult { SUCCESS, WRONG_SLOT, ALREADY_HAS_SIGIL, TIER_TOO_LOW, NO_PERMISSION, INVALID_ITEM }
    public NamespacedKey getSocketedSigilKey() { return SOCKETED_SIGILS_KEY; }

    /**
     * Public wrapper to update item lore with sigil entries.
     * Used by effects like DECREASE_SIGIL_TIER that modify sigil data directly.
     */
    public void updateItemLorePublic(ItemMeta meta, List<String> sigilIds, Material material) {
        updateItemLore(meta, sigilIds, material);
    }

    /**
     * Update the tier of a specific socketed sigil on armor.
     * Used by TierProgressionManager when a sigil levels up.
     */
    public void updateSocketedSigilTier(ItemStack armor, String sigilId, int newTier) {
        if (armor == null || !armor.hasItemMeta()) return;

        List<String> sigilData = getSocketedSigilData(armor);
        boolean updated = false;

        for (int i = 0; i < sigilData.size(); i++) {
            String entry = sigilData.get(i);
            String[] parts = entry.split(":");
            if (parts[0].equalsIgnoreCase(sigilId)) {
                sigilData.set(i, sigilId + ":" + newTier);
                updated = true;
                break;
            }
        }

        if (updated) {
            ItemMeta meta = armor.getItemMeta();
            meta.getPersistentDataContainer().set(
                    SOCKETED_SIGILS_KEY,
                    PersistentDataType.STRING,
                    String.join(",", sigilData)
            );
            armor.setItemMeta(meta);
        }
    }

    /**
     * Refresh the lore of armor to reflect current sigil states.
     * Used after tier-up to update displayed tier.
     */
    public void refreshArmorLore(ItemStack armor) {
        if (armor == null || !armor.hasItemMeta()) return;

        List<String> sigilData = getSocketedSigilData(armor);
        if (sigilData.isEmpty()) return;

        ItemMeta meta = armor.getItemMeta();
        updateItemLore(meta, sigilData, armor.getType());
        armor.setItemMeta(meta);
    }

    /**
     * Refresh all socketed items for all online players.
     * Updates lore based on current YAML configuration.
     * @return number of items updated
     */
    public int refreshAllPlayerItems() {
        int updatedCount = 0;

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            updatedCount += refreshPlayerItems(player);
        }

        return updatedCount;
    }

    /**
     * Refresh all socketed items for a specific player.
     * Updates lore based on current YAML configuration.
     * @param player the player to update
     * @return number of items updated
     */
    public int refreshPlayerItems(Player player) {
        int updatedCount = 0;

        // Check armor slots
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (refreshItemIfSocketed(armor[i])) {
                updatedCount++;
            }
        }
        // Apply armor changes back
        player.getInventory().setArmorContents(armor);

        // Check main hand
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (refreshItemIfSocketed(mainHand)) {
            player.getInventory().setItemInMainHand(mainHand);
            updatedCount++;
        }

        // Check off hand
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (refreshItemIfSocketed(offHand)) {
            player.getInventory().setItemInOffHand(offHand);
            updatedCount++;
        }

        // Check entire inventory for socketable items
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (refreshItemIfSocketed(contents[i])) {
                updatedCount++;
            }
        }
        player.getInventory().setContents(contents);

        return updatedCount;
    }

    /**
     * Refresh a single item's lore if it has socketed sigils.
     * @param item the item to check and refresh
     * @return true if the item was updated
     */
    private boolean refreshItemIfSocketed(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }

        List<String> sigilData = getSocketedSigilData(item);
        if (sigilData.isEmpty()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        updateItemLore(meta, sigilData, item.getType());
        item.setItemMeta(meta);
        return true;
    }
}
