package com.miracle.arcanesigils.gui.effect;

import com.miracle.arcanesigils.ArmorSetsPlugin;
import com.miracle.arcanesigils.core.Sigil;
import com.miracle.arcanesigils.gui.GUIManager;
import com.miracle.arcanesigils.gui.GUISession;
import com.miracle.arcanesigils.gui.GUIType;
import com.miracle.arcanesigils.gui.common.AbstractBrowserHandler;
import com.miracle.arcanesigils.gui.common.ItemBuilder;
import com.miracle.arcanesigils.utils.TextUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Browser for selecting colors for DUST particles.
 * Displays preset colors using dyes and wool.
 */
public class ColorSelectorHandler extends AbstractBrowserHandler<ColorSelectorHandler.ColorOption> {

    private static final List<ColorOption> COLOR_OPTIONS = buildColorOptions();

    public ColorSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    protected void handleBack(Player player, GUISession session) {
        playSound(player, "click");
        // Return to particle selector
        ParticleSelectorHandler.openGUI(guiManager, player, session);
    }

    @Override
    protected void handleItemSelection(Player player, GUISession session, int itemIndex, int page) {
        int actualIndex = getActualIndex(itemIndex, page);
        if (actualIndex >= COLOR_OPTIONS.size()) {
            playSound(player, "click");
            return;
        }

        ColorOption option = COLOR_OPTIONS.get(actualIndex);
        playSound(player, "click");
        applyColorSelection(player, session, option);
    }

    @Override
    protected List<ColorOption> getItems() {
        return COLOR_OPTIONS;
    }

    @Override
    protected ItemStack buildItemDisplay(ColorOption option) {
        List<String> lore = new ArrayList<>();
        lore.add("§7RGB: §f" + option.red + ", " + option.green + ", " + option.blue);
        lore.add("");
        lore.add("§7Click to select");
        return ItemBuilder.createItem(option.displayMaterial, option.colorCode + option.displayName, lore);
    }

    @Override
    protected String getTitle() {
        return "§8Select Dust Color";
    }

    @Override
    protected GUIType getGUIType() {
        return GUIType.COLOR_SELECTOR;
    }

    @Override
    protected String getBackButtonLabel() {
        return "Particle Types";
    }

    @SuppressWarnings("unchecked")
    private void applyColorSelection(Player player, GUISession session, ColorOption option) {
        Map<String, Object> params = (Map<String, Object>) session.get("params");
        if (params == null) {
            params = new java.util.HashMap<>();
        }
        // Store as DUST with color info: DUST:R:G:B - use correct key "particle_type"
        params.put("particle_type", "DUST:" + option.red + ":" + option.green + ":" + option.blue);
        session.put("params", params);

        player.sendMessage(TextUtil.colorize("§aParticle set to: " + option.colorCode + "Dust (" + option.displayName + ")"));
        returnToEffectParam(player, session, params);
    }

    @SuppressWarnings("unchecked")
    private void returnToEffectParam(Player player, GUISession session, Map<String, Object> params) {
        // Check if opened from Flow Builder (has configNode)
        Object configNode = session.get("configNode");
        if (configNode != null) {
            // Apply to Flow Builder node and return to Flow Builder
            returnToFlowBuilder(player, session, (String) params.get("particle_type"));
            return;
        }

        // Original EffectParamHandler return path
        Sigil sigil = session.get("sigil", Sigil.class);
        String signalKey = session.get("signalKey", String.class);
        String effectType = session.get("effectType", String.class);
        Integer effectIndex = session.get("effectIndex", Integer.class);
        if (sigil != null && signalKey != null && effectType != null) {
            // Use openGUIWithParams to preserve the updated params
            EffectParamHandler.openGUIWithParams(guiManager, player, sigil, signalKey,
                effectIndex != null ? effectIndex : -1, effectType, params);
        } else {
            player.closeInventory();
        }
    }

    /**
     * Return to Flow Builder after color selection.
     */
    private void returnToFlowBuilder(Player player, GUISession session, String particleName) {
        try {
            Object configNode = session.get("configNode");
            String paramKey = session.get("paramKey", String.class);
            if (paramKey == null) paramKey = "particle_type";

            if (configNode != null && particleName != null) {
                java.lang.reflect.Method setParam = configNode.getClass().getMethod("setParam", String.class, Object.class);
                setParam.invoke(configNode, paramKey, particleName);
            }

            Sigil sigil = session.get("sigil", Sigil.class);
            String signalKey = session.get("signalKey", String.class);
            Object flow = session.get("flow");

            if (sigil != null && signalKey != null) {
                com.miracle.arcanesigils.gui.flow.FlowBuilderHandler.openGUI(
                    guiManager, player, sigil, signalKey,
                    (com.miracle.arcanesigils.flow.FlowGraph) flow, session);
            } else {
                player.closeInventory();
            }
        } catch (Exception e) {
            player.sendMessage(TextUtil.colorize("§cError returning to flow builder"));
            player.closeInventory();
        }
    }

