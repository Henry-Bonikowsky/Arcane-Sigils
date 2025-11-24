package com.zenax.armorsets.gui.handlers;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import com.zenax.armorsets.sets.ArmorSet;
import com.zenax.armorsets.sets.SetSynergy;
import com.zenax.armorsets.sets.TriggerConfig;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handler for all configuration/editor GUI types.
 * This is a large handler that consolidates all the build menu, browser,
 * editor, and effect configuration GUIs.
 */
public class ConfigHandler extends AbstractGUIHandler {

    private static final Set<GUIType> SUPPORTED_TYPES = Set.of(
        GUIType.BUILD_MAIN_MENU,
        GUIType.SET_BROWSER,
        GUIType.FUNCTION_BROWSER,
        GUIType.SLOT_SELECTOR,
        GUIType.TRIGGER_SELECTOR,
        GUIType.EFFECT_SELECTOR,
        GUIType.TRIGGER_CONFIG,
        GUIType.CONFIRMATION,
        GUIType.SET_EDITOR,
        GUIType.FUNCTION_EDITOR,
        GUIType.SET_EFFECTS_VIEWER,
        GUIType.SET_SYNERGIES_VIEWER,
        GUIType.TRIGGER_REMOVER,
        GUIType.SYNERGY_CREATOR,
        GUIType.SYNERGY_EDITOR,
        GUIType.SIGIL_CREATOR,
        GUIType.EFFECT_VIEWER,
        GUIType.ITEM_DISPLAY_EDITOR,
        GUIType.GENERIC,
        GUIType.EFFECT_VALUE_CONFIG,
        GUIType.EFFECT_PARTICLE_CONFIG,
        GUIType.EFFECT_SOUND_CONFIG,
        GUIType.EFFECT_POTION_CONFIG,
        GUIType.EFFECT_MESSAGE_CONFIG,
        GUIType.EFFECT_TELEPORT_CONFIG,
        GUIType.CONDITION_CATEGORY_SELECTOR,
        GUIType.CONDITION_TYPE_SELECTOR,
        GUIType.CONDITION_PARAMETER_CONFIG,
        GUIType.CONDITION_VIEWER,
        GUIType.CONDITION_EDITOR,
        GUIType.CONDITION_TEMPLATE_SELECTOR,
        GUIType.CONDITION_PARAMETER_EDITOR,
        GUIType.CONDITION_PRESET_SELECTOR,
        GUIType.CONDITION_PRESET_MANAGER
    );

    public ConfigHandler(ArmorSetsPlugin plugin, GUIHandlerContext context) {
        super(plugin, context);
    }

