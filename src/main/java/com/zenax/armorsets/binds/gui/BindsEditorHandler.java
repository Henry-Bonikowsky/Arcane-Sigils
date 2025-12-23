package com.zenax.armorsets.binds.gui;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.binds.BindPreset;
import com.zenax.armorsets.binds.BindsManager;
import com.zenax.armorsets.binds.PlayerBindData;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.core.SigilManager;
import com.zenax.armorsets.core.SocketManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Handler for BINDS_EDITOR GUI type.
 * Allows players to select which sigils to bind to a specific key/command bind.
 *
 * Layout:
 * - Row 0: Back button (slot 0), rest empty
 * - Row 1+: Sigils displayed (binded sigils first with glow if equipped, then possible sigils)
 * - Rows expand based on sigil count (3 rows for 1-7 sigils, 4 rows for 8-14, etc., max 6 rows)
 */
public class BindsEditorHandler extends AbstractHandler {

    private static final int BACK_BUTTON_SLOT = 0;

    /**
     * Enum representing where a sigil is located in the player's inventory.
     * Used to determine glow effect and name color.
     */
    public enum SigilLocation {
        EQUIPPED,   // Armor slots (36-39)
        HOTBAR,     // Hotbar slots (0-8)
        INVENTORY,  // Main inventory (9-35) or offhand
        NOT_FOUND   // Not in player inventory
    }

    public BindsEditorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        // Back button
        if (slot == BACK_BUTTON_SLOT) {
            handleBackButton(player, session);
            return;
        }

