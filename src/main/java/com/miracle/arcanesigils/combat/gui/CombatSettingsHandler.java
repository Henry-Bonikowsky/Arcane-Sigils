package com.miracle.arcanesigils.combat.gui;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.combat.LegacyCombatConfig;
import com.miracle.arcanesigils.combat.LegacyCombatManager;
import com.miracle.arcanesigils.combat.modules.CombatModule;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI handler for the legacy combat settings menu.
 * Allows toggling combat modules and adjusting their values.
 */
public class CombatSettingsHandler extends AbstractHandler {

    private static final int SIZE = 54; // 6 rows

    // Layout slots (2 rows of 7 modules each, centered)
    private static final int MASTER_TOGGLE_SLOT = 4;
    private static final int[] MODULE_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33};
    private static final int RELOAD_SLOT = 48;
    private static final int DEFAULTS_SLOT = 50;
    private static final int CLOSE_SLOT = 49;

    // Module order (14 modules)
    private static final String[] MODULE_ORDER = {
        "global-damage-scaling", "attack-cooldown", "sword-blocking", "knockback",
        "regeneration", "hitbox", "sweep-attack", "critical-hits",
        "fishing-rod", "tool-damage", "golden-apple", "attack-indicator", "potions", "projectile-kb"
    };

    public CombatSettingsHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        openGUI(guiManager, player);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        LegacyCombatManager manager = plugin.getLegacyCombatManager();
        if (manager == null) return;

        LegacyCombatConfig config = manager.getConfig();

        // Close button
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            playSound(player, "close");
            return;
        }

        // Master toggle
        if (slot == MASTER_TOGGLE_SLOT) {
            config.setEnabled(!config.isEnabled());
            config.save();
            manager.reload();
            playSound(player, "click");
            openGUI(guiManager, player);
            return;
        }

        // Module toggles
        for (int i = 0; i < MODULE_ORDER.length && i < MODULE_SLOTS.length; i++) {
            if (slot == MODULE_SLOTS[i]) {
                String moduleId = MODULE_ORDER[i];

                if (event.isLeftClick()) {
                    // Toggle module
                    boolean current = config.isModuleEnabled(moduleId);
                    config.setModuleEnabled(moduleId, !current);
                    config.save();
                    manager.reload();
                    playSound(player, "click");
                    openGUI(guiManager, player);
                } else if (event.isRightClick()) {
                    // Open module config
                    com.miracle.arcanesigils.combat.modules.CombatModule module = manager.getModule(moduleId);
                    if (module != null && !module.getConfigParams().isEmpty()) {
                        ModuleConfigHandler.openGUI(guiManager, player, moduleId);
                        playSound(player, "click");
                    } else {
                        player.sendMessage(TextUtil.colorize("&6[Combat] &7This module has no configurable parameters."));
                        playSound(player, "error");
                    }
                }
                return;
            }
        }

        // Reload button
        if (slot == RELOAD_SLOT) {
            manager.reload();
            playSound(player, "success");
            player.sendMessage(TextUtil.colorize("&6[Combat] &aConfiguration reloaded!"));
            openGUI(guiManager, player);
            return;
        }

        // Defaults button
        if (slot == DEFAULTS_SLOT) {
            player.sendMessage(TextUtil.colorize("&6[Combat] &7Reset to defaults requires deleting combat.yml and reloading."));
            playSound(player, "error");
        }
    }

    /**
     * Open the Combat Settings GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player) {
        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
        Inventory inv = Bukkit.createInventory(null, SIZE, TextUtil.parseComponent("&8Legacy Combat Settings"));

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Header
        inv.setItem(4, ItemBuilder.createItem(Material.DIAMOND_SWORD,
            "&6&lLegacy Combat Settings",
            "&7Configure 1.8 PvP mechanics",
            "",
            "&eModules shown below can be",
            "&etoggled and configured."
        ));

        LegacyCombatManager manager = plugin.getLegacyCombatManager();
        if (manager == null) {
            inv.setItem(22, ItemBuilder.createItem(Material.BARRIER,
                "&c&lNot Available",
                "&7Legacy combat system is not loaded."
            ));

            GUISession session = new GUISession(GUIType.COMBAT_SETTINGS);
            guiManager.openGUI(player, inv, session);
            return;
        }

        LegacyCombatConfig config = manager.getConfig();
        Map<String, CombatModule> modules = manager.getModules();

        // Master toggle
        boolean masterEnabled = config.isEnabled();
        inv.setItem(MASTER_TOGGLE_SLOT, ItemBuilder.createItem(
            masterEnabled ? Material.LIME_DYE : Material.RED_DYE,
            masterEnabled ? "&a&lMaster: ENABLED" : "&c&lMaster: DISABLED",
            "",
            "&7Click to toggle all combat modules",
            "",
            masterEnabled ? "&aAll modules active" : "&cAll modules inactive"
        ));

        // Module toggles
        for (int i = 0; i < MODULE_ORDER.length && i < MODULE_SLOTS.length; i++) {
            String moduleId = MODULE_ORDER[i];
            CombatModule module = modules.get(moduleId);

            if (module != null) {
                inv.setItem(MODULE_SLOTS[i], createModuleItem(module, config));
            }
        }

        // Reload button
        inv.setItem(RELOAD_SLOT, ItemBuilder.createItem(Material.COMPASS,
            "&e&lReload Config",
            "&7Reload combat.yml from disk",
            "",
            "&eClick to reload"
        ));

        // Close button
        inv.setItem(CLOSE_SLOT, ItemBuilder.createItem(Material.BARRIER,
            "&c&lClose",
            "&7Close this menu"
        ));

        // Defaults button
        inv.setItem(DEFAULTS_SLOT, ItemBuilder.createItem(Material.WRITABLE_BOOK,
            "&6&lReset to Defaults",
            "&7Reset all values to default",
            "",
            "&eClick for info"
        ));

        GUISession session = new GUISession(GUIType.COMBAT_SETTINGS);
        guiManager.openGUI(player, inv, session);
    }

    private static ItemStack createModuleItem(CombatModule module, LegacyCombatConfig config) {
        boolean enabled = config.isModuleEnabled(module.getId());

        Material mat = switch (module.getId()) {
            case "global-damage-scaling" -> Material.NETHER_STAR;
            case "attack-cooldown" -> Material.IRON_SWORD;
            case "sword-blocking" -> Material.SHIELD;
            case "knockback" -> Material.SLIME_BALL;
            case "regeneration" -> Material.GOLDEN_CARROT;
            case "hitbox" -> Material.ENDER_EYE;
            case "sweep-attack" -> Material.IRON_AXE;
            case "critical-hits" -> Material.FEATHER;
            case "fishing-rod" -> Material.FISHING_ROD;
            case "tool-damage" -> Material.DIAMOND_AXE;
            case "golden-apple" -> Material.GOLDEN_APPLE;
            case "attack-indicator" -> Material.CLOCK;
            case "potions" -> Material.POTION;
            case "projectile-kb" -> Material.SNOWBALL;
            default -> Material.PAPER;
        };

        String status = enabled ? "&aEnabled" : "&cDisabled";
        String displayName = (enabled ? "&a" : "&c") + module.getDisplayName();

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Status: " + status);
        lore.add("");
        addModuleDescription(lore, module.getId());
        lore.add("");
        lore.add("&eLeft-click to toggle");
        lore.add("&eRight-click for settings");

        return ItemBuilder.createItem(mat, displayName, lore);
    }

    private static void addModuleDescription(List<String> lore, String moduleId) {
        switch (moduleId) {
            case "global-damage-scaling" -> {
                lore.add("&7Scale damage and resistance");
                lore.add("&7Higher values = harder hits");
            }
            case "attack-cooldown" -> lore.add("&7Removes 1.9+ attack cooldown");
            case "sword-blocking" -> {
                lore.add("&7Restores sword blocking");
                lore.add("&7Also disables shields");
            }
            case "knockback" -> {
                lore.add("&71.8 knockback mechanics");
                lore.add("&7with ping compensation");
                lore.add("&7Removes netherite KB resist");
            }
            case "regeneration" -> lore.add("&7Slow 1.8-style health regen");
            case "hitbox" -> lore.add("&7Extended reach (+0.1 blocks)");
            case "sweep-attack" -> lore.add("&7Disables 1.9+ sweep attack");
            case "critical-hits" -> lore.add("&7Crits without cooldown check");
            case "fishing-rod" -> lore.add("&7Rod knockback + rod tricking");
            case "tool-damage" -> lore.add("&7Reduces axe damage to 1.8 levels");
            case "golden-apple" -> lore.add("&7Faster gapple eating (1.0s)");
            case "attack-indicator" -> lore.add("&7Hides cooldown crosshair");
            case "potions" -> lore.add("&71.8 potion effect behavior");
            case "projectile-kb" -> {
                lore.add("&7Snowballs and eggs");
                lore.add("&7now have knockback");
            }
        }
    }
}
