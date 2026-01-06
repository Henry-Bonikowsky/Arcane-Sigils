package com.miracle.arcanesigils.combat.gui;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.combat.LegacyCombatConfig;
import com.miracle.arcanesigils.combat.LegacyCombatManager;
import com.miracle.arcanesigils.combat.modules.CombatModule;
import com.miracle.arcanesigils.combat.modules.ModuleParam;
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

/**
 * GUI handler for configuring individual combat module parameters.
 * Allows runtime editing of all module settings.
 */
public class ModuleConfigHandler extends AbstractHandler {

    private static final int SIZE = 54; // 6 rows
    private static final int BACK_SLOT = 45;
    private static final int SAVE_SLOT = 49;
    private static final int RELOAD_SLOT = 53;

    // Parameter slots - 3 rows of 7 parameters each (max 21 params)
    private static final int[] PARAM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    public ModuleConfigHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        String moduleId = session.get("moduleId", String.class);
        if (moduleId != null) {
            openGUI(guiManager, player, moduleId);
        } else {
            CombatSettingsHandler.openGUI(guiManager, player);
        }
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        String moduleId = session.get("moduleId", String.class);
        if (moduleId == null) return;

        LegacyCombatManager manager = plugin.getLegacyCombatManager();
        if (manager == null) return;

        CombatModule module = manager.getModule(moduleId);
        if (module == null) return;

        List<ModuleParam> params = module.getConfigParams();
        LegacyCombatConfig config = manager.getConfig();

        // Back button
        if (slot == BACK_SLOT) {
            CombatSettingsHandler.openGUI(guiManager, player);
            playSound(player, "click");
            return;
        }

        // Save button
        if (slot == SAVE_SLOT) {
            config.save();
            manager.reload();
            player.sendMessage(TextUtil.colorize("&6[Combat] &aSettings saved and applied!"));
            playSound(player, "success");
            openGUI(guiManager, player, moduleId); // Refresh
            return;
        }

        // Reload button
        if (slot == RELOAD_SLOT) {
            manager.reload();
            player.sendMessage(TextUtil.colorize("&6[Combat] &aModule reloaded!"));
            playSound(player, "success");
            openGUI(guiManager, player, moduleId);
            return;
        }

