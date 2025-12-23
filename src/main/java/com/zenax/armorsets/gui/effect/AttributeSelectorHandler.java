package com.zenax.armorsets.gui.effect;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.effects.impl.ModifyAttributeEffect;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Browser for selecting Minecraft attributes for the MODIFY_ATTRIBUTE effect.
 *
 * Layout (6 rows = 54 slots):
 * Rows 0-4: Attribute items (45 slots, paginated)
 * Row 5: [X][_][_][_][_][_][_][<][>]
 */
public class AttributeSelectorHandler extends AbstractHandler {

    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV = 52;
    private static final int SLOT_NEXT = 53;
    private static final int ITEMS_PER_PAGE = 45;

    public AttributeSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        int page = session.getInt("page", 0);

        switch (slot) {
            case SLOT_BACK -> handleBack(player, session);
            case SLOT_PREV -> handlePrevPage(player, session, page);
            case SLOT_NEXT -> handleNextPage(player, session, page);
            default -> handleAttributeSelect(player, session, slot, page);
        }
    }

    private void handleBack(Player player, GUISession session) {
        playSound(player, "click");

        @SuppressWarnings("unchecked")
        Consumer<String> cancelCallback = session.get("cancelCallback", Consumer.class);
        if (cancelCallback != null) {
            cancelCallback.accept(null);
        } else {
            player.closeInventory();
        }
    }

    private void handlePrevPage(Player player, GUISession session, int page) {
        if (page > 0) {
            playSound(player, "click");
            session.put("page", page - 1);
            openGUI(guiManager, player, session);
        } else {
            playSound(player, "error");
        }
    }

    private void handleNextPage(Player player, GUISession session, int page) {
        String[] attributes = ModifyAttributeEffect.getAvailableAttributes();
        int maxPages = (int) Math.ceil((double) attributes.length / ITEMS_PER_PAGE);

        if (page < maxPages - 1) {
            playSound(player, "click");
            session.put("page", page + 1);
            openGUI(guiManager, player, session);
        } else {
            playSound(player, "error");
        }
    }

    private void handleAttributeSelect(Player player, GUISession session, int slot, int page) {
        if (slot < 0 || slot >= ITEMS_PER_PAGE || slot >= 45) {
            return;
        }

        String[] attributes = ModifyAttributeEffect.getAvailableAttributes();
        int index = page * ITEMS_PER_PAGE + slot;

        if (index >= attributes.length) {
            return;
        }

        String selectedAttribute = attributes[index];
        playSound(player, "success");

        @SuppressWarnings("unchecked")
        Consumer<String> callback = session.get("callback", Consumer.class);
        if (callback != null) {
            callback.accept(selectedAttribute);
        } else {
            player.closeInventory();
        }
    }

    /**
     * Open the attribute selector.
     *
     * @param guiManager The GUI manager
     * @param player The player
     * @param callback Called with selected attribute name
     * @param cancelCallback Called if cancelled (optional)
     */
    public static void openGUI(GUIManager guiManager, Player player, Consumer<String> callback, Consumer<String> cancelCallback) {
        GUISession session = new GUISession(GUIType.ATTRIBUTE_SELECTOR);
        session.put("callback", callback);
        session.put("cancelCallback", cancelCallback);
        session.put("page", 0);
        openGUI(guiManager, player, session);
    }

    public static void openGUI(GUIManager guiManager, Player player, GUISession session) {
        int page = session.getInt("page", 0);

        Inventory inv = Bukkit.createInventory(null, 54,
                TextUtil.parseComponent("&7Select Attribute"));

        // Fill bottom row with background
        for (int i = 45; i <= 53; i++) {
            inv.setItem(i, ItemBuilder.createBackground());
        }

        // Get attributes
        String[] attributes = ModifyAttributeEffect.getAvailableAttributes();
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, attributes.length);

        // Build attribute items
        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            String attribute = attributes[i];
            inv.setItem(slot, buildAttributeItem(attribute));
        }

        // Back button
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("Effect Config"));

        // Pagination
        int maxPages = (int) Math.ceil((double) attributes.length / ITEMS_PER_PAGE);
        if (maxPages <= 0) maxPages = 1;

        if (page > 0) {
            inv.setItem(SLOT_PREV, ItemBuilder.createItem(
                    Material.ARROW,
                    "&ePrevious Page",
                    "&7Page " + page + "/" + maxPages
            ));
        }

        if (page < maxPages - 1) {
            inv.setItem(SLOT_NEXT, ItemBuilder.createItem(
                    Material.ARROW,
                    "&eNext Page",
                    "&7Page " + (page + 2) + "/" + maxPages
            ));
        }

        guiManager.openGUI(player, inv, session);
    }

    private static ItemStack buildAttributeItem(String attribute) {
        Material material = getAttributeMaterial(attribute);
        String displayName = ModifyAttributeEffect.getAttributeDisplayName(attribute);
        String description = ModifyAttributeEffect.getAttributeDescription(attribute);

        List<String> lore = new ArrayList<>();
        lore.add("&7" + description);
        lore.add("");
        lore.add("&8ID: " + attribute);
        lore.add("");
        lore.add("&aClick to select");

        return ItemBuilder.createItem(material, "&e" + displayName, lore);
    }

    private static Material getAttributeMaterial(String attribute) {
        if (attribute == null) return Material.PAPER;

        return switch (attribute) {
            case "GENERIC_MOVEMENT_SPEED" -> Material.LEATHER_BOOTS;
            case "GENERIC_MAX_HEALTH" -> Material.RED_DYE;
            case "GENERIC_ATTACK_DAMAGE" -> Material.IRON_SWORD;
            case "GENERIC_ATTACK_SPEED" -> Material.GOLDEN_SWORD;
            case "GENERIC_ARMOR" -> Material.IRON_CHESTPLATE;
            case "GENERIC_ARMOR_TOUGHNESS" -> Material.DIAMOND_CHESTPLATE;
            case "GENERIC_KNOCKBACK_RESISTANCE" -> Material.NETHERITE_CHESTPLATE;
            case "GENERIC_LUCK" -> Material.EMERALD;
            case "GENERIC_FOLLOW_RANGE" -> Material.ENDER_EYE;
            case "GENERIC_ATTACK_KNOCKBACK" -> Material.SLIME_BALL;
            case "GENERIC_FLYING_SPEED" -> Material.ELYTRA;
            case "GENERIC_MAX_ABSORPTION" -> Material.GOLDEN_APPLE;
            case "GENERIC_SCALE" -> Material.SLIME_BLOCK;
            case "GENERIC_STEP_HEIGHT" -> Material.OAK_STAIRS;
            case "GENERIC_GRAVITY" -> Material.ANVIL;
            case "GENERIC_SAFE_FALL_DISTANCE" -> Material.FEATHER;
            case "GENERIC_FALL_DAMAGE_MULTIPLIER" -> Material.HAY_BLOCK;
            case "GENERIC_JUMP_STRENGTH" -> Material.RABBIT_FOOT;
            case "GENERIC_BURNING_TIME" -> Material.FIRE_CHARGE;
            case "GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE" -> Material.SHIELD;
            case "GENERIC_MOVEMENT_EFFICIENCY" -> Material.ICE;
            case "GENERIC_OXYGEN_BONUS" -> Material.CONDUIT;
            case "GENERIC_WATER_MOVEMENT_EFFICIENCY" -> Material.PRISMARINE_SHARD;
            case "PLAYER_BLOCK_INTERACTION_RANGE" -> Material.SPYGLASS;
            case "PLAYER_ENTITY_INTERACTION_RANGE" -> Material.LEAD;
            case "PLAYER_BLOCK_BREAK_SPEED" -> Material.DIAMOND_PICKAXE;
            case "PLAYER_MINING_EFFICIENCY" -> Material.GOLDEN_PICKAXE;
            case "PLAYER_SNEAKING_SPEED" -> Material.SCULK_SENSOR;
            case "PLAYER_SUBMERGED_MINING_SPEED" -> Material.TURTLE_HELMET;
            case "PLAYER_SWEEPING_DAMAGE_RATIO" -> Material.NETHERITE_SWORD;
            default -> Material.PAPER;
        };
    }
}