    @Override
    public Set<GUIType> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        switch (session.getType()) {
            case BUILD_MAIN_MENU -> handleBuildMainMenuClick(player, session, slot, event);
            case SET_BROWSER -> handleSetBrowserClick(player, session, slot, event);
            case FUNCTION_BROWSER -> handleFunctionBrowserClick(player, session, slot, event);
            case SLOT_SELECTOR -> handleSlotSelectorClick(player, session, slot, event);
            case TRIGGER_SELECTOR -> handleTriggerSelectorClick(player, session, slot, event);
            case EFFECT_SELECTOR -> handleEffectSelectorClick(player, session, slot, event);
            case TRIGGER_CONFIG -> handleTriggerConfigClick(player, session, slot, event);
            case CONFIRMATION -> handleConfirmationClick(player, session, slot, event);
            case SET_EDITOR -> handleSetEditorClick(player, session, slot, event);
            case FUNCTION_EDITOR -> handleFunctionEditorClick(player, session, slot, event);
            case SET_EFFECTS_VIEWER -> handleSetEffectsViewerClick(player, session, slot, event);
            case SET_SYNERGIES_VIEWER -> handleSetSynergiesViewerClick(player, session, slot, event);
            case TRIGGER_REMOVER -> handleTriggerRemoverClick(player, session, slot, event);
            case SYNERGY_CREATOR -> handleSynergyCreatorClick(player, session, slot, event);
            case SYNERGY_EDITOR -> handleSynergyEditorClick(player, session, slot, event);
            case SIGIL_CREATOR -> handleSigilCreatorClick(player, session, slot, event);
            case EFFECT_VIEWER -> handleEffectViewerClick(player, session, slot, event);
            case ITEM_DISPLAY_EDITOR -> handleItemDisplayEditorClick(player, session, slot, event);
            case GENERIC -> handleGenericClick(player, session, slot, event);
            case EFFECT_VALUE_CONFIG -> handleEffectValueConfigClick(player, session, slot, event);
            case EFFECT_PARTICLE_CONFIG -> handleEffectParticleConfigClick(player, session, slot, event);
            case EFFECT_SOUND_CONFIG -> handleEffectSoundConfigClick(player, session, slot, event);
            case EFFECT_POTION_CONFIG -> handleEffectPotionConfigClick(player, session, slot, event);
            case EFFECT_MESSAGE_CONFIG -> handleEffectMessageConfigClick(player, session, slot, event);
            case EFFECT_TELEPORT_CONFIG -> handleEffectTeleportConfigClick(player, session, slot, event);
            case CONDITION_CATEGORY_SELECTOR -> handleConditionCategorySelectorClick(player, session, slot, event);
            case CONDITION_TYPE_SELECTOR -> handleConditionTypeSelectorClick(player, session, slot, event);
            case CONDITION_PARAMETER_CONFIG -> handleConditionParameterConfigClick(player, session, slot, event);
            case CONDITION_VIEWER -> handleConditionViewerClick(player, session, slot, event);
            case CONDITION_EDITOR -> handleConditionEditorClick(player, session, slot, event);
            case CONDITION_TEMPLATE_SELECTOR -> handleConditionTemplateSelectorClick(player, session, slot, event);
            case CONDITION_PARAMETER_EDITOR -> handleConditionParameterEditorClick(player, session, slot, event);
            case CONDITION_PRESET_SELECTOR -> handleConditionPresetSelectorClick(player, session, slot, event);
            case CONDITION_PRESET_MANAGER -> handleConditionPresetManagerClick(player, session, slot, event);
            default -> {}
        }
    }

    // ========== BUILD MAIN MENU ==========

    private void handleBuildMainMenuClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        switch (slot) {
            case 10 -> player.sendMessage(TextUtil.colorize("&e[CREATE SET] Feature coming soon"));
            case 12 -> context.openSigilCreator(player);
            case 14 -> context.openSetBrowser(player);
            case 16 -> context.openSigilBrowser(player);
            case 26 -> player.closeInventory();
        }
    }

    // ========== BROWSER HANDLERS ==========

    private void handleSetBrowserClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);

        if (item == null || item.getType().isAir()) return;

        if (item.getType() == Material.BARRIER) {
            context.openBuildMainMenu(player);
            return;
        }

        if (item.getType() == Material.DIAMOND_CHESTPLATE && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String clickedDisplayName = PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
            String plainName = clickedDisplayName.replaceAll("[^a-zA-Z0-9_ ]", "").trim();

            var allSets = plugin.getSetManager().getAllSets();
            for (var set : allSets) {
                if (set.getId().equalsIgnoreCase(plainName)) {
                    context.openSetEditor(player, set);
                    return;
                }
            }
            player.sendMessage(TextUtil.colorize("&cSet not found: '" + plainName + "'"));
        }
    }

    private void handleFunctionBrowserClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);

        if (item == null || item.getType().isAir()) return;

        if (item.getType() == Material.BARRIER) {
            context.openBuildMainMenu(player);
            return;
        }

        if (item.getType() == Material.NETHER_STAR && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String clickedDisplayName = PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
            String plainName = clickedDisplayName.replaceAll("[^a-zA-Z0-9_ ]", "").trim();

            var allSigils = plugin.getSigilManager().getAllSigils();
            for (var sigil : allSigils) {
                if (sigil.getName().equalsIgnoreCase(plainName)) {
                    context.openSigilEditor(player, sigil);
                    return;
                }
            }
            player.sendMessage(TextUtil.colorize("&cSigil not found: '" + plainName + "'"));
        }
    }

    // ========== SELECTOR HANDLERS ==========

    private void handleSlotSelectorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String creationMode = session.getString("creationMode");

        if ("SIGIL".equals(creationMode)) {
            Sigil sigil = session.get("sigil", Sigil.class);

            if (slot == 26) {
                context.openSigilCreator(player);
                return;
            }

            String armorSlot = switch (slot) {
                case 10 -> "HELMET";
                case 12 -> "CHESTPLATE";
                case 14 -> "LEGGINGS";
                case 16 -> "BOOTS";
                default -> null;
            };

            if (armorSlot != null && sigil != null) {
                sigil.setSlot(armorSlot);
                // Note: saveSigilToFile and reload logic remains in GUIManager
                player.sendMessage(TextUtil.colorize("&aSlot set to: &f" + armorSlot));
            }
            return;
        }

        String buildId = session.getString("buildId");

        if (slot == 22) {
            ArmorSet set = plugin.getSetManager().getSet(buildId);
            if (set != null) {
                context.openSetEditor(player, set);
            } else {
                context.openSetBrowser(player);
            }
            return;
        }

        String armorSlot = switch (slot) {
            case 10 -> "helmet";
            case 12 -> "chestplate";
            case 14 -> "leggings";
            case 16 -> "boots";
            default -> null;
        };

        if (armorSlot != null) {
            context.openTriggerSelectorWithSlot(player, "set", buildId, armorSlot);
        }
    }

    private void handleTriggerSelectorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        if (slot == 44) {
            String buildType = session.getString("buildType");
            String buildId = session.getString("buildId");

            if ("sigil".equalsIgnoreCase(buildType)) {
                Sigil sigil = plugin.getSigilManager().getSigil(buildId);
                if (sigil != null) {
                    context.openSigilEditor(player, sigil);
                } else {
                    context.openSigilBrowser(player);
                }
            } else if ("set".equalsIgnoreCase(buildType)) {
                ArmorSet set = plugin.getSetManager().getSet(buildId);
                if (set != null) {
                    context.openSetEditor(player, set);
                } else {
                    context.openSetBrowser(player);
                }
            } else {
                context.openBuildMainMenu(player);
            }
            return;
        }

        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);

        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String trigger = item.getItemMeta().getDisplayName()
                    .replace("§f", "")
                    .replace("&f", "");

            String buildType = session.getString("buildType");
            String buildId = session.getString("buildId");
            String armorSlot = session.getString("armorSlot");

            session.put("selectedTrigger", trigger);
            context.openEffectSelectorWithSlot(player, buildType, buildId, trigger, armorSlot);
        }
    }

    private void handleEffectSelectorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        if (slot == 44) {
            String buildType = session.getString("buildType");
            String buildId = session.getString("buildId");
            context.openTriggerSelector(player, buildType, buildId);
            return;
        }

        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(slot);

        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String effect = item.getItemMeta().getDisplayName()
                    .replace("§f", "")
                    .replace("&f", "");

            String buildType = session.getString("buildType");
            String buildId = session.getString("buildId");
            String trigger = session.getString("trigger");
            String armorSlot = session.getString("armorSlot");

            context.openEffectConfigForType(player, buildType, buildId, trigger, effect, armorSlot);
        }
    }

    // ========== TRIGGER CONFIG ==========

    private void handleTriggerConfigClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String buildType = session.getString("buildType");
        String buildId = session.getString("buildId");
        String trigger = session.getString("trigger");
        String effect = session.getString("effect");
        String armorSlot = session.getString("armorSlot");
        double chance = session.getChance();
        double cooldown = session.getCooldown();

        switch (slot) {
            case 16 -> {
                // Open condition viewer
                context.openConditionViewer(player, session);
                return;
            }
            case 19 -> chance = Math.max(0, chance - 10);
            case 20 -> chance = Math.max(0, chance - 1);
            case 24 -> chance = Math.min(100, chance + 1);
            case 25 -> chance = Math.min(100, chance + 10);
            case 28 -> cooldown = Math.max(0, cooldown - 5);
            case 29 -> cooldown = Math.max(0, cooldown - 1);
            case 33 -> cooldown = Math.min(300, cooldown + 1);
            case 34 -> cooldown = Math.min(300, cooldown + 5);
            case 39 -> {
                addTriggerEffect(player, buildType, buildId, trigger, effect, armorSlot, chance, cooldown);
                return;
            }
            case 41 -> {
                context.openEffectSelectorWithSlot(player, buildType, buildId, trigger, armorSlot);
                return;
            }
            default -> { return; }
        }

        context.openTriggerConfig(player, buildType, buildId, trigger, effect, armorSlot, chance, cooldown);
    }

    private void addTriggerEffect(Player player, String buildType, String buildId, String trigger, String effect, String armorSlot, double chance, double cooldown) {
        String triggerKey = trigger.equalsIgnoreCase("EFFECT_STATIC") ? "effect_static" : "on_" + trigger.toLowerCase();

        if ("sigil".equalsIgnoreCase(buildType)) {
            Sigil sigil = plugin.getSigilManager().getSigil(buildId);
            if (sigil != null) {
                TriggerConfig triggerConfig = sigil.getEffects().get(triggerKey);
                if (triggerConfig == null) {
                    triggerConfig = new TriggerConfig();
                    sigil.getEffects().put(triggerKey, triggerConfig);
                }

                triggerConfig.setChance(chance);
                triggerConfig.setCooldown(cooldown);
                if (!triggerConfig.getEffects().contains(effect)) {
                    triggerConfig.getEffects().add(effect);
                }

                player.sendMessage(TextUtil.colorize("&aAdded &f" + effect + " &ato &f" + trigger + " &a(" + (int)chance + "% chance, " + cooldown + "s cooldown)"));
                playSound(player, "socket");
                context.openSigilEditor(player, sigil);
            } else {
                player.sendMessage(TextUtil.colorize("&cSigil not found"));
                context.openSigilBrowser(player);
            }
        } else if ("set".equalsIgnoreCase(buildType)) {
            player.sendMessage(TextUtil.colorize("&cIndividual armor piece effects are no longer supported. Use set synergies instead."));
        }
    }

    // ========== CONFIRMATION ==========

    private void handleConfirmationClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        if (slot == 11) {
            String actionType = session.getString("actionType");
            player.sendMessage(TextUtil.colorize("&aAction confirmed: " + actionType));
            player.closeInventory();
        } else if (slot == 15) {
            player.closeInventory();
        }
    }

    // ========== EDITOR HANDLERS ==========

    private void handleSetEditorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        ArmorSet set = session.get("set", ArmorSet.class);
        String setId = session.getString("setId");

        switch (slot) {
            case 10 -> {
                if (set != null) {
                    context.openSetSynergiesViewer(player, set);
                } else {
                    player.sendMessage(TextUtil.colorize("&cError: Set not found in session"));
                }
            }
            case 11 -> {
                if (set != null) {
                    player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<new set name>"));
                    context.addPendingMessageInput(player.getUniqueId(), session);
                    session.put("inputType", "RENAME_SET");
                    player.closeInventory();
                }
            }
            case 12 -> {
                if (setId != null) {
                    context.openSynergyCreator(player, setId);
                }
            }
            case 13 -> {
                if (set != null) {
                    context.openSynergyEditor(player, set);
                }
            }
            case 14 -> {
                if (set != null) {
                    context.openTriggerRemover(player, "set", setId, set);
                }
            }
            case 16 -> {
                if (set != null) {
                    context.exportSetToYAML(player, set);
                }
            }
            case 31 -> context.openSetBrowser(player);
        }
    }

    private void handleFunctionEditorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Sigil sigil = session.get("sigil", Sigil.class);
        String sigilId = session.getString("sigilId");

        switch (slot) {
            case 10 -> {
                if (sigil != null) {
                    context.openEffectViewer(player, sigil);
                }
            }
            case 11 -> {
                if (sigil != null) {
                    context.openItemDisplayEditor(player, sigil);
                }
            }
            case 12 -> {
                if (sigil != null && !sigil.getEffects().isEmpty()) {
                    context.openTriggerEditor(player, sigil);
                } else {
                    player.sendMessage(TextUtil.colorize("&cNo triggers to edit"));
                }
            }
            case 13 -> {
                if (sigilId != null) {
                    context.openTriggerSelector(player, "sigil", sigilId);
                }
            }
            case 14 -> {
                if (sigil != null) {
                    context.openTriggerRemover(player, "sigil", sigilId, sigil);
                }
            }
            case 15 -> {
                if (sigil != null) {
                    context.exportSigilToYAML(player, sigil);
                }
            }
            case 31 -> context.openSigilBrowser(player);
        }
    }

    // ========== VIEWER HANDLERS ==========

    private void handleSetEffectsViewerClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        if (slot == 40) {
            ArmorSet set = session.get("set", ArmorSet.class);
            if (set != null) {
                context.openSetEditor(player, set);
            } else {
                context.openSetBrowser(player);
            }
            playSound(player, "close");
        }
    }

    private void handleSetSynergiesViewerClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        int lastSlot = inv.getSize() - 1;

        if (slot == lastSlot) {
            ArmorSet set = session.get("set", ArmorSet.class);
            if (set != null) {
                context.openSetEditor(player, set);
            } else {
                context.openSetBrowser(player);
            }
            playSound(player, "close");
        }
    }

    private void handleEffectViewerClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        if (slot == 40 || slot == event.getInventory().getSize() - 1) {
            Sigil sigil = session.get("sigil", Sigil.class);
            if (sigil != null) {
                context.openSigilEditor(player, sigil);
            }
        }
    }

    // ========== TRIGGER REMOVER ==========

    private void handleTriggerRemoverClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        int lastSlot = inv.getSize() - 1;

        if (slot == lastSlot) {
            String buildType = session.getString("buildType");
            Object target = session.get("target");

            if ("set".equalsIgnoreCase(buildType) && target instanceof ArmorSet set) {
                context.openSetEditor(player, set);
            } else if ("sigil".equalsIgnoreCase(buildType) && target instanceof Sigil sigil) {
                context.openSigilEditor(player, sigil);
            } else {
                context.openBuildMainMenu(player);
            }
            return;
        }

        ItemStack item = inv.getItem(slot);
        if (item == null || item.getType() != Material.TNT || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        String triggerKey = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "trigger_key"),
            PersistentDataType.STRING
        );

        if (triggerKey == null) return;

        String buildType = session.getString("buildType");
        Object target = session.get("target");
        boolean removed = false;

        if ("set".equalsIgnoreCase(buildType) && target instanceof ArmorSet set) {
            if (triggerKey.startsWith("synergy:")) {
                String synergyId = triggerKey.substring("synergy:".length());
                removed = set.getSynergies().removeIf(s -> s.getId().equals(synergyId));
            }

            if (removed) {
                player.sendMessage(TextUtil.colorize("&aRemoved trigger: &f" + triggerKey));
                playSound(player, "unsocket");
                if (set.getSynergies().isEmpty()) {
                    context.openSetEditor(player, set);
                } else {
                    context.openTriggerRemover(player, buildType, session.getString("buildId"), set);
                }
            } else {
                player.sendMessage(TextUtil.colorize("&cFailed to remove trigger"));
                playSound(player, "error");
            }

        } else if ("sigil".equalsIgnoreCase(buildType) && target instanceof Sigil sigil) {
            removed = sigil.getEffects().remove(triggerKey) != null;

            if (removed) {
                player.sendMessage(TextUtil.colorize("&aRemoved trigger: &f" + triggerKey));
                playSound(player, "unsocket");
                if (sigil.getEffects().isEmpty()) {
                    context.openSigilEditor(player, sigil);
                } else {
                    context.openTriggerRemover(player, buildType, session.getString("buildId"), sigil);
                }
            } else {
                player.sendMessage(TextUtil.colorize("&cFailed to remove trigger"));
                playSound(player, "error");
            }
        }
    }

    // ========== CREATOR HANDLERS ==========

    private void handleSigilCreatorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        switch (slot) {
            case 12 -> {
                player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<sigil ID> (use underscores for spaces, lowercase)"));
                session.put("inputType", "SIGIL_ID");
                context.addPendingMessageInput(player.getUniqueId(), session);
                player.closeInventory();
            }
            case 14 -> context.openBuildMainMenu(player);
        }
    }

    private void handleSynergyCreatorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String setId = session.getString("setId");

        switch (slot) {
            case 12 -> {
                player.sendMessage(TextUtil.colorize("&eType the synergy ID in chat:"));
                session.put("inputType", "SYNERGY_ID");
                context.addPendingMessageInput(player.getUniqueId(), session);
                player.closeInventory();
            }
            case 14 -> context.openSetEditor(player, plugin.getSetManager().getSet(setId));
        }
    }

    // ========== SYNERGY EDITOR ==========

    private void handleSynergyEditorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Boolean isSigilTriggerEditor = session.get("isSigilTriggerEditor", Boolean.class);
        if (isSigilTriggerEditor != null && isSigilTriggerEditor) {
            Sigil sigil = session.get("sigil", Sigil.class);
            @SuppressWarnings("unchecked")
            List<String> triggerKeys = (List<String>) session.get("triggerKeys");

            if (sigil == null || triggerKeys == null) {
                player.sendMessage(TextUtil.colorize("&cError: Sigil or trigger data not found"));
                return;
            }

            if (slot < triggerKeys.size()) {
                String selectedTrigger = triggerKeys.get(slot);
                context.openSigilTriggerConfigEditor(player, sigil, selectedTrigger);
                playSound(player, "click");
            }
            return;
        }

        String setId = session.getString("setId");
        String synergyId = session.getString("synergyId");

        switch (slot) {
            case 10 -> context.openTriggerSelectorForSynergy(player, setId, synergyId);
            case 12 -> context.openEffectSelectorWithSlot(player, "synergy", setId, null, null);
            case 14 -> {
                player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<chance 0-100>"));
                player.closeInventory();
            }
            case 16 -> {
                player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<cooldown seconds>"));
                player.closeInventory();
            }
            case 31 -> {
                ArmorSet set = plugin.getSetManager().getSet(setId);
                if (set != null) {
                    player.sendMessage(TextUtil.colorize("&aSynergy saved to set!"));
                    playSound(player, "socket");
                    context.openSetEditor(player, set);
                }
            }
            case 33 -> context.openSetEditor(player, plugin.getSetManager().getSet(setId));
        }
    }

    // ========== ITEM DISPLAY EDITOR ==========

    private void handleItemDisplayEditorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Sigil sigil = session.get("sigil", Sigil.class);

        switch (slot) {
            case 11 -> {
                if (sigil != null) {
                    context.openMaterialSelector(player, sigil);
                }
            }
            case 12 -> {
                if (sigil != null) {
                    player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<new sigil name>"));
                    context.addPendingMessageInput(player.getUniqueId(), session);
                    session.put("inputType", "RENAME_SIGIL");
                    player.closeInventory();
                }
            }
            case 14 -> {
                if (sigil != null) {
                    player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<description text>"));
                    context.addPendingMessageInput(player.getUniqueId(), session);
                    session.put("inputType", "EDIT_SIGIL_DESCRIPTION");
                    player.closeInventory();
                }
            }
            case 22 -> {
                if (sigil != null) {
                    context.openSigilEditor(player, sigil);
                }
            }
        }
    }

    // ========== GENERIC HANDLER ==========

    private void handleGenericClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Boolean isEditingSigilTrigger = session.get("isEditingSigilTrigger", Boolean.class);
        if (isEditingSigilTrigger != null && isEditingSigilTrigger) {
            handleSigilTriggerConfigEditorClick(player, session, slot, event);
            return;
        }

        String menuType = session.getString("menuType");
        if ("MATERIAL_SELECTOR".equals(menuType)) {
            handleMaterialSelectorClick(player, session, slot, event);
        }
    }

    private void handleSigilTriggerConfigEditorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Sigil sigil = session.get("sigil", Sigil.class);
        String triggerKey = session.getString("triggerKey");
        var triggerConfig = sigil != null ? sigil.getEffects().get(triggerKey) : null;

        if (sigil == null || triggerKey == null || triggerConfig == null) {
            player.sendMessage(TextUtil.colorize("&cError: Trigger data not found"));
            context.openTriggerEditor(player, sigil);
            return;
        }

        switch (slot) {
            case 10 -> {
                var newMode = triggerConfig.getTriggerMode().toString().equals("CHANCE") ?
                    TriggerConfig.TriggerMode.COOLDOWN :
                    TriggerConfig.TriggerMode.CHANCE;
                triggerConfig.setTriggerMode(newMode);
                player.sendMessage(TextUtil.colorize("&aChanged trigger mode to: &f" + newMode));
                playSound(player, "click");
                context.openSigilTriggerConfigEditor(player, sigil, triggerKey);
            }
            case 12 -> {
                player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<chance 0-100>"));
                GUISession inputSession = new GUISession(GUIType.GENERIC);
                inputSession.put("inputType", "SIGIL_TRIGGER_CHANCE");
                inputSession.put("sigil", sigil);
                inputSession.put("triggerKey", triggerKey);
                context.addPendingMessageInput(player.getUniqueId(), inputSession);
                player.closeInventory();
            }
            case 14 -> {
                player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<cooldown seconds>"));
                GUISession inputSession = new GUISession(GUIType.GENERIC);
                inputSession.put("inputType", "SIGIL_TRIGGER_COOLDOWN");
                inputSession.put("sigil", sigil);
                inputSession.put("triggerKey", triggerKey);
                context.addPendingMessageInput(player.getUniqueId(), inputSession);
                player.closeInventory();
            }
            case 16 -> {
                player.sendMessage(TextUtil.colorize("&7Effects for &f" + triggerKey + "&7:"));
                for (String effect : triggerConfig.getEffects()) {
                    player.sendMessage(TextUtil.colorize("&8  - &f" + effect));
                }
                context.openSigilTriggerConfigEditor(player, sigil, triggerKey);
            }
            case 40 -> context.openTriggerEditor(player, sigil);
        }
    }

    private void handleMaterialSelectorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        if (slot == 49) {
            Sigil sigil = session.get("sigil", Sigil.class);
            if (sigil != null) {
                context.openItemDisplayEditor(player, sigil);
            }
            return;
        }

        if (slot >= 0 && slot < 45) {
            ItemStack clicked = event.getInventory().getItem(slot);
            if (clicked != null && !clicked.getType().isAir()) {
                Material selectedMaterial = clicked.getType();
                Sigil sigil = session.get("sigil", Sigil.class);

                if (sigil != null) {
                    if (sigil.getItemForm() == null) {
                        sigil.setItemForm(new Sigil.ItemForm());
                    }
                    sigil.getItemForm().setMaterial(selectedMaterial);
                    player.sendMessage(TextUtil.colorize("&aSelected material: &f" + selectedMaterial.name()));
                    playSound(player, "click");
                    context.openItemDisplayEditor(player, sigil);
                }
            }
        }
    }

    // ========== EFFECT CONFIG HANDLERS ==========

    private void handleEffectValueConfigClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String buildType = session.getString("buildType");
        String buildId = session.getString("buildId");
        String trigger = session.getString("trigger");
        String effect = session.getString("effect");
        String armorSlot = session.getString("armorSlot");
        double value = session.getDouble("value", 10.0);
        String target = session.getString("target");
        int radius = session.getInt("radius", 5);

        switch (slot) {
            case 19 -> value = Math.max(0, value - 10);
            case 20 -> value = Math.max(0, value - 1);
            case 24 -> value = Math.min(1000, value + 1);
            case 25 -> value = Math.min(1000, value + 10);
            case 30 -> {
                target = "@Self";
                player.sendMessage(TextUtil.colorize("&7Target set to: &a@Self"));
            }
            case 31 -> {
                target = "@Victim";
                player.sendMessage(TextUtil.colorize("&7Target set to: &c@Victim"));
            }
            case 32 -> {
                if ("DAMAGE".equalsIgnoreCase(effect) || "INCREASE_DAMAGE".equalsIgnoreCase(effect)) {
                    target = "@Nearby:" + radius;
                    player.sendMessage(TextUtil.colorize("&7Target set to: &d@Nearby:" + radius));
                }
            }
            case 33 -> {
                if ("DAMAGE".equalsIgnoreCase(effect) || "INCREASE_DAMAGE".equalsIgnoreCase(effect)) {
                    radius = Math.max(1, radius - 1);
                    player.sendMessage(TextUtil.colorize("&7Radius: &d" + radius + " &7blocks"));
                    if (target != null && target.startsWith("@Nearby:")) {
                        target = "@Nearby:" + radius;
                    }
                }
            }
            case 35 -> {
                if ("DAMAGE".equalsIgnoreCase(effect) || "INCREASE_DAMAGE".equalsIgnoreCase(effect)) {
                    radius = Math.min(15, radius + 1);
                    player.sendMessage(TextUtil.colorize("&7Radius: &d" + radius + " &7blocks"));
                    if (target != null && target.startsWith("@Nearby:")) {
                        target = "@Nearby:" + radius;
                    }
                }
            }
            case 39 -> {
                String effectString = buildEffectString(effect, value, target);
                context.openTriggerConfig(player, buildType, buildId, trigger, effectString, armorSlot, 100, 0);
                return;
            }
            case 41 -> {
                context.openEffectSelectorWithSlot(player, buildType, buildId, trigger, armorSlot);
                return;
            }
            default -> { return; }
        }

        session.put("value", value);
        session.put("target", target);
        session.put("radius", radius);
        playSound(player, "click");
    }

    private void handleEffectParticleConfigClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String buildType = session.getString("buildType");
        String buildId = session.getString("buildId");
        String trigger = session.getString("trigger");
        String armorSlot = session.getString("armorSlot");
        String particleType = session.getString("particleType");
        int count = session.getInt("count", 10);

        if (slot >= 9 && slot < 36) {
            ItemStack clicked = event.getInventory().getItem(slot);
            if (clicked != null && clicked.hasItemMeta()) {
                String name = clicked.getItemMeta().getDisplayName();
                if (name != null) {
                    particleType = name.replace("§f", "").replace("&f", "");
                    session.put("particleType", particleType);
                    player.sendMessage(TextUtil.colorize("&7Selected particle: &f" + particleType));
                }
            }
            return;
        }

        switch (slot) {
            case 37 -> count = Math.max(1, count - 10);
            case 38 -> count = Math.max(1, count - 1);
            case 42 -> count = Math.min(100, count + 1);
            case 43 -> count = Math.min(100, count + 10);
            case 48 -> {
                String effectString = "PARTICLE:" + particleType + ":" + count;
                context.openTriggerConfig(player, buildType, buildId, trigger, effectString, armorSlot, 100, 0);
                return;
            }
            case 50 -> {
                context.openEffectSelectorWithSlot(player, buildType, buildId, trigger, armorSlot);
                return;
            }
            default -> { return; }
        }

        session.put("count", count);
    }

    private void handleEffectSoundConfigClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String buildType = session.getString("buildType");
        String buildId = session.getString("buildId");
        String trigger = session.getString("trigger");
        String armorSlot = session.getString("armorSlot");
        String soundType = session.getString("soundType");
        double volume = session.getDouble("volume", 1.0);
        double pitch = session.getDouble("pitch", 1.0);

        if (slot >= 9 && slot < 36) {
            ItemStack clicked = event.getInventory().getItem(slot);
            if (clicked != null && clicked.hasItemMeta()) {
                String sound = clicked.getItemMeta().getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "sound_id"),
                    PersistentDataType.STRING
                );
                if (sound != null) {
                    soundType = sound;
                    session.put("soundType", soundType);
                    player.sendMessage(TextUtil.colorize("&7Selected sound: &f" + soundType));
                }
            }
            return;
        }

        switch (slot) {
            case 36 -> volume = Math.max(0.1, volume - 0.5);
            case 38 -> volume = Math.min(2.0, volume + 0.5);
            case 42 -> pitch = Math.max(0.5, pitch - 0.5);
            case 44 -> pitch = Math.min(2.0, pitch + 0.5);
            case 48 -> {
                String effectString = "SOUND:" + soundType + ":" + volume + ":" + pitch;
                context.openTriggerConfig(player, buildType, buildId, trigger, effectString, armorSlot, 100, 0);
                return;
            }
            case 50 -> {
                context.openEffectSelectorWithSlot(player, buildType, buildId, trigger, armorSlot);
                return;
            }
            default -> { return; }
        }

        session.put("volume", volume);
        session.put("pitch", pitch);
    }

    private void handleEffectPotionConfigClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String buildType = session.getString("buildType");
        String buildId = session.getString("buildId");
        String trigger = session.getString("trigger");
        String armorSlot = session.getString("armorSlot");
        String potionType = session.getString("potionType");
        int duration = session.getInt("duration", 10);
        int amplifier = session.getInt("amplifier", 0);
        String target = session.getString("target");

        if (slot >= 9 && slot < 36) {
            ItemStack clicked = event.getInventory().getItem(slot);
            if (clicked != null && clicked.hasItemMeta()) {
                String potion = clicked.getItemMeta().getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "potion_id"),
                    PersistentDataType.STRING
                );
                if (potion != null) {
                    potionType = potion;
                    session.put("potionType", potionType);
                    player.sendMessage(TextUtil.colorize("&7Selected potion: &f" + potionType));
                }
            }
            return;
        }

        switch (slot) {
            case 36 -> duration = Math.max(1, duration - 5);
            case 37 -> duration = Math.max(1, duration - 1);
            case 39 -> duration = Math.min(300, duration + 1);
            case 40 -> duration = Math.min(300, duration + 5);
            case 42 -> amplifier = Math.max(0, amplifier - 1);
            case 44 -> amplifier = Math.min(4, amplifier + 1);
            case 46 -> {
                target = "@Self";
                player.sendMessage(TextUtil.colorize("&7Target set to: &a@Self"));
            }
            case 47 -> {
                target = "@Victim";
                player.sendMessage(TextUtil.colorize("&7Target set to: &c@Victim"));
            }
            case 51 -> {
                String effectString = "POTION:" + potionType + ":" + duration + ":" + amplifier;
                if (!"@Self".equals(target)) {
                    effectString += " " + target;
                }
                context.openTriggerConfig(player, buildType, buildId, trigger, effectString, armorSlot, 100, 0);
                return;
            }
            case 53 -> {
                context.openEffectSelectorWithSlot(player, buildType, buildId, trigger, armorSlot);
                return;
            }
            default -> { return; }
        }

        session.put("duration", duration);
        session.put("amplifier", amplifier);
        session.put("target", target);
        playSound(player, "click");
    }

    private void handleEffectMessageConfigClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String buildType = session.getString("buildType");
        String buildId = session.getString("buildId");
        String trigger = session.getString("trigger");
        String armorSlot = session.getString("armorSlot");
        String message = session.getString("message");

        if (slot >= 9 && slot <= 13) {
            ItemStack clicked = event.getInventory().getItem(slot);
            if (clicked != null && clicked.hasItemMeta()) {
                String preset = clicked.getItemMeta().getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "message_preset"),
                    PersistentDataType.STRING
                );
                if (preset != null) {
                    message = preset;
                    session.put("message", message);
                    player.sendMessage(TextUtil.colorize("&7Selected message: " + message));
                }
            }
            return;
        }

        switch (slot) {
            case 16 -> {
                player.closeInventory();
                player.sendMessage(TextUtil.colorize("&eType your custom message in chat:"));
                context.addPendingMessageInput(player.getUniqueId(), session);
            }
            case 18 -> {
                String effectString = "MESSAGE:" + message;
                context.openTriggerConfig(player, buildType, buildId, trigger, effectString, armorSlot, 100, 0);
            }
            case 26 -> context.openEffectSelectorWithSlot(player, buildType, buildId, trigger, armorSlot);
        }
    }

    private void handleEffectTeleportConfigClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String buildType = session.getString("buildType");
        String buildId = session.getString("buildId");
        String trigger = session.getString("trigger");
        String armorSlot = session.getString("armorSlot");
        String type = session.getString("type");
        double distance = session.getDouble("distance", 8.0);
        String facing = session.getString("facing");
        String teleportee = session.getString("teleportee");
        String target = session.getString("target");

        switch (slot) {
            case 10 -> {
                type = "RANDOM";
                player.sendMessage(TextUtil.colorize("&7Type set to: &fRANDOM"));
            }
            case 12 -> {
                type = "AROUND";
                player.sendMessage(TextUtil.colorize("&7Type set to: &fAROUND"));
            }
            case 14 -> {
                type = "BEHIND";
                player.sendMessage(TextUtil.colorize("&7Type set to: &fBEHIND"));
            }
            case 19 -> distance = Math.max(1, distance - 5);
            case 20 -> distance = Math.max(1, distance - 1);
            case 24 -> distance = Math.min(50, distance + 1);
            case 25 -> distance = Math.min(50, distance + 5);
            case 28 -> {
                facing = "KEEP";
                player.sendMessage(TextUtil.colorize("&7Facing set to: &fKEEP"));
            }
            case 30 -> {
                facing = "ENTITY";
                player.sendMessage(TextUtil.colorize("&7Facing set to: &fENTITY"));
            }
            case 32 -> {
                facing = "RANDOM";
                player.sendMessage(TextUtil.colorize("&7Facing set to: &fRANDOM"));
            }
            case 34 -> {
                facing = "AWAY";
                player.sendMessage(TextUtil.colorize("&7Facing set to: &fAWAY/SAME"));
            }
            case 37 -> {
                teleportee = "@Self".equals(teleportee) ? "@Victim" : "@Self";
                player.sendMessage(TextUtil.colorize("&7Teleportee set to: " + teleportee));
            }
            case 39 -> {
                target = "@Self".equals(target) ? "@Victim" : "@Self";
                player.sendMessage(TextUtil.colorize("&7Target set to: " + target));
            }
            case 45 -> {
                String effectString = "TELEPORT:" + type + ":" + (int)distance + ":" + facing + " " + target + " " + teleportee;
                context.openTriggerConfig(player, buildType, buildId, trigger, effectString, armorSlot, 100, 0);
                return;
            }
            case 53 -> {
                context.openEffectSelectorWithSlot(player, buildType, buildId, trigger, armorSlot);
                return;
            }
            default -> { return; }
        }

        session.put("type", type);
        session.put("distance", distance);
        session.put("facing", facing);
        session.put("teleportee", teleportee);
        session.put("target", target);
        playSound(player, "click");
    }

    // ========== CONDITION HANDLERS ==========

    private void handleConditionCategorySelectorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        GUISession parentSession = session.get("parentSession", GUISession.class);

        if (slot == 26) {
            // Back button
            if (parentSession != null) {
                context.openConditionViewer(player, parentSession);
            } else {
                player.closeInventory();
            }
            return;
        }

        com.zenax.armorsets.events.ConditionCategory category = switch (slot) {
            case 10 -> com.zenax.armorsets.events.ConditionCategory.HEALTH;
            case 12 -> com.zenax.armorsets.events.ConditionCategory.POTION;
            case 14 -> com.zenax.armorsets.events.ConditionCategory.ENVIRONMENTAL;
            case 16 -> com.zenax.armorsets.events.ConditionCategory.COMBAT;
            case 18 -> com.zenax.armorsets.events.ConditionCategory.META;
            default -> null;
        };

        if (category != null) {
            playSound(player, "click");
            context.openConditionTypeSelector(player, category, parentSession);
        }
    }

    private void handleConditionTypeSelectorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        int lastSlot = inv.getSize() - 1;
        GUISession parentSession = session.get("parentSession", GUISession.class);

        if (slot == lastSlot) {
            // Back button
            context.openConditionCategorySelector(player, parentSession);
            return;
        }

        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || clicked.getType().isAir()) return;

        // Find the condition type by matching icon and display name
        com.zenax.armorsets.events.ConditionCategory category = session.get("category", com.zenax.armorsets.events.ConditionCategory.class);
        if (category == null) return;

        com.zenax.armorsets.events.ConditionType[] types = com.zenax.armorsets.events.ConditionType.getByCategory(category);
        if (slot >= 0 && slot < types.length) {
            com.zenax.armorsets.events.ConditionType selectedType = types[slot];
            playSound(player, "click");
            context.openConditionParameterConfig(player, selectedType, parentSession);
        }
    }

    private void handleConditionParameterConfigClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        com.zenax.armorsets.events.ConditionType type = session.get("conditionType", com.zenax.armorsets.events.ConditionType.class);
        GUISession parentSession = session.get("parentSession", GUISession.class);
        int value = session.getInt("value", 0);
        String comparison = session.getString("comparison");
        if (comparison == null) comparison = "<";

        if (!type.hasParameters()) {
            // Simple conditions without parameters
            switch (slot) {
                case 12 -> {
                    // Confirm
                    String conditionString = type.getConfigKey();
                    addConditionToParent(player, parentSession, conditionString);
                    return;
                }
                case 14 -> {
                    // Cancel
                    context.openConditionTypeSelector(player, type.getCategory(), parentSession);
                    return;
                }
            }
            return;
        }

        // Conditions with parameters
        switch (slot) {
            case 19 -> value = Math.max(-1000, value - 10);
            case 20 -> value = Math.max(-1000, value - 1);
            case 24 -> value = Math.min(1000, value + 1);
            case 25 -> value = Math.min(1000, value + 10);
            case 30 -> {
                // Confirm
                String conditionString = buildConditionString(type, value, comparison);
                addConditionToParent(player, parentSession, conditionString);
                return;
            }
            case 32 -> {
                // Cancel
                context.openConditionTypeSelector(player, type.getCategory(), parentSession);
                return;
            }
            default -> { return; }
        }

        session.put("value", value);
        playSound(player, "click");
        // Refresh the GUI with updated value
        context.openConditionParameterConfig(player, type, parentSession);
    }

    private void handleConditionViewerClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        GUISession triggerSession = session.get("triggerSession", GUISession.class);
        @SuppressWarnings("unchecked")
        List<String> conditions = (List<String>) session.get("conditions");

        if (conditions == null) {
            conditions = new ArrayList<>();
            session.put("conditions", conditions);
        }

        switch (slot) {
            case 13 -> {
                // Toggle logic mode (AND/OR)
                context.toggleConditionLogic(player, triggerSession);
                playSound(player, "click");
                context.openConditionViewer(player, triggerSession);
                return;
            }
            case 23 -> {
                // Save as Preset
                player.sendMessage(TextUtil.colorize("&7Use: &f/as input &7<preset name>"));
                GUISession presetSession = new GUISession(GUIType.CONDITION_PRESET_MANAGER);
                presetSession.put("inputType", "SAVE_CONDITION_PRESET");
                presetSession.put("triggerSession", triggerSession);
                presetSession.put("conditions", new ArrayList<>(conditions));
                context.addPendingMessageInput(player.getUniqueId(), presetSession);
                player.closeInventory();
                return;
            }
            case 25 -> {
                // Template selector
                playSound(player, "click");
                context.openConditionTemplateSelector(player, triggerSession);
                return;
            }
            case 27 -> {
                // Add condition
                playSound(player, "click");
                context.openConditionCategorySelector(player, triggerSession);
                return;
            }
            case 29 -> {
                // Load Preset
                playSound(player, "click");
                context.openConditionPresetSelector(player, triggerSession);
                return;
            }
            case 31 -> {
                // Remove all
                conditions.clear();
                triggerSession.put("conditions", conditions);
                player.sendMessage(TextUtil.colorize("&aRemoved all conditions"));
                playSound(player, "unsocket");
                context.openConditionViewer(player, triggerSession);
                return;
            }
            case 35 -> {
                // Back
                String buildType = triggerSession.getString("buildType");
                String buildId = triggerSession.getString("buildId");
                String trigger = triggerSession.getString("trigger");
                String effect = triggerSession.getString("effect");
                String armorSlot = triggerSession.getString("armorSlot");
                double chance = triggerSession.getChance();
                double cooldown = triggerSession.getCooldown();
                context.openTriggerConfig(player, buildType, buildId, trigger, effect, armorSlot, chance, cooldown);
                return;
            }
        }

        // Clicked on a condition item
        if (slot >= 9 && slot < 27) {
            int index = slot - 9;
            if (index < conditions.size()) {
                if (event.isShiftClick() && event.isRightClick()) {
                    // Shift + Right-Click: Open parameter editor
                    String conditionString = conditions.get(index);
                    playSound(player, "click");
                    context.openConditionParameterEditor(player, conditionString, index, triggerSession);
                } else if (event.isShiftClick()) {
                    // Shift-Click: Remove condition
                    String removed = conditions.remove(index);
                    triggerSession.put("conditions", conditions);
                    player.sendMessage(TextUtil.colorize("&cRemoved condition: &f" + removed));
                    playSound(player, "unsocket");
                    context.openConditionViewer(player, triggerSession);
                } else {
                    // Regular click: Show condition info
                    String conditionString = conditions.get(index);
                    player.sendMessage(TextUtil.colorize("&eCondition: &f" + conditionString));
                    player.sendMessage(TextUtil.colorize("&7Shift-Click to remove, Shift-Right-Click to edit"));
                    playSound(player, "click");
                }
            }
        }
    }

    private void handleConditionEditorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String conditionString = session.getString("conditionString");
        GUISession parentSession = session.get("parentSession", GUISession.class);

        switch (slot) {
            case 12 -> {
                // Save (currently just closes, full edit functionality would require parsing)
                player.sendMessage(TextUtil.colorize("&eCondition editing coming soon"));
                playSound(player, "click");
                context.openConditionViewer(player, parentSession);
            }
            case 14 -> {
                // Cancel
                playSound(player, "close");
                context.openConditionViewer(player, parentSession);
            }
        }
    }

    // ========== HELPER METHODS ==========

    private String buildEffectString(String effect, double value, String target) {
        StringBuilder sb = new StringBuilder(effect);
        if (value > 0) {
            sb.append(":").append((int) value);
        }
        if (target != null && !"@Self".equals(target)) {
            sb.append(" ").append(target);
        }
        return sb.toString();
    }

    private String buildConditionString(com.zenax.armorsets.events.ConditionType type, int value, String comparison) {
        if (!type.hasParameters()) {
            return type.getConfigKey();
        }

        // Build condition string based on type
        return type.getConfigKey() + ":" + comparison + value;
    }

    private void addConditionToParent(Player player, GUISession parentSession, String conditionString) {
        if (parentSession == null) {
            player.sendMessage(TextUtil.colorize("&cError: No parent session"));
            player.closeInventory();
            return;
        }

        @SuppressWarnings("unchecked")
        List<String> conditions = (List<String>) parentSession.get("conditions");
        if (conditions == null) {
            conditions = new ArrayList<>();
            parentSession.put("conditions", conditions);
        }

        conditions.add(conditionString);
        player.sendMessage(TextUtil.colorize("&aAdded condition: &f" + conditionString));
        playSound(player, "socket");
        context.openConditionViewer(player, parentSession);
    }

    // ========== NEW CONDITION ENHANCEMENT HANDLERS ==========

    /**
     * Handle clicks in the Condition Template Selector GUI.
     * Displays 8 pre-built condition templates that users can apply with one click.
     */
    private void handleConditionTemplateSelectorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        GUISession parentSession = session.get("parentSession", GUISession.class);

        if (slot == 26) {
            // Back button
            if (parentSession != null) {
                context.openConditionViewer(player, parentSession);
            } else {
                player.closeInventory();
            }
            return;
        }

        // Template slots: 10-17 (8 templates)
        if (slot >= 10 && slot <= 17) {
            int templateIndex = slot - 10;
            com.zenax.armorsets.events.ConditionTemplate[] templates = com.zenax.armorsets.events.ConditionTemplate.values();

            if (templateIndex < templates.length) {
                com.zenax.armorsets.events.ConditionTemplate template = templates[templateIndex];

                // Add all template conditions to parent
                if (parentSession != null) {
                    @SuppressWarnings("unchecked")
                    List<String> conditions = (List<String>) parentSession.get("conditions");
                    if (conditions == null) {
                        conditions = new ArrayList<>();
                        parentSession.put("conditions", conditions);
                    }

                    // Check for conflicts before adding
                    List<com.zenax.armorsets.events.ConflictDetector.Conflict> conflicts =
                        com.zenax.armorsets.events.ConflictDetector.detectConflicts(
                            java.util.stream.Stream.concat(
                                conditions.stream(),
                                template.getConditions().stream()
                            ).toList()
                        );

                    if (!conflicts.isEmpty() && conflicts.stream()
                        .anyMatch(c -> c.getSeverity() == com.zenax.armorsets.events.ConflictDetector.ConflictSeverity.IMPOSSIBLE)) {
                        player.sendMessage(TextUtil.colorize("&cWarning: Template may conflict with existing conditions!"));
                        player.sendMessage(TextUtil.colorize("&7" + com.zenax.armorsets.events.ConflictDetector.getConflictSummary(conflicts)));
                    }

                    conditions.addAll(template.getConditions());
                    player.sendMessage(TextUtil.colorize("&aApplied template: &f" + template.getDisplayName()));
                    player.sendMessage(TextUtil.colorize("&7Added " + template.getConditions().size() + " conditions"));
                    playSound(player, "socket");
                    context.openConditionViewer(player, parentSession);
                } else {
                    player.sendMessage(TextUtil.colorize("&cError: No parent session"));
                    player.closeInventory();
                }
            }
        }
    }

    /**
     * Handle clicks in the Condition Parameter Editor GUI.
     * Allows direct editing of condition parameters without re-selecting the type.
     */
    private void handleConditionParameterEditorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        String conditionString = session.getString("conditionString");
        int conditionIndex = session.getInt("conditionIndex", -1);
        GUISession parentSession = session.get("parentSession", GUISession.class);

        if (parentSession == null || conditionString == null) {
            player.sendMessage(TextUtil.colorize("&cError: Invalid session data"));
            player.closeInventory();
            return;
        }

        // Parse condition
        String[] parts = conditionString.split(":");
        String type = parts.length > 0 ? parts[0] : "";
        String currentValue = parts.length > 1 ? parts[1] : "";
        double numericValue = 0;
        String comparison = "<";

        // Extract numeric value and comparison
        if (!currentValue.isEmpty()) {
            if (currentValue.startsWith(">=")) {
                comparison = ">=";
                try { numericValue = Double.parseDouble(currentValue.substring(2)); } catch (Exception e) {}
            } else if (currentValue.startsWith("<=")) {
                comparison = "<=";
                try { numericValue = Double.parseDouble(currentValue.substring(2)); } catch (Exception e) {}
            } else if (currentValue.startsWith("<")) {
                comparison = "<";
                try { numericValue = Double.parseDouble(currentValue.substring(1)); } catch (Exception e) {}
            } else if (currentValue.startsWith(">")) {
                comparison = ">";
                try { numericValue = Double.parseDouble(currentValue.substring(1)); } catch (Exception e) {}
            } else if (currentValue.startsWith("=")) {
                comparison = "=";
                try { numericValue = Double.parseDouble(currentValue.substring(1)); } catch (Exception e) {}
            } else {
                try { numericValue = Double.parseDouble(currentValue); } catch (Exception e) {}
            }
        }

        switch (slot) {
            case 11 -> {
                // Cycle comparison operator
                comparison = switch (comparison) {
                    case "<" -> "<=";
                    case "<=" -> ">";
                    case ">" -> ">=";
                    case ">=" -> "=";
                    case "=" -> "<";
                    default -> "<";
                };
                player.sendMessage(TextUtil.colorize("&7Comparison: &f" + comparison));
                playSound(player, "click");
            }
            case 19 -> numericValue = Math.max(-1000, numericValue - 10);
            case 20 -> numericValue = Math.max(-1000, numericValue - 1);
            case 24 -> numericValue = Math.min(1000, numericValue + 1);
            case 25 -> numericValue = Math.min(1000, numericValue + 10);
            case 30 -> {
                // Save changes
                String newConditionString = type + ":" + comparison + (int)numericValue;
                @SuppressWarnings("unchecked")
                List<String> conditions = (List<String>) parentSession.get("conditions");
                if (conditions != null && conditionIndex >= 0 && conditionIndex < conditions.size()) {
                    conditions.set(conditionIndex, newConditionString);
                    player.sendMessage(TextUtil.colorize("&aUpdated condition: &f" + newConditionString));
                    playSound(player, "socket");
                    context.openConditionViewer(player, parentSession);
                } else {
                    player.sendMessage(TextUtil.colorize("&cError: Invalid condition index"));
                    player.closeInventory();
                }
                return;
            }
            case 32 -> {
                // Cancel
                playSound(player, "close");
                context.openConditionViewer(player, parentSession);
                return;
            }
            default -> { return; }
        }

        // Update session and refresh
        session.put("conditionString", type + ":" + comparison + (int)numericValue);
        session.put("numericValue", numericValue);
        session.put("comparison", comparison);
        playSound(player, "click");
        context.openConditionParameterEditor(player, type + ":" + comparison + (int)numericValue, conditionIndex, parentSession);
    }

    /**
     * Handle clicks in the Condition Preset Selector GUI.
     * Displays saved condition presets that users can load.
     */
    private void handleConditionPresetSelectorClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        GUISession parentSession = session.get("parentSession", GUISession.class);
        Inventory inv = player.getOpenInventory().getTopInventory();
        int lastSlot = inv.getSize() - 1;

        if (slot == lastSlot) {
            // Back button
            if (parentSession != null) {
                context.openConditionViewer(player, parentSession);
            } else {
                player.closeInventory();
            }
            return;
        }

        // Preset items (slots 0-43)
        if (slot >= 0 && slot < 44) {
            ItemStack clicked = inv.getItem(slot);
            if (clicked == null || clicked.getType().isAir()) return;

            // Get preset ID from item metadata
            String presetId = clicked.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "preset_id"),
                PersistentDataType.STRING
            );

            if (presetId != null) {
                // Load preset from file
                java.io.File presetsFile = new java.io.File(plugin.getDataFolder(), "presets/conditions.yml");
                var allPresets = com.zenax.armorsets.events.ConditionPreset.loadAllPresets(presetsFile);
                com.zenax.armorsets.events.ConditionPreset preset = allPresets.get(presetId);

                if (preset != null && parentSession != null) {
                    @SuppressWarnings("unchecked")
                    List<String> conditions = (List<String>) parentSession.get("conditions");
                    if (conditions == null) {
                        conditions = new ArrayList<>();
                        parentSession.put("conditions", conditions);
                    }

                    if (event.isShiftClick()) {
                        // Shift-click: Replace existing conditions
                        conditions.clear();
                        conditions.addAll(preset.getConditions());
                        player.sendMessage(TextUtil.colorize("&aReplaced conditions with preset: &f" + preset.getName()));
                    } else {
                        // Regular click: Merge with existing
                        conditions.addAll(preset.getConditions());
                        player.sendMessage(TextUtil.colorize("&aAdded preset conditions: &f" + preset.getName()));
                    }

                    playSound(player, "socket");
                    context.openConditionViewer(player, parentSession);
                } else {
                    player.sendMessage(TextUtil.colorize("&cPreset not found: " + presetId));
                    playSound(player, "error");
                }
            }
        }
    }

    /**
     * Handle clicks in the Condition Preset Manager GUI.
     * This is mainly for handling input-based saves, so most logic happens in message input.
     */
    private void handleConditionPresetManagerClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        GUISession parentSession = session.get("triggerSession", GUISession.class);

        switch (slot) {
            case 14 -> {
                // Cancel
                if (parentSession != null) {
                    context.openConditionViewer(player, parentSession);
                } else {
                    player.closeInventory();
                }
            }
        }
    }
}
