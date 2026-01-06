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
 * Browser for selecting blocks for BLOCK (crack) particles.
 * Shows common blocks that make good particle effects.
 */
public class BlockParticleSelectorHandler extends AbstractBrowserHandler<Material> {

    private static final List<Material> BLOCK_OPTIONS = buildBlockOptions();

    public BlockParticleSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    protected void handleBack(Player player, GUISession session) {
        playSound(player, "click");
        ParticleSelectorHandler.openGUI(guiManager, player, session);
    }

    @Override
    protected void handleItemSelection(Player player, GUISession session, int itemIndex, int page) {
        int actualIndex = getActualIndex(itemIndex, page);
        if (actualIndex >= BLOCK_OPTIONS.size()) {
            playSound(player, "click");
            return;
        }

        Material material = BLOCK_OPTIONS.get(actualIndex);
        playSound(player, "click");
        applyBlockSelection(player, session, material);
    }

    @Override
    protected List<Material> getItems() {
        return BLOCK_OPTIONS;
    }

    @Override
    protected ItemStack buildItemDisplay(Material material) {
        String name = TextUtil.toProperCase(material.name().replace("_", " "));
        List<String> lore = new ArrayList<>();
        lore.add("§7Block: §f" + name);
        lore.add("");
        lore.add("§7Click to select");
        return ItemBuilder.createItem(material, "§e" + name, lore);
    }

    @Override
    protected String getTitle() {
        return "§8Select Block for Particle";
    }

    @Override
    protected GUIType getGUIType() {
        return GUIType.BLOCK_PARTICLE_SELECTOR;
    }

    @Override
    protected String getBackButtonLabel() {
        return "Particle Types";
    }

    @SuppressWarnings("unchecked")
    private void applyBlockSelection(Player player, GUISession session, Material material) {
        Map<String, Object> params = (Map<String, Object>) session.get("params");
        if (params == null) {
            params = new java.util.HashMap<>();
        }
        // Store as BLOCK with material: BLOCK:MATERIAL_NAME - use correct key "particle_type"
        params.put("particle_type", "BLOCK:" + material.name());
        session.put("params", params);

        String name = TextUtil.toProperCase(material.name().replace("_", " "));
        player.sendMessage(TextUtil.colorize("§aParticle set to: §fBlock Crack (" + name + ")"));
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
     * Return to Flow Builder after block selection.
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
        BlockParticleSelectorHandler handler = new BlockParticleSelectorHandler(plugin, guiManager);
        handler.openGUI(player, session, 1);
    }

    private static List<Material> buildBlockOptions() {
        List<Material> options = new ArrayList<>();

        // Stone variants
        options.add(Material.STONE);
        options.add(Material.COBBLESTONE);
        options.add(Material.MOSSY_COBBLESTONE);
        options.add(Material.GRANITE);
        options.add(Material.DIORITE);
        options.add(Material.ANDESITE);
        options.add(Material.DEEPSLATE);
        options.add(Material.TUFF);
        options.add(Material.CALCITE);

        // Ores
        options.add(Material.COAL_ORE);
        options.add(Material.IRON_ORE);
        options.add(Material.COPPER_ORE);
        options.add(Material.GOLD_ORE);
        options.add(Material.REDSTONE_ORE);
        options.add(Material.LAPIS_ORE);
        options.add(Material.DIAMOND_ORE);
        options.add(Material.EMERALD_ORE);
        options.add(Material.ANCIENT_DEBRIS);

        // Metal blocks
        options.add(Material.IRON_BLOCK);
        options.add(Material.GOLD_BLOCK);
        options.add(Material.DIAMOND_BLOCK);
        options.add(Material.EMERALD_BLOCK);
        options.add(Material.NETHERITE_BLOCK);
        options.add(Material.COPPER_BLOCK);
        options.add(Material.AMETHYST_BLOCK);

        // Earth blocks
        options.add(Material.DIRT);
        options.add(Material.GRASS_BLOCK);
        options.add(Material.SAND);
        options.add(Material.RED_SAND);
        options.add(Material.GRAVEL);
        options.add(Material.CLAY);
        options.add(Material.MUD);
        options.add(Material.SOUL_SAND);
        options.add(Material.SOUL_SOIL);
        options.add(Material.MYCELIUM);

        // Wood types
        options.add(Material.OAK_LOG);
        options.add(Material.BIRCH_LOG);
        options.add(Material.SPRUCE_LOG);
        options.add(Material.DARK_OAK_LOG);
        options.add(Material.ACACIA_LOG);
        options.add(Material.JUNGLE_LOG);
        options.add(Material.CHERRY_LOG);
        options.add(Material.CRIMSON_STEM);
        options.add(Material.WARPED_STEM);

        // Leaves
        options.add(Material.OAK_LEAVES);
        options.add(Material.BIRCH_LEAVES);
        options.add(Material.CHERRY_LEAVES);
        options.add(Material.AZALEA_LEAVES);

        // Nether blocks
        options.add(Material.NETHERRACK);
        options.add(Material.NETHER_BRICKS);
        options.add(Material.BASALT);
        options.add(Material.BLACKSTONE);
        options.add(Material.MAGMA_BLOCK);
        options.add(Material.GLOWSTONE);
        options.add(Material.SHROOMLIGHT);

        // End blocks
        options.add(Material.END_STONE);
        options.add(Material.PURPUR_BLOCK);
        options.add(Material.OBSIDIAN);
        options.add(Material.CRYING_OBSIDIAN);

        // Ice and snow
        options.add(Material.ICE);
        options.add(Material.PACKED_ICE);
        options.add(Material.BLUE_ICE);
        options.add(Material.SNOW_BLOCK);
        options.add(Material.POWDER_SNOW);

        // Colorful blocks
        options.add(Material.RED_WOOL);
        options.add(Material.ORANGE_WOOL);
        options.add(Material.YELLOW_WOOL);
        options.add(Material.LIME_WOOL);
        options.add(Material.GREEN_WOOL);
        options.add(Material.CYAN_WOOL);
        options.add(Material.LIGHT_BLUE_WOOL);
        options.add(Material.BLUE_WOOL);
        options.add(Material.PURPLE_WOOL);
        options.add(Material.MAGENTA_WOOL);
        options.add(Material.PINK_WOOL);
        options.add(Material.WHITE_WOOL);
        options.add(Material.BLACK_WOOL);

        // Glass
        options.add(Material.GLASS);
        options.add(Material.RED_STAINED_GLASS);
        options.add(Material.BLUE_STAINED_GLASS);
        options.add(Material.GREEN_STAINED_GLASS);
        options.add(Material.YELLOW_STAINED_GLASS);
        options.add(Material.PURPLE_STAINED_GLASS);
        options.add(Material.TINTED_GLASS);

        // Special blocks
        options.add(Material.PRISMARINE);
        options.add(Material.SEA_LANTERN);
        options.add(Material.SCULK);
        options.add(Material.HONEY_BLOCK);
        options.add(Material.SLIME_BLOCK);
        options.add(Material.SPONGE);
        options.add(Material.MOSS_BLOCK);
        options.add(Material.DRIPSTONE_BLOCK);

        return options;
    }
}