    /**
     * Static method to open the GUI.
     */
    public static void openGUI(GUIManager guiManager, Player player, GUISession session) {
        ArmorSetsPlugin plugin = (ArmorSetsPlugin) guiManager.getInputHelper().getPlugin();
        ColorSelectorHandler handler = new ColorSelectorHandler(plugin, guiManager);
        handler.openGUI(player, session, 1);
    }

    private static List<ColorOption> buildColorOptions() {
        List<ColorOption> options = new ArrayList<>();

        // Basic colors
        options.add(new ColorOption("Red", Material.RED_DYE, "§c", 255, 0, 0));
        options.add(new ColorOption("Orange", Material.ORANGE_DYE, "§6", 255, 165, 0));
        options.add(new ColorOption("Yellow", Material.YELLOW_DYE, "§e", 255, 255, 0));
        options.add(new ColorOption("Lime", Material.LIME_DYE, "§a", 0, 255, 0));
        options.add(new ColorOption("Green", Material.GREEN_DYE, "§2", 0, 128, 0));
        options.add(new ColorOption("Cyan", Material.CYAN_DYE, "§3", 0, 255, 255));
        options.add(new ColorOption("Light Blue", Material.LIGHT_BLUE_DYE, "§b", 173, 216, 230));
        options.add(new ColorOption("Blue", Material.BLUE_DYE, "§9", 0, 0, 255));
        options.add(new ColorOption("Purple", Material.PURPLE_DYE, "§5", 128, 0, 128));
        options.add(new ColorOption("Magenta", Material.MAGENTA_DYE, "§d", 255, 0, 255));
        options.add(new ColorOption("Pink", Material.PINK_DYE, "§d", 255, 192, 203));
        options.add(new ColorOption("White", Material.WHITE_DYE, "§f", 255, 255, 255));
        options.add(new ColorOption("Light Gray", Material.LIGHT_GRAY_DYE, "§7", 192, 192, 192));
        options.add(new ColorOption("Gray", Material.GRAY_DYE, "§8", 128, 128, 128));
        options.add(new ColorOption("Black", Material.BLACK_DYE, "§0", 0, 0, 0));
        options.add(new ColorOption("Brown", Material.BROWN_DYE, "§6", 139, 69, 19));

        // Special colors
        options.add(new ColorOption("Gold", Material.GOLD_INGOT, "§6", 255, 215, 0));
        options.add(new ColorOption("Silver", Material.IRON_INGOT, "§7", 192, 192, 192));
        options.add(new ColorOption("Aqua", Material.PRISMARINE_CRYSTALS, "§b", 0, 255, 255));
        options.add(new ColorOption("Coral", Material.FIRE_CORAL, "§c", 255, 127, 80));
        options.add(new ColorOption("Crimson", Material.CRIMSON_FUNGUS, "§4", 220, 20, 60));
        options.add(new ColorOption("Indigo", Material.LAPIS_LAZULI, "§9", 75, 0, 130));
        options.add(new ColorOption("Violet", Material.AMETHYST_SHARD, "§5", 238, 130, 238));
        options.add(new ColorOption("Turquoise", Material.DIAMOND, "§b", 64, 224, 208));
        options.add(new ColorOption("Olive", Material.MOSS_BLOCK, "§2", 128, 128, 0));
        options.add(new ColorOption("Navy", Material.BLUE_WOOL, "§1", 0, 0, 128));
        options.add(new ColorOption("Teal", Material.WARPED_FUNGUS, "§3", 0, 128, 128));
        options.add(new ColorOption("Maroon", Material.NETHER_WART, "§4", 128, 0, 0));

        return options;
    }

    /**
     * Color option data class.
     */
    public static class ColorOption {
        public final String displayName;
        public final Material displayMaterial;
        public final String colorCode;
        public final int red;
        public final int green;
        public final int blue;

        public ColorOption(String displayName, Material displayMaterial, String colorCode, int red, int green, int blue) {
            this.displayName = displayName;
            this.displayMaterial = displayMaterial;
            this.colorCode = colorCode;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }
}
