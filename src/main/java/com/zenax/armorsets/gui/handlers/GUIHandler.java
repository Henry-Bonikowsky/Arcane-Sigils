package com.zenax.armorsets.gui.handlers;

import com.zenax.armorsets.gui.GUISession;
import com.zenax.armorsets.gui.GUIType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Set;

/**
 * Interface for GUI click handlers.
 * Each handler is responsible for processing clicks for specific GUI types.
 */
public interface GUIHandler {

    /**
     * Get the set of GUI types this handler can process.
     *
     * @return Set of supported GUIType values
     */
    Set<GUIType> getSupportedTypes();

    /**
     * Check if this handler can process the given GUI type.
     *
     * @param type The GUI type to check
     * @return true if this handler supports the type
     */
    default boolean canHandle(GUIType type) {
        return getSupportedTypes().contains(type);
    }

    /**
     * Handle a click event in the GUI.
     *
     * @param player  The player who clicked
     * @param session The current GUI session
     * @param slot    The slot that was clicked
     * @param event   The inventory click event
     */
    void handleClick(Player player, GUISession session, int slot, InventoryClickEvent event);
}
