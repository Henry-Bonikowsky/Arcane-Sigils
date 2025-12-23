package com.zenax.armorsets.gui.tier;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.constants.GUIConstants;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.tier.TierParameterConfig;
import com.zenax.armorsets.tier.TierScalingConfig;
import com.zenax.armorsets.tier.TierXPConfig;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handler for the TIER_CONFIG GUI.
 * Allows admin configuration of sigil tier scaling settings using explicit parameter arrays.
 *
 * New system uses explicit value arrays per tier:
 * tier:
 *   mode: PARAMETER
 *   params:
 *     damage: [5, 7, 10, 15, 20]
 *     chance: [1, 1.5, 2, 2.5, 3]
 */
public class TierConfigHandler extends AbstractHandler {

    // Slot layout
    private static final int SLOT_INFO = 9;
    private static final int SLOT_MAX_TIER = 10;
    // SLOT_MODE removed - simplified to always use PARAMETER mode
    private static final int SLOT_ADD_PARAM = 14;
    private static final int SLOT_XP_TOGGLE = 16;

    // Parameter list starts at row 2 (slots 19-25), 7 params per row, 2 rows
    private static final int PARAMS_START = 19;
    private static final int PARAMS_PER_ROW = 7;
    private static final int PARAMS_ROWS = 2;

    private static final int SLOT_BACK = 36;
    private static final int SLOT_SAVE = 44;
    private static final int SLOT_XP_SETTINGS = 40;
    private static final int SLOT_PREVIEW = 38;

    public TierConfigHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void reopen(Player player, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil == null) {
            player.closeInventory();
            return;
        }
        TierScalingConfig config = session.get("tierConfig", TierScalingConfig.class);
        TierXPConfig xpConfig = session.get("xpConfig", TierXPConfig.class);
        Integer maxTier = session.get("maxTier", Integer.class);
        openGUI(guiManager, player, sigil, config, xpConfig, maxTier);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        var v = session.validator(player);
        Sigil sigil = v.require("sigil", Sigil.class);
        if (v.handleInvalid()) return;

        TierScalingConfig config = getOrCreateConfig(session, sigil);

