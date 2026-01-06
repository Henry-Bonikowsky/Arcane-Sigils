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
 * Browser for selecting particle types.
 * Special particles (DUST, ITEM, BLOCK) open sub-selectors.
 */
public class ParticleSelectorHandler extends AbstractBrowserHandler<ParticleSelectorHandler.ParticleOption> {

    private static final List<ParticleOption> PARTICLE_OPTIONS = buildParticleOptions();

    public ParticleSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleBack(Player player, GUISession session) {
        playSound(player, "click");
        Map<String, Object> params = (Map<String, Object>) session.get("params");
        if (params == null) {
            params = new java.util.HashMap<>();
        }
        // Pass null for particleName since we're just going back without selecting
        returnToEffectParam(player, session, params, null);
    }

    @Override
    protected void handleItemSelection(Player player, GUISession session, int itemIndex, int page) {
        int actualIndex = getActualIndex(itemIndex, page);
        if (actualIndex >= PARTICLE_OPTIONS.size()) {
            playSound(player, "click");
            return;
        }

        ParticleOption option = PARTICLE_OPTIONS.get(actualIndex);
        playSound(player, "click");

        // Check if this particle needs a sub-selector
        if (option.needsColorSelector) {
            ColorSelectorHandler.openGUI(guiManager, player, session);
        } else if (option.needsItemSelector) {
            ItemParticleSelectorHandler.openGUI(guiManager, player, session);
        } else if (option.needsBlockSelector) {
            BlockParticleSelectorHandler.openGUI(guiManager, player, session);
        } else {
            applyParticleSelection(player, session, option.particleName);
        }
    }

    @Override
    protected List<ParticleOption> getItems() {
        return PARTICLE_OPTIONS;
    }

    @Override
    protected ItemStack buildItemDisplay(ParticleOption option) {
        List<String> lore = new ArrayList<>();
        if (option.description != null) {
            lore.add("§7" + option.description);
            lore.add("");
        }
        if (option.needsColorSelector) {
            lore.add("§eClick to select color");
        } else if (option.needsItemSelector) {
            lore.add("§eClick to select item");
        } else if (option.needsBlockSelector) {
            lore.add("§eClick to select block");
        } else {
            lore.add("§7Click to select");
        }
        return ItemBuilder.createItem(option.displayMaterial, "§e" + option.displayName, lore);
    }

    @Override
    protected String getTitle() {
        return "§8Select Particle Type";
    }

    @Override
    protected GUIType getGUIType() {
        return GUIType.PARTICLE_SELECTOR;
    }

    @Override
    protected String getBackButtonLabel() {
        return "Effect Config";
    }

    @SuppressWarnings("unchecked")
    private void applyParticleSelection(Player player, GUISession session, String particleName) {
        Map<String, Object> params = (Map<String, Object>) session.get("params");
        if (params == null) {
            params = new java.util.HashMap<>();
        }
        // Use correct key "particle_type" to match EffectParamHandler config
        params.put("particle_type", particleName);
        session.put("params", params);

        player.sendMessage(TextUtil.colorize("§aParticle type set to: §f" + particleName));
        returnToEffectParam(player, session, params, particleName);
    }