        // Find which parameter was clicked
        for (int i = 0; i < PARAM_SLOTS.length && i < params.size(); i++) {
            if (slot == PARAM_SLOTS[i]) {
                ModuleParam param = params.get(i);

                if (event.isLeftClick()) {
                    // Increment value
                    param.increment();
                    playSound(player, "click");
                } else if (event.isRightClick()) {
                    // Decrement value
                    param.decrement();
                    playSound(player, "click");
                } else if (event.isShiftClick()) {
                    // Large step (5x)
                    for (int j = 0; j < 5; j++) {
                        if (event.isLeftClick()) {
                            param.increment();
                        } else {
                            param.decrement();
                        }
                    }
                    playSound(player, "click");
                }

                // Auto-save and apply immediately
                config.save();
                manager.reload();

                // Refresh GUI
                openGUI(guiManager, player, moduleId);
                return;
            }
        }
    }

    /**
     * Open the Module Config GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player, String moduleId) {
        ArmorSetsPlugin plugin = ArmorSetsPlugin.getInstance();
        LegacyCombatManager manager = plugin.getLegacyCombatManager();

        if (manager == null) {
            player.sendMessage(TextUtil.colorize("&cLegacy combat system not available."));
            return;
        }

        CombatModule module = manager.getModule(moduleId);
        if (module == null) {
            player.sendMessage(TextUtil.colorize("&cModule not found: " + moduleId));
            return;
        }

        List<ModuleParam> params = module.getConfigParams();
        if (params.isEmpty()) {
            player.sendMessage(TextUtil.colorize("&6[Combat] &7This module has no configurable parameters."));
            return;
        }

        String title = TextUtil.colorize("&8" + module.getDisplayName() + " Config");
        Inventory inv = Bukkit.createInventory(null, SIZE, TextUtil.parseComponent(title));

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Header
        inv.setItem(4, createHeaderItem(module));

        // Parameter items
        for (int i = 0; i < PARAM_SLOTS.length && i < params.size(); i++) {
            inv.setItem(PARAM_SLOTS[i], createParamItem(params.get(i)));
        }

        // Back button
        inv.setItem(BACK_SLOT, ItemBuilder.createItem(Material.ARROW,
            "&6&l\u2190 Back",
            "&7Return to combat settings"
        ));

        // Save button
        inv.setItem(SAVE_SLOT, ItemBuilder.createItem(Material.LIME_CONCRETE,
            "&a&lSave & Apply",
            "&7Save changes to disk and",
            "&7apply to all players",
            "",
            "&eClick to save"
        ));

        // Reload button
        inv.setItem(RELOAD_SLOT, ItemBuilder.createItem(Material.COMPASS,
            "&e&lReload Module",
            "&7Reload this module from config",
            "",
            "&eClick to reload"
        ));

        GUISession session = new GUISession(GUIType.COMBAT_MODULE_CONFIG);
        session.put("moduleId", moduleId);
        guiManager.openGUI(player, inv, session);
    }

    private static ItemStack createHeaderItem(CombatModule module) {
        Material mat = getModuleMaterial(module.getId());
        boolean enabled = module.isEnabled();

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Status: " + (enabled ? "&aEnabled" : "&cDisabled"));
        lore.add("");
        lore.add("&7Configure the parameters below.");
        lore.add("&7Changes are auto-saved.");
        lore.add("");
        lore.add("&eLeft-click: Increase value");
        lore.add("&eRight-click: Decrease value");
        lore.add("&eShift-click: Large step (5x)");

        return ItemBuilder.createItem(mat, "&6&l" + module.getDisplayName(), lore);
    }

    private static ItemStack createParamItem(ModuleParam param) {
        Material mat = getParamMaterial(param.getType());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7" + param.getDescription());
        lore.add("");
        lore.add("&fCurrent: &a" + param.getFormattedValue());

        if (param.getType() != ModuleParam.ParamType.BOOLEAN) {
            lore.add("");
            lore.add("&8Range: " + formatRange(param));
        }

        lore.add("");
        if (param.getType() == ModuleParam.ParamType.BOOLEAN) {
            lore.add("&eClick to toggle");
        } else {
            lore.add("&eLeft-click: +" + formatStep(param));
            lore.add("&eRight-click: -" + formatStep(param));
        }

        return ItemBuilder.createItem(mat, "&b" + param.getDisplayName(), lore);
    }

    private static String formatRange(ModuleParam param) {
        if (param.getType() == ModuleParam.ParamType.INTEGER ||
            param.getType() == ModuleParam.ParamType.MILLISECONDS ||
            param.getType() == ModuleParam.ParamType.TICKS) {
            return (int) param.getMin() + " - " + (int) param.getMax();
        }
        return String.format("%.2f - %.2f", param.getMin(), param.getMax());
    }

    private static String formatStep(ModuleParam param) {
        double step = param.getStep();
        if (param.getType() == ModuleParam.ParamType.INTEGER ||
            param.getType() == ModuleParam.ParamType.MILLISECONDS ||
            param.getType() == ModuleParam.ParamType.TICKS) {
            return String.valueOf((int) step);
        }
        if (step == (int) step) {
            return String.valueOf((int) step);
        }
        return String.format("%.2f", step);
    }

    private static Material getParamMaterial(ModuleParam.ParamType type) {
        return switch (type) {
            case BOOLEAN -> Material.LEVER;
            case INTEGER -> Material.IRON_INGOT;
            case DOUBLE -> Material.GOLD_INGOT;
            case PERCENTAGE -> Material.EXPERIENCE_BOTTLE;
            case MILLISECONDS -> Material.CLOCK;
            case TICKS -> Material.REPEATER;
            case SECONDS -> Material.CLOCK;
        };
    }

    private static Material getModuleMaterial(String moduleId) {
        return switch (moduleId) {
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
    }
}
