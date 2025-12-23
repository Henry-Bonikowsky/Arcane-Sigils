package com.zenax.armorsets.gui.tier;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.constants.GUIConstants;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.ItemBuilder;
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

/**
 * Handler for the TIER_XP_CONFIG GUI.
 * Allows admin configuration of sigil XP progression:
 * - XP gain per activation
 * - Curve type (LINEAR, EXPONENTIAL, CUSTOM)
 * - Base XP requirement
 * - Growth rate (for exponential curve)
 */
public class TierXPConfigHandler extends AbstractHandler {

    public TierXPConfigHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        // Handle back button
        if (slot == GUIConstants.TIER_XP_BACK) {
            Sigil sigil = session.get("sigil", Sigil.class);
            if (sigil != null) {
                guiManager.openTierConfig(player, sigil);
            } else {
                guiManager.openSigilBrowser(player);
            }
            playSound(player, "close");
            return;
        }

        // Handle save button
        if (slot == GUIConstants.TIER_XP_SAVE) {
            saveXPConfig(player, session);
            return;
        }

        // Handle other slots
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil == null) return;

        TierXPConfig config = getOrCreateConfig(session, sigil);

        switch (slot) {
            case GUIConstants.TIER_XP_GAIN -> {
                // Input XP gain per activation
                guiManager.getInputHelper().requestInteger(player, "XP Gain", config.getGainPerActivation(), 1, 100,
                        newValue -> {
                            config.setGainPerActivation(newValue);
                            session.put("xpConfig", config);
                            player.sendMessage(TextUtil.colorize("§7XP per activation: §a" + newValue));
                            refreshGUI(player, session, sigil);
                        },
                        () -> refreshGUI(player, session, sigil)
                );
            }
            case GUIConstants.TIER_XP_CURVE -> {
                // Cycle curve type
                TierXPConfig.CurveType current = config.getCurveType();
                TierXPConfig.CurveType next = switch (current) {
                    case LINEAR -> TierXPConfig.CurveType.EXPONENTIAL;
                    case EXPONENTIAL -> TierXPConfig.CurveType.CUSTOM;
                    case CUSTOM -> TierXPConfig.CurveType.LINEAR;
                };
                config.setCurveType(next);
                session.put("xpConfig", config);
                player.sendMessage(TextUtil.colorize("§7XP Curve: §a" + next.name()));

                // Update curve item in place
                Inventory inv = player.getOpenInventory().getTopInventory();
                Material curveMaterial = switch (next) {
                    case LINEAR -> Material.IRON_INGOT;
                    case EXPONENTIAL -> Material.GOLD_INGOT;
                    case CUSTOM -> Material.DIAMOND;
                };
                inv.setItem(slot, ItemBuilder.createItem(curveMaterial,
                        "§eXP Curve Type", "§7Current: §f" + next.name(), "",
                        "§fLINEAR&7: Same XP each tier",
                        "§fEXPONENTIAL&7: Increasing XP each tier",
                        "§fCUSTOM&7: Manual XP values", "", "§eClick to cycle"));

                // Update XP preview to reflect new curve
                int maxTier = sigil.getMaxTier();
                List<String> previewLore = new ArrayList<>();
                previewLore.add("§7XP required per tier:");
                previewLore.add("");
                for (int t = 2; t <= Math.min(maxTier, 5); t++) {
                    previewLore.add("§7T" + t + ": §f" + config.getXPForTier(t) + " XP");
                }
                if (maxTier > 5) {
                    previewLore.add("§7...");
                    previewLore.add("§7T" + maxTier + ": §f" + config.getXPForTier(maxTier) + " XP");
                }
                inv.setItem(22, ItemBuilder.createItem(Material.BOOK,
                        "§eXP Preview", previewLore.toArray(new String[0])));

                // Update growth rate display (only relevant for exponential)
                String growthStatus = next == TierXPConfig.CurveType.EXPONENTIAL
                        ? "§a" + String.format("%.2f", config.getGrowthRate())
                        : "§8N/A (not exponential)";
                inv.setItem(GUIConstants.TIER_XP_GROWTH, ItemBuilder.createItem(Material.BLAZE_ROD,
                        "§eGrowth Rate", "§7Current: " + growthStatus, "",
                        "§7Multiplier for each tier",
                        "§7T3 = BaseXP × Growth",
                        "§7T4 = BaseXP × Growth²", "",
                        "§eClick to change"));

                playSound(player, "click");
            }
            case GUIConstants.TIER_XP_BASE -> {
                // Input base XP
                guiManager.getInputHelper().requestInteger(player, "Base XP", config.getBaseXP(), 1, 10000,
                        newValue -> {
                            config.setBaseXP(newValue);
                            session.put("xpConfig", config);
                            player.sendMessage(TextUtil.colorize("§7Base XP: §a" + newValue));
                            refreshGUI(player, session, sigil);
                        },
                        () -> refreshGUI(player, session, sigil)
                );
            }
            case GUIConstants.TIER_XP_GROWTH -> {
                // Input growth rate
                guiManager.getInputHelper().requestNumber(player, "Growth Rate", config.getGrowthRate(), 1.0, 5.0,
                        newValue -> {
                            config.setGrowthRate(newValue);
                            session.put("xpConfig", config);
                            player.sendMessage(TextUtil.colorize("§7Growth Rate: §a" + String.format("%.2f", newValue)));
                            refreshGUI(player, session, sigil);
                        },
                        () -> refreshGUI(player, session, sigil)
                );
            }
        }
    }

    private TierXPConfig getOrCreateConfig(GUISession session, Sigil sigil) {
        TierXPConfig config = session.get("xpConfig", TierXPConfig.class);
        if (config == null) {
            config = sigil.getTierXPConfig();
            if (config == null) {
                config = new TierXPConfig();
            } else {
                config = config.copy();
            }
            session.put("xpConfig", config);
        }
        return config;
    }

    private void saveXPConfig(Player player, GUISession session) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil == null) {
            player.sendMessage(TextUtil.colorize("§cError: Sigil not found"));
            return;
        }

        TierXPConfig config = session.get("xpConfig", TierXPConfig.class);
        if (config != null) {
            sigil.setTierXPConfig(config);
        }

        // Auto-save
        plugin.getSigilManager().saveSigil(sigil);

        player.sendMessage(TextUtil.colorize("§aXP configuration saved!"));
        playSound(player, "success");
        guiManager.openTierConfig(player, sigil);
    }

    private void refreshGUI(Player player, GUISession session, Sigil sigil) {
        guiManager.openTierXPConfig(player, sigil);
    }

    /**
     * Build and open the TIER_XP_CONFIG GUI for a sigil.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil) {
        Inventory inv = Bukkit.createInventory(null, 45, TextUtil.parseComponent("§7Tier Config > §fXP Settings"));

        TierXPConfig config = sigil.getTierXPConfig();
        if (config == null) config = new TierXPConfig();

        // Fill background
        ItemBuilder.fillBackground(inv);

        // XP Gain per Activation
        inv.setItem(GUIConstants.TIER_XP_GAIN, ItemBuilder.createItem(Material.EXPERIENCE_BOTTLE,
                "§eXP per Activation",
                "§7Current: §f" + config.getGainPerActivation(),
                "",
                "§7XP gained each time the",
                "§7sigil effect activates",
                "",
                "§eClick to change"
        ));

        // Curve Type
        Material curveMaterial = switch (config.getCurveType()) {
            case LINEAR -> Material.IRON_INGOT;
            case EXPONENTIAL -> Material.GOLD_INGOT;
            case CUSTOM -> Material.DIAMOND;
        };
        inv.setItem(GUIConstants.TIER_XP_CURVE, ItemBuilder.createItem(curveMaterial,
                "§eXP Curve Type",
                "§7Current: §f" + config.getCurveType().name(),
                "",
                "§fLINEAR&7: Same XP each tier",
                "§fEXPONENTIAL&7: Increasing XP each tier",
                "§fCUSTOM&7: Manual XP values",
                "",
                "§eClick to cycle"
        ));

        // Base XP
        inv.setItem(GUIConstants.TIER_XP_BASE, ItemBuilder.createItem(Material.PAPER,
                "§eBase XP",
                "§7Current: §f" + config.getBaseXP(),
                "",
                "§7XP required for tier 2",
                "§7(first tier advancement)",
                "",
                "§eClick to change"
        ));

        // Growth Rate (only relevant for exponential)
        String growthStatus = config.getCurveType() == TierXPConfig.CurveType.EXPONENTIAL
                ? "§a" + String.format("%.2f", config.getGrowthRate())
                : "§8N/A (not exponential)";
        inv.setItem(GUIConstants.TIER_XP_GROWTH, ItemBuilder.createItem(Material.BLAZE_ROD,
                "§eGrowth Rate",
                "§7Current: " + growthStatus,
                "",
                "§7Multiplier for each tier",
                "§7T3 = BaseXP × Growth",
                "§7T4 = BaseXP × Growth²",
                "",
                "§eClick to change"
        ));

        // Preview - show XP requirements for first few tiers
        int maxTier = sigil.getMaxTier();
        List<String> previewLore = new ArrayList<>();
        previewLore.add("§7XP required per tier:");
        previewLore.add("");
        for (int t = 2; t <= Math.min(maxTier, 5); t++) {
            previewLore.add("§7T" + t + ": §f" + config.getXPForTier(t) + " XP");
        }
        if (maxTier > 5) {
            previewLore.add("§7...");
            previewLore.add("§7T" + maxTier + ": §f" + config.getXPForTier(maxTier) + " XP");
        }

        inv.setItem(22, ItemBuilder.createItem(Material.BOOK,
                "§eXP Preview",
                previewLore.toArray(new String[0])
        ));

        // Back button
        inv.setItem(GUIConstants.TIER_XP_BACK, ItemBuilder.createItem(Material.RED_DYE,
                "§cBack",
                "§7Return to tier config"
        ));

        // Save button
        inv.setItem(GUIConstants.TIER_XP_SAVE, ItemBuilder.createItem(Material.LIME_DYE,
                "§aSave Configuration",
                "§7Save XP settings",
                "§7to this sigil"
        ));

        // Create session
        GUISession session = new GUISession(GUIType.TIER_XP_CONFIG);
        session.put("sigil", sigil);
        session.put("sigilId", sigil.getId());
        session.put("xpConfig", config.copy());

        guiManager.openGUI(player, inv, session);
    }
}
