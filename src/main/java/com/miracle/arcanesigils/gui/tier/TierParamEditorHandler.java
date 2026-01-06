package com.miracle.arcanesigils.gui.tier;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.tier.TierParameterConfig;
import com.miracle.arcanesigils.tier.TierScalingConfig;
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
 * Visual editor for tier parameter values.
 * Shows each tier as a clickable slot with the current value.
 * Much more intuitive than comma-separated text input.
 *
 * Layout (54 slots):
 * Row 0: [Back] [±0.1] [Delete] [±0.5] [{param}] [±1] [±5] [±10] [Save]
 * Row 1-4: Tier slots (up to 36 tiers, or paginated for more)
 * Row 5: [---] [Copy T1] [---] [Prev] [Preview] [Next] [---] [---] [---]
 */
public class TierParamEditorHandler extends AbstractHandler {

    // Row 0 slots
    private static final int SLOT_BACK = 0;
    private static final int SLOT_INC_01 = 1;   // ±0.1
    private static final int SLOT_DELETE = 2;
    private static final int SLOT_INC_05 = 3;   // ±0.5
    private static final int SLOT_PARAM_NAME = 4;
    private static final int SLOT_INC_1 = 5;
    private static final int SLOT_INC_5 = 6;
    private static final int SLOT_INC_10 = 7;
    private static final int SLOT_SAVE = 8;

    // Tier slots start at row 1 (slot 9), 4 rows of 9 = 36 slots per page
    private static final int TIERS_START = 9;
    private static final int TIERS_PER_ROW = 9;
    private static final int TIER_ROWS = 4;
    private static final int TIERS_PER_PAGE = TIERS_PER_ROW * TIER_ROWS; // 36
    private static final int INVENTORY_SIZE = 54; // 6 rows

    // Row 5 - quick actions
    private static final int SLOT_COPY_T1 = 46;
    private static final int SLOT_PREV_PAGE = 48;
    private static final int SLOT_PREVIEW = 49;
    private static final int SLOT_NEXT_PAGE = 50;

    public TierParamEditorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Sigil sigil = session.get("sigil", Sigil.class);
        String paramName = session.get("paramName", String.class);
        TierScalingConfig config = session.get("tierConfig", TierScalingConfig.class);
        int maxTier = session.getInt("maxTier", 5);

        if (sigil == null || paramName == null || config == null) return;

        TierParameterConfig params = config.getParams();
        List<Double> values = new ArrayList<>(params.getValues(paramName));

        // Ensure values list has enough entries
        while (values.size() < maxTier) {
            values.add(0.0);
        }

        // Get increment amount from session (default 1)
        double increment = session.getDouble("increment", 1.0);
        int page = session.getInt("page", 1);
        int totalPages = (int) Math.ceil((double) maxTier / TIERS_PER_PAGE);

