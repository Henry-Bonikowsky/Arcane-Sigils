package com.zenax.armorsets.gui.handlers;

import com.zenax.armorsets.core.Sigil;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.sets.ArmorSet;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * Context interface that handlers use to interact with GUIManager functionality.
 * This provides a clean interface for handlers to call back into GUIManager
 * without creating circular dependencies.
 */
public interface GUIHandlerContext {

    // ===== GUI OPENING METHODS =====

    /**
     * Open a GUI for a player with a session.
     */
    void openGUI(Player player, Inventory inv, GUISession session);

    /**
     * Open the socket GUI for armor.
     */
    void openSocketGUI(Player player, ItemStack armor, int armorSlot);

    /**
     * Open the unsocket GUI for armor.
     */
    void openUnsocketGUI(Player player, ItemStack armor, int armorSlot);

    /**
     * Open the build main menu.
     */
    void openBuildMainMenu(Player player);

    /**
     * Open the set browser.
     */
    void openSetBrowser(Player player);

    /**
     * Open the sigil browser.
     */
    void openSigilBrowser(Player player);

    /**
     * Open the set editor for a specific set.
     */
    void openSetEditor(Player player, ArmorSet set);

    /**
     * Open the sigil editor for a specific sigil.
     */
    void openSigilEditor(Player player, Sigil sigil);

    /**
     * Open the sigil creator.
     */
    void openSigilCreator(Player player);

    /**
     * Open the synergy creator.
     */
    void openSynergyCreator(Player player, String setId);

    /**
     * Open the synergy editor.
     */
    void openSynergyEditor(Player player, String setId, String synergyId);

    /**
     * Open the synergy editor for a set.
     */
    void openSynergyEditor(Player player, ArmorSet set);

    /**
     * Open the set synergies viewer.
     */
    void openSetSynergiesViewer(Player player, ArmorSet set);

    /**
     * Open the trigger remover.
     */
    void openTriggerRemover(Player player, String buildType, String buildId, Object target);

    /**
     * Open the trigger selector.
     */
    void openTriggerSelector(Player player, String buildType, String buildId);

    /**
     * Open the trigger selector with armor slot.
     */
    void openTriggerSelectorWithSlot(Player player, String buildType, String buildId, String armorSlot);

    /**
     * Open the effect selector with armor slot.
     */
    void openEffectSelectorWithSlot(Player player, String buildType, String buildId, String trigger, String armorSlot);

    /**
     * Open the trigger config.
     */
    void openTriggerConfig(Player player, String buildType, String buildId, String trigger, String effect, String armorSlot, double chance, double cooldown);

    /**
     * Open the effect viewer.
     */
    void openEffectViewer(Player player, Sigil sigil);

    /**
     * Open the item display editor.
     */
    void openItemDisplayEditor(Player player, Sigil sigil);

    /**
     * Open the material selector.
     */
    void openMaterialSelector(Player player, Sigil sigil);

    /**
     * Open the trigger editor.
     */
    void openTriggerEditor(Player player, Sigil sigil);

    /**
     * Open the slot selector.
     */
    void openSlotSelector(Player player, String setId);

    // ===== EFFECT CONFIG METHODS =====

    /**
     * Open effect-specific configuration GUI.
     */
    void openEffectConfigForType(Player player, String buildType, String buildId, String trigger, String effect, String armorSlot);

    /**
     * Open effect value config.
     */
    void openEffectValueConfig(Player player, String buildType, String buildId, String trigger, String effect, String armorSlot);

    /**
     * Open effect particle config.
     */
    void openEffectParticleConfig(Player player, String buildType, String buildId, String trigger, String armorSlot);

    /**
     * Open effect sound config.
     */
    void openEffectSoundConfig(Player player, String buildType, String buildId, String trigger, String armorSlot);

    /**
     * Open effect potion config.
     */
    void openEffectPotionConfig(Player player, String buildType, String buildId, String trigger, String armorSlot);

    /**
     * Open effect message config.
     */
    void openEffectMessageConfig(Player player, String buildType, String buildId, String trigger, String armorSlot);

    /**
     * Open effect teleport config.
     */
    void openEffectTeleportConfig(Player player, String buildType, String buildId, String trigger, String armorSlot);

    // ===== EXPORT METHODS =====

    /**
     * Export set to YAML.
     */
    void exportSetToYAML(Player player, ArmorSet set);

    /**
     * Export sigil to YAML.
     */
    void exportSigilToYAML(Player player, Sigil sigil);

    // ===== PENDING INPUT METHODS =====

    /**
     * Get the pending message inputs map.
     */
    Map<UUID, GUISession> getPendingMessageInputs();

    /**
     * Add a pending message input.
     */
    void addPendingMessageInput(UUID playerId, GUISession session);

    // ===== SOUND METHODS =====

    /**
     * Play a sound for a player.
     */
    void playSound(Player player, String soundType);

    // ===== SIGIL TRIGGER EDITOR =====

    /**
     * Open sigil trigger config editor.
     */
    void openSigilTriggerConfigEditor(Player player, Sigil sigil, String triggerKey);

    // ===== SYNERGY TRIGGER SELECTOR =====

    /**
     * Open trigger selector for synergy.
     */
    void openTriggerSelectorForSynergy(Player player, String setId, String synergyId);

    // ===== CONDITION GUI METHODS =====

    /**
     * Open condition category selector.
     */
    void openConditionCategorySelector(Player player, GUISession parentSession);

    /**
     * Open condition type selector for a category.
     */
    void openConditionTypeSelector(Player player, com.zenax.armorsets.events.ConditionCategory category, GUISession parentSession);

    /**
     * Open condition parameter configuration.
     */
    void openConditionParameterConfig(Player player, com.zenax.armorsets.events.ConditionType type, GUISession parentSession);

    /**
     * Open condition viewer for a trigger.
     */
    void openConditionViewer(Player player, GUISession triggerSession);

    /**
     * Open condition editor.
     */
    void openConditionEditor(Player player, String conditionString, GUISession parentSession);

    // ===== NEW CONDITION ENHANCEMENT METHODS =====

    /**
     * Open condition template selector.
     */
    void openConditionTemplateSelector(Player player, GUISession parentSession);

    /**
     * Open condition parameter editor for direct editing.
     */
    void openConditionParameterEditor(Player player, String conditionString, int conditionIndex, GUISession parentSession);

    /**
     * Open condition preset selector for loading presets.
     */
    void openConditionPresetSelector(Player player, GUISession parentSession);

    /**
     * Open condition preset manager for saving new presets.
     */
    void openConditionPresetManager(Player player, GUISession parentSession);

    /**
     * Toggle condition logic mode (AND/OR) for a trigger.
     */
    void toggleConditionLogic(Player player, GUISession triggerSession);
}
