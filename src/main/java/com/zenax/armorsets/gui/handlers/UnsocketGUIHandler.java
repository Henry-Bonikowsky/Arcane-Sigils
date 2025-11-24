package com.zenax.armorsets.gui.handlers;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handler for the Unsocket GUI (removing sigils from armor).
 */
public class UnsocketGUIHandler extends AbstractGUIHandler {

    public UnsocketGUIHandler(ArmorSetsPlugin plugin, GUIHandlerContext context) {
        super(plugin, context);
    }

    @Override
    public Set<GUIType> getSupportedTypes() {
        return Set.of(GUIType.UNSOCKET);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        int lastSlot = inv.getSize() - 1;

        // Close button
        if (slot == lastSlot) {
            player.closeInventory();
            return;
        }

        // Ignore top row (border)
        if (slot < 9) return;

        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;

        // Check if it's an exclusive sigil (barrier = can't remove)
        if (clicked.getType() == Material.BARRIER) {
            player.sendMessage(TextUtil.colorize("&c&lExclusive sigils cannot be removed!"));
            playSound(player, "error");
            return;
        }

        // Get sigil ID from PDC
        String sigilId = clicked.getItemMeta().getPersistentDataContainer().get(
            new NamespacedKey(plugin, "unsocket_sigil_id"),
            PersistentDataType.STRING
        );

        if (sigilId == null) return;

        // Unsocket the sigil
        Sigil removed = plugin.getSocketManager().unsocketSigilById(player, session.getArmor(), sigilId);
        if (removed != null) {
            // Give sigil shard back to player
            ItemStack shard = plugin.getSigilManager().createSigilItem(removed);
            player.getInventory().addItem(shard);

            // Update armor in player inventory
            updateArmorInInventory(player, session.getArmorSlot(), session.getArmor());

            player.sendMessage(TextUtil.colorize("&aUnsocketed &f" + removed.getName() + "&a! Shard returned."));
            playSound(player, "unsocket");

            // Refresh GUI or close if no more sigils
            List<Sigil> remaining = plugin.getSocketManager().getSocketedSigils(session.getArmor());
            if (remaining.isEmpty()) {
                player.closeInventory();
            } else {
                context.openUnsocketGUI(player, session.getArmor(), session.getArmorSlot());
            }
        } else {
            player.sendMessage(TextUtil.colorize("&cFailed to unsocket sigil!"));
            playSound(player, "error");
        }
    }

    // ===== GUI CREATION METHODS =====

    /**
     * Create and open the unsocket GUI for an armor piece.
     *
     * @param player    The player
     * @param armor     The armor piece
     * @param armorSlot The slot the armor is in
     */
    public void openUnsocketGUI(Player player, ItemStack armor, int armorSlot) {
        List<Sigil> sigils = plugin.getSocketManager().getSocketedSigils(armor);

        if (sigils.isEmpty()) {
            player.sendMessage(TextUtil.colorize("&cNo sigils socketed on this armor!"));
            return;
        }

        // Calculate inventory size based on sigil count (min 27, 9 per row)
        int rows = Math.max(3, (int) Math.ceil((sigils.size() + 9) / 9.0));
        int size = Math.min(rows * 9, 54);

        Inventory gui = Bukkit.createInventory(null, size, TextUtil.parseComponent("&8Remove Sigil"));

        // Top row border
        ItemStack border = createBorderItem();
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, border);
        }

        // Display armor in center of top row
        gui.setItem(4, armor.clone());

        // Add sigils starting from slot 9
        int slot = 9;
        for (Sigil sigil : sigils) {
            if (slot >= size - 1) break;

            ItemStack sigilItem = createUnsocketSigilItem(sigil);
            gui.setItem(slot, sigilItem);
            slot++;
        }

        // Close button at last slot
        ItemStack close = createGuiItem(Material.BARRIER, "&cClose", "&7Close menu");
        gui.setItem(size - 1, close);

        GUISession session = new GUISession(GUIType.UNSOCKET, armor, armorSlot);
        context.openGUI(player, gui, session);
    }

    // ===== ITEM CREATION HELPERS =====

    /**
     * Create a sigil display item for the unsocket GUI.
     */
    public ItemStack createUnsocketSigilItem(Sigil sigil) {
        Material material = sigil.isExclusive() ? Material.BARRIER : Material.NETHER_STAR;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String rarityColor = getRarityColor(sigil.getRarity());
        String roman = toRomanNumeral(sigil.getTier());
        String baseName = sigil.getName().replaceAll("\\s*&8\\[T\\d+\\]", "").trim();

        meta.displayName(TextUtil.parseComponent(rarityColor + baseName + " &b" + roman));

        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.parseComponent("&8Rarity: " + rarityColor + sigil.getRarity()));
        lore.add(TextUtil.parseComponent("&8Slot: &f" + TextUtil.toProperCase(sigil.getSlot())));
        lore.add(Component.empty());

        for (String desc : sigil.getDescription()) {
            lore.add(TextUtil.parseComponent("&7" + desc));
        }

        lore.add(Component.empty());

        if (sigil.isExclusive()) {
            lore.add(TextUtil.parseComponent("&c&l* EXCLUSIVE - Cannot be removed"));
            if (sigil.getCrate() != null) {
                lore.add(TextUtil.parseComponent("&6* " + sigil.getCrate() + " Exclusive"));
            }
        } else {
            lore.add(TextUtil.parseComponent("&a&lClick to remove"));
            lore.add(TextUtil.parseComponent("&7Sigil will be returned as a shard"));
        }

        // Store sigil ID in PDC for identification
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "unsocket_sigil_id"),
            PersistentDataType.STRING,
            sigil.getId()
        );

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