    @SuppressWarnings("unchecked")
    private void returnToEffectParam(Player player, GUISession session, Map<String, Object> params, String particleName) {
        // Check if opened from Flow Builder (has configNode)
        Object configNode = session.get("configNode");
        if (configNode != null) {
            // Apply to Flow Builder node and return to Flow Builder
            returnToFlowBuilder(player, session, particleName);
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
     * Return to Flow Builder after particle selection.
     */
    private void returnToFlowBuilder(Player player, GUISession session, String particleName) {
        try {
            // Get the node and apply the particle selection (if a particle was selected)
            Object configNode = session.get("configNode");
            String paramKey = session.get("paramKey", String.class);
            if (paramKey == null) paramKey = "particle_type";

            // Only set param if particleName is not null (i.e., a particle was actually selected)
            if (configNode != null && particleName != null) {
                java.lang.reflect.Method setParam = configNode.getClass().getMethod("setParam", String.class, Object.class);
                setParam.invoke(configNode, paramKey, particleName);
            }

            // Return to Flow Builder
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
     * Static method to open the GUI (convenience for other handlers).
     */
    public static void openGUI(GUIManager guiManager, Player player, GUISession session) {
        openGUI(guiManager, player, session, 1);
    }

    public static void openGUI(GUIManager guiManager, Player player, GUISession session, int page) {
        ArmorSetsPlugin plugin = (ArmorSetsPlugin) guiManager.getInputHelper().getPlugin();
        ParticleSelectorHandler handler = new ParticleSelectorHandler(plugin, guiManager);
        handler.openGUI(player, session, page);
    }

    private static List<ParticleOption> buildParticleOptions() {
        List<ParticleOption> options = new ArrayList<>();

        // Special particles first (need sub-selectors)
        options.add(new ParticleOption("DUST", "Dust (Colored)", Material.RED_DYE,
            "Colored dust particle", true, false, false));
        options.add(new ParticleOption("ITEM", "Item Crack", Material.DIAMOND,
            "Item breaking particles", false, true, false));
        options.add(new ParticleOption("BLOCK", "Block Crack", Material.STONE,
            "Block breaking particles", false, false, true));

        // Common particles
        options.add(new ParticleOption("FLAME", "Flame", Material.BLAZE_POWDER, "Fire particles"));
        options.add(new ParticleOption("SOUL_FIRE_FLAME", "Soul Flame", Material.SOUL_TORCH, "Blue fire particles"));
        options.add(new ParticleOption("SMOKE", "Smoke", Material.COAL, "Smoke particles"));
        options.add(new ParticleOption("LARGE_SMOKE", "Large Smoke", Material.COAL_BLOCK, "Large smoke particles"));
        options.add(new ParticleOption("CLOUD", "Cloud", Material.WHITE_WOOL, "Cloud particles"));
        options.add(new ParticleOption("EXPLOSION", "Explosion", Material.TNT, "Explosion particles"));
        options.add(new ParticleOption("FIREWORK", "Firework", Material.FIREWORK_ROCKET, "Firework sparkles"));
        options.add(new ParticleOption("HEART", "Heart", Material.BEETROOT, "Heart particles"));
        options.add(new ParticleOption("HAPPY_VILLAGER", "Happy Villager", Material.EMERALD, "Green sparkles"));
        options.add(new ParticleOption("ANGRY_VILLAGER", "Angry Villager", Material.REDSTONE, "Angry particles"));
        options.add(new ParticleOption("CRIT", "Critical Hit", Material.IRON_SWORD, "Critical hit stars"));
        options.add(new ParticleOption("ENCHANTED_HIT", "Enchanted Hit", Material.ENCHANTED_BOOK, "Magic crit particles"));
        options.add(new ParticleOption("WITCH", "Witch", Material.BREWING_STAND, "Purple magic particles"));
        options.add(new ParticleOption("PORTAL", "Portal", Material.OBSIDIAN, "Portal particles"));
        options.add(new ParticleOption("END_ROD", "End Rod", Material.END_ROD, "Floating particles"));
        options.add(new ParticleOption("DRAGON_BREATH", "Dragon Breath", Material.DRAGON_BREATH, "Purple dragon particles"));
        options.add(new ParticleOption("TOTEM_OF_UNDYING", "Totem", Material.TOTEM_OF_UNDYING, "Totem resurrection effect"));
        options.add(new ParticleOption("ENCHANT", "Enchantment", Material.ENCHANTING_TABLE, "Enchanting glyphs"));
        options.add(new ParticleOption("EFFECT", "Spell", Material.POTION, "Potion spell particles"));
        options.add(new ParticleOption("INSTANT_EFFECT", "Instant Spell", Material.SPLASH_POTION, "Instant effect particles"));
        options.add(new ParticleOption("ENTITY_EFFECT", "Mob Spell", Material.SPIDER_EYE, "Mob effect particles"));
        options.add(new ParticleOption("NOTE", "Note", Material.NOTE_BLOCK, "Musical notes"));
        options.add(new ParticleOption("ELECTRIC_SPARK", "Electric Spark", Material.LIGHTNING_ROD, "Electric sparks"));
        options.add(new ParticleOption("SNOWFLAKE", "Snowflake", Material.SNOWBALL, "Snow particles"));
        options.add(new ParticleOption("DRIPPING_WATER", "Dripping Water", Material.WATER_BUCKET, "Water drips"));
        options.add(new ParticleOption("DRIPPING_LAVA", "Dripping Lava", Material.LAVA_BUCKET, "Lava drips"));
        options.add(new ParticleOption("DRIPPING_HONEY", "Dripping Honey", Material.HONEY_BOTTLE, "Honey drips"));
        options.add(new ParticleOption("WAX_ON", "Wax On", Material.HONEYCOMB, "Wax particles"));
        options.add(new ParticleOption("WAX_OFF", "Wax Off", Material.HONEYCOMB, "Wax removal particles"));
        options.add(new ParticleOption("GLOW", "Glow", Material.GLOW_INK_SAC, "Glowing particles"));
        options.add(new ParticleOption("SCULK_SOUL", "Sculk Soul", Material.SCULK, "Sculk soul particles"));
        options.add(new ParticleOption("SONIC_BOOM", "Sonic Boom", Material.SCULK_SHRIEKER, "Warden attack"));
        options.add(new ParticleOption("CHERRY_LEAVES", "Cherry Leaves", Material.CHERRY_LEAVES, "Falling cherry blossoms"));
        options.add(new ParticleOption("TRIAL_SPAWNER_DETECTION", "Trial Detection", Material.TRIAL_SPAWNER, "Trial spawner particles"));

        return options;
    }

    /**
     * Particle option data class.
     */
    public static class ParticleOption {
        public final String particleName;
        public final String displayName;
        public final Material displayMaterial;
        public final String description;
        public final boolean needsColorSelector;
        public final boolean needsItemSelector;
        public final boolean needsBlockSelector;

        public ParticleOption(String particleName, String displayName, Material displayMaterial, String description) {
            this(particleName, displayName, displayMaterial, description, false, false, false);
        }

        public ParticleOption(String particleName, String displayName, Material displayMaterial, String description,
                      boolean needsColorSelector, boolean needsItemSelector, boolean needsBlockSelector) {
            this.particleName = particleName;
            this.displayName = displayName;
            this.displayMaterial = displayMaterial;
            this.description = description;
            this.needsColorSelector = needsColorSelector;
            this.needsItemSelector = needsItemSelector;
            this.needsBlockSelector = needsBlockSelector;
        }
    }
}