        switch (slot) {
            case SLOT_BACK -> {
                // Return to tier config, preserving the edited config
                session.put("tierConfig", config);
                TierConfigHandler.openGUI(guiManager, player, sigil, config,
                        session.get("xpConfig", com.miracle.arcanesigils.tier.TierXPConfig.class),
                        maxTier);
                playSound(player, "close");
            }
            case SLOT_SAVE -> {
                // Save to sigil
                params.setValues(paramName, values);
                sigil.setTierScalingConfig(config);
                plugin.getSigilManager().saveSigil(sigil);
                player.sendMessage(TextUtil.colorize("§aParameter §f{" + paramName + "}&a saved!"));
                playSound(player, "success");
            }
            case SLOT_DELETE -> {
                if (event.isShiftClick()) {
                    params.removeParameter(paramName);
                    session.put("tierConfig", config);
                    player.sendMessage(TextUtil.colorize("§cDeleted parameter: §f{" + paramName + "}"));
                    TierConfigHandler.openGUI(guiManager, player, sigil, config,
                            session.get("xpConfig", com.miracle.arcanesigils.tier.TierXPConfig.class),
                            maxTier);
                    playSound(player, "click");
                } else {
                    player.sendMessage(TextUtil.colorize("§eShift+Click to delete this parameter"));
                }
            }
            case SLOT_INC_01 -> {
                session.put("increment", 0.1);
                updateIncrementButtons(player.getOpenInventory().getTopInventory(), 0.1);
                playSound(player, "click");
            }
            case SLOT_INC_05 -> {
                session.put("increment", 0.5);
                updateIncrementButtons(player.getOpenInventory().getTopInventory(), 0.5);
                playSound(player, "click");
            }
            case SLOT_INC_1 -> {
                session.put("increment", 1.0);
                updateIncrementButtons(player.getOpenInventory().getTopInventory(), 1.0);
                playSound(player, "click");
            }
            case SLOT_INC_5 -> {
                session.put("increment", 5.0);
                updateIncrementButtons(player.getOpenInventory().getTopInventory(), 5.0);
                playSound(player, "click");
            }
            case SLOT_INC_10 -> {
                session.put("increment", 10.0);
                updateIncrementButtons(player.getOpenInventory().getTopInventory(), 10.0);
                playSound(player, "click");
            }
            case SLOT_COPY_T1 -> {
                // Fill all tiers with T1 value
                double t1Value = values.isEmpty() ? 0 : values.get(0);
                for (int i = 0; i < values.size(); i++) {
                    values.set(i, t1Value);
                }
                params.setValues(paramName, values);
                player.sendMessage(TextUtil.colorize("§aFilled all tiers with §f" + formatValue(t1Value)));
                updateAllTierSlots(player.getOpenInventory().getTopInventory(), values, maxTier, increment, page);
                playSound(player, "click");
            }
            case SLOT_PREV_PAGE -> {
                if (page > 1) {
                    session.put("page", page - 1);
                    refreshGUI(player, session, sigil, paramName, config, values, maxTier);
                    playSound(player, "click");
                }
            }
            case SLOT_NEXT_PAGE -> {
                if (page < totalPages) {
                    session.put("page", page + 1);
                    refreshGUI(player, session, sigil, paramName, config, values, maxTier);
                    playSound(player, "click");
                }
            }
            default -> {
                // Check if clicking on a tier slot
                int tierIndex = getTierFromSlot(slot, maxTier, page);
                if (tierIndex >= 0 && tierIndex < values.size()) {
                    if (event.isLeftClick()) {
                        // Increase value
                        values.set(tierIndex, values.get(tierIndex) + increment);
                        params.setValues(paramName, values);
                        updateTierSlot(player.getOpenInventory().getTopInventory(), tierIndex, values, maxTier, increment, page);
                        playSound(player, "click");
                    } else if (event.isRightClick()) {
                        // Decrease value
                        values.set(tierIndex, Math.max(0, values.get(tierIndex) - increment));
                        params.setValues(paramName, values);
                        updateTierSlot(player.getOpenInventory().getTopInventory(), tierIndex, values, maxTier, increment, page);
                        playSound(player, "click");
                    } else if (event.isShiftClick()) {
                        // Direct input
                        int tier = tierIndex + 1;
                        guiManager.getInputHelper().requestNumber(player, "T" + tier + " Value",
                                values.get(tierIndex), 0, 10000,
                                newValue -> {
                                    values.set(tierIndex, newValue);
                                    params.setValues(paramName, values);
                                    refreshGUI(player, session, sigil, paramName, config, values, maxTier);
                                },
                                () -> refreshGUI(player, session, sigil, paramName, config, values, maxTier));
                    }
                }
            }
        }
    }

    private int getTierFromSlot(int slot, int maxTier, int page) {
        // Tier slots are in rows 1-4 (slots 9-44)
        if (slot < TIERS_START || slot >= TIERS_START + TIERS_PER_PAGE) {
            return -1;
        }
        int slotIndex = slot - TIERS_START;
        int tierIndex = (page - 1) * TIERS_PER_PAGE + slotIndex;
        if (tierIndex >= maxTier) {
            return -1;
        }
        return tierIndex;
    }

    private void updateTierSlot(Inventory inv, int tierIndex, List<Double> values, int maxTier, double increment, int page) {
        int slotIndex = tierIndex - ((page - 1) * TIERS_PER_PAGE);
        if (slotIndex >= 0 && slotIndex < TIERS_PER_PAGE) {
            int slot = TIERS_START + slotIndex;
            inv.setItem(slot, buildTierItem(tierIndex, values, maxTier, increment));
        }
    }

    private void updateIncrementButtons(Inventory inv, double selectedIncrement) {
        inv.setItem(SLOT_INC_01, buildIncrementItem(0.1, selectedIncrement));
        inv.setItem(SLOT_INC_05, buildIncrementItem(0.5, selectedIncrement));
        inv.setItem(SLOT_INC_1, buildIncrementItem(1.0, selectedIncrement));
        inv.setItem(SLOT_INC_5, buildIncrementItem(5.0, selectedIncrement));
        inv.setItem(SLOT_INC_10, buildIncrementItem(10.0, selectedIncrement));
    }

    private void updateAllTierSlots(Inventory inv, List<Double> values, int maxTier, double increment, int page) {
        int startTier = (page - 1) * TIERS_PER_PAGE;
        int endTier = Math.min(startTier + TIERS_PER_PAGE, maxTier);
        for (int tierIndex = startTier; tierIndex < endTier; tierIndex++) {
            int slotIndex = tierIndex - startTier;
            int slot = TIERS_START + slotIndex;
            inv.setItem(slot, buildTierItem(tierIndex, values, maxTier, increment));
        }
    }

    private void refreshGUI(Player player, GUISession session, Sigil sigil, String paramName,
                           TierScalingConfig config, List<Double> values, int maxTier) {
        openGUI(guiManager, player, sigil, paramName, config, values, maxTier, session);
    }

    /**
     * Open the tier param editor GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String paramName,
                               TierScalingConfig config, List<Double> values, int maxTier, GUISession existingSession) {
        int page = existingSession != null ? existingSession.getInt("page", 1) : 1;
        int totalPages = (int) Math.ceil((double) maxTier / TIERS_PER_PAGE);
        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;

        String title = totalPages > 1
                ? "§8Edit: §e{" + paramName + "} §7(" + page + "/" + totalPages + ")"
                : "§8Edit: §e{" + paramName + "}";
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, TextUtil.parseComponent(title));

        double increment = existingSession != null ? existingSession.getDouble("increment", 1.0) : 1.0;

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Back button
        inv.setItem(SLOT_BACK, ItemBuilder.createItem(Material.RED_DYE, "§cBack", "§7Return to tier config"));

        // Param name display
        inv.setItem(SLOT_PARAM_NAME, ItemBuilder.createItem(Material.NAME_TAG,
                "§e{" + paramName + "}",
                "§7Click tier slots to edit values",
                "",
                "§fLeft-Click&7: Increase by " + formatValue(increment),
                "§fRight-Click&7: Decrease by " + formatValue(increment),
                "§fShift-Click&7: Enter exact value",
                "",
                "§7Tiers: §f" + maxTier + (totalPages > 1 ? " §8(Page " + page + "/" + totalPages + ")" : "")
        ));

        // Delete button
        inv.setItem(SLOT_DELETE, ItemBuilder.createItem(Material.BARRIER,
                "§cDelete Parameter",
                "§7Shift+Click to remove",
                "§7this parameter entirely"
        ));

        // Increment buttons (decimal and whole numbers)
        inv.setItem(SLOT_INC_01, buildIncrementItem(0.1, increment));
        inv.setItem(SLOT_INC_05, buildIncrementItem(0.5, increment));
        inv.setItem(SLOT_INC_1, buildIncrementItem(1.0, increment));
        inv.setItem(SLOT_INC_5, buildIncrementItem(5.0, increment));
        inv.setItem(SLOT_INC_10, buildIncrementItem(10.0, increment));

        // Save button
        inv.setItem(SLOT_SAVE, ItemBuilder.createItem(Material.LIME_DYE,
                "§aSave Parameter",
                "§7Save changes to sigil"
        ));

        // Tier slots (with pagination)
        int startTier = (page - 1) * TIERS_PER_PAGE;
        int endTier = Math.min(startTier + TIERS_PER_PAGE, maxTier);
        for (int tierIndex = startTier; tierIndex < endTier; tierIndex++) {
            int slotIndex = tierIndex - startTier;
            int slot = TIERS_START + slotIndex;
            inv.setItem(slot, buildTierItem(tierIndex, values, maxTier, increment));
        }

        // Quick action button (keep Copy T1 as it's simple and useful)
        inv.setItem(SLOT_COPY_T1, ItemBuilder.createItem(Material.REPEATER,
                "§eFill All with T1",
                "§7Copy T1 value to all tiers",
                "§8Useful for starting with a base value"
        ));

        // Pagination buttons
        if (page > 1) {
            inv.setItem(SLOT_PREV_PAGE, ItemBuilder.createItem(Material.ARROW,
                    "§e← Previous Page",
                    "§7Go to page " + (page - 1),
                    "§7Tiers " + ((page - 2) * TIERS_PER_PAGE + 1) + "-" + ((page - 1) * TIERS_PER_PAGE)
            ));
        } else {
            inv.setItem(SLOT_PREV_PAGE, ItemBuilder.createItem(Material.GRAY_STAINED_GLASS_PANE, "§7", ""));
        }

        if (page < totalPages) {
            inv.setItem(SLOT_NEXT_PAGE, ItemBuilder.createItem(Material.ARROW,
                    "§eNext Page →",
                    "§7Go to page " + (page + 1),
                    "§7Tiers " + (page * TIERS_PER_PAGE + 1) + "-" + Math.min((page + 1) * TIERS_PER_PAGE, maxTier)
            ));
        } else {
            inv.setItem(SLOT_NEXT_PAGE, ItemBuilder.createItem(Material.GRAY_STAINED_GLASS_PANE, "§7", ""));
        }

        // Preview
        inv.setItem(SLOT_PREVIEW, buildPreviewItem(paramName, values, maxTier));

        // Create session
        GUISession session = new GUISession(GUIType.TIER_PARAM_EDITOR);
        session.put("sigil", sigil);
        session.put("sigilId", sigil.getId());
        session.put("paramName", paramName);
        session.put("tierConfig", config);
        session.put("maxTier", maxTier);
        session.put("increment", increment);
        session.put("page", page);
        if (existingSession != null) {
            session.put("xpConfig", existingSession.get("xpConfig"));
        }

        guiManager.openGUI(player, inv, session);
    }

    private static ItemStack buildIncrementItem(double value, double currentIncrement) {
        boolean selected = Math.abs(value - currentIncrement) < 0.01;
        Material mat = selected ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        String color = selected ? "§a" : "§7";
        return ItemBuilder.createItem(mat,
                color + "±" + formatValue(value),
                selected ? "§aSelected" : "§7Click to select"
        );
    }

    private static ItemStack buildTierItem(int tierIndex, List<Double> values, int maxTier, double increment) {
        int tier = tierIndex + 1;
        double value = tierIndex < values.size() ? values.get(tierIndex) : 0;

        // Color gradient based on tier progression
        Material mat;
        if (tier == 1) {
            mat = Material.WHITE_STAINED_GLASS_PANE;
        } else if (tier == maxTier) {
            mat = Material.LIME_STAINED_GLASS_PANE;
        } else if (tier > maxTier * 0.7) {
            mat = Material.YELLOW_STAINED_GLASS_PANE;
        } else if (tier > maxTier * 0.4) {
            mat = Material.ORANGE_STAINED_GLASS_PANE;
        } else {
            mat = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
        }

        List<String> lore = new ArrayList<>();
        lore.add("§7Value: §f" + formatValue(value));
        lore.add("");
        lore.add("§fLeft-Click&7: +" + formatValue(increment));
        lore.add("§fRight-Click&7: -" + formatValue(increment));
        lore.add("§fShift-Click&7: Enter value");

        return ItemBuilder.createItem(mat, "§eTier " + tier, lore.toArray(new String[0]));
    }

    private static ItemStack buildPreviewItem(String paramName, List<Double> values, int maxTier) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Values for §e{" + paramName + "}&7:");
        lore.add("");

        // Show progression
        StringBuilder progression = new StringBuilder();
        for (int i = 0; i < Math.min(maxTier, 5); i++) {
            if (i > 0) progression.append(" → ");
            double val = i < values.size() ? values.get(i) : 0;
            progression.append("§f").append(formatValue(val));
        }
        if (maxTier > 5) {
            progression.append(" → §f...");
        }
        lore.add(progression.toString());

        // Show T1 and max
        if (maxTier > 1) {
            double t1 = values.isEmpty() ? 0 : values.get(0);
            double tMax = maxTier <= values.size() ? values.get(maxTier - 1) : 0;
            lore.add("");
            lore.add("§7T1: §f" + formatValue(t1));
            lore.add("§7T" + maxTier + ": §a" + formatValue(tMax));

            if (t1 > 0) {
                double ratio = tMax / t1;
                lore.add("§7Ratio: §e" + String.format("%.1fx", ratio));
            }
        }

        return ItemBuilder.createItem(Material.SPYGLASS, "§ePreview", lore.toArray(new String[0]));
    }

    private static String formatValue(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }
}
