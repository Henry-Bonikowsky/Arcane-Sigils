package com.zenax.armorsets.gui.handlers;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.core.SocketManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handler for the Socket GUI (socketing sigils into armor).
 */
public class SocketGUIHandler extends AbstractGUIHandler {

    public SocketGUIHandler(ArmorSetsPlugin plugin, GUIHandlerContext context) {
        super(plugin, context);
    }

    @Override
    public Set<GUIType> getSupportedTypes() {
        return Set.of(GUIType.SOCKET);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        // Slot 15: Action button
        if (slot == 15) {
            ItemStack armor = session.getArmor();
            Sigil current = plugin.getSocketManager().getSocketedSigil(armor);

            if (current != null) {
                // Unsocket
                Sigil removed = plugin.getSocketManager().unsocketSigil(player, armor);
                if (removed != null) {
                    // Give sigil item to player
                    ItemStack sigilItem = plugin.getSigilManager().createSigilItem(removed);
                    player.getInventory().addItem(sigilItem);

                    // Update original armor in player inventory
                    updateArmorInInventory(player, session.getArmorSlot(), armor);

                    player.sendMessage(TextUtil.colorize(
                            plugin.getConfigManager().getMessage("sigil-unsocketed")
                                    .replace("%sigil_name%", removed.getName())
                    ));
                    playSound(player, "unsocket");
                }
            }

            player.closeInventory();
            return;
        }

        // Allow placing sigil items from cursor
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir() && slot == 11) {
            Sigil sigil = plugin.getSigilManager().getSigilFromItem(cursor);
            if (sigil != null) {
                // Try to socket
                var result = plugin.getSocketManager().socketSigil(player, session.getArmor(), sigil);
                if (result == SocketManager.SocketResult.SUCCESS) {
                    cursor.setAmount(cursor.getAmount() - 1);
                    event.setCursor(cursor.getAmount() <= 0 ? null : cursor);

                    updateArmorInInventory(player, session.getArmorSlot(), session.getArmor());
                    playSound(player, "socket");
                    player.closeInventory();
                } else {
                    playSound(player, "error");
                }
            }
        }
    }

    // ===== GUI CREATION METHODS =====

    /**
     * Create and open the socket GUI for an armor piece.
     *
     * @param player    The player
     * @param armor     The armor piece
     * @param armorSlot The slot the armor is in
     */
    public void openSocketGUI(Player player, ItemStack armor, int armorSlot) {
        String title = plugin.getConfigManager().getMainConfig().getString("gui.socket-title", "&8Socket Sigil");
        Inventory gui = Bukkit.createInventory(null, 27, TextUtil.parseComponent(title));

        // Slot 13: Armor piece display
        gui.setItem(13, armor.clone());

        // Slot 11: Current sigil or empty socket
        Sigil current = plugin.getSocketManager().getSocketedSigil(armor);
        if (current != null) {
            ItemStack sigilDisplay = createSigilDisplay(current, true);
            gui.setItem(11, sigilDisplay);
        } else {
            gui.setItem(11, createEmptySocketItem());
        }

        // Slot 15: Socket/Unsocket action button
        if (current != null) {
            gui.setItem(15, createUnsocketButton());
        } else {
            gui.setItem(15, createSocketButton());
        }

        // Fill borders
        ItemStack border = createBorderItem();
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, border);
            }
        }

        // Store session and open
        GUISession session = new GUISession(GUIType.SOCKET, armor, armorSlot);
        context.openGUI(player, gui, session);
    }

    // ===== ITEM CREATION HELPERS =====

    /**
     * Create the sigil display item.
     */
    public ItemStack createSigilDisplay(Sigil sigil, boolean installed) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(TextUtil.parseComponent("&d" + sigil.getName()));

        List<Component> lore = new ArrayList<>();
        for (String desc : sigil.getDescription()) {
            lore.add(TextUtil.parseComponent("&7" + TextUtil.toProperCase(desc)));
        }

        if (!sigil.getEffects().isEmpty()) {
            lore.add(Component.empty());
            lore.add(TextUtil.parseComponent("&b&lWhen Empowered:"));
            for (String triggerKey : sigil.getEffects().keySet()) {
                String triggerName = TextUtil.toProperCase(triggerKey.replace("_", " "));
                String description = TextUtil.getTriggerDescription(triggerKey);
                lore.add(TextUtil.parseComponent("&b- &3" + triggerName));
                lore.add(TextUtil.parseComponent("&7  " + TextUtil.toProperCase(description)));
                var triggerConfig = sigil.getEffects().get(triggerKey);
                for (String effect : triggerConfig.getEffects()) {
                    String effectDesc = TextUtil.getEffectDescription(effect);
                    lore.add(TextUtil.parseComponent("&8    ->&7 " + TextUtil.toProperCase(effectDesc)));
                }
            }
        }

        lore.add(Component.empty());
        lore.add(TextUtil.parseComponent("&8Tier: &f" + sigil.getTier()));
        lore.add(TextUtil.parseComponent("&8Slot: &f" + TextUtil.toProperCase(sigil.getSlot())));
        if (installed) {
            lore.add(Component.empty());
            lore.add(TextUtil.parseComponent("&aCurrently Installed"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create the empty socket placeholder item.
     */
    public ItemStack createEmptySocketItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&8Empty Socket"));
        meta.lore(List.of(TextUtil.parseComponent("&7Drop a sigil shard here to socket")));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create the socket action button.
     */
    public ItemStack createSocketButton() {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&aSocket Sigil"));
        meta.lore(List.of(TextUtil.parseComponent("&7Drop a sigil shard on the socket")));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create the unsocket action button.
     */
    public ItemStack createUnsocketButton() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.parseComponent("&cUnsocket Sigil"));
        meta.lore(List.of(TextUtil.parseComponent("&7Click to remove the current sigil")));
        item.setItemMeta(meta);
        return item;
    }
}
