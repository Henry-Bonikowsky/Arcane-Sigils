package com.zenax.armorsets.core;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class SocketManager implements Listener {

    private final ArmorSetsPlugin plugin;
    private final NamespacedKey SOCKETED_SIGILS_KEY;

    // Roman numerals for display
    private static final String[] ROMAN_NUMERALS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

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

        // Find matching worn armor piece for this sigil's slot
        ItemStack targetArmor = getWornArmorBySlot(player, sigil.getSlot());
        if (targetArmor == null || targetArmor.getType().isAir()) {
            player.sendMessage(TextUtil.colorize("&cYou must be wearing " + sigil.getSlot().toLowerCase() + " armor to socket this sigil!"));
            return;
        }

        event.setCancelled(true);

        // Try to socket
        SocketResult result = socketSigil(player, targetArmor, sigil);
        handleSocketResult(player, result, sigil);

        if (result == SocketResult.SUCCESS) {
            mainHand.setAmount(mainHand.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.3f, 1f);
            // Update the armor in the slot
            setWornArmorBySlot(player, sigil.getSlot(), targetArmor);
            // Force inventory update to client
            player.getInventory().setArmorContents(player.getInventory().getArmorContents());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;

        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        // Check if cursor has sigil and clicked is armor
        if (cursor != null && !cursor.getType().isAir() && plugin.getSigilManager().isSigilItem(cursor)) {
            if (clicked != null && !clicked.getType().isAir() && isArmor(clicked.getType())) {
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

    public SocketResult socketSigil(Player player, ItemStack armor, Sigil sigil) {
        if (armor == null || armor.getType().isAir()) return SocketResult.INVALID_ARMOR;
        if (!isArmor(armor.getType())) return SocketResult.INVALID_ARMOR;

        String armorSlot = getArmorSlot(armor.getType());
        if (!sigil.canSocketInto(armorSlot)) return SocketResult.WRONG_SLOT;
        if (!player.hasPermission("armorsets.socket")) return SocketResult.NO_PERMISSION;

        // Check if this sigil type is already socketed (ignore tier)
        List<String> currentSigils = getSocketedSigilData(armor);
        String baseId = sigil.getId().toLowerCase();
        for (String entry : currentSigils) {
            String existingBase = entry.split(":")[0];
            if (existingBase.equals(baseId)) {
                return SocketResult.ALREADY_HAS_SIGIL;
            }
        }

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) return SocketResult.INVALID_ARMOR;

        // Add sigil to list with format "sigilId:tier"
        currentSigils.add(baseId + ":" + sigil.getTier());
        String sigilData = String.join(",", currentSigils);
        meta.getPersistentDataContainer().set(SOCKETED_SIGILS_KEY, PersistentDataType.STRING, sigilData);

        updateArmorLore(meta, currentSigils);
        armor.setItemMeta(meta);
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
        updateArmorLore(meta, sigilData);
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
        updateArmorLore(meta, sigilData);
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

    private void updateArmorLore(ItemMeta meta, List<String> sigilIds) {
        List<Component> newLore = new ArrayList<>();

        // Get existing lore and filter out old sigil entries
        if (meta.hasLore()) {
            List<Component> existingLore = meta.lore();
            if (existingLore != null) {
                for (Component line : existingLore) {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);

                    // Skip old sigil lines (➤/▶/☆ prefix or old formats)
                    if (plain.contains("➤") || plain.startsWith("▶") || plain.contains("☆") ||
                        plain.contains("Sigils:") || plain.contains("[Sigil]") ||
                        plain.contains("Socketed:") || plain.contains("Exclusive") ||
                        plain.contains("Right-click with sigil shard")) {
                        continue;
                    }

                    newLore.add(line);
                }
            }
        }

        // Collect sigils and separate exclusive from regular
        List<Sigil> exclusiveSigils = new ArrayList<>();
        List<Sigil> regularSigils = new ArrayList<>();
        Set<String> crateNames = new java.util.LinkedHashSet<>();

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
                        crateNames.add(sigil.getCrate());
                    }
                } else {
                    regularSigils.add(sigil);
                }
            }
        }

        // Sort both lists by rarity (highest first)
        exclusiveSigils.sort((a, b) -> getRarityOrder(b.getRarity()) - getRarityOrder(a.getRarity()));
        regularSigils.sort((a, b) -> getRarityOrder(b.getRarity()) - getRarityOrder(a.getRarity()));

        // Add exclusive sigils first with ☆ prefix
        for (Sigil sigil : exclusiveSigils) {
            String baseName = sigil.getName().replaceAll("\\s*&8\\[T\\d+\\]", "").trim();
            String roman = toRomanNumeral(sigil.getTier());
            String rarityColor = getRarityColor(sigil.getRarity());
            // Format: "<rarity>☆ &f<name> &b<tier>"
            newLore.add(TextUtil.parseComponent(rarityColor + "☆ &f" + baseName + " &b" + roman));
        }

        // Add regular sigils with ➤ prefix
        for (Sigil sigil : regularSigils) {
            String baseName = sigil.getName().replaceAll("\\s*&8\\[T\\d+\\]", "").trim();
            String roman = toRomanNumeral(sigil.getTier());
            String rarityColor = getRarityColor(sigil.getRarity());
            // Format: "<rarity>➤ &f<name> &b<tier>"
            newLore.add(TextUtil.parseComponent(rarityColor + "➤ &f" + baseName + " &b" + roman));
        }

        // Add crate exclusive line at the bottom if there are exclusive sigils
        if (!crateNames.isEmpty()) {
            newLore.add(Component.empty());
            for (String crateName : crateNames) {
                newLore.add(TextUtil.parseComponent("&6☆ &e" + crateName + " Exclusive &6☆"));
            }
        }

        meta.lore(newLore);
    }

    private String toRomanNumeral(int num) {
        if (num >= 1 && num <= 10) {
            return ROMAN_NUMERALS[num - 1];
        }
        return String.valueOf(num);
    }

    private String getRarityColor(String rarity) {
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

    private void handleSocketResult(Player player, SocketResult result, Sigil sigil) {
        String message = switch (result) {
            case SUCCESS -> "&a&lSocketed! &f" + sigil.getName() + " &aadded to your armor!";
            case WRONG_SLOT -> "&cThis sigil can only go in " + sigil.getSlot().toLowerCase() + "!";
            case ALREADY_HAS_SIGIL -> "&cThis armor already has this sigil!";
            case NO_PERMISSION -> "&cYou don't have permission to socket sigils!";
            case INVALID_ARMOR -> "&cInvalid armor piece!";
            case TIER_TOO_LOW -> "&cYour armor tier is too low for this sigil!";
        };
        player.sendMessage(TextUtil.colorize(message));
    }

    public enum SocketResult { SUCCESS, WRONG_SLOT, ALREADY_HAS_SIGIL, TIER_TOO_LOW, NO_PERMISSION, INVALID_ARMOR }
    public NamespacedKey getSocketedSigilKey() { return SOCKETED_SIGILS_KEY; }
}
