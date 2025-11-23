package com.zenax.armorsets.components;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks the menu navigation state for a player.
 * Maintains history for back navigation and pagination state.
 */
public class MenuState {

    private final UUID playerId;
    private MenuType currentMenu;
    private int currentPage;
    private int totalPages;
    private final Deque<MenuType> navigationHistory;
    private final Map<String, Object> data;
    private long lastInteraction;
    private static final long TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    /**
     * Creates a new MenuState for the specified player.
     *
     * @param playerId The UUID of the player
     */
    public MenuState(UUID playerId) {
        this.playerId = playerId;
        this.currentMenu = MenuType.MAIN_MENU;
        this.currentPage = 0;
        this.totalPages = 1;
        this.navigationHistory = new ArrayDeque<>();
        this.data = new HashMap<>();
        this.lastInteraction = System.currentTimeMillis();
    }

    /**
     * Update the last interaction time to prevent timeout.
     */
    public void updateInteraction() {
        this.lastInteraction = System.currentTimeMillis();
    }

    /**
     * Check if this menu state has timed out.
     *
     * @return true if the state has expired
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - lastInteraction > TIMEOUT_MS;
    }

    /**
     * Navigate to a new menu, saving the current menu to history.
     *
     * @param newMenu The menu to navigate to
     */
    public void navigateTo(MenuType newMenu) {
        if (currentMenu != null) {
            navigationHistory.push(currentMenu);
        }
        this.currentMenu = newMenu;
        this.currentPage = 0;
        updateInteraction();
    }

    /**
     * Navigate back to the previous menu.
     *
     * @return true if navigation was successful, false if no history exists
     */
    public boolean navigateBack() {
        if (navigationHistory.isEmpty()) {
            return false;
        }
        this.currentMenu = navigationHistory.pop();
        this.currentPage = 0;
        updateInteraction();
        return true;
    }

    /**
     * Check if there's a previous menu to navigate back to.
     *
     * @return true if back navigation is possible
     */
    public boolean canNavigateBack() {
        return !navigationHistory.isEmpty();
    }

    /**
     * Go to the next page.
     *
     * @return true if successful, false if already on last page
     */
    public boolean nextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            updateInteraction();
            return true;
        }
        return false;
    }

    /**
     * Go to the previous page.
     *
     * @return true if successful, false if already on first page
     */
    public boolean previousPage() {
        if (currentPage > 0) {
            currentPage--;
            updateInteraction();
            return true;
        }
        return false;
    }

    /**
     * Store custom data associated with the current menu context.
     *
     * @param key   The data key
     * @param value The data value
     */
    public void setData(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Retrieve custom data from the menu context.
     *
     * @param key The data key
     * @param <T> The expected type
     * @return The data value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return (T) data.get(key);
    }

    /**
     * Check if custom data exists for the given key.
     *
     * @param key The data key
     * @return true if the key exists
     */
    public boolean hasData(String key) {
        return data.containsKey(key);
    }

    /**
     * Clear all custom data.
     */
    public void clearData() {
        data.clear();
    }

    /**
     * Reset the state to the main menu.
     */
    public void reset() {
        this.currentMenu = MenuType.MAIN_MENU;
        this.currentPage = 0;
        this.totalPages = 1;
        this.navigationHistory.clear();
        this.data.clear();
        updateInteraction();
    }

    // Getters and Setters

    public UUID getPlayerId() {
        return playerId;
    }

    public MenuType getCurrentMenu() {
        return currentMenu;
    }

    public void setCurrentMenu(MenuType currentMenu) {
        this.currentMenu = currentMenu;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = Math.max(1, totalPages);
    }

    public long getLastInteraction() {
        return lastInteraction;
    }

    /**
     * Enum representing different menu types/screens.
     */
    public enum MenuType {
        MAIN_MENU("Main Menu"),
        BROWSE_FUNCTIONS("Browse Functions"),
        BROWSE_SETS("Browse Armor Sets"),
        SOCKET_FUNCTION("Socket Function"),
        UNSOCKET_FUNCTION("Unsocket Function"),
        ARMOR_INFO("Armor Info"),
        HELP_COMMANDS("Help & Commands"),
        FUNCTION_DETAIL("Function Details"),
        SET_DETAIL("Set Details"),
        BUILD_MENU("Build Menu");

        private final String displayName;

        MenuType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
