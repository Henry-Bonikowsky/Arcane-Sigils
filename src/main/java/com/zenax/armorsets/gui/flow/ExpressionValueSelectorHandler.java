package com.zenax.armorsets.gui.flow;

import com.zenax.armorsets.ArmorSetsPlugin;
import com.zenax.armorsets.gui.GUIManager;
import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.common.AbstractHandler;
import com.zenax.armorsets.utils.TextUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.Consumer;

/**
 * Handler for selecting values in the Expression Builder.
 */
public class ExpressionValueSelectorHandler extends AbstractHandler {

    public ExpressionValueSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        // Back button
        if (slot == 45) {
            playSound(player, "click");
            GUISession exprSession = session.get("expressionSession", GUISession.class);
            Consumer<String> callback = session.get("expressionCallback", null);
            ExpressionBuilderHandler.openGUI(guiManager, player,
                    exprSession.get("exprValue1", String.class),
                    exprSession.get("exprOperator", String.class),
                    exprSession.get("exprValue2", String.class),
                    callback);
            return;
        }

        // Custom number (slot 7)
        if (slot == 7) {
            playSound(player, "click");
            GUISession exprSession = session.get("expressionSession", GUISession.class);
            String valueKey = session.get("valueKey", String.class);
            Consumer<String> callback = session.get("expressionCallback", null);

            guiManager.getInputHelper().requestText(player, "Number", "",
                input -> {
                    try {
                        Double.parseDouble(input); // Validate number
                        exprSession.put(valueKey, input);
                        ExpressionBuilderHandler.openGUI(guiManager, player,
                                exprSession.get("exprValue1", String.class),
                                exprSession.get("exprOperator", String.class),
                                exprSession.get("exprValue2", String.class),
                                callback);
                    } catch (NumberFormatException e) {
                        player.sendMessage(TextUtil.colorize("§cInvalid number!"));
                        ExpressionBuilderHandler.openGUI(guiManager, player,
                                exprSession.get("exprValue1", String.class),
                                exprSession.get("exprOperator", String.class),
                                exprSession.get("exprValue2", String.class),
                                callback);
                    }
                },
                () -> ExpressionBuilderHandler.openGUI(guiManager, player,
                        exprSession.get("exprValue1", String.class),
                        exprSession.get("exprOperator", String.class),
                        exprSession.get("exprValue2", String.class),
                        callback)
            );
            return;
        }

        // Get clicked item
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }

        // Extract value from display name
        String displayName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // Get the session and value key
        GUISession exprSession = session.get("expressionSession", GUISession.class);
        String valueKey = session.get("valueKey", String.class);
        Consumer<String> callback = session.get("expressionCallback", null);

        // Store the selected value
        exprSession.put(valueKey, displayName);
        playSound(player, "success");

        // Return to expression builder
        ExpressionBuilderHandler.openGUI(guiManager, player,
                exprSession.get("exprValue1", String.class),
                exprSession.get("exprOperator", String.class),
                exprSession.get("exprValue2", String.class),
                callback);
    }
}
