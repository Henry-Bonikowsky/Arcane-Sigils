package com.zenax.armorsets.gui.flow;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.flow.*;
import com.zenax.armorsets.flow.nodes.*;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.gui.common.ItemBuilder;
import com.zenax.armorsets.gui.effect.EffectParamHandler;
import com.zenax.armorsets.gui.effect.EffectParamHandler.EffectParamConfig;
import com.zenax.armorsets.gui.effect.EffectParamHandler.ParamInfo;
import com.zenax.armorsets.gui.effect.EffectParamHandler.ParamType;
import com.zenax.armorsets.gui.effect.ParticleSelectorHandler;
import com.zenax.armorsets.gui.effect.SoundSelectorHandler;
import com.zenax.armorsets.gui.input.SignInputHelper;
import com.zenax.armorsets.utils.LogHelper;
import com.zenax.armorsets.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.*;

/**
 * Handler for configuring node parameters.
 * Opens when clicking a param in the Flow Builder side panel.
 * Supports pagination for effects with many parameters.
 */
public class NodeConfigHandler extends AbstractHandler {

    private static final int INVENTORY_SIZE = 54;  // 6 rows for more params
    private static final int[] PARAM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,  // Row 1: 7 slots
        19, 20, 21, 22, 23, 24, 25   // Row 2: 7 slots (14 total visible per page)
    };
    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV_PAGE = 48;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_NEXT_PAGE = 50;
    private static final int PARAMS_PER_PAGE = 14;

    // Tier scaling slots (rows 4-5)
    private static final int[] TIER_ROW_1 = {37, 38, 39, 40, 41};  // Tiers 1-5
    private static final int[] TIER_ROW_2 = {46, 47, 48, 49, 50};  // Tiers 6-10

    private final SignInputHelper inputHelper;

    public NodeConfigHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
        this.inputHelper = guiManager.getInputHelper();
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        if (slot == SLOT_BACK) {
            goBackToBuilder(player, session);
            return;
        }

        // Handle pagination
        if (slot == SLOT_PREV_PAGE) {
            int page = session.getInt("paramPage", 0);
            if (page > 0) {
                session.put("paramPage", page - 1);
                playSound(player, "click");
                FlowNode node = session.get("configNode", FlowNode.class);
                openGUI(guiManager, player, session, node, 0);
            }
            return;
        }

        if (slot == SLOT_NEXT_PAGE) {
            FlowNode node = session.get("configNode", FlowNode.class);
            if (node == null) return;

            int page = session.getInt("paramPage", 0);
            int totalParams = getTotalParamCount(node);
            int maxPages = (int) Math.ceil((double) totalParams / PARAMS_PER_PAGE);

            if (page < maxPages - 1) {
                session.put("paramPage", page + 1);
                playSound(player, "click");
                openGUI(guiManager, player, session, node, 0);
            }
            return;
        }

        FlowNode node = session.get("configNode", FlowNode.class);
        if (node == null) return;

        // Check if clicking on tier slots
        int tierIndex = getTierSlotIndex(slot);
        if (tierIndex >= 0) {
            handleTierSlotClick(player, session, node, tierIndex, event);
            return;
        }

        // Handle based on node type and slot
        handleParamEdit(player, session, node, slot, event);
    }

    private int getTotalParamCount(FlowNode node) {
        if (node.getType() == NodeType.EFFECT) {
            EffectNode en = (EffectNode) node;
            EffectParamConfig config = EffectParamHandler.getParamConfig(en.getEffectType());

            // Build current param values map for showWhen evaluation
            Map<String, Object> currentParams = new HashMap<>();
            for (ParamInfo pi : config.params) {
                currentParams.put(pi.key, en.getParam(pi.key, pi.defaultValue));
            }

            // Count only visible params
            int visibleCount = 0;
            for (ParamInfo pi : config.params) {
                if (pi.isVisible(currentParams)) {
                    visibleCount++;
                }
            }
            return visibleCount;
        }
        return getParamsForNode(node).size();
    }

    private void handleParamEdit(Player player, GUISession session, FlowNode node, int slot, InventoryClickEvent event) {
        // Convert raw slot to slot index (0, 1, 2, ...)
        // PARAM_SLOTS maps slot numbers to indices
        int slotIndex = -1;
        for (int i = 0; i < PARAM_SLOTS.length; i++) {
            if (PARAM_SLOTS[i] == slot) {
                slotIndex = i;
                break;
            }
        }

        if (slotIndex < 0) {
            return;  // Not a param slot
        }

        switch (node.getType()) {
            case EFFECT -> handleEffectParam(player, session, (EffectNode) node, slot, event);
            case CONDITION -> handleConditionParam(player, session, (ConditionNode) node, slotIndex);
            case DELAY -> handleDelayParam(player, session, (DelayNode) node, slotIndex);
            case LOOP -> handleLoopParam(player, session, (LoopNode) node, slotIndex, event);
            case RANDOM -> handleRandomParam(player, session, (RandomNode) node, slotIndex, event);
            case VARIABLE -> handleVariableParam(player, session, (VariableNode) node, slotIndex, event);
            case TARGET -> handleTargetParam(player, session, (TargetNode) node, slotIndex, event);
            case MATH -> handleMathParam(player, session, (MathNode) node, slotIndex, event);
            case START -> handleStartParam(player, session, (StartNode) node, slot, event);
            default -> {}
        }
    }

    /**
     * Get tier index from slot (0-9), or -1 if not a tier slot.
     */
    private int getTierSlotIndex(int slot) {
        for (int i = 0; i < TIER_ROW_1.length; i++) {
            if (TIER_ROW_1[i] == slot) return i;
        }
        for (int i = 0; i < TIER_ROW_2.length; i++) {
            if (TIER_ROW_2[i] == slot) return i + 5;
        }
        return -1;
    }

    private void handleEffectParam(Player player, GUISession session, EffectNode node, int slot, InventoryClickEvent event) {
        // Find which param index this slot corresponds to
        int slotIndex = -1;
        for (int i = 0; i < PARAM_SLOTS.length; i++) {
            if (PARAM_SLOTS[i] == slot) {
                slotIndex = i;
                break;
            }
        }

        if (slotIndex < 0) {
            return;  // Not a param slot
        }

        // Get effect params config and filter by visibility (same as openGUI)
        EffectParamConfig config = EffectParamHandler.getParamConfig(node.getEffectType());

        // Build current param values map for showWhen evaluation
        Map<String, Object> currentParams = new HashMap<>();
        for (ParamInfo pi : config.params) {
            currentParams.put(pi.key, node.getParam(pi.key, pi.defaultValue));
        }

        // Filter to only visible params
        List<ParamInfo> visibleParams = new ArrayList<>();
        for (ParamInfo pi : config.params) {
            if (pi.isVisible(currentParams)) {
                visibleParams.add(pi);
            }
        }

        // Calculate actual param index based on page (into the FILTERED list)
        int page = session.getInt("paramPage", 0);
        int paramIndex = (page * PARAMS_PER_PAGE) + slotIndex;

        if (paramIndex >= visibleParams.size()) {
            return;  // Out of bounds
        }

        ParamInfo paramInfo = visibleParams.get(paramIndex);
        handleEffectParamByType(player, session, node, paramInfo, event);
    }

    private void handleEffectParamByType(Player player, GUISession session, EffectNode node, ParamInfo paramInfo, InventoryClickEvent event) {
        switch (paramInfo.type) {
            case NUMBER -> {
                // Shift+Click toggles tier scaling
                if (event.isShiftClick()) {
                    toggleTierScaling(player, session, node, paramInfo);
                    return;
                }
                double currentValue = node.getDoubleParam(paramInfo.key, ((Number) paramInfo.defaultValue).doubleValue());
                requestNumberInput(player, session, node, paramInfo.key, paramInfo.displayName, currentValue, paramInfo.min, paramInfo.max);
            }
            case TEXT -> {
                String currentValue = node.getStringParam(paramInfo.key, String.valueOf(paramInfo.defaultValue));
                requestTextInput(player, session, node, paramInfo.key, paramInfo.displayName, currentValue);
            }
            case TARGET, CYCLE -> {
                // Cycle through options
                String[] options = paramInfo.cycleOptions;
                if (options == null || options.length == 0) {
                    // Default target options
                    options = new String[]{"@Self", "@Victim", "@Nearby:5", "@Nearby:10"};
                }
                String current = node.getStringParam(paramInfo.key, String.valueOf(paramInfo.defaultValue));
                int currentIndex = 0;
                for (int i = 0; i < options.length; i++) {
                    if (options[i].equalsIgnoreCase(current)) {
                        currentIndex = i;
                        break;
                    }
                }
                String next = options[(currentIndex + 1) % options.length];
                node.setParam(paramInfo.key, next);
                player.sendMessage(TextUtil.colorize("§a" + paramInfo.displayName + " set to: §f" + next));
                playSound(player, "click");
                saveFlow(player, session);  // Persist to file
                refreshGUI(player, session, node);
            }
            case PARTICLE_BROWSER -> {
                // Open particle browser GUI
                openParticleBrowser(player, session, node, paramInfo.key);
            }
            case SOUND_BROWSER -> {
                // Open sound browser GUI
                openSoundBrowser(player, session, node, paramInfo.key);
            }
            case ATTRIBUTE_BROWSER -> {
                // Open attribute browser GUI
                openAttributeBrowser(player, session, node, paramInfo.key);
            }
        }
    }

    /**
     * Open particle browser for selecting particle type.
     * Creates a bridge session that stores the node and param key for callback.
     */
    private void openParticleBrowser(Player player, GUISession session, EffectNode node, String paramKey) {
        // Create a bridge session for the particle selector
        GUISession browserSession = new GUISession(GUIType.PARTICLE_SELECTOR);
        browserSession.put("sigil", session.get("sigil"));
        browserSession.put("signalKey", session.get("signalKey"));
        browserSession.put("flow", session.get("flow"));
        browserSession.put("originalFlow", session.get("originalFlow"));
        browserSession.put("configNode", node);
        browserSession.put("paramKey", paramKey);
        browserSession.put("flowBuilderSession", session);  // Store original session for return

        // Store effect type for EffectParamHandler compatibility
        browserSession.put("effectType", node.getEffectType());
        browserSession.put("effectIndex", -1);

        // Create params map from node for the selector
        Map<String, Object> params = new HashMap<>();
        params.put(paramKey, node.getStringParam(paramKey, "FLAME"));
        browserSession.put("params", params);

        playSound(player, "click");
        ParticleSelectorHandler.openGUI(guiManager, player, browserSession);
    }

    /**
     * Open sound browser for selecting sound type.
     * Creates a bridge session that stores the node and param key for callback.
     */
    private void openSoundBrowser(Player player, GUISession session, EffectNode node, String paramKey) {
        // Create a bridge session for the sound selector
        GUISession browserSession = new GUISession(GUIType.SOUND_SELECTOR);
        browserSession.put("sigil", session.get("sigil"));
        browserSession.put("signalKey", session.get("signalKey"));
        browserSession.put("flow", session.get("flow"));
        browserSession.put("originalFlow", session.get("originalFlow"));
        browserSession.put("configNode", node);
        browserSession.put("paramKey", paramKey);
        browserSession.put("flowBuilderSession", session);  // Store original session for return

        // Store effect type for EffectParamHandler compatibility
        browserSession.put("effectType", node.getEffectType());
        browserSession.put("effectIndex", -1);

        // Create params map from node for the selector
        Map<String, Object> params = new HashMap<>();
        params.put(paramKey, node.getStringParam(paramKey, "ENTITY_EXPERIENCE_ORB_PICKUP"));
        browserSession.put("params", params);

        playSound(player, "click");
        SoundSelectorHandler.openGUI(guiManager, player, browserSession);
    }

    /**
     * Open attribute browser for selecting attribute type.
     * Uses callback pattern to return selected attribute.
     */
    private void openAttributeBrowser(Player player, GUISession session, EffectNode node, String paramKey) {
        playSound(player, "click");

        // Store reference to node and session for callback
        final GUISession currentSession = session;
        final EffectNode currentNode = node;

        com.zenax.armorsets.gui.effect.AttributeSelectorHandler.openGUI(guiManager, player,
            selectedAttribute -> {
                // Callback when attribute is selected
                currentNode.setParam(paramKey, selectedAttribute);
                player.sendMessage(TextUtil.colorize("§aAttribute set to: §f" +
                    com.zenax.armorsets.effects.impl.ModifyAttributeEffect.getAttributeDisplayName(selectedAttribute)));

                // Return to node config
                int page = currentSession.getInt("paramPage", 0);
                openGUI(guiManager, player, currentSession, currentNode, page);
            },
            cancelled -> {
                // Cancelled - return to node config
                int page = currentSession.getInt("paramPage", 0);
                openGUI(guiManager, player, currentSession, currentNode, page);
            }
        );
    }

    private void handleConditionParam(Player player, GUISession session, ConditionNode node, int slot) {
        if (slot == 0) {
            // Open the condition selector GUI for Flow Builder condition nodes
            // This allows Alex to pick a condition type visually instead of typing it
            Sigil sigil = session.get("sigil", Sigil.class);
            String signalKey = session.get("signalKey", String.class);

            // Create a specialized session for the condition node selector
            GUISession conditionSession = new GUISession(GUIType.CONDITION_NODE_SELECTOR);
            conditionSession.put("sigil", sigil);
            conditionSession.put("signalKey", signalKey);
            conditionSession.put("flow", session.get("flow"));
            conditionSession.put("originalFlow", session.get("originalFlow"));
            conditionSession.put("conditionNode", node);
            conditionSession.put("flowBuilderSession", session);

            // Open the flow builder condition selector
            playSound(player, "click");
            com.zenax.armorsets.gui.condition.FlowConditionSelectorHandler.openGUI(guiManager, player, conditionSession);
        }
    }

    private void handleDelayParam(Player player, GUISession session, DelayNode node, int slot) {
        if (slot == 0) {
            requestNumberInput(player, session, node, "duration", "Duration (seconds)",
                    node.getDoubleParam("duration", 1.0), 0.1, 60);
        }
    }

    private void handleLoopParam(Player player, GUISession session, LoopNode node, int slot, InventoryClickEvent event) {
        switch (slot) {
            case 0 -> {
                // Loop type - cycle
                String current = node.getStringParam("type", "COUNT");
                String next = "COUNT".equals(current) ? "WHILE" : "COUNT";
                node.setParam("type", next);
                player.sendMessage(TextUtil.colorize("§aLoop type: §f" + next));
                playSound(player, "click");
                saveFlow(player, session);
                refreshGUI(player, session, node);
            }
            case 1 -> {
                String type = node.getStringParam("type", "COUNT");
                if ("COUNT".equals(type)) {
                    if (event.isLeftClick()) {
                        node.setParam("count", node.getIntParam("count", 3) + 1);
                    } else if (event.isRightClick()) {
                        node.setParam("count", Math.max(1, node.getIntParam("count", 3) - 1));
                    } else if (event.isShiftClick()) {
                        requestNumberInput(player, session, node, "count", "Loop Count",
                                node.getIntParam("count", 3), 1, 100);
                        return;
                    }
                    playSound(player, "click");
                    saveFlow(player, session);
                    refreshGUI(player, session, node);
                } else {
                    requestTextInput(player, session, node, "condition", "While Condition",
                            node.getStringParam("condition", ""));
                }
            }
        }
    }

    private void handleRandomParam(Player player, GUISession session, RandomNode node, int slot, InventoryClickEvent event) {
        if (slot == 0) {
            // Path count
            int current = node.getIntParam("pathCount", 2);
            if (event.isLeftClick() && current < 4) {
                node.setParam("pathCount", current + 1);
            } else if (event.isRightClick() && current > 2) {
                node.setParam("pathCount", current - 1);
            }
            playSound(player, "click");
            saveFlow(player, session);
            refreshGUI(player, session, node);
        } else if (slot >= 1 && slot <= 4) {
            // Weight
            int weightIndex = slot;
            String key = "weight" + weightIndex;
            if (event.isShiftClick()) {
                requestNumberInput(player, session, node, key, "Weight " + weightIndex + " (%)",
                        node.getDoubleParam(key, 50), 0, 100);
            } else {
                double change = event.isLeftClick() ? 10 : -10;
                double newVal = Math.max(0, Math.min(100, node.getDoubleParam(key, 50) + change));
                node.setParam(key, newVal);
                playSound(player, "click");
                saveFlow(player, session);
                refreshGUI(player, session, node);
            }
        }
    }

    private void handleVariableParam(Player player, GUISession session, VariableNode node, int slot, InventoryClickEvent event) {
        switch (slot) {
            case 0 -> {
                // Operation - cycle
                String[] ops = {"SET", "ADD", "SUBTRACT", "MULTIPLY", "DIVIDE"};
                String current = node.getStringParam("operation", "SET");
                int idx = 0;
                for (int i = 0; i < ops.length; i++) {
                    if (ops[i].equals(current)) {
                        idx = i;
                        break;
                    }
                }
                node.setParam("operation", ops[(idx + 1) % ops.length]);
                playSound(player, "click");
                saveFlow(player, session);
                refreshGUI(player, session, node);
            }
            case 1 -> requestTextInput(player, session, node, "name", "Variable Name",
                    node.getStringParam("name", "myVar"));
            case 2 -> requestTextInput(player, session, node, "value", "Value (number or expression)",
                    String.valueOf(node.getParam("value", "0")));
        }
    }

    private void handleTargetParam(Player player, GUISession session, TargetNode node, int slot, InventoryClickEvent event) {
        switch (slot) {
            case 0 -> {
                // Target type - cycle
                String[] types = {"SELF", "VICTIM", "NEAREST_PLAYER", "NEAREST_HOSTILE", "NEAREST_ENTITY", "RANDOM_PLAYER"};
                String current = node.getStringParam("targetType", "VICTIM");
                int idx = 0;
                for (int i = 0; i < types.length; i++) {
                    if (types[i].equals(current)) {
                        idx = i;
                        break;
                    }
                }
                node.setParam("targetType", types[(idx + 1) % types.length]);
                playSound(player, "click");
                saveFlow(player, session);
                refreshGUI(player, session, node);
            }
            case 1 -> requestNumberInput(player, session, node, "range", "Range (blocks)",
                    node.getDoubleParam("range", 10), 1, 50);
        }
    }

    private void handleMathParam(Player player, GUISession session, MathNode node, int slot, InventoryClickEvent event) {
        switch (slot) {
            case 0 -> {
                // Operation - cycle
                String[] ops = {"ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "MIN", "MAX", "RANDOM", "POWER"};
                String current = node.getStringParam("operation", "ADD");
                int idx = 0;
                for (int i = 0; i < ops.length; i++) {
                    if (ops[i].equals(current)) {
                        idx = i;
                        break;
                    }
                }
                node.setParam("operation", ops[(idx + 1) % ops.length]);
                playSound(player, "click");
                saveFlow(player, session);
                refreshGUI(player, session, node);
            }
            case 1 -> requestTextInput(player, session, node, "left", "Left Value",
                    String.valueOf(node.getParam("left", "0")));
            case 2 -> requestTextInput(player, session, node, "right", "Right Value",
                    String.valueOf(node.getParam("right", "0")));
            case 3 -> requestTextInput(player, session, node, "result", "Result Variable",
                    node.getStringParam("result", "result"));
        }
    }

    /**
     * Handle START node parameters.
     * SIGNAL flows: signal_type, chance, cooldown
     * ABILITY flows: cooldown only
     */
    private void handleStartParam(Player player, GUISession session, StartNode node, int slot, InventoryClickEvent event) {
        // Determine flow type from START node's flow_type param (synced from FlowConfig)
        String flowType = node.getStringParam("flow_type", "SIGNAL");
        boolean isSignalFlow = "SIGNAL".equalsIgnoreCase(flowType);

        // Find which param index this slot corresponds to
        int slotIndex = -1;
        for (int i = 0; i < PARAM_SLOTS.length; i++) {
            if (PARAM_SLOTS[i] == slot) {
                slotIndex = i;
                break;
            }
        }
        if (slotIndex < 0) return;

        if (isSignalFlow) {
            // SIGNAL flow params: indicator (0), signal_type (1), chance (2), cooldown (3), priority (4)
            switch (slotIndex) {
                case 0 -> {
                    // Flow indicator - not editable
                }
                case 1 -> {
                    // Signal type - cycle through options (must match SignalType enum)
                    String[] types = {
                        "ATTACK",        // When attacking
                        "DEFENSE",       // When taking damage
                        "KILL_MOB",      // When killing a mob
                        "KILL_PLAYER",   // When killing a player
                        "SHIFT",         // When sneaking
                        "FALL_DAMAGE",   // When taking fall damage
                        "BOW_SHOOT",     // When shooting a bow
                        "BOW_HIT",       // When arrow hits target
                        "TRIDENT_THROW", // When throwing trident
                        "BLOCK_BREAK",   // When breaking blocks
                        "BLOCK_PLACE",   // When placing blocks
                        "INTERACT",      // When right-clicking
                        "ITEM_BREAK",    // When item breaks
                        "FISH",          // When catching fish
                        "TICK",          // Periodic effect (every tick)
                        "EFFECT_STATIC"  // Passive/always active
                    };
                    String current = node.getStringParam("signal_type", "ATTACK");
                    int idx = 0;
                    for (int i = 0; i < types.length; i++) {
                        if (types[i].equalsIgnoreCase(current)) {
                            idx = i;
                            break;
                        }
                    }
                    String next = types[(idx + 1) % types.length];
                    node.setParam("signal_type", next);
                    player.sendMessage(TextUtil.colorize("§aSignal Type set to: §f" + next));
                    playSound(player, "click");
                    saveFlow(player, session);
                    refreshGUI(player, session, node);
                }
                case 2 -> {
                    // Chance - NUMBER with tier scaling
                    if (event.isShiftClick()) {
                        toggleStartParamTierScaling(player, session, node, "chance", "Chance", 100.0);
                        return;
                    }
                    double currentValue = node.getDoubleParam("chance", 100.0);
                    requestNumberInput(player, session, node, "chance", "Chance (%)", currentValue, 0, 100);
                }
                case 3 -> {
                    // Cooldown - NUMBER with tier scaling
                    if (event.isShiftClick()) {
                        toggleStartParamTierScaling(player, session, node, "cooldown", "Cooldown", 0.0);
                        return;
                    }
                    double currentValue = node.getDoubleParam("cooldown", 0.0);
                    requestNumberInput(player, session, node, "cooldown", "Cooldown (seconds)", currentValue, 0, 3600);
                }
                case 4 -> {
                    // Priority - higher priority flows are checked first
                    int currentValue = node.getIntParam("priority", 1);
                    requestNumberInput(player, session, node, "priority", "Priority (higher = checked first)", currentValue, 1, 100);
                }
            }
        } else {
            // ABILITY flow params: indicator (0), cooldown (1), priority (2)
            // Abilities default to 30s cooldown (0 makes no sense for active abilities)
            switch (slotIndex) {
                case 0 -> {
                    // Flow indicator - not editable
                }
                case 1 -> {
                    // Cooldown - NUMBER with tier scaling
                    if (event.isShiftClick()) {
                        toggleStartParamTierScaling(player, session, node, "cooldown", "Cooldown", 30.0);
                        return;
                    }
                    double currentValue = node.getDoubleParam("cooldown", 30.0);
                    requestNumberInput(player, session, node, "cooldown", "Cooldown (seconds)", currentValue, 0, 3600);
                }
                case 2 -> {
                    // Priority - higher priority flows are checked first
                    int currentValue = node.getIntParam("priority", 1);
                    requestNumberInput(player, session, node, "priority", "Priority (higher = checked first)", currentValue, 1, 100);
                }
            }
        }
    }

    /**
     * Toggle tier scaling for START node parameters.
     * Uses the UNIFIED tier system - stores {placeholder} in node and reads values from sigil's TierScalingConfig.
     */
    private void toggleStartParamTierScaling(Player player, GUISession session, FlowNode node, String paramKey, String displayName, double defaultValue) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil == null) {
            player.sendMessage(TextUtil.colorize("§cError: No sigil in session"));
            return;
        }

        // Check if currently using tier placeholder (unified system)
        Object currentValue = node.getParam(paramKey);
        boolean isCurrentlyScaled = isTierPlaceholder(currentValue, paramKey);

        if (isCurrentlyScaled) {
            // Turn off tier scaling - set to fixed value (tier 1 value or default)
            com.zenax.armorsets.tier.TierScalingConfig tierConfig = sigil.getTierScalingConfig();
            double fixedValue = defaultValue;
            if (tierConfig != null && tierConfig.hasParam(paramKey)) {
                fixedValue = tierConfig.getParamValue(paramKey, 1); // Use tier 1 value
            }
            node.setParam(paramKey, fixedValue);
            session.remove("currentScaledParam");
            player.sendMessage(TextUtil.colorize("§eTier scaling disabled for §f" + displayName + " §7(set to " + String.format("%.1f", fixedValue) + ")"));
        } else {
            // Turn on tier scaling - set to {placeholder}
            com.zenax.armorsets.tier.TierScalingConfig tierConfig = sigil.getTierScalingConfig();

            // Check if sigil has this param defined in tier config
            if (tierConfig == null || !tierConfig.hasParam(paramKey)) {
                // Create default tier values in the sigil's tier config
                int maxTier = sigil.getMaxTier();
                double baseValue = node.getDoubleParam(paramKey, defaultValue);
                List<Double> values = new java.util.ArrayList<>();

                // For cooldown: decrease from base to base*0.5 (faster at higher tiers)
                // For chance: increase from base to 100 (more reliable at higher tiers)
                boolean isCooldown = "cooldown".equals(paramKey);
                for (int i = 0; i < maxTier; i++) {
                    double progress = maxTier > 1 ? (double) i / (maxTier - 1) : 0;
                    if (isCooldown) {
                        values.add(baseValue * (1.0 - progress * 0.5));
                    } else {
                        double increase = (100 - baseValue) * progress;
                        values.add(Math.min(100, baseValue + increase));
                    }
                }

                // Add to sigil's tier config
                if (tierConfig == null) {
                    tierConfig = new com.zenax.armorsets.tier.TierScalingConfig();
                    sigil.setTierScalingConfig(tierConfig);
                }
                tierConfig.setParamValues(paramKey, values);

                player.sendMessage(TextUtil.colorize("§aCreated tier values for §f" + paramKey + " §ain Tier Config"));
            }

            // Set node param to placeholder
            node.setParam(paramKey, "{" + paramKey + "}");
            session.put("currentScaledParam", paramKey);

            // Show the tier values from config
            String preview = buildTierPreviewFromConfig(sigil, paramKey);
            player.sendMessage(TextUtil.colorize("§aTier scaling enabled for §f" + displayName));
            player.sendMessage(TextUtil.colorize("§7Values: " + preview));
            player.sendMessage(TextUtil.colorize("§7Edit values in §eTier Config §7menu"));
        }

        playSound(player, "click");
        saveFlow(player, session);
        refreshGUI(player, session, node);
    }

    private String cycleTarget(String current) {
        return switch (current) {
            case "@Self" -> "@Victim";
            case "@Victim" -> "@Nearby:5";
            case "@Nearby:5" -> "@Nearby:10";
            default -> "@Self";
        };
    }

    // ============ Tier Scaling Methods ============

    /**
     * Toggle tier scaling for a numeric parameter.
     * Uses the unified tier scaling system - params are registered in TierScalingConfig
     * and referenced via {placeholder} in the node params.
     */
    private void toggleTierScaling(Player player, GUISession session, FlowNode node, ParamInfo paramInfo) {
        String paramKey = paramInfo.key;
        Sigil sigil = session.get("sigil", Sigil.class);

        if (sigil == null) {
            player.sendMessage(TextUtil.colorize("§cError: No sigil in session"));
            return;
        }

        // Check if currently using tier placeholder
        String currentValue = node.getStringParam(paramKey, "");
        boolean isCurrentlyScaled = currentValue.equals("{" + paramKey + "}");

        if (isCurrentlyScaled) {
            // Turn off tier scaling - set back to the tier 1 value from TierScalingConfig
            com.zenax.armorsets.tier.TierScalingConfig tierConfig = sigil.getTierScalingConfig();
            double baseValue = ((Number) paramInfo.defaultValue).doubleValue();
            if (tierConfig != null && tierConfig.hasParam(paramKey)) {
                baseValue = tierConfig.getParamValue(paramKey, 1);
            }
            node.setParam(paramKey, baseValue);
            session.remove("currentScaledParam");
            player.sendMessage(TextUtil.colorize("§eTier scaling disabled for §f" + paramInfo.displayName));
            player.sendMessage(TextUtil.colorize("§7Value set to: §f" + baseValue));
        } else {
            // Turn on tier scaling - set param to {placeholder} and register in TierScalingConfig
            int maxTier = sigil.getMaxTier();
            double baseValue = node.getDoubleParam(paramKey, ((Number) paramInfo.defaultValue).doubleValue());

            // Ensure sigil has TierScalingConfig
            com.zenax.armorsets.tier.TierScalingConfig tierConfig = sigil.getTierScalingConfig();
            if (tierConfig == null) {
                tierConfig = new com.zenax.armorsets.tier.TierScalingConfig();
                sigil.setTierScalingConfig(tierConfig);
            }

            // Register param with default scaling if not already present
            if (!tierConfig.hasParam(paramKey)) {
                tierConfig.addScaledParam(paramKey, baseValue, maxTier);
            }

            // Set node param to placeholder
            node.setParam(paramKey, "{" + paramKey + "}");
            session.put("currentScaledParam", paramKey);

            // Save the sigil to persist the TierScalingConfig changes
            plugin.getSigilManager().saveSigil(sigil);

            player.sendMessage(TextUtil.colorize("§aTier scaling enabled for §f" + paramInfo.displayName));
            player.sendMessage(TextUtil.colorize("§7Edit values in Tier Config"));
        }

        playSound(player, "click");
        saveFlow(player, session);  // Also save the flow with updated node params
        refreshGUI(player, session, node);
    }

    /**
     * Handle clicking on a tier value slot.
     * With the unified tier system, values are edited in Tier Config, not here.
     */
    private void handleTierSlotClick(Player player, GUISession session, FlowNode node, int tierIndex, InventoryClickEvent event) {
        // Tier values are now read-only in Node Config - edit in Tier Config
        player.sendMessage(TextUtil.colorize("§7Edit tier values in §eTier Config"));
        playSound(player, "click");
    }

    /**
     * Refresh tier value display from TierScalingConfig.
     */
    private void refreshTierDisplay(org.bukkit.inventory.Inventory inv, GUISession session, FlowNode node, String paramKey) {
        Sigil sigil = session.get("sigil", Sigil.class);
        if (sigil == null) return;

        com.zenax.armorsets.tier.TierScalingConfig tierConfig = sigil.getTierScalingConfig();
        if (tierConfig == null || !tierConfig.hasParam(paramKey)) return;

        int maxTier = sigil.getMaxTier();

        // Update tier slots with values from TierScalingConfig (read-only)
        for (int i = 0; i < Math.min(5, maxTier); i++) {
            double val = tierConfig.getParamValue(paramKey, i + 1);
            inv.setItem(TIER_ROW_1[i], buildTierPreviewItem(i + 1, val, maxTier));
        }
        for (int i = 5; i < Math.min(10, maxTier); i++) {
            double val = tierConfig.getParamValue(paramKey, i + 1);
            inv.setItem(TIER_ROW_2[i - 5], buildTierPreviewItem(i + 1, val, maxTier));
        }
    }

    /**
     * Build a tier value item for inline editing.
     */
    private static org.bukkit.inventory.ItemStack buildTierValueItem(int tier, double value, int maxTier) {
        Material mat;
        if (tier == 1) {
            mat = Material.WHITE_STAINED_GLASS_PANE;
        } else if (tier == maxTier) {
            mat = Material.LIME_STAINED_GLASS_PANE;
        } else {
            mat = Material.YELLOW_STAINED_GLASS_PANE;
        }

        String valueStr = value == (long) value ? String.valueOf((long) value) : String.format("%.1f", value);

        return ItemBuilder.createItem(mat,
            "§eTier " + tier + "§7: §f" + valueStr,
            "§fLeft-Click§7: +1",
            "§fRight-Click§7: -1",
            "§fShift-Click§7: Enter value"
        );
    }

    private void requestTextInput(Player player, GUISession session, FlowNode node, String key, String label, String current) {
        inputHelper.requestText(player, label, current,
                newValue -> {
                    if (node instanceof EffectNode en && "effectType".equals(key)) {
                        en.setEffectType(newValue);
                    } else if (node instanceof ConditionNode cn && "condition".equals(key)) {
                        cn.setCondition(newValue);
                    } else {
                        node.setParam(key, newValue);
                    }
                    player.sendMessage(TextUtil.colorize("§a" + label + " set to: §f" + newValue));
                    saveFlow(player, session);
                    refreshGUI(player, session, node);
                },
                () -> refreshGUI(player, session, node)
        );
    }

    private void requestNumberInput(Player player, GUISession session, FlowNode node, String key, String label, double current, double min, double max) {
        inputHelper.requestNumber(player, label, current, min, max,
                newValue -> {
                    node.setParam(key, newValue);
                    player.sendMessage(TextUtil.colorize("§a" + label + " set to: §f" + newValue));
                    saveFlow(player, session);
                    refreshGUI(player, session, node);
                },
                () -> refreshGUI(player, session, node)
        );
    }

    private void goBackToBuilder(Player player, GUISession session) {
        playSound(player, "click");

        // Save the flow before returning to ensure parameter changes are persisted
        saveFlow(player, session);

        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        FlowGraph graph = session.get("flow", FlowGraph.class);
        FlowBuilderHandler.openGUI(guiManager, player, sigil, signalKey, graph, session);
    }

    /**
     * Save the current flow to the sigil's YAML file.
     * This ensures parameter changes made in the node config editor are persisted.
     */
    private void saveFlow(Player player, GUISession session) {
        FlowGraph graph = session.get("flow", FlowGraph.class);
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);

        if (graph == null || sigil == null) {
            LogHelper.warning("[NodeConfig] Cannot save flow: graph=%s, sigil=%s", graph, sigil);
            return;
        }

        // Only save if the graph is valid
        List<String> errors = graph.validate();
        if (!errors.isEmpty()) {
            // Notify user about validation errors
            if (player != null) {
                player.sendMessage(TextUtil.colorize("§c[Flow] Cannot save - validation errors:"));
                for (String error : errors) {
                    player.sendMessage(TextUtil.colorize("§7  - §c" + error));
                }
            }
            LogHelper.warning("[NodeConfig] Flow validation failed: %s", errors);
            return;
        }

        // Get or create FlowConfig
        FlowConfig flowConfig = session.get("flowConfig", FlowConfig.class);
        if (flowConfig == null) {
            flowConfig = new FlowConfig();
            flowConfig.setType(FlowType.SIGNAL);
            flowConfig.setTrigger(signalKey != null ? signalKey : "ATTACK");
            session.put("flowConfig", flowConfig);
        }
        flowConfig.setGraph(graph);

        // CRITICAL: Sync StartNode chance/cooldown/priority to FlowConfig
        FlowNode startNodeRaw = graph.getStartNode();
        if (startNodeRaw instanceof StartNode startNode) {
            flowConfig.setCooldown(startNode.getDoubleParam("cooldown", 0.0));
            flowConfig.setChance(startNode.getDoubleParam("chance", 100.0));
            flowConfig.setPriority(startNode.getIntParam("priority", 1));

            // Also sync signal_type to trigger
            String signalType = startNode.getStringParam("signal_type", null);
            if (signalType != null && flowConfig.getType() == FlowType.SIGNAL) {
                flowConfig.setTrigger(signalType);
            }
        }

        // Update sigil's flows list and save to file
        sigil.removeFlowByTrigger(flowConfig.getTrigger());
        sigil.addFlow(flowConfig);
        plugin.getSigilManager().saveSigil(sigil);

        // Log successful save
        LogHelper.debug("[NodeConfig] Saved flow for sigil '%s' trigger '%s' - nodes: %d",
            sigil.getId(), flowConfig.getTrigger(),
            flowConfig.getGraph() != null ? flowConfig.getGraph().getNodes().size() : 0);
    }

    private void refreshGUI(Player player, GUISession session, FlowNode node) {
        openGUI(guiManager, player, session, node, 0);
    }

    public static void openGUI(GUIManager guiManager, Player player, GUISession flowSession, FlowNode node, int selectedParam) {
        // Preserve page number if coming from same session
        int page = 0;
        if (flowSession.getType() == GUIType.NODE_CONFIG) {
            page = flowSession.getInt("paramPage", 0);
        }

        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE,
                TextUtil.parseComponent("§7Node > §f" + node.getDisplayName()));

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Build params based on node type
        List<ParamConfig> params;
        int totalParams;

        // Get currentScaledParam from previous session if available
        String currentScaledParam = null;
        if (flowSession.getType() == GUIType.NODE_CONFIG) {
            currentScaledParam = flowSession.get("currentScaledParam", String.class);
        }

        // Get sigil for tier scaling preview
        Sigil sigil = flowSession.get("sigil", Sigil.class);

        if (node.getType() == NodeType.EFFECT) {
            // Use full effect params from EffectParamHandler
            EffectNode en = (EffectNode) node;
            EffectParamConfig config = EffectParamHandler.getParamConfig(en.getEffectType());

            // Build current param values map for showWhen evaluation
            Map<String, Object> currentParams = new HashMap<>();
            for (ParamInfo pi : config.params) {
                currentParams.put(pi.key, en.getParam(pi.key, pi.defaultValue));
            }

            // Filter to only visible params (respecting showWhen conditions)
            List<ParamInfo> visibleParams = new ArrayList<>();
            for (ParamInfo pi : config.params) {
                if (pi.isVisible(currentParams)) {
                    visibleParams.add(pi);
                }
            }
            totalParams = visibleParams.size();

            // Convert to ParamConfig for display (paginated)
            params = new ArrayList<>();
            int startIndex = page * PARAMS_PER_PAGE;
            int endIndex = Math.min(startIndex + PARAMS_PER_PAGE, visibleParams.size());

            for (int i = startIndex; i < endIndex; i++) {
                ParamInfo paramInfo = visibleParams.get(i);
                Object currentValue = en.getParam(paramInfo.key, paramInfo.defaultValue);

                // Check if using tier placeholder (e.g., "{damage}")
                boolean isTierScaled = isTierPlaceholder(currentValue, paramInfo.key);

                // Use XP bottle for tier-scaled params
                Material material = isTierScaled ? Material.EXPERIENCE_BOTTLE : getMaterialForParamType(paramInfo.type);
                String instructions = getInstructionsForParamType(paramInfo, isTierScaled);
                String displayValue = isTierScaled ? buildTierPreviewFromConfig(sigil, paramInfo.key) : String.valueOf(currentValue);

                params.add(new ParamConfig(paramInfo.displayName, displayValue, material, instructions, isTierScaled));
            }
        } else {
            // Use standard params for other node types
            params = getParamsForNode(node, sigil);
            totalParams = params.size();
        }

        // Display params in slots
        for (int i = 0; i < Math.min(params.size(), PARAM_SLOTS.length); i++) {
            ParamConfig param = params.get(i);
            String title = param.isTierScaled ? "§a" + param.label + " §7(Tier Scaled)" : "§e" + param.label;
            String valueLine = param.isTierScaled ? "§aTier Scaled§7: " + param.currentValue : "§7Current: §f" + param.currentValue;

            inv.setItem(PARAM_SLOTS[i], ItemBuilder.createItem(param.material,
                    title,
                    valueLine,
                    "",
                    param.instructions));
        }

        // Pagination controls
        int maxPages = (int) Math.ceil((double) totalParams / PARAMS_PER_PAGE);
        if (maxPages > 1) {
            // Previous page button
            if (page > 0) {
                inv.setItem(SLOT_PREV_PAGE, ItemBuilder.createItem(Material.ARROW,
                        "§e« Previous Page",
                        "§7Go to page " + page));
            }

            // Page info
            inv.setItem(SLOT_PAGE_INFO, ItemBuilder.createItem(Material.PAPER,
                    "§fPage " + (page + 1) + " of " + maxPages,
                    "§7" + totalParams + " parameters total"));

            // Next page button
            if (page < maxPages - 1) {
                inv.setItem(SLOT_NEXT_PAGE, ItemBuilder.createItem(Material.ARROW,
                        "§eNext Page »",
                        "§7Go to page " + (page + 2)));
            }
        }

        // Back button
        inv.setItem(SLOT_BACK, ItemBuilder.createBackButton("Flow Builder"));

        // Show tier preview from TierScalingConfig if a param is tier-scaled
        if (currentScaledParam != null) {
            com.zenax.armorsets.tier.TierScalingConfig tierConfig = sigil != null ? sigil.getTierScalingConfig() : null;
            if (tierConfig != null && tierConfig.hasParam(currentScaledParam)) {
                int maxTier = sigil.getMaxTier();
                // Show tier values as preview (read-only - edit in Tier Config)
                for (int i = 0; i < Math.min(5, maxTier); i++) {
                    double val = tierConfig.getParamValue(currentScaledParam, i + 1);
                    inv.setItem(TIER_ROW_1[i], buildTierPreviewItem(i + 1, val, maxTier));
                }
                for (int i = 5; i < Math.min(10, maxTier); i++) {
                    double val = tierConfig.getParamValue(currentScaledParam, i + 1);
                    inv.setItem(TIER_ROW_2[i - 5], buildTierPreviewItem(i + 1, val, maxTier));
                }
            }
        }

        // Create session
        GUISession session = new GUISession(GUIType.NODE_CONFIG);
        session.put("sigil", flowSession.get("sigil"));
        session.put("signalKey", flowSession.get("signalKey"));
        session.put("flow", flowSession.get("flow"));
        session.put("originalFlow", flowSession.get("originalFlow"));
        session.put("selectedNode", flowSession.get("selectedNode"));
        session.put("viewX", flowSession.get("viewX"));
        session.put("viewY", flowSession.get("viewY"));
        session.put("configNode", node);
        session.put("paramPage", page);
        if (currentScaledParam != null) {
            session.put("currentScaledParam", currentScaledParam);
        }

        guiManager.openGUI(player, inv, session);
    }

    private static Material getMaterialForParamType(ParamType type) {
        return switch (type) {
            case NUMBER -> Material.PAPER;
            case TEXT -> Material.WRITABLE_BOOK;
            case TARGET -> Material.RECOVERY_COMPASS;
            case PARTICLE_BROWSER -> Material.FIREWORK_ROCKET;
            case SOUND_BROWSER -> Material.BELL;
            case ATTRIBUTE_BROWSER -> Material.GOLDEN_APPLE;
            case CYCLE -> Material.COMPASS;
        };
    }

    private static String getInstructionsForParamType(ParamInfo paramInfo) {
        return getInstructionsForParamType(paramInfo, false);
    }

    private static String getInstructionsForParamType(ParamInfo paramInfo, boolean isTierScaled) {
        if (isTierScaled) {
            return "§eShift+Click §7to disable tier scaling";
        }
        return switch (paramInfo.type) {
            case NUMBER -> "§eClick to change §7| §eShift+Click §7for tier scaling";
            case TEXT -> "§eClick to edit";
            case TARGET -> "§eClick to cycle";
            case PARTICLE_BROWSER -> "§eClick to select particle";
            case SOUND_BROWSER -> "§eClick to select sound";
            case ATTRIBUTE_BROWSER -> "§eClick to select attribute";
            case CYCLE -> {
                if (paramInfo.cycleOptions != null && paramInfo.cycleOptions.length > 0) {
                    yield "§eClick to cycle: §7" + String.join(", ", paramInfo.cycleOptions);
                }
                yield "§eClick to cycle";
            }
        };
    }

    /**
     * Build a preview string showing tier value progression (DEPRECATED - uses node tierValues).
     */
    @Deprecated
    private static String buildTierPreview(FlowNode node, String paramKey) {
        List<Double> values = node.getTierValues().get(paramKey);
        if (values == null || values.isEmpty()) return "(no values)";

        StringBuilder preview = new StringBuilder();
        int showCount = Math.min(3, values.size());
        for (int i = 0; i < showCount; i++) {
            if (i > 0) preview.append(" → ");
            double val = values.get(i);
            preview.append(val == (long) val ? String.valueOf((long) val) : String.format("%.1f", val));
        }
        if (values.size() > 3) {
            preview.append(" → ...");
        }
        return preview.toString();
    }

    /**
     * Check if a param value is a tier placeholder (e.g., "{damage}").
     */
    private static boolean isTierPlaceholder(Object value, String paramKey) {
        if (value == null) return false;
        String strVal = value.toString();
        return strVal.equals("{" + paramKey + "}");
    }

    /**
     * Build a preview string from TierScalingConfig (unified tier system).
     */
    private static String buildTierPreviewFromConfig(Sigil sigil, String paramKey) {
        if (sigil == null) return "(no sigil)";

        com.zenax.armorsets.tier.TierScalingConfig tierConfig = sigil.getTierScalingConfig();
        if (tierConfig == null || !tierConfig.hasParam(paramKey)) {
            return "(not in Tier Config)";
        }

        int maxTier = sigil.getMaxTier();
        StringBuilder preview = new StringBuilder();
        int showCount = Math.min(3, maxTier);
        for (int i = 0; i < showCount; i++) {
            if (i > 0) preview.append(" → ");
            double val = tierConfig.getParamValue(paramKey, i + 1);
            preview.append(val == (long) val ? String.valueOf((long) val) : String.format("%.1f", val));
        }
        if (maxTier > 3) {
            preview.append(" → ...");
        }
        return preview.toString();
    }

    /**
     * Build a tier preview item (read-only - edit in Tier Config).
     */
    private static org.bukkit.inventory.ItemStack buildTierPreviewItem(int tier, double value, int maxTier) {
        Material mat;
        if (tier == 1) {
            mat = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        } else if (tier == maxTier) {
            mat = Material.CYAN_STAINED_GLASS_PANE;
        } else {
            mat = Material.BLUE_STAINED_GLASS_PANE;
        }

        String valueStr = value == (long) value ? String.valueOf((long) value) : String.format("%.1f", value);

        return ItemBuilder.createItem(mat,
            "§bTier " + tier + "§7: §f" + valueStr,
            "§8Edit values in Tier Config"
        );
    }

    private static List<ParamConfig> getParamsForNode(FlowNode node) {
        return getParamsForNode(node, null);
    }

    private static List<ParamConfig> getParamsForNode(FlowNode node, Sigil sigil) {
        List<ParamConfig> params = new ArrayList<>();

        switch (node.getType()) {
            case EFFECT -> {
                // Effect params are handled separately using EffectParamHandler.getParamConfig()
                // This case should not be reached, but add fallback just in case
                EffectNode en = (EffectNode) node;
                EffectParamConfig config = EffectParamHandler.getParamConfig(en.getEffectType());
                for (ParamInfo paramInfo : config.params) {
                    Object currentValue = en.getParam(paramInfo.key, paramInfo.defaultValue);
                    Material material = getMaterialForParamType(paramInfo.type);
                    String instructions = getInstructionsForParamType(paramInfo);
                    params.add(new ParamConfig(paramInfo.displayName, String.valueOf(currentValue), material, instructions));
                }
            }
            case CONDITION -> {
                ConditionNode cn = (ConditionNode) node;
                params.add(new ParamConfig("Condition", cn.getCondition(), Material.COMPARATOR, "§eClick to edit"));
            }
            case DELAY -> {
                params.add(new ParamConfig("Duration", node.getDoubleParam("duration", 1.0) + "s", Material.CLOCK, "§eClick to change"));
            }
            case LOOP -> {
                params.add(new ParamConfig("Loop Type", node.getStringParam("type", "COUNT"), Material.REPEATER, "§eClick to cycle"));
                if ("COUNT".equals(node.getStringParam("type", "COUNT"))) {
                    params.add(new ParamConfig("Count", String.valueOf(node.getIntParam("count", 3)), Material.PAPER, "§eL/R click to adjust, Shift for input"));
                } else {
                    params.add(new ParamConfig("Condition", node.getStringParam("condition", ""), Material.COMPARATOR, "§eClick to edit"));
                }
            }
            case RANDOM -> {
                int pathCount = node.getIntParam("pathCount", 2);
                params.add(new ParamConfig("Path Count", String.valueOf(pathCount), Material.SLIME_BALL, "§eL/R click (2-4)"));
                for (int i = 1; i <= pathCount; i++) {
                    params.add(new ParamConfig("Weight " + i, node.getDoubleParam("weight" + i, 50) + "%", Material.PAPER, "§eL/R ±10%, Shift for input"));
                }
            }
            case VARIABLE -> {
                params.add(new ParamConfig("Operation", node.getStringParam("operation", "SET"), Material.ANVIL, "§eClick to cycle"));
                params.add(new ParamConfig("Variable Name", "$" + node.getStringParam("name", "var"), Material.CHEST, "§eClick to edit"));
                params.add(new ParamConfig("Value", String.valueOf(node.getParam("value", 0)), Material.PAPER, "§eClick to edit"));
            }
            case TARGET -> {
                params.add(new ParamConfig("Target Type", node.getStringParam("targetType", "VICTIM"), Material.ENDER_EYE, "§eClick to cycle"));
                params.add(new ParamConfig("Range", node.getDoubleParam("range", 10) + " blocks", Material.ARROW, "§eClick to change"));
            }
            case MATH -> {
                params.add(new ParamConfig("Operation", node.getStringParam("operation", "ADD"), Material.GOLD_INGOT, "§eClick to cycle"));
                params.add(new ParamConfig("Left Value", String.valueOf(node.getParam("left", 0)), Material.PAPER, "§eClick to edit"));
                params.add(new ParamConfig("Right Value", String.valueOf(node.getParam("right", 0)), Material.PAPER, "§eClick to edit"));
                params.add(new ParamConfig("Result Variable", "$" + node.getStringParam("result", "result"), Material.CHEST, "§eClick to edit"));
            }
            case START -> {
                // START node params depend on flow type (stored as flow_type param)
                String flowType = node.getStringParam("flow_type", "SIGNAL");
                boolean isSignalFlow = "SIGNAL".equalsIgnoreCase(flowType);

                // Check for tier placeholder (unified tier system)
                boolean isTierScaledChance = isTierPlaceholder(node.getParam("chance"), "chance");
                boolean isTierScaledCooldown = isTierPlaceholder(node.getParam("cooldown"), "cooldown");

                if (isSignalFlow) {
                    double cooldown = node.getDoubleParam("cooldown", 0.0);
                    // Flow type indicator
                    String signalType = node.getStringParam("signal_type", "ATTACK");
                    String signalDesc = getSignalDescription(signalType);
                    params.add(new ParamConfig("Signal Flow", signalDesc, Material.LIGHTNING_ROD, "§7Activates when event occurs"));

                    // Signal Type
                    params.add(new ParamConfig("Signal Type", signalType, Material.BELL, "§eClick to cycle"));

                    // Chance - THE authoritative source for chance config
                    double chance = node.getDoubleParam("chance", 100.0);
                    String chanceDisplay = isTierScaledChance ? buildTierPreviewFromConfig(sigil, "chance") : String.format("%.0f%%", chance);
                    Material chanceMat = isTierScaledChance ? Material.EXPERIENCE_BOTTLE : Material.PAPER;
                    String chanceInstr = isTierScaledChance
                            ? "§aScales with tier §7| §eShift+Click §7to disable"
                            : "§eClick to set §7| §eShift+Click §7for tier scaling";
                    params.add(new ParamConfig("Activation Chance", chanceDisplay, chanceMat, chanceInstr, isTierScaledChance));

                    // Cooldown - THE authoritative source for cooldown config
                    String cooldownDisplay = isTierScaledCooldown ? buildTierPreviewFromConfig(sigil, "cooldown") : String.format("%.1fs", cooldown);
                    Material cooldownMat = isTierScaledCooldown ? Material.EXPERIENCE_BOTTLE : Material.CLOCK;
                    String cooldownInstr = isTierScaledCooldown
                            ? "§aScales with tier §7| §eShift+Click §7to disable"
                            : "§eClick to set §7| §eShift+Click §7for tier scaling";
                    params.add(new ParamConfig("Cooldown", cooldownDisplay, cooldownMat, cooldownInstr, isTierScaledCooldown));

                    // Priority - higher priority flows are checked first when multiple flows share same trigger
                    int priority = node.getIntParam("priority", 1);
                    params.add(new ParamConfig("Priority", String.valueOf(priority), Material.ANVIL, "§eClick to set §7| §7Higher = checked first"));
                } else {
                    // Ability flows default to 30s cooldown (0 makes no sense for active abilities)
                    double cooldown = node.getDoubleParam("cooldown", 30.0);

                    // Flow type indicator
                    params.add(new ParamConfig("Ability Flow", "Hotkey activated", Material.TRIPWIRE_HOOK, "§7Activates via keybind"));

                    // Cooldown only - THE authoritative source for cooldown config
                    String cooldownDisplay = isTierScaledCooldown ? buildTierPreviewFromConfig(sigil, "cooldown") : String.format("%.1fs", cooldown);
                    Material cooldownMat = isTierScaledCooldown ? Material.EXPERIENCE_BOTTLE : Material.CLOCK;
                    String cooldownInstr = isTierScaledCooldown
                            ? "§aScales with tier §7| §eShift+Click §7to disable"
                            : "§eClick to set §7| §eShift+Click §7for tier scaling";
                    params.add(new ParamConfig("Cooldown", cooldownDisplay, cooldownMat, cooldownInstr, isTierScaledCooldown));

                    // Priority - higher priority flows are checked first when multiple flows share same trigger
                    int priority = node.getIntParam("priority", 1);
                    params.add(new ParamConfig("Priority", String.valueOf(priority), Material.ANVIL, "§eClick to set §7| §7Higher = checked first"));
                }
            }
            default -> {}
        }

        return params;
    }

    /**
     * Get a user-friendly description for a signal type.
     */
    private static String getSignalDescription(String signalType) {
        if (signalType == null) return "Unknown";
        return switch (signalType.toUpperCase()) {
            case "ATTACK" -> "When attacking";
            case "DEFENSE" -> "When taking damage";
            case "KILL_MOB" -> "When killing a mob";
            case "KILL_PLAYER" -> "When killing a player";
            case "SHIFT" -> "When sneaking";
            case "FALL_DAMAGE" -> "When taking fall damage";
            case "BOW_SHOOT" -> "When shooting a bow";
            case "BOW_HIT" -> "When arrow hits target";
            case "TRIDENT_THROW" -> "When throwing trident";
            case "BLOCK_BREAK" -> "When breaking blocks";
            case "BLOCK_PLACE" -> "When placing blocks";
            case "INTERACT" -> "When right-clicking";
            case "ITEM_BREAK" -> "When item breaks";
            case "FISH" -> "When catching fish";
            case "TICK" -> "Periodic effect";
            case "EFFECT_STATIC" -> "Always active";
            default -> "Triggers on " + signalType;
        };
    }

    private static class ParamConfig {
        final String label;
        final String currentValue;
        final Material material;
        final String instructions;
        final boolean isTierScaled;

        ParamConfig(String label, String currentValue, Material material, String instructions) {
            this(label, currentValue, material, instructions, false);
        }

        ParamConfig(String label, String currentValue, Material material, String instructions, boolean isTierScaled) {
            this.label = label;
            this.currentValue = currentValue != null ? currentValue : "(not set)";
            this.material = material;
            this.instructions = instructions;
            this.isTierScaled = isTierScaled;
        }
    }
}
