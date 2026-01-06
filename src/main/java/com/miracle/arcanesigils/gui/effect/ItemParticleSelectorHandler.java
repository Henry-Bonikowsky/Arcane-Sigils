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
 * Browser for selecting items for ITEM (crack) particles.
 * Shows common items that make good particle effects.
 */
public class ItemParticleSelectorHandler extends AbstractBrowserHandler<Material> {

    private static final List<Material> ITEM_OPTIONS = buildItemOptions();

    public ItemParticleSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
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
        if (actualIndex >= ITEM_OPTIONS.size()) {
            playSound(player, "click");
            return;
        }

        Material material = ITEM_OPTIONS.get(actualIndex);
        playSound(player, "click");
        applyItemSelection(player, session, material);
    }

    @Override
    protected List<Material> getItems() {
        return ITEM_OPTIONS;
    }

    @Override
    protected ItemStack buildItemDisplay(Material material) {
        String name = TextUtil.toProperCase(material.name().replace("_", " "));
        List<String> lore = new ArrayList<>();
        lore.add("§7Item: §f" + name);
        lore.add("");
        lore.add("§7Click to select");
        return ItemBuilder.createItem(material, "§e" + name, lore);
    }

    @Override
    protected String getTitle() {
        return "§8Select Item for Particle";
    }

    @Override
    protected GUIType getGUIType() {
        return GUIType.ITEM_PARTICLE_SELECTOR;
    }

    @Override
    protected String getBackButtonLabel() {
        return "Particle Types";
    }

    @SuppressWarnings("unchecked")
    private void applyItemSelection(Player player, GUISession session, Material material) {
        Map<String, Object> params = (Map<String, Object>) session.get("params");
        if (params == null) {
            params = new java.util.HashMap<>();
        }
        // Store as ITEM with material: ITEM:MATERIAL_NAME - use correct key "particle_type"
        params.put("particle_type", "ITEM:" + material.name());
        session.put("params", params);

        String name = TextUtil.toProperCase(material.name().replace("_", " "));
        player.sendMessage(TextUtil.colorize("§aParticle set to: §fItem Crack (" + name + ")"));
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
     * Return to Flow Builder after item selection.
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
        ItemParticleSelectorHandler handler = new ItemParticleSelectorHandler(plugin, guiManager);
        handler.openGUI(player, session, 1);
    }

    private static List<Material> buildItemOptions() {
        List<Material> options = new ArrayList<>();

        // Gems and valuables
        options.add(Material.DIAMOND);
        options.add(Material.EMERALD);
        options.add(Material.AMETHYST_SHARD);
        options.add(Material.LAPIS_LAZULI);
        options.add(Material.PRISMARINE_CRYSTALS);
        options.add(Material.ECHO_SHARD);
        options.add(Material.NETHER_STAR);
        options.add(Material.GOLD_NUGGET);
        options.add(Material.IRON_NUGGET);
        options.add(Material.COPPER_INGOT);

        // Food items
        options.add(Material.APPLE);
        options.add(Material.GOLDEN_APPLE);
        options.add(Material.ENCHANTED_GOLDEN_APPLE);
        options.add(Material.BREAD);
        options.add(Material.COOKIE);
        options.add(Material.MELON_SLICE);
        options.add(Material.SWEET_BERRIES);
        options.add(Material.GLOW_BERRIES);
        options.add(Material.CHORUS_FRUIT);

        // Combat items
        options.add(Material.ARROW);
        options.add(Material.SPECTRAL_ARROW);
        options.add(Material.FIRE_CHARGE);
        options.add(Material.BLAZE_POWDER);
        options.add(Material.GUNPOWDER);

        // Potions and brewing
        options.add(Material.GLASS_BOTTLE);
        options.add(Material.GHAST_TEAR);
        options.add(Material.GLOWSTONE_DUST);
        options.add(Material.REDSTONE);
        options.add(Material.SUGAR);
        options.add(Material.SPIDER_EYE);
        options.add(Material.FERMENTED_SPIDER_EYE);
        options.add(Material.MAGMA_CREAM);
        options.add(Material.RABBIT_FOOT);

        // Nature items
        options.add(Material.BONE_MEAL);
        options.add(Material.WHEAT_SEEDS);
        options.add(Material.PUMPKIN_SEEDS);
        options.add(Material.MELON_SEEDS);
        options.add(Material.FEATHER);
        options.add(Material.STRING);
        options.add(Material.SLIME_BALL);
        options.add(Material.HONEYCOMB);
        options.add(Material.INK_SAC);
        options.add(Material.GLOW_INK_SAC);

        // Dyes (colorful particles)
        options.add(Material.RED_DYE);
        options.add(Material.ORANGE_DYE);
        options.add(Material.YELLOW_DYE);
        options.add(Material.LIME_DYE);
        options.add(Material.GREEN_DYE);
        options.add(Material.CYAN_DYE);
        options.add(Material.LIGHT_BLUE_DYE);
        options.add(Material.BLUE_DYE);
        options.add(Material.PURPLE_DYE);
        options.add(Material.MAGENTA_DYE);
        options.add(Material.PINK_DYE);
        options.add(Material.WHITE_DYE);
        options.add(Material.BLACK_DYE);

        // Misc
        options.add(Material.ENDER_PEARL);
        options.add(Material.ENDER_EYE);
        options.add(Material.EXPERIENCE_BOTTLE);
        options.add(Material.SNOWBALL);
        options.add(Material.EGG);
        options.add(Material.PAPER);
        options.add(Material.BOOK);
        options.add(Material.CLOCK);
        options.add(Material.COMPASS);
        options.add(Material.HEART_OF_THE_SEA);
        options.add(Material.NAUTILUS_SHELL);
        options.add(Material.TURTLE_SCUTE);

        return options;
    }
}
