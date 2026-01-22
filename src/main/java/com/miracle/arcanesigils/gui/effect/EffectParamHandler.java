package com.miracle.arcanesigils.gui.effect;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.core.SigilManager;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.gui.input.SignInputHelper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for the EFFECT_PARAM GUI.
 * Allows configuring parameters for a specific effect.
 * Supports inline tier scaling for numeric parameters.
 *
 * Layout (6 rows = 54 slots):
 * Row 0: [_][_][_][_][I][_][_][_][_]  I=Info
 * Row 1: [_][P][P][P][P][P][P][P][P]  P=Parameters Row 1 (8 slots)
 * Row 2: [_][P][P][P][P][P][P][P][P]  P=Parameters Row 2 (8 slots) - 16 total
 * Row 3: [_][_][_][_][_][_][_][_][_]  Spacer
 * Row 4: [_][T][T][T][T][T][_][_][_]  T=Tier values (when scaled)
 * Row 5: [X][T][T][T][T][T][_][_][S]  X=Back, T=Tiers 6-10, S=Save
 *
 * Shift+Click on NUMBER param = toggle tier scaling
 * When tier-scaled, tier values shown below param
 */
public class EffectParamHandler extends AbstractHandler {

    // Slot positions - expanded to support more params (2 rows)
    private static final int[] PARAM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16, 17,  // Row 1: 8 slots
        19, 20, 21, 22, 23, 24, 25, 26   // Row 2: 8 more slots (16 total)
    };
    private static final int[] TIER_ROW_1 = {37, 38, 39, 40, 41};  // Tiers 1-5 (moved down)
    private static final int[] TIER_ROW_2 = {46, 47, 48, 49, 50};  // Tiers 6-10
    private static final int SLOT_INFO = 4;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_SAVE = 53;
    private static final int INVENTORY_SIZE = 54;  // 6 rows

    private final SigilManager sigilManager;
    private final SignInputHelper inputHelper;

    public EffectParamHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
        this.sigilManager = plugin.getSigilManager();
        this.inputHelper = guiManager.getInputHelper();
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        String effectType = session.get("effectType", String.class);

        if (sigil == null || signalKey == null || effectType == null) {
            player.sendMessage(TextUtil.colorize("§cError: Invalid session data!"));
            player.closeInventory();
            return;
        }

        switch (slot) {
            case SLOT_BACK -> handleBack(player, session);
            case SLOT_SAVE -> handleSave(player, session, sigil, signalKey);
            default -> {
                // Check if clicking on tier slots
                int tierIndex = getTierSlotIndex(slot);
                if (tierIndex >= 0) {
                    handleTierSlotClick(player, session, tierIndex, event);
                } else {
                    handleParamSlot(player, session, slot, event);
                }
            }
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

    /**
     * Handle clicking on a tier value slot.
     */
    private void handleTierSlotClick(Player player, GUISession session, int tierIndex, InventoryClickEvent event) {
        String scaledParam = session.get("currentScaledParam", String.class);
        if (scaledParam == null) return;

        @SuppressWarnings("unchecked")
        Map<String, List<Double>> tierValues = (Map<String, List<Double>>) session.get("tierValues");
        if (tierValues == null) return;

        List<Double> values = tierValues.get(scaledParam);
        if (values == null || tierIndex >= values.size()) return;

        Sigil sigil = session.get("sigil", Sigil.class);
        int maxTier = sigil != null ? sigil.getMaxTier() : 5;
        if (tierIndex >= maxTier) return;

        double increment = session.getDouble("tierIncrement", 1.0);
        double currentValue = values.get(tierIndex);

        if (event.isShiftClick()) {
            // Direct input
            String signalKey = session.get("signalKey", String.class);
            String effectType = session.get("effectType", String.class);
            Integer effectIndexObj = session.get("effectIndex", Integer.class);
            int effectIndex = effectIndexObj != null ? effectIndexObj : -1;
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) session.get("params");

            final int finalTierIndex = tierIndex;
            inputHelper.requestNumber(player, "Tier " + (tierIndex + 1) + " Value", currentValue, 0, 10000,
                newValue -> {
                    values.set(finalTierIndex, newValue);
                    openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, params);
                },
                () -> openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, params)
            );
        } else if (event.isLeftClick()) {
            values.set(tierIndex, currentValue + increment);
            refreshTierDisplay(player.getOpenInventory().getTopInventory(), session, scaledParam);
            playSound(player, "click");
        } else if (event.isRightClick()) {
            values.set(tierIndex, Math.max(0, currentValue - increment));
            refreshTierDisplay(player.getOpenInventory().getTopInventory(), session, scaledParam);
            playSound(player, "click");
        }
    }

    /**
     * Refresh just the tier value display slots.
     */
    private void refreshTierDisplay(Inventory inv, GUISession session, String paramKey) {
        @SuppressWarnings("unchecked")
        Map<String, List<Double>> tierValues = (Map<String, List<Double>>) session.get("tierValues");
        if (tierValues == null) return;

        List<Double> values = tierValues.get(paramKey);
        if (values == null) return;

        Sigil sigil = session.get("sigil", Sigil.class);
        int maxTier = sigil != null ? sigil.getMaxTier() : 5;
        double increment = session.getDouble("tierIncrement", 1.0);

        // Update tier slots
        for (int i = 0; i < Math.min(5, maxTier); i++) {
            double val = i < values.size() ? values.get(i) : 0;
            inv.setItem(TIER_ROW_1[i], buildTierValueItem(i + 1, val, increment, maxTier));
        }
        for (int i = 5; i < Math.min(10, maxTier); i++) {
            double val = i < values.size() ? values.get(i) : 0;
            inv.setItem(TIER_ROW_2[i - 5], buildTierValueItem(i + 1, val, increment, maxTier));
        }
    }

    /**
     * Handle back button - return to previous menu based on context.
     * If editing existing effect → go back to Effect Config (effects list)
     * If adding new effect → go back to Effect Selector (effect type picker)
     */
    private void handleBack(Player player, GUISession session) {
        playSound(player, "click");
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        Integer effectIndex = session.get("effectIndex", Integer.class);

        if (sigil == null || signalKey == null) {
            player.closeInventory();
            return;
        }

        boolean isEditing = effectIndex != null && effectIndex >= 0;
        boolean isAbilityMode = "ability".equals(signalKey);

        // Go back to Flow Builder (replaces old effect list)
        if (isAbilityMode) {
            com.miracle.arcanesigils.gui.sigil.SigilEditorHandler.openGUI(guiManager, player, sigil);
        } else {
            com.miracle.arcanesigils.gui.flow.FlowBuilderHandler.openGUI(guiManager, player, sigil, signalKey);
        }
    }

    /**
     * Handle save button.
     */
    private void handleSave(Player player, GUISession session, Sigil sigil, String signalKey) {
        String effectType = session.get("effectType", String.class);
        Integer effectIndex = session.get("effectIndex", Integer.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) session.get("params");
        if (params == null) {
            params = new HashMap<>();
        }

        // Get tier scaling data
        @SuppressWarnings("unchecked")
        Map<String, Boolean> scaledParams = (Map<String, Boolean>) session.get("scaledParams");
        @SuppressWarnings("unchecked")
        Map<String, List<Double>> tierValues = (Map<String, List<Double>>) session.get("tierValues");

        // Create tier parameters for scaled params
        if (scaledParams != null && tierValues != null && !scaledParams.isEmpty()) {
            createTierParams(sigil, scaledParams, tierValues);
        }

        // Build effect string from parameters (uses {paramName} for tier-scaled)
        String effectString = buildEffectString(effectType, params, scaledParams);

        // Effects are now handled by FlowBuilder nodes via FlowConfig
        // Redirect users to Flow Builder for effect configuration
        boolean isAbilityMode = "ability".equals(signalKey);

        player.sendMessage(TextUtil.colorize("§cEffect editing moved to Flow Builder!"));
        player.sendMessage(TextUtil.colorize("§7Use the Flow Builder to add effects to your sigil."));

        playSound(player, "click");

        // Return to appropriate GUI
        if (isAbilityMode) {
            com.miracle.arcanesigils.gui.sigil.SigilEditorHandler.openGUI(guiManager, player, sigil);
        } else {
            com.miracle.arcanesigils.gui.flow.FlowBuilderHandler.openGUI(guiManager, player, sigil, signalKey);
        }
    }

    /**
     * Create tier parameters in the sigil's tier config.
     */
    private void createTierParams(Sigil sigil, Map<String, Boolean> scaledParams, Map<String, List<Double>> tierValues) {
        TierScalingConfig tierConfig = sigil.getTierScalingConfig();
        if (tierConfig == null) {
            tierConfig = new TierScalingConfig();
            sigil.setTierScalingConfig(tierConfig);
        }

        TierParameterConfig paramConfig = tierConfig.getParams();

        for (Map.Entry<String, Boolean> entry : scaledParams.entrySet()) {
            if (!entry.getValue()) continue;

            String paramName = entry.getKey();
            List<Double> values = tierValues.get(paramName);

            if (values != null && !values.isEmpty()) {
                paramConfig.setValues(paramName, values);
            }
        }
    }

    /**
     * Handle parameter slot clicks.
     */
    private void handleParamSlot(Player player, GUISession session, int slot, InventoryClickEvent event) {
        int paramIndex = getParamIndex(slot);
        if (paramIndex < 0) {
            playSound(player, "click");
            return;
        }

        String effectType = session.get("effectType", String.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) session.get("params");
        if (params == null) {
            params = new HashMap<>();
            session.put("params", params);
        }

        // Get parameter configuration and filter to visible params
        EffectParamConfig config = getParamConfig(effectType);
        List<ParamInfo> visibleParams = new ArrayList<>();
        for (ParamInfo p : config.params) {
            if (p.isVisible(params)) {
                visibleParams.add(p);
            }
        }

        if (paramIndex >= visibleParams.size()) {
            playSound(player, "click");
            return;
        }

        ParamInfo paramInfo = visibleParams.get(paramIndex);
        playSound(player, "click");

        // Shift+Click on NUMBER params toggles tier scaling
        if (event.isShiftClick() && paramInfo.type == ParamType.NUMBER) {
            toggleTierScaling(player, session, paramInfo, params);
            return;
        }

        // Handle parameter based on type
        handleParamInput(player, session, paramInfo, params);
    }

    /**
     * Toggle tier scaling for a numeric parameter.
     */
    private void toggleTierScaling(Player player, GUISession session, ParamInfo paramInfo, Map<String, Object> params) {
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        String effectType = session.get("effectType", String.class);
        Integer effectIndexObj = session.get("effectIndex", Integer.class);
        int effectIndex = effectIndexObj != null ? effectIndexObj : -1;

        // Get or create tier scaling tracking
        @SuppressWarnings("unchecked")
        Map<String, Boolean> scaledParams = (Map<String, Boolean>) session.get("scaledParams");
        if (scaledParams == null) {
            scaledParams = new HashMap<>();
            session.put("scaledParams", scaledParams);
        }

        @SuppressWarnings("unchecked")
        Map<String, List<Double>> tierValues = (Map<String, List<Double>>) session.get("tierValues");
        if (tierValues == null) {
            tierValues = new HashMap<>();
            session.put("tierValues", tierValues);
        }

        boolean isCurrentlyScaled = scaledParams.getOrDefault(paramInfo.key, false);

        if (isCurrentlyScaled) {
            // Turn off tier scaling
            scaledParams.remove(paramInfo.key);
            tierValues.remove(paramInfo.key);
            session.remove("currentScaledParam");
            player.sendMessage(TextUtil.colorize("§eTier scaling disabled for §f" + paramInfo.displayName));
        } else {
            // Turn on tier scaling
            scaledParams.put(paramInfo.key, true);
            session.put("currentScaledParam", paramInfo.key);

            // Initialize tier values
            int maxTier = sigil != null ? sigil.getMaxTier() : 5;
            List<Double> values = new ArrayList<>();
            double baseValue = ((Number) paramInfo.defaultValue).doubleValue();
            if (params.containsKey(paramInfo.key)) {
                Object val = params.get(paramInfo.key);
                if (val instanceof Number num) {
                    baseValue = num.doubleValue();
                }
            }

            // Create linear progression from base to base*2
            for (int i = 0; i < maxTier; i++) {
                double progress = maxTier > 1 ? (double) i / (maxTier - 1) : 0;
                values.add(baseValue + (baseValue * progress));
            }
            tierValues.put(paramInfo.key, values);

            player.sendMessage(TextUtil.colorize("§aTier scaling enabled for §f" + paramInfo.displayName));
            player.sendMessage(TextUtil.colorize("§7Click tier slots below to adjust values"));
        }

        // Refresh GUI
        openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, params);
    }

    /**
     * Handle parameter input based on type.
     */
    private void handleParamInput(Player player, GUISession session, ParamInfo paramInfo,
                                  Map<String, Object> params) {
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        String effectType = session.get("effectType", String.class);

        switch (paramInfo.type) {
            case NUMBER -> {
                double currentValue;
                if (params.containsKey(paramInfo.key)) {
                    Object val = params.get(paramInfo.key);
                    if (val instanceof Number num) {
                        currentValue = num.doubleValue();
                    } else if (val instanceof String str) {
                        try {
                            currentValue = Double.parseDouble(str);
                        } catch (NumberFormatException e) {
                            currentValue = ((Number) paramInfo.defaultValue).doubleValue();
                        }
                    } else {
                        currentValue = ((Number) paramInfo.defaultValue).doubleValue();
                    }
                } else {
                    currentValue = ((Number) paramInfo.defaultValue).doubleValue();
                }

                Integer effectIndexObj = session.get("effectIndex", Integer.class);
                int effectIndex = effectIndexObj != null ? effectIndexObj : -1;
                final Map<String, Object> finalParams = params;
                inputHelper.requestNumber(player, paramInfo.displayName, currentValue,
                    paramInfo.min, paramInfo.max,
                    newValue -> {
                        finalParams.put(paramInfo.key, newValue);
                        player.sendMessage(TextUtil.colorize("§a" + paramInfo.displayName + " set to: §f" + newValue));
                        openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, finalParams);
                    },
                    () -> openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, finalParams)
                );
            }
            case TEXT -> {
                String currentValue = params.containsKey(paramInfo.key)
                    ? params.get(paramInfo.key).toString()
                    : String.valueOf(paramInfo.defaultValue);

                Integer effectIndexObj = session.get("effectIndex", Integer.class);
                int effectIndex = effectIndexObj != null ? effectIndexObj : -1;
                final Map<String, Object> finalParams = params;
                inputHelper.requestText(player, paramInfo.displayName, currentValue,
                    newValue -> {
                        finalParams.put(paramInfo.key, newValue);
                        player.sendMessage(TextUtil.colorize("§a" + paramInfo.displayName + " set to: §f" + newValue));
                        openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, finalParams);
                    },
                    () -> openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, finalParams)
                );
            }
            case TARGET -> {
                // Cycle through target options
                String currentTarget = params.containsKey(paramInfo.key)
                    ? params.get(paramInfo.key).toString()
                    : "@Self";

                String newTarget = switch (currentTarget) {
                    case "@Self" -> "@Victim";
                    case "@Victim" -> "@Nearby";
                    default -> "@Self";
                };

                params.put(paramInfo.key, newTarget);
                session.put("params", params);
                player.sendMessage(TextUtil.colorize("§aTarget set to: §f" + newTarget));
                Integer effectIndexObj = session.get("effectIndex", Integer.class);
                int effectIndex = effectIndexObj != null ? effectIndexObj : -1;
                openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, params);
            }
            case PARTICLE_BROWSER -> {
                // Open particle browser GUI
                session.put("params", params);
                ParticleSelectorHandler.openGUI(guiManager, player, session);
            }
            case SOUND_BROWSER -> {
                // Open sound browser GUI
                session.put("params", params);
                SoundSelectorHandler.openGUI(guiManager, player, session);
            }
            case ATTRIBUTE_BROWSER -> {
                // Open attribute browser GUI
                session.put("params", params);
                Integer effectIndexObj = session.get("effectIndex", Integer.class);
                int effectIndex = effectIndexObj != null ? effectIndexObj : -1;
                AttributeSelectorHandler.openGUI(guiManager, player,
                    selectedAttribute -> {
                        // Callback when attribute is selected
                        params.put(paramInfo.key, selectedAttribute);
                        session.put("params", params);
                        openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, params);
                    },
                    cancelled -> {
                        // Cancelled - return to effect config
                        openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, params);
                    }
                );
            }
            case CYCLE -> {
                // Cycle through predefined options
                String currentValue = params.containsKey(paramInfo.key)
                    ? params.get(paramInfo.key).toString()
                    : String.valueOf(paramInfo.defaultValue);

                String[] options = paramInfo.cycleOptions;
                if (options == null || options.length == 0) return;

                // Find current index and cycle to next
                int currentIndex = 0;
                for (int i = 0; i < options.length; i++) {
                    if (options[i].equalsIgnoreCase(currentValue)) {
                        currentIndex = i;
                        break;
                    }
                }
                int nextIndex = (currentIndex + 1) % options.length;
                String newValue = options[nextIndex];

                params.put(paramInfo.key, newValue);
                session.put("params", params);
                player.sendMessage(TextUtil.colorize("§a" + paramInfo.displayName + " set to: §f" + newValue));
                Integer effectIndexObj = session.get("effectIndex", Integer.class);
                int effectIndex = effectIndexObj != null ? effectIndexObj : -1;
                // Use openGUIWithParams to preserve current params
                openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, params);
            }
        }
    }

    /**
     * Get param index from slot position.
     */
    private int getParamIndex(int slot) {
        for (int i = 0; i < PARAM_SLOTS.length; i++) {
            if (PARAM_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Build effect string from parameters.
     */
    private String buildEffectString(String effectType, Map<String, Object> params, Map<String, Boolean> scaledParams) {
        StringBuilder sb = new StringBuilder(effectType.toUpperCase());

        // Special handling for SPAWN_ENTITY with key=value format
        if ("SPAWN_ENTITY".equalsIgnoreCase(effectType)) {
            return buildSpawnEntityString(params, scaledParams);
        }

        // Special handling for PARTICLE with key=value format for shapes
        if ("PARTICLE".equalsIgnoreCase(effectType)) {
            return buildParticleString(params, scaledParams);
        }

        // Special handling for SPAWN_DISPLAY with key=value format
        if ("SPAWN_DISPLAY".equalsIgnoreCase(effectType)) {
            return buildSpawnDisplayString(params, scaledParams);
        }

        EffectParamConfig config = getParamConfig(effectType);

        for (ParamInfo paramInfo : config.params) {
            // Skip target-style params (@ params) - they get appended at the end
            if (paramInfo.key.equals("teleportee") || paramInfo.key.equals("teleportTarget") || paramInfo.key.equals("target")) {
                continue;
            }

            // Check if this param is tier-scaled - use {paramName} placeholder
            boolean isTierScaled = scaledParams != null && scaledParams.getOrDefault(paramInfo.key, false);

            if (isTierScaled) {
                // Use placeholder for tier-scaled params
                sb.append(":").append("{").append(paramInfo.key).append("}");
            } else if (params.containsKey(paramInfo.key)) {
                Object value = params.get(paramInfo.key);
                sb.append(":").append(value);
            }
        }

        // Add teleportee and teleportTarget for TELEPORT effect
        if ("TELEPORT".equalsIgnoreCase(effectType)) {
            String teleportee = params.containsKey("teleportee") ? params.get("teleportee").toString() : "@Self";
            String teleportTarget = params.containsKey("teleportTarget") ? params.get("teleportTarget").toString() : "@Victim";
            sb.append(" ").append(teleportee).append(" ").append(teleportTarget);
        }
        // Add target if specified (for other effects)
        else if (params.containsKey("target")) {
            sb.append(" ").append(params.get("target"));
        }

        return sb.toString();
    }

    /**
     * Build SPAWN_ENTITY effect string with key=value format.
     */
    private String buildSpawnEntityString(Map<String, Object> params, Map<String, Boolean> scaledParams) {
        StringBuilder sb = new StringBuilder("SPAWN_ENTITY");

        // Core params in order: TYPE:COUNT:DURATION
        String type = params.getOrDefault("type", "HUSK").toString();
        String count = formatNumber(params.getOrDefault("count", 1));
        String duration = formatNumber(params.getOrDefault("duration", 10));

        sb.append(":").append(type);
        sb.append(":").append(count);
        sb.append(":").append(duration);

        // Stats - always serialize so GUI changes persist
        if (params.containsKey("hp")) {
            sb.append(":hp=").append(formatNumber(params.get("hp")));
        }
        if (params.containsKey("damage")) {
            sb.append(":damage=").append(formatNumber(params.get("damage")));
        }
        if (params.containsKey("speed")) {
            sb.append(":speed=").append(formatNumber(params.get("speed")));
        }
        if (params.containsKey("knockback_resist")) {
            sb.append(":knockback_resist=").append(formatNumber(params.get("knockback_resist")));
        }

        // Target mode
        String targetMode = params.getOrDefault("target", "VICTIM").toString();
        if (!targetMode.equals("VICTIM")) {
            sb.append(":target=").append(targetMode);
        }

        // AI/Behavior controls (CRITICAL for dummies)
        String noAi = params.getOrDefault("no_ai", "false").toString();
        if ("true".equalsIgnoreCase(noAi)) {
            sb.append(":no_ai=true");
        }
        String silent = params.getOrDefault("silent", "false").toString();
        if ("true".equalsIgnoreCase(silent)) {
            sb.append(":silent=true");
        }

        // Appearance
        String name = params.getOrDefault("name", "").toString().trim();
        if (!name.isEmpty()) {
            sb.append(":name=").append(name);
        }
        String glowing = params.getOrDefault("glowing", "false").toString();
        if ("true".equalsIgnoreCase(glowing)) {
            sb.append(":glowing=true");
        }
        String baby = params.getOrDefault("baby", "false").toString();
        if ("true".equalsIgnoreCase(baby)) {
            sb.append(":baby=true");
        }

        // Behavior sigil reference
        String behavior = params.getOrDefault("behavior", "").toString().trim();
        if (!behavior.isEmpty()) {
            sb.append(":behavior=").append(behavior);
        }

        // Targeting options
        String excludeOwner = params.getOrDefault("exclude_owner", "true").toString();
        if ("false".equalsIgnoreCase(excludeOwner)) {
            sb.append(":exclude_owner=false");
        }
        String forceTarget = params.getOrDefault("force_target", "true").toString();
        if ("false".equalsIgnoreCase(forceTarget)) {
            sb.append(":force_target=false");
        }
        String fireImmune = params.getOrDefault("fire_immune", "false").toString();
        if ("true".equalsIgnoreCase(fireImmune)) {
            sb.append(":fire_immune=true");
        }
        String noDrops = params.getOrDefault("no_drops", "true").toString();
        if ("false".equalsIgnoreCase(noDrops)) {
            sb.append(":no_drops=false");
        }

        // Note: on_hit_mark and on_hit_stun are handled by behavior sigils now

        // Add @Self target (spawn location)
        sb.append(" @Self");

        return sb.toString();
    }

    /**
     * Build PARTICLE effect string with key=value format for shapes.
     * Format: PARTICLE:FLAME:count=10:shape=circle:radius=3:points=20 @Target
     */
    private String buildParticleString(Map<String, Object> params, Map<String, Boolean> scaledParams) {
        StringBuilder sb = new StringBuilder("PARTICLE");

        // Particle type is required
        String particleType = params.getOrDefault("particle_type", "FLAME").toString();
        sb.append(":").append(particleType);

        // Count (value)
        Object count = params.get("value");
        if (count != null) {
            sb.append(":count=").append(formatNumber(count));
        }

        // Shape - only add shape params if shape is not "none"
        String shape = params.getOrDefault("shape", "none").toString();
        if (!"none".equalsIgnoreCase(shape)) {
            sb.append(":shape=").append(shape);

            // Shape-specific params
            if (params.containsKey("radius")) {
                sb.append(":radius=").append(formatNumber(params.get("radius")));
            }
            // Use spacing if provided, otherwise points
            if (params.containsKey("spacing")) {
                sb.append(":spacing=").append(formatNumber(params.get("spacing")));
            } else if (params.containsKey("points")) {
                sb.append(":points=").append(formatNumber(params.get("points")));
            }
            // Height is relevant for helix, spiral, cone
            if (params.containsKey("height") &&
                ("helix".equalsIgnoreCase(shape) || "spiral".equalsIgnoreCase(shape) || "cone".equalsIgnoreCase(shape))) {
                sb.append(":height=").append(formatNumber(params.get("height")));
            }
            // Duration for animated shapes
            if (params.containsKey("duration")) {
                sb.append(":duration=").append(formatNumber(params.get("duration")));
            }
            // Follow target
            String followTarget = params.getOrDefault("follow_target", "true").toString();
            if ("true".equalsIgnoreCase(followTarget)) {
                sb.append(":follow_target=true");
            }
            // Speed/spread also apply to shapes
            if (params.containsKey("spread")) {
                sb.append(":spread=").append(formatNumber(params.get("spread")));
            }
            if (params.containsKey("speed")) {
                sb.append(":speed=").append(formatNumber(params.get("speed")));
            }
        } else {
            // Basic particles use spread/speed
            if (params.containsKey("spread")) {
                sb.append(":spread=").append(formatNumber(params.get("spread")));
            }
            if (params.containsKey("speed")) {
                sb.append(":speed=").append(formatNumber(params.get("speed")));
            }
        }

        // Target
        String target = params.getOrDefault("target", "@Self").toString();
        sb.append(" ").append(target);

        return sb.toString();
    }

    /**
     * Build PULSE effect string.
     * Format: PULSE:FLAME:waves=3:start_radius=1:end_radius=7:wave_delay=10 @Target
     */
    private String buildPulseString(Map<String, Object> params, Map<String, Boolean> scaledParams) {
        StringBuilder sb = new StringBuilder("PULSE");

        // Particle type
        String particleType = params.getOrDefault("particle_type", "FLAME").toString();
        sb.append(":").append(particleType);

        // Core pulse params
        if (params.containsKey("waves")) {
            sb.append(":waves=").append(formatNumber(params.get("waves")));
        }
        if (params.containsKey("start_radius")) {
            sb.append(":start_radius=").append(formatNumber(params.get("start_radius")));
        }
        if (params.containsKey("end_radius")) {
            sb.append(":end_radius=").append(formatNumber(params.get("end_radius")));
        }
        if (params.containsKey("wave_delay")) {
            sb.append(":wave_delay=").append(formatNumber(params.get("wave_delay")));
        }

        // Fade
        String fade = params.getOrDefault("fade", "true").toString();
        if ("false".equalsIgnoreCase(fade)) {
            sb.append(":fade=false");
        }

        // Shape (sphere or circle)
        String shape = params.getOrDefault("shape", "sphere").toString();
        if (!"sphere".equalsIgnoreCase(shape)) {
            sb.append(":shape=").append(shape);
        }

        // Points
        if (params.containsKey("points")) {
            sb.append(":points=").append(formatNumber(params.get("points")));
        }

        // DUST color
        if ("DUST".equalsIgnoreCase(particleType)) {
            String color = params.getOrDefault("color", "").toString();
            if (!color.isEmpty()) {
                sb.append(":color=").append(color);
            }
            if (params.containsKey("size")) {
                sb.append(":size=").append(formatNumber(params.get("size")));
            }
        }

        // Material for FALLING_DUST
        if ("FALLING_DUST".equalsIgnoreCase(particleType) && params.containsKey("material")) {
            sb.append(":material=").append(params.get("material"));
        }

        // Target
        String target = params.getOrDefault("target", "@Self").toString();
        sb.append(" ").append(target);

        return sb.toString();
    }

    /**
     * Build SPAWN_DISPLAY effect string with key=value format.
     * Format: SPAWN_DISPLAY:TYPE:VALUE:DURATION:shape=circle:radius=3:points=20 @Target
     */
    private String buildSpawnDisplayString(Map<String, Object> params, Map<String, Boolean> scaledParams) {
        StringBuilder sb = new StringBuilder("SPAWN_DISPLAY");

        // Core params: TYPE:VALUE:DURATION
        String type = params.getOrDefault("type", "BLOCK").toString();
        String value = params.getOrDefault("value", "RED_SAND").toString();
        String duration = formatNumber(params.getOrDefault("duration", 10));

        sb.append(":").append(type);
        sb.append(":").append(value);
        sb.append(":").append(duration);

        // Shape params (key=value format)
        String shape = params.getOrDefault("shape", "point").toString();
        if (!"point".equalsIgnoreCase(shape)) {
            sb.append(":shape=").append(shape);
            if (params.containsKey("radius")) {
                sb.append(":radius=").append(formatNumber(params.get("radius")));
            }
            // Use spacing if provided, otherwise points (spacing is preferred)
            if (params.containsKey("spacing")) {
                sb.append(":spacing=").append(formatNumber(params.get("spacing")));
            } else if (params.containsKey("points")) {
                sb.append(":points=").append(formatNumber(params.get("points")));
            }
            // Height for spiral/helix/cone
            if (params.containsKey("height") &&
                ("spiral".equalsIgnoreCase(shape) || "helix".equalsIgnoreCase(shape) || "cone".equalsIgnoreCase(shape))) {
                sb.append(":height=").append(formatNumber(params.get("height")));
            }
            // Turns for spiral/helix
            if (params.containsKey("turns") &&
                ("spiral".equalsIgnoreCase(shape) || "helix".equalsIgnoreCase(shape))) {
                sb.append(":turns=").append(formatNumber(params.get("turns")));
            }
            // Rotate speed
            if (params.containsKey("rotate_speed")) {
                double rotSpeed = params.get("rotate_speed") instanceof Number ?
                    ((Number)params.get("rotate_speed")).doubleValue() : 0.0;
                if (rotSpeed > 0.01) {
                    sb.append(":rotate_speed=").append(formatNumber(params.get("rotate_speed")));
                }
            }
        }

        // Scale
        Object scale = params.get("scale");
        if (scale != null) {
            double scaleVal = scale instanceof Number ? ((Number) scale).doubleValue() : 1.0;
            if (Math.abs(scaleVal - 1.0) > 0.01) {
                sb.append(":scale=").append(formatNumber(scale));
            }
        }

        // Offset
        Object offsetY = params.get("offset_y");
        if (offsetY != null) {
            double offY = offsetY instanceof Number ? ((Number) offsetY).doubleValue() : 0.0;
            if (Math.abs(offY) > 0.01) {
                sb.append(":offset_y=").append(formatNumber(offsetY));
            }
        }

        // Follow owner
        String followOwner = params.getOrDefault("follow_owner", "false").toString();
        if ("true".equalsIgnoreCase(followOwner)) {
            sb.append(":follow_owner=true");
        }

        // Glow
        String glow = params.getOrDefault("glow", "false").toString();
        if ("true".equalsIgnoreCase(glow)) {
            sb.append(":glow=true");
        }

        // Billboard
        String billboard = params.getOrDefault("billboard", "FIXED").toString();
        if (!"FIXED".equalsIgnoreCase(billboard)) {
            sb.append(":billboard=").append(billboard);
        }

        // Grid ground snapping
        String snapToGround = params.getOrDefault("snap_to_ground", "false").toString();
        if ("true".equalsIgnoreCase(snapToGround)) {
            sb.append(":snap_to_ground=true");
            // Ground offset only matters when snap_to_ground is true
            Object groundOffset = params.get("ground_offset");
            if (groundOffset != null) {
                double offset = groundOffset instanceof Number ? ((Number) groundOffset).doubleValue() : 0.0;
                if (Math.abs(offset) > 0.001) {
                    sb.append(":ground_offset=").append(formatNumber(groundOffset));
                }
            }
        }

        // Behavior (for entity interactions like quicksand)
        String behavior = params.getOrDefault("behavior", "").toString();
        if (!behavior.isEmpty()) {
            sb.append(":behavior=").append(behavior);
        }

        // Target
        String target = params.getOrDefault("target", "@Self").toString();
        sb.append(" ").append(target);

        return sb.toString();
    }

    private String formatNumber(Object value) {
        if (value instanceof Number num) {
            double d = num.doubleValue();
            if (d == (long) d) {
                return String.valueOf((long) d);
            }
            return String.format("%.1f", d);
        }
        return String.valueOf(value);
    }

    /**
     * Parse effect string into parameters.
     */
    private static Map<String, Object> parseEffectString(String effectString) {
        Map<String, Object> params = new HashMap<>();

        if (effectString == null || effectString.isEmpty()) {
            return params;
        }

        // Split by space first to separate targets
        String[] spaceParts = effectString.split(" ");
        String mainPart = spaceParts[0];
        String effectType = mainPart.split(":")[0].toUpperCase();

        // Special handling for SPAWN_ENTITY with key=value format
        if ("SPAWN_ENTITY".equalsIgnoreCase(effectType)) {
            return parseSpawnEntityString(effectString);
        }

        // Special handling for PARTICLE with key=value format
        if ("PARTICLE".equalsIgnoreCase(effectType)) {
            return parseParticleString(effectString);
        }

        // Special handling for SPAWN_DISPLAY with key=value format
        if ("SPAWN_DISPLAY".equalsIgnoreCase(effectType)) {
            return parseSpawnDisplayString(effectString);
        }

        // Handle TELEPORT effect's dual target syntax: @teleportee @target
        if ("TELEPORT".equalsIgnoreCase(effectType)) {
            // Find @ targets
            List<String> targets = new ArrayList<>();
            for (int i = 1; i < spaceParts.length; i++) {
                if (spaceParts[i].startsWith("@")) {
                    targets.add(spaceParts[i]);
                }
            }
            if (targets.size() >= 1) {
                params.put("teleportee", targets.get(0));
            }
            if (targets.size() >= 2) {
                params.put("teleportTarget", targets.get(1));
            }
        } else {
            // Other effects with single target
            if (spaceParts.length > 1 && spaceParts[spaceParts.length - 1].startsWith("@")) {
                params.put("target", spaceParts[spaceParts.length - 1]);
            }
        }

        // Split main part by colon
        String[] parts = mainPart.split(":");
        if (parts.length <= 1) {
            return params;
        }

        // Get effect type to know parameter order
        EffectParamConfig config = getParamConfig(effectType);

        // Parse colon-separated parameters (skip @ params in the config)
        int colonParamIndex = 0;
        for (int i = 1; i < parts.length; i++) {
            // Find next non-@ param in config
            while (colonParamIndex < config.params.size()) {
                ParamInfo paramInfo = config.params.get(colonParamIndex);
                if (!paramInfo.key.equals("teleportee") && !paramInfo.key.equals("teleportTarget") && !paramInfo.key.equals("target")) {
                    String value = parts[i];
                    try {
                        if (paramInfo.type == ParamType.NUMBER) {
                            params.put(paramInfo.key, Double.parseDouble(value));
                        } else {
                            params.put(paramInfo.key, value);
                        }
                    } catch (NumberFormatException e) {
                        params.put(paramInfo.key, value);
                    }
                    colonParamIndex++;
                    break;
                }
                colonParamIndex++;
            }
        }

        return params;
    }

    /**
     * Parse SPAWN_ENTITY effect string with key=value format.
     */
    private static Map<String, Object> parseSpawnEntityString(String effectString) {
        Map<String, Object> params = new HashMap<>();

        // Remove target selector
        String mainPart = effectString.replaceAll("\\s+@\\w+.*$", "").trim();

        // Split by colon
        String[] parts = mainPart.split(":");
        if (parts.length < 2) return params;

        // Core params: SPAWN_ENTITY:TYPE:COUNT:DURATION
        if (parts.length > 1) params.put("type", parts[1]);
        if (parts.length > 2) {
            try { params.put("count", Double.parseDouble(parts[2])); }
            catch (NumberFormatException e) { params.put("count", 1.0); }
        }
        if (parts.length > 3) {
            try { params.put("duration", Double.parseDouble(parts[3])); }
            catch (NumberFormatException e) { params.put("duration", 10.0); }
        }

        // Parse key=value params
        for (int i = 4; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                String key = kv[0];
                String value = kv.length > 1 ? kv[1] : "";

                switch (key) {
                    // Stats
                    case "hp" -> {
                        try { params.put("hp", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("hp", 20.0); }
                    }
                    case "damage" -> {
                        try { params.put("damage", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("damage", 4.0); }
                    }
                    case "speed" -> {
                        try { params.put("speed", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("speed", 0.25); }
                    }
                    case "knockback_resist" -> {
                        try { params.put("knockback_resist", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("knockback_resist", 0.0); }
                    }
                    // Targeting
                    case "target" -> params.put("target", value);
                    // AI/Behavior controls
                    case "no_ai" -> params.put("no_ai", value);
                    case "silent" -> params.put("silent", value);
                    // Appearance
                    case "name" -> params.put("name", value);
                    case "glowing" -> params.put("glowing", value);
                    case "baby" -> params.put("baby", value);
                    // On-hit mark
                    case "on_hit_mark" -> {
                        // Format: MARK_NAME~DURATION
                        if (value.contains("~")) {
                            String[] markParts = value.split("~", 2);
                            params.put("on_hit_mark", markParts[0]);
                            try { params.put("on_hit_mark_duration", Double.parseDouble(markParts[1])); }
                            catch (NumberFormatException e) { params.put("on_hit_mark_duration", 10.0); }
                        } else {
                            params.put("on_hit_mark", value);
                        }
                    }
                    case "on_hit_mark_chance" -> {
                        try { params.put("on_hit_mark_chance", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("on_hit_mark_chance", 100.0); }
                    }
                    // On-hit stun
                    case "on_hit_stun" -> {
                        try { params.put("on_hit_stun", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("on_hit_stun", 0.0); }
                    }
                    case "on_hit_stun_chance" -> {
                        try { params.put("on_hit_stun_chance", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("on_hit_stun_chance", 100.0); }
                    }
                    // Behavior
                    case "behavior" -> params.put("behavior", value);
                    // Targeting options
                    case "exclude_owner" -> params.put("exclude_owner", value);
                    case "force_target" -> params.put("force_target", value);
                    case "fire_immune" -> params.put("fire_immune", value);
                    case "no_drops" -> params.put("no_drops", value);
                }
            }
        }

        return params;
    }

    /**
     * Parse PARTICLE effect string with key=value format.
     * Format: PARTICLE:FLAME:count=10:shape=circle:radius=3 @Target
     */
    private static Map<String, Object> parseParticleString(String effectString) {
        Map<String, Object> params = new HashMap<>();

        // Extract target
        String[] spaceParts = effectString.split(" ");
        if (spaceParts.length > 1 && spaceParts[spaceParts.length - 1].startsWith("@")) {
            params.put("target", spaceParts[spaceParts.length - 1]);
        }

        // Remove target selector
        String mainPart = effectString.replaceAll("\\s+@\\w+.*$", "").trim();
        String[] parts = mainPart.split(":");

        // PARTICLE:TYPE then key=value pairs
        if (parts.length >= 2) {
            String potentialType = parts[1];
            if (!potentialType.contains("=")) {
                params.put("particle_type", potentialType.toUpperCase());
            }
        }

        // Parse key=value params
        for (int i = 2; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                String key = kv[0].toLowerCase();
                String value = kv.length > 1 ? kv[1] : "";

                switch (key) {
                    case "count" -> {
                        try { params.put("value", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("value", 10.0); }
                    }
                    case "shape" -> params.put("shape", value.toLowerCase());
                    case "radius" -> {
                        try { params.put("radius", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("radius", 2.0); }
                    }
                    case "points" -> {
                        try { params.put("points", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("points", 20.0); }
                    }
                    case "height" -> {
                        try { params.put("height", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("height", 3.0); }
                    }
                    case "spread" -> {
                        try { params.put("spread", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("spread", 0.5); }
                    }
                    case "speed" -> {
                        try { params.put("speed", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("speed", 0.02); }
                    }
                }
            }
        }

        // Default shape if not specified
        if (!params.containsKey("shape")) {
            params.put("shape", "none");
        }

        return params;
    }

    /**
     * Parse SPAWN_DISPLAY effect string with key=value format.
     * Format: SPAWN_DISPLAY:TYPE:VALUE:DURATION:shape=circle:radius=3 @Target
     */
    private static Map<String, Object> parseSpawnDisplayString(String effectString) {
        Map<String, Object> params = new HashMap<>();

        // Extract target
        String[] spaceParts = effectString.split(" ");
        if (spaceParts.length > 1 && spaceParts[spaceParts.length - 1].startsWith("@")) {
            params.put("target", spaceParts[spaceParts.length - 1]);
        }

        // Remove target selector
        String mainPart = effectString.replaceAll("\\s+@\\w+.*$", "").trim();
        String[] parts = mainPart.split(":");

        // Core params: SPAWN_DISPLAY:TYPE:VALUE:DURATION
        if (parts.length > 1) params.put("type", parts[1].toUpperCase());
        if (parts.length > 2) params.put("value", parts[2]);
        if (parts.length > 3) {
            try { params.put("duration", Double.parseDouble(parts[3])); }
            catch (NumberFormatException e) { params.put("duration", 10.0); }
        }

        // Parse key=value params
        for (int i = 4; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                String key = kv[0].toLowerCase();
                String value = kv.length > 1 ? kv[1] : "";

                switch (key) {
                    case "shape", "pattern" -> params.put("shape", value.toLowerCase());
                    case "radius" -> {
                        try { params.put("radius", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("radius", 3.0); }
                    }
                    case "points" -> {
                        try { params.put("points", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("points", 16.0); }
                    }
                    case "height" -> {
                        try { params.put("height", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("height", 3.0); }
                    }
                    case "scale" -> {
                        try { params.put("scale", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("scale", 1.0); }
                    }
                    case "offset_y" -> {
                        try { params.put("offset_y", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("offset_y", 0.0); }
                    }
                    case "follow_owner" -> params.put("follow_owner", value);
                    case "glow" -> params.put("glow", value);
                    case "billboard" -> params.put("billboard", value.toUpperCase());
                    case "snap_to_ground" -> params.put("snap_to_ground", value);
                    case "ground_offset" -> {
                        try { params.put("ground_offset", Double.parseDouble(value)); }
                        catch (NumberFormatException e) { params.put("ground_offset", 0.0); }
                    }
                    case "behavior" -> params.put("behavior", value);
                }
            }
        }

        // Default shape if not specified
        if (!params.containsKey("shape")) {
            params.put("shape", "point");
        }

        return params;
    }

    /**
     * Open the Effect Param GUI for a new effect.
     */
    public static void openGUI(GUIManager guiManager, Player player, Sigil sigil, String signalKey,
                               int effectIndex, String effectString) {
        if (sigil == null || signalKey == null) {
            player.sendMessage(TextUtil.colorize("§cError: Invalid parameters!"));
            return;
        }

        // Parse effect type and parameters
        String effectType;
        Map<String, Object> params;

        if (effectIndex >= 0) {
            // Editing existing effect
            effectType = parseEffectType(effectString);
            params = parseEffectString(effectString);
        } else {
            // New effect (effectString is just the effect type)
            effectType = effectString;
            params = new HashMap<>();
        }

        openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, params);
    }

    /**
     * Open the Effect Param GUI with existing params (used when cycling/updating values).
     */
    public static void openGUIWithParams(GUIManager guiManager, Player player, Sigil sigil, String signalKey,
                                         int effectIndex, String effectType, Map<String, Object> params) {
        openGUIWithParams(guiManager, player, sigil, signalKey, effectIndex, effectType, params, null, null, null);
    }

    /**
     * Open the Effect Param GUI with existing params and tier scaling state.
     */
    public static void openGUIWithParams(GUIManager guiManager, Player player, Sigil sigil, String signalKey,
                                         int effectIndex, String effectType, Map<String, Object> params,
                                         Map<String, Boolean> scaledParams, Map<String, List<Double>> tierValues,
                                         String currentScaledParam) {
        if (sigil == null || signalKey == null) {
            player.sendMessage(TextUtil.colorize("§cError: Invalid parameters!"));
            return;
        }

        // Preserve existing session data if available
        GUISession existingSession = guiManager.getSession(player);
        if (existingSession != null && existingSession.getType() == GUIType.EFFECT_PARAM) {
            if (scaledParams == null) {
                @SuppressWarnings("unchecked")
                Map<String, Boolean> existing = (Map<String, Boolean>) existingSession.get("scaledParams");
                scaledParams = existing;
            }
            if (tierValues == null) {
                @SuppressWarnings("unchecked")
                Map<String, List<Double>> existing = (Map<String, List<Double>>) existingSession.get("tierValues");
                tierValues = existing;
            }
            if (currentScaledParam == null) {
                currentScaledParam = existingSession.get("currentScaledParam", String.class);
            }
        }

        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE,
            TextUtil.parseComponent("§7Effects > §f" + TextUtil.toProperCase(effectType.replace("_", " "))));

        // Fill background
        ItemBuilder.fillBackground(inv);

        // Get parameter configuration
        EffectParamConfig config = getParamConfig(effectType);

        // Info item
        inv.setItem(SLOT_INFO, ItemBuilder.createItem(Material.BOOK,
            "§eEffect Configuration",
            "§7Configure parameters for this effect",
            "",
            "§fShift+Click §7on number params",
            "§7to enable §eTier Scaling"
        ));

        // Add parameter items - filter by visibility conditions
        List<ParamInfo> visibleParams = new ArrayList<>();
        for (ParamInfo paramInfo : config.params) {
            if (paramInfo.isVisible(params)) {
                visibleParams.add(paramInfo);
            }
        }

        for (int i = 0; i < Math.min(visibleParams.size(), PARAM_SLOTS.length); i++) {
            ParamInfo paramInfo = visibleParams.get(i);
            Object value = params.getOrDefault(paramInfo.key, paramInfo.defaultValue);
            boolean isTierScaled = scaledParams != null && scaledParams.getOrDefault(paramInfo.key, false);
            inv.setItem(PARAM_SLOTS[i], buildParamItem(paramInfo, value, isTierScaled, tierValues, sigil.getMaxTier()));
        }

        // Show tier value slots if a param is tier-scaled
        int maxTier = sigil.getMaxTier();
        if (currentScaledParam != null && tierValues != null && tierValues.containsKey(currentScaledParam)) {
            List<Double> values = tierValues.get(currentScaledParam);
            double increment = 1.0; // Default increment

            // Show tier slots
            for (int i = 0; i < Math.min(5, maxTier); i++) {
                double val = i < values.size() ? values.get(i) : 0;
                inv.setItem(TIER_ROW_1[i], buildTierValueItem(i + 1, val, increment, maxTier));
            }
            for (int i = 5; i < Math.min(10, maxTier); i++) {
                double val = i < values.size() ? values.get(i) : 0;
                inv.setItem(TIER_ROW_2[i - 5], buildTierValueItem(i + 1, val, increment, maxTier));
            }
        }

        // Back button
        inv.setItem(SLOT_BACK, ItemBuilder.createCancelButton());

        // Save button
        inv.setItem(SLOT_SAVE, ItemBuilder.createConfirmButton("effect"));

        // Create session
        GUISession session = new GUISession(GUIType.EFFECT_PARAM);
        session.put("sigil", sigil);
        session.put("signalKey", signalKey);
        session.put("effectType", effectType);
        session.put("effectIndex", effectIndex);
        session.put("params", params);
        if (scaledParams != null) session.put("scaledParams", scaledParams);
        if (tierValues != null) session.put("tierValues", tierValues);
        if (currentScaledParam != null) session.put("currentScaledParam", currentScaledParam);
        session.put("tierIncrement", 1.0);

        // Open GUI
        guiManager.openGUI(player, inv, session);
    }

    /**
     * Build a tier value item for inline editing.
     */
    private static ItemStack buildTierValueItem(int tier, double value, double increment, int maxTier) {
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
            "§fLeft-Click§7: +" + formatIncrement(increment),
            "§fRight-Click§7: -" + formatIncrement(increment),
            "§fShift-Click§7: Enter value"
        );
    }

    private static String formatIncrement(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }

    /**
     * Build a parameter item.
     */
    private static ItemStack buildParamItem(ParamInfo paramInfo, Object currentValue, boolean isTierScaled,
                                            Map<String, List<Double>> tierValues, int maxTier) {
        Material material;

        if (isTierScaled) {
            material = Material.EXPERIENCE_BOTTLE; // Special material for tier-scaled params
        } else {
            material = switch (paramInfo.type) {
                case NUMBER -> Material.PAPER;
                case TEXT -> Material.WRITABLE_BOOK;
                case TARGET -> Material.RECOVERY_COMPASS;
                case PARTICLE_BROWSER -> Material.FIREWORK_ROCKET;
                case SOUND_BROWSER -> Material.BELL;
                case ATTRIBUTE_BROWSER -> Material.GOLDEN_APPLE;
                case CYCLE -> Material.COMPASS;
            };
        }

        List<String> lore = new ArrayList<>();

        if (isTierScaled && tierValues != null && tierValues.containsKey(paramInfo.key)) {
            List<Double> values = tierValues.get(paramInfo.key);
            lore.add("§aTier Scaled §8(click tiers below)");
            lore.add("");

            // Show tier progression preview
            StringBuilder preview = new StringBuilder("§7");
            for (int i = 0; i < Math.min(3, values.size()); i++) {
                if (i > 0) preview.append(" → ");
                double val = values.get(i);
                preview.append("§f").append(val == (long) val ? String.valueOf((long) val) : String.format("%.1f", val));
            }
            if (values.size() > 3) {
                preview.append(" → §f...");
            }
            lore.add(preview.toString());
            lore.add("");
            lore.add("§eShift+Click §7to disable scaling");
        } else {
            lore.add("§7Current: §f" + currentValue);
            lore.add("");

            if (paramInfo.type == ParamType.NUMBER) {
                lore.add("§7Range: §f" + paramInfo.min + " - " + paramInfo.max);
                lore.add("");
                lore.add("§eShift+Click §7to enable §aTier Scaling");
            }
        }

        if (paramInfo.type == ParamType.TARGET) {
            lore.add("§7Options:");
            lore.add("§8  - §f@Self");
            lore.add("§8  - §f@Victim");
            lore.add("§8  - §f@Nearby");
            lore.add("");
            lore.add("§7Click to cycle");
        } else if (paramInfo.type == ParamType.PARTICLE_BROWSER) {
            lore.add("§7Click to browse particles");
        } else if (paramInfo.type == ParamType.SOUND_BROWSER) {
            lore.add("§7Click to browse sounds");
            lore.add("§7Includes volume & pitch controls");
        } else if (paramInfo.type == ParamType.ATTRIBUTE_BROWSER) {
            lore.add("§7Click to browse attributes");
            lore.add("§7Speed, Health, Damage, Armor, etc.");
        } else if (paramInfo.type == ParamType.CYCLE && paramInfo.cycleOptions != null) {
            lore.add("§7Options:");
            for (String option : paramInfo.cycleOptions) {
                if (option.equalsIgnoreCase(String.valueOf(currentValue))) {
                    lore.add("§a  ► §f" + option);
                } else {
                    lore.add("§8  - §7" + option);
                }
            }
            lore.add("");
            lore.add("§7Click to cycle");
        } else if (!isTierScaled) {
            lore.add("§7Click to change");
        }

        String title = isTierScaled
            ? "§a" + paramInfo.displayName + " §7(Tier Scaled)"
            : "§e" + paramInfo.displayName;
        return ItemBuilder.createItem(material, title, lore);
    }

    /**
     * Parse effect type from effect string.
     */
    private static String parseEffectType(String effectString) {
        if (effectString == null || effectString.isEmpty()) {
            return "UNKNOWN";
        }

        int colonIndex = effectString.indexOf(':');
        if (colonIndex > 0) {
            return effectString.substring(0, colonIndex).toUpperCase();
        }

        int spaceIndex = effectString.indexOf(' ');
        if (spaceIndex > 0) {
            return effectString.substring(0, spaceIndex).toUpperCase();
        }

        return effectString.toUpperCase();
    }

    /**
     * Get parameter configuration for an effect type.
     * Parameter names from EFFECTS.pdf specification.
     */
    public static EffectParamConfig getParamConfig(String effectType) {
        effectType = effectType.toUpperCase();

        EffectParamConfig config = new EffectParamConfig();

        switch (effectType) {
            // ============ DAMAGE/COMBAT EFFECTS ============
            case "DEAL_DAMAGE", "DAMAGE" -> {
                config.addParam("value", "Damage", ParamType.NUMBER, 5, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Self", "@Nearby:5", "@Nearby:10");
            }
            case "DAMAGE_BOOST" -> {
                // Modifies event damage - no target needed
                config.addParam("value", "% Damage Boost", ParamType.NUMBER, 15, 0, 99999);
            }
            case "REDUCE_DAMAGE", "AEGIS" -> {
                // Modifies event damage - no target needed
                config.addParam("value", "% Damage Reduction", ParamType.NUMBER, 15, 0, 99999);
            }
            case "LIFESTEAL" -> {
                // Self-heal based on damage dealt - no target needed
                config.addParam("value", "% Lifesteal", ParamType.NUMBER, 25, 0, 99999);
            }
            case "DAMAGE_ARMOR", "DISINTEGRATE" -> {
                config.addParam("value", "Durability Damage", ParamType.NUMBER, 10, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Self");
            }
            case "BLEEDING" -> {
                config.addParam("value", "Damage Per Second", ParamType.NUMBER, 1, 0, 99999);
                config.addParam("duration", "Duration (seconds)", ParamType.NUMBER, 5, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Self", "@Nearby:5");
            }
            case "CLEAVE" -> {
                config.addParam("value", "Damage", ParamType.NUMBER, 3.0, 0, 99999);
                config.addParam("radius", "Radius", ParamType.NUMBER, 3.0, 0, 99999);
            }
            case "EXECUTE" -> {
                config.addParam("value", "% Health Threshold", ParamType.NUMBER, 30, 0, 99999);
                config.addParam("bonusDamage", "Bonus Damage", ParamType.NUMBER, 10, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Nearby:5");
            }
            case "REFLECT_DAMAGE" -> {
                // Reflects back to attacker - no target config needed
                config.addParam("value", "% Reflected", ParamType.NUMBER, 25, 0, 99999);
            }

            // ============ HEALING/SUSTAIN EFFECTS ============
            case "HEAL" -> {
                config.addParam("value", "Health", ParamType.NUMBER, 4, 0, 99999);
                config.addCycleParam("target", "Target", "@Self", "@Self", "@Victim", "@Nearby:5");
            }
            case "ABSORBTION" -> {
                // ABSORBTION:duration:hearts @Target
                config.addParam("duration", "Duration (seconds)", ParamType.NUMBER, 5, 0, 99999);
                config.addParam("value", "Absorption Hearts", ParamType.NUMBER, 2, 0, 99999);
                config.addCycleParam("target", "Target", "@Self", "@Self", "@Victim", "@Nearby:5");
            }
            case "DEVOUR" -> {
                config.addParam("value", "Absorption Hearts", ParamType.NUMBER, 4, 0, 99999);
            }
            case "SATURATE", "REPLENISH" -> {
                config.addParam("value", "Hunger Restored", ParamType.NUMBER, 4, 0, 99999);
                config.addCycleParam("target", "Target", "@Self", "@Self", "@Nearby:5");
            }
            case "PHOENIX" -> {
                // Self-resurrection - no target config needed
                config.addParam("value", "Health Restored", ParamType.NUMBER, 10, 0, 99999);
            }
            case "MAX_HEALTH_BOOST", "ANGELIC" -> {
                config.addParam("value", "Duration (seconds)", ParamType.NUMBER, 20, 0, 99999);
                config.addParam("amplifier", "Level", ParamType.NUMBER, 1, 0, 99999);
                config.addCycleParam("target", "Target", "@Self", "@Self", "@Victim");
            }

            // ============ MOVEMENT EFFECTS ============
            case "TELEPORT" -> {
                config.addCycleParam("type", "Type", "RANDOM", "RANDOM", "AROUND", "BEHIND");
                config.addParam("value", "Distance", ParamType.NUMBER, 5, 0, 99999);
                config.addCycleParam("facing", "Facing", "ENTITY", "ENTITY", "RANDOM", "AWAY", "KEEP", "SAME");
                config.addCycleParam("teleportee", "Who Teleports", "@Self", "@Self", "@Target", "@Victim");
                config.addCycleParam("teleportTarget", "Around/Behind Who", "@Target", "@Self", "@Target", "@Victim");
            }
            case "DASH" -> {
                // Uses get("direction") and getValue() for force
                config.addCycleParam("direction", "Direction", "FORWARD", "FORWARD", "WITH_MOMENTUM", "UP");
                config.addParam("value", "Force", ParamType.NUMBER, 1.5, 0, 99999);
            }
            case "DODGE" -> {
                // Uses getValue() as dodge chance percentage
                config.addParam("value", "% Dodge Chance", ParamType.NUMBER, 15, 0, 99999);
            }
            case "KNOCKBACK" -> {
                config.addParam("value", "Force", ParamType.NUMBER, 1.5, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Nearby:5", "@Nearby:10");
            }
            case "PULL" -> {
                config.addParam("value", "Force", ParamType.NUMBER, 1.5, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Nearby:5", "@Nearby:10");
            }
            case "LAUNCH" -> {
                config.addParam("value", "Height", ParamType.NUMBER, 1.5, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Self", "@Nearby:5");
            }
            case "GRAPPLE" -> {
                // Grapple to target location
                config.addParam("value", "Speed", ParamType.NUMBER, 1.5, 0, 99999);
                config.addParam("range", "Max Range", ParamType.NUMBER, 30, 0, 99999);
            }
            case "GROUND_SLAM" -> {
                // AoE around self
                config.addParam("value", "Damage", ParamType.NUMBER, 5.0, 0, 99999);
                config.addParam("radius", "Radius", ParamType.NUMBER, 4.0, 0, 99999);
                config.addParam("knockback", "Knockback", ParamType.NUMBER, 1.0, 0, 99999);
            }
            case "SWAP" -> {
                // Swap positions with target
                config.addParam("range", "Max Range", ParamType.NUMBER, 15, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Nearby:10");
            }
            case "SMOKEBOMB" -> {
                // AoE around self
                config.addParam("value", "Radius", ParamType.NUMBER, 5, 0, 99999);
                config.addParam("duration", "Blind Duration (ticks)", ParamType.NUMBER, 60, 0, 99999);
            }
            case "MODIFY_ATTRIBUTE" -> {
                // Modify any entity attribute temporarily
                config.addParam("attribute", "Attribute", ParamType.ATTRIBUTE_BROWSER, "GENERIC_MOVEMENT_SPEED");
                config.addCycleParam("operation", "Operation", "MULTIPLY_SCALAR_1", "ADD_NUMBER", "ADD_SCALAR", "MULTIPLY_SCALAR_1");
                config.addParam("value", "Value", ParamType.NUMBER, -0.25, -99999, 99999);
                config.addParam("duration", "Duration (seconds)", ParamType.NUMBER, 5, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Self", "@Nearby:5", "@NearbyEntities:5");
            }

            // ============ ENVIRONMENTAL EFFECTS ============
            case "FREEZING" -> {
                config.addParam("value", "Duration (seconds)", ParamType.NUMBER, 5, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Nearby:5");
            }
            case "IGNITE" -> {
                config.addParam("value", "Fire Ticks", ParamType.NUMBER, 100, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Nearby:5", "@Nearby:10");
            }
            case "LIGHTNING" -> {
                config.addParam("value", "Damage (0=visual only)", ParamType.NUMBER, 5, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Self", "@Nearby:5");
            }
            case "EXPLOSION" -> {
                config.addParam("value", "Power", ParamType.NUMBER, 2.0, 0, 99999);
                config.addCycleParam("fire", "Set Fire", "false", "false", "true");
                config.addCycleParam("breakBlocks", "Break Blocks", "false", "false", "true");
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Self");
            }

            // ============ UTILITY EFFECTS ============
            case "ALLURE" -> {
                // Uses getValue() for radius
                config.addParam("value", "Radius", ParamType.NUMBER, 5, 0, 99999);
            }
            case "INQUISITIVE" -> {
                // Uses getValue() as XP bonus percentage
                config.addParam("value", "% XP Bonus", ParamType.NUMBER, 25, 0, 99999);
            }
            case "ENLIGHTENED" -> {
                // Uses getValue() as luck bonus percentage
                config.addParam("value", "% Luck Bonus", ParamType.NUMBER, 25, 0, 99999);
            }
            case "RESIST_EFFECTS", "WARD" -> {
                // Uses getValue() as reduction percentage
                config.addParam("value", "% Reduction", ParamType.NUMBER, 15, 0, 99999);
            }

            // ============ VISUAL/AUDIO EFFECTS ============
            case "PARTICLE" -> {
                // Always visible - CORE params only
                config.addParam("particle_type", "Particle Type", ParamType.PARTICLE_BROWSER, "FLAME");
                config.addCycleParam("shape", "Shape", "none", "none", "circle", "spiral", "helix", "sphere", "line", "beam", "cone", "grid", "pulse");
                config.addCycleParam("target", "Target", "@Self", "@Self", "@Victim", "@Nearby:5");

                // Count - only for basic particles (no shape)
                config.addParamWhen("count", "Particle Count", ParamType.NUMBER, 10, 1, 100, "shape=none");

                // Pulse-specific params (expanding shockwave)
                config.addParamWhen("waves", "Waves", ParamType.NUMBER, 3, 1, 10, "shape=pulse");
                config.addParamWhen("start_radius", "Start Radius", ParamType.NUMBER, 1.0, 0.1, 30, "shape=pulse");
                config.addParamWhen("end_radius", "End Radius", ParamType.NUMBER, 7.0, 0.1, 30, "shape=pulse");
                config.addParamWhen("wave_delay", "Wave Delay (ticks)", ParamType.NUMBER, 10, 1, 60, "shape=pulse");
                config.addParamWhen("fade", "Fade Intensity", ParamType.NUMBER, 1.0, 0.0, 1.0, "shape=pulse");
                config.addCycleParamWhen("pulse_shape", "Pulse Shape", "sphere", "shape=pulse", "sphere", "circle");

                // Standard shape params - for all shapes except none
                config.addParamWhen("radius", "Radius", ParamType.NUMBER, 2.0, 0.1, 20, "shape!=none,pulse");
                config.addParamWhen("spacing", "Point Spacing", ParamType.NUMBER, 0.5, 0.1, 5, "shape!=none");
                config.addParamWhen("duration", "Duration (sec)", ParamType.NUMBER, 2.0, 0.1, 30, "shape!=none,pulse");
                config.addCycleParamWhen("follow_target", "Follow Target", "true", "shape!=none,pulse", "true", "false");
                config.addParamWhen("rotate_speed", "Rotate Speed", ParamType.NUMBER, 1.0, 0.0, 10, "shape!=none,pulse");
                config.addParamWhen("y_offset", "Y Offset", ParamType.NUMBER, 0.0, -5, 10, "shape!=none,pulse");

                // Spiral/helix specific
                config.addParamWhen("height", "Shape Height", ParamType.NUMBER, 3.0, 0.1, 20, "shape=spiral,helix,cone");
                config.addParamWhen("turns", "Spiral Turns", ParamType.NUMBER, 2.0, 0.5, 10, "shape=spiral,helix");

                // Beam specific
                config.addParamWhen("jitter", "Beam Jitter", ParamType.NUMBER, 0.0, 0.0, 2, "shape=beam");

                // Basic particle params - only when no shape
                config.addParamWhen("spread", "Spread", ParamType.NUMBER, 0.5, 0.0, 5, "shape=none");
                config.addParamWhen("speed", "Speed", ParamType.NUMBER, 0.02, 0.0, 1, "shape=none");

                // DUST color - only show for DUST particle type
                config.addCycleParamWhen("color", "Dust Color", "GOLD", "particle_type=DUST", "GOLD", "RED", "BLUE", "GREEN", "PURPLE", "WHITE", "ORANGE", "YELLOW", "CYAN", "PINK", "SAND");
                config.addParamWhen("size", "Dust Size", ParamType.NUMBER, 1.0, 0.1, 5, "particle_type=DUST");

                // Material for FALLING_DUST
                config.addCycleParamWhen("material", "Material", "SAND", "particle_type=FALLING_DUST", "SAND", "RED_SAND", "GRAVEL", "DIRT", "STONE", "GOLD_BLOCK");
            }
            case "SOUND" -> {
                // Sound browser handles sound, volume, pitch together
                config.addParam("sound", "Sound", ParamType.SOUND_BROWSER, "ENTITY_EXPERIENCE_ORB_PICKUP");
                config.addCycleParam("target", "Target", "@Self", "@Self", "@Victim");
            }
            case "MESSAGE" -> {
                config.addCycleParam("type", "Type", "CHAT", "CHAT", "ACTIONBAR", "TITLE", "SUBTITLE");
                config.addParam("message", "Message", ParamType.TEXT, "§aHello!");
                config.addCycleParam("target", "Target", "@Self", "@Self", "@Victim");
            }

            // ============ POTION EFFECT ============
            case "POTION_EFFECT", "POTION" -> {
                config.addParam("potion_type", "Effect Type", ParamType.TEXT, "SPEED");
                config.addParam("duration", "Duration (seconds)", ParamType.NUMBER, 10, 0, 99999);
                config.addParam("amplifier", "Level (0=I, 1=II...)", ParamType.NUMBER, 0, 0, 99999);
                config.addCycleParam("target", "Target", "@Self", "@Self", "@Victim", "@Nearby:5", "@Nearby:10");
            }

            // ============ SPAWNING EFFECTS ============
            case "SPAWN_ENTITY" -> {
                // Core parameters
                config.addParam("type", "Entity Type", ParamType.TEXT, "HUSK");
                config.addParam("count", "Count", ParamType.NUMBER, 1, 1, 100);
                config.addParam("duration", "Duration (sec)", ParamType.NUMBER, 10, 0, 99999);
                config.addParam("behavior", "Behavior (optional)", ParamType.TEXT, "");
                config.addCycleParam("target", "Target Mode", "VICTIM", "VICTIM", "TARGET", "NEARBY", "NONE");
                // Stats
                config.addParam("hp", "Health", ParamType.NUMBER, 20, 1, 99999);
                config.addParam("damage", "Attack Damage", ParamType.NUMBER, 4, 0, 99999);
                config.addParam("speed", "Move Speed", ParamType.NUMBER, 0.25, 0, 1);
                config.addParam("knockback_resist", "KB Resist (0-1)", ParamType.NUMBER, 0, 0, 1);
                // AI/Behavior controls (CRITICAL for dummies)
                config.addCycleParam("no_ai", "Disable AI", "false", "false", "true");
                config.addCycleParam("silent", "Silent", "false", "false", "true");
                // Appearance
                config.addParam("name", "Display Name", ParamType.TEXT, "");
                config.addCycleParam("glowing", "Glowing", "false", "false", "true");
                config.addCycleParam("baby", "Baby", "false", "false", "true");
                // Targeting options
                config.addCycleParam("exclude_owner", "Exclude Owner", "true", "true", "false");
                config.addCycleParam("force_target", "Force Target", "true", "true", "false");
                config.addCycleParam("fire_immune", "Fire Immune", "false", "false", "true");
                config.addCycleParam("no_drops", "No Drops", "true", "true", "false");
            }

            // ============ STATUS/CC EFFECTS ============
            case "STUN" -> {
                config.addParam("value", "Duration (seconds)", ParamType.NUMBER, 2.0, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Nearby:5");
            }
            case "MARK" -> {
                config.addParam("mark_name", "Mark Name", ParamType.TEXT, "MARKED");
                config.addParam("duration", "Duration (seconds)", ParamType.NUMBER, 10, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Nearby:5", "@Nearby:10");
            }

            // ============ SUMMON EFFECTS ============
            case "SUMMON_MUMMY" -> {
                // Specialized mummy summoning (deprecated - use SPAWN_ENTITY)
                config.addParam("duration", "Duration (sec)", ParamType.NUMBER, 10, 0, 99999);
                config.addParam("count", "Mummy Count", ParamType.NUMBER, 2, 1, 100);  // Keep max 100 to prevent lag
                config.addParam("markChance", "% Mark Chance", ParamType.NUMBER, 25, 0, 99999);
                config.addParam("curseChance", "% Stun Chance", ParamType.NUMBER, 5, 0, 99999);
                config.addParam("curseTier", "Stun Tier", ParamType.NUMBER, 5, 0, 99999);
            }

            // ============ ITEM MANIPULATION ============
            case "REMOVE_RANDOM_ENCHANT" -> {
                // Uses getValue() for number of enchants to remove
                config.addParam("value", "Enchants to Remove", ParamType.NUMBER, 1, 0, 99999);
            }

            // ============ DISARM/STEAL ============
            case "DISARM" -> {
                // Force target to drop held item
                config.addParam("range", "Max Range", ParamType.NUMBER, 10, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Nearby:5");
            }
            case "STEAL_BUFFS" -> {
                // Steal positive potion effects
                config.addParam("range", "Max Range", ParamType.NUMBER, 10, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim", "@Nearby:5");
            }
            case "CLEAR_NEGATIVE_EFFECTS" -> {
                // Clear debuffs from target
                config.addCycleParam("target", "Target", "@Self", "@Self", "@Victim", "@Nearby:5");
            }
            case "DROP_HEAD" -> {
                // Drop target's head
                config.addCycleParam("target", "Target", "@Victim", "@Victim");
            }
            case "REPAIR_ARMOR" -> {
                // Repair equipped armor
                config.addParam("value", "% Durability Restored", ParamType.NUMBER, 100, 0, 99999);
                config.addCycleParam("target", "Target", "@Self", "@Self", "@Victim");
            }
            case "DECREASE_SIGIL_TIER" -> {
                // Decrease sigil tier
                config.addParam("value", "Tiers to Decrease", ParamType.NUMBER, 1, 0, 99999);
                config.addCycleParam("target", "Target", "@Victim", "@Victim");
            }

            // ============ EFFECTS WITH NO PARAMETERS ============
            case "SOULBOUND", "CANCEL_EVENT" -> {
                // Truly no parameters needed
            }

            // ============ NEW EFFECTS (Flow System Foundation) ============
            case "SPAWN_DISPLAY" -> {
                // Always visible - core params
                config.addCycleParam("type", "Display Type", "BLOCK", "BLOCK", "ITEM", "TEXT");
                config.addParam("value", "Block/Item/Text", ParamType.TEXT, "RED_SAND");
                config.addParam("duration", "Duration (sec)", ParamType.NUMBER, 10, 1, 300);
                config.addCycleParam("shape", "Shape", "point", "point", "circle", "spiral", "helix", "sphere", "line", "beam", "cone", "grid", "ring");
                config.addCycleParam("target", "Location", "@Self", "@Self", "@Victim");

                // Shape params - only when shape != point
                config.addParamWhen("radius", "Radius", ParamType.NUMBER, 3, 0.1, 20, "shape!=point");
                config.addParamWhen("spacing", "Point Spacing", ParamType.NUMBER, 0.5, 0.1, 5, "shape!=point");
                config.addParamWhen("rotate_speed", "Rotate Speed", ParamType.NUMBER, 0.0, 0.0, 10, "shape!=point");

                // Spiral/helix specific
                config.addParamWhen("height", "Shape Height", ParamType.NUMBER, 3, 0.1, 20, "shape=spiral,helix,cone");
                config.addParamWhen("turns", "Spiral Turns", ParamType.NUMBER, 1.0, 0.5, 10, "shape=spiral,helix");

                // Transform - always useful
                config.addParam("scale", "Scale (uniform)", ParamType.NUMBER, 1.0, 0.1, 10);
                config.addParam("scale_x", "Scale X", ParamType.NUMBER, 1.0, 0.01, 10);
                config.addParam("scale_y", "Scale Y", ParamType.NUMBER, 1.0, 0.01, 10);
                config.addParam("scale_z", "Scale Z", ParamType.NUMBER, 1.0, 0.01, 10);
                config.addParam("offset_y", "Y Offset", ParamType.NUMBER, 0.0, -5, 10);

                // Animation
                config.addCycleParam("follow_owner", "Follow Player", "false", "false", "true");
                config.addCycleParam("glow", "Glowing", "false", "false", "true");
                config.addCycleParam("billboard", "Billboard", "FIXED", "FIXED", "VERTICAL", "HORIZONTAL", "CENTER", "OUTWARD");

                // Grid-specific ground snapping
                config.addCycleParamWhen("snap_to_ground", "Snap to Ground", "false", "shape=grid", "false", "true");
                config.addParamWhen("ground_offset", "Ground Offset", ParamType.NUMBER, 0.0, -1.0, 1.0, "shape=grid");

                // Behavior (for entity interactions like quicksand)
                config.addParam("behavior", "Behavior (optional)", ParamType.TEXT, "");
            }
            case "GIVE_ITEM" -> {
                // GIVE_ITEM:MATERIAL:AMOUNT @Target or GIVE_ITEM:CUSTOM:id:amount @Target
                config.addParam("material", "Item/Material", ParamType.TEXT, "DIAMOND");
                config.addParam("amount", "Amount", ParamType.NUMBER, 1, 0, 99999);
                config.addParam("random_max", "Random Max (0=fixed)", ParamType.NUMBER, 0, 0, 99999);
                config.addCycleParam("target", "Target", "@Self", "@Self", "@Victim");
            }

            case "DAMAGE_REDUCTION_BUFF" -> {
                // Flat damage reduction (not potion resistance) for X seconds
                config.addParam("duration", "Duration (seconds)", ParamType.NUMBER, 5, 0, 60);
                config.addParam("percent", "% Damage Reduction", ParamType.NUMBER, 25, 0, 80);
                config.addCycleParam("target", "Target", "@Self", "@Self", "@Victim");
            }

            default -> {
                // Generic value parameter for unrecognized effects
                config.addParam("value", "Value", ParamType.NUMBER, 10, 0, 99999);
            }
        }

        return config;
    }

    /**
     * Parameter type enum.
     * Public so NodeConfigHandler can use it for Flow Builder effect nodes.
     */
    public enum ParamType {
        NUMBER,
        TEXT,
        TARGET,
        PARTICLE_BROWSER,
        SOUND_BROWSER,
        ATTRIBUTE_BROWSER,
        CYCLE  // Cycles through predefined options
    }

    /**
     * Parameter info class.
     * Public so NodeConfigHandler can use it for Flow Builder effect nodes.
     */
    public static class ParamInfo {
        public final String key;
        public final String displayName;
        public final ParamType type;
        public final Object defaultValue;
        public final double min;
        public final double max;
        public final String[] cycleOptions;
        public final String showWhen; // Condition: "key=value", "key!=value", "key=v1,v2,v3"

        ParamInfo(String key, String displayName, ParamType type, Object defaultValue, double min, double max) {
            this(key, displayName, type, defaultValue, min, max, null, null);
        }

        ParamInfo(String key, String displayName, ParamType type, Object defaultValue, double min, double max, String[] cycleOptions) {
            this(key, displayName, type, defaultValue, min, max, cycleOptions, null);
        }

        ParamInfo(String key, String displayName, ParamType type, Object defaultValue, double min, double max, String[] cycleOptions, String showWhen) {
            this.key = key;
            this.displayName = displayName;
            this.type = type;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
            this.cycleOptions = cycleOptions;
            this.showWhen = showWhen;
        }

        /**
         * Check if this param should be visible based on current param values.
         */
        public boolean isVisible(Map<String, Object> currentParams) {
            if (showWhen == null || showWhen.isEmpty()) return true;

            // Parse condition: "key=value" or "key!=value" or "key=v1,v2,v3"
            boolean negated = showWhen.contains("!=");
            String[] parts = showWhen.split(negated ? "!=" : "=", 2);
            if (parts.length != 2) return true;

            String condKey = parts[0].trim();
            String condValues = parts[1].trim();
            Object currentValue = currentParams.get(condKey);
            String currentStr = currentValue != null ? currentValue.toString().toLowerCase() : "";

            // Check if current value matches any of the allowed values
            String[] allowedValues = condValues.split(",");
            boolean matches = false;
            for (String allowed : allowedValues) {
                if (currentStr.equalsIgnoreCase(allowed.trim())) {
                    matches = true;
                    break;
                }
            }

            return negated ? !matches : matches;
        }
    }

    /**
     * Effect parameter configuration class.
     * Public so NodeConfigHandler can use it for Flow Builder effect nodes.
     */
    public static class EffectParamConfig {
        public final List<ParamInfo> params = new ArrayList<>();

        void addParam(String key, String displayName, ParamType type, Object defaultValue) {
            addParam(key, displayName, type, defaultValue, 0, 100);
        }

        void addParam(String key, String displayName, ParamType type, Object defaultValue, double min, double max) {
            params.add(new ParamInfo(key, displayName, type, defaultValue, min, max, null, null));
        }

        void addCycleParam(String key, String displayName, String defaultValue, String... options) {
            params.add(new ParamInfo(key, displayName, ParamType.CYCLE, defaultValue, 0, 0, options, null));
        }

        // Conditional versions - only show when condition is met
        void addParamWhen(String key, String displayName, ParamType type, Object defaultValue, double min, double max, String showWhen) {
            params.add(new ParamInfo(key, displayName, type, defaultValue, min, max, null, showWhen));
        }

        void addCycleParamWhen(String key, String displayName, String defaultValue, String showWhen, String... options) {
            params.add(new ParamInfo(key, displayName, ParamType.CYCLE, defaultValue, 0, 0, options, showWhen));
        }
    }
}