        // Check if slot contains a sigil
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            playSound(player, "error");
            return;
        }

        // Get sigil ID from clicked item
        String sigilId = plugin.getSigilManager().getSigilIdFromItem(clickedItem);
        if (sigilId == null) {
            playSound(player, "error");
            return;
        }

        Integer bindSlot = session.get("bindSlot", Integer.class);
        if (bindSlot == null) {
            playSound(player, "error");
            return;
        }

        BindsManager bindsManager = plugin.getBindsManager();
        PlayerBindData playerData = bindsManager.getPlayerData(player);
        BindPreset currentBinds = getCurrentBindPreset(session, playerData);

        if (event.isLeftClick()) {
            // Bind sigil to this slot
            handleBindSigil(player, session, playerData, currentBinds, bindSlot, sigilId);
        } else if (event.isRightClick()) {
            // Unbind sigil from this slot
            handleUnbindSigil(player, session, playerData, currentBinds, bindSlot, sigilId);
        }
    }

    /**
     * Handle back button click - return to previous binds GUI.
     */
    private void handleBackButton(Player player, GUISession session) {
        GUIType sourceGui = session.get("sourceGui", GUIType.class);

        if (sourceGui == GUIType.BINDS_COMMAND) {
            // Open BINDS_COMMAND GUI
            openBindsCommandGUI(player);
        } else {
            // Default to BINDS_HOTBAR
            openBindsHotbarGUI(player);
        }

        playSound(player, "click");
    }

    /**
     * Handle binding a sigil to the current slot.
     */
    private void handleBindSigil(Player player, GUISession session, PlayerBindData playerData,
                                   BindPreset currentBinds, int bindSlot, String sigilId) {
        List<String> boundSigils = new ArrayList<>(currentBinds.getBind(bindSlot));

        // Check if already bound
        if (boundSigils.contains(sigilId)) {
            player.sendMessage(TextUtil.colorize("§cThis sigil is already bound to this slot!"));
            playSound(player, "error");
            return;
        }

        // Add sigil to bind
        boundSigils.add(sigilId);
        currentBinds.setBind(bindSlot, boundSigils);

        Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
        String sigilName = sigil != null ? sigil.getName() : sigilId;
        player.sendMessage(TextUtil.colorize("§aBound §f" + sigilName + " §ato slot " + bindSlot + "!"));
        playSound(player, "socket");

        // Refresh GUI
        openBindsEditor(player, bindSlot, session.get("sourceGui", GUIType.class));
    }

    /**
     * Handle unbinding a sigil from the current slot.
     */
    private void handleUnbindSigil(Player player, GUISession session, PlayerBindData playerData,
                                     BindPreset currentBinds, int bindSlot, String sigilId) {
        List<String> boundSigils = new ArrayList<>(currentBinds.getBind(bindSlot));

        // Check if actually bound
        if (!boundSigils.contains(sigilId)) {
            player.sendMessage(TextUtil.colorize("§cThis sigil is not bound to this slot!"));
            playSound(player, "error");
            return;
        }

        // Remove sigil from bind
        boundSigils.remove(sigilId);
        currentBinds.setBind(bindSlot, boundSigils);

        Sigil sigil = plugin.getSigilManager().getSigil(sigilId);
        String sigilName = sigil != null ? sigil.getName() : sigilId;
        player.sendMessage(TextUtil.colorize("§cUnbound §f" + sigilName + " §cfrom slot " + bindSlot + "!"));
        playSound(player, "unsocket");

        // Refresh GUI
        openBindsEditor(player, bindSlot, session.get("sourceGui", GUIType.class));
    }

    /**
     * Get the current bind preset based on source GUI.
     */
    private BindPreset getCurrentBindPreset(GUISession session, PlayerBindData playerData) {
        GUIType sourceGui = session.get("sourceGui", GUIType.class);
        if (sourceGui == GUIType.BINDS_COMMAND) {
            return playerData.getCommandBinds();
        }
        return playerData.getHotbarBinds();
    }

    /**
     * Open the Binds Editor GUI for a player.
     *
     * @param player    The player to show the GUI to
     * @param bindSlot  The bind slot being edited (1-9 for hotbar, 1-27 for command)
     * @param sourceGui The GUI that opened this editor (BINDS_HOTBAR or BINDS_COMMAND)
     */
    public void openBindsEditor(Player player, int bindSlot, GUIType sourceGui) {
        BindsManager bindsManager = plugin.getBindsManager();
        PlayerBindData playerData = bindsManager.getPlayerData(player);
        BindPreset currentBinds = sourceGui == GUIType.BINDS_COMMAND
            ? playerData.getCommandBinds()
            : playerData.getHotbarBinds();

        // Get activation-type sigils from player's inventory
        List<Sigil> activationSigils = getActivationSigilsFromInventory(player);
        Set<String> inventorySigilIds = new HashSet<>();
        for (Sigil sigil : activationSigils) {
            inventorySigilIds.add(sigil.getId());
        }

        // Get sigils already bound to this slot
        List<String> boundSigilIds = currentBinds.getBind(bindSlot);

        // Separate into categories
        List<Sigil> bindedSigils = new ArrayList<>();
        List<Sigil> possibleSigils = new ArrayList<>();

        // First, add sigils that are both bound AND in inventory
        for (Sigil sigil : activationSigils) {
            if (boundSigilIds.contains(sigil.getId())) {
                bindedSigils.add(sigil);
            } else {
                possibleSigils.add(sigil);
            }
        }

        // IMPORTANT: Also add bound sigils that are NOT in inventory (so they can be unbound)
        for (String boundId : boundSigilIds) {
            if (!inventorySigilIds.contains(boundId)) {
                // Sigil is bound but not in inventory - still need to show it for unbinding
                Sigil sigil = plugin.getSigilManager().getSigil(boundId);
                if (sigil != null) {
                    bindedSigils.add(sigil);
                }
            }
        }

        // Combine: binded first, then possible
        List<Sigil> allSigils = new ArrayList<>();
        allSigils.addAll(bindedSigils);
        allSigils.addAll(possibleSigils);

        // Calculate required rows (3 minimum, expand up to 6)
        int sigilCount = allSigils.size();
        int rows = calculateRequiredRows(sigilCount);
        int invSize = rows * 9;

        // Create inventory
        String title = TextUtil.colorize("§6&lBind Editor - Slot " + bindSlot);
        Inventory inv = Bukkit.createInventory(null, invSize, title);

        // Fill according to PDF layout:
        // Row 0: Back button + fillers (slots 1-8)
        // Middle rows: filler on left (0), sigils in middle (1-7), filler on right (8)
        // Last row: all fillers
        ItemStack filler = BindsGUIHelper.createFillerItem();

        // Row 0: Back button at 0, fillers at 1-8
        inv.setItem(BACK_BUTTON_SLOT, BindsGUIHelper.createBackItem());
        for (int i = 1; i <= 8; i++) {
            inv.setItem(i, filler);
        }

        // Middle rows (1 to rows-2): filler on edges, AIR in middle (sigils placed later)
        for (int row = 1; row < rows - 1; row++) {
            inv.setItem(row * 9, filler);      // Left edge
            inv.setItem(row * 9 + 8, filler);  // Right edge
            // Middle slots (1-7) stay AIR unless filled with sigils
        }

        // Last row: all fillers
        int lastRowStart = (rows - 1) * 9;
        for (int i = 0; i < 9; i++) {
            inv.setItem(lastRowStart + i, filler);
        }

        // Place sigils in middle slots of each row (slots 1-7 of rows 1 to rows-2)
        List<Integer> sigilSlots = getSigilSlots(rows);
        for (int i = 0; i < allSigils.size() && i < sigilSlots.size(); i++) {
            Sigil sigil = allSigils.get(i);
            boolean isBound = boundSigilIds.contains(sigil.getId());

            ItemStack sigilItem = createSigilDisplayItem(player, sigil, isBound);
            inv.setItem(sigilSlots.get(i), sigilItem);
        }

        // Create session
        GUISession session = new GUISession(GUIType.BINDS_EDITOR);
        session.put("bindSlot", bindSlot);
        session.put("sourceGui", sourceGui != null ? sourceGui : GUIType.BINDS_HOTBAR);

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Calculate required rows based on sigil count.
     * Minimum 3 rows, max 6 rows.
     * 7 sigils per row.
     */
    private int calculateRequiredRows(int sigilCount) {
        if (sigilCount <= 7) return 3;
        if (sigilCount <= 14) return 4;
        if (sigilCount <= 21) return 5;
        return 6; // Max 6 rows
    }

    /**
     * Get all sigil slot positions for the given number of rows.
     * Returns slots 10-16 for row 1, 19-25 for row 2, etc. (middle 7 slots of each middle row)
     */
    private List<Integer> getSigilSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        // Sigils go in middle rows (row 1 to rows-2), in slots 1-7 of each row
        for (int row = 1; row < rows - 1; row++) {
            int rowStart = row * 9;
            for (int col = 1; col <= 7; col++) {
                slots.add(rowStart + col);
            }
        }
        return slots;
    }

    /**
     * Get all activation-type sigils SOCKETED on items in the player's inventory.
     * These are sigils with exclusive: true and exclusive_type: ACTION.
     * Only socketed sigils count - sigil items themselves are NOT activatable.
     * Searches: armor slots, hotbar, main inventory, and offhand.
     */
    private List<Sigil> getActivationSigilsFromInventory(Player player) {
        Set<String> foundSigilIds = new HashSet<>();
        List<Sigil> result = new ArrayList<>();
        SocketManager socketManager = plugin.getSocketManager();

        // Check armor slots
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) continue;
            List<Sigil> socketed = socketManager.getSocketedSigils(armor);
            for (Sigil sigil : socketed) {
                if (isActivationSigil(sigil) && !foundSigilIds.contains(sigil.getId())) {
                    foundSigilIds.add(sigil.getId());
                    result.add(sigil);
                }
            }
        }

        // Check entire inventory (slots 0-35: hotbar 0-8, main inventory 9-35)
        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) continue;
            List<Sigil> socketed = socketManager.getSocketedSigils(item);
            for (Sigil sigil : socketed) {
                if (isActivationSigil(sigil) && !foundSigilIds.contains(sigil.getId())) {
                    foundSigilIds.add(sigil.getId());
                    result.add(sigil);
                }
            }
        }

        // Check offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            List<Sigil> socketed = socketManager.getSocketedSigils(offhand);
            for (Sigil sigil : socketed) {
                if (isActivationSigil(sigil) && !foundSigilIds.contains(sigil.getId())) {
                    foundSigilIds.add(sigil.getId());
                    result.add(sigil);
                }
            }
        }

        return result;
    }

    /**
     * Check if a sigil is an activation-type sigil (ability-based, not passive).
     * Uses sigil.isAbility() which checks both new flow.type=ABILITY and deprecated ExclusiveType.ACTION.
     */
    private boolean isActivationSigil(Sigil sigil) {
        return sigil.isAbility();
    }

    /**
     * Check if a sigil is socketed on any equipped item (armor, hotbar, main inventory, offhand).
     */
    private boolean isSigilEquipped(Player player, String sigilId) {
        return getSigilLocation(player, sigilId) != SigilLocation.NOT_FOUND;
    }

    /**
     * Find the item that has a sigil socketed into it.
     * Search order: armor → hotbar → main inventory → offhand
     * Returns the ItemStack, or null if not found.
     */
    public ItemStack findSocketedItem(Player player, String sigilId) {
        SocketManager socketManager = plugin.getSocketManager();

        // Check armor slots (39=helmet, 38=chest, 37=legs, 36=boots)
        for (int slot : new int[]{39, 38, 37, 36}) {
            ItemStack armor = player.getInventory().getItem(slot);
            if (armor != null && !armor.getType().isAir()) {
                List<String> socketedData = socketManager.getSocketedSigilData(armor);
                for (String data : socketedData) {
                    String id = data.split(":")[0];
                    if (id.equalsIgnoreCase(sigilId)) {
                        return armor;
                    }
                }
            }
        }

        // Check hotbar slots (0-8)
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir()) {
                List<String> socketedData = socketManager.getSocketedSigilData(item);
                for (String data : socketedData) {
                    String id = data.split(":")[0];
                    if (id.equalsIgnoreCase(sigilId)) {
                        return item;
                    }
                }
            }
        }

        // Check main inventory (9-35)
        for (int slot = 9; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir()) {
                List<String> socketedData = socketManager.getSocketedSigilData(item);
                for (String data : socketedData) {
                    String id = data.split(":")[0];
                    if (id.equalsIgnoreCase(sigilId)) {
                        return item;
                    }
                }
            }
        }

        // Check offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            List<String> socketedData = socketManager.getSocketedSigilData(offhand);
            for (String data : socketedData) {
                String id = data.split(":")[0];
                if (id.equalsIgnoreCase(sigilId)) {
                    return offhand;
                }
            }
        }

        return null;
    }

    /**
     * Get the location of a sigil in the player's inventory.
     * Used to determine glow (in inventory) and name color (equipped/hotbar vs inventory/not found).
     */
    public SigilLocation getSigilLocation(Player player, String sigilId) {
        SocketManager socketManager = plugin.getSocketManager();

        // Check armor slots (39=helmet, 38=chest, 37=legs, 36=boots)
        for (int slot : new int[]{39, 38, 37, 36}) {
            ItemStack armor = player.getInventory().getItem(slot);
            if (armor != null && !armor.getType().isAir()) {
                List<String> socketedData = socketManager.getSocketedSigilData(armor);
                for (String data : socketedData) {
                    String id = data.split(":")[0];
                    if (id.equalsIgnoreCase(sigilId)) {
                        return SigilLocation.EQUIPPED;
                    }
                }
            }
        }

        // Check hotbar slots (0-8)
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir()) {
                List<String> socketedData = socketManager.getSocketedSigilData(item);
                for (String data : socketedData) {
                    String id = data.split(":")[0];
                    if (id.equalsIgnoreCase(sigilId)) {
                        return SigilLocation.HOTBAR;
                    }
                }
            }
        }

        // Check main inventory (9-35)
        for (int slot = 9; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir()) {
                List<String> socketedData = socketManager.getSocketedSigilData(item);
                for (String data : socketedData) {
                    String id = data.split(":")[0];
                    if (id.equalsIgnoreCase(sigilId)) {
                        return SigilLocation.INVENTORY;
                    }
                }
            }
        }

        // Check offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            List<String> socketedData = socketManager.getSocketedSigilData(offhand);
            for (String data : socketedData) {
                String id = data.split(":")[0];
                if (id.equalsIgnoreCase(sigilId)) {
                    return SigilLocation.INVENTORY; // Offhand treated as INVENTORY
                }
            }
        }

        return SigilLocation.NOT_FOUND;
    }

    /**
     * Create a display item for a sigil in the editor.
     * Shows the actual item the sigil is socketed into (preserves CustomModelData for ItemsAdder).
     *
     * Visual indicators:
     * - Glow: Item is in inventory (anywhere)
     * - Green name: Equipped (armor) or Hotbar (0-8)
     * - Red name: Main inventory (9-35), offhand, or not in inventory
     */
    private ItemStack createSigilDisplayItem(Player player, Sigil sigil, boolean isBound) {
        SigilLocation location = getSigilLocation(player, sigil.getId());
        ItemStack socketedItem = findSocketedItem(player, sigil.getId());

        ItemStack item;

        if (socketedItem != null) {
            // Clone the actual item (preserves CustomModelData for ItemsAdder)
            item = socketedItem.clone();
        } else {
            // Sigil not in inventory - use RED_DYE as fallback
            item = new ItemStack(Material.RED_DYE);
        }

        // Apply glow if item is in inventory (anywhere)
        if (location != SigilLocation.NOT_FOUND) {
            ItemBuilder.addGlowEffect(item);
        }

        // Determine name color based on location
        // Green = Equipped or Hotbar, Red = Inventory or Not Found
        String nameColor = (location == SigilLocation.EQUIPPED || location == SigilLocation.HOTBAR) ? "§a" : "§c";
        String sigilNameStripped = TextUtil.stripColors(sigil.getName());

        // Build lore with proper §r resets
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.empty());

        if (isBound) {
            lore.add(TextUtil.parseComponent("§r&c&lRight Click §r&b- §r&fUnbind from this hotkey."));
        } else {
            lore.add(TextUtil.parseComponent("§r&a&lLeft Click §r&b- §r&fBind to this hotkey."));
        }

        // Set display name, lore, AND store sigil ID in PDC for click detection
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parseComponent("§r" + nameColor + sigilNameStripped));
            meta.lore(lore);

            // Store sigil ID in persistent data container so we can retrieve it on click
            NamespacedKey sigilIdKey = new NamespacedKey(plugin, "sigil_id");
            meta.getPersistentDataContainer().set(sigilIdKey, PersistentDataType.STRING, sigil.getId());

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Open the Hotbar Binds GUI.
     */
    private void openBindsHotbarGUI(Player player) {
        BindsHotbarHandler hotbarHandler = new BindsHotbarHandler(plugin, guiManager);
        hotbarHandler.openHotbarBindsGUI(player);
    }

    /**
     * Open the Command Binds GUI.
     */
    private void openBindsCommandGUI(Player player) {
        BindsCommandHandler commandHandler = new BindsCommandHandler(plugin, guiManager);
        commandHandler.openCommandBindsGUI(player, 0);
    }
}
