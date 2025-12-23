package com.zenax.armorsets.gui.tier;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.tier.TierParameterConfig;
import com.zenax.armorsets.tier.TierScalingConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zenax.armorsets.flow.FlowConfig;
import com.zenax.armorsets.utils.TextUtil;

/**
 * Browser for selecting which effect parameters to add to tier scaling.
 * Scans the sigil's effects for {param} placeholders and shows them as options.
 *
 * Layout:
 * Row 0: [Back] [---] [---] [Info] [---] [---] [---] [---] [Custom]
 * Row 1-4: Effect parameters (grouped by signal/effect)
 * Row 5: [---] [---] [---] [---] [---] [---] [---] [---] [---]
 */
public class TierParamSelectorHandler extends AbstractHandler {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    private static final int SLOT_BACK = 0;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_CUSTOM = 8;
    private static final int PARAMS_START = 9;
    private static final int PARAMS_PER_PAGE = 36; // 4 rows

    public TierParamSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Sigil sigil = session.get("sigil", Sigil.class);
        TierScalingConfig config = session.get("tierConfig", TierScalingConfig.class);
        if (sigil == null || config == null) return;

        int maxTier = session.getInt("maxTier", sigil.getMaxTier());

        switch (slot) {
            case SLOT_BACK -> {
                TierConfigHandler.openGUI(guiManager, player, sigil, config,
                        session.get("xpConfig", com.zenax.armorsets.tier.TierXPConfig.class),
                        maxTier);
                playSound(player, "close");
            }
            case SLOT_CUSTOM -> {
                // Allow custom parameter name entry
                guiManager.getInputHelper().requestText(player, "Parameter Name", "damage",
                        paramName -> {
                            if (paramName == null || paramName.isEmpty()) {
                                openGUI(guiManager, player, sigil, session);
                                return;
                            }
                            paramName = paramName.toLowerCase().replaceAll("[^a-z0-9_]", "");
                            addParameterAndOpenEditor(player, sigil, paramName, config, maxTier, session);
                        },
                        () -> openGUI(guiManager, player, sigil, session)
                );
            }
            default -> {
                // Check if clicking on a parameter option
                @SuppressWarnings("unchecked")
                Map<Integer, ParamInfo> slotMap = (Map<Integer, ParamInfo>) session.get("paramSlotMap");
                if (slotMap != null && slotMap.containsKey(slot)) {
                    ParamInfo info = slotMap.get(slot);
                    addParameterAndOpenEditor(player, sigil, info.name, config, maxTier, session);
                }
            }
        }
    }

    private void addParameterAndOpenEditor(Player player, Sigil sigil, String paramName,
                                           TierScalingConfig config, int maxTier, GUISession session) {
        TierParameterConfig params = config.getParams();

        if (params.hasParameter(paramName)) {
            player.sendMessage(TextUtil.colorize("§eParameter §f{" + paramName + "}&e already exists, opening editor..."));
        } else {
            // Add with default values
            params.addParameter(paramName, maxTier, 0);
            player.sendMessage(TextUtil.colorize("§aAdded parameter: §f{" + paramName + "}"));
        }

        session.put("tierConfig", config);

        // Get values and open editor
        List<Double> values = new ArrayList<>(params.getValues(paramName));
        while (values.size() < maxTier) {
            values.add(0.0);
        }

        TierParamEditorHandler.openGUI(guiManager, player, sigil, paramName, config, values, maxTier, session);
        playSound(player, "click");
    }

    /**
     * Open the parameter selector GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, GUISession existingSession) {
        Inventory inv = Bukkit.createInventory(null, 54,
                TextUtil.parseComponent("§8Select Parameter to Add"));

        TierScalingConfig config = existingSession.get("tierConfig", TierScalingConfig.class);
        int maxTier = existingSession.getInt("maxTier", sigil.getMaxTier());

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Back button
        inv.setItem(SLOT_BACK, ItemBuilder.createItem(Material.RED_DYE, "§cBack", "§7Return to tier config"));

        // Info
        inv.setItem(SLOT_INFO, ItemBuilder.createItem(Material.BOOK,
                "§eEffect Parameters",
                "§7These are the §f{param}&7 placeholders",
                "§7found in your sigil's effects.",
                "",
                "§7Click a parameter to add it",
                "§7to the tier scaling config."
        ));

        // Custom entry button
        inv.setItem(SLOT_CUSTOM, ItemBuilder.createItem(Material.WRITABLE_BOOK,
                "§aCustom Parameter",
                "§7Enter a custom parameter name",
                "§7not listed here",
                "",
                "§eClick to enter name"
        ));

        // Scan effects for placeholders
        Map<String, ParamInfo> foundParams = scanEffectsForPlaceholders(sigil);
        Set<String> existingParams = config != null && config.getParams() != null
                ? config.getParams().getParameterNames()
                : Collections.emptySet();

        // Build slot map
        Map<Integer, ParamInfo> slotMap = new HashMap<>();
        int slotIndex = 0;

        for (Map.Entry<String, ParamInfo> entry : foundParams.entrySet()) {
            if (slotIndex >= PARAMS_PER_PAGE) break;

            int slot = PARAMS_START + slotIndex;
            ParamInfo info = entry.getValue();
            slotMap.put(slot, info);

            boolean alreadyExists = existingParams.contains(info.name);

            // Different materials for built-in vs effect params
            Material mat;
            if (info.isBuiltIn) {
                mat = alreadyExists ? Material.ORANGE_STAINED_GLASS_PANE : Material.GOLD_BLOCK;
            } else {
                mat = alreadyExists ? Material.YELLOW_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE;
            }

            List<String> lore = new ArrayList<>();
            if (info.isBuiltIn) {
                lore.add("§6Built-in Signal Parameter");
            }
            lore.add("");
            lore.add(info.isBuiltIn ? "§7Description:" : "§7Found in effects:");
            for (String usage : info.usages) {
                if (lore.size() > 6) {
                    lore.add("§8... and more");
                    break;
                }
                lore.add("§8• §f" + truncate(usage, 35));
            }
            lore.add("");
            if (alreadyExists) {
                lore.add("§eAlready configured");
                lore.add("§eClick to edit values");
            } else {
                lore.add("§aClick to add");
            }

            inv.setItem(slot, ItemBuilder.createItem(mat,
                    "§e{" + info.name + "}",
                    lore.toArray(new String[0])));

            slotIndex++;
        }

        // If no params found, show message
        if (foundParams.isEmpty()) {
            inv.setItem(22, ItemBuilder.createItem(Material.BARRIER,
                    "§cNo Placeholders Found",
                    "§7Your effects don't have any",
                    "§7{param} placeholders yet.",
                    "",
                    "§7Add placeholders like §f{damage}&7",
                    "§7to your effect strings first,",
                    "§7or use §aCustom Parameter&7."
            ));
        }

        // Create session
        GUISession session = new GUISession(GUIType.TIER_PARAM_SELECTOR);
        session.put("sigil", sigil);
        session.put("sigilId", sigil.getId());
        session.put("tierConfig", config);
        session.put("maxTier", maxTier);
        session.put("paramSlotMap", slotMap);
        if (existingSession != null) {
            session.put("xpConfig", existingSession.get("xpConfig"));
        }

        guiManager.openGUI(player, inv, session);
    }

    /**
     * Scan all effects on the sigil for {param} placeholders,
     * and add built-in signal properties (chance, cooldown).
     */
    private static Map<String, ParamInfo> scanEffectsForPlaceholders(Sigil sigil) {
        Map<String, ParamInfo> found = new LinkedHashMap<>();

        // Always add built-in signal parameters first
        ParamInfo chanceInfo = new ParamInfo("chance");
        chanceInfo.isBuiltIn = true;
        chanceInfo.usages.add("Signal activation chance (%)");
        found.put("chance", chanceInfo);

        ParamInfo cooldownInfo = new ParamInfo("cooldown");
        cooldownInfo.isBuiltIn = true;
        cooldownInfo.usages.add("Signal cooldown (seconds)");
        found.put("cooldown", cooldownInfo);

        // Get all flows with their configs
        List<FlowConfig> flows = sigil.getFlows();
        if (flows == null) return found;

        for (FlowConfig flowConfig : flows) {
            String flowKey = flowConfig.getTrigger() != null ? flowConfig.getTrigger() : "flow";

            if (flowConfig.getGraph() == null) continue;

            // Scan all nodes in the flow graph for placeholders
            var nodes = flowConfig.getGraph().getNodes();
            if (nodes == null) continue;

            for (var node : nodes) {
                if (node.getParams() == null) continue;

                // Check effect data for placeholders - scan all parameter values
                String effectData = node.getParams().toString();
                Matcher matcher = PLACEHOLDER_PATTERN.matcher(effectData);
                while (matcher.find()) {
                    String paramName = matcher.group(1).toLowerCase();

                    // Skip context placeholders that aren't tier params
                    if (isContextPlaceholder(paramName)) continue;

                    ParamInfo info = found.computeIfAbsent(paramName, k -> new ParamInfo(k));
                    // Create a short description: FLOW -> NODE_TYPE
                    String nodeType = node.getType() != null ? node.getType().name() : "UNKNOWN";
                    info.usages.add(flowKey + " → " + nodeType);
                }
            }
        }

        // Also scan activation config if it exists (legacy support)
        Sigil.ActivationConfig activation = sigil.getActivation();
        if (activation != null && activation.getEffects() != null) {
            for (String effectString : activation.getEffects()) {
                Matcher matcher = PLACEHOLDER_PATTERN.matcher(effectString);
                while (matcher.find()) {
                    String paramName = matcher.group(1).toLowerCase();
                    if (isContextPlaceholder(paramName)) continue;

                    ParamInfo info = found.computeIfAbsent(paramName, k -> new ParamInfo(k));
                    String effectType = effectString.split(":")[0].split("\\s+")[0];
                    info.usages.add("ACTIVATION → " + effectType);
                }
            }
        }

        return found;
    }

    /**
     * Check if a placeholder is a context placeholder (not a tier param).
     * These are runtime values like player name, not configurable tier values.
     */
    private static boolean isContextPlaceholder(String name) {
        return switch (name.toLowerCase()) {
            case "player", "target", "victim", "attacker",
                 "world", "location", "biome", "tier" -> true;
            default -> false;
        };
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen - 3) + "..." : str;
    }

    /**
     * Info about a found parameter.
     */
    public static class ParamInfo {
        final String name;
        final List<String> usages = new ArrayList<>();
        boolean isBuiltIn = false;

        ParamInfo(String name) {
            this.name = name;
        }
    }
}