        switch (slot) {
            case SLOT_BACK -> {
                String returnSignalKey = session.get("returnSignalKey", String.class);
                if (returnSignalKey != null) {
                    // Auto-save and return to flow builder
                    saveConfigToSigil(player, session);
                    guiManager.openFlowBuilder(player, sigil, returnSignalKey);
                    playSound(player, "success");
                } else {
                    // Original behavior - return to sigil editor without auto-save
                    guiManager.openSigilEditor(player, sigil);
                    playSound(player, "close");
                }
            }
            case SLOT_SAVE -> {
                saveTierConfig(player, session);
            }
            case SLOT_MAX_TIER -> {
                int currentMax = session.getInt("maxTier", sigil.getMaxTier());
                guiManager.getInputHelper().requestNumber(player, "Max Tier", currentMax, 1, 100,
                        newValue -> {
                            int newMax = (int) Math.round(newValue);
                            session.put("maxTier", newMax);
                            player.sendMessage(TextUtil.colorize("§7Max Tier set to: §a" + newMax));
                            refreshGUI(player, session, sigil);
                        },
                        () -> refreshGUI(player, session, sigil)
                );
            }
            case SLOT_ADD_PARAM -> {
                // Open parameter selector browser
                TierParamSelectorHandler.openGUI(guiManager, player, sigil, session);
                playSound(player, "click");
            }
            case SLOT_XP_TOGGLE -> {
                TierXPConfig xpConfig = getOrCreateXPConfig(session, sigil);
                xpConfig.setEnabled(!xpConfig.isEnabled());
                session.put("xpConfig", xpConfig);
                player.sendMessage(TextUtil.colorize("§7XP Progression: " + (xpConfig.isEnabled() ? "§aEnabled" : "§cDisabled")));
                refreshGUI(player, session, sigil);
                playSound(player, "click");
            }
            case SLOT_XP_SETTINGS -> {
                guiManager.openTierXPConfig(player, sigil);
            }
            default -> {
                // Check if clicking on a parameter
                String paramName = getParamAtSlot(session, slot);
                if (paramName != null) {
                    if (event.isShiftClick() && event.isRightClick()) {
                        // Delete parameter
                        config.getParams().removeParameter(paramName);
                        session.put("tierConfig", config);
                        player.sendMessage(TextUtil.colorize("§cRemoved parameter: §f" + paramName));
                        refreshGUI(player, session, sigil);
                        playSound(player, "click");
                    } else {
                        // Edit parameter values
                        openParamEditor(player, sigil, paramName, session);
                    }
                }
            }
        }
    }

    private String getParamAtSlot(GUISession session, int slot) {
        @SuppressWarnings("unchecked")
        java.util.Map<Integer, String> slotMap = (java.util.Map<Integer, String>) session.get("paramSlotMap");
        return slotMap != null ? slotMap.get(slot) : null;
    }

    private void openParamEditor(Player player, Sigil sigil, String paramName, GUISession session) {
        TierScalingConfig config = session.get("tierConfig", TierScalingConfig.class);
        int maxTier = session.getInt("maxTier", sigil.getMaxTier());

        // Get current values
        List<Double> values = new ArrayList<>(config.getParams().getValues(paramName));
        while (values.size() < maxTier) {
            values.add(0.0);
        }

        // Open visual editor
        TierParamEditorHandler.openGUI(guiManager, player, sigil, paramName, config, values, maxTier, session);
    }

    private TierScalingConfig getOrCreateConfig(GUISession session, Sigil sigil) {
        TierScalingConfig config = session.get("tierConfig", TierScalingConfig.class);
        if (config == null) {
            config = sigil.getTierScalingConfig();
            if (config == null) {
                config = new TierScalingConfig();
            } else {
                config = config.copy();
            }
            session.put("tierConfig", config);
        }
        return config;
    }

    private TierXPConfig getOrCreateXPConfig(GUISession session, Sigil sigil) {
        TierXPConfig xpConfig = session.get("xpConfig", TierXPConfig.class);
        if (xpConfig == null) {
            xpConfig = sigil.getTierXPConfig();
            if (xpConfig == null) {
                xpConfig = new TierXPConfig();
            } else {
                xpConfig = xpConfig.copy();
            }
            session.put("xpConfig", xpConfig);
        }
        return xpConfig;
    }

    /**
     * Save tier config to sigil without navigation.
     * @return true if save was successful
     */
    private boolean saveConfigToSigil(Player player, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil == null) {
            player.sendMessage(TextUtil.colorize("§cError: Sigil not found"));
            return false;
        }

        TierScalingConfig config = session.get("tierConfig", TierScalingConfig.class);
        TierXPConfig xpConfig = session.get("xpConfig", TierXPConfig.class);
        Integer maxTier = session.get("maxTier", Integer.class);

        if (config != null) {
            sigil.setTierScalingConfig(config);
        }
        if (xpConfig != null) {
            sigil.setTierXPConfig(xpConfig);
        }
        if (maxTier != null) {
            sigil.setMaxTier(maxTier);
        }

        plugin.getSigilManager().saveSigil(sigil);
        return true;
    }

    private void saveTierConfig(Player player, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (!saveConfigToSigil(player, session)) {
            return;
        }

        player.sendMessage(TextUtil.colorize("§aTier configuration saved!"));
        playSound(player, "success");

        // Check if we should return to flow builder
        String returnSignalKey = session.get("returnSignalKey", String.class);
        if (returnSignalKey != null) {
            guiManager.openFlowBuilder(player, sigil, returnSignalKey);
        } else {
            guiManager.openSigilEditor(player, sigil);
        }
    }

    private void refreshGUI(Player player, GUISession session, Sigil sigil) {
        TierScalingConfig config = session.get("tierConfig", TierScalingConfig.class);
        TierXPConfig xpConfig = session.get("xpConfig", TierXPConfig.class);
        Integer maxTier = session.get("maxTier", Integer.class);
        String returnSignalKey = session.get("returnSignalKey", String.class);
        openGUI(guiManager, player, sigil, config, xpConfig, maxTier, returnSignalKey);
    }

    /**
     * Build and open the TIER_CONFIG GUI for a sigil.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil) {
        openGUI(guiManager, player, sigil, null, null, null, null);
    }

    /**
     * Build and open the TIER_CONFIG GUI with optional pre-existing configs.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil,
                               TierScalingConfig existingConfig, TierXPConfig existingXpConfig, Integer existingMaxTier) {
        openGUI(guiManager, player, sigil, existingConfig, existingXpConfig, existingMaxTier, null);
    }

    /**
     * Build and open the TIER_CONFIG GUI with optional pre-existing configs and return navigation.
     * @param returnSignalKey If set, back button will auto-save and return to flow builder with this signal key
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil,
                               TierScalingConfig existingConfig, TierXPConfig existingXpConfig,
                               Integer existingMaxTier, String returnSignalKey) {
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent("§7" + sigil.getName() + " > §fTier Config"));

        // Use existing config if provided, otherwise read from sigil
        TierScalingConfig config = existingConfig;
        if (config == null) {
            config = sigil.getTierScalingConfig();
            if (config == null) config = new TierScalingConfig();
        }

        TierXPConfig xpConfig = existingXpConfig;
        if (xpConfig == null) {
            xpConfig = sigil.getTierXPConfig();
            if (xpConfig == null) xpConfig = new TierXPConfig();
        }

        int maxTier = existingMaxTier != null ? existingMaxTier : sigil.getMaxTier();

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Workflow hint - explains how tier scaling works
        inv.setItem(SLOT_INFO, ItemBuilder.createItem(Material.BOOK,
                "§bHow Tier Scaling Works",
                "§71. Add a parameter (like §fdamage§7)",
                "§72. Set values for each tier",
                "§73. Use §f{damage}§7 in effect params",
                "§74. Higher tier = bigger numbers!",
                "",
                "§e§lNote: §7Chance and Cooldown are",
                "§7configured in §fStart Node§7, not here.",
                "§7Click the Start node in Flow Builder",
                "§7and Shift+Click to enable tier scaling."
        ));

        // Max Tier setting
        inv.setItem(SLOT_MAX_TIER, ItemBuilder.createItem(Material.NETHER_STAR,
                "§eMax Tier",
                "§7Current: §f" + maxTier,
                "",
                "§7The maximum tier level",
                "§7this sigil can reach",
                "",
                "§eClick to change"
        ));

        // Add Parameter button
        inv.setItem(SLOT_ADD_PARAM, ItemBuilder.createItem(Material.LIME_DYE,
                "§aAdd Effect Parameter",
                "§7Create a tier-scaled parameter",
                "§7for effect values like damage, radius, etc.",
                "",
                "§7Use §f{param_name}§7 in effect params",
                "§7to reference the scaled value.",
                "",
                "§c§lDo NOT add chance/cooldown here!",
                "§7Those are set in the §fStart Node§7.",
                "",
                "§eClick to add"
        ));

        // XP Toggle
        Material xpMaterial = xpConfig.isEnabled() ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE;
        inv.setItem(SLOT_XP_TOGGLE, ItemBuilder.createItem(xpMaterial,
                "§eXP Progression",
                "§7Status: " + (xpConfig.isEnabled() ? "§aEnabled" : "§cDisabled"),
                "",
                "§7When enabled, sigils gain XP",
                "§7and tier up automatically",
                "",
                "§eClick to toggle"
        ));

        // Parameter list
        java.util.Map<Integer, String> paramSlotMap = new java.util.HashMap<>();
        TierParameterConfig params = config.getParams();
        Set<String> paramNames = params.getParameterNames();

        int paramIndex = 0;
        for (String paramName : paramNames) {
            int row = paramIndex / PARAMS_PER_ROW;
            int col = paramIndex % PARAMS_PER_ROW;

            if (row >= PARAMS_ROWS) break; // Only show 2 rows of params

            int slot = PARAMS_START + (row * 9) + col;
            paramSlotMap.put(slot, paramName);

            // Build lore showing values at each tier
            List<String> lore = new ArrayList<>();
            lore.add("§7Values by tier:");
            List<Double> values = params.getValues(paramName);
            for (int t = 1; t <= Math.min(maxTier, 5); t++) {
                double val = t <= values.size() ? values.get(t - 1) : 0;
                String valStr = val == (long) val ? String.valueOf((long) val) : String.valueOf(val);
                lore.add("§8T" + t + ": §f" + valStr);
            }
            if (maxTier > 5) {
                lore.add("§8...");
                double maxVal = maxTier <= values.size() ? values.get(maxTier - 1) : 0;
                String maxValStr = maxVal == (long) maxVal ? String.valueOf((long) maxVal) : String.valueOf(maxVal);
                lore.add("§8T" + maxTier + ": §f" + maxValStr);
            }
            lore.add("");
            lore.add("§eClick to edit values");
            lore.add("§eShift+Right-Click to delete");

            inv.setItem(slot, ItemBuilder.createItem(Material.NAME_TAG,
                    "§e{" + paramName + "}",
                    lore.toArray(new String[0])));

            paramIndex++;
        }

        // Preview
        inv.setItem(SLOT_PREVIEW, buildPreviewItem(maxTier, config));

        // XP Settings
        inv.setItem(SLOT_XP_SETTINGS, ItemBuilder.createItem(Material.BOOK,
                "§eXP Settings",
                "§7Configure XP progression",
                "",
                "§eClick to configure"
        ));

        // Back button
        inv.setItem(SLOT_BACK, ItemBuilder.createItem(Material.RED_DYE,
                "§cBack",
                "§7Return to sigil editor"
        ));

        // Save button
        inv.setItem(SLOT_SAVE, ItemBuilder.createItem(Material.LIME_DYE,
                "§aSave Configuration",
                "§7Save all tier settings"
        ));

        // Create session
        GUISession session = new GUISession(GUIType.TIER_CONFIG);
        session.put("sigil", sigil);
        session.put("sigilId", sigil.getId());
        session.put("tierConfig", config.copy());
        session.put("xpConfig", xpConfig.copy());
        session.put("maxTier", maxTier);
        session.put("paramSlotMap", paramSlotMap);

        // Store return navigation info if coming from flow builder
        if (returnSignalKey != null) {
            session.put("returnSignalKey", returnSignalKey);
        }

        guiManager.openGUI(player, inv, session);
    }

    /**
     * Build the scaling preview item showing parameter values at different tiers.
     */
    private static ItemStack buildPreviewItem(int maxTier, TierScalingConfig config) {
        List<String> lore = new ArrayList<>();

        lore.add("§7Parameter values at each tier");
        lore.add("");

        TierParameterConfig params = config.getParams();
        if (params.isEmpty()) {
            lore.add("§8No parameters defined");
            lore.add("§8Add parameters to see preview");
        } else {
            // Show each parameter's values at key tiers
            for (String paramName : params.getParameterNames()) {
                StringBuilder line = new StringBuilder("§e{" + paramName + "}&7: ");
                int[] tiersToShow = maxTier <= 3
                        ? new int[]{1, maxTier}
                        : new int[]{1, maxTier / 2, maxTier};

                boolean first = true;
                for (int tier : tiersToShow) {
                    if (tier < 1 || tier > maxTier) continue;
                    if (!first) line.append(" → ");
                    first = false;
                    String val = params.getValueAsString(paramName, tier);
                    line.append("§f").append(val);
                }
                lore.add(line.toString());
            }
        }

        lore.add("");
        lore.add("§8Mode: " + config.getMode().name());

        return ItemBuilder.createItem(Material.SPYGLASS,
                "§eScaling Preview",
                lore.toArray(new String[0]));
    }
}
