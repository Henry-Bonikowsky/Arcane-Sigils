package com.miracle.arcanesigils.gui.params;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.flow.FlowConfig;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.gui.flow.FlowBuilderHandler;
import com.miracle.arcanesigils.gui.sigil.SigilEditorHandler;
import com.miracle.arcanesigils.tier.TierParameterConfig;
import com.miracle.arcanesigils.tier.TierScalingConfig;
import com.miracle.arcanesigils.utils.TextUtil;
import net.kyori.adventure.text.Component;
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
 * Quick parameter editor for non-programmers (Alex-friendly).
 * Shows all tier-scalable parameters in a simple table format.
 * 
 * Layout (54 slots):
 * Row 0: [← Back] [Edit Flow] [Help] [ ] [ ] [ ] [ ] [ ] [ ]
 * Row 1: [Param1 Name____] [T1] [T2] [T3] [T4] [T5] [ ] [ ] [ ]
 * Row 2: [Param2 Name____] [T1] [T2] [T3] [T4] [T5] [ ] [ ] [ ]
 * Row 3: [Param3 Name____] [T1] [T2] [T3] [T4] [T5] [ ] [ ] [ ]
 * Row 4: [Param4 Name____] [T1] [T2] [T3] [T4] [T5] [ ] [ ] [ ]
 * Row 5: [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
 * 
 * Click a tier value to edit it directly via chat input.
 */
public class QuickParamEditorHandler extends AbstractHandler {

    private static final int SLOT_BACK = 0;
    private static final int SLOT_ADVANCED = 1;
    private static final int SLOT_HELP = 2;
    
    private static final int MAX_PARAMS = 4; // 4 rows for params
    private static final int MAX_TIERS = 5;  // Support up to 5 tiers in this view
    
    public QuickParamEditorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);
        
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        
        if (sigil == null) {
            player.closeInventory();
            return;
        }
        
        switch (slot) {
            case SLOT_BACK -> {
                playSound(player, "click");
                SigilEditorHandler.openGUI(guiManager, player, sigil);
            }
            case SLOT_ADVANCED -> {
                playSound(player, "click");
                // Open full Flow Builder for advanced users
                FlowConfig flowConfig = sigil.getFlowForTrigger(signalKey);
                if (flowConfig != null && flowConfig.getGraph() != null) {
                    FlowBuilderHandler.openGUI(guiManager, player, sigil, signalKey, 
                        flowConfig.getGraph(), flowConfig);
                } else {
                    FlowBuilderHandler.openGUI(guiManager, player, sigil, signalKey);
                }
            }
            case SLOT_HELP -> {
                playSound(player, "click");
                player.sendMessage(TextUtil.colorize("§e§l=== Quick Parameter Editor ==="));
                player.sendMessage(TextUtil.colorize("§7• §fClick tier value§7: Edit that tier's value"));
                player.sendMessage(TextUtil.colorize("§7• §fEdit Flow§7: Open advanced Flow Builder"));
                player.sendMessage(TextUtil.colorize("§7• Changes save automatically!"));
            }
            default -> {
                // Check if clicking a parameter value
                ParamSlot paramSlot = getParamSlot(slot);
                if (paramSlot != null) {
                    handleParamClick(player, session, sigil, paramSlot, event);
                }
            }
        }
    }

    private void handleParamClick(Player player, GUISession session, Sigil sigil, 
                                   ParamSlot paramSlot, InventoryClickEvent event) {
        TierScalingConfig tierConfig = sigil.getTierScalingConfig();
        if (tierConfig == null) {
            player.sendMessage(TextUtil.colorize("§cNo tier config found!"));
            return;
        }
        
        TierParameterConfig params = tierConfig.getParams();
        List<String> paramNames = new ArrayList<>(params.getParameterNames());
        
        if (paramSlot.paramIndex >= paramNames.size()) {
            return; // Invalid param index
        }
        
        String paramName = paramNames.get(paramSlot.paramIndex);
        List<Double> values = new ArrayList<>(params.getValues(paramName));
        
        // Ensure we have enough values
        int maxTier = sigil.getMaxTier();
        while (values.size() < maxTier) {
            values.add(0.0);
        }
        
        if (paramSlot.tierIndex >= values.size()) {
            return; // Invalid tier index
        }
        
        // Request new value from player
        int tier = paramSlot.tierIndex + 1;
        double currentValue = values.get(paramSlot.tierIndex);
        
        player.closeInventory();
        player.sendMessage(TextUtil.colorize("§e§lEdit " + paramName + " (Tier " + tier + ")"));
        player.sendMessage(TextUtil.colorize("§7Current value: §f" + formatValue(currentValue)));
        player.sendMessage(TextUtil.colorize("§7Type new value in chat (or 'cancel'):"));
        
        // Use async chat input
        guiManager.getInputHelper().requestNumber(player, paramName + " T" + tier,
            currentValue, 0, 10000,
            newValue -> {
                // Save the new value
                values.set(paramSlot.tierIndex, newValue);
                params.setValues(paramName, values);
                sigil.setTierScalingConfig(tierConfig);
                plugin.getSigilManager().saveSigil(sigil);
                
                player.sendMessage(TextUtil.colorize("§a§l✓ §aSaved " + paramName + 
                    " tier " + tier + " = §f" + formatValue(newValue)));
                playSound(player, "success");
                
                // Reopen GUI
                String signalKey = session.get("signalKey", String.class);
                openGUI(guiManager, player, sigil, signalKey, session);
            },
            () -> {
                // Cancel - just reopen GUI
                String signalKey = session.get("signalKey", String.class);
                openGUI(guiManager, player, sigil, signalKey, session);
            });
    }

    private ParamSlot getParamSlot(int slot) {
        // Parameters start at row 1 (slot 9)
        // Layout: [Name] [T1] [T2] [T3] [T4] [T5] [empty] [empty] [empty]
        int row = slot / 9 - 1; // Row 0 is buttons, params start at row 1
        if (row < 0 || row >= MAX_PARAMS) {
            return null;
        }
        
        int col = slot % 9;
        if (col < 1 || col > 5) {
            return null; // Column 0 is name, 1-5 are tier values
        }
        
        int tierIndex = col - 1; // T1 = col 1, index 0
        return new ParamSlot(row, tierIndex);
    }

    private static class ParamSlot {
        final int paramIndex;
        final int tierIndex;
        
        ParamSlot(int paramIndex, int tierIndex) {
            this.paramIndex = paramIndex;
            this.tierIndex = tierIndex;
        }
    }

    private String formatValue(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // ============ Static Open Methods ============

    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, 
                                String signalKey) {
        openGUI(guiManager, player, sigil, signalKey, null);
    }

    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, 
                                String signalKey, GUISession existingSession) {
        TierScalingConfig tierConfig = sigil.getTierScalingConfig();
        if (tierConfig == null || tierConfig.getParams().isEmpty()) {
            player.sendMessage(TextUtil.colorize("§eNo tier parameters defined for this sigil."));
            player.sendMessage(TextUtil.colorize("§7Tip: Use §fEdit Flow§7 to create parameters."));
            // Fall back to Flow Builder
            FlowBuilderHandler.openGUI(guiManager, player, sigil, signalKey);
            return;
        }

        String title = "§7" + sigil.getName() + " > §fParams";
        Inventory inv = Bukkit.createInventory(null, 54, TextUtil.parseComponent(title));

        GUISession session;
        if (existingSession != null && existingSession.getType() == GUIType.QUICK_PARAM_EDITOR) {
            session = existingSession;
        } else {
            session = new GUISession(GUIType.QUICK_PARAM_EDITOR);
            session.put("sigil", sigil);
            session.put("signalKey", signalKey);
        }

        buildInventory(inv, sigil, signalKey);
        guiManager.openGUI(player, inv, session);
    }

    private static void buildInventory(Inventory inv, Sigil sigil, String signalKey) {
        TierScalingConfig tierConfig = sigil.getTierScalingConfig();
        TierParameterConfig params = tierConfig.getParams();
        
        // Row 0: Buttons
        inv.setItem(SLOT_BACK, ItemBuilder.createItem(Material.BARRIER, "§c← Back", 
            "§7Return to sigil editor"));
        inv.setItem(SLOT_ADVANCED, ItemBuilder.createItem(Material.COMPASS, "§6Edit Flow (Advanced)", 
            "§7Open full Flow Builder",
            "§7For creating complex effects"));
        inv.setItem(SLOT_HELP, ItemBuilder.createItem(Material.BOOK, "§e? Help", 
            "§7Show controls"));
        
        // Fill rest of row 0 with background
        for (int i = 3; i < 9; i++) {
            inv.setItem(i, ItemBuilder.createBackground());
        }

        // Rows 1-4: Parameter rows
        List<String> paramNames = new ArrayList<>(params.getParameterNames());
        int maxTier = sigil.getMaxTier();
        
        for (int paramIndex = 0; paramIndex < Math.min(paramNames.size(), MAX_PARAMS); paramIndex++) {
            String paramName = paramNames.get(paramIndex);
            List<Double> values = params.getValues(paramName);
            
            int row = paramIndex + 1; // Row 1-4
            int startSlot = row * 9;
            
            // Column 0: Parameter name
            List<String> nameLore = new ArrayList<>();
            nameLore.add("§7Parameter: §f{" + paramName + "}");
            nameLore.add("");
            nameLore.add("§7This value scales with tier.");
            nameLore.add("§7Click tier values to edit.");
            
            inv.setItem(startSlot, ItemBuilder.createItem(Material.NAME_TAG, 
                "§e{" + paramName + "}", nameLore));
            
            // Columns 1-5: Tier values (T1-T5)
            for (int tier = 1; tier <= Math.min(maxTier, MAX_TIERS); tier++) {
                int tierIndex = tier - 1;
                double value = tierIndex < values.size() ? values.get(tierIndex) : 0.0;
                
                List<String> tierLore = new ArrayList<>();
                tierLore.add("§7Parameter: §f{" + paramName + "}");
                tierLore.add("§7Tier: §f" + tier);
                tierLore.add("§7Value: §a" + formatValueStatic(value));
                tierLore.add("");
                tierLore.add("§eClick to edit");
                
                Material mat = getTierMaterial(tier);
                inv.setItem(startSlot + tier, ItemBuilder.createItem(mat, 
                    "§fT" + tier + ": §a" + formatValueStatic(value), tierLore));
            }
            
            // Fill remaining columns with background
            for (int col = Math.min(maxTier, MAX_TIERS) + 1; col < 9; col++) {
                inv.setItem(startSlot + col, ItemBuilder.createBackground());
            }
        }
        
        // Fill unused param rows with background
        for (int paramIndex = paramNames.size(); paramIndex < MAX_PARAMS; paramIndex++) {
            int row = paramIndex + 1;
            for (int col = 0; col < 9; col++) {
                inv.setItem(row * 9 + col, ItemBuilder.createBackground());
            }
        }
        
        // Row 5: Background
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, ItemBuilder.createBackground());
        }
    }

    private static Material getTierMaterial(int tier) {
        return switch (tier) {
            case 1 -> Material.IRON_INGOT;
            case 2 -> Material.GOLD_INGOT;
            case 3 -> Material.DIAMOND;
            case 4 -> Material.EMERALD;
            case 5 -> Material.NETHERITE_INGOT;
            default -> Material.PAPER;
        };
    }

    private static String formatValueStatic(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
