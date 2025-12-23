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
 * Handler for selecting operators in the Expression Builder.
 */
public class ExpressionOperatorSelectorHandler extends AbstractHandler {

    public ExpressionOperatorSelectorHandler(ArmorSetsPlugin plugin, GUIManager guiManager) {
        super(plugin, guiManager);
    }

    @Override
    public void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        event.setCancelled(true);

        // Back button
        if (slot == 18) {
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

        // Get clicked item
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }

        // Extract operator from display name
        String displayName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // Get the session
        GUISession exprSession = session.get("expressionSession", GUISession.class);
        Consumer<String> callback = session.get("expressionCallback", null);

        // Store the selected operator
        exprSession.put("exprOperator", displayName);
        playSound(player, "success");

        // Return to expression builder
        ExpressionBuilderHandler.openGUI(guiManager, player,
                exprSession.get("exprValue1", String.class),
                exprSession.get("exprOperator", String.class),
                exprSession.get("exprValue2", String.class),
                callback);
    }
}
